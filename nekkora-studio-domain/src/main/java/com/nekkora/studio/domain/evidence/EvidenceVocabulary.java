package com.nekkora.studio.domain.evidence;

/**
 * Vocabulários fechados de uma evidência observada (STUDIO-005). Os valores são os do contrato {@code evidence/1}; os
 * rótulos são para o usuário (sem códigos internos).
 */
public final class EvidenceVocabulary {

    private EvidenceVocabulary() {
    }

    /** Quem produziu a evidência. */
    public enum Source {
        INTERACTION_NORMALIZER("Sensor de interações"),
        FEEDBACK_OBSERVER("Observador de mensagens"),
        FORM_OBSERVER("Observador de formulários"),
        NAVIGATION_OBSERVER("Observador de navegação"),
        RECORDER("Recorder");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Relação temporal com a ação. */
    public enum Relation {
        AT_ACTION("No momento da ação"),
        AFTER_ACTION("Depois da ação");

        private final String label;

        Relation(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Confiança da associação com a etapa — sem percentuais. */
    public enum Confidence {
        DIRECTLY_OBSERVED("Observada diretamente"),
        TEMPORALLY_ASSOCIATED("Associada pelo tempo"),
        AMBIGUOUS("Ambígua"),
        INCOMPLETE("Incompleta");

        private final String label;

        Confidence(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Classificação do aviso pelos sinais da página (nunca pelo texto, nunca só por classe CSS). */
    public enum Classification {
        ERROR_INDICATOR("Indicador de erro"),
        STATUS_INDICATOR("Indicador de status"),
        VALIDATION_INDICATOR("Indicador de validação"),
        UNKNOWN_FEEDBACK("Aviso sem classificação");

        private final String label;

        Classification(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Sinal técnico observado na página. */
    public enum Signal {
        ROLE_ALERT("role=alert"),
        ROLE_ALERTDIALOG("role=alertdialog"),
        ROLE_STATUS("role=status"),
        ARIA_LIVE_ASSERTIVE("aria-live=assertive"),
        ARIA_LIVE_POLITE("aria-live=polite"),
        ARIA_INVALID("aria-invalid"),
        NATIVE_INVALID("validação nativa"),
        ARIA_ERRORMESSAGE("aria-errormessage"),
        ERROR_HINT("indício de erro no elemento"),
        ARIA_BUSY("aria-busy"),
        ROLE_PROGRESSBAR("role=progressbar"),
        SUBMIT_EVENT("evento de envio"),
        HISTORY_API("History API"),
        NEW_DOCUMENT("novo documento"),
        TARGET_ATTRIBUTE("atributo do elemento acionado");

        private final String label;

        Signal(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Limitação declarada da evidência. */
    public enum Limitation {
        MESSAGE_SUPPRESSED("Texto omitido por privacidade"),
        WINDOW_CUT_BY_NAVIGATION("Observação interrompida pela navegação"),
        WINDOW_CUT_BY_PAUSE("Observação interrompida pela pausa"),
        WINDOW_CUT_BY_STOP("Observação interrompida pelo encerramento"),
        EVIDENCE_LIMIT_REACHED("Limite de evidências atingido"),
        CROSS_DOCUMENT_NOT_ATTRIBUTED("Outro documento (iframe)"),
        PARTIAL_COVERAGE("Cobertura parcial"),
        ORDER_BY_RECEPTION("Ordem por recepção");

        private final String label;

        Limitation(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** A janela de observação foi cortada antes do fim. */
        public boolean cut() {
            return this == WINDOW_CUT_BY_NAVIGATION || this == WINDOW_CUT_BY_PAUSE || this == WINDOW_CUT_BY_STOP;
        }
    }

    /** Atributo de estado observado no elemento acionado. */
    public enum StateAttribute {
        ARIA_EXPANDED("aria-expanded", "expandido"),
        ARIA_PRESSED("aria-pressed", "pressionado"),
        ARIA_CHECKED("aria-checked", "marcado"),
        ARIA_SELECTED("aria-selected", "selecionado"),
        ARIA_DISABLED("aria-disabled", "desabilitado"),
        DISABLED("disabled", "desabilitado");

        private final String wire;
        private final String label;

        StateAttribute(String wire, String label) {
            this.wire = wire;
            this.label = label;
        }

        public String wire() {
            return wire;
        }

        public String label() {
            return label;
        }

        public static StateAttribute ofWire(String w) {
            for (StateAttribute a : values()) {
                if (a.wire.equals(w)) {
                    return a;
                }
            }
            return null;
        }
    }

    /** Valor de um atributo de estado. */
    public enum StateValue {
        TRUE("sim"), FALSE("não"), MIXED("misto"), ABSENT("ausente");

        private final String label;

        StateValue(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public static StateValue ofWire(String w) {
            return switch (String.valueOf(w)) {
                case "true" -> TRUE;
                case "false" -> FALSE;
                case "mixed" -> MIXED;
                case "absent" -> ABSENT;
                default -> null;
            };
        }
    }
}
