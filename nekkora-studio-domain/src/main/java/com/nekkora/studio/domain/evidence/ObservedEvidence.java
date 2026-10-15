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
 * <p>Invariantes (4ª barreira, no domínio — modo estrito, revisão final dos PRs #6): nenhuma mensagem com texto livre da
 * página ({@link Message} só NONE ou SUPPRESSED); região e campo só com descrições CONTROLADAS
 * ({@link EvidenceVocabulary#REGION_LABELS}, {@link EvidenceVocabulary#FIELD_LABELS}), senão {@code null}; título do
 * documento sempre {@code null}; evidência sem etapa só quando AMBÍGUA.
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

    /** Documento onde a evidência foi observada (identificado por frame, Shadow DOM e URL; o título é texto da página). */
    public record DocumentContext(boolean frame, boolean inShadow, String title, String url) {
        public DocumentContext {
            title = null; // modo estrito: o título da página nunca entra no domínio
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
            if (availability == Availability.AVAILABLE) {
                // modo estrito (revisão final dos PRs #6): nenhum caminho do domínio cria mensagem com texto livre da página
                throw new IllegalArgumentException("modo estrito: mensagem com texto livre não é aceita");
            }
            if (text != null) {
                throw new IllegalArgumentException("mensagem sem texto: NONE ou SUPPRESSED");
            }
        }

        public static Message none() {
            return new Message(Availability.NONE, null);
        }

        public static Message suppressed() {
            return new Message(Availability.SUPPRESSED, null);
        }

        /**
         * Mensagem observada pela página, pela política ESTRITA (revisão final do PR #6 da extensão): o texto livre nunca
         * entra no Studio — havendo texto, SUPPRESSED; sem texto, NONE. Nenhuma heurística decide o que é dado pessoal.
         */
        public static Message of(String text) {
            return text == null || text.isBlank() ? none() : suppressed();
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
        region = region != null && EvidenceVocabulary.REGION_LABELS.contains(region) ? region : null;
        field = field != null && EvidenceVocabulary.FIELD_LABELS.contains(field) ? field : null;
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
}
