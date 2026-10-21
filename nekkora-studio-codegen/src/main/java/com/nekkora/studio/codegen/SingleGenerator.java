package com.nekkora.studio.codegen;

import com.nekkora.studio.codegen.PythonGenerator.Blocker;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.target.TargetDecision;
import com.nekkora.studio.domain.variable.VariableDefinition;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.guided.GuidedPlan;
import com.nekkora.studio.validation.Finding;
import com.nekkora.studio.validation.Severity;
import com.nekkora.studio.validation.ValidationReport;
import com.nekkora.studio.validation.ValidationStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * STUDIO-014 — gerador oficial para processo de EXECUÇÃO ÚNICA (sem planilha), configuração {@link SingleConfig} 1.1.0.
 * Mesmo recorte e mesmas recusas do {@link PythonGenerator} (ações suportadas, alvo confirmado no debug guiado,
 * localizador validado e não posicional, sem iframe, sem valor gravado no código), mais:
 * <ul>
 *   <li>toda etapa do modelo está em exatamente um grupo (Page/método) e na ordem do modelo;</li>
 *   <li>todo preenchimento vem de variável de ENTRADA listada ou de credencial por apelido;</li>
 *   <li>todo clique tem o efeito declarado; efeito externo só com confirmação observável (sem repetição);</li>
 *   <li>todo Observar com alvo produz a variável SAÍDA DE ETAPA declarada para ele;</li>
 *   <li>a configuração carrega o gate {@code PDD_READY} e as revisões do PDD (o gerador recusa sem eles).</li>
 * </ul>
 * Saída determinística: mesma entrada → mesmos bytes.
 *
 * <p>NEKKORA-E2E-002 (configuração 1.2.0): Pressionar Enter (efeito declarado, confirmação pelo endereço quando a gravação
 * viu a página mudar), Observar sem alvo (endereço da página), leitura tipada (texto/itens, data por extenso), leitura
 * OPCIONAL só onde há fonte alternativa declarada, conferência com uma variável de entrada, lista de entrada e planilha de
 * saída pelo writer do framework. Sem esses recursos, nada muda.
 */
public final class SingleGenerator {

    public static final String GENERATOR = "nekkora-studio-codegen/1.1.0";
    public static final String GENERATOR_LIST = "nekkora-studio-codegen/1.2.0";
    public static final String FRAMEWORK_REQUIREMENT = "nekkora-rpa==0.2.0";
    /** 1.2.0: Enter, leitura tipada e planilha precisam do framework 0.3.0. */
    public static final String FRAMEWORK_REQUIREMENT_LIST = "nekkora-rpa[excel]==0.3.0";
    static final Set<ActionType> SUPPORTED = Set.of(ActionType.NAVIGATE, ActionType.FILL, ActionType.CLICK, ActionType.WAIT_FOR, ActionType.OBSERVE);
    static final Set<ActionType> SUPPORTED_MODERN = Set.of(ActionType.NAVIGATE, ActionType.FILL, ActionType.CLICK, ActionType.WAIT_FOR, ActionType.OBSERVE,
            ActionType.PRESS_KEY);

    /** Etapa pronta para gerar. */
    record Step(AutomationStep step, SingleConfig.Group group, String key, Map<String, Object> target, String input, String credential, String output,
                String navigateUrl, SingleConfig.Effect effect, String nextKey, String firstFillKey, SingleConfig.Read read, SingleConfig.KeyPress press,
                String label, boolean nextOnOtherScreen) {}

    private SingleGenerator() {}

    public static PythonGenerator.Result generate(Automation a, ValidationReport report, SingleConfig cfg) {
        return generate(a, report, cfg, CodeNames.none());
    }

