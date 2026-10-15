package com.nekkora.studio.recorder;

/**
 * Estado da entrega das etapas gravadas (STUDIO-003), para a interface. Só conta como registrada a etapa ACEITA pelo
 * domínio do Studio.
 *
 * @param enabled   a gravação produz etapas (capacidade steps/1 negociada)
 * @param accepted  etapas aceitas pelo domínio
 * @param pending   recebidas e ainda não entregues ao domínio (inclui as retidas por estarem fora de ordem)
 * @param rejected  recusadas pelo domínio (nunca contadas como registradas)
 * @param lost      interações declaradas perdidas pela extensão (documento substituído antes da consolidação)
 * @param waiting   a extensão já emitiu etapas que ainda não chegaram (retransmissão pedida)
 */
public record StepProgress(boolean enabled, int accepted, int pending, int rejected, int lost, boolean waiting) {

    public static final StepProgress NONE = new StepProgress(false, 0, 0, 0, 0, false);

    /** Há algo a explicar ao usuário sobre a entrega. */
    public boolean hasDeliveryProblem() {
        return rejected > 0 || lost > 0 || waiting;
    }
}
