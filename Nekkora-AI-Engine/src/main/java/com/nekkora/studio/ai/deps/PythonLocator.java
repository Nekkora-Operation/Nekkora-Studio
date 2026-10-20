package com.nekkora.studio.ai.deps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * PACKAGING-001 — EnvironmentResolver: encontra um Python ≥ 3.12 BASE (para criar a venv da automação), mostrando qual
 * foi escolhido e de onde veio. Ordem: o indicado pela pessoa; {@code -Dnekkora.python.base}; no Windows o lançador
 * {@code py -3.12}/{@code py -3}; o {@code PATH}; as pastas de instalação usuais. O atalho da Microsoft Store
 * ({@code WindowsApps\python.exe}) é ignorado — ele abre a loja em vez de rodar Python. Nunca altera o Python encontrado.
 */
public final class PythonLocator {
    private PythonLocator() {}

    public record Candidate(Path python, String version, String source) {}

    static final String PROBE = "import sys, venv, ensurepip; print('%d.%d.%d' % sys.version_info[:3]); print(sys.executable)";
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    /** Primeiro Python ≥ 3.12 com {@code venv} e {@code ensurepip} entre as fontes conhecidas. */
    public static Optional<Candidate> find(Optional<Path> chosen) {
        List<Source> sources = new ArrayList<>();
        chosen.ifPresent(p -> sources.add(new Source(List.of(p.toString()), "indicado pela pessoa")));
        String forced = System.getProperty("nekkora.python.base");
        if (forced != null) {
            // homologação/testes: SÓ esta fonte (ex.: um caminho inexistente prova o diagnóstico "Python ausente")
            sources.add(new Source(List.of(forced), "nekkora.python.base"));
            return first(sources);
        }
        if (WINDOWS) {
            sources.add(new Source(List.of("py", "-3.12"), "lançador py -3.12"));
            sources.add(new Source(List.of("py", "-3"), "lançador py -3"));
        }
        for (Path p : onPath()) sources.add(new Source(List.of(p.toString()), "PATH"));
        for (Path p : usual()) sources.add(new Source(List.of(p.toString()), "instalação usual"));
        return first(sources);
    }

    /** Só as fontes dadas (testes). */
    public static Optional<Candidate> findAmong(List<Path> pythons) {
        List<Source> sources = new ArrayList<>();
        for (Path p : pythons) sources.add(new Source(List.of(p.toString()), "lista dada"));
        return first(sources);
    }

    private record Source(List<String> command, String label) {}

    private static Optional<Candidate> first(List<Source> sources) {
        Set<String> seen = new LinkedHashSet<>();
        for (Source s : sources) {
            if (s.command().size() == 1) {
                Path p = Path.of(s.command().get(0));
                if (!Files.isRegularFile(p) || storeAlias(p)) continue;
            }
            Optional<Candidate> c = probe(s);
            if (c.isPresent() && seen.add(c.get().python().toString().toLowerCase(Locale.ROOT)) && atLeast312(c.get().version())) return c;
        }
        return Optional.empty();
    }

    private static Optional<Candidate> probe(Source s) {
        List<String> cmd = new ArrayList<>(s.command());
        cmd.addAll(List.of("-I", "-c", PROBE));
        try {
            ProcessRunner.Result r = ProcessRunner.runRaw(cmd, Duration.ofSeconds(20));
            if (r.exit() != 0) return Optional.empty();
            String[] lines = r.output().strip().split("\\R");
            if (lines.length < 2) return Optional.empty();
            Path exe = Path.of(lines[lines.length - 1].strip());
            if (storeAlias(exe)) return Optional.empty();
            return Optional.of(new Candidate(exe, lines[lines.length - 2].strip(), s.label()));
        } catch (IOException | RuntimeException e) {
            return Optional.empty(); // "py" ausente, caminho inválido…: próxima fonte
        }
    }

    static boolean atLeast312(String version) {
        String[] v = version.split("\\.");
        try {
            int major = Integer.parseInt(v[0]);
            int minor = Integer.parseInt(v[1]);
            return major > 3 || major == 3 && minor >= 12;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean storeAlias(Path p) {
        return p.toString().toLowerCase(Locale.ROOT).contains("\\windowsapps\\");
    }

    private static List<Path> onPath() {
        List<Path> out = new ArrayList<>();
        String path = System.getenv("PATH");
        if (path == null) return out;
        for (String dir : path.split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) continue;
            for (String name : WINDOWS ? List.of("python.exe") : List.of("python3.12", "python3", "python")) {
                try {
                    Path p = Path.of(dir.strip(), name);
                    if (Files.isRegularFile(p)) out.add(p);
                } catch (RuntimeException ignored) {
                    // entrada inválida no PATH
                }
            }
        }
        return out;
    }

    private static List<Path> usual() {
        List<Path> out = new ArrayList<>();
        if (WINDOWS) {
            String local = System.getenv("LOCALAPPDATA");
            for (String v : List.of("314", "313", "312")) {
                if (local != null) out.add(Path.of(local, "Programs", "Python", "Python" + v, "python.exe"));
                out.add(Path.of("C:\\Program Files", "Python" + v, "python.exe"));
            }
        } else {
            out.addAll(List.of(Path.of("/usr/bin/python3.12"), Path.of("/usr/local/bin/python3.12"), Path.of("/usr/bin/python3")));
        }
        return out;
    }
}
