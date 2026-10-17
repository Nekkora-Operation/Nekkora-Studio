package com.nekkora.studio.domain.target;

/**
 * Quem tomou uma decisão sobre o alvo de uma etapa (STUDIO-009, D-S009-06). NÃO existe "confirmação humana" nem
 * "verificado por IA": o Studio registra QUEM operava a interface, não prova que era uma pessoa.
 */
public enum DecisionActor {
    /** Decisão tomada na interface do Studio por quem a operava (uso normal do produto). */
    DEVELOPER("Desenvolvedor"),
    /**
     * Decisão tomada por um agente de teste em modo de laboratório explícito (ex.: Claude operando a homologação). Nunca
     * vale como validação humana, nunca sobrescreve uma decisão {@link #DEVELOPER} e não é transferível para outros
     * projetos ou ambientes sem revisão.
     */
    TEST_AGENT("Agente de teste (laboratório)");

    private final String label;

    DecisionActor(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Pode substituir uma decisão anterior deste ator? (agente de teste nunca passa por cima do desenvolvedor) */
    public boolean mayOverride(DecisionActor previous) {
        return previous == null || this == DEVELOPER || previous == TEST_AGENT;
    }
}
