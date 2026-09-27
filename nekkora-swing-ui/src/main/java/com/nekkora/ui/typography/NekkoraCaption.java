package com.nekkora.ui.typography;

import com.nekkora.tokens.TypeStyle;

/** Legenda: metadados, datas, textos de apoio curtos. */
public class NekkoraCaption extends NekkoraTextLabel {

    public NekkoraCaption(String text) {
        super(text, TypeStyle.CAPTION, TextTone.MUTED);
    }

    public NekkoraCaption(String text, TextTone tone) {
        super(text, TypeStyle.CAPTION, tone);
    }
}
