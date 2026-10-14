package com.nekkora.studio.domain.element;

import java.util.Objects;

/**
 * Localizador candidato produzido pela análise da página (o Studio não o calcula).
 *
 * @param strategy      estratégia ({@link LocatorStrategy})
 * @param description   forma legível/semântica (ex.: {@code campo "Usuário"}), segura para exibição
 * @param expression    expressão técnica (CSS/XPath) usada por mecanismos futuros; pode ser vazia
 * @param stability     estabilidade informada pelo produtor
 * @param matchCount    quantos elementos o localizador encontrou na captura
 * @param matchesTarget se encontrou o elemento-alvo
 */
public record LocatorCandidate(LocatorStrategy strategy, String description, String expression, LocatorStability stability,
                               int matchCount, boolean matchesTarget) {
    public LocatorCandidate {
        Objects.requireNonNull(strategy, "strategy");
        description = description == null ? "" : description;
        expression = expression == null ? "" : expression;
        stability = stability == null ? LocatorStability.UNKNOWN : stability;
        if (matchCount < 0) throw new IllegalArgumentException("matchCount < 0");
    }

    /** Validado na captura: encontra exatamente o alvo. */
    public boolean validated() {
        return matchesTarget && matchCount == 1;
    }
}
