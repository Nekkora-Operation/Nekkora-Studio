package com.nekkora.studio.codegen;

import java.util.List;
import java.util.Map;

/** JSON indentado e determinístico (ordem de inserção dos mapas). */
final class Pretty {
    private Pretty() {}

    static String json(Object v) {
        StringBuilder b = new StringBuilder();
        write(b, v, 0);
        return b.toString();
    }

    private static void write(StringBuilder b, Object v, int depth) {
        String pad = "  ".repeat(depth);
        if (v == null) {
            b.append("null");
        } else if (v instanceof String s) {
            b.append(Py.str(s));
        } else if (v instanceof Boolean || v instanceof Integer || v instanceof Long) {
            b.append(v);
        } else if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) {
                b.append("{}");
                return;
            }
            b.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                b.append(pad).append("  ").append(Py.str(String.valueOf(e.getKey()))).append(": ");
                write(b, e.getValue(), depth + 1);
                b.append(++i < m.size() ? ",\n" : "\n");
            }
            b.append(pad).append('}');
        } else if (v instanceof List<?> l) {
            if (l.isEmpty()) {
                b.append("[]");
                return;
            }
            b.append("[\n");
            for (int i = 0; i < l.size(); i++) {
                b.append(pad).append("  ");
                write(b, l.get(i), depth + 1);
                b.append(i + 1 < l.size() ? ",\n" : "\n");
            }
            b.append(pad).append(']');
        } else {
            throw new IllegalArgumentException("tipo não suportado no JSON gerado");
        }
    }
}
