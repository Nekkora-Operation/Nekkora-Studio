package com.nekkora.ui.dialog;

import com.nekkora.tokens.Spacing;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.input.NekkoraFormField;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Formulário em diálogo: campos empilhados, validação ao confirmar (o diálogo só fecha se o
 * validador aprovar) e foco no primeiro campo.
 */
public class NekkoraModalForm extends NekkoraDialog {
    private final NekkoraStack fields = NekkoraStack.vertical(Spacing.SPACE_6);
    private final List<NekkoraFormField> formFields = new ArrayList<>();

    public NekkoraModalForm(Component parent, String title, String confirmLabel, Predicate<NekkoraModalForm> validator) {
        super(parent, title);
        setContent(fields);
        addSecondary("Cancelar");
        addPrimary(confirmLabel, () -> validator == null || validator.test(this));
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                if (!formFields.isEmpty()) formFields.get(0).getControl().requestFocusInWindow();
            }
        });
    }

    public NekkoraModalForm addField(NekkoraFormField field) {
        formFields.add(field);
        fields.add(field);
        return this;
    }

    public List<NekkoraFormField> getFields() {
        return List.copyOf(formFields);
    }
}
