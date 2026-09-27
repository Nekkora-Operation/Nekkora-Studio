package com.nekkora.tokens;

/** Raios de canto. {@link #FULL} gera pílulas/círculos. */
public enum Radius {
    NONE(0),
    SMALL(4),
    MEDIUM(6),
    LARGE(10),
    FULL(999);

    private final int px;

    Radius(int px) {
        this.px = px;
    }

    public int px() {
        return px;
    }

    public String key() {
        return "radius." + name().toLowerCase();
    }
}
