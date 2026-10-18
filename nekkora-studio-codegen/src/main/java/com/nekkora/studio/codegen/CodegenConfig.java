package com.nekkora.studio.codegen;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.bridge.json.JsonException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Configuração de geração do projeto ({@code nekkora-studio/codegen-config/1.0.0}) — STUDIO-011. É CONFIGURAÇÃO, nunca
 * código nem dado: nome do processo, ambiente e origens permitidas, papel de cada sessão (inicialização, uma
 * transação por registro, conclusão) e a entrada tabular (formato, origem, colunas ↔ variáveis de ENTRADA do modelo).
 * Não contém valores de célula, credenciais nem seletores — os alvos vêm do modelo validado.
 *
 * <p>Leitura estrita: campo desconhecido, versão diferente, tipo errado ou valor fora do domínio → {@link Invalid}
 * com um código; o conteúdo do arquivo não entra na mensagem.
 */
public record CodegenConfig(String processName, String environment, List<String> allowedOrigins, String startUrl, List<Integer> initSessions,
                            int perRecordSession, List<Integer> endSessions, Input input, boolean observations, Advance initAdvance,
                            java.util.Map<String, String> textObservations, String revision) {

    public static final String SCHEMA = "nekkora-studio/codegen-config";
    public static final String VERSION = "1.0.0";
    private static final Pattern TOKEN = Pattern.compile("[a-z][a-z0-9_]{0,59}");
    private static final Pattern FILE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,79}");
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    /** Origem da planilha: baixada pelo próprio processo no INIT (clique numa etapa gravada) ou arquivo informado. */
    public enum Source { DOWNLOAD, FILE }

    public record Column(String header, String variable, boolean integerAsText) {}

    /**
     * Como provar que o ÚLTIMO clique da inicialização levou à tela por registro: {@code NONE} (nada a provar) ou
     * {@code REPLACED} (o nó do 1º campo da sessão por registro é substituído — formulário recriado). Declarado aqui
     * porque é comportamento do sistema-alvo, nunca presumido pelo gerador.
     */
    public enum Advance { NONE, REPLACED }

    public record Input(String format, String sheet, Source source, String downloadStep, String fileName, List<Column> columns) {}

    /** Configuração recusada; {@code code} é o motivo (sem conteúdo do arquivo). */
    public static final class Invalid extends RuntimeException {
        private final String code;

        public Invalid(String code) {
            super(code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public CodegenConfig {
        Objects.requireNonNull(input);
        allowedOrigins = List.copyOf(allowedOrigins);
        initSessions = List.copyOf(initSessions);
        endSessions = List.copyOf(endSessions);
    }

    public static CodegenConfig parse(String text) {
        Map<String, Object> m;
        try {
            m = Json.parseObject(text);
        } catch (JsonException | IllegalArgumentException e) {
            throw new Invalid("CONFIG_JSON");
        }
        only(m, Set.of("schema", "version", "processName", "environment", "allowedOrigins", "startUrl", "sessions", "input", "observations",
                "initAdvance", "textObservations"), "CONFIG");
        if (!SCHEMA.equals(m.get("schema"))) throw new Invalid("CONFIG_SCHEMA");
        if (!VERSION.equals(m.get("version"))) throw new Invalid("CONFIG_VERSION");
        String processName = str(m, "processName");
        if (!TOKEN.matcher(processName).matches()) throw new Invalid("CONFIG_PROCESS_NAME");
        String environment = str(m, "environment");
        if (!Set.of("lab", "benchmark").contains(environment)) throw new Invalid("CONFIG_ENVIRONMENT");
        List<String> origins = new ArrayList<>();
        for (Object o : list(m, "allowedOrigins")) {
            if (!(o instanceof String s) || !s.matches("https?://[a-z0-9.-]+(:[0-9]{1,5})?")) throw new Invalid("CONFIG_ORIGINS");
            origins.add(s);
        }
        if (origins.isEmpty() || new LinkedHashSet<>(origins).size() != origins.size()) throw new Invalid("CONFIG_ORIGINS");
        String startUrl = str(m, "startUrl");
        if (origins.stream().noneMatch(o -> startUrl.equals(o) || startUrl.startsWith(o + "/"))) throw new Invalid("CONFIG_START_URL");
        Map<String, Object> sessions = obj(m, "sessions");
        only(sessions, Set.of("init", "perRecord", "end"), "CONFIG_SESSIONS");
        List<Integer> init = ints(sessions, "init");
        int perRecord = positive(sessions.get("perRecord"), "CONFIG_SESSIONS");
        List<Integer> end = ints(sessions, "end");
        Set<Integer> all = new LinkedHashSet<>(init);
        all.add(perRecord);
        all.addAll(end);
        if (all.size() != init.size() + 1 + end.size()) throw new Invalid("CONFIG_SESSIONS_OVERLAP");
        Map<String, Object> in = obj(m, "input");
        only(in, Set.of("format", "sheet", "source", "downloadStep", "fileName", "columns"), "CONFIG_INPUT");
        String format = str(in, "format");
        if (!Set.of("xlsx", "csv").contains(format)) throw new Invalid("CONFIG_INPUT_FORMAT");
        Object sheetRaw = in.get("sheet");
        String sheet = sheetRaw == null || sheetRaw == Json.NULL ? null : sheetRaw instanceof String s && !s.isBlank() && s.length() <= 31 ? s : bad("CONFIG_INPUT_SHEET");
        Source source;
        try {
            source = Source.valueOf(str(in, "source"));
        } catch (IllegalArgumentException e) {
            throw new Invalid("CONFIG_INPUT_SOURCE");
        }
        String downloadStep = null;
        String fileName = null;
        if (source == Source.DOWNLOAD) {
            downloadStep = str(in, "downloadStep");
            if (!UUID.matcher(downloadStep).matches()) throw new Invalid("CONFIG_INPUT_DOWNLOAD_STEP");
            fileName = str(in, "fileName");
            if (!FILE.matcher(fileName).matches() || !fileName.toLowerCase().endsWith("." + format)) throw new Invalid("CONFIG_INPUT_FILE_NAME");
        } else if (in.containsKey("downloadStep") || in.containsKey("fileName")) {
            throw new Invalid("CONFIG_INPUT");
        }
        List<Column> columns = new ArrayList<>();
        Set<String> headers = new LinkedHashSet<>();
        Set<String> variables = new LinkedHashSet<>();
        for (Object o : list(in, "columns")) {
            if (!(o instanceof Map<?, ?> raw)) throw new Invalid("CONFIG_COLUMNS");
            @SuppressWarnings("unchecked")
            Map<String, Object> c = (Map<String, Object>) raw;
            only(c, Set.of("header", "variable", "integerAsText"), "CONFIG_COLUMNS");
            String header = str(c, "header").strip();
            String variable = str(c, "variable");
            if (header.isEmpty() || header.length() > 120 || !headers.add(header)) throw new Invalid("CONFIG_COLUMNS_HEADER");
            if (variable.isBlank() || !variables.add(variable)) throw new Invalid("CONFIG_COLUMNS_VARIABLE");
            Object iat = c.getOrDefault("integerAsText", Boolean.FALSE);
            if (!(iat instanceof Boolean b)) throw new Invalid("CONFIG_COLUMNS");
            columns.add(new Column(header, variable, b));
        }
        if (columns.isEmpty()) throw new Invalid("CONFIG_COLUMNS");
        Object obs = m.getOrDefault("observations", Boolean.FALSE);
        if (!(obs instanceof Boolean observations)) throw new Invalid("CONFIG_OBSERVATIONS");
        Advance advance;
        try {
            advance = Advance.valueOf(m.getOrDefault("initAdvance", "NONE") instanceof String a ? a : "");
        } catch (IllegalArgumentException e) {
            throw new Invalid("CONFIG_INIT_ADVANCE");
        }
        // observação por PADRÃO de texto (contrato de conteúdo, não seletor) para etapa OBSERVE sem alvo exportável
        java.util.Map<String, String> textObs = new java.util.LinkedHashMap<>();
        if (m.containsKey("textObservations")) {
            for (Object o : list(m, "textObservations")) {
                if (!(o instanceof Map<?, ?> raw)) throw new Invalid("CONFIG_TEXT_OBSERVATIONS");
                @SuppressWarnings("unchecked")
                Map<String, Object> t = (Map<String, Object>) raw;
                only(t, Set.of("stepId", "pattern"), "CONFIG_TEXT_OBSERVATIONS");
                String step = str(t, "stepId");
                String pattern = str(t, "pattern");
                if (!UUID.matcher(step).matches() || textObs.containsKey(step)) throw new Invalid("CONFIG_TEXT_OBSERVATIONS");
                if (!pattern.startsWith("^") || !pattern.endsWith("$") || pattern.length() > 300) throw new Invalid("CONFIG_TEXT_PATTERN");
                try {
                    Pattern.compile(pattern);
                } catch (java.util.regex.PatternSyntaxException e) {
                    throw new Invalid("CONFIG_TEXT_PATTERN");
                }
                textObs.put(step, pattern);
            }
        }
        return new CodegenConfig(processName, environment, origins, startUrl, init, perRecord, end,
                new Input(format, sheet, source, downloadStep, fileName, List.copyOf(columns)), observations, advance,
                java.util.Map.copyOf(textObs), "sha256:" + sha256(text.strip()));
    }

    private static <T> T bad(String code) {
        throw new Invalid(code);
    }

    private static void only(Map<String, Object> m, Set<String> allowed, String code) {
        for (String k : m.keySet()) if (!allowed.contains(k)) throw new Invalid(code + "_UNKNOWN_FIELD");
    }

    private static String str(Map<String, Object> m, String key) {
        if (!(m.get(key) instanceof String s) || s.isBlank()) throw new Invalid("CONFIG_FIELD_" + key.toUpperCase());
        return s;
    }

    private static List<Object> list(Map<String, Object> m, String key) {
        if (!(m.get(key) instanceof List<?> l)) throw new Invalid("CONFIG_FIELD_" + key.toUpperCase());
        @SuppressWarnings("unchecked")
        List<Object> out = (List<Object>) l;
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> obj(Map<String, Object> m, String key) {
        if (!(m.get(key) instanceof Map<?, ?> o)) throw new Invalid("CONFIG_FIELD_" + key.toUpperCase());
        return (Map<String, Object>) o;
    }

    private static List<Integer> ints(Map<String, Object> m, String key) {
        List<Integer> out = new ArrayList<>();
        for (Object o : list(m, key)) out.add(positive(o, "CONFIG_SESSIONS"));
        return out;
    }

    private static int positive(Object o, String code) {
        if (!(o instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue()) || n.intValue() < 1 || n.intValue() > 99) throw new Invalid(code);
        return n.intValue();
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
