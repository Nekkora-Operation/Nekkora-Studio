package com.nekkora.studio.domain.action;

import java.util.Arrays;
import java.util.List;

/**
 * Catálogo inicial de tipos de ação. Descreve o <b>significado</b> de cada ação — a STUDIO-001 não
 * executa nenhuma delas. Cada tipo declara seus requisitos ({@link TargetRequirement}, valor, opção),
 * de modo que a interface se apoia nesses metadados e um tipo novo não exige mudanças nas telas.
 *
 * <p>As ações não presumem elementos HTML: {@link TargetRequirement#NONE} e {@link TargetRequirement#OPTIONAL}
 * comportam etapas de arquivos, APIs ou sistemas desktop em missões futuras.
 */
public enum ActionType {
    CLICK("Clicar", "Clicar em um elemento.", TargetRequirement.EXPECTED, false, false),
    FILL("Preencher", "Preencher um campo com um valor, variável ou credencial.", TargetRequirement.EXPECTED, true, false),
    SELECT_OPTION("Selecionar opção", "Escolher uma opção em uma lista de seleção.", TargetRequirement.EXPECTED, false, true),
    CHECK("Marcar", "Marcar uma caixa de seleção ou controle equivalente.", TargetRequirement.EXPECTED, false, false),
    UNCHECK("Desmarcar", "Desmarcar uma caixa de seleção ou controle equivalente.", TargetRequirement.EXPECTED, false, false),
    /**
     * STUDIO-003: tecla com efeito próprio num elemento (ex.: Enter num campo, que envia o formulário). A tecla é o valor
     * da ação ({@link ValueSource.LiteralValue}). Acrescentado para registrar o que foi efetivamente observado em vez de
     * inventar um clique — um envio por Enter não é um clique em "Entrar".
     */
    PRESS_KEY("Pressionar tecla", "Pressionar uma tecla num elemento (ex.: Enter num campo).", TargetRequirement.EXPECTED, true, false),
    NAVIGATE("Navegar", "Abrir uma página, tela ou endereço.", TargetRequirement.NONE, true, false),
    WAIT_FOR("Aguardar", "Aguardar até que uma condição seja atendida.", TargetRequirement.OPTIONAL, false, false),
    OBSERVE("Observar", "Registrar uma observação sobre o processo.", TargetRequirement.OPTIONAL, false, false),
    UNKNOWN("Não classificada", "Ação ainda não classificada.", TargetRequirement.OPTIONAL, true, true);

    private final String label;
    private final String description;
    private final TargetRequirement target;
    private final boolean acceptsValue;
    private final boolean acceptsOption;

    ActionType(String label, String description, TargetRequirement target, boolean acceptsValue, boolean acceptsOption) {
        this.label = label;
        this.description = description;
        this.target = target;
        this.acceptsValue = acceptsValue;
        this.acceptsOption = acceptsOption;
    }

    /** Rótulo curto para o usuário (ex.: "Preencher"). */
    public String label() {
        return label;
    }

    /** Explicação do significado da ação. */
    public String description() {
        return description;
    }

    public TargetRequirement target() {
        return target;
    }

    /** Aceita um valor ({@link ValueSource}) — preencher campo, endereço de navegação. */
    public boolean acceptsValue() {
        return acceptsValue;
    }

    /** Aceita uma opção escolhida ({@link OptionChoice}) subordinada à mesma etapa. */
    public boolean acceptsOption() {
        return acceptsOption;
    }

    /** Tipos oferecidos na criação manual ({@link #UNKNOWN} fica reservado a origens automáticas). */
    public static List<ActionType> manualChoices() {
        return Arrays.stream(values()).filter(t -> t != UNKNOWN).toList();
    }
}
