package com.nekkora.ui.data;

import com.nekkora.ui.core.NekkoraComponent;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Radius;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * Marcador de ícone: ícone dentro de um quadrado arredondado com cor de tom — identifica tipos
 * de item em listas, cards e cabeçalhos. {@code solid} = fundo preenchido com ícone branco.
 */
public class NekkoraIconMarker extends NekkoraComponent {
    private IconName icon;
    private Tone tone;
    private final boolean solid;
    private final int size;

    public NekkoraIconMarker(IconName icon, Tone tone) {
        this(icon, tone, true, 32);
    }

    public NekkoraIconMarker(IconName icon, Tone tone, boolean solid, int logicalSize) {
        this.icon = icon;
        this.tone = tone;
        this.solid = solid;
        this.size = logicalSize;
    }

    public void setIcon(IconName icon) {
        this.icon = icon;
        repaint();
    }

    public void setTone(Tone tone) {
        this.tone = tone;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Scale.px(size), Scale.px(size));
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Paint.prepare(g);
        int s = Math.min(getWidth(), getHeight());
        float r = Scale.px(size >= 32 ? Radius.LARGE : Radius.MEDIUM);
        Color fg;
        if (solid) {
            Paint.fillRound(g2, 0, 0, s, s, r, Tokens.color(tone.solid()));
            fg = Tokens.color(ColorToken.STATUS_ON_SOLID);
        } else {
            Paint.fillRound(g2, 0, 0, s, s, r, Tokens.color(tone.soft()));
            fg = Tokens.color(tone.foreground());
        }
        int is = Math.round(s * 0.55f);
        NekkoraIcon.of(icon, Math.round(size * 0.55f)).paint(g2, (s - is) / 2, (s - is) / 2, is, fg, fg);
        g2.dispose();
    }
}
