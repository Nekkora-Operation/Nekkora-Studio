package com.nekkora.ui.navigation;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.action.NekkoraAction;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.IconSize;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Tokens;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

/** Item de menu com ícone Nekkora, atalho e variante destrutiva. */
public class NekkoraMenuItem extends JMenuItem {
    private boolean danger;
    private IconName iconName;
    private boolean initialized;

    public NekkoraMenuItem(String label, IconName icon, KeyStroke shortcut) {
        super(label);
        this.iconName = icon;
        setAccelerator(shortcut);
        initialized = true;
        applyStyle();
    }

    public NekkoraMenuItem(NekkoraAction action) {
        super(action);
        this.iconName = action.iconName();
        this.danger = action.isDanger();
        initialized = true;
        applyStyle();
    }

    public NekkoraMenuItem danger() {
        this.danger = true;
        applyStyle();
        return this;
    }

    public boolean isDanger() {
        return danger;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyStyle();
    }

    private void applyStyle() {
        if (!initialized) return;
        setFont(NekkoraFonts.font(TypeStyle.BODY));
        ColorToken c = danger ? ColorToken.STATUS_ERROR : ColorToken.TEXT_SECONDARY;
        setIcon(iconName == null ? null : NekkoraIcon.of(iconName, IconSize.M).withColor(c));
        if (danger) setForeground(Tokens.color(ColorToken.STATUS_ERROR));
    }
}
