package com.nekkora.studio.ai.deps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Processo filho do Dependency Manager: SEM shell (lista de argumentos), ambiente limpo de variáveis que mudariam o pip
 * ou o Python ({@code PIP_*}, {@code PYTHON*}, {@code VIRTUAL_ENV}), prazo, cancelamento que derruba a árvore de
 * processos e saída HIGIENIZADA linha a linha (pasta do usuário → {@code ~}, credenciais em URL → {@code ***}).
 */
final class ProcessRunner {
    private ProcessRunner() {}

    record Result(int exit, String output, boolean timedOut, boolean cancelled) {}

    private static final Pattern URL_CREDENTIALS = Pattern.compile("(https?://)[^/\\s:@]+(:[^/\\s@]*)?@");

    /** Saída HIGIENIZADA (para logs e progresso na tela). */
    static Result run(List<String> cmd, Path dir, Duration timeout, BooleanSupplier cancelled, Consumer<String> lines) throws IOException {
        return run(cmd, dir, timeout, cancelled, lines, true);
    }

    /** Saída CRUA: só para dados que o próprio Studio interpreta (ex.: o caminho do interpretador); nunca vai a log. */
    static Result runRaw(List<String> cmd, Duration timeout) throws IOException {
        return run(cmd, null, timeout, () -> false, null, false);
    }

    private static Result run(List<String> cmd, Path dir, Duration timeout, BooleanSupplier cancelled, Consumer<String> lines, boolean sanitize)
            throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        if (dir != null) pb.directory(dir.toFile());
        Map<String, String> env = pb.environment();
        env.keySet().removeIf(k -> k.startsWith("PIP_") || k.startsWith("PYTHON") || k.equals("VIRTUAL_ENV") || k.equals("CONDA_PREFIX"));
        env.put("PYTHONIOENCODING", "utf-8");
        env.put("PYTHONUTF8", "1");
        env.put("PIP_DISABLE_PIP_VERSION_CHECK", "1");
        env.put("PIP_NO_INPUT", "1");
        env.put("NO_COLOR", "1");
        Process p = pb.start();
        p.getOutputStream().close();
        List<String> out = new ArrayList<>();
        Thread reader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String clean = sanitize ? sanitize(line) : line;
                    synchronized (out) {
                        out.add(clean);
                    }
                    if (lines != null) lines.accept(clean);
                }
            } catch (IOException ignored) {
                // processo encerrado
            }
        }, "nekkora-deps-out");
        reader.setDaemon(true);
        reader.start();
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (!p.waitFor(100, TimeUnit.MILLISECONDS)) {
                boolean c = cancelled.getAsBoolean();
                if (c || System.nanoTime() > deadline) {
                    p.descendants().forEach(ProcessHandle::destroyForcibly);
                    p.destroyForcibly();
                    p.waitFor(10, TimeUnit.SECONDS);
                    return new Result(-1, joined(out), !c, c);
                }
            }
            reader.join(10_000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
            return new Result(-1, joined(out), false, true);
        }
        return new Result(p.exitValue(), joined(out), false, false);
    }

    private static String joined(List<String> out) {
        synchronized (out) {
            return String.join("\n", out);
        }
    }

    static String sanitize(String line) {
        String home = System.getProperty("user.home", "");
        String s = URL_CREDENTIALS.matcher(line).replaceAll("$1***@");
        if (!home.isBlank()) s = s.replace(home, "~").replace(home.replace('\\', '/'), "~");
        return s.length() > 600 ? s.substring(0, 600) + "…" : s;
    }
}
