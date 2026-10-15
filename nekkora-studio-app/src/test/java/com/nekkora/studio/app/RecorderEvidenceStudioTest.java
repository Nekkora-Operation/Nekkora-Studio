package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.app.recorder.RecorderPrompts;
import com.nekkora.studio.app.recorder.RecorderWindowHost;
import com.nekkora.studio.app.workspace.Selection;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.DomainValidationException;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.evidence.EvidenceType;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary;
import com.nekkora.studio.domain.evidence.ObservationSummary;
import com.nekkora.studio.domain.evidence.ObservedEvidence;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.recorder.BrowserTab;
import com.nekkora.studio.recorder.RecorderState;
import com.nekkora.studio.recorder.RecordingMode;
import com.nekkora.studio.ui.form.StepDetailsForm;
import com.nekkora.studio.ui.recorder.RecordingSetupPanel;
import com.nekkora.tokens.NekkoraThemes;
import java.time.Instant;
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
 * STUDIO-005 pela aplicação: evidências observadas chegam do navegador (outra thread), entram pela EvidenceIntake na
 * EDT, enriquecem a etapa sem editá-la, aparecem no painel, na aba Evidências e no Floating Recorder, não entram no
 * histórico e não o invalidam, e somem com o cancelamento.
 */
class RecorderEvidenceStudioTest {

    private static final Pattern INTERNAL = Pattern.compile("rec-[0-9a-f]{8}|tgt-|ev-|evd-|route-|cap-\\d|doc:|\\bn\\d+\\b|op-[0-9a-f]{8}");
    private final FakeBrowser browser = new FakeBrowser();
    private final AtomicInteger exits = new AtomicInteger();
    private final ScriptedPrompts prompts = new ScriptedPrompts();
    private Studio studio;
    private SessionId chosen;

