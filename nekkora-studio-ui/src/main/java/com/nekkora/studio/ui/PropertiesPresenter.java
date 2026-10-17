package com.nekkora.studio.ui;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.ModelVersion;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepComment;
import com.nekkora.studio.domain.StepField;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.ui.ElementPresentation.Row;
import com.nekkora.tokens.ControlSize;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Preenche o {@link NekkoraInspector} com as propriedades do item selecionado (automação, sessão ou etapa).
 * Somente leitura: alterações acontecem pelos diálogos e ações, que passam pelas operações de domínio — assim o
 * painel nunca mantém uma cópia própria dos dados. Nenhum identificador interno é exibido.
 *
 * <p>STUDIO-004: ações de edição (editar, mover, duplicar, excluir, restaurar o original) e os grupos Parâmetros,
 * Comentários e Origem e alterações. Com a automação em gravação, as ações de alterar ficam indisponíveis e o motivo é
 * dito em texto.
 *
 * <p>STUDIO-005: grupo "Evidências" da etapa gravada (resumo estruturado, linha do tempo com horário real e ordem
 * técnica, resultado de negócio "Não validado") e, na automação, as evidências sem etapa definida. Somente leitura.
 */
public final class PropertiesPresenter {

    /** Ações oferecidas no painel. */
    public interface Actions {
        void addSession();

        void renameSession(SessionId session);

        void addStep(SessionId session);

        void openStep(StepId step);

        // ---- STUDIO-004 (padrões mantêm implementações anteriores)

        default void editAutomation() {
        }

        /** STUDIO-008: abre "Variáveis e dependências" da automação. */
        default void showVariables() {
        }

        default void editSession(SessionId session) {
            renameSession(session);
        }

        default void moveSession(SessionId session, int delta) {
        }

        default void deleteSession(SessionId session) {
        }

        default void moveStep(StepId step, int delta) {
        }

        default void moveStepTo(StepId step) {
        }

        default void duplicateStep(StepId step) {
        }

        default void deleteStep(StepId step) {
        }

        default void restoreOriginal(StepId step, StepField field) {
        }
    }