    /** CODEGEN-QUALITY-001: com a evidência de nomes do PDD e o registro de rastreio da automação. */
    public static PythonGenerator.Result generate(Automation a, ValidationReport report, SingleConfig cfg, CodeNames names) {
        GuidedPlan plan = GuidedPlan.of(a, report);
        List<Blocker> blockers = new ArrayList<>();
        if (report.status() == ValidationStatus.INCOMPLETE) {
            for (Finding f : report.findings()) if (f.severity() == Severity.BLOCKER) blockers.add(new Blocker("STATIC_" + f.code().name(), f.step()));
            if (blockers.isEmpty()) blockers.add(new Blocker("STATIC_BLOCKERS", null));
        }
        // ordem do modelo = ordem dos grupos
        List<AutomationStep> modelOrder = new ArrayList<>();
        a.sessions().forEach(s -> modelOrder.addAll(s.steps()));
        Map<String, Integer> sessionOf = new LinkedHashMap<>();
        int sessionNumber = 0;
        for (var session : a.sessions()) {
            sessionNumber++;
            for (AutomationStep st : session.steps()) sessionOf.put(st.id().value().toString(), sessionNumber);
        }
        List<String> configOrder = new ArrayList<>();
        Map<String, SingleConfig.Group> groupOf = new LinkedHashMap<>();
        for (SingleConfig.Group g : cfg.groups()) for (String id : g.steps()) {
            configOrder.add(id);
            groupOf.put(id, g);
        }
        List<String> model = modelOrder.stream().map(s -> s.id().value().toString()).toList();
        if (!model.equals(configOrder)) {
            blockers.add(new Blocker(new LinkedHashSet<>(model).equals(new LinkedHashSet<>(configOrder)) ? "CONFIG_STEP_ORDER" : "CONFIG_STEP_COVERAGE", null));
            return new PythonGenerator.Result(List.copyOf(blockers), Map.of(), plan.planRevision());
        }
        for (String v : cfg.inputs()) {
            Optional<VariableDefinition> d = a.variableNamed(v);
            if (d.isEmpty()) blockers.add(new Blocker("INPUT_VARIABLE_MISSING", null));
            else if (d.get().origin().kind() != VariableOrigin.Kind.INPUT) blockers.add(new Blocker("INPUT_VARIABLE_NOT_INPUT", null));
        }
        Set<ActionType> supported = cfg.modern() ? SUPPORTED_MODERN : SUPPORTED;
        // chaves e alvos primeiro (o envio usa a chave da etapa seguinte / do 1º campo do grupo)
        Map<String, String> keyOf = new LinkedHashMap<>();
        Map<String, Map<String, Object>> targetOf = new LinkedHashMap<>();
        Set<String> used = new LinkedHashSet<>();
        Map<String, String> keyOfTarget = new LinkedHashMap<>(); // mesmo elemento (mesma identidade e localizador) na mesma tela → mesma chave
        for (AutomationStep s : modelOrder) {
            String id = s.id().value().toString();
            if (!supported.contains(s.actionType())) {
                blockers.add(new Blocker("UNSUPPORTED_ACTION_" + s.actionType().name(), s.id()));
                continue;
            }
            if (s.actionType() == ActionType.NAVIGATE) continue;
            Optional<ElementReference> el = s.element();
            SingleConfig.Read declared = cfg.reads().get(id);
            if (el.isEmpty() && s.actionType() == ActionType.OBSERVE && declared != null && "address".equals(declared.kind())) continue; // endereço da página
            if (el.isPresent() && s.actionType() == ActionType.OBSERVE && declared != null && "address".equals(declared.kind())) {
                blockers.add(new Blocker("ADDRESS_READ_WITH_TARGET", s.id()));
                continue;
            }
            if (el.isEmpty()) {
                blockers.add(new Blocker(s.actionType() == ActionType.OBSERVE ? "OBSERVE_WITHOUT_TARGET" : s.actionType() == ActionType.WAIT_FOR
                        ? "WAIT_WITHOUT_TARGET" : "TARGET_MISSING", s.id()));
                continue;
            }
            Optional<TargetDecision> d = s.targetDecision();
            if (d.isEmpty() || !d.get().confirmed()) {
                blockers.add(new Blocker("TARGET_NOT_CONFIRMED", s.id()));
                continue;
            }
            Map<String, Object> t = PythonGenerator.target(el.get(), s.id(), blockers);
            if (t == null) continue;
            if (cfg.modern() && s.actionType() == ActionType.OBSERVE && el.get().preferredLocator().map(c -> c.strategy() == LocatorStrategy.TEXT).orElse(false)) {
                // ler um dado localizando-o pelo PRÓPRIO texto é circular: na página seguinte o texto é outro
                blockers.add(new Blocker("READ_LOCATED_BY_ITS_OWN_TEXT", s.id()));
                continue;
            }
            SingleConfig.Group g = groupOf.get(id);
            String same = g.prefix() + "|" + identityOf(t);
            String k = keyOfTarget.get(same);
            if (k == null) {
                k = key(g.prefix(), el.get(), s, used, cfg.reads().get(id) != null && "date".equals(cfg.reads().get(id).kind()));
                keyOfTarget.put(same, k);
                targetOf.put(id, t);
            }
            keyOf.put(id, k);
        }
        List<Step> steps = new ArrayList<>();
        Set<String> inputsUsed = new LinkedHashSet<>();
        Set<String> outputsUsed = new LinkedHashSet<>();
        for (int i = 0; i < modelOrder.size(); i++) {
            AutomationStep s = modelOrder.get(i);
            String id = s.id().value().toString();
            SingleConfig.Group g = groupOf.get(id);
            if (!supported.contains(s.actionType())) continue;
            Optional<ValueSource> value = s.parameters().value();
            SingleConfig.Read read = null;
            SingleConfig.KeyPress press = null;
            String input = null;
            String credential = null;
            String output = null;
            String url = null;
            SingleConfig.Effect effect = null;
            String next = null;
            String firstFill = null;
            switch (s.actionType()) {
                case NAVIGATE -> {
                    if (value.isPresent() && value.get() instanceof ValueSource.LiteralValue lit && allowed(cfg, lit.text())) url = lit.text();
                    else blockers.add(new Blocker("NAVIGATE_URL_NOT_ALLOWED", s.id()));
                }
                case FILL -> {
                    if (value.isEmpty()) blockers.add(new Blocker("FILL_WITHOUT_VALUE", s.id()));
                    else if (value.get() instanceof ValueSource.VariableReference ref) {
                        if (!cfg.inputs().contains(ref.name())) blockers.add(new Blocker("VARIABLE_NOT_INPUT", s.id()));
                        else input = ref.name();
                        inputsUsed.add(ref.name());
                    } else if (value.get() instanceof ValueSource.CredentialReference cred) {
                        credential = cred.alias();
                        if (!credential.matches("[a-z][a-z0-9_.]{0,59}")) blockers.add(new Blocker("CREDENTIAL_ALIAS_INVALID", s.id()));
                    } else {
                        blockers.add(new Blocker("LITERAL_VALUE_NOT_GENERATED", s.id()));
                    }
                }
                case CLICK -> {
                    effect = cfg.effects().get(id);
                    if (effect == null) {
                        blockers.add(new Blocker("EFFECT_NOT_DECLARED", s.id()));
                    } else {
                        next = nextKey(modelOrder, i, keyOf);
                        if ("NEXT_TARGET_VISIBLE".equals(effect.postcondition()) && next == null) blockers.add(new Blocker("POSTCONDITION_WITHOUT_NEXT_TARGET", s.id()));
                        if ("FIRST_FIELD_REPLACED".equals(effect.postcondition())) {
                            firstFill = firstFillKey(g, a, keyOf);
                            if (firstFill == null) blockers.add(new Blocker("POSTCONDITION_WITHOUT_FIELD", s.id()));
                        }
                    }
                }
                case OBSERVE -> {
                    output = cfg.outputs().get(id);
                    if (output == null) {
                        blockers.add(new Blocker("OBSERVE_WITHOUT_OUTPUT", s.id()));
                    } else {
                        Optional<VariableDefinition> v = a.variableNamed(output);
                        if (v.isEmpty() || v.get().origin().kind() != VariableOrigin.Kind.STEP_OUTPUT || !s.id().equals(v.get().origin().producer())) {
                            blockers.add(new Blocker("OUTPUT_VARIABLE_NOT_DECLARED", s.id()));
                        }
                        outputsUsed.add(id);
                    }
                    if (cfg.modern()) {
                        read = cfg.reads().get(id);
                        if (read == null) blockers.add(new Blocker("READ_NOT_DECLARED", s.id()));
                        else if (read.expected() != null) {
                            if (!(value.orElse(null) instanceof ValueSource.VariableReference ref) || !ref.name().equals(read.expected())) {
                                blockers.add(new Blocker("READ_EXPECTED_MISMATCH", s.id()));
                            }
                            inputsUsed.add(read.expected());
                        }
                    }
                }
                case PRESS_KEY -> {
                    effect = cfg.effects().get(id);
                    press = cfg.keys().get(id);
                    if (press == null || !(value.orElse(null) instanceof ValueSource.LiteralValue lit && "Enter".equals(lit.text()))) {
                        blockers.add(new Blocker("KEY_NOT_ENTER", s.id()));
                    } else if (effect == null) {
                        blockers.add(new Blocker("EFFECT_NOT_DECLARED", s.id()));
                    } else {
                        next = nextKey(modelOrder, i, keyOf);
                        if ("NEXT_TARGET_VISIBLE".equals(effect.postcondition()) && next == null) blockers.add(new Blocker("POSTCONDITION_WITHOUT_NEXT_TARGET", s.id()));
                        if ("FIRST_FIELD_REPLACED".equals(effect.postcondition())) {
                            firstFill = firstFillKey(g, a, keyOf);
                            if (firstFill == null) blockers.add(new Blocker("POSTCONDITION_WITHOUT_FIELD", s.id()));
                        }
                    }
                }
                default -> {
                }
            }
            steps.add(new Step(s, g, keyOf.get(id), targetOf.get(id), input, credential, output, url, effect, next, firstFill, read, press, label(s, input, credential,
                    output, read), next != null && !sameRecordedScreen(s, nextTargeted(modelOrder, i, keyOf), sessionOf)));
        }
        for (String v : cfg.inputs()) if (!inputsUsed.contains(v)) blockers.add(new Blocker("INPUT_NOT_USED", null));
        for (String st : cfg.outputs().keySet()) if (!outputsUsed.contains(st)) blockers.add(new Blocker("OUTPUT_STEP_NOT_OBSERVE", null));
        for (String st : cfg.effects().keySet()) {
            boolean click = modelOrder.stream().anyMatch(x -> x.id().value().toString().equals(st)
                    && (x.actionType() == ActionType.CLICK || cfg.modern() && x.actionType() == ActionType.PRESS_KEY));
            if (!click) blockers.add(new Blocker("EFFECT_STEP_NOT_CLICK", null));
        }
        for (String st : cfg.reads().keySet()) if (!outputsUsed.contains(st)) blockers.add(new Blocker("READ_STEP_NOT_OBSERVE", null));
        if (!blockers.isEmpty()) return new PythonGenerator.Result(List.copyOf(blockers), Map.of(), plan.planRevision());
        return new PythonGenerator.Result(List.of(), SingleEmitter.emit(a, plan, cfg, steps, names), plan.planRevision());
    }

