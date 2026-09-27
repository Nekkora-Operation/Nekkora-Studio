package com.nekkora.tokens;

import static com.nekkora.tokens.ColorMath.contrast;
import static com.nekkora.tokens.ColorMath.over;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ThemeContrastTest {

    static Stream<NekkoraTheme> themes() {
        return NekkoraThemes.all().stream();
    }

    @ParameterizedTest
    @MethodSource("themes")
    void defineTodosOsTokens(NekkoraTheme theme) {
        assertEquals(ColorToken.values().length, theme.colors().size());
    }

    @ParameterizedTest
    @MethodSource("themes")
    void textoTemContrasteAA(NekkoraTheme t) {
        for (ColorToken bg : new ColorToken[] {ColorToken.BACKGROUND_CANVAS, ColorToken.BACKGROUND_SURFACE,
                ColorToken.BACKGROUND_ELEVATED, ColorToken.FIELD_BACKGROUND}) {
            Color b = t.color(bg);
            atLeast(t, "text.primary / " + bg.key(), t.color(ColorToken.TEXT_PRIMARY), b, 7.0);
            atLeast(t, "text.secondary / " + bg.key(), t.color(ColorToken.TEXT_SECONDARY), b, 4.5);
            atLeast(t, "text.muted / " + bg.key(), t.color(ColorToken.TEXT_MUTED), b, 3.0);
        }
        atLeast(t, "text.link", t.color(ColorToken.TEXT_LINK), t.color(ColorToken.BACKGROUND_SURFACE), 4.5);
        atLeast(t, "selection", t.color(ColorToken.SELECTION_TEXT), t.color(ColorToken.SELECTION_BACKGROUND), 4.5);
    }

    @ParameterizedTest
    @MethodSource("themes")
    void acoesTemContrasteAA(NekkoraTheme t) {
        atLeast(t, "action.primary", t.color(ColorToken.ACTION_PRIMARY_TEXT), t.color(ColorToken.ACTION_PRIMARY), 4.5);
        atLeast(t, "action.primaryHover", t.color(ColorToken.ACTION_PRIMARY_TEXT), t.color(ColorToken.ACTION_PRIMARY_HOVER), 4.5);
        atLeast(t, "action.secondary", t.color(ColorToken.ACTION_SECONDARY_TEXT), t.color(ColorToken.ACTION_SECONDARY), 4.5);
        atLeast(t, "action.danger", t.color(ColorToken.ACTION_DANGER_TEXT), t.color(ColorToken.ACTION_DANGER), 4.5);
        atLeast(t, "accent.solid", Color.WHITE, t.color(ColorToken.ACCENT_SOLID), 4.5);
    }

    @ParameterizedTest
    @MethodSource("themes")
    void statusTemContrasteAA(NekkoraTheme t) {
        Color surface = t.color(ColorToken.BACKGROUND_SURFACE);
        ColorToken[][] groups = {
                {ColorToken.STATUS_SUCCESS, ColorToken.STATUS_SUCCESS_SOFT, ColorToken.STATUS_SUCCESS_SOLID},
                {ColorToken.STATUS_WARNING, ColorToken.STATUS_WARNING_SOFT, ColorToken.STATUS_WARNING_SOLID},
                {ColorToken.STATUS_ERROR, ColorToken.STATUS_ERROR_SOFT, ColorToken.STATUS_ERROR_SOLID},
                {ColorToken.STATUS_INFO, ColorToken.STATUS_INFO_SOFT, ColorToken.STATUS_INFO_SOLID},
                {ColorToken.STATUS_NEUTRAL, ColorToken.STATUS_NEUTRAL_SOFT, ColorToken.STATUS_NEUTRAL_SOLID},
        };
        for (ColorToken[] g : groups) {
            atLeast(t, g[0].key() + " / surface", t.color(g[0]), surface, 4.5);
            atLeast(t, g[0].key() + " / soft", t.color(g[0]), over(t.color(g[1]), surface), 4.5);
            atLeast(t, g[2].key() + " / onSolid", t.color(ColorToken.STATUS_ON_SOLID), t.color(g[2]), 4.5);
        }
    }

    @ParameterizedTest
    @MethodSource("themes")
    void focoEBordaForteVisiveis(NekkoraTheme t) {
        // Componentes de UI (WCAG 1.4.11): 3:1 contra o fundo adjacente
        atLeast(t, "border.focus", t.color(ColorToken.BORDER_FOCUS), t.color(ColorToken.BACKGROUND_SURFACE), 3.0);
        atLeast(t, "border.strong", t.color(ColorToken.BORDER_STRONG), t.color(ColorToken.FIELD_BACKGROUND), 1.8);
    }

    private static void atLeast(NekkoraTheme t, String what, Color fg, Color bg, double min) {
        double ratio = contrast(fg, bg);
        assertTrue(ratio >= min, () -> String.format("%s — %s: contraste %.2f < %.1f (%s sobre %s)", t.id(), what,
                ratio, min, ColorMath.toHex(fg), ColorMath.toHex(bg)));
    }
}
