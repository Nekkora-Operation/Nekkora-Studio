package com.nekkora.studio.pdd;

import com.nekkora.studio.pdd.DocumentDraft.Annotation;
import com.nekkora.studio.pdd.DocumentDraft.Block;
import com.nekkora.studio.pdd.DocumentDraft.Instruction;
import com.nekkora.studio.pdd.DocumentDraft.Pending;
import com.nekkora.studio.pdd.DocumentDraft.SafeCapture;
import com.nekkora.studio.pdd.DocumentDraft.Section;
import java.util.ArrayList;
import java.util.List;

/**
 * STUDIO-010 — pendências do rascunho. Bloqueiam a exportação (falha segura):
 * <ul>
 *   <li>{@code PROCESS_CHANGED}: a automação mudou desde o rascunho (atualizar antes);</li>
 *   <li>{@code SECTION_TITLE_REVIEW}, {@code TITLE_REVIEW}, {@code INSTRUCTION_REVIEW}: texto neutro aguardando revisão;</li>
 *   <li>{@code TEXT_POLICY}: texto (inclusive revisado) com padrão proibido — dígitos, dado sensível, URL, seletor,
 *       expressão de variável, identificador técnico;</li>
 *   <li>{@code CAPTURE_MISMATCH}: a captura associada não corresponde às etapas ou à tela do bloco.</li>
 * </ul>
 * Só avisam (o documento declara a falta, sem inventar): {@code CAPTURE_MISSING} ("imagem indisponível") e
 * {@code MARKER_UNAVAILABLE} (o alvo da etapa não foi comprovado na imagem).
 */
final class Pendings {
    private Pendings() {}

    static List<Pending> of(DocumentDraft d, ProcessProjection current) {
        List<Pending> out = new ArrayList<>();
        if (current != null && !d.processRevision().equals(current.revision())) out.add(new Pending("PROCESS_CHANGED", "", 0, true));
        for (Section s : d.sections()) {
            text(out, s.title(), "SECTION_TITLE_REVIEW", "secao-" + s.number(), 0);
            for (Block b : s.blocks()) {
                text(out, b.title(), "TITLE_REVIEW", b.blockId(), 0);
                for (Instruction i : b.instructions()) text(out, i.text(), "INSTRUCTION_REVIEW", b.blockId(), i.ordinal());
                SafeCapture c = b.capture().map(d.captures()::get).orElse(null);
                if (b.capture().isPresent() && (c == null || !DraftEdits.matches(c, b))) {
                    out.add(new Pending("CAPTURE_MISMATCH", b.blockId(), 0, true));
                } else if (c == null) {
                    out.add(new Pending("CAPTURE_MISSING", b.blockId(), 0, false));
                } else {
                    for (Instruction i : b.instructions()) {
                        boolean placed = c.annotations().stream().anyMatch(a -> a.stepId().equals(i.stepId()) && a.status() == Annotation.Status.PLACED);
                        if (!placed) out.add(new Pending("MARKER_UNAVAILABLE", b.blockId(), i.ordinal(), false));
                    }
                }
            }
        }
        return out;
    }

    private static void text(List<Pending> out, DocText t, String review, String place, int instruction) {
        if (t.state() == DocText.State.NEEDS_REVIEW) out.add(new Pending(review, place, instruction, true));
        else if (!PddText.problems(t.value()).isEmpty()) out.add(new Pending("TEXT_POLICY", place, instruction, true));
    }

    static boolean exportable(List<Pending> pending) {
        return pending.stream().noneMatch(Pending::blocking);
    }
}
