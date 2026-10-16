package com.nekkora.studio.domain.variable;

import java.util.EnumSet;
import java.util.Set;

/**
 * Significado de uma variável (STUDIO-008) — vocabulário FECHADO e versionado com o modelo. Nunca é inferido pelo nome:
 * só existe quando o desenvolvedor o declara, e só vale como especificação quando ele o confirma
 * ({@link SemanticStatus#USER_CONFIRMED}).
 *
 * <p>Cada significado declara os tipos técnicos compatíveis e a sensibilidade MÍNIMA (regra conservadora: um CPF ou um
 * e-mail é dado pessoal — logo nunca pode ser uma constante gravada no projeto).
 */
public enum SemanticType {
    UNSPECIFIED("Não especificado", EnumSet.allOf(VariableType.class), Sensitivity.PUBLIC),
    FREE_TEXT("Texto livre", EnumSet.of(VariableType.STRING), Sensitivity.PUBLIC),
    IDENTIFIER("Identificador", EnumSet.of(VariableType.STRING, VariableType.INTEGER), Sensitivity.PUBLIC),
    BRAZILIAN_CPF("CPF", EnumSet.of(VariableType.STRING), Sensitivity.PERSONAL_DATA),
    EMAIL("E-mail", EnumSet.of(VariableType.STRING), Sensitivity.PERSONAL_DATA),
    CURRENCY("Valor monetário", EnumSet.of(VariableType.DECIMAL), Sensitivity.PUBLIC),
    PERCENTAGE("Porcentagem", EnumSet.of(VariableType.DECIMAL, VariableType.INTEGER), Sensitivity.PUBLIC),
    DATE_ISO("Data (ISO 8601)", EnumSet.of(VariableType.DATE), Sensitivity.PUBLIC);

    private final String label;
    private final Set<VariableType> types;
    private final Sensitivity minimumSensitivity;

    SemanticType(String label, Set<VariableType> types, Sensitivity minimumSensitivity) {
        this.label = label;
        this.types = types;
        this.minimumSensitivity = minimumSensitivity;
    }

    public String label() {
        return label;
    }

    /** O tipo técnico pode carregar este significado. */
    public boolean accepts(VariableType type) {
        return types.contains(type);
    }

    public Set<VariableType> types() {
        return Set.copyOf(types);
    }

    /** Sensibilidade mínima exigida para este significado. */
    public Sensitivity minimumSensitivity() {
        return minimumSensitivity;
    }
}
