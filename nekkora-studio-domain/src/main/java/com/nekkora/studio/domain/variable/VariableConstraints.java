package com.nekkora.studio.domain.variable;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Restrições TIPADAS e verificáveis de uma variável (STUDIO-008). Nenhuma é texto livre: comprimento e formato fechado
 * para textos; mínimo e máximo para números (decimais exatos, nunca ponto flutuante binário); escala para decimais; moeda
 * ISO 4217 para valores monetários. A coerência com o tipo é conferida por {@link VariableRules}. {@code length = 11}
 * NÃO garante dígitos verificadores de um CPF.
 *
 * @param required   o valor é obrigatório para quem consome a variável
 * @param minLength  comprimento mínimo (texto)
 * @param maxLength  comprimento máximo (texto)
 * @param textFormat formato fechado (texto)
 * @param minimum    valor mínimo (número)
 * @param maximum    valor máximo (número)
 * @param scale      casas decimais exatas (decimal)
 * @param currency   código ISO 4217 (valor monetário)
 */
public record VariableConstraints(boolean required, Integer minLength, Integer maxLength, TextFormat textFormat, BigDecimal minimum, BigDecimal maximum,
                                  Integer scale, String currency) {
    private static final VariableConstraints NONE = new VariableConstraints(false, null, null, TextFormat.ANY, null, null, null, null);

    public VariableConstraints {
        textFormat = textFormat == null ? TextFormat.ANY : textFormat;
    }

    public static VariableConstraints none() {
        return NONE;
    }

    public Optional<Integer> minLengthValue() {
        return Optional.ofNullable(minLength);
    }

    public Optional<Integer> maxLengthValue() {
        return Optional.ofNullable(maxLength);
    }

    public Optional<BigDecimal> minimumValue() {
        return Optional.ofNullable(minimum);
    }

    public Optional<BigDecimal> maximumValue() {
        return Optional.ofNullable(maximum);
    }

    public Optional<Integer> scaleValue() {
        return Optional.ofNullable(scale);
    }

    public Optional<String> currencyCode() {
        return Optional.ofNullable(currency);
    }

    @Override
    public boolean equals(Object o) {
        // decimais comparados pelo VALOR NUMÉRICO e pela escala escrita (1.0 ≠ 1.00 é uma diferença de declaração)
        return o instanceof VariableConstraints c && required == c.required && Objects.equals(minLength, c.minLength)
                && Objects.equals(maxLength, c.maxLength) && textFormat == c.textFormat && Objects.equals(minimum, c.minimum)
                && Objects.equals(maximum, c.maximum) && Objects.equals(scale, c.scale) && Objects.equals(currency, c.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(required, minLength, maxLength, textFormat, minimum, maximum, scale, currency);
    }
}
