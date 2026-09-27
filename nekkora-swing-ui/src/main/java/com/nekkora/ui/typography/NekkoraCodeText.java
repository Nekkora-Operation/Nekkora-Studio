package com.nekkora.ui.typography;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Radius;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.BorderFactory;

/** Código/identificador em linha, em fonte monoespaçada, opcionalmente em "chip" rebaixado. */
public class NekkoraCodeText extends NekkoraTextLabel {
    private boolean chip;

    public NekkoraCodeText(String text) {
        this(text, true);
    }

    public NekkoraCodeText(String text, boolean chip) {
        super(text, TypeStyle.MONOSPACE, TextTone.PRIMARY);
        setChip(chip);
    }

    public void setChip(boolean chip) {
        this.chip = chip;
        setBorder(chip ? BorderFactory.createEmptyBorder(Scale.px(2), Scale.px(6), Scale.px(2), Scale.px(6)) : null);
        repaint();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setChip(chip);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (chip) {
            Graphics2D g2 = Paint.prepare(g);
            Paint.fillRound(g2, 0, 0, getWidth(), getHeight(), Scale.px(Radius.SMALL), Tokens.color(ColorToken.BACKGROUND_SUNKEN));
            Paint.strokeRound(g2, 0, 0, getWidth(), getHeight(), Scale.px(Radius.SMALL), Tokens.color(ColorToken.BORDER_SUBTLE));
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
