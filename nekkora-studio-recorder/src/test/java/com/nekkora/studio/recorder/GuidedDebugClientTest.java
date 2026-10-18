package com.nekkora.studio.recorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.bridge.BridgeEvent;
import com.nekkora.studio.bridge.BrowserLink;
import com.nekkora.studio.bridge.LinkStatus;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.privacy.CapturePrivacy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * STUDIO-009 G2 — cliente do debug guiado: o que vai (targetId + descritor recuperável, sem valores) e o que volta
 * (códigos lidos como estão; nomes pela 4ª barreira; elemento escolhido com as regras de uma etapa gravada).
 */
class GuidedDebugClientTest {

    /** Navegador mínimo: devolve a resposta programada para cada comando e registra os pedidos. */
    static final class Link implements BrowserLink {
        final List<Map<String, Object>> sent = new CopyOnWriteArrayList<>();
        final List<String> types = new CopyOnWriteArrayList<>();
        final List<Listener> listeners = new CopyOnWriteArrayList<>();
        final Map<String, String> replies;

        Link(Map<String, String> replies) {
            this.replies = replies;
        }

        @Override
        public LinkStatus status() {
            return LinkStatus.of(LinkStatus.State.CONNECTED, "teste");
        }

        @Override
        public void addListener(Listener l) {
            listeners.add(l);
        }

        @Override
        public void removeListener(Listener l) {
            listeners.remove(l);
        }

        @Override
        public CompletableFuture<Map<String, Object>> send(String type, Map<String, Object> payload, Duration timeout) {
            types.add(type);
            sent.add(payload);
            return CompletableFuture.completedFuture(Json.parseObject(replies.getOrDefault(type, "{\"debug\":" + SESSION + ",\"duplicate\":false}")));
        }

        void emit(String type, String json) {
            listeners.forEach(l -> l.event(new BridgeEvent(type, Json.parseObject(json))));
        }
    }

    static final String SESSION = "{\"debugId\":\"DBG\",\"protocol\":\"guided-debug/1\",\"state\":\"ACTIVE\",\"tab\":{\"tabRef\":\"t-Q2f9LmZx7Kp1\","
            + "\"title\":null,\"host\":\"127.0.0.1\"},\"pageEpoch\":1,\"picking\":false,\"lease\":{\"leaseMs\":30000,\"expiresInMs\":29000},\"reason\":null}";

    private static ElementReference field(boolean sensitive) {
        return ElementReference.builder().semanticKind("SEARCH_INPUT").tag("input").accessibleName("Pesquisar").htmlId("q").sensitive(sensitive)
                .privacyPolicy(CapturePrivacy.VERSION)
                .addLocator(new LocatorCandidate(LocatorStrategy.ID, "", "#q", LocatorStability.HEURISTIC_STABLE, 1, true)).preferLastLocator()
                .addLocator(new LocatorCandidate(LocatorStrategy.XPATH_RELATIVE, "", "//input[@id='q']", LocatorStability.UNKNOWN, 1, true))
                .correlation(CorrelationKeys.of(Map.of(CorrelationKeys.Key.TARGET_ID, "tgt-3f6c1a", CorrelationKeys.Key.NODE_ID, "n41")))
                .build();
    }

    @Test
    @DisplayName("inspecionar: envia targetId e descritor recuperável (sem valores, sem nodeId), lê status, verificações, candidatos e caixa")
    void inspecionar() throws Exception {
        String inspect = "{\"debug\":" + SESSION + ",\"requestId\":\"rq-1\",\"stepRef\":\"x\",\"observedAt\":\"2026-10-17T12:00:03.000Z\",\"pageEpoch\":1,"
                + "\"identitySource\":\"TARGET_ID\",\"status\":\"RESOLVED\",\"score\":0.93,\"reasons\":[\"LOOKALIKES_PRESENT\"],\"checks\":{\"identity\":\"PASS\","
                + "\"unique\":\"PASS\",\"visible\":\"PASS\",\"enabled\":\"PASS\",\"editable\":\"PASS\",\"kindMatchesAction\":\"PASS\",\"frameReachable\":\"NOT_APPLICABLE\","
                + "\"shadowReachable\":\"NOT_APPLICABLE\"},\"element\":{\"kind\":\"SEARCH_INPUT\",\"name\":\"Ana Souza\",\"tag\":\"input\",\"sensitive\":false},"
                + "\"candidates\":[{\"candidateRef\":\"cand-1-1\",\"verdict\":\"CONFIRMED\",\"score\":0.93,\"kind\":\"SEARCH_INPUT\",\"name\":\"Pesquisar\",\"chosen\":true}],"
                + "\"highlighted\":true,\"box\":{\"x\":100,\"y\":200,\"width\":300,\"height\":30,\"viewportWidth\":1280,\"viewportHeight\":720,"
                + "\"devicePixelRatio\":1.25,\"screenX\":108,\"screenY\":300},\"continuity\":\"NOT_PROVEN\"}";
        Link link = new Link(Map.of("studio.debug.inspect", inspect));
        try (GuidedDebugClient c = new GuidedDebugClient(link, Duration.ofSeconds(30), Duration.ofSeconds(5))) {
            c.start("t-Q2f9LmZx7Kp1").get();
            StepId step = new StepId(UUID.randomUUID());
            GuidedDebugClient.Inspection r = c.inspect(step, ActionType.FILL, field(false), true).get();
            Map<String, Object> p = link.sent.get(1);
            assertEquals("tgt-3f6c1a", p.get("targetId"));
            assertEquals(step.value().toString(), p.get("stepRef"));
            @SuppressWarnings("unchecked")
            Map<String, Object> d = (Map<String, Object>) p.get("descriptor");
            assertEquals("Pesquisar", d.get("name"));
            assertEquals("q", d.get("htmlId"));
            String sent = Json.write(p);
            assertFalse(sent.contains("n41"), "nodeId (temporário) não vai: " + sent);
            assertTrue(sent.contains("//input[@id='q']") && sent.contains("\"xpath\""), "XPath vai como xpath");
            assertEquals(GuidedDebugClient.Status.RESOLVED, r.status());
            assertEquals(GuidedDebugClient.Check.PASS, r.check("editable"));
            assertEquals(List.of("LOOKALIKES_PRESENT"), r.reasons());
            assertEquals("", r.element().name(), "4ª barreira: nome fora do vocabulário não aparece");
            assertEquals("Pesquisar", r.candidates().get(0).name());
            assertEquals(108, r.box().screenX());
            assertEquals(1.25, r.box().devicePixelRatio());
            assertTrue(r.highlighted());
        }
    }

