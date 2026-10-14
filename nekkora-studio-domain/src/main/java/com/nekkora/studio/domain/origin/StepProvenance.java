package com.nekkora.studio.domain.origin;

import com.nekkora.studio.domain.element.CorrelationKeys;
import java.util.Objects;

/**
 * Procedência de uma etapa: a origem e as chaves de correlação com o produtor (ex.: gravação e captura
 * da Chrome Extension). As chaves são internas — servem para rastrear, nunca para exibir.
 */
public record StepProvenance(StepOrigin origin, CorrelationKeys correlation) {
    public StepProvenance {
        Objects.requireNonNull(origin, "origin");
        correlation = correlation == null ? CorrelationKeys.none() : correlation;
    }

    public static StepProvenance manual() {
        return new StepProvenance(StepOrigin.MANUAL, CorrelationKeys.none());
    }

    public static StepProvenance of(StepOrigin origin, CorrelationKeys correlation) {
        return new StepProvenance(origin, correlation);
    }
}
