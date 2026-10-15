package com.nekkora.studio.recorder;

/**
 * Estados do Recorder no Studio. Estados "-ING" aguardam a confirmação da extensão: o Studio NUNCA apresenta uma
 * operação como concluída antes dela (D-STUDIO-002-09).
 *
 * <p>{@link #PAUSED} é a pausa pedida pelo usuário e confirmada pela extensão — não confundir com o PAUSED técnico da
 * CaptureSession (motor Java indisponível), que aparece apenas como detalhe da captura.
 */
public enum RecorderState {
    IDLE("Pronto para gravar"),
    STARTING("Iniciando…"),
    RECORDING("Captura técnica ativa"),
    PAUSING("Pausando…"),
    PAUSED("Pausada"),
    RESUMING("Retomando…"),
    FINALIZING("Finalizando…"),
    COMPLETED("Concluída"),
    CANCELLING("Cancelando…"),
    CANCELLED("Cancelada"),
    DEGRADED("Captura interrompida"),
    RECOVERING("Recuperando…"),
    ERROR("Erro");

    private final String label;

    RecorderState(String label) {
        this.label = label;
    }

    /** Rótulo para o usuário. */
    public String label() {
        return label;
    }

    /** Aguardando confirmação do navegador. */
    public boolean pending() {
        return this == STARTING || this == PAUSING || this == RESUMING || this == FINALIZING || this == CANCELLING || this == RECOVERING;
    }

    /** Desfecho: a gravação técnica terminou (ou foi perdida). */
    public boolean terminal() {
        return this == COMPLETED || this == CANCELLED || this == ERROR;
    }

    /** Existe uma gravação em andamento (não ociosa e não terminada). */
    public boolean active() {
        return this != IDLE && !terminal();
    }
}
