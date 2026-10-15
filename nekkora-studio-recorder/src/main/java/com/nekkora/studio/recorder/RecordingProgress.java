package com.nekkora.studio.recorder;

/**
 * Estado técnico informado pela extensão.
 *
 * @param recorderAttached  o consumidor do Recorder está inscrito na captura da aba
 * @param otherConsumers    outros consumidores (inspeção manual etc.): se maior que 0 a página continua observada mesmo pausado
 * @param captureStatus     estado técnico da CaptureSession (ou null)
 * @param coverage          COMPLETE | PARTIAL | UNKNOWN
 * @param framesUnavailable documentos (iframes) que a captura não acompanha
 * @param technicalCaptures capturas-base durante a participação do Recorder
 * @param synchronizations  deltas aplicados durante a participação
 * @param recoveries        ressincronizações
 * @param segments          trechos de participação (cada retomada abre um)
 */
public record RecordingProgress(boolean recorderAttached, int otherConsumers, String captureStatus, String coverage, int framesUnavailable,
        int technicalCaptures, int synchronizations, int recoveries, int segments) {

    public static final RecordingProgress NONE = new RecordingProgress(false, 0, null, "UNKNOWN", 0, 0, 0, 0, 0);

    public boolean partialCoverage() {
        return "PARTIAL".equals(coverage);
    }
}
