package com.nekkora.studio.pdd;

import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.pdd.DocumentDraft.Block;
import com.nekkora.studio.pdd.DocumentDraft.Instruction;
import com.nekkora.studio.pdd.DocumentDraft.SafeCapture;
import com.nekkora.studio.pdd.DocumentDraft.Section;
import com.nekkora.studio.pdd.ProcessProjection.SectionFacts;
import com.nekkora.studio.pdd.ProcessProjection.StepFacts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * STUDIO-010 G2/G6 — edições do RASCUNHO (nunca da automação). Cada operação devolve um rascunho novo; o editor guarda o
 * histórico para desfazer/refazer. Operações inválidas lançam {@link DraftException} com um código.
 */
public final class DraftEdits {
    private DraftEdits() {}

    /** Edição recusada (código estável para a interface). */
    public static final class DraftException extends RuntimeException {
        private final String code;

        public DraftException(String code) {
            super(code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /**
     * Divide o bloco antes da etapa {@code at} (1 ≤ at < etapas). As duas partes perdem a captura (ela valia para as etapas
     * juntas) e recebem títulos derivados; um título revisado fica com a primeira parte.
     */
    public static DocumentDraft split(DocumentDraft d, ProcessProjection p, String blockId, int at) {
        Block b = d.block(blockId).orElseThrow(() -> new DraftException("BLOCK_NOT_FOUND"));
        if (at < 1 || at >= b.stepIds().size()) throw new DraftException("SPLIT_POSITION_INVALID");
        Map<StepId, StepFacts> facts = facts(p);
        Block first = rebuild(b.stepIds().subList(0, at), b, facts, true);
        Block second = rebuild(b.stepIds().subList(at, b.stepIds().size()), b, facts, false);
        return replace(d, b.blockId(), List.of(first, second));
    }

    /**
     * Junta dois blocos ADJACENTES da mesma seção e da mesma tela, sem troca de tela observada entre eles. A captura é
     * descartada (ela não mostra o bloco inteiro).
     */
    public static DocumentDraft join(DocumentDraft d, ProcessProjection p, String firstId, String secondId) {
        Section s = sectionOf(d, firstId);
        int i = indexIn(s, firstId);
        if (i + 1 >= s.blocks().size() || !s.blocks().get(i + 1).blockId().equals(secondId)) throw new DraftException("BLOCKS_NOT_ADJACENT");
        Block a = s.blocks().get(i);
        Block b = s.blocks().get(i + 1);
        Map<StepId, StepFacts> facts = facts(p);
        StepFacts lastOfA = facts.get(a.stepIds().get(a.stepIds().size() - 1));
        StepFacts firstOfB = facts.get(b.stepIds().get(0));
        if (lastOfA == null || firstOfB == null) throw new DraftException("PROCESS_CHANGED");
        if (DraftBuilder.boundary(lastOfA, firstOfB)) throw new DraftException("JOIN_CROSSES_SCREEN");
        List<StepId> ids = new ArrayList<>(a.stepIds());
        ids.addAll(b.stepIds());
        Block joined = rebuild(ids, a, facts, true);
        List<Instruction> kept = new ArrayList<>();
        for (Instruction x : a.instructions()) kept.add(x);
        for (Instruction x : b.instructions()) kept.add(new Instruction(x.stepId(), kept.size() + 1, x.text(), x.kind()));
        joined = new Block(joined.blockId(), joined.sessionId(), joined.stepIds(), joined.title(), kept, joined.screenKey(), "");
        List<Block> blocks = new ArrayList<>(s.blocks());
        blocks.remove(i + 1);
        blocks.set(i, joined);
        return withSection(d, new Section(s.sessionId(), s.number(), s.title(), blocks));
    }

    /** Título do bloco escrito/aceito no editor. Texto com problema da política é recusado aqui mesmo. */
    public static DocumentDraft reviseBlockTitle(DocumentDraft d, String blockId, String text, String reviewer) {
        checkText(text);
        Block b = d.block(blockId).orElseThrow(() -> new DraftException("BLOCK_NOT_FOUND"));
        return replace(d, blockId, List.of(new Block(b.blockId(), b.sessionId(), b.stepIds(), DocText.reviewed(text.strip(), reviewer), b.instructions(),
                b.screenKey(), b.captureSha())));
    }

    public static DocumentDraft reviseSectionTitle(DocumentDraft d, int sectionNumber, String text, String reviewer) {
        checkText(text);
        Section s = d.sections().stream().filter(x -> x.number() == sectionNumber).findFirst().orElseThrow(() -> new DraftException("SECTION_NOT_FOUND"));
        return withSection(d, new Section(s.sessionId(), s.number(), DocText.reviewed(text.strip(), reviewer), s.blocks()));
    }

    public static DocumentDraft reviseInstruction(DocumentDraft d, String blockId, int ordinal, String text, String reviewer) {
        checkText(text);
        Block b = d.block(blockId).orElseThrow(() -> new DraftException("BLOCK_NOT_FOUND"));
        List<Instruction> list = new ArrayList<>();
        boolean found = false;
        for (Instruction x : b.instructions()) {
            if (x.ordinal() == ordinal) {
                list.add(new Instruction(x.stepId(), x.ordinal(), DocText.reviewed(text.strip(), reviewer), x.kind()));
                found = true;
            } else {
                list.add(x);
            }
        }
        if (!found) throw new DraftException("INSTRUCTION_NOT_FOUND");
        return replace(d, blockId, List.of(new Block(b.blockId(), b.sessionId(), b.stepIds(), b.title(), list, b.screenKey(), b.captureSha())));
    }

    /**
     * Associa uma captura JÁ sanitizada ao bloco. Recusada se não foi feita para estas etapas ou se a tela é outra: uma
     * imagem de outra etapa ou de outra tela nunca ilustra este bloco.
     */
    public static DocumentDraft attachCapture(DocumentDraft d, String blockId, SafeCapture c) {
        Block b = d.block(blockId).orElseThrow(() -> new DraftException("BLOCK_NOT_FOUND"));
        if (!matches(c, b)) throw new DraftException("CAPTURE_DOES_NOT_MATCH_BLOCK");
        DocumentDraft withCapture = new DocumentDraft(d.automationId(), d.processRevision(), d.sections(), DocumentDraft.with(d.captures(), c));
        return replace(withCapture, blockId, List.of(new Block(b.blockId(), b.sessionId(), b.stepIds(), b.title(), b.instructions(), b.screenKey(),
                c.sha256())));
    }

    public static DocumentDraft detachCapture(DocumentDraft d, String blockId) {
        Block b = d.block(blockId).orElseThrow(() -> new DraftException("BLOCK_NOT_FOUND"));
        return replace(d, blockId, List.of(new Block(b.blockId(), b.sessionId(), b.stepIds(), b.title(), b.instructions(), b.screenKey(), "")));
    }

    /** Remove do mapa as capturas que nenhum bloco referencia (política de retenção). */
    public static DocumentDraft dropUnreferencedCaptures(DocumentDraft d) {
        Set<String> used = new HashSet<>();
        d.blocks().forEach(b -> b.capture().ifPresent(used::add));
        Map<String, SafeCapture> kept = new LinkedHashMap<>();
        d.captures().forEach((k, v) -> {
            if (used.contains(k)) kept.put(k, v);
        });
        return new DocumentDraft(d.automationId(), d.processRevision(), d.sections(), kept);
    }

    /**
     * Atualiza o rascunho para uma projeção nova (a automação mudou). Cada bloco cujas etapas continuam iguais (mesmos
     * identificadores, na mesma ordem, com os mesmos fatos) mantém textos revisados e captura; o resto é derivado de novo
     * e perde a captura. Etapas novas entram; etapas removidas saem — nada é inventado.
     */
    public static DocumentDraft refresh(DocumentDraft old, ProcessProjection oldProjection, ProcessProjection now) {
        DocumentDraft fresh = DraftBuilder.build(now);
        Map<StepId, StepFacts> before = oldProjection == null ? Map.of() : facts(oldProjection); // sem a projeção antiga: tudo é refeito
        Map<StepId, StepFacts> after = facts(now);
        Map<List<StepId>, Block> oldBlocks = new HashMap<>();
        old.blocks().forEach(b -> oldBlocks.put(b.stepIds(), b));
        Map<Integer, DocText> oldSectionTitles = new HashMap<>();
        Map<Integer, String> oldSectionIds = new HashMap<>();
        old.sections().forEach(s -> {
            oldSectionTitles.put(s.number(), s.title());
            oldSectionIds.put(s.number(), s.sessionId().value().toString());
        });
        List<Section> sections = new ArrayList<>();
        Map<String, SafeCapture> captures = new LinkedHashMap<>();
        for (Section s : fresh.sections()) {
            List<Block> blocks = new ArrayList<>();
            for (Block b : s.blocks()) {
                Block prev = oldBlocks.get(b.stepIds());
                boolean same = prev != null && b.stepIds().stream().allMatch(id -> before.get(id) != null && sameFacts(before.get(id), after.get(id)));
                if (same) {
                    blocks.add(prev);
                    prev.capture().map(old.captures()::get).ifPresent(c -> captures.put(c.sha256(), c));
                } else {
                    blocks.add(b);
                }
            }
            DocText title = s.title();
            if (s.sessionId().value().toString().equals(oldSectionIds.get(s.number())) && oldSectionTitles.get(s.number()).state() == DocText.State.REVIEWED) {
                title = oldSectionTitles.get(s.number());
            }
            sections.add(new Section(s.sessionId(), s.number(), title, blocks));
        }
        return new DocumentDraft(now.automationId(), now.revision(), sections, captures);
    }

    // ------------------------------------------------------------------------------------------- apoio

    /** A captura foi feita para exatamente estas etapas e esta tela? */
    static boolean matches(SafeCapture c, Block b) {
        if (!c.forSteps().equals(b.stepIds())) return false;
        if (!c.screenKey().isEmpty() && !b.screenKey().isEmpty() && !c.screenKey().equals(b.screenKey())) return false;
        return c.annotations().stream().allMatch(a -> b.stepIds().contains(a.stepId()));
    }

    private static boolean sameFacts(StepFacts a, StepFacts b) {
        if (b == null) return false;
        return a.action() == b.action() && a.hasTarget() == b.hasTarget() && a.targetKind().equals(b.targetKind()) && a.targetName().equals(b.targetName())
                && a.sensitive() == b.sensitive() && a.key().equals(b.key()) && a.screenKey().equals(b.screenKey())
                && a.screenChangeAfter() == b.screenChangeAfter() && a.origin() == b.origin();
    }

    private static void checkText(String text) {
        if (text == null || text.isBlank()) throw new DraftException("TEXT_EMPTY");
        List<String> problems = PddText.problems(text.strip());
        if (!problems.isEmpty()) throw new DraftException("TEXT_POLICY:" + String.join(",", problems));
    }

    static Map<StepId, StepFacts> facts(ProcessProjection p) {
        Map<StepId, StepFacts> m = new HashMap<>();
        for (SectionFacts s : p.sections()) s.steps().forEach(st -> m.put(st.stepId(), st));
        return m;
    }

    private static Block rebuild(List<StepId> ids, Block original, Map<StepId, StepFacts> facts, boolean keepReviewedTitle) {
        List<StepFacts> fs = new ArrayList<>();
        for (StepId id : ids) {
            StepFacts f = facts.get(id);
            if (f == null) throw new DraftException("PROCESS_CHANGED");
            fs.add(f);
        }
        Block derived = DraftBuilder.block(original.sessionId(), fs);
        List<Instruction> instructions = new ArrayList<>();
        int n = 0;
        for (StepId id : ids) {
            Instruction prev = original.instructions().stream().filter(x -> x.stepId().equals(id)).findFirst().orElse(null);
            Instruction base = prev != null ? prev : derived.instructions().stream().filter(x -> x.stepId().equals(id)).findFirst().orElseThrow();
            instructions.add(new Instruction(id, ++n, base.text(), base.kind()));
        }
        DocText title = keepReviewedTitle && original.title().state() == DocText.State.REVIEWED ? original.title() : derived.title();
        return new Block(derived.blockId(), original.sessionId(), ids, title, instructions, derived.screenKey(), "");
    }

    private static Section sectionOf(DocumentDraft d, String blockId) {
        return d.sections().stream().filter(s -> s.blocks().stream().anyMatch(b -> b.blockId().equals(blockId))).findFirst()
                .orElseThrow(() -> new DraftException("BLOCK_NOT_FOUND"));
    }

    private static int indexIn(Section s, String blockId) {
        for (int i = 0; i < s.blocks().size(); i++) if (s.blocks().get(i).blockId().equals(blockId)) return i;
        throw new DraftException("BLOCK_NOT_FOUND");
    }

    private static DocumentDraft replace(DocumentDraft d, String blockId, List<Block> with) {
        Section s = sectionOf(d, blockId);
        List<Block> blocks = new ArrayList<>();
        for (Block b : s.blocks()) {
            if (b.blockId().equals(blockId)) blocks.addAll(with);
            else blocks.add(b);
        }
        return withSection(d, new Section(s.sessionId(), s.number(), s.title(), blocks));
    }

    private static DocumentDraft withSection(DocumentDraft d, Section changed) {
        List<Section> sections = new ArrayList<>();
        for (Section s : d.sections()) sections.add(s.number() == changed.number() ? changed : s);
        return new DocumentDraft(d.automationId(), d.processRevision(), sections, d.captures());
    }
}
