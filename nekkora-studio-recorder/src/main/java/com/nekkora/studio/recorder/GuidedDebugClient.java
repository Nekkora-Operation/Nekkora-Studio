package com.nekkora.studio.recorder;

import com.nekkora.studio.bridge.BridgeEvent;
import com.nekkora.studio.bridge.BridgeProtocol;
import com.nekkora.studio.bridge.BrowserLink;
import com.nekkora.studio.bridge.LinkStatus;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.privacy.CapturePrivacy;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * STUDIO-009 (guided-debug/1): cliente do debug guiado — o Studio PEDE e a extensão inspeciona e destaca (D-S009-02).
 * Nada aqui decide identidade: o resultado vem do motor da extensão em códigos fechados e é só lido.
 *
 * <p>Uma instância = uma sessão de debug numa aba autorizada. A concessão é renovada automaticamente enquanto a sessão
 * vive; os eventos do navegador ({@code studio.debug.picked}, {@code studio.debug.changed}) chegam ao {@link Listener} em
 * threads do Bridge (quem atualiza Swing repassa para a EDT). O que vai para a extensão é o {@code targetId} da etapa e o
 * descritor recuperável já persistido (nunca valores); o que volta passa pela 4ª barreira de privacidade do Studio.
 */
public final class GuidedDebugClient implements BrowserLink.Listener, AutoCloseable {

    /** Resultado da inspeção (mesmos valores do contrato). Só {@link #RESOLVED} comprova a identidade. */
    public enum Status { RESOLVED, NOT_FOUND, AMBIGUOUS, IDENTITY_MISMATCH, STALE_TARGET, SCOPE_UNAVAILABLE, INSUFFICIENT_EVIDENCE, CONTEXT_UNAVAILABLE }

    /** Resultado de cada verificação situada. */
    public enum Check { PASS, FAIL, UNKNOWN, NOT_APPLICABLE }

    /** Fonte da identidade usada pelo motor. */
    public enum IdentitySource { TARGET_ID, DESCRIPTOR }

    public static final List<String> CHECKS = List.of("identity", "unique", "visible", "enabled", "editable", "kindMatchesAction", "frameReachable",
            "shadowReachable");

    /** Estado da sessão como a extensão o informa. */
    public record Session(String debugId, String state, String tabRef, int pageEpoch, boolean picking, String reason) {
        public boolean ended() {
            return "ENDED".equals(state);
        }
    }

    /** Caixa do destaque (px CSS do viewport) e posição aproximada na tela. */
    public record Box(int x, int y, int width, int height, int viewportWidth, int viewportHeight, double devicePixelRatio, int screenX, int screenY) {}

    /** Candidato oferecido para exame (nunca escolhido pelo Studio). */
    public record Candidate(String candidateRef, String verdict, double score, String kind, String name, boolean chosen) {}

    /** Resumo do elemento observado nesta verificação. */
    public record Observed(String kind, String name, String tag, boolean sensitive) {}

    /** Resultado de uma inspeção — situado: vale para {@link #pageEpoch()} e {@link #observedAt()}. */
    public record Inspection(String requestId, String stepRef, Instant observedAt, int pageEpoch, IdentitySource identitySource, Status status, double score,
                             List<String> reasons, Map<String, Check> checks, Observed element, List<Candidate> candidates, boolean highlighted, Box box,
                             String continuity) {
        public Inspection {
            reasons = List.copyOf(reasons);
            checks = Map.copyOf(checks);
            candidates = List.copyOf(candidates);
        }

        public Check check(String name) {
            return checks.getOrDefault(name, Check.UNKNOWN);
        }
    }

    /** Elemento escolhido no navegador (ou candidato adotado). A ação do elemento NÃO foi efetuada. */
    public record Picked(String requestId, int pageEpoch, Instant observedAt, String origin, ElementReference element, Map<String, Check> checks, Box box,
                         List<String> notes) {
        public Picked {
            checks = Map.copyOf(checks);
            notes = List.copyOf(notes);
        }
    }

    /** G6: operações da sonda de laboratório (só origem LAB; o valor vem do catálogo sintético da extensão). */
    public enum ProbeOperation { FILL_FIXTURE, CLICK, PRESS_ENTER }

