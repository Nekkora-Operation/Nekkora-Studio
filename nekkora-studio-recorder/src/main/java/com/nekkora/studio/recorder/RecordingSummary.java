package com.nekkora.studio.recorder;

import java.time.Duration;
import java.util.List;

/**
 * Resumo técnico do desfecho. Não diz que etapas foram gravadas (nenhuma é: captura de ações na STUDIO-003) e
 * registra que o PageMap NÃO foi exportado (finalizar não é exportar).
 */
public record RecordingSummary(RecorderState outcome, Duration duration, RecordingProgress progress, List<String> limitations,
        boolean pageMapExported) {
    public RecordingSummary {
        limitations = List.copyOf(limitations);
    }
}
