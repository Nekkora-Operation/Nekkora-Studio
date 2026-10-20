package com.nekkora.studio.ai.deps;

import com.nekkora.studio.bridge.json.Json;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PACKAGING-001 — os ARTEFATOS HOMOLOGADOS do nekkora-rpa que o Studio sabe instalar (recurso
 * {@code /nekkora/ai/framework/artefatos.json}, gerado por {@code tools/empacotar_framework.py}): por versão, o wheel
 * com SHA-256, o commit de origem e o catálogo correspondente; e os locks por hash das dependências e das ferramentas
 * de verificação. Só versões listadas aqui são instaláveis — nenhum endereço vem da IA nem do projeto.
 */
public final class FrameworkArtifacts {

    static final String BASE = "/nekkora/ai/framework/";
    public static final String SCHEMA = "nekkora-ai-engine/framework-artifacts";

    /** Origem do pacote principal: wheel embutido no Studio (antes da publicação) ou índice aprovado (depois). */
    public enum Source { BUNDLED, INDEX }

    public record Artifact(String version, String wheel, String sha256, long bytes, String commit, String catalog, List<String> schemas, Source source,
                           String index) {
        public Artifact {
            schemas = List.copyOf(schemas);
        }
    }

    public record Lock(String resource, String sha256, String index) {}

    private final Map<String, Artifact> versions;
    private final Lock dependencies;
    private final Lock tools;
    private final String ruff;
    private final String mypy;

    private FrameworkArtifacts(Map<String, Artifact> versions, Lock dependencies, Lock tools, String ruff, String mypy) {
        this.versions = versions;
        this.dependencies = dependencies;
        this.tools = tools;
        this.ruff = ruff;
        this.mypy = mypy;
    }

    public static FrameworkArtifacts bundled() {
        try (InputStream in = FrameworkArtifacts.class.getResourceAsStream(BASE + "artefatos.json")) {
            if (in == null) throw new IllegalStateException("ARTIFACTS_MISSING");
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("ARTIFACTS_MISSING", e);
        }
    }

    @SuppressWarnings("unchecked")
    static FrameworkArtifacts parse(String json) {
        Map<String, Object> m = Json.parseObject(json);
        if (!SCHEMA.equals(m.get("schema")) || !"1.0.0".equals(m.get("version")) || !"nekkora-rpa".equals(m.get("distribution"))) {
            throw new IllegalArgumentException("ARTIFACTS_SCHEMA");
        }
        Map<String, Artifact> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : ((Map<String, Object>) m.get("versions")).entrySet()) {
            Map<String, Object> v = (Map<String, Object>) e.getValue();
            List<String> schemas = new ArrayList<>();
            for (Object s : (List<Object>) v.get("schemas")) schemas.add((String) s);
            String sha = (String) v.get("sha256");
            if (sha == null || !sha.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("ARTIFACTS_HASH");
            out.put(e.getKey(), new Artifact(e.getKey(), (String) v.get("wheel"), sha, ((Number) v.get("bytes")).longValue(), (String) v.get("commit"),
                    (String) v.get("catalog"), schemas, Source.valueOf((String) v.get("source")), v.get("index") instanceof String s ? s : null));
        }
        Map<String, Object> d = (Map<String, Object>) m.get("dependencies");
        Map<String, Object> t = (Map<String, Object>) m.get("tools");
        return new FrameworkArtifacts(java.util.Collections.unmodifiableMap(out), new Lock((String) d.get("lock"), (String) d.get("sha256"), (String) d.get("index")),
                new Lock((String) t.get("lock"), (String) t.get("sha256"), (String) t.get("index")), (String) t.get("ruff"), (String) t.get("mypy"));
    }

    public Optional<Artifact> forVersion(String version) {
        return Optional.ofNullable(versions.get(version));
    }

    public List<String> versions() {
        return List.copyOf(versions.keySet());
    }

    public Lock dependencies() {
        return dependencies;
    }

    public Lock tools() {
        return tools;
    }

    public String ruff() {
        return ruff;
    }

    public String mypy() {
        return mypy;
    }

    /** Copia o wheel embutido para {@code dir} e confere o SHA-256 do manifesto; adulterado → {@code HASH_MISMATCH}. */
    public Path materializeWheel(Artifact a, Path dir) throws IOException, DependencyException {
        if (a.source() != Source.BUNDLED) throw new DependencyException(Diagnosis.INSTALL_FAILED, "o artefato vem do índice, não do Studio");
        return materialize(a.wheel(), a.sha256(), dir);
    }

    public Path materializeLock(Lock l, Path dir) throws IOException, DependencyException {
        return materialize(l.resource(), l.sha256(), dir);
    }

    private Path materialize(String name, String sha256, Path dir) throws IOException, DependencyException {
        if (!name.matches("[A-Za-z0-9._-]+")) throw new DependencyException(Diagnosis.INSTALL_FAILED, "nome de artefato inválido");
        Files.createDirectories(dir);
        Path target = dir.resolve(name);
        try (InputStream in = FrameworkArtifacts.class.getResourceAsStream(BASE + name)) {
            if (in == null) throw new DependencyException(Diagnosis.INSTALL_FAILED, "artefato ausente no Studio: " + name);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        verify(target, sha256);
        return target;
    }

    /** SHA-256 do arquivo igual ao esperado; senão o arquivo é apagado e o erro é {@code HASH_MISMATCH}. */
    public static void verify(Path file, String sha256) throws IOException, DependencyException {
        String got = sha256(file);
        if (!got.equals(sha256)) {
            Files.deleteIfExists(file);
            throw new DependencyException(Diagnosis.HASH_MISMATCH, file.getFileName() + ": SHA-256 diferente do homologado");
        }
    }

    public static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
