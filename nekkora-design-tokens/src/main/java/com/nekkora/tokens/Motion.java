package com.nekkora.tokens;

/**
 * Movimento: "movimento explica estado, não decora". Durações curtas, sem bounce.
 */
public enum Motion {
    /** Hover, foco, troca de cor. */
    FAST(120),
    /** Abrir/fechar painéis, toasts. */
    NORMAL(180),
    /** Transições maiores (raras). */
    SLOW(260);

    private final int millis;

    Motion(int millis) {
        this.millis = millis;
    }

    public int millis() {
        return millis;
    }
}
