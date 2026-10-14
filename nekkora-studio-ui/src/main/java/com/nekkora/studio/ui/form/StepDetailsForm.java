package com.nekkora.studio.ui.form;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.studio.ui.ElementPresentation;
import com.nekkora.studio.ui.ElementPresentation.Row;
import com.nekkora.studio.ui.StepPresentation;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.container.NekkoraSection;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.data.NekkoraKeyValueView;
import com.nekkora.ui.input.NekkoraFormField;
import com.nekkora.ui.typography.NekkoraText;
import com.nekkora.ui.typography.TextTone;
import java.util.List;
import javax.swing.JComponent;

/**
 * Primeira visualização expandida de uma etapa: campos básicos editáveis (nome, tipo, descrição) e os
 * detalhes somente leitura (posição, origem, elemento, contexto). O editor avançado é da STUDIO-004.
 */
public final class StepDetailsForm extends StudioForm<StepForm.Values> {
    private final StepId stepId;
    private final StepForm basics;
    private final NekkoraKeyValueView overview = new NekkoraKeyValueView();
    private final NekkoraKeyValueView element = new NekkoraKeyValueView();
    private final NekkoraKeyValueView technical = new NekkoraKeyValueView();
    private final List<Row> overviewRows;

    public StepDetailsForm(Automation automation, AutomationStep step) {
        this.stepId = step.id();
        basics = StepForm.forEdit(step);
        add(basics);
        NekkoraStack details = NekkoraStack.vertical(Spacing.SPACE_6);
        overviewRows = StepPresentation.overview(automation, step);
        fill(overview, overviewRows);
        details.add(new NekkoraSection("Resumo").setContent(overview));
        if (step.element().isPresent()) {
            fill(element, ElementPresentation.rows(step.element().get()));
            details.add(new NekkoraSection("Elemento").setContent(element));
        } else {
            details.add(new NekkoraSection("Elemento").setContent(new NekkoraText("Nenhum elemento vinculado.", TextTone.MUTED)));
        }
        fill(technical, StepPresentation.technical(step));
        details.add(new NekkoraSection("Contexto").setContent(technical));
        add(details);
    }

    public StepId stepId() {
        return stepId;
    }

    /** Linhas do resumo exibidas (para verificar que correspondem à etapa). */
    public List<Row> overviewRows() {
        return overviewRows;
    }

    private static void fill(NekkoraKeyValueView view, List<Row> rows) {
        for (Row r : rows) view.add(r.label(), r.value());
    }

    public StepForm basics() {
        return basics;
    }

    @Override
    public StepForm.Values values() {
        return basics.values();
    }

    @Override
    protected NekkoraFormField fieldFor(ValidationProblem.Field field) {
        return basics.fieldFor(field);
    }

    @Override
    protected NekkoraFormField mainField() {
        return basics.mainField();
    }

    @Override
    public JComponent initialFocus() {
        return basics.initialFocus();
    }

    @Override
    protected NekkoraFormField[] fields() {
        return basics.fields();
    }
}
