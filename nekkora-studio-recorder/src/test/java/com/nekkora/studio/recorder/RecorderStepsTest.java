package com.nekkora.studio.recorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepDraft;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** STUDIO-003: o Recorder entrega etapas ao domínio em ordem, confirma só depois e roteia pela sessão lógica certa. */
class RecorderStepsTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);
    private FakeBrowser browser;
    private RecorderController rec;
    private Automation automation;
    private LogicalSession login;
    private LogicalSession consulta;
    private final BrowserTab lab = new BrowserTab("t-AAAAAAAAAA", "Login de teste", "127.0.0.1", TabAccess.LAB, true, false);

    /** Domínio falso: registra as entregas; recusa etapas cujo nome contenha "RECUSAR". */
    record Delivered(AutomationId automation, SessionId session, String name) {
    }

    private final List<Delivered> delivered = new CopyOnWriteArrayList<>();
    private final List<String> discarded = new CopyOnWriteArrayList<>();
    private volatile CompletableFuture<Void> gate = CompletableFuture.completedFuture(null);

    private final StepSink sink = new StepSink() {
        @Override
        public CompletableFuture<Boolean> deliver(AutomationId a, SessionId s, StepDraft d) {
            return gate.thenApply(v -> {
                if (d.name().contains("RECUSAR")) {
                    return false;
                }
                delivered.add(new Delivered(a, s, d.name()));
                return true;
            });
        }

        @Override
        public CompletableFuture<Integer> discardRecording(AutomationId a, String recordingId) {
            discarded.add(recordingId);
            return CompletableFuture.completedFuture(1);
        }
    };

    @BeforeEach
    void setUp() {
        browser = new FakeBrowser();
        rec = new RecorderController(browser, clock, new RecorderController.Options(Duration.ofSeconds(30), Duration.ofMillis(100),
                Duration.ofSeconds(5), Duration.ofSeconds(5)), sink);
        automation = Automation.create("Consulta de clientes", "", clock);
        login = automation.addSession("Autenticação", "");
        consulta = automation.addSession("Consulta", "");
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

    private String startRecording() throws InterruptedException {
        rec.start(new RecordingTarget(automation.id(), login.id()), lab, RecordingMode.CAPTURE_AND_INTERACT);
        await(() -> rec.snapshot().state() == RecorderState.RECORDING, "RECORDING");
        return browser.recordingId;
    }

    private String route(FakeBrowser.Call c) {
        return Json.string(c.payload(), "routeRef");
    }

    private void emitStep(String rid, long seq, String route, String action, String name) {
        browser.emit("studio.recording.step", RecordedStepsTest.step(rid, seq, route, action,
                RecordedStepsTest.element("BUTTON", name, "button", "b" + seq, false), "CAPTURE_AND_INTERACT"));
    }

    private List<Long> acks() {
        return browser.calls.stream().filter(c -> c.type().equals("studio.recording.steps.ack")).map(c -> Json.number(c.payload(), "upToSequence", -1)).toList();
    }

    @Test
    void s003034InicioNegociaEtapasEEntregaEmOrdemSoConfirmandoDepoisDoDominio() throws Exception {
        String rid = startRecording();
        FakeBrowser.Call start = browser.last("studio.recording.start");
        assertEquals(List.of("steps/1"), start.payload().get("capabilities"), "capacidade negociada");
        String r1 = route(start);
        gate = new CompletableFuture<>(); // o domínio ainda não processou
        emitStep(rid, 2, r1, "CLICK", "Entrar");     // chega fora de ordem
        emitStep(rid, 1, r1, "FILL", "Usuário");
        Thread.sleep(150);
        assertTrue(acks().isEmpty(), "nenhuma confirmação antes do domínio aceitar");
        assertEquals(2, rec.snapshot().steps().pending());
        assertEquals(0, rec.snapshot().steps().accepted(), "nada apresentado como registrado antes do domínio");
        gate.complete(null);
        await(() -> delivered.size() == 2, "entregues");
        assertEquals(List.of("Preencher campo Usuário", "Clicar no botão Entrar"), delivered.stream().map(Delivered::name).toList(),
                "ordem da sequência, não da chegada");
        assertTrue(delivered.stream().allMatch(d -> d.session().equals(login.id()) && d.automation().equals(automation.id())));
        await(() -> !acks().isEmpty() && acks().get(acks().size() - 1) == 2L, "ACK cumulativo até 2");
        await(() -> rec.snapshot().steps().accepted() == 2, "contagem do Floating Recorder");
        emitStep(rid, 2, r1, "CLICK", "Entrar"); // reemissão
        Thread.sleep(150);
        assertEquals(2, delivered.size(), "repetida não duplica");
    }

    @Test
    void s003RecusadaPeloDominioNaoContaComoRegistradaEVaiNoAck() throws Exception {
        String rid = startRecording();
        String r1 = route(browser.last("studio.recording.start"));
        emitStep(rid, 1, r1, "CLICK", "RECUSAR");
        emitStep(rid, 2, r1, "CLICK", "Entrar");
        await(() -> rec.snapshot().steps().accepted() == 1 && rec.snapshot().steps().rejected() == 1, "1 aceita e 1 recusada");
        await(() -> browser.calls.stream().anyMatch(c -> c.type().equals("studio.recording.steps.ack") && c.payload().get("rejected") != null),
                "recusada informada à extensão");
        FakeBrowser.Call ack = browser.calls.stream().filter(c -> c.type().equals("studio.recording.steps.ack") && c.payload().get("rejected") != null)
                .findFirst().orElseThrow();
        assertEquals(List.of(1L), ack.payload().get("rejected"));
        assertTrue(rec.snapshot().steps().hasDeliveryProblem());
    }

    @Test
    void s003033TrocaDeSessaoUsaRotaNovaEAtrasadoDaRotaAntigaFicaNaSessaoAntiga() throws Exception {
        String rid = startRecording();
        String r1 = route(browser.last("studio.recording.start"));
        rec.associate(consulta.id());
        await(() -> browser.calls.stream().anyMatch(c -> c.type().equals("studio.recording.route")), "rota pedida à extensão");
        String r2 = route(browser.last("studio.recording.route"));
        assertFalse(r1.equals(r2));
        emitStep(rid, 1, r1, "FILL", "Senha");    // consolidada antes da troca (chega depois)
        emitStep(rid, 2, r2, "CLICK", "Clientes");
        await(() -> delivered.size() == 2, "entregues");
        assertEquals(login.id(), delivered.get(0).session(), "evento atrasado da sessão anterior não cai na nova");
        assertEquals(consulta.id(), delivered.get(1).session());
    }

    @Test
    void s003030LacunaDetectadaPelaRenovacaoPedeReemissao() throws Exception {
        String rid = startRecording();
        String r1 = route(browser.last("studio.recording.start"));
        emitStep(rid, 1, r1, "CLICK", "A");
        await(() -> delivered.size() == 1, "1 entregue");
        // a extensão emitiu 3 etapas, mas 2 e 3 se perderam no canal (ex.: Studio reconectado)
        browser.auto = c -> c.type().equals("studio.recording.renew")
                ? Json.obj().put("recordingId", rid).put("state", "RECORDING").put("expiresInMs", 30000)
                        .put("steps", Json.obj().put("enabled", true).put("lastSequence", 3).put("ackedSequence", 1).put("pending", 2).put("lost", 0).build()).build()
                : browser.extension(c);
        await(() -> browser.calls.stream().anyMatch(c -> c.type().equals("studio.recording.steps.sync")), "reemissão pedida");
        FakeBrowser.Call sync = browser.last("studio.recording.steps.sync");
        assertEquals(1L, sync.payload().get("afterSequence"));
        assertTrue(rec.snapshot().steps().waiting(), "a interface mostra que faltam etapas");
        emitStep(rid, 2, r1, "CLICK", "B");
        emitStep(rid, 3, r1, "CLICK", "C");
        await(() -> delivered.size() == 3, "reemitidas entregues em ordem");
        assertEquals(List.of("Clicar no botão A", "Clicar no botão B", "Clicar no botão C"), delivered.stream().map(Delivered::name).toList());
    }

    @Test
    void s003038CancelamentoConfirmadoDescartaAsEtapasDaGravacaoUmaVez() throws Exception {
        String rid = startRecording();
        String r1 = route(browser.last("studio.recording.start"));
        emitStep(rid, 1, r1, "CLICK", "A");
        await(() -> delivered.size() == 1, "entregue");
        rec.cancel();
        await(() -> rec.snapshot().state() == RecorderState.CANCELLED, "CANCELLED");
        await(() -> discarded.size() == 1, "descarte");
        assertEquals(List.of(rid), discarded);
        emitStep(rid, 2, r1, "CLICK", "B");
        Thread.sleep(150);
        assertEquals(1, delivered.size(), "nada de uma gravação cancelada entra no domínio");
    }

    @Test
    void s003EtapaDeOutraGravacaoOuDeControladorAntigoEhIgnorada() throws Exception {
        startRecording();
        String r1 = route(browser.last("studio.recording.start"));
        emitStep("rec-antiga", 1, r1, "CLICK", "Intruso");
        Thread.sleep(150);
        assertTrue(delivered.isEmpty());
        assertTrue(acks().isEmpty());
    }

    @Test
    void s003046SemDestinoDeEtapasOStudioNaoPedeACapacidade() throws Exception {
        rec.close();
        rec = new RecorderController(browser, clock, new RecorderController.Options(Duration.ofSeconds(30), Duration.ofMillis(100),
                Duration.ofSeconds(5), Duration.ofSeconds(5)));
        startRecording();
        Map<String, Object> p = browser.last("studio.recording.start").payload();
        assertFalse(p.containsKey("capabilities"), "gravação só técnica (comportamento STUDIO-002)");
        assertFalse(rec.snapshot().steps().enabled());
    }
}
