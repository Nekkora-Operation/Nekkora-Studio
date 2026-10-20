package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.app.pdd.PddPresenter;
import com.nekkora.studio.app.recorder.RecorderPrompts;
import com.nekkora.studio.app.recorder.RecorderWindowHost;
import com.nekkora.studio.bridge.BridgeCommandException;
import com.nekkora.studio.bridge.BridgeError;
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
import com.nekkora.studio.pdd.DraftJson;
import com.nekkora.studio.recorder.BrowserTab;
import com.nekkora.studio.recorder.TabAccess;
import com.nekkora.studio.ui.pdd.PddPanel;
import com.nekkora.tokens.NekkoraThemes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * NEKKORA-STUDIO-013 pela interface — Studio REAL com navegador falso da extensão 0.6.0: abas por estado (pode capturar,
 * falta autorizar o PDD no site, sem acesso do Chrome, gravando), "Autorizar captura PDD neste site" (sessão por padrão ou
 * lembrar), revogação, captura nekkora-visual/3 (descrição sintética desenhada pelo Studio), recusas e a automação intocada.
 * Dados fictícios; origens sintéticas (*.exemplo.test).
 */
class PddSitesUiTest {
    @TempDir
    Path out;

    static final String SITE = "https://app.exemplo.test:8443";

    /** Navegador falso 0.6.0: o consentimento concedido muda o perfil da aba, como a extensão faz. */
    static final class FakeBrowser implements BrowserLink {
        final List<Listener> listeners = new CopyOnWriteArrayList<>();
        final List<String> sent = new CopyOnWriteArrayList<>();
        volatile Map<String, Object> lastArm;
        volatile Map<String, Object> lastGrant;
        volatile String lastRevoke;
        volatile String consent = "NONE";
        volatile String level = "MASKED";
        volatile String version = "0.6.0";
        volatile String armReason;

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
            sent.add(type);
            boolean granted = !"NONE".equals(consent);
            String body = switch (type) {
                case "browser.tabs.list" -> "{\"tabs\":["
                        + "{\"tabRef\":\"t-LabTab000001\",\"title\":\"Entrar\",\"host\":\"127.0.0.1\",\"access\":\"LAB\",\"visualCapture\":\"LAB\","
                        + "\"origin\":\"http://127.0.0.1:18765\",\"pddConsent\":\"NOT_REQUIRED\",\"active\":false,\"recording\":false},"
                        + "{\"tabRef\":\"t-SiteTab00002\",\"title\":null,\"host\":\"app.exemplo.test\",\"access\":\"AUTHORIZED\",\"visualCapture\":"
                        + (granted ? "\"AUTHORIZED_SITE\"" : "null") + ",\"origin\":\"" + SITE + "\",\"pddConsent\":\"" + consent + "\",\"active\":true,\"recording\":false},"
                        + "{\"tabRef\":\"t-Portal000003\",\"title\":null,\"host\":\"portal.exemplo.test\",\"access\":\"PERMISSION_REQUIRED\",\"visualCapture\":null,"
                        + "\"origin\":null,\"pddConsent\":\"NONE\",\"active\":false,\"recording\":false},"
                        + "{\"tabRef\":\"t-Intern00004\",\"title\":null,\"host\":null,\"access\":\"BROWSER_INTERNAL\",\"visualCapture\":null,"
                        + "\"origin\":null,\"pddConsent\":\"NONE\",\"active\":false,\"recording\":false}],\"truncated\":false}";
                case "studio.pdd.consent.list" -> granted ? "{\"consents\":[{\"origin\":\"" + SITE + "\",\"consent\":\"" + consent + "\",\"actor\":\"DEVELOPER\","
                        + "\"via\":\"STUDIO\",\"grantedAt\":\"2026-10-19T12:00:00Z\",\"chromeAccess\":true,\"level\":\"" + level + "\"}]}" : "{\"consents\":[]}";
                case "studio.pdd.consent.grant" -> {
                    lastGrant = payload;
                    consent = (String) payload.get("scope");
                    level = payload.get("level") == null ? "MASKED" : (String) payload.get("level");
                    yield "{\"origin\":\"" + payload.get("origin") + "\",\"consent\":\"" + consent + "\",\"level\":\"" + level + "\",\"actor\":\""
                            + payload.get("actor") + "\",\"via\":\"STUDIO\",\"grantedAt\":\"2026-10-19T12:00:00Z\"}";
                }
                case "studio.pdd.consent.revoke" -> {
                    lastRevoke = (String) payload.get("origin");
                    consent = "NONE";
                    yield "{\"origin\":\"" + lastRevoke + "\",\"revoked\":true}";
                }
                case "studio.pdd.capture.arm" -> {
                    lastArm = payload;
                    if (armReason != null) {
                        yield null;
                    }
                    yield "{\"captureId\":\"" + payload.get("captureId") + "\",\"armed\":true,\"duplicate\":false,\"shortcut\":\"Alt+Shift+K\",\"policy\":\""
                            + payload.get("policy") + "\",\"profile\":\"AUTHORIZED_SITE\"}";
                }
                case "studio.pdd.capture.cancel" -> "{\"captureId\":\"" + payload.get("captureId") + "\",\"cancelled\":true}";
                default -> "{}";
            };
            if (body == null) {
                return CompletableFuture.failedFuture(new BridgeCommandException(BridgeError.VISUAL_CAPTURE_NOT_ALLOWED, "recusado", armReason, false));
            }
            return CompletableFuture.completedFuture(Json.parseObject(body));
        }

