package com.nekkora.ui.core;

import com.nekkora.tokens.ColorToken;

/**
 * Tom semântico de elementos de ênfase (badges, marcadores, alertas). Cada tom tem três
 * papéis: {@link #foreground()} (texto/ícone sobre fundo neutro), {@link #soft()} (fundo
 * suave) e {@link #solid()} (fundo sólido com texto branco AA).
 */
public enum Tone {
    NEUTRAL(ColorToken.STATUS_NEUTRAL, ColorToken.STATUS_NEUTRAL_SOFT, ColorToken.STATUS_NEUTRAL_SOLID),
    ACCENT(ColorToken.TEXT_LINK, ColorToken.ACCENT_SOFT, ColorToken.ACCENT_SOLID),
    SUCCESS(ColorToken.STATUS_SUCCESS, ColorToken.STATUS_SUCCESS_SOFT, ColorToken.STATUS_SUCCESS_SOLID),
    WARNING(ColorToken.STATUS_WARNING, ColorToken.STATUS_WARNING_SOFT, ColorToken.STATUS_WARNING_SOLID),
    ERROR(ColorToken.STATUS_ERROR, ColorToken.STATUS_ERROR_SOFT, ColorToken.STATUS_ERROR_SOLID),
    INFO(ColorToken.STATUS_INFO, ColorToken.STATUS_INFO_SOFT, ColorToken.STATUS_INFO_SOLID);

    private final ColorToken foreground;
    private final ColorToken soft;
    private final ColorToken solid;

    Tone(ColorToken foreground, ColorToken soft, ColorToken solid) {
        this.foreground = foreground;
        this.soft = soft;
        this.solid = solid;
    }

    public ColorToken foreground() {
        return foreground;
    }

    public ColorToken soft() {
        return soft;
    }

    public ColorToken solid() {
        return solid;
    }
}
