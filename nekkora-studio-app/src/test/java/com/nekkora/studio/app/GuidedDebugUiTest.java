package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.app.debug.GuidedDebugPresenter;
import com.nekkora.studio.app.recorder.RecorderPrompts;
import com.nekkora.studio.app.recorder.RecorderWindowHost;
import com.nekkora.studio.bridge.BridgeEvent;
import com.nekkora.studio.bridge.BrowserLink;
import com.nekkora.studio.bridge.LinkStatus;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.StepField;
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
import com.nekkora.studio.domain.target.DecisionActor;
import com.nekkora.studio.domain.target.TargetDecision;
import com.nekkora.studio.ui.debug.GuidedDebugPanel;
import com.nekkora.tokens.NekkoraThemes;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * STUDIO-009 G3/G4/G5 pela interface — Studio REAL com um navegador falso que responde como a extensão (pelo contrato
 * guided-debug/1): escolher a aba, inspecionar, Continuar (decisão, nada executado), Corrigir → selecionar → comparar →
 * aplicar (alvo efetivo trocado; captura intacta; desfazível), incompatível recusado, ambiguidade nunca confirmável,
 * segunda passagem sem pergunta e ator visível. Dados fictícios.
 */
class GuidedDebugUiTest {

    /** Navegador falso: respostas programáveis por tipo de comando; eventos emitidos pelo teste. */
    static final class FakeBrowser implements BrowserLink {
        final List<Listener> listeners = new CopyOnWriteArrayList<>();
        final List<String> commands = new CopyOnWriteArrayList<>();
        final List<Map<String, Object>> payloads = new CopyOnWriteArrayList<>();
        volatile Function<Map<String, Object>, String> inspect = p -> inspection("RESOLVED", "TARGET_ID", "SEARCH_INPUT", "Pesquisar", "[]");
        volatile String debugId;

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
            commands.add(type);
            payloads.add(payload);
            if (payload != null && payload.get("debugId") instanceof String id) debugId = id;
            String body;
            try {
                body = body(type, payload);
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
            return CompletableFuture.completedFuture(Json.parseObject(body));
        }

        private String body(String type, Map<String, Object> payload) {
            return switch (type) {
                case "browser.tabs.list" -> "{\"tabs\":[{\"tabRef\":\"t-LabTab000001\",\"title\":\"Consulta\",\"host\":\"127.0.0.1\",\"access\":\"LAB\","
                        + "\"active\":true,\"recording\":false},{\"tabRef\":\"t-Blocked00001\",\"title\":null,\"host\":\"portal.exemplo.com\","
                        + "\"access\":\"PERMISSION_REQUIRED\",\"active\":false,\"recording\":false}],\"truncated\":false}";
                case "studio.debug.inspect" -> inspect.apply(payload);
                case "studio.debug.pick" -> "{\"debug\":" + session() + ",\"requestId\":\"" + payload.get("requestId") + "\",\"picking\":true}";
                case "studio.debug.probe" -> "{\"debug\":" + session() + ",\"requestId\":\"" + payload.get("requestId") + "\",\"stepRef\":\"s\","
                        + "\"observedAt\":\"2026-10-17T12:01:00.000Z\",\"pageEpoch\":1,\"status\":\"RESOLVED\",\"performed\":true,\"operation\":\""
                        + payload.get("operation") + "\",\"reasons\":[],\"after\":{\"domChanged\":true,\"navigated\":false},\"duplicate\":false}";
                case "studio.debug.highlight" -> "{\"debug\":" + session() + ",\"requestId\":\"" + payload.get("requestId") + "\",\"highlighted\":true,\"box\":null}";
                default -> "{\"debug\":" + session() + ",\"duplicate\":false}";
            };
        }

