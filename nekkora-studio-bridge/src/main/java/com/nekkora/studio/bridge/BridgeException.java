package com.nekkora.studio.bridge;

import java.io.IOException;

/** Falha de transporte/handshake do Bridge com código estável. */
public final class BridgeException extends IOException {
    private final BridgeError code;

    public BridgeException(BridgeError code, String message) {
        super(message);
        this.code = code;
    }

    public BridgeError code() {
        return code;
    }
}
