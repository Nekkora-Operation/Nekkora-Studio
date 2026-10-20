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
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
 */
public final class SingleGenerator {

    public static final String GENERATOR = "nekkora-studio-codegen/1.1.0";
    public static final String FRAMEWORK_REQUIREMENT = "nekkora-rpa==0.2.0";
    static final Set<ActionType> SUPPORTED = Set.of(ActionType.NAVIGATE, ActionType.FILL, ActionType.CLICK, ActionType.WAIT_FOR, ActionType.OBSERVE);

    /** Etapa pronta para gerar. */
    record Step(AutomationStep step, SingleConfig.Group group, String key, Map<String, Object> target, String input, String credential, String output,
                String navigateUrl, SingleConfig.Effect effect, String nextKey, String firstFillKey) {}

    private SingleGenerator() {}

    public static PythonGenerator.Result generate(Automation a, ValidationReport report, SingleConfig cfg) {
        GuidedPlan plan = GuidedPlan.of(a, report);
        List<Blocker> blockers = new ArrayList<>();
        if (report.status() == ValidationStatus.INCOMPLETE) {
            for (Finding f : report.findings()) if (f.severity() == Severity.BLOCKER) blockers.add(new Blocker("STATIC_" + f.code().name(), f.step()));
            if (blockers.isEmpty()) blockers.add(new Blocker("STATIC_BLOCKERS", null));
        }
        // ordem do modelo = ordem dos grupos
        List<AutomationStep> modelOrder = new ArrayList<>();
        a.sessions().forEach(s -> modelOrder.addAll(s.steps()));
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
        // chaves e alvos primeiro (o envio usa a chave da etapa seguinte / do 1º campo do grupo)
        Map<String, String> keyOf = new LinkedHashMap<>();
        Map<String, Map<String, Object>> targetOf = new LinkedHashMap<>();
        Set<String> used = new LinkedHashSet<>();
        for (AutomationStep s : modelOrder) {
            String id = s.id().value().toString();
            if (!SUPPORTED.contains(s.actionType())) {
                blockers.add(new Blocker("UNSUPPORTED_ACTION_" + s.actionType().name(), s.id()));
                continue;
            }
            if (s.actionType() == ActionType.NAVIGATE) continue;
            Optional<ElementReference> el = s.element();
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
            SingleConfig.Group g = groupOf.get(id);
            keyOf.put(id, key(g.prefix(), el.get(), s.actionType(), used));
            targetOf.put(id, t);
        }
        List<Step> steps = new ArrayList<>();
        Set<String> inputsUsed = new LinkedHashSet<>();
        Set<String> outputsUsed = new LinkedHashSet<>();
        for (int i = 0; i < modelOrder.size(); i++) {
            AutomationStep s = modelOrder.get(i);
            String id = s.id().value().toString();
            SingleConfig.Group g = groupOf.get(id);
            if (!SUPPORTED.contains(s.actionType())) continue;
            Optional<ValueSource> value = s.parameters().value();
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
                }
                default -> {
                }
            }
            steps.add(new Step(s, g, keyOf.get(id), targetOf.get(id), input, credential, output, url, effect, next, firstFill));
        }
        for (String v : cfg.inputs()) if (!inputsUsed.contains(v)) blockers.add(new Blocker("INPUT_NOT_USED", null));
        for (String st : cfg.outputs().keySet()) if (!outputsUsed.contains(st)) blockers.add(new Blocker("OUTPUT_STEP_NOT_OBSERVE", null));
        for (String st : cfg.effects().keySet()) {
            boolean click = modelOrder.stream().anyMatch(x -> x.id().value().toString().equals(st) && x.actionType() == ActionType.CLICK);
            if (!click) blockers.add(new Blocker("EFFECT_STEP_NOT_CLICK", null));
        }
        if (!blockers.isEmpty()) return new PythonGenerator.Result(List.copyOf(blockers), Map.of(), plan.planRevision());
        return new PythonGenerator.Result(List.of(), SingleEmitter.emit(a, plan, cfg, steps), plan.planRevision());
    }

    static boolean allowed(SingleConfig cfg, String url) {
        return url != null && cfg.allowedOrigins().stream().anyMatch(o -> url.equals(o) || url.startsWith(o + "/"));
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

    /** Chave estável e legível: {@code processo.<prefixo da Page>.<rótulo|nome|ação>} (texto de interface, sem valores). */
    static String key(String prefix, ElementReference el, ActionType action, Set<String> used) {
        String base = null;
        for (LocatorCandidate c : el.locators()) {
            if (c.strategy() == LocatorStrategy.LABEL && PythonGenerator.labelText(c.expression()) != null) {
                base = PythonGenerator.labelText(c.expression());
                break;
            }
        }
        if (base == null && el.accessibleName() != null && !el.accessibleName().isBlank()) base = el.accessibleName();
        if (base == null) base = action.name().toLowerCase(Locale.ROOT);
        String slug = Normalizer.normalize(base, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (slug.isEmpty() || !Character.isLetter(slug.charAt(0))) slug = "alvo_" + slug;
        if (slug.length() > 40) slug = slug.substring(0, 40).replaceAll("_+$", "");
        String key = PythonGenerator.SYSTEM + "." + prefix + "." + slug;
        int n = 2;
        while (!used.add(key)) key = PythonGenerator.SYSTEM + "." + prefix + "." + slug + "_" + n++;
        return key;
    }
}
