package com.nekkora.studio.persistence;

import com.nekkora.studio.bridge.json.Json;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Leitura ESTRITA de um objeto JSON de um documento do projeto: o objeto precisa ter exatamente os campos declarados
 * (nenhum a mais — um campo desconhecido não é descartado em silêncio —, nenhum a menos; opcionais aparecem como
 * {@code null}), cada um com o tipo esperado. Toda falha vira {@link ProjectException.Error#INVALID_CONTENT} com a
 * posição (nunca o valor).
 */
final class Doc {
    private final Map<String, Object> m;
    private final String where;

    private Doc(Map<String, Object> m, String where) {
        this.m = m;
        this.where = where;
    }

    /** Objeto com exatamente estes campos. */
    @SuppressWarnings("unchecked")
    static Doc of(Object value, String where, String... fields) throws ProjectException {
        if (!(value instanceof Map<?, ?> raw)) throw invalid(where, "esperado um objeto");
        Map<String, Object> map = (Map<String, Object>) raw;
        Set<String> expected = Set.of(fields);
        for (String k : map.keySet()) {
            if (!expected.contains(k)) throw invalid(where + "." + k, "campo desconhecido");
        }
        for (String f : fields) {
            if (!map.containsKey(f)) throw invalid(where + "." + f, "campo obrigatório ausente");
        }
        return new Doc(map, where);
    }

    String where() {
        return where;
    }

    String at(String field) {
        return where + "." + field;
    }

    boolean isNull(String field) {
        return m.get(field) == Json.NULL;
    }

    String str(String field) throws ProjectException {
        Object v = m.get(field);
        if (!(v instanceof String s)) throw invalid(at(field), "esperado texto");
        return s;
    }

    String strOrNull(String field) throws ProjectException {
        return isNull(field) ? null : str(field);
    }

    long lng(String field) throws ProjectException {
        Object v = m.get(field);
        if (!(v instanceof Long n)) throw invalid(at(field), "esperado número inteiro");
        return n;
    }

    int integer(String field, int min, int max) throws ProjectException {
        long v = lng(field);
        if (v < min || v > max) throw invalid(at(field), "fora do intervalo");
        return (int) v;
    }

    boolean bool(String field) throws ProjectException {
        Object v = m.get(field);
        if (!(v instanceof Boolean b)) throw invalid(at(field), "esperado verdadeiro/falso");
        return b;
    }

    Instant instant(String field) throws ProjectException {
        return instant(str(field), at(field));
    }

    Instant instantOrNull(String field) throws ProjectException {
        return isNull(field) ? null : instant(field);
    }

    static Instant instant(String text, String where) throws ProjectException {
        try {
            Instant i = Instant.parse(text);
            if (!i.toString().equals(text)) throw invalid(where, "instante fora da forma canônica");
            return i;
        } catch (DateTimeException e) {
            throw invalid(where, "instante inválido");
        }
    }

    <E extends Enum<E>> E enumValue(String field, Class<E> type) throws ProjectException {
        return enumValue(str(field), type, at(field));
    }

    <E extends Enum<E>> E enumOrNull(String field, Class<E> type) throws ProjectException {
        return isNull(field) ? null : enumValue(field, type);
    }

    static <E extends Enum<E>> E enumValue(String name, Class<E> type, String where) throws ProjectException {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            throw invalid(where, "valor não reconhecido");
        }
    }

    Object raw(String field) {
        return m.get(field);
    }

    Doc obj(String field, String... fields) throws ProjectException {
        return of(m.get(field), at(field), fields);
    }

    Doc objOrNull(String field, String... fields) throws ProjectException {
        return isNull(field) ? null : obj(field, fields);
    }

    List<Object> list(String field, int max) throws ProjectException {
        Object v = m.get(field);
        if (!(v instanceof List<?> l)) throw invalid(at(field), "esperada uma lista");
        if (l.size() > max) throw new ProjectException(ProjectException.Error.TOO_LARGE, at(field));
        return new ArrayList<>(l);
    }

    List<String> strings(String field, int max) throws ProjectException {
        List<String> out = new ArrayList<>();
        List<Object> l = list(field, max);
        for (int i = 0; i < l.size(); i++) {
            if (!(l.get(i) instanceof String s)) throw invalid(at(field) + "[" + i + "]", "esperado texto");
            out.add(s);
        }
        return out;
    }

    /** Mapa texto → texto (ex.: chaves de correlação). */
    @SuppressWarnings("unchecked")
    Map<String, String> stringMap(String field, int max) throws ProjectException {
        Object v = m.get(field);
        if (!(v instanceof Map<?, ?> raw)) throw invalid(at(field), "esperado um objeto");
        if (raw.size() > max) throw new ProjectException(ProjectException.Error.TOO_LARGE, at(field));
        for (Map.Entry<String, Object> e : ((Map<String, Object>) raw).entrySet()) {
            if (!(e.getValue() instanceof String)) throw invalid(at(field) + "." + e.getKey(), "esperado texto");
        }
        return (Map<String, String>) (Map<?, ?>) raw;
    }

    interface Reader<T> {
        T read(Object value, String where) throws ProjectException;
    }

    <T> List<T> each(String field, int max, Reader<T> reader) throws ProjectException {
        List<Object> l = list(field, max);
        List<T> out = new ArrayList<>(l.size());
        for (int i = 0; i < l.size(); i++) out.add(reader.read(l.get(i), at(field) + "[" + i + "]"));
        return out;
    }

    static ProjectException invalid(String where, String why) {
        return new ProjectException(ProjectException.Error.INVALID_CONTENT, where + ": " + why);
    }

    /** Converte uma exceção de validação do domínio (construtores) em conteúdo inválido, com a posição. */
    static <T> T domain(String where, Function<Void, T> build) throws ProjectException {
        try {
            return build.apply(null);
        } catch (RuntimeException e) {
            String reason = e instanceof com.nekkora.studio.domain.DomainValidationException d ? d.problem().name() : "regra do domínio";
            throw invalid(where, reason);
        }
    }
}
