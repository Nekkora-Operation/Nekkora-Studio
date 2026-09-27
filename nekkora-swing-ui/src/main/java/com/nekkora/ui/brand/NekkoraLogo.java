package com.nekkora.ui.brand;

import com.nekkora.ui.core.NekkoraComponent;
import com.nekkora.tokens.ColorMath;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.FontRole;
import com.nekkora.tokens.FontWeight;
import com.nekkora.ui.icon.SvgPath;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Marca Nekkora — símbolo Cat-Eye (óculos com orelhas de gato) e logotipo monolinear, com os
 * mesmos desenhos da nekkora-lib web. Separado dos componentes funcionais.
 *
 * <pre>{@code
 * new NekkoraLogo(NekkoraLogo.Variant.FULL, 28);             // símbolo + nome
 * new NekkoraLogo(NekkoraLogo.Variant.SYMBOL, 32, Tone.MONO);
 * frame.setIconImages(NekkoraLogo.appIconImages());
 * }</pre>
 */
public class NekkoraLogo extends NekkoraComponent {

    /** FULL = símbolo + nome (+ tagline opcional) · COMPACT = símbolo + nome sem tagline. */
    public enum Variant { FULL, COMPACT, SYMBOL, WORDMARK, APP_ICON }

    /** BRAND = cores do tema · MONO = uma cor (texto) · INVERSE = branco, para fundos preenchidos. */
    public enum Tone { BRAND, MONO, INVERSE }

    public static final String TAGLINE = "AUTOMATE · OBSERVE · EVOLVE";

    // Símbolo: viewBox 14 31 100 67, com translate(0 -3.5)
    private static final Shape FRAME_L = SvgPath.parse("M38.26 55.58A20 20 0 1 1 20.3 72.03L19 40Z");
    private static final Shape FRAME_R = SvgPath.parse("M89.74 55.58A20 20 0 1 0 107.7 72.03L109 40Z");
    private static final Shape EAR_L = SvgPath.parse("M21.5 46L24 61L32.5 55.5Z");
    private static final Shape EAR_R = SvgPath.parse("M106.5 46L104 61L95.5 55.5Z");
    private static final Shape BRIDGE = SvgPath.parse("M58.8 68.7Q64 63.5 69.2 68.7");
    private static final Shape NOSE = SvgPath.parse("M60 91.5H68L64 96.5Z");
    private static final Shape PUPIL_L = new Ellipse2D.Float(42.5f - 4.2f, 76.5f - 9, 8.4f, 18);
    private static final Shape PUPIL_R = new Ellipse2D.Float(90.5f - 4.2f, 76.5f - 9, 8.4f, 18);
    private static final Shape GLINT_L = new Ellipse2D.Float(44.6f - 1.9f, 71.5f - 1.9f, 3.8f, 3.8f);
    private static final Shape GLINT_R = new Ellipse2D.Float(92.6f - 1.9f, 71.5f - 1.9f, 3.8f, 3.8f);
    // Logotipo: viewBox -6 34 377 72
    private static final Shape[] LETTERS = {
            SvgPath.parse("M0 100V60M0 80A20 20 0 0 1 40 80V100"),
            SvgPath.parse("M62 80H102A20 20 0 1 0 96.14 94.14"),
            SvgPath.parse("M124 40V100M152 60L125.5 85M135 76L154 100"),
            SvgPath.parse("M174 40V100M202 60L175.5 85M185 76L204 100"),
            SvgPath.parse("M288 100V60M288 80A20 20 0 0 1 308 60"),
            new Ellipse2D.Float(325, 60, 40, 40),
            SvgPath.parse("M365 60V100")};
    private static final Shape LENS_O = new Ellipse2D.Float(226, 60, 40, 40);
    private static final Shape LENS_PUPIL = new Ellipse2D.Float(247.5f - 3.4f, 80 - 9.5f, 6.8f, 19);

    private final Variant variant;
    private final int height;
    private final Tone tone;
    private boolean tagline;

    public NekkoraLogo(Variant variant, int logicalHeight) {
        this(variant, logicalHeight, Tone.BRAND);
    }

    public NekkoraLogo(Variant variant, int logicalHeight, Tone tone) {
        this.variant = variant;
        this.height = logicalHeight;
        this.tone = tone;
        getAccessibleContext().setAccessibleName("Nekkora");
    }

    /** Mostra a tagline abaixo do nome (só em {@link Variant#FULL}). */
    public NekkoraLogo withTagline() {
        this.tagline = true;
        revalidate();
        return this;
    }

    @Override
    public Dimension getPreferredSize() {
        float h = Scale.px(height);
        return switch (variant) {
            case SYMBOL -> new Dimension(Math.round(h * 100 / 67f), Math.round(h));
            case WORDMARK -> new Dimension(Math.round(h * 377 / 72f), Math.round(h));
            case APP_ICON -> new Dimension(Math.round(h), Math.round(h));
            default -> {
                float wm = h * 0.62f;
                float textX = h * 100 / 67f + h * 0.32f;
                int w = Math.round(textX + wm * 377 / 72f);
                boolean tag = variant == Variant.FULL && tagline;
                if (tag) w = Math.max(w, Math.round(textX + getFontMetrics(taglineFont()).stringWidth(TAGLINE) + h * 0.04f));
                yield new Dimension(w, Math.round(h + (tag ? h * 0.42f : 0)));
            }
        };
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Paint.prepare(g);
        paintLogo(g2, 0, 0, Scale.px(height));
        g2.dispose();
    }

