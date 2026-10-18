package com.nekkora.studio.codegen;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepContext;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.privacy.CapturePrivacy;
import com.nekkora.studio.domain.target.DecisionActor;
import com.nekkora.studio.domain.target.TargetDecision;
import com.nekkora.studio.domain.variable.SemanticStatus;
import com.nekkora.studio.domain.variable.SemanticType;
import com.nekkora.studio.domain.variable.Sensitivity;
import com.nekkora.studio.domain.variable.VariableDraft;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.variable.VariableType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Caso SINTÉTICO de formulário repetido por registro (mesmo formato de um desafio de formulários): sessão 1 baixa a
 * planilha e inicia; sessão 2 preenche campos com rótulo irmão e envia; sessão 3 observa o resultado. Sem dados reais.
 */
final class FormCase {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC);
    static final List<String> LABELS = List.of("First Name", "Last Name", "Email");
    static final String ORIGIN = "http://127.0.0.1:18080";

    final Automation a;
    final List<StepId> ids = new ArrayList<>();
    StepId download;
    StepId start;
    StepId submit;
    StepId observe;

    private FormCase() {
        a = Automation.create("Formulário por registro (sintético)", "", CLOCK);
        LogicalSession prep = a.addSession("Preparação", null);
        LogicalSession rec = a.addSession("Preenchimento por registro", null);
        LogicalSession end = a.addSession("Conclusão", null);
        StepContext page = new StepContext("Navegador", "Desafio", ORIGIN + "/{…}");
        download = add(prep, ActionType.CLICK, page, el("LINK", "a", "Baixar planilha",
                new LocatorCandidate(LocatorStrategy.ROLE_NAME, "", "", LocatorStability.HEURISTIC_STABLE, 1, true)), null);
        start = add(prep, ActionType.CLICK, page, el("BUTTON", "button", "Start",
                new LocatorCandidate(LocatorStrategy.ROLE_NAME, "", "", LocatorStability.HEURISTIC_STABLE, 1, true)), null);
        for (String label : LABELS) {
            String var = label.toLowerCase().replace(' ', '_');
            a.declareVariable(VariableDraft.of(var, VariableType.STRING).withSemantic(SemanticType.FREE_TEXT, SemanticStatus.USER_CONFIRMED)
                    .withSensitivity(Sensitivity.PERSONAL_DATA).withOrigin(VariableOrigin.input()).withPurpose("coluna da planilha"));
            add(rec, ActionType.FILL, page, el("TEXT_INPUT", "input", "", new LocatorCandidate(LocatorStrategy.LABEL, "",
                    "//*[label[normalize-space(.)=\"" + label + "\"]]/input", LocatorStability.HEURISTIC_STABLE, 1, true)),
                    new ValueSource.VariableReference(var));
        }
        submit = add(rec, ActionType.CLICK, page, el("BUTTON", "input", "Submit",
                new LocatorCandidate(LocatorStrategy.CONTEXTUAL, "", "form input[type=\"submit\"]", LocatorStability.HEURISTIC_STABLE, 1, true)), null);
        observe = add(end, ActionType.OBSERVE, page, el("GENERIC", "div", "",
                new LocatorCandidate(LocatorStrategy.CONTEXTUAL, "", "div.congratulations > div:not(.message1)", LocatorStability.HEURISTIC_STABLE, 1, true)), null);
    }

    static FormCase confirmed() {
        FormCase c = new FormCase();
        for (StepId id : c.ids) c.decide(id);
        return c;
    }

    static FormCase undecided() {
        return new FormCase();
    }

    void decide(StepId id) {
        a.decideTarget(id, TargetDecision.Kind.CONFIRMED, DecisionActor.TEST_AGENT,
                new TargetDecision.Basis("TARGET_ID", "RESOLVED", List.of(), CLOCK.instant(), ""));
    }

    StepId fill(int i) {
        return ids.get(2 + i);
    }

    private StepId add(LogicalSession s, ActionType type, StepContext page, ElementReference el, ValueSource value) {
        StepDraft.Builder b = StepDraft.builder().name(type.label()).actionType(type).context(page).element(el);
        if (value != null) b.parameters(ActionParameters.withValue(value));
        AutomationStep step = a.appendStep(s.id(), b.build());
        ids.add(step.id());
        return step.id();
    }

    static ElementReference el(String kind, String tag, String name, LocatorCandidate preferred) {
        return ElementReference.builder().semanticKind(kind).tag(tag).accessibleName(name).privacyPolicy(CapturePrivacy.VERSION)
                .sourceContract("nekkora-pagemap/0.3.0").addLocator(preferred).preferLastLocator()
                .addLocator(new LocatorCandidate(LocatorStrategy.POSITIONAL, "", "form > div:nth-of-type(3) > " + tag, LocatorStability.HEURISTIC_UNSTABLE, 1, true))
                .addAccessStep(AccessStep.of(AccessStep.Type.MAIN_DOCUMENT, "documento principal"))
                .correlation(CorrelationKeys.of(Map.of(CorrelationKeys.Key.TARGET_ID, "tgt-" + Integer.toHexString(System.identityHashCode(preferred)))))
                .build();
    }

    static String config(String overrideJson) {
        String base = """
                {"schema": "nekkora-studio/codegen-config", "version": "1.0.0", "processName": "formulario_por_registro",
                 "environment": "lab", "allowedOrigins": ["%s"], "startUrl": "%s/desafio/index.html",
                 "sessions": {"init": [1], "perRecord": 2, "end": [3]}, "initAdvance": "REPLACED", "observations": true,
                 "input": {"format": "xlsx", "sheet": null, "source": "DOWNLOAD", "downloadStep": "%s", "fileName": "entrada.xlsx",
                   "columns": [{"header": "First Name", "variable": "first_name"}, {"header": "Last Name", "variable": "last_name"},
                               {"header": "Email", "variable": "email"}]}}
                """;
        return overrideJson == null ? base : overrideJson;
    }

    String config() {
        return String.format(config(null), ORIGIN, ORIGIN, download.value());
    }
}
