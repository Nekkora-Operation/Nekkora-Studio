package com.nekkora.studio.recorder;

import java.time.Duration;
import java.util.List;

/**
 * Resumo do desfecho. Registra que o PageMap NÃO foi exportado (finalizar não é exportar) e, na STUDIO-003, a captura
 * de ações informada pela extensão ({@code NOT_AVAILABLE}, {@code AVAILABLE} ou {@code PARTIAL}) e as etapas
 * produzidas e confirmadas.
 *
 * @param actionCapture NOT_AVAILABLE (gravação só técnica), AVAILABLE ou PARTIAL
 * @param stepsProduced etapas que a extensão produziu
 * @param stepsAccepted etapas aceitas pelo domínio do Studio
 * @param stepsRejected etapas recusadas pelo domínio
 * @param stepsLost     interações declaradas perdidas
 * @param evidence      evidências observadas (STUDIO-005; {@link EvidenceProgress#NONE} sem a capacidade)
 */
public record RecordingSummary(RecorderState outcome, Duration duration, RecordingProgress progress, List<String> limitations,
        boolean pageMapExported, String actionCapture, int stepsProduced, int stepsAccepted, int stepsRejected, int stepsLost, EvidenceProgress evidence) {
    public RecordingSummary {
        limitations = List.copyOf(limitations);
        actionCapture = actionCapture == null ? "NOT_AVAILABLE" : actionCapture;
        evidence = evidence == null ? EvidenceProgress.NONE : evidence;
    }

    /** Resumo da STUDIO-003 (sem evidências). */
    public RecordingSummary(RecorderState outcome, Duration duration, RecordingProgress progress, List<String> limitations, boolean pageMapExported,
            String actionCapture, int stepsProduced, int stepsAccepted, int stepsRejected, int stepsLost) {
        this(outcome, duration, progress, limitations, pageMapExported, actionCapture, stepsProduced, stepsAccepted, stepsRejected, stepsLost,
                EvidenceProgress.NONE);
    }

    /** Resumo da STUDIO-002 (gravação só técnica). */
    public RecordingSummary(RecorderState outcome, Duration duration, RecordingProgress progress, List<String> limitations, boolean pageMapExported) {
        this(outcome, duration, progress, limitations, pageMapExported, "NOT_AVAILABLE", 0, 0, 0, 0);
    }

    /** A gravação produziu etapas (sensores de interação ativos). */
    public boolean actionsCaptured() {
        return !"NOT_AVAILABLE".equals(actionCapture);
    }
}
