package com.nekkora.studio.ai.plan;

import com.nekkora.studio.ai.context.ProjectContext;
import com.nekkora.studio.ai.quality.Layer;
import com.nekkora.studio.ai.quality.QualityIssue;
import com.nekkora.studio.codegen.CodeNames;
import com.nekkora.studio.codegen.CodeNaming;
import com.nekkora.studio.codegen.SingleConfig;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.variable.VariableDefinition;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.pdd.DocText;
import com.nekkora.studio.pdd.DocumentDraft;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * NEKKORA-CODEGEN-QUALITY-001 — divide o processo em TELAS (Pages) e MÉTODOS por evidência e dá a eles nomes do PDD:
 * <ul>
 *   <li>tela nova quando a tela opaca registrada muda, ou quando a etapa anterior registrou troca de documento/rota
 *       (mesmo com o mesmo modelo de endereço — a busca e a ficha da Wikipédia); voltar a uma tela já vista (não
 *       vizinha) reaproveita a Page;</li>
 *   <li>método novo a cada sessão ou bloco do PDD dentro da tela (uma Page tem seções; nenhuma classe por clique);</li>
 *   <li>Page: título REVISADO da seção do PDD onde a tela começa (1ª palavra de conteúdo), senão o nome seguro da sessão,
 *       senão provisório com apontamento {@code NOME_SEM_EVIDENCIA};</li>
 *   <li>método: título de bloco que começa por verbo no infinitivo (verbo + objeto), senão o que as ações fazem.</li>
 * </ul>
 * Títulos do PDD já passaram pela política de texto e foram revisados por um ator do Studio; nada é inventado.
 */
final class PageNamer {
    private PageNamer() {}

    record Result(List<SingleConfig.Group> groups, CodeNames names, List<QualityIssue> issues) {}

    /** Etapas seguidas da mesma tela, sessão e bloco do PDD, e do mesmo TIPO (agir na tela × ler dados). */
    private record Chunk(String page, int session, String block, boolean reads, List<ProjectContext.Step> steps) {}

