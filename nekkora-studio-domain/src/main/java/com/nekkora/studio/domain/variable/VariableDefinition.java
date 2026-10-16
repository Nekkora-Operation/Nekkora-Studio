package com.nekkora.studio.domain.variable;

import java.time.Instant;
import java.util.Objects;

/**
 * Declaração de uma variável do processo (STUDIO-008). Imutável: cada edição produz uma nova definição com a MESMA
 * identidade. É metadado declarado pelo desenvolvedor — nunca um valor de execução, nunca algo observado na página.
 *
 * <p>A finalidade ({@link #purpose()}) é contexto auxiliar para pessoas, não fonte de verdade executável: o significado
 * que vale é o {@link #semantic()} confirmado ({@link #authority()}). As regras de coerência ficam em
 * {@link VariableRules}; esta classe só garante a forma.
 *
 * @param id          identidade permanente
 * @param name        nome simbólico (mesma sintaxe de {@code ValueSource.REFERENCE_NAME}; o ponto faz parte do nome)
 * @param type        tipo técnico
 * @param semantic    significado (vocabulário fechado)
 * @param authority   quem sustenta o significado
 * @param purpose     finalidade em texto livre (opcional)
 * @param scope       visibilidade
 * @param origin      origem declarada do valor
 * @param sensitivity classificação de sensibilidade
 * @param constraints restrições tipadas
 * @param createdAt   criação
 * @param updatedAt   última alteração
 */
public record VariableDefinition(VariableId id, String name, VariableType type, SemanticType semantic, SemanticStatus authority, String purpose,
                                 VariableScope scope, VariableOrigin origin, Sensitivity sensitivity, VariableConstraints constraints, Instant createdAt,
                                 Instant updatedAt) {
    public VariableDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(semantic, "semantic");
        Objects.requireNonNull(authority, "authority");
        purpose = purpose == null ? "" : purpose;
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(sensitivity, "sensitivity");
        Objects.requireNonNull(constraints, "constraints");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** Mesma identidade e instante de criação, outro conteúdo (valor novo; o agregado só muda pelas operações de Automation). */
    public VariableDefinition with(VariableDraft d, Instant now) {
        return new VariableDefinition(id, d.name(), d.type(), d.semantic(), d.authority(), d.purpose(), d.scope(), d.origin(), d.sensitivity(),
                d.constraints(), createdAt, now);
    }

    /** Mesmo conteúdo, outro nome (valor novo). */
    public VariableDefinition renamed(String newName, Instant now) {
        return new VariableDefinition(id, newName, type, semantic, authority, purpose, scope, origin, sensitivity, constraints, createdAt, now);
    }

    /** O conteúdo declarado (para comparação e para o formulário de edição). */
    public VariableDraft draft() {
        return new VariableDraft(name, type, semantic, authority, purpose, scope, origin, sensitivity, constraints);
    }

    @Override
    public String toString() {
        return "VariableDefinition[" + type + ", " + semantic + "]"; // nem nome nem valor em logs
    }
}
