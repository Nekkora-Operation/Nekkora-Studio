package com.nekkora.studio.codegen;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
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
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STUDIO-011 — gera um projeto Python que USA o pacote {@code nekkora_rpa} a partir do modelo VALIDADO.
 *
 * <p>Recorte suportado (o resto é bloqueio explícito no pré-voo, nunca "aproximação"):
 * <ul>
 *   <li>ações {@code NAVIGATE}, {@code FILL} (variável de ENTRADA ligada a uma coluna, ou credencial), {@code CLICK},
 *       {@code WAIT_FOR} e {@code OBSERVE} com alvo;</li>
 *   <li>alvos com decisão {@code CONFIRMED} do debug guiado e localizador preferencial validado e NÃO posicional;</li>
 *   <li>sessões com papel declarado na configuração: inicialização, UMA sessão repetida por registro (uma transação
 *       por linha da planilha) e conclusão.</li>
 * </ul>
 * Na sessão por registro, o último clique é o ENVIO (não idempotente): ele só é dado por concluído quando o nó do 1º
 * campo preenchido foi substituído (prova objetiva de avanço) ou quando o formulário some. Valores de célula, nomes de
 * variáveis sensíveis, credenciais e textos livres nunca entram no código; os alvos vêm do modelo, nunca de seletores
 * escritos à mão. Saída determinística (mesma entrada → mesmos bytes).
 */
public final class PythonGenerator {

    public static final String GENERATOR = "nekkora-studio-codegen/1.0.0";
    public static final String FRAMEWORK_REQUIREMENT = "nekkora-rpa[excel]==0.2.0";
    public static final String SYSTEM = "processo";
    static final String HEADER = "# GERADO pelo Nekkora Studio (STUDIO-011) a partir do modelo validado. NÃO EDITE: correções vão para o\n"
            + "# modelo (debug guiado), e o código é gerado de novo. Arquivo editado à mão não é sobrescrito.\n";

    private static final Pattern LABEL_TEXT = Pattern.compile("normalize-space\\(\\.\\)=(?:\"([^\"]{1,200})\"|'([^']{1,200})')");
    private static final Map<String, String> ROLES = Map.ofEntries(Map.entry("BUTTON", "button"), Map.entry("LINK", "link"),
            Map.entry("TEXT_INPUT", "textbox"), Map.entry("TEXTAREA", "textbox"), Map.entry("SEARCH_INPUT", "searchbox"),
            Map.entry("CHECKBOX", "checkbox"), Map.entry("RADIO", "radio"), Map.entry("SELECT", "combobox"), Map.entry("HEADING", "heading"));
    private static final Set<ActionType> SUPPORTED = Set.of(ActionType.NAVIGATE, ActionType.FILL, ActionType.CLICK, ActionType.WAIT_FOR,
            ActionType.OBSERVE);

    public record Blocker(String code, StepId step) {
        @Override
        public String toString() {
            return step == null ? code : code + "@" + step.value();
        }
    }

    /** Resultado: bloqueios do pré-voo (se houver, nenhum arquivo) ou os arquivos gerados (caminho relativo → conteúdo). */
    public record Result(List<Blocker> blockers, Map<String, String> files, String planRevision) {
        public boolean ok() {
            return blockers.isEmpty();
        }
    }

    enum Role { INIT, RECORD, END }

    /** Etapa pronta para gerar. */
    record Step(AutomationStep step, int session, Role role, String key, Map<String, Object> target, String column, String credential,
                String navigateUrl, boolean download) {}

    private PythonGenerator() {}

