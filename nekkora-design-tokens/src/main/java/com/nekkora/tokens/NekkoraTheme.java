package com.nekkora.tokens;

import java.awt.Color;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Um tema resolvido: todos os {@link ColorToken} com valor definido.
 *
 * <p>Imutável. Crie temas novos com {@link ThemeFactory#create(String, String, ThemeMode, Palette)}
 * ou use os oficiais em {@link NekkoraThemes}.
 */
public final class NekkoraTheme {
    private final String id;
    private final String displayName;
    private final ThemeMode mode;
    private final Palette palette;
    private final Map<ColorToken, Color> colors;

    NekkoraTheme(String id, String displayName, ThemeMode mode, Palette palette, Map<ColorToken, Color> colors) {
        this.id = Objects.requireNonNull(id);
        this.displayName = Objects.requireNonNull(displayName);
        this.mode = Objects.requireNonNull(mode);
        this.palette = Objects.requireNonNull(palette);
        EnumMap<ColorToken, Color> copy = new EnumMap<>(ColorToken.class);
        copy.putAll(colors);
        for (ColorToken t : ColorToken.values()) {
            if (!copy.containsKey(t)) {
                throw new IllegalStateException("Tema '" + id + "' não define o token " + t.key());
            }
        }
        this.colors = Collections.unmodifiableMap(copy);
    }

    /** Identificador estável, ex.: {@code sakura-dark}. */
    public String id() {
        return id;
    }

    /** Nome para exibição, ex.: {@code Sakura Dark}. */
    public String displayName() {
        return displayName;
    }

    public ThemeMode mode() {
        return mode;
    }

    public boolean isDark() {
        return mode.isDark();
    }

    /** Paleta base de onde os tokens foram derivados. */
    public Palette palette() {
        return palette;
    }

    /** Cor resolvida para o token. Nunca nula. */
    public Color color(ColorToken token) {
        return colors.get(token);
    }

    /** Todos os tokens do tema (somente leitura). */
    public Map<ColorToken, Color> colors() {
        return colors;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
