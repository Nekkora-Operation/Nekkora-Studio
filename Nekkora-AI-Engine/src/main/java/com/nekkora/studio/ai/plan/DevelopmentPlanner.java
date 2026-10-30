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
import com.nekkora.studio.pdd.DocumentDraft;
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
 *   <li>NEKKORA-E2E-002: Enter com efeito declarado; como cada Observar lê (texto, data por extenso, endereço), quais
 *       leituras são OPCIONAIS (só as de colunas com fonte alternativa declarada) e a conferência com a variável de
 *       entrada; com o fluxo "lista → planilha", as colunas na ordem das etapas, a chave (a coluna de endereço) e a
 *       execução em lista (configuração 1.2.0). Sem esses recursos, a 1.1.0 continua igual.</li>
 * </ul>
 */
public final class DevelopmentPlanner {
    private DevelopmentPlanner() {}

    public static final String PLANNER = "nekkora-studio/planner-deterministic/1.0.0";

    public static DevelopmentPlan plan(Automation a, ProjectContext ctx, PddGate.Result gate, FrameworkCapabilityCatalog cat, Optional<CodegenConfig> declaredTabular) {
        return plan(a, ctx, gate, cat, declaredTabular, Optional.empty());
    }

    /**
     * CODEGEN-QUALITY-001: com o rascunho do PDD, os nomes de Pages e métodos e as docstrings vêm dos títulos REVISADOS do
     * documento ({@link PageNamer}); sem ele, dos nomes seguros das sessões.
     */
    public static DevelopmentPlan plan(Automation a, ProjectContext ctx, PddGate.Result gate, FrameworkCapabilityCatalog cat, Optional<CodegenConfig> declaredTabular,
                                       Optional<DocumentDraft> draft) {
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
        // grupos: tela (por evidência) × sessão × bloco do PDD, em sequência; nomes pelo PDD (PageNamer)
        PageNamer.Result named = PageNamer.name(a, ctx, draft);
        List<SingleConfig.Group> groups = named.groups();
        Map<String, String> prefixOfPage = new LinkedHashMap<>();
        Map<String, List<String>> methodsOfPage = new LinkedHashMap<>();
        for (SingleConfig.Group g : groups) {
            prefixOfPage.putIfAbsent(g.page(), g.prefix());
            methodsOfPage.computeIfAbsent(g.page(), k -> new ArrayList<>()).add(g.method());
        }
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
            } else if (s.action() == ActionType.OBSERVE && (s.hasTarget() || modernReads(ctx, gate))) {
                Optional<VariableDefinition> v = a.variables().stream()
                        .filter(x -> x.origin().kind() == VariableOrigin.Kind.STEP_OUTPUT && s.stepId().equals(x.origin().producer())).findFirst();
                if (v.isPresent()) {
                    outputs.put(id, v.get().name());
                    variable = v.get().name();
                }
            } else if (s.action() == ActionType.CLICK || s.action() == ActionType.PRESS_KEY) {
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
            String boundary = named.names().page(e.getKey()).boundary();
            pages.add(new DevelopmentPlan.PlannedPage(e.getKey(), prefixOfPage.get(e.getKey()), e.getValue(), count,
                    boundary.isEmpty() ? "uma tela observada na gravação; intenções da tela por chave, sem seletores" : boundary));
        }
        // NEKKORA-E2E-002: leituras tipadas, Enter e planilha (configuração 1.2.0 só quando usados)
        Map<String, SingleConfig.Read> reads = new LinkedHashMap<>();
        Map<String, SingleConfig.KeyPress> keys = new LinkedHashMap<>();
        SingleConfig.Output output = SingleConfig.Output.json();
        boolean list = gate.spec().listToSpreadsheet();
        if (modernReads(ctx, gate)) {
            Map<StepId, StepId> fallbackOf = new LinkedHashMap<>(); // principal → alternativa
            Set<StepId> alternatives = new LinkedHashSet<>();
            if (list) {
                gate.spec().columns().forEach((step, r) -> {
                    if (r.value() != null && r.value().startsWith(com.nekkora.studio.ai.spec.FunctionalSpec.FALLBACK)) {
                        StepId primary = new StepId(java.util.UUID.fromString(r.value().substring(com.nekkora.studio.ai.spec.FunctionalSpec.FALLBACK.length())));
                        fallbackOf.put(primary, step);
                        alternatives.add(step);
                    }
                });
            }
            for (ProjectContext.Step s : ctx.steps()) {
                String id = s.stepId().value().toString();
                // o Enter "navega" quando a extensão viu a navegação OU quando a gravação registrou troca de documento depois dele
                // (o mesmo sinal que separa as telas): sem isso, a leitura seguinte (ex.: o endereço) corre contra a navegação
                // (CODEGEN-QUALITY-001 rodada 3, achado na execução real da Wikipédia)
                if (s.action() == ActionType.PRESS_KEY) keys.put(id, new SingleConfig.KeyPress("Enter", s.navigates() || s.screenChangeAfter()));
                if (s.action() != ActionType.OBSERVE || !outputs.containsKey(id)) continue;
                AutomationStep st = a.step(s.stepId()).orElseThrow();
                VariableDefinition out = a.variableNamed(outputs.get(id)).orElseThrow();
                String kind = !s.hasTarget() ? "address" : out.type() == com.nekkora.studio.domain.variable.VariableType.DATE ? "date" : "text";
                String expected = null;
                if (st.parameters().value().orElse(null) instanceof ValueSource.VariableReference ref) {
                    Optional<VariableDefinition> ev = a.variableNamed(ref.name());
                    if (ev.isPresent() && ev.get().origin().kind() == VariableOrigin.Kind.INPUT) {
                        expected = ref.name();
                        if (!inputs.contains(ref.name())) inputs.add(ref.name());
                    } else {
                        issues.add(QualityIssue.of("READ_EXPECTED_NOT_INPUT", Layer.V3_PLAN, QualityIssue.Kind.BLOCKER, s.stepId(), "Conferência",
                                "A conferência de um dado lido só compara com uma variável de ENTRADA.", "Ligue a etapa a uma variável de entrada ou retire o valor."));
                    }
                }
                // SPEC-OPTIONAL-COLUMNS-001: coluna opcional por decisão revisada → o alvo pode faltar (exists() → None);
                // ambíguo, identidade divergente e data inválida continuam erro
                boolean optionalColumn = list && gate.spec().optionalColumn(s.stepId());
                boolean optional = s.hasTarget() && (alternatives.contains(s.stepId()) || fallbackOf.containsKey(s.stepId()) || optionalColumn);
                reads.put(id, new SingleConfig.Read(kind, optional, expected));
            }
            if (list) {
                List<SingleConfig.Column> columns = new ArrayList<>();
                Set<String> fields = new LinkedHashSet<>();
                String keyField = null;
                for (ProjectContext.Step s : ctx.steps()) {
                    SpecResolver.Resolved<String> r = gate.spec().columns().get(s.stepId());
                    String id = s.stepId().value().toString();
                    if (r == null || r.value() == null || !r.value().startsWith(com.nekkora.studio.ai.spec.FunctionalSpec.COLUMN) || !outputs.containsKey(id)) continue;
                    String header = r.value().substring(com.nekkora.studio.ai.spec.FunctionalSpec.COLUMN.length());
                    // CODEGEN-QUALITY-001 rodada 3: o CABEÇALHO é o contrato da planilha (fica como está); o campo é o nome interno
                    // da linha, em inglês pelo glossário ("Nome completo" → full_name)
                    // rodada 4 (R4-02): coluna de DATA → o campo diz que é data ("Nascimento" → birth_date; o cabeçalho não muda)
                    SingleConfig.Read read = reads.get(id);
                    boolean date = read != null && "date".equals(read.kind());
                    String english = com.nekkora.studio.codegen.EnglishNames.noun(header, date).snake();
                    String fieldBase = english.isEmpty() || !Character.isLetter(english.charAt(0)) || english.length() > 50 ? VariableDeriver.slug(header) : english;
                    String field = fieldBase;
                    for (int n = 2; !fields.add(field); n++) field = fieldBase + "_" + n;
                    String kind = read == null ? "text" : switch (read.kind()) {
                        case "address" -> "url";
                        case "date" -> "iso_date";
                        default -> "text";
                    };
                    boolean required = !gate.spec().optionalColumn(s.stepId());
                    if ("url".equals(kind) && keyField == null && required) keyField = field;
                    StepId alt = fallbackOf.get(s.stepId());
                    String reason = required ? null : gate.spec().requirements().get(s.stepId()).value().substring(com.nekkora.studio.ai.spec.FunctionalSpec.OPTIONAL.length());
                    columns.add(new SingleConfig.Column(header, field, kind, id, alt == null ? null : alt.value().toString(), required, reason));
                }
                // a chave da planilha é a 1ª coluna de endereço (ou a 1ª obrigatória): nunca uma coluna opcional
                String fallbackKey = columns.stream().filter(SingleConfig.Column::required).map(SingleConfig.Column::field).findFirst().orElse(null);
                if (!columns.isEmpty() && (keyField != null || fallbackKey != null)) {
                    output = new SingleConfig.Output("xlsx", "Dados", columns, keyField == null ? fallbackKey : keyField);
                } else if (!columns.isEmpty()) {
                    issues.add(QualityIssue.of("PLAN_OUTPUT_NO_REQUIRED_KEY", Layer.V3_PLAN, QualityIssue.Kind.BLOCKER, null, "Planilha de saída",
                            "Todas as colunas da planilha são opcionais: nenhuma identifica a linha.", "Declare obrigatória a coluna que identifica cada item."));
                }
            }
        }
        SingleConfig cfg = null;
        boolean blocked = issues.stream().anyMatch(i -> i.kind().blocking());
        if (!blocked && !caps.environment().isEmpty()) {
            try {
                cfg = SingleConfig.of(processName(a), caps.environment(), ctx.origins(), gate.spec().startUrl().value(), groups, inputs, outputs, effects,
                        new SingleConfig.Pdd("PDD_READY", gate.record().processRevision(), gate.record().documentRevision(), gate.record().specRevision()),
                        list && !output.columns().isEmpty() ? "list" : "single", reads, keys, output);
            } catch (CodegenConfig.Invalid e) {
                issues.add(QualityIssue.of("PLAN_CONFIG_INVALID", Layer.V3_PLAN, QualityIssue.Kind.BLOCKER, null, "Configuração derivada",
                        "A configuração derivada foi recusada pelo contrato do gerador (" + e.code() + ").", "Valide a automação e veja os apontamentos."));
            }
        }
        List<String> used = new ArrayList<>(caps.used());
        if (!reads.isEmpty() || !keys.isEmpty()) {
            if (!keys.isEmpty()) used.add("actions.press");
            if (reads.values().stream().anyMatch(r -> !"address".equals(r.kind()))) used.addAll(List.of("actions.read_items", "text.join_items"));
            if (reads.values().stream().anyMatch(r -> "date".equals(r.kind()))) used.add("text.parse_long_date");
            if (reads.values().stream().anyMatch(r -> "address".equals(r.kind()))) used.add("actions.current_address");
            if (reads.values().stream().anyMatch(r -> r.expected() != null)) used.addAll(List.of("text.same_address", "text.normalize_text"));
            if (reads.values().stream().anyMatch(SingleConfig.Read::optional)) used.add("actions.exists");
            if (!output.columns().isEmpty()) used.addAll(List.of("tabular.read", "tabular.input", "xlsx.schema", "xlsx.result_sink", "errors.business_rule_rejected"));
            used.removeIf(id -> cat.find(id).isEmpty());
        }
        boolean listed = cfg != null && cfg.list();
        issues.addAll(named.issues());
        return new DevelopmentPlan(listed ? DevelopmentPlan.ExecutionModel.LIST : DevelopmentPlan.ExecutionModel.SINGLE, listed ? "uma transação por linha da lista de entrada; planilha de saída (tudo ou nada)"
                : "nenhuma configuração tabular declarada: os dados vêm de uma entrada única", vars, pages, steps,
                new ArrayList<>(new LinkedHashSet<>(used)), cfg, null, issues, PLANNER, named.names());
    }

    /**
     * Precisa da configuração 1.2.0? Lista → planilha, Enter, Observar sem alvo (endereço) ou com conferência, ou leitura
     * de DATA. Sem nada disso, a 1.1.0 (e o código) continuam idênticos.
     */
    static boolean modernReads(ProjectContext ctx, PddGate.Result gate) {
        if (gate.spec() != null && gate.spec().listToSpreadsheet()) return true;
        for (ProjectContext.Step s : ctx.steps()) {
            if (s.action() == ActionType.PRESS_KEY) return true;
            if (s.action() == ActionType.OBSERVE && (!s.hasTarget() || s.value() == ProjectContext.ValueKind.VARIABLE || !s.anchor().isEmpty())) return true;
        }
        return false;
    }

    /** Nome técnico do processo (token do framework) a partir do nome da automação. */
    public static String processName(Automation a) {
        String s = VariableDeriver.slug(a.name());
        return s.length() > 60 ? s.substring(0, 60).replaceAll("_+$", "") : s;
    }

    /** Etapas do plano por id (para a UI). */
    public static Map<StepId, DevelopmentPlan.StepPlan> byStep(DevelopmentPlan p) {
        Map<StepId, DevelopmentPlan.StepPlan> out = new LinkedHashMap<>();
        p.steps().forEach(s -> out.put(s.step(), s));
        return out;
    }
}