    @Test
    @DisplayName("campo sensível: descritor sem id, name nem nome; status desconhecido vira CONTEXT_UNAVAILABLE (nunca RESOLVED)")
    void sensivelEDesconhecido() throws Exception {
        String inspect = "{\"debug\":" + SESSION + ",\"requestId\":\"rq-1\",\"stepRef\":\"x\",\"observedAt\":\"2026-10-17T12:00:03.000Z\",\"pageEpoch\":1,"
                + "\"identitySource\":\"DESCRIPTOR\",\"status\":\"LOOKS_GOOD\",\"score\":0.5,\"reasons\":[],\"checks\":{},\"element\":null,\"candidates\":[],"
                + "\"highlighted\":false,\"box\":null,\"continuity\":null}";
        Link link = new Link(Map.of("studio.debug.inspect", inspect));
        try (GuidedDebugClient c = new GuidedDebugClient(link, Duration.ofSeconds(30), Duration.ofSeconds(5))) {
            c.start("t-Q2f9LmZx7Kp1").get();
            GuidedDebugClient.Inspection r = c.inspect(new StepId(UUID.randomUUID()), ActionType.FILL, field(true), false).get();
            @SuppressWarnings("unchecked")
            Map<String, Object> d = (Map<String, Object>) link.sent.get(1).get("descriptor");
            assertNull(d.get("htmlId"));
            assertNull(d.get("htmlName"));
            assertEquals("", d.get("name"));
            assertEquals(GuidedDebugClient.Status.CONTEXT_UNAVAILABLE, r.status());
            assertEquals(GuidedDebugClient.Check.UNKNOWN, r.check("identity"));
            assertNull(r.element());
        }
    }

    @Test
    @DisplayName("STUDIO-012 — opção configurada: expectedOption só em SELECT_OPTION; volta só contagem e sim/não; ausente = não pedida")
    void opcaoConfigurada() throws Exception {
        String base = "{\"debug\":" + SESSION + ",\"requestId\":\"rq-1\",\"stepRef\":\"x\",\"observedAt\":\"2026-10-18T12:00:03.000Z\",\"pageEpoch\":1,"
                + "\"identitySource\":\"TARGET_ID\",\"status\":\"RESOLVED\",\"score\":0.9,\"reasons\":[],\"checks\":{},\"element\":null,\"candidates\":[],"
                + "\"highlighted\":false,\"box\":null,\"continuity\":null";
        Link link = new Link(Map.of("studio.debug.inspect", base + ",\"option\":{\"matches\":1,\"selected\":true}}"));
        try (GuidedDebugClient c = new GuidedDebugClient(link, Duration.ofSeconds(30), Duration.ofSeconds(5))) {
            c.start("t-Q2f9LmZx7Kp1").get();
            GuidedDebugClient.Inspection r = c.inspect(new StepId(UUID.randomUUID()), ActionType.SELECT_OPTION, field(false), false, "Auditoria Carefy").get();
            assertEquals("Auditoria Carefy", link.sent.get(1).get("expectedOption"));
            assertEquals(new GuidedDebugClient.OptionState(1, true), r.option());
            c.inspect(new StepId(UUID.randomUUID()), ActionType.CLICK, field(false), false, "Auditoria Carefy").get();
            assertFalse(link.sent.get(2).containsKey("expectedOption"), "só lista de seleção leva a opção");
            c.inspect(new StepId(UUID.randomUUID()), ActionType.SELECT_OPTION, field(false), false, "x".repeat(121)).get();
            assertFalse(link.sent.get(3).containsKey("expectedOption"), "acima do limite do contrato não vai");
        }
        Link sem = new Link(Map.of("studio.debug.inspect", base + "}"));
        try (GuidedDebugClient c = new GuidedDebugClient(sem, Duration.ofSeconds(30), Duration.ofSeconds(5))) {
            c.start("t-Q2f9LmZx7Kp1").get();
            assertNull(c.inspect(new StepId(UUID.randomUUID()), ActionType.SELECT_OPTION, field(false), false).get().option(), "extensão anterior: sem conferência");
        }
    }

