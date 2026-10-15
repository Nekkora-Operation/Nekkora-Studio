package com.nekkora.studio.domain.element;

import com.nekkora.studio.domain.DomainValidationException;
import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import java.time.Instant;
import java.util.Objects;

/**
 * Localizador proposto manualmente pelo usuário (STUDIO-004, D-S004-07). Fica ao lado do
 * {@link ElementReference} capturado, sem alterá-lo, e é sempre {@link Verification#NOT_VERIFIED}: digitar uma expressão
 * não comprova que ela encontra o elemento. Por isso não tem {@code matchCount} nem {@code matchesTarget} e nunca pode
 * ser o localizador preferencial (que exige validação na captura). A verificação ao vivo é de uma missão futura.
 *
 * @param syntax     linguagem da expressão
 * @param expression expressão CSS ou XPath (sem quebras de linha)
 * @param note       observação opcional do usuário
 * @param proposedAt quando foi proposto
 */
public record ManualLocator(Syntax syntax, String expression, String note, Instant proposedAt) {
    public static final int EXPRESSION_MAX = 500;
    public static final int NOTE_MAX = 200;

    /** Linguagem da expressão. */
    public enum Syntax {
        CSS("CSS"), XPATH("XPath");

        private final String label;

        Syntax(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Situação de verificação: um localizador manual nunca é verificado nesta fase. */
    public enum Verification {
        NOT_VERIFIED("Não verificado");

        private final String label;

        Verification(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public ManualLocator {
        Objects.requireNonNull(syntax, "syntax");
        Objects.requireNonNull(proposedAt, "proposedAt");
        expression = expression == null ? "" : expression.strip();
        note = note == null ? "" : note.strip();
        if (expression.isEmpty() || expression.length() > EXPRESSION_MAX || hasControl(expression) || note.length() > NOTE_MAX || hasControl(note)) {
            throw new DomainValidationException(ValidationProblem.MANUAL_LOCATOR_INVALID);
        }
        if (DisplaySafety.looksSensitive(expression) || DisplaySafety.looksSensitive(note)) {
            throw new DomainValidationException(ValidationProblem.MANUAL_LOCATOR_LOOKS_SENSITIVE);
        }
    }

    /** Sempre {@link Verification#NOT_VERIFIED}. */
    public Verification verification() {
        return Verification.NOT_VERIFIED;
    }

    private static boolean hasControl(String s) {
        return s.chars().anyMatch(Character::isISOControl);
    }
}
