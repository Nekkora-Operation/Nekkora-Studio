package com.nekkora.studio.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identidade interna de uma {@link LogicalSession}. Não tem relação com os identificadores de
 * sessão de captura/gravação da Chrome Extension. {@link #toString()} não revela o valor.
 */
public record SessionId(UUID value) {
    public SessionId {
        Objects.requireNonNull(value, "value");
    }

    public static SessionId newId() {
        return new SessionId(UUID.randomUUID());
    }

    public static SessionId parse(String text) {
        return new SessionId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return "SessionId[interno]";
    }
}
