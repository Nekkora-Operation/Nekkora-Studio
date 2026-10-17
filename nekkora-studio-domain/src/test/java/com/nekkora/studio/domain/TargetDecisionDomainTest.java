package com.nekkora.studio.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepProvenance;
import com.nekkora.studio.domain.privacy.CapturePrivacy;
import com.nekkora.studio.domain.target.DecisionActor;
import com.nekkora.studio.domain.target.TargetDecision;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * STUDIO-009 G4 — decisões sobre o alvo: confirmação/rejeição com ator, autoridade (agente de teste nunca passa por cima
 * do desenvolvedor), substituição atômica do alvo efetivo sem tocar a captura, recusa sem alteração parcial, restaurar o
 * original e desfazer/refazer pelo snapshot. Dados fictícios.
 */
class TargetDecisionDomainTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC);

    private static ElementReference element(String targetId, String kind, String tag, String name, String css) {
        return ElementReference.builder().semanticKind(kind).tag(tag).accessibleName(name).privacyPolicy(CapturePrivacy.VERSION)
                .addLocator(new LocatorCandidate(LocatorStrategy.ID, "", css, LocatorStability.HEURISTIC_STABLE, 1, true)).preferLastLocator()
                .correlation(CorrelationKeys.of(Map.of(CorrelationKeys.Key.TARGET_ID, targetId, CorrelationKeys.Key.RECORDING_ID, "rec-1",
                        CorrelationKeys.Key.RECORDED_EVENT_ID, "ev-" + targetId)))
                .build();
    }

    private Automation automation() {
        Automation a = Automation.create("Consulta de registro (laboratório)", null, clock);
        a.addSession("Consulta", null);
        return a;
    }

    private AutomationStep recordedFill(Automation a) {
        ElementReference captured = element("tgt-1", "SEARCH_INPUT", "input", "Código do registro", "#registro-codigo");
        return a.appendStep(a.sessions().get(0).id(), StepDraft.builder().name("Preencher código").actionType(ActionType.FILL)
                .parameters(ActionParameters.withValue(new ValueSource.VariableReference("registro.codigo"))).element(captured)
                .provenance(StepProvenance.recorded(captured.correlation(), new RecordingObservation(RecordingObservation.CaptureMode.CAPTURE_AND_INTERACT,
                        RecordingObservation.Observation.INTERACTION_OBSERVED, null, List.of(), List.of())))
                .build());
    }

    private static TargetDecision.Basis resolved() {
        return new TargetDecision.Basis("TARGET_ID", "RESOLVED", List.of(), Instant.parse("2026-10-17T12:00:05Z"), "http://127.0.0.1:18765/{…}");
    }

    @Test
    @DisplayName("confirmar: decisão com ator, base em códigos e expectativa calculada do estado; repetir a mesma decisão não conta revisão")
    void confirmar() {
        Automation a = automation();
        AutomationStep s = recordedFill(a);
        long rev = a.revision();
        assertTrue(a.decideTarget(s.id(), TargetDecision.Kind.CONFIRMED, DecisionActor.TEST_AGENT, resolved()));
        TargetDecision d = s.targetDecision().orElseThrow();
        assertEquals(DecisionActor.TEST_AGENT, d.actor());
        assertEquals(ActionType.FILL, d.expectation().action());
        assertEquals("VARIABLE", d.expectation().valueKind());
        assertEquals("registro.codigo", d.expectation().variableRef());
        assertEquals("tgt-1", d.expectation().targetId());
        assertEquals("#registro-codigo", d.expectation().preferredExpression());
        assertEquals(rev + 1, a.revision());
        assertFalse(a.decideTarget(s.id(), TargetDecision.Kind.CONFIRMED, DecisionActor.TEST_AGENT, resolved()), "mesma decisão: nada muda");
        assertEquals(rev + 1, a.revision());
        assertFalse(d.toString().contains("registro"), "toString sem dados");
    }

    @Test
    @DisplayName("autoridade: o desenvolvedor substitui o agente de teste; o agente de teste NUNCA substitui o desenvolvedor")
    void autoridade() {
        Automation a = automation();
        AutomationStep s = recordedFill(a);
        a.decideTarget(s.id(), TargetDecision.Kind.CONFIRMED, DecisionActor.TEST_AGENT, resolved());
        a.decideTarget(s.id(), TargetDecision.Kind.REJECTED, DecisionActor.DEVELOPER, TargetDecision.Basis.notInspected());
        assertEquals(DecisionActor.DEVELOPER, s.targetDecision().orElseThrow().actor());
        DomainValidationException e = assertThrows(DomainValidationException.class,
                () -> a.decideTarget(s.id(), TargetDecision.Kind.CONFIRMED, DecisionActor.TEST_AGENT, resolved()));
        assertEquals(ValidationProblem.TARGET_DECISION_AUTHORITY, e.problem());
        assertEquals(TargetDecision.Kind.REJECTED, s.targetDecision().orElseThrow().kind(), "a decisão do desenvolvedor ficou");
        assertThrows(DomainValidationException.class, () -> a.replaceTarget(s.id(), element("tgt-2", "SEARCH_INPUT", "input", "Código", "#c2"),
                DecisionActor.TEST_AGENT, resolved()), "nem trocando o alvo");
    }

    @Test
    @DisplayName("substituir: alvo efetivo novo + decisão confirmada numa operação; captura, baseline e procedência intactas; restaurar volta")
    void substituir() {
        Automation a = automation();
        AutomationStep s = recordedFill(a);
        ElementReference captured = s.capturedElement().orElseThrow();
        CapturedBaseline baseline = s.baseline().orElseThrow();
        StepProvenance provenance = s.provenance();
        ElementReference novo = element("tgt-2", "SEARCH_INPUT", "input", "Código", "#codigo-v2");
        a.replaceTarget(s.id(), novo, DecisionActor.TEST_AGENT, resolved());
        assertSame(novo, s.replacementTarget().orElseThrow());
        assertEquals("#codigo-v2", s.element().orElseThrow().preferredLocator().orElseThrow().expression());
        assertSame(captured, s.capturedElement().orElseThrow(), "a captura original nunca é reescrita");
        assertEquals(baseline, s.baseline().orElseThrow());
        assertSame(provenance, s.provenance());
        assertTrue(s.modified(StepField.TARGET));
        assertTrue(s.manualRevision().orElseThrow().fields().contains(StepField.TARGET));
        assertEquals("tgt-2", s.targetDecision().orElseThrow().expectation().targetId());
        // o preferencial agora indexa os localizadores do substituto
        assertTrue(a.choosePreferredLocator(s.id(), -1));
        assertTrue(s.element().orElseThrow().preferredLocator().isEmpty());
        assertTrue(a.restoreOriginal(s.id(), StepField.TARGET));
        assertTrue(s.replacementTarget().isEmpty());
        assertTrue(s.targetDecision().isEmpty(), "a decisão era sobre o substituto");
        assertEquals("#registro-codigo", s.element().orElseThrow().preferredLocator().orElseThrow().expression());
        assertFalse(s.modified(StepField.TARGET));
    }

    @Test
    @DisplayName("substituição incompatível (preencher um botão) recusada SEM alteração parcial; referência sem política recusada")
    void incompativel() {
        Automation a = automation();
        AutomationStep s = recordedFill(a);
        a.decideTarget(s.id(), TargetDecision.Kind.CONFIRMED, DecisionActor.TEST_AGENT, resolved());
        Automation.Snapshot before = a.snapshot();
        long rev = a.revision();
        DomainValidationException e = assertThrows(DomainValidationException.class,
                () -> a.replaceTarget(s.id(), element("tgt-9", "BUTTON", "button", "Consultar", "#consultar"), DecisionActor.TEST_AGENT, resolved()));
        assertEquals(ValidationProblem.TARGET_INCOMPATIBLE_WITH_ACTION, e.problem());
        assertEquals(rev, a.revision());
        assertTrue(s.replacementTarget().isEmpty());
        assertEquals("tgt-1", s.targetDecision().orElseThrow().expectation().targetId(), "decisão anterior intacta");
        ElementReference semPolitica = ElementReference.builder().semanticKind("SEARCH_INPUT").tag("input").build();
        assertEquals(ValidationProblem.TARGET_REPLACEMENT_INVALID, assertThrows(DomainValidationException.class,
                () -> a.replaceTarget(s.id(), semPolitica, DecisionActor.DEVELOPER, resolved())).problem());
        assertEquals(before.revision(), a.snapshot().revision());
    }

    @Test
    @DisplayName("desfazer/refazer: o snapshot leva e traz o substituto e a decisão")
    void desfazerRefazer() {
        Automation a = automation();
        AutomationStep s = recordedFill(a);
        Automation.Snapshot antes = a.snapshot();
        a.replaceTarget(s.id(), element("tgt-2", "SEARCH_INPUT", "input", "Código", "#codigo-v2"), DecisionActor.DEVELOPER, resolved());
        Automation.Snapshot depois = a.snapshot();
        a.restore(antes);
        AutomationStep x = a.step(s.id()).orElseThrow();
        assertTrue(x.replacementTarget().isEmpty());
        assertTrue(x.targetDecision().isEmpty());
        a.restore(depois);
        x = a.step(s.id()).orElseThrow();
        assertEquals("tgt-2", x.targetDecision().orElseThrow().expectation().targetId());
        assertEquals(DecisionActor.DEVELOPER, x.targetDecision().orElseThrow().actor());
    }

    @Test
    @DisplayName("sem elemento não há o que decidir; duplicar a etapa não herda decisão nem conta como verificada")
    void semElementoEDuplicar() {
        Automation a = automation();
        AutomationStep vazio = a.appendStep(a.sessions().get(0).id(), StepDraft.builder().name("Clicar").actionType(ActionType.CLICK).build());
        assertEquals(ValidationProblem.STEP_HAS_NO_ELEMENT, assertThrows(DomainValidationException.class,
                () -> a.decideTarget(vazio.id(), TargetDecision.Kind.CONFIRMED, DecisionActor.DEVELOPER, resolved())).problem());
        AutomationStep s = recordedFill(a);
        a.decideTarget(s.id(), TargetDecision.Kind.CONFIRMED, DecisionActor.DEVELOPER, resolved());
        AutomationStep copia = a.duplicateStep(s.id());
        assertTrue(copia.targetDecision().isEmpty());
    }
}
