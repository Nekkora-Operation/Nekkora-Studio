package com.nekkora.studio.domain;

/**
 * Estado de uma automação. Na STUDIO-001 toda automação é um rascunho mantido em memória; estados
 * como "salvo", "validado" ou "publicado" chegam com a persistência (STUDIO-006) e a execução.
 */
public enum AutomationStatus {
    /** Rascunho editável, ainda não persistido. */
    DRAFT("Rascunho");

    private final String label;

    AutomationStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
