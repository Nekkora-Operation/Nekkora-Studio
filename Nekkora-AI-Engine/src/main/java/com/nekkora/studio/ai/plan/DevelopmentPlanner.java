package com.nekkora.studio.ai.plan;

import com.nekkora.studio.ai.capability.CapabilityCheck;
import com.nekkora.studio.ai.capability.FrameworkCapabilityCatalog;
import com.nekkora.studio.ai.context.ProjectContext;
import com.nekkora.studio.ai.gate.PddGate;
import com.nekkora.studio.ai.quality.Layer;
import com.nekkora.studio.ai.quality.QualityIssue;
import com.nekkora.studio.ai.spec.EffectVocabulary;
import com.nekkora.studio.ai.spec.SpecResolver;
import com.nekkora.studio.codegen.CodegenConfig;
import com.nekkora.studio.codegen.SingleConfig;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.variable.VariableDefinition;
import com.nekkora.studio.domain.variable.VariableOrigin;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * STUDIO-014 — DevelopmentPlanner determinístico (o fluxo SEM IA; a IA, quando autorizada, só propõe e o
 * {@link PlanVerifier} confere). Exige o gate {@code PDD_READY}. Decide:
 * <ul>
 *   <li>o modelo de execução: lote tabular só com a configuração tabular DECLARADA do projeto; senão, execução única
 *       (nada de planilha, {@code perRecord} ou colunas fabricadas);</li>
 *   <li>as Pages: uma por TELA observada (resumo opaco do endereço e do título), na ordem do modelo; um método por grupo
 *       de etapas consecutivas da tela — nenhuma classe artificial por sessão;</li>
 *   <li>entradas, saídas e credenciais a partir das ligações do MODELO (as variáveis derivadas já aplicadas);</li>
 *   <li>efeito e confirmação de cada clique a partir da especificação resolvida pelo gate.</li>
 * </ul>
 */
public final class DevelopmentPlanner {
    private DevelopmentPlanner() {}

    public static final String PLANNER = "nekkora-studio/planner-deterministic/1.0.0";

