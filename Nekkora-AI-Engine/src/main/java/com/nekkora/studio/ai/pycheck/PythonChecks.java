package com.nekkora.studio.ai.pycheck;

import com.nekkora.studio.ai.quality.Layer;
import com.nekkora.studio.bridge.json.Json;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STUDIO-014 — V4/V5 executados DE VERDADE num interpretador Python isolado ({@code -I -B}: sem site do usuário, sem
 * {@code PYTHONPATH}, sem bytecode na pasta do projeto), sem rede e sem navegador. Nunca roda {@code main.py} nem o
 * processo: compila, importa os módulos (só definições), roda ruff (perfil do framework), mypy estrito e os testes de
 * contrato. Da saída das ferramentas só se guardam contagens e códigos (nunca trechos de código ou caminhos).
 *
 * <p>Sem interpretador com {@code nekkora_rpa}, ruff e mypy, as camadas ficam {@code NOT_CHECKED} — nunca aprovadas.
 */
public final class PythonChecks {

    public enum Status { PASS, FAIL, NOT_RUN, ERROR }

    /** Resultado de uma verificação: só números e códigos. */
    public record Check(String id, Layer layer, Status status, int problems, Map<String, Integer> codes, long millis, String reason) {
        public Check {
            codes = Map.copyOf(codes);
            reason = reason == null ? "" : reason;
        }
    }

    /** Interpretador verificado (versões das ferramentas). */
    public record Environment(Path python, String framework, String ruff, String mypy, String pythonVersion) {}

    static final String RUFF_SELECT = "E,F,W,I,B,UP,N,S,BLE,RUF";
    static final String RUFF_IGNORE = "S101,RUF001,RUF002,RUF003";
    static final Duration TIMEOUT = Duration.ofSeconds(180);
    private static final Pattern MYPY_ERROR = Pattern.compile("^[^:\\n]+:\\d+: error: .*\\[([a-z0-9-]+)\\]\\s*$", Pattern.MULTILINE);

    private PythonChecks() {}

    /**
     * Escolhe o interpretador: o configurado no Studio, {@code NEKKORA_PYTHON} ou a {@code .venv} da pasta gerada — e
     * confere que ele tem {@code nekkora_rpa}, ruff e mypy. Vazio = ambiente de verificação não configurado.
     */
    public static Optional<Environment> resolve(Optional<Path> configured, Path projectDir) {
        List<Path> candidates = new ArrayList<>();
        configured.ifPresent(candidates::add);
        String env = System.getenv("NEKKORA_PYTHON");
        if (env != null && !env.isBlank()) candidates.add(Path.of(env));
        if (projectDir != null) {
            candidates.add(projectDir.resolve(".venv/Scripts/python.exe"));
            candidates.add(projectDir.resolve(".venv/bin/python"));
        }
        for (Path p : candidates) {
            if (!Files.isRegularFile(p)) continue;
            try {
                Run r = run(p, null, List.of("-I", "-B", "-c", "import importlib.metadata as m, sys, nekkora_rpa; "
                        + "print(nekkora_rpa.__version__, m.version('ruff'), m.version('mypy'), '%d.%d.%d' % sys.version_info[:3])"), "", Duration.ofSeconds(30), () -> false);
                if (r.exit() != 0) continue;
                String[] v = r.out().strip().split("\\s+");
                if (v.length == 4) return Optional.of(new Environment(p, v[0], v[1], v[2], v[3]));
            } catch (IOException e) {
                // candidato inválido: tenta o próximo
            }
        }
        return Optional.empty();
    }

    /** Roda V4 (compilação, importação, ruff, mypy) e V5 (contrato) na pasta do projeto gerado. */
    public static List<Check> run(Environment env, Path projectDir, BooleanSupplier cancelled) throws IOException {
        Objects.requireNonNull(env);
        List<Check> out = new ArrayList<>();
        Path tmp = Files.createTempDirectory("nekkora-pycheck-");
        try {
            Path contract = extract(tmp, "contrato_projeto.py");
            // dois layouts: o pacote automation/ (código em inglês, rodada 3) e paginas.py/processo.py na raiz (projetos anteriores)
            out.add(simple("PY_COMPILE", Layer.V4_CODE, env, projectDir, List.of("-I", "-B", "-c",
                    "import ast, pathlib, sys\nfor p in sorted([*pathlib.Path('.').glob('*.py'), *pathlib.Path('automation').glob('*.py')]):\n"
                            + "    ast.parse(p.read_text(encoding='utf-8'), str(p))"), cancelled));
            out.add(simple("PY_IMPORT", Layer.V4_CODE, env, projectDir, List.of("-I", "-B", "-c",
                    "import importlib, pathlib, sys\nsys.path.insert(0, '.')\n"
                            + "mods = ['automation.selectors', 'automation.pages', 'automation.process', 'main'] if pathlib.Path('automation/__init__.py').is_file() "
                            + "else ['paginas', 'processo', 'main']\nfor m in mods:\n    importlib.import_module(m)"), cancelled));
            out.add(ruffCheck(env, projectDir, cancelled));
            out.add(ruffFormat(env, projectDir, cancelled));
            out.add(mypy(env, projectDir, tmp, cancelled));
            out.add(contract(env, projectDir, contract, cancelled));
            out.add(quality(env, projectDir, extract(tmp, "qualidade_codigo.py"), cancelled));
        } finally {
            deleteTree(tmp);
        }
        return out;
    }

