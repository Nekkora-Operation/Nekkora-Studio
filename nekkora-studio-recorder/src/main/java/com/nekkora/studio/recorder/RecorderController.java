package com.nekkora.studio.recorder;

import com.nekkora.studio.bridge.BridgeCommandException;
import com.nekkora.studio.bridge.BridgeError;
import com.nekkora.studio.bridge.BridgeEvent;
import com.nekkora.studio.bridge.BrowserLink;
import com.nekkora.studio.bridge.LinkStatus;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * RecorderController — ciclo de vida da gravação técnica controlada pelo Studio. Sem Swing (D-STUDIO-002-10).
 *
 * <ul>
 *   <li><b>Confirmação obrigatória</b>: cada comando leva o Recorder a um estado "-ING"; o estado final só vem da
 *       resposta (ou evento) da extensão, que é a fonte de verdade da gravação.</li>
 *   <li><b>Uma thread própria</b>: todas as mudanças de estado acontecem num executor de thread única; métodos
 *       públicos só enfileiram (nunca bloqueiam a EDT). Ouvintes recebem fotografias imutáveis nessa thread — a
 *       interface repassa para a EDT.</li>
 *   <li><b>Concessão</b>: renovada periodicamente enquanto há gravação; sem renovação a extensão libera a captura.</li>
 *   <li><b>Recuperação</b>: ligação perdida ⇒ DEGRADED; ao voltar, RECOVERING consulta o estado real e reconcilia.
 *       Resultado desconhecido (tempo esgotado, canal perdido) nunca é tratado como sucesso.</li>
 *   <li><b>Etapas (STUDIO-003)</b>: com um {@link StepSink}, o início negocia a capacidade {@code steps/1}; cada
 *       {@code studio.recording.step} é ordenado pela sequência ({@link StepInbox}), roteado à sessão lógica pela rota
 *       vigente quando foi consolidado, entregue ao domínio em ordem e só então CONFIRMADO à extensão (ACK cumulativo,
 *       com as recusadas à parte). Lacunas (renovação/consulta mostram etapas emitidas que não chegaram) pedem
 *       reemissão; o cancelamento confirmado descarta as etapas da gravação numa única operação de domínio.</li>
 *   <li><b>Evidências (STUDIO-005)</b>: com um {@link EvidenceSink}, o início pede também {@code evidence/1} (extensão
 *       anterior: recusa ⇒ nova tentativa só com {@code steps/1}, depois sem capacidades). Cada
 *       {@code studio.recording.evidence} é lido de forma estrita ({@link RecordedEvidence}), ordenado pela sequência
 *       própria ({@link EvidenceInbox}), espera a etapa dele chegar (fila limitada), é registrado no domínio DEPOIS da
 *       etapa e só então confirmado (ACK cumulativo contíguo). Lacunas pedem reemissão. Nada disso é edição.</li>
 * </ul>
 */
