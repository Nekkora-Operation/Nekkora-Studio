package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.app.pdd.PddPresenter;
import com.nekkora.studio.app.recorder.RecorderPrompts;
import com.nekkora.studio.app.recorder.RecorderWindowHost;
import com.nekkora.studio.bridge.BridgeEvent;
import com.nekkora.studio.bridge.BrowserLink;
import com.nekkora.studio.bridge.LinkStatus;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.Automation;
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
import com.nekkora.studio.ui.pdd.PddPanel;
import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * STUDIO-010 G6 pela interface — Studio REAL com um navegador falso que responde pelo contrato pdd-capture/1: preparar
 * o rascunho, revisar, desfazer/refazer, capturar (imagem aceita, falha e imagem adulterada recusada pela 4ª barreira),
 * prévia, exportar versões, automação nunca alterada, quatro temas. Dados fictícios.
 */
class PddUiTest {
    @TempDir
    Path out;

    /** Navegador falso: abas e captura armada; o "gesto" é o teste emitindo studio.pdd.captured. */
    static final class FakeBrowser implements BrowserLink {
        final List<Listener> listeners = new CopyOnWriteArrayList<>();
        final List<String> commands = new CopyOnWriteArrayList<>();
        volatile Map<String, Object> lastArm;

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
            String body = switch (type) {
                case "browser.tabs.list" -> "{\"tabs\":[{\"tabRef\":\"t-LabTab000001\",\"title\":\"Entrar\",\"host\":\"127.0.0.1\",\"access\":\"LAB\","
                        + "\"active\":true,\"recording\":false},{\"tabRef\":\"t-Site0000001\",\"title\":null,\"host\":\"site-a.exemplo\",\"access\":\"AUTHORIZED\","
                        + "\"active\":false,\"recording\":false}],\"truncated\":false}";
                case "studio.pdd.capture.arm" -> {
                    lastArm = payload;
                    yield "{\"captureId\":\"" + payload.get("captureId") + "\",\"armed\":true,\"duplicate\":false,\"shortcut\":\"Alt+Shift+K\"}";
                }
                case "studio.pdd.capture.cancel" -> "{\"captureId\":\"" + payload.get("captureId") + "\",\"cancelled\":true}";
                default -> "{}";
            };
            return CompletableFuture.completedFuture(Json.parseObject(body));
        }

