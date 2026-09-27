package com.nekkora.showcase.kit;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Radius;
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
import javax.swing.JComponent;

/** Barra que mostra um passo da escala de espaço. */
public final class SpaceBar extends JComponent {
    private final Spacing spacing;

    public SpaceBar(Spacing spacing) {
        this.spacing = spacing;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Scale.px(360), Scale.px(24));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Paint.prepare(g);
        g2.setFont(NekkoraFonts.font(TypeStyle.MONOSPACE));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Tokens.color(ColorToken.TEXT_SECONDARY));
        String label = spacing.key() + "  " + spacing.px() + "px";
        Paint.text(this, g2, label, 0, Paint.centeredBaseline(fm, 0, getHeight()));
        int x = Scale.px(140);
        Paint.fillRound(g2, x, Scale.px(6), Scale.px(spacing.px()) * 3, getHeight() - Scale.px(12), Scale.px(Radius.SMALL), Tokens.color(ColorToken.ACCENT));
        g2.dispose();
    }
}
