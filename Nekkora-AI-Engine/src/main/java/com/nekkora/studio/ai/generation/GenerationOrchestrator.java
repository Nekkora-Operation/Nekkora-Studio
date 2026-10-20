package com.nekkora.studio.ai.generation;

import com.nekkora.studio.ai.capability.FrameworkCapabilityCatalog;
import com.nekkora.studio.ai.context.ProjectContext;
import com.nekkora.studio.ai.gate.PddGate;
import com.nekkora.studio.ai.plan.DevelopmentPlan;
import com.nekkora.studio.ai.plan.DevelopmentPlanner;
import com.nekkora.studio.ai.quality.Layer;
import com.nekkora.studio.ai.quality.QualityIssue;
import com.nekkora.studio.ai.spec.FunctionalSpec;
import com.nekkora.studio.ai.store.DevelopmentStore;
import com.nekkora.studio.codegen.CodegenConfig;
import com.nekkora.studio.codegen.OutputWriter;
import com.nekkora.studio.codegen.PythonGenerator;
import com.nekkora.studio.codegen.SingleGenerator;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.pdd.DocumentDraft;
import com.nekkora.studio.pdd.ProcessProjection;
import com.nekkora.studio.validation.AutomationValidator;
import com.nekkora.studio.validation.RuleSet;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * STUDIO-014 — GenerationOrchestrator: do modelo ao Python pelo GERADOR OFICIAL, em fases, sem questionário:
 * <ol>
 *   <li>Verificando PDD — gate {@code PDD_READY} (bloqueado → nenhum arquivo);</li>
 *   <li>Organizando arquitetura e variáveis — plano determinístico (as variáveis derivadas já foram aplicadas no modelo
 *       pela operação desfazível do Studio);</li>
 *   <li>Validando modelo — validação estrutural (regras 1.1.0, as mesmas do debug guiado);</li>
 *   <li>Gerando Python — {@link SingleGenerator} (execução única) ou {@link PythonGenerator} (lote tabular declarado);
 *       gravação protegida ({@code .novo}/{@code .diff} para arquivo editado à mão).</li>
 * </ol>
 * A configuração técnica é DERIVADA e guardada para auditoria; ninguém a escreve. Nada é executado.
 */
public final class GenerationOrchestrator {
    private GenerationOrchestrator() {}

    public enum Phase {
        ANALYZING("Analisando projeto"), PDD("Verificando PDD"), PLAN("Organizando arquitetura e variáveis"), VALIDATING("Validando modelo"),
        GENERATING("Gerando Python"), CHECKING("Validando saída");

        private final String label;

        Phase(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Estado do desenvolvimento (os estados da missão). */
    public enum Stage { PROJECT_ANALYZED, PDD_READY, PLAN_VALIDATED, CODE_GENERATED, CODE_VALIDATED }

    public interface Progress {
        void phase(Phase p);
    }

    public record Run(Stage reached, Phase stoppedAt, PddGate.Result gate, DevelopmentPlan plan, List<PythonGenerator.Blocker> blockers,
                      OutputWriter.Report written, Path outputDir, String configJson, List<QualityIssue> issues) {
        public boolean generated() {
            return written != null;
        }
    }

    public static Run develop(Automation a, Optional<DocumentDraft> draft, FunctionalSpec spec, FrameworkCapabilityCatalog cat, Optional<CodegenConfig> declaredTabular,
                              Path outputDir, DevelopmentStore store, Instant now, Progress progress, BooleanSupplier cancelled) throws IOException {
        progress.phase(Phase.ANALYZING);
        ProjectContext ctx = ProjectContext.of(a, draft);
        progress.phase(Phase.PDD);
        PddGate.Result gate = PddGate.evaluate(ctx, draft, spec, ProcessProjection.of(a), now);
        store.saveGate(gate.record());
        if (!gate.ready()) return new Run(Stage.PROJECT_ANALYZED, Phase.PDD, gate, null, List.of(), null, outputDir, null, gate.issues());
        if (cancelled.getAsBoolean()) return cancelledRun(Stage.PDD_READY, Phase.PDD, gate, null, outputDir);
        progress.phase(Phase.PLAN);
        DevelopmentPlan plan = DevelopmentPlanner.plan(a, ctx, gate, cat, declaredTabular);
        if (!plan.ready()) return new Run(Stage.PDD_READY, Phase.PLAN, gate, plan, List.of(), null, outputDir, null, plan.issues());
        if (cancelled.getAsBoolean()) return cancelledRun(Stage.PLAN_VALIDATED, Phase.PLAN, gate, plan, outputDir);
        progress.phase(Phase.VALIDATING);
        var report = new AutomationValidator(RuleSet.V1_1).validate(a);
        progress.phase(Phase.GENERATING);
        PythonGenerator.Result result;
        String configJson;
        if (plan.tabularConfig().isPresent()) {
            result = PythonGenerator.generate(a, report, plan.tabularConfig().get());
            configJson = null; // configuração tabular é a DECLARADA no projeto (não é gravada de novo)
        } else {
            configJson = plan.singleConfig().orElseThrow().toJson();
            result = SingleGenerator.generate(a, report, plan.singleConfig().get());
        }
        if (!result.ok()) {
            List<QualityIssue> issues = new ArrayList<>();
            for (PythonGenerator.Blocker b : result.blockers()) {
                issues.add(QualityIssue.of("GEN_" + b.code(), Layer.V3_PLAN, QualityIssue.Kind.BLOCKER, b.step(), "Gerador oficial",
                        "O pré-voo do gerador recusou (" + b.code() + "). Nenhum arquivo foi gravado.", "Veja a etapa e valide a automação."));
            }
            return new Run(Stage.PLAN_VALIDATED, Phase.GENERATING, gate, plan, result.blockers(), null, outputDir, configJson, issues);
        }
        if (cancelled.getAsBoolean()) return cancelledRun(Stage.PLAN_VALIDATED, Phase.GENERATING, gate, plan, outputDir);
        if (configJson != null) store.saveConfig(a.id(), configJson);
        OutputWriter.Report written = OutputWriter.write(outputDir, result.files());
        return new Run(Stage.CODE_GENERATED, null, gate, plan, List.of(), written, outputDir, configJson, plan.issues());
    }

    private static Run cancelledRun(Stage reached, Phase at, PddGate.Result gate, DevelopmentPlan plan, Path outputDir) {
        return new Run(reached, at, gate, plan, List.of(new PythonGenerator.Blocker("CANCELLED", null)), null, outputDir, null,
                List.of(QualityIssue.of("CANCELLED", Layer.V3_PLAN, QualityIssue.Kind.INFO, null, "Desenvolvimento", "Cancelado: nenhum arquivo foi gravado.", "")));
    }

    /** Pasta padrão e segura ao lado do projeto: {@code <nome-do-projeto>-python/}. */
    public static Path defaultOutput(Path projectFile) {
        String base = projectFile.getFileName().toString().replaceFirst("\\.nekkora$", "");
        return projectFile.resolveSibling(base + "-python");
    }

    /** Estados atingidos (para o resumo). */
    public static Map<Stage, Boolean> stages(Run r, boolean codeValidated) {
        Map<Stage, Boolean> out = new java.util.EnumMap<>(Stage.class);
        for (Stage s : Stage.values()) out.put(s, s.ordinal() <= r.reached().ordinal() || (s == Stage.CODE_VALIDATED && codeValidated));
        return out;
    }
}
