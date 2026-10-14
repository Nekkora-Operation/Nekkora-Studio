package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.app.workspace.Selection;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.origin.StepOrigin;
import com.nekkora.studio.ui.LogicalSessionView;
import com.nekkora.studio.ui.form.NameDescriptionForm;
import com.nekkora.studio.ui.form.StepDetailsForm;
import com.nekkora.studio.ui.form.StepForm;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Matriz STUDIO-001 — fluxos da aplicação montada (headless, mesmos formulários dos diálogos reais). */
class StudioFlowTest {
    private final StudioFixture fx = new StudioFixture();

    @AfterEach
    void tearDown() {
        fx.dispose();
    }

    @Test
    @DisplayName("S001-001 inicializa direto no Studio, sem autenticação nem licença")
    void inicializacao() {
        String all = Edt.call(() -> StudioFixture.texts(fx.studio.view().component()));
        assertTrue(all.contains("Nekkora Studio"));
        assertTrue(all.contains("Automações"));
        for (String forbidden : List.of("Entrar com", "Usuário:", "Senha", "Licença", "Ativar", "Trial", "Assinatura")) {
            assertFalse(all.contains(forbidden), "texto de login/licença na tela inicial: " + forbidden);
        }
        assertTrue(fx.prompts.shown.isEmpty(), "nenhum diálogo é exibido na abertura");
    }

    @Test
    @DisplayName("S001-002 estado vazio adequado, sem automações fictícias")
    void estadoVazio() {
        assertTrue(fx.studio.state().automations().isEmpty());
        assertTrue(Edt.call(() -> fx.navigator().items().isEmpty()));
        String all = Edt.call(() -> StudioFixture.texts(fx.studio.view().component()));
        assertTrue(all.contains("Nenhuma automação"));
        assertTrue(all.contains("Nenhuma automação aberta"));
        assertTrue(all.contains("Nada selecionado"));
        for (String demo : List.of("Ana Souza", "Conciliação bancária", "Workspace Financeiro", "Financeiro")) {
            assertFalse(all.contains(demo), "dado de demonstração na aplicação: " + demo);
        }
        assertEquals(0, Edt.call(() -> fx.studio.view().workArea().getOpenCount()));
        assertFalse(fx.studio.state().hasUnsavedChanges());
    }

    @Test
    @DisplayName("S001-003 criar automação pelo diálogo atualiza domínio, navegação, área de trabalho e propriedades")
    void criarAutomacao() {
        Automation a = fx.newAutomation("Consulta de clientes", "Consulta o cadastro de clientes.");
        assertEquals("Consulta de clientes", a.name());
        assertEquals("Consulta o cadastro de clientes.", a.description());
        assertEquals(List.of(a), fx.studio.state().automations());
        assertEquals(List.of(a), Edt.call(() -> fx.navigator().items()));
        assertSame(a, Edt.call(() -> fx.navigator().automationList().getSelectedValue()));
        assertEquals(1, Edt.call(() -> fx.studio.view().workArea().getOpenCount()));
        assertTrue(Edt.call(() -> fx.overview().isShowingEmptyState()), "estado vazio de sessões");
        assertInstanceOf(Selection.OfAutomation.class, fx.studio.state().selection());
        assertTrue(fx.inspectorText().contains("Consulta o cadastro de clientes."));
        assertTrue(fx.studio.state().hasUnsavedChanges());
        assertEquals("Automação criada", fx.studio.state().lastNotice().orElseThrow().title());
    }

    @Test
    @DisplayName("S001-004 nome inválido: mensagem no campo e nada é criado")
    void validacao() {
        fx.prompts.then(ScriptedPrompts.name("   ", "x"));
        Edt.run(() -> fx.studio.controller().newAutomation());
        NameDescriptionForm form = (NameDescriptionForm) fx.prompts.last().form();
        assertEquals("Informe um nome.", Edt.call(form::visibleErrors));
        assertFalse(fx.prompts.results.get(0), "o diálogo continua aberto");
        assertTrue(fx.studio.state().automations().isEmpty());

        fx.newAutomation("Faturas", null);
        fx.prompts.then(ScriptedPrompts.name("faturas", null));
        Edt.run(() -> fx.studio.controller().newAutomation());
        assertEquals("Já existe uma automação com este nome.", Edt.call(() -> ((NameDescriptionForm) fx.prompts.last().form()).visibleErrors()));
        assertEquals(1, fx.studio.state().automations().size());
        assertTrue(fx.studio.state().currentOperation().isEmpty(), "operação encerrada após o diálogo");
    }

