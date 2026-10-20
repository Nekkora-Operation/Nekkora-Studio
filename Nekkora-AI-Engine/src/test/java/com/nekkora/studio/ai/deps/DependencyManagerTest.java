package com.nekkora.studio.ai.deps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PACKAGING-001 — Dependency Manager: contrato, artefatos, diagnóstico e (com Python e rede reais) a preparação. */
class DependencyManagerTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC);
    static final String ID = "3f1c2a9e-5b7d-4e21-9a0b-6c8d7e5f4a31";

    @TempDir
    Path tmp;

    // ------------------------------------------------------------------------------------------- sem rede

    @Test
    @DisplayName("requisito: só nekkora-rpa com versão exata; extras; chave de pasta separa 0.2.0 e 0.3.0; lido do manifest.json do projeto")
    void requisito() throws IOException {
        FrameworkRequirement r = FrameworkRequirement.parse("nekkora-rpa[excel]==0.3.0").orElseThrow();
        assertEquals("0.3.0", r.version());
        assertTrue(r.excel());
        assertEquals("nekkora-rpa[excel]==0.3.0", r.pip());
        assertEquals("0.3.0-excel", r.key());
        assertEquals("0.2.0", FrameworkRequirement.parse("nekkora-rpa==0.2.0").orElseThrow().key());
        for (String bad : List.of("nekkora-rpa>=0.3.0", "nekkora_rpa==0.3.0", "outro==1.0.0", "nekkora-rpa==0.3", "nekkora-rpa[excel]==0.3.0 ; x",
                "nekkora-rpa @ https://exemplo.test/x.whl", "")) {
            assertTrue(FrameworkRequirement.parse(bad).isEmpty(), bad);
        }
        Files.writeString(tmp.resolve("manifest.json"), "{\"framework\": \"nekkora-rpa[excel]==0.3.0\"}", StandardCharsets.UTF_8);
        assertEquals(r, FrameworkRequirement.ofGeneratedProject(tmp).orElseThrow());
        assertTrue(FrameworkRequirement.ofGeneratedProject(tmp.resolve("nada")).isEmpty());
    }

    @Test
    @DisplayName("artefatos homologados: 0.2.0 e 0.3.0 com SHA-256 que confere com os bytes do jar; arquivo adulterado → HASH_MISMATCH e apagado")
    void artefatos() throws Exception {
        FrameworkArtifacts a = FrameworkArtifacts.bundled();
        assertEquals(List.of("0.2.0", "0.3.0"), a.versions());
        for (String v : a.versions()) {
            FrameworkArtifacts.Artifact art = a.forVersion(v).orElseThrow();
            Path w = a.materializeWheel(art, tmp.resolve("c"));
            assertEquals(art.sha256(), FrameworkArtifacts.sha256(w));
            assertEquals(art.bytes(), Files.size(w));
            assertEquals("nekkora_rpa-" + v + "-py3-none-any.whl", w.getFileName().toString());
            assertTrue(com.nekkora.studio.ai.capability.FrameworkCapabilityCatalog.forVersion(v).isPresent(), "catálogo da mesma versão");
            assertEquals(v, com.nekkora.studio.ai.capability.FrameworkCapabilityCatalog.forVersion(v).orElseThrow().version());
        }
        a.materializeLock(a.dependencies(), tmp.resolve("c"));
        a.materializeLock(a.tools(), tmp.resolve("c"));
        Path copy = tmp.resolve("c").resolve(a.forVersion("0.3.0").orElseThrow().wheel());
        byte[] b = Files.readAllBytes(copy);
        b[b.length / 2] ^= 1;
        Files.write(copy, b);
        DependencyException e = assertThrows(DependencyException.class, () -> FrameworkArtifacts.verify(copy, a.forVersion("0.3.0").orElseThrow().sha256()));
        assertEquals(Diagnosis.HASH_MISMATCH, e.diagnosis());
        assertFalse(Files.exists(copy), "o artefato adulterado não fica no cache");
        assertTrue(com.nekkora.studio.ai.capability.FrameworkCapabilityCatalog.forVersion("9.9.9").isEmpty());
    }

    @Test
    @DisplayName("diagnóstico único pela saída do pip; logs higienizados (pasta do usuário, credencial em URL)")
    void diagnosticos() {
        assertEquals(Diagnosis.HASH_MISMATCH, DependencyManager.diagnose("ERROR: THESE PACKAGES DO NOT MATCH THE HASHES FROM THE REQUIREMENTS FILE."));
        assertEquals(Diagnosis.NETWORK_UNAVAILABLE, DependencyManager.diagnose("WARNING: Retrying ... NewConnectionError: Failed to establish a new connection"));
        assertEquals(Diagnosis.NETWORK_UNAVAILABLE, DependencyManager.diagnose("ERROR: Could not find a version that satisfies the requirement x (from versions: none)\n"
                + "ERROR: No matching distribution found for x"));
        assertEquals(Diagnosis.PROXY_OR_SSL, DependencyManager.diagnose("SSLError(SSLCertVerificationError(1, '[SSL: CERTIFICATE_VERIFY_FAILED]"));
        assertEquals(Diagnosis.PROXY_OR_SSL, DependencyManager.diagnose("ProxyError('Cannot connect to proxy.')"));
        assertEquals(Diagnosis.DISK_FULL, DependencyManager.diagnose("OSError: [Errno 28] No space left on device"));
        assertEquals(Diagnosis.PERMISSION_DENIED, DependencyManager.diagnose("PermissionError: [Errno 13] Permission denied: 'x'"));
        assertEquals(Diagnosis.INSTALL_FAILED, DependencyManager.diagnose("ERROR: algo inesperado"));
        String home = System.getProperty("user.home");
        String s = ProcessRunner.sanitize("Looking in indexes: https://usuario:senha@espelho.test/simple " + home + "\\pasta");
        assertFalse(s.contains("senha") || s.contains("usuario:") || s.contains(home), s);
        assertTrue(s.contains("https://***@espelho.test") && s.contains("~"), s);
        assertTrue(PythonLocator.atLeast312("3.12.10") && PythonLocator.atLeast312("3.13.0") && !PythonLocator.atLeast312("3.11.9"));
    }

    @Test
    @DisplayName("versão não homologada → UNSUPPORTED_VERSION; sem Python → PYTHON_MISSING; ambiente incompleto → ENV_CORRUPTED com plano de refazer")
    void avaliacaoSemInstalar() throws IOException {
        DependencyManager dm = new DependencyManager(tmp, FrameworkArtifacts.bundled(), CLOCK);
        assertEquals(Diagnosis.UNSUPPORTED_VERSION, dm.assess(ID, FrameworkRequirement.parse("nekkora-rpa==9.9.9").orElseThrow(), Optional.empty(), Optional.empty())
                .diagnosis());
        String old = System.getProperty("nekkora.python.base");
        System.setProperty("nekkora.python.base", tmp.resolve("nao-existe").resolve("python.exe").toString());
        try {
            DependencyManager.Assessment a = dm.assess(ID, FrameworkRequirement.parse("nekkora-rpa[excel]==0.3.0").orElseThrow(), Optional.empty(), Optional.empty());
            assertEquals(Diagnosis.PYTHON_MISSING, a.diagnosis(), a.detail());
            assertTrue(a.plan().isEmpty() && a.environment().isEmpty());
        } finally {
            restore(old);
        }
        Optional<PythonLocator.Candidate> py = PythonLocator.find(Optional.empty());
        Assumptions.assumeTrue(py.isPresent(), "sem Python 3.12+ nesta máquina");
        FrameworkRequirement req = FrameworkRequirement.parse("nekkora-rpa[excel]==0.3.0").orElseThrow();
        Files.createDirectories(dm.environmentDir(ID, req).resolve("venv"));
        DependencyManager.Assessment partial = dm.assess(ID, req, Optional.empty(), Optional.empty());
        assertEquals(Diagnosis.ENV_CORRUPTED, partial.diagnosis());
        DependencyManager.Plan plan = partial.plan().orElseThrow();
        String text = String.join("\n", plan.summary());
        assertTrue(text.contains("nekkora-rpa 0.3.0") && text.contains("excel") && text.contains("SHA-256") && text.contains("pypi.org")
                && text.contains(dm.environmentDir(ID, req).resolve("venv").toString()) && text.contains("não é alterado"), text);
        assertThrows(IllegalArgumentException.class, () -> dm.environmentDir("../fora", req));
    }

    @Test
    @DisplayName("caminho: pasta curta do ambiente (12 hex + versão); acima do limite do Windows sem caminhos longos → PATH_TOO_LONG antes de começar")
    void caminhoLongo() {
        DependencyManager dm = new DependencyManager(Path.of("C:\\Users\\pessoa\\AppData\\Local\\Nekkora\\Studio\\data"), FrameworkArtifacts.bundled(), CLOCK);
        Path dir = dm.environmentDir(ID, FrameworkRequirement.parse("nekkora-rpa[excel]==0.3.0").orElseThrow());
        assertTrue(dir.toString().endsWith("ambientes\\3f1c2a9e5b7d\\0.3.0-excel") || dir.toString().endsWith("ambientes/3f1c2a9e5b7d/0.3.0-excel"), dir.toString());
        assertTrue(DependencyManager.fitsWindowsPath(Path.of("C:\\Users\\pessoa\\AppData\\Local\\Nekkora\\Studio\\data\\ambientes\\3f1c2a9e5b7d\\0.3.0-excel"), true, false));
        Path deep = Path.of("C:\\" + "pasta-muito-longa\\".repeat(8) + "ambientes\\3f1c2a9e5b7d\\0.3.0-excel");
        assertFalse(DependencyManager.fitsWindowsPath(deep, true, false));
        assertTrue(DependencyManager.fitsWindowsPath(deep, true, true), "com LongPathsEnabled, cabe");
        assertTrue(DependencyManager.fitsWindowsPath(deep, false, false), "fora do Windows não há esse limite");
    }

    // ------------------------------------------------------------------------------------------- Python e rede reais

    private static Optional<PythonLocator.Candidate> realPythonAndNetwork() {
        Optional<PythonLocator.Candidate> py = PythonLocator.find(Optional.empty());
        if (py.isEmpty()) return py;
        try {
            HttpResponse<Void> r = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
                    .send(HttpRequest.newBuilder(URI.create("https://pypi.org/simple/openpyxl/")).timeout(Duration.ofSeconds(10)).GET().build(),
                            HttpResponse.BodyHandlers.discarding());
            return r.statusCode() == 200 ? py : Optional.empty();
        } catch (IOException | InterruptedException e) {
            return Optional.empty();
        }
    }

    @Test
    @DisplayName("[Python e PyPI reais] prepara 0.3.0[excel] e 0.2.0 em venvs SEPARADAS (caminho com espaço e acento), reaproveita, detecta dano e versão errada")
    void preparacaoReal() throws Exception {
        Optional<PythonLocator.Candidate> py = realPythonAndNetwork();
        Assumptions.assumeTrue(py.isPresent(), "sem Python 3.12+ ou sem acesso ao PyPI");
        Path data = tmp.resolve("dados do Studio — ação");
        DependencyManager dm = new DependencyManager(data, FrameworkArtifacts.bundled(), CLOCK);
        FrameworkRequirement v3 = FrameworkRequirement.parse("nekkora-rpa[excel]==0.3.0").orElseThrow();
        FrameworkRequirement v2 = FrameworkRequirement.parse("nekkora-rpa==0.2.0").orElseThrow();
        DependencyManager.Assessment before = dm.assess(ID, v3, Optional.empty(), Optional.empty());
        assertEquals(Diagnosis.NEEDS_INSTALL, before.diagnosis(), before.detail());
        List<DependencyManager.Progress> seen = new ArrayList<>();
        IntegrityVerifier.Result r = dm.install(before.plan().orElseThrow(), seen::add, () -> false);
        assertTrue(r.ok(), r.detail());
        assertEquals("0.3.0", r.environment().orElseThrow().framework());
        assertTrue(seen.stream().map(DependencyManager.Progress::step).distinct().count() >= 6, "todas as etapas visíveis");
        assertTrue(Files.isRegularFile(dm.environmentDir(ID, v3).resolve("ambiente.json")));
        String log = Files.readString(dm.environmentDir(ID, v3).resolve("instalacao.log"), StandardCharsets.UTF_8);
        assertFalse(log.contains(System.getProperty("user.home")), "log higienizado");
        DependencyManager.Assessment again = dm.assess(ID, v3, Optional.empty(), Optional.empty());
        assertEquals(Diagnosis.READY, again.diagnosis(), "reaproveita o ambiente: nada a instalar");
        assertTrue(again.environment().orElseThrow().python().startsWith(data));
        // 0.2.0 em OUTRA venv; a 0.3.0 continua intacta
        IntegrityVerifier.Result r2 = dm.install(dm.assess(ID, v2, Optional.empty(), Optional.empty()).plan().orElseThrow(), p -> { }, () -> false);
        assertTrue(r2.ok(), r2.detail());
        assertEquals("0.2.0", r2.environment().orElseThrow().framework());
        assertEquals(Diagnosis.READY, dm.assess(ID, v3, Optional.empty(), Optional.empty()).diagnosis());
        // o Python da 0.2.0 indicado para um projeto 0.3.0 → VERSION_MISMATCH (o catálogo 0.3.0 nunca valida o runtime 0.2.0)
        DependencyManager.Assessment wrong = dm.assess(ID, v3, Optional.of(r2.environment().orElseThrow().python()), Optional.empty());
        assertEquals(Diagnosis.VERSION_MISMATCH, wrong.diagnosis(), wrong.detail());
        // venv danificada → ENV_CORRUPTED com plano de refazer
        Files.delete(DependencyManager.pythonOf(dm.environmentDir(ID, v2).resolve("venv")));
        DependencyManager.Assessment broken = dm.assess(ID, v2, Optional.empty(), Optional.empty());
        assertEquals(Diagnosis.ENV_CORRUPTED, broken.diagnosis());
        assertTrue(broken.plan().isPresent());
    }

    @Test
    @DisplayName("[Python e PyPI reais] cancelar no meio não deixa ambiente 'pronto'; índice adversarial → HASH_MISMATCH; índice fora do ar → NETWORK_UNAVAILABLE; disco → DISK_FULL")
    void negativosReais() throws Exception {
        Optional<PythonLocator.Candidate> py = realPythonAndNetwork();
        Assumptions.assumeTrue(py.isPresent(), "sem Python 3.12+ ou sem acesso ao PyPI");
        FrameworkRequirement req = FrameworkRequirement.parse("nekkora-rpa[excel]==0.3.0").orElseThrow();
        // cancelamento durante as dependências
        DependencyManager dm = new DependencyManager(tmp.resolve("cancela"), FrameworkArtifacts.bundled(), CLOCK);
        AtomicBoolean cancel = new AtomicBoolean();
        DependencyException c = assertThrows(DependencyException.class, () -> dm.install(dm.assess(ID, req, Optional.empty(), Optional.empty()).plan().orElseThrow(),
                p -> {
                    if (p.step() >= 3) cancel.set(true);
                }, cancel::get));
        assertEquals(Diagnosis.CANCELLED, c.diagnosis());
        assertFalse(Files.exists(dm.environmentDir(ID, req).resolve("ambiente.json")), "nenhum registro de pronto");
        assertEquals(Diagnosis.ENV_CORRUPTED, dm.assess(ID, req, Optional.empty(), Optional.empty()).diagnosis(), "a próxima preparação refaz");
        // índice adversarial: serve arquivos com o nome certo e o conteúdo errado
        HttpServer evil = adversarialIndex(FrameworkArtifacts.bundled());
        try {
            DependencyManager bad = new DependencyManager(tmp.resolve("mau"), FrameworkArtifacts.bundled(), CLOCK)
                    .withDependencyIndex("http://127.0.0.1:" + evil.getAddress().getPort() + "/simple/");
            DependencyException h = assertThrows(DependencyException.class,
                    () -> bad.install(bad.assess(ID, req, Optional.empty(), Optional.empty()).plan().orElseThrow(), p -> { }, () -> false));
            assertEquals(Diagnosis.HASH_MISMATCH, h.diagnosis(), h.getMessage());
            assertFalse(Files.exists(bad.environmentDir(ID, req).resolve("ambiente.json")));
        } finally {
            evil.stop(0);
        }
        // índice inalcançável (porta fechada)
        DependencyManager off = new DependencyManager(tmp.resolve("off"), FrameworkArtifacts.bundled(), CLOCK).withDependencyIndex("http://127.0.0.1:9/simple/");
        DependencyException n = assertThrows(DependencyException.class,
                () -> off.install(off.assess(ID, req, Optional.empty(), Optional.empty()).plan().orElseThrow(), p -> { }, () -> false));
        assertEquals(Diagnosis.NETWORK_UNAVAILABLE, n.diagnosis(), n.getMessage());
        // disco: limite acima do espaço livre → recusa ANTES de começar
        DependencyManager full = new DependencyManager(tmp.resolve("cheio"), FrameworkArtifacts.bundled(), CLOCK).withMinFreeBytes(Long.MAX_VALUE);
        DependencyException d = assertThrows(DependencyException.class,
                () -> full.install(full.assess(ID, req, Optional.empty(), Optional.empty()).plan().orElseThrow(), p -> { }, () -> false));
        assertEquals(Diagnosis.DISK_FULL, d.diagnosis());
    }

    /** Índice "simple" local que responde a QUALQUER pacote do lock com um wheel de nome certo e bytes falsos. */
    static HttpServer adversarialIndex(FrameworkArtifacts a) throws IOException {
        Map<String, String> versions = new java.util.HashMap<>();
        try (var in = FrameworkArtifacts.class.getResourceAsStream(FrameworkArtifacts.BASE + a.dependencies().resource())) {
            Matcher m = Pattern.compile("(?m)^([A-Za-z0-9._-]+)==([^ \\\\]+)").matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            while (m.find()) versions.put(m.group(1).toLowerCase().replaceAll("[-_.]+", "-"), m.group(2));
        }
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            byte[] body;
            String type;
            Matcher m = Pattern.compile("^/simple/([^/]+)/?$").matcher(path);
            if (m.matches() && versions.containsKey(m.group(1))) {
                String file = m.group(1).replace('-', '_') + "-" + versions.get(m.group(1)) + "-py3-none-any.whl";
                body = ("<!DOCTYPE html><html><body><a href=\"/files/" + file + "\">" + file + "</a></body></html>").getBytes(StandardCharsets.UTF_8);
                type = "text/html";
            } else if (path.startsWith("/files/")) {
                body = "isto nao e um wheel".getBytes(StandardCharsets.UTF_8);
                type = "application/octet-stream";
            } else {
                ex.sendResponseHeaders(404, -1);
                ex.close();
                return;
            }
            ex.getResponseHeaders().add("Content-Type", type);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        s.start();
        return s;
    }

    private static void restore(String old) {
        if (old == null) System.clearProperty("nekkora.python.base");
        else System.setProperty("nekkora.python.base", old);
    }
}
