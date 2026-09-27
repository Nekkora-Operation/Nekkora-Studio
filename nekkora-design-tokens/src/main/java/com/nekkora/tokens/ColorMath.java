package com.nekkora.tokens;

import java.awt.Color;

/** Operações de cor usadas para derivar os tokens a partir da paleta de cada tema. */
public final class ColorMath {
    private ColorMath() {}

    /** Converte {@code #rrggbb} em cor. */
    public static Color hex(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() != 6) throw new IllegalArgumentException("Use #rrggbb: " + hex);
        return new Color(Integer.parseInt(h, 16));
    }

    /** Mistura {@code amount} (0..1) de {@code a} com o restante de {@code b} — como {@code color-mix()}. */
    public static Color mix(Color a, Color b, double amount) {
        double t = clamp(amount);
        return new Color(
                ch(a.getRed() * t + b.getRed() * (1 - t)),
                ch(a.getGreen() * t + b.getGreen() * (1 - t)),
                ch(a.getBlue() * t + b.getBlue() * (1 - t)),
                ch(a.getAlpha() * t + b.getAlpha() * (1 - t)));
    }

    /** A mesma cor com opacidade {@code alpha} (0..1). */
    public static Color alpha(Color c, double alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), ch(255 * clamp(alpha)));
    }

    /** Compõe {@code top} (possivelmente translúcida) sobre {@code bottom} opaca. */
    public static Color over(Color top, Color bottom) {
        double a = top.getAlpha() / 255.0;
        return new Color(
                ch(top.getRed() * a + bottom.getRed() * (1 - a)),
                ch(top.getGreen() * a + bottom.getGreen() * (1 - a)),
                ch(top.getBlue() * a + bottom.getBlue() * (1 - a)));
    }

    /** Luminância relativa (WCAG 2.x). */
    public static double luminance(Color c) {
        return 0.2126 * lin(c.getRed()) + 0.7152 * lin(c.getGreen()) + 0.0722 * lin(c.getBlue());
    }

    /** Razão de contraste WCAG entre duas cores opacas (1..21). */
    public static double contrast(Color a, Color b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** {@code #rrggbb} (ou {@code #rrggbbaa} se translúcida). */
    public static String toHex(Color c) {
        String rgb = String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
        return c.getAlpha() == 255 ? rgb : rgb + String.format("%02x", c.getAlpha());
    }

    private static double lin(int channel) {
        double s = channel / 255.0;
        return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static int ch(double v) {
        return (int) Math.round(Math.max(0, Math.min(255, v)));
    }
}
