package com.nekkora.tokens;

import static com.nekkora.tokens.ColorMath.alpha;
import static com.nekkora.tokens.ColorMath.hex;
import static com.nekkora.tokens.ColorMath.mix;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

/**
 * Deriva todos os tokens semânticos a partir de uma {@link Palette} + {@link ThemeMode}.
 *
 * <p>As regras espelham os blocos de modo da nekkora-lib (web): o mesmo tema tem a mesma
 * cara no navegador e no desktop. Para criar um tema novo basta declarar a paleta.
 */
public final class ThemeFactory {
    private ThemeFactory() {}

    private static final Color WHITE = Color.WHITE;
    private static final Color BLACK = Color.BLACK;

    public static NekkoraTheme create(String id, String displayName, ThemeMode mode, Palette p) {
        return create(id, displayName, mode, p, Map.of());
    }

    /**
     * @param overrides ajustes finos por token, aplicados depois da derivação
     */
    public static NekkoraTheme create(String id, String displayName, ThemeMode mode, Palette p,
                                      Map<ColorToken, Color> overrides) {
        Map<ColorToken, Color> c = new EnumMap<>(ColorToken.class);
        boolean dark = mode.isDark();

        Color canvas = p.background();
        Color surface = p.surface();

        // Fundos
        c.put(ColorToken.BACKGROUND_CANVAS, canvas);
        c.put(ColorToken.BACKGROUND_SURFACE, surface);
        c.put(ColorToken.BACKGROUND_ELEVATED, dark ? mix(p.highlight(), surface, 0.06) : WHITE);
        c.put(ColorToken.BACKGROUND_SUNKEN, dark ? mix(canvas, BLACK, 0.78) : mix(p.text(), canvas, 0.03));
        c.put(ColorToken.BACKGROUND_HOVER, dark ? alpha(p.highlight(), 0.07) : alpha(p.primary(), 0.07));
        c.put(ColorToken.BACKGROUND_PRESSED, dark ? alpha(p.highlight(), 0.12) : alpha(p.primary(), 0.12));
        c.put(ColorToken.FIELD_BACKGROUND, dark ? mix(canvas, surface, 0.55) : WHITE);

        // Texto
        c.put(ColorToken.TEXT_PRIMARY, p.text());
        c.put(ColorToken.TEXT_SECONDARY, mix(p.text(), canvas, dark ? 0.68 : 0.70));
        c.put(ColorToken.TEXT_MUTED, mix(p.text(), canvas, dark ? 0.50 : 0.52));
        c.put(ColorToken.TEXT_DISABLED, mix(p.text(), canvas, dark ? 0.32 : 0.34));
        c.put(ColorToken.TEXT_INVERSE, dark ? canvas : WHITE);
        c.put(ColorToken.TEXT_LINK, dark ? p.accent() : p.accentSolid());

        // Bordas (opacas, compostas sobre a superfície)
        c.put(ColorToken.BORDER_SUBTLE, mix(p.text(), surface, dark ? 0.07 : 0.07));
        c.put(ColorToken.BORDER_DEFAULT, mix(p.text(), surface, dark ? 0.13 : 0.14));
        c.put(ColorToken.BORDER_STRONG, mix(p.text(), surface, dark ? 0.24 : 0.32));
        c.put(ColorToken.BORDER_FOCUS, p.accent());

        // Ações
        c.put(ColorToken.ACTION_PRIMARY, p.accent());
        c.put(ColorToken.ACTION_PRIMARY_HOVER, p.accentHover());
        c.put(ColorToken.ACTION_PRIMARY_PRESSED, p.accentPressed());
        c.put(ColorToken.ACTION_PRIMARY_TEXT, dark ? canvas : WHITE);
        if (dark) {
            c.put(ColorToken.ACTION_SECONDARY, p.primary());
            c.put(ColorToken.ACTION_SECONDARY_HOVER, p.secondary());
            c.put(ColorToken.ACTION_SECONDARY_PRESSED, mix(p.primary(), canvas, 0.8));
            c.put(ColorToken.ACTION_SECONDARY_TEXT, p.text());
            c.put(ColorToken.ACTION_SECONDARY_BORDER, mix(p.highlight(), p.primary(), 0.14));
        } else {
            c.put(ColorToken.ACTION_SECONDARY, p.card());
            c.put(ColorToken.ACTION_SECONDARY_HOVER, mix(p.primary(), p.card(), 0.10));
            c.put(ColorToken.ACTION_SECONDARY_PRESSED, mix(p.primary(), p.card(), 0.18));
            c.put(ColorToken.ACTION_SECONDARY_TEXT, mix(p.primary(), p.text(), 0.55));
            c.put(ColorToken.ACTION_SECONDARY_BORDER, mix(p.primary(), p.card(), 0.16));
        }
        Color danger = hex("#d02c50");
        c.put(ColorToken.ACTION_DANGER, danger);
        c.put(ColorToken.ACTION_DANGER_HOVER, dark ? hex("#dc3d60") : hex("#b8233f"));
        c.put(ColorToken.ACTION_DANGER_PRESSED, dark ? hex("#b82546") : hex("#9e1d37"));
        c.put(ColorToken.ACTION_DANGER_TEXT, WHITE);

        // Destaque
        c.put(ColorToken.ACCENT, p.accent());
        c.put(ColorToken.ACCENT_SOFT, alpha(p.accent(), dark ? 0.16 : 0.11));
        c.put(ColorToken.ACCENT_SOLID, p.accentSolid());

        // Status — texto/ícone sobre o fundo · fundo suave · fundo sólido (texto branco AA)
        status(c, dark,
                ColorToken.STATUS_SUCCESS, ColorToken.STATUS_SUCCESS_SOFT, ColorToken.STATUS_SUCCESS_SOLID,
                dark ? hex("#4ade80") : hex("#166534"), hex("#15803d"));
        status(c, dark,
                ColorToken.STATUS_WARNING, ColorToken.STATUS_WARNING_SOFT, ColorToken.STATUS_WARNING_SOLID,
                dark ? hex("#f5a86a") : hex("#9a4508"), hex("#b45309"));
        status(c, dark,
                ColorToken.STATUS_ERROR, ColorToken.STATUS_ERROR_SOFT, ColorToken.STATUS_ERROR_SOLID,
                dark ? hex("#ff7d95") : hex("#c0264a"), danger);
        status(c, dark,
                ColorToken.STATUS_INFO, ColorToken.STATUS_INFO_SOFT, ColorToken.STATUS_INFO_SOLID,
                dark ? hex("#a9aaff") : hex("#4b4bc4"), hex("#5b5bd6"));
        c.put(ColorToken.STATUS_NEUTRAL, mix(p.text(), canvas, dark ? 0.72 : 0.70));
        c.put(ColorToken.STATUS_NEUTRAL_SOFT, alpha(p.text(), dark ? 0.12 : 0.08));
        c.put(ColorToken.STATUS_NEUTRAL_SOLID, mix(p.text(), canvas, dark ? 0.34 : 0.66));
        c.put(ColorToken.STATUS_ON_SOLID, WHITE);

        // Seleção e foco
        c.put(ColorToken.SELECTION_BACKGROUND, mix(p.accent(), surface, dark ? 0.26 : 0.16));
        c.put(ColorToken.SELECTION_TEXT, p.text());
        c.put(ColorToken.SELECTION_INACTIVE, mix(p.text(), surface, dark ? 0.12 : 0.09));
        c.put(ColorToken.FOCUS_RING, alpha(p.accent(), dark ? 0.45 : 0.35));

        // Sobreposição
        c.put(ColorToken.OVERLAY_SCRIM, dark ? alpha(BLACK, 0.55) : alpha(p.text(), 0.32));
        c.put(ColorToken.SHADOW, dark ? alpha(BLACK, 0.45) : alpha(p.text(), 0.16));

        // Marca
        c.put(ColorToken.BRAND_MARK, p.accent());
        c.put(ColorToken.BRAND_LENS, dark ? mix(p.highlight(), WHITE, 0.22) : WHITE);
        c.put(ColorToken.BRAND_PUPIL, dark ? canvas : p.text());
        c.put(ColorToken.BRAND_WORDMARK, p.text());

        c.putAll(overrides);
        return new NekkoraTheme(id, displayName, mode, p, c);
    }

    private static void status(Map<ColorToken, Color> c, boolean dark, ColorToken fg, ColorToken soft,
                               ColorToken solid, Color fgColor, Color solidColor) {
        c.put(fg, fgColor);
        c.put(soft, alpha(dark ? fgColor : solidColor, dark ? 0.16 : 0.10));
        c.put(solid, solidColor);
    }
}
