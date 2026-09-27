package com.nekkora.ui.core;

import com.nekkora.ui.icon.IconName;

/** Gravidade de mensagens de feedback (alertas, toasts, banners). Sempre ícone + cor + texto. */
public enum Severity {
    INFO(Tone.INFO, IconName.INFO, "Informação"),
    SUCCESS(Tone.SUCCESS, IconName.CHECK_CIRCLE, "Sucesso"),
    WARNING(Tone.WARNING, IconName.ALERT, "Atenção"),
    ERROR(Tone.ERROR, IconName.X_CIRCLE, "Erro");

    private final Tone tone;
    private final IconName icon;
    private final String label;

    Severity(Tone tone, IconName icon, String label) {
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

    /** Rótulo acessível padrão (lido por leitores de tela). */
    public String label() {
        return label;
    }
}
