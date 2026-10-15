package com.nekkora.studio.recorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.bridge.BridgeCommandException;
import com.nekkora.studio.bridge.BridgeError;
import com.nekkora.studio.bridge.BridgeServer;
import com.nekkora.studio.bridge.LinkStatus;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.LogicalSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Teste CRUZADO entre os repositórios: o BridgeServer e o RecorderController REAIS do Studio falando com o processo REAL
 * do Native Host da extensão ({@code nekkora-native-host.jar}, quando o repositório irmão está compilado ao lado). O
 * teste faz o papel do Chrome/extensão pelo stdio (Native Messaging, 32 bits little-endian + JSON).
 */
class RealNativeHostBridgeTest {

    private static final Path JAR = Path.of("..", "..", "Nekkora-Extension-Chrome", "java-core", "native-host", "target", "nekkora-native-host.jar");

    @TempDir
    Path tmp;
    private Process host;
    private OutputStream toHost;
    private BridgeServer server;
    private RecorderController recorder;
    private final List<Map<String, Object>> fromHost = new CopyOnWriteArrayList<>();
    private volatile String extState = "RECORDING";
    private volatile String extMode = "CAPTURE_ONLY";
    private volatile String recordingId;
    private volatile boolean leakUrl;
    private long clock;

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeTrue(Files.exists(JAR), "nekkora-native-host.jar não compilado ao lado (Nekkora-Extension-Chrome)");
        server = BridgeServer.start(new BridgeServer.Options(tmp.resolve("bridge"), BridgeServer.Transport.AUTO, Duration.ofSeconds(5), "cross-repo"));
        recorder = new RecorderController(server, Clock.systemUTC(), new RecorderController.Options(Duration.ofSeconds(30), Duration.ofMillis(500),
                Duration.ofSeconds(10), Duration.ofSeconds(20)));
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", JAR.toAbsolutePath().toString(),
                "chrome-extension://teste-cruzado/");
        pb.environment().put("NEKKORA_BRIDGE_DIR", tmp.resolve("bridge").toString());
        pb.environment().put("NEKKORA_DATA_DIR", tmp.resolve("host-data").toString());
        pb.redirectError(tmp.resolve("host-stderr.txt").toFile());
        host = pb.start();
        toHost = host.getOutputStream();
        Thread reader = new Thread(() -> readLoop(host.getInputStream()), "chrome-simulado");
        reader.setDaemon(true);
        reader.start();
        send(Json.obj().put("protocol", "nekkora-nm/1").put("id", "x1").put("type", "studio.link.configure")
                .put("payload", Json.obj().put("enabled", true).put("extension", Json.obj().put("version", "0.4.0").put("buildMode", "DEV").build()).build()).build());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (recorder != null) recorder.close();
        if (host != null) {
            host.getOutputStream().close();
            host.waitFor(10, TimeUnit.SECONDS);
            host.destroyForcibly();
        }
        if (server != null) server.close();
    }

    private synchronized void send(Map<String, Object> msg) throws IOException {
        byte[] body = Json.write(msg).getBytes(StandardCharsets.UTF_8);
        toHost.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.length).array());
        toHost.write(body);
        toHost.flush();
    }

    private void readLoop(InputStream in) {
        try {
            while (true) {
                byte[] h = in.readNBytes(4);
                if (h.length < 4) return;
                int n = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN).getInt();
                Map<String, Object> msg = Json.parseObject(new String(in.readNBytes(n), StandardCharsets.UTF_8));
                fromHost.add(msg);
                if ("studio.command".equals(msg.get("type"))) answer(msg);
            }
        } catch (IOException | RuntimeException e) {
            // fim
        }
    }

    /** A extensão simulada: estado autoritativo mínimo, payloads no formato do contrato. */
    private void answer(Map<String, Object> cmd) throws IOException {
        String type = Json.string(cmd, "command");
        Map<String, Object> p = Json.object(cmd, "payload");
        Map<String, Object> payload;
        switch (type) {
            case "browser.tabs.list" -> {
                Map<String, Object> tab = new java.util.LinkedHashMap<>(Json.obj().put("tabRef", "t-Lab0000001").put("title", "Sistema de testes").put("host", "127.0.0.1")
                        .put("access", "LAB").put("active", true).put("recording", false).build());
                if (leakUrl) tab.put("url", "http://127.0.0.1/?token=VAZOU");
                payload = Json.obj().put("tabs", List.of(tab)).put("truncated", false).build();
            }
            case "studio.recording.start" -> {
                recordingId = Json.string(p, "recordingId");
                extMode = Json.string(p, "mode");
                extState = "RECORDING";
                payload = Json.obj().put("recording", recording()).put("duplicate", false).build();
            }
            case "studio.recording.pause" -> {
                extState = "PAUSED";
                payload = Json.obj().put("recording", recording()).put("duplicate", false).build();
            }
            case "studio.recording.resume" -> {
                extState = "RECORDING";
                payload = Json.obj().put("recording", recording()).put("duplicate", false).build();
            }
            case "studio.recording.mode" -> {
                extMode = Json.string(p, "mode");
                payload = Json.obj().put("recording", recording()).put("duplicate", false).build();
            }
            case "studio.recording.finish", "studio.recording.cancel" -> {
                extState = type.endsWith("finish") ? "COMPLETED" : "CANCELLED";
                payload = Json.obj().put("recording", recording()).put("summary", Json.obj().put("state", extState).put("durationMs", 1200)
                        .put("counters", Json.obj().put("segments", 2).put("technicalCaptures", 2).put("synchronizations", 5).put("recoveries", 0).build())
                        .put("coverage", Json.obj().put("state", "COMPLETE").put("framesAttached", 0).put("framesUnavailable", 0).build())
                        .put("pageMapExported", false).put("actionCapture", "NOT_AVAILABLE").put("limitations", List.of("ACTION_CAPTURE_NOT_AVAILABLE")).build())
                        .put("duplicate", false).build();
            }
            case "studio.recording.status" -> payload = Json.obj().put("recordings", List.of(recording())).build();
            case "studio.recording.renew" -> payload = Json.obj().put("recordingId", recordingId).put("state", extState).put("expiresInMs", 30000).build();
            default -> payload = Map.of();
        }
        send(Json.obj().put("protocol", "nekkora-nm/1").put("type", "studio.reply").put("id", Json.string(cmd, "id")).put("ok", true)
                .put("payload", payload).put("error", null).build());
    }

    private Map<String, Object> recording() {
        clock += 1000;
        boolean attached = "RECORDING".equals(extState);
        return Json.obj().put("recordingId", recordingId).put("state", extState).put("mode", extMode)
                .put("tab", Json.obj().put("tabRef", "t-Lab0000001").put("title", "Sistema de testes").put("host", "127.0.0.1").build())
                .put("capture", Json.obj().put("recorderAttached", attached).put("otherConsumers", 0).put("captureStatus", attached ? "ACTIVE" : null).build())
                .put("coverage", Json.obj().put("state", attached ? "COMPLETE" : "UNKNOWN").put("framesAttached", 0).put("framesUnavailable", 0).build())
                .put("counters", Json.obj().put("segments", 1).put("technicalCaptures", 1).put("synchronizations", 0).put("recoveries", 0).build())
                .put("lease", Json.obj().put("leaseMs", 30000).put("expiresInMs", attached ? 30000 : 0).build())
                .put("startedAt", Instant.ofEpochMilli(0).toString()).put("updatedAt", Instant.ofEpochMilli(clock).toString()).put("reason", null).build();
    }

    private RecorderSnapshot await(Predicate<RecorderSnapshot> p, String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            if (p.test(recorder.snapshot())) return recorder.snapshot();
            Thread.sleep(20);
        }
        throw new AssertionError("tempo esgotado: " + what + " (" + recorder.snapshot().state() + ", ligação " + server.status() + ")");
    }

    @Test
    @DisplayName("Studio real ⇄ Native Host real: handshake, comandos confirmados, eventos e encerramento do Chrome")
    void cadeiaReal() throws Exception {
        await(s -> s.link() != null && s.link().connected(), "host conectado e autenticado");
        assertEquals("0.4.0", server.status().extension().version());
        assertTrue(fromHost.stream().anyMatch(m -> "studio.link".equals(m.get("type")) && "CONNECTED".equals(m.get("state"))), "a extensão foi avisada");
        List<BrowserTab> tabs = recorder.listTabs().get(10, TimeUnit.SECONDS);
        assertEquals(1, tabs.size());
        Automation a = Automation.create("Consulta de clientes", "", Clock.systemUTC());
        LogicalSession s = a.addSession("Autenticação", "");
        recorder.start(new RecordingTarget(a.id(), s.id()), tabs.get(0), RecordingMode.CAPTURE_AND_INTERACT);
        await(x -> x.state() == RecorderState.RECORDING, "RECORDING confirmado via host real");
        assertTrue(fromHost.stream().anyMatch(m -> "studio.command".equals(m.get("type")) && "studio.recording.start".equals(m.get("command"))
                && server.instanceId().equals(Json.string(Json.object(m, "controller"), "instanceId"))), "controlador identificado pelo host");
        recorder.pause();
        await(x -> x.state() == RecorderState.PAUSED, "PAUSED");
        recorder.setMode(RecordingMode.CAPTURE_ONLY);
        await(x -> x.mode() == RecordingMode.CAPTURE_ONLY && x.pendingMode() == null, "modo");
        recorder.resume();
        await(x -> x.state() == RecorderState.RECORDING, "RECORDING");
        await(x -> fromHost.stream().filter(m -> "studio.recording.renew".equals(m.get("command"))).count() >= 2, "concessão renovada pelo host");
        // Evento originado no navegador, repassado pelo host real.
        extState = "DEGRADED";
        Map<String, Object> rec = recording();
        rec.put("reason", "TAB_CLOSED");
        send(Json.obj().put("protocol", "nekkora-nm/1").put("type", "studio.event").put("event", "studio.recording.degraded")
                .put("payload", Json.obj().put("recordingId", recordingId).put("operationId", null).put("recording", rec).build())
                .put("controller", Json.obj().put("instanceId", server.instanceId()).build()).build());
        await(x -> x.state() == RecorderState.DEGRADED && "TAB_CLOSED".equals(x.reason()), "DEGRADED pelo evento do navegador");
        recorder.finish();
        RecorderSnapshot done = await(x -> x.state() == RecorderState.COMPLETED, "COMPLETED");
        assertFalse(done.summary().pageMapExported());
        // O Chrome fecha a porta nativa: o host avisa o Studio antes de sair.
        host.getOutputStream().close();
        host.waitFor(15, TimeUnit.SECONDS);
        long deadline = System.currentTimeMillis() + 10000;
        while (server.status().state() != LinkStatus.State.WAITING && System.currentTimeMillis() < deadline) Thread.sleep(20);
        assertEquals(LinkStatus.State.WAITING, server.status().state());
        assertEquals("CHROME_DISCONNECTED", server.status().detail());
        assertFalse(host.isAlive(), "o host termina quando o Chrome fecha a porta");
    }

    @Test
    @DisplayName("O host real não repassa ao Studio uma resposta fora do contrato (URL completa)")
    void contratoAplicadoPeloHostReal() throws Exception {
        await(s -> s.link() != null && s.link().connected(), "host conectado");
        leakUrl = true;
        ExecutionException e = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class, () -> recorder.listTabs().get(10, TimeUnit.SECONDS));
        BridgeCommandException x = (BridgeCommandException) e.getCause();
        assertEquals(BridgeError.INTERNAL_ERROR, x.code());
        assertFalse(String.valueOf(x.getMessage()).contains("VAZOU"));
    }
}