        String session() {
            return "{\"debugId\":\"" + debugId + "\",\"protocol\":\"guided-debug/1\",\"state\":\"ACTIVE\",\"tab\":{\"tabRef\":\"t-LabTab000001\",\"title\":null,"
                    + "\"host\":\"127.0.0.1\"},\"pageEpoch\":1,\"picking\":false,\"lease\":{\"leaseMs\":60000,\"expiresInMs\":59000},\"reason\":null}";
        }

        static String inspection(String status, String source, String kind, String name, String candidates) {
            boolean ok = "RESOLVED".equals(status);
            return "{\"requestId\":\"rq\",\"stepRef\":\"s\",\"observedAt\":\"2026-10-17T12:00:03.000Z\",\"pageEpoch\":1,\"identitySource\":\"" + source + "\","
                    + "\"status\":\"" + status + "\",\"score\":0.9,\"reasons\":[],\"checks\":{\"identity\":\"" + (ok ? "PASS" : "FAIL") + "\",\"unique\":\""
                    + (ok ? "PASS" : "FAIL") + "\",\"visible\":\"PASS\",\"enabled\":\"PASS\",\"editable\":\"PASS\",\"kindMatchesAction\":\"PASS\","
                    + "\"frameReachable\":\"NOT_APPLICABLE\",\"shadowReachable\":\"NOT_APPLICABLE\"},\"element\":" + (ok ? "{\"kind\":\"" + kind + "\",\"name\":\""
                    + name + "\",\"tag\":\"input\",\"sensitive\":false}" : "null") + ",\"candidates\":" + candidates + ",\"highlighted\":" + ok
                    + ",\"box\":{\"x\":100,\"y\":200,\"width\":300,\"height\":30,\"viewportWidth\":1280,\"viewportHeight\":720,\"devicePixelRatio\":1.25,"
                    + "\"screenX\":108,\"screenY\":300},\"continuity\":null}";
        }

        void emit(String type, String json) {
            listeners.forEach(l -> l.event(new BridgeEvent(type, Json.parseObject(json))));
        }
    }

    private final FakeBrowser browser = new FakeBrowser();
    private final ScriptedPrompts prompts = new ScriptedPrompts();
    private Studio studio;

    @AfterEach
    void tearDown() {
        System.clearProperty("nekkora.studio.decisionActor");
        if (studio != null) Edt.run(studio::dispose);
    }

    private Studio studio() {
        Edt.theme(NekkoraThemes.SAKURA_DARK);
        studio = Edt.call(() -> Studio.create(StudioFixture.CLOCK, prompts, () -> { }, browser, RecorderPrompts.none(), p -> RecorderWindowHost.headless()));
        return studio;
    }

    private static ElementReference field(String targetId, String kind, String tag, String name, String css) {
        return ElementReference.builder().semanticKind(kind).tag(tag).accessibleName(name).privacyPolicy(CapturePrivacy.VERSION)
                .addLocator(new LocatorCandidate(LocatorStrategy.ID, "", css, LocatorStability.HEURISTIC_STABLE, 1, true)).preferLastLocator()
                .correlation(CorrelationKeys.of(Map.of(CorrelationKeys.Key.TARGET_ID, targetId))).build();
    }

    /** Consulta de registro: preencher (variável) e consultar — etapas gravadas com identidade lógica. */
    private Automation labCase() {
        return Edt.call(() -> {
            Automation a = studio.service().createAutomation("Consulta de registro (laboratório)", "");
            LogicalSession s = studio.service().addSession(a.id(), "Consulta", "");
            for (Object[] x : new Object[][] {{"Preencher código", ActionType.FILL, field("tgt-1", "SEARCH_INPUT", "input", "Pesquisar", "#registro-codigo")},
                    {"Consultar", ActionType.CLICK, field("tgt-2", "BUTTON", "button", "Consultar", "#consultar")}}) {
                ElementReference e = (ElementReference) x[2];
                studio.service().appendStep(a.id(), s.id(), StepDraft.builder().name((String) x[0]).actionType((ActionType) x[1]).element(e)
                        .provenance(StepProvenance.recorded(e.correlation().with(CorrelationKeys.Key.RECORDING_ID, "rec-1")
                                .with(CorrelationKeys.Key.RECORDED_EVENT_ID, "ev-" + x[0].hashCode()), new RecordingObservation(
                                RecordingObservation.CaptureMode.CAPTURE_AND_INTERACT, RecordingObservation.Observation.INTERACTION_OBSERVED, null, List.of(),
                                List.of())))
                        .parameters(x[1] == ActionType.FILL ? ActionParameters.withValue(new ValueSource.VariableReference("registro.codigo"))
                                : ActionParameters.none()).build());
            }
            return studio.state().activeAutomation().orElseThrow();
        });
    }

