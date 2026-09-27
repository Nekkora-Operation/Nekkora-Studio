package com.nekkora.showcase.kit;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Elevation;
import com.nekkora.tokens.Radius;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;

/** Amostra de raio ou de elevação. */
public final class RadiusSample extends JComponent {
    private final Radius radius;
    private final Elevation elevation;

    public RadiusSample(Radius radius) {
        this(radius, Elevation.LEVEL_0);
    }

    public RadiusSample(Radius radius, Elevation elevation) {
        this.radius = radius;
        this.elevation = elevation;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Scale.px(120), Scale.px(84));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Paint.prepare(g);
        int m = Scale.px(14);
        float w = getWidth() - 2f * m, h = getHeight() - 2f * m;
        float r = Math.min(Scale.px(radius), h / 2);
        Paint.shadow(g2, m, m, w, h, r, elevation);
        Paint.fillRound(g2, m, m, w, h, r, Tokens.color(ColorToken.BACKGROUND_SURFACE));
        Paint.strokeRound(g2, m, m, w, h, r, Tokens.color(ColorToken.BORDER_DEFAULT));
        g2.dispose();
    }
}
