package com.nekkora.ui.dialog;

import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.input.NekkoraFormField;
import com.nekkora.ui.input.NekkoraTextField;
import java.awt.Component;

/**
 * Confirmações prontas. Destrutivas usam botão vermelho, ícone de alerta e, opcionalmente,
 * exigem digitar um texto (ex.: o nome do item) para liberar a ação.
 */
public final class NekkoraConfirmationDialog {
    private NekkoraConfirmationDialog() {}

    /** Confirmação simples. {@code true} se o usuário confirmou. */
    public static boolean confirm(Component parent, String title, String message, String confirmLabel) {
        NekkoraDialog d = new NekkoraDialog(parent, title);
        d.setDialogSize(NekkoraDialog.Size.SMALL);
        d.setDescription(message);
        d.addSecondary("Cancelar");
        d.addPrimary(confirmLabel);
        return d.showDialog() == NekkoraDialog.Result.CONFIRMED;
    }

    /** Confirmação destrutiva (sem digitação). */
    public static boolean confirmDanger(Component parent, String title, String message, String confirmLabel) {
        return confirmDanger(parent, title, message, confirmLabel, null);
    }

    /**
     * Confirmação destrutiva. Se {@code typeToConfirm} não for nulo, o botão só é liberado
     * quando o usuário digita exatamente esse texto. O foco inicial fica em "Cancelar".
     */
    public static boolean confirmDanger(Component parent, String title, String message, String confirmLabel,
                                        String typeToConfirm) {
        NekkoraDialog d = new NekkoraDialog(parent, title);
        d.setDialogSize(NekkoraDialog.Size.SMALL);
        d.setMarker(IconName.ALERT, Tone.ERROR);
        d.setDescription(message);
        NekkoraButton cancel = d.addSecondary("Cancelar");
        NekkoraButton danger = d.addDanger(confirmLabel, () -> true);
        d.getRootPane().setDefaultButton(null); // Enter não confirma ação destrutiva por acidente
        if (typeToConfirm != null) {
            NekkoraTextField field = new NekkoraTextField(typeToConfirm);
            NekkoraFormField ff = new NekkoraFormField("Digite \"" + typeToConfirm + "\" para confirmar", field);
            danger.setEnabled(false);
            field.onTextChange(t -> danger.setEnabled(t.equals(typeToConfirm)));
            d.setContent(ff);
            d.addWindowListener(focusOn(field));
        } else {
            d.addWindowListener(focusOn(cancel));
        }
        return d.showDialog() == NekkoraDialog.Result.CONFIRMED;
    }

    private static java.awt.event.WindowAdapter focusOn(Component c) {
        return new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                c.requestFocusInWindow();
            }
        };
    }
}
