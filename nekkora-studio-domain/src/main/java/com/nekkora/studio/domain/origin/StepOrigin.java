package com.nekkora.studio.domain.origin;

/**
 * Como uma etapa chegou ao modelo. Fica associada à etapa para rastreabilidade.
 *
 * <p>Na STUDIO-001 apenas {@link #MANUAL} é produzida. As demais são <b>previsões contratuais</b>:
 * o modelo as aceita para que Recorder (STUDIO-002/003), importação e geração futuras usem a mesma
 * operação de domínio — nenhuma delas está implementada nem é oferecida na interface.
 */
public enum StepOrigin {
    MANUAL("Manual"),
    RECORDED("Gravada"),
    IMPORTED("Importada"),
    GENERATED("Gerada");

    private final String label;

    StepOrigin(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
