package com.nekkora.studio.domain;

/**
 * Campos editáveis de uma etapa que têm valor original quando a etapa foi gravada (STUDIO-004). É por eles que o
 * Studio distingue o que foi capturado do que foi alterado depois, e é sobre eles que "restaurar o original" atua.
 * Comentários e localizadores propostos manualmente não têm valor capturado: são sempre intervenções manuais.
 */
public enum StepField {
    NAME("Nome"),
    DESCRIPTION("Descrição"),
    ACTION_TYPE("Tipo de ação"),
    PARAMETERS("Parâmetros"),
    PREFERRED_LOCATOR("Localizador preferencial");

    private final String label;

    StepField(String label) {
        this.label = label;
    }

    /** Nome do campo para o usuário. */
    public String label() {
        return label;
    }
}
