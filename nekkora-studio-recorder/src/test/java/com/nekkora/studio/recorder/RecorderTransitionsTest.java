package com.nekkora.studio.recorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Máquina de estados explícita: alcançabilidade, terminais, confirmações e recusas determinísticas. */
class RecorderTransitionsTest {

    @Test
    void todoEstadoEhAlcancavelAPartirDeIdleEVoltaAIdle() {
        Set<RecorderState> seen = EnumSet.noneOf(RecorderState.class);
        Deque<RecorderState> queue = new ArrayDeque<>();
        queue.add(RecorderState.IDLE);
        while (!queue.isEmpty()) {
            RecorderState s = queue.poll();
            if (seen.add(s)) {
                queue.addAll(RecorderTransitions.from(s));
            }
        }
        assertEquals(EnumSet.allOf(RecorderState.class), seen);
        for (RecorderState t : EnumSet.of(RecorderState.COMPLETED, RecorderState.CANCELLED, RecorderState.ERROR)) {
            assertEquals(Set.of(RecorderState.IDLE), RecorderTransitions.from(t), t + " só volta ao Studio");
        }
    }

    @Test
    void estadosConfirmadosSoViaEstadoPendente() {
        assertFalse(RecorderTransitions.allowed(RecorderState.IDLE, RecorderState.RECORDING), "Gravar → Gravando só após STARTING");
        assertFalse(RecorderTransitions.allowed(RecorderState.RECORDING, RecorderState.PAUSED), "pausa só após PAUSING");
        assertFalse(RecorderTransitions.allowed(RecorderState.PAUSED, RecorderState.RECORDING), "retomada só após RESUMING");
        assertFalse(RecorderTransitions.allowed(RecorderState.RECORDING, RecorderState.COMPLETED), "finalização só após FINALIZING");
        assertFalse(RecorderTransitions.allowed(RecorderState.RECORDING, RecorderState.CANCELLED), "cancelamento só após CANCELLING");
        assertTrue(RecorderTransitions.allowed(RecorderState.PAUSED, RecorderState.FINALIZING), "finalizar durante pausa");
        assertTrue(RecorderTransitions.allowed(RecorderState.DEGRADED, RecorderState.RECOVERING));
        assertTrue(RecorderTransitions.allowed(RecorderState.DEGRADED, RecorderState.ERROR));
        assertFalse(RecorderTransitions.allowed(RecorderState.DEGRADED, RecorderState.PAUSING), "sem pausa em captura interrompida");
        for (RecorderState s : RecorderState.values()) {
            assertEquals(s.pending(), EnumSet.of(RecorderState.STARTING, RecorderState.PAUSING, RecorderState.RESUMING, RecorderState.FINALIZING,
                    RecorderState.CANCELLING, RecorderState.RECOVERING).contains(s));
        }
    }

    @Test
    void transicaoInvalidaEhRecusada() {
        try {
            RecorderTransitions.check(RecorderState.COMPLETED, RecorderState.RECORDING);
            throw new AssertionError("deveria recusar");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("COMPLETED"));
        }
    }
}
