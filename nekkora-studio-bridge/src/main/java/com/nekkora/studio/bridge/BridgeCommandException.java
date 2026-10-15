package com.nekkora.studio.bridge;

/**
 * Comando recusado pelo navegador (ou não confirmado): código estável, mensagem para o usuário e motivo detalhado.
 * Completa excepcionalmente os futuros de {@link BrowserLink#send}.
 */
public final class BridgeCommandException extends RuntimeException {
    private final BridgeError code;
    private final String reason;
    private final boolean retryable;

    public BridgeCommandException(BridgeError code, String message, String reason, boolean retryable) {
        super(message);
        this.code = code;
        this.reason = reason;
        this.retryable = retryable;
    }

    public BridgeError code() {
        return code;
    }

    /** Motivo detalhado (ex.: PERMISSION_REQUIRED), ou null. */
    public String reason() {
        return reason;
    }

    public boolean retryable() {
        return retryable;
    }
}
