package com.nekkora.studio.pdd;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepContext;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepProvenance;
import com.nekkora.studio.domain.privacy.CapturePrivacy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Caso do laboratório da STUDIO-010 (dados FICTÍCIOS): "Consulta de registros" com Autenticação, Consulta e Resultado.
 * Os nomes de etapa e as notas trazem, de propósito, sentinelas de dado de teste que NUNCA podem chegar ao documento.
 */
final class PddLab {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC);
    static final String LOGIN = "http://127.0.0.1:18765/lab/studio010/login.html";
    static final String CONSULTA = "http://127.0.0.1:18765/lab/studio010/consulta.html";
    static final String RESULTADO = "http://127.0.0.1:18765/lab/studio010/resultado.html";
    /** Sentinelas: valores, variáveis, apelidos, seletores, ids e textos livres que não podem aparecer no documento. */
    static final List<String> SENTINELS = List.of("ana.souza", "Ana Souza", "123.456.789-09", "registro.codigo", "cofre.senha-lab", "S3nh@Lab",
            "REG-4321", "#usuario", "input#", "tgt-", "127.0.0.1", "studio010", "login.html", "nota interna", "Preencher usuário ana");

    private PddLab() {}

    static Automation consulta() {
        Automation a = Automation.create("Consulta de registros", "Processo de laboratório (fictício)", CLOCK);
        LogicalSession auth = a.addSession("Autenticação", "Entrar no laboratório");
        a.describeSession(auth.id(), "Entrar no laboratório", "nota interna: usuário ana.souza CPF 123.456.789-09");
        recorded(a, auth, "Preencher usuário ana.souza", ActionType.FILL, el("tgt-u", "TEXT_INPUT", "input", "Usuário", "#usuario"),
                ActionParameters.withValue(new ValueSource.LiteralValue("ana.souza")), LOGIN, "Entrar");
        recorded(a, auth, "Preencher senha S3nh@Lab", ActionType.FILL, el("tgt-s", "PASSWORD_INPUT", "input", "Senha", "#senha", true),
                ActionParameters.withValue(new ValueSource.CredentialReference("cofre.senha-lab")), LOGIN, "Entrar");
        recorded(a, auth, "Clicar em Entrar", ActionType.CLICK, el("tgt-e", "BUTTON", "button", "Entrar", "#entrar"), ActionParameters.none(), LOGIN, "Entrar");
        LogicalSession consulta = a.addSession("Consulta", "");
        recorded(a, consulta, "Código REG-4321", ActionType.FILL, el("tgt-c", "SEARCH_INPUT", "input", "Código do registro", "#codigo"),
                ActionParameters.withValue(new ValueSource.VariableReference("registro.codigo")), CONSULTA, "Consulta");
        recorded(a, consulta, "Consultar", ActionType.CLICK, el("tgt-k", "BUTTON", "button", "Consultar", "#consultar"), ActionParameters.none(), CONSULTA,
                "Consulta");
        LogicalSession resultado = a.addSession("Resultado", "");
        a.appendStep(resultado.id(), StepDraft.builder().name("Ver resultado de Ana Souza").actionType(ActionType.OBSERVE)
                .context(new StepContext("Laboratório", "Resultado", RESULTADO)).build());
        return a;
    }

    static AutomationStep recorded(Automation a, LogicalSession s, String name, ActionType type, ElementReference e, ActionParameters params, String url,
            String title) {
        return a.appendStep(s.id(), StepDraft.builder().name(name).actionType(type).element(e).parameters(params)
                .context(new StepContext("Laboratório", title, url))
                .provenance(StepProvenance.recorded(e.correlation().with(CorrelationKeys.Key.RECORDING_ID, "rec-1")
                                .with(CorrelationKeys.Key.RECORDED_EVENT_ID, "ev-" + Math.abs(name.hashCode())),
                        new RecordingObservation(RecordingObservation.CaptureMode.CAPTURE_AND_INTERACT, RecordingObservation.Observation.INTERACTION_OBSERVED,
                                null, List.of(), List.of())))
                .build());
    }

    static ElementReference el(String targetId, String kind, String tag, String name, String css) {
        return el(targetId, kind, tag, name, css, false);
    }

    static ElementReference el(String targetId, String kind, String tag, String name, String css, boolean sensitive) {
        return ElementReference.builder().semanticKind(kind).sensitive(sensitive).tag(tag).accessibleName(name).privacyPolicy(CapturePrivacy.VERSION)
                .addLocator(new LocatorCandidate(LocatorStrategy.ID, "", css, LocatorStability.HEURISTIC_STABLE, 1, true)).preferLastLocator()
                .correlation(CorrelationKeys.of(Map.of(CorrelationKeys.Key.TARGET_ID, targetId))).build();
    }
}
