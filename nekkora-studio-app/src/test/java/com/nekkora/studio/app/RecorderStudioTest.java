package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.app.recorder.RecorderPrompts;
import com.nekkora.studio.app.recorder.RecorderWindowHost;
import com.nekkora.studio.app.ui.StudioActions;
import com.nekkora.studio.bridge.LinkStatus;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepPosition;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.recorder.BrowserTab;
import com.nekkora.studio.recorder.RecorderSnapshot;
import com.nekkora.studio.recorder.RecorderState;
import com.nekkora.studio.recorder.RecordingMode;
import com.nekkora.studio.recorder.TabAccess;
import com.nekkora.studio.ui.LogicalSessionView;
import com.nekkora.studio.ui.recorder.RecordingSetupPanel;
import com.nekkora.tokens.NekkoraThemes;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** STUDIO-002 pela interface do Studio: Gravar, Floating Recorder, EDT, privacidade, domínio intacto e saída. */
class RecorderStudioTest {

    private final FakeBrowser browser = new FakeBrowser();
    private final AtomicInteger exits = new AtomicInteger();
    private final ScriptedPrompts prompts = new ScriptedPrompts();
    private final ScriptedRecorder recorderPrompts = new ScriptedRecorder();
    private final AtomicReference<RecorderWindowHost> host = new AtomicReference<>();
    private Studio studio;

    /** Diálogos do Recorder roteirizados. */
    static final class ScriptedRecorder implements RecorderPrompts {
        volatile java.util.function.Function<List<BrowserTab>, Optional<RecordingSetupPanel.Choice>> choose = tabs -> Optional.empty();
        volatile CloseDecision close = CloseDecision.CONTINUE;
        volatile ExitDecision exit = ExitDecision.STAY;
        final List<String> asked = new CopyOnWriteArrayList<>();
        SessionId preselected;

        @Override
        public Optional<RecordingSetupPanel.Choice> chooseRecording(Automation automation, SessionId pre, SetupSource source) {
            asked.add("setup");
            preselected = pre;
            try {
                return choose.apply(source.link().connected() ? source.tabs().get(5, java.util.concurrent.TimeUnit.SECONDS) : List.of());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public CloseDecision askClose() {
            asked.add("close");
            return close;
        }

        @Override
        public ExitDecision askExit() {
            asked.add("exit");
            return exit;
        }
    }

    @BeforeEach
    void setUp() {
        Edt.theme(NekkoraThemes.SAKURA_DARK);
        studio = Edt.call(() -> Studio.create(StudioFixture.CLOCK, prompts, exits::incrementAndGet, browser, recorderPrompts, p -> {
            RecorderWindowHost h = RecorderWindowHost.headless();
            host.set(h);
            return h;
        }));
        Edt.render(studio.view().component(), 1440, 900);
    }

    @AfterEach
    void tearDown() {
        Edt.run(studio::dispose);
    }

    private Automation automationWithSessions() {
        prompts.then(ScriptedPrompts.name("Consulta de clientes", null));
        Edt.run(() -> studio.controller().newAutomation());
        for (String s : List.of("Autenticação", "Consulta", "Resultado")) {
            prompts.then(ScriptedPrompts.name(s, null));
            Edt.run(() -> studio.controller().newSession());
        }
        prompts.then(ScriptedPrompts.step("Autenticação", "Preencher usuário", ActionType.FILL));
        Edt.run(() -> studio.controller().newStep(null));
        prompts.then(ScriptedPrompts.step("Consulta", "Pesquisar", ActionType.CLICK));
        Edt.run(() -> studio.controller().newStep(null));
        return studio.state().activeAutomation().orElseThrow();
    }

    private RecorderSnapshot await(Predicate<RecorderSnapshot> p, String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (p.test(studio.recorder().snapshot())) {
                Edt.flush();
                return studio.recorder().snapshot();
            }
            Thread.sleep(10);
        }
        throw new AssertionError("tempo esgotado: " + what + " (" + studio.recorder().snapshot().state() + ")");
    }

    private String recordLabel() {
        return Edt.call(() -> String.valueOf(studio.view().actions().get(StudioActions.RECORD).getValue(Action.NAME)));
    }

    private void chooseFirstAllowedTab(SessionId session, RecordingMode mode) {
        recorderPrompts.choose = tabs -> tabs.stream().filter(t -> t.access().allowed()).findFirst()
                .map(t -> new RecordingSetupPanel.Choice(session, t, mode));
    }

