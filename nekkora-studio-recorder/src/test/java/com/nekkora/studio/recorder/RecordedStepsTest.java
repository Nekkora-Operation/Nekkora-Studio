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
                Json.obj().put("strategy", "ID").put("description", "id \"" + htmlId + "\"").put("css", "#" + htmlId).put("xpath", null)
                        .put("matchCount", 1).put("matchesTarget", true).put("stability", "HEURISTIC_STABLE").put("validation", "LIVE").build(),
                Json.obj().put("strategy", "TEXT").put("description", "texto").put("css", null).put("xpath", "//button")
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
    void s003016NomesDeTodasAsAcoes() {
        Map<String, Object> sel = step("rec-1", 1, "route-1", "SELECT_OPTION", element("SELECT", "Estado", "select", "estado", false), "CAPTURE_AND_INTERACT");
        sel.put("parameters", Json.obj().put("valueCaptured", false).put("option", Json.obj().put("label", "São Paulo").put("value", "SP").build()).put("key", null).build());
        StepDraft s = RecordedStepDrafts.draft(parse(sel));
        assertEquals("Selecionar São Paulo no campo Estado", s.name());
        assertEquals("São Paulo", s.parameters().option().orElseThrow().label());
        assertEquals("SP", s.parameters().option().orElseThrow().value());

        Map<String, Object> radio = step("rec-1", 2, "route-1", "CHECK", element("RADIO", "Pessoa física", "input", "pf", false), "CAPTURE_AND_INTERACT");
        assertEquals("Marcar opção Pessoa física", RecordedStepDrafts.draft(parse(radio)).name());
        assertEquals("Marcar Somente ativos", RecordedStepDrafts.draft(parse(step("rec-1", 3, "route-1", "CHECK",
                element("CHECKBOX", "Somente ativos", "input", "ativos", false), "CAPTURE_AND_INTERACT"))).name());
        assertEquals("Desmarcar Somente ativos", RecordedStepDrafts.draft(parse(step("rec-1", 4, "route-1", "UNCHECK",
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
        assertEquals("Selecionar opção no campo Contato", s.name());
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
}
