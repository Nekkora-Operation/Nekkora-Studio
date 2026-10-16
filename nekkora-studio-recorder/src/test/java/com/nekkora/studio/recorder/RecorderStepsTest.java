package com.nekkora.studio.recorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.bridge.BridgeCommandException;
import com.nekkora.studio.bridge.BridgeError;
import com.nekkora.studio.bridge.LinkStatus;
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
        // CHROME-SECURITY-001: nomes do vocabulário controlado (letras soltas como "B" não são metadado de interface e
        // sairiam vazias), para a ORDEM continuar verificável pelo nome
        emitStep(rid, 1, r1, "CLICK", "Entrar");
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
        emitStep(rid, 2, r1, "CLICK", "Salvar");
        emitStep(rid, 3, r1, "CLICK", "Voltar");
        await(() -> delivered.size() == 3, "reemitidas entregues em ordem");
        assertEquals(List.of("Clicar no botão Entrar", "Clicar no botão Salvar", "Clicar no botão Voltar"), delivered.stream().map(Delivered::name).toList());
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

    // ------------------------------------------------------------- revisão técnica: troca de sessão confirmada

    /** A extensão segura a resposta de studio.recording.route (o teste decide o desfecho). */
    private void holdRoutes() {
        browser.auto = c -> c.type().equals("studio.recording.route") ? null : browser.extension(c);
    }

    private List<FakeBrowser.Call> routeCalls() {
        return browser.calls.stream().filter(c -> c.type().equals("studio.recording.route")).toList();
    }

    @Test
    void revTrocaDeSessaoSoValeDepoisDaConfirmacaoDaExtensao() throws Exception {
        String rid = startRecording();
        String r1 = route(browser.last("studio.recording.start"));
        holdRoutes();
        rec.associate(consulta.id());
        await(() -> routeCalls().size() == 1, "rota pedida à extensão");
        Thread.sleep(100);
        assertEquals(login.id(), rec.snapshot().session(), "sem confirmação, a sessão apresentada continua a anterior");
        assertEquals(1, rec.recording().orElseThrow().associations().size(), "nenhuma associação registrada antes da confirmação");
        assertEquals(consulta.id(), rec.snapshot().pendingSession(), "a interface sabe que a troca aguarda confirmação");
        emitStep(rid, 1, r1, "FILL", "Senha"); // da sessão anterior, chegando durante a troca
        await(() -> delivered.size() == 1, "entregue");
        assertEquals(login.id(), delivered.get(0).session());
        FakeBrowser.Call call = routeCalls().get(0);
        call.future().complete(browser.result());
        await(() -> consulta.id().equals(rec.snapshot().session()), "confirmada: sessão nova");
        assertEquals(null, rec.snapshot().pendingSession());
        assertEquals(2, rec.recording().orElseThrow().associations().size());
        String r2 = route(call);
        emitStep(rid, 2, r2, "CLICK", "Clientes");
        emitStep(rid, 3, r1, "CLICK", "Entrar"); // atrasada da rota anterior, depois da confirmação
        await(() -> delivered.size() == 3, "entregues");
        assertEquals(consulta.id(), delivered.get(1).session());
        assertEquals(login.id(), delivered.get(2).session(), "evento atrasado da sessão anterior fica na anterior");
    }

    @Test
    void revTrocaDeSessaoRecusadaMantemAAnteriorEInformaOUsuario() throws Exception {
        String rid = startRecording();
        String r1 = route(browser.last("studio.recording.start"));
        holdRoutes();
        rec.associate(consulta.id());
        await(() -> routeCalls().size() == 1, "rota pedida");
        routeCalls().get(0).future().completeExceptionally(new BridgeCommandException(BridgeError.CAPTURE_FAILED,
                "A página não confirmou a troca de sessão. As etapas continuam na sessão anterior.", "SENSOR_NOT_CONFIRMED", false));
        await(() -> rec.snapshot().problem() != null, "usuário informado");
        await(() -> rec.snapshot().state() == RecorderState.RECORDING, "estado real consultado na extensão");
        assertEquals(login.id(), rec.snapshot().session(), "recusa explícita: a associação anterior continua");
        assertEquals(1, rec.recording().orElseThrow().associations().size());
        assertEquals(null, rec.snapshot().pendingSession(), "nada pendente depois da recusa");
        assertEquals("Sessão não alterada", rec.snapshot().problem().title());
        assertEquals(RecorderState.RECORDING, rec.snapshot().state());
        Thread.sleep(150);
        assertEquals(1, routeCalls().size(), "recusa explícita não é repetida");
        emitStep(rid, 1, r1, "CLICK", "Entrar");
        await(() -> delivered.size() == 1, "entregue");
        assertEquals(login.id(), delivered.get(0).session());
    }

    @Test
    void revTrocaDeSessaoComTimeoutReconciliaComAMesmaOperacao() throws Exception {
        startRecording();
        holdRoutes();
        rec.associate(consulta.id());
        await(() -> routeCalls().size() == 1, "rota pedida");
        FakeBrowser.Call first = routeCalls().get(0);
        first.future().completeExceptionally(new BridgeCommandException(BridgeError.TIMEOUT, "sem resposta", null, true));
        Thread.sleep(50);
        assertEquals(login.id(), rec.snapshot().session(), "resultado desconhecido não é sucesso");
        assertEquals(consulta.id(), rec.snapshot().pendingSession(), "nem falha: continua pendente");
        await(() -> routeCalls().size() >= 2, "reconciliação: o mesmo pedido é refeito");
        FakeBrowser.Call second = routeCalls().get(1);
        assertEquals(first.op(), second.op(), "mesma operação (idempotente na extensão)");
        assertEquals(route(first), route(second));
        assertEquals(login.id(), rec.snapshot().session());
        second.future().complete(browser.result());
        await(() -> consulta.id().equals(rec.snapshot().session()), "confirmada na reconciliação");
    }

    @Test
    void revTrocaDeSessaoDuranteDesconexaoReconciliaAoVoltar() throws Exception {
        startRecording();
        holdRoutes();
        rec.associate(consulta.id());
        await(() -> routeCalls().size() == 1, "rota pedida");
        String op = routeCalls().get(0).op();
        browser.setStatus(LinkStatus.of(LinkStatus.State.WAITING, "CHROME_DISCONNECTED"));
        await(() -> rec.snapshot().linkLost(), "ligação perdida");
        assertEquals(login.id(), rec.snapshot().session(), "sem confirmação: sessão anterior");
        assertEquals(consulta.id(), rec.snapshot().pendingSession());
        browser.auto = browser::extension; // a extensão volta e responde
        browser.setStatus(new LinkStatus(LinkStatus.State.CONNECTED, null, new LinkStatus.ExtensionInfo("0.4.0", "DEV")));
        await(() -> consulta.id().equals(rec.snapshot().session()), "reconciliada ao voltar");
        assertTrue(routeCalls().stream().skip(1).allMatch(c -> c.op().equals(op)), "o mesmo pedido, sem nova operação");
    }

    // ------------------------------------------------- revisão técnica: sensor sem confirmação (modo, retomada)

    private static BridgeCommandException sensorNotConfirmed(String message) {
        return new BridgeCommandException(BridgeError.CAPTURE_FAILED, message, "SENSOR_NOT_CONFIRMED", false);
    }

    /** A extensão degradou (sensor sem confirmação) e avisa por evento, antes da resposta do comando. */
    private void extensionDegraded(String rid) {
        browser.state = "DEGRADED";
        browser.reason = "SENSOR_NOT_CONFIRMED";
        browser.emit("studio.recording.degraded", Json.obj().put("recordingId", rid).put("operationId", null).put("recording", browser.recording()).build());
    }

    @Test
    void revModoSemConfirmacaoDoSensorMantemOModoAnteriorEExplica() throws Exception {
        startRecording();
        browser.auto = c -> c.type().equals("studio.recording.mode") ? null : browser.extension(c);
        rec.setMode(RecordingMode.CAPTURE_ONLY);
        await(() -> browser.count("studio.recording.mode") == 1, "modo pedido");
        browser.last("studio.recording.mode").future().completeExceptionally(
                sensorNotConfirmed("A página não confirmou a troca de modo. A gravação continua no modo anterior."));
        await(() -> rec.snapshot().pendingMode() == null && rec.snapshot().problem() != null, "recusa aplicada");
        await(() -> rec.snapshot().state() == RecorderState.RECORDING, "estado confirmado pela extensão");
        assertEquals(RecordingMode.CAPTURE_AND_INTERACT, rec.snapshot().mode(), "o modo apresentado é o ativo na página");
        assertEquals("Modo não alterado", rec.snapshot().problem().title());
    }

    @Test
    void revModoSemConfirmacaoNemRestauracaoFicaInterrompido() throws Exception {
        String rid = startRecording();
        browser.auto = c -> c.type().equals("studio.recording.mode") ? null : browser.extension(c);
        rec.setMode(RecordingMode.CAPTURE_ONLY);
        await(() -> browser.count("studio.recording.mode") == 1, "modo pedido");
        extensionDegraded(rid);
        browser.last("studio.recording.mode").future().completeExceptionally(
                sensorNotConfirmed("A página não confirmou a troca de modo nem o modo anterior. A captura de ações foi interrompida."));
        await(() -> rec.snapshot().state() == RecorderState.DEGRADED && rec.snapshot().pendingMode() == null, "interrupção declarada");
        Thread.sleep(150);
        assertEquals(RecorderState.DEGRADED, rec.snapshot().state(), "nunca volta a parecer gravando");
        assertEquals(RecordingMode.CAPTURE_AND_INTERACT, rec.snapshot().mode());
        assertEquals("Captura interrompida", rec.snapshot().problem().title());
        assertTrue(rec.snapshot().problem().message().contains("sensores de interação"), rec.snapshot().problem().message());
    }

    @Test
    void revRetomadaSemConfirmacaoDoSensorNaoVoltaAPausaFalsa() throws Exception {
        String rid = startRecording();
        rec.pause();
        await(() -> rec.snapshot().state() == RecorderState.PAUSED, "PAUSED");
        browser.auto = c -> c.type().equals("studio.recording.resume") ? null : browser.extension(c);
        rec.resume();
        await(() -> browser.count("studio.recording.resume") == 1, "retomada pedida");
        extensionDegraded(rid);
        browser.last("studio.recording.resume").future().completeExceptionally(
                sensorNotConfirmed("Os sensores de interação não confirmaram a retomada na página. A captura de ações continua interrompida."));
        await(() -> rec.snapshot().state() == RecorderState.DEGRADED, "estado real: interrompida");
        Thread.sleep(150);
        assertEquals(RecorderState.DEGRADED, rec.snapshot().state(), "a recusa não devolve a uma pausa que a extensão não tem");
    }
}
