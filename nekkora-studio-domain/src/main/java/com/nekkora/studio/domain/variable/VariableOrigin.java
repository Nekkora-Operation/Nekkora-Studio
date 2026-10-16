package com.nekkora.studio.domain.variable;

import com.nekkora.studio.domain.StepId;
import java.util.Objects;
import java.util.Optional;

/**
 * De onde o valor de uma variável virá (STUDIO-008) — sempre uma DECLARAÇÃO, nunca uma prova:
 * <ul>
 *   <li>{@link Kind#INPUT}: entrada exigida numa execução futura; nenhum valor existe agora;</li>
 *   <li>{@link Kind#CONSTANT}: valor fixo NÃO sensível, gravado em claro no projeto (forma canônica do tipo);</li>
 *   <li>{@link Kind#STEP_OUTPUT}: produzido por uma etapa DECLARADA; sem contrato de extração, a produção não é
 *       comprovada;</li>
 *   <li>{@link Kind#UNBOUND}: sem origem — incompleta para quem a consome.</li>
 * </ul>
 *
 * @param kind     tipo de origem
 * @param constant forma canônica do valor (só {@link Kind#CONSTANT})
 * @param producer etapa produtora declarada (só {@link Kind#STEP_OUTPUT})
 */
public record VariableOrigin(Kind kind, String constant, StepId producer) {
    public enum Kind {
        INPUT("Entrada"), CONSTANT("Constante"), STEP_OUTPUT("Saída de etapa"), UNBOUND("Sem origem");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public VariableOrigin {
        Objects.requireNonNull(kind, "kind");
        if ((kind == Kind.CONSTANT) != (constant != null)) throw new IllegalArgumentException("constante exige o valor (e só ela)");
        if ((kind == Kind.STEP_OUTPUT) != (producer != null)) throw new IllegalArgumentException("saída de etapa exige a produtora (e só ela)");
    }

    public static VariableOrigin input() {
        return new VariableOrigin(Kind.INPUT, null, null);
    }

    public static VariableOrigin constant(String canonical) {
        return new VariableOrigin(Kind.CONSTANT, Objects.requireNonNull(canonical, "canonical"), null);
    }

    public static VariableOrigin stepOutput(StepId producer) {
        return new VariableOrigin(Kind.STEP_OUTPUT, null, Objects.requireNonNull(producer, "producer"));
    }

    public static VariableOrigin unbound() {
        return new VariableOrigin(Kind.UNBOUND, null, null);
    }

    public Optional<String> constantValue() {
        return Optional.ofNullable(constant);
    }

    public Optional<StepId> producerStep() {
        return Optional.ofNullable(producer);
    }

    @Override
    public String toString() {
        return "VariableOrigin[" + kind + "]"; // nunca o valor
    }
}
