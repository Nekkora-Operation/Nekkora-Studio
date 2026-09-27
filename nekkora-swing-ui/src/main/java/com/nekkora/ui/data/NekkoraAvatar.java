package com.nekkora.ui.data;

import com.nekkora.ui.core.NekkoraComponent;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.FontRole;
import com.nekkora.tokens.FontWeight;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** Avatar com iniciais (pessoa, equipe). O tom é estável por nome. */
public class NekkoraAvatar extends NekkoraComponent {
    private static final Tone[] TONES = {Tone.ACCENT, Tone.INFO, Tone.SUCCESS, Tone.WARNING, Tone.NEUTRAL};
    private final String name;
    private final int size;

    public NekkoraAvatar(String name) {
        this(name, 28);
    }

    public NekkoraAvatar(String name, int logicalSize) {
        this.name = name == null ? "" : name.strip();
        this.size = logicalSize;
        setToolTipText(this.name);
        getAccessibleContext().setAccessibleName(this.name);
    }

    public static String initials(String name) {
        String[] parts = name.strip().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return "?";
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Scale.px(size), Scale.px(size));
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Paint.prepare(g);
        int s = Math.min(getWidth(), getHeight());
        Tone tone = TONES[Math.floorMod(name.hashCode(), TONES.length)];
        Paint.fillRound(g2, 0, 0, s, s, s / 2f, Tokens.color(tone.solid()));
        g2.setFont(NekkoraFonts.font(FontRole.UI, FontWeight.SEMIBOLD, size * 0.4f));
        FontMetrics fm = g2.getFontMetrics();
        String t = initials(name);
        g2.setColor(Tokens.color(ColorToken.STATUS_ON_SOLID));
        Paint.text(this, g2, t, (s - fm.stringWidth(t)) / 2f, Paint.centeredBaseline(fm, 0, s));
        g2.dispose();
    }
}
