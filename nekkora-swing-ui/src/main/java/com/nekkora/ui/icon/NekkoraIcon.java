package com.nekkora.ui.icon;

import com.formdev.flatlaf.FlatLaf;
import com.nekkora.tokens.ColorMath;
import com.nekkora.tokens.ColorToken;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.plaf.UIResource;

/**
 * Ícone Nekkora vetorial (nítido em qualquer DPI), estilo duotone: traço arredondado + camada
 * preenchida translúcida.
 *
 * <p>Cor: por padrão acompanha o {@code foreground} do componente onde é pintado (como
 * {@code currentColor} na web); use {@link #withColor(ColorToken)} para fixar um papel do tema.
 * Imutável — os métodos {@code with*} devolvem cópias.
 *
 * <pre>{@code
 * NekkoraIcon.of(IconName.SEARCH)                         // 16px, cor do componente
 * NekkoraIcon.of(IconName.ALERT, IconSize.L).withColor(ColorToken.STATUS_WARNING)
 * }</pre>
 */
public class NekkoraIcon implements Icon, FlatLaf.DisabledIconProvider {

    /** Duotone (padrão) ou só traço. */
    public enum Variant { DUOTONE, OUTLINE }

    private static final float DUO_OPACITY = 0.26f;

    private final IconName name;
    private final int size;
    private final ColorToken colorToken;
    private final ColorToken duoToken;
    private final Variant variant;
    private final boolean disabled;
    private final double rotation;

    protected NekkoraIcon(IconName name, int size, ColorToken colorToken, ColorToken duoToken, Variant variant,
                          boolean disabled, double rotation) {
        this.name = Objects.requireNonNull(name);
        this.size = size;
        this.colorToken = colorToken;
        this.duoToken = duoToken;
        this.variant = variant;
        this.disabled = disabled;
        this.rotation = rotation;
    }

    public static NekkoraIcon of(IconName name) {
        return of(name, IconSize.M);
    }

    public static NekkoraIcon of(IconName name, IconSize size) {
        return new NekkoraIcon(name, size.px(), null, null, Variant.DUOTONE, false, 0);
    }

    /** Tamanho arbitrário em px lógicos (prefira {@link IconSize}). */
    public static NekkoraIcon of(IconName name, int logicalSize) {
        return new NekkoraIcon(name, logicalSize, null, null, Variant.DUOTONE, false, 0);
    }

    public NekkoraIcon withColor(ColorToken token) {
        return new NekkoraIcon(name, size, token, duoToken, variant, disabled, rotation);
    }

    /** Cor própria para a camada duotone (padrão: a mesma do traço). */
    public NekkoraIcon withDuoColor(ColorToken token) {
        return new NekkoraIcon(name, size, colorToken, token, variant, disabled, rotation);
    }

    public NekkoraIcon withVariant(Variant v) {
        return new NekkoraIcon(name, size, colorToken, duoToken, v, disabled, rotation);
    }

    public NekkoraIcon withSize(IconSize s) {
        return new NekkoraIcon(name, s.px(), colorToken, duoToken, variant, disabled, rotation);
    }

    public NekkoraIcon withSize(int logicalSize) {
        return new NekkoraIcon(name, logicalSize, colorToken, duoToken, variant, disabled, rotation);
    }

    /** Rotação em graus em torno do centro (usado por spinners e chevrons). */
    public NekkoraIcon withRotation(double degrees) {
        return new NekkoraIcon(name, size, colorToken, duoToken, variant, disabled, degrees);
    }

    /** Versão {@link UIResource} (para defaults do Look and Feel). */
    public Icon asUIResource() {
        return new UIResourceIcon(this);
    }

    public IconName name() {
        return name;
    }

    public int logicalSize() {
        return size;
    }

    @Override
    public Icon getDisabledIcon() {
        return new NekkoraIcon(name, size, colorToken, duoToken, variant, true, rotation);
    }

    @Override
    public int getIconWidth() {
        return Scale.px(size);
    }

    @Override
    public int getIconHeight() {
        return Scale.px(size);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Color color = resolveColor(c);
        Color duo = duoToken != null && !isDisabled(c) ? Tokens.color(duoToken) : color;
        paint((Graphics2D) g, x, y, getIconWidth(), color, duo);
    }

    /** Pinta o ícone em uma área quadrada com cores explícitas (usado por pintores customizados). */
    public void paint(Graphics2D g0, int x, int y, int px, Color color, Color duoColor) {
        IconRegistry.Glyph glyph = IconRegistry.glyph(name);
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.translate(x, y);
            double scale = px / 24.0;
            g.scale(scale, scale);
            if (rotation != 0) g.rotate(Math.toRadians(rotation), 12, 12);
            if (variant == Variant.DUOTONE && !glyph.duo().isEmpty()) {
                g.setColor(ColorMath.alpha(duoColor, duoColor.getAlpha() / 255.0 * DUO_OPACITY));
                for (Shape s : glyph.duo()) g.fill(s);
            }
            // traço um pouco mais grosso em tamanhos pequenos, para legibilidade
            float stroke = size <= 14 ? 2.0f : size <= 16 ? 1.9f : 1.75f;
            g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(color);
            for (Shape s : glyph.stroke()) g.draw(s);
            for (Shape s : glyph.fill()) g.fill(s);
        } finally {
            g.dispose();
        }
    }

    private boolean isDisabled(Component c) {
        return disabled || (c != null && !c.isEnabled());
    }

    private Color resolveColor(Component c) {
        if (isDisabled(c)) return Tokens.color(ColorToken.TEXT_DISABLED);
        if (colorToken != null) return Tokens.color(colorToken);
        if (c != null && c.getForeground() != null) return c.getForeground();
        return Tokens.color(ColorToken.TEXT_PRIMARY);
    }

    private static final class UIResourceIcon implements Icon, UIResource {
        private final NekkoraIcon delegate;

        UIResourceIcon(NekkoraIcon delegate) {
            this.delegate = delegate;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            delegate.paintIcon(c, g, x, y);
        }

        @Override
        public int getIconWidth() {
            return delegate.getIconWidth();
        }

        @Override
        public int getIconHeight() {
            return delegate.getIconHeight();
        }
    }
}
