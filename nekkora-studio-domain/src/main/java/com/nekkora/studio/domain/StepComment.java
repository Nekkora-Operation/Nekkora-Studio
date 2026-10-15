package com.nekkora.studio.domain;

import com.nekkora.studio.domain.privacy.DisplaySafety;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Comentário livre sobre uma etapa. Gerenciado pelas operações de {@link Automation} (STUDIO-004): o texto passa por
 * {@link #requireText} (limite, caracteres de controle, aparência de dado sensível) e a data de criação é preservada nas
 * edições. Não há autor: o Studio ainda não tem login e não presume identidade.
 *
 * @param text      texto do comentário
 * @param createdAt quando foi criado (nunca muda)
 * @param editedAt  última edição, ou nulo se nunca editado
 */
public record StepComment(String text, Instant createdAt, Instant editedAt) {
    public static final int TEXT_MAX = 1000;

    public StepComment {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public StepComment(String text, Instant createdAt) {
        this(text, createdAt, null);
    }

    public Optional<Instant> edited() {
        return Optional.ofNullable(editedAt);
    }

    /**
     * Texto normalizado de um comentário ou {@link DomainValidationException}: obrigatório, até {@link #TEXT_MAX}
     * caracteres, quebras de linha permitidas, demais caracteres de controle recusados, e nada com aparência de dado
     * sensível (e-mail, CPF, cartão, token) — um comentário não é lugar para segredos.
     */
    public static String requireText(String raw) {
        String t = raw == null ? "" : raw.strip();
        if (t.isEmpty()) throw new DomainValidationException(ValidationProblem.COMMENT_REQUIRED);
        if (t.length() > TEXT_MAX) throw new DomainValidationException(ValidationProblem.COMMENT_TOO_LONG);
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                throw new DomainValidationException(ValidationProblem.COMMENT_INVALID_CHARACTERS);
            }
        }
        if (DisplaySafety.looksSensitive(t)) throw new DomainValidationException(ValidationProblem.COMMENT_LOOKS_SENSITIVE);
        return t.replace("\r\n", "\n").replace('\r', '\n');
    }
}