    @Test
    @DisplayName("S001-005 duas automações: alternância com estado e seleção isolados")
    void multiplasAutomacoes() {
        Automation a = fx.newAutomation("A", null);
        fx.newSession("Login");
        AutomationStep stepA = fx.newStep(null, "Preencher usuário", ActionType.FILL);
        Automation b = fx.newAutomation("B", null);
        assertSame(b, fx.active());
        assertTrue(Edt.call(() -> fx.overview().isShowingEmptyState()));
        assertEquals(2, Edt.call(() -> fx.studio.view().workArea().getOpenCount()));

        Edt.run(() -> fx.navigator().automationList().setSelectedIndex(0)); // usuário escolhe "A" na navegação
        assertSame(a, fx.active());
        assertEquals(new Selection.OfStep(a.id(), a.sessions().get(0).id(), stepA.id()), fx.studio.state().selection(),
                "cada automação lembra a própria seleção");
        assertEquals(List.of("01   Preencher usuário"), fx.stepLines());
        assertEquals(0, b.stepCount());
        assertNotSame(fx.studio.view().overview(a.id()).orElseThrow(), fx.studio.view().overview(b.id()).orElseThrow());
    }

    @Test
    @DisplayName("S001-006/007 sessões criadas na ordem e exibidas na automação certa")
    void sessoes() {
        Automation a = fx.newAutomation("A", null);
        LogicalSession login = fx.newSession("Login");
        assertEquals(new Selection.OfSession(a.id(), login.id()), fx.studio.state().selection());
        fx.newSession("Consulta");
        fx.newSession("Resultado");
        assertEquals(List.of("Login", "Consulta", "Resultado"), a.sessions().stream().map(LogicalSession::name).toList());
        List<LogicalSessionView> views = fx.sessionViews();
        assertEquals(a.sessions().stream().map(LogicalSession::id).toList(), views.stream().map(LogicalSessionView::sessionId).toList());
        assertTrue(Edt.call(() -> views.get(0).getAccessibleContext().getAccessibleName()).contains("Sessão 1: Login"));
        assertTrue(Edt.call(() -> views.get(2).isShowingEmptyState()), "estado vazio de sessão sem etapas");
        assertTrue(fx.inspectorText().contains("Sessão 3 de 3"));
    }

    @Test
    @DisplayName("S001-006 renomear sessão (alteração básica do nome) pelo diálogo")
    void renomearSessao() {
        Automation a = fx.newAutomation("A", null);
        LogicalSession s = fx.newSession("Login");
        String[] initial = new String[1];
        fx.prompts.then(f -> {
            initial[0] = ((NameDescriptionForm) f).values().name();
            ScriptedPrompts.name("Autenticação", null).accept(f);
        });
        Edt.run(() -> fx.studio.controller().renameSession(s.id()));
        assertEquals("Login", initial[0], "o diálogo abre com o nome atual");
        assertEquals("Autenticação", s.name());
        assertTrue(Edt.call(() -> fx.sessionViews().get(0).getAccessibleContext().getAccessibleName()).contains("Autenticação"));
        assertTrue(fx.inspectorText().contains("Autenticação"));
        assertSame(a, fx.active());
    }

    @Test
    @DisplayName("S001-008 adicionar etapa manual à sessão selecionada")
    void adicionarEtapa() {
        Automation a = fx.newAutomation("A", null);
        LogicalSession login = fx.newSession("Login");
        fx.newSession("Consulta");
        Edt.run(() -> fx.studio.controller().selectSession(a.id(), login.id()));
        fx.prompts.then(ScriptedPrompts.step(null, "Preencher usuário", ActionType.FILL));
        Edt.run(() -> fx.studio.controller().newStep(null));
        StepForm form = (StepForm) fx.prompts.last().form();
        assertEquals("1 · Login", Edt.call(() -> ((StepForm.SessionChoice) form.sessionInput().getSelectedItem()).label()),
                "sugere a sessão selecionada");
        assertEquals(1, login.stepCount());
        AutomationStep step = login.steps().get(0);
        assertEquals(StepOrigin.MANUAL, step.provenance().origin());
        assertEquals(new Selection.OfStep(a.id(), login.id(), step.id()), fx.studio.state().selection());
        assertTrue(Edt.call(() -> fx.overview().newStepButton().isEnabled()));
    }

    @Test
    @DisplayName("S001-009 sequência e numeração visual 01..07 seguem o domínio")
    void sequencia() {
        Automation a = fx.missionExample();
        assertEquals(List.of("01   Preencher usuário", "02   Preencher senha", "03   Clicar em Entrar", "04   Abrir menu Clientes",
                "05   Preencher campo de pesquisa", "06   Clicar em Pesquisar", "07   Consultar detalhes"), fx.stepLines());
        assertEquals(7, a.stepCount());
        assertTrue(Edt.call(() -> com.nekkora.studio.ui.StudioText.summary(a)).equals("3 sessões · 7 etapas"));
    }

    @Test
    @DisplayName("S001-010 etapa da sessão A não aparece na sessão B")
    void isolamentoEntreSessoes() {
        fx.newAutomation("A", null);
        fx.newSession("A");
        fx.newSession("B");
        AutomationStep s = fx.newStep("A", "Clicar em Entrar", ActionType.CLICK);
        List<LogicalSessionView> views = fx.sessionViews();
        assertEquals(List.of(s), Edt.call(() -> views.get(0).itemsInOrder()));
        assertTrue(Edt.call(() -> views.get(1).itemsInOrder()).isEmpty());
    }