    private void paintLogo(Graphics2D g, float x, float y, float h) {
        switch (variant) {
            case SYMBOL -> paintSymbol(g, x, y, h, tone);
            case WORDMARK -> paintWordmark(g, x, y, h, tone);
            case APP_ICON -> paintAppIcon(g, x, y, h);
            default -> {
                paintSymbol(g, x, y, h, tone);
                float wmH = h * 0.62f;
                float wx = x + h * 100 / 67f + h * 0.32f;
                paintWordmark(g, wx, y + (h - wmH) / 2f, wmH, tone);
                if (variant == Variant.FULL && tagline) {
                    g.setFont(taglineFont());
                    g.setColor(Tokens.color(ColorToken.TEXT_MUTED));
                    FontMetrics fm = g.getFontMetrics();
                    Paint.text(this, g, TAGLINE, wx + h * 0.02f, y + h + fm.getAscent() * 0.9f);
                }
            }
        }
    }

    private java.awt.Font taglineFont() {
        return NekkoraFonts.font(FontRole.UI, FontWeight.SEMIBOLD, Math.max(9f, height * 0.26f));
    }

    private static Color c(ColorToken t) {
        return Tokens.color(t);
    }

    static void paintSymbol(Graphics2D g0, float x, float y, float h, Tone tone) {
        Graphics2D g = (Graphics2D) g0.create();
        float s = h / 67f;
        g.translate(x, y);
        g.scale(s, s);
        g.translate(-14, -31 - 3.5);
        Color mark, lens, pupil, ear, glint;
        switch (tone) {
            case MONO -> {
                mark = c(ColorToken.TEXT_PRIMARY);
                lens = null;
                pupil = mark;
                ear = mark;
                glint = null;
            }
            case INVERSE -> {
                mark = Color.WHITE;
                lens = Color.WHITE;
                pupil = c(ColorToken.ACCENT_SOLID);
                ear = ColorMath.alpha(Color.WHITE, 0.78);
                glint = null;
            }
            default -> {
                mark = c(ColorToken.BRAND_MARK);
                lens = c(ColorToken.BRAND_LENS);
                pupil = c(ColorToken.BRAND_PUPIL);
                ear = mark;
                glint = Color.WHITE;
            }
        }
        BasicStroke frame = new BasicStroke(6.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
        for (Shape f : new Shape[] {FRAME_L, FRAME_R}) {
            if (lens != null) {
                g.setColor(lens);
                g.fill(f);
            }
            g.setColor(mark);
            g.setStroke(frame);
            g.draw(f);
        }
        g.setColor(ear);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        for (Shape e : new Shape[] {EAR_L, EAR_R}) {
            g.fill(e);
            g.draw(e);
        }
        g.setColor(mark);
        g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(BRIDGE);
        g.setColor(pupil);
        g.fill(PUPIL_L);
        g.fill(PUPIL_R);
        if (glint != null) {
            g.setColor(glint);
            g.fill(GLINT_L);
            g.fill(GLINT_R);
        }
        g.setColor(mark);
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.fill(NOSE);
        g.draw(NOSE);
        g.dispose();
    }

    static void paintWordmark(Graphics2D g0, float x, float y, float h, Tone tone) {
        Graphics2D g = (Graphics2D) g0.create();
        float s = h / 72f;
        g.translate(x, y);
        g.scale(s, s);
        g.translate(6, -34);
        Color letters = switch (tone) {
            case MONO -> c(ColorToken.TEXT_PRIMARY);
            case INVERSE -> Color.WHITE;
            default -> c(ColorToken.BRAND_WORDMARK);
        };
        Color lensColor = tone == Tone.BRAND ? c(ColorToken.BRAND_MARK) : letters;
        g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(letters);
        for (Shape l : LETTERS) g.draw(l);
        g.setColor(lensColor);
        g.draw(LENS_O);
        g.fill(LENS_PUPIL);
        g.dispose();
    }

    static void paintAppIcon(Graphics2D g0, float x, float y, float size) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(c(ColorToken.ACCENT_SOLID));
        float r = size * 28 / 128f;
        g.fill(new java.awt.geom.RoundRectangle2D.Float(x, y, size, size, r * 2, r * 2));
        // translate(21 35.2) scale(0.86) do SVG, sobre grade de 128
        float k = size / 128f;
        float symH = 67 * 0.86f * k;
        paintSymbol(g, x + 21 * k, y + 35.2f * k + 3.5f * 0.86f * k, symH, Tone.INVERSE);
        g.dispose();
    }

    /** Ícone de app renderizado em vários tamanhos (para {@code Window#setIconImages}). */
    public static List<Image> appIconImages() {
        List<Image> images = new ArrayList<>();
        for (int s : new int[] {16, 20, 24, 32, 40, 48, 64, 128, 256}) images.add(appIcon(s));
        return images;
    }

    /** Ícone de app em {@code size}×{@code size} px. */
    public static BufferedImage appIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        paintAppIcon(g, 0, 0, size);
        g.dispose();
        return img;
    }
}
