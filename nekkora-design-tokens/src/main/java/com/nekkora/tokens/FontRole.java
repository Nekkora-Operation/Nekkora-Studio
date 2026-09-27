package com.nekkora.tokens;

import java.util.List;

/**
 * Famílias tipográficas por papel, com fallbacks em ordem de preferência.
 * A primeira família de cada lista é distribuída junto com a nekkora-swing-ui.
 */
public enum FontRole {
    /** Títulos e display — Sora. */
    DISPLAY(List.of("Sora", "Segoe UI Semibold", "SF Pro Display", "Helvetica Neue", "Dialog")),
    /** Interface e dados — Inter. */
    UI(List.of("Inter", "Segoe UI", "SF Pro Text", "Helvetica Neue", "Dialog")),
    /** Código, IDs, logs — JetBrains Mono. */
    MONO(List.of("JetBrains Mono", "Cascadia Mono", "Consolas", "Menlo", "DejaVu Sans Mono", "Monospaced"));

    private final List<String> families;

    FontRole(List<String> families) {
        this.families = families;
    }

    /** Famílias em ordem de preferência. */
    public List<String> families() {
        return families;
    }
}
