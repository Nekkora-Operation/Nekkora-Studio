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
 *   <li><b>Domínio intacto</b>: o Recorder NÃO cria etapas — a {@code StepIntake} fica para a STUDIO-003.</li>
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

    public RecorderController(BrowserLink link, Clock clock, Options options) {
        this.link = Objects.requireNonNull(link);
        this.clock = Objects.requireNonNull(clock);
        this.options = Objects.requireNonNull(options);
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
                    out.add(new BrowserTab(Json.string(t, "tabRef"), safe(Json.string(t, "title")), safe(Json.string(t, "host")),
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

    /** Associa a gravação a outra sessão lógica da MESMA automação (sem nova captura). */
    public void associate(SessionId session) {
        Objects.requireNonNull(session);
        run(() -> {
            if (recording != null && state.active() && recording.associate(session, clock.instant())) {
                publish();
            }
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
        Map<String, Object> payload = Json.obj().put("recordingId", recording.recordingId()).put("operationId", op).put("tabRef", tab.tabRef())
                .put("mode", requested.wire()).put("leaseMs", options.lease().toMillis()).build();
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
            pendingMode = null;
            problem = problemFor(x);
            move(RecorderState.IDLE);
            clearRecordingKeepProblem();
            publish();
        }));
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
            degradeForLink();
            return;
        }
        publish();
    }

    private void onEvent(BridgeEvent event) {
        if (recording == null || !recording.recordingId().equals(Json.string(event.payload(), "recordingId"))) {
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
        String title = safe(Json.string(tab, "title"));
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
        move(target);
    }

    private static UserProblem degradedProblem(String reason) {
        String why = switch (String.valueOf(reason)) {
            case "TAB_CLOSED" -> "A aba da gravação foi fechada.";
            case "PERMISSION_REVOKED" -> "A autorização do site foi revogada na extensão. A captura foi encerrada.";
            case "ORIGIN_NOT_ALLOWED" -> "A aba saiu para uma página que não está autorizada.";
            case "CAPTURE_ERROR" -> "A captura técnica da página entrou em erro.";
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
        return new RecordingSummary(outcome, Duration.ofMillis(Json.number(sum, "durationMs", 0)), progressOf(wrapper), lim,
                Json.bool(sum, "pageMapExported", false));
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
                recording == null ? null : recording.session(), tabTitle, tabHost, progress, reason, problem, summary, linkStatus);
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
