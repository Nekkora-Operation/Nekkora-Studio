package com.nekkora.ui.typography;

import com.nekkora.tokens.TypeStyle;

/**
 * Título. Níveis: {@link Level#DISPLAY_L}, {@link Level#DISPLAY_M}, {@link Level#H1},
 * {@link Level#H2}, {@link Level#H3}.
 */
public class NekkoraHeading extends NekkoraTextLabel {

    public enum Level {
        DISPLAY_L(TypeStyle.DISPLAY_L),
        DISPLAY_M(TypeStyle.DISPLAY_M),
        H1(TypeStyle.H1),
        H2(TypeStyle.H2),
        H3(TypeStyle.H3);

        private final TypeStyle style;

        Level(TypeStyle style) {
            this.style = style;
        }

        public TypeStyle style() {
            return style;
        }
    }

    public NekkoraHeading(String text) {
        this(text, Level.H2);
    }

    public NekkoraHeading(String text, Level level) {
        super(text, level.style(), TextTone.PRIMARY);
        getAccessibleContext().setAccessibleDescription("heading");
    }

    public void setLevel(Level level) {
        setTypeStyle(level.style());
    }
}
