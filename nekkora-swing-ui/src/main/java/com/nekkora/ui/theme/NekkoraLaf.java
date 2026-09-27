package com.nekkora.ui.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.nekkora.tokens.NekkoraTheme;
import javax.swing.LookAndFeel;
import javax.swing.UIDefaults;

/**
 * Look and Feel Nekkora. Usa o FlatLaf apenas como <b>infraestrutura</b> (HiDPI, delegates,
 * decorações de janela); todas as cores, fontes, raios e ícones vêm dos tokens Nekkora
 * (ver {@link NekkoraUIDefaults}).
 *
 * <p>Não instancie diretamente: use {@link ThemeManager#install()} e {@link ThemeManager#setTheme}.
 */
public final class NekkoraLaf {
    private NekkoraLaf() {}

    static LookAndFeel create(NekkoraTheme theme) {
        return theme.isDark() ? new Dark(theme) : new Light(theme);
    }

    /** Variante escura. */
    public static final class Dark extends FlatDarkLaf {
        private final NekkoraTheme theme;

        Dark(NekkoraTheme theme) {
            this.theme = theme;
            setExtraDefaults(NekkoraUIDefaults.baseVariables(theme));
        }

        @Override
        public String getName() {
            return "Nekkora — " + theme.displayName();
        }

        @Override
        public String getID() {
            return "Nekkora";
        }

        @Override
        public UIDefaults getDefaults() {
            UIDefaults d = super.getDefaults();
            NekkoraUIDefaults.apply(d, theme);
            return d;
        }
    }

    /** Variante clara. */
    public static final class Light extends FlatLightLaf {
        private final NekkoraTheme theme;

        Light(NekkoraTheme theme) {
            this.theme = theme;
            setExtraDefaults(NekkoraUIDefaults.baseVariables(theme));
        }

        @Override
        public String getName() {
            return "Nekkora — " + theme.displayName();
        }

        @Override
        public String getID() {
            return "Nekkora";
        }

        @Override
        public UIDefaults getDefaults() {
            UIDefaults d = super.getDefaults();
            NekkoraUIDefaults.apply(d, theme);
            return d;
        }
    }
}
