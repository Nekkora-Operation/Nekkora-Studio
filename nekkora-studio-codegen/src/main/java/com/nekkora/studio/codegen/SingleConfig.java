package com.nekkora.studio.codegen;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.bridge.json.JsonException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * STUDIO-014 — configuração de geração de um processo de EXECUÇÃO ÚNICA ({@code nekkora-studio/codegen-config/1.1.0},
 * {@code execution: "single"}). É um artefato TÉCNICO INTERNO: o Studio a deriva do modelo, do PDD e da especificação
 * funcional ("Desenvolver automação") e a guarda para auditoria; ninguém precisa escrevê-la. A 1.0.0 (lote tabular,
 * {@link CodegenConfig}) continua igual, byte a byte.
 *
 * <p>Conteúdo: nome do processo, ambiente e origens, endereço inicial, agrupamento das etapas em Pages (por tela) e
 * métodos, entradas (nomes de variáveis de ENTRADA), saídas (Observar → variável SAÍDA DE ETAPA), efeito declarado de cada
 * clique e a confirmação dos que têm efeito externo, e as revisões do PDD que liberaram a geração. Sem valores, sem
 * credenciais, sem seletores. Leitura estrita (campo desconhecido, versão ou domínio errado → {@link CodegenConfig.Invalid}).
 *
 * <p>NEKKORA-E2E-002 — versão 1.2.0 (só quando um recurso novo é usado; senão a 1.1.0 continua idêntica):
 * <ul>
 *   <li>{@code execution: "list"} — uma transação por linha de uma lista de entrada (CSV/XLSX com as variáveis de entrada);</li>
 *   <li>{@code reads} — como cada Observar lê: {@code text} (itens exibidos unidos), {@code date} (data por extenso →
 *       AAAA-MM-DD, só se completa), {@code address} (endereço da página); {@code optional} (alvo pode faltar: fonte
 *       alternativa declarada); {@code expected} (variável de entrada que o valor lido precisa igualar);</li>
 *   <li>{@code keys} — tecla Enter num campo ({@code navigates}: a gravação viu o documento mudar depois dela);</li>
 *   <li>{@code output} — planilha {@code xlsx}: aba, colunas na ordem (cabeçalho, campo, tipo, etapa e fonte alternativa)
 *       e campo-chave; com {@code json}, o destino de resultados de sempre.</li>
 * </ul>
 */
