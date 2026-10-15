package com.nekkora.studio.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.evidence.EvidenceType;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Classification;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Confidence;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Limitation;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Relation;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Signal;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Source;
import com.nekkora.studio.domain.evidence.ObservationSummary;
import com.nekkora.studio.domain.evidence.ObservedEvidence;
import com.nekkora.studio.domain.evidence.ObservedEvidence.Message;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.RecordingObservation.CaptureMode;
import com.nekkora.studio.domain.origin.RecordingObservation.Observation;
import com.nekkora.studio.domain.origin.StepProvenance;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** STUDIO-005: evidências observadas no domínio — imutáveis, fora da revisão e do estado editável, resumo estruturado. */
class EvidenceDomainTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);
    private static final Instant T = Instant.parse("2026-10-15T12:00:07Z");

    static StepDraft recorded(String name, ActionType type, String recordingId, String eventId, boolean executed) {
        CorrelationKeys keys = CorrelationKeys.of(Map.of(CorrelationKeys.Key.RECORDING_ID, recordingId, CorrelationKeys.Key.RECORDED_EVENT_ID, eventId));
        RecordingObservation obs = executed
                ? new RecordingObservation(CaptureMode.CAPTURE_AND_INTERACT, Observation.INTERACTION_OBSERVED, null, List.of(), List.of())
                : new RecordingObservation(CaptureMode.CAPTURE_ONLY, Observation.PROPOSED, null, List.of(), List.of());
        return StepDraft.builder().name(name).actionType(type).provenance(StepProvenance.recorded(keys, obs)).build();
    }

    static ObservedEvidence ev(String id, long seq, String rec, String eventId, EvidenceType type, Message msg, Limitation... lim) {
        boolean ambiguous = type == EvidenceType.AMBIGUOUS_FEEDBACK;
        return new ObservedEvidence(id, seq, rec, eventId, T.plusMillis(seq * 100), type, Source.FEEDBACK_OBSERVER, Relation.AFTER_ACTION,
                ambiguous ? Confidence.AMBIGUOUS : type == EvidenceType.OBSERVATION_INCOMPLETE ? Confidence.INCOMPLETE : Confidence.TEMPORALLY_ASSOCIATED,
                type == EvidenceType.VISIBLE_ERROR_MESSAGE ? Classification.ERROR_INDICATOR : null, null, "alerta do formulário login", msg, null, null, null,
                List.of(Signal.ROLE_ALERT), ambiguous ? 2 : 0, 0, List.of(lim));
    }

    private Automation automation() {
        Automation a = Automation.create("Login", null, clock);
        LogicalSession s = a.addSession("Autenticação", null);
        a.appendStep(s.id(), recorded("Preencher Usuário", ActionType.FILL, "rec-1", "ev-1", true));
        a.appendStep(s.id(), recorded("Clicar em Entrar", ActionType.CLICK, "rec-1", "ev-2", true));
        return a;
    }

    @Test
    void s005027E033EvidenciaEnriqueceAEtapaSemAlterarIdentidadeOrdemOriginalNemRevisao() {
        Automation a = automation();
        AutomationStep entrar = a.recordedStep("rec-1", "ev-2").orElseThrow();
        StepId id = entrar.id();
        long revision = a.revision();
        var baseline = entrar.baseline();
        List<StepPosition> order = a.sequence();

        assertTrue(a.attachEvidence(ev("evd-1", 1, "rec-1", "ev-2", EvidenceType.VISIBLE_ERROR_MESSAGE, Message.of("Usuário ou senha inválidos."))));
        assertFalse(a.attachEvidence(ev("evd-1", 1, "rec-1", "ev-2", EvidenceType.VISIBLE_ERROR_MESSAGE, Message.of("Usuário ou senha inválidos."))),
                "repetida (retransmissão): idempotente");

        assertEquals(revision, a.revision(), "evidência não é edição: revisão igual");
        assertEquals(id, entrar.id());
        assertEquals(order, a.sequence(), "ordem e numeração iguais");
        assertEquals(baseline, entrar.baseline(), "valores originais intactos");
        assertEquals(ActionType.CLICK, entrar.actionType());
        assertEquals(1, entrar.evidence().size());
        assertThrows(UnsupportedOperationException.class, () -> entrar.evidence().clear(), "somente leitura");
    }

    @Test
    void s005026OrdemTecnicaPelaSequenciaMesmoComChegadaForaDeOrdem() {
        Automation a = automation();
        a.attachEvidence(ev("evd-3", 3, "rec-1", "ev-2", EvidenceType.OBSERVATION_COMPLETE, Message.none()));
        a.attachEvidence(ev("evd-1", 1, "rec-1", "ev-2", EvidenceType.LOADING_INDICATOR, Message.none()));
        a.attachEvidence(ev("evd-2", 2, "rec-1", "ev-2", EvidenceType.LOADING_FINISHED, Message.none()));
        assertEquals(List.of(1L, 2L, 3L), a.recordedStep("rec-1", "ev-2").orElseThrow().evidence().stream().map(ObservedEvidence::sequence).toList());
    }

    @Test
    void s005028EtapaInexistenteOuDeOutraGravacaoERecusada() {
        Automation a = automation();
        DomainValidationException x = assertThrows(DomainValidationException.class,
                () -> a.attachEvidence(ev("evd-1", 1, "rec-outra", "ev-2", EvidenceType.VISIBLE_ALERT, Message.none())));
        assertEquals(ValidationProblem.EVIDENCE_STEP_NOT_FOUND, x.problem());
        assertThrows(DomainValidationException.class, () -> a.attachEvidence(ev("evd-2", 2, "rec-1", "ev-99", EvidenceType.VISIBLE_ALERT, Message.none())));
    }

    @Test
    void s005035PropostaDeCaptureOnlyNaoRecebeEvidenciaDeConsequencia() {
        Automation a = Automation.create("X", null, clock);
        LogicalSession s = a.addSession("S", null);
        a.appendStep(s.id(), recorded("Clicar em Entrar", ActionType.CLICK, "rec-1", "ev-1", false));
        DomainValidationException x = assertThrows(DomainValidationException.class,
                () -> a.attachEvidence(ev("evd-1", 1, "rec-1", "ev-1", EvidenceType.VISIBLE_ALERT, Message.none())));
        assertEquals(ValidationProblem.EVIDENCE_NOT_APPLICABLE, x.problem());
        assertEquals(ObservationSummary.Status.NOT_APPLICABLE, a.recordedStep("rec-1", "ev-1").orElseThrow().observation().status());
    }

    @Test
    void s005021AmbiguaFicaSemEtapaENaoEscolheNenhuma() {
        Automation a = automation();
        assertTrue(a.attachEvidence(ev("evd-9", 9, "rec-1", null, EvidenceType.AMBIGUOUS_FEEDBACK, Message.of("Atualização concluída."))));
        assertEquals(1, a.unattributedEvidence().size());
        a.sessions().get(0).steps().forEach(st -> assertTrue(st.evidence().isEmpty(), "nenhuma etapa escolhida"));
        assertThrows(IllegalArgumentException.class, () -> ev("evd-x", 1, "rec-1", null, EvidenceType.VISIBLE_ALERT, Message.none()),
                "só a ambígua fica sem etapa");
    }

    @Test
    void s005047MensagemSensivelNaoEntraNoDominio() {
        assertEquals(Message.Availability.SUPPRESSED, Message.of("CPF 123.456.789-09 já cadastrado").availability());
        assertEquals(Message.Availability.SUPPRESSED, Message.of("Enviamos para ana.souza@exemplo.com").availability());
        assertEquals(Message.Availability.SUPPRESSED, Message.of("Conta 98765432 bloqueada").availability());
        assertEquals(Message.Availability.SUPPRESSED, Message.of("x".repeat(161)).availability());
        // revisão do PR #6 da extensão: valor repetido pela página e dado de terceiro (fail-closed pela forma)
        assertEquals(Message.Availability.SUPPRESSED, Message.of("O código Kq7Segredo expirou.").availability());
        assertEquals(Message.Availability.SUPPRESSED, Message.of("Olá, JoanaPrado!").availability());
        assertEquals(Message.Availability.SUPPRESSED, Message.of("Cliente Maria Souza já possui cadastro.").availability());
        // modo estrito: nem mensagem comum nem dado de terceiro só em minúsculas (nunca digitado) entram com texto
        assertEquals(Message.Availability.SUPPRESSED, Message.of("Informe o CEP.").availability());
        assertEquals(Message.Availability.SUPPRESSED, Message.of("Usuário ou senha inválidos.").availability());
        assertEquals(Message.Availability.SUPPRESSED, Message.of("o cliente joão já existe").availability());
        assertEquals(Message.Availability.NONE, Message.of("   ").availability());
        assertThrows(IllegalArgumentException.class, () -> new Message(Message.Availability.AVAILABLE, "token eyJhbGciOiJIUzI1NiJ9abcdef.eyJzdWIiOiIxMjM0.abcdefghij"));
        assertThrows(IllegalArgumentException.class, () -> new Message(Message.Availability.SUPPRESSED, "texto"), "suprimida não tem texto");
        // P2 da revisão final: nem mensagem "segura" pode ser criada com texto livre pelo construtor público
        assertThrows(IllegalArgumentException.class, () -> new Message(Message.Availability.AVAILABLE, "Atenção"));
        assertThrows(IllegalArgumentException.class, () -> new Message(Message.Availability.AVAILABLE, "o cliente joão já existe"));
        // metadados: só descrições controladas; título nunca
        ObservedEvidence meta = new ObservedEvidence("evd-9", 9, "rec-1", "ev-1", T, EvidenceType.VISIBLE_ERROR_MESSAGE, Source.FEEDBACK_OBSERVER,
                Relation.AFTER_ACTION, Confidence.TEMPORALLY_ASSOCIATED, Classification.ERROR_INDICATOR,
                new ObservedEvidence.DocumentContext(false, false, "o cliente joão já existe", null), "mensagem de erro o cliente joão já existe",
                Message.suppressed(), "telefone do cliente joão", null, null, List.of(Signal.ROLE_ALERT), 0, 0, List.of());
        assertNull(meta.region());
        assertNull(meta.field());
        assertNull(meta.document().title());
        assertFalse(meta.toString().contains("joão"));
        ObservedEvidence ok = new ObservedEvidence("evd-10", 10, "rec-1", "ev-1", T, EvidenceType.VALIDATION_FEEDBACK, Source.FORM_OBSERVER,
                Relation.AFTER_ACTION, Confidence.DIRECTLY_OBSERVED, Classification.VALIDATION_INDICATOR, null, "região de validação", Message.none(),
                "campo de telefone", null, null, List.of(Signal.ARIA_INVALID), 0, 0, List.of());
        assertEquals("região de validação", ok.region());
        assertEquals("campo de telefone", ok.field());
        ObservedEvidence e = new ObservedEvidence("evd-1", 1, "rec-1", "ev-1", T, EvidenceType.VALIDATION_FEEDBACK, Source.FORM_OBSERVER, Relation.AFTER_ACTION,
                Confidence.DIRECTLY_OBSERVED, Classification.VALIDATION_INDICATOR,
                new ObservedEvidence.DocumentContext(false, false, "Pedido de ana.souza@exemplo.com", null), "região 123.456.789-09", Message.none(),
                "E-mail ana.souza@exemplo.com", null, null, List.of(Signal.NATIVE_INVALID), 0, 0, List.of());
        assertNull(e.region(), "rótulo com dado sensível é descartado");
        assertNull(e.field());
        assertNull(e.document().title());
    }

    @Test
    void s005034ResumoDistingueAsAusencias() {
        Automation a = automation();
        AutomationStep fill = a.recordedStep("rec-1", "ev-1").orElseThrow();
        AutomationStep click = a.recordedStep("rec-1", "ev-2").orElseThrow();
        assertEquals(ObservationSummary.Status.UNAVAILABLE, click.observation().status(), "sem evidência: indisponível (não é 'sem erro')");
        a.attachEvidence(ev("evd-1", 1, "rec-1", "ev-2", EvidenceType.VISIBLE_ERROR_MESSAGE, Message.suppressed(), Limitation.MESSAGE_SUPPRESSED));
        assertEquals(ObservationSummary.Status.IN_PROGRESS, click.observation().status());
        a.attachEvidence(ev("evd-2", 2, "rec-1", "ev-2", EvidenceType.OBSERVATION_COMPLETE, Message.none()));
        ObservationSummary s = click.observation();
        assertEquals(ObservationSummary.Status.OBSERVED, s.status());
        assertTrue(s.error(), "indicador de erro observado");
        assertEquals(1, s.suppressed());
        assertEquals(1, s.count(EvidenceType.Category.MESSAGE));
        assertEquals(RecordingObservation.BusinessOutcome.NOT_VALIDATED, s.businessOutcome(), "erro observado NÃO é falha de negócio");

        a.attachEvidence(ev("evd-3", 3, "rec-1", "ev-1", EvidenceType.OBSERVATION_COMPLETE, Message.none()));
        assertEquals(ObservationSummary.Status.NOTHING_OBSERVED, fill.observation().status());
        assertTrue(ObservationSummary.Status.NOTHING_OBSERVED.explanation().contains("não significa"));

        Automation b = automation();
        b.attachEvidence(ev("evd-4", 4, "rec-1", "ev-2", EvidenceType.OBSERVATION_INCOMPLETE, Message.none(), Limitation.WINDOW_CUT_BY_PAUSE));
        ObservationSummary cut = b.recordedStep("rec-1", "ev-2").orElseThrow().observation();
        assertEquals(ObservationSummary.Status.INCOMPLETE, cut.status());
        assertEquals(Limitation.WINDOW_CUT_BY_PAUSE, cut.cut());
    }

    @Test
    void s005029E030DuplicarNaoCopiaEvidenciasEMoverPreserva() {
        Automation a = automation();
        AutomationStep click = a.recordedStep("rec-1", "ev-2").orElseThrow();
        a.attachEvidence(ev("evd-1", 1, "rec-1", "ev-2", EvidenceType.VISIBLE_ALERT, Message.of("Atenção")));
        AutomationStep copy = a.duplicateStep(click.id());
        assertTrue(copy.evidence().isEmpty(), "a cópia não é uma interação observada");
        assertEquals(ObservationSummary.Status.NOT_APPLICABLE, copy.observation().status());
        LogicalSession other = a.addSession("Consulta", null);
        a.moveStep(click.id(), other.id(), 0);
        assertSame(click, a.step(click.id()).orElseThrow());
        assertEquals(1, click.evidence().size(), "mover preserva");
    }

    @Test
    void s005031E032DesfazerERefazerNaoApagamNemReaplicamEvidencias() {
        Automation a = automation();
        AutomationStep click = a.recordedStep("rec-1", "ev-2").orElseThrow();
        Automation.Snapshot before = a.snapshot();
        a.editStep(click.id(), StepEdit.of(a, click).name("Entrar no sistema"));
        Automation.Snapshot after = a.snapshot();
        long revision = a.revision();
        a.attachEvidence(ev("evd-1", 1, "rec-1", "ev-2", EvidenceType.VISIBLE_ALERT, Message.of("Atenção")));
        assertEquals(revision, a.revision(), "a chegada da evidência não invalida o histórico (revisão igual)");
        a.restore(before); // desfazer
        assertEquals("Clicar em Entrar", click.name());
        assertEquals(1, click.evidence().size(), "desfazer não apaga a evidência");
        a.restore(after); // refazer
        assertEquals("Entrar no sistema", click.name());
        assertEquals(1, click.evidence().size(), "refazer não reaplica (nem duplica)");
    }

    @Test
    void s005040CancelamentoDescartaEtapasEvidenciasENaoAtribuidasDaGravacao() {
        Automation a = automation();
        a.attachEvidence(ev("evd-1", 1, "rec-1", "ev-2", EvidenceType.VISIBLE_ALERT, Message.none()));
        a.attachEvidence(ev("evd-2", 2, "rec-1", null, EvidenceType.AMBIGUOUS_FEEDBACK, Message.none()));
        a.attachEvidence(ev("evd-3", 3, "rec-outra", null, EvidenceType.AMBIGUOUS_FEEDBACK, Message.none()));
        assertEquals(2, a.discardRecording("rec-1"));
        assertEquals(List.of("rec-outra"), a.unattributedEvidence().stream().map(ObservedEvidence::recordingId).toList());
    }

    @Test
    void s005053LimitePorEtapaERecusaDeclarada() {
        Automation a = automation();
        for (int i = 1; i <= Automation.MAX_EVIDENCE_PER_STEP; i++) {
            a.attachEvidence(ev("evd-" + i, i, "rec-1", "ev-2", EvidenceType.CONTENT_CHANGED, Message.none()));
        }
        DomainValidationException x = assertThrows(DomainValidationException.class,
                () -> a.attachEvidence(ev("evd-x", 999, "rec-1", "ev-2", EvidenceType.CONTENT_CHANGED, Message.none())));
        assertEquals(ValidationProblem.EVIDENCE_LIMIT_REACHED, x.problem());
    }

    @Test
    void s005064TiposSaoFechadosEOsDoContrato() {
        assertEquals(14, EvidenceType.values().length);
        for (EvidenceType t : EvidenceType.values()) {
            assertFalse(t.label().isBlank());
            assertFalse(t.name().contains("SUCCESS") || t.name().contains("FAILURE"), "nenhum tipo afirma resultado de negócio");
        }
    }
}
