package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.app.recorder.RecorderPrompts;
import com.nekkora.studio.app.recorder.RecorderWindowHost;
import com.nekkora.studio.app.workspace.Selection;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.origin.StepOrigin;
import com.nekkora.studio.recorder.BrowserTab;
import com.nekkora.studio.recorder.RecorderSnapshot;
import com.nekkora.studio.recorder.RecorderState;
import com.nekkora.studio.recorder.RecordingMode;
import com.nekkora.studio.ui.recorder.RecordingSetupPanel;
import com.nekkora.tokens.NekkoraThemes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * STUDIO-003 pela aplicação: etapas gravadas chegam do navegador (outra thread), entram pelo StepIntake na EDT, na sessão
 * certa, aparecem na tela e no Floating Recorder, mostram os detalhes da gravação e somem juntas no cancelamento.
 */
class RecorderStepsStudioTest {

    private static final Pattern INTERNAL = Pattern.compile("rec-[0-9a-f]{8}|tgt-|ev-|route-|cap-\\d|doc:|\\bn\\d+\\b|op-[0-9a-f]{8}");
    private final FakeBrowser browser = new FakeBrowser();
    private final AtomicInteger exits = new AtomicInteger();
    private final ScriptedPrompts prompts = new ScriptedPrompts();
    private Studio studio;
    private SessionId chosen;

    @BeforeEach
    void setUp() {
        browser.stepsAware = true;
        Edt.theme(NekkoraThemes.SAKURA_DARK);
        RecorderPrompts recorderPrompts = new RecorderPrompts() {
            @Override
            public Optional<RecordingSetupPanel.Choice> chooseRecording(Automation a, SessionId pre, SetupSource source) {
                try {
                    List<BrowserTab> tabs = source.tabs().get(5, java.util.concurrent.TimeUnit.SECONDS);
                    return tabs.stream().filter(t -> t.access().allowed()).findFirst()
                            .map(t -> new RecordingSetupPanel.Choice(chosen, t, RecordingMode.CAPTURE_AND_INTERACT));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public CloseDecision askClose() {
                return CloseDecision.CONTINUE;
            }

            @Override
            public ExitDecision askExit() {
                return ExitDecision.STAY;
            }
        };
        studio = Edt.call(() -> Studio.create(StudioFixture.CLOCK, prompts, exits::incrementAndGet, browser, recorderPrompts, p -> RecorderWindowHost.headless()));
        Edt.render(studio.view().component(), 1440, 900);
    }

    @AfterEach
    void tearDown() {
        Edt.run(studio::dispose);
    }

    private Automation automation() {
        prompts.then(ScriptedPrompts.name("Consulta de clientes", null));
        Edt.run(() -> studio.controller().newAutomation());
        for (String s : List.of("Autenticação", "Consulta")) {
            prompts.then(ScriptedPrompts.name(s, null));
            Edt.run(() -> studio.controller().newSession());
        }
        prompts.then(ScriptedPrompts.step("Autenticação", "Abrir o sistema", ActionType.NAVIGATE));
        Edt.run(() -> studio.controller().newStep(null));
        return studio.state().activeAutomation().orElseThrow();
    }

    private static void await(BooleanSupplier c, String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 8000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("tempo esgotado: " + what);
            }
            Thread.sleep(15);
        }
        Edt.flush();
    }

    private String startRecording(SessionId session) throws InterruptedException {
        chosen = session;
        Edt.run(() -> studio.recorderPresenter().record());
        await(() -> studio.recorder().snapshot().state() == RecorderState.RECORDING && studio.recorder().snapshot().steps().enabled(), "RECORDING com etapas");
        return browser.recordingId;
    }

    private String route(String type) {
        return Json.string(browser.last(type).payload(), "routeRef");
    }

