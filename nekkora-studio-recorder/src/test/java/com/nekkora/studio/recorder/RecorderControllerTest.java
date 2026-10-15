package com.nekkora.studio.recorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.bridge.BridgeCommandException;
import com.nekkora.studio.bridge.BridgeError;
import com.nekkora.studio.bridge.LinkStatus;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.StepPosition;
import com.nekkora.studio.domain.action.ActionType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** RecorderController: estados confirmados, recusas, perda de ligação, eventos do navegador e domínio intacto. */
class RecorderControllerTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);
    private FakeBrowser browser;
    private RecorderController rec;
    private final List<RecorderSnapshot> seen = new CopyOnWriteArrayList<>();
    private Automation automation;
    private LogicalSession login;
    private LogicalSession search;
    private final BrowserTab lab = new BrowserTab("t-AAAAAAAAAA", "Sistema de testes", "127.0.0.1", TabAccess.LAB, true, false);

    @BeforeEach
    void setUp() {
        browser = new FakeBrowser();
        rec = new RecorderController(browser, clock, new RecorderController.Options(Duration.ofSeconds(30), Duration.ofMillis(100),
                Duration.ofSeconds(5), Duration.ofSeconds(5)));
        rec.addListener(seen::add);
        automation = Automation.create("Consulta de clientes", "", clock);
        login = automation.addSession("Autenticação", "");
        search = automation.addSession("Consulta", "");
        automation.appendStep(login.id(), StepDraft.manual("Abrir o sistema", ActionType.NAVIGATE, ""));
        automation.appendStep(login.id(), StepDraft.manual("Preencher usuário", ActionType.FILL, ""));
    }

    @AfterEach
    void tearDown() {
        rec.close();
    }

    private RecorderSnapshot await(Predicate<RecorderSnapshot> p, String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            RecorderSnapshot s = rec.snapshot();
            if (p.test(s)) {
                return s;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("tempo esgotado: " + what + " (estado " + rec.snapshot().state() + ")");
    }

    private RecorderSnapshot awaitState(RecorderState s) throws InterruptedException {
        return await(x -> x.state() == s, s.name());
    }

    private void startRecording() throws InterruptedException {
        rec.start(new RecordingTarget(automation.id(), login.id()), lab, RecordingMode.CAPTURE_AND_INTERACT);
        awaitState(RecorderState.RECORDING);
    }

    @Test
    void gravarSoViraGravandoDepoisDaConfirmacao() throws Exception {
        browser.auto = null; // a extensão ainda não respondeu
        long t0 = System.nanoTime();
        rec.start(new RecordingTarget(automation.id(), login.id()), lab, RecordingMode.CAPTURE_AND_INTERACT);
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 50, "start não bloqueia quem chama (EDT)");
        awaitState(RecorderState.STARTING);
        Thread.sleep(200);
        assertEquals(RecorderState.STARTING, rec.snapshot().state(), "sem confirmação continua STARTING");
        assertEquals(RecordingMode.CAPTURE_AND_INTERACT, rec.snapshot().pendingMode());
        FakeBrowser.Call start = browser.last("studio.recording.start");
        assertEquals("t-AAAAAAAAAA", start.payload().get("tabRef"));
        assertEquals(30000L, start.payload().get("leaseMs"));
        browser.recordingId = Json.string(start.payload(), "recordingId");
        browser.mode = "CAPTURE_AND_INTERACT";
        start.future().complete(browser.result());
        RecorderSnapshot s = awaitState(RecorderState.RECORDING);
        assertEquals(RecordingMode.CAPTURE_AND_INTERACT, s.mode(), "modo confirmado pela extensão");
        assertNull(s.pendingMode());
        assertTrue(s.progress().recorderAttached());
        assertEquals("Sistema de testes", s.tabTitle());
        assertEquals(login.id(), s.session());
        browser.auto = browser::extension;
        await(x -> browser.count("studio.recording.renew") >= 2, "concessão renovada periodicamente");
        List<RecorderState> order = seen.stream().map(RecorderSnapshot::state).distinct().toList();
        assertEquals(List.of(RecorderState.IDLE, RecorderState.STARTING, RecorderState.RECORDING).subList(1, 3), order.subList(order.indexOf(RecorderState.STARTING), order.indexOf(RecorderState.STARTING) + 2));
    }

    @Test
    void paginaNaoAutorizadaRecusadaComOrientacao() throws Exception {
        browser.auto = c -> {
            c.future().completeExceptionally(new BridgeCommandException(BridgeError.ORIGIN_NOT_ALLOWED,
                    "Este site ainda não está autorizado. Abra a Nekkora Extension no Chrome e conceda acesso ao site.", "PERMISSION_REQUIRED", false));
            return null;
        };
        rec.start(new RecordingTarget(automation.id(), login.id()), new BrowserTab("t-BBBBBBBBBB", null, "portal.exemplo.com",
                TabAccess.PERMISSION_REQUIRED, false, false), RecordingMode.CAPTURE_ONLY);
        RecorderSnapshot s = await(x -> x.state() == RecorderState.IDLE && x.problem() != null, "recusa");
        assertEquals(BridgeError.ORIGIN_NOT_ALLOWED, s.problem().code());
        assertTrue(s.problem().message().contains("Abra a Nekkora Extension no Chrome e conceda acesso ao site"));
        assertTrue(rec.recording().isEmpty(), "nenhuma gravação criada");
    }

    @Test
    void semNavegadorConectadoNadaEhEnviado() throws Exception {
        browser.setStatus(LinkStatus.of(LinkStatus.State.WAITING, null));
        rec.start(new RecordingTarget(automation.id(), login.id()), lab, RecordingMode.CAPTURE_ONLY);
        RecorderSnapshot s = await(x -> x.problem() != null, "aviso");
        assertEquals(RecorderState.IDLE, s.state());
        assertEquals(BridgeError.EXTENSION_UNAVAILABLE, s.problem().code());
        assertEquals(0, browser.count("studio.recording.start"));
    }

    @Test
    void pausaConfirmadaRecusadaEModo() throws Exception {
        startRecording();
        browser.auto = null;
        rec.pause();
        awaitState(RecorderState.PAUSING);
        Thread.sleep(100);
        assertEquals(RecorderState.PAUSING, rec.snapshot().state(), "não apresenta pausa sem confirmação");
        FakeBrowser.Call p = browser.last("studio.recording.pause");
        p.future().completeExceptionally(new BridgeCommandException(BridgeError.CAPTURE_FAILED, "Falhou ao liberar.", null, true));
        RecorderSnapshot back = await(x -> x.state() == RecorderState.RECORDING && x.problem() != null, "volta a RECORDING");
        assertEquals(BridgeError.CAPTURE_FAILED, back.problem().code());
        browser.auto = browser::extension;
        rec.pause();
        RecorderSnapshot paused = awaitState(RecorderState.PAUSED);
        assertFalse(paused.progress().recorderAttached(), "consumidor do Recorder liberado");
        rec.setMode(RecordingMode.CAPTURE_ONLY);
        await(x -> x.mode() == RecordingMode.CAPTURE_ONLY && x.pendingMode() == null, "modo confirmado");
        browser.auto = c -> {
            if (c.type().equals("studio.recording.mode")) {
                c.future().completeExceptionally(new BridgeCommandException(BridgeError.INVALID_MODE, "Modo desconhecido.", null, false));
                return null;
            }
            return browser.extension(c);
        };
        rec.setMode(RecordingMode.CAPTURE_AND_INTERACT);
        RecorderSnapshot refused = await(x -> x.problem() != null && x.problem().code() == BridgeError.INVALID_MODE, "modo recusado");
        assertEquals(RecordingMode.CAPTURE_ONLY, refused.mode(), "modo anterior mantido");
        assertNull(refused.pendingMode());
        browser.auto = browser::extension;
        rec.resume();
        assertTrue(awaitState(RecorderState.RECORDING).progress().recorderAttached());
    }

    @Test
    void finalizarSemExportarECancelar() throws Exception {
        startRecording();
        rec.finish();
        RecorderSnapshot done = awaitState(RecorderState.COMPLETED);
        assertNotNull(done.summary());
        assertFalse(done.summary().pageMapExported(), "finalizar não é exportar");
        assertTrue(done.summary().limitations().contains("ACTION_CAPTURE_NOT_AVAILABLE"));
        rec.acknowledge();
        RecorderSnapshot idle = awaitState(RecorderState.IDLE);
        assertNull(idle.summary());
        startRecording();
        rec.cancel();
        assertEquals(RecorderState.CANCELLED, awaitState(RecorderState.CANCELLED).state());
        long renews = browser.count("studio.recording.renew");
        Thread.sleep(300);
        assertEquals(renews, browser.count("studio.recording.renew"), "nenhuma renovação depois do desfecho");
    }

    @Test
    void perdaDaLigacaoDegradaERecuperaPelaConsulta() throws Exception {
        startRecording();
        browser.setStatus(LinkStatus.of(LinkStatus.State.WAITING, "CHROME_DISCONNECTED"));
        RecorderSnapshot d = awaitState(RecorderState.DEGRADED);
        assertTrue(d.linkLost());
        rec.finish(); // sem ligação, nada é dado como finalizado
        Thread.sleep(100);
        assertEquals(RecorderState.DEGRADED, rec.snapshot().state());
        browser.setStatus(new LinkStatus(LinkStatus.State.CONNECTED, null, new LinkStatus.ExtensionInfo("0.4.0", "DEV")));
        await(x -> x.state() == RecorderState.RECORDING, "recuperada");
        assertTrue(browser.count("studio.recording.status") >= 1, "estado real consultado");
    }

    @Test
    void perdaDuranteOInicioReconciliaComOQueONavegadorSabe() throws Exception {
        browser.auto = null;
        rec.start(new RecordingTarget(automation.id(), login.id()), lab, RecordingMode.CAPTURE_ONLY);
        awaitState(RecorderState.STARTING);
        browser.setStatus(LinkStatus.of(LinkStatus.State.WAITING, null));
        awaitState(RecorderState.DEGRADED);
        browser.auto = browser::extension; // a extensão não chegou a registrar a gravação
        browser.auto = c -> {
            if (c.type().equals("studio.recording.status")) {
                c.future().completeExceptionally(new BridgeCommandException(BridgeError.RECORDING_NOT_FOUND, "Gravação inexistente.", null, false));
                return null;
            }
            return browser.extension(c);
        };
        browser.setStatus(new LinkStatus(LinkStatus.State.CONNECTED, null, null));
        RecorderSnapshot s = await(x -> x.state() == RecorderState.IDLE && x.problem() != null, "não iniciada");
        assertEquals("Gravação não iniciada", s.problem().title());
    }

    @Test
    void interrupcaoDaPaginaRetomadaEFinalizacao() throws Exception {
        startRecording();
        browser.state = "DEGRADED";
        Map<String, Object> r = browser.recording();
        r.put("reason", "TAB_CLOSED");
        browser.emit("studio.recording.degraded", Json.obj().put("recordingId", browser.recordingId).put("operationId", null).put("recording", r).build());
        RecorderSnapshot d = awaitState(RecorderState.DEGRADED);
        assertFalse(d.linkLost());
        assertEquals("TAB_CLOSED", d.reason());
        assertTrue(d.problem().message().contains("fechada"));
        browser.auto = c -> {
            if (c.type().equals("studio.recording.resume")) {
                c.future().completeExceptionally(new BridgeCommandException(BridgeError.TAB_NOT_FOUND, "A aba da gravação foi fechada.", null, false));
                return null;
            }
            return browser.extension(c);
        };
        rec.resume();
        RecorderSnapshot again = await(x -> x.state() == RecorderState.DEGRADED && x.problem() != null && x.problem().code() == BridgeError.TAB_NOT_FOUND,
                "retomada recusada");
        assertEquals(RecorderState.DEGRADED, again.state());
        rec.finish();
        assertEquals(RecorderState.COMPLETED, awaitState(RecorderState.COMPLETED).state());
    }

    @Test
    void concessaoExpiradaViraErro() throws Exception {
        startRecording();
        browser.state = "EXPIRED";
        browser.emit("studio.recording.expired", Json.obj().put("recordingId", browser.recordingId).put("operationId", null)
                .put("recording", browser.recording()).build());
        RecorderSnapshot e = awaitState(RecorderState.ERROR);
        assertEquals("Gravação expirada", e.problem().title());
    }

    @Test
    void eventoAntigoNaoRegrideEstado() throws Exception {
        startRecording();
        Map<String, Object> old = browser.recording();
        old.put("state", "PAUSED");
        old.put("updatedAt", Instant.ofEpochMilli(1).toString());
        rec.pause();
        awaitState(RecorderState.PAUSED);
        rec.resume();
        awaitState(RecorderState.RECORDING);
        browser.emit("studio.recording.paused", Json.obj().put("recordingId", browser.recordingId).put("operationId", "op-antiga").put("recording", old).build());
        Thread.sleep(150);
        assertEquals(RecorderState.RECORDING, rec.snapshot().state(), "fotografia antiga ignorada");
    }

    @Test
    void tempoEsgotadoConsultaAVerdade() throws Exception {
        startRecording();
        browser.auto = c -> {
            if (c.type().equals("studio.recording.pause")) {
                browser.state = "PAUSED"; // a extensão pausou, mas a resposta se perdeu
                c.future().completeExceptionally(new BridgeCommandException(BridgeError.TIMEOUT, "sem resposta", null, true));
                return null;
            }
            return browser.extension(c);
        };
        rec.pause();
        awaitState(RecorderState.PAUSED);
        assertTrue(browser.count("studio.recording.status") >= 1, "resultado desconhecido nunca é presumido");
    }

    @Test
    void abandonarSemLigacaoNaoFingeEncerramento() throws Exception {
        startRecording();
        browser.setStatus(LinkStatus.of(LinkStatus.State.WAITING, null));
        awaitState(RecorderState.DEGRADED);
        rec.abandon();
        RecorderSnapshot s = awaitState(RecorderState.ERROR);
        assertEquals("Gravação encerrada sem confirmação", s.problem().title());
        assertNull(s.summary());
    }

    @Test
    void associacaoASessaoSemNovaCaptura() throws Exception {
        startRecording();
        long starts = browser.count("studio.recording.start");
        rec.associate(search.id());
        await(x -> search.id().equals(x.session()), "nova sessão associada");
        assertEquals(starts, browser.count("studio.recording.start"), "nenhuma nova captura");
        TechnicalRecording t = rec.recording().orElseThrow();
        assertEquals(2, t.associations().size());
        assertEquals(login.id(), t.associations().get(0).session());
        assertTrue(t.recordingId().startsWith("rec-"));
        assertFalse(t.recordingId().contains(automation.id().toString()), "identidades independentes");
    }

    @Test
    void s002029ControleDaGravacaoNaoTocaAsEtapas() throws Exception {
        List<StepPosition> before = automation.sequence();
        long revision = automation.revision();
        startRecording();
        rec.pause();
        awaitState(RecorderState.PAUSED);
        rec.setMode(RecordingMode.CAPTURE_ONLY);
        rec.resume();
        awaitState(RecorderState.RECORDING);
        rec.associate(search.id());
        rec.finish();
        awaitState(RecorderState.COMPLETED);
        assertEquals(before, automation.sequence(), "nenhuma etapa artificial, numeração preservada");
        assertEquals(revision, automation.revision(), "domínio não alterado");
    }

    @Test
    void abasDoNavegador() throws Exception {
        List<BrowserTab> tabs = rec.listTabs().get();
        assertEquals(2, tabs.size());
        assertTrue(tabs.get(0).access().allowed());
        assertFalse(tabs.get(1).access().allowed());
        assertEquals("portal.exemplo.com", tabs.get(1).label());
    }

    @Test
    void encerramentoDoStudioFinalizaEEspera() throws Exception {
        startRecording();
        rec.shutdown(true, Duration.ofSeconds(2)).get();
        assertNotNull(browser.last("studio.recording.finish"));
        assertEquals(RecorderState.COMPLETED, rec.snapshot().state());
    }
}
