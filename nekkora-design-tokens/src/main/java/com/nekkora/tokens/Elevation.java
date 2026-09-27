package com.nekkora.tokens;

/**
 * Elevação: sombras discretas, usadas com parcimônia. A cor vem de {@link ColorToken#SHADOW}.
 *
 * @see #LEVEL_0
 */
public enum Elevation {
    /** Sem sombra — a maioria das superfícies. */
    LEVEL_0(0, 0, 0f),
    /** Cards destacados. */
    LEVEL_1(1, 4, 0.35f),
    /** Menus, popovers, toasts. */
    LEVEL_2(4, 12, 0.55f),
    /** Diálogos e sobreposições modais. */
    OVERLAY(10, 28, 0.8f);

    private final int offsetY;
    private final int blur;
    private final float strength;

    Elevation(int offsetY, int blur, float strength) {
        this.offsetY = offsetY;
        this.blur = blur;
        this.strength = strength;
    }

    public int offsetY() {
        return offsetY;
    }

    public int blur() {
        return blur;
    }

    /** Multiplicador (0..1) aplicado à opacidade de {@link ColorToken#SHADOW}. */
    public float strength() {
        return strength;
    }

    public String key() {
        return this == OVERLAY ? "elevation.overlay" : "elevation." + ordinal();
    }
}
