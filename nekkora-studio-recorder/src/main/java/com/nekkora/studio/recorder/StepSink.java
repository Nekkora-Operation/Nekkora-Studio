package com.nekkora.studio.recorder;

import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepDraft;
import java.util.concurrent.CompletableFuture;

/**
 * Destino das etapas gravadas no domínio do Studio (STUDIO-003). A aplicação implementa sobre a
 * {@link com.nekkora.studio.domain.port.StepIntake} — na EDT, sem simular cliques e sem tocar componentes Swing. O
 * Recorder só confirma a etapa à extensão DEPOIS que o futuro completa.
 */
public interface StepSink {

    /** Entrega a etapa; completa com {@code true} se o domínio a aceitou, {@code false} se recusou (validação). */
    CompletableFuture<Boolean> deliver(AutomationId automation, SessionId session, StepDraft draft);

    /** Cancelamento confirmado: descarta, numa única operação, todas as etapas desta gravação. */
    CompletableFuture<Integer> discardRecording(AutomationId automation, String recordingId);

    /** Destino nulo (Studio sem gravação de etapas): recusa tudo, sem efeito. */
    StepSink NONE = new StepSink() {
        @Override
        public CompletableFuture<Boolean> deliver(AutomationId automation, SessionId session, StepDraft draft) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<Integer> discardRecording(AutomationId automation, String recordingId) {
            return CompletableFuture.completedFuture(0);
        }
    };
}
