package com.nekkora.showcase.kit;

import com.nekkora.ui.theme.Scale;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Caixa de tamanho fixo (px lógicos) para exemplos de layout (árvores, tabelas, splits). */
public final class Fixed extends JPanel {
    private final int w;
    private final int h;

    public Fixed(JComponent content, int logicalWidth, int logicalHeight) {
        super(new BorderLayout());
        setOpaque(false);
        this.w = logicalWidth;
        this.h = logicalHeight;
        add(content, BorderLayout.CENTER);
    }

    @Override
    public Dimension getPreferredSize() {
        // largura 0 = ocupa a largura disponível (não força rolagem horizontal)
        return new Dimension(w <= 0 ? Scale.px(320) : Scale.px(w), h <= 0 ? super.getPreferredSize().height : Scale.px(h));
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension p = getPreferredSize();
        return new Dimension(w <= 0 ? Integer.MAX_VALUE : p.width, p.height);
    }
}
