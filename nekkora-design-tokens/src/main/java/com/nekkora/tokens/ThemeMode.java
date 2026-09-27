package com.nekkora.tokens;

/** Modo do tema: define as regras de derivação e o comportamento de contraste. */
public enum ThemeMode {
    DARK,
    LIGHT;

    public boolean isDark() {
        return this == DARK;
    }
}