    private GuidedDebugPresenter debug() {
        return Edt.call(() -> studio.guidedDebug());
    }

    private GuidedDebugPanel.Card card() {
        return Edt.call(() -> debug().panel().card());
    }

    private void begin() {
        Edt.run(() -> studio.view().actions().get(com.nekkora.studio.app.ui.StudioActions.GUIDED_DEBUG).actionPerformed(null));
        Edt.flush();
        assertEquals(GuidedDebugPanel.Mode.CHOOSE_TAB, card().mode());
        assertEquals(1, card().tabs().size(), "só a aba autorizada é oferecida");
        Edt.run(() -> debug().panel().startButton().doClick());
        Edt.flush();
    }

    @Test
    @DisplayName("Continuar: alvo comprovado → decisão CONFIRMED do ator (desfazível), NADA executado; avança; ator visível")
    void continuar() {
        studio();
        Automation a = labCase();
        begin();
        assertEquals(GuidedDebugPanel.Mode.STEP, card().mode());
        assertEquals(GuidedDebugPanel.Badge.PROPOSED, card().badge());
        assertTrue(card().canContinue());
        assertEquals("Desenvolvedor", card().actor());
        assertTrue(card().why().get(0).startsWith("Elemento reencontrado pela identidade registrada"), card().why().toString());
        Edt.run(() -> debug().panel().continueButton().doClick());
        Edt.flush();
        AutomationStep fill = a.sessions().get(0).steps().get(0);
        TargetDecision d = fill.targetDecision().orElseThrow();
        assertEquals(TargetDecision.Kind.CONFIRMED, d.kind());
        assertEquals(DecisionActor.DEVELOPER, d.actor());
        assertEquals("RESOLVED", d.basis().status());
        assertEquals("Confirmar alvo", Edt.call(() -> studio.service().history().undoLabel(a.id()).orElse("")));
        assertTrue(card().title().contains("Consultar"), "avançou para a próxima etapa: " + card().title());
        assertFalse(browser.commands.stream().anyMatch(c -> c.contains("execute") || c.contains("probe")), "nenhum comando de execução: " + browser.commands);
        Edt.run(() -> studio.controller().undo());
        assertTrue(fill.targetDecision().isEmpty(), "Ctrl+Z desfaz a decisão");
    }

