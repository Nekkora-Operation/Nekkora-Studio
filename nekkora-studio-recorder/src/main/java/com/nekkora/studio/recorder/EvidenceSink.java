package com.nekkora.studio.recorder;

import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.evidence.ObservedEvidence;
import java.util.concurrent.CompletableFuture;

/**
 * Destino das evidências observadas no domínio do Studio (STUDIO-005). A aplicação implementa sobre a
 * {@link com.nekkora.studio.domain.port.EvidenceIntake} — na EDT. O Recorder só confirma a evidência à extensão DEPOIS
 * que o futuro completa.
 */
public interface EvidenceSink {

    /** Entrega a evidência; {@code true} se o domínio a registrou (ou já a tinha), {@code false} se recusou. */
    CompletableFuture<Boolean> deliver(AutomationId automation, ObservedEvidence evidence);

    /** Destino nulo: o Studio não pede a capacidade evidence/1. */
    EvidenceSink NONE = (automation, evidence) -> CompletableFuture.completedFuture(false);
}
