package com.nekkora.ui.technical;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import javax.swing.BorderFactory;
import javax.swing.JTextArea;

/** Área de texto monoespaçada (payloads, scripts curtos, saídas). Fundo rebaixado. */
public class NekkoraMonospaceTextArea extends JTextArea {
    private boolean initialized;

    public NekkoraMonospaceTextArea() {
        this("", 8);
    }

    public NekkoraMonospaceTextArea(String text, int rows) {
        super(text, rows, 0);
        setTabSize(2);
        initialized = true;
        applyStyle();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyStyle();
    }

    private void applyStyle() {
        if (!initialized) return;
        setFont(NekkoraFonts.font(TypeStyle.MONOSPACE));
        setBackground(Tokens.color(ColorToken.BACKGROUND_SUNKEN));
        setForeground(Tokens.color(ColorToken.TEXT_PRIMARY));
        setBorder(BorderFactory.createEmptyBorder(Scale.px(10), Scale.px(12), Scale.px(10), Scale.px(12)));
    }
}
