package com.nekkora.studio.ai.pycheck;

import com.nekkora.studio.bridge.json.Json;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * NEKKORA-E2E-002 — TESTE AUTORIZADO do projeto gerado (desenvolvimento, não produção): o Studio lança o {@code main.py}
 * gerado num subprocesso Python isolado, sob comando explícito da pessoa, e acompanha pelo arquivo de eventos do
 * framework (sem valores). Nunca roda código que não seja o da revisão gerada: antes de lançar, os hashes do manifesto
 * são conferidos arquivo a arquivo.
 *
 * <ul>
 *   <li>sem shell e sem concatenação: argumentos em lista; ambiente MÍNIMO (sem chaves de API, sem variáveis de
 *       segredo, sem PYTHONPATH) — o processo de teste não precisa de credencial;</li>
 *   <li>prazo total e cancelamento (a árvore de processos inteira é encerrada);</li>
 *   <li>progresso: contagem de transações e a fase atual, lidas do {@code events.jsonl};</li>
 *   <li>resultado: o {@code summary.json} do framework e a CONFERÊNCIA da planilha reaberta por um script separado
 *       ({@code conferir_planilha.py}), que devolve só cabeçalhos, quantidade de linhas e o hash — nunca células.</li>
 * </ul>
 */
public final class AuthorizedTrial {
    private AuthorizedTrial() {}

    static final String VERIFY_RESOURCE = "/nekkora/ai/python/conferir_planilha.py";
    /** Variáveis de ambiente que o subprocesso herda (o resto — inclusive chaves de API — fica de fora). */
    static final Set<String> ENV_KEEP = Set.of("SYSTEMROOT", "SYSTEMDRIVE", "WINDIR", "COMSPEC", "PATH", "PATHEXT", "TEMP", "TMP", "TMPDIR", "USERPROFILE",
            "HOMEDRIVE", "HOMEPATH", "HOME", "LOCALAPPDATA", "APPDATA", "PROGRAMDATA", "PROGRAMFILES", "PROGRAMFILES(X86)", "COMMONPROGRAMFILES",
            "NUMBER_OF_PROCESSORS", "PROCESSOR_ARCHITECTURE", "PROCESSOR_IDENTIFIER", "OS", "LANG", "LC_ALL", "DISPLAY", "XAUTHORITY",
            "PLAYWRIGHT_BROWSERS_PATH");

    public enum Status { COMPLETED, COMPLETED_WITH_FAILURES, STOPPED, INIT_FAILED, CANCELLED, TIMEOUT, INPUT_INVALID, NOT_STARTED, CRASHED }

    /** O que lançar: a pasta do projeto gerado, a lista de entrada, a planilha de saída e a pasta da execução. */
    public record Request(Path project, Path input, Path spreadsheet, Path runDir, boolean visibleBrowser, Duration timeout) {}

    /** Progresso observado (só contagens e códigos). */
    public record Progress(String phase, int started, int succeeded, int rejected, int failed, long elapsedMs) {}

    /** Conferência da planilha REABERTA (sem células). */
    public record Verification(boolean ok, String code, int rows, List<String> headers, String sha256) {}

    public record Outcome(Status status, int exitCode, Map<String, Integer> counts, String outputStatus, int outputRows, String outputDetail,
                          Verification verification, long millis, String codeRevision) {
        public boolean proven() {
            return status == Status.COMPLETED && "COMMITTED".equals(outputStatus) && verification != null && verification.ok();
        }
    }

    /** Pré-condição falha (nada é lançado). */
    public static final class Refused extends Exception {
        private final String code;

