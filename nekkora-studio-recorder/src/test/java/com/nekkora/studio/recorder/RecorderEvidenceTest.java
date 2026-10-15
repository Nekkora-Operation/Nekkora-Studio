package com.nekkora.studio.recorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.evidence.EvidenceType;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Limitation;
import com.nekkora.studio.domain.evidence.ObservedEvidence;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * STUDIO-005: o Recorder lê as evidências de forma estrita, ordena pela sequência própria, espera a etapa delas, registra
 * no domínio DEPOIS da etapa e só então confirma; negocia evidence/1 com recuo para extensões anteriores.
 */
class RecorderEvidenceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);
    private FakeBrowser browser;
    private RecorderController rec;
    private Automation automation;
    private LogicalSession login;
    private final BrowserTab lab = new BrowserTab("t-AAAAAAAAAA", "Login de teste", "127.0.0.1", TabAccess.LAB, true, false);

    /** Ordem das entregas ao domínio (etapas e evidências, na mesma fila). */
    private final List<String> order = new CopyOnWriteArrayList<>();
    private final List<ObservedEvidence> evidence = new CopyOnWriteArrayList<>();
    private volatile CompletableFuture<Void> stepGate = CompletableFuture.completedFuture(null);
    private volatile boolean rejectEvidence;

    private final StepSink steps = new StepSink() {
        @Override
        public CompletableFuture<Boolean> deliver(AutomationId a, SessionId s, StepDraft d) {
            return stepGate.thenApply(v -> {
                order.add("step:" + d.name());
                return true;
            });
        }

        @Override
        public CompletableFuture<Integer> discardRecording(AutomationId a, String recordingId) {
            order.add("discard");
            return CompletableFuture.completedFuture(1);
        }
    };

    private final EvidenceSink sink = (a, e) -> {
        if (rejectEvidence) {
            return CompletableFuture.completedFuture(false);
        }
        order.add("evidence:" + e.type());
        evidence.add(e);
        return CompletableFuture.completedFuture(true);
    };

    @BeforeEach
    void setUp() {
        browser = new FakeBrowser();
        rec = new RecorderController(browser, clock, new RecorderController.Options(Duration.ofSeconds(30), Duration.ofMillis(100),
                Duration.ofSeconds(5), Duration.ofSeconds(5)), steps, sink);
        automation = Automation.create("Login", "", clock);
        login = automation.addSession("Autenticação", "");
    }

    @AfterEach
    void tearDown() {
        rec.close();
    }

    private static void await(BooleanSupplier c, String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("tempo esgotado: " + what);
            }
            Thread.sleep(10);
        }
    }

    private String start() throws InterruptedException {
        rec.start(new RecordingTarget(automation.id(), login.id()), lab, RecordingMode.CAPTURE_AND_INTERACT);
        await(() -> rec.snapshot().state() == RecorderState.RECORDING, "RECORDING");
        return browser.recordingId;
    }

    private String route() {
        return Json.string(browser.last("studio.recording.start").payload(), "routeRef");
    }

    private void emitStep(String rid, long seq, String name) {
        browser.emit("studio.recording.step", RecordedStepsTest.step(rid, seq, route(), "CLICK",
                RecordedStepsTest.element("BUTTON", name, "button", "b" + seq, false), "CAPTURE_AND_INTERACT"));
    }

    static String eventId(String rid, long stepSeq) {
        return "ev-" + rid.substring(Math.max(0, rid.length() - 8)) + "-" + stepSeq;
    }

    /** Evento studio.recording.evidence no formato do contrato. */
    static Map<String, Object> evidencePayload(String rid, long seq, Long stepSeq, String type, String availability, String text) {
        Map<String, Object> p = new LinkedHashMap<>();
        boolean ambiguous = stepSeq == null;
        p.put("recordingId", rid);
        p.put("evidenceId", "evd-" + rid.substring(Math.max(0, rid.length() - 8)) + "-" + seq);
        p.put("evidenceSequence", seq);
        p.put("eventId", ambiguous ? null : eventId(rid, stepSeq));
        p.put("stepSequence", stepSeq);
        p.put("observedAt", "2026-10-15T12:00:0" + (seq % 10) + ".500Z");
        p.put("type", ambiguous ? "AMBIGUOUS_FEEDBACK" : type);
        p.put("source", "FEEDBACK_OBSERVER");
        p.put("relation", "AFTER_ACTION");
        p.put("confidence", ambiguous ? "AMBIGUOUS" : type.startsWith("OBSERVATION_INCOMPLETE") ? "INCOMPLETE" : "TEMPORALLY_ASSOCIATED");
        p.put("classification", "VISIBLE_ERROR_MESSAGE".equals(type) ? "ERROR_INDICATOR" : null);
        p.put("document", Json.obj().put("frame", "MAIN").put("inShadow", false).put("title", "Login de teste")
                .put("url", "http://127.0.0.1:18765/lab/studio005/login-erro.html").build());
        Map<String, Object> det = new LinkedHashMap<>();
        det.put("region", "alerta do formulário login");
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("availability", availability);
        msg.put("text", text);
        det.put("message", msg);
        det.put("field", null);
        det.put("state", null);
        det.put("navigation", null);
        det.put("signals", List.of("ROLE_ALERT"));
        det.put("candidates", ambiguous ? 2 : 0);
        det.put("signalsObserved", 0);
        p.put("details", det);
        p.put("limitations", "OBSERVATION_INCOMPLETE".equals(type) ? List.of("WINDOW_CUT_BY_PAUSE") : List.of());
        return p;
    }

    private void emitEvidence(String rid, long seq, Long stepSeq, String type) {
        browser.emit("studio.recording.evidence", evidencePayload(rid, seq, stepSeq, type, "NONE", null));
    }

    private List<FakeBrowser.Call> evidenceAcks() {
        return browser.calls.stream().filter(c -> c.type().equals("studio.recording.evidence.ack")).toList();
    }

    @Test
    void s005003InicioPedeStepsEEvidenceQuandoHaDestinoDeEvidencias() throws Exception {
        start();
        assertEquals(List.of("steps/1", "evidence/1"), browser.last("studio.recording.start").payload().get("capabilities"));
        assertTrue(rec.snapshot().evidence().enabled(), "estado de evidências anunciado pela extensão");
    }

    @Test
    void s005005ExtensaoDaStudio003RecusaEvidenceERecuaParaSoEtapas() throws Exception {
        browser.preEvidence = true;
        start();
        assertEquals(2, browser.count("studio.recording.start"));
        assertEquals(List.of("steps/1"), browser.last("studio.recording.start").payload().get("capabilities"));
        assertTrue(rec.snapshot().steps().enabled(), "etapas continuam");
        assertFalse(rec.snapshot().evidence().enabled(), "sem evidências, sem erro para o usuário");
        assertNull(rec.snapshot().problem());
    }

    @Test
    void s005005bExtensaoDaStudio002RecuaAteAGravacaoTecnica() throws Exception {
        browser.legacy = true;
        start();
        assertEquals(3, browser.count("studio.recording.start"), "evidence+steps → steps → nenhuma");
        assertFalse(browser.last("studio.recording.start").payload().containsKey("capabilities"));
        assertFalse(rec.snapshot().steps().enabled());
        assertFalse(rec.snapshot().evidence().enabled());
    }

    @Test
    void s005023EvidenciaQueChegaAntesDaEtapaEsperaEEntraDepoisDela() throws Exception {
        String rid = start();
        emitEvidence(rid, 1, 1L, "VISIBLE_ERROR_MESSAGE"); // a etapa 1 ainda não chegou
        Thread.sleep(150);
        assertTrue(evidence.isEmpty(), "nenhuma etapa fictícia: a evidência espera");
        assertEquals(1, rec.snapshot().evidence().pending());
        assertTrue(evidenceAcks().isEmpty(), "nada confirmado antes do domínio");
        emitStep(rid, 1, "Entrar");
        await(() -> evidence.size() == 1, "entregue depois da etapa");
        assertEquals(List.of("step:Clicar no botão Entrar", "evidence:VISIBLE_ERROR_MESSAGE"), order);
        await(() -> !evidenceAcks().isEmpty() && Json.number(evidenceAcks().get(evidenceAcks().size() - 1).payload(), "upToSequence", -1) == 1,
                "ACK depois do domínio");
        await(() -> rec.snapshot().evidence().accepted() == 1, "contador do Floating Recorder");
        assertEquals(Instant.parse("2026-10-15T12:00:01.500Z"), rec.snapshot().evidence().lastObservedAt());
    }

    @Test
    void s005024E026ForaDeOrdemEReemissaoNaoDuplicam() throws Exception {
        String rid = start();
        emitStep(rid, 1, "Entrar");
        emitEvidence(rid, 2, 1L, "OBSERVATION_COMPLETE");
        emitEvidence(rid, 1, 1L, "VISIBLE_ERROR_MESSAGE");
        await(() -> evidence.size() == 2, "entregues");
        assertEquals(List.of(1L, 2L), evidence.stream().map(ObservedEvidence::sequence).toList(), "ordem da sequência, não da chegada");
        emitEvidence(rid, 1, 1L, "VISIBLE_ERROR_MESSAGE"); // reemissão (sync)
        Thread.sleep(150);
        assertEquals(2, evidence.size(), "repetida não duplica");
    }

    @Test
    void s005025LacunaInformadaPelaRenovacaoPedeReemissaoDasEvidencias() throws Exception {
        String rid = start();
        emitStep(rid, 1, "Entrar");
        emitEvidence(rid, 1, 1L, "VISIBLE_ALERT");
        await(() -> evidence.size() == 1, "1 entregue");
        browser.auto = c -> c.type().equals("studio.recording.renew")
                ? Json.obj().put("recordingId", rid).put("state", "RECORDING").put("expiresInMs", 30000)
                        .put("evidence", Json.obj().put("enabled", true).put("lastSequence", 3).put("ackedSequence", 1).put("pending", 2).put("lost", 0).build())
                        .build()
                : browser.extension(c);
        await(() -> browser.count("studio.recording.evidence.sync") > 0, "reemissão pedida");
        assertEquals(1L, browser.last("studio.recording.evidence.sync").payload().get("afterSequence"));
        assertTrue(rec.snapshot().evidence().waiting());
        emitEvidence(rid, 2, 1L, "LOADING_INDICATOR");
        emitEvidence(rid, 3, 1L, "OBSERVATION_COMPLETE");
        await(() -> evidence.size() == 3, "reemitidas entregues");
    }

    @Test
    void s005021AmbiguaEntraSemEtapaEContaComoNaoAtribuida() throws Exception {
        String rid = start();
        browser.emit("studio.recording.evidence", evidencePayload(rid, 1, null, null, "AVAILABLE", "Atualização concluída."));
        await(() -> evidence.size() == 1, "entregue");
        assertFalse(evidence.get(0).attributed());
        await(() -> rec.snapshot().evidence().unattributed() == 1, "não atribuída contada");
    }

    @Test
    void s005051E052EventoForaDoContratoNaoViraEvidencia() throws Exception {
        String rid = start();
        emitStep(rid, 1, "Entrar");
        Map<String, Object> html = evidencePayload(rid, 1, 1L, "VISIBLE_ALERT", "NONE", null);
        html.put("html", "<div role=alert>x</div>");
        browser.emit("studio.recording.evidence", html);
        Map<String, Object> unknown = evidencePayload(rid, 1, 1L, "VISIBLE_ALERT", "NONE", null);
        unknown.put("type", "BUSINESS_SUCCESS");
        browser.emit("studio.recording.evidence", unknown);
        Map<String, Object> suppressedWithText = evidencePayload(rid, 1, 1L, "VISIBLE_ALERT", "SUPPRESSED", "texto");
        browser.emit("studio.recording.evidence", suppressedWithText);
        Map<String, Object> ambiguousWithStep = evidencePayload(rid, 1, 1L, "AMBIGUOUS_FEEDBACK", "NONE", null);
        browser.emit("studio.recording.evidence", ambiguousWithStep);
        Thread.sleep(200);
        assertTrue(evidence.isEmpty(), "nada fora do contrato entra no domínio");
    }

    @Test
    void s005047QuartaBarreiraSuprimeMensagemSensivelQueEscapou() throws Exception {
        String rid = start();
        emitStep(rid, 1, "Entrar");
        browser.emit("studio.recording.evidence", evidencePayload(rid, 1, 1L, "VISIBLE_ERROR_MESSAGE", "AVAILABLE", "CPF 123.456.789-09 já cadastrado"));
        await(() -> evidence.size() == 1, "entregue");
        ObservedEvidence e = evidence.get(0);
        assertEquals(ObservedEvidence.Message.Availability.SUPPRESSED, e.message().availability());
        assertNull(e.message().text());
        assertTrue(e.limitations().contains(Limitation.MESSAGE_SUPPRESSED));
    }

    @Test
    void s005026RecusadaPeloDominioVaiNoAckComoRecusada() throws Exception {
        String rid = start();
        emitStep(rid, 1, "Entrar");
        rejectEvidence = true;
        emitEvidence(rid, 1, 1L, "VISIBLE_ALERT");
        await(() -> evidenceAcks().stream().anyMatch(c -> c.payload().get("rejected") != null), "recusada informada");
        assertEquals(List.of(1L), evidenceAcks().stream().filter(c -> c.payload().get("rejected") != null).findFirst().orElseThrow().payload().get("rejected"));
        assertEquals(1, rec.snapshot().evidence().rejected());
        assertTrue(rec.snapshot().evidence().partial());
    }

    @Test
    void s005040CanceladaNaoRecebeMaisEvidencias() throws Exception {
        String rid = start();
        emitStep(rid, 1, "Entrar");
        emitEvidence(rid, 1, 1L, "VISIBLE_ALERT");
        await(() -> evidence.size() == 1, "entregue");
        rec.cancel();
        await(() -> rec.snapshot().state() == RecorderState.CANCELLED, "CANCELLED");
        emitEvidence(rid, 2, 1L, "OBSERVATION_COMPLETE");
        Thread.sleep(150);
        assertEquals(1, evidence.size(), "nada de uma gravação cancelada entra no domínio");
        await(() -> order.contains("discard"), "etapas (e as evidências delas) descartadas");
    }

    @Test
    void s005037ContaObservacoesIncompletas() throws Exception {
        String rid = start();
        emitStep(rid, 1, "Entrar");
        emitEvidence(rid, 1, 1L, "OBSERVATION_INCOMPLETE");
        await(() -> rec.snapshot().evidence().incomplete() == 1, "incompleta contada");
        assertTrue(rec.snapshot().evidence().partial());
        assertEquals(EvidenceType.OBSERVATION_INCOMPLETE, evidence.get(0).type());
    }
}
