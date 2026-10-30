package com.nekkora.studio.ai;

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
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepProvenance;
import com.nekkora.studio.domain.privacy.CapturePrivacy;
import com.nekkora.studio.domain.target.DecisionActor;
import com.nekkora.studio.domain.target.TargetDecision;
import com.nekkora.studio.domain.variable.VariableDraft;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.variable.VariableType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * NEKKORA-E2E-002 — caso SINTÉTICO (pessoas fictícias) da leitura de fichas, como o Recorder grava: início (Navegar),
 * busca (Preencher sem valor + Enter que mudou de documento), leitura do ENDEREÇO conferido com a entrada, do título e de
 * células localizadas pelo CABEÇALHO DA LINHA (modo "Selecionar dado"), inclusive duas fontes alternativas. Alvos
 * confirmados no debug guiado. Origem do laboratório genérico do framework ({@code /leitura/}).
 */
public final class FichaCase {
    public static final String ORIGIN = "http://127.0.0.1:18766";

    public final Automation a;
    public final List<StepId> ids = new ArrayList<>();
    public StepId start;
    public StepId search;
    public StepId enter;
    public StepId address;
    public StepId title;
    public StepId fullName;
    public StepId born;
    public StepId died;
    public StepId nationality;
    public StepId occupation;
    public StepId fields;

    private FichaCase() {
        a = Automation.create("Fichas do acervo (sintética)", "", ConsultaCase.CLOCK);
        LogicalSession ini = a.addSession("Inicialização", null);
        LogicalSession pes = a.addSession("Pesquisa", null);
        LogicalSession ext = a.addSession("Extração", null);
        StepContext busca = new StepContext("Navegador", "Busca — Acervo", ORIGIN + "/leitura/busca.html");
        StepContext ficha = new StepContext("Navegador", "", ORIGIN + "/leitura/{…}");
        start = add(ini, StepDraft.builder().name("Abrir a busca").actionType(ActionType.NAVIGATE).context(busca)
                .parameters(ActionParameters.withValue(new ValueSource.LiteralValue(ORIGIN + "/leitura/busca.html"))).build(), false);
        ElementReference field = el("COMBOBOX", "input", "", LocatorStrategy.CONTEXTUAL, "form#buscaform input[name=\"q\"]", "busca");
        search = add(pes, StepDraft.builder().name("Preencher campo").actionType(ActionType.FILL).context(busca).element(field).build(), true);
        enter = add(pes, StepDraft.builder().name("Pressionar Enter").actionType(ActionType.PRESS_KEY).context(busca).element(field)
                .parameters(ActionParameters.withValue(new ValueSource.LiteralValue("Enter")))
                .provenance(StepProvenance.recorded(CorrelationKeys.of(Map.of(CorrelationKeys.Key.TARGET_ID, "tgt-busca")),
                        new RecordingObservation(RecordingObservation.CaptureMode.CAPTURE_AND_INTERACT, RecordingObservation.Observation.INTERACTION_OBSERVED,
                                RecordingObservation.BusinessOutcome.NOT_VALIDATED, List.of("FORM_SUBMITTED", "NAVIGATION_FOLLOWED"), List.of())))
                .build(), true);
        a.declareVariable(VariableDraft.of("endereco_esperado", VariableType.STRING).withOrigin(VariableOrigin.input()).withPurpose("Endereço da ficha esperada"));
        address = add(ext, StepDraft.builder().name("Ler o endereço da página").actionType(ActionType.OBSERVE).context(ficha)
                .parameters(ActionParameters.withValue(new ValueSource.VariableReference("endereco_esperado"))).build(), false);
        title = add(ext, read(ficha, el("HEADING", "h1", "", LocatorStrategy.ID, "h1#titulo", "titulo")), true);
        fullName = add(ext, read(ficha, cell("Nome completo")), true);
        born = add(ext, read(ficha, cell("Nascimento")), true);
        died = add(ext, read(ficha, cell("Morte")), true);
        nationality = add(ext, read(ficha, cell("Nacionalidade")), true);
        occupation = add(ext, read(ficha, cell("Ocupação")), true);
        fields = add(ext, read(ficha, cell("Campo(s)")), true);
    }