        public Refused(String code) {
            super(code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /**
     * Revisão do código gerado: sha256 do manifesto. Antes, confere que cada arquivo listado tem o hash do manifesto
     * (arquivo editado/trocado depois da geração → {@code CODE_CHANGED_SINCE_GENERATION}).
     */
    public static String verifyGenerated(Path project) throws Refused {
        Path manifest = project.resolve("manifest.json");
        if (!Files.isRegularFile(manifest)) throw new Refused("NO_GENERATED_CODE");
        try {
            String text = Files.readString(manifest, StandardCharsets.UTF_8);
            Map<String, Object> m = Json.parseObject(text);
            if (!(m.get("files") instanceof Map<?, ?> files) || files.isEmpty()) throw new Refused("MANIFEST_INVALID");
            for (Map.Entry<?, ?> e : files.entrySet()) {
                Path f = project.resolve(String.valueOf(e.getKey())).normalize();
                if (!f.startsWith(project) || !Files.isRegularFile(f)) throw new Refused("CODE_CHANGED_SINCE_GENERATION");
                String want = String.valueOf(e.getValue());
                // o gerador calcula sobre o texto LF; o checkout do Windows pode ter gravado CRLF
                String got = "sha256:" + sha256(Files.readString(f, StandardCharsets.UTF_8).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
                if (!want.equals(got)) throw new Refused("CODE_CHANGED_SINCE_GENERATION");
            }
            return com.nekkora.studio.ai.Hashes.sha256(text.replace("\r\n", "\n")); // o mesmo hash do V4/V5 (AutomationQualityGate.codeState)
        } catch (IOException | RuntimeException e) {
            throw new Refused("MANIFEST_INVALID");
        }
    }

    /** Lança, acompanha e confere. Chamar fora da thread de interface. */
    public static Outcome run(PythonChecks.Environment env, Request r, Consumer<Progress> progress, BooleanSupplier cancelled) throws Refused, IOException {
        String revision = verifyGenerated(r.project());
        if (!Files.isRegularFile(r.input())) throw new Refused("INPUT_MISSING");
        if (r.spreadsheet().getParent() == null || !Files.isDirectory(r.spreadsheet().getParent())) throw new Refused("OUTPUT_FOLDER_MISSING");
        Files.createDirectories(r.runDir());
        List<String> cmd = new ArrayList<>(List.of(env.python().toString(), "-E", "-s", "-B", "-X", "utf8", "main.py", "--entrada", r.input().toString(), "--planilha",
                r.spreadsheet().toString(), "--saida", r.runDir().toString(), "--channel", "chrome"));
        if (!r.visibleBrowser()) cmd.add("--headless");
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(r.project().toFile()).redirectErrorStream(true);
        Map<String, String> envVars = pb.environment();
        Map<String, String> keep = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : envVars.entrySet()) {
            if (ENV_KEEP.contains(e.getKey().toUpperCase(Locale.ROOT))) keep.put(e.getKey(), e.getValue());
        }
        envVars.clear();
        envVars.putAll(keep);
        envVars.put("PYTHONIOENCODING", "utf-8");
        envVars.put("PYTHONUTF8", "1");
        long t0 = System.nanoTime();
        Process p = pb.start();
        StringBuilder stdout = new StringBuilder();
        Thread drain = new Thread(() -> drain(p.getInputStream(), stdout), "nekkora-trial-saida");
        drain.setDaemon(true);
        drain.start();
        Path events = r.runDir().resolve("execucao").resolve("events.jsonl");
        long read = 0;
        Progress last = new Progress("INIT", 0, 0, 0, 0, 0);
        Status status = null;
        while (true) {
            boolean done = waitFor(p, 300);
            long elapsed = (System.nanoTime() - t0) / 1_000_000;
            long[] pos = {read};
            last = scan(events, pos, last, elapsed);
            read = pos[0];
            progress.accept(last);
            if (done) break;
            if (cancelled.getAsBoolean()) {
                kill(p);
                status = Status.CANCELLED;
                break;
            }
            if (elapsed > r.timeout().toMillis()) {
                kill(p);
                status = Status.TIMEOUT;
                break;
            }
        }
        try {
            drain.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int exit = p.isAlive() ? -1 : p.exitValue();
        long millis = (System.nanoTime() - t0) / 1_000_000;
        Map<String, Object> summary = readJson(r.runDir().resolve("execucao").resolve("summary.json"));
        Map<String, Integer> counts = new LinkedHashMap<>();
        String outStatus = "";
        int outRows = 0;
        String outDetail = "";
        if (summary != null) {
            if (summary.get("counts") instanceof Map<?, ?> c) c.forEach((k, v) -> counts.put(String.valueOf(k), v instanceof Number n ? n.intValue() : 0));
            if (summary.get("output") instanceof Map<?, ?> o) {
                outStatus = String.valueOf(o.get("status"));
                outRows = o.get("rows") instanceof Number n ? n.intValue() : 0;
                outDetail = o.get("detail") == null || o.get("detail") == Json.NULL ? "" : String.valueOf(o.get("detail"));
            }
            if (status == null) {
                try {
                    status = Status.valueOf(String.valueOf(summary.get("status")));
                } catch (IllegalArgumentException e) {
                    status = Status.CRASHED;
                }
            }
        }
        if (status == null) status = stdout.toString().contains("\"INPUT_INVALID\"") ? Status.INPUT_INVALID : exit == 0 ? Status.CRASHED : Status.CRASHED;
        Verification v = "COMMITTED".equals(outStatus) && Files.isRegularFile(r.spreadsheet()) ? verify(env, r) : null;
        return new Outcome(status, exit, counts, outStatus, outRows, outDetail, v, millis, revision);
    }

    /** Reabre a planilha num processo separado (nekkora_rpa.xlsx.read_back com o esquema do projeto gerado). */
    static Verification verify(PythonChecks.Environment env, Request r) {
        try {
            Path script = Files.createTempFile("nekkora-conferir-", ".py");
            try (InputStream in = AuthorizedTrial.class.getResourceAsStream(VERIFY_RESOURCE)) {
                if (in == null) return new Verification(false, "VERIFY_SCRIPT_MISSING", 0, List.of(), "");
                Files.write(script, in.readAllBytes());
            }
            try {
                ProcessBuilder pb = new ProcessBuilder(env.python().toString(), "-E", "-s", "-B", "-X", "utf8", script.toString(), r.project().toString(), r.spreadsheet().toString())
                        .redirectErrorStream(true);
                pb.environment().remove("PYTHONPATH");
                pb.environment().put("PYTHONIOENCODING", "utf-8");
                Process p = pb.start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (!waitFor(p, 60_000)) {
                    kill(p);
                    return new Verification(false, "VERIFY_TIMEOUT", 0, List.of(), "");
                }
                String line = out.strip().lines().reduce((a, b) -> b).orElse("");
                Map<String, Object> m = Json.parseObject(line);
                List<String> headers = new ArrayList<>();
                if (m.get("headers") instanceof List<?> h) h.forEach(x -> headers.add(String.valueOf(x)));
                return new Verification(Boolean.TRUE.equals(m.get("ok")), String.valueOf(m.getOrDefault("code", "")),
                        m.get("rows") instanceof Number n ? n.intValue() : 0, headers, String.valueOf(m.getOrDefault("sha256", "")));
            } finally {
                Files.deleteIfExists(script);
            }
        } catch (IOException | RuntimeException e) {
            return new Verification(false, "VERIFY_FAILED", 0, List.of(), "");
        }
    }

    /** Lê as linhas novas do events.jsonl e atualiza as contagens (só códigos). */
    static Progress scan(Path events, long[] pos, Progress last, long elapsed) {
        String phase = last.phase();
        int started = last.started();
        int ok = last.succeeded();
        int rej = last.rejected();
        int fail = last.failed();
        if (Files.isRegularFile(events)) {
            try (RandomAccessFile f = new RandomAccessFile(events.toFile(), "r")) {
                if (f.length() > pos[0]) {
                    f.seek(pos[0]);
                    byte[] buf = new byte[(int) Math.min(f.length() - pos[0], 4_000_000)];
                    f.readFully(buf);
                    String text = new String(buf, StandardCharsets.UTF_8);
                    int cut = text.lastIndexOf('\n');
                    if (cut >= 0) {
                        pos[0] += text.substring(0, cut + 1).getBytes(StandardCharsets.UTF_8).length;
                        for (String line : text.substring(0, cut).split("\n")) {
                            Map<String, Object> e;
                            try {
                                e = Json.parseObject(line);
                            } catch (RuntimeException x) {
                                continue;
                            }
                            String code = String.valueOf(e.get("event_code"));
                            if (e.get("phase") instanceof String ph) phase = ph;
                            switch (code) {
                                case "TRANSACTION_STARTED" -> started++;
                                case "TRANSACTION_COMPLETED" -> {
                                    if ("BUSINESS_REJECTED".equals(e.get("outcome"))) rej++;
                                    else ok++;
                                }
                                case "TRANSACTION_FAILED" -> fail++;
                                default -> {
                                }
                            }
                        }
                    }
                }
            } catch (IOException ignored) {
                // o arquivo pode estar sendo escrito: a próxima leitura continua de onde parou
            }
        }
        return new Progress(phase, started, ok, rej, fail, elapsed);
    }

    private static Map<String, Object> readJson(Path p) {
        try {
            return Files.isRegularFile(p) ? Json.parseObject(Files.readString(p, StandardCharsets.UTF_8)) : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void drain(InputStream in, StringBuilder out) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                synchronized (out) {
                    if (out.length() < 200_000) out.append(line).append('\n');
                }
            }
        } catch (IOException ignored) {
            // processo encerrado
        }
    }

    private static boolean waitFor(Process p, long ms) {
        try {
            return p.waitFor(ms, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return !p.isAlive();
        }
    }

    static void kill(Process p) {
        p.descendants().forEach(ProcessHandle::destroyForcibly); // navegador e filhos do Python
        p.destroyForcibly();
        waitFor(p, 5000);
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
