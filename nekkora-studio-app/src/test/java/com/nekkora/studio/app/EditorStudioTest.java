package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.app.workspace.Selection;
import com.nekkora.studio.app.workspace.StudioChange;
import com.nekkora.studio.app.workspace.StudioEvent;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepContext;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.StepField;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.OptionChoice;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.element.ManualLocator;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepOrigin;
import com.nekkora.studio.domain.origin.StepProvenance;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import com.nekkora.studio.ui.LogicalSessionView;
import com.nekkora.studio.ui.form.MoveStepForm;
import com.nekkora.studio.ui.form.NameDescriptionForm;
import com.nekkora.studio.ui.form.StepDetailsForm;
import com.nekkora.studio.ui.form.StudioForms;
import com.nekkora.tokens.NekkoraThemes;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * STUDIO-004 pela aplicação: cada caso passa pela interface (formulários, atalhos, menus e painel) → controlador → caso
 * de uso → domínio → estado → evento → tela. Os formulários são os mesmos dos diálogos reais (preenchidos pelo usuário
 * roteirizado).
 */
class EditorStudioTest {
    private final StudioFixture fx = new StudioFixture();

    @AfterEach
    void tearDown() {
        fx.dispose();
    }

    // ------------------------------------------------------------------ apoio

    private static ElementReference element(String kind, String name, boolean sensitive) {
        return ElementReference.builder().semanticKind(kind).accessibleName(name).tag("input").htmlId("campo-" + name.toLowerCase())
                .sensitive(sensitive).privacyPolicy("nekkora-privacy/1").sourceContract("nekkora-bridge/1")
                .addLocator(new LocatorCandidate(LocatorStrategy.ID, "id \"campo\"", "#campo", LocatorStability.HEURISTIC_STABLE, 1, true))
                .preferLastLocator()
                .addLocator(new LocatorCandidate(LocatorStrategy.ROLE_NAME, "textbox \"" + name + "\"", "", LocatorStability.HEURISTIC_STABLE, 1, true))
                .addLocator(new LocatorCandidate(LocatorStrategy.TEXT, "texto " + name, "", LocatorStability.HEURISTIC_UNSTABLE, 3, true))
                .correlation(CorrelationKeys.of(Map.of(CorrelationKeys.Key.TARGET_ID, "tgt-9f8e7d6c5b4a39281706f5e4d3c2b1a0")))
                .build();
    }

    /** Etapa gravada entregue pelo StepIntake (como o Recorder faz), sem passar pelo histórico de edição. */
    private AutomationStep recorded(LogicalSession s, String name, ActionType type, ElementReference e, ActionParameters p, String event) {
        CorrelationKeys keys = CorrelationKeys.of(Map.of(CorrelationKeys.Key.RECORDING_ID, "rec-3f2a9c1e-7b44-4d1a-9a55-0c2e8f6d1b77",
                CorrelationKeys.Key.RECORDED_EVENT_ID, event));
        RecordingObservation obs = new RecordingObservation(RecordingObservation.CaptureMode.CAPTURE_AND_INTERACT,
                RecordingObservation.Observation.INTERACTION_OBSERVED, null, List.of("FORM_SUBMITTED"), List.of());
        StepDraft d = StepDraft.builder().name(name).actionType(type).element(e).parameters(p).provenance(StepProvenance.recorded(keys, obs))
                .context(new StepContext("Navegador", "Login de teste", "http://127.0.0.1:18765/lab/studio003/login.html")).build();
        return Edt.call(() -> fx.studio.stepIntake().appendStep(fx.active().id(), s.id(), d));
    }

    /** Automação "gravada": Autenticação (Usuário, Senha, Entrar, Clientes) e Consulta (Pesquisa manual). */
    private Automation recordedExample() {
        fx.newAutomation("Consulta de clientes", "Consulta cadastral.");
        LogicalSession auth = fx.newSession("Autenticação");
        LogicalSession consulta = fx.newSession("Consulta");
        recorded(auth, "Preencher campo Usuário", ActionType.FILL, element("TEXT_INPUT", "Usuário", false), null, "ev-1");
        recorded(auth, "Preencher campo Senha", ActionType.FILL, element("PASSWORD_INPUT", "Senha", true), null, "ev-2");
        recorded(auth, "Clicar no botão Entrar", ActionType.CLICK, element("BUTTON", "Entrar", false), null, "ev-3");
        recorded(auth, "Clicar no link Clientes", ActionType.CLICK, element("LINK", "Clientes", false), null, "ev-4");
        fx.newStep("Consulta", "Preencher Pesquisa", ActionType.FILL);
        return fx.active();
    }

