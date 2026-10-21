package com.nekkora.studio.codegen;

import java.util.List;
import java.util.Map;

/** Literal de string seguro para Python e TOML (aspas duplas; escapa barra, aspas e controles). */
final class Py {
    private Py() {}

    static String str(String s) {
        return quote(s, '"');
    }

    /**
     * Literal com as aspas que o {@code ruff format} prefere: duplas, a não ser que o texto tenha mais aspas duplas que
     * simples ({@code 'header input[name="search"]'}) — assim o arquivo gerado já sai formatado.
     */
    static String pref(String s) {
        long dq = s.chars().filter(c -> c == '"').count();
        long sq = s.chars().filter(c -> c == '\'').count();
        return quote(s, dq > sq ? '\'' : '"');
    }

    private static String quote(String s, char q) {
        StringBuilder b = new StringBuilder().append(q);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c == q) b.append('\\').append(c);
                    else if (c < 0x20 || c == 0x7f) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append(q).toString();
    }

    /**
     * Literal Python de dados JSON (mapas, listas, textos, booleanos), sempre aberto um item por linha com vírgula final —
     * a forma que o {@code ruff format} mantém. Recuo de 4 espaços a partir de {@code depth}.
     */
    static String literal(Object v, int depth) {
        StringBuilder b = new StringBuilder();
        literal(b, v, depth);
        return b.toString();
    }

    private static void literal(StringBuilder b, Object v, int depth) {
        String pad = "    ".repeat(depth);
        if (v == null) {
            b.append("None");
        } else if (v instanceof String s) {
            b.append(pref(s));
        } else if (v instanceof Boolean x) {
            b.append(x ? "True" : "False");
        } else if (v instanceof Integer || v instanceof Long) {
            b.append(v);
        } else if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) {
                b.append("{}");
                return;
            }
            b.append("{\n");
            for (Map.Entry<?, ?> e : m.entrySet()) {
                b.append(pad).append("    ").append(pref(String.valueOf(e.getKey()))).append(": ");
                literal(b, e.getValue(), depth + 1);
                b.append(",\n");
            }
            b.append(pad).append('}');
        } else if (v instanceof List<?> l) {
            if (l.isEmpty()) {
                b.append("[]");
                return;
            }
            b.append("[\n");
            for (Object x : l) {
                b.append(pad).append("    ");
                literal(b, x, depth + 1);
                b.append(",\n");
            }
            b.append(pad).append(']');
        } else {
            throw new IllegalArgumentException("tipo não suportado no literal gerado");
        }
    }
}
