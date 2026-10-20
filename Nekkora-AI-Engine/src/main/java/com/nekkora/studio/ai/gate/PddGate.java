package com.nekkora.studio.ai.gate;

import com.nekkora.studio.ai.context.ProjectContext;
import com.nekkora.studio.ai.quality.Layer;
import com.nekkora.studio.ai.quality.QualityIssue;
import com.nekkora.studio.ai.spec.FunctionalSpec;
import com.nekkora.studio.ai.spec.SpecResolver;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.pdd.DocText;
import com.nekkora.studio.pdd.DocumentDraft;
import com.nekkora.studio.pdd.ProcessProjection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * STUDIO-014 — gate {@code PDD_READY}: verificação DETERMINÍSTICA de que o PDD e a especificação funcional descrevem a
 * revisão atual do modelo com o que o código precisa. Não é texto de IA nem existência de PDF:
 * <ol>
 *   <li>existe rascunho do PDD da automação;</li>
 *   <li>ele foi derivado da revisão atual do processo ({@code processRevision});</li>
 *   <li>sem pendência que impeça exportar (texto a revisar, política de texto, imagem trocada);</li>
 *   <li>cobertura: toda etapa do modelo está num bloco e todo bloco aponta para etapas que existem;</li>
 *   <li>especificação: efeito de cada clique, confirmação de cada envio e conclusão observável — decididos ou derivados
 *       com procedência; o resto vira pedido de ajuda.</li>
 * </ol>
 * Sem imagem num bloco não bloqueia (a política de captura pode proibir): vira limitação declarada. Marcador manual não
 * é confirmação de alvo — o gate não o usa para nada além de documentação. O registro ({@link GateRecord}) amarra o
 * resultado às revisões exatas do modelo, do processo, do documento e da especificação: qualquer edição o invalida.
 */
public final class PddGate {
    private PddGate() {}

    public static final String EVALUATOR = "nekkora-studio/pdd-gate/1.0.0";

    public enum State { PDD_READY, PDD_BLOCKED }

    public record Result(State state, List<QualityIssue> issues, GateRecord record, SpecResolver.ResolvedSpec spec) {
        public boolean ready() {
            return state == State.PDD_READY;
        }
    }

    public static Result evaluate(ProjectContext ctx, Optional<DocumentDraft> draft, FunctionalSpec spec, ProcessProjection projection, Instant now) {
        List<QualityIssue> issues = new ArrayList<>();
        SpecResolver.ResolvedSpec rs = SpecResolver.resolve(ctx, spec);
        Set<String> reviewers = new TreeSet<>();
        int covered = 0;
        if (draft.isEmpty()) {
            issues.add(QualityIssue.of("PDD_MISSING", Layer.V1_PDD, QualityIssue.Kind.BLOCKER, null, "PDD",
                    "Esta automação ainda não tem PDD.", "Abra o PDD (Automação → PDD visual…): o rascunho é montado da gravação; revise os textos marcados."));
        } else {
            DocumentDraft d = draft.get();
            boolean outdated = !d.processRevision().equals(projection.revision());
            if (outdated) {
                issues.add(QualityIssue.of("PDD_OUTDATED", Layer.V1_PDD, QualityIssue.Kind.BLOCKER, null, "PDD",
                        "O PDD foi feito de outra versão do processo.", "Abra o PDD e use \"Atualizar com a automação\"; depois revise os blocos marcados."));
            }
            for (DocumentDraft.Pending p : d.pending(projection)) {
                if (p.code().equals("PROCESS_CHANGED")) continue;
                QualityIssue.Kind k = p.blocking() ? QualityIssue.Kind.BLOCKER : QualityIssue.Kind.INFO;
                String detail = switch (p.code()) {
                    case "SECTION_TITLE_REVIEW", "TITLE_REVIEW", "INSTRUCTION_REVIEW" -> "Texto do PDD aguardando revisão.";
                    case "TEXT_POLICY" -> "Texto do PDD com padrão proibido (dado, endereço ou identificador técnico).";
                    case "CAPTURE_MISMATCH" -> "A imagem do bloco não corresponde às etapas.";
                    case "CAPTURE_MISSING" -> "Bloco sem imagem: limitação visual declarada no documento (não impede o desenvolvimento).";
                    case "MARKER_UNAVAILABLE" -> "Etapa sem marcador na imagem (limitação visual).";
                    case "MARKER_OUTSIDE_CROP" -> "Marcador fora do recorte (limitação visual).";
                    default -> "Pendência do PDD.";
                };
                issues.add(QualityIssue.of("PDD_" + p.code(), Layer.V1_PDD, k, null, "PDD · " + p.place(), detail,
                        p.blocking() ? "Abra o PDD neste bloco e revise." : "").withBlock(p.place()));
            }
            Set<StepId> inBlocks = new LinkedHashSet<>();
            for (DocumentDraft.Block b : d.blocks()) {
                inBlocks.addAll(b.stepIds());
                collect(reviewers, b.title());
                b.instructions().forEach(i -> collect(reviewers, i.text()));
                for (StepId id : b.stepIds()) {
                    if (ctx.step(id).isEmpty() && !outdated) {
                        issues.add(QualityIssue.of("PDD_STEP_NOT_IN_MODEL", Layer.V1_PDD, QualityIssue.Kind.BLOCKER, null, "PDD · " + b.blockId(),
                                "O PDD descreve uma etapa que não existe mais no modelo.", "Atualize o PDD com a automação.").withBlock(b.blockId()));
                    }
                }
            }
            d.sections().forEach(s -> collect(reviewers, s.title()));
            for (ProjectContext.Step s : ctx.steps()) {
                if (inBlocks.contains(s.stepId())) covered++;
                else if (!outdated) {
                    issues.add(QualityIssue.of("PDD_STEP_NOT_COVERED", Layer.V1_PDD, QualityIssue.Kind.BLOCKER, s.stepId(), placeOf(s),
                            "Etapa do modelo sem descrição no PDD.", "Atualize o PDD com a automação e revise o bloco."));
                }
            }
        }
        issues.addAll(rs.issues());
        spec.decisions().forEach(x -> reviewers.add(x.actor().name()));
        boolean ready = issues.stream().noneMatch(i -> i.kind().blocking());
        GateRecord rec = new GateRecord(ready ? State.PDD_READY : State.PDD_BLOCKED, ctx.automationId(), ctx.automationRevision(), projection.revision(),
                draft.map(DocumentDraft::revision).orElse(""), spec.revision(), covered, ctx.steps().size(), List.copyOf(reviewers),
                issues.stream().filter(i -> i.kind().blocking()).map(QualityIssue::id).toList(), now, EVALUATOR);
        return new Result(rec.state(), List.copyOf(issues), rec, rs);
    }

    private static void collect(Set<String> reviewers, DocText t) {
        if (t.state() == DocText.State.REVIEWED) reviewers.add(t.reviewer());
    }

    private static String placeOf(ProjectContext.Step s) {
        return "Sessão " + s.session() + " · Etapa " + String.format("%02d", s.number()) + (s.targetName().isEmpty() ? "" : " — " + s.targetName());
    }

    /** Contagem por código (para resumos). */
    public static Map<String, Integer> counts(List<QualityIssue> issues) {
        Map<String, Integer> out = new LinkedHashMap<>();
        issues.forEach(i -> out.merge(i.code(), 1, Integer::sum));
        return out;
    }
}
