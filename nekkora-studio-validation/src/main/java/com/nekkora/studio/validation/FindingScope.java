package com.nekkora.studio.validation;

/** Onde o apontamento está: na automação, numa sessão, numa etapa ou (regras 1.1.0) numa variável declarada. */
public enum FindingScope {
    AUTOMATION, SESSION, STEP,
    /** STUDIO-008 (regras 1.1.0). */
    VARIABLE;

    /** Escopos de uma versão das regras. */
    public static java.util.List<FindingScope> of(RuleSet rules) {
        return rules.includes(RuleSet.V1_1) ? java.util.List.of(values()) : java.util.List.of(AUTOMATION, SESSION, STEP);
    }
}
