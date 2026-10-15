package com.nekkora.studio.app.workspace;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Histórico de edição em memória, por automação (STUDIO-004, D-S004-09). Cada entrada é uma operação de edição
 * confirmada pelo domínio, guardada como duas fotografias do agregado ({@link Automation.Snapshot}): antes e depois.
 * Desfazer/refazer restaura a fotografia — as mesmas sessões e etapas, sem criar identidades novas.
 *
 * <p>Coerência: cada entrada sabe em qual revisão a automação precisa estar para ser desfeita (ou refeita). Se a
 * automação mudou por outro caminho (ex.: etapas gravadas entregues pelo Recorder), a revisão não bate e nada é aplicado
 * — o conflito é tratado por quem chama. Nada vai para o disco; operações recusadas pelo domínio nunca entram.
 */
public final class EditHistory {
    /** Limite padrão de operações guardadas por automação. */
    public static final int DEFAULT_LIMIT = 100;

    /** Uma operação de edição confirmada. */
    public static final class Entry {
        private final String label;
        private final Automation.Snapshot before;
        private final Automation.Snapshot after;
        private final Selection selectionBefore;
        private final Selection selectionAfter;
        private long expectedRevision;

        Entry(String label, Automation.Snapshot before, Automation.Snapshot after, Selection selectionBefore, Selection selectionAfter) {
            this.label = Objects.requireNonNull(label);
            this.before = before;
            this.after = after;
            this.selectionBefore = selectionBefore;
            this.selectionAfter = selectionAfter;
            this.expectedRevision = after.revision();
        }

        /** Descrição curta da operação (ex.: "Mover etapa"). */
        public String label() {
            return label;
        }

        Automation.Snapshot before() {
            return before;
        }

        Automation.Snapshot after() {
            return after;
        }

        Selection selectionBefore() {
            return selectionBefore;
        }

        Selection selectionAfter() {
            return selectionAfter;
        }

        long expectedRevision() {
            return expectedRevision;
        }

        void expect(long revision) {
            expectedRevision = revision;
        }
    }

    private final int limit;
    private final Map<AutomationId, Deque<Entry>> undo = new HashMap<>();
    private final Map<AutomationId, Deque<Entry>> redo = new HashMap<>();

    public EditHistory() {
        this(DEFAULT_LIMIT);
    }

    /** @param limit operações guardadas por automação (as mais antigas saem primeiro) */
    public EditHistory(int limit) {
        if (limit < 1) throw new IllegalArgumentException("limit < 1");
        this.limit = limit;
    }

    public int limit() {
        return limit;
    }

    /** Registra uma operação confirmada. Uma nova edição invalida o que poderia ser refeito. */
    void record(AutomationId id, Entry entry) {
        Deque<Entry> u = undo.computeIfAbsent(id, k -> new ArrayDeque<>());
        u.push(entry);
        while (u.size() > limit) u.removeLast();
        redo.remove(id);
    }

    Optional<Entry> peekUndo(AutomationId id) {
        return Optional.ofNullable(undo.getOrDefault(id, new ArrayDeque<>()).peek());
    }

    Optional<Entry> peekRedo(AutomationId id) {
        return Optional.ofNullable(redo.getOrDefault(id, new ArrayDeque<>()).peek());
    }

    /**
     * Desfeita: a entrada passa para a pilha de refazer, esperando a revisão atual. A automação voltou ao estado "antes"
     * dela; se a entrada anterior (agora no topo) terminou exatamente nesse estado — nada aconteceu entre as duas —, ela
     * passa a esperar a revisão atual (restaurar conta revisão). Se houve uma alteração externa entre elas (ex.: etapas
     * do Recorder), a anterior continua esperando a revisão antiga e será recusada como conflito.
     */
    void undone(AutomationId id, long revisionNow) {
        Entry e = undo.get(id).pop();
        e.expect(revisionNow);
        redo.computeIfAbsent(id, k -> new ArrayDeque<>()).push(e);
        Entry previous = undo.get(id).peek();
        if (previous != null && previous.after().revision() == e.before().revision()) previous.expect(revisionNow);
    }

    /** Refeita: volta para a pilha de desfazer; a próxima a refazer, se começa no estado atual, espera a revisão atual. */
    void redone(AutomationId id, long revisionNow) {
        Entry e = redo.get(id).pop();
        e.expect(revisionNow);
        undo.computeIfAbsent(id, k -> new ArrayDeque<>()).push(e);
        Entry next = redo.get(id).peek();
        if (next != null && next.before().revision() == e.after().revision()) next.expect(revisionNow);
    }

    /** Descarta o histórico da automação (conflito com uma alteração externa). */
    void clear(AutomationId id) {
        undo.remove(id);
        redo.remove(id);
    }

    public int undoSize(AutomationId id) {
        return undo.getOrDefault(id, new ArrayDeque<>()).size();
    }

    public int redoSize(AutomationId id) {
        return redo.getOrDefault(id, new ArrayDeque<>()).size();
    }

    public Optional<String> undoLabel(AutomationId id) {
        return peekUndo(id).map(Entry::label);
    }

    public Optional<String> redoLabel(AutomationId id) {
        return peekRedo(id).map(Entry::label);
    }
}
