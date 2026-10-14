package com.nekkora.studio.domain;

import java.time.Instant;
import java.util.Objects;

/** Comentário livre sobre uma etapa (a edição de comentários chega na STUDIO-004). */
public record StepComment(String text, Instant createdAt) {
    public StepComment {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
