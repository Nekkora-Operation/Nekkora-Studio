package com.nekkora.studio.validation;

/** Parte da automação a que um apontamento se refere (para a navegação e para abrir o editor certo). */
public enum FindingField {
    STRUCTURE("Estrutura"),
    ACTION_TYPE("Tipo de ação"),
    PARAMETERS("Parâmetros"),
    ELEMENT("Elemento"),
    LOCATORS("Localizadores"),
    OBSERVATION("Observação"),
    /** STUDIO-008 (regras 1.1.0): a declaração da variável. */
    VARIABLE("Variável");

    private final String label;

    FindingField(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Campos de uma versão das regras. */
    public static java.util.List<FindingField> of(RuleSet rules) {
        return java.util.Arrays.stream(values()).filter(f -> f != VARIABLE || rules.includes(RuleSet.V1_1)).toList();
    }
}
