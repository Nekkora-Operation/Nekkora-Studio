package com.nekkora.studio.domain.variable;

/**
 * Classificação de sensibilidade de uma variável (STUDIO-008), em ordem crescente. Variáveis {@link #PERSONAL_DATA} e
 * {@link #SECRET} têm só metadados: nunca uma constante gravada no projeto, em diagnósticos, recuperação, logs ou
 * contratos. Credenciais continuam a ser só apelidos ({@code CredentialReference}), não variáveis.
 */
public enum Sensitivity {
    PUBLIC("Público"),
    INTERNAL("Interno"),
    PERSONAL_DATA("Dados pessoais"),
    SECRET("Segredo");

    private final String label;

    Sensitivity(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Uma constante (valor gravado em claro no projeto) é permitida. */
    public boolean allowsConstant() {
        return this == PUBLIC || this == INTERNAL;
    }

    public boolean atLeast(Sensitivity other) {
        return compareTo(other) >= 0;
    }
}
