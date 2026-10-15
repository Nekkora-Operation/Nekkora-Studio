package com.nekkora.studio.recorder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Entrada ordenada das etapas de UMA gravação: a ordem vem da sequência explícita da extensão, nunca da ordem de
 * chegada nem de timestamps. Repetidas (mesma sequência ou mesmo eventId) são descartadas; fora de ordem ficam
 * retidas até a lacuna ser preenchida (retransmissão por {@code studio.recording.steps.sync}).
 *
 * <p>Não é thread-safe: usada só pela thread do Recorder.
 */
final class StepInbox {

    static final int MAX_BUFFERED = 200;

    private long processed; // última sequência entregue ao domínio (ou recusada por ele)
    private long highestSeen;
    private final TreeMap<Long, RecordedStep> buffered = new TreeMap<>();
    private final Set<String> seenEvents = new HashSet<>();
    private int duplicates;

    /** Resultado de uma chegada: etapas prontas, em ordem, e se há lacuna a pedir de novo. */
    record Accepted(List<RecordedStep> ready, boolean gap, boolean duplicate, boolean overflow) {
    }

    Accepted accept(RecordedStep s) {
        if (s.sequence() <= processed || buffered.containsKey(s.sequence()) || seenEvents.contains(s.eventId())) {
            duplicates++;
            return new Accepted(List.of(), false, true, false);
        }
        highestSeen = Math.max(highestSeen, s.sequence());
        if (buffered.size() >= MAX_BUFFERED && s.sequence() != processed + 1) {
            return new Accepted(List.of(), true, false, true); // não retém sem limite; a reemissão trará de novo
        }
        buffered.put(s.sequence(), s);
        List<RecordedStep> ready = new ArrayList<>();
        while (!buffered.isEmpty() && buffered.firstKey() == processed + ready.size() + 1) {
            RecordedStep next = buffered.pollFirstEntry().getValue();
            ready.add(next);
            seenEvents.add(next.eventId());
        }
        processed += ready.size();
        return new Accepted(ready, !buffered.isEmpty(), false, false);
    }

    /** Última sequência entregue em ordem (base do pedido de reemissão e da confirmação). */
    long processed() {
        return processed;
    }

    long highestSeen() {
        return highestSeen;
    }

    /** A extensão informou que já emitiu até {@code lastSequence}: há etapas que não chegaram? */
    boolean missing(long lastSequence) {
        return lastSequence > processed;
    }

    int buffered() {
        return buffered.size();
    }

    int duplicates() {
        return duplicates;
    }
}
