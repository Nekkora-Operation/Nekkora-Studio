package com.nekkora.ui.theme;

import com.nekkora.tokens.FontRole;
import com.nekkora.tokens.FontWeight;
import com.nekkora.tokens.TypeStyle;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.TextAttribute;
import java.io.InputStream;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tipografia Nekkora: Sora (display), Inter (interface) e JetBrains Mono (código), distribuídas
 * com a biblioteca (licença OFL). Se uma fonte não puder ser carregada, usa o primeiro fallback
 * instalado em {@link FontRole#families()}.
 *
 * <p>Telas não criam fontes: usam {@link #font(TypeStyle)} ou os componentes de tipografia.
 */
public final class NekkoraFonts {
    private static final Logger LOG = Logger.getLogger(NekkoraFonts.class.getName());
    private static final String BASE = "/com/nekkora/ui/fonts/";

    private static final Map<FontRole, Map<FontWeight, String>> FILES = Map.of(
            FontRole.UI, weights("Inter-Regular.ttf", "Inter-Medium.ttf", "Inter-SemiBold.ttf", "Inter-Bold.ttf"),
            FontRole.DISPLAY, weights("Sora-Regular.ttf", "Sora-Regular.ttf", "Sora-SemiBold.ttf", "Sora-Bold.ttf"),
            FontRole.MONO, weights("JetBrainsMono-Regular.ttf", "JetBrainsMono-Regular.ttf",
                    "JetBrainsMono-SemiBold.ttf", "JetBrainsMono-SemiBold.ttf"));

    private static Map<FontRole, Map<FontWeight, Font>> base;

    private NekkoraFonts() {}

    /** Carrega e registra as fontes (idempotente). Chamado por {@link ThemeManager#install()}. */
    public static synchronized void load() {
        if (base != null) return;
        Map<FontRole, Map<FontWeight, Font>> loaded = new EnumMap<>(FontRole.class);
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Set<String> installed = new HashSet<>(Arrays.asList(ge.getAvailableFontFamilyNames()));
        for (FontRole role : FontRole.values()) {
            Map<FontWeight, Font> byWeight = new EnumMap<>(FontWeight.class);
            for (FontWeight w : FontWeight.values()) {
                Font f = loadResource(FILES.get(role).get(w));
                if (f != null) {
                    ge.registerFont(f);
                } else {
                    f = fallback(role, w, installed);
                }
                byWeight.put(w, f);
            }
            loaded.put(role, byWeight);
        }
        base = loaded;
    }

    /** Fonte do nível tipográfico, já escalada para o DPI atual. */
    public static Font font(TypeStyle style) {
        return font(style.role(), style.weight(), style.size(), style.tracking());
    }

    /** Fonte do nível tipográfico com outro peso (ex.: corpo em negrito). */
    public static Font font(TypeStyle style, FontWeight weight) {
        return font(style.role(), weight, style.size(), style.tracking());
    }

    /** Fonte por papel/peso/tamanho (px lógicos). Prefira {@link #font(TypeStyle)}. */
    public static Font font(FontRole role, FontWeight weight, float size) {
        return font(role, weight, size, 0f);
    }

    private static Font font(FontRole role, FontWeight weight, float size, float tracking) {
        load();
        Font f = base.get(role).get(weight).deriveFont(Scale.pxf(size));
        if (tracking != 0f) {
            f = f.deriveFont(Map.of(TextAttribute.TRACKING, tracking));
        }
        return f;
    }

    /** Nome da família efetivamente usada para o papel (ex.: "Inter"). */
    public static String family(FontRole role) {
        load();
        return base.get(role).get(FontWeight.REGULAR).getFamily();
    }

    private static Font loadResource(String file) {
        try (InputStream in = NekkoraFonts.class.getResourceAsStream(BASE + file)) {
            if (in == null) return null;
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Não foi possível carregar a fonte " + file + "; usando fallback", e);
            return null;
        }
    }

    private static Font fallback(FontRole role, FontWeight w, Set<String> installed) {
        int style = w.value() >= FontWeight.SEMIBOLD.value() ? Font.BOLD : Font.PLAIN;
        for (String family : role.families()) {
            if (installed.contains(family)) return new Font(family, style, 1);
        }
        return new Font(role == FontRole.MONO ? Font.MONOSPACED : Font.SANS_SERIF, style, 1);
    }

    private static Map<FontWeight, String> weights(String regular, String medium, String semibold, String bold) {
        Map<FontWeight, String> m = new EnumMap<>(FontWeight.class);
        m.put(FontWeight.REGULAR, regular);
        m.put(FontWeight.MEDIUM, medium);
        m.put(FontWeight.SEMIBOLD, semibold);
        m.put(FontWeight.BOLD, bold);
        return m;
    }
}
