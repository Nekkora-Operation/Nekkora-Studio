package com.nekkora.ui.navigation;

import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.action.NekkoraAction;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.theme.NekkoraFonts;
import javax.swing.JMenu;
import javax.swing.KeyStroke;

/** Menu de barra de menus ("Arquivo", "Editar"...), com itens Nekkora. */
public class NekkoraMenu extends JMenu {

    public NekkoraMenu(String label) {
        super(label);
    }

    public NekkoraMenu item(String label, IconName icon, KeyStroke shortcut, Runnable action) {
        NekkoraMenuItem mi = new NekkoraMenuItem(label, icon, shortcut);
        mi.addActionListener(e -> action.run());
        add(mi);
        return this;
    }

    public NekkoraMenu item(NekkoraAction action) {
        add(new NekkoraMenuItem(action));
        return this;
    }

    public NekkoraMenu separator() {
        addSeparator();
        return this;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setFont(NekkoraFonts.font(TypeStyle.BODY));
    }
}
