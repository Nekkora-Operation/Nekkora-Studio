package com.nekkora.studio.domain.element;

import java.util.Objects;

/**
 * Passo da trajetória de acesso até o elemento (documento principal → iframe → shadow root → elemento),
 * no formato reduzido do {@code AccessStep} do PageMap 0.2.0.
 *
 * @param type        tipo do passo
 * @param label       descrição curta e segura (ex.: {@code iframe#cadastro})
 * @param locator     localizador usado para entrar neste passo; pode ser vazio
 * @param correlation chaves internas (documento, frame, nó) — nunca exibidas
 */
public record AccessStep(Type type, String label, String locator, CorrelationKeys correlation) {
    /** Mesmos valores de {@code AccessStep.type} do PageMap 0.2.0. */
    public enum Type { MAIN_DOCUMENT, IFRAME, SHADOW_ROOT, ELEMENT }

    public AccessStep {
        Objects.requireNonNull(type, "type");
        label = label == null ? "" : label;
        locator = locator == null ? "" : locator;
        correlation = correlation == null ? CorrelationKeys.none() : correlation;
    }

    public static AccessStep of(Type type, String label) {
        return new AccessStep(type, label, "", CorrelationKeys.none());
    }
}