    static Map<String, Object> element(String kind, String name, String tag, String htmlId, boolean sensitive) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("resolution", "RESOLVED");
        e.put("kind", kind);
        e.put("role", null);
        e.put("name", name);
        e.put("tag", tag);
        e.put("htmlId", htmlId);
        e.put("htmlName", null);
        e.put("sensitive", sensitive);
        e.put("locators", List.of(Json.obj().put("strategy", "ID").put("description", tag + " {id=" + htmlId + "}").put("css", "#" + htmlId).put("xpath", null)
                .put("matchCount", 1).put("matchesTarget", true).put("stability", "HEURISTIC_STABLE").put("validation", "LIVE").build()));
        e.put("preferredLocator", 0);
        e.put("accessPath", List.of(Json.obj().put("type", "MAIN_DOCUMENT").put("label", "Login de teste").put("locator", null).build()));
        e.put("context", List.of(Json.obj().put("relation", "IN_FORM").put("kind", "FORM").put("name", "login").build()));
        e.put("lookalikes", 0);
        e.put("correlation", Json.obj().put("targetId", "tgt-a1b2c3d4").put("nodeId", "n12").put("captureId", "cap-9").put("documentId", "doc:f0").build());
        return e;
    }

    private void step(String rid, long seq, String route, String action, Map<String, Object> el, Map<String, Object> params) throws InterruptedException {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("recordingId", rid);
        p.put("eventId", "ev-" + seq + "-" + rid.substring(rid.length() - 6));
        p.put("sequence", seq);
        p.put("routeRef", route);
        p.put("observedAt", "2026-10-15T12:00:00.000Z");
        p.put("mode", "CAPTURE_AND_INTERACT");
        p.put("observation", "INTERACTION_OBSERVED");
        p.put("action", action);
        p.put("element", el);
        p.put("parameters", params != null ? params : Json.obj().put("valueCaptured", false).put("option", null).put("key", null).build());
        p.put("context", Json.obj().put("url", "http://127.0.0.1:18765/lab/studio003/login.html").put("title", "Login de teste").put("frame", "MAIN").build());
        p.put("evidence", "CLICK".equals(action) ? List.of("FORM_SUBMITTED") : List.of());
        p.put("notes", List.of());
        p.put("events", 4);
        browser.emitFromBridgeThread("studio.recording.step", p); // eventos do navegador chegam fora da EDT
    }

    private List<String> names(LogicalSession s) {
        return Edt.call(() -> s.steps().stream().map(AutomationStep::name).toList());
    }

    @Test
    @DisplayName("S003-013/015/017/034/035/036 — login gravado: StepIntake na EDT, ordem, sessão certa, tela e Floating Recorder atualizados, sem IDs")
    void s003LoginGravadoNoStudio() throws Exception {
        Automation a = automation();
        LogicalSession login = a.sessions().get(0);
        List<String> violations = new CopyOnWriteArrayList<>();
        RepaintManager previous = Edt.call(() -> RepaintManager.currentManager((JComponent) null));
        RepaintManager checking = new RepaintManager() {
            @Override
            public synchronized void addInvalidComponent(JComponent c) {
                if (!SwingUtilities.isEventDispatchThread()) violations.add("invalidate " + c.getClass().getSimpleName() + " em " + Thread.currentThread().getName());
                super.addInvalidComponent(c);
            }

            @Override
            public void addDirtyRegion(JComponent c, int x, int y, int w, int h) {
                if (!SwingUtilities.isEventDispatchThread()) violations.add("repaint " + c.getClass().getSimpleName() + " em " + Thread.currentThread().getName());
                super.addDirtyRegion(c, x, y, w, h);
            }
        };
        Edt.run(() -> RepaintManager.setCurrentManager(checking));
        try {
            String rid = startRecording(login.id());
            String r = route("studio.recording.start");
            step(rid, 1, r, "FILL", element("TEXT_INPUT", "Usuário", "input", "usuario", false), null);
            step(rid, 2, r, "FILL", element("PASSWORD_INPUT", "Senha", "input", "senha", true), null);
            step(rid, 3, r, "CLICK", element("BUTTON", "Entrar", "button", "entrar", false), null);
            await(() -> studio.recorder().snapshot().steps().accepted() == 3, "3 etapas registradas");
        } finally {
            Edt.run(() -> RepaintManager.setCurrentManager(previous));
        }
        assertTrue(violations.isEmpty(), "Swing alterado fora da EDT:\n" + String.join("\n", violations));
        assertEquals(List.of("Abrir o sistema", "Preencher campo Usuário", "Preencher campo Senha", "Clicar no botão Entrar"), names(login),
                "etapas gravadas depois da manual, na ordem da sequência");
        List<AutomationStep> recorded = Edt.call(() -> login.steps().subList(1, 4));
        assertTrue(recorded.stream().allMatch(s -> s.provenance().origin() == StepOrigin.RECORDED));
        assertTrue(recorded.get(1).parameters().isEmpty(), "senha sem valor");
        assertTrue(recorded.get(1).element().orElseThrow().sensitive());
        await(() -> browser.calls.stream().anyMatch(c -> c.type().equals("studio.recording.steps.ack") && Json.number(c.payload(), "upToSequence", 0) == 3),
                "confirmação depois do domínio");
        String view = StudioFixture.texts(studio.view().component());
        assertTrue(view.contains("Preencher campo Usuário") && view.contains("Clicar no botão Entrar"), "tela atualizada: " + view);
        String panel = StudioFixture.texts(studio.recorderPresenter().panel());
        assertTrue(panel.contains("3 registradas"), "Floating Recorder com a contagem real: " + panel);
        assertEquals("Gravando", Edt.call(() -> studio.recorderPresenter().panel().badge().getText()));
        assertFalse(panel.contains("Captura de ações indisponível"), "aviso da gravação técnica removido com sensores ativos");
        for (String text : List.of(view, panel)) {
            assertFalse(INTERNAL.matcher(text).find(), "identificador interno na interface: " + INTERNAL.matcher(text).results().map(m -> m.group()).toList());
        }
    }

    @Test
    @DisplayName("S003-025 detalhes — a etapa gravada mostra elemento, localizador, modo, interação e resultado não validado")
    void s003DetalhesDaEtapaGravada() throws Exception {
        Automation a = automation();
        LogicalSession login = a.sessions().get(0);
        String rid = startRecording(login.id());
        step(rid, 1, route("studio.recording.start"), "CLICK", element("BUTTON", "Entrar", "button", "entrar", false), null);
        await(() -> studio.recorder().snapshot().steps().accepted() == 1, "registrada");
        AutomationStep s = Edt.call(() -> login.steps().get(1));
        Edt.run(() -> studio.service().select(new Selection.OfStep(a.id(), login.id(), s.id())));
        Edt.flush();
        String props = StudioFixture.texts(studio.view().component());
        for (String expected : List.of("Gravada", "Capturar e interagir", "Interação observada e encaminhada à página", "Não validado", "envio de formulário",
                "Botão", "<button>", "entrar", "button {id=entrar}")) {
            assertTrue(props.contains(expected), "detalhe '" + expected + "' ausente em: " + props);
        }
        assertFalse(INTERNAL.matcher(props).find(), "IDs internos: " + INTERNAL.matcher(props).results().map(m -> m.group()).toList());
    }

    @Test
    @DisplayName("S003-033 — troca de sessão pelo Floating Recorder: rota nova; atrasado da sessão anterior fica nela")
    void s003TrocaDeSessao() throws Exception {
        Automation a = automation();
        LogicalSession login = a.sessions().get(0);
        LogicalSession consulta = a.sessions().get(1);
        String rid = startRecording(login.id());
        String r1 = route("studio.recording.start");
        Edt.run(() -> studio.recorderPresenter().panel().sessionCombo().setSelectedItem(consulta));
        await(() -> browser.count("studio.recording.route") == 1, "rota pedida");
        String r2 = route("studio.recording.route");
        step(rid, 2, r2, "CLICK", element("LINK", "Clientes", "a", "menu", false), null);
        step(rid, 1, r1, "FILL", element("PASSWORD_INPUT", "Senha", "input", "senha", true), null); // atrasado
        await(() -> studio.recorder().snapshot().steps().accepted() == 2, "2 registradas");
        assertEquals(List.of("Abrir o sistema", "Preencher campo Senha"), names(login));
        assertEquals(List.of("Clicar no link Clientes"), names(consulta));
    }

    @Test
    @DisplayName("Revisão — troca de sessão aguardando a extensão: a tela mostra a sessão confirmada e avisa; recusa mantém a anterior")
    void revTrocaDeSessaoPendenteNaTela() throws Exception {
        Automation a = automation();
        LogicalSession login = a.sessions().get(0);
        LogicalSession consulta = a.sessions().get(1);
        String rid = startRecording(login.id());
        String r1 = route("studio.recording.start");
        browser.auto = c -> c.type().equals("studio.recording.route") ? null : browser.extension(c); // a extensão ainda não respondeu
        Edt.run(() -> studio.recorderPresenter().panel().sessionCombo().setSelectedItem(consulta));
        await(() -> browser.count("studio.recording.route") == 1 && studio.recorder().snapshot().pendingSession() != null, "troca pendente");
        Edt.flush();
        var panel = studio.recorderPresenter().panel();
        assertEquals(login, Edt.call(() -> panel.sessionCombo().getSelectedValue()), "a tela mostra a sessão confirmada");
        assertFalse(Edt.call(() -> panel.sessionCombo().isEnabled()), "nada de outra troca enquanto esta não é confirmada");
        assertTrue(Edt.call(() -> panel.sessionNote().isVisible()));
        assertEquals("Aguardando a confirmação da extensão para trocar para a sessão “Consulta”. Até lá, as etapas continuam na sessão atual.",
                Edt.call(() -> panel.sessionNote().getText()));
        step(rid, 1, r1, "FILL", element("PASSWORD_INPUT", "Senha", "input", "senha", true), null);
        await(() -> studio.recorder().snapshot().steps().accepted() == 1, "1 registrada");
        assertEquals(List.of("Abrir o sistema", "Preencher campo Senha"), names(login));
        browser.last("studio.recording.route").future().completeExceptionally(new com.nekkora.studio.bridge.BridgeCommandException(
                com.nekkora.studio.bridge.BridgeError.CAPTURE_FAILED, "A página não confirmou a troca de sessão.", "SENSOR_NOT_CONFIRMED", false));
        await(() -> studio.recorder().snapshot().pendingSession() == null && studio.recorder().snapshot().problem() != null, "recusa aplicada");
        Edt.flush();
        assertEquals(login, Edt.call(() -> panel.sessionCombo().getSelectedValue()), "recusa: continua a sessão anterior");
        assertFalse(Edt.call(() -> panel.sessionNote().isVisible()));
        assertTrue(Edt.call(() -> panel.problemAlert().isVisible()), "o usuário é informado da recusa");
        assertEquals("Sessão não alterada", studio.recorder().snapshot().problem().title());
        assertTrue(Edt.call(() -> panel.sessionCombo().isEnabled()));
    }

    @Test
    @DisplayName("S003-038 — cancelar descarta só as etapas desta gravação (as manuais ficam)")
    void s003Cancelamento() throws Exception {
        Automation a = automation();
        LogicalSession login = a.sessions().get(0);
        String rid = startRecording(login.id());
        String r = route("studio.recording.start");
        step(rid, 1, r, "FILL", element("TEXT_INPUT", "Usuário", "input", "usuario", false), null);
        step(rid, 2, r, "CLICK", element("BUTTON", "Entrar", "button", "entrar", false), null);
        await(() -> studio.recorder().snapshot().steps().accepted() == 2, "registradas");
        Edt.run(() -> studio.recorderPresenter().panel().cancelButton().doClick());
        await(() -> studio.recorder().snapshot().state() == RecorderState.CANCELLED && names(login).size() == 1, "descartadas");
        assertEquals(List.of("Abrir o sistema"), names(login), "manual preservada; nenhuma exclusão parcial");
        String panel = StudioFixture.texts(studio.recorderPresenter().panel());
        assertTrue(panel.contains("as etapas desta gravação foram descartadas"), panel);
    }

    @Test
    @DisplayName("S003-016 — select: uma etapa com a opção como parâmetro; Enter vira PRESS_KEY")
    void s003SelectEEnter() throws Exception {
        Automation a = automation();
        LogicalSession consulta = a.sessions().get(1);
        String rid = startRecording(consulta.id());
        String r = route("studio.recording.start");
        step(rid, 1, r, "SELECT_OPTION", element("SELECT", "Estado", "select", "estado", false),
                Json.obj().put("valueCaptured", false).put("option", Json.obj().put("label", "São Paulo").put("value", "SP").build()).put("key", null).build());
        step(rid, 2, r, "PRESS_KEY", element("SEARCH_INPUT", "Pesquisa", "input", "pesquisa", false),
                Json.obj().put("valueCaptured", false).put("option", null).put("key", "Enter").build());
        await(() -> studio.recorder().snapshot().steps().accepted() == 2, "registradas");
        // CHROME-SECURITY-001: a opção escolhida é valor do usuário (OPTION_WITHHELD) — configurada no Studio
        assertEquals(List.of("Selecionar opção no campo Estado", "Pressionar Enter no campo Pesquisa"), names(consulta));
        AutomationStep sel = Edt.call(() -> consulta.steps().get(0));
        assertEquals(ActionType.SELECT_OPTION, sel.actionType());
        assertTrue(sel.parameters().option().isEmpty(), "opção não chega ao modelo");
        List<String> types = new ArrayList<>();
        Edt.run(() -> consulta.steps().forEach(s -> types.add(s.actionType().name())));
        assertEquals(List.of("SELECT_OPTION", "PRESS_KEY"), types);
        Edt.run(() -> studio.recorderPresenter().panel().finishButton().doClick());
        RecorderSnapshot done = null;
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline && (done = studio.recorder().snapshot()).state() != RecorderState.COMPLETED) Thread.sleep(15);
        Edt.flush();
        String summary = StudioFixture.texts(studio.recorderPresenter().panel());
        assertTrue(summary.contains("Gravação concluída") && summary.contains("Etapas registradas: 2"), summary);
        assertTrue(summary.contains("O PageMap não foi exportado"), summary);
    }

    // ------------------------------------------------------------- STUDIO-004: edição × gravação ativa

    @Test
    @DisplayName("S004-047/048 automação em gravação: edição recusada no caso de uso e na tela; Recorder e StepIntake seguem; ao finalizar, volta")
    void s004BloqueioDuranteGravacao() throws Exception {
        Automation a = automation();
        Automation outra = Edt.call(() -> studio.service().createAutomation("Outra automação", null));
        Edt.run(() -> studio.controller().openAutomation(a.id()));
        LogicalSession login = a.sessions().get(0);
        AutomationStep manual = login.steps().get(0);
        String rid = startRecording(login.id());
        String r = route("studio.recording.start");
        assertTrue(studio.service().isLocked(a.id()));
        assertFalse(studio.service().isLocked(outra.id()), "outras automações continuam editáveis");
        com.nekkora.studio.domain.DomainValidationException e = org.junit.jupiter.api.Assertions.assertThrows(
                com.nekkora.studio.domain.DomainValidationException.class,
                () -> Edt.call(() -> {
                    studio.service().moveStep(a.id(), manual.id(), login.id(), 0);
                    return null;
                }));
        assertEquals(com.nekkora.studio.domain.ValidationProblem.AUTOMATION_RECORDING, e.problem(), "bloqueio no caso de uso, não só no botão");
        for (Runnable op : List.<Runnable>of(
                () -> studio.service().removeStep(a.id(), manual.id()),
                () -> studio.service().updateAutomation(a.id(), "Outro nome", ""),
                () -> studio.service().editSession(a.id(), login.id(), "X", "", ""),
                () -> studio.service().duplicateStep(a.id(), manual.id()),
                () -> studio.service().addComment(a.id(), manual.id(), "comentário"),
                () -> studio.service().undo(a.id()))) {
            org.junit.jupiter.api.Assertions.assertThrows(com.nekkora.studio.domain.DomainValidationException.class, () -> Edt.call(() -> {
                op.run();
                return null;
            }));
        }
        assertEquals("Abrir o sistema", manual.name(), "nada mudou");
        Edt.run(() -> studio.service().updateAutomation(outra.id(), "Outra automação editada", ""));
        assertEquals("Outra automação editada", outra.name(), "edição de outra automação durante a gravação");
        Edt.run(() -> studio.controller().openAutomation(a.id()));
        Edt.flush();
        var overview = studio.view().overview(a.id()).orElseThrow();
        assertTrue(Edt.call(() -> overview.lockBanner().isVisible()), "aviso visível");
        assertFalse(Edt.call(overview::isEditable));
        Edt.run(() -> studio.controller().selectStep(a.id(), login.id(), manual.id()));
        String inspector = StudioFixture.texts(studio.view().inspector());
        assertTrue(inspector.contains(com.nekkora.studio.ui.PropertiesPresenter.LOCKED_NOTE), inspector);
        var list = overview.sessionView(login.id()).orElseThrow().stepList();
        Edt.run(() -> {
            Object key = list.getInputMap(JComponent.WHEN_FOCUSED).get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0));
            list.getActionMap().get(key).actionPerformed(new java.awt.event.ActionEvent(list, 0, "delete"));
        }); // Delete na lista: o caso de uso recusa, explica e não pergunta nada
        assertEquals("Edição indisponível durante a gravação", studio.state().lastNotice().orElseThrow().title());
        assertEquals(1, names(login).size());

        // o Recorder continua entregando etapas pelo StepIntake, fora do histórico de edição
        int history = studio.service().history().undoSize(a.id());
        step(rid, 1, r, "FILL", element("TEXT_INPUT", "Usuário", "input", "usuario", false), null);
        await(() -> studio.recorder().snapshot().steps().accepted() == 1, "registrada");
        assertEquals(List.of("Abrir o sistema", "Preencher campo Usuário"), names(login));
        assertEquals(history, studio.service().history().undoSize(a.id()), "etapa do Recorder fora do histórico");

        Edt.run(() -> studio.recorderPresenter().panel().finishButton().doClick());
        await(() -> !studio.service().isLocked(a.id()), "desbloqueada ao finalizar");
        Edt.flush();
        assertTrue(Edt.call(overview::isEditable));
        assertFalse(Edt.call(() -> overview.lockBanner().isVisible()));
        AutomationStep usuario = login.steps().get(1);
        Edt.run(() -> studio.service().moveStep(a.id(), usuario.id(), login.id(), 0));
        assertEquals(List.of("Preencher campo Usuário", "Abrir o sistema"), names(login), "edição disponível depois da gravação");
    }

    @Test
    @DisplayName("S004-054 cancelamento continua descartando só a gravação cancelada, também depois de edições anteriores")
    void s004CancelamentoPreservado() throws Exception {
        Automation a = automation();
        LogicalSession login = a.sessions().get(0);
        Edt.run(() -> studio.service().updateStepBasics(a.id(), login.steps().get(0).id(), "Abrir o sistema de clientes", "", ActionType.NAVIGATE));
        String rid = startRecording(login.id());
        String r = route("studio.recording.start");
        step(rid, 1, r, "FILL", element("TEXT_INPUT", "Usuário", "input", "usuario", false), null);
        await(() -> studio.recorder().snapshot().steps().accepted() == 1, "registrada");
        Edt.run(() -> studio.recorderPresenter().panel().cancelButton().doClick());
        await(() -> studio.recorder().snapshot().state() == RecorderState.CANCELLED && names(login).size() == 1, "descartada");
        assertEquals(List.of("Abrir o sistema de clientes"), names(login), "a etapa manual editada continua");
    }
}