    private AutomationStep step(String name) {
        return fx.active().sessions().stream().flatMap(s -> s.steps().stream()).filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    private void select(AutomationStep s) {
        Automation a = fx.active();
        Edt.run(() -> fx.studio.controller().selectStep(a.id(), a.sessionOf(s.id()).orElseThrow().id(), s.id()));
    }

    private static List<String> names(LogicalSession s) {
        return s.steps().stream().map(AutomationStep::name).toList();
    }

    private static boolean press(JComponent c, KeyStroke ks) {
        return Edt.call(() -> {
            for (int cond : new int[] {JComponent.WHEN_FOCUSED, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, JComponent.WHEN_IN_FOCUSED_WINDOW}) {
                Object key = c.getInputMap(cond).get(ks);
                if (key == null) continue;
                Action a = c.getActionMap().get(key);
                if (a == null || !a.isEnabled()) continue;
                a.actionPerformed(new ActionEvent(c, ActionEvent.ACTION_PERFORMED, String.valueOf(key)));
                return true;
            }
            return false;
        });
    }

    private JComponent root() {
        return fx.studio.view().component();
    }

    private LogicalSessionView view(String session) {
        return fx.sessionViews().stream().filter(v -> v.sessionId().equals(fx.active().sessions().stream()
                .filter(s -> s.name().equals(session)).findFirst().orElseThrow().id())).findFirst().orElseThrow();
    }

    private static final KeyStroke F2 = KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0);
    private static final KeyStroke DELETE = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0);
    private static final KeyStroke ALT_UP = KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK);
    private static final KeyStroke ALT_DOWN = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK);
    private static final KeyStroke CTRL_D = KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK);
    private static final KeyStroke CTRL_Z = KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK);
    private static final KeyStroke CTRL_Y = KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK);

    // ------------------------------------------------------------------ automação e sessões

    @Test
    @DisplayName("S004-001/002/003 editar nome e descrição da automação pela interface; nome duplicado mostrado no campo")
    void automacao() {
        Automation outra = fx.newAutomation("Outra", null);
        Automation a = recordedExample();
        int sessions = a.sessions().size();
        int steps = a.stepCount();
        fx.prompts.then(f -> {
            NameDescriptionForm n = (NameDescriptionForm) f;
            assertEquals("Consulta de clientes", n.nameInput().getText(), "formulário começa com os valores atuais");
            n.nameInput().setText("outra");
            n.descriptionInput().setText("Nova descrição");
        });
        Edt.run(() -> fx.studio.controller().editAutomation());
        assertFalse(fx.prompts.results.get(fx.prompts.results.size() - 1), "duplicado: o diálogo continua aberto");
        assertTrue(fx.prompts.last().form().visibleErrors().contains("Já existe uma automação com este nome"));
        assertEquals("Consulta de clientes", a.name());
        assertEquals("Consulta cadastral.", a.description(), "nenhuma alteração parcial");

        Edt.run(() -> fx.studio.controller().selectAutomation(a.id()));
        fx.prompts.then(f -> {
            NameDescriptionForm n = (NameDescriptionForm) f;
            n.nameInput().setText("Consulta de clientes PF");
            n.descriptionInput().setText("Automação destinada à consulta de informações cadastrais.");
        });
        assertTrue(press(root(), F2), "F2 com a automação selecionada");
        assertEquals("Consulta de clientes PF", a.name());
        assertEquals("Automação destinada à consulta de informações cadastrais.", a.description());
        assertSame(a, fx.active(), "mesma automação (identidade preservada)");
        assertEquals(sessions, a.sessions().size());
        assertEquals(steps, a.stepCount());
        assertTrue(fx.navigator().items().contains(a));
        int tab = Edt.call(() -> fx.studio.view().workArea().getTabs().indexOfComponent(fx.overview()));
        assertEquals("Consulta de clientes PF", Edt.call(() -> fx.studio.view().workArea().getTabs().getTitleAt(tab)), "aba renomeada");
        assertEquals(2, fx.studio.state().automations().size(), "nenhuma automação nova para simular a renomeação");
        assertNotEquals(outra.id(), a.id());
    }

    @Test
    @DisplayName("S004-004/005 editar sessão (nome, descrição, observações) preservando identidade, etapas e seleção")
    void sessao() {
        Automation a = recordedExample();
        LogicalSession auth = a.sessions().get(0);
        Edt.run(() -> fx.studio.controller().selectSession(a.id(), auth.id()));
        fx.prompts.then(f -> {
            NameDescriptionForm n = (NameDescriptionForm) f;
            n.nameInput().setText("Autenticação");
            n.descriptionInput().setText("Etapas responsáveis pelo acesso inicial ao sistema.");
            n.notesInput().setText("O acesso utiliza credenciais disponibilizadas pelo cliente.");
        });
        assertTrue(press(root(), F2));
        assertSame(auth, a.sessions().get(0));
        assertEquals("Etapas responsáveis pelo acesso inicial ao sistema.", auth.description());
        assertEquals("O acesso utiliza credenciais disponibilizadas pelo cliente.", auth.notes());
        assertEquals(4, auth.stepCount());
        assertEquals(new Selection.OfSession(a.id(), auth.id()), fx.studio.state().selection());
        assertTrue(fx.inspectorText().contains("O acesso utiliza credenciais disponibilizadas pelo cliente."));
    }

    @Test
    @DisplayName("S004-006/007/008/019 reorganizar sessões pelo painel e menu: numeração recalculada na tela")
    void reorganizarSessoes() {
        Automation a = recordedExample();
        fx.newSession("Resultado");
        var noticeBefore = fx.studio.state().lastNotice();
        LogicalSession consulta = a.sessions().get(1);
        Edt.run(() -> fx.studio.controller().moveSession(consulta.id(), -1));
        assertEquals(List.of("Consulta", "Autenticação", "Resultado"), a.sessions().stream().map(LogicalSession::name).toList());
        assertEquals("01   Preencher Pesquisa", fx.stepLines().get(0), "numeração recalculada");
        assertEquals("05   Clicar no link Clientes", fx.stepLines().get(4));
        assertEquals(List.of("Consulta", "Autenticação", "Resultado"),
                fx.sessionViews().stream().map(v -> a.session(v.sessionId()).orElseThrow().name()).toList(), "ordem na tela");
        Edt.run(() -> fx.studio.controller().moveSession(consulta.id(), -1)); // primeira: nada acontece
        assertEquals("Consulta", a.sessions().get(0).name());
        LogicalSession resultado = a.sessions().get(2);
        Edt.run(() -> fx.studio.controller().moveSession(resultado.id(), 1)); // última: nada acontece
        Edt.run(() -> fx.studio.controller().moveSession(resultado.id(), -2));
        assertEquals("Resultado", a.sessions().get(0).name(), "última → primeira");
        assertEquals(noticeBefore, fx.studio.state().lastNotice(), "limites não geram aviso de erro");
    }

    @Test
    @DisplayName("regressão (homologação real): subir e descer a mesma sessão devolve a ordem original também na tela")
    void subirEDescerSessao() {
        Automation a = recordedExample();
        LogicalSession auth = a.sessions().get(0);
        LogicalSession consulta = a.sessions().get(1);
        Edt.run(() -> fx.studio.controller().moveSession(consulta.id(), -1));
        assertEquals(List.of(consulta.id(), auth.id()), fx.sessionViews().stream().map(LogicalSessionView::sessionId).toList());
        Edt.run(() -> fx.studio.controller().moveSession(consulta.id(), 1));
        assertEquals(List.of(auth.id(), consulta.id()), fx.sessionViews().stream().map(LogicalSessionView::sessionId).toList(),
                "cartões na ordem do domínio");
        assertEquals("01   Preencher campo Usuário", fx.stepLines().get(0));
        Edt.run(() -> fx.studio.controller().moveSession(consulta.id(), -1));
        Edt.run(() -> fx.studio.controller().undo());
        assertEquals(List.of(auth.id(), consulta.id()), fx.sessionViews().stream().map(LogicalSessionView::sessionId).toList(), "também no desfazer");
    }

    @Test
    @DisplayName("S004-009/010/011 excluir sessão vazia, com etapas (confirmação explícita) e cancelar")
    void excluirSessao() {
        Automation a = recordedExample();
        LogicalSession vazia = fx.newSession("Vazia");
        fx.prompts.thenConfirm(true);
        Edt.run(() -> fx.studio.controller().deleteSession(vazia.id()));
        assertEquals("Excluir sessão", fx.prompts.confirmations.get(0).confirmLabel());
        assertEquals(2, a.sessions().size());

        LogicalSession auth = a.sessions().get(0);
        fx.prompts.thenConfirm(false);
        Edt.run(() -> fx.studio.controller().deleteSession(auth.id()));
        StudioConfirmations.assertText(fx.prompts.confirmations.get(1), "Sessão: Autenticação", "Etapas existentes: 4",
                "A exclusão também removerá as 4 etapas contidas nesta sessão.");
        assertEquals("Excluir sessão e etapas", fx.prompts.confirmations.get(1).confirmLabel());
        assertTrue(fx.prompts.confirmations.get(1).danger());
        assertEquals(2, a.sessions().size(), "cancelar não exclui");
        assertEquals(5, a.stepCount());

        Edt.run(() -> fx.studio.controller().selectSession(a.id(), auth.id()));
        fx.prompts.thenConfirm(true);
        Edt.run(() -> fx.studio.controller().deleteSession(auth.id()));
        assertEquals(List.of("Consulta"), a.sessions().stream().map(LogicalSession::name).toList());
        assertEquals(1, a.stepCount(), "nenhuma etapa órfã");
        assertEquals(new Selection.OfSession(a.id(), a.sessions().get(0).id()), fx.studio.state().selection(), "seleção vai para outra sessão");
        assertEquals(List.of("01   Preencher Pesquisa"), fx.stepLines());
    }

    // ------------------------------------------------------------------ etapas

    @Test
    @DisplayName("S004-012/013/014/015 editor da etapa: nome, descrição e tipo; parâmetros incompatíveis exigem decisão explícita")
    void editarEtapa() {
        recordedExample();
        AutomationStep pesquisa = step("Preencher Pesquisa");
        select(pesquisa);
        fx.prompts.then(f -> {
            StepDetailsForm d = (StepDetailsForm) f;
            d.basics().nameInput().setText("Pesquisar cliente");
            d.basics().descriptionInput().setText("Termo da consulta");
            d.modeInput(StepDetailsForm.ValueMode.VARIABLE).setSelected(true);
            d.valueInput().setText("consulta.termo");
        });
        assertTrue(press(root(), F2), "F2 com a etapa selecionada abre o editor");
        assertEquals("Pesquisar cliente", pesquisa.name());
        assertEquals("Termo da consulta", pesquisa.description());
        assertEquals("Variável consulta.termo", pesquisa.parameters().value().orElseThrow().display());

        fx.prompts.then(f -> ((StepDetailsForm) f).basics().typeInput().setSelectedItem(ActionType.CLICK)).thenConfirm(false);
        Edt.run(() -> fx.studio.controller().openStepDetails(pesquisa.id()));
        assertFalse(fx.prompts.results.get(fx.prompts.results.size() - 1), "recusar a remoção mantém o diálogo aberto");
        assertEquals(ActionType.FILL, pesquisa.actionType(), "nada muda");
        assertTrue(pesquisa.parameters().value().isPresent(), "o valor não some em silêncio");
        assertEquals("Remover parâmetros incompatíveis?", fx.prompts.confirmations.get(0).title());
        assertTrue(fx.prompts.confirmations.get(0).message().contains("Variável consulta.termo"));
        assertTrue(fx.prompts.last().form().visibleErrors().contains("Este tipo de ação não recebe valor"));

        fx.prompts.then(f -> ((StepDetailsForm) f).basics().typeInput().setSelectedItem(ActionType.CLICK)).thenConfirm(true);
        Edt.run(() -> fx.studio.controller().openStepDetails(pesquisa.id()));
        assertEquals(ActionType.CLICK, pesquisa.actionType());
        assertTrue(pesquisa.parameters().isEmpty(), "removidos por decisão explícita");
    }

    @Test
    @DisplayName("S004-016/018/019/050 Alt+↑/Alt+↓ na lista reorganizam a etapa; StepId e seleção preservados")
    void reorganizarPeloTeclado() {
        Automation a = recordedExample();
        AutomationStep entrar = step("Clicar no botão Entrar");
        select(entrar);
        LogicalSessionView v = view("Autenticação");
        assertTrue(press(v.stepList(), ALT_UP));
        assertTrue(press(v.stepList(), ALT_UP));
        assertEquals(List.of("Clicar no botão Entrar", "Preencher campo Usuário", "Preencher campo Senha", "Clicar no link Clientes"),
                names(a.sessions().get(0)));
        assertSame(entrar, a.sessions().get(0).steps().get(0), "mesma entidade");
        assertEquals(new Selection.OfStep(a.id(), a.sessions().get(0).id(), entrar.id()), fx.studio.state().selection());
        assertEquals("01   Clicar no botão Entrar", fx.stepLines().get(0));
        assertEquals(entrar, Edt.call(() -> v.stepList().getSelectedValue()), "a etapa continua selecionada na lista");
        assertTrue(press(v.stepList(), ALT_UP), "na primeira posição: atalho aceito, nada muda");
        assertEquals(entrar, a.sessions().get(0).steps().get(0));
        assertTrue(press(v.stepList(), ALT_DOWN));
        assertEquals("Clicar no botão Entrar", names(a.sessions().get(0)).get(1));
    }

    @Test
    @DisplayName("S004-017/018/019/039 mover etapa para outra sessão pelo diálogo: mesma entidade, comentário e numeração")
    void moverEntreSessoes() {
        Automation a = recordedExample();
        AutomationStep clientes = step("Clicar no link Clientes");
        Edt.run(() -> fx.studio.service().addComment(a.id(), clientes.id(), "Menu lateral"));
        select(clientes);
        fx.prompts.then(f -> {
            MoveStepForm m = (MoveStepForm) f;
            for (int i = 0; i < m.sessionInput().getItemCount(); i++) {
                if (m.sessionInput().getItemAt(i).label().endsWith("· Consulta")) m.sessionInput().setSelectedIndex(i);
            }
            m.positionInput().setSelectedIndex(0);
            assertEquals("No início", m.positionInput().getItemAt(0).label());
        });
        Edt.run(() -> fx.studio.controller().moveStepTo(clientes.id()));
        assertEquals(List.of("Preencher campo Usuário", "Preencher campo Senha", "Clicar no botão Entrar"), names(a.sessions().get(0)));
        assertEquals(List.of("Clicar no link Clientes", "Preencher Pesquisa"), names(a.sessions().get(1)));
        assertSame(clientes, a.step(clientes.id()).orElseThrow());
        assertEquals(StepOrigin.RECORDED, clientes.provenance().origin());
        assertEquals("Menu lateral", clientes.comments().get(0).text());
        assertEquals(List.of("01   Preencher campo Usuário", "02   Preencher campo Senha", "03   Clicar no botão Entrar", "04   Clicar no link Clientes",
                "05   Preencher Pesquisa"), fx.stepLines());
        assertEquals(new Selection.OfStep(a.id(), a.sessions().get(1).id(), clientes.id()), fx.studio.state().selection(), "selecionada no destino");
    }

    @Test
    @DisplayName("S004-020/021 Ctrl+D duplica: StepId novo, origem manual, sem gravação nem evento inventados")
    void duplicar() {
        Automation a = recordedExample();
        AutomationStep entrar = step("Clicar no botão Entrar");
        select(entrar);
        long recordedBefore = a.sessions().stream().flatMap(s -> s.steps().stream()).filter(s -> s.provenance().origin() == StepOrigin.RECORDED).count();
        assertTrue(press(view("Autenticação").stepList(), CTRL_D));
        AutomationStep copy = a.sessions().get(0).steps().get(3);
        assertNotEquals(entrar.id(), copy.id());
        assertEquals("Clicar no botão Entrar", copy.name());
        assertEquals(StepOrigin.MANUAL, copy.provenance().origin());
        assertTrue(copy.provenance().correlation().isEmpty());
        assertTrue(copy.provenance().recordingObservation().isEmpty());
        assertEquals(entrar.id(), copy.derivedFrom().orElseThrow());
        assertEquals(recordedBefore, a.sessions().stream().flatMap(s -> s.steps().stream()).filter(s -> s.provenance().origin() == StepOrigin.RECORDED).count());
        assertEquals(new Selection.OfStep(a.id(), a.sessions().get(0).id(), copy.id()), fx.studio.state().selection(), "a cópia fica selecionada");
        assertTrue(fx.inspectorText().contains("Manual — duplicada de outra etapa"));
        assertFalse(fx.inspectorText().contains("Gravada pelo Nekkora Recorder"));
    }

    @Test
    @DisplayName("S004-022/023/050 Delete exclui com confirmação; seleção vai para a próxima, a anterior ou a sessão")
    void excluirEtapa() {
        Automation a = recordedExample();
        AutomationStep senha = step("Preencher campo Senha");
        select(senha);
        LogicalSessionView v = view("Autenticação");
        fx.prompts.thenConfirm(false);
        assertTrue(press(v.stepList(), DELETE));
        assertEquals(4, a.sessions().get(0).stepCount(), "cancelar não exclui");
        assertTrue(fx.prompts.confirmations.get(0).message().contains("Preencher campo Senha"));
        assertTrue(fx.prompts.confirmations.get(0).message().contains("removerá a etapa da sessão Autenticação"));
        fx.prompts.thenConfirm(true);
        assertTrue(press(v.stepList(), DELETE));
        assertEquals(List.of("Preencher campo Usuário", "Clicar no botão Entrar", "Clicar no link Clientes"), names(a.sessions().get(0)));
        AutomationStep entrar = step("Clicar no botão Entrar");
        assertEquals(new Selection.OfStep(a.id(), a.sessions().get(0).id(), entrar.id()), fx.studio.state().selection(), "a próxima");
        AutomationStep clientes = step("Clicar no link Clientes");
        select(clientes);
        fx.prompts.thenConfirm(true);
        Edt.run(() -> fx.studio.controller().deleteStep(clientes.id()));
        assertEquals(new Selection.OfStep(a.id(), a.sessions().get(0).id(), entrar.id()), fx.studio.state().selection(), "a anterior");
        AutomationStep pesquisa = step("Preencher Pesquisa");
        select(pesquisa);
        fx.prompts.thenConfirm(true);
        Edt.run(() -> fx.studio.controller().deleteStep(pesquisa.id()));
        assertEquals(new Selection.OfSession(a.id(), a.sessions().get(1).id()), fx.studio.state().selection(), "sessão vazia: a sessão");
        assertEquals(List.of("01   Preencher campo Usuário", "02   Clicar no botão Entrar"), fx.stepLines());
    }

    // ------------------------------------------------------------------ parâmetros

    @Test
    @DisplayName("S004-024/025/026/027 literal permitido, variável, credencial; literal desabilitado e recusado em campo sensível")
    void parametros() {
        Automation a = recordedExample();
        AutomationStep usuario = step("Preencher campo Usuário");
        fx.prompts.then(f -> {
            StepDetailsForm d = (StepDetailsForm) f;
            d.modeInput(StepDetailsForm.ValueMode.VARIABLE).setSelected(true);
            d.valueInput().setText("cliente.usuario");
        });
        Edt.run(() -> fx.studio.controller().openStepDetails(usuario.id()));
        assertEquals(new ValueSource.VariableReference("cliente.usuario"), usuario.parameters().value().orElseThrow());

        AutomationStep senha = step("Preencher campo Senha");
        fx.prompts.then(f -> {
            StepDetailsForm d = (StepDetailsForm) f;
            assertFalse(d.modeInput(StepDetailsForm.ValueMode.LITERAL).isEnabled(), "literal indisponível em campo sensível");
            d.modeInput(StepDetailsForm.ValueMode.CREDENTIAL).setSelected(true);
            d.valueInput().setText("sistema.senha");
        });
        Edt.run(() -> fx.studio.controller().openStepDetails(senha.id()));
        assertEquals(new ValueSource.CredentialReference("sistema.senha"), senha.parameters().value().orElseThrow());
        assertTrue(fx.inspectorText().contains("Credencial sistema.senha"));

        fx.prompts.then(f -> {
            StepDetailsForm d = (StepDetailsForm) f;
            d.modeInput(StepDetailsForm.ValueMode.LITERAL).setSelected(true); // forçado: o domínio recusa
            d.valueInput().setText("Senha2026Teste");
        });
        Edt.run(() -> fx.studio.controller().openStepDetails(senha.id()));
        assertTrue(fx.prompts.last().form().visibleErrors().contains("Campos sensíveis não guardam valores digitados"));
        assertEquals(new ValueSource.CredentialReference("sistema.senha"), senha.parameters().value().orElseThrow(), "nada mudou");

        AutomationStep pesquisa = step("Preencher Pesquisa");
        fx.prompts.then(f -> {
            StepDetailsForm d = (StepDetailsForm) f;
            d.modeInput(StepDetailsForm.ValueMode.LITERAL).setSelected(true);
            d.valueInput().setText("Silva");
        });
        Edt.run(() -> fx.studio.controller().openStepDetails(pesquisa.id()));
        assertEquals(new ValueSource.LiteralValue("Silva"), pesquisa.parameters().value().orElseThrow());

        fx.prompts.then(f -> {
            StepDetailsForm d = (StepDetailsForm) f;
            d.modeInput(StepDetailsForm.ValueMode.VARIABLE).setSelected(true);
            d.valueInput().setText("nome com espaço");
        });
        Edt.run(() -> fx.studio.controller().openStepDetails(pesquisa.id()));
        assertTrue(fx.prompts.last().form().visibleErrors().contains("Use letras, números, ponto"));
        assertEquals(a.stepCount(), 5);
    }

    @Test
    @DisplayName("S004-028 OptionChoice: rótulo e valor técnico distintos, sem etapa extra")
    void opcao() {
        Automation a = recordedExample();
        AutomationStep estado = recorded(a.sessions().get(1), "Selecionar São Paulo no campo Estado", ActionType.SELECT_OPTION,
                element("SELECT", "Estado", false), ActionParameters.withOption(new OptionChoice("São Paulo", "SP")), "ev-5");
        int steps = a.stepCount();
        fx.prompts.then(f -> {
            StepDetailsForm d = (StepDetailsForm) f;
            assertEquals("São Paulo", d.optionLabelInput().getText());
            assertEquals("SP", d.optionValueInput().getText());
            d.optionLabelInput().setText("Rio de Janeiro");
            d.optionValueInput().setText("RJ");
        });
        Edt.run(() -> fx.studio.controller().openStepDetails(estado.id()));
        assertEquals(new OptionChoice("Rio de Janeiro", "RJ"), estado.parameters().option().orElseThrow());
        assertEquals(steps, a.stepCount());
        assertEquals(new OptionChoice("São Paulo", "SP"), estado.baseline().orElseThrow().parameters().option().orElseThrow());
    }

    // ------------------------------------------------------------------ referências técnicas e procedência

    @Test
    @DisplayName("S004-029/030/031/032 localizadores: visualizar, escolher validado, manual não verificado, original intacto")
    void localizadores() {
        recordedExample();
        AutomationStep entrar = step("Clicar no botão Entrar");
        ElementReference original = entrar.capturedElement().orElseThrow();
        select(entrar);
        String inspector = fx.inspectorText();
        assertTrue(inspector.contains("✓ Preferencial"), inspector);
        assertTrue(inspector.contains("Validado na captura"));
        assertTrue(inspector.contains("Candidato alternativo, não validado: 3 correspondências"));
        fx.prompts.then(f -> {
            StepDetailsForm d = (StepDetailsForm) f;
            assertEquals(3, d.preferredInputs().size());
            assertTrue(d.preferredInputs().get(1).isEnabled());
            assertFalse(d.preferredInputs().get(2).isEnabled(), "não validado não pode ser preferencial");
            d.preferredInputs().get(1).setSelected(true);
            d.manualSyntaxInput().setSelectedItem(ManualLocator.Syntax.CSS);
            d.manualExpressionInput().setText("form#login button[type=submit]");
            d.addManualLocatorFromInputs();
            assertTrue(StudioFixture.texts(d).contains("Não verificado"));
        });
        Edt.run(() -> fx.studio.controller().openStepDetails(entrar.id()));
        assertEquals(1, entrar.element().orElseThrow().preferredLocatorIndex());
        assertSame(original, entrar.capturedElement().orElseThrow(), "ElementReference original preservado");
        assertEquals(0, original.preferredLocatorIndex());
        assertEquals(1, entrar.manualLocators().size());
        assertEquals(ManualLocator.Verification.NOT_VERIFIED, entrar.manualLocators().get(0).verification());
        assertEquals(3, entrar.element().orElseThrow().locators().size(), "nada recalculado nem acrescentado aos capturados");
        assertTrue(fx.inspectorText().contains("Proposto manualmente"));
        assertTrue(fx.inspectorText().contains("Não verificado"));
    }

    @Test
    @DisplayName("S004-033/034/035 procedência: RECORDED preservado, alteração identificável, original × atual e restauração")
    void procedenciaERestauracao() {
        recordedExample();
        AutomationStep entrar = step("Clicar no botão Entrar");
        select(entrar);
        assertTrue(fx.inspectorText().contains("Alterada manualmente\nNão") || fx.inspectorText().contains("Não"));
        fx.prompts.then(f -> ((StepDetailsForm) f).basics().nameInput().setText("Confirmar autenticação"));
        Edt.run(() -> fx.studio.controller().openStepDetails(entrar.id()));
        assertEquals(StepOrigin.RECORDED, entrar.provenance().origin());
        assertEquals(RecordingObservation.BusinessOutcome.NOT_VALIDATED, entrar.provenance().recordingObservation().orElseThrow().businessOutcome());
        String text = fx.inspectorText();
        assertTrue(text.contains("Gravada pelo Nekkora Recorder"), text);
        assertTrue(text.contains("Nome original") && text.contains("Clicar no botão Entrar"));
        assertTrue(text.contains("Nome atual") && text.contains("Confirmar autenticação"));
        assertTrue(text.contains("Não validado"), "resultado de negócio continua não validado");
        assertTrue(text.contains("Alterada manualmente"));
        fx.prompts.thenCancel();
        Edt.run(() -> fx.studio.controller().openStepDetails(entrar.id()));
        String origin = Edt.call(() -> ((StepDetailsForm) fx.prompts.last().form()).originText());
        assertTrue(origin.contains("Nome — modificado manualmente"), origin);
        assertTrue(origin.contains("Original: Clicar no botão Entrar"));
        assertTrue(origin.contains("Atual: Confirmar autenticação"));
        Edt.run(() -> fx.studio.controller().restoreOriginal(entrar.id(), StepField.NAME));
        assertEquals("Clicar no botão Entrar", entrar.name());
        assertTrue(entrar.manualRevision().isEmpty());
        assertFalse(DisplaySafety.UUID.matcher(fx.inspectorText()).find(), "nenhum identificador interno");
    }

    // ------------------------------------------------------------------ comentários

    @Test
    @DisplayName("S004-036/037/038/039 comentários no editor: adicionar, editar (data preservada), excluir; acompanham a etapa")
    void comentarios() {
        Automation a = recordedExample();
        AutomationStep pesquisa = step("Preencher Pesquisa");
        fx.prompts.then(f -> {
            StepDetailsForm d = (StepDetailsForm) f;
            d.commentInput().setText("Aguardar carregamento dos resultados.");
            d.commitCommentFromInput();
            d.commentInput().setText("Segundo comentário");
            d.commitCommentFromInput();
        });
        Edt.run(() -> fx.studio.controller().openStepDetails(pesquisa.id()));
        assertEquals(List.of("Aguardar carregamento dos resultados.", "Segundo comentário"), pesquisa.comments().stream().map(c -> c.text()).toList());
        var created = pesquisa.comments().get(0).createdAt();
        fx.prompts.then(f -> {
            StepDetailsForm d = (StepDetailsForm) f;
            d.editComment(0);
            d.commentInput().setText("Aguardar a tabela de resultados.");
            d.commitCommentFromInput();
            d.removeComment(1);
            d.commentInput().setText("CPF 123.456.789-09 do cliente");
            d.commitCommentFromInput();
            assertTrue(d.commentFieldView().hasError(), "dado sensível recusado no próprio campo");
        });
        Edt.run(() -> fx.studio.controller().openStepDetails(pesquisa.id()));
        assertEquals(List.of("Aguardar a tabela de resultados."), pesquisa.comments().stream().map(c -> c.text()).toList());
        assertEquals(created, pesquisa.comments().get(0).createdAt(), "data de criação preservada");
        Edt.run(() -> fx.studio.service().moveStep(a.id(), pesquisa.id(), a.sessions().get(0).id(), 0));
        assertEquals("Aguardar a tabela de resultados.", pesquisa.comments().get(0).text(), "acompanha a etapa movida");
        select(pesquisa);
        assertTrue(fx.inspectorText().contains("Aguardar a tabela de resultados."));
    }

    // ------------------------------------------------------------------ desfazer / refazer

    @Test
    @DisplayName("S004-040/041/042/043/044/045 desfazer e refazer: edição, exclusão, movimentação; identidades; redo invalidado")
    void desfazerRefazer() {
        Automation a = recordedExample();
        AutomationStep entrar = step("Clicar no botão Entrar");
        AutomationStep clientes = step("Clicar no link Clientes");
        Edt.run(() -> fx.studio.service().updateStepBasics(a.id(), entrar.id(), "Confirmar autenticação", "", ActionType.CLICK));
        Edt.run(() -> fx.studio.service().moveStep(a.id(), clientes.id(), a.sessions().get(1).id(), 0));
        LogicalSession auth = a.sessions().get(0);
        Selection beforeDelete = fx.studio.state().selection();
        Edt.run(() -> fx.studio.service().removeSession(a.id(), auth.id()));
        assertEquals(1, a.sessions().size());

        assertTrue(press(root(), CTRL_Z), "Ctrl+Z: desfaz a exclusão");
        assertEquals(2, a.sessions().size());
        assertSame(auth, a.sessions().get(0), "a mesma sessão de volta");
        assertSame(entrar, a.step(entrar.id()).orElseThrow(), "a mesma etapa de volta");
        assertEquals(beforeDelete, fx.studio.state().selection(), "seleção de antes da exclusão restaurada");
        assertTrue(press(root(), CTRL_Z), "desfaz a movimentação");
        assertEquals(auth.id(), a.sessionOf(clientes.id()).orElseThrow().id());
        assertTrue(press(root(), CTRL_Z), "desfaz a edição");
        assertEquals("Clicar no botão Entrar", entrar.name());
        // antes destas edições: a criação manual de "Preencher Pesquisa" (vizinha) e, antes dela, etapas do Recorder
        assertTrue(press(root(), CTRL_Z), "desfaz a criação da etapa manual");
        assertTrue(a.sessions().get(1).steps().stream().noneMatch(st -> st.name().equals("Preencher Pesquisa")));
        int recordedSteps = a.stepCount();
        assertTrue(press(root(), CTRL_Z), "a próxima é anterior às etapas do Recorder: conflito");
        assertEquals(ValidationProblem.HISTORY_CONFLICT.message(), fx.studio.state().lastNotice().orElseThrow().message());
        assertEquals(recordedSteps, a.stepCount(), "nenhuma etapa gravada removida por um desfazer antigo");
        assertFalse(Edt.call(() -> fx.studio.view().actions().get(com.nekkora.studio.app.ui.StudioActions.UNDO).isEnabled()), "histórico descartado");
        assertFalse(fx.studio.service().canRedo(a.id()), "o conflito descarta também o refazer");
    }

    @Test
    @DisplayName("S004-043/044 refazer reaplica sem identidades novas; nova edição invalida o refazer")
    void refazer() {
        Automation a = recordedExample();
        AutomationStep entrar = step("Clicar no botão Entrar");
        AutomationStep clientes = step("Clicar no link Clientes");
        Edt.run(() -> fx.studio.service().updateStepBasics(a.id(), entrar.id(), "Confirmar autenticação", "", ActionType.CLICK));
        Edt.run(() -> fx.studio.service().moveStep(a.id(), clientes.id(), a.sessions().get(1).id(), 0));
        AutomationStep copy = Edt.call(() -> fx.studio.service().duplicateStep(a.id(), entrar.id()));
        assertTrue(press(root(), CTRL_Z));
        assertTrue(a.step(copy.id()).isEmpty(), "duplicação desfeita");
        assertTrue(press(root(), CTRL_Z));
        assertTrue(press(root(), CTRL_Z));
        assertEquals("Clicar no botão Entrar", entrar.name());

        assertTrue(press(root(), CTRL_Y), "refaz a edição");
        assertEquals("Confirmar autenticação", entrar.name());
        assertTrue(press(root(), CTRL_Y), "refaz a movimentação");
        assertEquals(a.sessions().get(1).id(), a.sessionOf(clientes.id()).orElseThrow().id());
        assertTrue(press(root(), CTRL_Y), "refaz a duplicação");
        assertSame(copy, a.step(copy.id()).orElseThrow(), "a mesma cópia, sem identificador novo");
        assertEquals(new Selection.OfStep(a.id(), a.sessions().get(0).id(), copy.id()), fx.studio.state().selection(), "seleção do refazer");
        Edt.run(() -> fx.studio.controller().undo());
        int stepsNow = a.stepCount();
        Edt.run(() -> fx.studio.service().addComment(a.id(), entrar.id(), "Nova edição"));
        assertFalse(fx.studio.service().canRedo(a.id()), "nova edição invalida o refazer");
        assertEquals(stepsNow, a.stepCount(), "nada perdido nem duplicado");
        long ids = a.sessions().stream().flatMap(s -> s.steps().stream()).map(AutomationStep::id).distinct().count();
        assertEquals(a.stepCount(), ids, "nenhuma identidade duplicada");
    }

    @Test
    @DisplayName("histórico: etapas do Recorder não entram e invalidam o desfazer com conflito explícito")
    void conflitoComORecorder() {
        Automation a = recordedExample();
        AutomationStep entrar = step("Clicar no botão Entrar");
        Edt.run(() -> fx.studio.service().updateStepBasics(a.id(), entrar.id(), "Confirmar autenticação", "", ActionType.CLICK));
        int history = fx.studio.service().history().undoSize(a.id());
        recorded(a.sessions().get(1), "Clicar no botão Pesquisar", ActionType.CLICK, element("BUTTON", "Pesquisar", false), null, "ev-9");
        assertEquals(history, fx.studio.service().history().undoSize(a.id()), "a etapa do Recorder não entra no histórico");
        Edt.run(() -> fx.studio.controller().undo());
        assertEquals("Confirmar autenticação", entrar.name(), "nada aplicado sobre um estado diferente");
        assertEquals(ValidationProblem.HISTORY_CONFLICT.message(), fx.studio.state().lastNotice().orElseThrow().message());
        assertEquals(0, fx.studio.service().history().undoSize(a.id()), "histórico descartado");
        assertTrue(a.step(step("Clicar no botão Pesquisar").id()).isPresent(), "a etapa gravada continua");
    }

    @Test
    @DisplayName("S004-046 operação recusada não altera nada nem entra no histórico")
    void semAlteracaoParcial() {
        Automation a = recordedExample();
        AutomationStep usuario = step("Preencher campo Usuário");
        long revision = a.revision();
        int history = fx.studio.service().history().undoSize(a.id());
        fx.prompts.then(f -> {
            StepDetailsForm d = (StepDetailsForm) f;
            d.basics().nameInput().setText("Novo nome");
            d.modeInput(StepDetailsForm.ValueMode.VARIABLE).setSelected(true);
            d.valueInput().setText("inválida com espaço");
        });
        Edt.run(() -> fx.studio.controller().openStepDetails(usuario.id()));
        assertEquals("Preencher campo Usuário", usuario.name(), "o nome não muda quando o parâmetro é recusado");
        assertEquals(revision, a.revision());
        assertEquals(history, fx.studio.service().history().undoSize(a.id()));
    }

    // ------------------------------------------------------------------ tela, teclado, temas, privacidade

    @Test
    @DisplayName("S004-049 atualização incremental: mover entre duas sessões relê só elas; reordenar sessões reconcilia sem recriar")
    void incremental() {
        Automation a = recordedExample();
        fx.newSession("Resultado");
        List<StudioEvent> events = new ArrayList<>();
        Edt.run(() -> fx.studio.state().addListener(events::add));
        List<LogicalSessionView> views = fx.sessionViews();
        int[] before = views.stream().mapToInt(LogicalSessionView::refreshCount).toArray();
        AutomationStep clientes = step("Clicar no link Clientes");
        Edt.run(() -> fx.studio.service().moveStep(a.id(), clientes.id(), a.sessions().get(1).id(), 0));
        StudioEvent e = events.get(events.size() - 1);
        assertTrue(e.has(StudioChange.CONTENT));
        assertEquals(java.util.Set.of(a.sessions().get(0).id(), a.sessions().get(1).id()), e.sessions(), "origem e destino");
        assertEquals(before[0] + 1, views.get(0).refreshCount());
        assertEquals(before[1] + 1, views.get(1).refreshCount());
        assertEquals(before[2], views.get(2).refreshCount(), "a sessão não afetada não foi relida");
        Edt.run(() -> fx.studio.service().moveSession(a.id(), a.sessions().get(2).id(), 0));
        List<LogicalSessionView> after = fx.sessionViews();
        assertTrue(after.containsAll(views), "as mesmas visões, reordenadas (sem reconstruir)");
    }

    @Test
    @DisplayName("S004-051 atalhos respeitam o contexto: Ctrl+Z num campo desfaz a digitação; Delete num campo não exclui etapa")
    void atalhos() {
        Automation a = recordedExample();
        AutomationStep entrar = step("Clicar no botão Entrar");
        Edt.run(() -> fx.studio.service().updateStepBasics(a.id(), entrar.id(), "Confirmar autenticação", "", ActionType.CLICK));
        com.nekkora.ui.input.NekkoraTextField field = new com.nekkora.ui.input.NekkoraTextField("x");
        Edt.run(() -> {
            field.setText("abc");
            field.replaceSelection("");
            field.setCaretPosition(3);
            field.replaceSelection("d");
        });
        assertTrue(press(field, CTRL_Z), "o campo trata o próprio Ctrl+Z");
        assertEquals("abc", Edt.call(field::getText), "desfez a digitação");
        assertEquals("Confirmar autenticação", entrar.name(), "o Ctrl+Z do campo não desfez a edição estrutural");
        assertFalse(Edt.call(() -> field.getInputMap(JComponent.WHEN_FOCUSED).get(DELETE) != null
                && "studio.step.delete".equals(field.getInputMap(JComponent.WHEN_FOCUSED).get(DELETE))), "Delete do campo é do campo");
        Object listDelete = Edt.call(() -> view("Autenticação").stepList().getInputMap(JComponent.WHEN_FOCUSED).get(DELETE));
        assertEquals("studio.step.delete", listDelete, "Delete só exclui com o foco na lista de etapas");
        assertTrue(Edt.call(() -> root().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(DELETE)) == null, "Delete não é atalho da janela");
        fx.prompts.thenCancel();
        assertTrue(press(root(), F2), "F2 edita o item selecionado");
        assertEquals(fx.active().sessions().get(1).steps().get(0).name(), fx.prompts.last().title());
    }

    @Test
    @DisplayName("S004-052 editor e painel nos quatro temas, sem cores fora dos tokens")
    void temas() {
        recordedExample();
        AutomationStep entrar = step("Clicar no botão Entrar");
        select(entrar);
        for (var theme : NekkoraThemes.all()) {
            Edt.theme(theme);
            Edt.render(fx.studio.view().component(), 1366, 768);
            StepDetailsForm form = Edt.call(() -> (StepDetailsForm) StudioForms.stepDetails(fx.active(), entrar).form());
            for (int i = 0; i < 5; i++) {
                int tab = i;
                Edt.run(() -> form.tabs().setSelectedIndex(tab));
                assertTrue(Edt.render(form, 760, 900).getWidth() > 0, theme.displayName());
            }
        }
        Edt.theme(NekkoraThemes.SAKURA_DARK);
    }

    @Test
    @DisplayName("S004-053 privacidade: editor e painel sem identificadores internos; segredos recusados em parâmetros e comentários")
    void privacidade() {
        Automation a = recordedExample();
        StringBuilder all = new StringBuilder();
        for (AutomationStep s : a.sessions().stream().flatMap(x -> x.steps().stream()).toList()) {
            select(s);
            all.append(fx.inspectorText());
            all.append(Edt.call(() -> StudioFixture.texts(StudioForms.stepDetails(a, s).form())));
            all.append(Edt.call(() -> ((StepDetailsForm) StudioForms.stepDetails(a, s).form()).originText()));
        }
        String text = all.toString();
        assertFalse(DisplaySafety.UUID.matcher(text).find(), "UUID visível");
        for (String secret : List.of("tgt-", "rec-3f2a9c1e", "ev-1", "ev-2")) assertFalse(text.contains(secret), "identificador interno visível: " + secret);
        AutomationStep pesquisa = step("Preencher Pesquisa");
        int history = fx.studio.service().history().undoSize(a.id());
        Edt.run(() -> {
            try {
                fx.studio.service().addComment(a.id(), pesquisa.id(), "token Bearer abcdefghijklmnop");
            } catch (com.nekkora.studio.domain.DomainValidationException ignored) {
                // recusado
            }
        });
        assertTrue(pesquisa.comments().isEmpty());
        assertEquals(history, fx.studio.service().history().undoSize(a.id()), "o histórico não guarda o que a privacidade recusou");
    }

    @Test
    @DisplayName("S004-056 nada simulado: editar não executa, não valida o negócio e o histórico é só em memória")
    void nadaSimulado() {
        recordedExample();
        AutomationStep entrar = step("Clicar no botão Entrar");
        fx.prompts.then(f -> ((StepDetailsForm) f).basics().nameInput().setText("Confirmar autenticação"));
        Edt.run(() -> fx.studio.controller().openStepDetails(entrar.id()));
        assertEquals(RecordingObservation.BusinessOutcome.NOT_VALIDATED, entrar.provenance().recordingObservation().orElseThrow().businessOutcome());
        String tooltip = Edt.call(() -> String.valueOf(fx.studio.view().actions().get(com.nekkora.studio.app.ui.StudioActions.UNDO)
                .getValue(Action.SHORT_DESCRIPTION)));
        assertTrue(tooltip.contains("somente em memória"));
        assertTrue(fx.studio.state().lastNotice().isEmpty() || !fx.studio.state().lastNotice().get().title().toLowerCase().contains("salv"));
    }

    // ------------------------------------------------------------------ revisão técnica (P1)

    @Test
    @DisplayName("REV-P1 FILL com variável → SELECT_OPTION com São Paulo/SP: salva a opção digitada, descarta só a variável antiga")
    void trocaDeTipoComParametroAnterior() {
        Automation a = recordedExample();
        AutomationStep pesquisa = step("Preencher Pesquisa");
        Edt.run(() -> fx.studio.service().setStepParameters(a.id(), pesquisa.id(),
                ActionParameters.withValue(new ValueSource.VariableReference("cliente.estado"))));
        fx.prompts.then(f -> {
            StepDetailsForm d = (StepDetailsForm) f;
            d.basics().typeInput().setSelectedItem(ActionType.SELECT_OPTION);
            assertTrue(d.modeInput(StepDetailsForm.ValueMode.VARIABLE).isSelected(), "o modo antigo continua marcado, escondido");
            d.optionLabelInput().setText("São Paulo");
            d.optionValueInput().setText("SP");
        }).thenConfirm(true);
        Edt.run(() -> fx.studio.controller().openStepDetails(pesquisa.id()));
        assertEquals(ActionType.SELECT_OPTION, pesquisa.actionType());
        assertEquals(new OptionChoice("São Paulo", "SP"), pesquisa.parameters().option().orElseThrow(), "a opção informada foi salva");
        assertTrue(pesquisa.parameters().value().isEmpty());
        assertEquals(1, fx.prompts.confirmations.size(), "o descarte da variável antiga foi perguntado");
        assertTrue(fx.prompts.confirmations.get(0).message().contains("Variável cliente.estado"));
        assertFalse(fx.prompts.confirmations.get(0).message().contains("São Paulo"), "a opção nova não está entre o que será removido");
    }

    @Test
    @DisplayName("REV-P1 trocar para um tipo que aceita o parâmetro novo sem ter um antigo não pergunta nada")
    void trocaDeTipoSemParametroAntigo() {
        Automation a = recordedExample();
        AutomationStep clientes = step("Clicar no link Clientes");
        fx.prompts.then(f -> {
            StepDetailsForm d = (StepDetailsForm) f;
            d.basics().typeInput().setSelectedItem(ActionType.SELECT_OPTION);
            d.optionLabelInput().setText("Clientes ativos");
        });
        Edt.run(() -> fx.studio.controller().openStepDetails(clientes.id()));
        assertEquals(new OptionChoice("Clientes ativos", ""), clientes.parameters().option().orElseThrow());
        assertTrue(fx.prompts.confirmations.isEmpty());
        assertEquals(a.stepCount(), 5);
    }

    @Test
    @DisplayName("REV-P1 opção com dado sensível pelo editor: recusada no campo, etapa e histórico intactos")
    void opcaoSensivelPeloEditor() {
        Automation a = recordedExample();
        AutomationStep estado = recorded(a.sessions().get(1), "Selecionar São Paulo no campo Estado", ActionType.SELECT_OPTION,
                element("SELECT", "Estado", false), ActionParameters.withOption(new OptionChoice("São Paulo", "SP")), "ev-5");
        for (String[] bad : List.of(new String[] {"usuario@empresa.com", "SP"}, new String[] {"São Paulo", "eyJhbGciOiJIUzI1NiJ9"},
                new String[] {"São Paulo", "3f2a9c1e-7b44-4d1a-9a55-0c2e8f6d1b77"}, new String[] {"123.456.789-09", ""})) {
            long revision = a.revision();
            int history = fx.studio.service().history().undoSize(a.id());
            fx.prompts.then(f -> {
                StepDetailsForm d = (StepDetailsForm) f;
                d.optionLabelInput().setText(bad[0]);
                d.optionValueInput().setText(bad[1]);
            });
            Edt.run(() -> fx.studio.controller().openStepDetails(estado.id()));
            assertFalse(fx.prompts.results.get(fx.prompts.results.size() - 1), "recusado: " + bad[0] + " / " + bad[1]);
            assertTrue(fx.prompts.last().form().visibleErrors().contains("dado sensível"), fx.prompts.last().form().visibleErrors());
            assertEquals(new OptionChoice("São Paulo", "SP"), estado.parameters().option().orElseThrow(), "etapa intacta");
            assertEquals(revision, a.revision());
            assertEquals(history, fx.studio.service().history().undoSize(a.id()), "nada no histórico");
        }
    }

    @Test
    @DisplayName("REV-P2 comentário numa etapa gravada aparece como intervenção manual no painel")
    void comentarioComoIntervencaoNoPainel() {
        Automation a = recordedExample();
        AutomationStep entrar = step("Clicar no botão Entrar");
        Edt.run(() -> fx.studio.service().addComment(a.id(), entrar.id(), "Verificar se a mensagem de autenticação aparece."));
        select(entrar);
        String text = fx.inspectorText();
        assertTrue(text.contains("Alterada manualmente\nSim"), text);
        assertTrue(text.contains("Comentários acrescentados"), text);
        assertTrue(entrar.manualRevision().orElseThrow().fields().isEmpty());
    }
}