public final class RecorderController implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(RecorderController.class.getName());

    /** Prazos do Recorder. */
    public record Options(Duration lease, Duration renewEvery, Duration commandTimeout, Duration startTimeout) {
        public static Options defaults() {
            return new Options(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(120));
        }
    }

    private final BrowserLink link;
    private final Clock clock;
    private final Options options;
    private final StepSink sink;
    private final EvidenceSink evidenceSink;
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "nekkora-recorder");
        t.setDaemon(true);
        return t;
    });
    private final List<Consumer<RecorderSnapshot>> listeners = new CopyOnWriteArrayList<>();
    private final BrowserLink.Listener linkListener;

    // ---- estado (somente na thread do Recorder)
    private RecorderState state = RecorderState.IDLE;
    private RecordingMode mode;
    private RecordingMode pendingMode;
    private TechnicalRecording recording;
    private String tabTitle;
    private String tabHost;
    private RecordingProgress progress = RecordingProgress.NONE;
    private String reason;
    private UserProblem problem;
    private RecordingSummary summary;
    private LinkStatus linkStatus;
    private Instant lastApplied;
    private boolean confirmedStart;
    private boolean linkDown;
    private String pendingOp;
    private ScheduledFuture<?> renewTask;
    private volatile RecorderSnapshot snapshot;

    // ---- etapas gravadas (STUDIO-003; somente na thread do Recorder)
    private boolean stepsEnabled;
    private StepInbox inbox = new StepInbox();
    private final java.util.Map<String, SessionId> routes = new java.util.HashMap<>();
    private int routeCounter;
    private int accepted;
    private int rejected;
    private int lost;
    private int inFlight;
    private long delivered;
    private final List<Long> rejectedSinceAck = new ArrayList<>();
    private boolean ackInFlight;
    private boolean ackDirty;
    private boolean syncing;
    private boolean waiting;
    private boolean halted;
    private boolean discarded;
    /** Troca de sessão pedida e ainda não confirmada pela extensão (a associação só muda com a confirmação). */
    private SessionId pendingSession;
    private String pendingRouteRef;
    private String pendingRouteOp;
    private boolean routeInFlight;
    private SessionId queuedSession;
    private CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

    // ---- evidências observadas (STUDIO-005; somente na thread do Recorder)
    /** Evidências esperando a etapa delas (que chega por outro canal). */
    static final int MAX_HELD_EVIDENCE = 500;
    private boolean evidenceEnabled;
    private EvidenceInbox evInbox = new EvidenceInbox();
    private final java.util.Set<String> knownEvents = new java.util.HashSet<>();
    private final java.util.Map<String, List<com.nekkora.studio.domain.evidence.ObservedEvidence>> heldForStep = new java.util.HashMap<>();
    private int heldCount;
    private int evProduced;
    private int evAccepted;
    private int evUnattributed;
    private int evIncomplete;
    private int evRejected;
    private int evLost;
    private int evInFlight;
    private Instant evLast;
    private final List<Long> evRejectedSinceAck = new ArrayList<>();
    private boolean evAckInFlight;
    private boolean evAckDirty;
    private boolean evSyncing;
    private boolean evWaiting;

    public RecorderController(BrowserLink link, Clock clock, Options options) {
        this(link, clock, options, StepSink.NONE);
    }

    /** @param sink destino das etapas gravadas; {@link StepSink#NONE} = gravação só técnica (STUDIO-002) */
    public RecorderController(BrowserLink link, Clock clock, Options options, StepSink sink) {
        this(link, clock, options, sink, EvidenceSink.NONE);
    }

    /**
     * @param sink         destino das etapas gravadas
     * @param evidenceSink destino das evidências observadas; {@link EvidenceSink#NONE} = sem a capacidade evidence/1
     */
    public RecorderController(BrowserLink link, Clock clock, Options options, StepSink sink, EvidenceSink evidenceSink) {
        this.link = Objects.requireNonNull(link);
        this.clock = Objects.requireNonNull(clock);
        this.options = Objects.requireNonNull(options);
        this.sink = Objects.requireNonNull(sink);
        this.evidenceSink = Objects.requireNonNull(evidenceSink);
        this.linkStatus = link.status();
        this.snapshot = build();
        this.linkListener = new BrowserLink.Listener() {
            @Override
            public void statusChanged(LinkStatus status) {
                run(() -> onLink(status));
            }

            @Override
            public void event(BridgeEvent event) {
                run(() -> onEvent(event));
            }
        };
        link.addListener(linkListener);
    }

    // ================================================================================= API (qualquer thread)

    public void addListener(Consumer<RecorderSnapshot> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<RecorderSnapshot> listener) {
        listeners.remove(listener);
    }

    /** Última fotografia publicada. */
    public RecorderSnapshot snapshot() {
        return snapshot;
    }

    /** Abas do Chrome (apenas informações autorizadas e mascaradas pela extensão). */
    public CompletableFuture<List<BrowserTab>> listTabs() {
        return link.send("browser.tabs.list", Map.of(), options.commandTimeout()).thenApply(p -> {
            List<BrowserTab> out = new ArrayList<>();
            for (Object o : Json.array(p, "tabs")) {
                if (o instanceof Map<?, ?> raw) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> t = (Map<String, Object>) raw;
                    out.add(new BrowserTab(Json.string(t, "tabRef"), tabTitle(Json.string(t, "title")), safe(Json.string(t, "host")),
                            TabAccess.of(Json.string(t, "access")), Json.bool(t, "active", false), Json.bool(t, "recording", false)));
                }
            }
            return out;
        });
    }

    public void start(RecordingTarget target, BrowserTab tab, RecordingMode requested) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(tab);
        Objects.requireNonNull(requested);
        run(() -> doStart(target, tab, requested));
    }

    public void pause() {
        run(() -> simple("studio.recording.pause", RecorderState.RECORDING, RecorderState.PAUSING));
    }

    /** Continua após a pausa; a partir de uma interrupção da página, tenta retomar (RECOVERING). */
    public void resume() {
        run(() -> {
            if (state == RecorderState.PAUSED) {
                simple("studio.recording.resume", RecorderState.PAUSED, RecorderState.RESUMING);
            } else if (state == RecorderState.DEGRADED && !linkDown) {
                simple("studio.recording.resume", RecorderState.DEGRADED, RecorderState.RECOVERING);
            }
        });
    }

    public void finish() {
        run(() -> end("studio.recording.finish", RecorderState.FINALIZING));
    }

    public void cancel() {
        run(() -> end("studio.recording.cancel", RecorderState.CANCELLING));
    }

    public void setMode(RecordingMode requested) {
        Objects.requireNonNull(requested);
        run(() -> doMode(requested));
    }

    /**
     * Associa a gravação a outra sessão lógica da MESMA automação (sem nova captura). Com etapas gravadas, a troca fica
     * PENDENTE até a extensão confirmar a rota nova; recusa explícita mantém a sessão anterior, resultado desconhecido
     * (tempo esgotado, ligação perdida) é reconciliado refazendo o mesmo pedido (idempotente pela operação).
     */
    public void associate(SessionId session) {
        Objects.requireNonNull(session);
        run(() -> {
            if (recording == null || !state.active()) {
                return;
            }
            if (!routing()) {
                if (recording.associate(session, clock.instant())) {
                    publish();
                }
                return;
            }
            if (pendingSession != null) {
                queuedSession = session.equals(pendingSession) ? null : session; // vale a última escolha, depois da atual
                return;
            }
            if (recording.session().equals(session) || !(state == RecorderState.RECORDING || state == RecorderState.PAUSED
                    || state == RecorderState.DEGRADED)) {
                return;
            }
            requestRoute(session);
        });
    }

    /** Desfecho visto: volta ao estado ocioso. */
    public void acknowledge() {
        run(() -> {
            if (state.terminal()) {
                move(RecorderState.IDLE);
                clearRecording();
                publish();
            } else if (state == RecorderState.IDLE && problem != null) {
                problem = null;
                publish();
            }
        });
    }

    /**
     * Ligação perdida e o usuário desiste de esperar: a gravação é encerrada SÓ no Studio (ERROR). Nada é dado como
     * finalizado; a extensão libera a captura quando a concessão expirar.
     */
    public void abandon() {
        run(() -> {
            if (state == RecorderState.DEGRADED && linkDown) {
                stopRenew();
                move(RecorderState.ERROR);
                problem = new UserProblem(BridgeError.LINK_LOST, "Gravação encerrada sem confirmação",
                        "O navegador não confirmou o encerramento. A extensão libera a captura sozinha quando a concessão expirar.");
                publish();
            }
        });
    }

    /** A gravação técnica corrente (associações), se houver. */
    public java.util.Optional<TechnicalRecording> recording() {
        return java.util.Optional.ofNullable(recording);
    }

    /**
     * Encerramento do Studio: pede ao navegador para finalizar (ou cancelar) e espera a confirmação por no máximo
     * {@code wait}. Sem ligação, nada é enviado — a concessão expira e a extensão libera a captura.
     */
    public CompletableFuture<Void> shutdown(boolean finish, Duration wait) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        run(() -> {
            if (recording == null || !state.active() || !link.status().connected()) {
                done.complete(null);
                return;
            }
            String op = newOp();
            pendingOp = op;
            if (state == RecorderState.RECORDING || state == RecorderState.PAUSED || state == RecorderState.DEGRADED) {
                move(finish ? RecorderState.FINALIZING : RecorderState.CANCELLING);
                publish();
            }
            String type = finish ? "studio.recording.finish" : "studio.recording.cancel";
            link.send(type, Map.of("recordingId", recording.recordingId(), "operationId", op), wait)
                    .whenComplete((p, e) -> run(() -> {
                        pendingOp = null;
                        if (e == null) {
                            applyResult(p);
                        }
                        stopRenew();
                        publish();
                        done.complete(null);
                    }));
        });
        return done.orTimeout(wait.toMillis() + 1000, TimeUnit.MILLISECONDS).exceptionally(e -> null);
    }

    @Override
    public void close() {
        link.removeListener(linkListener);
        exec.shutdownNow();
    }

    // ================================================================================= operações

    private void doStart(RecordingTarget target, BrowserTab tab, RecordingMode requested) {
        if (state != RecorderState.IDLE) {
            return; // uma gravação por vez
        }
        problem = null;
        summary = null;
        if (!link.status().connected()) {
            problem = new UserProblem(BridgeError.EXTENSION_UNAVAILABLE, "Navegador não conectado",
                    "Abra o Chrome e ligue “Conectar ao Nekkora Studio” no painel da Nekkora Extension.");
            publish();
            return;
        }
        recording = new TechnicalRecording("rec-" + UUID.randomUUID(), target.automation(), target.session(), clock.instant());
        pendingMode = requested;
        tabTitle = tab.label();
        tabHost = tab.host();
        confirmedStart = false;
        lastApplied = null;
        progress = RecordingProgress.NONE;
        move(RecorderState.STARTING);
        publish();
        String op = newOp();
        pendingOp = op;
        Json.Builder start = Json.obj().put("recordingId", recording.recordingId()).put("operationId", op).put("tabRef", tab.tabRef())
                .put("mode", requested.wire()).put("leaseMs", options.lease().toMillis());
        resetSteps();
        if (sink != StepSink.NONE) {
            String route = "route-" + (++routeCounter);
            routes.put(route, target.session());
            start.put("capabilities", evidenceSink != EvidenceSink.NONE
                    ? List.of(com.nekkora.studio.bridge.BridgeProtocol.CAPABILITY_STEPS, com.nekkora.studio.bridge.BridgeProtocol.CAPABILITY_EVIDENCE)
                    : List.of(com.nekkora.studio.bridge.BridgeProtocol.CAPABILITY_STEPS)).put("routeRef", route);
        }
        Map<String, Object> payload = start.build();
        link.send("studio.recording.start", payload, options.startTimeout()).whenComplete((p, e) -> run(() -> {
            if (!op.equals(pendingOp)) {
                if (e == null) {
                    applyResult(p); // operação superada (ex.: ligação perdida e reconciliada): a resposta ainda é verdade
                }
                return;
            }
            pendingOp = null;
            if (e == null) {
                confirmedStart = true;
                applyResult(p);
                startRenew();
                publish();
                return;
            }
            BridgeCommandException x = cause(e);
            if (unknownOutcome(x)) {
                recoverOrDegrade();
                return;
            }
            if (x.code() == BridgeError.BAD_REQUEST && evidenceRequested(payload)) {
                // Extensão da STUDIO-003/004 (contrato sem evidence/1): etapas sem evidências, como antes.
                LOG.info("Extensão sem evidências observadas: gravação com etapas");
                payload.put("capabilities", List.of(com.nekkora.studio.bridge.BridgeProtocol.CAPABILITY_STEPS));
                pendingOp = newOp();
                payload.put("operationId", pendingOp);
                String retry = pendingOp;
                link.send("studio.recording.start", payload, options.startTimeout()).whenComplete((p2, e2) -> run(() -> onStepsOnlyStart(retry, payload, p2, e2)));
                return;
            }
            if (x.code() == BridgeError.BAD_REQUEST && payload.containsKey("capabilities")) {
                // Extensão da STUDIO-002 (contrato sem steps/1): grava só a parte técnica, como antes.
                LOG.info("Extensão sem gravação de etapas: gravação técnica");
                payload.remove("capabilities");
                payload.remove("routeRef");
                routes.clear();
                pendingOp = newOp();
                payload.put("operationId", pendingOp);
                String retry = pendingOp;
                link.send("studio.recording.start", payload, options.startTimeout()).whenComplete((p2, e2) -> run(() -> onStartResult(retry, p2, e2)));
                return;
            }
            pendingMode = null;
            problem = problemFor(x);
            move(RecorderState.IDLE);
            clearRecordingKeepProblem();
            publish();
        }));
    }

    private static boolean evidenceRequested(Map<String, Object> payload) {
        return payload.get("capabilities") instanceof List<?> l && l.contains(com.nekkora.studio.bridge.BridgeProtocol.CAPABILITY_EVIDENCE);
    }

    /** Resposta do início só com steps/1 (extensão anterior à STUDIO-005); recusa ⇒ última tentativa, só técnica. */
    private void onStepsOnlyStart(String op, Map<String, Object> payload, Map<String, Object> p, Throwable e) {
        if (e != null && op.equals(pendingOp) && cause(e).code() == BridgeError.BAD_REQUEST) {
            LOG.info("Extensão sem gravação de etapas: gravação técnica");
            payload.remove("capabilities");
            payload.remove("routeRef");
            routes.clear();
            pendingOp = newOp();
            payload.put("operationId", pendingOp);
            String retry = pendingOp;
            link.send("studio.recording.start", payload, options.startTimeout()).whenComplete((p2, e2) -> run(() -> onStartResult(retry, p2, e2)));
            return;
        }
        onStartResult(op, p, e);
    }

    /** Resposta do início sem a capacidade de etapas (extensão anterior à STUDIO-003). */
    private void onStartResult(String op, Map<String, Object> p, Throwable e) {
        if (!op.equals(pendingOp)) {
            if (e == null) {
                applyResult(p);
            }
            return;
        }
        pendingOp = null;
        if (e == null) {
            confirmedStart = true;
            applyResult(p);
            startRenew();
            publish();
            return;
        }
        BridgeCommandException x = cause(e);
        if (unknownOutcome(x)) {
            recoverOrDegrade();
            return;
        }
        pendingMode = null;
        problem = problemFor(x);
        move(RecorderState.IDLE);
        clearRecordingKeepProblem();
        publish();
    }

    /** pause / resume: estado estável → pendente → confirmado. */
    private void simple(String type, RecorderState from, RecorderState pending) {
        if (state != from || recording == null || linkDown) {
            return;
        }
        problem = null;
        move(pending);
        publish();
        String op = newOp();
        pendingOp = op;
        link.send(type, Map.of("recordingId", recording.recordingId(), "operationId", op), options.commandTimeout())
                .whenComplete((p, e) -> run(() -> onResult(op, from, p, e)));
    }

    private void end(String type, RecorderState pending) {
        if (recording == null || linkDown || !(state == RecorderState.RECORDING || state == RecorderState.PAUSED || state == RecorderState.DEGRADED)) {
            return;
        }
        RecorderState from = state;
        problem = null;
        move(pending);
        publish();
        String op = newOp();
        pendingOp = op;
        link.send(type, Map.of("recordingId", recording.recordingId(), "operationId", op), options.commandTimeout())
                .whenComplete((p, e) -> run(() -> {
                    onResult(op, from, p, e);
                    if (state.terminal()) {
                        stopRenew();
                    }
                }));
    }

    private void doMode(RecordingMode requested) {
        if (recording == null || linkDown || !(state == RecorderState.RECORDING || state == RecorderState.PAUSED || state == RecorderState.DEGRADED)) {
            return;
        }
        if (requested == mode && pendingMode == null) {
            return;
        }
        pendingMode = requested;
        problem = null;
        publish();
        String op = newOp();
        link.send("studio.recording.mode", Map.of("recordingId", recording.recordingId(), "operationId", op, "mode", requested.wire()),
                options.commandTimeout()).whenComplete((p, e) -> run(() -> {
                    if (e == null) {
                        applyResult(p);
                    } else {
                        pendingMode = null;
                        BridgeCommandException x = cause(e);
                        problem = problemFor(x);
                        if (sensorNotConfirmed(x)) {
                            // a página não confirmou o modo novo: a extensão manteve o anterior ou interrompeu a captura
                            problem = new UserProblem(x.code(), "Modo não alterado", safeMessage(x.getMessage()));
                            recoverOrDegrade();
                            return;
                        }
                        if (unknownOutcome(x)) {
                            recoverOrDegrade();
                            return;
                        }
                    }
                    publish();
                }));
    }

    private void onResult(String op, RecorderState from, Map<String, Object> payload, Throwable error) {
        if (!op.equals(pendingOp)) {
            if (error == null) {
                applyResult(payload);
                publish();
            }
            return;
        }
        pendingOp = null;
        if (error == null) {
            applyResult(payload);
            publish();
            return;
        }
        BridgeCommandException x = cause(error);
        if (sensorNotConfirmed(x)) {
            // ex.: retomada sem o sensor da página: a extensão pode ter interrompido a captura; nada de voltar a um estado suposto
            problem = new UserProblem(x.code(), "Captura interrompida", safeMessage(x.getMessage()));
            recoverOrDegrade();
            return;
        }
        if (unknownOutcome(x) || x.code() == BridgeError.RECORDING_ENDED || x.code() == BridgeError.RECORDING_NOT_FOUND
                || x.code() == BridgeError.INVALID_STATE) {
            problem = x.code() == BridgeError.RECORDING_ENDED ? problemFor(x) : null;
            recoverOrDegrade(); // a verdade está na extensão: reconcilia
            return;
        }
        problem = problemFor(x);
        move(from); // recusa explícita: volta ao estado estável anterior, sem fingir sucesso
        publish();
    }

    // ================================================================================= reconciliação

    private void recoverOrDegrade() {
        if (recording == null) {
            return;
        }
        if (!link.status().connected()) {
            degradeForLink();
            return;
        }
        move(RecorderState.RECOVERING);
        publish();
        String rid = recording.recordingId();
        link.send("studio.recording.status", Map.of("recordingId", rid), options.commandTimeout()).whenComplete((p, e) -> run(() -> {
            if (recording == null || !rid.equals(recording.recordingId())) {
                return;
            }
            if (e == null) {
                List<Object> list = Json.array(p, "recordings");
                if (!list.isEmpty() && list.get(0) instanceof Map<?, ?> m) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rec = (Map<String, Object>) m;
                    confirmedStart = true;
                    lastApplied = null; // a consulta é a verdade corrente
                    applyRecording(rec, null);
                    if (state.active() && renewTask == null) {
                        startRenew();
                    }
                    sendRoute(); // troca de sessão sem desfecho conhecido: o mesmo pedido de novo
                }
                publish();
                return;
            }
            BridgeCommandException x = cause(e);
            if (x.code() == BridgeError.RECORDING_NOT_FOUND && !confirmedStart) {
                problem = new UserProblem(x.code(), "Gravação não iniciada", "O navegador não chegou a iniciar a captura técnica. Tente gravar novamente.");
                move(RecorderState.IDLE);
                clearRecordingKeepProblem();
            } else if (unknownOutcome(x)) {
                degradeForLink();
                return;
            } else {
                problem = new UserProblem(x.code(), "Gravação perdida", "O navegador não reconhece mais esta gravação técnica.");
                stopRenew();
                move(RecorderState.ERROR);
            }
            publish();
        }));
    }

    private void degradeForLink() {
        linkDown = true;
        reason = "LINK_LOST";
        move(RecorderState.DEGRADED);
        publish();
    }

    private void onLink(LinkStatus status) {
        linkStatus = status;
        if (status.connected()) {
            if (linkDown && recording != null && state.active()) {
                linkDown = false;
                recoverOrDegrade();
                return;
            }
            linkDown = false;
        } else if (recording != null && state.active()) {
            pendingOp = null; // comandos em voo falham com LINK_LOST; o resultado é desconhecido
            routeInFlight = false; // a troca de sessão continua pendente até a reconciliação
            degradeForLink();
            return;
        }
        publish();
    }

    private void onEvent(BridgeEvent event) {
        if (recording == null || !recording.recordingId().equals(Json.string(event.payload(), "recordingId"))) {
            return; // gravação antiga, de outro controlador ou já encerrada no Studio: nada é aceito
        }
        if ("studio.recording.step".equals(event.type())) {
            onStep(event.payload());
            return;
        }
        if ("studio.recording.evidence".equals(event.type())) {
            onEvidence(event.payload());
            return;
        }
        String op = Json.string(event.payload(), "operationId");
        if (op != null && op.equals(pendingOp)) {
            return; // a resposta do comando é quem conclui a operação
        }
        Map<String, Object> rec = Json.object(event.payload(), "recording");
        if (rec != null) {
            applyRecording(rec, Json.object(event.payload(), "summary"));
            if (state.terminal()) {
                stopRenew();
            }
            publish();
        }
    }

    // ================================================================================= aplicação da verdade

    private void applyResult(Map<String, Object> payload) {
        Map<String, Object> rec = Json.object(payload, "recording");
        if (rec != null) {
            applyRecording(rec, Json.object(payload, "summary"));
        }
    }

    private void applyRecording(Map<String, Object> rec, Map<String, Object> sum) {
        if (recording == null || !recording.recordingId().equals(Json.string(rec, "recordingId"))) {
            return;
        }
        Instant updated = parse(Json.string(rec, "updatedAt"));
        if (lastApplied != null && updated != null && updated.isBefore(lastApplied)) {
            return; // fotografia mais antiga que a já aplicada
        }
        if (updated != null) {
            lastApplied = updated;
        }
        RecordingMode m = RecordingMode.ofWire(Json.string(rec, "mode"));
        if (m != null) {
            mode = m;
            if (pendingMode == m) {
                pendingMode = null;
            }
        }
        Map<String, Object> tab = Json.object(rec, "tab");
        String title = tabTitle(Json.string(tab, "title"));
        tabTitle = title != null ? title : tabTitle;
        tabHost = safe(Json.string(tab, "host")) != null ? safe(Json.string(tab, "host")) : tabHost;
        progress = progressOf(rec);
        reason = Json.string(rec, "reason");
        RecorderState target = switch (String.valueOf(Json.string(rec, "state"))) {
            case "RECORDING" -> RecorderState.RECORDING;
            case "PAUSED" -> RecorderState.PAUSED;
            case "DEGRADED" -> RecorderState.DEGRADED;
            case "COMPLETED" -> RecorderState.COMPLETED;
            case "CANCELLED" -> RecorderState.CANCELLED;
            default -> RecorderState.ERROR; // EXPIRED, FAILED
        };
        if (target == RecorderState.ERROR) {
            problem = "EXPIRED".equals(Json.string(rec, "state"))
                    ? new UserProblem(BridgeError.RECORDING_ENDED, "Gravação expirada", "A concessão de controle expirou e a extensão encerrou a captura técnica.")
                    : new UserProblem(BridgeError.INTERNAL_ERROR, "Gravação falhou", "A extensão encerrou a gravação técnica com falha.");
        }
        if (target == RecorderState.DEGRADED) {
            linkDown = false;
            problem = degradedProblem(reason);
        }
        if (sum != null) {
            summary = summaryOf(target, sum);
        }
        Map<String, Object> st = Json.object(rec, "steps");
        if (st != null) {
            stepsState(st);
        }
        Map<String, Object> evs = Json.object(rec, "evidence");
        if (evs != null) {
            evidenceState(evs);
        }
        move(target);
        if (target.terminal()) {
            clearPendingRoute(); // encerrada: nenhuma troca pendente sobrevive
            queuedSession = null;
        }
        if (target == RecorderState.CANCELLED) {
            discardSteps();
        }
    }

    private static UserProblem degradedProblem(String reason) {
        String why = switch (String.valueOf(reason)) {
            case "TAB_CLOSED" -> "A aba da gravação foi fechada.";
            case "PERMISSION_REVOKED" -> "A autorização do site foi revogada na extensão. A captura foi encerrada.";
            case "ORIGIN_NOT_ALLOWED" -> "A aba saiu para uma página que não está autorizada.";
            case "CAPTURE_ERROR" -> "A captura técnica da página entrou em erro.";
            case "SENSOR_NOT_CONFIRMED" -> "A página não confirmou os sensores de interação. A captura de ações foi interrompida; use “Tentar retomar”.";
            default -> "A captura técnica da página foi encerrada pelo navegador.";
        };
        return new UserProblem(BridgeError.CAPTURE_FAILED, "Captura interrompida", why);
    }

    private RecordingProgress progressOf(Map<String, Object> rec) {
        Map<String, Object> cap = Json.object(rec, "capture");
        Map<String, Object> cov = Json.object(rec, "coverage");
        Map<String, Object> cnt = Json.object(rec, "counters");
        return new RecordingProgress(Json.bool(cap, "recorderAttached", false), (int) Json.number(cap, "otherConsumers", 0),
                Json.string(cap, "captureStatus"), String.valueOf(Json.string(cov, "state")), (int) Json.number(cov, "framesUnavailable", 0),
                (int) Json.number(cnt, "technicalCaptures", 0), (int) Json.number(cnt, "synchronizations", 0),
                (int) Json.number(cnt, "recoveries", 0), (int) Json.number(cnt, "segments", 0));
    }

    private RecordingSummary summaryOf(RecorderState outcome, Map<String, Object> sum) {
        Map<String, Object> wrapper = Json.obj().put("capture", Json.obj().build()).put("coverage", Json.object(sum, "coverage"))
                .put("counters", Json.object(sum, "counters")).build();
        List<String> lim = new ArrayList<>();
        for (Object o : Json.array(sum, "limitations")) {
            lim.add(String.valueOf(o));
        }
        Map<String, Object> st = Json.object(sum, "steps");
        Map<String, Object> ev = Json.object(sum, "evidence");
        EvidenceProgress evidence = ev == null ? EvidenceProgress.NONE
                : new EvidenceProgress(true, (int) Json.number(ev, "produced", 0), evAccepted, evUnattributed, evIncomplete,
                        evInbox.buffered() + heldCount + evInFlight, evRejected, (int) Json.number(ev, "lost", 0), evWaiting, evLast);
        return new RecordingSummary(outcome, Duration.ofMillis(Json.number(sum, "durationMs", 0)), progressOf(wrapper), lim,
                Json.bool(sum, "pageMapExported", false), Json.string(sum, "actionCapture"), (int) Json.number(st, "produced", 0), accepted, rejected,
                (int) Json.number(st, "lost", 0), evidence);
    }

    // ================================================================================= etapas (STUDIO-003)

    private void resetSteps() {
        stepsEnabled = false;
        inbox = new StepInbox();
        routes.clear();
        routeCounter = 0;
        accepted = 0;
        rejected = 0;
        lost = 0;
        inFlight = 0;
        delivered = 0;
        rejectedSinceAck.clear();
        ackInFlight = false;
        ackDirty = false;
        syncing = false;
        waiting = false;
        halted = false;
        discarded = false;
        clearPendingRoute();
        queuedSession = null;
        chain = CompletableFuture.completedFuture(null);
        evidenceEnabled = false;
        evInbox = new EvidenceInbox();
        knownEvents.clear();
        heldForStep.clear();
        heldCount = 0;
        evProduced = 0;
        evAccepted = 0;
        evUnattributed = 0;
        evIncomplete = 0;
        evRejected = 0;
        evLost = 0;
        evInFlight = 0;
        evLast = null;
        evRejectedSinceAck.clear();
        evAckInFlight = false;
        evAckDirty = false;
        evSyncing = false;
        evWaiting = false;
    }

    /** Estado da entrega informado pela extensão (gravação, renovação, consulta): perdas declaradas e lacunas. */
    private void stepsState(Map<String, Object> st) {
        if (!Json.bool(st, "enabled", false) || sink == StepSink.NONE) {
            return;
        }
        stepsEnabled = true;
        lost = (int) Json.number(st, "lost", lost);
        long last = Json.number(st, "lastSequence", 0);
        if (inbox.missing(last) && inbox.highestSeen() < last && !halted) {
            waiting = true;
            requestSync(); // a extensão emitiu etapas que não chegaram aqui (ex.: canal caiu): reemissão em ordem
        } else if (!inbox.missing(last)) {
            waiting = false;
        }
    }

    private void onStep(Map<String, Object> payload) {
        if (halted || sink == StepSink.NONE || !state.active() && state != RecorderState.COMPLETED) {
            return; // cancelada, expirada ou sem destino: a etapa não entra no domínio
        }
        stepsEnabled = true;
        RecordedStep step = RecordedStep.parse(payload).orElse(null);
        if (step == null) {
            LOG.warning("Etapa fora do contrato ignorada");
            return;
        }
        StepInbox.Accepted a = inbox.accept(step);
        for (RecordedStep ready : a.ready()) {
            deliver(ready);
        }
        if (a.gap()) {
            waiting = true;
            requestSync();
        } else if (!a.duplicate() && inbox.buffered() == 0) {
            waiting = false;
        }
        publish();
    }

    /** Entrega ao domínio EM ORDEM (uma de cada vez) e só então confirma. */
    private void deliver(RecordedStep step) {
        String rid = recording.recordingId();
        com.nekkora.studio.domain.AutomationId automation = recording.automation();
        SessionId session = routes.get(step.routeRef());
        com.nekkora.studio.domain.StepDraft draft = null;
        if (session != null) {
            try {
                draft = RecordedStepDrafts.draft(step);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Etapa da gravação não pôde ser montada", e);
            }
        }
        inFlight++;
        long seq = step.sequence();
        com.nekkora.studio.domain.StepDraft d = draft;
        chain = chain.thenCompose(v -> d == null ? CompletableFuture.completedFuture(false)
                : sink.deliver(automation, session, d).exceptionally(e -> false))
                .thenAccept(ok -> run(() -> delivered(rid, seq, Boolean.TRUE.equals(ok))))
                .exceptionally(e -> null);
        // STUDIO-005: as evidências que esperavam esta etapa entram no domínio DEPOIS dela (mesma fila)
        knownEvents.add(step.eventId());
        List<com.nekkora.studio.domain.evidence.ObservedEvidence> held = heldForStep.remove(step.eventId());
        if (held != null) {
            heldCount -= held.size();
            held.forEach(this::enqueueEvidence);
        }
    }

    private void delivered(String rid, long seq, boolean ok) {
        if (recording == null || !rid.equals(recording.recordingId())) {
            return;
        }
        inFlight = Math.max(0, inFlight - 1);
        delivered = Math.max(delivered, seq);
        if (ok) {
            accepted++;
        } else {
            rejected++;
            rejectedSinceAck.add(seq);
        }
        ack();
        publish();
    }

    /** ACK cumulativo: tudo até {@code delivered} foi processado pelo domínio (aceito ou recusado). */
    private void ack() {
        if (recording == null || !link.status().connected()) {
            return;
        }
        if (ackInFlight) {
            ackDirty = true;
            return;
        }
        ackInFlight = true;
        ackDirty = false;
        String rid = recording.recordingId();
        Json.Builder p = Json.obj().put("recordingId", rid).put("upToSequence", delivered);
        if (!rejectedSinceAck.isEmpty()) {
            p.put("rejected", List.copyOf(rejectedSinceAck.subList(0, Math.min(100, rejectedSinceAck.size()))));
        }
        int sent = Math.min(100, rejectedSinceAck.size());
        link.send("studio.recording.steps.ack", p.build(), options.commandTimeout()).whenComplete((r, e) -> run(() -> {
            ackInFlight = false;
            if (recording == null || !rid.equals(recording.recordingId())) {
                return;
            }
            if (e == null) {
                rejectedSinceAck.subList(0, Math.min(sent, rejectedSinceAck.size())).clear();
                Map<String, Object> st = Json.object(r, "steps");
                if (st != null) {
                    stepsState(st);
                }
            }
            if (ackDirty || (e == null && !rejectedSinceAck.isEmpty())) {
                ack();
            }
        }));
    }

    private void requestSync() {
        if (syncing || recording == null || !link.status().connected()) {
            return;
        }
        syncing = true;
        String rid = recording.recordingId();
        link.send("studio.recording.steps.sync", Map.of("recordingId", rid, "afterSequence", inbox.processed()), options.commandTimeout())
                .whenComplete((r, e) -> run(() -> syncing = false));
    }

    /** As etapas desta gravação são roteadas por sessão (steps/1 pedido): trocar de sessão exige a extensão. */
    private boolean routing() {
        return sink != StepSink.NONE && !routes.isEmpty();
    }

    /**
     * Troca de sessão lógica: rota nova, PENDENTE. O mapeamento rota → sessão é registrado já (eventos da rota nova só
     * existem se a extensão a aplicou) e os das rotas antigas são preservados (eventos em trânsito).
     */
    private void requestRoute(SessionId session) {
        pendingSession = session;
        pendingRouteRef = "route-" + (++routeCounter);
        pendingRouteOp = newOp();
        routes.put(pendingRouteRef, session);
        problem = null;
        publish();
        sendRoute();
    }

    private void sendRoute() {
        if (pendingRouteOp == null || routeInFlight || recording == null || !link.status().connected()) {
            return; // sem ligação: a reconciliação refaz o pedido quando ela voltar
        }
        routeInFlight = true;
        String rid = recording.recordingId();
        String op = pendingRouteOp;
        link.send("studio.recording.route", Map.of("recordingId", rid, "operationId", op, "routeRef", pendingRouteRef), options.commandTimeout())
                .whenComplete((p, e) -> run(() -> onRouteResult(rid, op, p, e)));
    }

    private void onRouteResult(String rid, String op, Map<String, Object> payload, Throwable error) {
        if (recording == null || !rid.equals(recording.recordingId()) || !op.equals(pendingRouteOp)) {
            return;
        }
        routeInFlight = false;
        if (error == null) {
            recording.associate(pendingSession, clock.instant()); // confirmada: só agora a associação muda
            clearPendingRoute();
            applyResult(payload);
            publish();
            nextQueuedRoute();
            return;
        }
        BridgeCommandException x = cause(error);
        if (unknownOutcome(x)) {
            // Resultado desconhecido: nem sucesso nem falha. O mesmo pedido é refeito (renovação ou religação).
            LOG.info("Troca de sessão sem resposta da extensão: reconciliando");
            publish();
            return;
        }
        clearPendingRoute();
        queuedSession = null;
        problem = new UserProblem(x.code(), "Sessão não alterada",
                "O navegador não confirmou a troca de sessão. As próximas etapas continuam na sessão anterior.");
        if (x.code() == BridgeError.RECORDING_ENDED || x.code() == BridgeError.RECORDING_NOT_FOUND || x.code() == BridgeError.INVALID_STATE
                || sensorNotConfirmed(x)) {
            recoverOrDegrade(); // a verdade vem da extensão (sem o sensor confirmado ela pode ter interrompido a captura)
            return;
        }
        publish();
    }

    private void nextQueuedRoute() {
        SessionId next = queuedSession;
        queuedSession = null;
        if (next != null && recording != null && state.active() && !recording.session().equals(next)) {
            requestRoute(next);
        }
    }

    private void clearPendingRoute() {
        pendingSession = null;
        pendingRouteRef = null;
        pendingRouteOp = null;
        routeInFlight = false;
    }

    /** Cancelamento CONFIRMADO: as etapas desta gravação saem do domínio numa única operação (depois das entregas). */
    private void discardSteps() {
        if (discarded || recording == null || sink == StepSink.NONE || !stepsEnabled && accepted == 0) {
            return;
        }
        discarded = true;
        halted = true;
        com.nekkora.studio.domain.AutomationId automation = recording.automation();
        String rid = recording.recordingId();
        chain = chain.thenCompose(v -> sink.discardRecording(automation, rid).exceptionally(e -> 0)).thenAccept(n -> run(() -> {
            if (recording != null && rid.equals(recording.recordingId())) {
                accepted = 0;
                publish();
            }
        })).exceptionally(e -> null);
    }

    // ================================================================================= evidências (STUDIO-005)

    /** Estado das evidências informado pela extensão (gravação, renovação, confirmação): perdas e lacunas. */
    private void evidenceState(Map<String, Object> st) {
        if (!Json.bool(st, "enabled", false) || evidenceSink == EvidenceSink.NONE) {
            return;
        }
        evidenceEnabled = true;
        evLost = (int) Json.number(st, "lost", evLost);
        long last = Json.number(st, "lastSequence", 0);
        evProduced = (int) Math.max(evProduced, last);
        if (evInbox.missing(last) && evInbox.highestSeen() < last && !halted) {
            evWaiting = true;
            requestEvidenceSync(); // emitidas que não chegaram aqui: reemissão em ordem
        } else if (!evInbox.missing(last)) {
            evWaiting = false;
        }
    }

    private void onEvidence(Map<String, Object> payload) {
        if (halted || evidenceSink == EvidenceSink.NONE || !state.active() && state != RecorderState.COMPLETED) {
            return; // cancelada, expirada ou sem destino: a evidência não entra no domínio
        }
        evidenceEnabled = true;
        com.nekkora.studio.domain.evidence.ObservedEvidence e = RecordedEvidence.parse(payload).orElse(null);
        if (e == null || !e.recordingId().equals(recording.recordingId())) {
            LOG.warning("Evidência fora do contrato ignorada");
            return;
        }
        evProduced = (int) Math.max(evProduced, e.sequence());
        EvidenceInbox.Accepted a = evInbox.accept(e);
        for (com.nekkora.studio.domain.evidence.ObservedEvidence ready : a.ready()) {
            routeEvidence(ready);
        }
        if (a.gap()) {
            evWaiting = true;
            requestEvidenceSync();
        } else if (!a.duplicate() && evInbox.buffered() == 0) {
            evWaiting = false;
        }
        publish();
    }

    /** A evidência de uma etapa ainda não recebida espera por ela (fila limitada); a ambígua segue direto. */
    private void routeEvidence(com.nekkora.studio.domain.evidence.ObservedEvidence e) {
        if (e.attributed() && !knownEvents.contains(e.eventId())) {
            if (heldCount >= MAX_HELD_EVIDENCE) {
                evidenceDone(recording.recordingId(), e, false); // limite: recusada e declarada (nunca uma etapa fictícia)
                return;
            }
            heldForStep.computeIfAbsent(e.eventId(), k -> new ArrayList<>()).add(e);
            heldCount++;
            return;
        }
        enqueueEvidence(e);
    }

    private void enqueueEvidence(com.nekkora.studio.domain.evidence.ObservedEvidence e) {
        String rid = recording.recordingId();
        com.nekkora.studio.domain.AutomationId automation = recording.automation();
        evInFlight++;
        chain = chain.thenCompose(v -> evidenceSink.deliver(automation, e).exceptionally(x -> false))
                .thenAccept(ok -> run(() -> {
                    evInFlight = Math.max(0, evInFlight - 1);
                    evidenceDone(rid, e, Boolean.TRUE.equals(ok));
                }))
                .exceptionally(x -> null);
    }

    private void evidenceDone(String rid, com.nekkora.studio.domain.evidence.ObservedEvidence e, boolean ok) {
        if (recording == null || !rid.equals(recording.recordingId())) {
            return;
        }
        if (ok) {
            evAccepted++;
            if (!e.attributed()) {
                evUnattributed++;
            }
            if (e.type() == com.nekkora.studio.domain.evidence.EvidenceType.OBSERVATION_INCOMPLETE) {
                evIncomplete++;
            }
            evLast = evLast == null || e.observedAt().isAfter(evLast) ? e.observedAt() : evLast;
        } else {
            evRejected++;
            evRejectedSinceAck.add(e.sequence());
        }
        evInbox.concluded(e.sequence());
        ackEvidence();
        publish();
    }

    /** ACK cumulativo das evidências: tudo até a maior sequência CONTÍGUA concluída no domínio (aceita ou recusada). */
    private void ackEvidence() {
        if (recording == null || !link.status().connected()) {
            return;
        }
        if (evAckInFlight) {
            evAckDirty = true;
            return;
        }
        evAckInFlight = true;
        evAckDirty = false;
        String rid = recording.recordingId();
        long upTo = evInbox.acked();
        List<Long> rej = evRejectedSinceAck.stream().filter(s -> s <= upTo).limit(100).toList();
        Json.Builder p = Json.obj().put("recordingId", rid).put("upToSequence", upTo);
        if (!rej.isEmpty()) {
            p.put("rejected", rej);
        }
        link.send("studio.recording.evidence.ack", p.build(), options.commandTimeout()).whenComplete((r, x) -> run(() -> {
            evAckInFlight = false;
            if (recording == null || !rid.equals(recording.recordingId())) {
                return;
            }
            if (x == null) {
                evRejectedSinceAck.removeAll(rej);
                Map<String, Object> st = Json.object(r, "evidence");
                if (st != null) {
                    evidenceState(st);
                }
            }
            if (evAckDirty || (x == null && evRejectedSinceAck.stream().anyMatch(s -> s <= evInbox.acked()))) {
                ackEvidence();
            }
        }));
    }

    private void requestEvidenceSync() {
        if (evSyncing || recording == null || !link.status().connected()) {
            return;
        }
        evSyncing = true;
        String rid = recording.recordingId();
        link.send("studio.recording.evidence.sync", Map.of("recordingId", rid, "afterSequence", evInbox.released()), options.commandTimeout())
                .whenComplete((r, e) -> run(() -> evSyncing = false));
    }

    /** Aguarda as entregas em andamento (usado em testes e na saída do Studio). */
    CompletableFuture<Void> deliveries() {
        CompletableFuture<Void> out = new CompletableFuture<>();
        run(() -> chain.whenComplete((v, e) -> out.complete(null)));
        return out;
    }

    // ================================================================================= concessão

    private void startRenew() {
        stopRenew();
        long every = options.renewEvery().toMillis();
        renewTask = exec.scheduleWithFixedDelay(() -> {
            if (recording == null || !state.active() || linkDown || !link.status().connected()) {
                return;
            }
            String rid = recording.recordingId();
            link.send("studio.recording.renew", Map.of("recordingId", rid), options.commandTimeout()).whenComplete((p, e) -> run(() -> {
                if (recording == null || !rid.equals(recording.recordingId())) {
                    return;
                }
                if (e != null) {
                    BridgeCommandException x = cause(e);
                    // RECORDING_ENDED: concessão vencida recusada pela extensão (prazo absoluto); a verdade vem da consulta
                    if (x.code() == BridgeError.RECORDING_NOT_FOUND || x.code() == BridgeError.NOT_CONTROLLER
                            || x.code() == BridgeError.RECORDING_ENDED && pendingOp == null) {
                        recoverOrDegrade();
                    }
                    return;
                }
                Map<String, Object> steps = Json.object(p, "steps");
                if (steps != null) {
                    stepsState(steps);
                    publish();
                }
                Map<String, Object> evs = Json.object(p, "evidence");
                if (evs != null) {
                    evidenceState(evs);
                    publish();
                }
                sendRoute(); // troca pendente com resultado desconhecido: reconcilia enquanto a extensão responde
                String st = Json.string(p, "state");
                if (st != null && !st.equals("RECORDING") && !st.equals("PAUSED") && !st.equals("DEGRADED") && state.active() && pendingOp == null) {
                    recoverOrDegrade(); // terminou do lado do navegador (ex.: expirou): a verdade vem da consulta
                }
            }));
        }, every, every, TimeUnit.MILLISECONDS);
    }

    private void stopRenew() {
        if (renewTask != null) {
            renewTask.cancel(false);
            renewTask = null;
        }
    }

    // ================================================================================= apoio

    private void move(RecorderState to) {
        if (to == state) {
            return;
        }
        if (RecorderTransitions.allowed(state, to)) {
            state = to;
        } else if (RecorderTransitions.allowed(state, RecorderState.RECOVERING) && RecorderTransitions.allowed(RecorderState.RECOVERING, to)) {
            state = to; // reconciliação: a verdade da extensão passa pela recuperação
        } else {
            LOG.warning("Transição ignorada: " + state + " → " + to);
        }
    }

    private void clearRecording() {
        clearRecordingKeepProblem();
        problem = null;
    }

    private void clearRecordingKeepProblem() {
        stopRenew();
        resetSteps();
        recording = null;
        mode = null;
        pendingMode = null;
        tabTitle = null;
        tabHost = null;
        progress = RecordingProgress.NONE;
        reason = null;
        summary = state.terminal() ? summary : null;
        lastApplied = null;
        pendingOp = null;
        if (state == RecorderState.IDLE) {
            summary = null;
        }
    }

    /** A extensão não obteve a confirmação {active, epoch, mode} do sensor da página (nada foi dado como feito). */
    private static boolean sensorNotConfirmed(BridgeCommandException x) {
        return x.code() == BridgeError.CAPTURE_FAILED && "SENSOR_NOT_CONFIRMED".equals(x.reason());
    }

    private static boolean unknownOutcome(BridgeCommandException x) {
        return x.code() == BridgeError.LINK_LOST || x.code() == BridgeError.TIMEOUT || x.code() == BridgeError.EXTENSION_UNAVAILABLE;
    }

    private static BridgeCommandException cause(Throwable e) {
        Throwable t = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
        return t instanceof BridgeCommandException b ? b
                : new BridgeCommandException(BridgeError.INTERNAL_ERROR, "Falha inesperada: " + t.getClass().getSimpleName(), null, false);
    }

    private static UserProblem problemFor(BridgeCommandException x) {
        String message = safeMessage(x.getMessage());
        return switch (x.code()) {
            case ORIGIN_NOT_ALLOWED -> new UserProblem(x.code(), "Site não autorizado", "PERMISSION_REQUIRED".equals(x.reason())
                    ? "Este site ainda não está autorizado. Abra a Nekkora Extension no Chrome e conceda acesso ao site." : message);
            case TAB_NOT_FOUND -> new UserProblem(x.code(), "Aba não encontrada", message);
            case CAPTURE_FAILED -> new UserProblem(x.code(), "Captura técnica não iniciada", message);
            case RECORDING_ALREADY_ACTIVE -> new UserProblem(x.code(), "Aba já em gravação", message);
            case EXTENSION_UNAVAILABLE -> new UserProblem(x.code(), "Navegador não conectado",
                    "Abra o Chrome e ligue “Conectar ao Nekkora Studio” no painel da Nekkora Extension.");
            case TIMEOUT, LINK_LOST -> new UserProblem(x.code(), "Sem confirmação do navegador", "A operação não foi confirmada pela extensão.");
            case RECORDING_ENDED -> new UserProblem(x.code(), "Gravação já encerrada", message);
            default -> new UserProblem(x.code(), "Operação recusada pelo navegador", message);
        };
    }

    /** Mensagens vindas da extensão passam pela barreira de apresentação (sem identificadores internos). */
    private static String safeMessage(String s) {
        if (s == null || s.isBlank()) {
            return "A extensão recusou a operação.";
        }
        return DisplaySafety.isSafeToDisplay(s) ? s : "A extensão recusou a operação.";
    }

    private static String safe(String s) {
        return s == null || DisplaySafety.isSafeToDisplay(s) ? s : null;
    }

    /** Título de aba (CHROME-SECURITY-001, D4): só se for do vocabulário controlado; senão null (a aba é identificada pelo host). */
    private static String tabTitle(String s) {
        return s == null ? null : com.nekkora.studio.domain.privacy.CapturePrivacy.title(s);
    }

    private static Instant parse(String s) {
        try {
            return s == null ? null : Instant.parse(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String newOp() {
        return "op-" + UUID.randomUUID();
    }

    private RecorderSnapshot build() {
        return new RecorderSnapshot(state, mode, pendingMode, recording == null ? null : recording.automation(),
                recording == null ? null : recording.session(), tabTitle, tabHost, progress, reason, problem, summary, linkStatus,
                stepsEnabled ? new StepProgress(true, accepted, inbox.buffered() + inFlight, rejected, lost, waiting) : StepProgress.NONE,
                pendingSession,
                evidenceEnabled ? new EvidenceProgress(true, evProduced, evAccepted, evUnattributed, evIncomplete, evInbox.buffered() + heldCount + evInFlight,
                        evRejected, evLost, evWaiting, evLast) : EvidenceProgress.NONE);
    }

    private void publish() {
        RecorderSnapshot s = build();
        snapshot = s;
        for (Consumer<RecorderSnapshot> l : listeners) {
            try {
                l.accept(s);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Ouvinte do Recorder falhou", e);
            }
        }
    }

    private void run(Runnable r) {
        try {
            exec.execute(() -> {
                try {
                    r.run();
                } catch (RuntimeException e) {
                    LOG.log(Level.SEVERE, "Falha no Recorder", e);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // encerrado
        }
    }
}
