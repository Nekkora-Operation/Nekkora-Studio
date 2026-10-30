package com.nekkora.studio.ai.plan;

import com.nekkora.studio.ai.quality.QualityIssue;
import com.nekkora.studio.codegen.CodegenConfig;
import com.nekkora.studio.codegen.SingleConfig;
import com.nekkora.studio.domain.StepId;
import java.util.List;
import java.util.Optional;

/**
 * STUDIO-014 — plano TIPADO de desenvolvimento (nunca Python): modelo de execução, variáveis com procedência, Pages
 * justificadas, o vínculo etapa → classe/método → chave → variável → capacidade → pré/pós-condição, as capacidades do
 * framework usadas e a configuração técnica que o gerador oficial vai receber. É o que "Desenvolver automação" mostra
 * em "O que vou utilizar" e o que a camada V3 verifica.
 */
public record DevelopmentPlan(ExecutionModel model, String modelBasis, List<VariableDeriver.Planned> variables, List<PlannedPage> pages, List<StepPlan> steps,
                              List<String> capabilities, SingleConfig single, CodegenConfig tabular, List<QualityIssue> issues, String planner,
                              com.nekkora.studio.codegen.CodeNames names) {

    /** Sem evidência de nomes (lote tabular, PDD pendente). */
    public DevelopmentPlan(ExecutionModel model, String modelBasis, List<VariableDeriver.Planned> variables, List<PlannedPage> pages, List<StepPlan> steps,
                           List<String> capabilities, SingleConfig single, CodegenConfig tabular, List<QualityIssue> issues, String planner) {
        this(model, modelBasis, variables, pages, steps, capabilities, single, tabular, issues, planner, com.nekkora.studio.codegen.CodeNames.none());
    }

    /** Como o processo recebe os dados. */
    public enum ExecutionModel {
        SINGLE("Execução única (sem planilha)"), TABULAR("Lote tabular: uma transação por linha da planilha"),
        /** NEKKORA-E2E-002: lista de entrada (CSV/XLSX) → uma transação por item → planilha Excel de saída, tudo ou nada. */
        LIST("Lista → planilha: uma transação por item da lista; planilha Excel de saída (tudo ou nada)");

        private final String label;

        ExecutionModel(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Uma Page (classe) e por que ela existe. */
    public record PlannedPage(String className, String prefix, List<String> methods, int steps, String justification) {
        public PlannedPage {
            methods = List.copyOf(methods);
        }
    }

    /** Vínculo de uma etapa. */
    public record StepPlan(StepId step, String page, String method, String capability, String variable, String effect, String postcondition) {}

    public DevelopmentPlan {
        variables = List.copyOf(variables);
        pages = List.copyOf(pages);
        steps = List.copyOf(steps);
        capabilities = List.copyOf(capabilities);
        issues = List.copyOf(issues);
        names = names == null ? com.nekkora.studio.codegen.CodeNames.none() : names;
    }

    public Optional<SingleConfig> singleConfig() {
        return Optional.ofNullable(single);
    }

    public Optional<CodegenConfig> tabularConfig() {
        return Optional.ofNullable(tabular);
    }

    public boolean ready() {
        return issues.stream().noneMatch(i -> i.kind().blocking()) && (single != null || tabular != null);
    }
}