        void emit(String json) {
            listeners.forEach(l -> l.event(new BridgeEvent("studio.pdd.captured", Json.parseObject(json))));
        }
    }

    private final FakeBrowser browser = new FakeBrowser();
    private Studio studio;

    @AfterEach
    void tearDown() {
        if (studio != null) Edt.run(studio::dispose);
    }

    private Studio studio() {
        Edt.theme(NekkoraThemes.SAKURA_DARK);
        studio = Edt.call(() -> Studio.create(StudioFixture.CLOCK, new ScriptedPrompts(), () -> { }, browser, RecorderPrompts.none(),
                p -> RecorderWindowHost.headless()));
        studio.useVisualPddHosts(p -> PddPresenter.Host.headless(out));
        return studio;
    }

    private static ElementReference el(String targetId, String kind, String tag, String name, String css, boolean sensitive) {
        return ElementReference.builder().semanticKind(kind).tag(tag).accessibleName(name).sensitive(sensitive).privacyPolicy(CapturePrivacy.VERSION)
                .addLocator(new LocatorCandidate(LocatorStrategy.ID, "", css, LocatorStability.HEURISTIC_STABLE, 1, true)).preferLastLocator()
                .correlation(CorrelationKeys.of(Map.of(CorrelationKeys.Key.TARGET_ID, targetId))).build();
    }

    /** "Consulta de registros" (fictícia): Autenticação (usuário, senha, entrar) e Consulta. */
    private Automation lab() {
        return Edt.call(() -> {
            Automation a = studio.service().createAutomation("Consulta de registros", "");
            LogicalSession auth = studio.service().addSession(a.id(), "Autenticação", "");
            Object[][] steps = {{"Preencher usuário ana.souza", ActionType.FILL, el("tgt-u", "TEXT_INPUT", "input", "Usuário", "#usuario", false),
                    ActionParameters.withValue(new ValueSource.LiteralValue("ana.souza"))},
                    {"Senha", ActionType.FILL, el("tgt-s", "PASSWORD_INPUT", "input", "Senha", "#senha", true),
                            ActionParameters.withValue(new ValueSource.CredentialReference("cofre.senha-lab"))},
                    {"Entrar", ActionType.CLICK, el("tgt-e", "BUTTON", "button", "Entrar", "#entrar", false), ActionParameters.none()}};
            for (Object[] x : steps) {
                ElementReference e = (ElementReference) x[2];
                studio.service().appendStep(a.id(), auth.id(), StepDraft.builder().name((String) x[0]).actionType((ActionType) x[1]).element(e)
                        .parameters((ActionParameters) x[3]).context(new StepContext("Laboratório", "Entrar", "http://127.0.0.1:18765/lab/studio010/login.html"))
                        .provenance(StepProvenance.recorded(e.correlation().with(CorrelationKeys.Key.RECORDING_ID, "rec-1")
                                .with(CorrelationKeys.Key.RECORDED_EVENT_ID, "ev-" + x[0].hashCode()), new RecordingObservation(
                                RecordingObservation.CaptureMode.CAPTURE_AND_INTERACT, RecordingObservation.Observation.INTERACTION_OBSERVED, null, List.of(),
                                List.of()))).build());
            }
            LogicalSession consulta = studio.service().addSession(a.id(), "Consulta", "");
            studio.service().appendStep(a.id(), consulta.id(), StepDraft.builder().name("Consultar").actionType(ActionType.CLICK)
                    .element(el("tgt-k", "BUTTON", "button", "Consultar", "#consultar", false))
                    .context(new StepContext("Laboratório", "Consulta", "http://127.0.0.1:18765/lab/studio010/consulta.html")).build());
            return studio.state().activeAutomation().orElseThrow();
        });
    }

    private PddPresenter pdd() {
        return Edt.call(studio::visualPdd);
    }

    private PddPanel.Model model() {
        return Edt.call(() -> pdd().panel().model());
    }

    private void open() {
        Edt.run(() -> studio.view().actions().get(com.nekkora.studio.app.ui.StudioActions.VISUAL_PDD).actionPerformed(null));
        Edt.flush();
        Edt.flush();
    }

    /** PNG sanitizado sintético 400×200: fundo opaco, 1 rótulo e 1 campo; {@code leak} põe um pixel fora da regra. */
    static String captured(String captureId, String stepRef, boolean leak) throws Exception {
        BufferedImage img = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 200; y++) for (int x = 0; x < 400; x++) img.setRGB(x, y, 0xD5D8DD);
        for (int y = 20; y < 40; y++) for (int x = 20; x < 80; x++) img.setRGB(x, y, 0x1F2328);
        for (int y = 50; y < 80; y++) for (int x = 20; x < 220; x++) img.setRGB(x, y, y == 50 || y == 79 || x == 20 || x == 219 ? 0x8A9099 : 0xFFFFFF);
        if (leak) img.setRGB(300, 150, 0xE6007E);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(img, "png", o);
        byte[] png = o.toByteArray();
        String sha = "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png));
        return "{\"captureId\":\"" + captureId + "\",\"status\":\"CAPTURED\",\"policy\":\"nekkora-visual/1\",\"observedAt\":\"2026-10-17T12:00:00Z\","
                + "\"width\":400,\"height\":200,\"devicePixelRatio\":1,\"png\":\"" + Base64.getEncoder().encodeToString(png) + "\",\"sha256\":\"" + sha
                + "\",\"regions\":[{\"kind\":\"CARRIER\",\"x\":20,\"y\":20,\"width\":60,\"height\":20},{\"kind\":\"FIELD\",\"x\":20,\"y\":50,\"width\":200,"
                + "\"height\":30}],\"annotations\":[{\"stepRef\":\"" + stepRef + "\",\"status\":\"PLACED\",\"x\":0.05,\"y\":0.25,\"width\":0.5,\"height\":0.15,"
                + "\"reason\":\"\"}],\"limitations\":[]}";
    }

    @Test
    @DisplayName("G6 preparar: seções e blocos na ordem; título neutro bloqueia a exportação até revisar; desfazer/refazer do rascunho; automação intocada")
    void prepararRevisarDesfazer() {
        studio();
        Automation a = lab();
        long revision = a.revision();
        open();
        PddPanel.Model m = model();
        assertEquals(List.of("1. Sessão 1 (revisar)", "    Preencher os campos e selecionar “Entrar”", "2. Consulta", "    Selecionar “Consultar”"),
                m.items().stream().map(PddPanel.Item::label).toList());
        assertFalse(m.canExport(), "título neutro da sessão bloqueia");
        Edt.run(() -> pdd().select("secao-1"));
        Edt.run(() -> pdd().saveTitle("secao-1", "Autenticação"));
        assertTrue(model().canExport(), model().status());
        assertTrue(model().preview().size() >= 1, "prévia = o PDF rasterizado");
        Edt.run(() -> pdd().undo());
        assertFalse(model().canExport());
        Edt.run(() -> pdd().redo());
        assertTrue(model().canExport());
        Edt.run(() -> pdd().saveTitle("secao-1", "CPF 123.456.789-09"));
        assertEquals("Alteração recusada", model().bannerTitle(), "texto com dado pessoal recusado");
        assertEquals(revision, a.revision(), "a automação nunca muda");
    }

    @Test
    @DisplayName("G4/G6 captura: arma com targetId (nunca valor), imagem aceita vira bloco com imagem; falha vira aviso; imagem adulterada é recusada pela 4ª barreira")
    void captura() throws Exception {
        studio();
        Automation a = lab();
        open();
        String login = model().items().get(1).key();
        Edt.run(() -> pdd().select(login));
        Edt.flush();
        assertEquals(1, model().tabs().size(), "só a aba de laboratório");
        Edt.run(() -> pdd().capture(login, "t-LabTab000001"));
        Edt.flush();
        assertTrue(model().banner().contains("Alt+Shift+K"), model().banner());
        String arm = Json.write(browser.lastArm);
        assertTrue(arm.contains("tgt-u") && !arm.contains("ana.souza") && !arm.contains("cofre.senha-lab"), "arma com identidade, nunca valores");
        String cid = (String) browser.lastArm.get("captureId");
        String first = a.sessions().get(0).steps().get(0).id().value().toString();
        browser.emit(captured(cid, first, false));
        Edt.flush();
        Edt.flush();
        assertTrue(model().hasImage(), model().banner());
        assertEquals("Imagem sanitizada associada", model().bannerTitle());
        // falha
        Edt.run(() -> pdd().capture(login, "t-LabTab000001"));
        Edt.flush();
        String cid2 = (String) browser.lastArm.get("captureId");
        browser.emit("{\"captureId\":\"" + cid2 + "\",\"status\":\"FAILED\",\"code\":\"PAGE_CHANGED_DURING_CAPTURE\",\"observedAt\":\"x\"}");
        Edt.flush();
        Edt.flush();
        assertTrue(model().banner().startsWith("A página mudou durante a captura"), model().banner());
        // adulterada (1 pixel fora da regra): 4ª barreira
        Edt.run(() -> pdd().removeCapture(login));
        Edt.run(() -> pdd().capture(login, "t-LabTab000001"));
        Edt.flush();
        browser.emit(captured((String) browser.lastArm.get("captureId"), first, true));
        Edt.flush();
        Edt.flush();
        assertEquals("Imagem recusada pelo Studio", model().bannerTitle());
        assertFalse(model().hasImage());
    }

    @Test
    @DisplayName("G7/G8 exportar: v001 e v002 na pasta escolhida, sem sobrescrever; automação alterada → aviso e pendência até atualizar")
    void exportarVersoes() throws Exception {
        studio();
        Automation a = lab();
        open();
        Edt.run(() -> pdd().saveTitle("secao-1", "Autenticação"));
        Edt.run(() -> pdd().export());
        Edt.run(() -> pdd().export());
        assertTrue(Files.exists(out.resolve("pdd-visual-v001.pdf")) && Files.exists(out.resolve("pdd-visual-v002.pdf")), model().banner());
        Edt.run(() -> studio.service().appendStep(a.id(), a.sessions().get(1).id(), StepDraft.builder().name("w").actionType(ActionType.WAIT_FOR)
                .context(new StepContext("Laboratório", "Consulta", "http://127.0.0.1:18765/lab/studio010/consulta.html")).build()));
        Edt.run(() -> pdd().select("secao-2"));
        assertFalse(model().canExport(), "processo mudou");
        assertEquals("A automação mudou", model().bannerTitle());
        Edt.run(() -> pdd().refresh());
        Edt.run(() -> pdd().select("secao-1"));
        assertEquals("PDF desatualizado", model().bannerTitle(), "o último PDF exportado ficou desatualizado");
    }

    @Test
    @DisplayName("G6 temas e acessibilidade: a tela do PDD monta nos quatro temas; botões e campos com nome acessível")
    void temas() {
        studio();
        lab();
        open();
        for (NekkoraTheme t : NekkoraThemes.all()) {
            Edt.theme(t);
            Edt.run(() -> pdd().select("secao-1"));
            assertTrue(model().items().size() >= 4);
        }
        PddPanel p = Edt.call(() -> pdd().panel());
        assertEquals("Título", p.titleField().getAccessibleContext().getAccessibleName());
        assertTrue(p.exportButton().getAccessibleContext().getAccessibleDescription().contains("nunca substitui"));
    }
}