    static Result name(Automation a, ProjectContext ctx, Optional<DocumentDraft> draft) {
        Map<Integer, String> sectionTitle = new LinkedHashMap<>();
        Map<String, String> blockTitle = new LinkedHashMap<>();
        draft.ifPresent(d -> {
            d.sections().forEach(s -> {
                if (usable(s.title())) sectionTitle.put(s.number(), s.title().value());
            });
            d.blocks().forEach(b -> {
                if (usable(b.title())) blockTitle.put(b.blockId(), b.title().value());
            });
        });
        List<QualityIssue> issues = new ArrayList<>();
        // 1) telas por evidência
        List<ProjectContext.Step> steps = ctx.steps();
        List<String> screenOf = new ArrayList<>();
        Map<String, String> pageOfScreen = new LinkedHashMap<>(); // id da tela → classe
        Map<String, String> boundaryOf = new LinkedHashMap<>();
        Map<String, Map<Integer, Integer>> sessionsOf = new LinkedHashMap<>(); // tela → sessão → nº de etapas
        Map<String, StepId> firstStepOf = new LinkedHashMap<>();
        String previousOpaque = null;
        String current = null;
        int fresh = 0;
        for (int i = 0; i < steps.size(); i++) {
            ProjectContext.Step s = steps.get(i);
            String opaque = s.screenKey().isEmpty() ? "sessao-" + s.session() : s.screenKey();
            boolean documentChanged = i > 0 && steps.get(i - 1).screenChangeAfter();
            if (current == null || !opaque.equals(previousOpaque) || documentChanged) {
                if (documentChanged && opaque.equals(previousOpaque)) {
                    current = opaque + "#" + (++fresh); // mesmo modelo de endereço, documento novo: outra tela
                    boundaryOf.put(current, "tela nova: a etapa anterior registrou troca de documento (o endereço tem o mesmo modelo)");
                } else {
                    current = opaque;
                    boundaryOf.putIfAbsent(current, documentChanged ? "tela nova: a etapa anterior registrou troca de documento"
                            : "tela observada na gravação (endereço/título diferentes da anterior)");
                }
                firstStepOf.putIfAbsent(current, s.stepId());
            }
            previousOpaque = opaque;
            screenOf.add(current);
            sessionsOf.computeIfAbsent(current, k -> new LinkedHashMap<>()).merge(s.session(), 1, Integer::sum);
        }
        // 2) nome de cada tela (na ordem em que aparecem)
        Set<String> classes = new HashSet<>();
        Set<String> prefixes = new HashSet<>();
        Map<String, String> prefixOfPage = new LinkedHashMap<>();
        Map<String, CodeNames.PageInfo> pageInfo = new LinkedHashMap<>();
        int n = 0;
        for (String screen : new java.util.LinkedHashSet<>(screenOf)) {
            n++;
            // a seção do PDD com mais etapas da tela (empate: a primeira) — uma etapa "Navegar" isolada não nomeia a tela
            int session = sessionsOf.get(screen).entrySet().stream().max(java.util.Comparator.comparingInt(Map.Entry<Integer, Integer>::getValue))
                    .map(Map.Entry::getKey).orElseThrow();
            for (Map.Entry<Integer, Integer> e : sessionsOf.get(screen).entrySet()) {
                if (e.getValue().equals(sessionsOf.get(screen).get(session))) {
                    session = e.getKey();
                    break;
                }
            }
            String title = sectionTitle.getOrDefault(session, "");
            Optional<String> noun = CodeNaming.screenNoun(title);
            CodeNames.Source source = CodeNames.Source.PDD_SECTION_TITLE;
            if (noun.isEmpty()) {
                String safe = ctx.sessionNames().size() >= session ? ctx.sessionNames().get(session - 1) : "";
                noun = CodeNaming.screenNoun(safe);
                source = CodeNames.Source.SESSION_NAME;
                if (noun.isPresent() && title.isEmpty()) title = safe;
            }
            String base;
            if (noun.isPresent()) {
                base = noun.get();
            } else {
                base = "pagina_" + n;
                source = CodeNames.Source.PROVISIONAL;
                issues.add(QualityIssue.of("NOME_SEM_EVIDENCIA", Layer.V3_PLAN, QualityIssue.Kind.WARNING, firstStepOf.get(screen), "Nome da tela " + n,
                        "A seção do PDD onde esta tela começa não tem título que diga o que ela é: o nome no código é provisório.",
                        "Escreva no PDD um título para a seção (ex.: \"Pesquisa do artigo\") e desenvolva de novo."));
            }
            String cls = CodeNaming.unique(base.startsWith("pagina_") ? "Pagina" + n : "Pagina" + CodeNaming.pascal(base), classes, "");
            String prefix = CodeNaming.unique(CodeNaming.snake(base, 40), prefixes, "_");
            pageOfScreen.put(screen, cls);
            prefixOfPage.put(cls, prefix);
            pageInfo.put(cls, new CodeNames.PageInfo(title, List.of(), source, boundaryOf.getOrDefault(screen, "")));
        }
        // 3) métodos: tela × sessão × bloco, em sequência
        List<Chunk> chunks = new ArrayList<>();
        Chunk open = null;
        for (int i = 0; i < steps.size(); i++) {
            ProjectContext.Step s = steps.get(i);
            String page = pageOfScreen.get(screenOf.get(i));
            // ler e agir viram métodos separados: "ler_resultado" e "clicar_sair", nunca um "ler_dados" que também sai
            boolean reads = s.action() == ActionType.OBSERVE;
            if (open == null || !open.page().equals(page) || open.session() != s.session() || !open.block().equals(s.blockId()) || open.reads() != reads) {
                open = new Chunk(page, s.session(), s.blockId(), reads, new ArrayList<>());
                chunks.add(open);
            }
            open.steps().add(s);
        }
        Map<String, Set<String>> methodsOf = new LinkedHashMap<>();
        Map<String, CodeNames.MethodInfo> methodInfo = new LinkedHashMap<>();
        Map<String, List<String>> blockTitlesOf = new LinkedHashMap<>();
        List<SingleConfig.Group> groups = new ArrayList<>();
        for (Chunk c : chunks) {
            String title = c.block().isEmpty() ? "" : blockTitle.getOrDefault(c.block(), "");
            if (!title.isEmpty()) blockTitlesOf.computeIfAbsent(c.page(), k -> new ArrayList<>()).add(title);
            // métodos que só LEEM ou CONFEREM dados são nomeados pelo que leem (o título do bloco pode descrever a seção toda);
            // os que AGEM na tela levam o verbo + objeto do título do bloco, quando o PDD o deu
            boolean readsOnly = c.steps().stream().allMatch(x -> x.action() == ActionType.OBSERVE);
            Optional<String> fromTitle = readsOnly ? Optional.empty() : CodeNaming.methodFromTitle(title);
            String base = fromTitle.orElseGet(() -> fromActions(a, c.steps()));
            Set<String> taken = methodsOf.computeIfAbsent(c.page(), k -> new HashSet<>());
            String method = CodeNaming.unique(CodeNaming.safe(base), taken, "_");
            methodInfo.put(c.page() + "." + method, new CodeNames.MethodInfo(title, fromTitle.isPresent() ? CodeNames.Source.PDD_BLOCK_TITLE
                    : CodeNames.Source.ACTIONS));
            groups.add(new SingleConfig.Group(c.page(), prefixOfPage.get(c.page()), method, c.steps().stream().map(x -> x.stepId().value().toString()).toList()));
        }
        Map<String, CodeNames.PageInfo> pages = new LinkedHashMap<>();
        pageInfo.forEach((cls, info) -> pages.put(cls, new CodeNames.PageInfo(info.title(), blockTitlesOf.getOrDefault(cls, List.of()), info.source(), info.boundary())));
        return new Result(groups, new CodeNames(pages, methodInfo, Map.of(), Set.of()), issues);
    }

