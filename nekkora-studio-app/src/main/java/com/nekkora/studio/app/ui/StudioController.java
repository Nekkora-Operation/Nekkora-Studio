package com.nekkora.studio.app.ui;

import com.nekkora.studio.app.workspace.Selection;
import com.nekkora.studio.app.workspace.StudioOperation;
import com.nekkora.studio.app.workspace.WorkspaceService;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.DomainValidationException;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.studio.ui.form.FormSpec;
import com.nekkora.studio.ui.form.StudioForms;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Traduz intenções do usuário em casos de uso: abre o diálogo adequado, executa a operação no
 * {@link WorkspaceService} e devolve ao formulário os problemas de validação do domínio. Não contém regras
 * de negócio nem estado próprio.
 */
public final class StudioController {
    /** Aviso ao fechar com alterações em memória (seção 17). */
    public static final String EXIT_MESSAGE = "As automações deste Studio existem apenas na memória. Ao fechar, todas as "
            + "automações, sessões e etapas criadas serão perdidas — o salvamento ainda não está disponível nesta versão.";

    private final WorkspaceService service;
    private final StudioPrompts prompts;
    private final Runnable exitAction;
    private java.util.function.Consumer<Runnable> exitGuard = Runnable::run;

    public StudioController(WorkspaceService service, StudioPrompts prompts, Runnable exitAction) {
        this.service = Objects.requireNonNull(service);
        this.prompts = Objects.requireNonNull(prompts);
        this.exitAction = Objects.requireNonNull(exitAction);
    }

    public WorkspaceService service() {
        return service;
    }

    // ------------------------------------------------------------------ criação

    public void newAutomation() {
        runForm(StudioOperation.NEW_AUTOMATION, StudioForms.newAutomation(),
                v -> attempt(() -> service.createAutomation(v.name(), v.description())));
    }

    public void newSession() {
        Automation a = active().orElse(null);
        if (a == null) return; // permitido durante a gravação: é assim que o Floating Recorder cria a próxima sessão
        runForm(StudioOperation.NEW_SESSION, StudioForms.newSession(a),
                v -> attempt(() -> service.addSession(a.id(), v.name(), v.description())));
    }

    /** Nova etapa; {@code preselected} pode ser nulo (o formulário sugere a sessão selecionada ou a última). */
    public void newStep(SessionId preselected) {
        Automation a = active().orElse(null);
        if (a == null || a.sessions().isEmpty() || !editable(a)) return;
        SessionId pre = preselected != null ? preselected : selectedSession().orElse(null);
        runForm(StudioOperation.NEW_STEP, StudioForms.newStep(a, pre),
                v -> attempt(() -> service.addManualStep(a.id(), v.session(), v.name(), v.actionType(), v.description())));
    }

    public void renameSession(SessionId sessionId) {
        Automation a = active().orElse(null);
        if (a == null || !editable(a)) return;
        LogicalSession s = a.session(sessionId).orElse(null);
        if (s == null) return;
        service.select(new Selection.OfSession(a.id(), sessionId));
        runForm(StudioOperation.RENAME_SESSION, StudioForms.renameSession(s.name()),
                v -> attempt(() -> service.renameSession(a.id(), sessionId, v.name())));
    }

    /** Renomeia a sessão selecionada (ou a sessão da etapa selecionada). */
    public void renameSelectedSession() {
        selectedSession().ifPresent(this::renameSession);
    }

    /**
     * Detalhes e edição completa da etapa (STUDIO-004). Salvar aplica UMA edição validada pelo domínio. Se o tipo novo
     * não aceita os parâmetros atuais, o usuário decide explicitamente removê-los — nunca são descartados em silêncio.
     * Durante a gravação da automação os detalhes continuam consultáveis; salvar é recusado com o motivo.
     */
    public void openStepDetails(StepId stepId) {
        Automation a = active().orElse(null);
        if (a == null) return;
        AutomationStep step = a.step(stepId).orElse(null);
        if (step == null) return;
        SessionId session = a.sessionOf(stepId).orElseThrow().id();
        service.select(new Selection.OfStep(a.id(), session, stepId));
        runForm(StudioOperation.STEP_DETAILS, StudioForms.stepDetails(a, step), v -> {
            try {
                boolean drop = false;
                if (v.parametersIncompatible()) {
                    if (!prompts.confirm(new StudioPrompts.Confirmation("Remover parâmetros incompatíveis?",
                            "O tipo \"" + v.actionType().label() + "\" não aceita os parâmetros atuais ("
                                    + com.nekkora.studio.ui.StepPresentation.parameters(v.parameters())
                                    + "). Para salvar, eles serão removidos da etapa.", "Remover parâmetros e salvar", true))) {
                        return Optional.of(v.parameters().value().isPresent() ? ValidationProblem.VALUE_NOT_ACCEPTED : ValidationProblem.OPTION_NOT_ACCEPTED);
                    }
                    drop = true;
                }
                service.editStep(a.id(), stepId, v.toEdit(a, step, service.state().catalog().clock().instant(), drop));
                return Optional.empty();
            } catch (DomainValidationException e) {
                return Optional.of(e.problem());
            }
        });
    }

