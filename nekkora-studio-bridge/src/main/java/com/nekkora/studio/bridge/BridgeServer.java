package com.nekkora.studio.bridge;

import com.nekkora.studio.bridge.json.Json;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * StudioBridgeServer: publica o Bridge local do Studio e atende o Native Host (D-STUDIO-002-01/04/06/07).
 *
 * <ul>
 *   <li><b>Local</b>: Unix Domain Socket no diretório restrito ao usuário (validado no Windows 11), ou TCP somente em
 *       {@code 127.0.0.1} com porta dinâmica quando o socket não é possível. Nunca {@code 0.0.0.0}.</li>
 *   <li><b>Autenticado</b>: segredo efêmero de 32 bytes por inicialização, só no arquivo de descoberta (restrito ao
 *       usuário); desafio-resposta HMAC nos dois sentidos — o segredo não trafega e provas não podem ser repetidas.
 *       Limite: não protege contra um processo malicioso do MESMO usuário, que pode ler o arquivo.</li>
 *   <li><b>Instância única</b>: trava de arquivo no diretório; a segunda instância do Studio fica sem Bridge
 *       (UNAVAILABLE/OTHER_INSTANCE) e continua editando automações normalmente.</li>
 *   <li><b>Um controlador</b>: uma conexão autenticada por vez (outra recebe BRIDGE_BUSY). A reserva é atômica
 *       ({@code compareAndSet}) no handshake; respostas e eventos só valem vindos da conexão ativa, e cada comando
 *       pendente só pode ser resolvido pela conexão que o recebeu.</li>
 *   <li><b>Vivo</b>: batimentos periódicos; sem sinal por 3 intervalos o canal é encerrado (DEGRADED no Recorder).</li>
 * </ul>
 * Nenhuma chamada bloqueia quem chama: comandos devolvem {@link CompletableFuture}.
 */
