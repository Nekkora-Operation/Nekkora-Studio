package com.nekkora.studio.guided;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepContext;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.element.ContextAnchor;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepProvenance;
import com.nekkora.studio.domain.privacy.CapturePrivacy;
import com.nekkora.studio.domain.variable.SemanticStatus;
import com.nekkora.studio.domain.variable.SemanticType;
import com.nekkora.studio.domain.variable.Sensitivity;
import com.nekkora.studio.domain.variable.VariableDraft;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.variable.VariableType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Caso de laboratório da STUDIO-009, montado como o Recorder o entregaria: "Consulta de registro" — abrir a página,
 * preencher o código do registro (variável de entrada), clicar em Consultar e aguardar o resultado. Dados 100% fictícios;
 * ids técnicos inventados no formato da extensão.
 */
final class LabCase {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC);
    static final String LITERAL_URL = "http://127.0.0.1:8765/studio009/consulta.html";

    private LabCase() {}

    static Automation consulta() {
        Automation a = Automation.create("Consulta de registro (laboratório)", "Caso vertical da STUDIO-009", CLOCK);
        LogicalSession s = a.addSession("Consulta", null);
        a.declareVariable(VariableDraft.of("registro.codigo", VariableType.STRING).withSemantic(SemanticType.IDENTIFIER, SemanticStatus.USER_CONFIRMED)
                .withSensitivity(Sensitivity.INTERNAL).withOrigin(VariableOrigin.input()).withPurpose("Código fictício do registro consultado"));
        StepContext page = new StepContext("Navegador", "Consulta de registro", "http://127.0.0.1:8765/studio009/{…}");
        a.appendStep(s.id(), StepDraft.builder().name("Abrir a consulta").actionType(ActionType.NAVIGATE)
                .parameters(ActionParameters.withValue(new ValueSource.LiteralValue(LITERAL_URL))).context(page).build());
        a.appendStep(s.id(), recorded("Preencher código do registro", ActionType.FILL, "ev-1", page,
                field("tgt-3f6c1a", "SEARCH_INPUT", "input", "Código do registro", "#registro-codigo", LocatorStrategy.ID))
                .parameters(ActionParameters.withValue(new ValueSource.VariableReference("registro.codigo"))).build());
        a.appendStep(s.id(), recorded("Consultar", ActionType.CLICK, "ev-2", page,
                field("tgt-8d02b7", "BUTTON", "button", "Consultar", "[data-testid=\"consultar\"]", LocatorStrategy.TEST_ATTRIBUTE)).build());
        a.appendStep(s.id(), StepDraft.builder().name("Aguardar resultado").actionType(ActionType.WAIT_FOR).context(page)
                .element(descriptorOnly()).build());
        return a;
    }

    static StepDraft.Builder recorded(String name, ActionType type, String event, StepContext context, ElementReference element) {
        Map<CorrelationKeys.Key, String> keys = new EnumMap<>(CorrelationKeys.Key.class);
        keys.put(CorrelationKeys.Key.RECORDING_ID, "rec-lab-009");
        keys.put(CorrelationKeys.Key.RECORDED_EVENT_ID, event);
        return StepDraft.builder().name(name).actionType(type).context(context).element(element).provenance(StepProvenance.recorded(
                CorrelationKeys.of(keys), new RecordingObservation(RecordingObservation.CaptureMode.CAPTURE_AND_INTERACT,
                        RecordingObservation.Observation.INTERACTION_OBSERVED, null, List.of(), List.of())));
    }

    /** Referência como a do Recorder: identidade lógica registrada, localizador validado preferido, nome do vocabulário. */
    static ElementReference field(String targetId, String kind, String tag, String name, String css, LocatorStrategy strategy) {
        return ElementReference.builder().semanticKind(kind).tag(tag).accessibleName(name).privacyPolicy(CapturePrivacy.VERSION)
                .sourceContract("nekkora-pagemap/0.3.0")
                .addLocator(new LocatorCandidate(strategy, "", css, LocatorStability.HEURISTIC_STABLE, 1, true)).preferLastLocator()
                .addLocator(new LocatorCandidate(LocatorStrategy.POSITIONAL, "", "main > form > " + tag + ":nth-child(2)", LocatorStability.HEURISTIC_UNSTABLE,
                        1, true))
                .addAccessStep(AccessStep.of(AccessStep.Type.MAIN_DOCUMENT, "documento principal"))
                .addContext(new ContextAnchor("IN_FORM", "FORM", "Consulta", ""))
                .correlation(CorrelationKeys.of(Map.of(CorrelationKeys.Key.TARGET_ID, targetId, CorrelationKeys.Key.NODE_ID, "n-41",
                        CorrelationKeys.Key.CAPTURE_ID, "cap-7")))
                .build();
    }

    /** Referência criada sem identidade lógica (ex.: informada à mão): só o descritor, sem localizador validado. */
    static ElementReference descriptorOnly() {
        return ElementReference.builder().semanticKind("STATUS").tag("div").accessibleName("Resultado da consulta").privacyPolicy(CapturePrivacy.VERSION)
                .addLocator(new LocatorCandidate(LocatorStrategy.ROLE_NAME, "", "[role=\"status\"]", LocatorStability.UNKNOWN, 2, true))
                .build();
    }
}
