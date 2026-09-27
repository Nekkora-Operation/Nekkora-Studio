package com.nekkora.ui;

import java.awt.event.ActionEvent;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/** Aciona o binding de teclado de um componente (sem depender de foco real — funciona headless). */
final class Keys {
    private Keys() {}

    static boolean press(JComponent c, String keyStroke) {
        return press(c, KeyStroke.getKeyStroke(keyStroke));
    }

    static boolean press(JComponent c, KeyStroke ks) {
        for (int cond : new int[] {JComponent.WHEN_FOCUSED, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, JComponent.WHEN_IN_FOCUSED_WINDOW}) {
            Object key = c.getInputMap(cond).get(ks);
            if (key == null) continue;
            Action a = c.getActionMap().get(key);
            if (a == null || !a.isEnabled()) continue;
            a.actionPerformed(new ActionEvent(c, ActionEvent.ACTION_PERFORMED, String.valueOf(key)));
            return true;
        }
        return false;
    }
}
