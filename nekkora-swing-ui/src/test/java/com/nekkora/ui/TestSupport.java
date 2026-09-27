package com.nekkora.ui;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.ui.theme.ThemeManager;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/** Utilitários de teste: tudo na EDT, renderização fora da tela (funciona headless). */
public final class TestSupport {
    private TestSupport() {}

    public static void onEdt(Runnable r) {
        onEdt(() -> {
            r.run();
            return null;
        });
    }

    public static <T> T onEdt(Callable<T> c) {
        if (SwingUtilities.isEventDispatchThread()) {
            try {
                return c.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    out.set(c.call());
                } catch (Throwable t) {
                    err.set(t);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (err.get() != null) {
            if (err.get() instanceof RuntimeException re) throw re;
            if (err.get() instanceof Error er) throw er;
            throw new RuntimeException(err.get());
        }
        return out.get();
    }

    /** Instala (ou troca) o tema. */
    public static void theme(NekkoraTheme theme) {
        onEdt(() -> {
            if (ThemeManager.isInstalled()) ThemeManager.setTheme(theme);
            else ThemeManager.install(theme);
        });
    }

    /** Dimensiona, faz o layout recursivo e pinta em uma imagem. */
    public static BufferedImage render(JComponent c, int w, int h) {
        return onEdt(() -> {
            c.setSize(w, h);
            layout(c);
            layout(c); // segunda passada: componentes com quebra de linha
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(ThemeManager.color(ColorToken.BACKGROUND_CANVAS));
            g.fillRect(0, 0, w, h);
            c.printAll(g);
            g.dispose();
            return img;
        });
    }

    public static void layout(Component c) {
        if (c instanceof Container ct) {
            ct.doLayout();
            for (Component ch : ct.getComponents()) layout(ch);
        }
    }
}
