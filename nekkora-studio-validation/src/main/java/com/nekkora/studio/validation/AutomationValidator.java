package com.nekkora.studio.validation;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.TargetRequirement;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.element.ManualLocator;
import com.nekkora.studio.domain.element.ObservedObstacle;
import com.nekkora.studio.domain.evidence.ObservationSummary;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.variable.SemanticStatus;
import com.nekkora.studio.domain.variable.SemanticType;
import com.nekkora.studio.domain.variable.VariableDefinition;
import com.nekkora.studio.domain.variable.VariableId;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.variable.VariableRules;
import com.nekkora.studio.domain.variable.VariableType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Automation Validation Engine (STUDIO-007): diagnóstico ESTRUTURAL de uma automação.
 *
 * <p>Responde se a automação tem as informações estruturais que as regras estáticas conseguem verificar — nunca se ela
 * funciona. Não executa ações, não consulta navegador, Native Host, disco ou rede, não usa a interface e não modifica a
 * automação (só lê). As regras se apoiam nos metadados que o domínio já tem: {@link ActionType} ({@link
 * TargetRequirement}, {@code acceptsValue}, {@code acceptsOption}), {@link ActionParameters}/{@link ValueSource}, {@link
 * ElementReference}/{@link LocatorCandidate}/{@link ManualLocator}, os obstáculos e limitações registrados na captura e o
 * {@link ObservationSummary} da STUDIO-005.
 *
 * <p>Distinções preservadas: localizador validado na captura não significa elemento disponível hoje; continuidade técnica
 * de um nó não significa o mesmo alvo funcional; nome ou localizador omitido pela privacidade não é defeito; evidência não
 * é resultado de negócio e não resolve bloqueios; referência de variável ou credencial não comprova resolução.
 *
 * <p>Regras 1.1.0 (STUDIO-008, {@link RuleSet#V1_1}): com o catálogo de variáveis, uma referência deixa de ser
 * "resolução indisponível" e passa a ser conferida contra a DECLARAÇÃO — nome exato, escopo, tipo técnico compatível com
 * a ação, origem declarada e, para saída de etapa, a ordem da produtora declarada ({@link DataFlowAnalysis}). O Studio
 * nunca comprova que um valor existirá: entrada e saída de etapa ficam como limitação da avaliação, não como garantia.
 *
 * <p>Uso: {@code ValidationReport report = new AutomationValidator(RuleSet.LATEST).validate(automation);} — na thread que
 * é dona da automação (no Studio, a EDT). {@code new AutomationValidator()} usa as regras 1.0.0 (contrato congelado). O
 * validador não guarda estado: pode ser reutilizado.
 */
public final class AutomationValidator {
    /** Versão das regras 1.0.0 (vai no relatório). */
    public static final String RULES_VERSION = "nekkora-validation-rules/1.0.0";
    /** Versão das regras 1.1.0 (STUDIO-008). */
    public static final String RULES_VERSION_1_1 = "nekkora-validation-rules/1.1.0";

    private final RuleSet rules;

    /** Regras 1.0.0 (o relatório 1.0.0 congelado). */
    public AutomationValidator() {
        this(RuleSet.V1_0);
    }

    public AutomationValidator(RuleSet rules) {
        this.rules = java.util.Objects.requireNonNull(rules, "rules");
    }

    public RuleSet rules() {
        return rules;
    }

    /** Teclas reconhecidas para {@link ActionType#PRESS_KEY} (nomes de {@code KeyboardEvent.key}). */
    static final Set<String> KNOWN_KEYS = Set.of("Enter", "Tab", "Escape", "Backspace", "Delete", "Insert", "Space", " ", "ArrowUp", "ArrowDown",
            "ArrowLeft", "ArrowRight", "Home", "End", "PageUp", "PageDown", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12");

    public ValidationReport validate(Automation automation) {
        List<Finding> out = new ArrayList<>();
        String aid = automation.id().value().toString();
        if (automation.sessions().isEmpty()) {
            out.add(new Finding(id(FindingCode.AUTOMATION_HAS_NO_SESSIONS, aid, null), FindingCode.AUTOMATION_HAS_NO_SESSIONS, FindingScope.AUTOMATION,
                    null, null, 0, 0, null));
        } else if (automation.stepCount() == 0) {
            out.add(new Finding(id(FindingCode.AUTOMATION_HAS_NO_STEPS, aid, null), FindingCode.AUTOMATION_HAS_NO_STEPS, FindingScope.AUTOMATION,
                    null, null, 0, 0, null));
        }
        if (!automation.unattributedEvidence().isEmpty()) {
            out.add(new Finding(id(FindingCode.UNATTRIBUTED_EVIDENCE, aid, null), FindingCode.UNATTRIBUTED_EVIDENCE, FindingScope.AUTOMATION,
                    null, null, 0, 0, null));
        }
        DataFlowAnalysis flow = rules.includes(RuleSet.V1_1) ? DataFlowAnalysis.of(automation) : null;
        if (flow != null) variables(automation, flow, out);
        int steps = 0;
        List<LogicalSession> sessions = automation.sessions();
        for (int s = 0; s < sessions.size(); s++) {
            LogicalSession session = sessions.get(s);
            if (session.isEmpty()) {
                out.add(new Finding(id(FindingCode.SESSION_EMPTY, session.id().value().toString(), null), FindingCode.SESSION_EMPTY,
                        FindingScope.SESSION, session.id(), null, s + 1, 0, null));
            }
            List<AutomationStep> list = session.steps();
            for (int i = 0; i < list.size(); i++) {
                steps++;
                out.addAll(step(session, s + 1, list.get(i), i + 1, flow));
            }
        }
        return new ValidationReport(automation.id(), rules, sessions.size(), steps, out);
    }

    // ------------------------------------------------------------------ variável (regras 1.1.0)

    /** Apontamentos de cada variável declarada, na ordem do catálogo (depois da automação, antes das sessões). */
    private static void variables(Automation automation, DataFlowAnalysis flow, List<Finding> out) {
        Map<SessionId, Integer> numbers = new HashMap<>();
        for (int s = 0; s < automation.sessions().size(); s++) numbers.put(automation.sessions().get(s).id(), s + 1);
        for (VariableDefinition v : automation.variables()) {
            List<FindingCode> codes = new ArrayList<>();
            if (!flow.scopeExists(v)) codes.add(FindingCode.VARIABLE_SCOPE_SESSION_MISSING);
            switch (flow.producer(v)) {
                case MISSING -> codes.add(FindingCode.VARIABLE_PRODUCER_MISSING);
                case OUT_OF_SCOPE -> codes.add(FindingCode.VARIABLE_PRODUCER_OUT_OF_SCOPE);
                default -> {
                }
            }
            ValidationProblem problem = VariableRules.problem(v).orElse(null);
            boolean sensitiveConstant = problem == ValidationProblem.VARIABLE_CONSTANT_FORBIDDEN || problem == ValidationProblem.VARIABLE_CONSTANT_LOOKS_SENSITIVE;
            if (sensitiveConstant) codes.add(FindingCode.SENSITIVE_CONSTANT_FORBIDDEN);
            else if (problem != null) codes.add(FindingCode.VARIABLE_DECLARATION_INCONSISTENT);
            if (v.authority() == SemanticStatus.PROPOSED_UNCONFIRMED) codes.add(FindingCode.VARIABLE_SEMANTIC_UNCONFIRMED);
            if (v.semantic() == SemanticType.UNSPECIFIED) codes.add(FindingCode.VARIABLE_SEMANTIC_UNSPECIFIED);
            if (v.origin().kind() == VariableOrigin.Kind.CONSTANT && !sensitiveConstant) codes.add(FindingCode.CONSTANT_STORED_UNENCRYPTED);
            if (flow.consumersOf(v.id()).isEmpty()) codes.add(FindingCode.VARIABLE_UNUSED);
            SessionId session = v.scope().sessionId().filter(numbers::containsKey).orElse(null);
            int sessionNumber = session == null ? 0 : numbers.get(session);
            String vid = v.id().value().toString();
            codes.stream().sorted(Comparator.comparing(FindingCode::ordinal)).forEach(c -> out.add(new Finding(id(c, vid, null), c, FindingScope.VARIABLE,
                    session, null, sessionNumber, 0, null, v.id())));
        }
    }

    // ------------------------------------------------------------------ etapa

    private List<Finding> step(LogicalSession session, int sessionNumber, AutomationStep step, int stepNumber, DataFlowAnalysis flow) {
        Set<Hit> hits = new LinkedHashSet<>();
        ActionType type = step.actionType();
        action(type, hits);
        parameters(step, session, type, flow, hits);
        element(step, type, hits);
        recorderLimitations(step, type, hits);
        observation(step, hits);
        String sid = step.id().value().toString();
        return hits.stream().sorted(Comparator.comparing((Hit h) -> h.code().ordinal()).thenComparing(h -> h.detail() == null ? "" : h.detail()))
                .map(h -> new Finding(id(h.code(), sid, h.detail()), h.code(), FindingScope.STEP, session.id(), step.id(), sessionNumber, stepNumber,
                        h.detail(), h.variable()))
                .toList();
    }

    /** Achado de uma regra sobre a etapa (antes da ordenação); {@code variable}: a variável declarada envolvida (1.1.0). */
    private record Hit(FindingCode code, String detail, VariableId variable) {
        Hit(FindingCode code) {
            this(code, null, null);
        }

        Hit(FindingCode code, String detail) {
            this(code, detail, null);
        }

        Hit(FindingCode code, VariableId variable) {
            this(code, null, variable);
        }
    }

    private static void action(ActionType type, Set<Hit> hits) {
        switch (type) {
            case UNKNOWN -> hits.add(new Hit(FindingCode.ACTION_UNCLASSIFIED));
            case WAIT_FOR -> hits.add(new Hit(FindingCode.WAIT_CONDITION_NOT_MODELED));
            case OBSERVE -> hits.add(new Hit(FindingCode.OBSERVE_IS_NOT_VALIDATION));
            default -> {
            }
        }
    }

    private static void parameters(AutomationStep step, LogicalSession session, ActionType type, DataFlowAnalysis flow, Set<Hit> hits) {
        ActionParameters p = step.parameters();
        ValueSource value = p.value().orElse(null);
        if ((value != null && !type.acceptsValue()) || (p.option().isPresent() && !type.acceptsOption())) {
            hits.add(new Hit(FindingCode.PARAMETER_NOT_APPLICABLE));
        }
        switch (type) {
            case FILL -> {
                if (value == null) hits.add(new Hit(FindingCode.REQUIRED_VALUE_MISSING));
            }
            case PRESS_KEY -> {
                if (value == null) hits.add(new Hit(FindingCode.KEY_NOT_CONFIGURED));
                else if (value instanceof ValueSource.LiteralValue l && !KNOWN_KEYS.contains(l.text())) hits.add(new Hit(FindingCode.KEY_NOT_RECOGNIZED));
            }
            case NAVIGATE -> {
                if (value == null) {
                    hits.add(new Hit(FindingCode.NAVIGATION_TARGET_MISSING));
                } else if (value instanceof ValueSource.LiteralValue l) {
                    String t = l.text().strip().toLowerCase(Locale.ROOT);
                    if (!t.startsWith("http://") && !t.startsWith("https://")) hits.add(new Hit(FindingCode.NAVIGATION_TARGET_NOT_ADDRESS));
                }
            }
            case SELECT_OPTION -> {
                if (p.option().isEmpty()) hits.add(new Hit(FindingCode.OPTION_NOT_CONFIGURED));
            }
            default -> {
            }
        }
        // dependências: a referência existe, a resolução não pode ser comprovada (nada é consultado nem inventado)
        if (value instanceof ValueSource.VariableReference) {
            if (flow == null) hits.add(new Hit(FindingCode.VARIABLE_RESOLUTION_UNAVAILABLE));
            else variableUse(step, session, type, flow, hits);
            if (step.element().map(ElementReference::sensitive).orElse(false)) hits.add(new Hit(FindingCode.SENSITIVE_FIELD_WITHOUT_CREDENTIAL));
        } else if (value instanceof ValueSource.CredentialReference) {
            hits.add(new Hit(FindingCode.CREDENTIAL_RESOLUTION_UNAVAILABLE));
        }
    }

    /**
     * Regras 1.1.0: a referência conferida contra a declaração. Nome exato (sem aproximação), escopo, tipo, origem
     * declarada e — para saída de etapa — a posição da produtora DECLARADA na ordem linear.
     */
    private static void variableUse(AutomationStep step, LogicalSession session, ActionType type, DataFlowAnalysis flow, Set<Hit> hits) {
        DataFlowAnalysis.Consumption c = flow.consumptionAt(step.id()).orElseThrow();
        VariableDefinition v = c.variable();
        if (v == null) {
            hits.add(new Hit(FindingCode.VARIABLE_NOT_DECLARED));
            return;
        }
        VariableId id = v.id();
        if (!v.scope().visibleIn(session.id())) hits.add(new Hit(FindingCode.VARIABLE_OUT_OF_SCOPE, id));
        if (!accepts(type, v.type())) hits.add(new Hit(FindingCode.VARIABLE_TYPE_INCOMPATIBLE, id));
        switch (v.origin().kind()) {
            case UNBOUND -> hits.add(new Hit(FindingCode.VARIABLE_ORIGIN_UNBOUND, id));
            case INPUT -> hits.add(new Hit(FindingCode.INPUT_VALUE_NOT_AVAILABLE, id));
            case CONSTANT -> {
                // valor fixo declarado (não sensível, conferido pelo domínio): nada a apontar na etapa
            }
            case STEP_OUTPUT -> {
                if (flow.producer(v) != DataFlowAnalysis.ProducerState.PRESENT) {
                    hits.add(new Hit(FindingCode.DECLARED_PRODUCER_MISSING, id));
                    return;
                }
                int producer = flow.order(v.origin().producer());
                if (producer == c.order()) {
                    hits.add(new Hit(FindingCode.STEP_OUTPUT_SELF_CONSUMED, id));
                    return;
                }
                if (producer > c.order()) hits.add(new Hit(FindingCode.DECLARED_PRODUCER_AFTER_CONSUMER, id));
                else hits.add(new Hit(FindingCode.STEP_OUTPUT_NOT_PROVEN, id));
                if (flow.cycleSteps().contains(step.id())) hits.add(new Hit(FindingCode.DATA_DEPENDENCY_CYCLE, id));
            }
        }
    }

    /**
     * Tipo técnico aceito pelo valor da ação: preencher aceita texto, número e data (o valor é digitado); navegar e
     * pressionar tecla, só texto. Ações que não usam valor já recebem {@link FindingCode#PARAMETER_NOT_APPLICABLE}.
     */
    static boolean accepts(ActionType action, VariableType type) {
        return switch (action) {
            case FILL -> type != VariableType.BOOLEAN;
            case NAVIGATE, PRESS_KEY -> type == VariableType.STRING;
            default -> true;
        };
    }

    private static void element(AutomationStep step, ActionType type, Set<Hit> hits) {
        ElementReference e = step.element().orElse(null);
        if (e == null) {
            if (type.target() == TargetRequirement.EXPECTED) hits.add(new Hit(FindingCode.ELEMENT_MISSING));
            return; // localizadores manuais exigem elemento capturado (domínio): sem elemento, não há o que avaliar
        }
        if (type.target() == TargetRequirement.NONE) return; // ex.: navegar — o elemento não é usado pela ação
        List<LocatorCandidate> captured = e.locators();
        List<ManualLocator> manual = step.manualLocators();
        if (captured.isEmpty()) {
            hits.add(new Hit(manual.isEmpty() ? FindingCode.LOCATOR_MISSING : FindingCode.MANUAL_LOCATOR_ONLY));
        } else {
            LocatorCandidate reference = e.preferredLocator().orElse(null);
            if (reference == null) {
                reference = captured.stream().filter(LocatorCandidate::validated).findFirst().orElse(null);
                if (reference != null) hits.add(new Hit(FindingCode.LOCATOR_PREFERENCE_NOT_SET));
            }
            if (reference == null) {
                boolean ambiguous = captured.stream().anyMatch(c -> c.matchCount() > 1);
                hits.add(new Hit(ambiguous ? FindingCode.LOCATOR_MULTIPLE_MATCHES : FindingCode.LOCATOR_NOT_VALIDATED));
            } else {
                hits.add(new Hit(FindingCode.LOCATOR_VALIDATED_AT_CAPTURE)); // histórico: a página real não foi consultada
                if (reference.stability() == LocatorStability.UNKNOWN) hits.add(new Hit(FindingCode.LOCATOR_STABILITY_UNKNOWN));
                if (reference.stability() == LocatorStability.HEURISTIC_UNSTABLE || reference.stability() == LocatorStability.OBSERVED_UNSTABLE) {
                    hits.add(new Hit(FindingCode.LOCATOR_UNSTABLE));
                }
                if (reference.strategy() == LocatorStrategy.POSITIONAL) hits.add(new Hit(FindingCode.LOCATOR_POSITIONAL));
            }
            if (!manual.isEmpty()) hits.add(new Hit(FindingCode.MANUAL_LOCATOR_UNVERIFIED));
        }
        // nome acessível vazio ou id/name omitidos pela privacidade NÃO geram apontamento: não são defeito da captura
        for (ObservedObstacle o : e.obstacles()) {
            FindingCode code = switch (o.severity()) {
                case HIGH -> FindingCode.OBSTACLE_HIGH;
                case MEDIUM -> FindingCode.OBSTACLE_MEDIUM;
                case LOW -> FindingCode.OBSTACLE_LOW;
            };
            hits.add(new Hit(code, FindingDetails.obstacle(o.type())));
        }
    }

    /** Limitações declaradas pelo Recorder na captura (códigos estáveis do protocolo; nunca texto livre). */
    private static void recorderLimitations(AutomationStep step, ActionType type, Set<Hit> hits) {
        RecordingObservation r = step.provenance().recordingObservation().orElse(null);
        if (r == null) return;
        for (String code : r.limitations()) {
            switch (code) {
                case "NO_UNIQUE_LOCATOR" -> hits.add(new Hit(FindingCode.RECORDER_NO_UNIQUE_LOCATOR));
                case "SAFE_LOCATOR_UNAVAILABLE" -> hits.add(new Hit(FindingCode.RECORDER_SAFE_LOCATOR_UNAVAILABLE));
                case "LIVE_CHECK_UNAVAILABLE" -> hits.add(new Hit(FindingCode.RECORDER_LIVE_CHECK_UNAVAILABLE));
                case "LOCATORS_WITHHELD" -> hits.add(new Hit(FindingCode.PRIVACY_LOCATORS_WITHHELD));
                case "OPTION_WITHHELD" -> {
                    // só explica uma opção que AINDA falta; depois de configurada, não há o que apontar
                    if (type.acceptsOption() && step.parameters().option().isEmpty()) hits.add(new Hit(FindingCode.PRIVACY_OPTION_WITHHELD));
                }
                default -> {
                    // ELEMENT_UNRESOLVED, AMBIGUOUS_TARGET… já chegam como obstáculos do elemento; os demais não são de configuração
                }
            }
        }
    }

    /** STUDIO-005: o que foi observado é informação; nunca resultado de negócio, nunca resolve nem cria bloqueio. */
    private static void observation(AutomationStep step, Set<Hit> hits) {
        ObservationSummary s = ObservationSummary.of(step);
        switch (s.status()) {
            case OBSERVED -> hits.add(new Hit(FindingCode.OBSERVATION_SIGNALS));
            case INCOMPLETE -> hits.add(new Hit(FindingCode.OBSERVATION_INCOMPLETE));
            case IN_PROGRESS -> hits.add(new Hit(FindingCode.OBSERVATION_IN_PROGRESS));
            default -> {
                // não se aplica, indisponível ou nada observado: ausência de evidência não é configuração incorreta
            }
        }
        if (s.error()) hits.add(new Hit(FindingCode.OBSERVATION_ERROR_INDICATOR));
    }

    private static String id(FindingCode code, String subject, String detail) {
        return code.name() + ":" + subject + (detail == null ? "" : ":" + detail);
    }
}