    @Test
    @DisplayName("ambíguo: requer revisão, Continuar desabilitado; Corrigir → rejeição → selecionar → comparar → aplicar troca só o alvo")
    void corrigir() {
        studio();
        Automation a = labCase();
        browser.inspect = p -> FakeBrowser.inspection("AMBIGUOUS", "DESCRIPTOR", "SEARCH_INPUT", "", "[{\"candidateRef\":\"cand-1-1\",\"verdict\":"
                + "\"CONFIRMED\",\"score\":0.7,\"kind\":\"SEARCH_INPUT\",\"name\":\"Pesquisar\",\"chosen\":false},{\"candidateRef\":\"cand-1-2\",\"verdict\":"
                + "\"CONFIRMED\",\"score\":0.7,\"kind\":\"SEARCH_INPUT\",\"name\":\"Ana Souza\",\"chosen\":false}]");
        begin();
        assertEquals(GuidedDebugPanel.Badge.REVIEW, card().badge());
        assertFalse(card().canContinue(), "ambiguidade nunca é confirmável");
        assertFalse(Edt.call(() -> debug().panel().continueButton().isEnabled()));
        assertTrue(card().candidates().stream().noneMatch(c -> c.label().contains("Ana")), "nome fora do vocabulário não aparece: " + card().candidates());
        AutomationStep fill = a.sessions().get(0).steps().get(0);
        ElementReference captured = fill.capturedElement().orElseThrow();
        Edt.run(() -> debug().panel().correctButton().doClick());
        Edt.flush();
        assertEquals(TargetDecision.Kind.REJECTED, fill.targetDecision().orElseThrow().kind(), "Corrigir rejeita a proposta");
        assertEquals(GuidedDebugPanel.Mode.CORRECTING, card().mode());
        Edt.run(() -> debug().panel().pickButton().doClick());
        Edt.flush();
        assertEquals(GuidedDebugPanel.Mode.PICKING, card().mode());
        String element = "{\"resolution\":\"RESOLVED\",\"kind\":\"SEARCH_INPUT\",\"role\":null,\"name\":\"Pesquisar\",\"tag\":\"input\",\"htmlId\":\"codigo-v2\","
                + "\"htmlName\":null,\"sensitive\":false,\"locators\":[{\"strategy\":\"ID\",\"description\":\"\",\"css\":\"#codigo-v2\",\"xpath\":null,\"matchCount\":1,"
                + "\"matchesTarget\":true,\"stability\":\"HEURISTIC_STABLE\",\"validation\":\"LIVE\"}],\"preferredLocator\":0,\"accessPath\":[],\"context\":[],"
                + "\"lookalikes\":0,\"correlation\":{\"targetId\":\"tgt-9\",\"nodeId\":\"n9\",\"captureId\":\"cap-2\",\"documentId\":\"doc:f0\"}}";
        String checks = "{\"identity\":\"NOT_APPLICABLE\",\"unique\":\"NOT_APPLICABLE\",\"visible\":\"PASS\",\"enabled\":\"PASS\",\"editable\":\"PASS\","
                + "\"kindMatchesAction\":\"PASS\",\"frameReachable\":\"NOT_APPLICABLE\",\"shadowReachable\":\"NOT_APPLICABLE\"}";
        browser.emit("studio.debug.picked", "{\"debugId\":\"" + browser.debugId + "\",\"requestId\":\"rq-x\",\"pageEpoch\":1,\"observedAt\":"
                + "\"2026-10-17T12:00:09.000Z\",\"origin\":\"BROWSER_SELECTION\",\"element\":" + element + ",\"checks\":" + checks + ",\"box\":null}");
        Edt.flush();
        assertEquals(GuidedDebugPanel.Mode.COMPARING, card().mode());
        assertTrue(card().comparison().compatible());
        assertTrue(Edt.call(() -> debug().panel().applyButton().isEnabled()));
        browser.inspect = p -> FakeBrowser.inspection("RESOLVED", "TARGET_ID", "SEARCH_INPUT", "Pesquisar", "[]");
        Edt.run(() -> debug().panel().applyButton().doClick());
        Edt.flush();
        assertEquals("tgt-9", fill.element().orElseThrow().correlation().get(CorrelationKeys.Key.TARGET_ID).orElseThrow(), "alvo efetivo trocado");
        assertSame(captured, fill.capturedElement().orElseThrow(), "a captura original nunca é reescrita");
        assertTrue(fill.modified(StepField.TARGET));
        assertEquals(TargetDecision.Kind.CONFIRMED, fill.targetDecision().orElseThrow().kind());
        assertEquals("SELECTED_IN_BROWSER", fill.targetDecision().orElseThrow().basis().status());
        assertEquals("Corrigir alvo", Edt.call(() -> studio.service().history().undoLabel(a.id()).orElse("")));
        assertTrue(Edt.call(() -> studio.state().project(a.id()).orElseThrow().dirty()), "projeto alterado");
        Edt.run(() -> studio.controller().undo());
        assertTrue(fill.replacementTarget().isEmpty(), "desfazer volta ao alvo anterior");
    }

