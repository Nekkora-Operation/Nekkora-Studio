package com.nekkora.ui.theme;

import com.nekkora.tokens.ColorMath;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Radius;
import com.nekkora.tokens.Spacing;
import com.nekkora.tokens.TypeStyle;
import java.awt.Color;
import java.awt.Font;

/**
 * Acesso rápido aos tokens do tema <b>atual</b>. Use em código de pintura (resolva no momento
 * de pintar — assim a troca de tema é automática).
 */
public final class Tokens {
    private Tokens() {}

    public static Color color(ColorToken token) {
        return ThemeManager.current().color(token);
    }

    /** Cor do token com opacidade adicional (0..1). */
    public static Color color(ColorToken token, double alpha) {
        Color c = color(token);
        return ColorMath.alpha(c, c.getAlpha() / 255.0 * alpha);
    }

    public static Font font(TypeStyle style) {
        return NekkoraFonts.font(style);
    }

    public static int space(Spacing s) {
        return Scale.px(s);
    }

    public static int space(int step) {
        return Scale.px(Spacing.of(step));
    }

    public static int radius(Radius r) {
        return Scale.px(r);
    }

    public static boolean isDark() {
        return ThemeManager.current().isDark();
    }
}
