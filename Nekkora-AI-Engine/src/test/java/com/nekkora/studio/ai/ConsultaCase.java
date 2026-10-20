package com.nekkora.studio.ai;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepContext;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.privacy.CapturePrivacy;
import com.nekkora.studio.domain.target.DecisionActor;
import com.nekkora.studio.domain.target.TargetDecision;
import com.nekkora.studio.pdd.DocText;
import com.nekkora.studio.pdd.DocumentDraft;
import com.nekkora.studio.pdd.DraftBuilder;
import com.nekkora.studio.pdd.DraftEdits;
import com.nekkora.studio.pdd.ProcessProjection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Caso SINTÉTICO do Caso A (sem planilha): login (usuário, senha, Entrar) → consulta (código do registro, Consultar) →
 * ler o resultado (Observar) → Sair. Como o Recorder grava: preenchimentos SEM valor; alvos confirmados no debug guiado.
 */
public final class ConsultaCase {
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC);
    public static final String ORIGIN = "http://127.0.0.1:18765";

    public final Automation a;
    public final List<StepId> ids = new ArrayList<>();
    public StepId user;
    public StepId password;
    public StepId enter;
    public StepId code;
    public StepId query;
    public StepId result;
    public StepId exit;

    private ConsultaCase(String queryLabel, boolean withObserve) {
        this(queryLabel, withObserve, ORIGIN);
    }

    /** O mesmo caso gravado em outra origem (ex.: site público de treinamento https). */
    public static ConsultaCase confirmedAt(String origin) {
        ConsultaCase c = new ConsultaCase("Consultar", true, origin);
        c.ids.forEach(c::decide);
        return c;
    }

    private ConsultaCase(String queryLabel, boolean withObserve, String origin) {
        a = Automation.create("Consulta de registro (sintética)", "", CLOCK);
        LogicalSession login = a.addSession("Acesso", null);
        LogicalSession consulta = a.addSession("Consulta", null);
        StepContext p1 = new StepContext("Navegador", "Portal — entrar", origin + "/lab/studio014/portal.html");
        StepContext p2 = new StepContext("Navegador", "Portal — consulta", origin + "/lab/studio014/portal.html?tela=consulta");
        user = add(login, ActionType.FILL, p1, el("TEXT_INPUT", "input", "Usuário", "#usuario", false));
        password = add(login, ActionType.FILL, p1, el("TEXT_INPUT", "input", "Senha", "#senha", true));
        enter = add(login, ActionType.CLICK, p1, el("BUTTON", "button", "Entrar", "#entrar", false));
        code = add(consulta, ActionType.FILL, p2, el("SEARCH_INPUT", "input", "Código do registro", "#registro-codigo", false));
        query = add(consulta, ActionType.CLICK, p2, el("BUTTON", "button", queryLabel, "#consultar", false));
        if (withObserve) result = add(consulta, ActionType.OBSERVE, p2, el("GENERIC", "div", "Resultado da consulta", "#resultado", false));
        exit = add(consulta, ActionType.CLICK, p2, el("BUTTON", "button", "Sair", "#sair", false));
    }

    public static ConsultaCase confirmed() {
        ConsultaCase c = new ConsultaCase("Consultar", true);
        c.ids.forEach(c::decide);
        return c;
    }

    /** O botão de consulta tem um rótulo fora do vocabulário (o efeito precisa de uma decisão). */
    public static ConsultaCase unknownButton() {
        ConsultaCase c = new ConsultaCase("Processar", true);
        c.ids.forEach(c::decide);
        return c;
    }

    /** Sem etapa que leia o resultado (conclusão observável ausente). */
    public static ConsultaCase withoutObservation() {
        ConsultaCase c = new ConsultaCase("Consultar", false);
        c.ids.forEach(c::decide);
        return c;
    }

    public void decide(StepId id) {
        a.decideTarget(id, TargetDecision.Kind.CONFIRMED, DecisionActor.TEST_AGENT, new TargetDecision.Basis("TARGET_ID", "RESOLVED", List.of(), CLOCK.instant(), ""));
    }

    /** Rascunho do PDD da revisão atual com TODOS os textos revisados (como a pessoa faria no editor). */
    public DocumentDraft reviewedDraft() {
        ProcessProjection p = ProcessProjection.of(a);
        DocumentDraft d = DraftBuilder.build(p);
        for (DocumentDraft.Section s : d.sections()) {
            if (s.title().state() == DocText.State.NEEDS_REVIEW) d = DraftEdits.reviseSectionTitle(d, s.number(), "Etapa do processo", "TEST_AGENT");
        }
        for (DocumentDraft.Block b : d.blocks()) {
            if (b.title().state() == DocText.State.NEEDS_REVIEW) d = DraftEdits.reviseBlockTitle(d, b.blockId(), "Bloco do processo", "TEST_AGENT");
            for (DocumentDraft.Instruction i : b.instructions()) {
                if (i.text().state() == DocText.State.NEEDS_REVIEW) d = DraftEdits.reviseInstruction(d, b.blockId(), i.ordinal(), "Informar o campo", "TEST_AGENT");
            }
        }
        return d;
    }

    private StepId add(LogicalSession s, ActionType type, StepContext page, ElementReference el) {
        AutomationStep step = a.appendStep(s.id(), StepDraft.builder().name(type.label()).actionType(type).context(page).element(el).build());
        ids.add(step.id());
        return step.id();
    }

    static ElementReference el(String kind, String tag, String name, String css, boolean sensitive) {
        LocatorCandidate preferred = new LocatorCandidate(LocatorStrategy.ID, "", css, LocatorStability.OBSERVED_STABLE, 1, true);
        return ElementReference.builder().semanticKind(kind).tag(tag).accessibleName(name).sensitive(sensitive).privacyPolicy(CapturePrivacy.VERSION)
                .sourceContract("nekkora-pagemap/0.3.0").addLocator(preferred).preferLastLocator()
                .addAccessStep(AccessStep.of(AccessStep.Type.MAIN_DOCUMENT, "documento principal"))
                .correlation(CorrelationKeys.of(Map.of(CorrelationKeys.Key.TARGET_ID, "tgt-" + css.substring(1))))
                .build();
    }
}
