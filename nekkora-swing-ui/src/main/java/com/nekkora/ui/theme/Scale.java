package com.nekkora.ui.theme;

import com.formdev.flatlaf.util.UIScale;
import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.Radius;
import com.nekkora.tokens.Spacing;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;

/**
 * Escalonamento de DPI. Todo valor em px lógicos (tokens) passa por aqui antes de virar pixel.
 *
 * <p>No Windows/macOS com Java 9+ o próprio Java2D escala a tela (fator 1 aqui); no Linux e em
 * {@code -Dflatlaf.uiScale=1.5} o fator é aplicado por esta classe.
 */
public final class Scale {
    private Scale() {}

    /** Fator usado enquanto o Look and Feel está sendo montado (o UIScale ainda não existe). */
    private static volatile float pendingFactor = 0f;

    public static int px(int logical) {
        float f = pendingFactor;
        return f > 0 ? Math.round(logical * f) : UIScale.scale(logical);
    }

    public static float pxf(float logical) {
        float f = pendingFactor;
        return f > 0 ? logical * f : UIScale.scale(logical);
    }

    /**
     * Executa {@code r} com o fator que o FlatLaf vai adotar para {@code systemFont} — as fontes
     * Nekkora são montadas antes de o UIScale estar disponível.
     */
    static void withPendingFactor(Font systemFont, Runnable r) {
        pendingFactor = estimateFactor(systemFont);
        try {
            r.run();
        } finally {
            pendingFactor = 0f;
        }
    }

    private static float estimateFactor(Font systemFont) {
        String custom = System.getProperty("flatlaf.uiScale");
        if (custom != null) {
            try {
                String v = custom.trim().toLowerCase();
                if (v.endsWith("x")) return Float.parseFloat(v.substring(0, v.length() - 1));
                if (v.endsWith("%")) return Float.parseFloat(v.substring(0, v.length() - 1)) / 100f;
                return Float.parseFloat(v);
            } catch (NumberFormatException ignored) {
                // cai no cálculo padrão
            }
        }
        if (systemFont == null) return 1f;
        float f = UIScale.computeFontScaleFactor(systemFont);
        if (f < 1f) return 1f;
        return f > 1f ? Math.round(f * 4f) / 4f : f;
    }

    public static int px(Spacing s) {
        return px(s.px());
    }

    public static int px(Radius r) {
        return px(r.px());
    }

    public static int height(ControlSize size) {
        return px(size.height());
    }

    public static Insets insets(int top, int left, int bottom, int right) {
        return new Insets(px(top), px(left), px(bottom), px(right));
    }

    public static Insets insets(Spacing vertical, Spacing horizontal) {
        return insets(vertical.px(), horizontal.px(), vertical.px(), horizontal.px());
    }

    public static Dimension size(int w, int h) {
        return new Dimension(px(w), px(h));
    }

    /** Fator atual (1.0 = 100%). */
    public static float factor() {
        return UIScale.getUserScaleFactor();
    }
}
