package com.nekkora.ui.typography;

import com.nekkora.tokens.FontRole;
import com.nekkora.tokens.FontWeight;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.IconSize;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.Dimension;
import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;

/**
 * Base dos componentes de tipografia: um {@link JLabel} com nível ({@link TypeStyle}) e tom
 * ({@link TextTone}) semânticos. Fonte e cor são reaplicadas a cada troca de tema.
 */
public class NekkoraTextLabel extends JLabel {
    /** Folga relativa para títulos na fonte de display (ver {@link #getPreferredSize()}). */
    private static final float DISPLAY_SLACK = 0.06f;

    private TypeStyle style;
    private TextTone tone;
    private FontWeight weight;

    public NekkoraTextLabel(String text, TypeStyle style, TextTone tone) {
        super(text);
        this.style = style;
        this.tone = tone;
        applyStyle();
    }

    public TypeStyle getTypeStyle() {
        return style;
    }

    public void setTypeStyle(TypeStyle style) {
        this.style = style;
        applyStyle();
    }

    public TextTone getTone() {
        return tone;
    }

    public void setTone(TextTone tone) {
        this.tone = tone;
        applyStyle();
    }

    /** Sobrescreve o peso do nível (ex.: um valor em destaque no corpo). */
    public void setWeight(FontWeight weight) {
        this.weight = weight;
        applyStyle();
    }

    /** Ícone à esquerda do texto, na cor do texto. */
    public void setIcon(IconName icon) {
        setIcon(icon == null ? null : NekkoraIcon.of(icon, style != null && style.size() >= 16 ? IconSize.L : IconSize.M));
        setIconTextGap(Scale.px(6));
    }

    /**
     * Folga horizontal para a fonte de display (Sora): no Windows, o texto desenhado na tela chega a ~4% mais
     * largo que a métrica usada no layout (dicas de renderização diferentes), e a última letra de títulos em
     * layouts que respeitam a largura preferida (BoxLayout, FlowLayout) era cortada.
     */
    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        String t = getText();
        if (isPreferredSizeSet() || style == null || style.role() != FontRole.DISPLAY || t == null || t.isEmpty()
                || BasicHTML.isHTMLString(t)) {
            return d;
        }
        int extra = Math.max(Scale.px(2), Math.round(getFontMetrics(getFont()).stringWidth(t) * DISPLAY_SLACK));
        return new Dimension(d.width + extra, d.height);
    }

    /** O máximo do JLabel vem do UI delegate (sem a folga); BoxLayout o usaria para cortar a largura de novo. */
    @Override
    public Dimension getMaximumSize() {
        Dimension max = super.getMaximumSize();
        Dimension pref = getPreferredSize();
        return new Dimension(Math.max(max.width, pref.width), Math.max(max.height, pref.height));
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyStyle();
    }

    private void applyStyle() {
        if (style == null) return; // chamado pelo construtor de JLabel antes dos campos
        setFont(weight == null ? NekkoraFonts.font(style) : NekkoraFonts.font(style, weight));
        setForeground(Tokens.color(tone.token()));
    }
}
