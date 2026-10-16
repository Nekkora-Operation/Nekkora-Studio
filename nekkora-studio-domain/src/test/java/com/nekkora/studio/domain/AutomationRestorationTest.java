package com.nekkora.studio.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.evidence.EvidenceType;
import com.nekkora.studio.domain.evidence.ObservedEvidence;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepProvenance;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** STUDIO-006: a reconstrução preserva tudo e não aceita nenhum estado que o domínio recusaria. */
class AutomationRestorationTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);
    private static final Instant T = Instant.parse("2026-10-16T10:00:00Z");

    private static ElementReference element(boolean validated) {
        return ElementReference.builder().accessibleName("Entrar").semanticKind("BUTTON").tag("button")
                .addLocator(new LocatorCandidate(LocatorStrategy.ID, "button {id=entrar}", "#entrar", null, validated ? 1 : 2, true)).build();
    }

    private static StepProvenance recorded(String event, boolean executed) {
        CorrelationKeys keys = CorrelationKeys.of(Map.of(CorrelationKeys.Key.RECORDING_ID, "rec-1", CorrelationKeys.Key.RECORDED_EVENT_ID, event));
        return StepProvenance.recorded(keys, executed
                ? new RecordingObservation(RecordingObservation.CaptureMode.CAPTURE_AND_INTERACT, RecordingObservation.Observation.INTERACTION_OBSERVED, null,
                        List.of(), List.of())
                : new RecordingObservation(RecordingObservation.CaptureMode.CAPTURE_ONLY, RecordingObservation.Observation.PROPOSED, null, List.of(), List.of()));
    }

    private static AutomationRestoration.StepData step(UnaryOperator<AutomationRestoration.StepData> change) {
        AutomationRestoration.StepData base = new AutomationRestoration.StepData(new StepId(UUID.randomUUID()), T, recorded("ev-1", true), element(true),
                new StepContext("Navegador", "", ""), "", new CapturedBaseline("Clicar no botão Entrar", "", ActionType.CLICK, ActionParameters.none(), -1),
                null, "Entrar", "", ActionType.CLICK, ActionParameters.none(), -1, List.of(), List.of(), Map.of(), null, List.of());
        return change.apply(base);
    }

    private static AutomationRestoration.StepData with(AutomationRestoration.StepData s, StepProvenance p, CapturedBaseline b, ElementReference e, int preferred,
            ActionType t, ActionParameters params, List<StepComment> comments, List<ObservedEvidence> ev, StepId derived) {
        return new AutomationRestoration.StepData(s.id(), s.createdAt(), p, e, s.context(), s.observedOutcome(), b, derived, s.name(), s.description(), t, params,
                preferred, s.manualLocators(), comments, s.edits(), s.lastManualChange(), ev);
    }

    private AutomationRestoration.AutomationData automation(List<AutomationRestoration.SessionData> sessions, String model) {
        return new AutomationRestoration.AutomationData(new AutomationId(UUID.randomUUID()), "Login", "", AutomationStatus.DRAFT, T, T.plusSeconds(5), model, 3,
                sessions, List.of());
    }

    private String refused(AutomationRestoration.AutomationData d) {
        return assertThrows(AutomationRestoration.RestoreException.class, () -> AutomationRestoration.restore(d, clock)).reason();
    }

    private AutomationRestoration.AutomationData one(AutomationRestoration.StepData s) {
        return automation(List.of(new AutomationRestoration.SessionData(new SessionId(UUID.randomUUID()), "Autenticação", "", "", List.of(s))), ModelVersion.CURRENT);
    }

    private static ObservedEvidence evidence(String event) {
        return new ObservedEvidence("evd-1", 1, "rec-1", event, T, EvidenceType.FORM_SUBMITTED, com.nekkora.studio.domain.evidence.EvidenceVocabulary.Source.FORM_OBSERVER,
                com.nekkora.studio.domain.evidence.EvidenceVocabulary.Relation.AFTER_ACTION,
                com.nekkora.studio.domain.evidence.EvidenceVocabulary.Confidence.DIRECTLY_OBSERVED, null, null, null, null, null, null, null, List.of(), 0, 0,
                List.of());
    }

    @Test
    @DisplayName("estado válido: identidade, instantes, revisão e original preservados; nada conta como intervenção")
    void valido() {
        AutomationRestoration.StepData s = step(x -> x);
        AutomationRestoration.AutomationData d = one(s);
        Automation a = AutomationRestoration.restore(d, clock);
        assertEquals(d.id(), a.id());
        assertEquals(T, a.createdAt());
        assertEquals(3, a.revision());
        AutomationStep st = a.step(s.id()).orElseThrow();
        assertEquals("Clicar no botão Entrar", st.baseline().orElseThrow().name(), "original como persistido (não recalculado do efetivo)");
        assertEquals("Entrar", st.name());
    }

    @Test
    @DisplayName("recusas: versão do modelo, nome de sessão repetido, identidade repetida, texto fora da forma normalizada")
    void recusasEstruturais() {
        assertEquals("MODEL_VERSION_UNSUPPORTED", refused(automation(List.of(), "2.0.0")));
        SessionId sid = new SessionId(UUID.randomUUID());
        assertEquals("SESSION_NAME_IN_USE", refused(automation(List.of(new AutomationRestoration.SessionData(new SessionId(UUID.randomUUID()), "A", "", "", List.of()),
                new AutomationRestoration.SessionData(new SessionId(UUID.randomUUID()), " a ".strip(), "", "", List.of())), ModelVersion.CURRENT)));
        assertEquals("DUPLICATE_ID", refused(automation(List.of(new AutomationRestoration.SessionData(sid, "A", "", "", List.of()),
                new AutomationRestoration.SessionData(sid, "B", "", "", List.of())), ModelVersion.CURRENT)));
        assertEquals("TEXT_NOT_NORMALIZED", refused(automation(List.of(new AutomationRestoration.SessionData(sid, " A", "", "", List.of())), ModelVersion.CURRENT)));
    }

    @Test
    @DisplayName("recusas da etapa: original em etapa manual, preferencial não validado, parâmetro incompatível, derivada gravada, evidência indevida")
    void recusasDaEtapa() {
        AutomationRestoration.StepData ok = step(x -> x);
        CapturedBaseline b = ok.baseline();
        assertEquals("BASELINE_INCONSISTENT", refused(one(with(ok, StepProvenance.manual(), b, ok.capturedElement(), -1, ActionType.CLICK, ActionParameters.none(),
                List.of(), List.of(), null))));
        assertEquals("LOCATOR_NOT_FOUND", refused(one(with(ok, ok.provenance(), b, element(false), 0, ActionType.CLICK, ActionParameters.none(), List.of(),
                List.of(), null))));
        assertEquals("VALUE_NOT_ACCEPTED", refused(one(with(ok, ok.provenance(), b, ok.capturedElement(), -1, ActionType.CLICK,
                ActionParameters.withValue(new ValueSource.VariableReference("x")), List.of(), List.of(), null))));
        assertEquals("DERIVED_FROM_INVALID", refused(one(with(ok, ok.provenance(), b, ok.capturedElement(), -1, ActionType.CLICK, ActionParameters.none(), List.of(),
                List.of(), new StepId(UUID.randomUUID())))));
        assertEquals("EVIDENCE_NOT_APPLICABLE", refused(one(with(ok, recorded("ev-1", false), b, ok.capturedElement(), -1, ActionType.CLICK,
                ActionParameters.none(), List.of(), List.of(evidence("ev-1")), null))));
        assertEquals("EVIDENCE_STEP_MISMATCH", refused(one(with(ok, ok.provenance(), b, ok.capturedElement(), -1, ActionType.CLICK, ActionParameters.none(),
                List.of(), List.of(evidence("ev-9")), null))));
        assertEquals("COMMENT_LOOKS_SENSITIVE", refused(one(with(ok, ok.provenance(), b, ok.capturedElement(), -1, ActionType.CLICK, ActionParameters.none(),
                List.of(new StepComment("CPF 123.456.789-09", T)), List.of(), null))));
    }
}
