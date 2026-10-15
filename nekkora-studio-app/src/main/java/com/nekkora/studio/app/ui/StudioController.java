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
        if (a == null) return;
        runForm(StudioOperation.NEW_SESSION, StudioForms.newSession(a),
                v -> attempt(() -> service.addSession(a.id(), v.name(), v.description())));
    }

    /** Nova etapa; {@code preselected} pode ser nulo (o formulário sugere a sessão selecionada ou a última). */
    public void newStep(SessionId preselected) {
        Automation a = active().orElse(null);
        if (a == null || a.sessions().isEmpty()) return;
        SessionId pre = preselected != null ? preselected : selectedSession().orElse(null);
        runForm(StudioOperation.NEW_STEP, StudioForms.newStep(a, pre),
                v -> attempt(() -> service.addManualStep(a.id(), v.session(), v.name(), v.actionType(), v.description())));
    }

    public void renameSession(SessionId sessionId) {
        Automation a = active().orElse(null);
        if (a == null) return;
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

    /** Detalhes da etapa (diálogo), com edição dos campos básicos. */
    public void openStepDetails(StepId stepId) {
        Automation a = active().orElse(null);
        if (a == null) return;
        AutomationStep step = a.step(stepId).orElse(null);
        if (step == null) return;
        SessionId session = a.sessionOf(stepId).orElseThrow().id();
        service.select(new Selection.OfStep(a.id(), session, stepId));
        runForm(StudioOperation.STEP_DETAILS, StudioForms.stepDetails(a, step),
                v -> attempt(() -> service.updateStepBasics(a.id(), stepId, v.name(), v.description(), v.actionType())));
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