    // ------------------------------------------------------------------------------------------------ pré-voo + geração
    public static Result generate(Automation a, ValidationReport report, CodegenConfig cfg) {
        GuidedPlan plan = GuidedPlan.of(a, report);
        List<Blocker> blockers = new ArrayList<>();
        if (report.status() == ValidationStatus.INCOMPLETE) {
            for (Finding f : report.findings()) {
                if (f.severity() == Severity.BLOCKER) blockers.add(new Blocker("STATIC_" + f.code().name(), f.step()));
            }
            if (blockers.isEmpty()) blockers.add(new Blocker("STATIC_BLOCKERS", null));
        }
        List<LogicalSession> sessions = a.sessions();
        Map<Integer, Role> roles = new TreeMap<>();
        for (int n : cfg.initSessions()) roles.put(n, Role.INIT);
        roles.put(cfg.perRecordSession(), Role.RECORD);
        for (int n : cfg.endSessions()) roles.put(n, Role.END);
        for (int n : roles.keySet()) if (n > sessions.size()) blockers.add(new Blocker("CONFIG_SESSION_NOT_FOUND", null));
        for (int i = 1; i <= sessions.size(); i++) {
            if (!roles.containsKey(i) && !sessions.get(i - 1).isEmpty()) blockers.add(new Blocker("SESSION_WITHOUT_ROLE", null));
        }
        Map<String, CodegenConfig.Column> byVariable = new LinkedHashMap<>();
        for (CodegenConfig.Column c : cfg.input().columns()) {
            Optional<VariableDefinition> v = a.variableNamed(c.variable());
            if (v.isEmpty()) blockers.add(new Blocker("COLUMN_VARIABLE_MISSING", null));
            else if (v.get().origin().kind() != VariableOrigin.Kind.INPUT) blockers.add(new Blocker("COLUMN_VARIABLE_NOT_INPUT", null));
            byVariable.put(c.variable(), c);
        }
        if (!blockers.isEmpty() && blockers.stream().anyMatch(b -> b.code().startsWith("CONFIG_"))) return new Result(List.copyOf(blockers), Map.of(), plan.planRevision());

        List<Step> steps = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        Set<String> usedVariables = new LinkedHashSet<>();
        boolean downloadFound = cfg.input().source() != CodegenConfig.Source.DOWNLOAD;
        for (int n = 1; n <= sessions.size(); n++) {
            Role role = roles.get(n);
            if (role == null) continue;
            for (AutomationStep s : sessions.get(n - 1).steps()) {
                StepId id = s.id();
                ActionType action = s.actionType();
                if (!SUPPORTED.contains(action)) {
                    blockers.add(new Blocker("UNSUPPORTED_ACTION_" + action.name(), id));
                    continue;
                }
                boolean download = cfg.input().source() == CodegenConfig.Source.DOWNLOAD && id.value().toString().equals(cfg.input().downloadStep());
                if (download) {
                    downloadFound = true;
                    if (action != ActionType.CLICK || role != Role.INIT) blockers.add(new Blocker("DOWNLOAD_STEP_NOT_INIT_CLICK", id));
                }
                String column = null;
                String credential = null;
                String url = null;
                Optional<ValueSource> value = s.parameters().value();
                if (action == ActionType.FILL) {
                    if (value.isEmpty()) {
                        blockers.add(new Blocker("FILL_WITHOUT_VALUE", id));
                    } else if (value.get() instanceof ValueSource.VariableReference ref) {
                        CodegenConfig.Column c = byVariable.get(ref.name());
                        if (c == null) blockers.add(new Blocker("VARIABLE_WITHOUT_COLUMN", id));
                        else if (role != Role.RECORD) blockers.add(new Blocker("RECORD_VALUE_OUTSIDE_RECORD_SESSION", id));
                        else column = c.header();
                        usedVariables.add(ref.name());
                    } else if (value.get() instanceof ValueSource.CredentialReference cred) {
                        credential = cred.alias();
                        if (!credential.matches("[a-z][a-z0-9_.]{0,59}")) blockers.add(new Blocker("CREDENTIAL_ALIAS_INVALID", id));
                    } else {
                        blockers.add(new Blocker("LITERAL_VALUE_NOT_GENERATED", id)); // valores gravados nunca vão para o código
                    }
                }
                if (action == ActionType.NAVIGATE) {
                    if (value.isPresent() && value.get() instanceof ValueSource.LiteralValue lit && allowed(cfg, lit.text())) url = lit.text();
                    else blockers.add(new Blocker("NAVIGATE_URL_NOT_ALLOWED", id));
                    steps.add(new Step(s, n, role, null, null, null, null, url, false));
                    continue;
                }
                boolean needsTarget = action != ActionType.WAIT_FOR && action != ActionType.OBSERVE || s.element().isPresent();
                if (!needsTarget) {
                    blockers.add(new Blocker(action == ActionType.OBSERVE ? "OBSERVE_WITHOUT_TARGET" : "WAIT_WITHOUT_TARGET", id));
                    continue;
                }
                Optional<ElementReference> el = s.element();
                if (el.isEmpty()) {
                    blockers.add(new Blocker("TARGET_MISSING", id));
                    continue;
                }
                Optional<TargetDecision> d = s.targetDecision();
                if (d.isEmpty() || !d.get().confirmed()) {
                    blockers.add(new Blocker("TARGET_NOT_CONFIRMED", id));
                    continue;
                }
                Map<String, Object> target = target(el.get(), id, blockers);
                if (target == null) continue;
                String key = key(n, el.get(), action, keys);
                steps.add(new Step(s, n, role, key, target, column, credential, null, download));
            }
        }
        if (!downloadFound) blockers.add(new Blocker("DOWNLOAD_STEP_NOT_FOUND", null));
        for (String v : byVariable.keySet()) if (!usedVariables.contains(v)) blockers.add(new Blocker("COLUMN_NOT_USED", null));
        List<Step> record = steps.stream().filter(s -> s.role() == Role.RECORD).toList();
        boolean hasFill = record.stream().anyMatch(s -> s.step().actionType() == ActionType.FILL);
        boolean endsWithClick = !record.isEmpty() && record.get(record.size() - 1).step().actionType() == ActionType.CLICK;
        if (!hasFill || !endsWithClick) blockers.add(new Blocker("RECORD_SESSION_SHAPE", null));
        if (!blockers.isEmpty()) return new Result(List.copyOf(blockers), Map.of(), plan.planRevision());
        return new Result(List.of(), Emitter.emit(a, plan, cfg, steps), plan.planRevision());
    }

