package com.nekkora.showcase.kit;

import com.nekkora.tokens.ColorMath;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Radius;
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

/** Amostra de um token de cor (resolvido no tema atual, ao vivo). */
public final class Swatch extends JComponent {
    private final ColorToken token;

    public Swatch(ColorToken token) {
        this.token = token;
        setToolTipText(token.key());
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Scale.px(188), Scale.px(92));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Paint.prepare(g);
        float r = Scale.px(Radius.MEDIUM);
        int chip = Scale.px(48);
        Paint.fillRound(g2, 0, 0, getWidth(), chip, r, Tokens.color(ColorToken.BACKGROUND_SURFACE));
        Paint.fillRound(g2, 0, 0, getWidth(), chip, r, Tokens.color(token));
        Paint.strokeRound(g2, 0, 0, getWidth(), chip, r, Tokens.color(ColorToken.BORDER_SUBTLE));
        g2.setFont(NekkoraFonts.font(TypeStyle.LABEL));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Tokens.color(ColorToken.TEXT_PRIMARY));
        Paint.text(this, g2, token.key(), 0, chip + Scale.px(6) + fm.getAscent());
        g2.setFont(NekkoraFonts.font(TypeStyle.MONOSPACE));
        FontMetrics mm = g2.getFontMetrics();
        g2.setColor(Tokens.color(ColorToken.TEXT_MUTED));
        Paint.text(this, g2, ColorMath.toHex(Tokens.color(token)), 0, chip + Scale.px(6) + fm.getHeight() + mm.getAscent());
        g2.dispose();
    }
}
