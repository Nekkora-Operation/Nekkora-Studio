package com.nekkora.studio.recorder;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.evidence.EvidenceType;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Classification;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Confidence;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Limitation;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Relation;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Signal;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Source;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.StateAttribute;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.StateValue;
import com.nekkora.studio.domain.evidence.ObservedEvidence;
import com.nekkora.studio.domain.evidence.ObservedEvidence.Message;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Leitura ESTRITA do evento {@code studio.recording.evidence} (STUDIO-005). Qualquer campo essencial ausente ou fora do
 * vocabulário fechado descarta o evento (nada é completado por palpite). Campos que o contrato não prevê (HTML, valores,
 * PageMap) não têm onde entrar: só os campos conhecidos são lidos.
 *
 * <p>4ª barreira de privacidade: o trecho de mensagem passa de novo pela política de apresentação do Studio; se não
 * passar, vira {@code SUPPRESSED} com a limitação {@code MESSAGE_SUPPRESSED}. Região, campo, título e URLs passam pela
 * mesma barreira nos construtores do domínio.
 */
public final class RecordedEvidence {

    private static final Set<String> KEYS = Set.of("recordingId", "evidenceId", "evidenceSequence", "eventId", "stepSequence", "observedAt", "type", "source",
            "relation", "confidence", "classification", "document", "details", "limitations");

    private RecordedEvidence() {
    }

    /** Evidência validada, ou vazio quando o evento está fora do contrato. */
    public static Optional<ObservedEvidence> parse(Map<String, Object> p) {
        if (p == null || !KEYS.containsAll(p.keySet())) {
            return Optional.empty();
        }
        try {
            String recordingId = Json.string(p, "recordingId");
            String evidenceId = Json.string(p, "evidenceId");
            long seq = Json.number(p, "evidenceSequence", -1);
            String eventId = Json.string(p, "eventId");
            EvidenceType type = EvidenceType.valueOf(String.valueOf(Json.string(p, "type")));
            Source source = Source.valueOf(String.valueOf(Json.string(p, "source")));
            Relation relation = Relation.valueOf(String.valueOf(Json.string(p, "relation")));
            Confidence confidence = Confidence.valueOf(String.valueOf(Json.string(p, "confidence")));
            String cls = Json.string(p, "classification");
            Classification classification = cls == null ? null : Classification.valueOf(cls);
            Instant at = Instant.parse(String.valueOf(Json.string(p, "observedAt")));
            Map<String, Object> doc = Json.object(p, "document");
            Map<String, Object> det = Json.object(p, "details");
            if (doc == null || det == null || recordingId == null || evidenceId == null || seq < 1) {
                return Optional.empty();
            }
            String frame = Json.string(doc, "frame");
            if (!"MAIN".equals(frame) && !"FRAME".equals(frame)) {
                return Optional.empty();
            }
            ObservedEvidence.DocumentContext document = new ObservedEvidence.DocumentContext("FRAME".equals(frame), Json.bool(doc, "inShadow", false),
                    Json.string(doc, "title"), com.nekkora.studio.domain.privacy.CapturePrivacy.urlTemplate(Json.string(doc, "url")));
            List<Limitation> limitations = new ArrayList<>();
            for (Object o : Json.array(p, "limitations")) {
                limitations.add(Limitation.valueOf(String.valueOf(o)));
            }
            Message message = message(Json.object(det, "message"), limitations);
            if (message == null) {
                return Optional.empty();
            }
            ObservedEvidence.StateChange state = null;
            Map<String, Object> st = Json.object(det, "state");
            if (st != null) {
                StateAttribute a = StateAttribute.ofWire(Json.string(st, "attribute"));
                StateValue from = StateValue.ofWire(Json.string(st, "from"));
                StateValue to = StateValue.ofWire(Json.string(st, "to"));
                if (a == null || from == null || to == null) {
                    return Optional.empty();
                }
                state = new ObservedEvidence.StateChange(a, from, to);
            }
            ObservedEvidence.Navigation navigation = null;
            Map<String, Object> nav = Json.object(det, "navigation");
            if (nav != null) {
                String kind = Json.string(nav, "kind");
                if (!"SAME_DOCUMENT".equals(kind) && !"NEW_DOCUMENT".equals(kind)) {
                    return Optional.empty();
                }
                navigation = new ObservedEvidence.Navigation("NEW_DOCUMENT".equals(kind),
                        com.nekkora.studio.domain.privacy.CapturePrivacy.urlTemplate(Json.string(nav, "url")));
            }
            List<Signal> signals = new ArrayList<>();
            for (Object o : Json.array(det, "signals")) {
                signals.add(Signal.valueOf(String.valueOf(o)));
            }
            return Optional.of(new ObservedEvidence(evidenceId, seq, recordingId, eventId, at, type, source, relation, confidence, classification, document,
                    Json.string(det, "region"), message, Json.string(det, "field"), state, navigation, signals, (int) Json.number(det, "candidates", 0),
                    (int) Json.number(det, "signalsObserved", 0), limitations.size() > 8 ? limitations.subList(0, 8) : limitations));
        } catch (RuntimeException e) {
            return Optional.empty(); // vocabulário desconhecido ou invariante violada: o evento não vira evidência
        }
    }

    private static Message message(Map<String, Object> m, List<Limitation> limitations) {
        if (m == null) {
            return null;
        }
        String availability = Json.string(m, "availability");
        String text = Json.string(m, "text");
        switch (String.valueOf(availability)) {
            case "NONE":
                return text == null ? Message.none() : null;
            case "SUPPRESSED":
                return text == null ? Message.suppressed() : null;
            case "AVAILABLE":
                Message out = Message.of(text);
                if (out.availability() != Message.Availability.AVAILABLE && !limitations.contains(Limitation.MESSAGE_SUPPRESSED)) {
                    limitations.add(Limitation.MESSAGE_SUPPRESSED); // 4ª barreira: em caso de dúvida, omitido
                }
                return out.availability() == Message.Availability.NONE ? Message.suppressed() : out;
            default:
                return null;
        }
    }
}
