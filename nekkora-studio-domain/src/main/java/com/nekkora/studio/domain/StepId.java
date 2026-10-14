package com.nekkora.studio.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identidade interna e permanente de uma {@link AutomationStep}. A numeração vista pelo usuário é
 * calculada a partir da ordem ({@link Automation#sequence()}) e nunca substitui esta identidade.
 * {@link #toString()} não revela o valor.
 */
public record StepId(UUID value) {
    public StepId {
        Objects.requireNonNull(value, "value");
    }

    public static StepId newId() {
        return new StepId(UUID.randomUUID());
    }

    public static StepId parse(String text) {
        return new StepId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return "StepId[interno]";
    }
}
