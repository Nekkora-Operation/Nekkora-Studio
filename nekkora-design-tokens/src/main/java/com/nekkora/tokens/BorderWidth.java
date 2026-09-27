package com.nekkora.tokens;

/** Espessuras de borda. As cores ficam em {@link ColorToken#BORDER_SUBTLE}, {@code DEFAULT} e {@code STRONG}. */
public enum BorderWidth {
    /** Divisórias e contornos de superfície. */
    THIN(1f),
    /** Indicadores de seleção e ênfase. */
    STRONG(2f),
    /** Anel de foco. */
    FOCUS(2f);

    private final float px;

    BorderWidth(float px) {
        this.px = px;
    }

    public float px() {
        return px;
    }
}
