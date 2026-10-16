package com.nekkora.studio.validation;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.evidence.EvidenceType;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Classification;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Confidence;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Limitation;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Relation;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Signal;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Source;
import com.nekkora.studio.domain.evidence.ObservedEvidence;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepProvenance;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** Automações de teste (dados fictícios) montadas pelas operações do domínio. */
final class Fixtures {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);
    static final Instant T = Instant.parse("2026-10-16T12:00:07Z");

    private Fixtures() {}

    /** Automação com uma sessão vazia. */
    static Automation withSession(String name) {
        Automation a = Automation.create(name, "", CLOCK);
        a.addSession("Autenticação", null);
        return a;
    }

    static LogicalSession session(Automation a) {
        return a.sessions().get(0);
    }

    static AutomationStep add(Automation a, StepDraft d) {
        return a.appendStep(session(a).id(), d);
    }

    static StepDraft manual(String name, ActionType type) {
        return StepDraft.builder().name(name).actionType(type).build();
    }

    static StepDraft manual(String name, ActionType type, ActionParameters p) {
        return StepDraft.builder().name(name).actionType(type).parameters(p).build();
    }

    static StepDraft withElement(String name, ActionType type, ElementReference e) {
        return StepDraft.builder().name(name).actionType(type).element(e).build();
    }

    static StepDraft withElement(String name, ActionType type, ElementReference e, ActionParameters p) {
        return StepDraft.builder().name(name).actionType(type).element(e).parameters(p).build();
    }

    /** Localizador candidato. */
    static LocatorCandidate locator(LocatorStrategy strategy, LocatorStability stability, int matches, boolean target) {
        return new LocatorCandidate(strategy, "botão Entrar", "#entrar", stability, matches, target);
    }

    /** Elemento com um localizador VALIDADO e preferencial (estável). */
    static ElementReference good() {
        return ElementReference.builder().accessibleName("Entrar").semanticKind("BUTTON").tag("button")
                .addLocator(locator(LocatorStrategy.ID, LocatorStability.OBSERVED_STABLE, 1, true)).preferLastLocator()
                .privacyPolicy("nekkora-privacy/2").build();
    }

    /** Campo sensível (senha) com localizador validado. */
    static ElementReference password() {
        return ElementReference.builder().accessibleName("Senha").semanticKind("TEXT_INPUT").tag("input").sensitive(true)
                .addLocator(locator(LocatorStrategy.LABEL, LocatorStability.HEURISTIC_STABLE, 1, true)).preferLastLocator().build();
    }

    /** Etapa GRAVADA (Capturar e interagir), com limitações declaradas pelo Recorder. */
    static StepDraft recorded(String name, ActionType type, String eventId, ElementReference e, String... limitations) {
        CorrelationKeys keys = CorrelationKeys.of(Map.of(CorrelationKeys.Key.RECORDING_ID, "rec-1", CorrelationKeys.Key.RECORDED_EVENT_ID, eventId));
        RecordingObservation obs = new RecordingObservation(RecordingObservation.CaptureMode.CAPTURE_AND_INTERACT,
                RecordingObservation.Observation.INTERACTION_OBSERVED, null, List.of(), List.of(limitations));
        StepDraft.Builder b = StepDraft.builder().name(name).actionType(type).provenance(StepProvenance.recorded(keys, obs));
        if (e != null) b.element(e);
        return b.build();
    }

    /** Evidência fictícia (STUDIO-005). */
    static ObservedEvidence evidence(String id, long seq, String eventId, EvidenceType type, Limitation... lim) {
        boolean ambiguous = type == EvidenceType.AMBIGUOUS_FEEDBACK; // sem etapa definida: eventId nulo
        return new ObservedEvidence(id, seq, "rec-1", ambiguous ? null : eventId, T.plusMillis(seq * 100), type, Source.FEEDBACK_OBSERVER, Relation.AFTER_ACTION,
                ambiguous ? Confidence.AMBIGUOUS : type == EvidenceType.OBSERVATION_INCOMPLETE ? Confidence.INCOMPLETE : Confidence.TEMPORALLY_ASSOCIATED,
                type == EvidenceType.VISIBLE_ERROR_MESSAGE ? Classification.ERROR_INDICATOR : null, null, "alerta do formulário",
                type == EvidenceType.VISIBLE_ERROR_MESSAGE ? ObservedEvidence.Message.suppressed() : ObservedEvidence.Message.none(), null, null, null,
                List.of(Signal.ROLE_ALERT), ambiguous ? 2 : 0, 0, List.of(lim));
    }
}