    // ------------------------------------------------------------------ STUDIO-004: edição

    /** F2: edita o item selecionado (automação, sessão ou etapa). */
    public void editSelected() {
        Automation a = active().orElse(null);
        if (a == null) return;
        switch (service.state().selection()) {
            case Selection.OfSession s -> editSession(s.session());
            case Selection.OfStep s -> openStepDetails(s.step());
            default -> editAutomation();
        }
    }

    /** Propriedades da automação (nome único, descrição). */
    public void editAutomation() {
        Automation a = active().orElse(null);
        if (a == null || !editable(a)) return;
        runForm(StudioOperation.EDIT_AUTOMATION, StudioForms.editAutomation(a), v -> attempt(() -> service.updateAutomation(a.id(), v.name(), v.description())));
    }

    /** Nome, descrição e observações de uma sessão. */
    public void editSession(SessionId sessionId) {
        Automation a = active().orElse(null);
        if (a == null || !editable(a)) return;
        LogicalSession s = a.session(sessionId).orElse(null);
        if (s == null) return;
        service.select(new Selection.OfSession(a.id(), sessionId));
        runForm(StudioOperation.EDIT_SESSION, StudioForms.editSession(s),
                v -> attempt(() -> service.editSession(a.id(), sessionId, v.name(), v.description(), v.notes())));
    }

    /** Move a sessão uma posição (-1 para cima, +1 para baixo); nos limites, nada acontece. */
    public void moveSession(SessionId sessionId, int delta) {
        Automation a = active().orElse(null);
        if (a == null || !editable(a)) return;
        int target = a.sessionNumber(sessionId) - 1 + delta;
        if (a.sessionNumber(sessionId) == 0 || target < 0 || target >= a.sessions().size()) return;
        attemptNotice("Sessão não movida", () -> service.moveSession(a.id(), sessionId, target));
    }

    /** Exclui a sessão — com confirmação explícita que diz quantas etapas vão junto. */
    public void deleteSession(SessionId sessionId) {
        Automation a = active().orElse(null);
        if (a == null || !editable(a)) return;
        LogicalSession s = a.session(sessionId).orElse(null);
        if (s == null) return;
        int n = s.stepCount();
        String message = "Sessão: " + s.name() + "\nEtapas existentes: " + n + "\n\n" + (n == 0 ? "A sessão está vazia."
                : "A exclusão também removerá " + (n == 1 ? "a etapa contida" : "as " + n + " etapas contidas") + " nesta sessão.")
                + " Você pode desfazer enquanto o histórico desta automação existir.";
        if (!confirmOperation(new StudioPrompts.Confirmation("Excluir sessão?", message, n == 0 ? "Excluir sessão" : "Excluir sessão e etapas", true))) return;
        attemptNotice("Sessão não excluída", () -> service.removeSession(a.id(), sessionId));
    }

    /** Move a etapa uma posição dentro da sessão (Alt+↑ / Alt+↓). */
    public void moveStep(StepId stepId, int delta) {
        Automation a = active().orElse(null);
        if (a == null || !editable(a)) return;
        LogicalSession s = a.sessionOf(stepId).orElse(null);
        if (s == null) return;
        int target = s.positionOf(stepId) - 1 + delta;
        if (target < 0 || target >= s.stepCount()) return;
        attemptNotice("Etapa não movida", () -> service.moveStep(a.id(), stepId, s.id(), target));
    }

    /** Move a etapa para outra posição ou sessão (diálogo). */
    public void moveStepTo(StepId stepId) {
        Automation a = active().orElse(null);
        if (a == null || !editable(a)) return;
        AutomationStep step = a.step(stepId).orElse(null);
        if (step == null) return;
        runForm(StudioOperation.MOVE_STEP, StudioForms.moveStep(a, step), v -> attempt(() -> service.moveStep(a.id(), stepId, v.session(), v.index())));
    }

    public void duplicateStep(StepId stepId) {
        Automation a = active().orElse(null);
        if (a == null || !editable(a) || a.step(stepId).isEmpty()) return;
        attemptNotice("Etapa não duplicada", () -> service.duplicateStep(a.id(), stepId));
    }