public final class BridgeServer implements BrowserLink, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(BridgeServer.class.getName());
    public static final String PROTOCOL = "nekkora-bridge/1";
    public static final String DISCOVERY_FILE = "studio-bridge.json";
    static final long HANDSHAKE_TIMEOUT_MS = 5000;

    /** Transporte desejado. */
    public enum Transport { AUTO, UNIX, TCP }

    /** Configuração da publicação. */
    public record Options(Path directory, Transport transport, Duration heartbeat, String studioVersion) {
        public static Options defaults(String studioVersion) {
            String t = System.getProperty("nekkora.bridge.transport", "auto").toUpperCase(java.util.Locale.ROOT);
            Transport tr = "UNIX".equals(t) ? Transport.UNIX : "TCP".equals(t) ? Transport.TCP : Transport.AUTO;
            return new Options(BridgeDirectory.defaultDirectory(), tr, Duration.ofSeconds(5), studioVersion);
        }
    }

    private final Options options;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "nekkora-bridge-timer"));
    private final byte[] secret = new byte[32];
    private final String instanceId;
    private volatile LinkStatus status = LinkStatus.of(LinkStatus.State.STARTING, null);
    /** Controlador: reservado atomicamente no handshake, antes do welcome. */
    private final AtomicReference<Connection> active = new AtomicReference<>();
    private volatile boolean closed;
    private ServerSocketChannel server;
    private FileChannel lockChannel;
    private FileLock lock;
    private Path socketPath;
    private Path discoveryFile;
    private String transportUsed;
    private String address;

    // Pontos de interceptação só para os testes de concorrência (nulos em produção).
    /** Chamado no handshake depois da autenticação e antes da reserva do controlador. */
    volatile Runnable handshakeGate;
    /** Chamado no handshake depois da reserva do controlador e antes do welcome. */
    volatile Runnable reservedGate;
    /** Chamado no leitor antes de despachar cada mensagem: recebe a mensagem e uma ação que fecha a conexão. */
    volatile java.util.function.BiConsumer<Map<String, Object>, Runnable> dispatchGate;

    /** Comando aguardando resposta; {@code owner} é a única conexão que pode resolvê-lo. */
    private record Pending(CompletableFuture<Map<String, Object>> future, String type, ScheduledFuture<?> timeout, Connection owner) {
    }

    /** Conexão autenticada com um Native Host. */
    private final class Connection {
        final SocketChannel channel;
        final InputStream in;
        final OutputStream out;
        final AtomicLong seq = new AtomicLong();
        final AtomicBoolean open = new AtomicBoolean(true);
        /** Reservada e já com o welcome enviado: só então recebe comandos. */
        volatile boolean ready;
        volatile long lastInbound = System.currentTimeMillis();
        volatile String disconnectReason;
        /** Política de privacidade declarada no hello (null = anterior à nekkora-privacy/2). */
        volatile String privacy;
        ScheduledFuture<?> heartbeat;

        Connection(SocketChannel channel) {
            this.channel = channel;
            this.in = Channels.newInputStream(channel);
            this.out = Channels.newOutputStream(channel);
        }

        void write(Map<String, Object> msg) throws IOException {
            synchronized (this) {
                msg.put("seq", seq.incrementAndGet());
                BridgeFrames.write(out, Json.write(msg));
            }
        }

        void close() {
            open.set(false);
            try {
                channel.close();
            } catch (IOException ignored) {
                // encerramento
            }
        }
    }

    private BridgeServer(Options options) {
        this.options = options;
        SecureRandom random = new SecureRandom();
        random.nextBytes(secret);
        byte[] id = new byte[9];
        random.nextBytes(id);
        this.instanceId = "studio-" + Base64.getUrlEncoder().withoutPadding().encodeToString(id).replace('-', 'x').replace('_', 'y');
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    /**
     * Publica o Bridge. Nunca lança: se a publicação não for segura ou possível, o servidor fica UNAVAILABLE com o
     * motivo (o Studio continua funcionando, só sem gravação).
     */
    public static BridgeServer start(Options options) {
        BridgeServer s = new BridgeServer(options);
        try {
            s.publish();
            s.setStatus(LinkStatus.of(LinkStatus.State.WAITING, null));
        } catch (BridgeException e) {
            LOG.warning("Bridge indisponível: " + e.code() + " — " + e.getMessage());
            s.release();
            s.setStatus(LinkStatus.of(LinkStatus.State.UNAVAILABLE, e.code().name()));
        }
        return s;
    }

    private void publish() throws BridgeException {
        Path dir = BridgeDirectory.prepare(options.directory());
        try {
            lockChannel = FileChannel.open(dir.resolve("bridge.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                lock = lockChannel.tryLock();
            } catch (OverlappingFileLockException e) {
                lock = null;
            }
            if (lock == null) {
                throw new BridgeException(BridgeError.OTHER_INSTANCE, "outra instância do Studio já publica o Bridge");
            }
        } catch (BridgeException e) {
            throw e;
        } catch (IOException e) {
            throw new BridgeException(BridgeError.UNAVAILABLE, "trava de instância: " + e.getMessage());
        }
        bind(dir);
        writeDiscovery(dir);
        Thread accept = daemon(this::acceptLoop, "nekkora-bridge-accept");
        accept.start();
        LOG.info("Bridge publicado (" + transportUsed + ")");
    }

    private void bind(Path dir) throws BridgeException {
        Transport t = options.transport();
        Path sock = dir.resolve("studio.sock");
        if (t != Transport.TCP && sock.toString().length() <= 100) {
            try {
                Files.deleteIfExists(sock); // com a trava em mãos, um socket existente é órfão de uma execução anterior
                ServerSocketChannel ch = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
                ch.bind(UnixDomainSocketAddress.of(sock));
                try {
                    BridgeDirectory.verifyPrivate(sock);
                } catch (BridgeException e) {
                    BridgeDirectory.restrict(sock, false);
                    BridgeDirectory.verifyPrivate(sock);
                }
                server = ch;
                socketPath = sock;
                transportUsed = "unix";
                address = sock.toString();
                return;
            } catch (IOException | UnsupportedOperationException e) {
                if (t == Transport.UNIX) {
                    throw new BridgeException(BridgeError.UNAVAILABLE, "Unix Domain Socket indisponível: " + e.getMessage());
                }
                LOG.info("Unix Domain Socket indisponível; usando TCP local: " + e.getMessage());
                try {
                    Files.deleteIfExists(sock);
                } catch (IOException ignored) {
                    // sem socket
                }
            }
        }
        try {
            ServerSocketChannel ch = ServerSocketChannel.open();
            ch.bind(new InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0)); // somente loopback, porta dinâmica
            server = ch;
            transportUsed = "tcp";
            address = "127.0.0.1:" + ((InetSocketAddress) ch.getLocalAddress()).getPort();
        } catch (IOException e) {
            throw new BridgeException(BridgeError.UNAVAILABLE, "TCP local: " + e.getMessage());
        }
    }

    private void writeDiscovery(Path dir) throws BridgeException {
        Map<String, Object> d = Json.obj().put("protocol", PROTOCOL).put("transport", transportUsed).put("address", address)
                .put("instanceId", instanceId).put("pid", ProcessHandle.current().pid())
                .put("createdAt", java.time.Instant.now().toString())
                .put("secret", Base64.getUrlEncoder().withoutPadding().encodeToString(secret)).build();
        Path tmp = dir.resolve(DISCOVERY_FILE + ".tmp");
        Path f = dir.resolve(DISCOVERY_FILE);
        try {
            Files.deleteIfExists(tmp);
            Files.writeString(tmp, Json.write(d), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            BridgeDirectory.restrict(tmp, false);
            BridgeDirectory.verifyPrivate(tmp);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            BridgeDirectory.verifyPrivate(f);
            discoveryFile = f;
        } catch (BridgeException e) {
            throw e;
        } catch (IOException e) {
            throw new BridgeException(BridgeError.UNAVAILABLE, "arquivo de descoberta: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------------------ conexões

    private void acceptLoop() {
        while (!closed) {
            SocketChannel ch;
            try {
                ch = server.accept();
            } catch (IOException e) {
                if (!closed) {
                    LOG.log(Level.WARNING, "Bridge: accept falhou", e);
                }
                return;
            }
            daemon(() -> handshake(ch), "nekkora-bridge-handshake").start();
        }
    }

    private void handshake(SocketChannel ch) {
        Connection c = new Connection(ch);
        AtomicBoolean done = new AtomicBoolean();
        boolean reservedByMe = false;
        scheduler.schedule(() -> {
            if (!done.get()) {
                c.close(); // handshake parado não ocupa o servidor
            }
        }, HANDSHAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        try {
            String serverNonce = BridgeAuth.nonce();
            c.write(Json.obj().put("protocol", PROTOCOL).put("kind", "handshake").put("type", "bridge.challenge")
                    .put("payload", Json.obj().put("instanceId", instanceId).put("serverNonce", serverNonce).put("accepts", List.of(PROTOCOL))
                            .put("studio", Json.obj().put("version", options.studioVersion()).build()).build()).build());
            String raw = BridgeFrames.read(c.in);
            if (raw == null) {
                c.close();
                return;
            }
            Map<String, Object> hello = Json.parseObject(raw);
            if ("bridge.rejected".equals(Json.string(hello, "type"))) {
                LOG.warning("Host recusou o Studio: " + Json.string(Json.object(Json.object(hello, "payload"), "error"), "code"));
                c.close();
                return;
            }
            if (!PROTOCOL.equals(Json.string(hello, "protocol"))) {
                reject(c, BridgeError.PROTOCOL_UNSUPPORTED, "O Studio fala " + PROTOCOL + ".");
                return;
            }
            Map<String, Object> p = Json.object(hello, "payload");
            String clientNonce = Json.string(p, "clientNonce");
            String proof = Json.string(p, "proof");
            if (!"handshake".equals(Json.string(hello, "kind")) || !"bridge.hello".equals(Json.string(hello, "type")) || clientNonce == null
                    || !clientNonce.matches("[A-Za-z0-9_-]{43}") || proof == null || !instanceId.equals(Json.string(p, "instanceId"))) {
                reject(c, BridgeError.BAD_REQUEST, "Handshake inválido.");
                return;
            }
            if (!BridgeAuth.matches(BridgeAuth.clientProof(secret, instanceId, serverNonce, clientNonce), proof)) {
                LOG.warning("Bridge: tentativa de conexão com credencial inválida recusada");
                reject(c, BridgeError.AUTH_FAILED, "Credencial do Bridge inválida.");
                return;
            }
            Runnable gate = handshakeGate;
            if (gate != null) {
                gate.run();
            }
            if (!reserve(c)) {
                reject(c, BridgeError.BRIDGE_BUSY, "Outro navegador já está conectado a este Studio.");
                return;
            }
            reservedByMe = true;
            Runnable reserved = reservedGate;
            if (reserved != null) {
                reserved.run();
            }
            c.write(Json.obj().put("protocol", PROTOCOL).put("kind", "handshake").put("type", "bridge.welcome")
                    .put("payload", Json.obj().put("proof", BridgeAuth.serverProof(secret, instanceId, serverNonce, clientNonce))
                            .put("heartbeatMs", options.heartbeat().toMillis()).put("maxFrameBytes", BridgeFrames.MAX_FRAME_BYTES).build()).build());
            done.set(true);
            Map<String, Object> client = Json.object(p, "client");
            Map<String, Object> ext = Json.object(client, "extension");
            c.privacy = Json.string(client, "privacy");
            attach(c, new LinkStatus.ExtensionInfo(Json.string(ext, "version"), Json.string(ext, "buildMode"), c.privacy));
            reservedByMe = false; // a partir daqui o leitor libera a reserva ao terminar
        } catch (IOException | RuntimeException e) {
            LOG.fine("Bridge: handshake encerrado: " + e.getMessage());
            c.close();
        } finally {
            if (reservedByMe) {
                active.compareAndSet(c, null); // falhou depois da reserva: o Studio não fica ocupado
            }
            done.set(true);
        }
    }

    /**
     * Reserva atômica do controlador: só um handshake vence. Uma conexão anterior já fechada (canal perdido, leitor
     * ainda terminando) pode ser substituída; as mensagens dela passam a ser descartadas.
     */
    private boolean reserve(Connection c) {
        while (true) {
            Connection current = active.get();
            if (closed || current != null && current.open.get()) {
                return false;
            }
            if (active.compareAndSet(current, c)) {
                return true;
            }
        }
    }

    private void reject(Connection c, BridgeError code, String message) {
        try {
            c.write(Json.obj().put("protocol", PROTOCOL).put("kind", "handshake").put("type", "bridge.rejected")
                    .put("payload", Json.obj().put("error", Json.obj().put("code", code.name()).put("message", message).build()).build()).build());
        } catch (IOException ignored) {
            // o host pode já ter fechado
        }
        c.close();
    }

    private void attach(Connection c, LinkStatus.ExtensionInfo ext) {
        c.ready = true;
        setStatus(new LinkStatus(LinkStatus.State.CONNECTED, null, ext));
        long hb = options.heartbeat().toMillis();
        c.heartbeat = scheduler.scheduleWithFixedDelay(() -> {
            if (!c.open.get()) {
                return;
            }
            if (System.currentTimeMillis() - c.lastInbound > 3 * hb) {
                LOG.warning("Bridge: navegador sem sinal; canal encerrado");
                c.disconnectReason = "HEARTBEAT_LOST";
                c.close();
                return;
            }
            send(c, "bridge.heartbeat", Map.of(), Duration.ofMillis(3 * hb)).exceptionally(e -> null);
        }, hb, hb, TimeUnit.MILLISECONDS);
        daemon(() -> readLoop(c), "nekkora-bridge-reader").start();
    }

    private void readLoop(Connection c) {
        try {
            while (c.open.get()) {
                String raw = BridgeFrames.read(c.in);
                if (raw == null) {
                    break;
                }
                c.lastInbound = System.currentTimeMillis();
                Map<String, Object> msg = Json.parseObject(raw);
                java.util.function.BiConsumer<Map<String, Object>, Runnable> gate = dispatchGate;
                if (gate != null) {
                    gate.accept(msg, c::close);
                }
                onMessage(c, msg);
            }
        } catch (IOException | RuntimeException e) {
            LOG.fine("Bridge: leitura encerrada: " + e.getMessage());
        }
        detach(c);
    }

    private void onMessage(Connection c, Map<String, Object> msg) {
        if (!PROTOCOL.equals(Json.string(msg, "protocol")) || active.get() != c || !c.open.get()) {
            return; // só a conexão ativa fala pelo navegador
        }
        String kind = Json.string(msg, "kind");
        String type = Json.string(msg, "type");
        if ("response".equals(kind)) {
            String id = String.valueOf(Json.string(msg, "id"));
            Pending p = pending.get(id);
            if (p == null || p.owner() != c || !pending.remove(id, p)) {
                return; // resposta de outra conexão não resolve este comando
            }
            p.timeout().cancel(false);
            if (Json.bool(msg, "ok", false)) {
                Map<String, Object> payload = Json.object(msg, "payload");
                p.future().complete(payload == null ? Map.of() : payload);
            } else {
                Map<String, Object> e = Json.object(msg, "error");
                p.future().completeExceptionally(new BridgeCommandException(BridgeError.of(Json.string(e, "code")),
                        String.valueOf(Json.string(e, "message")), Json.string(e, "reason"), Json.bool(e, "retryable", false)));
            }
            return;
        }
        if (!"event".equals(kind) || type == null) {
            return;
        }
        Map<String, Object> payload = Json.object(msg, "payload");
        switch (type) {
            case "bridge.connected" -> {
                Map<String, Object> ext = Json.object(payload, "extension");
                setStatus(new LinkStatus(LinkStatus.State.CONNECTED, null,
                        new LinkStatus.ExtensionInfo(Json.string(ext, "version"), Json.string(ext, "buildMode"), c.privacy)));
            }
            case "bridge.disconnected" -> c.disconnectReason = Json.string(payload, "reason");
            default -> {
                if (type.startsWith("studio.recording.") && payload != null) {
                    BridgeEvent ev = new BridgeEvent(type, payload);
                    for (Listener l : listeners) {
                        try {
                            l.event(ev);
                        } catch (RuntimeException ex) {
                            LOG.log(Level.WARNING, "Ouvinte de evento falhou", ex);
                        }
                    }
                }
            }
        }
    }

    private void detach(Connection c) {
        c.close();
        if (c.heartbeat != null) {
            c.heartbeat.cancel(false);
        }
        for (Map.Entry<String, Pending> e : pending.entrySet()) {
            if (e.getValue().owner() == c && pending.remove(e.getKey(), e.getValue())) {
                e.getValue().timeout().cancel(false);
                e.getValue().future().completeExceptionally(new BridgeCommandException(BridgeError.LINK_LOST,
                        "A conexão com o navegador foi perdida antes da confirmação.", c.disconnectReason, true));
            }
        }
        if (!active.compareAndSet(c, null)) {
            return; // já substituída por outra conexão: o estado é dela
        }
        if (!closed) {
            setStatus(LinkStatus.of(LinkStatus.State.WAITING, c.disconnectReason != null ? c.disconnectReason : "LINK_LOST"));
        }
    }

    // ------------------------------------------------------------------------------------ BrowserLink

    @Override
    public LinkStatus status() {
        return status;
    }

    @Override
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    @Override
    public CompletableFuture<Map<String, Object>> send(String type, Map<String, Object> payload, Duration timeout) {
        return send(active.get(), type, payload, timeout);
    }

    /** Envia por uma conexão específica; a resposta só é aceita vinda dela. */
    private CompletableFuture<Map<String, Object>> send(Connection c, String type, Map<String, Object> payload, Duration timeout) {
        CompletableFuture<Map<String, Object>> f = new CompletableFuture<>();
        if (c == null || !c.ready || !c.open.get() || active.get() != c) {
            f.completeExceptionally(new BridgeCommandException(BridgeError.EXTENSION_UNAVAILABLE,
                    "O navegador não está conectado ao Studio.", null, true));
            return f;
        }
        String id = "c-" + ids.incrementAndGet();
        ScheduledFuture<?> t = scheduler.schedule(() -> {
            Pending p = pending.remove(id);
            if (p != null) {
                p.future().completeExceptionally(new BridgeCommandException(BridgeError.TIMEOUT,
                        "O navegador não confirmou a operação a tempo.", null, true));
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
        pending.put(id, new Pending(f, type, t, c));
        try {
            c.write(Json.obj().put("protocol", PROTOCOL).put("kind", "command").put("type", type).put("id", id)
                    .put("payload", payload == null ? Map.of() : payload).build());
        } catch (IOException | RuntimeException e) {
            c.disconnectReason = "WRITE_FAILED";
            detach(c);
        }
        return f;
    }

    // ------------------------------------------------------------------------------------ estado

    private void setStatus(LinkStatus s) {
        status = s;
        for (Listener l : listeners) {
            try {
                l.statusChanged(s);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Ouvinte de estado falhou", e);
            }
        }
    }

    /** Transporte efetivamente publicado ("unix"/"tcp"), ou null. */
    public String transport() {
        return transportUsed;
    }

    /** Identidade técnica desta instância (nunca exibida ao usuário). */
    public String instanceId() {
        return instanceId;
    }

    public Path discoveryFile() {
        return discoveryFile;
    }

    public Path socketPath() {
        return socketPath;
    }

    /** Encerra: fecha o canal, apaga o arquivo de descoberta e o socket, solta a trava e invalida o segredo. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Connection c = active.get();
        if (c != null) {
            c.disconnectReason = "STUDIO_CLOSED";
            detach(c);
        }
        try {
            if (server != null) {
                server.close();
            }
        } catch (IOException ignored) {
            // encerramento
        }
        release();
        scheduler.shutdownNow();
        setStatus(LinkStatus.of(LinkStatus.State.UNAVAILABLE, "CLOSED"));
    }

    private void release() {
        try {
            if (discoveryFile != null) {
                Files.deleteIfExists(discoveryFile);
            }
            if (socketPath != null) {
                Files.deleteIfExists(socketPath);
            }
        } catch (IOException e) {
            LOG.warning("Bridge: limpeza incompleta: " + e.getMessage());
        }
        Arrays.fill(secret, (byte) 0); // o segredo deixa de existir: o arquivo apagado não pode ser reaproveitado
        try {
            if (lock != null) {
                lock.release();
            }
            if (lockChannel != null) {
                lockChannel.close();
            }
        } catch (IOException ignored) {
            // encerramento
        }
    }
}
