package com.nekkora.studio.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identidade interna de uma {@link Automation}. Nunca é apresentada ao usuário: {@link #toString()}
 * não revela o valor — use {@link #value()} apenas para correlação e persistência futura.
 */
public record AutomationId(UUID value) {
    public AutomationId {
        Objects.requireNonNull(value, "value");
    }

    public static AutomationId newId() {
        return new AutomationId(UUID.randomUUID());
    }

    public static AutomationId parse(String text) {
        return new AutomationId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return "AutomationId[interno]";
    }
}