    /** Chaves do catálogo sintético da sonda (o valor nunca trafega nem aparece no Studio). */
    public static final List<String> LAB_FIXTURE_KEYS = List.of("lab:registro-ativo", "lab:registro-suspenso", "lab:registro-encerrado",
            "lab:registro-inexistente");

    /**
     * Resultado da sonda — fatos separados: alvo reencontrado ({@link #status}) ≠ operação efetuada ({@link #performed}) ≠
     * mudança observada ({@link #domChanged}/{@link #navigated}). Nenhum campo diz "sucesso de negócio".
     */
    public record ProbeResult(String requestId, Instant observedAt, int pageEpoch, Status status, boolean performed, ProbeOperation operation,
                              List<String> reasons, boolean domChanged, boolean navigated, boolean duplicate) {
        public ProbeResult {
            reasons = List.copyOf(reasons);
        }
    }

    /** Eventos do navegador para esta sessão. */
    public interface Listener {
        void picked(Picked picked);

        void changed(Session session);
    }

    private static final Pattern TAG = Pattern.compile("[a-z][a-z0-9-]{0,40}");
    private static final Set<String> KINDS_OF_ACTION = Set.of("CLICK", "FILL", "SELECT_OPTION", "CHECK", "UNCHECK", "PRESS_KEY", "WAIT_FOR", "OBSERVE",
            "UNKNOWN");