        void emit(String json) {
            listeners.forEach(l -> l.event(new BridgeEvent("studio.pdd.captured", Json.parseObject(json))));
        }
    }

    private final FakeBrowser browser = new FakeBrowser();
    private Studio studio;
    /** Resposta da pessoa às confirmações do print integral (liberar no site, exportar). */
    private volatile boolean confirms = true;
    private final List<String> asked = new CopyOnWriteArrayList<>();
    private volatile int shows;

    @AfterEach
    void tearDown() {
        if (studio != null) Edt.run(studio::dispose);
    }

    private void studio() {
        Edt.theme(NekkoraThemes.SAKURA_DARK);
        studio = Edt.call(() -> Studio.create(StudioFixture.CLOCK, new ScriptedPrompts(), () -> { }, browser, RecorderPrompts.none(),
                p -> RecorderWindowHost.headless()));
        studio.useVisualPddHosts(p -> new PddPresenter.Host() {
            public void show() {
                shows++;
            }

            public void hide() {}

            public java.util.Optional<Path> chooseFolder() {
                return java.util.Optional.of(out);
            }

            public boolean confirm(String title, String message, String action) {
                asked.add(title);
                return confirms;
            }
        });
    }

    private static ElementReference el(String targetId, String kind, String tag, String name, String css, boolean sensitive) {
        return ElementReference.builder().semanticKind(kind).tag(tag).accessibleName(name).sensitive(sensitive).privacyPolicy(CapturePrivacy.VERSION)
                .addLocator(new LocatorCandidate(LocatorStrategy.ID, "", css, LocatorStability.HEURISTIC_STABLE, 1, true)).preferLastLocator()
                .correlation(CorrelationKeys.of(Map.of(CorrelationKeys.Key.TARGET_ID, targetId))).build();
    }

    private Automation site() {
        return Edt.call(() -> {
            Automation a = studio.service().createAutomation("Login de site sintético", "");
            LogicalSession auth = studio.service().addSession(a.id(), "Autenticação", "");
            Object[][] steps = {{"Usuário", ActionType.FILL, el("tgt-u", "TEXT_INPUT", "input", "Usuário", "#usuario", false),
                    ActionParameters.withValue(new ValueSource.LiteralValue("ana.souza"))},
                    {"Senha", ActionType.FILL, el("tgt-s", "PASSWORD_INPUT", "input", "Senha", "#senha", true),
                            ActionParameters.withValue(new ValueSource.CredentialReference("cofre.senha-lab"))},
                    {"Entrar", ActionType.CLICK, el("tgt-e", "BUTTON", "button", "Entrar", "#entrar", false), ActionParameters.none()}};
            for (Object[] x : steps) {
                ElementReference e = (ElementReference) x[2];
                studio.service().appendStep(a.id(), auth.id(), StepDraft.builder().name((String) x[0]).actionType((ActionType) x[1]).element(e)
                        .parameters((ActionParameters) x[3]).context(new StepContext("Site sintético", "Entrar", SITE + "/entrar"))
                        .provenance(StepProvenance.recorded(e.correlation().with(CorrelationKeys.Key.RECORDING_ID, "rec-13")
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

    private void selectTab(String tabRef) {
        Edt.run(() -> {
            var combo = pdd().panel().tabsInput();
            for (int i = 0; i < combo.getItemCount(); i++) if (combo.getItemAt(i).tabRef().equals(tabRef)) combo.setSelectedIndex(i);
        });
        Edt.flush();
    }

    private long revision() {
        return Edt.call(() -> studio.state().activeAutomation().orElseThrow().revision());
    }

    /** Descrição v3 (px CSS) do login sintético; {@code label} = texto do botão (o vocabulário decide). */
    static String capturedV3(Automation a, String label) {
        var steps = a.sessions().get(0).steps();
        String u = steps.get(0).id().value().toString();
        String s = steps.get(1).id().value().toString();
        String e = steps.get(2).id().value().toString();
        return "{\"captureId\":\"CID\",\"status\":\"CAPTURED\",\"policy\":\"nekkora-visual/3\",\"observedAt\":\"2026-10-19T12:00:00Z\","
                + "\"viewport\":{\"width\":400,\"height\":200,\"dpr\":1.25},\"regions\":["
                + "{\"kind\":\"SURFACE\",\"x\":20,\"y\":10,\"width\":360,\"height\":180,\"tone\":\"LIGHT\"},"
                + "{\"kind\":\"CONTROL\",\"x\":260,\"y\":120,\"width\":60,\"height\":24},"
                + "{\"kind\":\"FIELD\",\"x\":40,\"y\":60,\"width\":200,\"height\":24},"
                + "{\"kind\":\"FIELD\",\"x\":40,\"y\":110,\"width\":200,\"height\":24},"
                + "{\"kind\":\"TEXT\",\"x\":40,\"y\":20,\"width\":128,\"height\":8,\"ink\":\"DARK\"},"
                + "{\"kind\":\"LABEL\",\"x\":40,\"y\":38,\"width\":80,\"height\":18,\"text\":\"Usuário\",\"size\":13,\"weight\":\"NORMAL\",\"ink\":\"DARK\",\"align\":\"START\"},"
                + "{\"kind\":\"LABEL\",\"x\":260,\"y\":120,\"width\":60,\"height\":24,\"text\":\"" + label + "\",\"size\":13,\"weight\":\"BOLD\",\"ink\":\"DARK\",\"align\":\"CENTER\"}"
                + "],\"annotations\":[{\"stepRef\":\"" + u + "\",\"status\":\"PLACED\",\"x\":0.1,\"y\":0.3,\"width\":0.5,\"height\":0.12,\"reason\":\"\"},"
                + "{\"stepRef\":\"" + s + "\",\"status\":\"PLACED\",\"x\":0.1,\"y\":0.55,\"width\":0.5,\"height\":0.12,\"reason\":\"\"},"
                + "{\"stepRef\":\"" + e + "\",\"status\":\"OMITTED\",\"x\":0,\"y\":0,\"width\":0,\"height\":0,\"reason\":\"AMBIGUOUS\"}],"
                + "\"limitations\":[\"TABLE_CONTENT_HIDDEN\"]}";
    }

    private void shoot(String login, String tabRef, String json) {
        Edt.run(() -> pdd().capture(login, tabRef));
        Edt.flush();
        browser.emit(json.replace("CID", (String) browser.lastArm.get("captureId")));
        Edt.flush();
        Edt.flush();
    }

    @Test
    @DisplayName("S013 abas por estado: site sem autorização PDD não captura e explica; sem acesso do Chrome explica; página interna nem aparece")
    void abasPorEstado() {
        studio();
        site();
        openLogin();
        List<PddPanel.Tab> tabs = model().tabs();
        assertEquals(List.of("READY", "CONSENT_REQUIRED", "CHROME_ACCESS_REQUIRED"), tabs.stream().map(PddPanel.Tab::state).toList());
        assertTrue(tabs.get(1).label().endsWith("captura PDD não autorizada"), tabs.get(1).label());
        assertFalse(tabs.stream().anyMatch(t -> t.tabRef().equals("t-Intern00004")), "página interna não é oferecida");
        selectTab("t-SiteTab00002");
        assertFalse(Edt.call(() -> pdd().panel().captureButton().isEnabled()), "sem autorização PDD, a captura fica indisponível");
        String text = Edt.call(() -> pdd().panel().consentText().getText());
        assertTrue(text.startsWith("Esta página permite gravação, mas a captura de PDD ainda não está autorizada"), text);
        assertTrue(text.contains(SITE) && !text.contains("/entrar"), "origem sem caminho: " + text);
        assertTrue(Edt.call(() -> pdd().panel().authorizeButton().isVisible()));
        selectTab("t-Portal000003");
        assertTrue(Edt.call(() -> pdd().panel().consentText().getText()).startsWith("Sem acesso do Chrome"));
        assertFalse(Edt.call(() -> pdd().panel().authorizeButton().isVisible()), "o Studio não concede acesso do Chrome");
        selectTab("t-LabTab000001");
        assertTrue(Edt.call(() -> pdd().panel().captureButton().isEnabled()), "o laboratório não precisa de autorização");
    }

    @Test
    @DisplayName("S013 autorizar pelo Studio: sessão por padrão, origem exata e quem decidiu; depois captura v3 desenhada pelo Studio, rascunho 1.2.0, PDF sintético")
    void autorizarECapturarV3() throws Exception {
        studio();
        Automation a = site();
        long before = revision();
        String login = openLogin();
        selectTab("t-SiteTab00002");
        Edt.run(() -> pdd().panel().authorizeButton().doClick());
        Edt.flush();
        Edt.flush();
        assertEquals(Map.of("tabRef", "t-SiteTab00002", "origin", SITE, "scope", "SESSION", "actor", "DEVELOPER", "level", "FULL"), browser.lastGrant,
                "pedido da usuária: sem escolha, o print integral (o padrão)");
        assertTrue(asked.isEmpty(), "autorizar não pede confirmação (o texto da autorização avisa)");
        assertEquals("Captura PDD autorizada", model().bannerTitle(), model().banner());
        assertEquals("READY", model().tabs().stream().filter(t -> t.tabRef().equals("t-SiteTab00002")).findFirst().orElseThrow().state());
        assertEquals(1, model().sites().size());
        selectTab("t-SiteTab00002");
        assertTrue(Edt.call(() -> pdd().panel().captureButton().isEnabled()));
        shoot(login, "t-SiteTab00002", capturedV3(a, "Entrar"));
        assertEquals("nekkora-visual/5", browser.lastArm.get("policy"), "site no print integral (o padrão): o Studio pede a v5; a descrição v3 continua aceita");
        assertEquals(SITE, browser.lastArm.get("origin"), "a origem mostrada vai junto (aba em outra origem = nada é armado)");
        assertTrue(model().hasImage(), model().banner());
        assertEquals("Imagem associada · rascunho 1.2.0", model().bannerTitle(), model().banner());
        DocumentDraft d = Edt.call(() -> pdd().draft().orElseThrow());
        DocumentDraft.SafeCapture c = d.captures().get(d.block(login).orElseThrow().captureSha());
        assertEquals("nekkora-visual/3", c.policy());
        assertEquals(800, c.width(), "desenhada em escala 2");
        assertEquals(2.0, c.devicePixelRatio());
        assertTrue(DraftJson.write(d).contains("\"1.2.0\""));
        Edt.run(() -> pdd().export());
        Path pdf = out.resolve("pdd-visual-v001.pdf");
        assertTrue(Files.exists(pdf), model().banner());
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            String text = new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
            assertTrue(text.contains("nenhum pixel da página foi copiado"), text);
            for (String s : List.of("ana.souza", "cofre.senha-lab", "/entrar", "app.exemplo.test")) assertFalse(text.contains(s), "no PDF: " + s);
        }
        assertEquals(before, revision(), "consentimento, captura e PDF não mudam a automação (.nekkora)");
    }

    /** Print v4 sintético 400×200 "da tela" (cor-sentinela) com as máscaras pintadas; {@code leak} = pixel da página dentro de uma máscara. */
    static String capturedV4(Automation a, boolean leak) throws Exception {
        int w = 400, h = 200;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, 0xE6007E);
        int[][] m = {{0, 250, 20, 130, 90}, {1, 40, 60, 200, 24}, {1, 40, 110, 200, 24}, {2, 40, 20, 150, 16}};
        String[] kinds = {"BLOCK", "FIELD", "TEXT"};
        StringBuilder rj = new StringBuilder();
        for (int[] r : m) {
            int[] fb = com.nekkora.studio.pdd.VisualVerifier.v4Paint(kinds[r[0]], r[0] == 2 ? "DARK" : null);
            for (int y = r[2]; y < r[2] + r[4]; y++) {
                for (int x = r[1]; x < r[1] + r[3]; x++) {
                    img.setRGB(x, y, y == r[2] || y == r[2] + r[4] - 1 || x == r[1] || x == r[1] + r[3] - 1 ? fb[1] : fb[0]);
                }
            }
            if (rj.length() > 0) rj.append(',');
            rj.append("{\"kind\":\"").append(kinds[r[0]]).append("\",\"x\":").append(r[1]).append(",\"y\":").append(r[2]).append(",\"width\":")
                    .append(r[3]).append(",\"height\":").append(r[4]).append(r[0] == 2 ? ",\"ink\":\"DARK\"" : "").append('}');
        }
        if (leak) img.setRGB(300, 60, 0xE6007E - 1);
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", o);
        byte[] png = o.toByteArray();
        String sha = "sha256:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(png));
        String u = a.sessions().get(0).steps().get(0).id().value().toString();
        return "{\"captureId\":\"CID\",\"status\":\"CAPTURED\",\"policy\":\"nekkora-visual/4\",\"observedAt\":\"2026-10-19T12:00:00Z\","
                + "\"width\":400,\"height\":200,\"devicePixelRatio\":1,\"png\":\"" + java.util.Base64.getEncoder().encodeToString(png) + "\",\"sha256\":\"" + sha
                + "\",\"regions\":[" + rj + "],\"annotations\":[{\"stepRef\":\"" + u + "\",\"status\":\"PLACED\",\"x\":0.1,\"y\":0.3,\"width\":0.5,"
                + "\"height\":0.12,\"reason\":\"\"}],\"limitations\":[\"TABLE_CONTENT_HIDDEN\"]}";
    }

    @Test
    @DisplayName("S013 v4: print real mascarado aceito (rascunho 1.2.0, PDF com a procedência da v4); pixel da página dentro de máscara recusado")
    void printRealMascarado() throws Exception {
        studio();
        Automation a = site();
        String login = openLogin();
        browser.consent = "SESSION";
        Edt.run(() -> pdd().reloadTabs());
        Edt.flush();
        shoot(login, "t-SiteTab00002", capturedV4(a, true));
        assertFalse(model().hasImage());
        assertEquals("Imagem recusada pelo Studio", model().bannerTitle(), model().banner());
        shoot(login, "t-SiteTab00002", capturedV4(a, false));
        assertTrue(model().hasImage(), model().banner());
        assertEquals("Imagem associada · rascunho 1.2.0", model().bannerTitle());
        assertEquals("nekkora-visual/4", model().image().policy());
        Edt.run(() -> pdd().export());
        try (PDDocument doc = Loader.loadPDF(out.resolve("pdd-visual-v001.pdf").toFile())) {
            String text = new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
            assertTrue(text.contains("capturas reais da tela"), text);
        }
    }

    /** Print v5 sintético 400×200: a "tela" toda na cor-sentinela (os dados ficam) e só a senha coberta; {@code leak} = senha à mostra. */
    static String capturedV5(Automation a, boolean leak) throws Exception {
        int w = 400, h = 200;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, 0xE6007E);
        int[] fb = com.nekkora.studio.pdd.VisualVerifier.v4Paint("FIELD", null);
        for (int y = 110; y < 134 && !leak; y++) {
            for (int x = 40; x < 240; x++) img.setRGB(x, y, y == 110 || y == 133 || x == 40 || x == 239 ? fb[1] : fb[0]);
        }
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", o);
        byte[] png = o.toByteArray();
        String sha = "sha256:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(png));
        String u = a.sessions().get(0).steps().get(0).id().value().toString();
        return "{\"captureId\":\"CID\",\"status\":\"CAPTURED\",\"policy\":\"nekkora-visual/5\",\"observedAt\":\"2026-10-19T12:00:00Z\","
                + "\"width\":400,\"height\":200,\"devicePixelRatio\":1,\"png\":\"" + java.util.Base64.getEncoder().encodeToString(png) + "\",\"sha256\":\"" + sha
                + "\",\"regions\":[{\"kind\":\"FIELD\",\"x\":40,\"y\":110,\"width\":200,\"height\":24}],\"annotations\":[{\"stepRef\":\"" + u
                + "\",\"status\":\"PLACED\",\"x\":0.1,\"y\":0.3,\"width\":0.5,\"height\":0.12,\"reason\":\"\"}],\"limitations\":[]}";
    }

    private PddPanel.Tab siteTab() {
        return model().tabs().stream().filter(t -> t.tabRef().equals("t-SiteTab00002")).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("v5 é o padrão: autorizar já no print integral, sem segundo clique; \"Print mascarado\" escolhido arma a v4; \"Mascarar esta captura\"; trocar o nível sem confirmação")
    void printIntegralPadrao() {
        studio();
        site();
        String login = openLogin();
        selectTab("t-SiteTab00002");
        assertTrue(Edt.call(() -> pdd().panel().levelCombo().isVisible()), "a escolha da imagem aparece ao autorizar");
        assertTrue(Edt.call(() -> pdd().panel().levelCombo().getSelectedValue()).startsWith("Print integral (padrão)"));
        assertTrue(Edt.call(() -> pdd().panel().consentText().getText()).contains("Por padrão a imagem é o print integral"), "o aviso de uma linha");
        confirms = false; // nenhuma confirmação deve ser pedida ao autorizar
        Edt.run(() -> pdd().panel().authorizeButton().doClick());
        Edt.flush();
        Edt.flush();
        assertTrue(asked.isEmpty(), "sem segundo clique");
        assertEquals("FULL", browser.lastGrant.get("level"));
        assertTrue(siteTab().full(), "a aba sabe que o site está no print integral");
        assertTrue(model().sites().get(0).label().contains("print integral"), model().sites().get(0).label());
        selectTab("t-SiteTab00002");
        assertTrue(Edt.call(() -> pdd().panel().maskThisBox().isVisible()), "cada captura pode sair mascarada");
        Edt.run(() -> pdd().panel().captureButton().doClick());
        Edt.flush();
        assertEquals("nekkora-visual/5", browser.lastArm.get("policy"));
        Edt.run(() -> pdd().cancelCapture());
        Edt.run(() -> {
            pdd().panel().maskThisBox().setSelected(true);
            pdd().panel().captureButton().doClick();
        });
        Edt.flush();
        assertEquals("nekkora-visual/4", browser.lastArm.get("policy"), "esta captura sai mascarada");
        Edt.run(() -> pdd().cancelCapture());
        Edt.run(() -> pdd().panel().siteLevelButton().doClick()); // "Voltar ao print mascarado neste site"
        Edt.flush();
        Edt.flush();
        assertEquals("MASKED", browser.lastGrant.get("level"));
        assertFalse(siteTab().full());
        Edt.run(() -> pdd().capture(login, "t-SiteTab00002"));
        Edt.flush();
        assertEquals("nekkora-visual/4", browser.lastArm.get("policy"), "site mascarado nunca arma a v5");
        Edt.run(() -> pdd().cancelCapture());
        selectTab("t-SiteTab00002"); // a lista de abas foi relida: a escolha volta para a primeira
        Edt.run(() -> pdd().panel().siteLevelButton().doClick()); // "Liberar print integral neste site", sem confirmação
        Edt.flush();
        Edt.flush();
        assertEquals("FULL", browser.lastGrant.get("level"));
        assertTrue(asked.isEmpty(), "trocar o nível não pede confirmação");
        // autorizar sem escolher o nível (chamada de quem não passa a escolha): print integral, o padrão
        Edt.run(() -> pdd().revokeSite(SITE));
        Edt.flush();
        Edt.flush();
        Edt.run(() -> pdd().authorizeSite("t-SiteTab00002", SITE, false));
        Edt.flush();
        Edt.flush();
        assertEquals("FULL", browser.lastGrant.get("level"), "sem escolha: integral");
        // autorizar escolhendo o mascarado
        Edt.run(() -> pdd().revokeSite(SITE));
        Edt.flush();
        Edt.flush();
        selectTab("t-SiteTab00002");
        Edt.run(() -> {
            pdd().panel().levelCombo().setSelectedIndex(1);
            pdd().panel().authorizeButton().doClick();
        });
        Edt.flush();
        Edt.flush();
        assertEquals("MASKED", browser.lastGrant.get("level"));
    }

    @Test
    @DisplayName("Recorder: \"Capturar tela\" depois de finalizar arma a captura para o bloco da última etapa da gravação; a imagem é associada; site sem autorização abre o PDD")
    void capturarPeloRecorder() throws Exception {
        studio();
        Automation a = site();
        var session = a.sessions().get(0);
        var rec = new com.nekkora.studio.recorder.RecorderController.LastRecording("rec-13", a.id(), session.id(), "t-SiteTab00002");
        List<String> status = new CopyOnWriteArrayList<>();
        // site ainda sem autorização do PDD: a janela do PDD abre no bloco certo
        Edt.run(() -> pdd().captureAfterRecording(rec, (t, busy) -> status.add(t)));
        Edt.flush();
        Edt.flush();
        assertTrue(status.get(status.size() - 1).startsWith("Autorize a captura do PDD neste site"), status.toString());
        assertTrue(shows > 0, "a janela do PDD abriu para autorizar");
        assertNull(browser.lastArm, "nada foi armado sem autorização");
        // autorizado (print integral): arma e associa a imagem ao bloco da etapa "Entrar" (a última desta gravação)
        browser.consent = "SESSION";
        browser.level = "FULL";
        status.clear();
        Edt.run(() -> pdd().captureAfterRecording(rec, (t, busy) -> status.add(t)));
        Edt.flush();
        Edt.flush();
        assertEquals("nekkora-visual/5", browser.lastArm.get("policy"), status.toString());
        assertEquals(SITE, browser.lastArm.get("origin"));
        assertTrue(status.stream().anyMatch(x -> x.startsWith("Captura armada")), status.toString());
        browser.emit(capturedV5(a, false).replace("CID", (String) browser.lastArm.get("captureId")));
        Edt.flush();
        Edt.flush();
        assertTrue(status.get(status.size() - 1).startsWith("Imagem associada"), status.toString());
        DocumentDraft d = Edt.call(() -> pdd().draft().orElseThrow());
        var entrar = a.sessions().get(0).steps().get(2).id();
        DocumentDraft.Block b = d.blocks().stream().filter(x -> x.stepIds().contains(entrar)).findFirst().orElseThrow();
        assertFalse(b.captureSha().isEmpty(), "a imagem foi para o bloco da última etapa da gravação");
        assertEquals("nekkora-visual/5", d.captures().get(b.captureSha()).policy());
        // gravação sem etapas registradas no Studio: nada a capturar
        status.clear();
        Edt.run(() -> pdd().captureAfterRecording(new com.nekkora.studio.recorder.RecorderController.LastRecording("rec-outra", a.id(), session.id(),
                "t-SiteTab00002"), (t, busy) -> status.add(t)));
        assertTrue(status.get(0).startsWith("Nenhuma etapa desta gravação"), status.toString());
    }

    @Test
    @DisplayName("v5 aceito: selo \"Print integral\" na imagem; senha à mostra é recusada; exportar pede confirmação (recusada = nenhum PDF; aceita = PDF com a procedência do integral)")
    void printIntegralEExportar() throws Exception {
        studio();
        Automation a = site();
        long before = revision();
        String login = openLogin();
        browser.consent = "SESSION";
        browser.level = "FULL";
        Edt.run(() -> pdd().reloadTabs());
        Edt.flush();
        Edt.flush();
        shoot(login, "t-SiteTab00002", capturedV5(a, true));
        assertEquals("nekkora-visual/5", browser.lastArm.get("policy"));
        assertFalse(model().hasImage());
        assertEquals("Imagem recusada pelo Studio", model().bannerTitle(), model().banner());
        shoot(login, "t-SiteTab00002", capturedV5(a, false));
        assertTrue(model().hasImage(), model().banner());
        assertEquals("nekkora-visual/5", model().image().policy());
        assertTrue(Edt.call(() -> pdd().panel().fullBadge().isVisible()), "selo do print integral");
        confirms = false;
        asked.clear();
        Edt.run(() -> pdd().export());
        assertEquals(List.of("Exportar com print integral?"), asked);
        assertFalse(Files.exists(out.resolve("pdd-visual-v001.pdf")), "recusada: nenhum PDF");
        assertEquals("Exportação cancelada", model().bannerTitle());
        confirms = true;
        Edt.run(() -> pdd().export());
        Path pdf = out.resolve("pdd-visual-v001.pdf");
        assertTrue(Files.exists(pdf), model().banner());
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            String text = new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
            assertTrue(text.contains("Este documento contém imagens em print integral"), text);
            assertTrue(text.contains("Print integral: a imagem mostra os dados da tela"), text);
        }
        assertEquals(before, revision(), "o print integral não muda a automação (.nekkora)");
    }

    @Test
    @DisplayName("S013 lembrar o site e revogar pela lista; revogar cancela a captura armada (evento PDD_CONSENT_REVOKED) sem trocar a imagem")
    void lembrarERevogar() {
        studio();
        site();
        String login = openLogin();
        selectTab("t-SiteTab00002");
        Edt.run(() -> {
            pdd().panel().rememberBox().setSelected(true);
            pdd().panel().authorizeButton().doClick();
        });
        Edt.flush();
        Edt.flush();
        assertEquals("REMEMBERED", browser.lastGrant.get("scope"));
        assertTrue(model().sites().get(0).label().contains("lembrado"), model().sites().get(0).label());
        selectTab("t-SiteTab00002");
        Edt.run(() -> pdd().capture(login, "t-SiteTab00002"));
        Edt.flush();
        Edt.run(() -> pdd().panel().revokeSiteButton().doClick());
        Edt.flush();
        assertEquals(SITE, browser.lastRevoke);
        browser.emit("{\"captureId\":\"" + browser.lastArm.get("captureId") + "\",\"status\":\"FAILED\",\"code\":\"PDD_CONSENT_REVOKED\",\"observedAt\":\"x\"}");
        Edt.flush();
        Edt.flush();
        assertFalse(model().hasImage());
        assertTrue(model().banner().contains("revogada"), model().banner());
        assertEquals("CONSENT_REQUIRED", model().tabs().stream().filter(t -> t.tabRef().equals("t-SiteTab00002")).findFirst().orElseThrow().state());
    }

    @Test
    @DisplayName("S013 recusas: rótulo com texto fora do vocabulário na descrição (cadeia comprometida) e armação recusada com motivo próprio")
    void recusas() {
        studio();
        Automation a = site();
        String login = openLogin();
        browser.consent = "SESSION";
        Edt.run(() -> pdd().reloadTabs());
        Edt.flush();
        shoot(login, "t-SiteTab00002", capturedV3(a, "Maria Souza"));
        assertFalse(model().hasImage());
        assertEquals("Imagem recusada pelo Studio", model().bannerTitle(), model().banner());
        assertTrue(model().banner().contains("LABEL_NOT_INTERFACE_TEXT"), model().banner());
        for (String[] c : new String[][] {{"PDD_CONSENT_REQUIRED", "ainda não está autorizada"}, {"ORIGIN_CHANGED", "mudou de site"},
                {"POLICY_REQUIRES_V3", "nekkora-visual/3"}, {"BROWSER_RESTRICTED", "bloqueada pelo navegador"}, {"PERMISSION_REQUIRED", "Sem acesso do Chrome"}}) {
            browser.armReason = c[0];
            Edt.run(() -> pdd().capture(login, "t-SiteTab00002"));
            Edt.flush();
            Edt.flush();
            assertEquals("Captura não armada", model().bannerTitle(), c[0]);
            assertTrue(model().banner().contains(c[1]), c[0] + ": " + model().banner());
        }
    }

    @Test
    @DisplayName("S013 extensão anterior (0.5.0): site autorizado fica indisponível com aviso de atualização; laboratório continua (v2)")
    void extensaoAntiga() {
        browser.version = "0.5.0";
        studio();
        site();
        openLogin();
        PddPanel.Tab t = model().tabs().stream().filter(x -> x.tabRef().equals("t-SiteTab00002")).findFirst().orElseThrow();
        assertEquals("UPDATE_EXTENSION", t.state());
        assertFalse(browser.sent.contains("studio.pdd.consent.list"), "nenhum comando novo para extensão antiga");
        selectTab("t-SiteTab00002");
        assertTrue(Edt.call(() -> pdd().panel().consentText().getText()).contains("0.6.0"));
    }

    @Test
    @DisplayName("S013 estado das abas (puro): perfil BENCHMARK de extensão antiga não captura; origem com caminho é descartada; dica só com contagens")
    void estadoPuro() {
        assertEquals("UPDATE_EXTENSION", PddPresenter.tab(new BrowserTab("t-Bench0000001", "RPA", "rpachallenge.com", TabAccess.AUTHORIZED, true, false,
                "BENCHMARK", "https://rpachallenge.com", null), true).state());
        BrowserTab withPath = new BrowserTab("t-Site00000001", null, "app.exemplo.test", TabAccess.AUTHORIZED, true, false, null, SITE + "/pacientes?id=1", "NONE");
        assertNull(withPath.origin(), "origem com caminho/consulta é descartada");
        assertNull(PddPresenter.tab(withPath, true), "sem origem confiável, não há como autorizar");
        assertEquals("RECORDING", PddPresenter.tab(new BrowserTab("t-Rec000000001", "Lab", "127.0.0.1", TabAccess.LAB, true, true, "LAB",
                "http://127.0.0.1:18765", "NOT_REQUIRED"), true).state());
        String hint = PddPresenter.tabsHint(false, 1, 2, 3, 1);
        assertTrue(hint.contains("3 aba(s) com acesso do Chrome, mas sem autorização de captura do PDD"), hint);
        assertTrue(hint.contains("2 aba(s) sem acesso do Chrome") && hint.contains("1 aba(s) sendo gravada(s)") && hint.contains("bloqueada(s) pelo navegador"));
        assertNotNull(PddPresenter.tabsHint(true, 0, 0, 0, 0));
    }
}
