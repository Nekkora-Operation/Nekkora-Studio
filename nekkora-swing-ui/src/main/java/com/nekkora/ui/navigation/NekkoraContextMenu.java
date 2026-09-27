package com.nekkora.ui.navigation;

import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;

/** Atalho semântico para menus de contexto (ver {@link NekkoraPopupMenu#installContextMenu}). */
public final class NekkoraContextMenu {
    private NekkoraContextMenu() {}

    public static void install(JComponent target, Supplier<JPopupMenu> factory) {
        NekkoraPopupMenu.installContextMenu(target, factory);
    }
}