    static boolean allowed(SingleConfig cfg, String url) {
        return url != null && cfg.allowedOrigins().stream().anyMatch(o -> url.equals(o) || url.startsWith(o + "/"));
    }

    private static AutomationStep nextTargeted(List<AutomationStep> order, int i, Map<String, String> keyOf) {
        for (int j = i + 1; j < order.size(); j++) if (keyOf.containsKey(order.get(j).id().value().toString())) return order.get(j);
        return null;
    }

    /**
     * CODEGEN-QUALITY-001: a confirmação "o próximo alvo existe" de um clique/Enter SEM efeito continua decidida pela
     * TELA GRAVADA (modelo de endereço + título; sem eles, a sessão) — a mesma regra de antes da divisão das Pages por
     * troca de documento. Assim, separar a busca e a ficha em duas classes não acrescenta nem retira pós-condição.
     */
    static boolean sameRecordedScreen(AutomationStep a, AutomationStep b, Map<String, Integer> sessionOf) {
        if (b == null) return true;
        String sa = recordedScreen(a, sessionOf);
        String sb = recordedScreen(b, sessionOf);
        return sa.equals(sb);
    }

    private static String recordedScreen(AutomationStep s, Map<String, Integer> sessionOf) {
        String l = s.context().map(c -> c.location() == null ? "" : c.location()).orElse("");
        String t = s.context().map(c -> c.screenTitle() == null ? "" : c.screenTitle()).orElse("");
        return l.isEmpty() && t.isEmpty() ? "sessao-" + sessionOf.get(s.id().value().toString()) : l + "\n" + t;
    }

