package com.nekkora.studio.guided;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.target.DecisionActor;
import com.nekkora.studio.domain.target.TargetDecision;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * STUDIO-009 G7 — registro de uma passagem do debug guiado ({@code nekkora-studio/guided-debug-record/1.0.0}): contexto
 * SOMENTE LEITURA para quem interpreta (desenvolvedor, operador de teste, geração assistida). Liga cada verificação e
 * decisão à etapa ({@link StepId}) e à revisão do plano; tudo em códigos — nenhum valor, nome, texto da página ou frase
 * do resolvedor. Distingue sempre o ator ({@code DEVELOPER} × {@code TEST_AGENT}): o registro nunca diz "confirmado por
 * humano" nem "verificado por IA".
 */
public final class GuidedDebugRecord {
    public static final String SCHEMA = "nekkora-studio/guided-debug-record";
    public static final String VERSION = "1.0.0";
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,59}");

    /** Tipo de passagem. */
    public enum Kind { VERIFICATION, DECISION, CORRECTION, PROBE }

    /** Uma passagem: verificação, decisão, correção aplicada ou sonda — só códigos. */
    public record Entry(StepId step, Instant at, Kind kind, String status, String identitySource, List<String> reasons, String outcome,
                        List<String> outcomeReasons, DecisionActor actor) {
        public Entry {
            Objects.requireNonNull(step);
            Objects.requireNonNull(at);
            Objects.requireNonNull(kind);
            Objects.requireNonNull(actor);
            status = code(status);
            identitySource = identitySource == null || identitySource.isEmpty() ? "" : code(identitySource);
            reasons = reasons == null ? List.of() : reasons.stream().filter(r -> r != null && CODE.matcher(r).matches()).distinct().limit(16).toList();
            outcome = outcome == null || outcome.isEmpty() ? "" : code(outcome);
            outcomeReasons = outcomeReasons == null ? List.of() : outcomeReasons.stream().filter(r -> r != null && CODE.matcher(r).matches()).distinct()
                    .limit(16).toList();
        }
    }

    private static String code(String c) {
        if (c == null || !CODE.matcher(c).matches()) throw new IllegalArgumentException("código inválido");
        return c;
    }

    private GuidedDebugRecord() {}

    /**
     * JSON canônico do registro. {@code plan} identifica a revisão do plano depurado; {@code a} fornece o estado FINAL das
     * decisões (que pode ter sido desfeito depois de uma passagem — o registro mostra os dois).
     */
    public static String toJson(GuidedPlan plan, Automation a, DecisionActor operator, Instant exportedAt, List<Entry> entries) {
        Objects.requireNonNull(plan);
        Objects.requireNonNull(a);
        if (!plan.automationId().equals(a.id())) throw new IllegalArgumentException("plano de outra automação");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema", SCHEMA);
        root.put("version", VERSION);
        root.put("automationId", a.id().value().toString());
        root.put("planSchema", GuidedPlan.SCHEMA + "/" + GuidedPlan.VERSION);
        root.put("planRevision", plan.planRevision());
        root.put("operator", operator.name());
        root.put("exportedAt", exportedAt.toString());
        root.put("meaning", "NO_EXECUTION_NO_BUSINESS_OUTCOME");
        List<Object> list = new ArrayList<>();
        for (Entry e : entries) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stepId", e.step().value().toString());
            m.put("at", e.at().toString());
            m.put("kind", e.kind().name());
            m.put("status", e.status());
            m.put("identitySource", e.identitySource());
            m.put("reasons", new ArrayList<Object>(e.reasons()));
            m.put("outcome", e.outcome());
            m.put("outcomeReasons", new ArrayList<Object>(e.outcomeReasons()));
            m.put("actor", e.actor().name());
            list.add(m);
        }
        root.put("entries", list);
        List<Object> decisions = new ArrayList<>();
        int confirmed = 0;
        int rejected = 0;
        int undecided = 0;
        for (GuidedPlan.Step s : plan.steps()) {
            if (s.applicability() == GuidedPlan.Applicability.NO_TARGET_REQUIRED) continue;
            AutomationStep st = a.step(s.id()).orElse(null);
            TargetDecision d = st == null ? null : st.targetDecision().orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stepId", s.id().value().toString());
            m.put("decision", d == null ? null : d.kind().name());
            m.put("actor", d == null ? null : d.actor().name());
            m.put("basisStatus", d == null ? null : d.basis().status());
            m.put("targetReplaced", st != null && st.replacementTarget().isPresent());
            decisions.add(m);
            if (d == null) undecided++;
            else if (d.confirmed()) confirmed++;
            else rejected++;
        }
        root.put("finalDecisions", decisions);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("entries", entries.size());
        summary.put("confirmed", confirmed);
        summary.put("rejected", rejected);
        summary.put("undecided", undecided);
        summary.put("reused", entries.stream().filter(e -> "REUSE".equals(e.outcome())).count());
        summary.put("probesPerformed", entries.stream().filter(e -> e.kind() == Kind.PROBE && "PERFORMED".equals(e.outcome())).count());
        root.put("summary", summary);
        root.put("exclusions", List.of("VALUES", "NAMES_AND_DESCRIPTIONS", "PAGE_TEXT", "RESOLVER_STATEMENTS", "CORRELATION_KEYS", "BUSINESS_OUTCOME"));
        StringBuilder b = new StringBuilder();
        Write.value(b, root, 0);
        return b.append('\n').toString();
    }

    /** Escrita canônica (a mesma forma do plano). */
    static final class Write {
        private Write() {}

        @SuppressWarnings("unchecked")
        static void value(StringBuilder b, Object v, int level) {
            if (v == null) {
                b.append("null");
            } else if (v instanceof String s) {
                b.append(GuidedPlanJson.str(s));
            } else if (v instanceof Number || v instanceof Boolean) {
                b.append(v);
            } else if (v instanceof Map<?, ?> m) {
                if (m.isEmpty()) {
                    b.append("{}");
                    return;
                }
                b.append("{\n");
                int i = 0;
                for (Map.Entry<String, Object> e : ((Map<String, Object>) m).entrySet()) {
                    b.append("  ".repeat(level + 1)).append(GuidedPlanJson.str(e.getKey())).append(": ");
                    value(b, e.getValue(), level + 1);
                    b.append(++i < m.size() ? ",\n" : "\n");
                }
                b.append("  ".repeat(level)).append('}');
            } else if (v instanceof List<?> l) {
                if (l.isEmpty()) {
                    b.append("[]");
                    return;
                }
                if (l.stream().allMatch(x -> x instanceof String || x instanceof Number)) {
                    b.append('[');
                    for (int i = 0; i < l.size(); i++) {
                        if (i > 0) b.append(", ");
                        value(b, l.get(i), level);
                    }
                    b.append(']');
                    return;
                }
                b.append("[\n");
                for (int i = 0; i < l.size(); i++) {
                    b.append("  ".repeat(level + 1));
                    value(b, l.get(i), level + 1);
                    b.append(i < l.size() - 1 ? ",\n" : "\n");
                }
                b.append("  ".repeat(level)).append(']');
            } else {
                throw new IllegalArgumentException("tipo não suportado");
            }
        }
    }
}
