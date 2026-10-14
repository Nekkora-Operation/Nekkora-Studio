package com.nekkora.studio.app.workspace;

import java.util.Objects;

/** Notificação ao usuário sobre uma operação <b>realmente</b> concluída (nunca sobre uma simulação). */
public record Notice(Kind kind, String title, String message) {
    public enum Kind { SUCCESS, INFO, WARNING, ERROR }

    public Notice {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(title, "title");
    }
}
