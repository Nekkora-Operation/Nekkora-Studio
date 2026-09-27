package com.nekkora.tokens;

/**
 * Escala de espaçamento {@code space.1 .. space.12} (px lógicos, antes do escalonamento de DPI).
 *
 * <p>Use sempre a escala — nunca números soltos em telas.
 */
public enum Spacing {
    SPACE_1(2),
    SPACE_2(4),
    SPACE_3(6),
    SPACE_4(8),
    SPACE_5(12),
    SPACE_6(16),
    SPACE_7(20),
    SPACE_8(24),
    SPACE_9(32),
    SPACE_10(40),
    SPACE_11(48),
    SPACE_12(64);

    private final int px;

    Spacing(int px) {
        this.px = px;
    }

    /** Valor em px lógicos. */
    public int px() {
        return px;
    }

    /** Nome no design system, ex.: {@code space.4}. */
    public String key() {
        return "space." + (ordinal() + 1);
    }

    /** {@code step} de 1 a 12. */
    public static Spacing of(int step) {
        if (step < 1 || step > 12) throw new IllegalArgumentException("space.1 .. space.12: " + step);
        return values()[step - 1];
    }
}