    public static DevelopmentPlan plan(Automation a, ProjectContext ctx, PddGate.Result gate, FrameworkCapabilityCatalog cat, Optional<CodegenConfig> declaredTabular) {
        List<QualityIssue> issues = new ArrayList<>();
        List<VariableDeriver.Planned> vars = VariableDeriver.derive(a, ctx);
        if (!gate.ready()) {
            issues.add(QualityIssue.of("PDD_NOT_READY", Layer.V3_PLAN, QualityIssue.Kind.BLOCKER, null, "PDD",
                    "O plano só é montado com o PDD pronto (PDD_READY) para a revisão atual.", "Resolva as pendências do PDD."));
            return new DevelopmentPlan(declaredTabular.isPresent() ? DevelopmentPlan.ExecutionModel.TABULAR : DevelopmentPlan.ExecutionModel.SINGLE, "",
                    vars, List.of(), List.of(), List.of(), null, null, issues, PLANNER);
        }
        CapabilityCheck.Result caps = CapabilityCheck.check(a, ctx, cat, declaredTabular);
        if (declaredTabular.isPresent()) {
            return new DevelopmentPlan(DevelopmentPlan.ExecutionModel.TABULAR, "configuração tabular declarada no projeto (" + declaredTabular.get().revision() + ")",
                    vars, List.of(), List.of(), caps.used(), null, declaredTabular.get(), caps.issues(), PLANNER);
        }
        issues.addAll(caps.issues());
        for (VariableDeriver.Planned p : vars) {
            if (p.changesModel()) {
                issues.add(new QualityIssue("VARIABLES_NOT_APPLIED", Layer.V3_PLAN, QualityIssue.Kind.AUTO_FIX_SAFE, p.step(), null, "Variáveis",
                        "Há variáveis derivadas ainda não registradas no modelo.", "Aplicar registra todas numa operação desfazível (Ctrl+Z).", null,
                        new QualityIssue.Fix("APPLY_DERIVED_VARIABLES", "Registrar as variáveis derivadas", "etapas sem origem de valor",
                                "variáveis de entrada, saídas e credenciais por apelido"), null));
                break;
            }
        }
        for (VariableDeriver.Planned p : vars) {
            if (p.needsReview()) {
                issues.add(QualityIssue.of("VARIABLE_NAME_GENERIC", Layer.V3_PLAN, QualityIssue.Kind.WARNING, p.step(), "Variável " + p.name(),
                        "O campo não tem rótulo seguro: o nome da variável é provisório.", "Renomeie em Variáveis e dependências (Ctrl+Shift+B)."));
            }
        }
        // Observar logo depois de um clique na MESMA tela: o código lê assim que o alvo existir (o 0.2.0 não tem "esperar o
        // texto mudar"); se o elemento já existir antes do resultado, a leitura pode pegar o texto anterior — declarado.
        for (int i = 1; i < ctx.steps().size(); i++) {
            ProjectContext.Step prev = ctx.steps().get(i - 1);
            ProjectContext.Step s = ctx.steps().get(i);
            if (s.action() == ActionType.OBSERVE && s.hasTarget() && prev.action() == ActionType.CLICK && prev.screenKey().equals(s.screenKey())) {
                issues.add(QualityIssue.of("OBSERVE_AFTER_ACTION_SAME_SCREEN", Layer.V3_PLAN, QualityIssue.Kind.WARNING, s.stepId(),
                        "Sessão " + s.session() + " · Etapa " + String.format("%02d", s.number()),
                        "A leitura acontece assim que o alvo existir. Se ele já estiver na tela antes de o resultado chegar, o texto lido pode ser o anterior.",
                        "Confira no laboratório que o resultado só aparece depois da ação; o nekkora_rpa 0.2.0 não tem a espera \"texto mudou\".")
                        .withCapability("actions.read_text"));
            }
        }
        // grupos: tela (ou sessão sem tela conhecida) × sessão, em sequência
        Map<String, String> pageOfScreen = new LinkedHashMap<>();
        Map<String, String> prefixOfPage = new LinkedHashMap<>();
        Map<String, List<String>> methodsOfPage = new LinkedHashMap<>();
        List<SingleConfig.Group> groups = new ArrayList<>();
        List<String> current = null;
        String currentKey = null;
        String currentPage = null;
        String currentMethod = null;
        for (ProjectContext.Step s : ctx.steps()) {
            String screen = s.screenKey().isEmpty() ? "sessao-" + s.session() : s.screenKey();
            String key = screen + "|" + s.session();
            if (!key.equals(currentKey)) {
                if (current != null) groups.add(new SingleConfig.Group(currentPage, prefixOfPage.get(currentPage), currentMethod, current));
                currentPage = pageOfScreen.computeIfAbsent(screen, k -> pageName(ctx, s.session(), pageOfScreen.values()));
                String page = currentPage;
                prefixOfPage.computeIfAbsent(page, k -> prefix(page, prefixOfPage.values()));
                List<String> methods = methodsOfPage.computeIfAbsent(page, k -> new ArrayList<>());
                currentMethod = unique(methodName(ctx, s.session()), methods);
                current = new ArrayList<>();
                currentKey = key;
            }
            current.add(s.stepId().value().toString());
        }
        if (current != null) groups.add(new SingleConfig.Group(currentPage, prefixOfPage.get(currentPage), currentMethod, current));
        // entradas, saídas e efeitos a partir do MODELO e da especificação
        List<String> inputs = new ArrayList<>();
        Map<String, String> outputs = new LinkedHashMap<>();
        Map<String, SingleConfig.Effect> effects = new LinkedHashMap<>();
        List<DevelopmentPlan.StepPlan> steps = new ArrayList<>();
        Map<String, String> pageOfStep = new LinkedHashMap<>();
        Map<String, String> methodOfStep = new LinkedHashMap<>();
        for (SingleConfig.Group g : groups) for (String id : g.steps()) {
            pageOfStep.put(id, g.page());
            methodOfStep.put(id, g.method());
        }
        for (ProjectContext.Step s : ctx.steps()) {
            String id = s.stepId().value().toString();
            AutomationStep st = a.step(s.stepId()).orElseThrow();
            String variable = null;
            String effect = null;
            String post = null;
            if (s.action() == ActionType.FILL && st.parameters().value().orElse(null) instanceof ValueSource.VariableReference r) {
                variable = r.name();
                Optional<VariableDefinition> v = a.variableNamed(r.name());
                if (v.isPresent() && v.get().origin().kind() == VariableOrigin.Kind.INPUT && !inputs.contains(r.name())) inputs.add(r.name());
            } else if (s.action() == ActionType.FILL && st.parameters().value().orElse(null) instanceof ValueSource.CredentialReference c) {
                variable = c.alias();
            } else if (s.action() == ActionType.OBSERVE && s.hasTarget()) {
                Optional<VariableDefinition> v = a.variables().stream()
                        .filter(x -> x.origin().kind() == VariableOrigin.Kind.STEP_OUTPUT && s.stepId().equals(x.origin().producer())).findFirst();
                if (v.isPresent()) {
                    outputs.put(id, v.get().name());
                    variable = v.get().name();
                }
            } else if (s.action() == ActionType.CLICK) {
                SpecResolver.Resolved<EffectVocabulary.Effect> e = gate.spec().effects().get(s.stepId());
                if (e != null && e.known()) {
                    effect = e.value().name();
                    SpecResolver.Resolved<String> pc = gate.spec().postconditions().get(s.stepId());
                    post = e.value() == EffectVocabulary.Effect.EXTERNAL && pc != null && pc.known() ? pc.value() : null;
                    if (e.value() == EffectVocabulary.Effect.NONE || post != null) effects.put(id, new SingleConfig.Effect(effect, post));
                }
            }
            String capability = cat.forAction(s.action()).stream().map(FrameworkCapabilityCatalog.Capability::id).findFirst().orElse(null);
            steps.add(new DevelopmentPlan.StepPlan(s.stepId(), pageOfStep.get(id), methodOfStep.get(id), capability, variable, effect, post));
        }
        List<DevelopmentPlan.PlannedPage> pages = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : methodsOfPage.entrySet()) {
            int count = (int) groups.stream().filter(g -> g.page().equals(e.getKey())).mapToLong(g -> g.steps().size()).sum();
            pages.add(new DevelopmentPlan.PlannedPage(e.getKey(), prefixOfPage.get(e.getKey()), e.getValue(), count,
                    "uma tela observada na gravação; intenções da tela por chave, sem seletores"));
        }
        SingleConfig cfg = null;
        boolean blocked = issues.stream().anyMatch(i -> i.kind().blocking());
        if (!blocked && !caps.environment().isEmpty()) {
            try {
                cfg = SingleConfig.of(processName(a), caps.environment(), ctx.origins(), gate.spec().startUrl().value(), groups, inputs, outputs, effects,
                        new SingleConfig.Pdd("PDD_READY", gate.record().processRevision(), gate.record().documentRevision(), gate.record().specRevision()));
            } catch (CodegenConfig.Invalid e) {
                issues.add(QualityIssue.of("PLAN_CONFIG_INVALID", Layer.V3_PLAN, QualityIssue.Kind.BLOCKER, null, "Configuração derivada",
                        "A configuração derivada foi recusada pelo contrato do gerador (" + e.code() + ").", "Valide a automação e veja os apontamentos."));
            }
        }
        return new DevelopmentPlan(DevelopmentPlan.ExecutionModel.SINGLE, "nenhuma configuração tabular declarada: os dados vêm de uma entrada única",
                vars, pages, steps, caps.used(), cfg, null, issues, PLANNER);
    }

    /** Nome técnico do processo (token do framework) a partir do nome da automação. */
    public static String processName(Automation a) {
        String s = VariableDeriver.slug(a.name());
        return s.length() > 60 ? s.substring(0, 60).replaceAll("_+$", "") : s;
    }

    private static String pageName(ProjectContext ctx, int session, java.util.Collection<String> taken) {
        String name = ctx.sessionNames().size() >= session ? ctx.sessionNames().get(session - 1) : "";
        String camel = camel(name);
        String base = camel.isEmpty() ? "Tela" + session : "Tela" + camel;
        if (base.length() > 36) base = base.substring(0, 36);
        String out = base;
        int i = 2;
        while (taken.contains(out)) out = base + i++;
        return out;
    }

    private static String prefix(String page, java.util.Collection<String> taken) {
        String base = page.replaceFirst("^Tela", "").replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
        if (base.isEmpty() || !Character.isLetter(base.charAt(0))) base = "tela_" + base;
        String out = base;
        int i = 2;
        while (taken.contains(out)) out = base + "_" + i++;
        return out;
    }

    private static String methodName(ProjectContext ctx, int session) {
        String name = ctx.sessionNames().size() >= session ? ctx.sessionNames().get(session - 1) : "";
        String s = VariableDeriver.slug(name);
        return name.isBlank() || s.startsWith("campo_") ? "etapas_sessao_" + session : s;
    }

    private static String unique(String base, List<String> taken) {
        String out = base;
        int i = 2;
        while (taken.contains(out)) out = base + "_" + i++;
        taken.add(out);
        return out;
    }

    private static String camel(String text) {
        StringBuilder b = new StringBuilder();
        for (String w : VariableDeriver.words(text)) {
            if (w.isEmpty() || !Character.isLetterOrDigit(w.charAt(0))) continue;
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        String out = b.toString();
        return out.isEmpty() || !Character.isLetter(out.charAt(0)) ? "" : out;
    }

    /** Etapas do plano por id (para a UI). */
    public static Map<StepId, DevelopmentPlan.StepPlan> byStep(DevelopmentPlan p) {
        Map<StepId, DevelopmentPlan.StepPlan> out = new LinkedHashMap<>();
        p.steps().forEach(s -> out.put(s.step(), s));
        return out;
    }
}
