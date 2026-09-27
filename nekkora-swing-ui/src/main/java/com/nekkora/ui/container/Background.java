package com.nekkora.ui.container;

import com.nekkora.tokens.ColorToken;

/** Fundo de um contêiner, por papel. */
public enum Background {
    /** Transparente: herda o fundo do pai. */
    NONE(null),
    CANVAS(ColorToken.BACKGROUND_CANVAS),
    SURFACE(ColorToken.BACKGROUND_SURFACE),
    ELEVATED(ColorToken.BACKGROUND_ELEVATED),
    SUNKEN(ColorToken.BACKGROUND_SUNKEN);

    private final ColorToken token;

    Background(ColorToken token) {
        this.token = token;
    }

    /** Token de cor, ou {@code null} para transparente. */
    public ColorToken token() {
        return token;
    }
}
