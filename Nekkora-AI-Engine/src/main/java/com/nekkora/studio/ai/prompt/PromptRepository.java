package com.nekkora.studio.ai.prompt;

import com.nekkora.studio.ai.Hashes;
import com.nekkora.studio.bridge.json.Json;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * STUDIO-014 — o Prompt-Mestre em uso. Lugar único: o diretório {@code Nekkora-AI-Engine/} do repositório (índice
 * {@code prompts.json} + arquivos {@code NEKKORA-PROMPT-MESTRE-*.md}), copiado SEM edição para o jar pelo Maven. Nada
 * de prompt embutido em código: o texto é lido do recurso e conferido pelo SHA-256 registrado no índice (quebras de
 * linha normalizadas para LF, o mesmo hash em qualquer checkout). Hash divergente → o prompt é recusado.
 *
 * <p>O prompt descreve COMPORTAMENTO desejado do agente; ele não prova que uma capacidade exista no produto. Quem diz o
 * que existe é o {@link com.nekkora.studio.ai.capability.FrameworkCapabilityCatalog} e o próprio código.
 */
public final class PromptRepository {

    public static final String INDEX_SCHEMA = "nekkora-ai-engine/prompts";
    static final String BASE = "/nekkora/ai/prompts/";

    /** Uma versão do Prompt-Mestre. {@code text} só é carregado quando o hash confere. */
    public record Prompt(String id, String version, String title, String file, String sha256, String text) {
        public Prompt {
            Objects.requireNonNull(id);
            Objects.requireNonNull(text);
        }

        /** Identificação para relatórios: {@code v003 (0.3, sha256:…)} — nunca o texto. */
        public String label() {
            return id + " (" + version + ", " + sha256 + ")";
        }
    }

    /** Prompt indisponível ou adulterado (código estável, sem conteúdo). */
    public static final class PromptUnavailable extends RuntimeException {
        private final String code;

        public PromptUnavailable(String code) {
            super(code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private final String current;
    private final List<Map<String, Object>> entries;
    private final Loader loader;

    /** Origem dos arquivos (o jar; nos testes, um mapa em memória). */
    @FunctionalInterface
    public interface Loader {
        Optional<byte[]> read(String name) throws IOException;
    }

    private PromptRepository(String current, List<Map<String, Object>> entries, Loader loader) {
        this.current = current;
        this.entries = entries;
        this.loader = loader;
    }

    /** Repositório do jar do Studio. */
    public static PromptRepository bundled() {
        return of(name -> {
            try (InputStream in = PromptRepository.class.getResourceAsStream(BASE + name)) {
                return in == null ? Optional.empty() : Optional.of(in.readAllBytes());
            }
        });
    }

    public static PromptRepository of(Loader loader) {
        Map<String, Object> index;
        try {
            byte[] raw = loader.read("prompts.json").orElseThrow(() -> new PromptUnavailable("PROMPT_INDEX_MISSING"));
            index = Json.parseObject(new String(raw, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            if (e instanceof PromptUnavailable p) throw p;
            throw new PromptUnavailable("PROMPT_INDEX_INVALID");
        }
        if (!INDEX_SCHEMA.equals(index.get("schema")) || !"1.0.0".equals(index.get("version"))) throw new PromptUnavailable("PROMPT_INDEX_INVALID");
        if (!(index.get("current") instanceof String cur) || !(index.get("prompts") instanceof List<?> list)) throw new PromptUnavailable("PROMPT_INDEX_INVALID");
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) throw new PromptUnavailable("PROMPT_INDEX_INVALID");
            @SuppressWarnings("unchecked")
            Map<String, Object> e = (Map<String, Object>) m;
            for (String k : List.of("id", "version", "title", "file", "sha256")) {
                if (!(e.get(k) instanceof String s) || s.isBlank()) throw new PromptUnavailable("PROMPT_INDEX_INVALID");
            }
            if (!((String) e.get("file")).matches("NEKKORA-PROMPT-MESTRE-[A-Za-z0-9-]+\\.md")) throw new PromptUnavailable("PROMPT_INDEX_INVALID");
            entries.add(e);
        }
        return new PromptRepository(cur, List.copyOf(entries), loader);
    }

    /** O prompt vigente (o {@code current} do índice), com o hash conferido. */
    public Prompt current() {
        return byId(current);
    }

    public List<String> ids() {
        return entries.stream().map(e -> (String) e.get("id")).toList();
    }

    public Prompt byId(String id) {
        Map<String, Object> e = entries.stream().filter(x -> id.equals(x.get("id"))).findFirst()
                .orElseThrow(() -> new PromptUnavailable("PROMPT_NOT_INDEXED"));
        byte[] raw;
        try {
            raw = loader.read((String) e.get("file")).orElseThrow(() -> new PromptUnavailable("PROMPT_FILE_MISSING"));
        } catch (IOException ex) {
            throw new PromptUnavailable("PROMPT_FILE_MISSING");
        }
        String text = new String(raw, StandardCharsets.UTF_8).replace("\r\n", "\n");
        String sha = Hashes.sha256(text);
        if (!sha.equals(e.get("sha256"))) throw new PromptUnavailable("PROMPT_HASH_MISMATCH");
        return new Prompt(id, (String) e.get("version"), (String) e.get("title"), (String) e.get("file"), sha, text);
    }
}
