package com.nekkora.studio.app.workspace;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.DomainValidationException;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.StepEdit;
import com.nekkora.studio.domain.StepField;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.element.ManualLocator;
import com.nekkora.studio.domain.port.StepIntake;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.swing.SwingUtilities;

/**
 * Casos de uso da área de trabalho. Cada operação: (1) executa a operação de domínio — que valida antes de
 * alterar —, (2) atualiza o estado da aplicação e (3) notifica a interface. Se o domínio recusar, a
 * {@link DomainValidationException} sobe sem nenhuma alteração parcial.
 *
 * <p>Confinado à EDT: o estado é observado por componentes Swing. Implementa {@link StepIntake} — a porta
 * que o Recorder usará para entregar etapas sem simular cliques.
 *
 * <p>STUDIO-004: as operações de edição (D-S004-04) entram no {@link EditHistory} (desfazer/refazer em memória) e são
 * recusadas, AQUI no caso de uso, para a automação que está recebendo uma gravação ({@link RecordingGuard},
 * D-S004-10). As etapas entregues pelo Recorder nunca entram no histórico.
 */
public final class WorkspaceService implements StepIntake {

    /** Diz se uma automação está recebendo uma gravação (início, gravação, pausa, encerramento ou entrega pendente). */
    @FunctionalInterface
    public interface RecordingGuard {
        RecordingGuard NONE = id -> false;

        boolean isRecording(AutomationId automation);
    }

    private final StudioState state;
    private final EditHistory history;
    private RecordingGuard guard = RecordingGuard.NONE;

    public WorkspaceService(StudioState state) {
        this(state, new EditHistory());
    }

    public WorkspaceService(StudioState state, EditHistory history) {
        this.state = Objects.requireNonNull(state, "state");
        this.history = Objects.requireNonNull(history, "history");
    }

    public StudioState state() {
        return state;
    }

    public EditHistory history() {
        return history;
    }

    /** Liga a proteção contra edição concorrente com uma gravação (montagem da aplicação). */
    public void setRecordingGuard(RecordingGuard guard) {
        this.guard = Objects.requireNonNull(guard);
    }

    /** A automação está bloqueada para edição por uma gravação em andamento. */
    public boolean isLocked(AutomationId id) {
        return id != null && guard.isRecording(id);
    }

    /** O estado de gravação mudou (Recorder): a tela reavalia o que pode ser editado. */
    public void editLockChanged(AutomationId automation) {
        requireEdt();
        state.fire(automation, StudioChange.EDIT_LOCK);
    }

    // ------------------------------------------------------------------ automação

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

