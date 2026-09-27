package com.nekkora.ui.data;

import com.nekkora.ui.core.NekkoraComponent;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.FontRole;
import com.nekkora.tokens.FontWeight;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.internal.Spin;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;

/**
 * Badge: rótulo curto de categoria/estado/contagem, em pílula.
 *
 * <p>{@link Variant#SOFT} (padrão) para informação, {@link Variant#SOLID} (fundo preenchido,
 * texto branco AA) para destaque, {@link Variant#OUTLINE} para baixa ênfase.
 */
public class NekkoraBadge extends NekkoraComponent {

    public enum Variant { SOFT, SOLID, OUTLINE }

    public enum Size { SMALL, MEDIUM }

    private String text;
    private Tone tone;
    private Variant variant = Variant.SOFT;
    private Size size = Size.MEDIUM;
    private IconName icon;
    private boolean dot;
    private boolean spinning;
    private final Spin spin = new Spin(this);

    public NekkoraBadge(String text) {
        this(text, Tone.NEUTRAL);
    }

    public NekkoraBadge(String text, Tone tone) {
        this.text = text;
        this.tone = tone;
        updateAccessibleName();
    }

    public NekkoraBadge(String text, Tone tone, Variant variant) {
        this(text, tone);
        this.variant = variant;
    }

    /** Contador (ex.: itens não lidos). 0 esconde o badge. */
    public static NekkoraBadge count(int n) {
        NekkoraBadge b = new NekkoraBadge(n > 99 ? "99+" : Integer.toString(n), Tone.ACCENT, Variant.SOLID);
        b.setSize(Size.SMALL);
        b.setVisible(n > 0);
        return b;
    }

    public NekkoraBadge withIcon(IconName icon) {
        this.icon = icon;
        revalidate();
        repaint();
        return this;
    }

    /** Bolinha de cor antes do texto (em vez de ícone). */
    public NekkoraBadge withDot() {
        this.dot = true;
        revalidate();
        repaint();
        return this;
    }

    public NekkoraBadge withVariant(Variant variant) {
        this.variant = variant;
        repaint();
        return this;
    }

    public NekkoraBadge withSize(Size size) {
        setSize(size);
        return this;
    }

    public void setSize(Size size) {
        this.size = size;
        revalidate();
        repaint();
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
        updateAccessibleName();
        revalidate();
        repaint();
    }

    public Tone getTone() {
        return tone;
    }

    public void setTone(Tone tone) {
        this.tone = tone;
        repaint();
    }

    public Variant getVariant() {
        return variant;
    }

    /** Gira o ícone (indicador de atividade). */
    protected void setSpinning(boolean spinning) {
        this.spinning = spinning;
        spin.setActive(spinning);
    }

    protected void setIconInternal(IconName icon) {
        this.icon = icon;
    }

    private void updateAccessibleName() {
        getAccessibleContext().setAccessibleName(text);
    }

    private Font font() {
        return NekkoraFonts.font(FontRole.UI, size == Size.SMALL ? FontWeight.SEMIBOLD : FontWeight.MEDIUM,
                size == Size.SMALL ? 11f : 12f);
    }

    private int height() {
        return Scale.px(size == Size.SMALL ? 18 : 22);
    }

    private int iconSize() {
        return Scale.px(size == Size.SMALL ? 12 : 14);
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        FontMetrics fm = getFontMetrics(font());
        int pad = Scale.px(size == Size.SMALL ? 6 : 8);
        int w = text == null ? 0 : fm.stringWidth(text);
        int gap = Scale.px(4);
        if (icon != null) w += iconSize() + (w > 0 ? gap : 0);
        else if (dot) w += Scale.px(6) + gap;
        int h = height();
        return new Dimension(Math.max(h, w + 2 * pad), h);
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
        try {
            Dimension pref = getPreferredSize();
            float w = Math.min(getWidth(), pref.width), h = Math.min(getHeight(), pref.height);
            float y = (getHeight() - h) / 2f;
            float r = h / 2f;
            Color fg;
            switch (variant) {
                case SOLID -> {
                    Paint.fillRound(g2, 0, y, w, h, r, Tokens.color(tone.solid()));
                    fg = Tokens.color(ColorToken.STATUS_ON_SOLID);
                }
                case OUTLINE -> {
                    fg = Tokens.color(tone.foreground());
                    Paint.strokeRound(g2, 0, y, w, h, r, com.nekkora.tokens.ColorMath.alpha(fg, 0.55));
                }
                default -> {
                    Paint.fillRound(g2, 0, y, w, h, r, Tokens.color(tone.soft()));
                    fg = Tokens.color(tone.foreground());
                }
            }
            if (!isEnabled()) fg = Tokens.color(ColorToken.TEXT_DISABLED);
            Font f = font();
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();
            int pad = Scale.px(size == Size.SMALL ? 6 : 8);
            int gap = Scale.px(4);
            int tw = text == null ? 0 : fm.stringWidth(text);
            float x = pad;
            if (text == null || text.isEmpty()) x = (w - (icon != null ? iconSize() : 0)) / 2f;
            if (icon != null) {
                int s = iconSize();
                NekkoraIcon ic = NekkoraIcon.of(icon, size == Size.SMALL ? 12 : 14);
                if (spinning) ic = ic.withRotation(spin.angle());
                ic.paint(g2, Math.round(x), Math.round(y + (h - s) / 2f), s, fg, fg);
                x += s + gap;
            } else if (dot) {
                float d = Scale.pxf(6);
                g2.setColor(variant == Variant.SOLID ? fg : Tokens.color(tone.foreground()));
                g2.fill(new Ellipse2D.Float(x, y + (h - d) / 2f, d, d));
                x += d + gap;
            }
            if (tw > 0) {
                g2.setColor(fg);
                Paint.text(this, g2, text, x, Paint.centeredBaseline(fm, Math.round(y), Math.round(h)));
            }
        } finally {
            g2.dispose();
        }
    }
}
