package com.nekkora.ui.typography;

import com.nekkora.tokens.FontWeight;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.IconSize;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import javax.swing.JLabel;

/**
 * Base dos componentes de tipografia: um {@link JLabel} com nível ({@link TypeStyle}) e tom
 * ({@link TextTone}) semânticos. Fonte e cor são reaplicadas a cada troca de tema.
 */
public class NekkoraTextLabel extends JLabel {
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
