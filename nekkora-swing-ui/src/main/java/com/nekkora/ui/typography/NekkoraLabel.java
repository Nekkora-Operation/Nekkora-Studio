package com.nekkora.ui.typography;

import com.nekkora.tokens.TypeStyle;
import java.awt.Component;

/** Rótulo de controle/formulário (nível LABEL). Associe ao controle com {@link #setLabelFor}. */
public class NekkoraLabel extends NekkoraTextLabel {

    public NekkoraLabel(String text) {
        super(text, TypeStyle.LABEL, TextTone.SECONDARY);
    }

    public NekkoraLabel(String text, Component labelFor) {
        this(text);
        setLabelFor(labelFor);
    }

    /** Rótulo em caixa alta, para grupos e seções pequenas. */
    public static NekkoraLabel overline(String text) {
        NekkoraLabel l = new NekkoraLabel(text == null ? null : text.toUpperCase());
        l.setTypeStyle(TypeStyle.OVERLINE);
        l.setTone(TextTone.MUTED);
        return l;
    }
}
