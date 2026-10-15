package com.nekkora.studio.bridge.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON estrito (RFC 8259) para o canal de controle do Bridge — sem dependências externas.
 *
 * <p>Leitura: objetos viram {@code Map<String, Object>} (ordem preservada, chaves duplicadas recusadas), arrays
 * {@code List<Object>}, números inteiros {@code Long}, demais números {@code Double}, {@code true/false}
 * {@code Boolean} e {@code null} {@link #NULL}. Profundidade limitada a {@value #MAX_DEPTH}.
 */
public final class Json {

    /** Marcador do {@code null} JSON (distingue "campo nulo" de "campo ausente"). */
    public static final Object NULL = new Object() {
        @Override
        public String toString() {
            return "null";
        }
    };

    public static final int MAX_DEPTH = 64;

    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    /** Analisa um documento JSON completo. */
    public static Object parse(String text) {
        Json p = new Json(text);
        p.skipWs();
        Object v = p.value(0);
        p.skipWs();
        if (p.pos != text.length()) {
            throw p.error("conteúdo após o fim do documento");
        }
        return v;
    }

    /** Analisa um documento que precisa ser um objeto. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) {
            throw new JsonException("esperado um objeto JSON");
        }
        return (Map<String, Object>) v;
    }

    private Object value(int depth) {
        if (depth > MAX_DEPTH) {
            throw error("profundidade máxima excedida");
        }
        if (pos >= text.length()) {
            throw error("fim inesperado");
        }
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> object(depth);
            case '[' -> array(depth);
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", NULL);
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) {
                    yield number();
                }
                throw error("caractere inesperado '" + c + "'");
            }
        };
    }

    private Map<String, Object> object(int depth) {
        Map<String, Object> out = new LinkedHashMap<>();
        pos++;
        skipWs();
        if (peek() == '}') {
            pos++;
            return out;
        }
        while (true) {
            skipWs();
            if (peek() != '"') {
                throw error("esperado nome de campo");
            }
            String key = string();
            skipWs();
            expect(':');
            skipWs();
            Object v = value(depth + 1);
            if (out.putIfAbsent(key, v) != null) {
                throw error("campo duplicado: " + key);
            }
            skipWs();
            char c = next();
            if (c == '}') {
                return out;
            }
            if (c != ',') {
                throw error("esperado ',' ou '}'");
            }
        }
    }

    private List<Object> array(int depth) {
        List<Object> out = new ArrayList<>();
        pos++;
        skipWs();
        if (peek() == ']') {
            pos++;
            return out;
        }
        while (true) {
            skipWs();
            out.add(value(depth + 1));
            skipWs();
            char c = next();
            if (c == ']') {
                return out;
            }
            if (c != ',') {
                throw error("esperado ',' ou ']'");
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw error("texto não terminado");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c < 0x20) {
                throw error("caractere de controle sem escape");
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = next();
            switch (e) {
                case '"', '\\', '/' -> sb.append(e);
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (pos + 4 > text.length()) {
                        throw error("escape \\u incompleto");
                    }
                    try {
                        sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error("escape \\u inválido");
                    }
                    pos += 4;
                }
                default -> throw error("escape inválido \\" + e);
            }
        }
    }

    private Object number() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        if (peek() == '0') {
            pos++;
        } else if (peek() >= '1' && peek() <= '9') {
            while (peek() >= '0' && peek() <= '9') {
                pos++;
            }
        } else {
            throw error("número inválido");
        }
        boolean decimal = false;
        if (peek() == '.') {
            decimal = true;
            pos++;
            if (!(peek() >= '0' && peek() <= '9')) {
                throw error("número inválido");
            }
            while (peek() >= '0' && peek() <= '9') {
                pos++;
            }
        }
        if (peek() == 'e' || peek() == 'E') {
            decimal = true;
            pos++;
            if (peek() == '+' || peek() == '-') {
                pos++;
            }
            if (!(peek() >= '0' && peek() <= '9')) {
                throw error("número inválido");
            }
            while (peek() >= '0' && peek() <= '9') {
                pos++;
            }
        }
        String s = text.substring(start, pos);
        if (!decimal) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ex) {
                return Double.parseDouble(s);
            }
        }
        return Double.parseDouble(s);
    }

    private Object literal(String word, Object v) {
        if (!text.startsWith(word, pos)) {
            throw error("literal inválido");
        }
        pos += word.length();
        return v;
    }

    private void skipWs() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                return;
            }
        }
    }

    private char peek() {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    private char next() {
        if (pos >= text.length()) {
            throw error("fim inesperado");
        }
        return text.charAt(pos++);
    }

    private void expect(char c) {
        if (next() != c) {
            throw error("esperado '" + c + "'");
        }
    }

    private JsonException error(String message) {
        return new JsonException(message + " (posição " + pos + ")");
    }

    // ------------------------------------------------------------------------------------ escrita

    /** Serializa Map/List/String/Number/Boolean/{@link #NULL}/null (null Java vira {@code null}). */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v, int depth) {
        if (depth > MAX_DEPTH) {
            throw new JsonException("profundidade máxima excedida");
        }
        if (v == null || v == NULL) {
            sb.append("null");
        } else if (v instanceof String s) {
            quote(sb, s);
        } else if (v instanceof Boolean b) {
            sb.append(b);
        } else if (v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof Byte) {
            sb.append(((Number) v).longValue());
        } else if (v instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new JsonException("número não representável em JSON");
            }
            sb.append(d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : Double.toString(d));
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                quote(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue(), depth + 1);
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, o, depth + 1);
            }
            sb.append(']');
        } else {
            throw new JsonException("tipo não serializável: " + v.getClass().getSimpleName());
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------------------------ leitura tipada

    /** Objeto filho ({@code null} se ausente ou de outro tipo). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v instanceof List ? (List<Object>) v : Collections.emptyList();
    }

    public static String string(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v instanceof String s ? s : null;
    }

    public static long number(Map<String, Object> m, String key, long fallback) {
        Object v = m == null ? null : m.get(key);
        return v instanceof Number n ? n.longValue() : fallback;
    }

    public static boolean bool(Map<String, Object> m, String key, boolean fallback) {
        Object v = m == null ? null : m.get(key);
        return v instanceof Boolean b ? b : fallback;
    }

    /** Construtor fluente de objetos (ordem de inserção preservada). */
    public static Builder obj() {
        return new Builder();
    }

    /** Monta um objeto JSON. */
    public static final class Builder {
        private final Map<String, Object> m = new LinkedHashMap<>();

        public Builder put(String key, Object value) {
            m.put(key, value == null ? NULL : value);
            return this;
        }

        public Map<String, Object> build() {
            return m;
        }
    }
}
