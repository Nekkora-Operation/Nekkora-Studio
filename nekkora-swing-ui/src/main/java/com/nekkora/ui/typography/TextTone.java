package com.nekkora.ui.typography;

import com.nekkora.tokens.ColorToken;

/** Papel de cor de um texto. */
public enum TextTone {
    PRIMARY(ColorToken.TEXT_PRIMARY),
    SECONDARY(ColorToken.TEXT_SECONDARY),
    MUTED(ColorToken.TEXT_MUTED),
    DISABLED(ColorToken.TEXT_DISABLED),
    LINK(ColorToken.TEXT_LINK),
    ACCENT(ColorToken.ACCENT),
    SUCCESS(ColorToken.STATUS_SUCCESS),
    WARNING(ColorToken.STATUS_WARNING),
    ERROR(ColorToken.STATUS_ERROR),
    INFO(ColorToken.STATUS_INFO);

    private final ColorToken token;

    TextTone(ColorToken token) {
        this.token = token;
    }

    public ColorToken token() {
        return token;
    }
}
