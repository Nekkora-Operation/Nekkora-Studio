package com.nekkora.ui.feedback;

import com.nekkora.ui.core.NekkoraComponent;
import com.nekkora.tokens.ColorMath;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Radius;
import com.nekkora.ui.core.NekkoraUI;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.HierarchyEvent;
import javax.swing.Timer;

/** Placeholder de carregamento com brilho suave (linhas de texto, blocos, círculos). */
public class NekkoraSkeleton extends NekkoraComponent {

    public enum Shape { TEXT, BLOCK, CIRCLE }

    private final Shape shape;
    private final int lines;
    private final Timer timer;
    private float phase;

    public NekkoraSkeleton(Shape shape) {
        this(shape, 1);
    }

    /** Para {@link Shape#TEXT}: número de linhas (a última é mais curta). */
    public NekkoraSkeleton(Shape shape, int lines) {
        this.shape = shape;
        this.lines = Math.max(1, lines);
        timer = new Timer(40, e -> {
            phase = (phase + (NekkoraUI.isReducedMotion() ? 0.004f : 0.02f)) % 1.4f;
            repaint();
        });
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) timer.start();
                else timer.stop();
            }
        });
        getAccessibleContext().setAccessibleName("Carregando conteúdo");
    }

    @Override
    public Dimension getPreferredSize() {
        return switch (shape) {
            case TEXT -> new Dimension(Scale.px(240), lines * Scale.px(20));
            case BLOCK -> new Dimension(Scale.px(240), Scale.px(80));
            case CIRCLE -> new Dimension(Scale.px(32), Scale.px(32));
        };
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Paint.prepare(g);
        Color base = Tokens.color(ColorToken.STATUS_NEUTRAL_SOFT);
        Color shine = ColorMath.alpha(Tokens.color(ColorToken.TEXT_PRIMARY), Tokens.isDark() ? 0.07 : 0.05);
        int w = getWidth(), h = getHeight();
        float sx = (phase - 0.2f) * w;
        GradientPaint gp = new GradientPaint(sx, 0, ColorMath.alpha(shine, 0), sx + w * 0.2f, 0, shine, true);
        switch (shape) {
            case CIRCLE -> {
                int s = Math.min(w, h);
                Paint.fillRound(g2, 0, 0, s, s, s / 2f, base);
            }
            case BLOCK -> Paint.fillRound(g2, 0, 0, w, h, Scale.px(Radius.MEDIUM), base);
            default -> {
                int lh = Scale.px(20), bar = Scale.px(10);
                for (int i = 0; i < lines; i++) {
                    int lw = i == lines - 1 && lines > 1 ? (int) (w * 0.6) : w;
                    Paint.fillRound(g2, 0, i * lh + (lh - bar) / 2f, lw, bar, bar / 2f, base);
                }
            }
        }
        g2.setPaint(gp);
        g2.setComposite(java.awt.AlphaComposite.SrcAtop);
        g2.fillRect(0, 0, w, h);
        g2.dispose();
    }
}
