package com.nekkora.ui.input;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.function.IntConsumer;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/** Setas ↑/↓ (e Shift para passo 10) em campos numéricos. */
final class NumericKeys {
    private NumericKeys() {}

    static void install(JComponent c, IntConsumer step) {
        bind(c, KeyEvent.VK_UP, 0, "nekkora.inc", () -> step.accept(1));
        bind(c, KeyEvent.VK_DOWN, 0, "nekkora.dec", () -> step.accept(-1));
        bind(c, KeyEvent.VK_UP, KeyEvent.SHIFT_DOWN_MASK, "nekkora.inc10", () -> step.accept(10));
        bind(c, KeyEvent.VK_DOWN, KeyEvent.SHIFT_DOWN_MASK, "nekkora.dec10", () -> step.accept(-10));
    }

    private static void bind(JComponent c, int key, int mods, String name, Runnable r) {
        c.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, mods), name);
        c.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (c.isEnabled()) r.run();
            }
        });
    }
}
