package com.nekkora.studio.app;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.ui.Nekkora;
import com.nekkora.ui.theme.ThemeManager;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/** Execução na EDT e renderização fora da tela (headless) para os testes da aplicação. */
public final class Edt {
    private Edt() {}

    public static void run(Runnable r) {
        call(() -> {
            r.run();
            return null;
        });
    }

    public static <T> T call(Callable<T> c) {
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
        if (err.get() instanceof RuntimeException re) throw re;
        if (err.get() instanceof Error er) throw er;
        if (err.get() != null) throw new RuntimeException(err.get());
        return out.get();
    }

    /** Instala o design system (primeira vez) ou troca o tema. */
    public static void theme(NekkoraTheme theme) {
        run(() -> {
            if (ThemeManager.isInstalled()) ThemeManager.setTheme(theme);
            else Nekkora.install(theme);
        });
    }

    /** Processa eventos pendentes da EDT (ex.: revalidações agendadas). */
    public static void flush() {
        for (int i = 0; i < 3; i++) run(() -> { });
    }

    public static BufferedImage render(JComponent c, int w, int h) {
        return call(() -> {
            c.setSize(w, h);
            layout(c);
            layout(c);
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
