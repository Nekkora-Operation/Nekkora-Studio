package com.nekkora.studio.guided;

import com.nekkora.studio.domain.target.DecisionActor;
import com.nekkora.studio.domain.target.TargetDecision;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * STUDIO-009 G5 — reuso inteligente de uma decisão confirmada. Na segunda passagem do debug, uma etapa cujo alvo já foi
 * CONFIRMADO não interrompe o desenvolvedor de novo SE, e somente se, a verificação técnica ATUAL comprovar a mesma
 * identidade no mesmo contexto e nada relevante tiver mudado na etapa. Qualquer outra situação pede revisão, com os
 * motivos em códigos. Nunca reutiliza "porque a string CSS coincide": a identidade vem do motor da extensão
 * ({@code RESOLVED}) e a etapa é comparada pela EXPECTATIVA registrada na decisão (D-S009-14).
 *
 * <p>Pura: sem rede, sem GUI, sem relógio. A verificação atual chega como {@link Verification} (códigos do contrato
 * {@code guided-debug/1}).
 */
public final class ReusePolicy {

    /** Resultado da avaliação. */
    public enum Verdict {
        /** Mesma identidade comprovada agora, mesmo contexto, nada relevante mudou: seguir sem perguntar. */
        REUSE,
        /** Há decisão, mas algo exige revisão (motivos em {@link Outcome#reasons()}). */
        REVIEW,
        /** Não há decisão confirmada: é uma primeira passagem. */
        NO_DECISION
    }

    /** Motivos de revisão. */
    public enum Reason {
        DECISION_REJECTED, IDENTITY_NOT_RESOLVED, IDENTITY_SOURCE_WEAKER, ACTION_CHANGED, VALUE_SOURCE_CHANGED, TARGET_IDENTITY_CHANGED,
        TARGET_KIND_CHANGED, PREFERRED_LOCATOR_CHANGED, ACCESS_PATH_CHANGED, ATTRIBUTES_CHANGED, STATE_NOT_SUITABLE, TEST_AGENT_DECISION_NOT_TRANSFERABLE,
        PAGE_CHANGED
    }

    /** Verificação técnica atual (situada), em códigos. */
    public record Verification(String status, String identitySource, List<String> reasons, Map<String, String> checks) {
        public Verification {
            Objects.requireNonNull(status);
            Objects.requireNonNull(identitySource);
            reasons = List.copyOf(reasons);
            checks = Map.copyOf(checks);
        }
    }

    /** Decisão do reuso com os motivos. */
    public record Outcome(Verdict verdict, List<Reason> reasons) {
        public Outcome {
            reasons = List.copyOf(reasons);
        }

        public boolean reuse() {
            return verdict == Verdict.REUSE;
        }
    }

    /** Motivos do motor que indicam mudança relevante do alvo (não só re-renderização). */
    static final Set<String> CHANGE_MARKERS = Set.of("ID_ATTRIBUTE_CHANGED", "NAME_ATTRIBUTE_CHANGED", "KIND_INCOMPATIBLE_AT_ADDRESS",
            "INDISTINGUISHABLE_AT_REGISTRATION", "PAGE_CHANGED_DURING_CHECK");
    /** Verificações de estado que precisam passar para seguir sem perguntar (as não aplicáveis são ignoradas). */
    static final List<String> STATE_CHECKS = List.of("unique", "visible", "enabled", "editable", "kindMatchesAction", "frameReachable", "shadowReachable");

    private ReusePolicy() {}

    /**
     * @param decision decisão registrada na etapa (pode ser nula)
     * @param current  o que a etapa espera AGORA (calculado pelo domínio do estado atual)
     * @param now      verificação técnica atual
     * @param operator quem está operando o debug agora
     */
    public static Outcome evaluate(TargetDecision decision, TargetDecision.Expectation current, Verification now, DecisionActor operator) {
        if (decision == null) return new Outcome(Verdict.NO_DECISION, List.of());
        List<Reason> r = new ArrayList<>();
        if (decision.kind() != TargetDecision.Kind.CONFIRMED) r.add(Reason.DECISION_REJECTED);
        // uma decisão de agente de teste não vale como autorização para quem opera o produto (§0.1: não transferível)
        if (decision.actor() == DecisionActor.TEST_AGENT && operator == DecisionActor.DEVELOPER) r.add(Reason.TEST_AGENT_DECISION_NOT_TRANSFERABLE);
        if (!"RESOLVED".equals(now.status())) r.add(Reason.IDENTITY_NOT_RESOLVED);
        if ("TARGET_ID".equals(decision.basis().identitySource()) && !"TARGET_ID".equals(now.identitySource())) r.add(Reason.IDENTITY_SOURCE_WEAKER);
        TargetDecision.Expectation was = decision.expectation();
        if (was.action() != current.action()) r.add(Reason.ACTION_CHANGED);
        if (!was.valueKind().equals(current.valueKind()) || !was.variableRef().equals(current.variableRef())) r.add(Reason.VALUE_SOURCE_CHANGED);
        if (!was.targetId().equals(current.targetId())) r.add(Reason.TARGET_IDENTITY_CHANGED);
        if (!was.targetKind().equals(current.targetKind()) || !was.targetTag().equals(current.targetTag())) r.add(Reason.TARGET_KIND_CHANGED);
        if (!was.preferredExpression().equals(current.preferredExpression())) r.add(Reason.PREFERRED_LOCATOR_CHANGED);
        if (!was.path().equals(current.path())) r.add(Reason.ACCESS_PATH_CHANGED);
        if (now.reasons().stream().anyMatch(CHANGE_MARKERS::contains)) {
            r.add(now.reasons().contains("PAGE_CHANGED_DURING_CHECK") ? Reason.PAGE_CHANGED : Reason.ATTRIBUTES_CHANGED);
        }
        for (String c : STATE_CHECKS) {
            String v = now.checks().getOrDefault(c, "UNKNOWN");
            if (!"PASS".equals(v) && !"NOT_APPLICABLE".equals(v)) {
                r.add(Reason.STATE_NOT_SUITABLE);
                break;
            }
        }
        return r.isEmpty() ? new Outcome(Verdict.REUSE, List.of()) : new Outcome(Verdict.REVIEW, r);
    }
}
