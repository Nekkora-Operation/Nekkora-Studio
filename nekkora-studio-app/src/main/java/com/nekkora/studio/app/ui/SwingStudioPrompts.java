package com.nekkora.studio.app.ui;

import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.studio.ui.form.FormSpec;
import com.nekkora.studio.ui.form.StudioDialogs;
import java.awt.Component;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/** Diálogos reais (NekkoraDialog) ancorados na janela do Studio. */
public final class SwingStudioPrompts implements StudioPrompts {
    private final Supplier<Component> parent;

    public SwingStudioPrompts(Supplier<Component> parent) {
        this.parent = parent;
    }

    @Override
    public <V> boolean showForm(FormSpec<V> spec, Function<V, Optional<ValidationProblem>> submit) {
        return StudioDialogs.showForm(parent.get(), spec, submit);
    }

    @Override
    public boolean confirmExit(String message) {
        return StudioDialogs.confirmDiscardOnExit(parent.get(), message);
    }

    @Override
    public boolean confirm(Confirmation c) {
        return c.danger() ? com.nekkora.ui.dialog.NekkoraConfirmationDialog.confirmDanger(parent.get(), c.title(), c.message(), c.confirmLabel())
                : com.nekkora.ui.dialog.NekkoraConfirmationDialog.confirm(parent.get(), c.title(), c.message(), c.confirmLabel());
    }
}
