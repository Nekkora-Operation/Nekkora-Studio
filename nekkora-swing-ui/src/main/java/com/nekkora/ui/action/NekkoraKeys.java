package com.nekkora.ui.action;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/** Atalhos de teclado: criação multiplataforma (Ctrl no Windows/Linux, ⌘ no macOS) e exibição. */
public final class NekkoraKeys {
    private NekkoraKeys() {}

    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");

    /** Modificador de atalhos do sistema (Ctrl ou ⌘). */
    public static int menuMask() {
        try {
            return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (java.awt.HeadlessException e) {
            return MAC ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
        }
    }

    /** Ex.: {@code shortcut(KeyEvent.VK_S)} = Ctrl+S / ⌘S. */
    public static KeyStroke shortcut(int keyCode) {
        return KeyStroke.getKeyStroke(keyCode, menuMask());
    }

    /** Ex.: {@code shortcut(KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK)} = Ctrl+Shift+S. */
    public static KeyStroke shortcut(int keyCode, int extraModifiers) {
        return KeyStroke.getKeyStroke(keyCode, menuMask() | extraModifiers);
    }

    /** Liga um atalho a uma ação enquanto o foco estiver dentro de {@code scope}. */
    public static void bind(JComponent scope, KeyStroke key, String id, Runnable action) {
        scope.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, id);
        scope.getActionMap().put(id, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    /** Texto legível do atalho: "Ctrl+Shift+S" (ou "⇧⌘S" no macOS). */
    public static String format(KeyStroke ks) {
        if (ks == null) return "";
        int m = ks.getModifiers();
        StringBuilder b = new StringBuilder();
        if (MAC) {
            if ((m & InputEvent.CTRL_DOWN_MASK) != 0) b.append('⌃');
            if ((m & InputEvent.ALT_DOWN_MASK) != 0) b.append('⌥');
            if ((m & InputEvent.SHIFT_DOWN_MASK) != 0) b.append('⇧');
            if ((m & InputEvent.META_DOWN_MASK) != 0) b.append('⌘');
            return b.append(keyText(ks.getKeyCode())).toString();
        }
        if ((m & InputEvent.CTRL_DOWN_MASK) != 0) b.append("Ctrl+");
        if ((m & InputEvent.ALT_DOWN_MASK) != 0) b.append("Alt+");
        if ((m & InputEvent.SHIFT_DOWN_MASK) != 0) b.append("Shift+");
        if ((m & InputEvent.META_DOWN_MASK) != 0) b.append("Meta+");
        return b.append(keyText(ks.getKeyCode())).toString();
    }

    private static String keyText(int code) {
        return switch (code) {
            case KeyEvent.VK_ENTER -> "Enter";
            case KeyEvent.VK_ESCAPE -> "Esc";
            case KeyEvent.VK_DELETE -> "Del";
            case KeyEvent.VK_BACK_SPACE -> "Backspace";
            case KeyEvent.VK_SPACE -> "Espaço";
            case KeyEvent.VK_TAB -> "Tab";
            case KeyEvent.VK_UP -> "↑";
            case KeyEvent.VK_DOWN -> "↓";
            case KeyEvent.VK_LEFT -> "←";
            case KeyEvent.VK_RIGHT -> "→";
            case KeyEvent.VK_COMMA -> ",";
            case KeyEvent.VK_PERIOD -> ".";
            case KeyEvent.VK_SLASH -> "/";
            default -> KeyEvent.getKeyText(code);
        };
    }
}