    public static FichaCase confirmed() {
        FichaCase c = new FichaCase();
        c.ids.forEach(c::decide);
        return c;
    }

    /**
     * Rascunho do PDD da revisão atual com os textos revisados como a pessoa faria no editor (os mesmos títulos do PDD da
     * E2E-002): seções "Inicialização do processo", "Pesquisa da pessoa" e "Extração dos dados"; blocos "Abrir a busca do
     * acervo", "Pesquisar a pessoa pelo nome", "Ler o endereço e os dados da ficha" e, nas leituras, o genérico "Etapas do
     * processo" (o código cai no nome pelas ações).
     */
    public com.nekkora.studio.pdd.DocumentDraft reviewedDraft() {
        com.nekkora.studio.pdd.DocumentDraft d = com.nekkora.studio.pdd.DraftBuilder.build(com.nekkora.studio.pdd.ProcessProjection.of(a));
        String[] sections = {"Inicialização do processo", "Pesquisa da pessoa", "Extração dos dados"};
        for (var s : d.sections()) {
            d = com.nekkora.studio.pdd.DraftEdits.reviseSectionTitle(d, s.number(), sections[s.number() - 1], "TEST_AGENT");
        }
        for (var b : d.blocks()) {
            String title = b.stepIds().contains(start) ? "Abrir a busca do acervo" : b.stepIds().contains(search) ? "Pesquisar a pessoa pelo nome"
                    : b.stepIds().contains(address) ? "Ler o endereço e os dados da ficha" : "Etapas do processo";
            d = com.nekkora.studio.pdd.DraftEdits.reviseBlockTitle(d, b.blockId(), title, "TEST_AGENT");
            for (var i : b.instructions()) {
                if (i.text().state() == com.nekkora.studio.pdd.DocText.State.NEEDS_REVIEW) {
                    d = com.nekkora.studio.pdd.DraftEdits.reviseInstruction(d, b.blockId(), i.ordinal(), "Ler o dado indicado", "TEST_AGENT");
                }
            }
        }
        return d;
    }

    public void decide(StepId id) {
        a.decideTarget(id, TargetDecision.Kind.CONFIRMED, DecisionActor.TEST_AGENT, new TargetDecision.Basis("TARGET_ID", "RESOLVED", List.of(),
                ConsultaCase.CLOCK.instant(), ""));
    }

    private static StepDraft read(StepContext page, ElementReference e) {
        return StepDraft.builder().name("Ler o dado").actionType(ActionType.OBSERVE).context(page).element(e).build();
    }

    private StepId add(LogicalSession s, StepDraft d, boolean targeted) {
        AutomationStep step = a.appendStep(s.id(), d);
        if (targeted) ids.add(step.id());
        return step.id();
    }

    static ElementReference cell(String header) {
        return el("TABLE_CELL", "td", "", LocatorStrategy.ROW_CONTEXT, "//table//tr[th[normalize-space(.)=\"" + header + "\"]]//td",
                "celula-" + Math.abs(header.hashCode()));
    }

    static ElementReference el(String kind, String tag, String name, LocatorStrategy strategy, String expr, String target) {
        LocatorCandidate preferred = new LocatorCandidate(strategy, "", expr, LocatorStability.HEURISTIC_STABLE, 1, true);
        return ElementReference.builder().semanticKind(kind).tag(tag).accessibleName(name).sensitive(false).privacyPolicy(CapturePrivacy.VERSION)
                .sourceContract("nekkora-pagemap/0.3.0").addLocator(preferred).preferLastLocator()
                .addAccessStep(AccessStep.of(AccessStep.Type.MAIN_DOCUMENT, "documento principal"))
                .correlation(CorrelationKeys.of(Map.of(CorrelationKeys.Key.TARGET_ID, "tgt-" + target)))
                .build();
    }
}