    private static String nextKey(List<AutomationStep> order, int i, Map<String, String> keyOf) {
        for (int j = i + 1; j < order.size(); j++) {
            String k = keyOf.get(order.get(j).id().value().toString());
            if (k != null) return k;
        }
        return null;
    }

    private static String firstFillKey(SingleConfig.Group g, Automation a, Map<String, String> keyOf) {
        for (String id : g.steps()) {
            AutomationStep s = a.step(new StepId(java.util.UUID.fromString(id))).orElse(null);
            if (s != null && s.actionType() == ActionType.FILL && keyOf.containsKey(id)) return keyOf.get(id);
        }
        return null;
    }

    /**
     * CODEGEN-QUALITY-001: como a etapa é CHAMADA nas docstrings — o texto de INTERFACE que identifica o alvo (rótulo,
     * âncora da linha, nome acessível), exatamente como está na página (não é traduzido). Vazio sem esse texto: a
     * docstring diz o papel em inglês ("the page address", "the title", "the field"). Nunca valores.
     */
    static String label(AutomationStep s, String input, String credential, String output, SingleConfig.Read read) {
        if (read != null && "address".equals(read.kind())) return "";
        ElementReference el = s.element().orElse(null);
        if (el != null) {
            for (LocatorCandidate c : el.locators()) {
                if (c.strategy() == LocatorStrategy.LABEL && PythonGenerator.labelText(c.expression()) != null) return PythonGenerator.labelText(c.expression());
            }
            String anchor = PythonGenerator.anchorText(el);
            if (anchor != null) return anchor;
            if (el.accessibleName() != null && !el.accessibleName().isBlank()) return el.accessibleName().strip();
        }
        return "";
    }

