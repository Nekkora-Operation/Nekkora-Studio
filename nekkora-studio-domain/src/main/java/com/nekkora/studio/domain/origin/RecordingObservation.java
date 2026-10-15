package com.nekkora.studio.domain.origin;

import java.util.List;
import java.util.Objects;

/**
 * Como uma etapa GRAVADA foi observada no navegador (STUDIO-003). Separa observação de execução: uma proposta em
 * {@link CaptureMode#CAPTURE_ONLY} não foi executada; uma interação em {@link CaptureMode#CAPTURE_AND_INTERACT} foi
 * encaminhada à página, mas isso não comprova o sucesso da operação de negócio — {@link #businessOutcome()} é sempre
 * {@link BusinessOutcome#NOT_VALIDATED} nesta fase.
 *
 * @param mode            modo de captura em que a etapa foi registrada
 * @param observation     o que de fato aconteceu com a ação
 * @param businessOutcome resultado de negócio (não validado pelo Recorder)
 * @param evidence        códigos de evidência observada (ex.: {@code FORM_SUBMITTED}); sem conteúdo da página
 * @param limitations     códigos de limitação (ex.: {@code AMBIGUOUS_TARGET}, {@code ELEMENT_UNRESOLVED})
 */
public record RecordingObservation(CaptureMode mode, Observation observation, BusinessOutcome businessOutcome, List<String> evidence,
                                   List<String> limitations) {

    /** Modo de captura da gravação. */
    public enum CaptureMode {
        CAPTURE_ONLY("Capturar"),
        CAPTURE_AND_INTERACT("Capturar e interagir");

        private final String label;

        CaptureMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** O que aconteceu com a ação no navegador. */
    public enum Observation {
        /** Elemento selecionado sem executar a ação (CAPTURE_ONLY). */
        PROPOSED("Proposta — a ação não foi executada"),
        /** Interação real encaminhada à página (CAPTURE_AND_INTERACT). */
        INTERACTION_OBSERVED("Interação observada e encaminhada à página");

        private final String label;

        Observation(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Resultado da operação de negócio. */
    public enum BusinessOutcome {
        NOT_VALIDATED("Não validado");

        private final String label;

        BusinessOutcome(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public RecordingObservation {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(observation, "observation");
        businessOutcome = businessOutcome == null ? BusinessOutcome.NOT_VALIDATED : businessOutcome;
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
        limitations = List.copyOf(limitations == null ? List.of() : limitations);
        if (mode == CaptureMode.CAPTURE_ONLY && observation != Observation.PROPOSED) {
            throw new IllegalArgumentException("em CAPTURE_ONLY a ação nunca é executada");
        }
    }
}
