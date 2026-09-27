package com.nekkora.ui.internal;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.nekkora.tokens.BorderWidth;
import com.nekkora.tokens.ColorMath;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Elevation;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;

/** Utilitários de pintura compartilhados pelos componentes. Não é API pública. */
public final class Paint {
    private Paint() {}

    /** Cópia do Graphics com antialiasing e hints de texto do FlatLaf. Lembre de {@code dispose()}. */
    public static Graphics2D prepare(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        FlatUIUtils.setRenderingHints(g2);
        return g2;
    }

    public static Shape round(float x, float y, float w, float h, float radius) {
        float arc = Math.min(radius * 2, Math.min(w, h));
        return new RoundRectangle2D.Float(x, y, w, h, arc, arc);
    }

    public static void fillRound(Graphics2D g, float x, float y, float w, float h, float radius, Color c) {
        if (c == null) return;
        g.setColor(c);
        g.fill(round(x, y, w, h, radius));
    }

    /** Contorno de 1px (escalado) desenhado por dentro da área. */
    public static void strokeRound(Graphics2D g, float x, float y, float w, float h, float radius, Color c) {
        strokeRound(g, x, y, w, h, radius, c, Scale.pxf(BorderWidth.THIN.px()));
    }

    public static void strokeRound(Graphics2D g, float x, float y, float w, float h, float radius, Color c,
                                   float width) {
        if (c == null) return;
        Area outer = new Area(round(x, y, w, h, radius));
        outer.subtract(new Area(round(x + width, y + width, w - 2 * width, h - 2 * width, Math.max(0, radius - width))));
        g.setColor(c);
        g.fill(outer);
    }

    /** Anel de foco (token {@code focus.ring}) ao redor da área, ocupando {@code focusWidth} por fora. */
    public static void focusRing(Graphics2D g, float x, float y, float w, float h, float radius) {
        float fw = focusWidth();
        strokeRound(g, x - fw, y - fw, w + 2 * fw, h + 2 * fw, radius + fw, Tokens.color(ColorToken.FOCUS_RING), fw);
    }

    /** Largura do anel de foco em px (escalada). */
    public static float focusWidth() {
        return Scale.pxf(BorderWidth.FOCUS.px());
    }

    /** Sombra suave (várias camadas) para a elevação indicada. */
    public static void shadow(Graphics2D g, float x, float y, float w, float h, float radius, Elevation e) {
        if (e == Elevation.LEVEL_0) return;
        Color base = Tokens.color(ColorToken.SHADOW);
        int layers = Math.max(2, Scale.px(e.blur()) / 2);
        float oy = Scale.pxf(e.offsetY());
        double maxAlpha = base.getAlpha() / 255.0 * e.strength() / layers * 1.6;
        for (int i = layers; i >= 1; i--) {
            float spread = i * Scale.pxf(e.blur()) / layers / 2f;
            double a = maxAlpha * (1 - (i - 1) / (double) layers);
            g.setColor(ColorMath.alpha(base, a));
            g.fill(round(x - spread, y - spread + oy, w + 2 * spread, h + 2 * spread, radius + spread));
        }
    }

    /** Desenha texto com o antialiasing do Look and Feel. */
    public static void text(JComponent c, Graphics2D g, String s, float x, float baseline) {
        FlatUIUtils.drawString(c, g, s, Math.round(x), Math.round(baseline));
    }

    /** Baseline para centralizar verticalmente uma linha de texto em {@code [y, y+h]}. */
    public static int centeredBaseline(FontMetrics fm, int y, int h) {
        return y + (h - fm.getHeight()) / 2 + fm.getAscent();
    }

    /** Aplica opacidade de estado desabilitado (0.45, como na web). */
    public static void disabledComposite(Graphics2D g) {
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.45f));
    }

    public static BasicStroke stroke(float logicalWidth) {
        return new BasicStroke(Scale.pxf(logicalWidth), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    /** Trunca {@code s} com reticências para caber em {@code maxWidth}. */
    public static String ellipsize(String s, FontMetrics fm, int maxWidth) {
        if (s == null || fm.stringWidth(s) <= maxWidth) return s;
        String dots = "…";
        int w = maxWidth - fm.stringWidth(dots);
        int i = s.length();
        while (i > 0 && fm.stringWidth(s.substring(0, i)) > w) i--;
        return s.substring(0, i) + dots;
    }

    /** Trunca no meio (para caminhos e identificadores): {@code C:\…\arquivo.txt}. */
    public static String ellipsizeMiddle(String s, FontMetrics fm, int maxWidth) {
        if (s == null || fm.stringWidth(s) <= maxWidth) return s;
        String dots = "…";
        int keep = s.length();
        while (keep > 2) {
            keep--;
            int head = (keep + 1) / 2, tail = keep / 2;
            String candidate = s.substring(0, head) + dots + s.substring(s.length() - tail);
            if (fm.stringWidth(candidate) <= maxWidth) return candidate;
        }
        return dots;
    }
}
