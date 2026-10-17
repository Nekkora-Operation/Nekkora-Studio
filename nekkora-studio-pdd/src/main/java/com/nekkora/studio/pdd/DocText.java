package com.nekkora.studio.pdd;

import java.util.List;
import java.util.Objects;

/**
 * Texto do documento com a sua procedência e situação de revisão.
 *
 * @param value    o texto (já forma final; nunca contém valores, variáveis, seletores — ver {@link PddText})
 * @param state    {@code DERIVED} (modelo determinístico sobre fatos seguros), {@code NEEDS_REVIEW} (faltou contexto
 *                 seguro: neutro, precisa de revisão antes de exportar) ou {@code REVIEWED} (escrito/aceito no editor)
 * @param reviewer quem revisou ({@code DEVELOPER} ou {@code TEST_AGENT}); vazio se não revisado
 */
public record DocText(String value, State state, String reviewer) {

    /** Situação de revisão. */
    public enum State { DERIVED, NEEDS_REVIEW, REVIEWED }

    /** Revisores aceitos (o mesmo vocabulário de ator da STUDIO-009; nunca "confirmado por humano"). */
    public static final List<String> REVIEWERS = List.of("DEVELOPER", "TEST_AGENT");

    public DocText {
        Objects.requireNonNull(value);
        Objects.requireNonNull(state);
        reviewer = reviewer == null ? "" : reviewer;
        if (state == State.REVIEWED && !REVIEWERS.contains(reviewer)) throw new IllegalArgumentException("revisor inválido");
        if (state != State.REVIEWED && !reviewer.isEmpty()) throw new IllegalArgumentException("revisor só em texto revisado");
    }

    public static DocText derived(String v) {
        return new DocText(v, State.DERIVED, "");
    }

    public static DocText needsReview(String neutral) {
        return new DocText(neutral, State.NEEDS_REVIEW, "");
    }

    public static DocText reviewed(String v, String reviewer) {
        return new DocText(v, State.REVIEWED, reviewer);
    }

    /** Pode ir ao PDF: não pendente de revisão e sem problemas da política de texto. */
    public boolean exportable() {
        return state != State.NEEDS_REVIEW && PddText.problems(value).isEmpty();
    }
}
