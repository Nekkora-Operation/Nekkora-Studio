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
 *
 * <p>Significado não é representação (revisão 1 do PR #11): um CPF pode ser escrito só com dígitos ou com máscara, e o
 * significado sozinho não diz qual a etapa espera. {@link #representations()} lista os formatos que FIXAM a
 * representação quando o significado admite mais de uma; o Studio aponta a falta, nunca escolhe uma.
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

    /**
     * Formatos que declaram a representação deste significado quando ele admite mais de uma. Vazio quando a
     * representação é única ou já é exigida por outras restrições: e-mail (uma forma), data ISO 8601 (a própria norma),
     * valor monetário (escala e moeda obrigatórias pelas regras da variável), identificador, texto livre, porcentagem
     * e não especificado (sem representação canônica no vocabulário atual — limitação documentada).
     */
    public Set<TextFormat> representations() {
        return this == BRAZILIAN_CPF ? Set.of(TextFormat.DIGITS, TextFormat.BRAZILIAN_CPF_MASKED) : Set.of();
    }

    /**
     * O formato de texto pode descrever alguma forma válida deste significado (revisão 2 do PR #11). Só o manifestamente
     * contraditório é recusado — nenhuma forma válida do significado satisfaz o formato: e-mail só com dígitos, com letras e
     * dígitos ou com máscara de CPF; CPF com forma de e-mail. Admitir não é declarar a representação (CPF com letras e
     * dígitos é admitido, mas continua sem representação: {@link #representationDeclared}). Significados sem forma
     * conhecida admitem qualquer formato; para CPF e e-mail a lista é explícita (um formato novo entra recusado até ser
     * avaliado).
     */
    public boolean admits(TextFormat format) {
        return switch (this) {
            case BRAZILIAN_CPF -> format == TextFormat.ANY || format == TextFormat.DIGITS || format == TextFormat.LETTERS_AND_DIGITS
                    || format == TextFormat.BRAZILIAN_CPF_MASKED;
            case EMAIL -> format == TextFormat.ANY || format == TextFormat.EMAIL_SHAPE;
            default -> true;
        };
    }

    /** As restrições declaram a representação esperada (sempre verdadeiro quando o significado não exige uma). */
    public boolean representationDeclared(VariableConstraints constraints) {
        Set<TextFormat> accepted = representations();
        return accepted.isEmpty() || accepted.contains(constraints.textFormat());
    }
}
