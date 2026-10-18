package com.nekkora.studio.recorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepOrigin;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** STUDIO-003 no Studio: leitura do evento, ordem/deduplicação e montagem determinística da etapa. */
class RecordedStepsTest {

    static Map<String, Object> element(String kind, String name, String tag, String htmlId, boolean sensitive) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("resolution", "RESOLVED");
        e.put("kind", kind);
        e.put("role", null);
        e.put("name", name);
        e.put("tag", tag);
        e.put("htmlId", htmlId);
        e.put("htmlName", null);
        e.put("sensitive", sensitive);
        e.put("locators", List.of(
                Json.obj().put("strategy", "ID").put("description", tag + " {id=" + htmlId + "}").put("css", "#" + htmlId).put("xpath", null)
                        .put("matchCount", 1).put("matchesTarget", true).put("stability", "HEURISTIC_STABLE").put("validation", "LIVE").build(),
                Json.obj().put("strategy", "TEXT").put("description", "button {text=\"Entrar\"}").put("css", null).put("xpath", "//button")
                        .put("matchCount", 3).put("matchesTarget", true).put("stability", "UNKNOWN").put("validation", "NONE").build()));
        e.put("preferredLocator", 0);
        e.put("accessPath", List.of(Json.obj().put("type", "MAIN_DOCUMENT").put("label", "Login de teste").put("locator", null).build(),
                Json.obj().put("type", "IFRAME").put("label", "iframe#cadastro").put("locator", "#cadastro").build()));
        e.put("context", List.of(Json.obj().put("relation", "IN_FORM").put("kind", "FORM").put("name", "login").build()));
        e.put("lookalikes", 0);
        e.put("correlation", Json.obj().put("targetId", "tgt-a1b2c3").put("nodeId", "n12").put("captureId", "cap-9").put("documentId", "doc:f0").build());
        return e;
    }

    static Map<String, Object> step(String rid, long seq, String route, String action, Map<String, Object> element, String mode) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("recordingId", rid);
        p.put("eventId", "ev-" + rid.substring(Math.max(0, rid.length() - 8)) + "-" + seq);
        p.put("sequence", seq);
        p.put("routeRef", route);
        p.put("observedAt", "2026-10-15T12:00:0" + (seq % 10) + ".000Z");
        p.put("mode", mode);
        p.put("observation", "CAPTURE_ONLY".equals(mode) ? "PROPOSED" : "INTERACTION_OBSERVED");
        p.put("action", action);
        p.put("element", element);
        p.put("parameters", Json.obj().put("valueCaptured", false).put("option", null).put("key", null).build());
        p.put("context", Json.obj().put("url", "http://127.0.0.1:18765/lab/studio003/login.html").put("title", "Login de teste").put("frame", "MAIN").build());
        p.put("evidence", List.of());
        p.put("notes", List.of());
        p.put("events", 3);
        return p;
    }

    /**
     * CHROME-SECURITY-001: o nome do controle entra no nome da etapa só se for do vocabulário controlado; senão a etapa
     * usa o nome genérico do tipo de controle.
     */
    private static String named(String prefix, String controlName, String generic) {
        String t = com.nekkora.studio.domain.privacy.CapturePrivacy.interfaceText(controlName);
        return t == null ? generic : prefix + " " + t;
    }

    private static RecordedStep parse(Map<String, Object> p) {
        return RecordedStep.parse(p).orElseThrow();
    }

    @Test
    void s003CenarioDoLoginViraTresInstrucoesComNomesDeterministicos() {
        StepDraft u = RecordedStepDrafts.draft(parse(step("rec-1", 1, "route-1", "FILL", element("TEXT_INPUT", "Usuário", "input", "usuario", false), "CAPTURE_AND_INTERACT")));
        StepDraft p = RecordedStepDrafts.draft(parse(step("rec-1", 2, "route-1", "FILL", element("PASSWORD_INPUT", "Senha", "input", "senha", true), "CAPTURE_AND_INTERACT")));
        Map<String, Object> clickPayload = step("rec-1", 3, "route-1", "CLICK", element("BUTTON", "Entrar", "button", "entrar", false), "CAPTURE_AND_INTERACT");
        clickPayload.put("evidence", List.of("FORM_SUBMITTED", "NAVIGATION_FOLLOWED"));
        clickPayload.put("context", Json.obj().put("url", "http://127.0.0.1:18765/lab/studio003/login.html").put("title", "Login de teste").put("frame", "MAIN")
                .put("after", Json.obj().put("navigation", "NEW_DOCUMENT").put("url", null).build()).build());
        StepDraft c = RecordedStepDrafts.draft(parse(clickPayload));
        assertEquals("Preencher campo Usuário", u.name());
        assertEquals("Preencher campo Senha", p.name());
        assertEquals("Clicar no botão Entrar", c.name());
        assertEquals(ActionType.FILL, u.actionType());
        assertTrue(u.parameters().isEmpty(), "nenhum valor digitado (ValueSource fica para o usuário configurar)");
        assertTrue(p.element().sensitive());
        assertTrue(p.element().htmlId().isEmpty(), "campo sensível sem id HTML");
        assertEquals(StepOrigin.RECORDED, c.provenance().origin());
        RecordingObservation o = c.provenance().recordingObservation().orElseThrow();
        assertEquals(RecordingObservation.Observation.INTERACTION_OBSERVED, o.observation());
        assertEquals(RecordingObservation.BusinessOutcome.NOT_VALIDATED, o.businessOutcome());
        assertTrue(c.observedOutcome().contains("Envio de formulário observado"), c.observedOutcome());
        assertTrue(c.observedOutcome().contains("não validado"));
        assertTrue(c.description().contains("não foi validado"));
    }

    @Test
    void s003026ReferenciaDoElementoComoVeioDaExtensaoSemRecalculo() {
        ElementReference e = RecordedStepDrafts.draft(parse(step("rec-1", 1, "route-1", "CLICK", element("BUTTON", "Entrar", "button", "entrar", false),
                "CAPTURE_AND_INTERACT"))).element();
        assertEquals("Entrar", e.accessibleName());
        assertEquals("BUTTON", e.semanticKind());
        assertEquals("button", e.tag());
        assertEquals("entrar", e.displayableHtmlId().orElseThrow(), "id HTML apresentável (política do produtor reconhecida)");
        assertEquals(2, e.locators().size());
        assertEquals("#entrar", e.preferredLocator().orElseThrow().expression(), "preferencial = o validado pela extensão");
        assertTrue(e.stabilityNotes().stream().anyMatch(n -> n.contains("página real")), e.stabilityNotes().toString());
        assertEquals(2, e.accessPath().size());
        assertEquals("IFRAME", e.accessPath().get(1).type().name());
        assertEquals("IN_FORM", e.context().get(0).relation());
        assertEquals("tgt-a1b2c3", e.correlation().get(CorrelationKeys.Key.TARGET_ID).orElseThrow());
        assertEquals("rec-1", e.correlation().get(CorrelationKeys.Key.RECORDING_ID).orElseThrow());
        assertFalse(e.toString().contains("tgt-"), "chaves internas não aparecem em textos");
    }

    @Test
    void localizadorNaoValidadoNuncaViraPreferencial() {
        Map<String, Object> el = element("BUTTON", "Excluir", "button", "excluir", false);
        el.put("locators", List.of(Json.obj().put("strategy", "ID").put("description", "id").put("css", "#excluir").put("xpath", null)
                .put("matchCount", 2).put("matchesTarget", true).put("stability", "HEURISTIC_STABLE").put("validation", "NONE").build()));
        el.put("preferredLocator", 0); // mesmo que o produtor aponte, um localizador com 2 correspondências não é validado
        el.put("lookalikes", 1);
        Map<String, Object> payload = step("rec-1", 1, "route-1", "CLICK", el, "CAPTURE_AND_INTERACT");
        payload.put("notes", List.of("NO_UNIQUE_LOCATOR", "INDISTINGUISHABLE_LOOKALIKES"));
        ElementReference e = RecordedStepDrafts.draft(parse(payload)).element();
        assertTrue(e.preferredLocator().isEmpty(), "nenhuma identidade inventada");
        assertTrue(e.obstacles().stream().anyMatch(o -> o.type().equals("INDISTINGUISHABLE_LOOKALIKES")));
        assertTrue(e.stabilityNotes().stream().anyMatch(n -> n.contains("parecido")));
    }

    @Test
    void s012IdentificadorTecnicoAceitoRetidoExplicadoEHoverComoDependencia() {
        // id/name do desenvolvedor com termo técnico aprovado: aceito como veio (4ª barreira = mesma regra da extensão)
        ElementReference login = RecordedStepDrafts.draft(parse(step("rec-1", 1, "route-1", "FILL", element("TEXT_INPUT", "Usuário", "input", "dsLogin", false),
                "CAPTURE_AND_INTERACT"))).element();
        assertEquals("dsLogin", login.displayableHtmlId().orElseThrow());
        assertEquals("#dsLogin", login.preferredLocator().orElseThrow().expression(), "id técnico estável e único é o preferido");
        // fora das listas: o Studio também retém (e o localizador com o id não sobrevive)
        Map<String, Object> other = element("TEXT_INPUT", "Usuário", "input", "dsJoao", false);
        Map<String, Object> p = step("rec-1", 2, "route-1", "FILL", other, "CAPTURE_AND_INTERACT");
        p.put("notes", List.of("IDENTIFIER_WITHHELD"));
        ElementReference e = RecordedStepDrafts.draft(parse(p)).element();
        assertTrue(e.htmlId().isEmpty(), "id fora das listas não é aceito pela 4ª barreira");
        assertTrue(e.locators().stream().noneMatch(l -> l.expression().contains("dsJoao")), e.locators().toString());
        assertTrue(e.stabilityNotes().stream().anyMatch(n -> n.contains("tem id/name") && n.contains("reteve")), e.stabilityNotes().toString());
        // alvo revelado por :hover: obstáculo de dependência de outra interação (revisão), nenhuma etapa inventada
        Map<String, Object> hover = step("rec-1", 3, "route-1", "CLICK", element("LINK", "Prestadores", "a", "prestadores", false), "CAPTURE_AND_INTERACT");
        hover.put("notes", List.of("REVEALED_BY_HOVER"));
        StepDraft h = RecordedStepDrafts.draft(parse(hover));
        assertEquals(ActionType.CLICK, h.actionType());
        assertTrue(h.element().obstacles().stream().anyMatch(o -> o.type().equals("INTERACTION_DEPENDENCY") && o.description().contains(":hover")),
                h.element().obstacles().toString());
        assertTrue(h.provenance().recordingObservation().orElseThrow().limitations().contains("REVEALED_BY_HOVER"));
    }

    @Test
    void s003016NomesDeTodasAsAcoes() {
        Map<String, Object> sel = step("rec-1", 1, "route-1", "SELECT_OPTION", element("SELECT", "Estado", "select", "estado", false), "CAPTURE_AND_INTERACT");
        sel.put("parameters", Json.obj().put("valueCaptured", false).put("option", Json.obj().put("label", "São Paulo").put("value", "SP").build()).put("key", null).build());
        RecordedStep parsedSel = parse(sel);
        StepDraft s = RecordedStepDrafts.draft(parsedSel);
        // CHROME-SECURITY-001: a opção escolhida é valor do usuário — nem de extensão antiga é aceita (OPTION_WITHHELD);
        // a escolha é configurada no Studio
        assertEquals("Selecionar opção no campo Estado", s.name());
        assertTrue(s.parameters().option().isEmpty());
        assertTrue(parsedSel.notes().contains("OPTION_WITHHELD"), parsedSel.notes().toString());
        assertTrue(s.element().stabilityNotes().stream().anyMatch(n -> n.contains("configure a opção no Studio")), "limitação visível ao usuário");

        Map<String, Object> radio = step("rec-1", 2, "route-1", "CHECK", element("RADIO", "Pessoa física", "input", "pf", false), "CAPTURE_AND_INTERACT");
        assertEquals(named("Marcar opção", "Pessoa física", "Marcar opção"), RecordedStepDrafts.draft(parse(radio)).name());
        assertEquals(named("Marcar", "Somente ativos", "Marcar caixa de seleção"), RecordedStepDrafts.draft(parse(step("rec-1", 3, "route-1", "CHECK",
                element("CHECKBOX", "Somente ativos", "input", "ativos", false), "CAPTURE_AND_INTERACT"))).name());
        assertEquals(named("Desmarcar", "Somente ativos", "Desmarcar caixa de seleção"), RecordedStepDrafts.draft(parse(step("rec-1", 4, "route-1", "UNCHECK",
                element("CHECKBOX", "Somente ativos", "input", "ativos", false), "CAPTURE_AND_INTERACT"))).name());
        assertEquals("Clicar no link Clientes", RecordedStepDrafts.draft(parse(step("rec-1", 5, "route-1", "CLICK",
                element("LINK", "Clientes", "a", "menu", false), "CAPTURE_AND_INTERACT"))).name());

        Map<String, Object> enter = step("rec-1", 6, "route-1", "PRESS_KEY", element("SEARCH_INPUT", "Pesquisa", "input", "pesquisa", false), "CAPTURE_AND_INTERACT");
        enter.put("parameters", Json.obj().put("valueCaptured", false).put("option", null).put("key", "Enter").build());
        StepDraft e = RecordedStepDrafts.draft(parse(enter));
        assertEquals("Pressionar Enter no campo Pesquisa", e.name());
        assertEquals(ActionType.PRESS_KEY, e.actionType());
        assertEquals("Enter", e.parameters().value().orElseThrow().display());

        Map<String, Object> unk = step("rec-1", 7, "route-1", "UNKNOWN", element("TEXT", "", "span", "detalhes", false), "CAPTURE_AND_INTERACT");
        unk.put("notes", List.of("AMBIGUOUS_TARGET"));
        StepDraft u = RecordedStepDrafts.draft(parse(unk));
        assertEquals("Interação não classificada em <span>", u.name());
        assertEquals(ActionType.UNKNOWN, u.actionType());
        assertTrue(u.element().obstacles().stream().anyMatch(o -> o.type().equals("AMBIGUOUS_TARGET")));

        Map<String, Object> noOpt = step("rec-1", 8, "route-1", "SELECT_OPTION", element("SELECT", "Estado", "select", "estado", false), "CAPTURE_ONLY");
        StepDraft p = RecordedStepDrafts.draft(parse(noOpt));
        assertEquals("Selecionar opção no campo Estado", p.name());
        assertEquals(RecordingObservation.Observation.PROPOSED, p.provenance().recordingObservation().orElseThrow().observation());
        assertTrue(p.description().contains("NÃO foi executada"));
    }

    @Test
    void s003037OpcaoComDadoPessoalNaoEntraNoModeloENomesNuncaTemIdsInternos() {
        Map<String, Object> sel = step("rec-1", 1, "route-1", "SELECT_OPTION", element("SELECT", "Contato", "select", "contato", false), "CAPTURE_AND_INTERACT");
        sel.put("parameters", Json.obj().put("valueCaptured", false).put("option", Json.obj().put("label", "ana@exemplo.com").put("value", "x").build()).put("key", null).build());
        StepDraft s = RecordedStepDrafts.draft(parse(sel));
        assertTrue(s.parameters().option().isEmpty(), "opção com dado pessoal não é guardada");
        // o rótulo do campo só aparece se for do vocabulário controlado ("Contato" não é)
        String campo = com.nekkora.studio.domain.privacy.CapturePrivacy.interfaceText("Contato");
        assertEquals(campo == null ? "Selecionar opção no campo" : "Selecionar opção no campo " + campo, s.name());
        for (String name : List.of(s.name(), s.description())) {
            assertTrue(DisplaySafety.isSafeToDisplay(name), name);
            assertFalse(name.contains("rec-1") || name.contains("tgt-") || name.contains("n12"));
        }
    }

    @Test
    void eventoForaDoContratoNaoViraEtapa() {
        Map<String, Object> ok = step("rec-1", 1, "route-1", "CLICK", element("BUTTON", "Entrar", "button", "entrar", false), "CAPTURE_AND_INTERACT");
        assertTrue(RecordedStep.parse(ok).isPresent());
        Map<String, Object> value = new LinkedHashMap<>(ok);
        value.put("parameters", Json.obj().put("valueCaptured", true).build());
        assertTrue(RecordedStep.parse(value).isEmpty(), "valor digitado: recusado");
        Map<String, Object> noSeq = new LinkedHashMap<>(ok);
        noSeq.remove("sequence");
        assertTrue(RecordedStep.parse(noSeq).isEmpty());
        Map<String, Object> nav = new LinkedHashMap<>(ok);
        nav.put("action", "NAVIGATE");
        assertTrue(RecordedStep.parse(nav).isEmpty(), "ação fora do catálogo do contrato");
        Map<String, Object> badId = new LinkedHashMap<>(ok);
        badId.put("recordingId", "rec 1; drop");
        assertTrue(RecordedStep.parse(badId).isEmpty());
    }

    @Test
    void s003028s003029InboxOrdenaPelaSequenciaDescartaRepetidasEDetectaLacuna() {
        StepInbox in = new StepInbox();
        Map<String, Object> el = element("BUTTON", "B", "button", "b", false);
        List<Long> out = new ArrayList<>();
        StepInbox.Accepted a2 = in.accept(parse(step("rec-1", 2, "route-1", "CLICK", el, "CAPTURE_AND_INTERACT")));
        assertTrue(a2.ready().isEmpty());
        assertTrue(a2.gap(), "a 1 ainda não chegou");
        StepInbox.Accepted a1 = in.accept(parse(step("rec-1", 1, "route-1", "CLICK", el, "CAPTURE_AND_INTERACT")));
        a1.ready().forEach(s -> out.add(s.sequence()));
        assertEquals(List.of(1L, 2L), out, "ordem da sequência, não da chegada");
        assertTrue(in.accept(parse(step("rec-1", 2, "route-1", "CLICK", el, "CAPTURE_AND_INTERACT"))).duplicate(), "reemissão não duplica");
        assertEquals(2, in.processed());
        assertTrue(in.missing(3));
        assertFalse(in.missing(2));
        assertEquals(1, in.duplicates());
    }

    @org.junit.jupiter.api.Test
    void revDuplicarEtapaProduzidaPeloRecorderNaoCarregaChavesDoEvento() {
        StepDraft d = RecordedStepDrafts.draft(parse(step("rec-77", 3, "route-1", "CLICK", element("BUTTON", "Entrar", "button", "entrar", false),
                "CAPTURE_AND_INTERACT")));
        com.nekkora.studio.domain.Automation a = com.nekkora.studio.domain.Automation.create("A", null, java.time.Clock.systemUTC());
        com.nekkora.studio.domain.LogicalSession s = a.addSession("S", null);
        com.nekkora.studio.domain.AutomationStep original = a.appendStep(s.id(), d);
        com.nekkora.studio.domain.AutomationStep copy = a.duplicateStep(original.id());
        CorrelationKeys keys = copy.element().orElseThrow().correlation();
        assertTrue(keys.get(CorrelationKeys.Key.RECORDING_ID).isEmpty(), "gravação da original não vai para a cópia");
        assertTrue(keys.get(CorrelationKeys.Key.RECORDED_EVENT_ID).isEmpty(), "evento da original não vai para a cópia");
        assertEquals("tgt-a1b2c3", keys.get(CorrelationKeys.Key.TARGET_ID).orElseThrow(), "identidade lógica do elemento fica");
        assertTrue(copy.provenance().correlation().isEmpty());
        assertTrue(original.element().orElseThrow().correlation().get(CorrelationKeys.Key.RECORDED_EVENT_ID).isPresent(), "original intacta");
    }

    @org.junit.jupiter.api.Test
    void revOpcaoDoRecorderContinuaAceita() {
        Map<String, Object> p = step("rec-1", 4, "route-1", "SELECT_OPTION", element("SELECT", "Estado", "select", "estado", false), "CAPTURE_AND_INTERACT");
        p.put("parameters", Json.obj().put("valueCaptured", false).put("option", Json.obj().put("label", "São Paulo").put("value", "SP").build())
                .put("key", null).build());
        RecordedStep parsed = parse(p);
        StepDraft d = RecordedStepDrafts.draft(parsed);
        // CHROME-SECURITY-001: o evento da STUDIO-003 com opção continua ACEITO (vira etapa, contrato inalterado), mas a
        // opção em si não entra no modelo (OPTION_WITHHELD) — a escolha é configurada no Studio
        assertEquals("Selecionar opção no campo Estado", d.name());
        assertTrue(d.parameters().option().isEmpty());
        assertTrue(parsed.notes().contains("OPTION_WITHHELD"));
    }

    @Test
    void sec001QuartaBarreiraEtapaDeExtensaoAntigaNaoTrazTextoDaPagina() {
        // CHROME-SECURITY-001: uma extensão anterior à nekkora-privacy/2 (ou com defeito) envia nome, ids, localizadores,
        // opção, URL e título com dados de pessoas (fictícios) — o Studio reaplica a política antes de criar a etapa
        Map<String, Object> e = element("BUTTON", "Consultar cliente Bartolomeu Quixadá", "button", "cliente-anacleto-prudencio", false);
        e.put("htmlName", "usuario_hermengarda");
        e.put("accessPath", List.of(Json.obj().put("type", "MAIN_DOCUMENT").put("label", "Ficha de Lucíola Assumpção").put("locator", null).build()));
        e.put("context", List.of(Json.obj().put("relation", "IN_FORM").put("kind", "FORM").put("name", "Cadastro de Nicanor Esperança").build()));
        Map<String, Object> p = step("rec-9", 1, "route-1", "SELECT_OPTION", e, "CAPTURE_AND_INTERACT");
        p.put("parameters", Json.obj().put("valueCaptured", false).put("option", Json.obj().put("label", "Zéfiro Quintanilha").put("value", "t1").build())
                .put("key", null).build());
        p.put("context", Json.obj().put("url", "http://127.0.0.1:18765/clientes/joao-oliveira?cliente=Ataulfo#ficha").put("title", "Ficha de Belmira Saraiva")
                .put("frame", "MAIN").build());
        RecordedStep s = parse(p);
        String all = s.toString() + s.element();
        for (String secret : new String[]{"Quixad", "anacleto", "hermengarda", "Assump", "Esperan", "Quintanilha", "oliveira", "Ataulfo", "Saraiva"}) {
            assertFalse(all.contains(secret), secret + " em " + all);
        }
        assertEquals("", s.element().get("name"));
        assertEquals(null, s.element().get("htmlId"));
        assertEquals(null, s.optionLabel());
        assertEquals("http://127.0.0.1:18765/clientes/{…}?cliente={…}", s.url());
        assertEquals(null, s.title());
        assertTrue(s.notes().contains("OPTION_WITHHELD") && s.notes().contains("LOCATORS_WITHHELD"), s.notes().toString());
        StepDraft d = RecordedStepDrafts.draft(s);
        assertFalse(d.name().contains("Quixad"), d.name());
    }

    @Test
    void sec001P1PayloadAdversarialComPapelArbitrarioENumeroLongoNoLocalizador() {
        // Revisão dos PRs #7 (P1-01/P1-02): o Studio não confia no produtor
        Map<String, Object> el = element("BUTTON", "Salvar", "button", "salvar", false);
        el.put("role", "gumercindo-tavares");
        el.put("locators", List.of(
                Json.obj().put("strategy", "ATTRIBUTE").put("description", "button {data-customer=12345678900}").put("css", null)
                        .put("xpath", "//button[@data-customer='12345678900']").put("matchCount", 1).put("matchesTarget", true)
                        .put("stability", "HEURISTIC_STABLE").put("validation", "LIVE").build(),
                Json.obj().put("strategy", "POSITIONAL").put("description", "html >> body >> div {nth-of-type 123} >> button {nth-of-type 1}")
                        .put("css", "html > body > div:nth-of-type(123) > button:nth-of-type(1)").put("xpath", null).put("matchCount", 1)
                        .put("matchesTarget", true).put("stability", "HEURISTIC_UNSTABLE").put("validation", "LIVE").build()));
        el.put("preferredLocator", 1);
        RecordedStep r = parse(step("rec-1", 1, "route-1", "CLICK", el, "CAPTURE_AND_INTERACT"));
        String all = r.element().toString();
        assertFalse(all.contains("gumercindo") || all.contains("12345678900"), all);
        assertTrue(r.notes().contains("LOCATORS_WITHHELD"), r.notes().toString());
        ElementReference ref = RecordedStepDrafts.draft(r).element();
        assertEquals(1, ref.locators().size(), "a posição estrutural continua");
        assertTrue(ref.locators().get(0).expression().contains("nth-of-type(123)"));
    }

    @Test
    void sec001SegundaRevisaoOperadoresCssValorSemAspasENomeDeComponente() {
        // 2ª revisão dos PRs #7: o Studio recebe payload adversarial — operadores CSS além de "=", escape CSS, construção não
        // reconhecida, valor sem aspas na notação semântica e nome de componente com dado de pessoa
        String[][] forged = {
            {"button[data-client^=joao-oliveira]", null, ""},
            {"button[data-client~=joao-oliveira]", null, ""},
            {"input[aria-label*=gumercindo-tavares]", null, ""},
            {"#\\6a oao-oliveira", null, ""},
            {"button:not([data-client=joao-oliveira])", null, ""},
            {null, "//ficha-cliente-joao/button", ""},
            {null, null, "button {aria-label=Consultar Joao Oliveira}"},
        };
        for (String[] f : forged) {
            Map<String, Object> el = element("BUTTON", "Salvar", "ficha-cliente-joao", "salvar", false);
            el.put("locators", List.of(Json.obj().put("strategy", "ATTRIBUTE").put("description", f[2]).put("css", f[0]).put("xpath", f[1])
                    .put("matchCount", 1).put("matchesTarget", true).put("stability", "HEURISTIC_STABLE").put("validation", "LIVE").build()));
            el.put("preferredLocator", 0);
            el.put("accessPath", List.of(Json.obj().put("type", "SHADOW_ROOT").put("label", "").put("locator", "ficha-cliente-joao[data-x~=joao]")
                    .put("restriction", null).build()));
            RecordedStep r = parse(step("rec-1", 1, "route-1", "CLICK", el, "CAPTURE_AND_INTERACT"));
            String all = r.element().toString();
            assertFalse(all.contains("joao") || all.contains("gumercindo") || all.contains("6a oao"), all);
            assertEquals("custom-element", r.element().get("tag"));
            assertTrue(r.notes().contains("LOCATORS_WITHHELD"), r.notes().toString());
            assertEquals(0, RecordedStepDrafts.draft(r).element().locators().size());
        }
        // operador reconhecido com literal da política e posição de componente retido (:nth-child) continuam
        Map<String, Object> ok = element("BUTTON", "Salvar", "button", "salvar", false);
        ok.put("locators", List.of(Json.obj().put("strategy", "POSITIONAL").put("description", "html >> body >> * {nth-child 3} >> button {nth-of-type 1}")
                .put("css", "html > body > :nth-child(3) > button:nth-of-type(1)").put("xpath", "/html/body/*[3]/button[1]").put("matchCount", 1)
                .put("matchesTarget", true).put("stability", "HEURISTIC_UNSTABLE").put("validation", "LIVE").build(),
                Json.obj().put("strategy", "ATTRIBUTE").put("description", "").put("css", "input[aria-label*='Salvar']").put("xpath", null)
                        .put("matchCount", 1).put("matchesTarget", true).put("stability", "HEURISTIC_STABLE").put("validation", "LIVE").build()));
        ok.put("preferredLocator", 0);
        RecordedStep r = parse(step("rec-1", 2, "route-1", "CLICK", ok, "CAPTURE_AND_INTERACT"));
        assertEquals(2, RecordedStepDrafts.draft(r).element().locators().size(), r.element().toString());
        assertEquals("button", r.element().get("tag"));
    }
}