    /** S004: nome e descrição da automação (nome único no catálogo; identidade preservada). */
    public void updateAutomation(AutomationId id, String name, String description) {
        edit(id, "Editar automação", true, (a, c) -> {
            state.catalog().updateDetails(id, name, description);
            c.header = true;
            return null;
        });
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

    // ------------------------------------------------------------------ sessões

    /**
     * Acrescenta uma sessão ao final e a seleciona. Permitido durante a gravação: é assim que o Floating Recorder cria a
     * próxima sessão (STUDIO-002/003) — acrescentar ao final não disputa a ordem com as etapas gravadas.
     */
    public LogicalSession addSession(AutomationId automationId, String name, String description) {
        return edit(automationId, "Nova sessão", false, (a, c) -> {
            LogicalSession s = a.addSession(name, description);
            c.structure = true;
            c.selection = new Selection.OfSession(automationId, s.id());
            return s;
        });
    }

    public void renameSession(AutomationId automationId, SessionId sessionId, String name) {
        edit(automationId, "Renomear sessão", true, (a, c) -> {
            a.renameSession(sessionId, name);
            c.sessions.add(sessionId);
            return null;
        });
    }

    /** S004: nome, descrição e observações da sessão, numa única operação. */
    public void editSession(AutomationId automationId, SessionId sessionId, String name, String description, String notes) {
        edit(automationId, "Editar sessão", true, (a, c) -> {
            a.editSession(sessionId, name, description, notes);
            c.sessions.add(sessionId);
            return null;
        });
    }

    /** S004: muda a posição da sessão (0..n-1); a seleção continua na mesma sessão. */
    public void moveSession(AutomationId automationId, SessionId sessionId, int targetIndex) {
        edit(automationId, "Mover sessão", true, (a, c) -> {
            a.moveSession(sessionId, targetIndex);
            c.structure = true;
            return null;
        });
    }

    /**
     * S004: exclui a sessão e as etapas dela (atômico). A seleção vai para a sessão que ocupou o lugar, a anterior ou a
     * automação.
     *
     * @return etapas removidas junto
     */
    public int removeSession(AutomationId automationId, SessionId sessionId) {
        return edit(automationId, "Excluir sessão", true, (a, c) -> {
            int index = a.sessionNumber(sessionId) - 1;
            int removed = a.removeSession(sessionId);
            c.structure = true;
            List<LogicalSession> rest = a.sessions();
            c.selection = rest.isEmpty() ? new Selection.OfAutomation(automationId)
                    : new Selection.OfSession(automationId, rest.get(Math.min(Math.max(index, 0), rest.size() - 1)).id());
            return removed;
        });
    }

    // ------------------------------------------------------------------ etapas

    /** Etapa manual básica, acrescentada ao final da sessão e selecionada. Bloqueada na automação em gravação. */
    public AutomationStep addManualStep(AutomationId automationId, SessionId sessionId, String name, ActionType type, String description) {
        return edit(automationId, "Nova etapa", true, (a, c) -> {
            AutomationStep step = a.appendStep(sessionId, StepDraft.manual(name, type, description));
            c.sessions.add(sessionId);
            c.selection = new Selection.OfStep(automationId, sessionId, step.id());
            return step;
        });
    }

    /**
     * {@inheritDoc} A seleção do usuário não é alterada (o Recorder não deve "roubar" o foco). Não entra no histórico de
     * edição: é uma entrega do Recorder, não uma edição.
     */
    @Override
    public AutomationStep appendStep(AutomationId automationId, SessionId sessionId, StepDraft draft) {
        requireEdt();
        Automation a = state.catalog().require(automationId);
        AutomationStep step = a.appendStep(sessionId, draft);
        changed(a, sessionId == null ? Set.of() : Set.of(sessionId), false);
        return step;
    }

    /**
     * Cancelamento confirmado de uma gravação (STUDIO-003): descarta as etapas RECORDED dela numa única operação de
     * domínio; etapas manuais e de outras gravações ficam. Se a etapa selecionada sumiu, a seleção volta à sessão.
     */
    public int discardRecording(AutomationId automationId, String recordingId) {
        requireEdt();
        Automation a = state.catalog().find(automationId).orElse(null);
        if (a == null) return 0;
        int removed = a.discardRecording(recordingId);
        if (removed == 0) return 0;
        boolean selectionGone = state.selection() instanceof Selection.OfStep s && s.automation().equals(automationId) && a.step(s.step()).isEmpty();
        if (selectionGone && state.selection() instanceof Selection.OfStep s) {
            state.select(automationId, new Selection.OfSession(automationId, s.session()));
        }
        changed(a, Set.of(), selectionGone);
        return removed;
    }

    public void updateStepBasics(AutomationId automationId, StepId stepId, String name, String description, ActionType type) {
        edit(automationId, "Editar etapa", true, (a, c) -> {
            a.updateStepBasics(stepId, name, description, type);
            c.sessions.add(a.sessionOf(stepId).orElseThrow().id());
            return null;
        });
    }

    /** S004: edição completa (inclui mudar de sessão — a etapa vai para o final da sessão escolhida, selecionada). */
    public boolean editStep(AutomationId automationId, StepId stepId, StepEdit edit) {
        return edit(automationId, "Editar etapa", true, (a, c) -> {
            SessionId from = a.sessionOf(stepId).map(LogicalSession::id).orElse(null);
            boolean changed = a.editStep(stepId, edit);
            SessionId to = a.sessionOf(stepId).orElseThrow().id();
            if (from != null) c.sessions.add(from);
            c.sessions.add(to);
            if (!to.equals(from)) c.selection = new Selection.OfStep(automationId, to, stepId);
            return changed;
        });
    }

    /** S004: move a etapa para a posição {@code targetIndex} (0..n) da sessão de destino; ela continua selecionada. */
    public void moveStep(AutomationId automationId, StepId stepId, SessionId destination, int targetIndex) {
        edit(automationId, "Mover etapa", true, (a, c) -> {
            SessionId from = a.sessionOf(stepId).map(LogicalSession::id).orElseThrow(() -> new DomainValidationException(ValidationProblem.STEP_NOT_FOUND));
            a.moveStep(stepId, destination, targetIndex);
            c.sessions.add(from);
            c.sessions.add(destination);
            c.selection = new Selection.OfStep(automationId, destination, stepId);
            return null;
        });
    }

    /** S004: duplica a etapa logo depois dela e seleciona a cópia (etapa MANUAL derivada). */
    public AutomationStep duplicateStep(AutomationId automationId, StepId stepId) {
        return edit(automationId, "Duplicar etapa", true, (a, c) -> {
            AutomationStep copy = a.duplicateStep(stepId);
            SessionId s = a.sessionOf(copy.id()).orElseThrow().id();
            c.sessions.add(s);
            c.selection = new Selection.OfStep(automationId, s, copy.id());
            return copy;
        });
    }

    /** S004: exclui a etapa; a seleção vai para a próxima, a anterior ou a própria sessão. */
    public void removeStep(AutomationId automationId, StepId stepId) {
        edit(automationId, "Excluir etapa", true, (a, c) -> {
            LogicalSession session = a.sessionOf(stepId).orElseThrow(() -> new DomainValidationException(ValidationProblem.STEP_NOT_FOUND));
            int index = session.positionOf(stepId) - 1;
            a.removeStep(stepId);
            c.sessions.add(session.id());
            List<AutomationStep> rest = session.steps();
            c.selection = rest.isEmpty() ? new Selection.OfSession(automationId, session.id())
                    : new Selection.OfStep(automationId, session.id(), rest.get(Math.min(index, rest.size() - 1)).id());
            return null;
        });
    }

    public void setStepParameters(AutomationId automationId, StepId stepId, ActionParameters parameters) {
        stepEdit(automationId, stepId, "Alterar parâmetros", a -> a.setStepParameters(stepId, parameters));
    }

    public void choosePreferredLocator(AutomationId automationId, StepId stepId, int index) {
        stepEdit(automationId, stepId, "Alterar localizador preferencial", a -> a.choosePreferredLocator(stepId, index));
    }

    public void addManualLocator(AutomationId automationId, StepId stepId, ManualLocator locator) {
        stepEdit(automationId, stepId, "Propor localizador", a -> a.addManualLocator(stepId, locator));
    }

    public void removeManualLocator(AutomationId automationId, StepId stepId, int index) {
        stepEdit(automationId, stepId, "Remover localizador proposto", a -> a.removeManualLocator(stepId, index));
    }

    public void addComment(AutomationId automationId, StepId stepId, String text) {
        stepEdit(automationId, stepId, "Adicionar comentário", a -> a.addComment(stepId, text));
    }

    public void editComment(AutomationId automationId, StepId stepId, int index, String text) {
        stepEdit(automationId, stepId, "Editar comentário", a -> a.editComment(stepId, index, text));
    }

    public void removeComment(AutomationId automationId, StepId stepId, int index) {
        stepEdit(automationId, stepId, "Excluir comentário", a -> a.removeComment(stepId, index));
    }

    public void restoreOriginal(AutomationId automationId, StepId stepId, StepField field) {
        stepEdit(automationId, stepId, "Restaurar " + field.label().toLowerCase(java.util.Locale.ROOT) + " original",
                a -> a.restoreOriginal(stepId, field));
    }

    // ------------------------------------------------------------------ desfazer / refazer

    public boolean canUndo(AutomationId id) {
        return history.undoSize(id) > 0 && !isLocked(id);
    }

    public boolean canRedo(AutomationId id) {
        return history.redoSize(id) > 0 && !isLocked(id);
    }

    /**
     * Desfaz a última edição da automação. Se ela mudou por outro caminho depois (ex.: etapas gravadas), nada é aplicado:
     * o histórico é descartado e o conflito é informado ({@link ValidationProblem#HISTORY_CONFLICT}).
     */
    public void undo(AutomationId id) {
        travel(id, true);
    }

    public void redo(AutomationId id) {
        travel(id, false);
    }

    private void travel(AutomationId id, boolean back) {
        requireEdt();
        Automation a = state.catalog().require(id);
        requireEditable(id);
        EditHistory.Entry e = (back ? history.peekUndo(id) : history.peekRedo(id))
                .orElseThrow(() -> new DomainValidationException(back ? ValidationProblem.NOTHING_TO_UNDO : ValidationProblem.NOTHING_TO_REDO));
        if (a.revision() != e.expectedRevision()) {
            history.clear(id);
            state.fire(id, StudioChange.HISTORY);
            throw new DomainValidationException(ValidationProblem.HISTORY_CONFLICT);
        }
        Automation.Snapshot target = back ? e.before() : e.after();
        for (Automation other : state.catalog().all()) {
            if (other != a && other.name().strip().equalsIgnoreCase(target.name().strip())) {
                throw new DomainValidationException(ValidationProblem.AUTOMATION_NAME_IN_USE);
            }
        }
        a.restore(target);
        if (back) history.undone(id, a.revision());
        else history.redone(id, a.revision());
        Selection wanted = back ? e.selectionBefore() : e.selectionAfter();
        state.select(id, valid(a, wanted) ? wanted : new Selection.OfAutomation(id));
        state.markUnsaved();
        state.fire(id, StudioChange.CONTENT, StudioChange.CATALOG, StudioChange.UNSAVED_CHANGES, StudioChange.SELECTION, StudioChange.HISTORY);
    }

    // ------------------------------------------------------------------ seleção e avisos

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

    /** Informa o usuário sobre algo que REALMENTE aconteceu fora dos casos de uso do domínio (ex.: o navegador recusou gravar). */
    public void report(Notice notice) {
        requireEdt();
        state.notice(notice);
        state.fire(null, StudioChange.NOTICE);
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

    // ------------------------------------------------------------------ apoio

    /** O que uma operação de edição alterou (para notificar só a parte afetada e ajustar a seleção). */
    private static final class Change {
        final Set<SessionId> sessions = new LinkedHashSet<>();
        boolean structure;
        boolean header;
        Selection selection;
    }

    @FunctionalInterface
    private interface EditOp<R> {
        R apply(Automation a, Change c);
    }

    @FunctionalInterface
    private interface StepOp {
        boolean apply(Automation a);
    }

    private void stepEdit(AutomationId automationId, StepId stepId, String label, StepOp op) {
        edit(automationId, label, true, (a, c) -> {
            LogicalSession s = a.sessionOf(stepId).orElseThrow(() -> new DomainValidationException(ValidationProblem.STEP_NOT_FOUND));
            op.apply(a);
            c.sessions.add(s.id());
            return null;
        });
    }

    /**
     * Executa uma operação de edição: (1) recusa na automação em gravação ({@code lockable}), (2) fotografa o antes,
     * (3) executa a operação de domínio (que valida antes de alterar), (4) sem efeito, termina sem histórico nem
     * evento; com efeito, registra antes/depois no histórico, ajusta a seleção e notifica só o que mudou.
     */
    private <R> R edit(AutomationId id, String label, boolean lockable, EditOp<R> op) {
        requireEdt();
        Automation a = state.catalog().require(id);
        if (lockable) requireEditable(id);
        Automation.Snapshot before = a.snapshot();
        Selection selectionBefore = state.selectionOf(id);
        Change c = new Change();
        R result = op.apply(a, c);
        if (a.revision() == before.revision()) return result;
        if (c.selection != null) state.select(id, c.selection);
        else if (!valid(a, state.selectionOf(id))) state.select(id, new Selection.OfAutomation(id));
        history.record(id, new EditHistory.Entry(label, before, a.snapshot(), selectionBefore, state.selectionOf(id)));
        state.markUnsaved();
        boolean selectionChanged = !state.selectionOf(id).equals(selectionBefore);
        Set<SessionId> affected = c.structure || c.header ? Set.of() : c.sessions;
        if (selectionChanged) {
            state.fireFor(id, affected, StudioChange.CONTENT, StudioChange.CATALOG, StudioChange.UNSAVED_CHANGES, StudioChange.SELECTION,
                    StudioChange.HISTORY);
        } else {
            state.fireFor(id, affected, StudioChange.CONTENT, StudioChange.CATALOG, StudioChange.UNSAVED_CHANGES, StudioChange.HISTORY);
        }
        return result;
    }

    private void requireEditable(AutomationId id) {
        if (isLocked(id)) throw new DomainValidationException(ValidationProblem.AUTOMATION_RECORDING);
    }

    private static boolean valid(Automation a, Selection s) {
        return switch (s) {
            case Selection.None n -> true;
            case Selection.OfAutomation x -> x.automation().equals(a.id());
            case Selection.OfSession x -> a.session(x.session()).isPresent();
            case Selection.OfStep x -> a.session(x.session()).flatMap(ss -> ss.step(x.step())).isPresent();
        };
    }

    private void changed(Automation a, Set<SessionId> sessions, boolean selectionChanged) {
        state.markUnsaved();
        if (selectionChanged) state.fireFor(a.id(), sessions, StudioChange.CONTENT, StudioChange.CATALOG, StudioChange.UNSAVED_CHANGES, StudioChange.SELECTION);
        else state.fireFor(a.id(), sessions, StudioChange.CONTENT, StudioChange.CATALOG, StudioChange.UNSAVED_CHANGES);
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("O estado do Studio só pode ser alterado na thread de interface (EDT).");
        }
    }
}
