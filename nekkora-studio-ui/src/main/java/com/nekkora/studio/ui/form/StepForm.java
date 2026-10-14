package com.nekkora.studio.ui.form;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.ui.ActionTypeStyle;
import com.nekkora.ui.input.NekkoraComboBox;
import com.nekkora.ui.input.NekkoraFormField;
import com.nekkora.ui.input.NekkoraTextArea;
import com.nekkora.ui.input.NekkoraTextField;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/**
 * Formulário básico de etapa: nome, tipo de ação, sessão e descrição opcional. No modo de edição a sessão
 * não muda (mover etapas é da STUDIO-004). Os tipos oferecidos vêm do catálogo do domínio.
 */
public final class StepForm extends StudioForm<StepForm.Values> {

    /** Valores digitados. {@code session} é nulo no modo de edição. */
    public record Values(SessionId session, String name, ActionType actionType, String description) {}

    /** Opção de sessão: o identificador fica no item, o usuário vê apenas "2 · Consulta". */
    public record SessionChoice(SessionId id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final NekkoraComboBox<SessionChoice> session;
    private final NekkoraFormField sessionField;
    private final NekkoraTextField name = new NekkoraTextField("Ex.: Preencher usuário");
    private final NekkoraFormField nameField;
    private final NekkoraComboBox<ActionType> type;
    private final NekkoraFormField typeField;
    private final NekkoraTextArea description = FormKeys.tabMovesFocus(new NekkoraTextArea("O que esta etapa faz e por quê (opcional).", 3));
    private final NekkoraFormField descriptionField;

    private StepForm(List<SessionChoice> sessions, SessionChoice preselected, List<ActionType> types) {
        if (sessions != null) {
            session = new NekkoraComboBox<>(sessions);
            session.setLabelProvider(SessionChoice::label);
            if (preselected != null) session.setSelectedItem(preselected);
            sessionField = new NekkoraFormField("Sessão", session).required().helper("A etapa entra no final desta sessão.");
            add(sessionField);
        } else {
            session = null;
            sessionField = null;
        }
        nameField = new NekkoraFormField("Nome da etapa", name).required().helper("Uma instrução clara, como \"Clicar em Entrar\".");
        add(nameField);
        type = new NekkoraComboBox<>(types);
        type.setLabelProvider(t -> t.label() + " — " + t.description());
        type.setIconProvider(ActionTypeStyle::icon);
        typeField = new NekkoraFormField("Tipo de ação", type).required();
        add(typeField);
        descriptionField = new NekkoraFormField("Descrição", description.scrollable()).optional();
        add(descriptionField);
    }

    /** Criação: escolha da sessão (pré-selecionada, se houver). */
    public static StepForm forCreation(Automation automation, SessionId preselected) {
        List<SessionChoice> choices = new ArrayList<>();
        SessionChoice pre = null;
        int n = 0;
        for (LogicalSession s : automation.sessions()) {
            SessionChoice c = new SessionChoice(s.id(), (++n) + " · " + s.name());
            choices.add(c);
            if (s.id().equals(preselected)) pre = c;
        }
        if (pre == null && !choices.isEmpty()) pre = choices.get(choices.size() - 1);
        StepForm f = new StepForm(choices, pre, ActionType.manualChoices());
        f.type.setSelectedItem(ActionType.CLICK);
        return f;
    }

    /** Edição básica de uma etapa existente. */
    public static StepForm forEdit(AutomationStep step) {
        List<ActionType> types = new ArrayList<>(ActionType.manualChoices());
        if (!types.contains(step.actionType())) types.add(step.actionType());
        StepForm f = new StepForm(null, null, types);
        f.name.setText(step.name());
        f.description.setText(step.description());
        f.type.setSelectedItem(step.actionType());
        return f;
    }

    @Override
    public Values values() {
        SessionChoice c = session == null ? null : (SessionChoice) session.getSelectedItem();
        return new Values(c == null ? null : c.id(), name.getText(), (ActionType) type.getSelectedItem(), description.getText());
    }

    public NekkoraTextField nameInput() {
        return name;
    }

    public NekkoraComboBox<ActionType> typeInput() {
        return type;
    }

    public NekkoraComboBox<SessionChoice> sessionInput() {
        return session;
    }

    public NekkoraTextArea descriptionInput() {
        return description;
    }

    @Override
    protected NekkoraFormField fieldFor(ValidationProblem.Field field) {
        return switch (field) {
            case SESSION -> sessionField;
            case ACTION_TYPE, VALUE -> typeField;
            case DESCRIPTION -> descriptionField;
            default -> nameField;
        };
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
        return new NekkoraFormField[] {sessionField, nameField, typeField, descriptionField};
    }
}
