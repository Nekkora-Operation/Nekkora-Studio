package com.nekkora.studio.app.project;

import com.nekkora.studio.bridge.json.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Projetos recentes (STUDIO-006, §30): só caminho, último acesso e versão do formato quando conhecida — nunca cópia do
 * projeto nem dado de página. Sem diretório de dados (testes), fica só em memória. A disponibilidade é verificada na
 * hora de mostrar (o arquivo pode ter sido removido).
 */
public final class RecentProjects {
    public static final int MAX = 10;
    private static final Logger LOG = Logger.getLogger(RecentProjects.class.getName());

    /** Uma entrada da lista. */
    public record Entry(Path path, Instant lastAccess, String formatVersion) {
        /** O arquivo ainda existe. */
        public boolean available() {
            return Files.isRegularFile(path);
        }
    }

    private final Path file;
    private final List<Entry> entries = new ArrayList<>();

    private RecentProjects(Path file) {
        this.file = file;
    }

    /** Lista persistida em {@code <dataDir>/recent-projects.json}; um arquivo ilegível vira lista vazia (não bloqueia o Studio). */
    public static RecentProjects load(Path dataDir) {
        RecentProjects r = new RecentProjects(dataDir.resolve("recent-projects.json"));
        try {
            if (Files.isRegularFile(r.file) && Files.size(r.file) < 256 * 1024) {
                Map<String, Object> m = Json.parseObject(Files.readString(r.file, StandardCharsets.UTF_8));
                for (Object o : Json.array(m, "projects")) {
                    if (!(o instanceof Map<?, ?> raw)) continue;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> e = (Map<String, Object>) raw;
                    String path = Json.string(e, "path");
                    String at = Json.string(e, "lastAccess");
                    if (path == null || at == null || r.entries.size() >= MAX) continue;
                    try {
                        r.entries.add(new Entry(Path.of(path), Instant.parse(at), Json.string(e, "formatVersion")));
                    } catch (RuntimeException ignored) {
                        // entrada inválida: ignorada
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.warning("Lista de projetos recentes ilegível: " + e.getClass().getSimpleName());
        }
        return r;
    }

    public static RecentProjects inMemory() {
        return new RecentProjects(null);
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    /** Registra (ou move para o topo) o projeto. */
    public void touch(Path path, Instant at, String formatVersion) {
        Path p = path.toAbsolutePath().normalize();
        entries.removeIf(e -> e.path().equals(p));
        entries.add(0, new Entry(p, at, formatVersion));
        while (entries.size() > MAX) entries.remove(entries.size() - 1);
        persist();
    }

    public void remove(Path path) {
        Path p = path.toAbsolutePath().normalize();
        if (entries.removeIf(e -> e.path().equals(p))) persist();
    }

    public Optional<Entry> find(Path path) {
        Path p = path.toAbsolutePath().normalize();
        return entries.stream().filter(e -> e.path().equals(p)).findFirst();
    }

    private void persist() {
        if (file == null) return;
        List<Object> list = new ArrayList<>();
        for (Entry e : entries) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("path", e.path().toString());
            m.put("lastAccess", e.lastAccess().toString());
            m.put("formatVersion", e.formatVersion() == null ? Json.NULL : e.formatVersion());
            list.add(m);
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("projects", list);
        try {
            StudioDataDir.writeAtomically(file, Json.write(doc).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.warning("Lista de projetos recentes não gravada: " + e.getClass().getSimpleName());
        }
    }
}
