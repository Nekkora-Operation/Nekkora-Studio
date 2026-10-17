package com.nekkora.studio.pdd;

import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.pdd.DocumentDraft.Block;
import com.nekkora.studio.pdd.DocumentDraft.Instruction;
import com.nekkora.studio.pdd.DocumentDraft.Section;
import com.nekkora.studio.pdd.ProcessProjection.SectionFacts;
import com.nekkora.studio.pdd.ProcessProjection.StepFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * STUDIO-010 G2 — rascunho inicial a partir da projeção. Agrupamento CONSERVADOR, só dentro da sessão e só entre
 * etapas CONSECUTIVAS da mesma tela. Abre bloco novo:
 * <ul>
 *   <li>na mudança de tela (resumo do modelo de URL e do título);</li>
 *   <li>depois de uma etapa em que a gravação observou troca de rota ou de documento;</li>
 *   <li>numa navegação (a etapa abre outra tela);</li>
 *   <li>entre etapas com tela conhecida e etapas sem tela conhecida (manuais, por exemplo).</li>
 * </ul>
 * Nenhuma etapa se perde nem muda de ordem: a concatenação dos blocos de uma seção é a sessão.
 */
public final class DraftBuilder {
    private DraftBuilder() {}

    public static DocumentDraft build(ProcessProjection p) {
        List<Section> sections = new ArrayList<>();
        for (SectionFacts s : p.sections()) {
            sections.add(new Section(s.sessionId(), s.number(), Instructions.sectionTitle(s), group(s)));
        }
        return new DocumentDraft(p.automationId(), p.revision(), sections, Map.of());
    }

    static List<Block> group(SectionFacts s) {
        List<Block> out = new ArrayList<>();
        List<StepFacts> current = new ArrayList<>();
        StepFacts previous = null;
        for (StepFacts st : s.steps()) {
            if (previous != null && boundary(previous, st)) {
                out.add(block(s.sessionId(), current));
                current = new ArrayList<>();
            }
            current.add(st);
            previous = st;
        }
        if (!current.isEmpty()) out.add(block(s.sessionId(), current));
        return out;
    }

    /** Há fronteira de tela entre duas etapas consecutivas? */
    static boolean boundary(StepFacts a, StepFacts b) {
        if (a.screenChangeAfter()) return true;
        if (b.action() == ActionType.NAVIGATE) return true;
        if (a.screenKey().isEmpty() != b.screenKey().isEmpty()) return true;
        return !a.screenKey().equals(b.screenKey());
    }

    static Block block(SessionId session, List<StepFacts> steps) {
        List<StepId> ids = steps.stream().map(StepFacts::stepId).toList();
        List<Instruction> instructions = new ArrayList<>();
        int n = 0;
        for (StepFacts st : steps) {
            instructions.add(new Instruction(st.stepId(), ++n, Instructions.of(st),
                    st.origin() == ProcessProjection.Origin.RECORDED ? Instruction.Kind.OBSERVED : Instruction.Kind.DECLARED));
        }
        return new Block(blockId(session, ids.get(0)), session, ids, Instructions.blockTitle(steps), instructions, steps.get(0).screenKey(), "");
    }

    /** Identidade do bloco: estável para o mesmo começo (sessão + primeira etapa). */
    static String blockId(SessionId session, StepId first) {
        return "blk-" + Canonical.sha256(session.value() + "|" + first.value()).substring(7, 23);
    }
}
