package com.nekkora.studio.recorder;

import java.time.Instant;

/**
 * Estado das evidências observadas da gravação (STUDIO-005), para a interface (Floating Recorder) e o resumo. Só conta
 * como registrada a evidência ACEITA pelo domínio do Studio. Nenhum campo diz "sucesso" ou "falha": evidência não é
 * resultado de negócio.
 *
 * @param enabled        a gravação entrega evidências (capacidade evidence/1 negociada)
 * @param produced       evidências que a extensão informou ter produzido
 * @param accepted       aceitas pelo domínio (inclui as não atribuídas)
 * @param unattributed   aceitas sem etapa definida (ambíguas)
 * @param incomplete     observações interrompidas (OBSERVATION_INCOMPLETE aceitas)
 * @param pending        recebidas e ainda não registradas (fora de ordem ou esperando a etapa)
 * @param rejected       recusadas pelo domínio
 * @param lost           declaradas perdidas pela extensão (limite)
 * @param waiting        a extensão já emitiu evidências que não chegaram (retransmissão pedida)
 * @param lastObservedAt instante da última evidência aceita, ou null
 */
public record EvidenceProgress(boolean enabled, int produced, int accepted, int unattributed, int incomplete, int pending, int rejected, int lost,
                               boolean waiting, Instant lastObservedAt) {

    public static final EvidenceProgress NONE = new EvidenceProgress(false, 0, 0, 0, 0, 0, 0, 0, false, null);

    /** A cobertura da observação tem limitação a explicar. */
    public boolean partial() {
        return incomplete > 0 || lost > 0 || rejected > 0 || waiting;
    }
}
