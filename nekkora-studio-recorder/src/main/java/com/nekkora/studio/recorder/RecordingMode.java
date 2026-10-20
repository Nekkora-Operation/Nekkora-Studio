package com.nekkora.studio.recorder;

/**
 * Modos de captura (D-STUDIO-002-11). Nesta versão o modo é apenas uma CONFIGURAÇÃO confirmada pela extensão: os
 * sensores que diferenciam os dois comportamentos chegam na STUDIO-003.
 */
public enum RecordingMode {
    /** Selecionar e registrar o elemento sem executar sua ação (STUDIO-003). */
    CAPTURE_ONLY("Capturar", "Selecionar e registrar o elemento sem executar a ação."),
    /** Observar a interação sem bloquear sua execução normal (STUDIO-003). */
    CAPTURE_AND_INTERACT("Capturar e interagir", "Observar a interação sem bloquear a execução normal da página."),
    /**
     * NEKKORA-E2E-002: apontar um TEXTO da página (célula, título, parágrafo, item) para o robô LER na execução. Nada é
     * clicado e nenhum texto da página é guardado: a etapa vira Observar com o alvo apontado.
     */
    SELECT_DATA("Selecionar dado", "Apontar um texto da página (célula, título, parágrafo) para o robô ler; nada é clicado.");

    private final String label;
    private final String description;

    RecordingMode(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** Nome no contrato nekkora-bridge/1. */
    public String wire() {
        return name();
    }

    static RecordingMode ofWire(String s) {
        for (RecordingMode m : values()) {
            if (m.name().equals(s)) {
                return m;
            }
        }
        return null;
    }
}
