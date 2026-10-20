package com.nekkora.studio.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Interpretador Python com o {@code nekkora_rpa} para os testes que rodam ferramentas REAIS: {@code NEKKORA_TEST_PYTHON}
 * ou a venv do repositório irmão {@code Nekkora-RPA-Framework/.venv}. Sem ele, esses testes são PULADOS (e contados como
 * pulados) — nunca dados por aprovados.
 */
public final class PythonForTests {
    private PythonForTests() {}

    public static Path frameworkRepo() {
        return Path.of("..", "..", "Nekkora-RPA-Framework").toAbsolutePath().normalize();
    }

    public static Path framework() {
        String env = System.getenv("NEKKORA_TEST_PYTHON");
        if (env != null && !env.isBlank() && Files.isRegularFile(Path.of(env))) return Path.of(env);
        Path repo = frameworkRepo();
        for (Path p : List.of(repo.resolve(".venv/Scripts/python.exe"), repo.resolve(".venv/bin/python"))) {
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    public static String run(Path python, List<String> args, String stdin) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add(python.toString());
        cmd.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        Process p = pb.start();
        p.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(120, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("tempo esgotado");
        }
        return out;
    }
}
