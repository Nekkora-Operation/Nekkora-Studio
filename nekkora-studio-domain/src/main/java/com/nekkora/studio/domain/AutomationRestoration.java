package com.nekkora.studio.domain;

import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.ManualLocator;
import com.nekkora.studio.domain.evidence.ObservedEvidence;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepOrigin;
import com.nekkora.studio.domain.origin.StepProvenance;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reconstrução CONTROLADA de uma {@link Automation} a partir de um estado já persistido (STUDIO-006, D-S006-05).
 *
 * <p>Diferente de {@link Automation#create} seguido de operações comuns, a reconstrução preserva as identidades
 * ({@link AutomationId}, {@link SessionId}, {@link StepId}, {@code evidenceId}), os instantes de criação e alteração, o
 * original capturado ({@link CapturedBaseline}), a procedência, as revisões manuais e as evidências — e não registra nada
 * como uma intervenção feita durante o carregamento. O histórico de desfazer/refazer não faz parte do estado (D-S006-09).
 *
 * <p>Não é uma porta para estados inválidos: TODAS as regras do domínio são reaplicadas antes de qualquer objeto ser
 * devolvido (nomes, limites, unicidade de identidades e de nomes de sessão, compatibilidade de parâmetros com o tipo,
 * localizador preferencial validado, original só em etapa gravada, evidências só na etapa gravada e executada do mesmo
 * evento, em ordem técnica e sem repetição). Qualquer violação recusa a reconstrução inteira ({@link RestoreException}) —
 * nada é corrigido, descartado ou reordenado em silêncio.
 */
public final class AutomationRestoration {

    private AutomationRestoration() {}

    /** Estado completo de uma automação. */
    public record AutomationData(AutomationId id, String name, String description, AutomationStatus status, Instant createdAt, Instant updatedAt,
                                 String modelVersion, long revision, List<SessionData> sessions, List<ObservedEvidence> unattributedEvidence) {
        public AutomationData {
            sessions = List.copyOf(sessions);
            unattributedEvidence = List.copyOf(unattributedEvidence);
        }
    }

    /** Sessão lógica com as etapas na ordem. */
    public record SessionData(SessionId id, String name, String description, String notes, List<StepData> steps) {
        public SessionData {
            steps = List.copyOf(steps);
        }
    }

    /**
     * Etapa: a parte ORIGINAL (imutável depois da criação) e a parte EFETIVA (editável).
     *
     * @param capturedElement  referência do elemento como recebida (ou nula)
     * @param baseline         valores capturados (só em etapa {@link StepOrigin#RECORDED})
     * @param preferredLocator localizador preferencial EFETIVO (índice em {@code capturedElement}), ou -1
     * @param edits            instante da última alteração manual de cada campo
     * @param lastManualChange última alteração manual (ou nula)
     * @param evidence         evidências observadas, em ordem técnica
     */
    public record StepData(StepId id, Instant createdAt, StepProvenance provenance, ElementReference capturedElement, StepContext context,
                           String observedOutcome, CapturedBaseline baseline, StepId derivedFrom, String name, String description, ActionType actionType,
                           ActionParameters parameters, int preferredLocator, List<ManualLocator> manualLocators, List<StepComment> comments,
                           Map<StepField, Instant> edits, Instant lastManualChange, List<ObservedEvidence> evidence) {
        public StepData {
            manualLocators = List.copyOf(manualLocators);
            comments = List.copyOf(comments);
            edits = Map.copyOf(edits);
            evidence = List.copyOf(evidence);
        }
    }

    /**
     * A reconstrução foi recusada. {@link #reason()} é um código estável; {@link #where()} indica a posição no estado
     * (ex.: {@code sessions[1].steps[3].preferredLocator}) — nunca o conteúdo.
     */
    public static final class RestoreException extends RuntimeException {
        private final String reason;
        private final String where;

        RestoreException(String reason, String where) {
            super(reason + " em " + where);
            this.reason = reason;
            this.where = where;
        }

        public String reason() {
            return reason;
        }

        public String where() {
            return where;
        }
    }

    /** Estado das etapas de um alvo, para conferir as evidências atribuídas. */
    private record EventKey(String recordingId, String eventId) {}

    /**
     * Reconstrói a automação. O relógio é o do catálogo que vai recebê-la (operações futuras); não altera nenhum
     * instante restaurado.
     *
     * @throws RestoreException se qualquer regra for violada (nada é devolvido)
     */
    public static Automation restore(AutomationData d, Clock clock) {
        Objects.requireNonNull(d, "data");
        Objects.requireNonNull(clock, "clock");
        String where = "automation";
        check(d.id() != null, "ID_REQUIRED", where + ".id");
        check(ModelVersion.CURRENT.equals(d.modelVersion()), "MODEL_VERSION_UNSUPPORTED", where + ".modelVersion");
        check(d.status() == AutomationStatus.DRAFT, "STATUS_UNSUPPORTED", where + ".status");
        check(d.createdAt() != null && d.updatedAt() != null && !d.updatedAt().isBefore(d.createdAt()), "TIMESTAMPS_INVALID", where + ".updatedAt");
        check(d.revision() >= 0, "REVISION_INVALID", where + ".revision");
        String name = name(d.name(), where + ".name");
        String description = description(d.description(), where + ".description");

        Automation a = Automation.restored(d.id(), name, description, d.createdAt(), d.updatedAt(), d.revision(), clock);
        Set<SessionId> sessionIds = new HashSet<>();
        Set<StepId> stepIds = new HashSet<>();
        Set<String> evidenceIds = new HashSet<>();
        Set<EventKey> events = new HashSet<>();
        List<String> sessionNames = new ArrayList<>();
        for (int s = 0; s < d.sessions().size(); s++) {
            SessionData sd = d.sessions().get(s);
            String sw = "sessions[" + s + "]";
            check(sd != null && sd.id() != null, "ID_REQUIRED", sw + ".id");
            check(sessionIds.add(sd.id()), "DUPLICATE_ID", sw + ".id");
            String sn = name(sd.name(), sw + ".name");
            for (String other : sessionNames) check(!Texts.sameName(other, sn), "SESSION_NAME_IN_USE", sw + ".name");
            sessionNames.add(sn);
            LogicalSession session = new LogicalSession(sd.id(), sn, description(sd.description(), sw + ".description"));
            session.annotate(description(sd.notes(), sw + ".notes"));
            for (int i = 0; i < sd.steps().size(); i++) {
                StepData st = sd.steps().get(i);
                String tw = sw + ".steps[" + i + "]";
                check(st != null && st.id() != null, "ID_REQUIRED", tw + ".id");
                check(stepIds.add(st.id()), "DUPLICATE_ID", tw + ".id");
                session.append(step(st, tw, evidenceIds, events));
            }
            a.adoptSession(session);
        }
        check(d.unattributedEvidence().size() <= Automation.MAX_UNATTRIBUTED, "EVIDENCE_LIMIT", "unattributedEvidence");
        for (int i = 0; i < d.unattributedEvidence().size(); i++) {
            ObservedEvidence e = d.unattributedEvidence().get(i);
            String ew = "unattributedEvidence[" + i + "]";
            check(e != null && !e.attributed(), "EVIDENCE_NOT_UNATTRIBUTED", ew);
            check(evidenceIds.add(e.evidenceId()), "DUPLICATE_ID", ew + ".evidenceId");
            a.adoptUnattributed(e);
        }
        return a;
    }

    private static AutomationStep step(StepData st, String where, Set<String> evidenceIds, Set<EventKey> events) {
        check(st.createdAt() != null, "TIMESTAMPS_INVALID", where + ".createdAt");
        check(st.provenance() != null, "PROVENANCE_REQUIRED", where + ".provenance");
        StepProvenance p = st.provenance();
        boolean recorded = p.origin() == StepOrigin.RECORDED;
        // original: só uma etapa gravada tem valores capturados; e só ela tem a observação da gravação
        check(recorded == (st.baseline() != null), "BASELINE_INCONSISTENT", where + ".baseline");
        check(recorded == p.recordingObservation().isPresent(), "PROVENANCE_INCONSISTENT", where + ".provenance");
        check(st.derivedFrom() == null || (!st.derivedFrom().equals(st.id()) && p.origin() == StepOrigin.MANUAL), "DERIVED_FROM_INVALID",
                where + ".derivedFrom");
        if (recorded) {
            String rec = p.correlation().get(CorrelationKeys.Key.RECORDING_ID).orElse(null);
            String evt = p.correlation().get(CorrelationKeys.Key.RECORDED_EVENT_ID).orElse(null);
            check(rec != null && evt != null, "PROVENANCE_INCONSISTENT", where + ".provenance.correlation");
            check(events.add(new EventKey(rec, evt)), "DUPLICATE_RECORDED_EVENT", where + ".provenance.correlation");
            CapturedBaseline b = st.baseline();
            name(b.name(), where + ".baseline.name");
            description(b.description(), where + ".baseline.description");
            check(preferredValid(st.capturedElement(), b.preferredLocator()), "LOCATOR_NOT_FOUND", where + ".baseline.preferredLocator");
            // o original do preferencial é o do elemento recebido na captura (como na criação da etapa)
            check(b.preferredLocator() == (st.capturedElement() == null ? -1 : st.capturedElement().preferredLocatorIndex()), "BASELINE_INCONSISTENT",
                    where + ".baseline.preferredLocator");
        }
        String n = name(st.name(), where + ".name");
        String desc = description(st.description(), where + ".description");
        check(st.actionType() != null && st.parameters() != null, "ACTION_REQUIRED", where + ".actionType");
        check(preferredValid(st.capturedElement(), st.preferredLocator()), "LOCATOR_NOT_FOUND", where + ".preferredLocator");
        ElementReference effective = st.capturedElement() == null ? null : st.capturedElement().withPreferredLocator(st.preferredLocator());
        try {
            Automation.checkParameters(st.actionType(), st.parameters(), effective);
        } catch (DomainValidationException e) {
            throw new RestoreException(e.problem().name(), where + ".parameters");
        }
        check(st.manualLocators().isEmpty() || st.capturedElement() != null, "STEP_HAS_NO_ELEMENT", where + ".manualLocators");
        for (int c = 0; c < st.comments().size(); c++) {
            StepComment sc = st.comments().get(c);
            String cw = where + ".comments[" + c + "]";
            check(sc != null, "COMMENT_REQUIRED", cw);
            String text;
            try {
                text = StepComment.requireText(sc.text());
            } catch (DomainValidationException e) {
                throw new RestoreException(e.problem().name(), cw + ".text");
            }
            check(text.equals(sc.text()), "TEXT_NOT_NORMALIZED", cw + ".text");
            check(sc.editedAt() == null || !sc.editedAt().isBefore(sc.createdAt()), "TIMESTAMPS_INVALID", cw + ".editedAt");
        }
        // evidências: só na etapa gravada e EXECUTADA do mesmo evento, ordem técnica, sem repetição, no limite
        boolean executed = p.recordingObservation().map(o -> o.observation() == RecordingObservation.Observation.INTERACTION_OBSERVED).orElse(false);
        check(st.evidence().isEmpty() || executed, "EVIDENCE_NOT_APPLICABLE", where + ".evidence");
        check(st.evidence().size() <= Automation.MAX_EVIDENCE_PER_STEP, "EVIDENCE_LIMIT", where + ".evidence");
        long previous = Long.MIN_VALUE;
        for (int e = 0; e < st.evidence().size(); e++) {
            ObservedEvidence ev = st.evidence().get(e);
            String ew = where + ".evidence[" + e + "]";
            check(ev != null && ev.attributed(), "EVIDENCE_NOT_ATTRIBUTED", ew);
            check(ev.recordingId().equals(p.correlation().get(CorrelationKeys.Key.RECORDING_ID).orElse(null))
                    && ev.eventId().equals(p.correlation().get(CorrelationKeys.Key.RECORDED_EVENT_ID).orElse(null)), "EVIDENCE_STEP_MISMATCH", ew);
            check(ev.sequence() >= previous, "EVIDENCE_ORDER", ew + ".sequence");
            previous = ev.sequence();
            check(evidenceIds.add(ev.evidenceId()), "DUPLICATE_ID", ew + ".evidenceId");
        }
        AutomationStep.State state = new AutomationStep.State(n, desc, st.actionType(), st.parameters(), effective, st.manualLocators(), st.comments(),
                st.edits(), st.lastManualChange());
        return new AutomationStep(st.id(), p, st.capturedElement(), st.context(), st.observedOutcome(), st.createdAt(), st.baseline(), st.derivedFrom(),
                state, st.evidence());
    }

    private static boolean preferredValid(ElementReference captured, int index) {
        if (captured == null) return index == -1;
        if (index < -1 || index >= captured.locators().size()) return false;
        return index == -1 || captured.locators().get(index).validated();
    }

    /** O nome precisa estar exatamente como o domínio o guardaria (normalizado). */
    private static String name(String raw, String where) {
        try {
            String n = Texts.requireName(raw);
            check(n.equals(raw), "TEXT_NOT_NORMALIZED", where);
            return n;
        } catch (DomainValidationException e) {
            throw new RestoreException(e.problem().name(), where);
        }
    }

    private static String description(String raw, String where) {
        check(raw != null, "TEXT_REQUIRED", where);
        try {
            String d = Texts.optionalDescription(raw);
            check(d.equals(raw), "TEXT_NOT_NORMALIZED", where);
            return d;
        } catch (DomainValidationException e) {
            throw new RestoreException(e.problem().name(), where);
        }
    }

    private static void check(boolean ok, String reason, String where) {
        if (!ok) throw new RestoreException(reason, where);
    }
}
