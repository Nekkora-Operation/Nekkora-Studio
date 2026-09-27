package com.nekkora.ui;

import com.nekkora.ui.brand.NekkoraLogo;
import com.nekkora.ui.container.Background;
import com.nekkora.ui.container.NekkoraPanel;
import com.nekkora.ui.theme.Scale;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import javax.swing.JFrame;

/**
 * Janela principal Nekkora: ícone da marca, fundo do tema, tamanho mínimo pensado para
 * 1366×768 e abertura em ~85% da área útil da tela.
 */
public class NekkoraFrame extends JFrame {

    public NekkoraFrame(String title) {
        super(title);
        setIconImages(NekkoraLogo.appIconImages());
        setContentPane(new NekkoraPanel(new BorderLayout(), Background.CANVAS));
        setMinimumSize(new Dimension(Scale.px(1024), Scale.px(640)));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    /** Dimensiona para ~85% da área útil da tela e centraliza. */
    public void sizeToScreen() {
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int w = Math.max(getMinimumSize().width, Math.min(Scale.px(1600), (int) (usable.width * 0.85)));
        int h = Math.max(getMinimumSize().height, Math.min(Scale.px(1000), (int) (usable.height * 0.85)));
        setSize(Math.min(w, usable.width), Math.min(h, usable.height));
        setLocationRelativeTo(null);
    }
}
