package com.nekkora.studio.domain.variable;

import java.util.regex.Pattern;

/**
 * Formato fechado de um texto (restrição verificável — nunca uma expressão livre interpretada como regra executável).
 * {@link #EMAIL_SHAPE} confere só a forma, não a existência do endereço.
 */
public enum TextFormat {
    ANY("Qualquer texto", null),
    DIGITS("Somente dígitos", Pattern.compile("[0-9]*")),
    LETTERS_AND_DIGITS("Letras e dígitos", Pattern.compile("[\\p{L}0-9]*")),
    EMAIL_SHAPE("Forma de e-mail", Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"));

    private final String label;
    private final Pattern pattern;

    TextFormat(String label, Pattern pattern) {
        this.label = label;
        this.pattern = pattern;
    }

    public String label() {
        return label;
    }

    public boolean matches(String text) {
        return pattern == null || pattern.matcher(text).matches();
    }
}
