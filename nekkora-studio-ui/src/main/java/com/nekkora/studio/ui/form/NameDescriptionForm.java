package com.nekkora.studio.ui.form;

import com.nekkora.studio.domain.Texts;
import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.ui.input.NekkoraFormField;
import com.nekkora.ui.input.NekkoraTextArea;
import com.nekkora.ui.input.NekkoraTextField;
import javax.swing.JComponent;

/** Formulário de nome (obrigatório) e descrição opcional — automação, sessão, renomear sessão. */
public final class NameDescriptionForm extends StudioForm<NameDescriptionForm.Values> {

    /** Valores digitados (sem normalização — o domínio normaliza). */
    public record Values(String name, String description) {}

    private final NekkoraTextField name;
    private final NekkoraFormField nameField;
    private final NekkoraTextArea description;
    private final NekkoraFormField descriptionField;

    /**
     * @param nameLabel        rótulo do nome
     * @param namePlaceholder  exemplo no campo
     * @param helper           texto de ajuda do nome
     * @param withDescription  se mostra a descrição
     */
    public NameDescriptionForm(String nameLabel, String namePlaceholder, String helper, boolean withDescription) {
        name = new NekkoraTextField(namePlaceholder);
        nameField = new NekkoraFormField(nameLabel, name).required().helper(helper);
        add(nameField);
        if (withDescription) {
            description = FormKeys.tabMovesFocus(new NekkoraTextArea("Explique o objetivo em poucas palavras.", 3));
            descriptionField = new NekkoraFormField("Descrição", description.scrollable()).optional()
                    .helper("Até " + Texts.DESCRIPTION_MAX + " caracteres.");
            add(descriptionField);
        } else {
            description = null;
            descriptionField = null;
        }
    }

    public NameDescriptionForm withValues(String nameText, String descriptionText) {
        name.setText(nameText == null ? "" : nameText);
        if (description != null) description.setText(descriptionText == null ? "" : descriptionText);
        name.selectAll();
        return this;
    }

    @Override
    public Values values() {
        return new Values(name.getText(), description == null ? "" : description.getText());
    }

    public NekkoraTextField nameInput() {
        return name;
    }

    public NekkoraTextArea descriptionInput() {
        return description;
    }

    @Override
    protected NekkoraFormField fieldFor(ValidationProblem.Field field) {
        return field == ValidationProblem.Field.DESCRIPTION && descriptionField != null ? descriptionField : nameField;
    }

    @Override
    protected NekkoraFormField mainField() {
        return nameField;
    }

    @Override
    public JComponent initialFocus() {
        return name;
    }

    @Override
    protected NekkoraFormField[] fields() {
        return new NekkoraFormField[] {nameField, descriptionField};
    }
}