    static boolean allowed(CodegenConfig cfg, String url) {
        return url != null && cfg.allowedOrigins().stream().anyMatch(o -> url.equals(o) || url.startsWith(o + "/"));
    }

    // ------------------------------------------------------------------------------------------------ alvos
    /** Registro do framework para o alvo (nekkora-rpa/selectors 1.1.0) ou {@code null} com bloqueio. */
    static Map<String, Object> target(ElementReference el, StepId id, List<Blocker> blockers) {
        Optional<LocatorCandidate> preferred = el.preferredLocator();
        if (preferred.isEmpty() || !preferred.get().validated()) {
            blockers.add(new Blocker("LOCATOR_NOT_VALIDATED", id));
            return null;
        }
        if (preferred.get().strategy() == LocatorStrategy.POSITIONAL) {
            blockers.add(new Blocker("LOCATOR_POSITIONAL", id)); // posição muda: nunca é o localizador gerado
            return null;
        }
        String name = el.accessibleName() == null ? "" : el.accessibleName().strip();
        String role = ROLES.get(el.semanticKind() == null ? "" : el.semanticKind());
        String label = null;
        for (LocatorCandidate c : el.locators()) {
            if (c.strategy() == LocatorStrategy.LABEL) {
                label = labelText(c.expression());
                if (label != null) break;
            }
        }
        Map<String, Object> expected = new LinkedHashMap<>();
        String tag = el.tag() == null ? "" : el.tag().strip().toLowerCase(Locale.ROOT);
        if (!tag.matches("[a-z][a-z0-9-]{0,39}")) {
            blockers.add(new Blocker("TARGET_TAG_UNKNOWN", id));
            return null;
        }
        expected.put("tag", tag);
        if (!name.isEmpty() && role != null) {
            expected.put("role", role);
            expected.put("name", name);
        } else if (label != null) {
            expected.put("label", label);
        }
        Map<String, Object> pref = locator(preferred.get(), role, name);
        if (pref == null) {
            blockers.add(new Blocker("LOCATOR_NOT_EXPORTABLE", id));
            return null;
        }
        boolean strong = expected.containsKey("name") || expected.containsKey("label");
        List<Object> alternatives = new ArrayList<>();
        if (strong) {
            for (LocatorCandidate c : el.locators()) {
                if (c == preferred.get() || !c.validated() || c.strategy() == LocatorStrategy.POSITIONAL) continue;
                Map<String, Object> alt = locator(c, role, name);
                if (alt != null && !alt.equals(pref) && !alternatives.contains(alt) && alternatives.size() < 3) alternatives.add(alt);
            }
        }
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("preferred", pref);
        if (!alternatives.isEmpty()) t.put("alternatives", alternatives);
        t.put("expected", expected);
        Map<String, Object> prov = new LinkedHashMap<>();
        prov.put("studioStepId", id.value().toString());
        t.put("provenance", prov);
        return t;
    }

    static String labelText(String expression) {
        if (expression == null) return null;
        Matcher m = LABEL_TEXT.matcher(expression);
        if (!m.find()) return null;
        String text = m.group(1) != null ? m.group(1) : m.group(2);
        text = text.strip().replaceAll("\\s+", " ");
        return text.isEmpty() ? null : text;
    }

    static Map<String, Object> locator(LocatorCandidate c, String role, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (c.strategy() == LocatorStrategy.LABEL) {
            String text = labelText(c.expression());
            if (text == null) return null;
            m.put("strategy", "label");
            m.put("text", text);
            return m;
        }
        String expr = c.expression() == null ? "" : c.expression().strip();
        if (expr.isEmpty()) {
            if (c.strategy() == LocatorStrategy.ROLE_NAME && role != null && !name.isEmpty()) {
                m.put("strategy", "role");
                m.put("role", role);
                m.put("name", name);
                return m;
            }
            return null;
        }
        if (expr.length() > 1000) return null;
        m.put("strategy", expr.startsWith("/") || expr.startsWith("(") ? "xpath" : "css");
        m.put("expression", expr);
        return m;
    }

    /** Chave estável e legível: {@code processo.s<sessão>.<rótulo|nome|ação>} (sem valores; texto de interface). */
    static String key(int session, ElementReference el, ActionType action, Set<String> used) {
        String base = null;
        for (LocatorCandidate c : el.locators()) {
            if (c.strategy() == LocatorStrategy.LABEL && labelText(c.expression()) != null) {
                base = labelText(c.expression());
                break;
            }
        }
        if (base == null && el.accessibleName() != null && !el.accessibleName().isBlank()) base = el.accessibleName();
        if (base == null) base = action.name().toLowerCase(Locale.ROOT);
        String slug = Normalizer.normalize(base, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (slug.isEmpty() || !Character.isLetter(slug.charAt(0))) slug = "alvo_" + slug;
        if (slug.length() > 40) slug = slug.substring(0, 40).replaceAll("_+$", "");
        String key = SYSTEM + ".s" + session + "." + slug;
        int i = 2;
        while (!used.add(key)) key = SYSTEM + ".s" + session + "." + slug + "_" + i++;
        return key;
    }
}
