package com.nekkora.studio.guided;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.target.DecisionActor;
import com.nekkora.studio.domain.target.TargetDecision;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * STUDIO-009 G5 — reuso: só segue sem perguntar quando a identidade é comprovada AGORA, pela mesma fonte, e a etapa
 * espera a mesma coisa; cada mudança real pede revisão com o motivo; decisão de agente de teste não é transferível.
 */
class ReusePolicyTest {

    private static final TargetDecision.Expectation EXPECT = new TargetDecision.Expectation(ActionType.FILL, "VARIABLE", "registro.codigo", "tgt-1",
            "SEARCH_INPUT", "input", "#registro-codigo", List.of(AccessStep.Type.MAIN_DOCUMENT));

    private static TargetDecision decision(TargetDecision.Kind kind, DecisionActor actor) {
        return new TargetDecision(kind, actor, Instant.parse("2026-10-17T12:00:00Z"),
                new TargetDecision.Basis("TARGET_ID", "RESOLVED", List.of(), Instant.parse("2026-10-17T12:00:00Z"), ""), EXPECT);
    }

    private static Map<String, String> checks(String visible) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("identity", "PASS");
        m.put("unique", "PASS");
        m.put("visible", visible);
        m.put("enabled", "PASS");
        m.put("editable", "PASS");
        m.put("kindMatchesAction", "PASS");
        m.put("frameReachable", "NOT_APPLICABLE");
        m.put("shadowReachable", "NOT_APPLICABLE");
        return m;
    }

    private static ReusePolicy.Verification resolved(List<String> reasons) {
        return new ReusePolicy.Verification("RESOLVED", "TARGET_ID", reasons, checks("PASS"));
    }

    @Test
    @DisplayName("mesma identidade comprovada agora, mesma expectativa: REUSE (não pergunta de novo); sem decisão: NO_DECISION")
    void reusa() {
        assertEquals(ReusePolicy.Verdict.REUSE, ReusePolicy.evaluate(decision(TargetDecision.Kind.CONFIRMED, DecisionActor.TEST_AGENT), EXPECT,
                resolved(List.of("NODE_REPLACED")), DecisionActor.TEST_AGENT).verdict(), "re-renderização simples não pede revisão");
        assertEquals(ReusePolicy.Verdict.NO_DECISION, ReusePolicy.evaluate(null, EXPECT, resolved(List.of()), DecisionActor.DEVELOPER).verdict());
    }

    @Test
    @DisplayName("mudanças reais pedem revisão: ação, valor, identidade, tipo, localizador, trajetória, atributos, estado, fonte mais fraca, rejeição")
    void revisa() {
        TargetDecision ok = decision(TargetDecision.Kind.CONFIRMED, DecisionActor.DEVELOPER);
        Map<ReusePolicy.Reason, TargetDecision.Expectation> changes = Map.of(
                ReusePolicy.Reason.ACTION_CHANGED, new TargetDecision.Expectation(ActionType.CLICK, "VARIABLE", "registro.codigo", "tgt-1", "SEARCH_INPUT",
                        "input", "#registro-codigo", List.of(AccessStep.Type.MAIN_DOCUMENT)),
                ReusePolicy.Reason.VALUE_SOURCE_CHANGED, new TargetDecision.Expectation(ActionType.FILL, "LITERAL", "", "tgt-1", "SEARCH_INPUT", "input",
                        "#registro-codigo", List.of(AccessStep.Type.MAIN_DOCUMENT)),
                ReusePolicy.Reason.TARGET_IDENTITY_CHANGED, new TargetDecision.Expectation(ActionType.FILL, "VARIABLE", "registro.codigo", "tgt-2",
                        "SEARCH_INPUT", "input", "#registro-codigo", List.of(AccessStep.Type.MAIN_DOCUMENT)),
                ReusePolicy.Reason.PREFERRED_LOCATOR_CHANGED, new TargetDecision.Expectation(ActionType.FILL, "VARIABLE", "registro.codigo", "tgt-1",
                        "SEARCH_INPUT", "input", "#outro", List.of(AccessStep.Type.MAIN_DOCUMENT)),
                ReusePolicy.Reason.ACCESS_PATH_CHANGED, new TargetDecision.Expectation(ActionType.FILL, "VARIABLE", "registro.codigo", "tgt-1",
                        "SEARCH_INPUT", "input", "#registro-codigo", List.of(AccessStep.Type.MAIN_DOCUMENT, AccessStep.Type.IFRAME)));
        for (Map.Entry<ReusePolicy.Reason, TargetDecision.Expectation> c : changes.entrySet()) {
            ReusePolicy.Outcome o = ReusePolicy.evaluate(ok, c.getValue(), resolved(List.of()), DecisionActor.DEVELOPER);
            assertEquals(ReusePolicy.Verdict.REVIEW, o.verdict(), c.getKey().name());
            assertTrue(o.reasons().contains(c.getKey()), c.getKey() + " em " + o.reasons());
        }
        assertTrue(ReusePolicy.evaluate(ok, EXPECT, new ReusePolicy.Verification("AMBIGUOUS", "TARGET_ID", List.of(), checks("UNKNOWN")),
                DecisionActor.DEVELOPER).reasons().contains(ReusePolicy.Reason.IDENTITY_NOT_RESOLVED), "ambiguidade nunca vira reuso");
        assertTrue(ReusePolicy.evaluate(ok, EXPECT, new ReusePolicy.Verification("RESOLVED", "DESCRIPTOR", List.of(), checks("PASS")),
                DecisionActor.DEVELOPER).reasons().contains(ReusePolicy.Reason.IDENTITY_SOURCE_WEAKER), "confirmado pela identidade, comprovado só pelo descritor");
        assertTrue(ReusePolicy.evaluate(ok, EXPECT, resolved(List.of("ID_ATTRIBUTE_CHANGED")), DecisionActor.DEVELOPER).reasons()
                .contains(ReusePolicy.Reason.ATTRIBUTES_CHANGED), "id mudou: revisar, mesmo resolvido");
        assertTrue(ReusePolicy.evaluate(ok, EXPECT, new ReusePolicy.Verification("RESOLVED", "TARGET_ID", List.of(), checks("FAIL")), DecisionActor.DEVELOPER)
                .reasons().contains(ReusePolicy.Reason.STATE_NOT_SUITABLE), "oculto agora");
        assertTrue(ReusePolicy.evaluate(decision(TargetDecision.Kind.REJECTED, DecisionActor.DEVELOPER), EXPECT, resolved(List.of()), DecisionActor.DEVELOPER)
                .reasons().contains(ReusePolicy.Reason.DECISION_REJECTED));
    }

    @Test
    @DisplayName("decisão de agente de teste não é transferível para quem opera o produto; no laboratório, vale para o próprio agente")
    void naoTransferivel() {
        TargetDecision lab = decision(TargetDecision.Kind.CONFIRMED, DecisionActor.TEST_AGENT);
        assertEquals(List.of(ReusePolicy.Reason.TEST_AGENT_DECISION_NOT_TRANSFERABLE),
                ReusePolicy.evaluate(lab, EXPECT, resolved(List.of()), DecisionActor.DEVELOPER).reasons());
        assertTrue(ReusePolicy.evaluate(lab, EXPECT, resolved(List.of()), DecisionActor.TEST_AGENT).reuse());
    }
}
