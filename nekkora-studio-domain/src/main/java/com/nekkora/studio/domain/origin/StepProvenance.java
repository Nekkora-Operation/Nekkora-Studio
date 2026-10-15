package com.nekkora.studio.domain.origin;

import com.nekkora.studio.domain.element.CorrelationKeys;
import java.util.Objects;

/**
 * Procedência de uma etapa: a origem e as chaves de correlação com o produtor (ex.: gravação e captura
 * da Chrome Extension). As chaves são internas — servem para rastrear, nunca para exibir.
 *
 * <p>STUDIO-003: etapas {@link StepOrigin#RECORDED} levam também a {@link RecordingObservation} (modo de captura, se a
 * ação foi executada ou só proposta, resultado de negócio NÃO validado, evidências e limitações). Evolução
 * compatível: o construtor anterior continua válido e produz etapas sem observação de gravação.
 */
public record StepProvenance(StepOrigin origin, CorrelationKeys correlation, RecordingObservation recording) {
    public StepProvenance {
        Objects.requireNonNull(origin, "origin");
        correlation = correlation == null ? CorrelationKeys.none() : correlation;
        if (recording != null && origin != StepOrigin.RECORDED) {
            throw new IllegalArgumentException("observação de gravação só existe em etapa de origem RECORDED");
        }
    }

    public StepProvenance(StepOrigin origin, CorrelationKeys correlation) {
        this(origin, correlation, null);
    }

    /** Etapa gravada pelo Recorder, com a observação do navegador. */
    public static StepProvenance recorded(CorrelationKeys correlation, RecordingObservation observation) {
        return new StepProvenance(StepOrigin.RECORDED, correlation, Objects.requireNonNull(observation, "observation"));
    }

    public java.util.Optional<RecordingObservation> recordingObservation() {
        return java.util.Optional.ofNullable(recording);
    }

    public static StepProvenance manual() {
        return new StepProvenance(StepOrigin.MANUAL, CorrelationKeys.none());
    }

    public static StepProvenance of(StepOrigin origin, CorrelationKeys correlation) {
        return new StepProvenance(origin, correlation);
    }
}