    @Test
    @DisplayName("seleção: o evento vira ElementReference pelas regras de etapa (4ª barreira), só para a própria sessão; changed atualiza o estado")
    void selecao() throws Exception {
        Link link = new Link(Map.of());
        List<GuidedDebugClient.Picked> picked = new ArrayList<>();
        List<GuidedDebugClient.Session> changed = new ArrayList<>();
        try (GuidedDebugClient c = new GuidedDebugClient(link, Duration.ofSeconds(30), Duration.ofSeconds(5))) {
            c.addListener(new GuidedDebugClient.Listener() {
                @Override
                public void picked(GuidedDebugClient.Picked p) {
                    picked.add(p);
                }

                @Override
                public void changed(GuidedDebugClient.Session s) {
                    changed.add(s);
                }
            });
            c.start("t-Q2f9LmZx7Kp1").get();
            String element = "{\"resolution\":\"RESOLVED\",\"kind\":\"BUTTON\",\"role\":\"button\",\"name\":\"Consultar\",\"tag\":\"button\",\"htmlId\":\"consultar\","
                    + "\"htmlName\":null,\"sensitive\":false,\"locators\":[{\"strategy\":\"TEST_ATTRIBUTE\",\"description\":\"\",\"css\":\"[data-testid=\\\"consultar\\\"]\","
                    + "\"xpath\":null,\"matchCount\":1,\"matchesTarget\":true,\"stability\":\"HEURISTIC_STABLE\",\"validation\":\"LIVE\"},{\"strategy\":\"ATTRIBUTE\","
                    + "\"description\":\"\",\"css\":\"[data-cliente=\\\"Ana Souza\\\"]\",\"xpath\":null,\"matchCount\":1,\"matchesTarget\":true,\"stability\":\"UNKNOWN\","
                    + "\"validation\":\"NONE\"}],\"preferredLocator\":0,\"accessPath\":[{\"type\":\"MAIN_DOCUMENT\",\"label\":\"\",\"locator\":null,\"restriction\":null}],"
                    + "\"context\":[],\"lookalikes\":0,\"correlation\":{\"targetId\":\"tgt-8d02b7\",\"nodeId\":\"n31\",\"captureId\":\"cap-40\",\"documentId\":\"doc:f0\"}}";
            String checks = "{\"identity\":\"NOT_APPLICABLE\",\"unique\":\"NOT_APPLICABLE\",\"visible\":\"PASS\",\"enabled\":\"PASS\",\"editable\":\"NOT_APPLICABLE\","
                    + "\"kindMatchesAction\":\"NOT_APPLICABLE\",\"frameReachable\":\"NOT_APPLICABLE\",\"shadowReachable\":\"NOT_APPLICABLE\"}";
            link.emit("studio.debug.picked", "{\"debugId\":\"outra-sessao\",\"requestId\":\"rq-5\",\"pageEpoch\":1,\"observedAt\":\"2026-10-17T12:00:09.000Z\","
                    + "\"origin\":\"BROWSER_SELECTION\",\"element\":" + element + ",\"checks\":" + checks + ",\"box\":null}");
            assertTrue(picked.isEmpty(), "evento de outra sessão é ignorado");
            link.emit("studio.debug.picked", "{\"debugId\":\"" + c.debugId() + "\",\"requestId\":\"rq-5\",\"pageEpoch\":1,\"observedAt\":\"2026-10-17T12:00:09.000Z\","
                    + "\"origin\":\"BROWSER_SELECTION\",\"element\":" + element + ",\"checks\":" + checks + ",\"box\":null}");
            assertEquals(1, picked.size());
            ElementReference e = picked.get(0).element();
            assertEquals("tgt-8d02b7", e.correlation().get(CorrelationKeys.Key.TARGET_ID).orElseThrow());
            assertTrue(e.correlation().get(CorrelationKeys.Key.RECORDING_ID).isEmpty(), "não houve gravação");
            assertEquals(1, e.locators().size(), "localizador com texto da página descartado pela 4ª barreira: " + e.locators());
            assertEquals(0, e.preferredLocatorIndex());
            assertEquals(GuidedDebugClient.Check.PASS, picked.get(0).checks().get("visible"));
            link.emit("studio.debug.changed", "{\"debug\":" + SESSION.replace("\"DBG\"", "\"" + c.debugId() + "\"").replace("\"ACTIVE\"", "\"ENDED\"")
                    .replace("\"reason\":null", "\"reason\":\"TAB_CLOSED\"") + "}");
            assertEquals("TAB_CLOSED", changed.get(0).reason());
            assertTrue(c.session().ended());
        }
    }
}