    /** Identidade do alvo no registro, sem a procedência: dois passos sobre o MESMO elemento compartilham a chave. */
    private static String identityOf(Map<String, Object> target) {
        Map<String, Object> t = new LinkedHashMap<>(target);
        t.remove("provenance");
        return Pretty.json(t);
    }

    /**
     * Chave estável e legível do alvo: {@code process.<prefixo da Page>.<nome>} — o nome vem, nesta ordem, do rótulo
     * associado, da âncora da linha (th/dt), do nome acessível, da variável de entrada do preenchimento
     * ("campo &lt;variável&gt;"), do cabeçalho lido ("título") ou, sem evidência, de "alvo" (provisório, apontado pelo
     * Quality Gate) — e vira inglês pelo glossário governado ({@link EnglishNames}: "Nome completo" → {@code full_name},
     * "campo termo_de_busca" → {@code search_term_field}). Texto de interface, nunca valores.
     */
    static String key(String prefix, ElementReference el, AutomationStep s, Set<String> used) {
        return key(prefix, el, s, used, false);
    }

    /** Rodada 4 (R4-02): com {@code date}, o elemento guarda uma data e o nome diz isso ("Nascimento" → {@code birth_date}). */
    static String key(String prefix, ElementReference el, AutomationStep s, Set<String> used, boolean date) {
        return key(prefix, el, s, used, date, null);
    }

