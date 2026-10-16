package com.nekkora.studio.domain.variable;

/**
 * Quem sustenta o significado declarado de uma variável (STUDIO-008). Distingue o que o desenvolvedor CONFIRMOU do que
 * é só uma proposta ainda não confirmada e da ausência de especificação. Nenhum produtor automático (IA) existe nesta
 * missão: uma proposta é o significado escolhido e ainda não confirmado pelo próprio desenvolvedor.
 */
public enum SemanticAuthority {
    USER_CONFIRMED("Confirmado pelo desenvolvedor"),
    PROPOSED_UNCONFIRMED("Proposto, não confirmado"),
    UNSPECIFIED("Sem especificação");

    private final String label;

    SemanticAuthority(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
