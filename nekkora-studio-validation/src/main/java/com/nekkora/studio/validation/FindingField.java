package com.nekkora.studio.validation;

/** Parte da automação a que um apontamento se refere (para a navegação e para abrir o editor certo). */
public enum FindingField {
    STRUCTURE("Estrutura"),
    ACTION_TYPE("Tipo de ação"),
    PARAMETERS("Parâmetros"),
    ELEMENT("Elemento"),
    LOCATORS("Localizadores"),
    OBSERVATION("Observação");

    private final String label;

    FindingField(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