    /**
     * Rodada 5 (CQ-R5-02): com {@code effect}, o nome é o do efeito confirmado da etapa (o clique que baixa a planilha →
     * {@code download_spreadsheet}), não o texto do botão. O texto da página continua nos localizadores, sem mudança.
     */
    static String key(String prefix, ElementReference el, AutomationStep s, Set<String> used, boolean date, String effect) {
        String base = null;
        for (LocatorCandidate c : el.locators()) {
            if (c.strategy() == LocatorStrategy.LABEL && PythonGenerator.labelText(c.expression()) != null) {
                base = PythonGenerator.labelText(c.expression());
                break;
            }
        }
        if (base == null) base = PythonGenerator.anchorText(el);
        if (base == null && el.accessibleName() != null && !el.accessibleName().isBlank()) base = el.accessibleName();
        if (base != null) base = withoutIconTokens(base);
        if (base == null && s.actionType() == ActionType.FILL && s.parameters().value().orElse(null) instanceof ValueSource.VariableReference ref) {
            base = "campo " + ref.name();
        }
        if (base == null && s.actionType() == ActionType.FILL && s.parameters().value().orElse(null) instanceof ValueSource.CredentialReference cred) {
            base = "campo " + cred.alias().substring(cred.alias().lastIndexOf('.') + 1);
        }
        if (base == null && "HEADING".equals(el.semanticKind()) && s.actionType() == ActionType.OBSERVE) base = "titulo";
        if (base == null) base = "alvo";
        String slug = effect != null ? effect : EnglishNames.noun(base, date).snake();
        if (slug.length() > 40) slug = slug.substring(0, 40).replaceAll("_+$", "");
        if (slug.isEmpty() || !Character.isLetter(slug.charAt(0))) slug = "target";
        // o nome do elemento repete o da tela (botão "Consultar" na tela de consulta): o tipo do elemento desfaz a ambiguidade
        if (slug.equals(prefix)) slug = slug + "_" + switch (el.semanticKind() == null ? "" : el.semanticKind()) {
            case "BUTTON" -> "button";
            case "LINK" -> "link";
            case "HEADING" -> "heading";
            default -> "element";
        };
        String key = CodePlan.SYSTEM + "." + prefix + "." + slug;
        int n = 2;
        while (!used.add(key)) key = CodePlan.SYSTEM + "." + prefix + "." + slug + "_" + n++;
        return key;
    }

    /** Nome de ícone de fonte (ligadura em snake_case minúsculo: {@code cloud_download}, {@code arrow_forward}). */
    private static final java.util.regex.Pattern ICON_TOKEN = java.util.regex.Pattern.compile("[a-z][a-z0-9]*(?:_[a-z0-9]+)+");

    /**
     * Rodada 5 (CQ-R5-02): o texto acessível de botões e links com ícone de fonte traz o NOME do ícone junto do rótulo
     * ("Download Excel cloud_download"). Para o NOME no código, esses tokens são artefato visual e saem — só quando sobra
     * outra palavra (um rótulo feito só do nome do ícone fica como está). O texto da página nos localizadores não muda.
     */
    static String withoutIconTokens(String label) {
        List<String> kept = new ArrayList<>();
        boolean dropped = false;
        for (String w : label.trim().split("\\s+")) {
            if (ICON_TOKEN.matcher(w).matches()) dropped = true;
            else kept.add(w);
        }
        return dropped && !kept.isEmpty() ? String.join(" ", kept) : label;
    }
}
