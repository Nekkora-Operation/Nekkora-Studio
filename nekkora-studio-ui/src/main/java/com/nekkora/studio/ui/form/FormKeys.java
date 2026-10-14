package com.nekkora.studio.ui.form;

import java.awt.KeyboardFocusManager;
import javax.swing.JComponent;

/** Comportamento de teclado dos formulários do Studio. */
final class FormKeys {
    private FormKeys() {}

    /**
     * Em áreas de texto, Tab e Shift+Tab passam a mover o foco (em vez de inserir tabulação), para que o
     * formulário inteiro seja navegável por teclado sem armadilhas.
     */
    static <T extends JComponent> T tabMovesFocus(T c) {
        c.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        c.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
        return c;
    }
}
