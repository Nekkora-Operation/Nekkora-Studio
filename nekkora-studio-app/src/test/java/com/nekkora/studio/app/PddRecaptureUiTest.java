package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.nekkora.studio.pdd.DocumentDraft;
import com.nekkora.studio.pdd.VisualVerifier;
import com.nekkora.studio.ui.pdd.PddPanel;
import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import java.awt.Rectangle;
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
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * STUDIO-012 PDD-01 pela interface — Studio REAL com navegador falso que fala a {@code nekkora-visual/2}: captura nova
 * como CANDIDATA (revisar → Aplicar/Cancelar), recorte por seleção na imagem, marcador manual, desfazer seguro (a imagem
 * anterior continua no disco), falhas e mudanças de processo que nunca trocam a imagem do bloco, e a automação intocada.
 * Dados fictícios.
 */
class PddRecaptureUiTest {
    @TempDir
    Path out;

    /** Navegador falso: extensão 0.5.0 (entende a v2); o "gesto" é o teste emitindo studio.pdd.captured. */
    static final class FakeBrowser implements BrowserLink {
        final List<Listener> listeners = new CopyOnWriteArrayList<>();
        volatile Map<String, Object> lastArm;
        volatile String version = "0.5.0";

        @Override
        public LinkStatus status() {
            return new LinkStatus(LinkStatus.State.CONNECTED, "teste", new LinkStatus.ExtensionInfo(version, "DEV", "nekkora-privacy/2"));
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
            String body = switch (type) {
                case "browser.tabs.list" -> "{\"tabs\":[{\"tabRef\":\"t-LabTab000001\",\"title\":\"Entrar\",\"host\":\"127.0.0.1\",\"access\":\"LAB\","
                        + "\"active\":true,\"recording\":false}],\"truncated\":false}";
                case "studio.pdd.capture.arm" -> {
                    lastArm = payload;
                    String policy = (String) payload.get("policy");
                    yield "{\"captureId\":\"" + payload.get("captureId") + "\",\"armed\":true,\"duplicate\":false,\"shortcut\":\"Alt+Shift+K\""
                            + (policy == null ? "" : ",\"policy\":\"" + policy + "\"") + "}";
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

    private void studio() {
        Edt.theme(NekkoraThemes.SAKURA_DARK);
        studio = Edt.call(() -> Studio.create(StudioFixture.CLOCK, new ScriptedPrompts(), () -> { }, browser, RecorderPrompts.none(),
                p -> RecorderWindowHost.headless()));
        studio.useVisualPddHosts(p -> PddPresenter.Host.headless(out));
    }

    private static ElementReference el(String targetId, String kind, String tag, String name, String css, boolean sensitive) {
        return ElementReference.builder().semanticKind(kind).tag(tag).accessibleName(name).sensitive(sensitive).privacyPolicy(CapturePrivacy.VERSION)
                .addLocator(new LocatorCandidate(LocatorStrategy.ID, "", css, LocatorStability.HEURISTIC_STABLE, 1, true)).preferLastLocator()
                .correlation(CorrelationKeys.of(Map.of(CorrelationKeys.Key.TARGET_ID, targetId))).build();
    }

    /** Login fictício: usuário, senha, entrar (input submit). */
    private Automation lab() {
        return Edt.call(() -> {
            Automation a = studio.service().createAutomation("Login de laboratório", "");
            LogicalSession auth = studio.service().addSession(a.id(), "Autenticação", "");
            Object[][] steps = {{"Preencher usuário ana.souza", ActionType.FILL, el("tgt-u", "TEXT_INPUT", "input", "Usuário", "#ds_login", false),
                    ActionParameters.withValue(new ValueSource.LiteralValue("ana.souza"))},
                    {"Senha", ActionType.FILL, el("tgt-s", "PASSWORD_INPUT", "input", "Senha", "#passwordTemp", true),
                            ActionParameters.withValue(new ValueSource.CredentialReference("cofre.senha-lab"))},
                    {"Entrar", ActionType.CLICK, el("tgt-e", "BUTTON", "input", "Entrar", "#Button_DoLogin", false), ActionParameters.none()}};
            for (Object[] x : steps) {
                ElementReference e = (ElementReference) x[2];
                studio.service().appendStep(a.id(), auth.id(), StepDraft.builder().name((String) x[0]).actionType((ActionType) x[1]).element(e)
                        .parameters((ActionParameters) x[3]).context(new StepContext("Laboratório", "Entrar", "http://127.0.0.1:18765/lab/studio012/pdd-login.html"))
                        .provenance(StepProvenance.recorded(e.correlation().with(CorrelationKeys.Key.RECORDING_ID, "rec-1")
                                .with(CorrelationKeys.Key.RECORDED_EVENT_ID, "ev-" + x[0].hashCode()), new RecordingObservation(
                                RecordingObservation.CaptureMode.CAPTURE_AND_INTERACT, RecordingObservation.Observation.INTERACTION_OBSERVED, null, List.of(),
                                List.of()))).build());
            }
            return studio.state().activeAutomation().orElseThrow();
        });
    }

    private PddPresenter pdd() {
        return Edt.call(studio::visualPdd);
    }

    private PddPanel.Model model() {
        return Edt.call(() -> pdd().panel().model());
    }

    private String openLogin() {
        Edt.run(() -> studio.view().actions().get(com.nekkora.studio.app.ui.StudioActions.VISUAL_PDD).actionPerformed(null));
        Edt.flush();
        Edt.flush();
        Edt.run(() -> pdd().saveTitle("secao-1", "Autenticação"));
        String login = model().items().get(1).key();
        Edt.run(() -> pdd().select(login));
        Edt.flush();
        return login;
    }

    /** Representação v2 sintética 400×200: página, cartão (MID ou LIGHT), campo, rótulo e botão-portador; {@code leak} = pixel real fora. */
    static String capturedV2(String captureId, Automation a, boolean placeButton, String cardTone, boolean leak) throws Exception {
        int w = 400, h = 200;
        String[][] regions = {{"SURFACE", "20", "10", "360", "180", cardTone}, {"FIELD", "40", "60", "200", "24", null},
            {"CARRIER", "40", "36", "60", "18", null}, {"CARRIER", "260", "120", "60", "24", null}};
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, VisualVerifier.V2_PAGE);
        StringBuilder rj = new StringBuilder();
        for (String[] r : regions) {
            int x0 = Integer.parseInt(r[1]), y0 = Integer.parseInt(r[2]), rw = Integer.parseInt(r[3]), rh = Integer.parseInt(r[4]);
            for (int y = y0; y < y0 + rh; y++) {
                for (int x = x0; x < x0 + rw; x++) {
                    if ("CARRIER".equals(r[0])) {
                        img.setRGB(x, y, rw == 60 && rh == 24 ? 0x333333 : 0x1F2328);
                        continue;
                    }
                    int[] fb = VisualVerifier.v2Paint(r[0], r[5]);
                    img.setRGB(x, y, y == y0 || y == y0 + rh - 1 || x == x0 || x == x0 + rw - 1 ? fb[1] : fb[0]);
                }
            }
            if (rj.length() > 0) rj.append(',');
            rj.append("{\"kind\":\"").append(r[0]).append("\",\"x\":").append(x0).append(",\"y\":").append(y0).append(",\"width\":").append(rw)
                    .append(",\"height\":").append(rh).append(r[5] == null ? "" : ",\"tone\":\"" + r[5] + "\"").append('}');
        }
        if (leak) img.setRGB(200, 150, 0xE6007E);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(img, "png", o);
        byte[] png = o.toByteArray();
        String sha = "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png));
        var steps = a.sessions().get(0).steps();
        String u = steps.get(0).id().value().toString();
        String s = steps.get(1).id().value().toString();
        String e = steps.get(2).id().value().toString();
        String button = placeButton ? "{\"stepRef\":\"" + e + "\",\"status\":\"PLACED\",\"x\":0.65,\"y\":0.6,\"width\":0.15,\"height\":0.12,\"reason\":\"\"}"
                : "{\"stepRef\":\"" + e + "\",\"status\":\"OMITTED\",\"x\":0,\"y\":0,\"width\":0,\"height\":0,\"reason\":\"AMBIGUOUS\"}";
        return "{\"captureId\":\"" + captureId + "\",\"status\":\"CAPTURED\",\"policy\":\"nekkora-visual/2\",\"observedAt\":\"2026-10-18T12:00:00Z\","
                + "\"width\":400,\"height\":200,\"devicePixelRatio\":1,\"png\":\"" + Base64.getEncoder().encodeToString(png) + "\",\"sha256\":\"" + sha
                + "\",\"regions\":[" + rj + "],\"annotations\":[{\"stepRef\":\"" + u + "\",\"status\":\"PLACED\",\"x\":0.1,\"y\":0.3,\"width\":0.5,"
                + "\"height\":0.12,\"reason\":\"\"},{\"stepRef\":\"" + s + "\",\"status\":\"OMITTED\",\"x\":0,\"y\":0,\"width\":0,\"height\":0,"
                + "\"reason\":\"TARGET_OUTSIDE_VIEWPORT\"}," + button + "],\"limitations\":[]}";
    }

    private void shoot(String login, String json) {
        Edt.run(() -> pdd().capture(login, "t-LabTab000001"));
        Edt.flush();
        browser.emit(json.replace("CID", (String) browser.lastArm.get("captureId")));
        Edt.flush();
        Edt.flush();
    }

    private DocumentDraft draft() {
        return Edt.call(() -> pdd().draft().orElseThrow());
    }

    private String imageOf(String blockId) {
        DocumentDraft d = draft();
        return d.captures().get(d.block(blockId).orElseThrow().captureSha()).sha256();
    }

    @Test
    @DisplayName("PDD01 recaptura: 1ª captura v2 associa; Capturar novamente = candidata (prévia mostra, bloco não muda); Cancelar mantém; Aplicar substitui; Desfazer volta com o arquivo no disco")
    void recapturaAplicarCancelarDesfazer() throws Exception {
        studio();
        Automation a = lab();
        long revision = a.revision();
        String login = openLogin();
        shoot(login, capturedV2("CID", a, true, "MID", false));
        assertEquals("nekkora-visual/2", browser.lastArm.get("policy"), "o Studio pede a v2 à extensão que a entende");
        assertTrue(model().hasImage(), model().banner());
        assertEquals("Imagem associada · rascunho 1.1.0", model().bannerTitle(), "a subida do formato do rascunho é avisada");
        assertNotNull(model().image());
        assertFalse(model().reviewing());
        String first = imageOf(login);
        // Capturar novamente → CANCELAR
        shoot(login, capturedV2("CID", a, true, "LIGHT", false));
        assertTrue(model().reviewing(), model().banner());
        assertEquals("Nova captura pronta para revisão", model().bannerTitle());
        assertEquals(first, imageOf(login), "o bloco continua com a anterior enquanto a nova é revisada");
        assertFalse(model().canExport(), "não exporta com uma candidata pendente");
        assertEquals("Nova captura em revisão", model().status(), "o selo não diz \"Pronto para exportar\" com a exportação bloqueada");
        assertTrue(Edt.call(() -> pdd().panel().applyButton().isShowing() || pdd().panel().applyButton().isVisible()));
        Edt.run(() -> pdd().panel().cancelCandidateButton().doClick());
        assertFalse(model().reviewing());
        assertEquals("Nova captura descartada", model().bannerTitle());
        assertEquals(first, imageOf(login));
        // Capturar novamente → recorte na candidata (pelo editor) → APLICAR
        shoot(login, capturedV2("CID", a, true, "LIGHT", false));
        Edt.run(() -> {
            pdd().panel().selectAreaButton().doClick();
            pdd().panel().imageEditor().select(new Rectangle(20, 10, 360, 180));
        });
        assertEquals(new Rectangle(20, 10, 360, 180), model().image().crop(), "recorte da candidata");
        Edt.run(() -> pdd().panel().applyButton().doClick());
        Edt.flush();
        assertEquals("Imagem substituída", model().bannerTitle(), model().banner());
        String second = imageOf(login);
        assertNotEquals(first, second);
        DocumentDraft.SafeCapture now = draft().captures().get(draft().block(login).orElseThrow().captureSha());
        assertEquals(360, now.crop().width());
        assertEquals("TEST_AGENT".equals(now.crop().reviewer()) || "DEVELOPER".equals(now.crop().reviewer()), true);
        // DESFAZER: volta à anterior, e o arquivo dela continua no disco (achado A1)
        Edt.run(() -> pdd().undo());
        assertEquals(first, imageOf(login));
        assertTrue(Edt.call(() -> pdd().store().capture(draft().automationId(), first)).isPresent(), "o arquivo da anterior não foi apagado");
        assertNotNull(model().image(), "a prévia/editor mostram a imagem anterior");
        Edt.run(() -> pdd().redo());
        assertEquals(second, imageOf(login));
        Edt.run(() -> pdd().export());
        assertTrue(Files.exists(out.resolve("pdd-visual-v001.pdf")), model().banner());
        assertEquals(revision, a.revision(), "a automação nunca muda");
        // fim do histórico (Studio encerrando): a imagem que só o desfazer alcançava sai do disco; a referenciada fica
        Edt.run(() -> pdd().dispose());
        assertFalse(Edt.call(() -> pdd().store().capture(draft().automationId(), first)).isPresent(), "a anterior, sem histórico, foi apagada");
        assertTrue(Edt.call(() -> pdd().store().capture(draft().automationId(), second)).isPresent(), "a imagem do bloco continua");
    }

    @Test
    @DisplayName("PDD01 recaptura que falha (troca de aba, página mudou), imagem adulterada ou processo alterado: a imagem do bloco nunca é trocada")
    void falhasNaoSubstituem() throws Exception {
        studio();
        Automation a = lab();
        String login = openLogin();
        shoot(login, capturedV2("CID", a, true, "MID", false));
        String first = imageOf(login);
        for (String code : List.of("ACTIVE_TAB_CHANGED", "PAGE_CHANGED_DURING_CAPTURE", "CANCELLED")) {
            shoot(login, "{\"captureId\":\"CID\",\"status\":\"FAILED\",\"code\":\"" + code + "\",\"observedAt\":\"x\"}");
            assertFalse(model().reviewing(), code);
            assertEquals(first, imageOf(login), code);
            assertEquals("Imagem indisponível", model().bannerTitle(), code);
        }
        shoot(login, capturedV2("CID", a, true, "LIGHT", true)); // 1 pixel real fora dos portadores
        assertEquals("Imagem recusada pelo Studio", model().bannerTitle());
        assertTrue(model().banner().contains("A imagem anterior continua"), model().banner());
        assertFalse(model().reviewing());
        assertEquals(first, imageOf(login));
        // candidata pendente + a automação muda (etapa nova no bloco) → Aplicar recusa e descarta
        shoot(login, capturedV2("CID", a, true, "LIGHT", false));
        assertTrue(model().reviewing());
        Edt.run(() -> studio.service().appendStep(a.id(), a.sessions().get(0).id(), StepDraft.builder().name("Esperar").actionType(ActionType.WAIT_FOR)
                .context(new StepContext("Laboratório", "Entrar", "http://127.0.0.1:18765/lab/studio012/pdd-login.html")).build()));
        Edt.run(() -> pdd().applyCandidate());
        assertEquals("Nova captura descartada", model().bannerTitle());
        assertFalse(model().reviewing());
        assertEquals(first, imageOf(login), "nada foi trocado");
    }

    @Test
    @DisplayName("PDD01 com o PDF desatualizado, \"Captura armada\" (a instrução do gesto) continua visível; o aviso de estado volta depois")
    void capturaArmadaNaoFicaEscondida() throws Exception {
        studio();
        Automation a = lab();
        String login = openLogin();
        shoot(login, capturedV2("CID", a, true, "MID", false));
        Edt.run(() -> pdd().export());
        Edt.run(() -> studio.service().appendStep(a.id(), a.sessions().get(0).id(), StepDraft.builder().name("Esperar").actionType(ActionType.WAIT_FOR).build()));
        Edt.run(() -> pdd().refresh());
        Edt.run(() -> pdd().select(login));
        assertEquals("PDF desatualizado", model().bannerTitle(), model().banner());
        Edt.run(() -> pdd().capture(login, "t-LabTab000001"));
        Edt.flush();
        assertEquals("Captura armada", model().bannerTitle(), "a instrução do gesto não pode ser coberta pelo aviso de estado");
        Edt.run(() -> pdd().cancelCapture());
        Edt.flush();
        Edt.run(() -> pdd().select(login));
        assertEquals("PDF desatualizado", model().bannerTitle(), "sem captura armada, o aviso de estado volta");
    }

    @Test
    @DisplayName("PDD01 marcador manual pelo editor: só para instrução sem automático; MANUAL + revisor; sai no PDF como manual; removível; desfazível")
    void marcadorManual() throws Exception {
        studio();
        Automation a = lab();
        long revision = a.revision();
        String login = openLogin();
        shoot(login, capturedV2("CID", a, false, "MID", false)); // botão sem marcador automático
        List<PddPanel.MarkerSlot> slots = model().markers();
        assertEquals(List.of(true, true, true), slots.stream().map(s -> s.ordinal() > 0).toList());
        assertFalse(slots.get(0).markable(), "a instrução 1 tem marcador automático: não aceita manual");
        assertTrue(slots.get(2).markable());
        Edt.run(() -> {
            PddPanel p = pdd().panel();
            p.markerInput().setSelectedIndex(2);
            p.placeMarkerButton().doClick();
            p.imageEditor().select(new Rectangle(260, 120, 60, 24));
        });
        DocumentDraft.SafeCapture c = draft().captures().get(draft().block(login).orElseThrow().captureSha());
        DocumentDraft.Annotation m = c.annotations().stream().filter(DocumentDraft.Annotation::manual).findFirst().orElseThrow();
        assertEquals(a.sessions().get(0).steps().get(2).id(), m.stepId());
        assertTrue(DocumentDraft.Annotation.Provenance.MANUAL == m.provenance() && List.of("DEVELOPER", "TEST_AGENT").contains(m.reviewer()));
        assertEquals(0.65, m.x(), 1e-6);
        assertTrue(model().markers().get(2).manual());
        assertTrue(model().image().marks().stream().anyMatch(k -> k.ordinal() == 3 && k.manual()));
        Edt.run(() -> pdd().export());
        try (PDDocument doc = Loader.loadPDF(out.resolve("pdd-visual-v001.pdf").toFile())) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("(marcador posicionado manualmente)") && text.replaceAll("\\s+", " ").contains("não comprovam o elemento"), text);
            for (String s : List.of("ana.souza", "cofre.senha-lab", "#ds_login", "tgt-")) assertFalse(text.contains(s), s);
        }
        Edt.run(() -> {
            pdd().panel().markerInput().setSelectedIndex(2);
            pdd().panel().removeMarkerButton().doClick();
        });
        assertFalse(model().markers().get(2).manual());
        Edt.run(() -> pdd().undo());
        assertTrue(model().markers().get(2).manual(), "desfazer devolve o marcador manual");
        assertEquals(revision, a.revision(), "marcador manual não toca na automação");
    }

