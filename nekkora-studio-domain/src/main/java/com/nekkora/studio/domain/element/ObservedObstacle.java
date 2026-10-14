package com.nekkora.studio.domain.element;

import java.util.Objects;

/**
 * Obstáculo de automação observado pelo produtor (ex.: {@code CROSS_ORIGIN_FRAME}, {@code DYNAMIC_IDS}).
 *
 * @param type        valor de {@code AutomationObstacle.type} do PageMap
 * @param severity    gravidade
 * @param description explicação legível
 */
public record ObservedObstacle(String type, Severity severity, String description) {
    /** Mesmos valores de {@code AutomationObstacle.severity}. */
    public enum Severity { LOW, MEDIUM, HIGH }

    public ObservedObstacle {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(severity, "severity");
        description = description == null ? "" : description;
    }
}
