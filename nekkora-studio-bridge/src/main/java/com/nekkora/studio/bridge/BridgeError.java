package com.nekkora.studio.bridge;

/**
 * Códigos estáveis do nekkora-bridge/1 ({@code contracts/studio-bridge.schema.json}, ErrorCode) e os do próprio Studio
 * (indisponibilidade local do Bridge, perda do canal).
 */
public enum BridgeError {
    BAD_REQUEST, UNKNOWN_COMMAND, COMMAND_NOT_ALLOWED, PROTOCOL_UNSUPPORTED, AUTH_FAILED, BRIDGE_BUSY, HANDSHAKE_TIMEOUT,
    FRAME_TOO_LARGE, EXTENSION_UNAVAILABLE, TIMEOUT, TAB_NOT_FOUND, ORIGIN_NOT_ALLOWED, RECORDING_NOT_FOUND,
    RECORDING_ALREADY_ACTIVE, RECORDING_ENDED, INVALID_STATE, NOT_CONTROLLER, INVALID_MODE, CAPTURE_FAILED,
    OPERATION_CONFLICT, INTERNAL_ERROR,
    // STUDIO-009 (guided-debug/1)
    DEBUG_NOT_FOUND, DEBUG_ENDED, CANDIDATE_EXPIRED, PROBE_NOT_ALLOWED,
    /** Studio: o canal caiu antes da resposta. */
    LINK_LOST,
    /** Studio: outra instância já publica o Bridge neste usuário. */
    OTHER_INSTANCE,
    /** Studio: não foi possível garantir permissões restritas ao usuário no diretório do Bridge. */
    INSECURE_DIRECTORY,
    /** Studio: falha de E/S ao publicar o Bridge. */
    UNAVAILABLE;

    /** Código recebido do host; desconhecidos viram {@link #INTERNAL_ERROR}. */
    public static BridgeError of(String code) {
        try {
            return code == null ? INTERNAL_ERROR : valueOf(code);
        } catch (IllegalArgumentException e) {
            return INTERNAL_ERROR;
        }
    }
}