    @Test
    @DisplayName("PDD01 compatibilidade: extensão anterior (0.4.0) → o Studio não pede a v2 e tudo segue como na v1 (rascunho 1.0.0)")
    void extensaoAnterior() throws Exception {
        browser.version = "0.4.0";
        studio();
        Automation a = lab();
        String login = openLogin();
        Edt.run(() -> pdd().capture(login, "t-LabTab000001"));
        Edt.flush();
        assertNull(browser.lastArm.get("policy"), "sem pedido de política para extensão anterior");
        browser.emit(PddUiTest.captured((String) browser.lastArm.get("captureId"), a.sessions().get(0).steps().get(0).id().value().toString(), false));
        Edt.flush();
        Edt.flush();
        assertEquals("Imagem sanitizada associada", model().bannerTitle());
        assertFalse(draft().needsV11(), "rascunho continua 1.0.0");
        assertEquals("nekkora-visual/1", model().image().policy());
    }

    /** Layout de toda a árvore sem janela (teste headless): cada contêiner, de cima para baixo. */
    private static void layoutAll(java.awt.Component c) {
        if (c instanceof java.awt.Container ct) {
            ct.doLayout();
            for (java.awt.Component ch : ct.getComponents()) layoutAll(ch);
        }
    }

    @Test
    @DisplayName("PDD01 temas e acessibilidade: editor de imagem, recorte, marcador e Aplicar/Cancelar montam nos quatro temas, com nome acessível")
    void temasEAcessibilidade() throws Exception {
        studio();
        Automation a = lab();
        String login = openLogin();
        shoot(login, capturedV2("CID", a, true, "MID", false));
        shoot(login, capturedV2("CID", a, true, "LIGHT", false));
        for (NekkoraTheme t : NekkoraThemes.all()) {
            Edt.theme(t);
            Edt.run(() -> pdd().select(login));
            assertTrue(model().reviewing(), t.id());
            BufferedImage shot = Edt.call(() -> {
                PddPanel p = pdd().panel();
                p.setSize(1366, 768);
                p.doLayout();
                BufferedImage img = new BufferedImage(1366, 768, BufferedImage.TYPE_INT_RGB);
                var g = img.createGraphics();
                p.printAll(g);
                g.dispose();
                return img;
            });
            assertNotNull(shot);
        }
        PddPanel p = Edt.call(() -> pdd().panel());
        // 1366×768 (janela do PDD com margem): Aplicar e Cancelar alcançáveis rolando a coluna de edição
        for (javax.swing.JComponent b : List.of(p.applyButton(), p.cancelCandidateButton())) {
            String where = Edt.call(() -> {
                p.setSize(1326, 640);
                layoutAll(p);
                javax.swing.JViewport vp = (javax.swing.JViewport) javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JViewport.class, b);
                b.scrollRectToVisible(new Rectangle(0, 0, b.getWidth(), b.getHeight()));
                layoutAll(p);
                Rectangle r = javax.swing.SwingUtilities.convertRectangle(b.getParent(), b.getBounds(), vp);
                return new Rectangle(0, 0, vp.getWidth(), vp.getHeight()).contains(r) ? "" : "fora da área visível: " + r + " em " + vp.getSize()
                        + " · vista " + vp.getView().getSize() + " (preferida " + vp.getView().getPreferredSize() + ")";
            });
            assertEquals("", where, ((javax.swing.AbstractButton) b).getText());
        }
        assertEquals("Imagem sanitizada do bloco", p.imageEditor().getAccessibleContext().getAccessibleName());
        assertTrue(p.imageEditor().getAccessibleContext().getAccessibleDescription().contains("Enter"));
        assertTrue(p.applyButton().getAccessibleContext().getAccessibleDescription().contains("Substitui"));
        assertTrue(p.cancelCandidateButton().getAccessibleContext().getAccessibleDescription().contains("anterior continua"));
        assertEquals("Instrução do marcador", p.markerInput().getAccessibleContext().getAccessibleName());
        Edt.run(() -> pdd().cancelCandidate());
    }
}
