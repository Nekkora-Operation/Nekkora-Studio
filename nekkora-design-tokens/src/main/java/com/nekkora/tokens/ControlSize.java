package com.nekkora.tokens;

/** Alturas de controle (botões, campos, combos) — densidade de aplicação desktop. */
public enum ControlSize {
    SMALL(24, 8, 12),
    MEDIUM(30, 12, 16),
    LARGE(36, 16, 20);

    private final int height;
    private final int paddingX;
    private final int iconSize;

    ControlSize(int height, int paddingX, int iconSize) {
        this.height = height;
        this.paddingX = paddingX;
        this.iconSize = iconSize;
    }

    public int height() {
        return height;
    }

    public int paddingX() {
        return paddingX;
    }

    /** Tamanho de ícone recomendado dentro do controle. */
    public int iconSize() {
        return iconSize;
    }
}