    @Test
    @DisplayName("S002-001 o Studio abre sem Chrome e sem login; Gravar explica como conectar")
    void s002001SemNavegador() {
        Studio offline = Edt.call(() -> Studio.create(StudioFixture.CLOCK, prompts, () -> { }));
        try {
            String status = Edt.call(() -> offline.recorderPresenter().statusItem().getText());
            assertTrue(status.startsWith("Navegador: indisponível"), status);
            assertFalse(Edt.call(() -> offline.view().actions().get(StudioActions.RECORD).isEnabled()));
            prompts.then(ScriptedPrompts.name("A", null));
            Edt.run(() -> offline.controller().newAutomation());
            assertEquals(1, offline.state().automations().size(), "automações continuam funcionando sem o navegador");
        } finally {
            Edt.run(offline::dispose);
        }
        browser.setStatus(LinkStatus.of(LinkStatus.State.WAITING, null));
        Edt.flush();
        String waiting = Edt.call(() -> studio.recorderPresenter().statusItem().getText());
        assertEquals("Navegador: aguardando conexão com a extensão", waiting);
    }

    @Test
    @DisplayName("Gravar → Gravando somente depois da confirmação da extensão; Floating Recorder com estado real")
    void gravarSoMudaComConfirmacao() throws Exception {
        Automation a = automationWithSessions();
        SessionId login = a.sessions().get(0).id();
        Edt.run(() -> studio.controller().selectSession(a.id(), login));
        assertTrue(Edt.call(() -> studio.view().actions().get(StudioActions.RECORD).isEnabled()));
        chooseFirstAllowedTab(login, RecordingMode.CAPTURE_AND_INTERACT);
        browser.auto = c -> c.type().equals("studio.recording.start") ? null : browser.extension(c); // o início ainda não foi confirmado
        Edt.run(() -> studio.view().actions().get(StudioActions.RECORD).actionPerformed(null));
        assertEquals(login, recorderPrompts.preselected, "sessão selecionada pré-escolhida");
        await(s -> s.state() == RecorderState.STARTING, "STARTING");
        Edt.flush();
        assertEquals("Gravar", recordLabel(), "sem confirmação o botão continua Gravar");
        assertTrue(host.get().isShowing(), "Floating Recorder aberto mostrando o início");
        assertEquals("Iniciando…", Edt.call(() -> studio.recorderPresenter().panel().badge().getText()));
        FakeBrowser.Call start = browser.last("studio.recording.start");
        browser.recordingId = Json.string(start.payload(), "recordingId");
        browser.mode = "CAPTURE_AND_INTERACT";
        start.future().complete(browser.result());
        await(s -> s.state() == RecorderState.RECORDING, "RECORDING");
        assertEquals("Gravando", recordLabel());
        assertEquals("Captura técnica ativa", Edt.call(() -> studio.recorderPresenter().panel().badge().getText()));
        String panel = StudioFixture.texts(studio.recorderPresenter().panel());
        assertTrue(panel.contains("Consulta de clientes") && panel.contains("Sistema de testes"), panel);
        assertEquals("Autenticação", Edt.call(() -> studio.recorderPresenter().panel().sessionCombo().getSelectedValue().name()));
        assertFalse(panel.contains("Em execução"), "o estado da gravação não fala em execução de automações");
        assertTrue(panel.contains("Captura de ações: próxima versão"), "limitação explicada");
        assertTrue(panel.contains("Configuração para a próxima etapa"), "modo identificado como configuração");
    }