    @BeforeEach
    void setUp() {
        browser.stepsAware = true;
        browser.evidenceAware = true;
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

    private Automation automation(String name) {
        prompts.then(ScriptedPrompts.name(name, null));
        Edt.run(() -> studio.controller().newAutomation());
        prompts.then(ScriptedPrompts.name("Autenticação", null));
        Edt.run(() -> studio.controller().newSession());
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
        await(() -> studio.recorder().snapshot().state() == RecorderState.RECORDING && studio.recorder().snapshot().evidence().enabled(),
                "RECORDING com evidências");
        return browser.recordingId;
    }

    private String route() {
        return Json.string(browser.last("studio.recording.start").payload(), "routeRef");
    }

    private static String eventId(String rid, long seq) {
        return "ev-" + seq + "-" + rid.substring(rid.length() - 6);
    }

    private void step(String rid, long seq, String action, String name) throws InterruptedException {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("recordingId", rid);
        p.put("eventId", eventId(rid, seq));
        p.put("sequence", seq);
        p.put("routeRef", route());
        p.put("observedAt", "2026-10-15T12:00:0" + seq + ".000Z");
        p.put("mode", "CAPTURE_AND_INTERACT");
        p.put("observation", "INTERACTION_OBSERVED");
        p.put("action", action);
        p.put("element", RecorderStepsStudioTest.element("FILL".equals(action) ? "TEXT_INPUT" : "BUTTON", name, "FILL".equals(action) ? "input" : "button",
                name.toLowerCase(java.util.Locale.ROOT), false));
        p.put("parameters", Json.obj().put("valueCaptured", false).put("option", null).put("key", null).build());
        p.put("context", Json.obj().put("url", "http://127.0.0.1:18765/lab/studio005/login-erro.html").put("title", "Login de teste").put("frame", "MAIN").build());
        p.put("evidence", List.of());
        p.put("notes", List.of());
        p.put("events", 2);
        browser.emitFromBridgeThread("studio.recording.step", p);
    }

    private void evidence(String rid, long seq, Long stepSeq, String type, String availability, String text, String... limitations) throws InterruptedException {
        Map<String, Object> p = new LinkedHashMap<>();
        boolean ambiguous = stepSeq == null;
        p.put("recordingId", rid);
        p.put("evidenceId", "evd-" + rid.substring(rid.length() - 6) + "-" + seq);
        p.put("evidenceSequence", seq);
        p.put("eventId", ambiguous ? null : eventId(rid, stepSeq));
        p.put("stepSequence", stepSeq);
        p.put("observedAt", Instant.parse("2026-10-15T12:00:10Z").plusMillis(seq * 250).toString());
        p.put("type", ambiguous ? "AMBIGUOUS_FEEDBACK" : type);
        p.put("source", "FEEDBACK_OBSERVER");
        p.put("relation", "AFTER_ACTION");
        p.put("confidence", ambiguous ? "AMBIGUOUS" : "OBSERVATION_INCOMPLETE".equals(type) ? "INCOMPLETE" : "TEMPORALLY_ASSOCIATED");
        p.put("classification", "VISIBLE_ERROR_MESSAGE".equals(type) ? "ERROR_INDICATOR" : null);
        p.put("document", Json.obj().put("frame", "MAIN").put("inShadow", false).put("title", "Login de teste")
                .put("url", "http://127.0.0.1:18765/lab/studio005/login-erro.html").build());
        Map<String, Object> det = new LinkedHashMap<>();
        det.put("region", "mensagem de erro do formulário login");
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("availability", availability);
        msg.put("text", text);
        det.put("message", msg);
        det.put("field", null);
        det.put("state", null);
        det.put("navigation", null);
        det.put("signals", List.of("ROLE_ALERT"));
        det.put("candidates", ambiguous ? 2 : 0);
        det.put("signalsObserved", type != null && type.startsWith("OBSERVATION_COMPLETE") ? 1 : 0);
        p.put("details", det);
        p.put("limitations", List.of(limitations));
        browser.emitFromBridgeThread("studio.recording.evidence", p);
    }

    private static ObservedEvidence direct(String rid, String eventId, long seq) {
        return new ObservedEvidence("evd-late-" + seq, seq, rid, eventId, Instant.parse("2026-10-15T12:01:00Z"), EvidenceType.VISIBLE_STATUS_MESSAGE,
                EvidenceVocabulary.Source.FEEDBACK_OBSERVER, EvidenceVocabulary.Relation.AFTER_ACTION, EvidenceVocabulary.Confidence.TEMPORALLY_ASSOCIATED,
                EvidenceVocabulary.Classification.STATUS_INDICATOR, null, "status", ObservedEvidence.Message.of("Sessão encerrada."), null, null, null,
                List.of(EvidenceVocabulary.Signal.ROLE_STATUS), 0, 0, List.of());
    }

    @Test
    @DisplayName("S005-010/031/034/040/062 — login com erro: evidências na etapa certa, na EDT, sem editar a etapa; painel, aba e Floating Recorder")
    void s005LoginComErroNoStudio() throws Exception {
        Automation a = automation("Login com erro");
        LogicalSession login = a.sessions().get(0);
        List<String> violations = new CopyOnWriteArrayList<>();
        RepaintManager previous = Edt.call(() -> RepaintManager.currentManager((JComponent) null));
        RepaintManager checking = new RepaintManager() {
            @Override
            public synchronized void addInvalidComponent(JComponent c) {
                if (!SwingUtilities.isEventDispatchThread()) violations.add("invalidate " + c.getClass().getSimpleName());
                super.addInvalidComponent(c);
            }

            @Override
            public void addDirtyRegion(JComponent c, int x, int y, int w, int h) {
                if (!SwingUtilities.isEventDispatchThread()) violations.add("repaint " + c.getClass().getSimpleName());
                super.addDirtyRegion(c, x, y, w, h);
            }
        };
        Edt.run(() -> RepaintManager.setCurrentManager(checking));
        String rid;
        try {
            rid = startRecording(login.id());
            evidence(rid, 1, 3L, "VISIBLE_ERROR_MESSAGE", "AVAILABLE", "Usuário ou senha inválidos."); // antes da etapa
            step(rid, 1, "FILL", "Usuário");
            step(rid, 2, "FILL", "Senha");
            step(rid, 3, "CLICK", "Entrar");
            evidence(rid, 2, 3L, "OBSERVATION_COMPLETE", "NONE", null);
            evidence(rid, 3, 1L, "OBSERVATION_COMPLETE", "NONE", null);
            evidence(rid, 4, null, null, "AVAILABLE", "Atualização concluída.");
            await(() -> studio.recorder().snapshot().evidence().accepted() == 4, "4 evidências registradas");
        } finally {
            Edt.run(() -> RepaintManager.setCurrentManager(previous));
        }
        assertTrue(violations.isEmpty(), "Swing alterado fora da EDT:\n" + String.join("\n", violations));
        AutomationStep entrar = Edt.call(() -> login.steps().get(3));
        assertEquals("Clicar no botão Entrar", entrar.name());
        assertEquals(List.of(EvidenceType.VISIBLE_ERROR_MESSAGE, EvidenceType.OBSERVATION_COMPLETE), entrar.evidence().stream().map(ObservedEvidence::type).toList());
        ObservationSummary summary = entrar.observation();
        assertEquals(ObservationSummary.Status.OBSERVED, summary.status());
        assertTrue(summary.error());
        assertEquals(RecordingObservation.BusinessOutcome.NOT_VALIDATED, summary.businessOutcome());
        assertTrue(entrar.manualRevision().isEmpty(), "evidência não é edição manual");
        assertEquals(1, a.unattributedEvidence().size());
        await(() -> browser.calls.stream().anyMatch(c -> c.type().equals("studio.recording.evidence.ack") && Json.number(c.payload(), "upToSequence", 0) == 4),
                "confirmação depois do domínio");

        Edt.run(() -> studio.service().select(new Selection.OfStep(a.id(), login.id(), entrar.id())));
        Edt.flush();
        String props = StudioFixture.texts(studio.view().inspector());
        for (String expected : List.of("Evidências", "Mudanças observadas", "Mensagem de erro exibida", "“Usuário ou senha inválidos.”", "Indicador de erro",
                "Resultado de negócio", "Não validado", "Observação concluída")) {
            assertTrue(props.contains(expected), "'" + expected + "' ausente em: " + props);
        }
        assertFalse(props.contains("Sucesso") || props.contains("Falha"), "nenhum resultado de negócio inventado");
        String panel = StudioFixture.texts(studio.recorderPresenter().panel());
        assertTrue(panel.contains("4 registradas") && panel.contains("1 sem etapa definida"), "Floating Recorder: " + panel);
        assertTrue(panel.contains("Última observação"), panel);
        String view = StudioFixture.texts(studio.view().component());
        assertTrue(view.contains("Indicador de erro observado"), "selo na lista de etapas: " + view);
        for (String text : List.of(view, panel, props)) {
            assertFalse(INTERNAL.matcher(text).find(), "identificador interno na interface: " + INTERNAL.matcher(text).results().map(m -> m.group()).toList());
        }

        StepDetailsForm form = Edt.call(() -> new StepDetailsForm(a, entrar));
        assertEquals(6, Edt.call(() -> form.tabs().getTabCount()));
        assertEquals("Origem e alterações", Edt.call(() -> form.tabs().getTitleAt(4)), "abas existentes nas mesmas posições");
        assertEquals("Evidências", Edt.call(() -> form.tabs().getTitleAt(StepDetailsForm.EVIDENCE_TAB)), "Evidências é a última aba");
        // as seis abas cabem na largura útil do diálogo grande (760 lógicos − margens = 700), na escala da tela em uso,
        // sem esconder "Geral" atrás das setas de rolagem
        Edt.render(form, com.nekkora.ui.theme.Scale.px(700), com.nekkora.ui.theme.Scale.px(640));
        for (int i = 0; i < 6; i++) {
            int index = i;
            java.awt.Rectangle r = Edt.call(() -> form.tabs().getBoundsAt(index));
            int width = Edt.call(() -> form.tabs().getWidth());
            assertTrue(r != null && r.x >= 0 && r.x + r.width <= width, "aba " + index + " visível sem rolagem: " + r + " em " + width);
        }
        Edt.run(() -> form.tabs().setSelectedIndex(StepDetailsForm.EVIDENCE_TAB));
        String tab = StudioFixture.texts(Edt.call(() -> (JComponent) form.tabs().getComponentAt(StepDetailsForm.EVIDENCE_TAB)));
        assertTrue(tab.contains("Linha do tempo") && tab.contains("Usuário ou senha inválidos.") && tab.contains("Não validado"), tab);
    }

    @Test
    @DisplayName("S005-053/054 — durante a gravação a edição fica bloqueada, mas a recepção de evidências continua (fora do histórico)")
    void s005BloqueioDeEdicaoComRecepcao() throws Exception {
        Automation a = automation("Bloqueio");
        LogicalSession login = a.sessions().get(0);
        String rid = startRecording(login.id());
        step(rid, 1, "CLICK", "Entrar");
        await(() -> studio.recorder().snapshot().steps().accepted() == 1, "etapa");
        assertTrue(studio.service().isLocked(a.id()));
        int history = studio.service().history().undoSize(a.id());
        long revision = Edt.call(a::revision);
        evidence(rid, 1, 1L, "VISIBLE_ALERT", "AVAILABLE", "Atenção");
        await(() -> studio.recorder().snapshot().evidence().accepted() == 1, "evidência aceita durante a gravação");
        assertEquals(history, studio.service().history().undoSize(a.id()), "fora do histórico");
        assertEquals(revision, (long) Edt.call(a::revision), "não conta revisão");
        AutomationStep entrar = Edt.call(() -> login.steps().get(1));
        DomainValidationException e = assertThrows(DomainValidationException.class,
                () -> Edt.call(() -> studio.service().duplicateStep(a.id(), entrar.id())));
        assertEquals(ValidationProblem.AUTOMATION_RECORDING, e.problem(), "edição continua bloqueada");
    }

    @Test
    @DisplayName("S005-050/051/052 — depois da gravação: desfazer/refazer não apagam nem reaplicam evidências; chegada tardia não invalida o histórico; duplicar não copia")
    void s005HistoricoEDuplicacao() throws Exception {
        Automation a = automation("Histórico");
        LogicalSession login = a.sessions().get(0);
        String rid = startRecording(login.id());
        step(rid, 1, "CLICK", "Entrar");
        evidence(rid, 1, 1L, "VISIBLE_ALERT", "AVAILABLE", "Atenção");
        await(() -> studio.recorder().snapshot().evidence().accepted() == 1, "evidência");
        Edt.run(() -> studio.recorderPresenter().panel().finishButton().doClick());
        await(() -> !studio.service().isLocked(a.id()), "desbloqueada");
        AutomationStep entrar = Edt.call(() -> login.steps().get(1));
        Edt.run(() -> studio.service().updateStepBasics(a.id(), entrar.id(), "Entrar no sistema", "", ActionType.CLICK));
        assertTrue(studio.service().canUndo(a.id()));
        // evidência tardia (chega depois da edição): não é edição e não invalida o histórico
        assertTrue(Edt.call(() -> studio.service().attachEvidence(a.id(), direct(rid, eventId(rid, 1), 2))));
        assertTrue(studio.service().canUndo(a.id()), "histórico preservado");
        Edt.run(() -> studio.service().undo(a.id()));
        assertEquals("Clicar no botão Entrar", entrar.name());
        assertEquals(2, entrar.evidence().size(), "desfazer não apaga evidências");
        Edt.run(() -> studio.service().redo(a.id()));
        assertEquals("Entrar no sistema", entrar.name());
        assertEquals(2, entrar.evidence().size(), "refazer não reaplica nem duplica");
        assertFalse(Edt.call(() -> studio.service().attachEvidence(a.id(), direct(rid, eventId(rid, 1), 2))), "repetida: idempotente");

        AutomationStep copy = Edt.call(() -> studio.service().duplicateStep(a.id(), entrar.id()));
        assertTrue(copy.evidence().isEmpty(), "duplicada sem evidências");
        assertEquals(ObservationSummary.Status.NOT_APPLICABLE, copy.observation().status());
        Edt.run(() -> studio.service().moveStep(a.id(), entrar.id(), login.id(), 0));
        assertEquals(2, entrar.evidence().size(), "mover preserva");
    }

    @Test
    @DisplayName("S005-055 (concorrência) — rajada de evidências da thread do Bridge enquanto o usuário desfaz/refaz OUTRA automação")
    void s005ConcorrenciaComHistoricoDeOutraAutomacao() throws Exception {
        Automation outra = automation("Outra");
        LogicalSession os = outra.sessions().get(0);
        AutomationStep manual = Edt.call(() -> os.steps().get(0));
        Edt.run(() -> studio.service().updateStepBasics(outra.id(), manual.id(), "Abrir o portal", "", ActionType.NAVIGATE));
        Automation a = automation("Gravada");
        LogicalSession login = a.sessions().get(0);
        String rid = startRecording(login.id());
        step(rid, 1, "CLICK", "Entrar");
        await(() -> studio.recorder().snapshot().steps().accepted() == 1, "etapa");
        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 60; i++) {
                    evidence(rid, i, 1L, "CONTENT_CHANGED", "NONE", null);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "bridge-burst");
        producer.start();
        for (int i = 0; i < 20; i++) {
            Edt.run(() -> studio.service().undo(outra.id()));
            Edt.run(() -> studio.service().redo(outra.id()));
        }
        producer.join();
        await(() -> studio.recorder().snapshot().evidence().accepted() == 60, "60 evidências");
        AutomationStep entrar = Edt.call(() -> login.steps().get(1));
        assertEquals(60, entrar.evidence().size());
        assertEquals(java.util.stream.LongStream.rangeClosed(1, 60).boxed().toList(), entrar.evidence().stream().map(ObservedEvidence::sequence).toList(),
                "ordem técnica");
        assertEquals("Abrir o portal", manual.name(), "histórico da outra automação íntegro");
        assertTrue(studio.service().canUndo(outra.id()));
    }

    @Test
    @DisplayName("S005-056 — cancelar descarta as etapas da gravação, as evidências delas e as não atribuídas")
    void s005Cancelamento() throws Exception {
        Automation a = automation("Cancelada");
        LogicalSession login = a.sessions().get(0);
        String rid = startRecording(login.id());
        step(rid, 1, "CLICK", "Entrar");
        evidence(rid, 1, 1L, "VISIBLE_ALERT", "AVAILABLE", "Atenção");
        evidence(rid, 2, null, null, "NONE", null);
        await(() -> studio.recorder().snapshot().evidence().accepted() == 2, "evidências");
        Edt.run(() -> studio.recorderPresenter().panel().cancelButton().doClick());
        await(() -> studio.recorder().snapshot().state() == RecorderState.CANCELLED && login.steps().size() == 1, "descartada");
        assertTrue(Edt.call(() -> a.unattributedEvidence().isEmpty()));
    }
}
