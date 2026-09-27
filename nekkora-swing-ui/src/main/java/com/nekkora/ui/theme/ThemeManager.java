package com.nekkora.ui.theme;

import com.formdev.flatlaf.FlatLaf;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import java.awt.Color;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * Ponto único de tema da aplicação.
 *
 * <pre>{@code
 * public static void main(String[] args) {
 *     SwingUtilities.invokeLater(() -> {
 *         ThemeManager.install();                           // tema salvo ou Sakura Dark
 *         new MainWindow().setVisible(true);
 *     });
 * }
 * // em qualquer momento, sem reiniciar:
 * ThemeManager.setTheme(NekkoraThemes.MIDNIGHT_DARK);
 * }</pre>
 */
public final class ThemeManager {
    private static final String PREF_KEY = "nekkora.theme";
    private static final List<ThemeListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile NekkoraTheme current = NekkoraThemes.defaultTheme();
    private static volatile boolean installed;
    private static volatile boolean persist;

    private ThemeManager() {}

    /** Instala o Look and Feel Nekkora com o último tema salvo (ou o padrão). */
    public static void install() {
        persist = true;
        String saved = prefs() != null ? prefs().get(PREF_KEY, null) : null;
        install(saved == null ? NekkoraThemes.defaultTheme()
                : NekkoraThemes.byId(saved).orElse(NekkoraThemes.defaultTheme()));
    }

    /** Instala o Look and Feel Nekkora com o tema indicado. */
    public static void install(NekkoraTheme theme) {
        onEdt(() -> {
            NekkoraFonts.load();
            current = Objects.requireNonNull(theme);
            applyLaf(theme);
            installed = true;
        });
    }

    /** Tema ativo. Nunca nulo. */
    public static NekkoraTheme current() {
        return current;
    }

    /** Atalho para {@code current().color(token)}. */
    public static Color color(ColorToken token) {
        return current.color(token);
    }

    public static boolean isInstalled() {
        return installed;
    }

    /**
     * Troca o tema <b>ao vivo</b>: reinstala o Look and Feel, atualiza todas as janelas abertas e
     * notifica os {@link ThemeListener}s. Pode ser chamado de qualquer thread.
     */
    public static void setTheme(NekkoraTheme theme) {
        Objects.requireNonNull(theme);
        onEdt(() -> {
            NekkoraTheme old = current;
            if (!installed) {
                install(theme);
            } else {
                current = theme;
                applyLaf(theme);
                FlatLaf.updateUI();
            }
            if (persist && prefs() != null) prefs().put(PREF_KEY, theme.id());
            for (ThemeListener l : LISTENERS) l.themeChanged(old, theme);
        });
    }

    public static void addThemeListener(ThemeListener listener) {
        LISTENERS.add(Objects.requireNonNull(listener));
    }

    public static void removeThemeListener(ThemeListener listener) {
        LISTENERS.remove(listener);
    }

    private static void applyLaf(NekkoraTheme theme) {
        try {
            UIManager.setLookAndFeel(NekkoraLaf.create(theme));
        } catch (UnsupportedLookAndFeelException e) {
            throw new IllegalStateException("Não foi possível instalar o Look and Feel Nekkora", e);
        }
    }

    private static Preferences prefs() {
        try {
            return Preferences.userNodeForPackage(ThemeManager.class);
        } catch (SecurityException e) {
            return null;
        }
    }

    private static void onEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(r);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            throw new IllegalStateException(cause);
        }
    }
}