    @Test
    @DisplayName("Pausar, continuar, modo, nova sessão, finalizar sem exportar e voltar ao Studio")
    void cicloCompletoPeloFloating() throws Exception {
        Automation a = automationWithSessions();
        SessionId login = a.sessions().get(0).id();
        chooseFirstAllowedTab(login, RecordingMode.CAPTURE_ONLY);
        Edt.run(() -> studio.recorderPresenter().record());
        await(s -> s.state() == RecorderState.RECORDING, "RECORDING");
        var panel = studio.recorderPresenter().panel();
        Edt.run(() -> panel.pauseResumeButton().doClick());
        await(s -> s.state() == RecorderState.PAUSED, "PAUSED");
        assertEquals("Continuar", Edt.call(() -> panel.pauseResumeButton().getText()));
        assertEquals("Gravação pausada", recordLabel());
        Edt.run(() -> panel.modeButton(RecordingMode.CAPTURE_AND_INTERACT).doClick());
        await(s -> s.mode() == RecordingMode.CAPTURE_AND_INTERACT && s.pendingMode() == null, "modo confirmado");
        Edt.run(() -> panel.pauseResumeButton().doClick());
        await(s -> s.state() == RecorderState.RECORDING, "RECORDING de novo");
        // Nova sessão pelo Recorder: usa o domínio (mesmo formulário) e associa sem nova captura.
        prompts.then(ScriptedPrompts.name("Exportação", null));
        long starts = browser.count("studio.recording.start");
        Edt.run(() -> panel.newSessionButton().doClick());
        SessionId created = a.sessions().get(a.sessions().size() - 1).id();
        assertEquals("Exportação", a.sessions().get(a.sessions().size() - 1).name());
        await(s -> created.equals(s.session()), "nova sessão associada");
        assertEquals(starts, browser.count("studio.recording.start"));
        Edt.run(() -> panel.finishButton().doClick());
        RecorderSnapshot done = await(s -> s.state() == RecorderState.COMPLETED, "COMPLETED");
        assertFalse(done.summary().pageMapExported());
        assertTrue(Edt.call(panel::showingSummary));
        String summary = StudioFixture.texts(panel);
        assertTrue(summary.contains("O PageMap não foi exportado"), summary);
        assertTrue(summary.contains("Nenhuma etapa foi criada"), summary);
        assertEquals("Gravar", recordLabel());
        StudioFixture.texts(panel);
        Edt.run(() -> {
            for (java.awt.Component c : allButtons(panel)) {
                if (c instanceof javax.swing.AbstractButton b && "Voltar ao Studio".equals(b.getText())) b.doClick();
            }
        });
        await(s -> s.state() == RecorderState.IDLE, "IDLE");
        assertFalse(host.get().isShowing(), "de volta ao Studio");
    }

    private static List<java.awt.Component> allButtons(java.awt.Container c) {
        List<java.awt.Component> out = new ArrayList<>();
        for (java.awt.Component ch : c.getComponents()) {
            out.add(ch);
            if (ch instanceof java.awt.Container ct) out.addAll(allButtons(ct));
        }
        return out;
    }

    @Test
    @DisplayName("S002-007 aba sem autorização: início recusado, explicado no Studio, nada gravado")
    void s002007NaoAutorizada() throws Exception {
        Automation a = automationWithSessions();
        browser.auto = c -> {
            if (c.type().equals("studio.recording.start")) {
                c.future().completeExceptionally(new com.nekkora.studio.bridge.BridgeCommandException(com.nekkora.studio.bridge.BridgeError.ORIGIN_NOT_ALLOWED,
                        "Este site ainda não está autorizado. Abra a Nekkora Extension no Chrome e conceda acesso ao site.", "PERMISSION_REQUIRED", false));
                return null;
            }
            return browser.extension(c);
        };
        recorderPrompts.choose = tabs -> Optional.of(new RecordingSetupPanel.Choice(a.sessions().get(0).id(),
                new BrowserTab("t-BBBBBBBBBB", null, "portal.exemplo.com", TabAccess.PERMISSION_REQUIRED, false, false), RecordingMode.CAPTURE_ONLY));
        Edt.run(() -> studio.recorderPresenter().record());
        await(s -> s.state() == RecorderState.IDLE && browser.count("studio.recording.start") == 1 && s.problem() == null, "recusa tratada");
        Edt.flush();
        var notice = studio.state().lastNotice().orElseThrow();
        assertEquals("Site não autorizado", notice.title());
        assertTrue(notice.message().contains("conceda acesso ao site"));
        assertFalse(host.get().isShowing());
        assertEquals("Gravar", recordLabel());
    }

