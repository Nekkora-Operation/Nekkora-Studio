package com.nekkora.studio.recorder;

import com.nekkora.studio.bridge.BridgeCommandException;
import com.nekkora.studio.bridge.BridgeError;
import com.nekkora.studio.bridge.BridgeEvent;
import com.nekkora.studio.bridge.BrowserLink;
import com.nekkora.studio.bridge.LinkStatus;
import com.nekkora.studio.bridge.json.Json;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Navegador falso para os testes do Recorder: registra os comandos e responde como a extensão (fotografias no
 * formato do contrato), ou deixa a resposta pendente para o teste decidir (atraso, recusa, perda).
 */
final class FakeBrowser implements BrowserLink {

    /** Comando recebido. */
    record Call(String type, Map<String, Object> payload, CompletableFuture<Map<String, Object>> future) {
        String op() {
            return Json.string(payload, "operationId");
        }
    }

    final List<Call> calls = new CopyOnWriteArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile LinkStatus status = new LinkStatus(LinkStatus.State.CONNECTED, null, new LinkStatus.ExtensionInfo("0.4.0", "DEV"));
    /** Responde automaticamente (null = deixa pendente). */
    volatile Function<Call, Map<String, Object>> auto = this::extension;
    String state = "RECORDING";
    String mode = "CAPTURE_ONLY";
    String recordingId;
    long clock = 0;
    /** A extensão anuncia a entrega de etapas quando o início pediu steps/1 (STUDIO-003). */
    boolean stepsEnabled;
    /** Simula a extensão da STUDIO-002: recusa o início com a capacidade steps/1 (campo fora do contrato). */
    boolean legacy;
    int otherConsumers = 0;

    @Override
    public LinkStatus status() {
        return status;
    }

    @Override
    public void addListener(Listener l) {
        listeners.add(l);
    }

    @Override
    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    @Override
    public CompletableFuture<Map<String, Object>> send(String type, Map<String, Object> payload, Duration timeout) {
        CompletableFuture<Map<String, Object>> f = new CompletableFuture<>();
        Call c = new Call(type, payload, f);
        calls.add(c);
        if (!status.connected()) {
            f.completeExceptionally(new BridgeCommandException(BridgeError.EXTENSION_UNAVAILABLE, "sem navegador", null, true));
            return f;
        }
        Function<Call, Map<String, Object>> a = auto;
        if (a != null) {
            Map<String, Object> r = a.apply(c);
            if (r != null) {
                f.complete(r);
            }
        }
        return f;
    }

    /** Comportamento mínimo da extensão (estado autoritativo). */
    Map<String, Object> extension(Call c) {
        String rid = Json.string(c.payload(), "recordingId");
        switch (c.type()) {
            case "studio.recording.start" -> {
                if (legacy && c.payload().containsKey("capabilities")) {
                    c.future().completeExceptionally(new BridgeCommandException(BridgeError.BAD_REQUEST, "Comando fora do contrato nekkora-bridge/1.", null, false));
                    return null;
                }
                stepsEnabled = c.payload().containsKey("capabilities");
                recordingId = rid;
                state = "RECORDING";
                mode = Json.string(c.payload(), "mode");
                return result();
            }
            case "studio.recording.pause" -> state = "PAUSED";
            case "studio.recording.resume" -> state = "RECORDING";
            case "studio.recording.mode" -> mode = Json.string(c.payload(), "mode");
            case "studio.recording.finish" -> {
                state = "COMPLETED";
                return Json.obj().put("recording", recording()).put("summary", summary("COMPLETED")).put("duplicate", false).build();
            }
            case "studio.recording.cancel" -> {
                state = "CANCELLED";
                return Json.obj().put("recording", recording()).put("summary", summary("CANCELLED")).put("duplicate", false).build();
            }
            case "studio.recording.status" -> {
                if (recordingId == null) {
                    return null;
                }
                return Json.obj().put("recordings", List.of(recording())).build();
            }
            case "studio.recording.renew" -> {
                return Json.obj().put("recordingId", rid).put("state", state).put("expiresInMs", 30000).build();
            }
            case "browser.tabs.list" -> {
                return Json.obj().put("tabs", List.of(
                        Json.obj().put("tabRef", "t-AAAAAAAAAA").put("title", "Sistema de testes").put("host", "127.0.0.1").put("access", "LAB")
                                .put("active", true).put("recording", false).build(),
                        Json.obj().put("tabRef", "t-BBBBBBBBBB").put("title", null).put("host", "portal.exemplo.com").put("access", "PERMISSION_REQUIRED")
                                .put("active", false).put("recording", false).build())).put("truncated", false).build();
            }
            default -> {
                return Map.of();
            }
        }
        return result();
    }

