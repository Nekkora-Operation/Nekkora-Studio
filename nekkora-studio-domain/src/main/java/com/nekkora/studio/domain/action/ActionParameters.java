package com.nekkora.studio.domain.action;

import java.util.Objects;
import java.util.Optional;

/**
 * Parâmetros de uma ação: valor (preencher, navegar) e opção escolhida (selecionar). Imutável.
 * A compatibilidade com o {@link ActionType} é verificada pela operação de domínio que cria a etapa.
 */
public final class ActionParameters {
    private static final ActionParameters NONE = new ActionParameters(null, null);

    private final ValueSource value;
    private final OptionChoice option;

    private ActionParameters(ValueSource value, OptionChoice option) {
        this.value = value;
        this.option = option;
    }

    public static ActionParameters none() {
        return NONE;
    }

    public static ActionParameters withValue(ValueSource value) {
        return new ActionParameters(Objects.requireNonNull(value, "value"), null);
    }

    public static ActionParameters withOption(OptionChoice option) {
        return new ActionParameters(null, Objects.requireNonNull(option, "option"));
    }

    public Optional<ValueSource> value() {
        return Optional.ofNullable(value);
    }

    public Optional<OptionChoice> option() {
        return Optional.ofNullable(option);
    }

    public boolean isEmpty() {
        return value == null && option == null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ActionParameters p && Objects.equals(value, p.value) && Objects.equals(option, p.option);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, option);
    }
}
