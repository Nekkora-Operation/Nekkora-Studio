package com.nekkora.studio.ui.form;

import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.container.NekkoraSection;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.input.NekkoraFormField;
import com.nekkora.ui.input.NekkoraTextField;
import com.nekkora.ui.typography.NekkoraParagraph;
import com.nekkora.ui.typography.NekkoraText;
import com.nekkora.ui.typography.TextTone;
import java.util.List;
import javax.swing.JComponent;

/**
 * Renomear variável (STUDIO-008) com PRÉVIA: antes de confirmar, o usuário vê quais etapas passam a usar o novo nome.
 * A troca do nome e das referências é UMA operação (um Ctrl+Z desfaz tudo); nome original e procedência das etapas
 * gravadas não mudam.
 */
public final class RenameVariableForm extends StudioForm<String> {
    /** Quantas etapas a prévia lista nominalmente (o resto vira "e mais N"). */
    static final int PREVIEW_MAX = 12;

    private final NekkoraTextField name = new NekkoraTextField("Ex.: cliente.cpf");
    private final NekkoraFormField nameField;
    private final NekkoraParagraph preview;

    /**
     * @param currentName nome atual
     * @param consumers   lugares das etapas que usam a variável (ordem das etapas)
     */
    public RenameVariableForm(String currentName, List<String> consumers) {
        name.setText(currentName);
        name.selectAll();
        nameField = new NekkoraFormField("Novo nome", name).required()
                .helper("Letras, números, ponto, hífen ou sublinhado, começando por letra.");
        add(nameField);
        String summary = consumers.isEmpty() ? "Nenhuma etapa usa esta variável: só a declaração muda."
                : (consumers.size() == 1 ? "1 etapa passa" : consumers.size() + " etapas passam") + " a usar o novo nome, na mesma operação.";
        preview = new NekkoraParagraph(summary, TextTone.SECONDARY);
        preview.getAccessibleContext().setAccessibleName("Prévia da renomeação");
        add(preview);
        if (!consumers.isEmpty()) {
            NekkoraStack list = NekkoraStack.vertical(Spacing.SPACE_2);
            for (int i = 0; i < Math.min(PREVIEW_MAX, consumers.size()); i++) list.add(new NekkoraText(consumers.get(i)));
            if (consumers.size() > PREVIEW_MAX) list.add(new NekkoraText("e mais " + (consumers.size() - PREVIEW_MAX), TextTone.MUTED));
            add(new NekkoraSection("Etapas atualizadas").setContent(list));
        }
    }

    @Override
    public String values() {
        return name.getText().strip();
    }

    public NekkoraTextField nameInput() {
        return name;
    }

    public String previewText() {
        return preview.getText();
    }

    @Override
    protected NekkoraFormField fieldFor(ValidationProblem.Field field) {
        return nameField;
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
        return new NekkoraFormField[] {nameField};
    }
}