    private static Check simple(String id, Layer layer, Environment env, Path dir, List<String> args, BooleanSupplier cancelled) throws IOException {
        if (cancelled.getAsBoolean()) return new Check(id, layer, Status.NOT_RUN, 0, Map.of(), 0, "CANCELLED");
        long t = System.nanoTime();
        Run r = run(env.python(), dir, args, "", TIMEOUT, cancelled);
        long ms = (System.nanoTime() - t) / 1_000_000;
        if (r.timedOut()) return new Check(id, layer, Status.ERROR, 0, Map.of(), ms, "TIMEOUT");
        return new Check(id, layer, r.exit() == 0 ? Status.PASS : Status.FAIL, r.exit() == 0 ? 0 : 1,
                r.exit() == 0 ? Map.of() : Map.of(errorType(r.out()), 1), ms, "");
    }

    private static Check ruffCheck(Environment env, Path dir, BooleanSupplier cancelled) throws IOException {
        if (cancelled.getAsBoolean()) return new Check("RUFF_CHECK", Layer.V4_CODE, Status.NOT_RUN, 0, Map.of(), 0, "CANCELLED");
        long t = System.nanoTime();
        Run r = run(env.python(), dir, List.of("-I", "-B", "-m", "ruff", "check", "--no-cache", "--isolated", "--output-format", "json", "--line-length", "120",
                "--target-version", "py312", "--select", RUFF_SELECT, "--ignore", RUFF_IGNORE, "."), "", TIMEOUT, cancelled);
        long ms = (System.nanoTime() - t) / 1_000_000;
        if (r.timedOut()) return new Check("RUFF_CHECK", Layer.V4_CODE, Status.ERROR, 0, Map.of(), ms, "TIMEOUT");
        try {
            Object parsed = Json.parse(r.out().strip());
            if (!(parsed instanceof List<?> list)) return new Check("RUFF_CHECK", Layer.V4_CODE, Status.ERROR, 0, Map.of(), ms, "OUTPUT_INVALID");
            Map<String, Integer> codes = new LinkedHashMap<>();
            for (Object o : list) if (o instanceof Map<?, ?> m && m.get("code") instanceof String c) codes.merge(c, 1, Integer::sum);
            return new Check("RUFF_CHECK", Layer.V4_CODE, list.isEmpty() ? Status.PASS : Status.FAIL, list.size(), codes, ms, "");
        } catch (RuntimeException e) {
            return new Check("RUFF_CHECK", Layer.V4_CODE, Status.ERROR, 0, Map.of(), ms, "OUTPUT_INVALID");
        }
    }

    private static Check ruffFormat(Environment env, Path dir, BooleanSupplier cancelled) throws IOException {
        if (cancelled.getAsBoolean()) return new Check("RUFF_FORMAT", Layer.V4_CODE, Status.NOT_RUN, 0, Map.of(), 0, "CANCELLED");
        long t = System.nanoTime();
        Run r = run(env.python(), dir, List.of("-I", "-B", "-m", "ruff", "format", "--check", "--no-cache", "--isolated", "--line-length", "120",
                "--target-version", "py312", "."), "", TIMEOUT, cancelled);
        long ms = (System.nanoTime() - t) / 1_000_000;
        if (r.timedOut()) return new Check("RUFF_FORMAT", Layer.V4_CODE, Status.ERROR, 0, Map.of(), ms, "TIMEOUT");
        int n = (int) r.out().lines().filter(l -> l.startsWith("Would reformat")).count();
        return new Check("RUFF_FORMAT", Layer.V4_CODE, r.exit() == 0 ? Status.PASS : Status.FAIL, r.exit() == 0 ? 0 : Math.max(1, n),
                r.exit() == 0 ? Map.of() : Map.of("FORMAT", Math.max(1, n)), ms, "");
    }