    @Test
    @DisplayName("S002-023 eventos do navegador chegam de outra thread e a interface só muda na EDT")
    void s002023SomenteNaEdt() throws Exception {
        Automation a = automationWithSessions();
        chooseFirstAllowedTab(a.sessions().get(0).id(), RecordingMode.CAPTURE_ONLY);
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
            Edt.run(() -> studio.recorderPresenter().record());
            await(s -> s.state() == RecorderState.RECORDING, "RECORDING");
            browser.state = "DEGRADED";
            Map<String, Object> r = browser.recording();
            r.put("reason", "TAB_CLOSED");
            browser.emitFromBridgeThread("studio.recording.degraded", Json.obj().put("recordingId", browser.recordingId).put("operationId", null).put("recording", r).build());
            await(s -> s.state() == RecorderState.DEGRADED, "DEGRADED");
            Thread bridge = new Thread(() -> browser.setStatus(LinkStatus.of(LinkStatus.State.WAITING, "CHROME_DISCONNECTED")), "fake-bridge-status");
            bridge.start();
            bridge.join();
            await(s -> s.linkLost(), "ligação perdida");
            Edt.flush();
            assertEquals("Gravação interrompida", recordLabel());
        } finally {
            Edt.run(() -> RepaintManager.setCurrentManager(previous));
        }
        assertTrue(violations.isEmpty(), "Swing alterado fora da EDT:\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("S002-026 IDs internos e credenciais não aparecem na interface")
    void s002026Privacidade() throws Exception {
        Automation a = automationWithSessions();
        chooseFirstAllowedTab(a.sessions().get(0).id(), RecordingMode.CAPTURE_ONLY);
        Edt.run(() -> studio.recorderPresenter().record());
        await(s -> s.state() == RecorderState.RECORDING, "RECORDING");
        Edt.run(() -> studio.recorderPresenter().panel().pauseResumeButton().doClick());
        await(s -> s.state() == RecorderState.PAUSED, "PAUSED");
        String recordingId = browser.recordingId;
        List<String> ops = new ArrayList<>();
        for (FakeBrowser.Call c : browser.calls) if (c.op() != null) ops.add(c.op());
        String text = StudioFixture.texts(studio.view().component()) + StudioFixture.texts(studio.recorderPresenter().panel());
        List<String> secrets = new ArrayList<>(ops);
        secrets.add(recordingId);
        secrets.add("t-AAAAAAAAAA");
        secrets.add(a.id().toString());
        for (var s : a.sessions()) secrets.add(s.id().toString());
        for (String s : secrets) assertFalse(text.contains(s), "identificador técnico visível: " + s);
        assertFalse(text.matches("(?s).*\\brec-[0-9a-f-]{8}.*"));
        assertFalse(text.matches("(?s).*\\bop-[0-9a-f-]{8}.*"));
    }

    @Test
    @DisplayName("S002-029 o controle da gravação não cria nem renumera etapas")
    void s002029DominioIntacto() throws Exception {
        Automation a = automationWithSessions();
        List<StepPosition> before = a.sequence();
        long revision = a.revision();
        chooseFirstAllowedTab(a.sessions().get(1).id(), RecordingMode.CAPTURE_ONLY);
        Edt.run(() -> studio.recorderPresenter().record());
        await(s -> s.state() == RecorderState.RECORDING, "RECORDING");
        var panel = studio.recorderPresenter().panel();
        Edt.run(() -> panel.pauseResumeButton().doClick());
        await(s -> s.state() == RecorderState.PAUSED, "PAUSED");
        Edt.run(() -> panel.pauseResumeButton().doClick());
        await(s -> s.state() == RecorderState.RECORDING, "RECORDING");
        Edt.run(() -> panel.sessionCombo().setSelectedIndex(2));
        await(s -> a.sessions().get(2).id().equals(s.session()), "associação");
        Edt.run(() -> panel.cancelButton().doClick());
        await(s -> s.state() == RecorderState.CANCELLED, "CANCELLED");
        assertEquals(before, a.sequence(), "nenhuma etapa artificial, numeração preservada");
        assertEquals(revision, a.revision());
        assertEquals(2, a.stepCount());
    }

    @Test
    @DisplayName("Seção 20: alteração numa sessão relê só ela e renumera as seguintes, preservando a seleção")
    void atualizacaoIncremental() {
        Automation a = automationWithSessions();
        List<LogicalSessionView> views = Edt.call(() -> studio.view().overview(a.id()).orElseThrow().sessionViews());
        SessionId consulta = a.sessions().get(1).id();
        var selectedStep = a.sessions().get(0).steps().get(0).id();
        Edt.run(() -> studio.controller().selectStep(a.id(), a.sessions().get(0).id(), selectedStep));
        int r0 = views.get(0).refreshCount();
        int r1 = views.get(1).refreshCount();
        int r2 = views.get(2).refreshCount();
        prompts.then(ScriptedPrompts.step("Autenticação", "Preencher senha", ActionType.FILL));
        Edt.run(() -> studio.controller().newStep(a.sessions().get(0).id()));
        assertEquals(r0 + 1, views.get(0).refreshCount(), "a sessão afetada é relida");
        assertEquals(r1, views.get(1).refreshCount(), "as outras sessões não são reconstruídas");
        assertEquals(r2, views.get(2).refreshCount());
        String pesquisar = Edt.call(() -> views.get(1).labelOf(a.sessions().get(1).steps().get(0)));
        assertTrue(pesquisar.startsWith("03"), "a sessão seguinte foi renumerada: " + pesquisar);
        prompts.then(ScriptedPrompts.step("Resultado", "Observar", ActionType.OBSERVE));
        Edt.run(() -> studio.controller().newStep(a.sessions().get(2).id()));
        assertEquals(r1, views.get(1).refreshCount(), "acrescentar no fim não mexe nas anteriores");
        assertEquals(consulta, a.sessions().get(1).id());
    }

    @Test
    @DisplayName("Fechar o Recorder não encerra a gravação em silêncio; sair do Studio finaliza antes")
    void fecharESairComGravacao() throws Exception {
        Automation a = automationWithSessions();
        chooseFirstAllowedTab(a.sessions().get(0).id(), RecordingMode.CAPTURE_ONLY);
        Edt.run(() -> studio.recorderPresenter().record());
        await(s -> s.state() == RecorderState.RECORDING, "RECORDING");
        recorderPrompts.close = RecorderPrompts.CloseDecision.CONTINUE;
        Edt.run(() -> studio.recorderPresenter().closeRequest().run());
        Thread.sleep(100);
        assertEquals(RecorderState.RECORDING, studio.recorder().snapshot().state(), "fechar a janela não para a gravação");
        assertTrue(recorderPrompts.asked.contains("close"));
        Edt.run(() -> studio.recorderPresenter().panel().collapseButton().doClick());
        assertFalse(host.get().isShowing(), "recolhido");
        Edt.run(() -> studio.recorderPresenter().record());
        assertTrue(host.get().isShowing(), "restaurado pelo botão do Studio");
        recorderPrompts.exit = RecorderPrompts.ExitDecision.STAY;
        prompts.thenExit(true); // aviso das alterações em memória (STUDIO-001) e depois a decisão sobre a gravação
        Edt.run(() -> studio.controller().requestExit());
        Thread.sleep(100);
        assertEquals(0, exits.get(), "ficar no Studio");
        assertEquals(RecorderState.RECORDING, studio.recorder().snapshot().state());
        recorderPrompts.exit = RecorderPrompts.ExitDecision.FINISH;
        prompts.thenExit(true);
        Edt.run(() -> studio.controller().requestExit());
        long deadline = System.currentTimeMillis() + 5000;
        while (exits.get() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(20);
        assertEquals(1, exits.get(), "sai depois da confirmação do navegador");
        assertNotNull(browser.last("studio.recording.finish"), "finalização pedida antes de fechar");
        assertEquals(RecorderState.COMPLETED, studio.recorder().snapshot().state());
    }

    @Test
    @DisplayName("S002-025 o Floating Recorder nos quatro temas")
    void s002025QuatroTemas() throws Exception {
        Automation a = automationWithSessions();
        chooseFirstAllowedTab(a.sessions().get(0).id(), RecordingMode.CAPTURE_ONLY);
        Edt.run(() -> studio.recorderPresenter().record());
        await(s -> s.state() == RecorderState.RECORDING, "RECORDING");
        for (var theme : NekkoraThemes.all()) {
            Edt.theme(theme);
            BufferedImage img = Edt.render(studio.recorderPresenter().panel(), 400, 560);
            int bg = img.getRGB(2, 2);
            int distinct = 0;
            for (int y = 0; y < img.getHeight(); y += 7) for (int x = 0; x < img.getWidth(); x += 7) if (img.getRGB(x, y) != bg) distinct++;
            assertTrue(distinct > 150, theme.id() + ": painel renderizado com conteúdo (" + distinct + ")");
        }
        Edt.theme(NekkoraThemes.SAKURA_DARK);
    }
}
