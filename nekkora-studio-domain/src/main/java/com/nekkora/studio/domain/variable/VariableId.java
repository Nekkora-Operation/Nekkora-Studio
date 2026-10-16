package com.nekkora.studio.domain.variable;

import java.util.Objects;
import java.util.UUID;

/**
 * Identidade interna e permanente de uma {@link VariableDefinition} (STUDIO-008). Preservada por edição, renomeação,
 * desfazer/refazer e salvamento. {@link #toString()} não revela o valor.
 */
public record VariableId(UUID value) {
    public VariableId {
        Objects.requireNonNull(value, "value");
    }

    public static VariableId newId() {
        return new VariableId(UUID.randomUUID());
    }

    public static VariableId parse(String text) {
        return new VariableId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return "VariableId[interno]";
    }
}
