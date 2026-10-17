package com.nekkora.studio.guided;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepContext;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.TargetRequirement;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.element.ContextAnchor;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.element.ManualLocator;
import com.nekkora.studio.domain.origin.StepOrigin;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import com.nekkora.studio.domain.variable.VariableId;
import com.nekkora.studio.semantics.SemanticContract;
import com.nekkora.studio.validation.Finding;
import com.nekkora.studio.validation.FindingCode;
import com.nekkora.studio.validation.Severity;
import com.nekkora.studio.validation.ValidationReport;
import com.nekkora.studio.validation.ValidationStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Plano intermediário do debug guiado (STUDIO-009, G1): PROJEÇÃO determinística do modelo — etapas na ordem da
 * automação, intenção (ação e origem do valor), alvo técnico (como foi capturado ou ajustado), contexto, incertezas e os
 * critérios que o debug visual precisa verificar no navegador. Não é um segundo modelo (D-S009-01): cada etapa é
 * identificada pelo {@link StepId} do domínio, e nada aqui é editável.
 *
 * <p>Sem valores (D-S009-08 e §4 da missão): nenhum literal, valor fixo, apelido de credencial, finalidade, nome de
 * etapa/sessão/automação ou chave de correlação; do alvo, só o que já é ponto fixo da {@code nekkora-privacy/2} no
 * {@code .nekkora} (tipo, tag, nome acessível apresentável, localizadores analisados, trajetória e âncoras). O plano não
 * executa nada e não certifica execução: {@link #staticStatus()} é o estado ESTÁTICO da validação, nunca um resultado.
 *
 * <p>Como o contrato semântico (STUDIO-008), guarda só cópias próprias: do domínio, apenas identidades e enums.
 */
public final class GuidedPlan {
    public static final String SCHEMA = "nekkora-studio/guided-debug-plan";
    public static final String VERSION = "1.0.0";

    /** O que o plano NUNCA contém (publicado no JSON para quem o consome). */
    public static final List<String> EXCLUSIONS = List.of("LITERAL_VALUES", "CONSTANT_VALUES", "CREDENTIAL_ALIASES", "PURPOSES", "NAMES_AND_DESCRIPTIONS",
            "COMMENTS", "CORRELATION_KEYS", "RUNTIME_VALUES", "RUNTIME_RESULTS");

    /** Teclas transportáveis no plano (vocabulário fechado); qualquer outra fica retida. */
    public static final Set<String> KEY_VOCABULARY = Set.of("Enter", "Tab", "Escape");

    /** Como o debug pode reencontrar o alvo (D-S009-03). */
    public enum IdentitySource {
        /** Identidade lógica registrada pela extensão ({@code targetId}); a prova mais forte disponível. */
        TARGET_ID,
        /** Só o descritor recuperável (a referência compacta persistida): prova mais fraca, reuso restrito. */
        DESCRIPTOR
    }

    /** Como o alvo efetivo se relaciona com o capturado. */
    public enum TargetState {
        /** Igual ao capturado (ou ao informado na criação). */
        AS_CAPTURED,
        /** Mesmo elemento capturado; outro localizador preferencial. */
        PREFERRED_LOCATOR_CHANGED
    }

    /** O que o debug visual faz com a etapa. */
    public enum Applicability {
        /** A etapa atua sobre um elemento e tem referência: inspecionar e destacar. */
        INSPECT_TARGET,
        /** A ação não exige alvo (ex.: navegar): nada a destacar. */
        NO_TARGET_REQUIRED,
        /** A ação espera um alvo e não há referência verificável: só Corrigir (selecionar no navegador). */
        TARGET_MISSING
    }

    /** Critérios que o debug verifica no navegador (cada um vira um resultado na verificação). */
    public enum Criterion {
        CONTEXT_AUTHORIZED, TARGET_IDENTITY_RESOLVED, TARGET_UNIQUE, TARGET_VISIBLE, TARGET_ENABLED, TARGET_EDITABLE, TARGET_KIND_MATCHES_ACTION,
        FRAME_CHAIN_REACHABLE, SHADOW_SCOPE_REACHABLE
    }

    /** Incertezas do plano (o que se sabe que NÃO está comprovado antes do debug). */
    public enum Uncertainty {
        /** A ação espera um alvo e a etapa não tem referência técnica. */
        NO_TARGET_REFERENCE,
        /** Sem identidade lógica registrada: o reencontro usará só o descritor. */
        IDENTITY_FROM_DESCRIPTOR_ONLY,
        /** Nenhum localizador validado (único e no alvo) na captura. */
        NO_VALIDATED_LOCATOR,
        /** Há localizadores manuais, nunca verificados. */
        MANUAL_LOCATORS_UNVERIFIED,
        /** O tipo do elemento não é o usual para a ação (ex.: preencher um botão). */
        ACTION_KIND_UNUSUAL,
        /** Elemento sensível (ex.: senha): o valor nunca é guardado nem transportado. */
        TARGET_SENSITIVE,
        /** O alvo fica dentro de iframe: o debug precisa atravessar a cadeia de frames. */
        TARGET_IN_FRAME,
        /** O alvo fica dentro de shadow root. */
        TARGET_IN_SHADOW_ROOT,
        /** O valor da etapa é literal e fica retido no plano (o caso de teste usa dados fictícios próprios). */
        VALUE_WITHHELD,
        /** A validação estática encontrou bloqueio nesta etapa. */
        STATIC_BLOCKER
    }

    /** Origem do valor da etapa, sem o valor. */
    public enum ValueKind { NONE, LITERAL, VARIABLE, CREDENTIAL }

    /** Intenção da etapa: ação e origem do valor. {@code variable*} só para variável declarada; {@code key} só do vocabulário. */
    public record Intent(ActionType actionType, TargetRequirement targetRequirement, ValueKind value, VariableId variableId, String variableName,
                         String key, boolean optionDeclared) {}

    /** Localizador do alvo como capturado (expressões já analisadas pela {@code nekkora-privacy/2}). */
    public record Locator(int index, LocatorStrategy strategy, String description, String expression, LocatorStability stability, int matchCountAtCapture,
                          boolean validatedAtCapture) {}

    /** Localizador proposto à mão — sempre não verificado. */
    public record ManualLocatorView(ManualLocator.Syntax syntax, String expression) {}

    /** Passo da trajetória (documento → iframe → shadow root → elemento). */
    public record PathStep(AccessStep.Type type, String label) {}

    /** Âncora de contexto (linha, diálogo, formulário). */
    public record Anchor(String relation, String kind, String name) {}

    /** Alvo técnico efetivo da etapa. */
    public record Target(IdentitySource identitySource, TargetState state, String kind, String tag, String name, boolean sensitive, List<Locator> locators,
                         int preferredLocator, List<ManualLocatorView> manualLocators, List<PathStep> accessPath, List<Anchor> context) {
        public Target {
            locators = List.copyOf(locators);
            manualLocators = List.copyOf(manualLocators);
            accessPath = List.copyOf(accessPath);
            context = List.copyOf(context);
        }
    }

    /** Onde a etapa acontece (textos já sanitizados pelo produtor; nunca a URL completa). */
    public record Context(String application, String screenTitle, String location) {}

    /** Uma etapa do plano. */
    public record Step(StepId id, SessionId session, int sessionNumber, int stepNumber, StepOrigin origin, Intent intent, Target target, Context context,
                       Applicability applicability, List<Criterion> criteria, List<Uncertainty> uncertainties, List<FindingCode> findings,
                       List<SemanticContract.UncertaintyCode> semanticUncertainties) {
        public Step {
            criteria = List.copyOf(criteria);
            uncertainties = List.copyOf(uncertainties);
            findings = List.copyOf(findings);
            semanticUncertainties = List.copyOf(semanticUncertainties);
        }
    }

    /** Variável do processo como o contrato semântico a publica (sem valor nem finalidade). */
    public record Variable(VariableId id, String name, String technicalType, String semanticType, String semanticStatus, String sensitivity, String origin,
                           StepId producer) {}

    private final AutomationId automationId;
    private final String modelVersion;
    private final long automationRevision;
    private final String rulesVersion;
    private final ValidationStatus staticStatus;
    private final List<Variable> variables;
    private final List<Step> steps;
    private final String planRevision;
    private final String json;

    private GuidedPlan(AutomationId automationId, String modelVersion, long automationRevision, String rulesVersion, ValidationStatus staticStatus,
            List<Variable> variables, List<Step> steps) {
        this.automationId = automationId;
        this.modelVersion = modelVersion;
        this.automationRevision = automationRevision;
        this.rulesVersion = rulesVersion;
        this.staticStatus = staticStatus;
        this.variables = List.copyOf(variables);
        this.steps = List.copyOf(steps);
        this.planRevision = GuidedPlanJson.revisionOf(GuidedPlanJson.write(this, null));
        this.json = GuidedPlanJson.write(this, planRevision);
    }

    /**
     * Projeta a automação. {@code report} precisa ser da mesma automação (regras 1.1.0 recomendadas: são as que conhecem
     * variáveis). Só lê; linear no tamanho da automação.
     */
    public static GuidedPlan of(Automation a, ValidationReport report) {
        Objects.requireNonNull(a, "automation");
        Objects.requireNonNull(report, "report");
        if (!report.automationId().equals(a.id())) throw new IllegalArgumentException("relatório de outra automação");
        SemanticContract contract = SemanticContract.of(a);
        List<Variable> vars = new ArrayList<>();
        for (SemanticContract.Variable v : contract.variables()) {
            vars.add(new Variable(v.id(), v.name(), v.technicalType().name(), v.semanticType().name(), v.semanticStatus().name(), v.sensitivity().name(),
                    v.origin().kind().name(), v.producer() == null ? null : v.producer().step()));
        }
        List<Step> steps = new ArrayList<>();
        int s = 0;
        int index = 0;
        for (LogicalSession session : a.sessions()) {
            s++;
            int n = 0;
            for (AutomationStep st : session.steps()) {
                n++;
                SemanticContract.Step cs = contract.steps().get(index++);
                steps.add(step(st, session, s, n, cs, contract, report));
            }
        }
        return new GuidedPlan(a.id(), a.modelVersion(), a.revision(), report.rulesVersion(), report.status(), vars, steps);
    }

    private static Step step(AutomationStep st, LogicalSession session, int sessionNumber, int stepNumber, SemanticContract.Step cs,
            SemanticContract contract, ValidationReport report) {
        ActionType action = st.actionType();
        Set<Uncertainty> uncertain = EnumSet.noneOf(Uncertainty.class);
        Intent intent = intent(st, cs, contract, uncertain);
        Target target = target(st, action, uncertain);
        Applicability applicability;
        if (target != null) {
            applicability = Applicability.INSPECT_TARGET;
        } else if (action.target() == TargetRequirement.EXPECTED) {
            applicability = Applicability.TARGET_MISSING;
            uncertain.add(Uncertainty.NO_TARGET_REFERENCE);
        } else {
            applicability = Applicability.NO_TARGET_REQUIRED;
        }
        List<Finding> found = report.forStep(st.id());
        if (found.stream().anyMatch(f -> f.severity() == Severity.BLOCKER)) uncertain.add(Uncertainty.STATIC_BLOCKER);
        List<FindingCode> findings = found.stream().map(Finding::code).distinct().sorted(Comparator.comparing(FindingCode::ordinal)).toList();
        Set<SemanticContract.UncertaintyCode> semantic = new TreeSet<>(Comparator.comparing(SemanticContract.UncertaintyCode::ordinal));
        for (SemanticContract.Uncertainty u : contract.uncertainties()) {
            if (st.id().equals(u.step()) || (u.step() == null && u.variable() != null && u.variable().equals(cs.variable()))) semantic.add(u.code());
        }
        StepContext c = st.context().orElse(null);
        Context context = c == null ? null : new Context(safe(c.application()), safe(c.screenTitle()), safe(c.location()));
        return new Step(st.id(), session.id(), sessionNumber, stepNumber, st.provenance().origin(), intent, target, context, applicability,
                criteria(action, target, applicability), List.copyOf(uncertain), findings, List.copyOf(semantic));
    }

    private static Intent intent(AutomationStep st, SemanticContract.Step cs, SemanticContract contract, Set<Uncertainty> uncertain) {
        ValueSource value = st.parameters().value().orElse(null);
        ValueKind kind = switch (cs.value()) {
            case NONE -> ValueKind.NONE;
            case LITERAL -> ValueKind.LITERAL;
            case VARIABLE -> ValueKind.VARIABLE;
            case CREDENTIAL -> ValueKind.CREDENTIAL;
        };
        String key = null;
        if (st.actionType() == ActionType.PRESS_KEY && value instanceof ValueSource.LiteralValue l && KEY_VOCABULARY.contains(l.text())) {
            key = l.text(); // tecla do vocabulário fechado: é a própria ação, não um dado
        } else if (kind == ValueKind.LITERAL) {
            uncertain.add(Uncertainty.VALUE_WITHHELD);
        }
        String variableName = null;
        if (cs.variable() != null) {
            variableName = contract.variables().stream().filter(v -> v.id().equals(cs.variable())).map(SemanticContract.Variable::name).findFirst().orElse(null);
        }
        return new Intent(st.actionType(), st.actionType().target(), kind, cs.variable(), variableName, key, st.parameters().option().isPresent());
    }

    private static Target target(AutomationStep st, ActionType action, Set<Uncertainty> uncertain) {
        ElementReference e = st.element().orElse(null);
        List<ManualLocatorView> manual = st.manualLocators().stream().map(m -> new ManualLocatorView(m.syntax(), m.expression())).toList();
        if (!manual.isEmpty()) uncertain.add(Uncertainty.MANUAL_LOCATORS_UNVERIFIED);
        if (e == null) return null; // o domínio só aceita localizador manual ao lado de uma referência
        boolean registered = e.correlation().get(CorrelationKeys.Key.TARGET_ID).isPresent();
        IdentitySource source = registered ? IdentitySource.TARGET_ID : IdentitySource.DESCRIPTOR;
        if (!registered) uncertain.add(Uncertainty.IDENTITY_FROM_DESCRIPTOR_ONLY);
        List<Locator> locators = new ArrayList<>();
        for (int i = 0; i < e.locators().size(); i++) {
            LocatorCandidate l = e.locators().get(i);
            locators.add(new Locator(i, l.strategy(), l.description(), l.expression(), l.stability(), l.matchCount(), l.validated()));
        }
        if (locators.stream().noneMatch(Locator::validatedAtCapture)) uncertain.add(Uncertainty.NO_VALIDATED_LOCATOR);
        if (e.sensitive()) uncertain.add(Uncertainty.TARGET_SENSITIVE);
        List<PathStep> path = e.accessPath().stream().map(p -> new PathStep(p.type(), safe(p.label()))).toList();
        if (path.stream().anyMatch(p -> p.type() == AccessStep.Type.IFRAME)) uncertain.add(Uncertainty.TARGET_IN_FRAME);
        if (path.stream().anyMatch(p -> p.type() == AccessStep.Type.SHADOW_ROOT)) uncertain.add(Uncertainty.TARGET_IN_SHADOW_ROOT);
        if (unusual(action, e.semanticKind())) uncertain.add(Uncertainty.ACTION_KIND_UNUSUAL);
        List<Anchor> anchors = new ArrayList<>();
        for (ContextAnchor c : e.context()) anchors.add(new Anchor(c.relation(), c.kind(), safe(c.name())));
        TargetState state = st.capturedElement().map(ElementReference::preferredLocatorIndex).orElse(e.preferredLocatorIndex()) == e.preferredLocatorIndex()
                ? TargetState.AS_CAPTURED : TargetState.PREFERRED_LOCATOR_CHANGED;
        return new Target(source, state, e.semanticKind(), e.tag(), safe(e.accessibleName()), e.sensitive(), locators, e.preferredLocatorIndex(), manual, path,
                anchors);
    }

    /** Tipos usuais por ação; tipo desconhecido nunca é "incomum" (não há o que afirmar). Clicar e teclar aceitam qualquer. */
    static final Map<ActionType, Set<String>> USUAL_KINDS = Map.of(
            ActionType.FILL, Set.of("TEXT_INPUT", "EMAIL_INPUT", "PASSWORD_INPUT", "NUMBER_INPUT", "DATE_INPUT", "SEARCH_INPUT", "TEL_INPUT", "URL_INPUT",
                    "OTHER_INPUT", "TEXTAREA", "CONTENTEDITABLE", "COMBOBOX"),
            ActionType.SELECT_OPTION, Set.of("SELECT", "COMBOBOX", "LISTBOX", "CUSTOM_DROPDOWN", "OPTION"),
            ActionType.CHECK, Set.of("CHECKBOX", "RADIO", "SWITCH"),
            ActionType.UNCHECK, Set.of("CHECKBOX", "SWITCH"));

    static boolean unusual(ActionType action, String kind) {
        Set<String> usual = USUAL_KINDS.get(action);
        return usual != null && !ElementReference.UNKNOWN_KIND.equals(kind) && !usual.contains(kind);
    }

    private static List<Criterion> criteria(ActionType action, Target target, Applicability applicability) {
        if (applicability != Applicability.INSPECT_TARGET) return List.of();
        List<Criterion> c = new ArrayList<>(List.of(Criterion.CONTEXT_AUTHORIZED, Criterion.TARGET_IDENTITY_RESOLVED, Criterion.TARGET_UNIQUE,
                Criterion.TARGET_VISIBLE));
        switch (action) {
            case FILL, PRESS_KEY -> c.addAll(List.of(Criterion.TARGET_ENABLED, Criterion.TARGET_EDITABLE));
            case CLICK, SELECT_OPTION, CHECK, UNCHECK -> c.add(Criterion.TARGET_ENABLED);
            default -> {
            }
        }
        if (USUAL_KINDS.containsKey(action)) c.add(Criterion.TARGET_KIND_MATCHES_ACTION);
        if (target.accessPath().stream().anyMatch(p -> p.type() == AccessStep.Type.IFRAME)) c.add(Criterion.FRAME_CHAIN_REACHABLE);
        if (target.accessPath().stream().anyMatch(p -> p.type() == AccessStep.Type.SHADOW_ROOT)) c.add(Criterion.SHADOW_SCOPE_REACHABLE);
        return c;
    }

    /** Texto apresentável (mesma regra das telas); senão vazio — nunca "consertado". */
    private static String safe(String text) {
        return text == null || text.isBlank() || !DisplaySafety.isSafeToDisplay(text) ? "" : text;
    }

    public AutomationId automationId() {
        return automationId;
    }

    public String modelVersion() {
        return modelVersion;
    }

    /** Revisão do modelo de onde o plano foi projetado (muda a cada edição da automação). */
    public long automationRevision() {
        return automationRevision;
    }

    public String rulesVersion() {
        return rulesVersion;
    }

    /** Estado da validação ESTÁTICA — nunca sucesso de execução. */
    public ValidationStatus staticStatus() {
        return staticStatus;
    }

    public List<Variable> variables() {
        return variables;
    }

    public List<Step> steps() {
        return steps;
    }

    public java.util.Optional<Step> step(StepId id) {
        return steps.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    /**
     * Revisão do CONTEÚDO do plano: {@code sha256:} do JSON canônico sem este campo e sem a revisão da automação. Duas
     * projeções com o mesmo conteúdo têm a mesma revisão, mesmo que a automação tenha mudado em algo que o plano não
     * contém (ex.: o nome de uma etapa).
     */
    public String planRevision() {
        return planRevision;
    }

    /** JSON canônico ({@code nekkora-studio/guided-debug-plan/1.0.0}). */
    public String toJson() {
        return json;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GuidedPlan p && json.equals(p.json);
    }

    @Override
    public int hashCode() {
        return json.hashCode();
    }

    @Override
    public String toString() {
        return "GuidedPlan[" + steps.size() + " etapa(s), " + variables.size() + " variável(is)]";
    }
}
