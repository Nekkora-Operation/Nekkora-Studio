package com.nekkora.studio.domain.target;

import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.element.AccessStep;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Decisão DURÁVEL sobre o alvo de uma etapa (STUDIO-009, D-S009-07): "este é o elemento que a etapa quer" (confirmado)
 * ou "este não é" (rejeitado), com o ator, o instante e o que embasou a decisão. É separada da verificação técnica, que é
 * situada (página, instante, fonte de identidade) e envelhece: a decisão registra só um RESUMO em códigos da verificação
 * que a sustentou ({@link Basis}) e o que se esperava da etapa naquele momento ({@link Expectation}), para que uma
 * segunda passagem saiba se a mesma decisão ainda vale (G5) — nunca porque "a string CSS coincide".
 *
 * <p>Sem valores e sem texto livre: ação, códigos, tipo/tag do elemento, expressão do localizador preferencial (já
 * analisada pela {@code nekkora-privacy/2}), modelo de URL e o {@code targetId} técnico.
 *
 * @param kind        confirmado ou rejeitado
 * @param actor       quem decidiu
 * @param decidedAt   quando
 * @param basis       resumo da verificação técnica que embasou a decisão
 * @param expectation o que a etapa esperava no momento da decisão
 */
public record TargetDecision(Kind kind, DecisionActor actor, Instant decidedAt, Basis basis, Expectation expectation) {

    /** Confirmado ou rejeitado. */
    public enum Kind {
        CONFIRMED("Confirmado"), REJECTED("Rejeitado");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,59}");
    private static final Pattern TARGET = Pattern.compile("[A-Za-z0-9._:-]{1,80}");

    /**
     * Resumo da verificação técnica que embasou a decisão (códigos do contrato {@code guided-debug/1}).
     *
     * @param identitySource {@code TARGET_ID} ou {@code DESCRIPTOR} (ou {@code NONE} sem verificação — ex.: escolha manual)
     * @param status         status da inspeção ({@code RESOLVED}...) ou {@code NOT_INSPECTED}
     * @param reasons        códigos de motivo/limitação
     * @param observedAt     instante da observação (pode ser nulo sem verificação)
     * @param location       modelo de URL sanitizado da página observada (pode ser vazio)
     */
    public record Basis(String identitySource, String status, List<String> reasons, Instant observedAt, String location) {
        public Basis {
            identitySource = code(identitySource);
            status = code(status);
            reasons = List.copyOf(reasons == null ? List.of() : reasons);
            for (String r : reasons) code(r);
            if (reasons.size() > 16) throw new IllegalArgumentException("motivos demais");
            location = location == null ? "" : location;
            if (location.length() > 2048 || location.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("local inválido");
        }

        /** Decisão sem verificação técnica (ex.: referência escolhida na interface sem inspeção). */
        public static Basis notInspected() {
            return new Basis("NONE", "NOT_INSPECTED", List.of(), null, "");
        }

        public boolean resolved() {
            return "RESOLVED".equals(status);
        }
    }

    /**
     * O que a etapa esperava quando a decisão foi tomada. Mudou ação, origem do valor, a identidade técnica ou o tipo do
     * alvo ⇒ a decisão não pode ser reutilizada sem revisão.
     *
     * @param action              tipo de ação da etapa
     * @param valueKind           origem do valor ({@code NONE}, {@code LITERAL}, {@code VARIABLE}, {@code CREDENTIAL})
     * @param variableRef         nome da variável referenciada (ou vazio)
     * @param targetId            identidade lógica do alvo efetivo (ou vazio, só descritor)
     * @param targetKind          tipo semântico do alvo efetivo
     * @param targetTag           tag do alvo efetivo
     * @param preferredExpression expressão do localizador preferencial do alvo efetivo (ou vazio)
     * @param path                tipos da trajetória de acesso
     */
    public record Expectation(ActionType action, String valueKind, String variableRef, String targetId, String targetKind, String targetTag,
                              String preferredExpression, List<AccessStep.Type> path) {
        public Expectation {
            Objects.requireNonNull(action, "action");
            valueKind = code(valueKind);
            variableRef = variableRef == null ? "" : variableRef;
            targetId = targetId == null ? "" : targetId;
            if (!targetId.isEmpty() && !TARGET.matcher(targetId).matches()) throw new IllegalArgumentException("targetId inválido");
            targetKind = targetKind == null ? "UNKNOWN" : targetKind;
            targetTag = targetTag == null ? "" : targetTag;
            preferredExpression = preferredExpression == null ? "" : preferredExpression;
            path = List.copyOf(path == null ? List.of() : path);
        }
    }

    public TargetDecision {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(decidedAt, "decidedAt");
        Objects.requireNonNull(basis, "basis");
        Objects.requireNonNull(expectation, "expectation");
    }

    public boolean confirmed() {
        return kind == Kind.CONFIRMED;
    }

    private static String code(String c) {
        if (c == null || !CODE.matcher(c).matches()) throw new IllegalArgumentException("código inválido: " + c);
        return c;
    }

    @Override
    public String toString() {
        return "TargetDecision[" + kind + " por " + actor + "]";
    }
}
