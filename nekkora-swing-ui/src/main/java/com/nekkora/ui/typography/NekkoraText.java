package com.nekkora.ui.typography;

import com.nekkora.tokens.TypeStyle;

/** Texto de corpo em uma linha. Para parágrafos com quebra, use {@link NekkoraParagraph}. */
public class NekkoraText extends NekkoraTextLabel {

    /** Tamanhos de corpo. */
    public enum Size {
        LARGE(TypeStyle.BODY_L),
        DEFAULT(TypeStyle.BODY),
        SMALL(TypeStyle.BODY_S);

        private final TypeStyle style;

        Size(TypeStyle style) {
            this.style = style;
        }
    }

    public NekkoraText(String text) {
        this(text, Size.DEFAULT, TextTone.PRIMARY);
    }

    public NekkoraText(String text, TextTone tone) {
        this(text, Size.DEFAULT, tone);
    }

    public NekkoraText(String text, Size size, TextTone tone) {
        super(text, size.style, tone);
    }

    public static NekkoraText secondary(String text) {
        return new NekkoraText(text, TextTone.SECONDARY);
    }

    public static NekkoraText muted(String text) {
        return new NekkoraText(text, TextTone.MUTED);
    }
}
