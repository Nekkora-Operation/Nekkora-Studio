package com.nekkora.studio.bridge;

import java.util.List;

/**
 * Vocabulário do nekkora-bridge/1 usado pelo Studio (comandos que o Studio envia e eventos que aceita). Conferido contra
 * o contrato da extensão ({@code contracts/studio-bridge.schema.json}) pelos testes de contrato.
 */
public final class BridgeProtocol {

    public static final String VERSION = BridgeServer.PROTOCOL;

    public static final List<String> COMMANDS = List.of("bridge.status", "bridge.heartbeat", "browser.tabs.list",
            "studio.recording.start", "studio.recording.pause", "studio.recording.resume", "studio.recording.mode",
            "studio.recording.finish", "studio.recording.cancel", "studio.recording.status", "studio.recording.renew",
            // STUDIO-003: entrega confiável das etapas gravadas e rota de sessão lógica
            "studio.recording.steps.ack", "studio.recording.steps.sync", "studio.recording.route");

    public static final List<String> EVENTS = List.of("bridge.connected", "bridge.disconnected",
            "studio.recording.started", "studio.recording.paused", "studio.recording.resumed", "studio.recording.modeChanged",
            "studio.recording.finished", "studio.recording.cancelled", "studio.recording.degraded", "studio.recording.recovered",
            "studio.recording.failed", "studio.recording.expired",
            // STUDIO-003: etapa gravada
            "studio.recording.step");

    public static final List<String> RECORDING_STATES = List.of("RECORDING", "PAUSED", "DEGRADED", "COMPLETED", "CANCELLED", "EXPIRED", "FAILED");

    public static final List<String> MODES = List.of("CAPTURE_ONLY", "CAPTURE_AND_INTERACT");

    private BridgeProtocol() {
    }
}