    /** Exclui a etapa — com confirmação. */
    public void deleteStep(StepId stepId) {
        Automation a = active().orElse(null);
        if (a == null || !editable(a)) return;
        AutomationStep step = a.step(stepId).orElse(null);
        if (step == null) return;
        String session = a.sessionOf(stepId).orElseThrow().name();
        if (!confirmOperation(new StudioPrompts.Confirmation("Excluir etapa?", step.name() + "\n\nEsta operação removerá a etapa da sessão "
                + session + ". Você pode desfazer enquanto o histórico desta automação existir.", "Excluir", true))) return;
        attemptNotice("Etapa não excluída", () -> service.removeStep(a.id(), stepId));
    }

    /** Volta um campo da etapa gravada ao valor capturado. */
    public void restoreOriginal(StepId stepId, com.nekkora.studio.domain.StepField field) {
        Automation a = active().orElse(null);
        if (a == null || !editable(a)) return;
        attemptNotice("Valor original não restaurado", () -> service.restoreOriginal(a.id(), stepId, field));
    }

    /** Ctrl+Z: desfaz a última edição da automação ativa. */
    public void undo() {
        active().ifPresent(a -> attemptNotice("Nada foi desfeito", () -> service.undo(a.id())));
    }

    /** Ctrl+Y: refaz a última edição desfeita da automação ativa. */
    public void redo() {
        active().ifPresent(a -> attemptNotice("Nada foi refeito", () -> service.redo(a.id())));
    }

    /** Recusa logo (com o motivo) uma edição na automação que está sendo gravada; o caso de uso também recusa. */
    private boolean editable(Automation a) {
        if (!service.isLocked(a.id())) return true;
        service.report(new com.nekkora.studio.app.workspace.Notice(com.nekkora.studio.app.workspace.Notice.Kind.WARNING,
                "Edição indisponível durante a gravação", ValidationProblem.AUTOMATION_RECORDING.message()));
        return false;
    }

    private boolean confirmOperation(StudioPrompts.Confirmation c) {
        if (!service.beginOperation(StudioOperation.CONFIRM)) return false;
        try {
            return prompts.confirm(c);
        } finally {
            service.endOperation();
        }
    }

    /** Executa um caso de uso sem formulário; uma recusa do domínio vira um aviso com o motivo. */
    private void attemptNotice(String title, Runnable operation) {
        try {
            operation.run();
        } catch (DomainValidationException e) {
            service.report(new com.nekkora.studio.app.workspace.Notice(com.nekkora.studio.app.workspace.Notice.Kind.WARNING, title, e.problem().message()));
        }
    }

    // ------------------------------------------------------------------ navegação e seleção

    public void openAutomation(AutomationId id) {
        service.openAutomation(id);
    }

    public void closeAutomation(AutomationId id) {
        service.closeAutomation(id);
    }

    public void selectAutomation(AutomationId id) {
        service.select(new Selection.OfAutomation(id));
    }

    public void selectSession(AutomationId id, SessionId session) {
        service.select(new Selection.OfSession(id, session));
    }

    public void selectStep(AutomationId id, SessionId session, StepId step) {
        service.select(new Selection.OfStep(id, session, step));
    }

    // ------------------------------------------------------------------ encerramento

    /**
     * Guarda de saída (STUDIO-002): recebe a continuação do encerramento e decide quando executá-la — por exemplo,
     * depois de finalizar ou cancelar uma gravação técnica em andamento. Padrão: encerra direto.
     */
    public void setExitGuard(java.util.function.Consumer<Runnable> guard) {
        this.exitGuard = Objects.requireNonNull(guard);
    }

    /** Fecha o Studio, avisando antes se houver alterações que só existem em memória. */
    public void requestExit() {
        if (!service.state().hasUnsavedChanges()) {
            exitGuard.accept(exitAction);
            return;
        }
        if (!service.beginOperation(StudioOperation.EXIT)) return;
        boolean discard;
        try {
            discard = prompts.confirmExit(EXIT_MESSAGE);
        } finally {
            service.endOperation();
        }
        if (discard) exitGuard.accept(exitAction);
    }

    // ------------------------------------------------------------------ apoio

    private Optional<Automation> active() {
        return service.state().activeAutomation();
    }

    private Optional<SessionId> selectedSession() {
        return switch (service.state().selection()) {
            case Selection.OfSession s -> Optional.of(s.session());
            case Selection.OfStep s -> Optional.of(s.session());
            default -> Optional.empty();
        };
    }

    private <V> void runForm(StudioOperation op, FormSpec<V> spec, Function<V, Optional<ValidationProblem>> submit) {
        if (!service.beginOperation(op)) return;
        try {
            prompts.showForm(spec, submit);
        } finally {
            service.endOperation();
        }
    }

    private static Optional<ValidationProblem> attempt(Runnable operation) {
        try {
            operation.run();
            return Optional.empty();
        } catch (DomainValidationException e) {
            return Optional.of(e.problem());
        }
    }
}
