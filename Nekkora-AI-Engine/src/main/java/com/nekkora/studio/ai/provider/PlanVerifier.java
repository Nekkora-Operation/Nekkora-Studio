package com.nekkora.studio.ai.provider;

import com.nekkora.studio.ai.context.ProjectContext;
import com.nekkora.studio.ai.spec.FunctionalSpec;
import com.nekkora.studio.ai.spec.SpecResolver;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.pdd.PddText;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * STUDIO-014 — PlanVerifier: a resposta da IA é DADO NÃO CONFIÁVEL. Cada proposta só passa se: o JSON segue o schema
 * (campos e tipos), o {@code StepId} existe no projeto, o tipo de proposta cabe na ação da etapa, o valor é do
 * vocabulário fechado (ou um nome de variável válido que não colide com segredos) e a justificativa passa pela política
 * de texto do PDD (sem números, endereços, dados). Tudo o mais é RECUSADO com código, nunca aplicado.
 *
 * <p>NEKKORA-E2E-002: {@code DATA_FLOW} (do processo inteiro: {@code stepId} = {@value #PROCESS}; SINGLE_JSON ou
 * LIST_XLSX), {@code OUTPUT_COLUMN} (só em Observar: {@code COLUMN:<cabeçalho do vocabulário>}, {@code FALLBACK:<StepId de
 * OUTRO Observar>} ou {@code NONE}) e {@code VARIABLE_TYPE} (só em Observar com alvo: DATE ou TEXT).
 */
public final class PlanVerifier {
    private PlanVerifier() {}

    public enum Kind { EFFECT, POSTCONDITION, VARIABLE_NAME, DATA_FLOW, OUTPUT_COLUMN, VARIABLE_TYPE }

    /** {@code stepId} das propostas que valem para o processo inteiro. */
    public static final String PROCESS = "PROCESSO";

    public record Proposal(Kind kind, StepId step, String value, String rationale) {}

    public record Rejection(String code, int index) {}

    public record Result(List<Proposal> accepted, List<Rejection> rejected, String provider, int inputTokens, int outputTokens, String model) {
        public Result {
            accepted = List.copyOf(accepted);
            rejected = List.copyOf(rejected);
            model = model == null ? "" : model;
        }

        public Result(List<Proposal> accepted, List<Rejection> rejected, String provider, int inputTokens, int outputTokens) {
            this(accepted, rejected, provider, inputTokens, outputTokens, "");
        }
    }

    static final Set<String> FIELDS = Set.of("kind", "stepId", "value", "rationale");

    @SuppressWarnings("unchecked")
    public static Result verify(String toolInput, ProjectContext ctx) {
        List<Proposal> ok = new ArrayList<>();
        List<Rejection> no = new ArrayList<>();
        Map<String, Object> m;
        try {
            m = Json.parseObject(toolInput);
        } catch (RuntimeException e) {
            return new Result(List.of(), List.of(new Rejection("SCHEMA_INVALID", -1)), "", 0, 0);
        }
        if (!Set.of("proposals").equals(m.keySet()) || !(m.get("proposals") instanceof List<?> list) || list.size() > 50) {
            return new Result(List.of(), List.of(new Rejection("SCHEMA_INVALID", -1)), "", 0, 0);
        }
        Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Map<?, ?> raw)) {
                no.add(new Rejection("SCHEMA_INVALID", i));
                continue;
            }
            Map<String, Object> p = (Map<String, Object>) raw;
            if (!FIELDS.containsAll(p.keySet()) || !(p.get("kind") instanceof String k) || !(p.get("stepId") instanceof String sid)
                    || !(p.get("value") instanceof String value) || (p.containsKey("rationale") && !(p.get("rationale") instanceof String))) {
                no.add(new Rejection("SCHEMA_INVALID", i));
                continue;
            }
            Kind kind;
            try {
                kind = Kind.valueOf(k);
            } catch (IllegalArgumentException e) {
                no.add(new Rejection("KIND_UNKNOWN", i));
                continue;
            }
            if (kind == Kind.DATA_FLOW) { // proposta do processo inteiro (sem etapa)
                if (!PROCESS.equals(sid)) no.add(new Rejection("KIND_NOT_FOR_STEP", i));
                else if (!FunctionalSpec.DATA_FLOWS.contains(value)) no.add(new Rejection("VALUE_NOT_ALLOWED", i));
                else if (!seen.add(kind + "|" + PROCESS)) no.add(new Rejection("DUPLICATE", i));
                else ok.add(new Proposal(kind, null, value, rationale(p)));
                continue;
            }
            StepId step;
            try {
                step = new StepId(UUID.fromString(sid));
            } catch (IllegalArgumentException e) {
                no.add(new Rejection("STEP_UNKNOWN", i));
                continue;
            }
            Optional<ProjectContext.Step> s = ctx.step(step);
            if (s.isEmpty()) {
                no.add(new Rejection("STEP_UNKNOWN", i)); // StepId inventado
                continue;
            }
            String code = switch (kind) {
                case EFFECT -> !SpecResolver.needsEffect(s.get()) ? "KIND_NOT_FOR_STEP" : FunctionalSpec.EFFECTS.contains(value) ? null : "VALUE_NOT_ALLOWED";
                case POSTCONDITION -> s.get().action() != ActionType.CLICK ? "KIND_NOT_FOR_STEP" : FunctionalSpec.POSTCONDITIONS.contains(value) ? null : "VALUE_NOT_ALLOWED";
                case VARIABLE_NAME -> !(s.get().action() == ActionType.FILL || s.get().action() == ActionType.OBSERVE) || s.get().sensitive() ? "KIND_NOT_FOR_STEP"
                        : value.matches("[a-z][a-z0-9_]{2,39}") && !value.startsWith("credencial") ? null : "NAME_INVALID";
                case DATA_FLOW -> "KIND_NOT_FOR_STEP";
                case OUTPUT_COLUMN -> s.get().action() != ActionType.OBSERVE ? "KIND_NOT_FOR_STEP" : column(value, step, ctx);
                case VARIABLE_TYPE -> s.get().action() != ActionType.OBSERVE || !s.get().hasTarget() ? "KIND_NOT_FOR_STEP"
                        : Set.of("DATE", "TEXT").contains(value) ? null : "VALUE_NOT_ALLOWED";
            };
            if (code != null) {
                no.add(new Rejection(code, i));
                continue;
            }
            if (!seen.add(kind + "|" + step.value())) {
                no.add(new Rejection("DUPLICATE", i));
                continue;
            }
            ok.add(new Proposal(kind, step, value, rationale(p)));
        }
        return new Result(ok, no, "", 0, 0);
    }

    /** Justificativa fora da política de texto do PDD é descartada (a proposta fica). */
    private static String rationale(Map<String, Object> p) {
        String why = p.get("rationale") instanceof String r ? r.replaceAll("\\p{Cntrl}", " ").strip() : "";
        return why.length() > 200 || !PddText.problems(why).isEmpty() ? "" : why;
    }

    /** Valor de OUTPUT_COLUMN: NONE, COLUMN:<cabeçalho do vocabulário> ou FALLBACK:<StepId de OUTRO Observar existente>. */
    private static String column(String value, StepId step, ProjectContext ctx) {
        if (FunctionalSpec.NOT_IN_OUTPUT.equals(value)) return null;
        if (value.startsWith(FunctionalSpec.COLUMN)) return FunctionalSpec.headerOk(value.substring(FunctionalSpec.COLUMN.length())) ? null : "HEADER_INVALID";
        if (value.startsWith(FunctionalSpec.FALLBACK)) {
            try {
                StepId other = new StepId(UUID.fromString(value.substring(FunctionalSpec.FALLBACK.length())));
                return !other.equals(step) && ctx.step(other).map(o -> o.action() == ActionType.OBSERVE).orElse(false) ? null : "STEP_UNKNOWN";
            } catch (IllegalArgumentException e) {
                return "STEP_UNKNOWN";
            }
        }
        return "VALUE_NOT_ALLOWED";
    }
}
