package com.nekkora.studio.ui.form;

import com.nekkora.ui.dialog.NekkoraDialog;
import com.nekkora.ui.icon.IconName;
import java.util.Objects;

/**
 * Descrição completa de um diálogo de formulário (título, explicação, formulário, rótulo de confirmação).
 * Separar a especificação da exibição permite que a aplicação mostre o diálogo real e que os testes usem
 * exatamente o mesmo formulário sem abrir janelas.
 */
public record FormSpec<V>(String title, String description, IconName marker, StudioForm<V> form, String confirmLabel,
                          NekkoraDialog.Size size) {
    public FormSpec {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(confirmLabel, "confirmLabel");
        size = size == null ? NekkoraDialog.Size.MEDIUM : size;
    }
}
