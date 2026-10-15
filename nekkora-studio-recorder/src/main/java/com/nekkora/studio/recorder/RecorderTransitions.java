package com.nekkora.studio.recorder;

import static com.nekkora.studio.recorder.RecorderState.CANCELLED;
import static com.nekkora.studio.recorder.RecorderState.CANCELLING;
import static com.nekkora.studio.recorder.RecorderState.COMPLETED;
import static com.nekkora.studio.recorder.RecorderState.DEGRADED;
import static com.nekkora.studio.recorder.RecorderState.ERROR;
import static com.nekkora.studio.recorder.RecorderState.FINALIZING;
import static com.nekkora.studio.recorder.RecorderState.IDLE;
import static com.nekkora.studio.recorder.RecorderState.PAUSED;
import static com.nekkora.studio.recorder.RecorderState.PAUSING;
import static com.nekkora.studio.recorder.RecorderState.RECORDING;
import static com.nekkora.studio.recorder.RecorderState.RECOVERING;
import static com.nekkora.studio.recorder.RecorderState.RESUMING;
import static com.nekkora.studio.recorder.RecorderState.STARTING;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Transições PERMITIDAS da máquina de estados do Recorder (fonte única; documentada em docs/studio-002). Qualquer
 * outra transição é um erro de programação e é recusada.
 *
 * <pre>
 * IDLE ─► STARTING ─► RECORDING ─► PAUSING ─► PAUSED ─► RESUMING ─► RECORDING
 *                         │                      │
 *                         ├─► FINALIZING ─► COMPLETED   (também a partir de PAUSED e DEGRADED)
 *                         ├─► CANCELLING ─► CANCELLED   (também a partir de PAUSED e DEGRADED)
 *                         └─► DEGRADED ─► RECOVERING ─► RECORDING | PAUSED | DEGRADED | ERROR
 * COMPLETED | CANCELLED | ERROR ─► IDLE (o usuário volta ao Studio)
 * </pre>
 */
public final class RecorderTransitions {

    private static final Map<RecorderState, Set<RecorderState>> ALLOWED = new EnumMap<>(RecorderState.class);

    static {
        allow(IDLE, STARTING);
        allow(STARTING, RECORDING, IDLE, DEGRADED, RECOVERING, ERROR);
        allow(RECORDING, PAUSING, FINALIZING, CANCELLING, DEGRADED, RECOVERING, ERROR);
        allow(PAUSING, PAUSED, RECORDING, DEGRADED, RECOVERING, ERROR);
        allow(PAUSED, RESUMING, FINALIZING, CANCELLING, DEGRADED, RECOVERING, ERROR);
        allow(RESUMING, RECORDING, PAUSED, DEGRADED, RECOVERING, ERROR);
        allow(FINALIZING, COMPLETED, RECORDING, PAUSED, DEGRADED, RECOVERING, CANCELLED, ERROR);
        allow(CANCELLING, CANCELLED, RECORDING, PAUSED, DEGRADED, RECOVERING, COMPLETED, ERROR);
        allow(DEGRADED, RECOVERING, FINALIZING, CANCELLING, ERROR);
        allow(RECOVERING, RECORDING, PAUSED, DEGRADED, COMPLETED, CANCELLED, ERROR, IDLE);
        allow(COMPLETED, IDLE);
        allow(CANCELLED, IDLE);
        allow(ERROR, IDLE);
    }

    private RecorderTransitions() {
    }

    private static void allow(RecorderState from, RecorderState... to) {
        ALLOWED.put(from, Collections.unmodifiableSet(EnumSet.of(to[0], to)));
    }

    public static boolean allowed(RecorderState from, RecorderState to) {
        return from == to || ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /** Destinos permitidos a partir de um estado. */
    public static Set<RecorderState> from(RecorderState state) {
        return ALLOWED.getOrDefault(state, Set.of());
    }

    static void check(RecorderState from, RecorderState to) {
        if (!allowed(from, to)) {
            throw new IllegalStateException("Transição não permitida no Recorder: " + from + " → " + to);
        }
    }
}
