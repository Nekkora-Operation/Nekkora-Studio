package com.nekkora.ui.container;

import com.nekkora.ui.core.NekkoraComponent;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Spacing;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** Divisória de 1px (horizontal ou vertical), com rótulo central opcional ("ou"). */
public class NekkoraDivider extends NekkoraComponent {
    private final boolean vertical;
    private final String label;
    private Spacing margin = Spacing.SPACE_4;

    public NekkoraDivider() {
        this(false, null);
    }

    public NekkoraDivider(boolean vertical, String label) {
        this.vertical = vertical;
        this.label = label;
        getAccessibleContext().setAccessibleName("separador");
    }

    public static NekkoraDivider horizontal() {
        return new NekkoraDivider(false, null);
    }

    public static NekkoraDivider vertical() {
        return new NekkoraDivider(true, null);
    }

    public static NekkoraDivider labeled(String label) {
        return new NekkoraDivider(false, label);
    }

    /** Respiro antes/depois da linha (padrão space.4). */
    public NekkoraDivider withMargin(Spacing margin) {
        this.margin = margin;
        revalidate();
        return this;
    }

    @Override
    public Dimension getPreferredSize() {
        int m = margin == null ? 0 : Scale.px(margin) * 2;
        int thick = label != null ? getFontMetrics(NekkoraFonts.font(TypeStyle.CAPTION)).getHeight() : 1;
        return vertical ? new Dimension(m + 1, Scale.px(16)) : new Dimension(Scale.px(16), m + thick);
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension p = getPreferredSize();
        return vertical ? new Dimension(p.width, Integer.MAX_VALUE) : new Dimension(Integer.MAX_VALUE, p.height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Paint.prepare(g);
        g2.setColor(Tokens.color(ColorToken.BORDER_SUBTLE));
        int t = Math.max(1, Scale.px(1));
        if (vertical) {
            g2.fillRect((getWidth() - t) / 2, 0, t, getHeight());
        } else if (label == null) {
            g2.fillRect(0, (getHeight() - t) / 2, getWidth(), t);
        } else {
            g2.setFont(NekkoraFonts.font(TypeStyle.CAPTION));
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(label), pad = Scale.px(Spacing.SPACE_4);
            int cx = (getWidth() - tw) / 2, cy = getHeight() / 2;
            g2.fillRect(0, cy, Math.max(0, cx - pad), t);
            g2.fillRect(cx + tw + pad, cy, Math.max(0, getWidth() - cx - tw - pad), t);
            g2.setColor(Tokens.color(ColorToken.TEXT_MUTED));
            Paint.text(this, g2, label, cx, Paint.centeredBaseline(fm, 0, getHeight()));
        }
        g2.dispose();
    }
}