    private final BrowserLink link;
    private final String debugId;
    private final Duration lease;
    private final Duration timeout;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong requests = new AtomicLong();
    private final ScheduledExecutorService renewals = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "nekkora-guided-debug-lease");
        t.setDaemon(true);
        return t;
    });
    private volatile Session session;
    private volatile ScheduledFuture<?> renewal;

    public GuidedDebugClient(BrowserLink link, Duration lease, Duration timeout) {
        this.link = Objects.requireNonNull(link);
        this.lease = Objects.requireNonNull(lease);
        this.timeout = Objects.requireNonNull(timeout);
        this.debugId = "dbg-" + UUID.randomUUID();
        link.addListener(this);
    }

    public String debugId() {
        return debugId;
    }

    public Session session() {
        return session;
    }

    public void addListener(Listener l) {
        listeners.add(Objects.requireNonNull(l));
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** Abre a sessão na aba (tabRef opaco, da lista de abas do Recorder). Extensão antiga: UNKNOWN_COMMAND. */
    public CompletableFuture<Session> start(String tabRef) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("debugId", debugId);
        p.put("operationId", "op-" + UUID.randomUUID());
        p.put("tabRef", tabRef);
        p.put("leaseMs", lease.toMillis());
        p.put("capabilities", List.of(BridgeProtocol.CAPABILITY_GUIDED_DEBUG));
        return link.send("studio.debug.start", p, timeout).thenApply(r -> {
            Session s = update(Json.object(r, "debug"));
            long every = Math.max(1000, lease.toMillis() / 3);
            renewal = renewals.scheduleAtFixedRate(this::renew, every, every, TimeUnit.MILLISECONDS);
            return s;
        });
    }

    private void renew() {
        Session s = session;
        if (s == null || s.ended()) {
            return;
        }
        link.send("studio.debug.renew", Map.of("debugId", debugId), timeout).thenAccept(r -> update(Json.object(r, "debug")));
    }

    /**
     * Inspeciona o alvo de uma etapa: {@code targetId} registrado (se houver) e o descritor recuperável da referência
     * EFETIVA. Somente leitura — nada acontece na página além do destaque (quando pedido).
     */
    public CompletableFuture<Inspection> inspect(StepId step, ActionType action, ElementReference target, boolean highlight) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("debugId", debugId);
        p.put("requestId", nextRequest());
        p.put("stepRef", step.value().toString());
        p.put("actionType", KINDS_OF_ACTION.contains(action.name()) ? action.name() : "UNKNOWN");
        p.put("targetId", target.correlation().get(CorrelationKeys.Key.TARGET_ID).orElse(null));
        p.put("descriptor", descriptor(target));
        p.put("highlight", highlight);
        return link.send("studio.debug.inspect", p, timeout).thenApply(this::inspection);
    }

    /**
     * G6: sonda de laboratório sobre o alvo da etapa. {@code requestId} novo a cada chamada (repetir a MESMA operação exige
     * um pedido explícito novo); a extensão recusa fora de origem LAB ({@code PROBE_NOT_ALLOWED}).
     */
    public CompletableFuture<ProbeResult> probe(StepId step, ProbeOperation operation, ElementReference target, String valueKey) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("debugId", debugId);
        p.put("requestId", nextRequest());
        p.put("stepRef", step.value().toString());
        p.put("operation", operation.name());
        p.put("targetId", target.correlation().get(CorrelationKeys.Key.TARGET_ID).orElse(null));
        p.put("descriptor", descriptor(target));
        p.put("valueKey", operation == ProbeOperation.FILL_FIXTURE ? valueKey : null);
        p.put("authorization", "LAB_PROBE");
        return link.send("studio.debug.probe", p, timeout).thenApply(r -> {
            update(Json.object(r, "debug"));
            List<String> reasons = new ArrayList<>();
            for (Object o : Json.array(r, "reasons")) {
                if (o instanceof String s) {
                    reasons.add(s);
                }
            }
            Map<String, Object> after = Json.object(r, "after");
            return new ProbeResult(Json.string(r, "requestId"), instant(Json.string(r, "observedAt")), (int) Json.number(r, "pageEpoch", 0),
                    enumOr(Status.class, Json.string(r, "status"), Status.CONTEXT_UNAVAILABLE), Json.bool(r, "performed", false),
                    enumOr(ProbeOperation.class, Json.string(r, "operation"), operation), reasons, after != null && Json.bool(after, "domChanged", false),
                    after != null && Json.bool(after, "navigated", false), Json.bool(r, "duplicate", false));
        });
    }

    /** Destaca um candidato de uma inspeção da época atual (null limpa). */
    public CompletableFuture<Boolean> highlight(String candidateRef) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("debugId", debugId);
        p.put("requestId", nextRequest());
        p.put("candidateRef", candidateRef);
        return link.send("studio.debug.highlight", p, timeout).thenApply(r -> {
            update(Json.object(r, "debug"));
            return Json.bool(r, "highlighted", false);
        });
    }

    /** Arma a seleção no navegador; o elemento escolhido chega em {@link Listener#picked}. Devolve o requestId. */
    public CompletableFuture<String> pickStart(ActionType action) {
        return pick("START", null, action);
    }

    public CompletableFuture<String> pickCancel() {
        return pick("CANCEL", null, ActionType.UNKNOWN);
    }

    /** Descreve um candidato examinado como referência nova (resultado em {@link Listener#picked}). */
    public CompletableFuture<String> adopt(String candidateRef, ActionType action) {
        return pick("ADOPT_CANDIDATE", Objects.requireNonNull(candidateRef), action);
    }

    private CompletableFuture<String> pick(String what, String candidateRef, ActionType action) {
        String requestId = nextRequest();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("debugId", debugId);
        p.put("requestId", requestId);
        p.put("action", what);
        p.put("candidateRef", candidateRef);
        p.put("actionType", KINDS_OF_ACTION.contains(action.name()) ? action.name() : "UNKNOWN");
        return link.send("studio.debug.pick", p, timeout).thenApply(r -> {
            update(Json.object(r, "debug"));
            return requestId;
        });
    }

    /** Encerra: destaque limpo, seleção desarmada, consumidor liberado. */
    public CompletableFuture<Session> finish() {
        cancelRenewal();
        Session s = session;
        if (s == null) {
            return CompletableFuture.completedFuture(null);
        }
        return link.send("studio.debug.finish", Map.of("debugId", debugId, "operationId", "op-" + UUID.randomUUID()), timeout)
                .thenApply(r -> update(Json.object(r, "debug")));
    }

    /**
     * G8: encerra explicitamente uma sessão que ESTE Studio perdeu com a queda do canal (o fim não pôde ser entregue). A
     * extensão mantém "uma sessão por aba": sem isso, a aba ficaria ocupada até a concessão vencer. Melhor esforço —
     * sessão já encerrada ou desconhecida é ignorada.
     */
    public static CompletableFuture<Void> finishOrphan(BrowserLink link, String debugId, Duration timeout) {
        return link.send("studio.debug.finish", Map.of("debugId", debugId, "operationId", "op-" + UUID.randomUUID()), timeout).handle((r, e) -> null);
    }

    @Override
    public void close() {
        if (session != null && !session.ended()) {
            finish();
        }
        cancelRenewal();
        renewals.shutdownNow();
        link.removeListener(this);
    }

    private void cancelRenewal() {
        ScheduledFuture<?> r = renewal;
        if (r != null) {
            r.cancel(false);
        }
    }

    private String nextRequest() {
        return "rq-" + debugId.substring(4, 12) + "-" + requests.incrementAndGet();
    }

    // ------------------------------------------------------------------------------------------- Bridge

    @Override
    public void statusChanged(LinkStatus status) {
        // G8: canal perdido = sessão encerrada DESTE lado (nenhuma verificação anterior vale mais; a sessão da extensão
        // expira pela concessão ou é substituída quando este Studio abrir outra na mesma aba)
        Session s = session;
        if (status.connected() || s == null || s.ended()) return;
        Session lost = new Session(s.debugId(), "ENDED", s.tabRef(), s.pageEpoch(), false, LINK_LOST);
        session = lost;
        listeners.forEach(l -> l.changed(lost));
    }

    /** Motivo local (não vem da extensão): o canal com o navegador caiu durante a sessão. */
    public static final String LINK_LOST = "LINK_LOST";

    @Override
    public void event(BridgeEvent event) {
        Map<String, Object> p = event.payload();
        if ("studio.debug.changed".equals(event.type())) {
            Map<String, Object> d = Json.object(p, "debug");
            if (d != null && debugId.equals(Json.string(d, "debugId"))) {
                Session s = update(d);
                if (s.ended()) {
                    cancelRenewal();
                }
                listeners.forEach(l -> l.changed(s));
            }
        } else if ("studio.debug.picked".equals(event.type()) && debugId.equals(Json.string(p, "debugId"))) {
            Map<String, Object> e = Json.object(p, "element");
            if (e == null) {
                return;
            }
            List<String> notes = new ArrayList<>();
            for (Object o : Json.array(p, "notes")) {
                if (o instanceof String s) {
                    notes.add(s);
                }
            }
            Picked picked = new Picked(Json.string(p, "requestId"), (int) Json.number(p, "pageEpoch", 0), instant(Json.string(p, "observedAt")),
                    Json.string(p, "origin"), RecordedStepDrafts.selectedElement(e, notes), checks(Json.object(p, "checks")), box(Json.object(p, "box")), notes);
            listeners.forEach(l -> l.picked(picked));
        }
    }

    // ------------------------------------------------------------------------------------------- leitura

    private Session update(Map<String, Object> d) {
        if (d == null) {
            return session;
        }
        Map<String, Object> tab = Json.object(d, "tab");
        Session s = new Session(Json.string(d, "debugId"), Json.string(d, "state"), tab == null ? null : Json.string(tab, "tabRef"),
                (int) Json.number(d, "pageEpoch", 0), Json.bool(d, "picking", false), Json.string(d, "reason"));
        session = s;
        return s;
    }

    private Inspection inspection(Map<String, Object> r) {
        update(Json.object(r, "debug"));
        List<String> reasons = new ArrayList<>();
        for (Object o : Json.array(r, "reasons")) {
            if (o instanceof String s) {
                reasons.add(s);
            }
        }
        List<Candidate> candidates = new ArrayList<>();
        for (Object o : Json.array(r, "candidates")) {
            if (o instanceof Map<?, ?> raw) {
                @SuppressWarnings("unchecked")
                Map<String, Object> c = (Map<String, Object>) raw;
                candidates.add(new Candidate(Json.string(c, "candidateRef"), Json.string(c, "verdict"), number(c, "score"), Json.string(c, "kind"),
                        interfaceName(Json.string(c, "name")), Json.bool(c, "chosen", false)));
            }
        }
        Map<String, Object> e = Json.object(r, "element");
        Observed observed = e == null ? null : new Observed(Json.string(e, "kind"), interfaceName(Json.string(e, "name")),
                safeTag(Json.string(e, "tag")), Json.bool(e, "sensitive", false));
        return new Inspection(Json.string(r, "requestId"), Json.string(r, "stepRef"), instant(Json.string(r, "observedAt")), (int) Json.number(r, "pageEpoch", 0),
                enumOr(IdentitySource.class, Json.string(r, "identitySource"), IdentitySource.DESCRIPTOR),
                enumOr(Status.class, Json.string(r, "status"), Status.CONTEXT_UNAVAILABLE), number(r, "score"), reasons, checks(Json.object(r, "checks")),
                observed, candidates, Json.bool(r, "highlighted", false), box(Json.object(r, "box")), Json.string(r, "continuity"));
    }

    private static Map<String, Check> checks(Map<String, Object> c) {
        Map<String, Check> out = new LinkedHashMap<>();
        for (String k : CHECKS) {
            out.put(k, c == null ? Check.UNKNOWN : enumOr(Check.class, Json.string(c, k), Check.UNKNOWN));
        }
        return out;
    }

    private static Box box(Map<String, Object> b) {
        if (b == null) {
            return null;
        }
        return new Box((int) Json.number(b, "x", 0), (int) Json.number(b, "y", 0), (int) Json.number(b, "width", 0), (int) Json.number(b, "height", 0),
                (int) Json.number(b, "viewportWidth", 0), (int) Json.number(b, "viewportHeight", 0), number(b, "devicePixelRatio"),
                (int) Json.number(b, "screenX", 0), (int) Json.number(b, "screenY", 0));
    }

    private static double number(Map<String, Object> m, String field) {
        Object v = m.get(field);
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    private static Instant instant(String s) {
        try {
            return s == null ? null : Instant.parse(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 4ª barreira: nome só se for inteiro do vocabulário (como as etapas gravadas). */
    private static String interfaceName(String s) {
        String t = s == null ? null : CapturePrivacy.interfaceText(s);
        return t == null ? "" : t;
    }

    private static String safeTag(String t) {
        String safe = t == null ? null : CapturePrivacy.tagName(t);
        return safe == null ? "unknown" : safe;
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback) {
        try {
            return name == null ? fallback : Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------------------------------- descritor

    /**
     * Descritor recuperável (D-S009-03): o que o {@code .nekkora} já guarda da referência efetiva, na forma do contrato. Só
     * campos que já são ponto fixo da {@code nekkora-privacy/2}; nada é inventado para completar.
     */
    static Map<String, Object> descriptor(ElementReference e) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("kind", e.semanticKind().matches("[A-Z_]{1,40}") ? e.semanticKind() : ElementReference.UNKNOWN_KIND);
        d.put("tag", TAG.matcher(e.tag()).matches() ? e.tag() : "unknown");
        String name = e.accessibleName();
        d.put("name", name.length() <= 120 && !e.sensitive() && DisplaySafety.isSafeToDisplay(name) ? name : "");
        d.put("htmlId", e.sensitive() ? null : e.htmlId().filter(v -> v.length() <= 120).orElse(null));
        d.put("htmlName", e.sensitive() ? null : e.htmlName().filter(v -> v.length() <= 120).orElse(null));
        List<Object> locators = new ArrayList<>();
        for (LocatorCandidate l : e.locators()) {
            if (locators.size() >= 5) {
                break;
            }
            String x = l.expression();
            boolean xpath = x.startsWith("/") || x.startsWith("(");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("strategy", l.strategy().name());
            m.put("css", !xpath && !x.isEmpty() && x.length() <= 500 ? x : null);
            m.put("xpath", xpath && x.length() <= 500 ? x : null);
            m.put("description", l.description().length() <= 200 ? l.description() : "");
            locators.add(m);
        }
        d.put("locators", locators);
        List<Object> path = new ArrayList<>();
        for (AccessStep s : e.accessPath()) {
            if (path.size() >= 12) {
                break;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", s.type().name());
            m.put("locator", s.locator().isEmpty() || s.locator().length() > 500 ? null : s.locator());
            path.add(m);
        }
        d.put("accessPath", path);
        return d;
    }
}
