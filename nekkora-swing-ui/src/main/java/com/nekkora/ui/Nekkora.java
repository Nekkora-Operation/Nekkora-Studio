package com.nekkora.ui;

import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.ui.feedback.NekkoraTooltips;
import com.nekkora.ui.theme.ThemeManager;

/**
 * Ponto de entrada da Nekkora Swing UI.
 *
 * <pre>{@code
 * public static void main(String[] args) {
 *     SwingUtilities.invokeLater(() -> {
 *         Nekkora.install();
 *         NekkoraFrame f = new NekkoraFrame("Nekkora Studio");
 *         ...
 *     });
 * }
 * }</pre>
 */
public final class Nekkora {
    private Nekkora() {}

    /** Instala tema (o último escolhido, ou Sakura Dark), fontes e padrões globais. */
    public static void install() {
        configureSystem();
        ThemeManager.install();
        NekkoraTooltips.configure();
    }

    /** Instala com um tema específico (não lê a preferência salva). */
    public static void install(NekkoraTheme theme) {
        configureSystem();
        ThemeManager.install(theme);
        NekkoraTooltips.configure();
    }

    private static void configureSystem() {
        // barra de título integrada ao tema no Windows/Linux (FlatLaf); no macOS, barra nativa
        if (System.getProperty("flatlaf.useWindowDecorations") == null) {
            System.setProperty("flatlaf.useWindowDecorations", "true");
        }
        if (System.getProperty("flatlaf.menuBarEmbedded") == null) {
            System.setProperty("flatlaf.menuBarEmbedded", "true");
        }
    }
}
