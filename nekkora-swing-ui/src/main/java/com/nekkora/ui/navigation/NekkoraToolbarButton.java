package com.nekkora.ui.navigation;

import com.nekkora.tokens.ControlSize;
import com.nekkora.ui.action.NekkoraAction;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.feedback.NekkoraTooltips;
import com.nekkora.ui.icon.IconName;
import javax.swing.KeyStroke;

/**
 * Botão de toolbar: discreto (ghost), ícone com texto opcional, tooltip com atalho.
 * Construa a partir de uma {@link NekkoraAction} para reaproveitar rótulo, ícone e atalho.
 */
public class NekkoraToolbarButton extends NekkoraButton {

    public NekkoraToolbarButton(IconName icon, String label) {
        this(icon, label, false);
    }

    /** @param showText mostra o rótulo ao lado do ícone (ações importantes) */
    public NekkoraToolbarButton(IconName icon, String label, boolean showText) {
        super(showText ? label : null, icon, Variant.GHOST);
        setControlSize(ControlSize.MEDIUM);
        setIconOnly(!showText);
        NekkoraTooltips.install(this, label, null);
        getAccessibleContext().setAccessibleName(label);
        setFocusable(true);
    }

    public NekkoraToolbarButton(NekkoraAction action, boolean showText) {
        this(action.iconName(), action.label(), showText);
        setAction(action);
        if (!showText) setText(null);
        setLeadingIcon(action.iconName());
        NekkoraTooltips.install(this, action.label(), action.shortcutKey());
    }

    /** Atualiza o tooltip com o atalho. */
    public NekkoraToolbarButton withShortcut(KeyStroke ks) {
        NekkoraTooltips.install(this, getAccessibleContext().getAccessibleName(), ks);
        return this;
    }

    @Override
    protected void configurePropertiesFromAction(javax.swing.Action a) {
        super.configurePropertiesFromAction(a);
        setIcon(null); // ícone é pintado pela lib
    }
}
