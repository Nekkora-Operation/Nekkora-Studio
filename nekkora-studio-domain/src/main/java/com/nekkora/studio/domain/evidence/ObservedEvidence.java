package com.nekkora.studio.domain.evidence;

import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Classification;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Confidence;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Limitation;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Relation;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Signal;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Source;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.StateAttribute;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.StateValue;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Evidência OBSERVADA na página depois (ou no momento) de uma interação gravada — STUDIO-005. Imutável: uma vez
 * registrada, nada a altera (o editor não edita evidências; desfazer/refazer não as apaga nem as reaplica).
 *
 * <p>Separa os quatro conceitos da missão: a <b>interação</b> é a etapa; a <b>observação</b> é a janela delimitada;
 * a <b>evidência</b> é este acontecimento; o <b>resultado de negócio</b> não existe aqui — continua
 * {@link com.nekkora.studio.domain.origin.RecordingObservation.BusinessOutcome#NOT_VALIDATED}.
 *
 * <p>Invariantes (4ª barreira, no domínio): texto de mensagem só com {@link Message.Availability#AVAILABLE}, até 160
 * caracteres e seguro para exibir; região, campo e título sem dado sensível; evidência sem etapa só quando AMBÍGUA.
 *
 * @param evidenceId      identidade estável (deduplicação)
 * @param sequence        sequência das evidências da gravação (ordem técnica)
 * @param recordingId     gravação técnica (interna)
 * @param eventId         evento da etapa à qual pertence; vazio = não atribuída (ambígua)
 * @param observedAt      instante real da observação
 * @param type            tipo
 * @param source          produtor
 * @param relation        relação temporal com a ação
 * @param confidence      confiança da associação
 * @param classification  classificação do aviso, quando houver
 * @param document        documento onde foi observada
 * @param region          descrição curta da região (ex.: "alerta do formulário login"), ou null
 * @param message         trecho da mensagem (ou a razão de não haver texto)
 * @param field           nome acessível do campo validado, ou null
 * @param state           mudança de atributo do elemento acionado, ou null
 * @param navigation      navegação observada, ou null
 * @param signals         sinais técnicos que a sustentam
 * @param candidates      etapas candidatas (só ambígua)
 * @param signalsObserved sinais observados na janela (fim de observação)
 * @param limitations     limitações declaradas
 */
public record ObservedEvidence(String evidenceId, long sequence, String recordingId, String eventId, Instant observedAt, EvidenceType type, Source source,
                               Relation relation, Confidence confidence, Classification classification, DocumentContext document, String region,
                               Message message, String field, StateChange state, Navigation navigation, List<Signal> signals, int candidates,
                               int signalsObserved, List<Limitation> limitations) {

    public static final int MESSAGE_MAX = 160;
    public static final int LABEL_MAX = 120;
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._:-]{1,80}");

    /** Documento onde a evidência foi observada. */
    public record DocumentContext(boolean frame, boolean inShadow, String title, String url) {
        public DocumentContext {
            title = label(title);
            url = url == null || url.length() > 2000 || !DisplaySafety.isSafeToDisplay(url) ? null : url;
        }

        public static DocumentContext main() {
            return new DocumentContext(false, false, null, null);
        }
    }

    /** Trecho de mensagem: o texto só existe quando liberado pelas barreiras de privacidade. */
    public record Message(Availability availability, String text) {
        /** Disponibilidade do texto. */
        public enum Availability {
            NONE("Sem texto"), AVAILABLE("Texto disponível"), SUPPRESSED("Texto omitido por privacidade");

            private final String label;

            Availability(String label) {
                this.label = label;
            }

            public String label() {
                return label;
            }
        }

        public Message {
            Objects.requireNonNull(availability, "availability");
            if (availability != Availability.AVAILABLE) {
                if (text != null) {
                    throw new IllegalArgumentException("texto só com AVAILABLE");
                }
            } else if (text == null || text.isBlank() || text.length() > MESSAGE_MAX || !DisplaySafety.isSafeToDisplay(text)) {
                throw new IllegalArgumentException("trecho de mensagem fora da política");
            }
        }

        public static Message none() {
            return new Message(Availability.NONE, null);
        }

        public static Message suppressed() {
            return new Message(Availability.SUPPRESSED, null);
        }

        /** Texto liberado, ou SUPPRESSED quando não passa na política (em caso de dúvida, omite). */
        public static Message of(String text) {
            if (text == null || text.isBlank()) {
                return none();
            }
            String t = text.strip();
            return t.length() <= MESSAGE_MAX && DisplaySafety.isSafeToDisplay(t) && !t.matches("(?s).*\\d{4,}.*") && !looksIdentifying(t)
                    ? new Message(Availability.AVAILABLE, t) : suppressed();
        }

        /**
         * Fail-closed pela forma (revisão do PR #6 da extensão), a mesma regra das outras barreiras: palavra com letras e
         * dígitos, palavra com maiúscula interna ou nome próprio fora do início da frase.
         */
        static boolean looksIdentifying(String text) {
            for (String sentence : text.split("[.!?…]+\\s*")) {
                int i = 0;
                for (String w : sentence.strip().split("[\\s,;:()\"“”'’«»/–—-]+")) {
                    if (w.isEmpty()) {
                        continue;
                    }
                    if (w.matches("(?=.*\\d)(?=.*\\p{L}).*") || w.matches(".*\\p{Ll}\\p{Lu}.*") || (i > 0 && w.matches("\\p{Lu}\\p{Ll}+.*"))) {
                        return true;
                    }
                    i++;
                }
            }
            return false;
        }
    }

    /** Mudança de um atributo de estado do elemento acionado. */
    public record StateChange(StateAttribute attribute, StateValue from, StateValue to) {
        public StateChange {
            Objects.requireNonNull(attribute);
            Objects.requireNonNull(from);
            Objects.requireNonNull(to);
        }
    }

    /** Navegação observada. */
    public record Navigation(boolean newDocument, String url) {
        public Navigation {
            url = url == null || url.length() > 2000 || !DisplaySafety.isSafeToDisplay(url) ? null : url;
        }
    }

    public ObservedEvidence {
        if (evidenceId == null || !ID.matcher(evidenceId).matches()) {
            throw new IllegalArgumentException("evidenceId inválido");
        }
        if (recordingId == null || !ID.matcher(recordingId).matches()) {
            throw new IllegalArgumentException("recordingId inválido");
        }
        if (eventId != null && !ID.matcher(eventId).matches()) {
            throw new IllegalArgumentException("eventId inválido");
        }
        if (sequence < 1) {
            throw new IllegalArgumentException("sequência inválida");
        }
        Objects.requireNonNull(observedAt, "observedAt");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(relation, "relation");
        Objects.requireNonNull(confidence, "confidence");
        document = document == null ? DocumentContext.main() : document;
        message = message == null ? Message.none() : message;
        region = label(region);
        field = label(field);
        signals = List.copyOf(signals == null ? List.of() : signals);
        limitations = List.copyOf(limitations == null ? List.of() : limitations);
        if (signals.size() > 8 || limitations.size() > 8 || candidates < 0 || candidates > 20 || signalsObserved < 0 || signalsObserved > 50) {
            throw new IllegalArgumentException("evidência fora dos limites");
        }
        boolean ambiguous = type == EvidenceType.AMBIGUOUS_FEEDBACK;
        if (ambiguous != (eventId == null)) {
            throw new IllegalArgumentException("só a evidência ambígua fica sem etapa");
        }
        if (ambiguous && confidence != Confidence.AMBIGUOUS) {
            throw new IllegalArgumentException("evidência ambígua tem confiança AMBIGUOUS");
        }
        if (type == EvidenceType.ELEMENT_STATE_CHANGED && state == null) {
            throw new IllegalArgumentException("mudança de estado sem atributo");
        }
    }

    /** Pertence a uma etapa (não ambígua). */
    public boolean attributed() {
        return eventId != null;
    }

    public Optional<String> stepEventId() {
        return Optional.ofNullable(eventId);
    }

    /** É um sinal observado (não o fim da janela de observação). */
    public boolean signal() {
        return !type.windowEnd();
    }

    private static String label(String s) {
        if (s == null) {
            return null;
        }
        String t = s.strip();
        if (t.isEmpty()) {
            return null;
        }
        if (t.length() > LABEL_MAX) {
            t = t.substring(0, LABEL_MAX);
        }
        return DisplaySafety.isSafeToDisplay(t) ? t : null;
    }
}