    /** Explica por que a edição está indisponível (gravação em andamento). */
    public static final String LOCKED_NOTE = "Esta automação está sendo gravada. Finalize ou cancele a gravação para editar com segurança.";

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
        showAutomation(a, true);
    }

    public void showAutomation(Automation a, boolean editable) {
        inspector.clearGroups();
        inspector.setSubject(IconName.WORKFLOW, Tone.ACCENT, a.name(), "Automação");
        inspector.setSubjectExtra(new NekkoraStatusBadge(Status.DRAFT).withSize(NekkoraBadge.Size.SMALL));
        NekkoraPropertiesGroup general = inspector.addGroup("Geral");
        general.addRow("Nome", value(a.name()));
        general.addRow("Descrição", paragraph(a.description()));
        general.addRow("Estado", value(a.status().label()));
        NekkoraPropertiesGroup structure = inspector.addGroup("Estrutura");
        structure.addRow("Sessões", value(Integer.toString(a.sessions().size())));
        structure.addRow("Etapas", value(Integer.toString(a.stepCount())));
        if (!a.unattributedEvidence().isEmpty()) {
            NekkoraPropertiesGroup amb = inspector.addGroup("Evidências sem etapa definida");
            amb.addFullRow(new NekkoraParagraph("Mudanças observadas depois de mais de uma ação candidata: nenhuma etapa foi escolhida.", TextTone.SECONDARY));
            timeline(amb, a.unattributedEvidence());
        }
        NekkoraPropertiesGroup record = inspector.addGroup("Registro");
        record.addRow("Criada em", value(StudioText.dateTime(a.createdAt())));
        record.addRow("Atualizada em", value(StudioText.dateTime(a.updatedAt())));
        // STUDIO-008: 1.0.0 (sem variáveis) e 1.1.0 (com catálogo) são ambas atuais; "anterior" só para versão não suportada
        record.addRow("Versão do modelo", value(ModelVersion.supported(a.modelVersion()) ? a.modelVersion() : a.modelVersion() + " (anterior)"));
        record.setExpanded(false);
        NekkoraPropertiesGroup act = inspector.addGroup("Ações");
        NekkoraButton edit = NekkoraButton.secondary("Editar propriedades").withIcon(IconName.EDIT).onClick(e -> actions.editAutomation());
        edit.setEnabled(editable);
        act.addFullRow(buttons(edit, NekkoraButton.ghost("Nova sessão").withIcon(IconName.LAYERS).onClick(e -> actions.addSession())));
        // STUDIO-008: consulta (permitida também durante a gravação; editar variáveis não)
        int vars = a.variables().size();
        NekkoraButton variables = NekkoraButton.ghost("Variáveis e dependências").withIcon(IconName.BRACES).onClick(e -> actions.showVariables());
        variables.getAccessibleContext().setAccessibleDescription(vars == 0 ? "Nenhuma variável declarada" : vars == 1 ? "1 variável declarada"
                : vars + " variáveis declaradas");
        act.addFullRow(buttons(variables));
        lockedNote(act, editable);
    }

    public void showSession(Automation a, SessionId sessionId) {
        showSession(a, sessionId, true);
    }

    public void showSession(Automation a, SessionId sessionId, boolean editable) {
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
        NekkoraButton add = NekkoraButton.secondary("Adicionar etapa").withIcon(IconName.PLUS).onClick(e -> actions.addStep(sessionId));
        NekkoraButton edit = NekkoraButton.ghost("Editar").withIcon(IconName.EDIT).onClick(e -> actions.editSession(sessionId));
        NekkoraButton up = small("Para cima", IconName.ARROW_UP, () -> actions.moveSession(sessionId, -1));
        NekkoraButton down = small("Para baixo", IconName.ARROW_DOWN, () -> actions.moveSession(sessionId, 1));
        NekkoraButton delete = small("Excluir", IconName.TRASH, () -> actions.deleteSession(sessionId));
        up.getAccessibleContext().setAccessibleName("Mover sessão para cima");
        down.getAccessibleContext().setAccessibleName("Mover sessão para baixo");
        delete.getAccessibleContext().setAccessibleName("Excluir sessão");
        add.setEnabled(editable);
        edit.setEnabled(editable);
        up.setEnabled(editable && number > 1);
        down.setEnabled(editable && number < a.sessions().size());
        delete.setEnabled(editable);
        act.addFullRow(buttons(add, edit));
        act.addFullRow(buttons(up, down, delete));
        lockedNote(act, editable);
    }

    public void showStep(Automation a, StepId stepId) {
        showStep(a, stepId, true);
    }

    public void showStep(Automation a, StepId stepId, boolean editable) {
        AutomationStep step = a.step(stepId).orElseThrow();
        inspector.clearGroups();
        inspector.setSubject(ActionTypeStyle.icon(step.actionType()), ActionTypeStyle.tone(step.actionType()), step.name(),
                StepPresentation.positionTitle(a, step));
        NekkoraStack badges = NekkoraStack.horizontal(Spacing.SPACE_2);
        badges.add(new NekkoraBadge(step.provenance().origin().label(), Tone.NEUTRAL)
                .withIcon(ActionTypeStyle.icon(step.provenance().origin())).withSize(NekkoraBadge.Size.SMALL));
        if (step.manualRevision().isPresent()) {
            badges.add(new NekkoraBadge("Alterada manualmente", Tone.WARNING).withIcon(IconName.EDIT).withSize(NekkoraBadge.Size.SMALL));
        }
        inspector.setSubjectExtra(badges);
        NekkoraPropertiesGroup general = inspector.addGroup("Geral");
        general.addRow("Nome", value(step.name()));
        general.addRow("Descrição", paragraph(step.description()));
        rows(general, StepPresentation.overview(a, step));
        NekkoraPropertiesGroup params = inspector.addGroup("Parâmetros");
        params.addRow("Parâmetros", value(StepPresentation.parameters(step.parameters())));
        NekkoraPropertiesGroup element = inspector.addGroup("Elemento");
        step.element().ifPresentOrElse(e -> {
            rows(element, ElementPresentation.rows(e));
            rows(element, ElementPresentation.locatorRows(e, step.manualLocators()));
        }, () -> element.addFullRow(paragraph("Nenhum elemento vinculado.")));
        List<Row> recorded = StepPresentation.recording(step);
        if (!recorded.isEmpty()) {
            NekkoraPropertiesGroup rec = inspector.addGroup("Gravação");
            rows(rec, recorded);
            NekkoraPropertiesGroup ev = inspector.addGroup("Evidências");
            rows(ev, EvidencePresentation.summary(step));
            timeline(ev, step.evidence());
        }
        NekkoraPropertiesGroup origin = inspector.addGroup("Origem e alterações");
        rows(origin, StepPresentation.revision(step));
        if (step.manualRevision().isPresent()) {
            List<NekkoraButton> restore = new ArrayList<>();
            for (StepField f : StepField.values()) {
                if (!step.modified(f)) continue;
                NekkoraButton b = small("Restaurar " + f.label().toLowerCase(java.util.Locale.ROOT), IconName.REFRESH, () -> actions.restoreOriginal(stepId, f));
                b.setEnabled(editable);
                restore.add(b);
            }
            for (NekkoraButton b : restore) origin.addFullRow(buttons(b));
        }
        NekkoraPropertiesGroup comments = inspector.addGroup("Comentários");
        if (step.comments().isEmpty()) comments.addFullRow(paragraph(null));
        for (StepComment c : step.comments()) {
            comments.addRow(StudioText.dateTime(c.createdAt()) + (c.editedAt() != null ? " (editado)" : ""), paragraph(c.text()));
        }
        NekkoraPropertiesGroup technical = inspector.addGroup("Contexto");
        rows(technical, StepPresentation.technical(step));
        technical.setExpanded(false);
        NekkoraPropertiesGroup act = inspector.addGroup("Ações");
        NekkoraButton edit = NekkoraButton.secondary(editable ? "Editar etapa" : "Ver detalhes").withIcon(editable ? IconName.EDIT : IconName.EYE)
                .onClick(e -> actions.openStep(stepId));
        LogicalSession session = a.sessionOf(stepId).orElseThrow();
        int pos = session.positionOf(stepId);
        NekkoraButton up = small("Para cima", IconName.ARROW_UP, () -> actions.moveStep(stepId, -1));
        NekkoraButton down = small("Para baixo", IconName.ARROW_DOWN, () -> actions.moveStep(stepId, 1));
        up.getAccessibleContext().setAccessibleName("Mover etapa para cima");
        down.getAccessibleContext().setAccessibleName("Mover etapa para baixo");
        NekkoraButton moveTo = small("Mover para…", IconName.SORT, () -> actions.moveStepTo(stepId));
        NekkoraButton duplicate = small("Duplicar", IconName.COPY, () -> actions.duplicateStep(stepId));
        NekkoraButton delete = small("Excluir", IconName.TRASH, () -> actions.deleteStep(stepId));
        delete.getAccessibleContext().setAccessibleName("Excluir etapa");
        up.setEnabled(editable && pos > 1);
        down.setEnabled(editable && pos < session.stepCount());
        moveTo.setEnabled(editable);
        duplicate.setEnabled(editable);
        delete.setEnabled(editable);
        act.addFullRow(buttons(edit));
        act.addFullRow(buttons(up, down, moveTo));
        act.addFullRow(buttons(duplicate, delete));
        lockedNote(act, editable);
    }

    /** Linhas da linha do tempo no painel (as mais recentes; a lista completa fica na aba Evidências dos detalhes). */
    static final int PANEL_TIMELINE = 20;

    private static void timeline(NekkoraPropertiesGroup g, List<com.nekkora.studio.domain.evidence.ObservedEvidence> evidence) {
        List<EvidencePresentation.TimelineEntry> all = EvidencePresentation.timeline(evidence);
        int from = Math.max(0, all.size() - PANEL_TIMELINE);
        if (from > 0) g.addFullRow(new NekkoraParagraph(from + " evidência(s) anterior(es) na aba Evidências dos detalhes.", TextTone.MUTED));
        for (EvidencePresentation.TimelineEntry t : all.subList(from, all.size())) {
            g.addRow(t.time(), paragraph(t.title() + " — " + t.detail()));
        }
    }

    private static void lockedNote(NekkoraPropertiesGroup g, boolean editable) {
        if (!editable) g.addFullRow(new NekkoraParagraph(LOCKED_NOTE, TextTone.SECONDARY));
    }

    private static NekkoraButton small(String label, IconName icon, Runnable action) {
        return NekkoraButton.ghost(label).withIcon(icon).withSize(ControlSize.SMALL).onClick(e -> action.run());
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