public record SingleConfig(String processName, String environment, List<String> allowedOrigins, String startUrl, List<Group> groups, List<String> inputs,
                           Map<String, String> outputs, Map<String, Effect> effects, Pdd pdd, String execution, Map<String, Read> reads,
                           Map<String, KeyPress> keys, Output output, String revision) {

    public static final String VERSION = "1.1.0";
    public static final String VERSION_LIST = "1.2.0";
    static final Pattern TOKEN = Pattern.compile("[a-z][a-z0-9_]{0,59}");
    static final Pattern CLASS = Pattern.compile("[A-Z][A-Za-z0-9]{0,39}");
    static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    static final Pattern VARIABLE = Pattern.compile("[a-z][a-z0-9_]{0,59}");
    static final Pattern SHA = Pattern.compile("sha256:[0-9a-f]{64}");
    static final Set<String> READ_KINDS = Set.of("text", "date", "address");
    static final Set<String> COLUMN_KINDS = Set.of("text", "iso_date", "url");

    /** Etapas consecutivas de uma tela, chamadas como um método da Page. */
    public record Group(String page, String prefix, String method, List<String> steps) {
        public Group {
            steps = List.copyOf(steps);
        }
    }

    /** Efeito declarado de um clique (ou Enter): {@code NONE} ou {@code EXTERNAL} (com a confirmação observável). */
    public record Effect(String effect, String postcondition) {}

    /** Revisões que liberaram a geração (o gate PDD_READY do Studio). */
    public record Pdd(String gate, String processRevision, String documentRevision, String specRevision) {}

    /** 1.2.0: como um Observar lê o dado. */
    public record Read(String kind, boolean optional, String expected) {}

    /** 1.2.0: tecla numa etapa Pressionar tecla. */
    public record KeyPress(String key, boolean navigates) {}

    /** 1.2.0: uma coluna da planilha de saída. {@code fallbackStep} = leitura usada quando o alvo de {@code step} falta. */
    public record Column(String header, String field, String kind, String step, String fallbackStep) {}

    /** 1.2.0: saída do processo. */
    public record Output(String format, String sheet, List<Column> columns, String keyField) {
        public Output {
            columns = List.copyOf(columns);
        }

        public static Output json() {
            return new Output("json", "", List.of(), "");
        }
    }

    public SingleConfig {
        allowedOrigins = List.copyOf(allowedOrigins);
        groups = List.copyOf(groups);
        inputs = List.copyOf(inputs);
        outputs = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
        effects = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(effects));
        Objects.requireNonNull(pdd);
        execution = execution == null ? "single" : execution;
        reads = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(reads == null ? Map.of() : reads));
        keys = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(keys == null ? Map.of() : keys));
        output = output == null ? Output.json() : output;
    }

    /** Usa algum recurso da 1.2.0? (lista, leitura tipada, tecla ou planilha) */
    public boolean modern() {
        return !"single".equals(execution) || !reads.isEmpty() || !keys.isEmpty() || !"json".equals(output.format());
    }

    public boolean list() {
        return "list".equals(execution);
    }

    public boolean spreadsheet() {
        return "xlsx".equals(output.format());
    }

    /** JSON canônico (o que o Studio grava e o que entra no manifesto pela revisão). */
    public String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schema", CodegenConfig.SCHEMA);
        m.put("version", modern() ? VERSION_LIST : VERSION);
        m.put("execution", execution);
        m.put("processName", processName);
        m.put("environment", environment);
        m.put("allowedOrigins", allowedOrigins);
        m.put("startUrl", startUrl);
        List<Object> gs = new ArrayList<>();
        for (Group g : groups) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("page", g.page());
            x.put("prefix", g.prefix());
            x.put("method", g.method());
            x.put("steps", g.steps());
            gs.add(x);
        }
        m.put("groups", gs);
        m.put("inputs", inputs);
        List<Object> outs = new ArrayList<>();
        outputs.forEach((step, variable) -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("stepId", step);
            x.put("variable", variable);
            outs.add(x);
        });
        m.put("outputs", outs);
        List<Object> effs = new ArrayList<>();
        effects.forEach((step, e) -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("stepId", step);
            x.put("effect", e.effect());
            x.put("postcondition", e.postcondition());
            effs.add(x);
        });
        m.put("effects", effs);
        if (modern()) {
            List<Object> rs = new ArrayList<>();
            reads.forEach((step, r) -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("stepId", step);
                x.put("kind", r.kind());
                x.put("optional", r.optional());
                x.put("expected", r.expected());
                rs.add(x);
            });
            m.put("reads", rs);
            List<Object> ks = new ArrayList<>();
            keys.forEach((step, k) -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("stepId", step);
                x.put("key", k.key());
                x.put("navigates", k.navigates());
                ks.add(x);
            });
            m.put("keys", ks);
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("format", output.format());
            o.put("sheet", output.sheet());
            List<Object> cols = new ArrayList<>();
            for (Column c : output.columns()) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("header", c.header());
                x.put("field", c.field());
                x.put("kind", c.kind());
                x.put("stepId", c.step());
                x.put("fallbackStepId", c.fallbackStep());
                cols.add(x);
            }
            o.put("columns", cols);
            o.put("keyField", output.keyField());
            m.put("output", o);
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("gate", pdd.gate());
        p.put("processRevision", pdd.processRevision());
        p.put("documentRevision", pdd.documentRevision());
        p.put("specRevision", pdd.specRevision());
        m.put("pdd", p);
        return Pretty.json(m) + "\n";
    }

    /** Monta a partir das partes (o Studio) — passa pela MESMA validação da leitura. */
    public static SingleConfig of(String processName, String environment, List<String> origins, String startUrl, List<Group> groups, List<String> inputs,
                                  Map<String, String> outputs, Map<String, Effect> effects, Pdd pdd) {
        return of(processName, environment, origins, startUrl, groups, inputs, outputs, effects, pdd, "single", Map.of(), Map.of(), Output.json());
    }

    /** 1.2.0: com execução, leituras, teclas e saída. */
    public static SingleConfig of(String processName, String environment, List<String> origins, String startUrl, List<Group> groups, List<String> inputs,
                                  Map<String, String> outputs, Map<String, Effect> effects, Pdd pdd, String execution, Map<String, Read> reads,
                                  Map<String, KeyPress> keys, Output output) {
        SingleConfig draft = new SingleConfig(processName, environment, origins, startUrl, groups, inputs, outputs, effects, pdd, execution, reads, keys,
                output, "");
        return parse(draft.toJson());
    }

    @SuppressWarnings("unchecked")
    public static SingleConfig parse(String text) {
        Map<String, Object> m;
        try {
            m = Json.parseObject(text);
        } catch (JsonException | IllegalArgumentException e) {
            throw new CodegenConfig.Invalid("CONFIG_JSON");
        }
        Object version = m.get("version");
        boolean modern = VERSION_LIST.equals(version);
        if (modern) {
            only(m, Set.of("schema", "version", "execution", "processName", "environment", "allowedOrigins", "startUrl", "groups", "inputs", "outputs",
                    "effects", "reads", "keys", "output", "pdd"), "CONFIG");
        } else {
            only(m, Set.of("schema", "version", "execution", "processName", "environment", "allowedOrigins", "startUrl", "groups", "inputs", "outputs",
                    "effects", "pdd"), "CONFIG");
        }
        if (!CodegenConfig.SCHEMA.equals(m.get("schema"))) throw new CodegenConfig.Invalid("CONFIG_SCHEMA");
        if (!VERSION.equals(version) && !modern) throw new CodegenConfig.Invalid("CONFIG_VERSION");
        String execution = (String) m.get("execution");
        if (!"single".equals(execution) && !(modern && "list".equals(execution))) throw new CodegenConfig.Invalid("CONFIG_EXECUTION");
        String processName = str(m, "processName");
        if (!TOKEN.matcher(processName).matches()) throw new CodegenConfig.Invalid("CONFIG_PROCESS_NAME");
        String environment = str(m, "environment");
        if (!Set.of("lab", "benchmark").contains(environment)) throw new CodegenConfig.Invalid("CONFIG_ENVIRONMENT");
        List<String> origins = new ArrayList<>();
        for (Object o : list(m, "allowedOrigins")) {
            if (!(o instanceof String s) || !s.matches("https?://[a-z0-9.-]+(:[0-9]{1,5})?")) throw new CodegenConfig.Invalid("CONFIG_ORIGINS");
            origins.add(s);
        }
        if (origins.isEmpty() || new LinkedHashSet<>(origins).size() != origins.size()) throw new CodegenConfig.Invalid("CONFIG_ORIGINS");
        String startUrl = str(m, "startUrl");
        if (startUrl.contains("?") || startUrl.contains("#") || origins.stream().noneMatch(o -> startUrl.equals(o) || startUrl.startsWith(o + "/"))) {
            throw new CodegenConfig.Invalid("CONFIG_START_URL");
        }
        List<Group> groups = new ArrayList<>();
        Set<String> seenSteps = new LinkedHashSet<>();
        Map<String, String> prefixOfPage = new LinkedHashMap<>();
        Set<String> methods = new LinkedHashSet<>();
        for (Object o : list(m, "groups")) {
            if (!(o instanceof Map<?, ?> raw)) throw new CodegenConfig.Invalid("CONFIG_GROUPS");
            Map<String, Object> g = (Map<String, Object>) raw;
            only(g, Set.of("page", "prefix", "method", "steps"), "CONFIG_GROUPS");
            String page = str(g, "page");
            String prefix = str(g, "prefix");
            String method = str(g, "method");
            if (!CLASS.matcher(page).matches() || !TOKEN.matcher(prefix).matches() || !TOKEN.matcher(method).matches()) throw new CodegenConfig.Invalid("CONFIG_GROUPS");
            if (prefixOfPage.containsKey(page) && !prefixOfPage.get(page).equals(prefix)) throw new CodegenConfig.Invalid("CONFIG_GROUPS_PREFIX");
            if (prefixOfPage.containsValue(prefix) && !prefix.equals(prefixOfPage.get(page))) throw new CodegenConfig.Invalid("CONFIG_GROUPS_PREFIX");
            prefixOfPage.put(page, prefix);
            if (!methods.add(page + "." + method)) throw new CodegenConfig.Invalid("CONFIG_GROUPS_METHOD");
            List<String> steps = new ArrayList<>();
            for (Object s : list(g, "steps")) {
                if (!(s instanceof String id) || !UUID.matcher(id).matches() || !seenSteps.add(id)) throw new CodegenConfig.Invalid("CONFIG_GROUPS_STEPS");
                steps.add(id);
            }
            if (steps.isEmpty()) throw new CodegenConfig.Invalid("CONFIG_GROUPS_STEPS");
            groups.add(new Group(page, prefix, method, steps));
        }
        if (groups.isEmpty()) throw new CodegenConfig.Invalid("CONFIG_GROUPS");
        List<String> inputs = new ArrayList<>();
        for (Object o : list(m, "inputs")) {
            if (!(o instanceof String v) || !VARIABLE.matcher(v).matches() || inputs.contains(v)) throw new CodegenConfig.Invalid("CONFIG_INPUTS");
            inputs.add(v);
        }
        Map<String, String> outputs = new LinkedHashMap<>();
        for (Object o : list(m, "outputs")) {
            if (!(o instanceof Map<?, ?> raw)) throw new CodegenConfig.Invalid("CONFIG_OUTPUTS");
            Map<String, Object> x = (Map<String, Object>) raw;
            only(x, Set.of("stepId", "variable"), "CONFIG_OUTPUTS");
            String step = str(x, "stepId");
            String var = str(x, "variable");
            if (!UUID.matcher(step).matches() || !VARIABLE.matcher(var).matches() || outputs.containsKey(step) || outputs.containsValue(var)
                    || inputs.contains(var)) {
                throw new CodegenConfig.Invalid("CONFIG_OUTPUTS");
            }
            outputs.put(step, var);
        }
        Map<String, Effect> effects = new LinkedHashMap<>();
        for (Object o : list(m, "effects")) {
            if (!(o instanceof Map<?, ?> raw)) throw new CodegenConfig.Invalid("CONFIG_EFFECTS");
            Map<String, Object> x = (Map<String, Object>) raw;
            only(x, Set.of("stepId", "effect", "postcondition"), "CONFIG_EFFECTS");
            String step = str(x, "stepId");
            String effect = str(x, "effect");
            Object pc = x.get("postcondition");
            String post = pc == null || pc == Json.NULL ? null : pc instanceof String s ? s : null;
            if (!UUID.matcher(step).matches() || effects.containsKey(step) || !Set.of("NONE", "EXTERNAL").contains(effect)) throw new CodegenConfig.Invalid("CONFIG_EFFECTS");
            if ("EXTERNAL".equals(effect) != (post != null)) throw new CodegenConfig.Invalid("CONFIG_EFFECTS_POSTCONDITION");
            if (post != null && !Set.of("TARGET_HIDDEN", "FIRST_FIELD_REPLACED", "NEXT_TARGET_VISIBLE").contains(post)) {
                throw new CodegenConfig.Invalid("CONFIG_EFFECTS_POSTCONDITION");
            }
            effects.put(step, new Effect(effect, post));
        }
        Map<String, Read> reads = new LinkedHashMap<>();
        Map<String, KeyPress> keys = new LinkedHashMap<>();
        Output output = Output.json();
        if (modern) {
            for (Object o : list(m, "reads")) {
                if (!(o instanceof Map<?, ?> raw)) throw new CodegenConfig.Invalid("CONFIG_READS");
                Map<String, Object> x = (Map<String, Object>) raw;
                only(x, Set.of("stepId", "kind", "optional", "expected"), "CONFIG_READS");
                String step = str(x, "stepId");
                String kind = str(x, "kind");
                Object opt = x.get("optional");
                Object exp = x.get("expected");
                String expected = exp == null || exp == Json.NULL ? null : exp instanceof String s ? s : "";
                if (!UUID.matcher(step).matches() || !outputs.containsKey(step) || reads.containsKey(step) || !READ_KINDS.contains(kind)
                        || !(opt instanceof Boolean) || expected != null && !inputs.contains(expected)) {
                    throw new CodegenConfig.Invalid("CONFIG_READS");
                }
                reads.put(step, new Read(kind, (Boolean) opt, expected));
            }
            for (Object o : list(m, "keys")) {
                if (!(o instanceof Map<?, ?> raw)) throw new CodegenConfig.Invalid("CONFIG_KEYS");
                Map<String, Object> x = (Map<String, Object>) raw;
                only(x, Set.of("stepId", "key", "navigates"), "CONFIG_KEYS");
                String step = str(x, "stepId");
                if (!UUID.matcher(step).matches() || keys.containsKey(step) || !"Enter".equals(x.get("key")) || !(x.get("navigates") instanceof Boolean nav)) {
                    throw new CodegenConfig.Invalid("CONFIG_KEYS");
                }
                keys.put(step, new KeyPress("Enter", nav));
            }
            Map<String, Object> o = obj(m, "output");
            only(o, Set.of("format", "sheet", "columns", "keyField"), "CONFIG_OUTPUT");
            String format = (String) o.get("format");
            if ("json".equals(format)) {
                if (!list(o, "columns").isEmpty() || !"".equals(o.get("sheet")) || !"".equals(o.get("keyField"))) throw new CodegenConfig.Invalid("CONFIG_OUTPUT");
            } else if ("xlsx".equals(format)) {
                String sheet = str(o, "sheet");
                if (sheet.length() > 31 || !sheet.matches("[\\p{L}\\p{N} ._-]+")) throw new CodegenConfig.Invalid("CONFIG_OUTPUT_SHEET");
                List<Column> cols = new ArrayList<>();
                Set<String> fields = new LinkedHashSet<>();
                Set<String> headers = new LinkedHashSet<>();
                Set<String> used = new LinkedHashSet<>();
                for (Object c : list(o, "columns")) {
                    if (!(c instanceof Map<?, ?> rawc)) throw new CodegenConfig.Invalid("CONFIG_OUTPUT_COLUMNS");
                    Map<String, Object> x = (Map<String, Object>) rawc;
                    only(x, Set.of("header", "field", "kind", "stepId", "fallbackStepId"), "CONFIG_OUTPUT_COLUMNS");
                    String header = str(x, "header");
                    String field = str(x, "field");
                    String kind = str(x, "kind");
                    String step = str(x, "stepId");
                    Object fb = x.get("fallbackStepId");
                    String fallback = fb == null || fb == Json.NULL ? null : fb instanceof String s ? s : "";
                    if (header.length() > 60 || !header.equals(header.strip()) || "=+-@".indexOf(header.charAt(0)) >= 0 || !headers.add(header.toLowerCase(java.util.Locale.ROOT))
                            || !VARIABLE.matcher(field).matches() || !fields.add(field) || !COLUMN_KINDS.contains(kind) || !outputs.containsKey(step) || !used.add(step)
                            || fallback != null && (!outputs.containsKey(fallback) || !used.add(fallback))) {
                        throw new CodegenConfig.Invalid("CONFIG_OUTPUT_COLUMNS");
                    }
                    cols.add(new Column(header, field, kind, step, fallback));
                }
                String keyField = str(o, "keyField");
                if (cols.isEmpty() || !fields.contains(keyField)) throw new CodegenConfig.Invalid("CONFIG_OUTPUT_KEY");
                output = new Output("xlsx", sheet, cols, keyField);
            } else {
                throw new CodegenConfig.Invalid("CONFIG_OUTPUT_FORMAT");
            }
            if ("list".equals(execution) && (inputs.isEmpty() || !"xlsx".equals(format))) throw new CodegenConfig.Invalid("CONFIG_LIST");
        }
        Map<String, Object> p = obj(m, "pdd");
        only(p, Set.of("gate", "processRevision", "documentRevision", "specRevision"), "CONFIG_PDD");
        Pdd pdd = new Pdd(str(p, "gate"), str(p, "processRevision"), str(p, "documentRevision"), str(p, "specRevision"));
        if (!"PDD_READY".equals(pdd.gate())) throw new CodegenConfig.Invalid("CONFIG_PDD_NOT_READY");
        for (String r : List.of(pdd.processRevision(), pdd.documentRevision(), pdd.specRevision())) {
            if (!SHA.matcher(r).matches()) throw new CodegenConfig.Invalid("CONFIG_PDD");
        }
        SingleConfig c = new SingleConfig(processName, environment, origins, startUrl, groups, inputs, outputs, effects, pdd, execution, reads, keys, output, "");
        if (modern != c.modern()) throw new CodegenConfig.Invalid("CONFIG_VERSION"); // 1.2.0 só com recurso novo (e vice-versa)
        return new SingleConfig(processName, environment, origins, startUrl, groups, inputs, outputs, effects, pdd, execution, reads, keys, output,
                "sha256:" + CodegenConfig.sha256(c.toJson().strip()));
    }

    private static void only(Map<String, Object> m, Set<String> allowed, String code) {
        for (String k : m.keySet()) if (!allowed.contains(k)) throw new CodegenConfig.Invalid(code + "_UNKNOWN_FIELD");
        for (String k : allowed) if (!m.containsKey(k)) throw new CodegenConfig.Invalid(code + "_MISSING_FIELD");
    }

    private static String str(Map<String, Object> m, String key) {
        if (!(m.get(key) instanceof String s) || s.isBlank()) throw new CodegenConfig.Invalid("CONFIG_FIELD_" + key.toUpperCase());
        return s;
    }

    private static List<Object> list(Map<String, Object> m, String key) {
        if (!(m.get(key) instanceof List<?> l)) throw new CodegenConfig.Invalid("CONFIG_FIELD_" + key.toUpperCase());
        @SuppressWarnings("unchecked")
        List<Object> out = (List<Object>) l;
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> obj(Map<String, Object> m, String key) {
        if (!(m.get(key) instanceof Map<?, ?> o)) throw new CodegenConfig.Invalid("CONFIG_FIELD_" + key.toUpperCase());
        return (Map<String, Object>) o;
    }
}
