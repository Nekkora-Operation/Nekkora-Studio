package com.nekkora.studio.recorder;

import com.nekkora.studio.domain.evidence.ObservedEvidence;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Entrada ordenada das evidências de UMA gravação (STUDIO-005): ordem pela sequência explícita da extensão
 * ({@code evidenceSequence}); repetidas (mesma sequência ou mesmo evidenceId) são descartadas; fora de ordem ficam
 * retidas, até um limite, enquanto a lacuna é pedida de novo ({@code studio.recording.evidence.sync}).
 *
 * <p>Também controla a CONFIRMAÇÃO: uma evidência pode esperar a etapa dela (que chega por outro canal), então as
 * conclusões não são em ordem — a confirmação cumulativa avança só até a maior sequência contígua concluída.
 *
 * <p>Não é thread-safe: usada só pela thread do Recorder.
 */
final class EvidenceInbox {

    static final int MAX_BUFFERED = 500;

    private long released; // última sequência liberada em ordem
    private long highestSeen;
    private final TreeMap<Long, ObservedEvidence> buffered = new TreeMap<>();
    private final Set<String> seen = new HashSet<>();
    private final TreeSet<Long> concluded = new TreeSet<>();
    private long acked; // maior sequência contígua concluída (aceita ou recusada pelo domínio)
    private int duplicates;

    record Accepted(List<ObservedEvidence> ready, boolean gap, boolean duplicate) {
    }

    Accepted accept(ObservedEvidence e) {
        if (e.sequence() <= released || buffered.containsKey(e.sequence()) || seen.contains(e.evidenceId())) {
            duplicates++;
            return new Accepted(List.of(), false, true);
        }
        highestSeen = Math.max(highestSeen, e.sequence());
        if (buffered.size() >= MAX_BUFFERED && e.sequence() != released + 1) {
            return new Accepted(List.of(), true, false); // não retém sem limite; a reemissão trará de novo
        }
        buffered.put(e.sequence(), e);
        List<ObservedEvidence> ready = new ArrayList<>();
        while (!buffered.isEmpty() && buffered.firstKey() == released + 1) {
            ObservedEvidence next = buffered.pollFirstEntry().getValue();
            ready.add(next);
            seen.add(next.evidenceId());
            released++;
        }
        return new Accepted(ready, !buffered.isEmpty(), false);
    }

    /** A evidência foi concluída no domínio (aceita ou recusada): avança a confirmação contígua. */
    long concluded(long sequence) {
        if (sequence > acked) {
            concluded.add(sequence);
        }
        while (concluded.contains(acked + 1)) {
            concluded.remove(acked + 1);
            acked++;
        }
        return acked;
    }

    long acked() {
        return acked;
    }

    long released() {
        return released;
    }

    long highestSeen() {
        return highestSeen;
    }

    /** A extensão informou que já emitiu até {@code lastSequence}: falta alguma aqui? */
    boolean missing(long lastSequence) {
        return lastSequence > released;
    }

    int buffered() {
        return buffered.size();
    }

    int duplicates() {
        return duplicates;
    }
}
