package com.nekkora.studio.domain.port;

import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.evidence.ObservedEvidence;

/**
 * Entrada de EVIDÊNCIAS observadas no Studio (STUDIO-005), separada da {@link StepIntake}: a evidência enriquece uma
 * etapa já existente (ou fica como não atribuída) sem recriá-la nem alterar identidade, ordem, original, tipo ou
 * parâmetros. Não é edição: não entra no histórico de desfazer/refazer e é aceita durante a gravação.
 *
 * <p>Implementações devem ser chamadas na thread de interface (EDT) ou fazer a transferência para ela.
 */
public interface EvidenceIntake {

    /**
     * Registra a evidência na automação.
     *
     * @return {@code false} se já estava registrada (idempotente)
     * @throws com.nekkora.studio.domain.DomainValidationException se a etapa não existe, não foi executada ou o limite foi
     *                                                             atingido
     */
    boolean attachEvidence(AutomationId automation, ObservedEvidence evidence);
}
