package com.nekkora.studio.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.RecordingObservation.CaptureMode;
import com.nekkora.studio.domain.origin.RecordingObservation.Observation;
import com.nekkora.studio.domain.origin.StepOrigin;
import com.nekkora.studio.domain.origin.StepProvenance;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** STUDIO-003: etapas gravadas no domínio — procedência, observação, PRESS_KEY e descarte transacional da gravação. */
class RecordedStepDomainTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);

    private static StepDraft recorded(String name, ActionType type, String recordingId, String eventId) {
        CorrelationKeys keys = CorrelationKeys.of(Map.of(CorrelationKeys.Key.RECORDING_ID, recordingId, CorrelationKeys.Key.RECORDED_EVENT_ID, eventId));
        RecordingObservation obs = new RecordingObservation(CaptureMode.CAPTURE_AND_INTERACT, Observation.INTERACTION_OBSERVED, null, List.of("FORM_SUBMITTED"),
                List.of());
        return StepDraft.builder().name(name).actionType(type).provenance(StepProvenance.recorded(keys, obs)).build();
    }

    @Test
    void s003038DescarteDaGravacaoRemoveSoAsEtapasDelaEmTodasAsSessoes() {
        Automation a = Automation.create("Consulta de clientes", null, clock);
        LogicalSession login = a.addSession("Autenticação", null);
        LogicalSession consulta = a.addSession("Consulta", null);
        a.appendStep(login.id(), StepDraft.manual("Abrir o sistema", ActionType.NAVIGATE, null));
        a.appendStep(login.id(), recorded("Preencher campo Usuário", ActionType.FILL, "rec-anterior", "ev-1"));
        a.appendStep(login.id(), recorded("Preencher campo Senha", ActionType.FILL, "rec-cancelada", "ev-1"));
        a.appendStep(consulta.id(), recorded("Clicar em Pesquisar", ActionType.CLICK, "rec-cancelada", "ev-2"));
        a.appendStep(consulta.id(), StepDraft.manual("Conferir resultado", ActionType.OBSERVE, null));
        long revision = a.revision();

        assertEquals(2, a.discardRecording("rec-cancelada"));
        assertEquals(List.of("Abrir o sistema", "Preencher campo Usuário"), login.steps().stream().map(AutomationStep::name).toList());
        assertEquals(List.of("Conferir resultado"), consulta.steps().stream().map(AutomationStep::name).toList());
        assertEquals(revision + 1, a.revision(), "uma única alteração");
        assertEquals(List.of(1, 2, 3), a.sequence().stream().map(StepPosition::number).toList(), "numeração recalculada");

        assertEquals(0, a.discardRecording("rec-cancelada"), "repetir não tem efeito");
        assertEquals(0, a.discardRecording("rec-inexistente"));
        assertEquals(revision + 1, a.revision(), "nada mudou: revisão igual");
    }

    @Test
    void etapaManualNuncaEhDescartadaMesmoComChaveDeGravacao() {
        Automation a = Automation.create("X", null, clock);
        LogicalSession s = a.addSession("S", null);
        StepDraft manualWithKey = StepDraft.builder().name("Manual").actionType(ActionType.CLICK)
                .provenance(new StepProvenance(StepOrigin.MANUAL, CorrelationKeys.of(Map.of(CorrelationKeys.Key.RECORDING_ID, "rec-1")))).build();
        a.appendStep(s.id(), manualWithKey);
        assertEquals(0, a.discardRecording("rec-1"));
        assertEquals(1, s.stepCount());
    }

    @Test
    void pressKeyAceitaATeclaComoValorEProcedenciaGuardaAObservacao() {
        Automation a = Automation.create("X", null, clock);
        LogicalSession s = a.addSession("S", null);
        StepDraft d = StepDraft.builder().name("Pressionar Enter no campo Pesquisa").actionType(ActionType.PRESS_KEY)
                .parameters(ActionParameters.withValue(new ValueSource.LiteralValue("Enter")))
                .provenance(StepProvenance.recorded(CorrelationKeys.of(Map.of(CorrelationKeys.Key.RECORDING_ID, "rec-1")),
                        new RecordingObservation(CaptureMode.CAPTURE_AND_INTERACT, Observation.INTERACTION_OBSERVED, null, List.of(), List.of())))
                .build();
        AutomationStep step = a.appendStep(s.id(), d);
        assertEquals(ActionType.PRESS_KEY, step.actionType());
        assertEquals("Enter", step.parameters().value().orElseThrow().display());
        RecordingObservation o = step.provenance().recordingObservation().orElseThrow();
        assertEquals(RecordingObservation.BusinessOutcome.NOT_VALIDATED, o.businessOutcome(), "o Recorder não valida resultado de negócio");
        assertTrue(ActionType.manualChoices().contains(ActionType.PRESS_KEY));
        assertFalse(ActionType.PRESS_KEY.acceptsOption());
    }

    @Test
    void observacaoSoEmEtapaGravadaEPropostaNuncaEhExecucao() {
        RecordingObservation proposta = new RecordingObservation(CaptureMode.CAPTURE_ONLY, Observation.PROPOSED, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> new StepProvenance(StepOrigin.MANUAL, CorrelationKeys.none(), proposta));
        assertThrows(IllegalArgumentException.class,
                () -> new RecordingObservation(CaptureMode.CAPTURE_ONLY, Observation.INTERACTION_OBSERVED, null, null, null),
                "em CAPTURE_ONLY a ação não é executada");
        assertTrue(new StepProvenance(StepOrigin.MANUAL, CorrelationKeys.none()).recordingObservation().isEmpty(), "construtor anterior compatível");
    }

    @Test
    void s003013CampoSensivelGravadoNuncaGuardaLiteral() {
        Automation a = Automation.create("X", null, clock);
        LogicalSession s = a.addSession("S", null);
        ElementReference senha = ElementReference.builder().accessibleName("Senha").semanticKind("PASSWORD_INPUT").tag("input").sensitive(true).build();
        StepDraft d = StepDraft.builder().name("Preencher campo Senha").actionType(ActionType.FILL).element(senha)
                .parameters(ActionParameters.withValue(new ValueSource.LiteralValue("qualquer"))).build();
        DomainValidationException e = assertThrows(DomainValidationException.class, () -> a.appendStep(s.id(), d));
        assertEquals(ValidationProblem.LITERAL_ON_SENSITIVE_TARGET, e.problem());
        StepDraft sem = StepDraft.builder().name("Preencher campo Senha").actionType(ActionType.FILL).element(senha).build();
        assertTrue(a.appendStep(s.id(), sem).parameters().isEmpty(), "etapa gravada sem valor configurado (ValueSource futuro)");
    }
}
