package com.nekkora.studio.ai.spec;

import com.nekkora.studio.ai.context.ProjectContext;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.variable.VariableType;
import com.nekkora.studio.pdd.DocumentDraft;
import com.nekkora.studio.pdd.PddText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * NEKKORA-E2E-002 — o apêndice "Dados do processo" do PDD: como os itens entram (lista de entrada) e como os dados lidos
 * saem (planilha), com as colunas NA ORDEM, a etapa que fornece cada uma, a fonte alternativa declarada e as regras de
 * normalização e de incompletude. Tudo derivado do modelo e da especificação funcional resolvida — nenhum valor, nenhum
 * seletor, nenhum identificador técnico (o texto passa pela política de texto do PDD; linha recusada não entra).
 */
public final class DataFlowDocument {
    private DataFlowDocument() {}

    public static List<String> lines(Automation a, Optional<DocumentDraft> draft, FunctionalSpec spec) {
        ProjectContext ctx = ProjectContext.of(a, draft);
        SpecResolver.ResolvedSpec rs = SpecResolver.resolve(ctx, spec);
        if (!rs.listToSpreadsheet()) return List.of();
        List<String> out = new ArrayList<>();
        out.add("Fluxo: uma transação por linha de uma lista de entrada (CSV ou XLSX); uma planilha Excel de saída, com uma linha por item.");
        out.add("A planilha só é gravada se TODOS os itens derem certo (tudo ou nada); nada parcial fica com o nome do arquivo.");
        List<String> inputs = new ArrayList<>();
        for (ProjectContext.Step s : ctx.steps()) {
            String v = variableOf(a, s.stepId());
            if (v != null && a.variableNamed(v).map(x -> x.origin().kind() == VariableOrigin.Kind.INPUT).orElse(false) && !inputs.contains(v)) inputs.add(v);
        }
        if (!inputs.isEmpty()) out.add("Entrada: uma coluna por dado de entrada — " + String.join("; ", inputs.stream().map(DataFlowDocument::label).toList()) + ".");
        Map<StepId, StepId> fallbackOf = new LinkedHashMap<>();
        rs.columns().forEach((step, r) -> {
            if (r.value() != null && r.value().startsWith(FunctionalSpec.FALLBACK)) {
                fallbackOf.put(new StepId(java.util.UUID.fromString(r.value().substring(FunctionalSpec.FALLBACK.length()))), step);
            }
        });
        out.add("Planilha de saída (aba Dados), colunas nesta ordem:");
        int n = 0;
        for (ProjectContext.Step s : ctx.steps()) {
            SpecResolver.Resolved<String> r = rs.columns().get(s.stepId());
            if (r == null || r.value() == null || !r.value().startsWith(FunctionalSpec.COLUMN)) continue;
            n++;
            String header = r.value().substring(FunctionalSpec.COLUMN.length());
            StringBuilder b = new StringBuilder(n + ". " + header + " ← " + source(ctx, s));
            if (!s.hasTarget()) b.append(" (chave: sem repetição)");
            if (s.value() == ProjectContext.ValueKind.VARIABLE) b.append(", conferido com a entrada");
            StepId alt = fallbackOf.get(s.stepId());
            if (alt != null) ctx.step(alt).ifPresent(x -> b.append("; se não existir na página: ").append(source(ctx, x)));
            // SPEC-OPTIONAL-COLUMNS-001: a obrigatoriedade revisada vai para o PDD (e muda a especificação → o código fica desatualizado)
            if (rs.optionalColumn(s.stepId())) {
                String reason = rs.requirements().get(s.stepId()).value().substring(FunctionalSpec.OPTIONAL.length());
                b.append("; OPCIONAL (").append(SpecResolver.reasonLabel(reason)).append("): sem o dado, a célula fica vazia");
            }
            out.add(b.toString());
            if (dateOf(a, s.stepId())) out.add("   " + header + ": data por extenso normalizada para AAAA-MM-DD só se dia, mês e ano estiverem no texto.");
        }
        out.add("Itens de uma lista exibida são unidos por ponto e vírgula; o texto é gravado como texto (nunca fórmula).");
        boolean anyOptional = rs.requirements().keySet().stream().anyMatch(rs::optionalColumn);
        out.add(anyOptional ? "Nada é presumido: dado obrigatório vazio ou data incompleta rejeita o item, e a planilha não é gravada; coluna opcional sem "
                + "dado fica vazia (nunca recebe outro valor). As colunas não marcadas como opcionais são obrigatórias."
                : "Nada é presumido: dado obrigatório vazio ou data incompleta rejeita o item, e a planilha não é gravada.");
        out.add("Proveniência separada da planilha: para cada linha, coluna → etapa que forneceu o dado → alvo.");
        return out.stream().filter(l -> PddText.problems(l.length() > PddText.MAX_LENGTH ? l.substring(0, PddText.MAX_LENGTH) : l).isEmpty()).toList();
    }

    private static String source(ProjectContext ctx, ProjectContext.Step s) {
        String where = "sessão " + s.session() + ", etapa " + s.number();
        if (!s.hasTarget()) return "endereço da página lido (" + where + ")";
        if (!s.anchor().isEmpty()) return "linha “" + s.anchor() + "” da tabela (" + where + ")";
        if ("HEADING".equals(s.targetKind())) return "título da página (" + where + ")";
        return "dado apontado (" + where + ")";
    }

    private static String variableOf(Automation a, StepId step) {
        return a.step(step).flatMap(st -> st.parameters().value()).map(v -> v instanceof ValueSource.VariableReference r ? r.name() : null).orElse(null);
    }

    private static boolean dateOf(Automation a, StepId step) {
        return a.step(step).filter(st -> st.actionType() == ActionType.OBSERVE).isPresent() && a.variables().stream()
                .anyMatch(v -> step.equals(v.origin().producer()) && v.type() == VariableType.DATE);
    }

    /** Nome de variável legível (o PDD não mostra identificadores técnicos). */
    static String label(String variable) {
        return variable.replace('_', ' ');
    }
}
