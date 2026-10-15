package com.nekkora.studio.ui.form;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.studio.ui.StudioText;
import com.nekkora.ui.input.NekkoraComboBox;
import com.nekkora.ui.input.NekkoraFormField;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/**
 * Mover uma etapa (STUDIO-004): sessão de destino — da MESMA automação — e posição dentro dela. A etapa continua sendo
 * a mesma (identidade, procedência, referência e comentários); só a posição muda.
 */
public final class MoveStepForm extends StudioForm<MoveStepForm.Values> {

    /** Destino escolhido: sessão e posição final (0..n) contada sem a própria etapa. */
    public record Values(SessionId session, int index) {}

    /** Opção de posição: o usuário vê "No início", "Depois de 02 · Preencher Senha", "No final". */
    public record Position(int index, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final Automation automation;
    private final AutomationStep step;
    private final NekkoraComboBox<StepForm.SessionChoice> session;
    private final NekkoraFormField sessionField;
    private final NekkoraComboBox<Position> position = new NekkoraComboBox<>();
    private final NekkoraFormField positionField;

    public MoveStepForm(Automation automation, AutomationStep step) {
        this.automation = automation;
        this.step = step;
        List<StepForm.SessionChoice> choices = new ArrayList<>();
        StepForm.SessionChoice current = null;
        SessionId currentId = automation.sessionOf(step.id()).orElseThrow().id();
        int n = 0;
        for (LogicalSession s : automation.sessions()) {
            StepForm.SessionChoice c = new StepForm.SessionChoice(s.id(), (++n) + " · " + s.name());
            choices.add(c);
            if (s.id().equals(currentId)) current = c;
        }
        session = new NekkoraComboBox<>(choices);
        session.setLabelProvider(StepForm.SessionChoice::label);
        session.setSelectedItem(current);
        sessionField = new NekkoraFormField("Sessão de destino", session).required()
                .helper("Somente sessões desta automação. A etapa continua a mesma: só muda de lugar.");
        add(sessionField);
        position.setLabelProvider(Position::label);
        positionField = new NekkoraFormField("Posição", position).required();
        add(positionField);
        session.addActionListener(e -> fillPositions());
        fillPositions();
    }

    private void fillPositions() {
        StepForm.SessionChoice c = (StepForm.SessionChoice) session.getSelectedItem();
        position.removeAllItems();
        if (c == null) return;
        LogicalSession s = automation.session(c.id()).orElseThrow();
        List<AutomationStep> others = s.steps().stream().filter(x -> !x.id().equals(step.id())).toList();
        position.addItem(new Position(0, "No início"));
        for (int i = 0; i < others.size(); i++) {
            int number = automation.positionOf(others.get(i).id()).orElseThrow().number();
            position.addItem(new Position(i + 1, i + 1 == others.size() ? "No final (depois de " + StudioText.number(number) + " · " + others.get(i).name() + ")"
                    : "Depois de " + StudioText.number(number) + " · " + others.get(i).name()));
        }
        int currentIndex = s.positionOf(step.id()) - 1;
        position.setSelectedIndex(currentIndex >= 0 ? currentIndex : position.getItemCount() - 1);
    }

    @Override
    public Values values() {
        StepForm.SessionChoice c = (StepForm.SessionChoice) session.getSelectedItem();
        Position p = (Position) position.getSelectedItem();
        return new Values(c == null ? null : c.id(), p == null ? 0 : p.index());
    }

    public NekkoraComboBox<StepForm.SessionChoice> sessionInput() {
        return session;
    }

    public NekkoraComboBox<Position> positionInput() {
        return position;
    }

    @Override
    protected NekkoraFormField fieldFor(ValidationProblem.Field field) {
        return field == ValidationProblem.Field.SESSION ? sessionField : positionField;
    }

    @Override
    protected NekkoraFormField mainField() {
        return sessionField;
    }

    @Override
    public JComponent initialFocus() {
        return session;
    }

    @Override
    protected NekkoraFormField[] fields() {
        return new NekkoraFormField[] {sessionField, positionField};
    }
}