    @Test
    @DisplayName("escolha incompatível (botão para preencher): Aplicar desabilitado; nada muda")
    void incompativel() {
        studio();
        Automation a = labCase();
        browser.inspect = p -> FakeBrowser.inspection("NOT_FOUND", "TARGET_ID", "", "", "[]");
        begin();
        assertEquals(GuidedDebugPanel.Badge.NOT_VERIFIABLE, card().badge());
        Edt.run(() -> debug().panel().correctButton().doClick());
        Edt.run(() -> debug().panel().pickButton().doClick());
        String button = "{\"resolution\":\"RESOLVED\",\"kind\":\"BUTTON\",\"role\":\"button\",\"name\":\"Consultar\",\"tag\":\"button\",\"htmlId\":\"consultar\","
                + "\"htmlName\":null,\"sensitive\":false,\"locators\":[],\"preferredLocator\":-1,\"accessPath\":[],\"context\":[],\"lookalikes\":0,"
                + "\"correlation\":{\"targetId\":\"tgt-2\"}}";
        browser.emit("studio.debug.picked", "{\"debugId\":\"" + browser.debugId + "\",\"requestId\":\"rq-x\",\"pageEpoch\":1,\"observedAt\":"
                + "\"2026-10-17T12:00:09.000Z\",\"origin\":\"BROWSER_SELECTION\",\"element\":" + button + ",\"checks\":{},\"box\":null}");
        Edt.flush();
        assertFalse(card().comparison().compatible());
        assertFalse(Edt.call(() -> debug().panel().applyButton().isEnabled()));
        assertTrue(card().comparison().verdict().startsWith("Incompatível"));
        assertTrue(a.sessions().get(0).steps().get(0).replacementTarget().isEmpty());
    }

    @Test
    @DisplayName("segunda passagem: confirmado e comprovado de novo → reconfirmado sem pergunta e avança sozinho; agente de teste rotulado")
    void reuso() throws Exception {
        System.setProperty("nekkora.studio.decisionActor", "TEST_AGENT");
        studio();
        Automation a = labCase();
        begin();
        assertEquals("Agente de teste (laboratório)", card().actor());
        Edt.run(() -> debug().panel().continueButton().doClick());
        Edt.run(() -> debug().panel().continueButton().doClick());
        Edt.flush();
        assertEquals(GuidedDebugPanel.Mode.FINISHED, card().mode());
        assertEquals(DecisionActor.TEST_AGENT, a.sessions().get(0).steps().get(0).targetDecision().orElseThrow().actor());
        // segunda passagem
        Edt.run(() -> debug().finish());
        begin();
        assertEquals(GuidedDebugPanel.Badge.REUSED, card().badge(), card().message());
        Thread.sleep(GuidedDebugPresenter.REUSE_ADVANCE_MS + 600);
        Edt.flush();
        assertTrue(card().title().contains("Consultar") || card().mode() == GuidedDebugPanel.Mode.FINISHED, "avançou sem pergunta: " + card().title());
        assertNotNull(debug().journal().stream().filter(e -> "REUSE".equals(e.outcome())).findFirst().orElse(null), "passagem registrada como reuso");
    }

