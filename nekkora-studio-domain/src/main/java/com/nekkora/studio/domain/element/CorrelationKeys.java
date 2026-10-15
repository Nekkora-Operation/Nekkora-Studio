package com.nekkora.studio.domain.element;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Chaves de correlação com o produtor de dados técnicos (ex.: {@code targetId}, {@code nodeId} e
 * {@code captureId} do PageMap). Permanecem exclusivamente no domínio técnico: {@link #toString()} não
 * revela valores e nenhuma tela as apresenta.
 */
public final class CorrelationKeys {
    /** Chaves conhecidas — nomes alinhados aos campos dos contratos da Chrome Extension. */
    public enum Key {
        RECORDING_ID, CAPTURE_SESSION_ID, CAPTURE_ID, PAGE_SESSION_ID, TARGET_ID, NODE_ID, DOCUMENT_ID, FRAME_ID,
        /** STUDIO-003: identidade do evento da etapa gravada (deduplicação e rastreio da origem). */
        RECORDED_EVENT_ID
    }

    private static final CorrelationKeys NONE = new CorrelationKeys(new EnumMap<>(Key.class));

    private final Map<Key, String> values;

    private CorrelationKeys(EnumMap<Key, String> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    public static CorrelationKeys none() {
        return NONE;
    }

    public static CorrelationKeys of(Map<Key, String> values) {
        EnumMap<Key, String> copy = new EnumMap<>(Key.class);
        values.forEach((k, v) -> copy.put(Objects.requireNonNull(k), Objects.requireNonNull(v)));
        return copy.isEmpty() ? NONE : new CorrelationKeys(copy);
    }

    public CorrelationKeys with(Key key, String value) {
        EnumMap<Key, String> copy = values.isEmpty() ? new EnumMap<>(Key.class) : new EnumMap<>(values);
        copy.put(Objects.requireNonNull(key), Objects.requireNonNull(value));
        return new CorrelationKeys(copy);
    }

    /** Cópia sem as chaves indicadas. */
    public CorrelationKeys without(Key... keys) {
        EnumMap<Key, String> copy = values.isEmpty() ? new EnumMap<>(Key.class) : new EnumMap<>(values);
        for (Key k : keys) copy.remove(k);
        return copy.isEmpty() ? NONE : new CorrelationKeys(copy);
    }

    public Optional<String> get(Key key) {
        return Optional.ofNullable(values.get(key));
    }

    public Map<Key, String> asMap() {
        return values;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CorrelationKeys c && values.equals(c.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return "CorrelationKeys[" + values.size() + " chave(s) interna(s)]";
    }
}