    Map<String, Object> result() {
        return Json.obj().put("recording", recording()).put("duplicate", false).build();
    }

    Map<String, Object> recording() {
        clock += 1000;
        boolean attached = "RECORDING".equals(state);
        return withSteps(Json.obj().put("recordingId", recordingId).put("state", state).put("mode", mode)
                .put("tab", Json.obj().put("tabRef", "t-AAAAAAAAAA").put("title", "Sistema de testes").put("host", "127.0.0.1").build())
                .put("capture", Json.obj().put("recorderAttached", attached).put("otherConsumers", otherConsumers)
                        .put("captureStatus", attached || otherConsumers > 0 ? "ACTIVE" : null).build())
                .put("coverage", Json.obj().put("state", attached ? "COMPLETE" : "UNKNOWN").put("framesAttached", 0).put("framesUnavailable", 0).build())
                .put("counters", Json.obj().put("segments", 1).put("technicalCaptures", 1).put("synchronizations", 3).put("recoveries", 0).build())
                .put("lease", Json.obj().put("leaseMs", 30000).put("expiresInMs", 30000).build())
                .put("startedAt", Instant.ofEpochMilli(0).toString()).put("updatedAt", Instant.ofEpochMilli(clock).toString())
                .put("reason", null).build());
    }

    /** Fotografia da gravação; com steps/1 negociado, inclui o estado da entrega (como a extensão real). */
    private Map<String, Object> withSteps(Map<String, Object> rec) {
        if (stepsEnabled) {
            rec.put("steps", Json.obj().put("enabled", true).put("lastSequence", 0).put("ackedSequence", 0).put("pending", 0).put("lost", 0).build());
        }
        return rec;
    }

    Map<String, Object> summary(String outcome) {
        return Json.obj().put("state", outcome).put("durationMs", 4200)
                .put("counters", Json.obj().put("segments", 2).put("technicalCaptures", 2).put("synchronizations", 9).put("recoveries", 0).build())
                .put("coverage", Json.obj().put("state", "COMPLETE").put("framesAttached", 0).put("framesUnavailable", 0).build())
                .put("pageMapExported", false).put("actionCapture", "NOT_AVAILABLE").put("limitations", List.of("ACTION_CAPTURE_NOT_AVAILABLE")).build();
    }

    void setStatus(LinkStatus s) {
        status = s;
        if (!s.connected()) {
            for (Call c : calls) {
                c.future().completeExceptionally(new BridgeCommandException(BridgeError.LINK_LOST, "perdida", null, true));
            }
        }
        listeners.forEach(l -> l.statusChanged(s));
    }

    void emit(String type, Map<String, Object> payload) {
        listeners.forEach(l -> l.event(new BridgeEvent(type, payload)));
    }

    Call last(String type) {
        for (int i = calls.size() - 1; i >= 0; i--) {
            if (calls.get(i).type().equals(type)) {
                return calls.get(i);
            }
        }
        return null;
    }

    long count(String type) {
        return calls.stream().filter(c -> c.type().equals(type)).count();
    }
}
