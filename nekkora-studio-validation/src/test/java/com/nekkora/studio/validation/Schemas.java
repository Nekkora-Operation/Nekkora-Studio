package com.nekkora.studio.validation;

import com.nekkora.studio.bridge.json.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Validador mínimo de JSON Schema para os testes de contrato. */
final class Schemas {
    private Schemas() {}

    /**
     * Validador mínimo de JSON Schema 2020-12 — mesmo validador do teste de contrato da STUDIO-006 (ProjectFormatContractTest); só as palavras-chave usadas pelos schemas ({@code $ref} local,
     * type, const, enum, pattern, min/maxLength, minimum/maximum, required, properties, additionalProperties,
     * propertyNames, items, min/maxItems, anyOf). Palavra-chave desconhecida falha (o schema não pode usar algo que o
     * teste não confere).
     */
    static final class SchemaValidator {
        private static final java.util.Set<String> KNOWN = java.util.Set.of("$ref", "type", "const", "enum", "pattern", "minLength", "maxLength", "minimum",
                "maximum", "required", "properties", "additionalProperties", "propertyNames", "items", "minItems", "maxItems", "anyOf", "description");
        private final Map<String, Object> defs;

        SchemaValidator(Map<String, Object> schema) {
            this.defs = Json.object(schema, "$defs");
        }

        List<String> validate(Object value, String definition) {
            List<String> errors = new ArrayList<>();
            check(value, Json.object(defs, definition), "$", errors);
            return errors;
        }

        @SuppressWarnings("unchecked")
        private void check(Object v, Map<String, Object> s, String at, List<String> errors) {
            for (String k : s.keySet()) if (!KNOWN.contains(k)) throw new AssertionError("palavra-chave não suportada pelo validador: " + k);
            if (s.containsKey("$ref")) {
                String ref = (String) s.get("$ref");
                check(v, Json.object(defs, ref.substring("#/$defs/".length())), at, errors);
            }
            if (s.containsKey("anyOf")) {
                boolean ok = false;
                for (Object alt : (List<Object>) s.get("anyOf")) {
                    List<String> e = new ArrayList<>();
                    check(v, (Map<String, Object>) alt, at, e);
                    ok |= e.isEmpty();
                }
                if (!ok) errors.add(at + ": nenhuma alternativa");
            }
            if (s.containsKey("type")) {
                Object t = s.get("type");
                List<Object> types = t instanceof List<?> l ? (List<Object>) l : List.of(t);
                if (types.stream().noneMatch(x -> isType(v, (String) x))) {
                    errors.add(at + ": tipo " + types);
                    return;
                }
            }
            if (s.containsKey("const") && !jsonEquals(s.get("const"), v)) errors.add(at + ": const");
            if (s.containsKey("enum") && ((List<Object>) s.get("enum")).stream().noneMatch(x -> jsonEquals(x, v))) errors.add(at + ": enum");
            if (v instanceof String str) {
                if (s.containsKey("pattern") && !Pattern.compile((String) s.get("pattern")).matcher(str).find()) errors.add(at + ": pattern");
                int len = str.codePointCount(0, str.length());
                if (s.containsKey("minLength") && len < (Long) s.get("minLength")) errors.add(at + ": minLength");
                if (s.containsKey("maxLength") && len > (Long) s.get("maxLength")) errors.add(at + ": maxLength");
            }
            if (v instanceof Long n) {
                if (s.containsKey("minimum") && n < (Long) s.get("minimum")) errors.add(at + ": minimum");
                if (s.containsKey("maximum") && n > (Long) s.get("maximum")) errors.add(at + ": maximum");
            }
            if (v instanceof List<?> list) {
                if (s.containsKey("minItems") && list.size() < (Long) s.get("minItems")) errors.add(at + ": minItems");
                if (s.containsKey("maxItems") && list.size() > (Long) s.get("maxItems")) errors.add(at + ": maxItems");
                if (s.containsKey("items")) for (int i = 0; i < list.size(); i++) check(list.get(i), (Map<String, Object>) s.get("items"), at + "[" + i + "]", errors);
            }
            if (v instanceof Map<?, ?> raw) {
                Map<String, Object> obj = (Map<String, Object>) raw;
                if (s.containsKey("required")) for (Object r : (List<Object>) s.get("required")) if (!obj.containsKey(r)) errors.add(at + ": falta " + r);
                Map<String, Object> props = s.containsKey("properties") ? (Map<String, Object>) s.get("properties") : Map.of();
                for (Map.Entry<String, Object> e : obj.entrySet()) {
                    String p = at + "." + e.getKey();
                    if (s.containsKey("propertyNames")) check(e.getKey(), (Map<String, Object>) s.get("propertyNames"), p + "(nome)", errors);
                    if (props.containsKey(e.getKey())) {
                        check(e.getValue(), (Map<String, Object>) props.get(e.getKey()), p, errors);
                    } else if (Boolean.FALSE.equals(s.get("additionalProperties"))) {
                        errors.add(p + ": campo não permitido");
                    } else if (s.get("additionalProperties") instanceof Map<?, ?> extra) {
                        check(e.getValue(), (Map<String, Object>) extra, p, errors);
                    }
                }
            }
        }

        private static boolean isType(Object v, String type) {
            return switch (type) {
                case "null" -> v == Json.NULL || v == null;
                case "string" -> v instanceof String;
                case "integer" -> v instanceof Long;
                case "number" -> v instanceof Long || v instanceof Double;
                case "boolean" -> v instanceof Boolean;
                case "object" -> v instanceof Map;
                case "array" -> v instanceof List;
                default -> throw new AssertionError("tipo " + type);
            };
        }

        private static boolean jsonEquals(Object a, Object b) {
            return Json.write(a).equals(Json.write(b));
        }
    }
}
