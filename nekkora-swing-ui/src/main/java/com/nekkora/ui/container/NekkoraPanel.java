package com.nekkora.ui.container;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Radius;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

/**
 * Painel base: fundo por papel ({@link Background}), respiro interno pela escala de espaço,
 * borda e raio opcionais — sem {@code setBackground}/{@code setBorder} nas telas.
 */
public class NekkoraPanel extends JPanel {
    private Background background = Background.NONE;
    private Spacing paddingV;
    private Spacing paddingH;
    private ColorToken outline;
    private Radius radius = Radius.NONE;
    private boolean initialized;

    public NekkoraPanel() {
        this(new BorderLayout());
    }

    public NekkoraPanel(LayoutManager layout) {
        super(layout);
        initialized = true;
        applyStyle();
    }

    public NekkoraPanel(LayoutManager layout, Background background) {
        this(layout);
        setSurface(background);
    }

    public Background getSurface() {
        return background;
    }

    /** Define o fundo por papel (nome evita conflito com {@code setBackground(Color)}). */
    public NekkoraPanel setSurface(Background background) {
        this.background = background;
        applyStyle();
        repaint();
        return this;
    }

    /** Respiro interno igual nos quatro lados. */
    public NekkoraPanel setPadding(Spacing all) {
        return setPadding(all, all);
    }

    public NekkoraPanel setPadding(Spacing vertical, Spacing horizontal) {
        this.paddingV = vertical;
        this.paddingH = horizontal;
        applyStyle();
        revalidate();
        return this;
    }

    /** Contorno por papel (ex.: {@code BORDER_SUBTLE}); {@code null} remove. */
    public NekkoraPanel setOutline(ColorToken outline) {
        this.outline = outline;
        repaint();
        return this;
    }

    public NekkoraPanel setRadius(Radius radius) {
        this.radius = radius;
        repaint();
        return this;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyStyle();
    }

    protected void applyStyle() {
        if (!initialized) return;
        setOpaque(false); // pintamos o fundo nós mesmos (raio)
        if (paddingV == null && paddingH == null) return; // sem respiro definido: não mexe na borda
        int v = paddingV == null ? 0 : Scale.px(paddingV), h = paddingH == null ? 0 : Scale.px(paddingH);
        setBorder(BorderFactory.createEmptyBorder(v, h, v, h));
    }

    @Override
    protected void paintComponent(Graphics g) {
        ColorToken bg = background.token();
        if (bg != null || outline != null) {
            Graphics2D g2 = Paint.prepare(g);
            float r = Scale.px(radius);
            if (bg != null) {
                if (r == 0) {
                    g2.setColor(Tokens.color(bg));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                } else {
                    Paint.fillRound(g2, 0, 0, getWidth(), getHeight(), r, Tokens.color(bg));
                }
            }
            if (outline != null) Paint.strokeRound(g2, 0, 0, getWidth(), getHeight(), r, Tokens.color(outline));
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