    private static Check mypy(Environment env, Path dir, Path tmp, BooleanSupplier cancelled) throws IOException {
        if (cancelled.getAsBoolean()) return new Check("MYPY_STRICT", Layer.V4_CODE, Status.NOT_RUN, 0, Map.of(), 0, "CANCELLED");
        List<String> files = new ArrayList<>();
        try (var s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().endsWith(".py")).map(p -> p.getFileName().toString()).sorted().forEach(files::add);
        }
        if (Files.isRegularFile(dir.resolve("automation").resolve("__init__.py"))) {
            try (var s = Files.list(dir.resolve("automation"))) {
                s.filter(p -> p.getFileName().toString().endsWith(".py")).map(p -> "automation/" + p.getFileName()).sorted().forEach(files::add);
            }
        }
        List<String> args = new ArrayList<>(List.of("-I", "-B", "-m", "mypy", "--strict", "--no-incremental", "--cache-dir", tmp.resolve("mypy").toString(),
                "--python-version", "3.12", "--show-error-codes", "--no-error-summary", "--no-pretty"));
        args.addAll(files);
        long t = System.nanoTime();
        Run r = run(env.python(), dir, args, "", TIMEOUT, cancelled);
        long ms = (System.nanoTime() - t) / 1_000_000;
        if (r.timedOut()) return new Check("MYPY_STRICT", Layer.V4_CODE, Status.ERROR, 0, Map.of(), ms, "TIMEOUT");
        Map<String, Integer> codes = new LinkedHashMap<>();
        Matcher m = MYPY_ERROR.matcher(r.out());
        int n = 0;
        while (m.find()) {
            codes.merge(m.group(1), 1, Integer::sum);
            n++;
        }
        if (r.exit() != 0 && n == 0) return new Check("MYPY_STRICT", Layer.V4_CODE, Status.ERROR, 0, Map.of(), ms, "MYPY_FAILED");
        return new Check("MYPY_STRICT", Layer.V4_CODE, n == 0 ? Status.PASS : Status.FAIL, n, codes, ms, "");
    }

    @SuppressWarnings("unchecked")
    private static Check contract(Environment env, Path dir, Path script, BooleanSupplier cancelled) throws IOException {
        if (cancelled.getAsBoolean()) return new Check("CONTRACT_TESTS", Layer.V5_TESTS, Status.NOT_RUN, 0, Map.of(), 0, "CANCELLED");
        long t = System.nanoTime();
        Run r = run(env.python(), dir, List.of("-I", "-B", script.toString(), dir.toAbsolutePath().toString()), "", TIMEOUT, cancelled);
        long ms = (System.nanoTime() - t) / 1_000_000;
        if (r.timedOut()) return new Check("CONTRACT_TESTS", Layer.V5_TESTS, Status.ERROR, 0, Map.of(), ms, "TIMEOUT");
        try {
            Map<String, Object> m = Json.parseObject(lastLine(r.out()));
            if (m.get("error") instanceof String e) return new Check("CONTRACT_TESTS", Layer.V5_TESTS, Status.ERROR, 0, Map.of(), ms, e);
            Map<String, Integer> codes = new LinkedHashMap<>();
            int total = ((Number) m.get("total")).intValue();
            for (Object o : (List<Object>) m.get("checks")) {
                Map<String, Object> c = (Map<String, Object>) o;
                if (!Boolean.TRUE.equals(c.get("ok"))) codes.merge((String) c.get("code"), 1, Integer::sum);
            }
            int failed = ((Number) m.get("failed")).intValue();
            codes.put("TOTAL", total);
            return new Check("CONTRACT_TESTS", Layer.V5_TESTS, failed == 0 ? Status.PASS : Status.FAIL, failed, codes, ms, "");
        } catch (RuntimeException e) {
            return new Check("CONTRACT_TESTS", Layer.V5_TESTS, Status.ERROR, 0, Map.of(), ms, "OUTPUT_INVALID");
        }
    }

    /**
     * CODEGEN-QUALITY-001 (camada VQ): análise da AST do código gerado. Os achados vão em {@code codes} como
     * {@code "<CÓDIGO> <arquivo>:<símbolo>"} → quantidade (só identificadores gerados; nenhum trecho de código).
     */
    @SuppressWarnings("unchecked")
    private static Check quality(Environment env, Path dir, Path script, BooleanSupplier cancelled) throws IOException {
        if (cancelled.getAsBoolean()) return new Check("CODE_QUALITY", Layer.VQ_GENERATION, Status.NOT_RUN, 0, Map.of(), 0, "CANCELLED");
        long t = System.nanoTime();
        Run r = run(env.python(), dir, List.of("-I", "-B", script.toString(), dir.toAbsolutePath().toString()), "", TIMEOUT, cancelled);
        long ms = (System.nanoTime() - t) / 1_000_000;
        if (r.timedOut()) return new Check("CODE_QUALITY", Layer.VQ_GENERATION, Status.ERROR, 0, Map.of(), ms, "TIMEOUT");
        try {
            Map<String, Object> m = Json.parseObject(lastLine(r.out()));
            if (m.get("error") instanceof String e) return new Check("CODE_QUALITY", Layer.VQ_GENERATION, Status.ERROR, 0, Map.of(), ms, e);
            Map<String, Integer> codes = new LinkedHashMap<>();
            for (Object o : (List<Object>) m.get("findings")) {
                Map<String, Object> f = (Map<String, Object>) o;
                codes.merge(f.get("code") + " " + f.get("file") + ":" + f.get("symbol"), 1, Integer::sum);
            }
            int total = ((Number) m.get("total")).intValue();
            return new Check("CODE_QUALITY", Layer.VQ_GENERATION, total == 0 ? Status.PASS : Status.FAIL, total, codes, ms, "");
        } catch (RuntimeException e) {
            return new Check("CODE_QUALITY", Layer.VQ_GENERATION, Status.ERROR, 0, Map.of(), ms, "OUTPUT_INVALID");
        }
    }

    /** Sonda do framework instalado (JSON) para comparar com o catálogo. */
    public static Optional<String> probe(Environment env, String requestJson) throws IOException {
        Path tmp = Files.createTempDirectory("nekkora-probe-");
        try {
            Path script = extract(tmp, "sonda_framework.py");
            Run r = run(env.python(), tmp, List.of("-I", "-B", script.toString()), requestJson, Duration.ofSeconds(60), () -> false);
            return r.exit() == 0 ? Optional.of(lastLine(r.out())) : Optional.empty();
        } finally {
            deleteTree(tmp);
        }
    }

    static String lastLine(String s) {
        String[] lines = s.strip().split("\\R");
        return lines.length == 0 ? "" : lines[lines.length - 1];
    }

    private static String errorType(String out) {
        Matcher m = Pattern.compile("(?m)^([A-Za-z]+Error)\\b").matcher(out);
        String last = "PYTHON_ERROR";
        while (m.find()) last = m.group(1);
        return last;
    }

    record Run(int exit, String out, boolean timedOut) {}

    static Run run(Path python, Path dir, List<String> args, String stdin, Duration timeout, BooleanSupplier cancelled) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(python.toString());
        cmd.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        if (dir != null) pb.directory(dir.toFile());
        Map<String, String> e = pb.environment();
        e.remove("PYTHONPATH");
        e.remove("PYTHONSTARTUP");
        e.put("PYTHONIOENCODING", "utf-8");
        e.put("PYTHONDONTWRITEBYTECODE", "1");
        e.put("NO_COLOR", "1");
        Process p = pb.start();
        try (var in = p.getOutputStream()) {
            in.write(stdin.getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder out = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (InputStream is = p.getInputStream()) {
                out.append(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // processo encerrado
            }
        }, "nekkora-pycheck-out");
        reader.setDaemon(true);
        reader.start();
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (!p.waitFor(100, TimeUnit.MILLISECONDS)) {
                if (cancelled.getAsBoolean() || System.nanoTime() > deadline) {
                    p.descendants().forEach(ProcessHandle::destroyForcibly);
                    p.destroyForcibly();
                    return new Run(-1, "", true);
                }
            }
            reader.join(5000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            return new Run(-1, "", true);
        }
        synchronized (out) {
            return new Run(p.exitValue(), out.toString(), false);
        }
    }

    private static Path extract(Path dir, String name) throws IOException {
        try (InputStream in = PythonChecks.class.getResourceAsStream("/nekkora/ai/python/" + name)) {
            if (in == null) throw new IOException("recurso ausente");
            Path f = dir.resolve(name);
            Files.write(f, in.readAllBytes());
            return f;
        }
    }

    static void deleteTree(Path dir) {
        try (var s = Files.walk(dir)) {
            for (Path p : s.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // temporário: melhor esforço
        }
    }
}
