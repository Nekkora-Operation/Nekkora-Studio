package com.nekkora.studio.domain.action;

import java.util.Objects;

/**
 * Opção escolhida em uma ação {@link ActionType#SELECT_OPTION}. É um parâmetro subordinado à mesma
 * etapa — "Selecionar São Paulo no campo Estado" é <b>uma</b> etapa cujo alvo é o {@code select} e cuja
 * opção é {@code São Paulo}/{@code SP}; o {@code option} não vira uma etapa separada.
 *
 * @param label texto visível da opção (ex.: "São Paulo")
 * @param value valor técnico da opção, quando conhecido (ex.: "SP"); pode ser vazio
 */
public record OptionChoice(String label, String value) {
    public OptionChoice {
        Objects.requireNonNull(label, "label");
        value = value == null ? "" : value;
    }
}
