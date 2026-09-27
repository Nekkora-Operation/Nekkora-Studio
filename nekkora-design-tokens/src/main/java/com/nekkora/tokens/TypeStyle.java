package com.nekkora.tokens;

/**
 * Hierarquia tipográfica. Telas escolhem um <b>nível</b>, nunca uma fonte ou tamanho.
 *
 * <p>Tamanhos em px lógicos (desktop), escalados pelo DPI em tempo de execução.
 */
public enum TypeStyle {
    DISPLAY_L(FontRole.DISPLAY, FontWeight.SEMIBOLD, 30, 1.2f, -0.01f),
    DISPLAY_M(FontRole.DISPLAY, FontWeight.SEMIBOLD, 24, 1.25f, -0.01f),
    H1(FontRole.DISPLAY, FontWeight.SEMIBOLD, 20, 1.3f, 0f),
    H2(FontRole.DISPLAY, FontWeight.SEMIBOLD, 16, 1.35f, 0f),
    H3(FontRole.UI, FontWeight.SEMIBOLD, 14, 1.4f, 0f),
    BODY_L(FontRole.UI, FontWeight.REGULAR, 14, 1.5f, 0f),
    BODY(FontRole.UI, FontWeight.REGULAR, 13, 1.5f, 0f),
    BODY_S(FontRole.UI, FontWeight.REGULAR, 12, 1.45f, 0f),
    LABEL(FontRole.UI, FontWeight.MEDIUM, 12, 1.35f, 0f),
    CAPTION(FontRole.UI, FontWeight.REGULAR, 11, 1.35f, 0f),
    /** Rótulos em caixa alta (grupos de navegação, cabeçalhos de seção pequenos). */
    OVERLINE(FontRole.UI, FontWeight.SEMIBOLD, 10.5f, 1.3f, 0.06f),
    MONOSPACE(FontRole.MONO, FontWeight.REGULAR, 12, 1.55f, 0f);

    private final FontRole role;
    private final FontWeight weight;
    private final float size;
    private final float lineHeight;
    private final float tracking;

    TypeStyle(FontRole role, FontWeight weight, float size, float lineHeight, float tracking) {
        this.role = role;
        this.weight = weight;
        this.size = size;
        this.lineHeight = lineHeight;
        this.tracking = tracking;
    }

    public FontRole role() {
        return role;
    }

    public FontWeight weight() {
        return weight;
    }

    /** Tamanho em px lógicos. */
    public float size() {
        return size;
    }

    /** Altura de linha (multiplicador do tamanho). */
    public float lineHeight() {
        return lineHeight;
    }

    /** Espaçamento entre letras, em em (ex.: 0.06 = 6%). */
    public float tracking() {
        return tracking;
    }
}
