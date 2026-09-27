package com.nekkora.tokens;

import java.awt.Color;
import java.util.Objects;

/**
 * Paleta base de um tema — as mesmas 8 cores da nekkora-lib (web).
 * Os tokens semânticos são derivados dela por {@link ThemeFactory}.
 *
 * @param background  fundo da janela
 * @param surface     superfícies (painéis)
 * @param card        cards e ênfases de superfície
 * @param primary     cor institucional (botão secundário, ícones de marca)
 * @param secondary   variação da primária (hover)
 * @param highlight   brilho/realce (bordas e hovers no escuro)
 * @param text        texto principal
 * @param accent      destaque da marca (ação primária, foco, links)
 * @param accentHover destaque em hover
 * @param accentPressed destaque pressionado
 * @param accentSolid destaque escurecido que suporta texto branco (AA)
 */
public record Palette(
        Color background,
        Color surface,
        Color card,
        Color primary,
        Color secondary,
        Color highlight,
        Color text,
        Color accent,
        Color accentHover,
        Color accentPressed,
        Color accentSolid) {

    public Palette {
        Objects.requireNonNull(background);
        Objects.requireNonNull(surface);
        Objects.requireNonNull(card);
        Objects.requireNonNull(primary);
        Objects.requireNonNull(secondary);
        Objects.requireNonNull(highlight);
        Objects.requireNonNull(text);
        Objects.requireNonNull(accent);
        Objects.requireNonNull(accentHover);
        Objects.requireNonNull(accentPressed);
        Objects.requireNonNull(accentSolid);
    }

    /** Atalho com valores {@code #rrggbb}, na mesma ordem dos componentes do record. */
    public static Palette of(String background, String surface, String card, String primary, String secondary,
                             String highlight, String text, String accent, String accentHover,
                             String accentPressed, String accentSolid) {
        return new Palette(ColorMath.hex(background), ColorMath.hex(surface), ColorMath.hex(card),
                ColorMath.hex(primary), ColorMath.hex(secondary), ColorMath.hex(highlight), ColorMath.hex(text),
                ColorMath.hex(accent), ColorMath.hex(accentHover), ColorMath.hex(accentPressed),
                ColorMath.hex(accentSolid));
    }
}
