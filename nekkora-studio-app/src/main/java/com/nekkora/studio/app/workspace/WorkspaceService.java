package com.nekkora.studio.app.workspace;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.DomainValidationException;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.port.StepIntake;
import java.util.Objects;
import javax.swing.SwingUtilities;

/**
 * Casos de uso da área de trabalho. Cada operação: (1) executa a operação de domínio — que valida antes de
 * alterar —, (2) atualiza o estado da aplicação e (3) notifica a interface. Se o domínio recusar, a
 * {@link DomainValidationException} sobe sem nenhuma alteração parcial.
 *
 * <p>Confinado à EDT: o estado é observado por componentes Swing. Implementa {@link StepIntake} — a porta
 * que o Recorder usará para entregar etapas sem simular cliques.
 */
public final class WorkspaceService implements StepIntake {
    private final StudioState state;

    public WorkspaceService(StudioState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public StudioState state() {
        return state;
    }

    /** Cria a automação, abre sua área de trabalho e a seleciona. */
    public Automation createAutomation(String name, String description) {
        requireEdt();
        Automation a = state.catalog().create(name, description);
        state.open(a.id());
        state.activate(a.id());
        state.select(a.id(), new Selection.OfAutomation(a.id()));
        state.markUnsaved();
        state.notice(new Notice(Notice.Kind.SUCCESS, "Automação criada", a.name() + " — rascunho em memória."));
        state.fire(a.id(), StudioChange.CATALOG, StudioChange.OPEN_AUTOMATIONS, StudioChange.ACTIVE_AUTOMATION,
                StudioChange.SELECTION, StudioChange.UNSAVED_CHANGES, StudioChange.NOTICE);
        return a;
    }

    /** Abre (ou apenas ativa) uma automação existente. */
    public void openAutomation(AutomationId id) {
        requireEdt();
        state.catalog().require(id);
        boolean opened = state.open(id);
        if (!opened && id.equals(state.activeId().orElse(null))) return;
        state.activate(id);
        if (opened) state.fire(id, StudioChange.OPEN_AUTOMATIONS, StudioChange.ACTIVE_AUTOMATION, StudioChange.SELECTION);
        else state.fire(id, StudioChange.ACTIVE_AUTOMATION, StudioChange.SELECTION);
    }

    /** Fecha a área de trabalho da automação (ela continua no catálogo em memória). */
    public void closeAutomation(AutomationId id) {
        requireEdt();
        if (!state.openAutomations().contains(id)) return;
        AutomationId next = state.close(id);
        state.activate(next);
        state.fire(id, StudioChange.OPEN_AUTOMATIONS, StudioChange.ACTIVE_AUTOMATION, StudioChange.SELECTION);
    }

    /** Acrescenta uma sessão ao final e a seleciona. */
    public LogicalSession addSession(AutomationId automationId, String name, String description) {
        requireEdt();
        Automation a = state.catalog().require(automationId);
        LogicalSession s = a.addSession(name, description);
        state.select(automationId, new Selection.OfSession(automationId, s.id()));
        changed(a, true);
        return s;
    }

    public void renameSession(AutomationId automationId, SessionId sessionId, String name) {
        requireEdt();
        Automation a = state.catalog().require(automationId);
        a.renameSession(sessionId, name);
        changed(a, false);
    }

    /** Etapa manual básica, acrescentada ao final da sessão e selecionada. */
    public AutomationStep addManualStep(AutomationId automationId, SessionId sessionId, String name, ActionType type, String description) {
        AutomationStep step = appendStep(automationId, sessionId, StepDraft.manual(name, type, description));
        state.select(automationId, new Selection.OfStep(automationId, sessionId, step.id()));
        state.fire(automationId, StudioChange.SELECTION);
        return step;
    }

    /** {@inheritDoc} A seleção do usuário não é alterada (o Recorder não deve "roubar" o foco). */
    @Override
    public AutomationStep appendStep(AutomationId automationId, SessionId sessionId, StepDraft draft) {
        requireEdt();
        Automation a = state.catalog().require(automationId);
        AutomationStep step = a.appendStep(sessionId, draft);
        changed(a, false);
        return step;
    }

    public void updateStepBasics(AutomationId automationId, StepId stepId, String name, String description, ActionType type) {
        requireEdt();
        Automation a = state.catalog().require(automationId);
        a.updateStepBasics(stepId, name, description, type);
        changed(a, false);
    }

    /** Seleciona um item da automação indicada (que se torna a ativa). */
    public void select(Selection selection) {
        requireEdt();
        AutomationId id = switch (selection) {
            case Selection.None n -> null;
            case Selection.OfAutomation s -> s.automation();
            case Selection.OfSession s -> s.automation();
            case Selection.OfStep s -> s.automation();
        };
        if (id == null) return;
        Automation a = state.catalog().require(id);
        if (selection instanceof Selection.OfSession s && a.session(s.session()).isEmpty()) {
            throw new DomainValidationException(ValidationProblem.SESSION_NOT_FOUND);
        }
        if (selection instanceof Selection.OfStep s && !a.session(s.session()).flatMap(x -> x.step(s.step())).isPresent()) {
            throw new DomainValidationException(ValidationProblem.STEP_NOT_FOUND);
        }
        boolean activeChanged = !id.equals(state.activeId().orElse(null));
        boolean opened = state.open(id);
        state.activate(id);
        if (selection.equals(state.selectionOf(id)) && !activeChanged) return;
        state.select(id, selection);
        if (opened) state.fire(id, StudioChange.OPEN_AUTOMATIONS, StudioChange.ACTIVE_AUTOMATION, StudioChange.SELECTION);
        else if (activeChanged) state.fire(id, StudioChange.ACTIVE_AUTOMATION, StudioChange.SELECTION);
        else state.fire(id, StudioChange.SELECTION);
    }

    /** Marca o início de uma operação interativa; {@code false} se já houver outra em andamento. */
    public boolean beginOperation(StudioOperation op) {
        requireEdt();
        if (state.currentOperation().isPresent()) return false;
        state.operation(op);
        state.fire(null, StudioChange.OPERATION);
        return true;
    }

    public void endOperation() {
        requireEdt();
        state.operation(null);
        state.fire(null, StudioChange.OPERATION);
    }

    private void changed(Automation a, boolean selectionChanged) {
        state.markUnsaved();
        if (selectionChanged) state.fire(a.id(), StudioChange.CONTENT, StudioChange.CATALOG, StudioChange.UNSAVED_CHANGES, StudioChange.SELECTION);
        else state.fire(a.id(), StudioChange.CONTENT, StudioChange.CATALOG, StudioChange.UNSAVED_CHANGES);
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("O estado do Studio só pode ser alterado na thread de interface (EDT).");
        }
    }
}