    /**
     * Texto do PDD aproveitável como nome: REVISADO por um ator do Studio, exportável e não genérico. Texto derivado pelo
     * modelo determinístico do PDD ("Observar a tela") não é vocabulário aprovado.
     */
    private static boolean usable(DocText t) {
        return t != null && t.state() == DocText.State.REVIEWED && t.exportable() && !CodeNaming.genericTitle(t.value());
    }

    /** Nome pelo que as ações do grupo FAZEM (quando o PDD não deu um título de passo). */
    static String fromActions(Automation a, List<ProjectContext.Step> steps) {
        List<String> fills = new ArrayList<>();
        List<String> reads = new ArrayList<>();
        List<String> checks = new ArrayList<>();
        List<String> clicks = new ArrayList<>();
        boolean navigate = false;
        boolean submit = false;
        for (ProjectContext.Step s : steps) {
            AutomationStep st = a.step(s.stepId()).orElse(null);
            switch (s.action()) {
                case NAVIGATE -> navigate = true;
                case FILL -> {
                    Object v = st == null ? null : st.parameters().value().orElse(null);
                    if (v instanceof ValueSource.VariableReference r) fills.add(r.name());
                    else if (v instanceof ValueSource.CredentialReference c) fills.add(c.alias().substring(c.alias().lastIndexOf('.') + 1));
                    else fills.add(CodeNaming.snake(s.targetName(), 30));
                }
                case PRESS_KEY -> submit = true;
                case CLICK -> clicks.add(CodeNaming.snake(s.targetName(), 30));
                case OBSERVE -> {
                    String out = a.variables().stream().filter(x -> x.origin().kind() == VariableOrigin.Kind.STEP_OUTPUT && s.stepId().equals(x.origin().producer()))
                            .map(VariableDefinition::name).findFirst().orElse("");
                    if (st != null && st.parameters().value().orElse(null) instanceof ValueSource.VariableReference) checks.add(out);
                    else reads.add(out);
                }
                default -> {
                }
            }
        }
        if (navigate && fills.isEmpty() && reads.isEmpty() && clicks.isEmpty()) return "abrir_pagina_inicial";
        if (!fills.isEmpty() && reads.isEmpty() && checks.isEmpty()) {
            boolean login = fills.contains("senha");
            if (login) return "entrar";
            return (submit || !clicks.isEmpty() ? "enviar_" : "preencher_") + first(fills);
        }
        if (fills.isEmpty() && clicks.isEmpty() && !submit && reads.isEmpty() && checks.size() == 1) return "conferir_" + first(checks);
        if (fills.isEmpty() && clicks.isEmpty() && !submit && !reads.isEmpty() && checks.isEmpty()) {
            return reads.size() == 1 ? "ler_" + first(reads) : "ler_dados";
        }
        if (fills.isEmpty() && reads.isEmpty() && checks.isEmpty() && !clicks.isEmpty()) return "clicar_" + first(clicks);
        // ações misturadas sem título de passo no PDD: o nome diz o que entra e o que sai, sem inventar a intenção
        if (!fills.isEmpty() && (!reads.isEmpty() || !checks.isEmpty())) return "preencher_e_ler_" + first(reads.isEmpty() ? checks : reads);
        if (!reads.isEmpty() || !checks.isEmpty()) return "ler_dados";
        return "executar_etapas";
    }

    private static String first(List<String> names) {
        for (String x : names) if (!x.isEmpty()) return x;
        return "campo";
    }
}
