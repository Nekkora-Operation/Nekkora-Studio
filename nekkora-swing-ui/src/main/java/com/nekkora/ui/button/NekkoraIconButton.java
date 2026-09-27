package com.nekkora.ui.button;

import com.nekkora.tokens.ControlSize;
import com.nekkora.ui.icon.IconName;

/**
 * Botão só com ícone. O texto de ajuda é obrigatório: vira tooltip e nome acessível.
 *
 * <pre>{@code new NekkoraIconButton(IconName.REFRESH, "Atualizar")}</pre>
 */
public class NekkoraIconButton extends NekkoraButton {

    public NekkoraIconButton(IconName icon, String label) {
        this(icon, label, Variant.GHOST);
    }

    public NekkoraIconButton(IconName icon, String label, Variant variant) {
        super(null, icon, variant);
        setIconOnly(true);
        setLabel(label);
    }

    /** Texto de ajuda (tooltip + nome acessível). */
    public void setLabel(String label) {
        setToolTipText(label);
        getAccessibleContext().setAccessibleName(label);
    }

    @Override
    public NekkoraIconButton withSize(ControlSize size) {
        super.withSize(size);
        return this;
    }
}
