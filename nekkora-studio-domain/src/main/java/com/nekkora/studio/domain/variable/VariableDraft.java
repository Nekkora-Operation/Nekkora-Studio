package com.nekkora.studio.domain.variable;

import java.util.Objects;

/**
 * Conteúdo pedido para criar ou editar uma variável (sem identidade nem instantes). Uma constante pode vir em forma
 * livre (ex.: "1.50"); a operação de domínio a confere e a guarda na forma canônica do tipo.
 */
public record VariableDraft(String name, VariableType type, SemanticType semantic, SemanticStatus authority, String purpose, VariableScope scope,
                            VariableOrigin origin, Sensitivity sensitivity, VariableConstraints constraints) {
    public VariableDraft {
        Objects.requireNonNull(type, "type");
        semantic = semantic == null ? SemanticType.UNSPECIFIED : semantic;
        authority = authority == null ? SemanticStatus.UNSPECIFIED : authority;
        purpose = purpose == null ? "" : purpose;
        scope = scope == null ? VariableScope.automation() : scope;
        origin = origin == null ? VariableOrigin.unbound() : origin;
        sensitivity = sensitivity == null ? Sensitivity.PUBLIC : sensitivity;
        constraints = constraints == null ? VariableConstraints.none() : constraints;
    }

    /** Variável mínima: nome e tipo; o resto não especificado. */
    public static VariableDraft of(String name, VariableType type) {
        return new VariableDraft(name, type, null, null, null, null, null, null, null);
    }

    public VariableDraft withName(String n) {
        return new VariableDraft(n, type, semantic, authority, purpose, scope, origin, sensitivity, constraints);
    }

    public VariableDraft withSemantic(SemanticType s, SemanticStatus a) {
        return new VariableDraft(name, type, s, a, purpose, scope, origin, sensitivity, constraints);
    }

    public VariableDraft withPurpose(String p) {
        return new VariableDraft(name, type, semantic, authority, p, scope, origin, sensitivity, constraints);
    }

    public VariableDraft withScope(VariableScope s) {
        return new VariableDraft(name, type, semantic, authority, purpose, s, origin, sensitivity, constraints);
    }

    public VariableDraft withOrigin(VariableOrigin o) {
        return new VariableDraft(name, type, semantic, authority, purpose, scope, o, sensitivity, constraints);
    }

    public VariableDraft withSensitivity(Sensitivity s) {
        return new VariableDraft(name, type, semantic, authority, purpose, scope, origin, s, constraints);
    }

    public VariableDraft withConstraints(VariableConstraints c) {
        return new VariableDraft(name, type, semantic, authority, purpose, scope, origin, sensitivity, c);
    }

    public VariableDraft withType(VariableType t) {
        return new VariableDraft(name, t, semantic, authority, purpose, scope, origin, sensitivity, constraints);
    }

    @Override
    public String toString() {
        return "VariableDraft[" + type + ", " + semantic + "]"; // nem nome, nem finalidade, nem valor em logs (revisão 1 do PR #11)
    }
}
