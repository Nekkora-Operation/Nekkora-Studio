package com.nekkora.studio.ui;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.ModelVersion;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.ui.ElementPresentation.Row;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.core.Status;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.data.NekkoraBadge;
import com.nekkora.ui.data.NekkoraStatusBadge;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.studio.NekkoraInspector;
import com.nekkora.ui.studio.NekkoraPropertiesGroup;
import com.nekkora.ui.typography.NekkoraParagraph;
import com.nekkora.ui.typography.TextTone;
import java.util.List;

/**
 * Preenche o {@link NekkoraInspector} com as propriedades do item selecionado (automação, sessão ou etapa).
 * Somente leitura: alterações acontecem pelos diálogos, que passam pelas operações de domínio — assim o
 * painel nunca mantém uma cópia própria dos dados. Nenhum identificador interno é exibido.
 */
public final class PropertiesPresenter {

    /** Ações oferecidas no painel. */
    public interface Actions {
        void addSession();

        void renameSession(SessionId session);

        void addStep(SessionId session);

        void openStep(StepId step);
    }

    private final NekkoraInspector inspector;
    private final Actions actions;

    public PropertiesPresenter(NekkoraInspector inspector, Actions actions) {
        this.inspector = inspector;
        this.actions = actions;
    }

    public void showNothing() {
        inspector.clearGroups();
        inspector.setSubjectExtra(null);
        inspector.showEmpty();
    }

    public void showAutomation(Automation a) {
        inspector.clearGroups();
        inspector.setSubject(IconName.WORKFLOW, Tone.ACCENT, a.name(), "Automação");
        inspector.setSubjectExtra(new NekkoraStatusBadge(Status.DRAFT).withSize(NekkoraBadge.Size.SMALL));
        NekkoraPropertiesGroup general = inspector.addGroup("Geral");
        general.addRow("Nome", value(a.name()));
        general.addRow("Descrição", paragraph(a.description()));
        general.addRow("Estado", value(a.status().label() + " em memória — não salvo"));
        NekkoraPropertiesGroup structure = inspector.addGroup("Estrutura");
        structure.addRow("Sessões", value(Integer.toString(a.sessions().size())));
        structure.addRow("Etapas", value(Integer.toString(a.stepCount())));
        NekkoraPropertiesGroup record = inspector.addGroup("Registro");
        record.addRow("Criada em", value(StudioText.dateTime(a.createdAt())));
        record.addRow("Atualizada em", value(StudioText.dateTime(a.updatedAt())));
        record.addRow("Versão do modelo", value(a.modelVersion().equals(ModelVersion.CURRENT) ? a.modelVersion() : a.modelVersion() + " (anterior)"));
        record.setExpanded(false);
        NekkoraPropertiesGroup act = inspector.addGroup("Ações");
        act.addFullRow(buttons(NekkoraButton.secondary("Nova sessão").withIcon(IconName.LAYERS).onClick(e -> actions.addSession())));
    }

    public void showSession(Automation a, SessionId sessionId) {
        LogicalSession s = a.session(sessionId).orElseThrow();
        int number = a.sessionNumber(sessionId);
        inspector.clearGroups();
        inspector.setSubject(IconName.LAYERS, Tone.ACCENT, s.name(), "Sessão " + number + " de " + a.sessions().size() + " · " + a.name());
        inspector.setSubjectExtra(null);
        NekkoraPropertiesGroup general = inspector.addGroup("Geral");
        general.addRow("Nome", value(s.name()));
        general.addRow("Descrição", paragraph(s.description()));
        if (!s.notes().isBlank()) general.addRow("Observações", paragraph(s.notes()));
        general.addRow("Ordem", value(number + "ª sessão"));
        general.addRow("Etapas", value(StudioText.steps(s.stepCount())));
        NekkoraPropertiesGroup act = inspector.addGroup("Ações");
        act.addFullRow(buttons(
                NekkoraButton.secondary("Adicionar etapa").withIcon(IconName.PLUS).onClick(e -> actions.addStep(sessionId)),
                NekkoraButton.ghost("Renomear").withIcon(IconName.EDIT).onClick(e -> actions.renameSession(sessionId))));
    }

    public void showStep(Automation a, StepId stepId) {
        AutomationStep step = a.step(stepId).orElseThrow();
        inspector.clearGroups();
        inspector.setSubject(ActionTypeStyle.icon(step.actionType()), ActionTypeStyle.tone(step.actionType()), step.name(),
                StepPresentation.positionTitle(a, step));
        inspector.setSubjectExtra(new NekkoraBadge(step.provenance().origin().label(), Tone.NEUTRAL)
                .withIcon(ActionTypeStyle.icon(step.provenance().origin())).withSize(NekkoraBadge.Size.SMALL));
        NekkoraPropertiesGroup general = inspector.addGroup("Geral");
        general.addRow("Nome", value(step.name()));
        general.addRow("Descrição", paragraph(step.description()));
        rows(general, StepPresentation.overview(a, step));
        NekkoraPropertiesGroup element = inspector.addGroup("Elemento");
        step.element().ifPresentOrElse(e -> rows(element, ElementPresentation.rows(e)),
                () -> element.addFullRow(paragraph("Nenhum elemento vinculado. A gravação (próximas versões) poderá vincular o elemento da página.")));
        NekkoraPropertiesGroup technical = inspector.addGroup("Contexto");
        rows(technical, StepPresentation.technical(step));
        technical.setExpanded(false);
        NekkoraPropertiesGroup act = inspector.addGroup("Ações");
        act.addFullRow(buttons(NekkoraButton.secondary("Ver detalhes").withIcon(IconName.EYE).onClick(e -> actions.openStep(stepId))));
    }

    private static void rows(NekkoraPropertiesGroup g, List<Row> rows) {
        for (Row r : rows) g.addRow(r.label(), value(r.value()));
    }

    private static NekkoraParagraph value(String text) {
        NekkoraParagraph p = new NekkoraParagraph(StudioText.orDash(text), TextTone.PRIMARY);
        p.getAccessibleContext().setAccessibleName(StudioText.orDash(text));
        return p;
    }

    private static NekkoraParagraph paragraph(String text) {
        NekkoraParagraph p = new NekkoraParagraph(StudioText.orDash(text), text == null || text.isBlank() ? TextTone.MUTED : TextTone.PRIMARY);
        p.getAccessibleContext().setAccessibleName(StudioText.orDash(text));
        return p;
    }

    private static NekkoraStack buttons(NekkoraButton... buttons) {
        NekkoraStack row = NekkoraStack.horizontal(Spacing.SPACE_4).align(NekkoraStack.Align.CENTER);
        row.addAll(buttons);
        row.addGlue();
        return row;
    }
}
