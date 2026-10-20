package com.nekkora.studio.ai.deps;

import com.nekkora.studio.ai.pycheck.PythonChecks;
import com.nekkora.studio.bridge.json.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * PACKAGING-001 — Nekkora Dependency Manager: prepara o ambiente Python EXCLUSIVO de cada automação, na versão exata
 * que o código gerado exige, sem Python global, sem clone e sem token.
 *
 * <ul>
 *   <li><b>Onde</b>: {@code <dados do Studio>/ambientes/<automação>/nekkora-rpa-<versão>[-extras]/venv} — fora do projeto,
 *   do checkout e de pastas sincronizadas; 0.2.0 e 0.3.0 nunca dividem venv.</li>
 *   <li><b>O quê</b>: o wheel HOMOLOGADO (SHA-256 do manifesto) e as dependências/ferramentas por {@code --require-hashes}
 *   do PyPI oficial ({@code --isolated}: nem {@code PIP_*} nem pip.conf mudam o índice); nada do projeto é enviado.</li>
 *   <li><b>Quando</b>: só depois do consentimento da pessoa ({@link #plan}); a IA nunca instala nem escolhe endereço.</li>
 *   <li><b>Recuperação</b>: o registro {@code ambiente.json} só é gravado no fim; ambiente sem registro (cancelado,
 *   queda, disco cheio) é apagado e refeito na próxima preparação.</li>
 * </ul>
 */
public final class DependencyManager {

    public static final String RECORD_SCHEMA = "nekkora-studio/python-environment";
    /** Espaço livre mínimo antes de começar (playwright + mypy + ruff + cache do pip). */
    static final long MIN_FREE_BYTES = 600L * 1024 * 1024;
    static final Duration STEP_TIMEOUT = Duration.ofMinutes(15);

    private final Path root;
    private final FrameworkArtifacts artifacts;
    private final Clock clock;
    private String dependencyIndex;
    private long minFreeBytes = MIN_FREE_BYTES;

    public DependencyManager(Path studioData, FrameworkArtifacts artifacts, Clock clock) {
        this.root = studioData.resolve("ambientes");
        this.artifacts = artifacts;
        this.clock = clock;
        this.dependencyIndex = artifacts.dependencies().index();
    }

    /** Testes: índice das dependências (ex.: índice local adversarial) e limite de disco. */
    DependencyManager withDependencyIndex(String url) {
        this.dependencyIndex = url;
        return this;
    }

    DependencyManager withMinFreeBytes(long bytes) {
        this.minFreeBytes = bytes;
        return this;
    }

    public FrameworkArtifacts artifacts() {
        return artifacts;
    }

    // ------------------------------------------------------------------------------------------- avaliação

    /** Estado do ambiente para o requisito: pronto (com o interpretador), ou o diagnóstico e o plano de preparação. */
    public record Assessment(FrameworkRequirement requirement, Diagnosis diagnosis, String detail, Optional<PythonChecks.Environment> environment,
                             Optional<Plan> plan, Path environmentDir, boolean configured) {
        public boolean ready() {
            return diagnosis == Diagnosis.READY;
        }
    }

    /** O que será feito, mostrado ANTES na confirmação (pacote, versão, extras, origem, destino, efeitos). */
    public record Plan(FrameworkRequirement requirement, FrameworkArtifacts.Artifact artifact, PythonLocator.Candidate base, Path environmentDir,
                       List<String> summary) {
        public Plan {
            summary = List.copyOf(summary);
        }
    }

    /** Pasta CURTA (12 hex da automação + versão): o Windows limita caminhos a 260 caracteres sem LongPathsEnabled. */
    public Path environmentDir(String automationId, FrameworkRequirement req) {
        if (!automationId.matches("[A-Za-z0-9-]{1,64}")) throw new IllegalArgumentException("automationId");
        String id = automationId.replace("-", "");
        return root.resolve(id.substring(0, Math.min(12, id.length()))).resolve(req.key());
    }

    /** Arquivo mais fundo do maior pacote (playwright/driver/…) a partir de site-packages, com folga. */
    static final int DEEPEST_IN_SITE_PACKAGES = 110;
    static final int WINDOWS_MAX_PATH = 259;

    /** O caminho mais fundo da instalação caberia no limite do Windows (sem caminhos longos habilitados)? Recebe o caminho ABSOLUTO como texto. */
    static boolean fitsWindowsPath(String absoluteEnvironmentDir, boolean windows, boolean longPathsEnabled) {
        if (!windows || longPathsEnabled) return true;
        int deepest = absoluteEnvironmentDir.length() + "\\venv\\Lib\\site-packages\\".length() + DEEPEST_IN_SITE_PACKAGES;
        return deepest <= WINDOWS_MAX_PATH;
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    /** {@code LongPathsEnabled} do Windows (consulta ao registro sem shell); qualquer falha conta como desligado. */
    private static boolean longPathsEnabled() {
        if (!windows()) return true;
        try {
            ProcessRunner.Result r = ProcessRunner.runRaw(List.of("reg", "query", "HKLM\\SYSTEM\\CurrentControlSet\\Control\\FileSystem", "/v",
                    "LongPathsEnabled"), Duration.ofSeconds(10));
            return r.exit() == 0 && r.output().matches("(?s).*LongPathsEnabled\\s+REG_DWORD\\s+0x1\\b.*");
        } catch (IOException e) {
            return false;
        }
    }

    public static Path pythonOf(Path venv) {
        Path win = venv.resolve("Scripts").resolve("python.exe");
        return Files.isRegularFile(win) || System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win") ? win
                : venv.resolve("bin").resolve("python");
    }

    /**
     * Avalia SEM instalar nada (Desenvolver/Validar/Executar): o Python indicado em Opções avançadas tem precedência e é
     * conferido contra o requisito; senão, o ambiente da automação. Sem rede.
     */
    public Assessment assess(String automationId, FrameworkRequirement req, Optional<Path> configuredPython, Optional<Path> chosenBase) {
        Path dir = environmentDir(automationId, req);
        if (configuredPython.isPresent()) {
            IntegrityVerifier.Result r = IntegrityVerifier.verify(configuredPython.get(), req, artifacts, false);
            return new Assessment(req, r.diagnosis(), "Python indicado em Opções avançadas: " + r.detail(), r.environment(), Optional.empty(), dir, true);
        }
        Optional<FrameworkArtifacts.Artifact> artifact = artifacts.forVersion(req.version());
        if (artifact.isEmpty()) {
            return new Assessment(req, Diagnosis.UNSUPPORTED_VERSION, req.pip() + " não está entre as versões homologadas deste Studio "
                    + artifacts.versions(), Optional.empty(), Optional.empty(), dir, false);
        }
        Optional<Map<String, Object>> record = readRecord(dir);
        if (record.isPresent() && recordMatches(record.get(), req, artifact.get())) {
            IntegrityVerifier.Result r = IntegrityVerifier.verify(pythonOf(dir.resolve("venv")), req, artifacts, false);
            if (r.ok()) return new Assessment(req, Diagnosis.READY, r.detail(), r.environment(), Optional.empty(), dir, false);
            return withPlan(req, artifact.get(), dir, Diagnosis.ENV_CORRUPTED, r.detail(), chosenBase);
        }
        boolean partial = Files.exists(dir) && record.isEmpty();
        return withPlan(req, artifact.get(), dir, partial ? Diagnosis.ENV_CORRUPTED : Diagnosis.NEEDS_INSTALL,
                partial ? "preparação anterior interrompida ou incompleta (o ambiente será refeito do zero)" : "a venv desta automação ainda não existe", chosenBase);
    }

    private Assessment withPlan(FrameworkRequirement req, FrameworkArtifacts.Artifact artifact, Path dir, Diagnosis d, String detail, Optional<Path> chosenBase) {
        Optional<PythonLocator.Candidate> base = PythonLocator.find(chosenBase);
        if (base.isEmpty()) return new Assessment(req, Diagnosis.PYTHON_MISSING, "nenhum Python ≥ 3.12 com venv encontrado", Optional.empty(), Optional.empty(), dir, false);
        return new Assessment(req, d, detail, Optional.empty(), Optional.of(plan(req, artifact, base.get(), dir)), dir, false);
    }

    Plan plan(FrameworkRequirement req, FrameworkArtifacts.Artifact artifact, PythonLocator.Candidate base, Path dir) {
        List<String> s = new ArrayList<>();
        s.add("Pacote: nekkora-rpa " + artifact.version() + (req.extras().isEmpty() ? "" : " com o extra " + String.join(", ", req.extras()))
                + " — exatamente o que o código gerado exige (" + req.pip() + ").");
        s.add(artifact.source() == FrameworkArtifacts.Source.BUNDLED
                ? "Origem do framework: artefato homologado embutido no Studio (commit " + artifact.commit().substring(0, 7) + ", SHA-256 "
                        + artifact.sha256().substring(0, 12) + "…); não é baixado."
                : "Origem do framework: índice " + artifact.index() + ", conferido pelo SHA-256 " + artifact.sha256().substring(0, 12) + "….");
        s.add("Dependências e ferramentas de verificação: do PyPI oficial (" + dependencyIndex + "), versões exatas conferidas por hash — "
                + "playwright, openpyxl, ruff " + artifacts.ruff() + ", mypy " + artifacts.mypy() + " e o que elas exigem (cerca de 150 MB).");
        s.add("Python base: " + base.python() + " (" + base.version() + ", " + base.source() + ") — não é alterado.");
        s.add("Destino: " + dir.resolve("venv") + " (venv exclusiva desta automação; nada vai para o Python global nem para a pasta do projeto).");
        s.add("Nada do projeto (.nekkora, PDD, planilhas, credenciais) é enviado ao índice de pacotes. Não instala navegadores.");
        return new Plan(req, artifact, base, dir, s);
    }

    @SuppressWarnings("unchecked")
    private static boolean recordMatches(Map<String, Object> r, FrameworkRequirement req, FrameworkArtifacts.Artifact artifact) {
        return RECORD_SCHEMA.equals(r.get("schema")) && req.pip().equals(r.get("requirement")) && artifact.sha256().equals(r.get("wheelSha256"));
    }

    private static Optional<Map<String, Object>> readRecord(Path dir) {
        Path f = dir.resolve("ambiente.json");
        if (!Files.isRegularFile(f)) return Optional.empty();
        try {
            return Optional.of(Json.parseObject(Files.readString(f, StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------------------------------- instalação

    /** Etapa visível na janela (sem valores; caminhos com a pasta do usuário trocada por ~). */
    public record Progress(int step, int steps, String label, String line) {}

    /**
     * PackageInstaller: executa o plano CONSENTIDO, fora da EDT, sem shell. Cancelar derruba o pip; o ambiente
     * incompleto fica sem registro e é refeito na próxima vez. Termina com a verificação completa (pip check, metadados,
     * import, esquemas, ferramentas e sonda contra o catálogo da versão).
     */
    public IntegrityVerifier.Result install(Plan plan, Consumer<Progress> progress, BooleanSupplier cancelled) throws DependencyException {
        Path dir = plan.environmentDir();
        Path venv = dir.resolve("venv");
        Path cache = dir.resolve("artefatos");
        Path log = dir.resolve("instalacao.log");
        int steps = 6;
        try {
            Files.createDirectories(dir.getParent());
        } catch (IOException e) {
            throw new DependencyException(Diagnosis.PERMISSION_DENIED, "a pasta de ambientes não pode ser criada");
        }
        if (!fitsWindowsPath(dir.toAbsolutePath().toString(), windows(), longPathsEnabled())) {
            throw new DependencyException(Diagnosis.PATH_TOO_LONG, "pasta do ambiente com " + dir.toAbsolutePath().toString().length() + " caracteres");
        }
        try {
            if (dir.getParent().toFile().getUsableSpace() < minFreeBytes) {
                throw new DependencyException(Diagnosis.DISK_FULL, "espaço livre abaixo de " + (minFreeBytes >> 20) + " MB");
            }
            progress.accept(new Progress(1, steps, "Conferindo os artefatos homologados", ""));
            deleteTree(dir); // ambiente incompleto/antigo deste requisito: refeito do zero
            Files.createDirectories(cache);
            Path wheel = plan.artifact().source() == FrameworkArtifacts.Source.BUNDLED ? artifacts.materializeWheel(plan.artifact(), cache) : null;
            Path deps = artifacts.materializeLock(artifacts.dependencies(), cache);
            Path tools = artifacts.materializeLock(artifacts.tools(), cache);
            check(cancelled);
            StringBuilder all = new StringBuilder();
            progress.accept(new Progress(2, steps, "Criando a venv da automação", ""));
            step(List.of(plan.base().python().toString(), "-m", "venv", "venv"), dir, cancelled, progress, 2, steps, "Criando a venv da automação", all);
            Path py = pythonOf(venv);
            List<String> pip = new ArrayList<>(List.of(py.toString(), "-I", "-m", "pip", "--isolated", "--disable-pip-version-check", "install", "--no-input"));
            progress.accept(new Progress(3, steps, "Instalando as dependências (por hash)", ""));
            step(concat(pip, "--require-hashes", "--only-binary=:all:", "--index-url", dependencyIndex, "-r", rel(dir, deps)), dir, cancelled, progress, 3, steps,
                    "Instalando as dependências (por hash)", all);
            progress.accept(new Progress(4, steps, "Instalando ruff e mypy (por hash)", ""));
            step(concat(pip, "--require-hashes", "--only-binary=:all:", "--index-url", dependencyIndex, "-r", rel(dir, tools)), dir, cancelled, progress, 4, steps,
                    "Instalando ruff e mypy (por hash)", all);
            progress.accept(new Progress(5, steps, "Instalando " + plan.requirement().pip(), ""));
            Path req = cache.resolve("nekkora-rpa.txt");
            // URI codificada: caminho com espaço/acento não quebra a linha do arquivo de requisitos
            String spec = wheel != null ? "nekkora-rpa @ " + wheel.toAbsolutePath().toUri() : "nekkora-rpa==" + plan.artifact().version();
            Files.writeString(req, spec + " --hash=sha256:" + plan.artifact().sha256() + "\n", StandardCharsets.UTF_8);
            List<String> main = concat(pip, "--no-deps", "--require-hashes", "--no-cache-dir");
            main.addAll(wheel != null ? List.of("--no-index") : List.of("--index-url", plan.artifact().index()));
            main.addAll(List.of("-r", rel(dir, req)));
            step(main, dir, cancelled, progress, 5, steps, "Instalando " + plan.requirement().pip(), all);
            progress.accept(new Progress(6, steps, "Verificando (pip check, versão, import, esquemas, sonda do catálogo " + plan.requirement().version() + ")", ""));
            IntegrityVerifier.Result v = IntegrityVerifier.verify(py, plan.requirement(), artifacts, true);
            Files.writeString(log, all.toString(), StandardCharsets.UTF_8);
            if (!v.ok()) throw new DependencyException(v.diagnosis(), v.detail());
            writeRecord(plan, v);
            return v;
        } catch (IOException e) {
            throw new DependencyException(ioDiagnosis(e), "falha de arquivo: " + e.getClass().getSimpleName() + " " + ProcessRunner.sanitize(String.valueOf(e.getMessage())));
        }
    }

    private static void check(BooleanSupplier cancelled) throws DependencyException {
        if (cancelled.getAsBoolean()) throw new DependencyException(Diagnosis.CANCELLED, "cancelado pela pessoa");
    }

    private void step(List<String> cmd, Path dir, BooleanSupplier cancelled, Consumer<Progress> progress, int n, int steps, String label, StringBuilder all)
            throws IOException, DependencyException {
        check(cancelled);
        all.append("$ ").append(ProcessRunner.sanitize(String.join(" ", cmd.subList(Math.min(1, cmd.size()), cmd.size())))).append('\n');
        // diretório de trabalho = pasta do ambiente e argumentos RELATIVOS em ASCII: o lançador python.exe de uma venv no Windows
        // repassa os argumentos perdendo caracteres fora do ASCII (pasta com acento/travessão chegava ao pip com "?")
        ProcessRunner.Result r = ProcessRunner.run(cmd, dir, STEP_TIMEOUT, cancelled, line -> progress.accept(new Progress(n, steps, label, line)));
        all.append(r.output()).append('\n');
        if (r.cancelled()) throw new DependencyException(Diagnosis.CANCELLED, "cancelado pela pessoa");
        if (r.timedOut()) throw new DependencyException(Diagnosis.TIMEOUT, label + ": prazo de " + STEP_TIMEOUT.toMinutes() + " min");
        if (r.exit() != 0) {
            try {
                Files.writeString(dir.resolve("instalacao.log"), all.toString(), StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                // o diagnóstico continua valendo
            }
            throw new DependencyException(diagnose(r.output()), label + ": " + lastMeaningful(r.output()));
        }
    }

    /** Diagnóstico pela saída do pip (sem valores do projeto: só pacotes e erros). */
    static Diagnosis diagnose(String out) {
        String o = out.toLowerCase(java.util.Locale.ROOT);
        if (o.contains("do not match the hashes") || o.contains("hash mismatch") || o.contains("hashes are required")) return Diagnosis.HASH_MISMATCH;
        if (o.contains("certificate_verify_failed") || o.contains("sslerror") || o.contains("ssl:") || o.contains("proxyerror")
                || o.contains("tunnel connection failed")) return Diagnosis.PROXY_OR_SSL;
        if (o.contains("no space left") || o.contains("errno 28")) return Diagnosis.DISK_FULL;
        if (o.contains("permission denied") || o.contains("errno 13") || o.contains("acesso negado") || o.contains("access is denied")) {
            return Diagnosis.PERMISSION_DENIED;
        }
        if (o.contains("failed to establish a new connection") || o.contains("getaddrinfo failed") || o.contains("name or service not known")
                || o.contains("connection refused") || o.contains("connectionerror") || o.contains("max retries exceeded")
                || o.contains("could not fetch url") || o.contains("no matching distribution found") && o.contains("from versions: none")) {
            return Diagnosis.NETWORK_UNAVAILABLE;
        }
        return Diagnosis.INSTALL_FAILED;
    }

    private static Diagnosis ioDiagnosis(IOException e) {
        String m = String.valueOf(e.getMessage()).toLowerCase(java.util.Locale.ROOT);
        if (e instanceof java.nio.file.AccessDeniedException || m.contains("acesso negado") || m.contains("access is denied")) return Diagnosis.PERMISSION_DENIED;
        if (m.contains("space") || m.contains("espaço")) return Diagnosis.DISK_FULL;
        return Diagnosis.INSTALL_FAILED;
    }

    private static String lastMeaningful(String out) {
        String[] lines = out.strip().split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String l = lines[i].strip();
            if (l.startsWith("ERROR") || l.startsWith("error:")) return l.length() > 240 ? l.substring(0, 240) + "…" : l;
        }
        return lines.length == 0 ? "" : lines[lines.length - 1].strip();
    }

    private void writeRecord(Plan plan, IntegrityVerifier.Result v) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schema", RECORD_SCHEMA);
        m.put("version", "1.0.0");
        m.put("requirement", plan.requirement().pip());
        m.put("wheelSha256", plan.artifact().sha256());
        m.put("source", plan.artifact().source().name());
        m.put("commit", plan.artifact().commit());
        m.put("dependenciesLockSha256", artifacts.dependencies().sha256());
        m.put("toolsLockSha256", artifacts.tools().sha256());
        m.put("dependencyIndex", dependencyIndex);
        m.put("pythonBase", ProcessRunner.sanitize(plan.base().python().toString()));
        m.put("python", v.environment().map(PythonChecks.Environment::pythonVersion).orElse(""));
        m.put("verified", v.detail());
        m.put("installedAt", clock.instant().toString());
        Path tmp = plan.environmentDir().resolve("ambiente.json.tmp");
        Files.writeString(tmp, Json.write(m), StandardCharsets.UTF_8);
        Files.move(tmp, plan.environmentDir().resolve("ambiente.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Caminho relativo à pasta do ambiente (sempre ASCII: nomes gerados pelo próprio Studio). */
    private static String rel(Path dir, Path file) {
        String r = dir.relativize(file).toString();
        if (!r.chars().allMatch(c -> c < 128)) throw new IllegalStateException("caminho relativo não ASCII: " + r);
        return r;
    }

    private static List<String> concat(List<String> base, String... more) {
        List<String> out = new ArrayList<>(base);
        out.addAll(List.of(more));
        return out;
    }

    static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var s = Files.walk(dir)) {
            for (Path p : s.sorted(java.util.Comparator.reverseOrder()).toList()) {
                p.toFile().setWritable(true);
                Files.deleteIfExists(p);
            }
        }
    }
}
