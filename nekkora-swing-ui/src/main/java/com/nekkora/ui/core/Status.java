package com.nekkora.ui.core;

import com.nekkora.ui.icon.IconName;

/**
 * Sistema de status genérico. A lib não conhece o domínio: cada aplicação mapeia seus próprios
 * estados (ex.: "execução concluída", "robô parado") para um destes.
 *
 * <p>Status nunca depende só de cor: todo status tem ícone e rótulo.
 */
public enum Status {
    SUCCESS(Tone.SUCCESS, IconName.CHECK_CIRCLE, "Sucesso"),
    RUNNING(Tone.INFO, IconName.LOADER, "Em execução"),
    PENDING(Tone.NEUTRAL, IconName.CLOCK, "Pendente"),
    WARNING(Tone.WARNING, IconName.ALERT, "Atenção"),
    FAILED(Tone.ERROR, IconName.X_CIRCLE, "Falhou"),
    PAUSED(Tone.WARNING, IconName.PAUSE_CIRCLE, "Pausado"),
    DISABLED(Tone.NEUTRAL, IconName.MINUS_CIRCLE, "Desativado"),
    DRAFT(Tone.ACCENT, IconName.EDIT, "Rascunho"),
    INFO(Tone.INFO, IconName.INFO, "Informação");

    private final Tone tone;
    private final IconName icon;
    private final String label;

    Status(Tone tone, IconName icon, String label) {
        this.tone = tone;
        this.icon = icon;
        this.label = label;
    }

    public Tone tone() {
        return tone;
    }

    public IconName icon() {
        return icon;
    }

    /** Rótulo padrão em português — a aplicação pode exibir outro. */
    public String label() {
        return label;
    }

    /** Status que indicam atividade em andamento (ícone animado). */
    public boolean isActive() {
        return this == RUNNING;
    }
}
