package com.nekkora.studio.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Revisão manual de uma etapa gravada (STUDIO-004, D-S004-06): quais campos diferem do valor capturado, se há
 * localizadores propostos à mão e quando a última alteração manual aconteceu. É calculada a partir do estado efetivo e
 * do {@link CapturedBaseline} — restaurar um campo ao original faz ele deixar de constar aqui.
 *
 * @param fields          campos cujo valor atual difere do capturado
 * @param manualLocators  há localizadores propostos manualmente (não verificados)
 * @param lastChangedAt   instante da última alteração manual registrada
 */
public record ManualRevision(Set<StepField> fields, boolean manualLocators, Instant lastChangedAt) {
    public ManualRevision {
        fields = Set.copyOf(fields);
        Objects.requireNonNull(lastChangedAt, "lastChangedAt");
    }
}