    @Test
    @DisplayName("S001-011 selecionar etapa na lista atualiza o painel de propriedades")
    void selecao() {
        Automation a = fx.missionExample();
        LogicalSessionView consulta = fx.sessionViews().get(1);
        Edt.run(() -> consulta.stepList().setSelectedIndex(0)); // usuário clica em "04 Abrir menu Clientes"
        AutomationStep step = a.sessions().get(1).steps().get(0);
        assertEquals(new Selection.OfStep(a.id(), a.sessions().get(1).id(), step.id()), fx.studio.state().selection());
        String inspector = fx.inspectorText();
        assertTrue(inspector.contains("Abrir menu Clientes"));
        assertTrue(inspector.contains("Etapa 04 de 07"));
        assertTrue(inspector.contains("Clicar"));
        assertTrue(inspector.contains("Consulta"));
        assertTrue(Edt.call(() -> fx.sessionViews().get(0).stepList().isSelectionEmpty()), "seleção única entre as sessões");

        Edt.run(() -> fx.studio.controller().selectSession(a.id(), a.sessions().get(2).id()));
        assertTrue(fx.inspectorText().contains("Sessão 3 de 3"));
        assertTrue(Edt.call(() -> fx.sessionViews().get(2).isHighlighted()));
        Edt.run(() -> fx.studio.controller().selectAutomation(a.id()));
        assertTrue(fx.inspectorText().contains("Automação"));
    }

    @Test
    @DisplayName("S001-012 detalhes da etapa correspondem à etapa selecionada; edição básica aplica no domínio")
    void detalhes() {
        Automation a = fx.missionExample();
        AutomationStep step = a.sessions().get(1).steps().get(1); // 05 Preencher campo de pesquisa
        fx.prompts.then(f -> {
            StepDetailsForm d = (StepDetailsForm) f;
            d.basics().nameInput().setText("Preencher pesquisa de clientes");
        });
        Edt.run(() -> fx.studio.controller().openStepDetails(step.id()));
        StepDetailsForm form = (StepDetailsForm) fx.prompts.last().form();
        assertEquals(step.id(), form.stepId());
        assertEquals("Preencher campo de pesquisa", fx.prompts.last().title());
        assertTrue(fx.prompts.last().description().startsWith("Etapa 05 de 07 · Consulta"));
        assertTrue(form.overviewRows().stream().anyMatch(r -> r.label().equals("Tipo de ação") && r.value().equals("Preencher")));
        assertTrue(form.overviewRows().stream().anyMatch(r -> r.label().equals("Origem") && r.value().equals("Manual")));
        assertEquals("Preencher pesquisa de clientes", step.name());
        assertTrue(fx.stepLines().contains("05   Preencher pesquisa de clientes"));
        assertTrue(fx.inspectorText().contains("Preencher pesquisa de clientes"));
    }

    @Test
    @DisplayName("S001-014 tipos de ação distintos são representados conforme o modelo")
    void tiposDeAcao() {
        Automation a = fx.newAutomation("Tipos", null);
        fx.newSession("S");
        for (ActionType t : ActionType.manualChoices()) fx.newStep("S", "Etapa " + t.label(), t);
        LogicalSessionView v = fx.sessionViews().get(0);
        List<AutomationStep> items = Edt.call(v::itemsInOrder);
        assertEquals(ActionType.manualChoices(), items.stream().map(AutomationStep::actionType).toList());
        for (AutomationStep s : items) {
            assertTrue(LogicalSessionView.detailOf(s).startsWith(s.actionType().label() + " · Manual"));
        }
        assertEquals(ActionType.manualChoices().size(), a.stepCount());
        StepForm f = Edt.call(() -> StepForm.forCreation(a, null));
        assertEquals(ActionType.manualChoices().size(), f.typeInput().getItemCount(), "o formulário oferece o catálogo do domínio");
    }

    @Test
    @DisplayName("fechar a aba mantém a automação em memória e permite reabrir pela navegação")
    void fecharEReabrir() {
        Automation a = fx.newAutomation("A", null);
        Automation b = fx.newAutomation("B", null);
        Edt.run(() -> fx.studio.controller().closeAutomation(b.id()));
        Edt.flush();
        assertSame(a, fx.active());
        assertEquals(List.of(a.id()), fx.studio.state().openAutomations());
        assertEquals(1, Edt.call(() -> fx.studio.view().workArea().getOpenCount()), "a aba acompanha o estado");
        assertEquals(2, fx.studio.state().automations().size());
        Edt.run(() -> fx.studio.controller().openAutomation(b.id()));
        assertSame(b, fx.active());
    }
}
