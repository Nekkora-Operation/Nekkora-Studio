package com.nekkora.studio.domain.variable;

/**
 * Tipo TÉCNICO de uma variável (STUDIO-008): como o valor é representado. Identificadores com zeros à esquerda (ex.: CPF)
 * são {@link #STRING}; valores monetários são {@link #DECIMAL} com escala explícita (nunca ponto flutuante binário).
 */
public enum VariableType {
    STRING("Texto"),
    INTEGER("Número inteiro"),
    DECIMAL("Número decimal"),
    BOOLEAN("Verdadeiro ou falso"),
    DATE("Data");

    private final String label;

    VariableType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean numeric() {
        return this == INTEGER || this == DECIMAL;
    }
}
