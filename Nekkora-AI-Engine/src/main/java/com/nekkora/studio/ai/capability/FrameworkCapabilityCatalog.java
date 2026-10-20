package com.nekkora.studio.ai.capability;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.action.ActionType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * STUDIO-014 — capacidades REAIS do {@code nekkora_rpa}: o catálogo é GERADO por introspecção do pacote instalado
 * ({@code Nekkora-AI-Engine/tools/gerar_catalogo.py}) e vai no jar. Cada item traz import, assinatura, versão, efeito,
 * limitações, o arquivo/linha dentro do pacote e a seção da documentação do repositório do framework (conferida na
 * geração). Uma capacidade que não está aqui não existe para o Studio: {@link #require} devolve
 * {@code UNSUPPORTED_CAPABILITY} com o caminho de extensão — nada de método inventado.
 *
 * <p>{@link #compare} confronta o catálogo com a SONDA do interpretador escolhido (V2/V4): versão diferente ou assinatura
 * divergente viram achados explícitos, nunca aceitação silenciosa.
 */
public final class FrameworkCapabilityCatalog {

    public static final String SCHEMA = "nekkora-ai-engine/framework-capabilities";
    static final String RESOURCE = "/nekkora/ai/catalog/nekkora-rpa-0.3.0.json";

    /** Efeito declarado da capacidade (curadoria): o que ela pode causar fora do processo. */
    public enum Effect { NONE, READ, NAVIGATION, MAY_AFFECT_TARGET, LOCAL_FILES }

    public record Source(String file, int line) {}

    public record DocRef(String file, String section) {}

    public record Capability(String id, String name, String group, String importLine, String signature, String summary, String purpose, Effect effect,
                             List<String> limitations, Source source, DocRef docs) {
        public Capability {
            Objects.requireNonNull(id);
            limitations = List.copyOf(limitations);
        }

        /** "Onde consultar": o código no pacote instalado e a seção da documentação do framework. */
        public String whereToRead(String version) {
            return "nekkora_rpa " + version + " · " + source.file() + ":" + source.line() + " · " + docs.file() + " — "
                    + docs.section().replaceFirst("^#+\\s*", "");
        }
    }

    /** Capacidade pedida que o framework não tem. */
    public record Unsupported(String code, String requested, DocRef extending) {}

    /** Resultado da sonda do interpretador (JSON da {@code sonda_framework.py}). */
    public record Probe(String version, String python, Map<String, String> signatures, List<String> missing, String fingerprint) {
        public Probe {
            signatures = Map.copyOf(signatures);
            missing = List.copyOf(missing);
        }

        public static Probe parse(String json) {
            Map<String, Object> m = Json.parseObject(json);
            if (m.get("error") instanceof String e) throw new IllegalArgumentException(e);
            Map<String, String> sig = new LinkedHashMap<>();
            Object raw = m.get("signatures");
            if (!(raw instanceof Map<?, ?> s)) throw new IllegalArgumentException("PROBE_INVALID");
            for (Map.Entry<?, ?> e : s.entrySet()) {
                if (!(e.getKey() instanceof String k) || !(e.getValue() instanceof String v)) throw new IllegalArgumentException("PROBE_INVALID");
                sig.put(k, v);
            }
            List<String> missing = new ArrayList<>();
            if (m.get("missing") instanceof List<?> l) for (Object o : l) if (o instanceof String x) missing.add(x);
            String v = m.get("version") instanceof String x ? x : "";
            String py = m.get("python") instanceof String x ? x : "";
            String fp = m.get("fingerprint") instanceof String x ? x : "";
            return new Probe(v, py, sig, missing, fp);
        }
    }

    /** Divergência entre o catálogo e o instalado. */
    public record Mismatch(String code, String capability) {}

    /** Capacidades que o código gerado usa para cada tipo de ação do modelo (o que o gerador emite). */
    private static final Map<ActionType, List<String>> BY_ACTION = Map.of(
            ActionType.NAVIGATE, List.of("actions.navigate"),
            ActionType.FILL, List.of("actions.fill"),
            ActionType.CLICK, List.of("actions.click"),
            ActionType.WAIT_FOR, List.of("actions.wait_visible"),
            ActionType.OBSERVE, List.of("actions.read_text"),
            ActionType.PRESS_KEY, List.of("actions.press")); // NEKKORA-E2E-002 (0.3.0)

    private final String version;
    private final String fingerprint;
    private final String commit;
    private final DocRef extending;
    private final Map<String, Capability> byId;

    private FrameworkCapabilityCatalog(String version, String fingerprint, String commit, DocRef extending, Map<String, Capability> byId) {
        this.version = version;
        this.fingerprint = fingerprint;
        this.commit = commit;
        this.extending = extending;
        this.byId = byId;
    }

    /** O catálogo do jar (gerado do framework 0.3.0; o da 0.2.0 continua no jar para auditoria). */
    public static FrameworkCapabilityCatalog bundled() {
        try (InputStream in = FrameworkCapabilityCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("CATALOG_MISSING");
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("CATALOG_MISSING", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static FrameworkCapabilityCatalog parse(String json) {
        Map<String, Object> m = Json.parseObject(json);
        if (!SCHEMA.equals(m.get("schema")) || !"1.0.0".equals(m.get("version"))) throw new IllegalArgumentException("CATALOG_SCHEMA");
        Map<String, Object> fw = (Map<String, Object>) m.get("framework");
        Map<String, Object> ext = (Map<String, Object>) m.get("extending");
        Map<String, Capability> out = new LinkedHashMap<>();
        for (Object o : (List<Object>) m.get("capabilities")) {
            Map<String, Object> c = (Map<String, Object>) o;
            Map<String, Object> src = (Map<String, Object>) c.get("source");
            Map<String, Object> docs = (Map<String, Object>) c.get("docs");
            List<String> lim = new ArrayList<>();
            for (Object l : (List<Object>) c.get("limitations")) lim.add((String) l);
            Capability cap = new Capability((String) c.get("id"), (String) c.get("name"), (String) c.get("group"), (String) c.get("import"),
                    (String) c.get("signature"), (String) c.get("summary"), (String) c.get("purpose"), Effect.valueOf((String) c.get("effect")), lim,
                    new Source((String) src.get("file"), ((Number) src.get("line")).intValue()), new DocRef((String) docs.get("file"), (String) docs.get("section")));
            if (out.put(cap.id(), cap) != null) throw new IllegalArgumentException("CATALOG_DUPLICATE");
        }
        return new FrameworkCapabilityCatalog((String) fw.get("version"), (String) fw.get("fingerprint"), (String) fw.get("commit"),
                new DocRef((String) ext.get("file"), (String) ext.get("section")), java.util.Collections.unmodifiableMap(out));
    }

    public String version() {
        return version;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public String commit() {
        return commit;
    }

    public DocRef extending() {
        return extending;
    }

    public List<Capability> all() {
        return List.copyOf(byId.values());
    }

    public Optional<Capability> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** A capacidade existe? Senão, {@code UNSUPPORTED_CAPABILITY} com o caminho de extensão (sem inventar método). */
    public Optional<Unsupported> require(String id) {
        return byId.containsKey(id) ? Optional.empty() : Optional.of(new Unsupported("UNSUPPORTED_CAPABILITY", id, extending));
    }

    /** Capacidades que o gerador emite para a ação; vazio = a ação não tem tradução no framework desta versão. */
    public List<Capability> forAction(ActionType action) {
        return BY_ACTION.getOrDefault(action, List.of()).stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    /** O mapa {@code id → objeto} para a sonda (o mesmo que gerou o catálogo). */
    public Map<String, String> probeRequest() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Capability c : byId.values()) out.put(c.id(), objectPath(c));
        return out;
    }

    private static String objectPath(Capability c) {
        // o módulo vem do arquivo do pacote (nekkora_rpa/libs/actions.py → nekkora_rpa.libs.actions)
        String module = c.source().file().replaceFirst("\\.py$", "").replace('/', '.').replaceFirst("\\.__init__$", "");
        return module + ":" + c.name();
    }

    /** Divergências entre este catálogo e o interpretador sondado; vazio = compatível. */
    public List<Mismatch> compare(Probe p) {
        List<Mismatch> out = new ArrayList<>();
        if (!version.equals(p.version())) out.add(new Mismatch("FRAMEWORK_VERSION_MISMATCH", null));
        for (String id : p.missing()) out.add(new Mismatch("CAPABILITY_MISSING_IN_INSTALLED", id));
        for (Capability c : byId.values()) {
            String sig = p.signatures().get(c.id());
            if (sig != null && !sig.equals(c.signature())) out.add(new Mismatch("SIGNATURE_MISMATCH", c.id()));
        }
        return out;
    }

    /** Ações do modelo que o gerador sabe traduzir nesta versão. */
    public static Set<ActionType> generatable() {
        return BY_ACTION.keySet();
    }
}
