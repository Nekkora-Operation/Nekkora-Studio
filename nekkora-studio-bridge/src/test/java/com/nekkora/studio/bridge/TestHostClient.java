package com.nekkora.studio.bridge;

import com.nekkora.studio.bridge.json.Json;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Lado "Native Host" do nekkora-bridge/1 para os testes do Studio: lê o arquivo de descoberta, conecta, autentica e
 * responde aos comandos com uma função configurável. Variações de ataque/erro para os testes de segurança.
 */
final class TestHostClient implements AutoCloseable {

    final BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
    final Map<String, Object> discovery;
    private final SocketChannel channel;
    private final InputStream in;
    private final OutputStream out;
    private final AtomicLong seq = new AtomicLong();
    volatile Function<Map<String, Object>, Map<String, Object>> responder = cmd -> Json.obj().put("echo", Json.string(cmd, "type")).build();
    volatile boolean answerHeartbeats = true;
    String rejection;
    Map<String, Object> lastHello;

    /** Variações do handshake. */
    enum Mode { NORMAL, WRONG_SECRET, WRONG_PROTOCOL }

    TestHostClient(Path dir, Mode mode) throws IOException {
        this(dir, mode, null);
    }

    /** @param replayHello hello capturado de outra conexão (teste de repetição) */
    TestHostClient(Path dir, Mode mode, Map<String, Object> replayHello) throws IOException {
        discovery = Json.parseObject(Files.readString(dir.resolve(BridgeServer.DISCOVERY_FILE)));
        String transport = Json.string(discovery, "transport");
        String address = Json.string(discovery, "address");
        if ("unix".equals(transport)) {
            channel = SocketChannel.open(StandardProtocolFamily.UNIX);
            channel.connect(UnixDomainSocketAddress.of(address));
        } else {
            channel = SocketChannel.open(new InetSocketAddress(java.net.InetAddress.getLoopbackAddress(),
                    Integer.parseInt(address.substring(address.lastIndexOf(':') + 1))));
        }
        in = Channels.newInputStream(channel);
        out = Channels.newOutputStream(channel);
        Map<String, Object> challenge = Json.parseObject(BridgeFrames.read(in));
        Map<String, Object> cp = Json.object(challenge, "payload");
        String instanceId = Json.string(cp, "instanceId");
        String serverNonce = Json.string(cp, "serverNonce");
        byte[] secret = Base64.getUrlDecoder().decode(Json.string(discovery, "secret"));
        if (mode == Mode.WRONG_SECRET) {
            secret = new byte[32];
        }
        String clientNonce = BridgeAuth.nonce();
        Map<String, Object> hello = replayHello != null ? replayHello : Json.obj()
                .put("protocol", mode == Mode.WRONG_PROTOCOL ? "nekkora-bridge/2" : "nekkora-bridge/1").put("kind", "handshake").put("type", "bridge.hello")
                .put("payload", Json.obj().put("instanceId", instanceId).put("clientNonce", clientNonce)
                        .put("proof", BridgeAuth.clientProof(secret, instanceId, serverNonce, clientNonce))
                        .put("client", Json.obj().put("hostVersion", "test").put("nativeProtocol", "nekkora-nm/1")
                                .put("extension", Json.obj().put("version", "0.4.0").put("buildMode", "DEV").build()).build()).build()).build();
        lastHello = new java.util.LinkedHashMap<>(hello);
        send(hello);
        Map<String, Object> answer = Json.parseObject(BridgeFrames.read(in));
        if ("bridge.rejected".equals(Json.string(answer, "type"))) {
            rejection = Json.string(Json.object(Json.object(answer, "payload"), "error"), "code");
            return;
        }
        String expected = BridgeAuth.serverProof(secret, instanceId, serverNonce, clientNonce);
        if (!BridgeAuth.matches(expected, Json.string(Json.object(answer, "payload"), "proof"))) {
            rejection = "SERVER_PROOF_INVALID";
            return;
        }
        Thread t = new Thread(this::loop, "test-host");
        t.setDaemon(true);
        t.start();
    }

    private void loop() {
        try {
            while (true) {
                String raw = BridgeFrames.read(in);
                if (raw == null) {
                    break;
                }
                Map<String, Object> msg = Json.parseObject(raw);
                received.add(msg);
                if (!"command".equals(Json.string(msg, "kind"))) {
                    continue;
                }
                String type = Json.string(msg, "type");
                if ("bridge.heartbeat".equals(type) && !answerHeartbeats) {
                    continue;
                }
                Map<String, Object> payload = "bridge.heartbeat".equals(type) ? Json.obj().put("at", java.time.Instant.now().toString()).build()
                        : responder.apply(msg);
                if (payload == null) {
                    continue; // não responde (teste de tempo esgotado)
                }
                if (payload.containsKey("__error")) {
                    send(Json.obj().put("protocol", "nekkora-bridge/1").put("kind", "response").put("type", type).put("id", Json.string(msg, "id"))
                            .put("ok", false).put("payload", null).put("error", payload.get("__error")).build());
                } else {
                    send(Json.obj().put("protocol", "nekkora-bridge/1").put("kind", "response").put("type", type).put("id", Json.string(msg, "id"))
                            .put("ok", true).put("payload", payload).put("error", null).build());
                }
            }
        } catch (IOException | RuntimeException e) {
            // encerrado
        }
        received.add(Json.obj().put("kind", "closed").build());
    }

    synchronized void send(Map<String, Object> msg) throws IOException {
        msg.put("seq", seq.incrementAndGet());
        BridgeFrames.write(out, Json.write(msg));
    }

    /** Quadro acima do teto de controle (o Studio só aceita o do print nekkora-visual/4). */
    synchronized void sendLarge(Map<String, Object> msg) throws IOException {
        msg.put("seq", seq.incrementAndGet());
        BridgeFrames.write(out, Json.write(msg), BridgeFrames.MAX_CAPTURE_FRAME_BYTES);
    }

    void event(String type, Map<String, Object> payload) throws IOException {
        send(Json.obj().put("protocol", "nekkora-bridge/1").put("kind", "event").put("type", type).put("payload", payload).build());
    }

    Map<String, Object> await(java.util.function.Predicate<Map<String, Object>> f, long ms) throws InterruptedException {
        long deadline = System.currentTimeMillis() + ms;
        while (true) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) {
                return null;
            }
            Map<String, Object> m = received.poll(left, TimeUnit.MILLISECONDS);
            if (m != null && f.test(m)) {
                return m;
            }
        }
    }

    static List<String> types(List<Map<String, Object>> l) {
        return l.stream().map(m -> Json.string(m, "type")).toList();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
