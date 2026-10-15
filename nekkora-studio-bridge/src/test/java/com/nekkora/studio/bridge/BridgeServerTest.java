package com.nekkora.studio.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.bridge.json.Json;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Studio Bridge (servidor): publicação restrita, autenticação, instância única, comandos, batimentos e limpeza. */
class BridgeServerTest {

    @TempDir
    Path tmp;
    private final List<BridgeServer> servers = new CopyOnWriteArrayList<>();
    private final List<LinkStatus> statuses = new CopyOnWriteArrayList<>();
    private final List<BridgeEvent> events = new CopyOnWriteArrayList<>();

    private BridgeServer start(BridgeServer.Transport t, int heartbeatMs) {
        BridgeServer s = BridgeServer.start(new BridgeServer.Options(tmp.resolve("bridge"), t, Duration.ofMillis(heartbeatMs), "test"));
        s.addListener(new BrowserLink.Listener() {
            @Override
            public void statusChanged(LinkStatus status) {
                statuses.add(status);
            }

            @Override
            public void event(BridgeEvent event) {
                events.add(event);
            }
        });
        servers.add(s);
        return s;
    }

    @AfterEach
    void closeAll() {
        servers.forEach(BridgeServer::close);
    }

    private static void waitFor(java.util.function.BooleanSupplier c, long ms, String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + ms;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("tempo esgotado: " + what);
            }
            Thread.sleep(20);
        }
    }

    private static void assertOwnerOnly(Path p) throws Exception {
        AclFileAttributeView acl = Files.getFileAttributeView(p, AclFileAttributeView.class);
        if (acl == null) {
            String perms = java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(p));
            assertTrue(perms.startsWith("rw"), p.getFileName() + ": o dono lê e escreve (" + perms + ")");
            assertEquals("------", perms.substring(3), p.getFileName() + ": nada para grupo e outros (" + perms + ")");
            return;
        }
        for (AclEntry e : acl.getAcl()) {
            assertEquals(Files.getOwner(p), e.principal(), p.getFileName() + ": entrada de outro principal " + e);
            assertEquals(AclEntryType.ALLOW, e.type());
        }
    }

    @Test
    void publicaComPermissoesRestritasAoUsuarioESemSegredoNosLogs() throws Exception {
        List<String> logs = new CopyOnWriteArrayList<>();
        Handler h = new Handler() {
            @Override
            public void publish(LogRecord r) {
                logs.add(r.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger.getLogger(BridgeServer.class.getName()).addHandler(h);
        try {
            BridgeServer s = start(BridgeServer.Transport.AUTO, 5000);
            assertEquals(LinkStatus.State.WAITING, s.status().state());
            assertEquals("unix", s.transport(), "Unix Domain Socket preferencial (validado no Windows 11)");
            Path dir = tmp.resolve("bridge");
            assertOwnerOnly(dir);
            assertOwnerOnly(s.discoveryFile());
            assertOwnerOnly(s.socketPath());
            Map<String, Object> d = Json.parseObject(Files.readString(s.discoveryFile()));
            assertEquals("nekkora-bridge/1", d.get("protocol"));
            assertEquals(43, Json.string(d, "secret").length(), "segredo de 32 bytes");
            try (TestHostClient c = new TestHostClient(dir, TestHostClient.Mode.WRONG_SECRET)) {
                assertEquals("AUTH_FAILED", c.rejection);
            }
            String secret = Json.string(d, "secret");
            assertTrue(logs.stream().noneMatch(l -> l != null && l.contains(secret)), "o segredo nunca vai para o log");
        } finally {
            Logger.getLogger(BridgeServer.class.getName()).removeHandler(h);
        }
    }

    @Test
    void conexaoAutenticadaComandosEEventos() throws Exception {
        BridgeServer s = start(BridgeServer.Transport.AUTO, 5000);
        try (TestHostClient c = new TestHostClient(tmp.resolve("bridge"), TestHostClient.Mode.NORMAL)) {
            assertNull(c.rejection);
            waitFor(() -> s.status().connected(), 5000, "CONNECTED");
            assertEquals("0.4.0", s.status().extension().version());
            Map<String, Object> r = s.send("bridge.status", Map.of(), Duration.ofSeconds(5)).get(5, TimeUnit.SECONDS);
            assertEquals("bridge.status", r.get("echo"));
            c.event("studio.recording.paused", Json.obj().put("recordingId", "rec-1").put("operationId", "op-1").build());
            waitFor(() -> !events.isEmpty(), 5000, "evento entregue");
            assertEquals("studio.recording.paused", events.get(0).type());
            c.event("bridge.connected", Json.obj().put("extension", Json.obj().put("version", "0.4.1").put("buildMode", "PRD").build()).build());
            waitFor(() -> "0.4.1".equals(s.status().extension().version()), 5000, "informação da extensão atualizada");
        }
    }

    @Test
    void recusaCredencialInvalidaProtocoloIncompativelERepeticao() throws Exception {
        BridgeServer s = start(BridgeServer.Transport.AUTO, 5000);
        Path dir = tmp.resolve("bridge");
        try (TestHostClient bad = new TestHostClient(dir, TestHostClient.Mode.WRONG_SECRET)) {
            assertEquals("AUTH_FAILED", bad.rejection);
        }
        try (TestHostClient old = new TestHostClient(dir, TestHostClient.Mode.WRONG_PROTOCOL)) {
            assertEquals("PROTOCOL_UNSUPPORTED", old.rejection, "versão incompatível recusada com erro explícito");
        }
        Map<String, Object> captured;
        try (TestHostClient ok = new TestHostClient(dir, TestHostClient.Mode.NORMAL)) {
            assertNull(ok.rejection);
            captured = ok.lastHello;
        }
        waitFor(() -> !s.status().connected(), 5000, "desconexão");
        captured.remove("seq");
        try (TestHostClient replay = new TestHostClient(dir, TestHostClient.Mode.NORMAL, captured)) {
            assertEquals("AUTH_FAILED", replay.rejection, "prova capturada não serve para outra conexão (nonce do servidor muda)");
        }
        assertFalse(s.status().connected());
    }

    @Test
    void instanciaUnicaEUmControladorPorVez() throws Exception {
        BridgeServer a = start(BridgeServer.Transport.AUTO, 5000);
        BridgeServer b = start(BridgeServer.Transport.AUTO, 5000);
        assertEquals(LinkStatus.State.WAITING, a.status().state());
        assertEquals(LinkStatus.State.UNAVAILABLE, b.status().state());
        assertEquals("OTHER_INSTANCE", b.status().detail());
        try (TestHostClient first = new TestHostClient(tmp.resolve("bridge"), TestHostClient.Mode.NORMAL);
             TestHostClient second = new TestHostClient(tmp.resolve("bridge"), TestHostClient.Mode.NORMAL)) {
            assertNull(first.rejection);
            assertEquals("BRIDGE_BUSY", second.rejection, "um único navegador controlador por vez");
        }
    }

    @Test
    void comandoSemNavegadorTempoEsgotadoECanalPerdido() throws Exception {
        BridgeServer s = start(BridgeServer.Transport.AUTO, 5000);
        ExecutionException none = assertThrows(ExecutionException.class, () -> s.send("bridge.status", Map.of(), Duration.ofSeconds(1)).get());
        assertEquals(BridgeError.EXTENSION_UNAVAILABLE, ((BridgeCommandException) none.getCause()).code());
        try (TestHostClient c = new TestHostClient(tmp.resolve("bridge"), TestHostClient.Mode.NORMAL)) {
            waitFor(() -> s.status().connected(), 5000, "CONNECTED");
            c.responder = cmd -> null; // nunca responde
            long t0 = System.nanoTime();
            CompletableFuture<Map<String, Object>> f = s.send("studio.recording.pause", Map.of("recordingId", "r", "operationId", "o"), Duration.ofMillis(400));
            assertTrue((System.nanoTime() - t0) / 1_000_000 < 50, "send não bloqueia quem chama");
            ExecutionException to = assertThrows(ExecutionException.class, () -> f.get(5, TimeUnit.SECONDS));
            assertEquals(BridgeError.TIMEOUT, ((BridgeCommandException) to.getCause()).code());
            c.responder = cmd -> Json.obj().put("__error", Json.obj().put("code", "ORIGIN_NOT_ALLOWED").put("message", "Autorize o site.")
                    .put("reason", "PERMISSION_REQUIRED").put("retryable", false).build()).build();
            ExecutionException denied = assertThrows(ExecutionException.class,
                    () -> s.send("studio.recording.start", Map.of(), Duration.ofSeconds(5)).get(5, TimeUnit.SECONDS));
            BridgeCommandException e = (BridgeCommandException) denied.getCause();
            assertEquals(BridgeError.ORIGIN_NOT_ALLOWED, e.code());
            assertEquals("PERMISSION_REQUIRED", e.reason());
            c.responder = cmd -> null;
            CompletableFuture<Map<String, Object>> lost = s.send("studio.recording.finish", Map.of(), Duration.ofSeconds(30));
            c.close();
            ExecutionException l = assertThrows(ExecutionException.class, () -> lost.get(5, TimeUnit.SECONDS));
            assertEquals(BridgeError.LINK_LOST, ((BridgeCommandException) l.getCause()).code(), "nada é dado como confirmado sem resposta");
            waitFor(() -> s.status().state() == LinkStatus.State.WAITING, 5000, "WAITING após perda");
        }
    }

    @Test
    void navegadorSilenciosoEhDetectadoPelosBatimentos() throws Exception {
        BridgeServer s = start(BridgeServer.Transport.AUTO, 300);
        try (TestHostClient c = new TestHostClient(tmp.resolve("bridge"), TestHostClient.Mode.NORMAL)) {
            waitFor(() -> s.status().connected(), 5000, "CONNECTED");
            assertNotNull(c.await(m -> "bridge.heartbeat".equals(m.get("type")), 3000), "o Studio envia batimentos");
            c.answerHeartbeats = false;
            waitFor(() -> s.status().state() == LinkStatus.State.WAITING, 5000, "canal encerrado por falta de sinal");
            assertEquals("HEARTBEAT_LOST", s.status().detail());
        }
    }

    @Test
    void encerramentoLimpaDescobertaSocketETrava() throws Exception {
        BridgeServer s = start(BridgeServer.Transport.AUTO, 5000);
        Path discovery = s.discoveryFile();
        Path socket = s.socketPath();
        assertTrue(Files.exists(discovery) && Files.exists(socket));
        s.close();
        assertFalse(Files.exists(discovery), "arquivo de descoberta removido (credencial invalidada)");
        assertFalse(Files.exists(socket), "socket removido");
        BridgeServer again = start(BridgeServer.Transport.AUTO, 5000);
        assertEquals(LinkStatus.State.WAITING, again.status().state(), "trava liberada: nova instância publica normalmente");
        Map<String, Object> d = Json.parseObject(Files.readString(again.discoveryFile()));
        assertFalse(s.instanceId().equals(Json.string(d, "instanceId")), "nova identidade e novo segredo a cada inicialização");
    }

    @Test
    void tcpSomenteNoLoopbackComAMesmaAutenticacao() throws Exception {
        BridgeServer s = start(BridgeServer.Transport.TCP, 5000);
        assertEquals("tcp", s.transport());
        Map<String, Object> d = Json.parseObject(Files.readString(s.discoveryFile()));
        String address = Json.string(d, "address");
        assertTrue(address.startsWith("127.0.0.1:"), address);
        java.lang.reflect.Field f = BridgeServer.class.getDeclaredField("server");
        f.setAccessible(true);
        InetSocketAddress bound = (InetSocketAddress) ((java.nio.channels.ServerSocketChannel) f.get(s)).getLocalAddress();
        assertTrue(bound.getAddress().isLoopbackAddress(), "nunca 0.0.0.0");
        try (TestHostClient bad = new TestHostClient(tmp.resolve("bridge"), TestHostClient.Mode.WRONG_SECRET)) {
            assertEquals("AUTH_FAILED", bad.rejection);
        }
        try (TestHostClient ok = new TestHostClient(tmp.resolve("bridge"), TestHostClient.Mode.NORMAL)) {
            assertNull(ok.rejection);
            waitFor(() -> s.status().connected(), 5000, "CONNECTED por TCP");
        }
    }

    @Test
    void aclComOutroPrincipalEhDetectada() throws Exception {
        Path f = tmp.resolve("x.json");
        Files.writeString(f, "{}");
        AclFileAttributeView acl = Files.getFileAttributeView(f, AclFileAttributeView.class);
        if (acl == null) {
            Files.setPosixFilePermissions(f, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
        }
        // %TEMP% herda entradas de SISTEMA/Administradores: um arquivo comum NÃO é privado.
        BridgeException e = assertThrows(BridgeException.class, () -> BridgeDirectory.verifyPrivate(f));
        assertEquals(BridgeError.INSECURE_DIRECTORY, e.code());
        BridgeDirectory.restrict(f, false);
        BridgeDirectory.verifyPrivate(f);
        assertInstanceOf(Path.class, f);
    }
}
