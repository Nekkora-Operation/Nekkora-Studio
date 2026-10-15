package com.nekkora.studio.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.bridge.json.Json;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exclusividade do controlador sob concorrência (revisão da STUDIO-002, P1): dois handshakes simultâneos e mensagens
 * em trânsito de uma conexão que já perdeu o controle.
 */
class BridgeServerConcurrencyTest {

    @TempDir
    Path tmp;
    private final List<BridgeServer> servers = new CopyOnWriteArrayList<>();
    private final List<BridgeEvent> events = new CopyOnWriteArrayList<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    private BridgeServer start(int heartbeatMs) {
        BridgeServer s = BridgeServer.start(new BridgeServer.Options(tmp.resolve("bridge"), BridgeServer.Transport.AUTO,
                Duration.ofMillis(heartbeatMs), "test"));
        s.addListener(new BrowserLink.Listener() {
            @Override
            public void statusChanged(LinkStatus status) {
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
        pool.shutdownNow();
    }

    private static void waitFor(java.util.function.BooleanSupplier c, long ms, String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + ms;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("tempo esgotado: " + what);
            }
            Thread.sleep(10);
        }
    }

    /**
     * Os dois handshakes chegam juntos ao ponto de decisão (barreira depois da autenticação): só um é autorizado, o
     * outro recebe BRIDGE_BUSY e nada dele chega ao Studio.
     */
    @RepeatedTest(5)
    void doisHandshakesSimultaneosSoUmAutorizado() throws Exception {
        BridgeServer s = start(5000);
        CyclicBarrier barrier = new CyclicBarrier(2);
        s.handshakeGate = () -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("os dois handshakes não chegaram juntos", e);
            }
        };
        Path dir = tmp.resolve("bridge");
        Future<TestHostClient> fa = pool.submit(() -> new TestHostClient(dir, TestHostClient.Mode.NORMAL));
        Future<TestHostClient> fb = pool.submit(() -> new TestHostClient(dir, TestHostClient.Mode.NORMAL));
        try (TestHostClient a = fa.get(10, TimeUnit.SECONDS); TestHostClient b = fb.get(10, TimeUnit.SECONDS)) {
            s.handshakeGate = null;
            List<TestHostClient> accepted = new ArrayList<>();
            List<TestHostClient> refused = new ArrayList<>();
            for (TestHostClient c : List.of(a, b)) {
                (c.rejection == null ? accepted : refused).add(c);
            }
            assertEquals(1, accepted.size(), "um único cliente autenticado");
            assertEquals(1, refused.size(), "o outro é recusado");
            assertEquals("BRIDGE_BUSY", refused.get(0).rejection);
            TestHostClient winner = accepted.get(0);
            TestHostClient loser = refused.get(0);
            waitFor(() -> s.status().connected(), 5000, "CONNECTED");

            // o recusado tenta mesmo assim: evento e uma resposta com o próximo id de comando
            try {
                loser.event("studio.recording.paused", Json.obj().put("recordingId", "rec-intruso").build());
                loser.send(Json.obj().put("protocol", "nekkora-bridge/1").put("kind", "response").put("type", "bridge.status")
                        .put("id", "c-1").put("ok", true).put("payload", Json.obj().put("forjado", true).build()).put("error", null).build());
            } catch (java.io.IOException expected) {
                // o canal recusado já foi fechado pelo Studio
            }
            winner.responder = cmd -> Json.obj().put("de", "vencedor").build();
            Map<String, Object> r = s.send("bridge.status", Map.of(), Duration.ofSeconds(5)).get(5, TimeUnit.SECONDS);
            assertEquals("vencedor", r.get("de"), "a resposta vem da conexão autorizada");
            Thread.sleep(100);
            assertTrue(events.isEmpty(), "nenhum evento aceito do cliente recusado: " + events);
            winner.event("studio.recording.paused", Json.obj().put("recordingId", "rec-1").build());
            waitFor(() -> events.size() == 1, 5000, "evento do vencedor entregue");
            assertEquals("rec-1", events.get(0).payload().get("recordingId"));
        }
    }

    /**
     * A conexão A perde o canal (fechada pelo Studio, como na falta de batimentos) enquanto o leitor dela ainda segura
     * uma mensagem; B assume o controle. A mensagem atrasada de A não pode resolver o comando de B nem virar evento.
     */
    @Test
    void mensagemAtrasadaDaConexaoAnteriorEhDescartada() throws Exception {
        for (String held : List.of("response", "event")) {
            events.clear();
            BridgeServer s = start(5000);
            Path dir = tmp.resolve("bridge");
            CountDownLatch aHolding = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicReference<String> idOfB = new AtomicReference<>();
            s.dispatchGate = (msg, closeConnection) -> {
                boolean marked = "atrasada".equals(Json.string(msg, "id"))
                        || "rec-atrasado".equals(Json.string(Json.object(msg, "payload"), "recordingId"));
                if (!marked) {
                    return;
                }
                closeConnection.run(); // o Studio fecha A enquanto a mensagem está em trânsito
                aHolding.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if ("atrasada".equals(Json.string(msg, "id"))) {
                    msg.put("id", idOfB.get()); // a resposta em trânsito passa a apontar para o comando de B
                }
            };
            TestHostClient a = new TestHostClient(dir, TestHostClient.Mode.NORMAL);
            assertNull(a.rejection);
            waitFor(() -> s.status().connected(), 5000, "A conectado");
            if ("response".equals(held)) {
                a.send(Json.obj().put("protocol", "nekkora-bridge/1").put("kind", "response").put("type", "studio.recording.pause")
                        .put("id", "atrasada").put("ok", true).put("payload", Json.obj().put("forjado", true).build()).put("error", null).build());
            } else {
                a.event("studio.recording.paused", Json.obj().put("recordingId", "rec-atrasado").build());
            }
            assertTrue(aHolding.await(5, TimeUnit.SECONDS), "leitor de A segurando a mensagem");

            TestHostClient b = new TestHostClient(dir, TestHostClient.Mode.NORMAL);
            assertNull(b.rejection, "A já não está aberta: B pode assumir");
            waitFor(() -> s.status().connected(), 5000, "B conectado");
            b.responder = cmd -> null; // B ainda não respondeu
            CompletableFuture<Map<String, Object>> f = s.send("studio.recording.pause", Map.of("recordingId", "rec-b"), Duration.ofSeconds(10));
            Map<String, Object> cmd = b.await(m -> "studio.recording.pause".equals(m.get("type")), 5000);
            idOfB.set(Json.string(cmd, "id"));
            release.countDown();
            Thread.sleep(200);
            assertFalse(f.isDone(), held + ": a resposta atrasada de A não resolve o comando de B");
            assertTrue(events.isEmpty(), held + ": evento da conexão anterior não é entregue: " + events);
            b.send(Json.obj().put("protocol", "nekkora-bridge/1").put("kind", "response").put("type", "studio.recording.pause")
                    .put("id", idOfB.get()).put("ok", true).put("payload", Json.obj().put("de", "B").build()).put("error", null).build());
            assertEquals("B", f.get(5, TimeUnit.SECONDS).get("de"), "o comando de B é resolvido só por B");
            assertTrue(s.status().connected(), "B continua controlador");
            a.close();
            b.close();
            s.close();
            servers.remove(s);
        }
    }

    /** Sem pontos de interceptação: 8 navegadores ao mesmo tempo, 20 rodadas — sempre exatamente um controlador. */
    @Test
    void muitosHandshakesSimultaneosSemInterceptacao() throws Exception {
        Path dir = tmp.resolve("bridge");
        for (int round = 0; round < 20; round++) {
            BridgeServer s = start(5000);
            CyclicBarrier go = new CyclicBarrier(8);
            List<Future<TestHostClient>> fs = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                fs.add(pool.submit(() -> {
                    go.await(5, TimeUnit.SECONDS);
                    return new TestHostClient(dir, TestHostClient.Mode.NORMAL);
                }));
            }
            // todos os handshakes terminam antes de qualquer fechamento (fechar o vencedor liberaria o controle)
            List<TestHostClient> clients = new ArrayList<>();
            for (Future<TestHostClient> f : fs) {
                clients.add(f.get(10, TimeUnit.SECONDS));
            }
            int ok = 0;
            int busy = 0;
            for (TestHostClient c : clients) {
                if (c.rejection == null) {
                    ok++;
                } else if ("BRIDGE_BUSY".equals(c.rejection)) {
                    busy++;
                }
            }
            for (TestHostClient c : clients) {
                c.close();
            }
            assertEquals(1, ok, "rodada " + round + ": exatamente um controlador");
            assertEquals(7, busy, "rodada " + round + ": os demais recusados com BRIDGE_BUSY");
            s.close();
            servers.remove(s);
        }
    }

    /** Uma falha depois da reserva não deixa o Studio ocupado: o próximo navegador conecta. */
    @Test
    void falhaNoHandshakeLiberaAReserva() throws Exception {
        BridgeServer s = start(5000);
        Path dir = tmp.resolve("bridge");
        AtomicBoolean fail = new AtomicBoolean(true);
        s.reservedGate = () -> {
            if (fail.getAndSet(false)) {
                throw new IllegalStateException("falha simulada depois da reserva");
            }
        };
        try (TestHostClient first = new TestHostClient(dir, TestHostClient.Mode.NORMAL)) {
            // o primeiro não recebe welcome: o canal fecha
            assertTrue(first.rejection != null || first.await(m -> "closed".equals(m.get("kind")), 2000) != null);
        } catch (java.io.IOException | RuntimeException closed) {
            // canal fechado no meio do handshake
        }
        assertFalse(s.status().connected());
        try (TestHostClient second = new TestHostClient(dir, TestHostClient.Mode.NORMAL)) {
            assertNull(second.rejection, "a reserva da tentativa que falhou foi liberada");
            waitFor(() -> s.status().connected(), 5000, "CONNECTED");
        }
    }
}
