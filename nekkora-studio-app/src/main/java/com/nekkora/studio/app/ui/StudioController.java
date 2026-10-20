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
import com.nekkora.studio.domain.StepEdit;
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
    private final WorkspaceService service;
    private final com.nekkora.studio.app.workspace.ProjectService projects;
    private final com.nekkora.studio.app.workspace.ValidationService validation;
    private final StudioPrompts prompts;
    private final Runnable exitAction;
    private java.util.function.Consumer<Runnable> exitGuard = Runnable::run;
    private Runnable showValidation = () -> { };

    public StudioController(WorkspaceService service, com.nekkora.studio.app.workspace.ProjectService projects,
            com.nekkora.studio.app.workspace.ValidationService validation, StudioPrompts prompts, Runnable exitAction) {
        this.service = Objects.requireNonNull(service);
        this.projects = Objects.requireNonNull(projects);
        this.validation = Objects.requireNonNull(validation);
        this.prompts = Objects.requireNonNull(prompts);
        this.exitAction = Objects.requireNonNull(exitAction);
    }

    public WorkspaceService service() {
        return service;
    }

    /** Casos de uso de projeto (STUDIO-006). */
    public com.nekkora.studio.app.workspace.ProjectService projects() {
        return projects;
    }

    /** Validação estrutural (STUDIO-007). */
    public com.nekkora.studio.app.workspace.ValidationService validation() {
        return validation;
    }

    // ------------------------------------------------------------------ STUDIO-007: validação

    /** A tela registra como revelar o painel de validação. */
    public void onValidationShown(Runnable show) {
        this.showValidation = show == null ? () -> { } : show;
    }

    private Runnable afterValidation = () -> { };

    /** STUDIO-014: depois da validação estrutural, as outras camadas (PDD, capacidades, plano, código, execução). */
    public void onValidated(Runnable after) {
        this.afterValidation = after == null ? () -> { } : after;
    }

    // ------------------------------------------------------------------ STUDIO-008: variáveis

    private java.util.function.Consumer<com.nekkora.studio.domain.variable.VariableId> showVariables = v -> { };

    /** A janela mostra o painel de variáveis (e seleciona a variável, se não for nula). */
    public void onVariablesShown(java.util.function.Consumer<com.nekkora.studio.domain.variable.VariableId> show) {
        this.showVariables = show == null ? v -> { } : show;
    }

    /** Automação → Variáveis e dependências: consulta (permitida durante a gravação; editar não). */
    public void showVariables() {
        active().ifPresent(a -> showVariables.accept(null));
    }

    /** Declarar variável: só metadados; o domínio decide (nome, significado, sensibilidade, constante). */
    public void newVariable() {
        Automation a = active().orElse(null);
        if (a == null || !editable(a)) return;
        com.nekkora.studio.domain.variable.VariableId[] created = new com.nekkora.studio.domain.variable.VariableId[1];
        runForm(StudioOperation.VARIABLE, StudioForms.newVariable(a), v -> attempt(() -> created[0] = service.declareVariable(a.id(), v.toDraft()).id()));
        if (created[0] != null) showVariables.accept(created[0]);
    }

    public void editVariable(com.nekkora.studio.domain.variable.VariableId id) {
        Automation a = active().orElse(null);
        if (a == null || !editable(a)) return;
        com.nekkora.studio.domain.variable.VariableDefinition v = a.variable(id).orElse(null);
        if (v == null) return;
        runForm(StudioOperation.VARIABLE, StudioForms.editVariable(a, v), x -> attempt(() -> service.updateVariable(a.id(), id, x.toDraft())));
        showVariables.accept(id);
    }

    /** Renomear com prévia das etapas que passam a usar o novo nome (uma única operação desfazível). */
    public void renameVariable(com.nekkora.studio.domain.variable.VariableId id) {
        Automation a = active().orElse(null);
        if (a == null || !editable(a)) return;
        com.nekkora.studio.domain.variable.VariableDefinition v = a.variable(id).orElse(null);
        if (v == null) return;
        java.util.Map<StepId, String> places = com.nekkora.studio.ui.VariablePresentation.places(a);
        java.util.List<String> consumers = com.nekkora.studio.validation.DataFlowAnalysis.of(a).consumersOf(id).stream().map(c -> places.get(c.step()))
                .toList();
        int[] changed = {-1};
        runForm(StudioOperation.VARIABLE, StudioForms.renameVariable(v.name(), consumers),
                name -> attempt(() -> changed[0] = service.renameVariable(a.id(), id, name)));
        if (changed[0] >= 0) {
            service.report(new com.nekkora.studio.app.workspace.Notice(com.nekkora.studio.app.workspace.Notice.Kind.SUCCESS, "Variável renomeada",
                    changed[0] == 0 ? "Nenhuma etapa usava a variável." : (changed[0] == 1 ? "1 etapa atualizada." : changed[0] + " etapas atualizadas.")
                            + " Use Desfazer para voltar."));
            showVariables.accept(id);
        }
    }

    /** Excluir: em uso, só com confirmação explícita do descarte dos vínculos (as etapas ficam sem valor). */
    public void deleteVariable(com.nekkora.studio.domain.variable.VariableId id) {
        Automation a = active().orElse(null);
        if (a == null || !editable(a)) return;
        com.nekkora.studio.domain.variable.VariableDefinition v = a.variable(id).orElse(null);
        if (v == null) return;
        int n = a.consumersOf(id).size();
        String message = "Variável: " + v.name() + "\nEtapas que a usam: " + n + "\n\n" + (n == 0 ? "Nenhuma etapa usa esta variável."
                : (n == 1 ? "A etapa que a usa ficará" : "As " + n + " etapas que a usam ficarão") + " sem valor configurado — a validação vai apontar.")
                + " Você pode desfazer enquanto o histórico desta automação existir.";
        if (!confirmOperation(new StudioPrompts.Confirmation("Excluir variável?", message, n == 0 ? "Excluir variável"
                : n == 1 ? "Excluir e desligar 1 etapa" : "Excluir e desligar " + n + " etapas", true))) {
            return;
        }
        attemptNotice("Variável não excluída", () -> service.removeVariable(a.id(), id, n > 0));
        showVariables.accept(null);
    }

    /** Seleciona uma etapa (a partir do painel de variáveis). */
    public void goToStep(StepId stepId) {
        Automation a = active().orElse(null);
        if (a == null) return;
        a.sessionOf(stepId).ifPresent(s -> service.select(new Selection.OfStep(a.id(), s.id(), stepId)));
    }

    /**
     * Automação → Validar automação: diagnóstico estrutural do estado ATUAL da automação ativa. Não executa nada, não
     * consulta o navegador, não edita a automação (nada vai para o desfazer) e não a marca como não salva. Permitido
     * durante uma gravação (consulta): o resultado pode ficar desatualizado logo depois, e o painel diz isso.
     */
    public void validateActive() {
        active().ifPresent(a -> {
            validation.validate(a.id());
            showValidation.run();
            afterValidation.run();
        });
    }

    /** Seleciona o lugar de um apontamento na automação ativa (a etapa pode ter mudado de sessão desde a validação). */
    public void goToFinding(com.nekkora.studio.validation.Finding f) {
        Automation a = active().orElse(null);
        if (a == null) return;
        if (f.scope() == com.nekkora.studio.validation.FindingScope.VARIABLE) {
            if (a.variable(f.variable()).isPresent()) {
                showVariables.accept(f.variable());
            } else {
                service.report(new com.nekkora.studio.app.workspace.Notice(com.nekkora.studio.app.workspace.Notice.Kind.INFO, "Variável não encontrada",
                        "A variável deste apontamento não existe mais. Valide a automação novamente."));
            }
            return;
        }
        if (f.step() != null) {
            Optional<LogicalSession> s = a.sessionOf(f.step());
            if (s.isPresent()) {
                service.select(new Selection.OfStep(a.id(), s.get().id(), f.step()));
                return;
            }
            service.report(new com.nekkora.studio.app.workspace.Notice(com.nekkora.studio.app.workspace.Notice.Kind.INFO, "Etapa não encontrada",
                    "A etapa deste apontamento não existe mais. Valide a automação novamente."));
            return;
        }
        if (f.session() != null && a.session(f.session()).isPresent()) {
            service.select(new Selection.OfSession(a.id(), f.session()));
            return;
        }
        service.select(new Selection.OfAutomation(a.id()));
    }

    /** Abre o editor de etapa EXISTENTE no apontamento (não há editor dentro do painel de validação). */
    public void editFinding(com.nekkora.studio.validation.Finding f) {
        Automation a = active().orElse(null);
        if (a == null || f.step() == null || a.step(f.step()).isEmpty()) {
            goToFinding(f);
            return;
        }
        goToFinding(f);
        openStepDetails(f.step());
    }

    // ------------------------------------------------------------------ STUDIO-006: projetos

    /** Arquivo → Abrir projeto (Ctrl+O). */
    public void openProject() {
        projects.open();
    }

    /** Projetos recentes: abre (ou ativa, se já aberto). Arquivo removido: indisponível, nada é carregado. */
    public void openRecent(java.nio.file.Path path) {
        projects.open(path);
    }

    /** Arquivo → Salvar (Ctrl+S): a automação ativa. */
    public void saveActive() {
        active().ifPresent(a -> projects.save(a.id()));
    }

    /** Arquivo → Salvar como (Ctrl+Shift+S). */
    public void saveActiveAs() {
        active().ifPresent(a -> projects.saveAs(a.id()));
    }

    /** Arquivo → Fechar projeto: retira do Studio, perguntando se há alterações não salvas. */
    public void closeActiveProject() {
        active().ifPresent(a -> projects.closeProject(a.id()));
    }

    /** Arquivo → Recuperação automática. */
    public void configureRecovery() {
        projects.configureRecovery();
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
                StepEdit edit = v.toEdit(a, step, service.state().catalog().clock().instant()); // valida antes de perguntar
                java.util.List<String> lost = v.discarded();
                if (!lost.isEmpty() && !prompts.confirm(new StudioPrompts.Confirmation("Remover parâmetros incompatíveis?",
                        "O tipo \"" + v.actionType().label() + "\" não aceita: " + String.join("; ", lost)
                                + ". Para salvar, isso será removido da etapa.", "Remover e salvar", true))) {
                    return Optional.of(v.discardsValue() ? ValidationProblem.VALUE_NOT_ACCEPTED : ValidationProblem.OPTION_NOT_ACCEPTED);
                }
                service.editStep(a.id(), stepId, edit);
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

    /**
     * Fecha o Studio (§29): com alterações não salvas, pergunta Salvar todos / Descartar / Cancelar. Se algum salvamento
     * falhar, o Studio continua aberto. Depois, a guarda de saída da gravação técnica (STUDIO-002) decide.
     */
    public void requestExit() {
        projects.confirmExit().thenAccept(ok -> {
            if (ok) exitGuard.accept(exitAction);
        });
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
