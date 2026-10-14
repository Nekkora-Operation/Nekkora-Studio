package com.nekkora.studio.domain.element;

/** Estabilidade de um localizador — mesmos valores do enum {@code stability} do PageMap 0.2.0. */
public enum LocatorStability {
    OBSERVED_STABLE("Estável (observado)"),
    HEURISTIC_STABLE("Provavelmente estável"),
    UNKNOWN("Estabilidade desconhecida"),
    HEURISTIC_UNSTABLE("Provavelmente instável"),
    OBSERVED_UNSTABLE("Instável (observado)");

    private final String label;

    LocatorStability(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
