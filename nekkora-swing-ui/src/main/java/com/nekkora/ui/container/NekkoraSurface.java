package com.nekkora.ui.container;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Radius;
import java.awt.BorderLayout;
import java.awt.LayoutManager;

/** Superfície: área de trabalho com fundo {@code background.surface}. */
public class NekkoraSurface extends NekkoraPanel {

    public NekkoraSurface() {
        this(new BorderLayout());
    }

    public NekkoraSurface(LayoutManager layout) {
        super(layout, Background.SURFACE);
    }

    /** Superfície destacada: contorno sutil e cantos arredondados. */
    public static NekkoraSurface outlined(LayoutManager layout) {
        NekkoraSurface s = new NekkoraSurface(layout);
        s.setOutline(ColorToken.BORDER_SUBTLE);
        s.setRadius(Radius.LARGE);
        return s;
    }
}
