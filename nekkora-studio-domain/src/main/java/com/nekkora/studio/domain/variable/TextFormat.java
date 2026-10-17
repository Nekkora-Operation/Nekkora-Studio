package com.nekkora.studio.domain.variable;

import java.util.regex.Pattern;

/**
 * Formato fechado de um texto (restrição verificável — nunca uma expressão livre interpretada como regra executável).
 * {@link #EMAIL_SHAPE} confere só a forma, não a existência do endereço; {@link #BRAZILIAN_CPF_MASKED} confere só a
 * máscara {@code 000.000.000-00}, não os dígitos verificadores. Novos formatos entram no FIM (os nomes são gravados no
 * {@code .nekkora}).
 */
public enum TextFormat {
    ANY("Qualquer texto", null),
    DIGITS("Somente dígitos", Pattern.compile("[0-9]*")),
    LETTERS_AND_DIGITS("Letras e dígitos", Pattern.compile("[\\p{L}0-9]*")),
    EMAIL_SHAPE("Forma de e-mail", Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")),
    /** Representação de CPF com máscara (revisão 1 do PR #11): a alternativa a {@link #DIGITS} (só dígitos). */
    BRAZILIAN_CPF_MASKED("Máscara de CPF (000.000.000-00)", Pattern.compile("[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2}"));

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