    @Test
    @DisplayName("G8: comprovado mas oculto/desabilitado agora → requer revisão (nunca \"proposto\"); Continuar segue possível, com aviso")
    void estadoInadequado() {
        studio();
        labCase();
        browser.inspect = p -> FakeBrowser.inspection("RESOLVED", "TARGET_ID", "SEARCH_INPUT", "Pesquisar", "[]").replace("\"visible\":\"PASS\"",
                "\"visible\":\"FAIL\"");
        begin();
        assertEquals(GuidedDebugPanel.Badge.REVIEW, card().badge());
        assertTrue(card().message().startsWith("O elemento foi comprovado, mas agora não está em condição"), card().message());
        assertTrue(card().canContinue(), "a identidade foi comprovada: a decisão continua possível, com aviso");
    }

    @Test
    @DisplayName("G8: canal perdido no meio do debug → sessão encerrada, nada confirmável; a próxima abertura encerra a sessão órfã antes")
    void canalPerdido() {
        studio();
        labCase();
        begin();
        assertTrue(card().canContinue());
        String lost = browser.debugId;
        browser.listeners.forEach(l -> l.statusChanged(LinkStatus.of(LinkStatus.State.WAITING, "teste")));
        Edt.flush();
        assertEquals(GuidedDebugPanel.Mode.FINISHED, card().mode());
        assertFalse(card().canContinue(), "nada é confirmado com base numa verificação de antes da queda");
        assertTrue(card().message().startsWith("A conexão com o navegador caiu"), card().message());
        Edt.run(() -> debug().panel().finishButton().doClick());
        int from = browser.commands.size();
        begin();
        List<String> after = browser.commands.subList(from, browser.commands.size());
        int fin = -1;
        for (int i = 0; i < after.size(); i++) {
            if ("studio.debug.finish".equals(after.get(i)) && lost.equals(browser.payloads.get(from + i).get("debugId"))) fin = i;
        }
        assertTrue(fin >= 0 && fin < after.indexOf("studio.debug.start"), "a sessão órfã é encerrada antes da nova: " + after);
        assertEquals(GuidedDebugPanel.Mode.STEP, card().mode());
    }

    @Test
    @DisplayName("navegador desligado: explica e não abre sessão; extensão antiga: mensagem de atualização")
    void indisponivel() {
        studio();
        labCase();
        browser.inspect = p -> {
            throw new com.nekkora.studio.bridge.BridgeCommandException(com.nekkora.studio.bridge.BridgeError.UNKNOWN_COMMAND, "x", null, false);
        };
        begin();
        assertTrue(card().message().contains("Atualize a Nekkora Extension") || card().badge() == GuidedDebugPanel.Badge.NOT_VERIFIABLE, card().message());
    }

    @Test
    @DisplayName("G6 sonda: só em aba LAB com alvo comprovado; envia a CHAVE do dado fictício (nunca o valor); mensagem separa os fatos")
    void sonda() {
        studio();
        labCase();
        begin();
        assertTrue(Edt.call(() -> debug().panel().probeButton().isShowing() || debug().panel().probeButton().getParent().isVisible()));
        assertEquals("Preencher (laboratório)", Edt.call(() -> debug().panel().probeButton().getText()));
        Edt.run(() -> debug().panel().probeKeysInput().setSelectedItem("lab:registro-suspenso"));
        Edt.run(() -> debug().panel().probeButton().doClick());
        Edt.flush();
        Map<String, Object> sent = browser.payloads.get(browser.commands.lastIndexOf("studio.debug.probe"));
        assertEquals("lab:registro-suspenso", sent.get("valueKey"));
        assertEquals("LAB_PROBE", sent.get("authorization"));
        assertFalse(Json.write(sent).contains("REG-"), "o Studio não conhece o valor");
        assertTrue(card().message().startsWith("Operação efetuada no laboratório"), card().message());
        assertTrue(card().message().contains("não é verificado aqui"));
        browser.inspect = p -> FakeBrowser.inspection("AMBIGUOUS", "DESCRIPTOR", "", "", "[]");
        Edt.run(() -> debug().next());
        Edt.flush();
        assertFalse(Edt.call(() -> debug().panel().probeButton().getParent().isVisible()), "sem alvo comprovado, sem sonda");
    }
}
