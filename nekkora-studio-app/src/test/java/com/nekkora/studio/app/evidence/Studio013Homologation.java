package com.nekkora.studio.app.evidence;

import com.nekkora.studio.app.StudioApplication;
import com.nekkora.studio.app.ui.StudioActions;
import com.nekkora.studio.app.workspace.Selection;
import com.nekkora.studio.bridge.BridgeServer;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.evidence.EvidenceType;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary;
import com.nekkora.studio.domain.evidence.ObservationSummary;
import com.nekkora.studio.domain.evidence.ObservedEvidence;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepOrigin;
import com.nekkora.studio.recorder.RecorderState;
import com.nekkora.studio.recorder.RecordingMode;
import com.nekkora.studio.ui.recorder.FloatingRecorderPanel;
import com.nekkora.studio.ui.AutomationOverview;
import com.nekkora.studio.ui.LogicalSessionView;
import com.nekkora.studio.ui.form.MoveStepForm;
import com.nekkora.studio.ui.form.StepDetailsForm;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.app.pdd.PddPresenter;
import com.nekkora.studio.app.pdd.SwingPddHost;
import com.nekkora.studio.ui.pdd.PddPanel;
import com.nekkora.studio.ui.pdd.PddImageEditor;
import com.nekkora.studio.pdd.VisualVerifier;
import com.nekkora.studio.pdd.DocumentDraft;
import javax.swing.JDialog;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.target.DecisionActor;
import com.nekkora.studio.domain.target.TargetDecision;
import com.nekkora.studio.persistence.ProjectFormat;
import com.nekkora.studio.validation.AutomationValidator;
import com.nekkora.studio.validation.RuleSet;
import java.util.LinkedHashMap;
import com.nekkora.studio.ui.recorder.FloatingRecorderWindow;
import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.dialog.NekkoraDialog;
import com.nekkora.ui.input.NekkoraRadioButton;
import com.nekkora.ui.navigation.NekkoraToolbarButton;
import com.nekkora.ui.theme.ThemeManager;
import java.awt.AWTException;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Homologação REAL da NEKKORA-STUDIO-013 no Windows — captura do PDD em SITE autorizado, sem lista compilada: Studio de
 * verdade (dados isolados), Chrome for Testing com a extensão desta branch (0.6.0), Native Host do script oficial, mouse e
 * teclado do sistema (Robot) e o "sistema real" SINTÉTICO {@code test-lab/pages/studio013/sistema.html} servido como site
 * externo de teste ({@code site-a.nekkora.test}, resolvido para 127.0.0.1 pelo próprio Chrome), com dados FICTÍCIOS e
 * marcadores-sentinela em todo dado sensível (cor #E6007E e texto).
 *
 * <p>Roteiro: mapeamento no site → janela do PDD mostra "Esta página permite gravação, mas a captura de PDD ainda não está
 * autorizada" → "Autorizar captura PDD neste site" (clique real, sessão por padrão) → captura com o gesto Alt+Shift+K →
 * imagem nekkora-visual/3 desenhada pelo Studio (nenhum pixel da página) → recaptura, recorte, marcador manual, Aplicar →
 * PDF → revogar pela lista, "lembrar este site", revogar com a captura armada, acesso do Chrome revogado → laboratório local
 * sem autorização → temas e higiene dos artefatos. Operador: Claude como AGENTE DE TESTE (TEST_AGENT). Antes de cada
 * clique, tecla ou captura de tela o primeiro plano é conferido.
 *
 * <p>Uso: {@code java -Dnekkora.studio.decisionActor=TEST_AGENT ... Studio013Homologation <saída> <repositório-da-extensão> [escala]}.
 */
public final class Studio013Homologation {

    private static final int LAB_PORT = 18765;
    private static final String BASE = "http://127.0.0.1:" + LAB_PORT + "/lab/";
    private static final String SITE_ORIGIN = "http://site-a.nekkora.test:" + LAB_PORT;
    private static final String SITE_PAGE = SITE_ORIGIN + "/lab/studio013/sistema.html";
    private static final String LAB_PAGE = BASE + "studio013/sistema.html";
    private static final String PAGE = LAB_PAGE;
    /** Digitados na GRAVAÇÃO (fictícios): nunca podem aparecer no modelo, rascunho, PDF ou logs. */
    private static final String USER = "anasouza";
    private static final String PASS = "Lab42senha";
    /** Tudo o que NUNCA pode chegar ao documento: valores, dados do sistema sintético, caminhos e identificadores técnicos. */
    private static final List<String> SENTINELS = List.of(USER, PASS, "SENTINELA", "Sentinela", "Ana Souza", "Bruno Lima", "Carla Dias", "Zeta",
            "123.456.789-09", "987.654.321-00", "S3nh@Lab", "ana.souza", "autofill", "sombra.aberta", "Internado", "Observação", "Exame",
            "sistema.html", "studio013", "tgt-");
    /** Rótulos que a página pode mostrar (todos do vocabulário). */
    private static final java.util.Set<String> ROTULOS = java.util.Set.of("Início", "Menu", "Usuário", "Senha", "Entrar", "Pesquisar", "Salvar", "Ver detalhes",
            "Confirmar", "Cancelar", "Sair");
    private static final int CHROME_X = 880;

    private final File out;
    private final Path ext;
    private final boolean compat = false;
    /** Execução em 150% (JVM com -Dsun.java2d.uiScale=1.5): só o mapeamento e as capturas por tema. */
    private boolean scale;
    /** G9: Python + Playwright por último (depois do debug e do modelo homologados). */
    private boolean python;
    private int cdpPortUsed;
    /** Escala da interface Java (1.0 ou 1.5): o Robot usa coordenadas lógicas; Chrome e página, px físicos. */
    private double sc = 1;
    private final Robot robot;
    private final List<String> log = new ArrayList<>();
    private int failures;
    private int checks;
    private StudioApplication.Window window;
    private BridgeServer bridge;
    private Process chrome;
    private Process labServer;
    private Path profile;
    private Path testExtension;
    /** A mesma extensão da branch declarada como 0.4.0 (o Studio não pede a v2) e como ela é (0.5.0). */
    private Path extV1;
    private Path extV2;
    private Cdp cdp;
    private Cdp.Page harness;
    private Cdp.Page panel;
    private String extId;
    private Cdp.Target labTarget;
    private Cdp.Page page;
    private int labTab;

    private Studio013Homologation(File out, Path ext) throws AWTException {
        this.out = out;
        this.ext = ext;
        this.robot = new Robot();
        robot.setAutoDelay(25);
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Studio013Homologation h = new Studio013Homologation(out, Path.of(args[1]).toAbsolutePath().normalize());
        h.scale = args.length > 2 && "escala".equals(args[2]);
        h.python = args.length > 2 && "python".equals(args[2]);
        int code = 1;
        Thread watchdog = new Thread(() -> { // log parado por 4 min = travado: registra a EDT, encerra os processos de teste e sai
            while (true) {
                sleep(5000);
                if (System.currentTimeMillis() - h.lastLog > 240_000) {
                    h.log("TRAVADO há 4 min — estado da EDT:");
                    Thread.getAllStackTraces().forEach((t, st) -> {
                        if (t.getName().startsWith("AWT-EventQueue")) for (int i = 0; i < Math.min(25, st.length); i++) h.log("    at " + st[i]);
                    });
                    h.log(String.format("RESULTADO: %d verificações, %d falha(s) + TRAVAMENTO", h.checks, h.failures + 1));
                    h.writeLog();
                    h.teardown();
                    Runtime.getRuntime().halt(4);
                }
            }
        }, "vigia");
        watchdog.setDaemon(true);
        watchdog.start();
        try {
            h.run();
            code = h.failures == 0 ? 0 : 2;
        } catch (Throwable t) {
            for (Throwable x = t; x != null; x = x.getCause()) {
                h.log((x == t ? "ERRO " : "CAUSA ") + x);
                for (StackTraceElement s : x.getStackTrace()) h.log("    at " + s);
            }
            h.failures++;
        } finally {
            h.log(String.format("RESULTADO: %d verificações, %d falha(s)", h.checks, h.failures));
            h.writeLog();
            h.teardown();
        }
        System.exit(code);
    }

    // ============================================================================== roteiro

    private void run() throws Exception {
        log("Tela: " + GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds() + " · java " + System.getProperty("java.version")
                + (scale ? " · interface em 150%" : ""));
        log("Operador: Claude como AGENTE DE TESTE (autorizações, recortes e marcadores manuais TEST_AGENT; nenhuma confirmação humana) · ator do Studio: "
                + System.getProperty("nekkora.studio.decisionActor"));
        sc = scale ? 1.5 : 1;
        prepareEnvironment();
        Path studioData = Path.of(System.getenv().getOrDefault("NEKKORA_STUDIO_DATA_DIR", out.toPath().resolve("studio-data").toString()));
        Files.createDirectories(studioData);
        bridge = BridgeServer.start(BridgeServer.Options.defaults(StudioApplication.VERSION));
        window = edt(() -> StudioApplication.launch(Optional.of(NekkoraThemes.SAKURA_DARK), bridge, bridge::close,
                com.nekkora.studio.app.StudioEnvironment.at(studioData), List.of()));
        edt(() -> {
            window.frame().setSize(scale ? 840 : 870, scale ? 660 : 1000);
            window.frame().setLocation(0, 0);
            window.frame().toFront();
            return null;
        });
        waitFor("janela do Studio", 10, () -> edt(() -> window.frame().isShowing()));
        if (scale) {
            runScaled();
            return;
        }
        launchChrome(SITE_PAGE);
        connect();
        check("A0: extensão 0.6.0 conectada (consentimento PDD por origem e nekkora-visual/3)", statusText().contains("Extension 0.6.0"));

        // ================================================================ A1 — mapeamento real num SITE autorizado pelo Chrome
        focusStudio();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_N);
        NekkoraDialog nd = waitDialog("Nova automação");
        typeIntoFocused("Atendimento sintético");
        press(KeyEvent.VK_ENTER);
        waitClosed(nd);
        newSession("Autenticação");
        Automation a = active();
        LogicalSession auth = a.sessions().get(0);
        selectSession(a, auth);
        startRecordingOn("site-a", RecordingMode.CAPTURE_AND_INTERACT);
        moveFloatingAway();
        clickInPage("document.getElementById('usuario')");
        typeText(USER);
        clickInPage("document.getElementById('senha')");
        typeText(PASS);
        clickInPage("document.getElementById('entrar')");
        waitFor("3 etapas na Autenticação", 30, () -> edt(auth::stepCount) >= 3);
        sleep(1500);
        finishRecording();
        log("  etapas: " + edt(() -> names(auth)));
        check("A1: gravação no site autorizado pelo Chrome = preencher, preencher, clicar", edt(() -> kinds(auth).equals(List.of("FILL", "FILL", "CLICK"))));
        check("A1: nenhum valor digitado no modelo", !edt(() -> domainText(a)).contains(USER) && !edt(() -> domainText(a)).contains(PASS));
        long revisionBefore = edt(a::revision);
        String nekkoraBefore = edt(() -> domainText(a));

        // ================================================================ A2 — PDD: o site grava, mas a captura do PDD não está autorizada
        menuPath("Automação", "Gerar documentação", "PDD visual…");
        JDialog dlg = pddDialog();
        PddPresenter pdd = edt(() -> window.studio().visualPdd());
        PddPanel panel = edt(pdd::panel);
        waitFor("abas listadas", 15, () -> !model(panel).tabs().isEmpty());
        log("  abas: " + edt(() -> model(panel).tabs().stream().map(t -> t.label() + " [" + t.state() + "]").toList()));
        selectItem(panel, "secao-1");
        setText(panel.titleField(), "Autenticação");
        click(edt(panel::saveTitleButton));
        String loginKey = model(panel).items().get(1).key();
        selectItem(panel, loginKey);
        selectTab(panel, "site-a");
        String consent = edt(() -> panel.consentText().getText());
        log("  " + consent);
        check("A2: \"Esta página permite gravação, mas a captura de PDD ainda não está autorizada\"",
                consent.startsWith("Esta página permite gravação, mas a captura de PDD ainda não está autorizada"));
        check("A2: a origem aparece exata e sem caminho", consent.contains(SITE_ORIGIN) && !consent.contains("/lab/") && !consent.contains("sistema"));
        check("A2: sem autorização, \"Capturar tela…\" fica indisponível e \"Autorizar\" aparece",
                !edt(() -> panel.captureButton().isEnabled()) && edt(() -> panel.authorizeButton().isShowing()));
        shot(dlg, panel, "A2-site-sem-autorizacao-pdd");

        // ================================================================ A3 — autorizar pelo Studio (clique real; padrão = só esta sessão)
        click(edt(panel::authorizeButton));
        waitFor("autorização concedida", 15, () -> "Captura PDD autorizada".equals(model(panel).bannerTitle()));
        waitFor("lista de sites", 10, () -> !model(panel).sites().isEmpty());
        log("  " + model(panel).banner() + " · sites: " + model(panel).sites().stream().map(PddPanel.Site::label).toList());
        check("A3: site autorizado só nesta sessão, registrado como agente de teste", model(panel).sites().size() == 1
                && model(panel).sites().get(0).label().contains("até fechar o navegador") && model(panel).sites().get(0).label().contains("agente de teste"));
        selectTab(panel, "site-a");
        check("A3: a aba passa a poder capturar", edt(() -> panel.captureButton().isEnabled()));
        check("A3: \"Capturar\", \"Revogar captura PDD\" e a lista de sites inteiros dentro da janela (nada empurrado para fora)",
                reachable(dlg, panel.captureButton(), panel.revokeSiteButton(), panel.sitesCombo(), panel.tabsInput()));
        shot(dlg, panel, "A3-site-autorizado");

        // ================================================================ A4 — captura v3 (gesto real): imagem sintética desenhada pelo Studio
        boolean c1 = captureBlock(panel, loginKey, SITE_PAGE, null, "site-a");
        check("A4: captura no site associada e rascunho 1.2.0", c1 && "Imagem associada · rascunho 1.2.0".equals(model(panel).bannerTitle()));
        DocumentDraft d1 = edt(() -> pdd.draft().orElseThrow());
        DocumentDraft.SafeCapture lc = d1.captures().get(d1.block(loginKey).orElseThrow().captureSha());
        log("  " + lc.width() + "×" + lc.height() + " escala " + lc.devicePixelRatio() + " · regiões " + lc.regions().stream()
                .collect(java.util.stream.Collectors.groupingBy(DocumentDraft.Region::kind, java.util.TreeMap::new, java.util.stream.Collectors.counting()))
                + " · limitações " + lc.limitations());
        java.util.Map<String, Long> kinds = lc.regions().stream().collect(java.util.stream.Collectors.groupingBy(DocumentDraft.Region::kind,
                java.util.stream.Collectors.counting()));
        check("A4: política nekkora-visual/4 (print real) só com máscaras BLOCK/FIELD/TEXT", "nekkora-visual/4".equals(lc.policy())
                && List.of("BLOCK", "FIELD", "TEXT").containsAll(kinds.keySet()));
        check("A4: campos (inclusive de shadow roots aberta e fechada) cobertos", kinds.getOrDefault("FIELD", 0L) >= 8);
        check("A4: tabela, frame, canvas, vídeo, fundo e pseudo-elemento cobertos", kinds.getOrDefault("BLOCK", 0L) >= 6
                && lc.limitations().containsAll(List.of("TABLE_CONTENT_HIDDEN", "FRAMES_HIDDEN")));
        check("A4: linhas de texto de dados cobertas", kinds.getOrDefault("TEXT", 0L) >= 5);
        BufferedImage img = storedImage(pdd, loginKey);
        save(img, "A4-print-mascarado-site");
        check("A4: NENHUM pixel da cor-sentinela no print real (todo dado da página está na cor-sentinela)", sentinelPixels(img) == 0);
        String draftJson = Files.readString(edt(() -> pdd.store().folder(a.id())).resolve("rascunho.json"));
        for (String s : SENTINELS) check("A4: rascunho sem \"" + s + "\"", !draftJson.contains(s));
        shot(dlg, panel, "A4-captura-v4");

        // ================================================================ A5 — recaptura → candidata → recorte (mouse) → Aplicar
        String t5 = recapture(panel, loginKey, SITE_PAGE, null, "site-a");
        check("A5: nova captura vira candidata", "Nova captura pronta para revisão".equals(t5) && model(panel).reviewing());
        int imgW = model(panel).image().image().getWidth();
        @SuppressWarnings("unchecked")
        List<Object> g = (List<Object>) page.eval("(() => { const c = document.getElementById('ficha').getBoundingClientRect(); return [c.left, c.top, c.width, c.height]; })()");
        int s2 = (int) Math.round(lc.devicePixelRatio()); // px do dispositivo por px CSS (print real)
        Rectangle wanted = new Rectangle((int) Math.max(0, (((Number) g.get(0)).doubleValue() - 8) * s2), (int) Math.max(0, (((Number) g.get(1)).doubleValue() - 8) * s2),
                (int) Math.min(imgW, (((Number) g.get(2)).doubleValue() + 16) * s2), (int) ((((Number) g.get(3)).doubleValue() + 16) * s2));
        dragOnEditor(panel, panel.selectAreaButton(), wanted);
        Rectangle crop = model(panel).image().crop();
        log("  recorte pedido " + wanted + " · obtido " + crop);
        check("A5: recorte pelo mouse sobre a candidata", crop != null && Math.abs(crop.x - wanted.x) <= 8 && Math.abs(crop.width - wanted.width) <= 10);
        click(edt(panel::applyButton));
        waitFor("aplicada", 10, () -> !model(panel).reviewing());
        check("A5: Aplicar substitui a imagem do bloco", "Imagem substituída".equals(model(panel).bannerTitle()));

        // ================================================================ A6 — marcador manual (o alvo não é comprovado fora de uma inspeção ativa)
        DocumentDraft d2 = edt(() -> pdd.draft().orElseThrow());
        DocumentDraft.SafeCapture c2 = d2.captures().get(d2.block(loginKey).orElseThrow().captureSha());
        log("  marcadores: " + c2.annotations().stream().map(x -> x.status() + "/" + x.reason()).toList());
        @SuppressWarnings("unchecked")
        List<Object> br = (List<Object>) page.eval("(() => { const r = document.getElementById('entrar').getBoundingClientRect(); return [r.left, r.top, r.width, r.height]; })()");
        Rectangle btn = new Rectangle((int) Math.round(((Number) br.get(0)).doubleValue() * s2), (int) Math.round(((Number) br.get(1)).doubleValue() * s2),
                (int) Math.round(((Number) br.get(2)).doubleValue() * s2), (int) Math.round(((Number) br.get(3)).doubleValue() * s2));
        edt(() -> {
            panel.markerInput().setSelectedIndex(2);
            return null;
        });
        boolean markable = edt(() -> panel.placeMarkerButton().isEnabled());
        if (markable) {
            dragOnEditor(panel, panel.placeMarkerButton(), new Rectangle(btn.x - 3, btn.y - 3, btn.width + 6, btn.height + 6));
            DocumentDraft d3 = edt(() -> pdd.draft().orElseThrow());
            DocumentDraft.Annotation manual = d3.captures().get(d3.block(loginKey).orElseThrow().captureSha()).annotations().stream()
                    .filter(DocumentDraft.Annotation::manual).findFirst().orElse(null);
            check("A6: marcador MANUAL + TEST_AGENT na etapa Entrar", manual != null && "TEST_AGENT".equals(manual.reviewer())
                    && manual.stepId().equals(edt(() -> auth.steps().get(2).id())));
        } else {
            check("A6: Entrar já tem marcador automático (manual dispensado)", c2.annotations().stream().anyMatch(x -> !x.manual()
                    && x.status() == DocumentDraft.Annotation.Status.PLACED && x.stepId().equals(auth.steps().get(2).id())));
        }
        check("A6: a automação não mudou", edt(a::revision) == revisionBefore && nekkoraBefore.equals(edt(() -> domainText(a))));
        shot(dlg, panel, "A6-recorte-e-marcador");

        // ================================================================ A7 — exportar o PDF
        Path pdfDir = out.toPath().resolve("pdf");
        Files.createDirectories(pdfDir);
        exportByDialog(panel, pdfDir);
        Path v1 = pdfDir.resolve("pdd-visual-v001.pdf");
        check("A7: PDF exportado", Files.exists(v1));
        List<BufferedImage> pages = renderPdf(v1, "A7-pdf-pagina");
        String text;
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(v1.toFile())) {
            text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
        }
        Files.writeString(out.toPath().resolve("S013-pdf-texto-extraido.txt"), text, StandardCharsets.UTF_8);
        String flat = text.replaceAll("\\s+", " ");
        check("A7: o PDF declara o print real com os dados cobertos e pede revisão antes de compartilhar", flat.contains("capturas reais da tela")
                && flat.contains("Revise as imagens antes de compartilhar"));
        for (String s : SENTINELS) check("A7: PDF sem \"" + s + "\"", !text.contains(s));
        int pageSentinels = 0;
        for (BufferedImage p : pages) pageSentinels += sentinelPixels(p);
        check("A7: nenhuma cor-sentinela nas páginas renderizadas", pageSentinels == 0);

        // ================================================================ A8 — revogar pela lista; autorizar lembrando o site
        click(edt(panel::revokeSiteButton));
        waitFor("revogação", 15, () -> "Captura PDD revogada".equals(model(panel).bannerTitle()));
        waitFor("aba volta a pedir autorização", 15, () -> model(panel).tabs().stream().anyMatch(t -> "CONSENT_REQUIRED".equals(t.state())));
        selectTab(panel, "site-a");
        check("A8: revogado pelo Studio → captura indisponível de novo", !edt(() -> panel.captureButton().isEnabled()) && model(panel).sites().isEmpty());
        click(edt(panel::rememberBox));
        click(edt(panel::authorizeButton));
        waitFor("autorizado (lembrar)", 15, () -> model(panel).sites().stream().anyMatch(s -> s.label().contains("lembrado")));
        check("A8: \"Lembrar este site\" registra o site como lembrado", model(panel).sites().get(0).label().contains("lembrado"));

        // ================================================================ A9 — revogar com a captura ARMADA
        selectItem(panel, loginKey);
        selectTab(panel, "site-a");
        click(edt(panel::captureButton));
        waitFor("captura armada", 15, () -> "Captura armada".equals(model(panel).bannerTitle()));
        click(edt(panel::revokeSiteButton));
        waitFor("captura derrubada", 20, () -> "Imagem indisponível".equals(model(panel).bannerTitle()) || model(panel).banner().contains("revogada"));
        log("  " + model(panel).bannerTitle() + ": " + model(panel).banner());
        sleep(1500);
        check("A9: revogar com a captura armada cancela a captura (nenhuma imagem nova)", !model(panel).reviewing());

        // ================================================================ A10 — acesso do Chrome revogado: o consentimento PDD é apagado
        selectTab(panel, "site-a");
        click(edt(panel::authorizeButton));
        waitFor("autorizado de novo", 15, () -> !model(panel).sites().isEmpty());
        siteAccess("http://site-a.nekkora.test/*", false);
        sleep(1500);
        edt(() -> {
            pdd.reloadTabs();
            return null;
        });
        waitFor("aba sem acesso do Chrome", 15, () -> model(panel).tabs().stream().anyMatch(t -> "CHROME_ACCESS_REQUIRED".equals(t.state())));
        check("A10: sem acesso do Chrome, a autorização PDD do site some da lista", model(panel).sites().isEmpty());
        siteAccess("http://site-a.nekkora.test/*", true);
        sleep(1500);
        edt(() -> {
            pdd.reloadTabs();
            return null;
        });
        waitFor("acesso devolvido", 15, () -> model(panel).tabs().stream().anyMatch(t -> "CONSENT_REQUIRED".equals(t.state())));
        check("A10: devolver o acesso do Chrome não devolve a autorização PDD", model(panel).sites().isEmpty());

        // ================================================================ A11 — o laboratório local continua sem autorização (agora v3)
        String t11 = recapture(panel, loginKey, LAB_PAGE, null, "127.0.0.1");
        check("A11: laboratório captura sem pedir autorização (candidata v4, sem cor-sentinela)", "Nova captura pronta para revisão".equals(t11)
                && "nekkora-visual/4".equals(model(panel).image().policy()) && sentinelPixels(model(panel).image().image()) == 0);
        click(edt(panel::cancelCandidateButton));

        // ================================================================ A12 — temas e higiene dos artefatos
        for (NekkoraTheme t : NekkoraThemes.all()) {
            edt(() -> {
                ThemeManager.setTheme(t);
                return null;
            });
            sleep(700);
            shot(dlg, panel, "A12-tema-" + t.toString().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-"));
        }
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("rascunho e capturas", dirText(edt(() -> pdd.store().folder(a.id()))));
        sources.put("logs do host", hostLogs());
        // as linhas de verificação citam as próprias sentinelas ("rascunho sem …"): só o que foi OBSERVADO entra na varredura
        sources.put("log da homologação (observações)", String.join("\n", log.stream().filter(l -> !l.startsWith("OK ") && !l.startsWith("FALHA ")).toList()));
        for (Map.Entry<String, String> e : sources.entrySet()) {
            check("A12 " + e.getKey() + ": sem valores digitados e sem dados do sistema sintético", SENTINELS.stream().noneMatch(e.getValue()::contains));
        }
        int dirty = 0;
        try (Stream<Path> fs = Files.list(out.toPath())) {
            for (Path f : fs.filter(f -> f.toString().endsWith(".png")).sorted().toList()) {
                int n = sentinelPixels(ImageIO.read(f.toFile()));
                if (n > 0) {
                    dirty++;
                    log("  cor-sentinela em " + f.getFileName() + ": " + n + " px");
                }
            }
        }
        check("A12: nenhuma evidência PNG desta rodada com a cor-sentinela", dirty == 0);
        check("A12: a automação (.nekkora) nunca mudou pelo PDD nem pelas autorizações", edt(a::revision) == revisionBefore
                && nekkoraBefore.equals(edt(() -> domainText(a))));
    }

    /** Os componentes aparecem INTEIROS dentro da janela (rolando a coluna até eles, como o usuário faria). */
    private boolean reachable(java.awt.Window w, javax.swing.JComponent... cs) throws Exception {
        return edt(() -> {
            Rectangle b = w.getBounds();
            for (javax.swing.JComponent x : cs) {
                x.scrollRectToVisible(new Rectangle(0, 0, x.getWidth(), x.getHeight()));
                if (!x.isShowing()) return false;
                java.awt.Point p = x.getLocationOnScreen();
                java.awt.Rectangle vis = x.getVisibleRect();
                if (vis.width < x.getWidth() || !b.contains(p.x, p.y) || !b.contains(p.x + x.getWidth() - 1, p.y + x.getHeight() - 1)) {
                    log("  fora da janela: " + x.getAccessibleContext().getAccessibleName() + " " + new Rectangle(p.x, p.y, x.getWidth(), x.getHeight())
                            + " visível " + vis + " janela " + b);
                    return false;
                }
            }
            return true;
        });
    }

    /** 150%: autorizar e capturar no site, revisar a candidata e temas (a janela inteira dentro da tela). */
    private void runScaled() throws Exception {
        launchChrome(SITE_PAGE);
        connect();
        focusStudio();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_N);
        NekkoraDialog nd = waitDialog("Nova automação");
        typeIntoFocused("Atendimento sintético");
        press(KeyEvent.VK_ENTER);
        waitClosed(nd);
        newSession("Autenticação");
        Automation a = active();
        LogicalSession auth = a.sessions().get(0);
        selectSession(a, auth);
        startRecordingOn("site-a", RecordingMode.CAPTURE_AND_INTERACT);
        edt(() -> {
            java.awt.Rectangle sb = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            floating().setLocation(sb.x + 8, Math.max(sb.y, sb.y + sb.height - floating().getHeight() - 8));
            return null;
        });
        clickInPage("document.getElementById('usuario')");
        typeText(USER);
        clickInPage("document.getElementById('entrar')");
        waitFor("2 etapas", 30, () -> edt(auth::stepCount) >= 2);
        sleep(1500);
        finishRecording();
        menuPath("Automação", "Gerar documentação", "PDD visual…");
        JDialog dlg = pddDialog();
        PddPresenter pdd = edt(() -> window.studio().visualPdd());
        PddPanel panel = edt(pdd::panel);
        edt(() -> {
            java.awt.Rectangle s = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            dlg.setBounds(s.x + 8, s.y + 8, s.width - 16, s.height - 16);
            dlg.validate();
            return null;
        });
        waitFor("abas", 15, () -> !model(panel).tabs().isEmpty());
        String loginKey = model(panel).items().get(1).key();
        selectItem(panel, loginKey);
        selectTab(panel, "site-a");
        check("E1: em 150%, \"Autorizar captura PDD neste site\" visível dentro da janela", edt(() -> {
            javax.swing.JComponent x = panel.authorizeButton();
            x.scrollRectToVisible(new Rectangle(0, 0, x.getWidth(), x.getHeight()));
            java.awt.Point p = x.getLocationOnScreen();
            return x.isShowing() && dlg.getBounds().contains(p.x, p.y) && dlg.getBounds().contains(p.x + x.getWidth() - 1, p.y + x.getHeight() - 1);
        }));
        click(edt(panel::authorizeButton));
        waitFor("autorizado", 15, () -> !model(panel).sites().isEmpty());
        boolean c1 = captureBlock(panel, loginKey, SITE_PAGE, null, "site-a");
        DocumentDraft d = edt(() -> pdd.draft().orElseThrow());
        check("E1: captura v4 em 150%", c1 && "nekkora-visual/4".equals(d.captures().get(d.block(loginKey).orElseThrow().captureSha()).policy()));
        recapture(panel, loginKey, SITE_PAGE, null, "site-a");
        for (NekkoraTheme t : NekkoraThemes.all()) {
            edt(() -> {
                ThemeManager.setTheme(t);
                return null;
            });
            sleep(700);
            shot(dlg, panel, "E1-150-tema-" + t.toString().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-"));
        }
        click(edt(panel::applyButton));
        check("E1: Aplicar em 150%", "Imagem substituída".equals(model(panel).bannerTitle()));
        int dirty = 0;
        try (Stream<Path> fs = Files.list(out.toPath())) {
            for (Path f : fs.filter(f -> f.toString().endsWith(".png")).sorted().toList()) if (sentinelPixels(ImageIO.read(f.toFile())) > 0) dirty++;
        }
        check("E1: nenhuma evidência PNG com a cor-sentinela", dirty == 0);
    }

    // ============================================================================== apoio PDD-01

    private static final String FILL_LOGIN = "document.getElementById('ds_login').value='" + USER + "';document.getElementById('passwordTemp').value='" + PASS + "';";

    private void finishRecording() throws Exception {
        restoreStudio(floating());
        clickButtonWithText(floating(), "Finalizar");
        waitFor("gravação finalizada", 30, () -> !edt(() -> window.studio().recorder().snapshot().state().active()));
        sleep(600);
        clickButtonWithText(floating(), "Voltar ao Studio");
        sleep(1000);
    }

    private JDialog pddDialog() throws Exception {
        return (JDialog) waitValue("janela do PDD", 15, () -> edt(() -> {
            for (Window w : Window.getWindows()) if (w instanceof JDialog d && d.isShowing() && d.getTitle().startsWith("PDD visual")) return d;
            return null;
        }));
    }

    /** "Capturar novamente…" + gesto real no Chrome; devolve o título do aviso que veio depois. */
    private String recapture(PddPanel p, String key, String url, String prepare, String tabTitle) throws Exception {
        cdp.activate(labTarget);
        navigate(url);
        if (prepare != null) page.eval(prepare);
        edt(() -> {
            window.studio().visualPdd().reloadTabs();
            return null;
        });
        sleep(1200);
        selectItem(p, key);
        selectTab(p, tabTitle);
        click(edt(p::captureButton));
        waitFor("captura armada", 15, () -> "Captura armada".equals(model(p).bannerTitle()));
        focusChrome();
        guardForeground("atalho de captura");
        press(KeyEvent.VK_ALT, KeyEvent.VK_SHIFT, KeyEvent.VK_K);
        waitFor("captura concluída", 30, () -> !"Captura armada".equals(model(p).bannerTitle()));
        log("  " + model(p).bannerTitle() + ": " + model(p).banner());
        selectItem(p, key); // a janela do PDD volta à frente
        return model(p).bannerTitle();
    }

    /** Clique real no botão de modo e ARRASTO real do mouse sobre o editor (retângulo em px da imagem). */
    private void dragOnEditor(PddPanel p, AbstractButton modeButton, Rectangle r) throws Exception {
        click(modeButton);
        PddImageEditor ed = edt(p::imageEditor);
        edt(() -> {
            ed.scrollRectToVisible(new Rectangle(0, 0, ed.getWidth(), ed.getHeight()));
            return null;
        });
        sleep(200);
        double[] g = edt(() -> {
            java.awt.Point o = ed.getLocationOnScreen();
            double s = Math.min(1.0, Math.max(1, ed.getWidth() - 2) / (double) p.model().image().image().getWidth());
            return new double[] {o.x, o.y, s};
        });
        int x0 = (int) Math.round(g[0] + 1 + r.x * g[2]), y0 = (int) Math.round(g[1] + 1 + r.y * g[2]);
        int x1 = (int) Math.round(g[0] + 1 + (r.x + r.width) * g[2]), y1 = (int) Math.round(g[1] + 1 + (r.y + r.height) * g[2]);
        guardForeground("arrasto no editor de imagem");
        robot.mouseMove(x0, y0);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        for (int i = 1; i <= 8; i++) {
            robot.mouseMove(x0 + (x1 - x0) * i / 8, y0 + (y1 - y0) * i / 8);
            sleep(30);
        }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        sleep(500);
    }

    private void exportByDialog(PddPanel p, Path dir) throws Exception {
        long before = countPdfs(dir);
        click(edt(p::exportButton));
        waitChooser("Pasta para o PDF (versões anteriores nunca são substituídas)");
        typePath(dir);
        waitFor("PDF exportado", 30, () -> "PDF exportado".equals(model(p).bannerTitle()) && countPdfs(dir) == before + 1);
    }

    private static long countPdfs(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(f -> f.toString().endsWith(".pdf")).count();
        }
    }

    private static String imageOf(PddPresenter pdd, String blockId) throws Exception {
        return imageOf(edt(() -> pdd.draft().orElseThrow()), blockId);
    }

    private static String imageOf(DocumentDraft d, String blockId) {
        DocumentDraft.Block b = d.block(blockId).orElseThrow();
        return b.captureSha().isEmpty() ? "" : d.captures().get(b.captureSha()).sha256();
    }

    private static BufferedImage storedImage(PddPresenter pdd, String blockId) throws Exception {
        DocumentDraft d = edt(() -> pdd.draft().orElseThrow());
        byte[] png = pdd.store().capture(d.automationId(), imageOf(d, blockId)).orElseThrow();
        return ImageIO.read(new java.io.ByteArrayInputStream(png));
    }

    /** Fração dos pixels exatamente na cor {@code rgb}. */
    private static double share(BufferedImage img, int rgb) {
        long n = 0;
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) if ((img.getRGB(x, y) & 0xFFFFFF) == (rgb & 0xFFFFFF)) n++;
        return n / (double) ((long) img.getWidth() * img.getHeight());
    }

    /** Fração de pixels a até ±4 por canal de {@code rgb} (página renderizada: bordas suavizadas). */
    private static double nearShare(BufferedImage img, int rgb) {
        long n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int c = img.getRGB(x, y);
                if (Math.abs(((c >> 16) & 0xFF) - ((rgb >> 16) & 0xFF)) <= 4 && Math.abs(((c >> 8) & 0xFF) - ((rgb >> 8) & 0xFF)) <= 4
                        && Math.abs((c & 0xFF) - (rgb & 0xFF)) <= 4) n++;
            }
        }
        return n / (double) ((long) img.getWidth() * img.getHeight());
    }

    /** Fração de pixels com luminância < 128 (texto incluído; a página do PDD é clara). */
    private static double darkShare(BufferedImage img) {
        long n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int c = img.getRGB(x, y);
                if ((((c >> 16) & 0xFF) * 299 + ((c >> 8) & 0xFF) * 587 + (c & 0xFF) * 114) / 1000 < 128) n++;
            }
        }
        return n / (double) ((long) img.getWidth() * img.getHeight());
    }

    /** Fração de pixels claros (luminância ≥ 225), exceto o cinza de ocultação. */
    private static double shareLight(BufferedImage img) {
        long n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int c = img.getRGB(x, y) & 0xFFFFFF;
                int l = (((c >> 16) & 0xFF) * 299 + ((c >> 8) & 0xFF) * 587 + (c & 0xFF) * 114) / 1000;
                if (l >= 225 && c != (VisualVerifier.OPAQUE & 0xFFFFFF) && c != (VisualVerifier.V2_PAGE & 0xFFFFFF)) n++;
            }
        }
        return n / (double) ((long) img.getWidth() * img.getHeight());
    }

    /** Páginas do PDF a 96 dpi (zoom de 100% na tela), gravadas como evidência. */
    private List<BufferedImage> renderPdf(Path pdf, String name) throws Exception {
        List<BufferedImage> pages = new ArrayList<>();
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf.toFile())) {
            org.apache.pdfbox.rendering.PDFRenderer r = new org.apache.pdfbox.rendering.PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage img = r.renderImageWithDPI(i, 96);
                pages.add(img);
                save(img, name + "-" + (i + 1));
            }
        }
        return pages;
    }

    /** Diferença média por canal entre a prévia e o PDF exportado renderizado na resolução da prévia. */
    private static double previewDiff(Path pdf, List<BufferedImage> preview, Path out) throws Exception {
        double total = 0;
        long count = 0;
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf.toFile())) {
            org.apache.pdfbox.rendering.PDFRenderer r = new org.apache.pdfbox.rendering.PDFRenderer(doc);
            for (int i = 0; i < preview.size(); i++) {
                BufferedImage pv = preview.get(i);
                float dpi = 72; // a resolução da prévia no PddPresenter (calcular pela largura dá 71,97 e desloca a suavização)
                BufferedImage ex = r.renderImageWithDPI(i, dpi);
                if (i == 0) {
                    ImageIO.write(pv, "png", out.resolve("S013-B5-previa-pagina-1.png").toFile());
                    ImageIO.write(ex, "png", out.resolve("S013-B5-exportado-mesma-resolucao-pagina-1.png").toFile());
                    int first = -1, last = -1;
                    for (int y = 0; y < Math.min(pv.getHeight(), ex.getHeight()); y++) {
                        for (int x = 0; x < Math.min(pv.getWidth(), ex.getWidth()); x++) {
                            if (pv.getRGB(x, y) != ex.getRGB(x, y)) {
                                if (first < 0) first = y;
                                last = y;
                                break;
                            }
                        }
                    }
                    System.out.println("  prévia × exportado, página 1: linhas diferentes de " + first + " a " + last + " (altura " + pv.getHeight() + ")");
                }
                int w = Math.min(pv.getWidth(), ex.getWidth()), h = Math.min(pv.getHeight(), ex.getHeight());
                if (Math.abs(pv.getWidth() - ex.getWidth()) > 2 || Math.abs(pv.getHeight() - ex.getHeight()) > 2) return 255;
                for (int y = (int) (h * 0.14); y < h; y++) { // o cabeçalho traz "Versão prévia" × "Versão vNNN" de propósito
                    for (int x = 0; x < w; x++) {
                        int a = pv.getRGB(x, y), b = ex.getRGB(x, y);
                        total += Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)) + Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)) + Math.abs((a & 0xFF) - (b & 0xFF));
                        count += 3;
                    }
                }
            }
        }
        return count == 0 ? 255 : total / count;
    }

    /** "Antes" e "depois" da mesma página lado a lado (só PDFs gerados com imagens já sanitizadas). */
    private void compose(BufferedImage left, BufferedImage right, String name) throws IOException {
        int gap = 24;
        BufferedImage img = new BufferedImage(left.getWidth() + gap + right.getWidth(), Math.max(left.getHeight(), right.getHeight()), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.drawImage(left, 0, 0, null);
        g.drawImage(right, left.getWidth() + gap, 0, null);
        g.setColor(new java.awt.Color(0x8A, 0x90, 0x99));
        g.fillRect(left.getWidth() + gap / 2 - 1, 0, 2, img.getHeight());
        g.dispose();
        save(img, name);
    }

    private void relaunchChrome(Path extension) throws Exception {
        try {
            if (page != null) page.close();
            if (harness != null) harness.close();
            if (panel != null) panel.close();
            if (cdp != null) cdp.close();
        } catch (RuntimeException ignored) {
            // encerramento
        }
        chrome.descendants().forEach(ProcessHandle::destroy);
        chrome.destroy();
        waitFor("Chrome encerrado", 20, () -> !chrome.isAlive());
        sleep(2500);
        waitFor("Studio sem navegador", 20, () -> !statusText().startsWith("Navegador: conectado"));
        testExtension = extension;
        newProfile();
        launchChrome(SITE_PAGE);
        log("Chrome relançado com a extensão " + extension.getFileName());
    }

    /** Captura da janela do PDD À FRENTE: antes, um clique real na linha selecionada (o Chrome não pode cobri-la). */
    private void shot(JDialog dlg, PddPanel p, String name) throws Exception {
        String sel = model(p).selected();
        if (sel != null) selectItem(p, sel);
        sleep(400);
        captureWindow(dlg, name);
    }

    // ============================================================================== apoio STUDIO-010

    private static PddPanel.Model model(PddPanel p) throws Exception {
        return edt(p::model);
    }

    private List<String> kinds(LogicalSession s) {
        return s.steps().stream().map(st -> st.actionType().name()).toList();
    }

    /**
     * A cor-sentinela #E6007E misturada com fundo claro: r alto, g baixo, b ENTRE g e r, com r−b ≈ 0,45·(r−g). Exclui as
     * franjas de ClearType de texto azul (b ≥ r) e o rosa dos marcadores #C2466D (r−b ≈ 0,69·(r−g)).
     */
    static int sentinelPixels(BufferedImage img) {
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int c = img.getRGB(x, y);
                int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                int rg = r - g;
                if (r > 170 && rg > 70 && b > g && r - b > 0.33 * rg && r - b < 0.58 * rg) n++;
            }
        }
        return n;
    }

    /** Menu da barra com submenu: "Automação" → "Gerar documentação" → "PDD visual…". */
    private void menuPath(String top, String sub, String item) throws Exception {
        focusStudio();
        javax.swing.JMenu m = edt(() -> {
            var bar = window.frame().getJMenuBar();
            for (int i = 0; i < bar.getMenuCount(); i++) if (top.equals(bar.getMenu(i).getText())) return bar.getMenu(i);
            return null;
        });
        click(m);
        waitFor("menu " + top, 5, () -> edt(m::isPopupMenuVisible));
        javax.swing.JMenu sm = (javax.swing.JMenu) waitValue("submenu " + sub, 5, () -> edt(() -> {
            for (Component c : m.getPopupMenu().getComponents()) if (c instanceof javax.swing.JMenu x && sub.equals(x.getText()) && x.isShowing()) return x;
            return null;
        }));
        click(sm);
        waitFor("submenu aberto", 5, () -> edt(sm::isPopupMenuVisible));
        javax.swing.JMenuItem it = (javax.swing.JMenuItem) waitValue("item " + item, 5, () -> edt(() -> {
            for (Component c : sm.getPopupMenu().getComponents()) if (c instanceof javax.swing.JMenuItem x && item.equals(x.getText()) && x.isShowing()) return x;
            return null;
        }));
        click(it);
        sleep(500);
    }

    /** Clique REAL na linha da lista do PDD. */
    /** 150%: o Studio foi minimizado para mexer no Chrome; volta (com as janelas dele) antes de clicar nelas. */
    private void restoreStudio(java.awt.Window front) throws Exception {
        if (edt(() -> (window.frame().getExtendedState() & java.awt.Frame.ICONIFIED) != 0)) {
            edt(() -> {
                window.frame().setExtendedState(java.awt.Frame.NORMAL);
                if (front != null) front.toFront();
                return null;
            });
            sleep(800);
        }
    }

    private void selectItem(PddPanel p, String key) throws Exception {
        restoreStudio(edt(() -> SwingUtilities.getWindowAncestor(p)));
        int index = edt(() -> {
            List<PddPanel.Item> items = p.model().items();
            for (int i = 0; i < items.size(); i++) if (items.get(i).key().equals(key)) return i;
            return -1;
        });
        java.awt.Rectangle r = edt(() -> {
            var list = p.itemsList();
            list.ensureIndexIsVisible(index);
            java.awt.Rectangle cell = list.getCellBounds(index, index);
            java.awt.Point o = list.getLocationOnScreen();
            return new java.awt.Rectangle(o.x + cell.x, o.y + cell.y, cell.width, cell.height);
        });
        guardForeground("seleção na lista do PDD");
        robot.mouseMove(r.x + Math.min(r.width / 2, 120), r.y + r.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        waitFor("item " + key + " selecionado", 5, () -> key.equals(model(p).selected()));
        sleep(300);
    }

    /** Escreve no campo (como colar) depois de um clique real nele. */
    private void setText(JTextComponent field, String text) throws Exception {
        click(field);
        edt(() -> {
            field.setText(text);
            return null;
        });
        sleep(150);
    }

    private void selectTab(PddPanel p, String titlePart) throws Exception {
        edt(() -> {
            var combo = p.tabsInput();
            for (int i = 0; i < combo.getItemCount(); i++) {
                if (combo.getItemAt(i).label().startsWith(titlePart)) {
                    combo.setSelectedIndex(i);
                    return null;
                }
            }
            combo.setSelectedIndex(0);
            return null;
        });
    }

    /**
     * Captura de um bloco como o usuário faria: deixa a aba do laboratório no estado da etapa, escolhe o bloco e a aba,
     * "Capturar tela…" no Studio e o GESTO no Chrome (Alt+Shift+K pelo teclado do sistema).
     */
    private boolean captureBlock(PddPanel p, String key, String url, String fill, String tabTitle) throws Exception {
        cdp.activate(labTarget);
        navigate(url);
        if (fill != null) page.eval(fill);
        edt(() -> {
            window.studio().visualPdd().reloadTabs();
            return null;
        });
        sleep(1200);
        selectItem(p, key);
        selectTab(p, tabTitle);
        click(edt(p::captureButton));
        waitFor("captura armada", 15, () -> "Captura armada".equals(model(p).bannerTitle()));
        log("  " + model(p).banner());
        focusChrome();
        guardForeground("atalho de captura");
        press(KeyEvent.VK_ALT, KeyEvent.VK_SHIFT, KeyEvent.VK_K);
        waitFor("captura concluída", 30, () -> !"Captura armada".equals(model(p).bannerTitle()));
        log("  " + model(p).bannerTitle() + ": " + model(p).banner());
        return model(p).hasImage() && ("Imagem sanitizada associada".equals(model(p).bannerTitle()) || model(p).bannerTitle().startsWith("Imagem associada"));
    }

    // ============================================================================== apoio de arquivos

    private static String dirText(Path dir) throws IOException {
        StringBuilder b = new StringBuilder();
        if (!Files.isDirectory(dir)) return "";
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.filter(Files::isRegularFile).filter(x -> !x.toString().endsWith(".lck")).toList()) { // .lck: trava vazia do log
                if (p.toString().endsWith(".nekkora")) b.append(zipText(p));
                else if (Files.size(p) < 20_000_000) b.append(new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
            }
        }
        return b.toString();
    }

    private static String zipText(Path file) throws IOException {
        StringBuilder b = new StringBuilder();
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(file.toFile())) {
            for (java.util.zip.ZipEntry e : java.util.Collections.list(z.entries())) {
                b.append(new String(z.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8)).append('\n');
            }
        }
        return b.toString();
    }

    private static String formatOf(Path file) throws IOException {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"formatVersion\"\\s*:\\s*\"([^\"]+)\"").matcher(zipText(file));
        return m.find() ? m.group(1) : "";
    }

    private Automation active() throws Exception {
        return edt(() -> window.studio().state().activeAutomation().orElseThrow());
    }



    /** Diálogo de arquivos do sistema (JFileChooser) aberto, ou null. */
    private Window fileChooser() throws Exception {
        return edt(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof javax.swing.JDialog dl && dl.isShowing() && find(dl, javax.swing.JFileChooser.class, x -> true) != null) return w;
            }
            return null;
        });
    }

    private void waitChooser(String title) throws Exception {
        waitFor("diálogo " + title, 15, () -> fileChooser() != null && title.equals(((javax.swing.JDialog) fileChooser()).getTitle()));
        sleep(500);
    }

    private void typePath(Path p) throws Exception {
        Window w = fileChooser();
        JTextComponent field = edt(() -> find(w, JTextComponent.class, x -> x.isShowing() && x.isEditable()));
        click(field);
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_A);
        typeAscii(p.toString());
        sleep(300);
        press(KeyEvent.VK_ENTER);
        waitFor("diálogo de arquivo fechado", 15, () -> fileChooser() == null);
    }

    private void openByDialog(Path file) throws Exception {
        focusStudio();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_O);
        waitChooser("Abrir projeto");
        typePath(file);
        waitFor("projeto aberto " + file.getFileName(), 30, () -> edt(() -> window.studio().state().activeAutomation()
                .flatMap(x -> window.studio().state().project(x.id())).flatMap(com.nekkora.studio.app.workspace.ProjectState::path).map(file::equals)
                .orElse(false)));
        sleep(500);
    }

    private void menu(String top, String item) throws Exception {
        focusStudio();
        javax.swing.JMenu m = edt(() -> {
            var bar = window.frame().getJMenuBar();
            for (int i = 0; i < bar.getMenuCount(); i++) if (top.equals(bar.getMenu(i).getText())) return bar.getMenu(i);
            return null;
        });
        click(m);
        waitFor("menu " + top, 5, () -> edt(m::isPopupMenuVisible));
        javax.swing.JMenuItem it = (javax.swing.JMenuItem) waitValue("item " + item, 5, () -> edt(() -> {
            for (Component c : m.getPopupMenu().getComponents()) {
                if (c instanceof javax.swing.JMenuItem x && item.equals(x.getText()) && x.isShowing()) return x;
            }
            return null;
        }));
        click(it);
        sleep(300);
    }

    // ============================================================================== apoio STUDIO-009

    /**
     * Proteção da máquina (achado da homologação G8): antes de cada clique, tecla ou captura, a janela em primeiro plano
     * tem de ser do Studio (esta JVM) ou do Chrome de teste. Outro programa na frente (mensageiro, notificação aberta,
     * outra janela da usuária) → NADA é clicado, teclado nem capturado e a homologação para. Só o nome do executável vai
     * ao log — nunca o título da janela.
     */
    private Process foreground;
    private java.io.BufferedWriter fgIn;
    private java.io.BufferedReader fgOut;

    private void guardForeground(String what) {
        try {
            long end = System.currentTimeMillis() + 3000;
            String seen = "";
            while (System.currentTimeMillis() < end) {
                String[] r = foregroundProcess();
                long pid = Long.parseLong(r[0]);
                if (pid == ProcessHandle.current().pid() || chrome != null && (pid == chrome.pid()
                        || r[1].equalsIgnoreCase(chrome.info().command().orElse("-")))) {
                    return;
                }
                seen = r[1].isEmpty() ? "pid " + pid : Path.of(r[1]).getFileName().toString();
                sleep(200);
            }
            throw new IllegalStateException("outro programa em primeiro plano (" + seen + ") antes de: " + what
                    + " — homologação interrompida SEM clicar, teclar ou capturar");
        } catch (IOException e) {
            throw new IllegalStateException("verificação do primeiro plano indisponível: " + e.getMessage());
        }
    }

    private String[] foregroundProcess() throws IOException {
        return windowProcess("?");
    }

    /**
     * Antes de um clique que muda o foco (barra de endereço do Chrome de teste): a janela SOB o ponto tem de ser do
     * Chrome de teste ou desta JVM — nunca uma janela da usuária (outro Chrome, outro programa).
     */
    private void guardPoint(int x, int y, String what) {
        guardPoint(x, y, what, false);
    }

    /** {@code chromeOnly}: o clique é para a página/barra do Chrome de teste — o próprio Studio por cima também não serve. */
    private void guardPoint(int x, int y, String what, boolean chromeOnly) {
        try {
            // coordenadas do Java (lógicas, escaladas por sun.java2d.uiScale) → px físicos do Windows (escala do sistema 100%)
            String[] r = windowProcess("pt " + Math.round(x * sc) + " " + Math.round(y * sc));
            long pid = Long.parseLong(r[0]);
            boolean ours = !chromeOnly && pid == ProcessHandle.current().pid() || chrome != null && (pid == chrome.pid()
                    || r[1].equalsIgnoreCase(chrome.info().command().orElse("-")));
            if (!ours) throw new IllegalStateException("janela de outro programa (" + (r[1].isEmpty() ? "pid " + pid : Path.of(r[1]).getFileName())
                    + ") sob o ponto de " + what + " — homologação interrompida SEM clicar");
        } catch (IOException e) {
            throw new IllegalStateException("verificação da janela sob o ponto indisponível: " + e.getMessage());
        }
    }

    private String[] windowProcess(String query) throws IOException {
        if (foreground == null || !foreground.isAlive()) {
            Path script = Files.createTempFile("nk009-fg", ".ps1");
            Files.writeString(script, String.join(System.lineSeparator(),
                    "Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public static class NkFg {"
                            + " [DllImport(\"user32.dll\")] public static extern IntPtr GetForegroundWindow();"
                            + " [DllImport(\"user32.dll\")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);"
                            + " [StructLayout(LayoutKind.Sequential)] public struct P { public int X; public int Y; }"
                            + " [DllImport(\"user32.dll\")] public static extern IntPtr WindowFromPoint(P p);"
                            + " [DllImport(\"user32.dll\")] public static extern IntPtr GetAncestor(IntPtr h, uint f); }'",
                    "while ($true) { $l = [Console]::In.ReadLine(); if ($l -eq $null) { break }",
                    "  $h = [NkFg]::GetForegroundWindow()",
                    "  if ($l.StartsWith('pt ')) { $q = $l.Split(' '); $pt = New-Object NkFg+P; $pt.X = [int]$q[1]; $pt.Y = [int]$q[2]; $h = [NkFg]::GetAncestor([NkFg]::WindowFromPoint($pt), 2) }",
                    "  $p = [uint32]0; [void][NkFg]::GetWindowThreadProcessId($h, [ref]$p)",
                    "  $path = ''; try { $path = (Get-Process -Id $p -ErrorAction Stop).Path } catch { }",
                    "  [Console]::Out.WriteLine(\"$p|$path\"); [Console]::Out.Flush() }"), StandardCharsets.UTF_8);
            foreground = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString()).start();
            fgIn = new java.io.BufferedWriter(new java.io.OutputStreamWriter(foreground.getOutputStream(), StandardCharsets.UTF_8));
            fgOut = new java.io.BufferedReader(new java.io.InputStreamReader(foreground.getInputStream(), StandardCharsets.UTF_8));
        }
        fgIn.write(query);
        fgIn.newLine();
        fgIn.flush();
        String line = fgOut.readLine();
        if (line == null) throw new IOException("sem resposta");
        int bar = line.indexOf('|');
        return new String[] {line.substring(0, bar).trim(), line.substring(bar + 1).trim()};
    }








    private static Path hostDataDir() {
        String d = System.getenv("NEKKORA_DATA_DIR");
        return d == null ? Path.of(System.getProperty("java.io.tmpdir"), "nekkora-s009-host") : Path.of(d);
    }


    private AutomationOverview overview(Automation a) {
        return window.studio().view().overview(a.id()).orElseThrow();
    }

    private List<String> lines(Automation a) throws Exception {
        return edt(() -> {
            List<String> out = new ArrayList<>();
            for (LogicalSessionView v : overview(a).sessionViews()) for (AutomationStep s : v.itemsInOrder()) out.add(v.labelOf(s));
            return out;
        });
    }

    private String domainText(Automation a) throws Exception {
        return edt(() -> {
            StringBuilder b = new StringBuilder(a.name()).append(a.description());
            for (LogicalSession s : a.sessions()) {
                b.append(s.name()).append(s.description()).append(s.notes());
                for (AutomationStep st : s.steps()) {
                    b.append(st.name()).append(st.description()).append(st.parameters().value().map(v -> v.display()).orElse(""))
                            .append(st.parameters().option().map(o -> o.label() + o.value()).orElse(""));
                    st.comments().forEach(c -> b.append(c.text()));
                    st.baseline().ifPresent(bl -> b.append(bl.name()).append(bl.description()));
                }
            }
            return b.toString();
        });
    }

    private void selectSession(Automation a, LogicalSession s) throws Exception {
        edt(() -> {
            window.studio().controller().selectSession(a.id(), s.id());
            return null;
        });
        sleep(500);
    }

    /** Botão do painel de propriedades pelo texto ou nome acessível (clicado com o mouse real). */
    private AbstractButton inspectorButton(String name) throws Exception {
        AbstractButton b = edt(() -> find(window.studio().view().inspector(), AbstractButton.class, x -> x.isShowing() && x.isEnabled()
                && (name.equals(x.getText()) || name.equals(x.getAccessibleContext().getAccessibleName()))));
        if (b == null) throw new IllegalStateException("botão do painel não encontrado: " + name);
        return b;
    }

    /** Clique REAL na linha da lista de etapas da sessão (dá o foco à lista, como o usuário). */
    private void clickRow(LogicalSession s, int index) throws Exception {
        focusStudio();
        Automation a = edt(() -> window.studio().state().activeAutomation().orElseThrow());
        LogicalSessionView v = edt(() -> overview(a).sessionView(s.id()).orElseThrow());
        Rectangle r = edt(() -> {
            v.stepList().ensureIndexIsVisible(index);
            Rectangle cell = v.stepList().getCellBounds(index, index);
            java.awt.Point p = v.stepList().getLocationOnScreen();
            return new Rectangle(p.x + cell.x, p.y + cell.y, cell.width, cell.height);
        });
        guardForeground("linha da sessão");
        robot.mouseMove(r.x + Math.min(r.width / 2, 200), r.y + r.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        waitFor("linha selecionada", 10, () -> edt(() -> v.stepList().getSelectedIndex() == index && v.stepList().isFocusOwner()));
        sleep(300);
    }

    /** Clique REAL numa aba do editor de etapa. */
    private void clickTab(StepDetailsForm form, int index) throws Exception {
        Rectangle r = edt(() -> {
            Rectangle tab = form.tabs().getBoundsAt(index);
            java.awt.Point p = form.tabs().getLocationOnScreen();
            return new Rectangle(p.x + tab.x, p.y + tab.y, tab.width, tab.height);
        });
        guardForeground("aba do editor");
        robot.mouseMove(r.x + r.width / 2, r.y + r.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        waitFor("aba " + index, 5, () -> edt(() -> form.tabs().getSelectedIndex() == index));
        sleep(300);
    }

    /** Foco na área de trabalho do Studio (fora de campos de texto) para os atalhos da janela. */
    private void focusStudioWorkspace() throws Exception {
        edt(() -> {
            Automation a = window.studio().state().activeAutomation().orElseThrow();
            window.studio().controller().selectAutomation(a.id());
            return null;
        });
        focusStudio();
        sleep(300);
    }

    // ============================================================================== navegador

    private void connect() throws Exception {
        Cdp.Target harnessTarget = cdp.open("chrome-extension://" + extId + "/src/ui/harness.html");
        harness = cdp.page(harnessTarget);
        waitFor("harness da extensão", 20, () -> harness.eval("!!(window.nekkora && window.nekkora.call)") == Boolean.TRUE);
        labTab = ((Number) waitValue("aba do laboratório", 20,
                () -> harness.eval("chrome.tabs.query({}).then(t => (t.find(x => (x.url || '').startsWith('" + SITE_ORIGIN + "')) || {}).id || null)"))).intValue();
        Cdp.Target panelTarget = cdp.open("chrome-extension://" + extId + "/src/ui/panel.html?tabId=" + labTab);
        panel = cdp.page(panelTarget);
        waitFor("painel", 20, () -> panel.eval("!!document.getElementById('studio-enabled')") == Boolean.TRUE);
        waitFor("painel inicializado", 20, () -> !"verificando…".equals(panel.eval("document.getElementById('host-status').textContent")));
        panel.eval("document.getElementById('studio-enabled').click()");
        waitFor("Studio conectado", 30, () -> statusText().startsWith("Navegador: conectado"));
        check("Conexão autenticada Studio ⇄ host ⇄ extensão", statusText().startsWith("Navegador: conectado (Nekkora Extension "));
        cdp.activate(labTarget);
        sleep(500);
    }

    /** Navegação de preparação do cenário (endereço digitado como o usuário faria). */
    private void navigate(String url) throws Exception {
        page.send("Page.navigate", Json.obj().put("url", url).build());
        waitFor("página " + url, 20, () -> "complete".equals(page.eval("document.readyState")) && String.valueOf(page.eval("location.href")).startsWith(url.split("\\?")[0]));
        sleep(1500);
    }

    /** Durante a gravação: o usuário digita o endereço na barra (Ctrl+L) e tecla Enter. */
    private void navigateByUser(String url) throws Exception {
        cdp.activate(labTarget);
        sleep(300);
        focusChrome(); // foco na janela do Chrome (barra de endereço)
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_L);
        typeAscii(url);
        press(KeyEvent.VK_ENTER);
        String path = url.split("\\?")[0];
        waitPage(path.substring(path.lastIndexOf('/') + 1)); // sem a consulta (?externo=…)
        sleep(2500);
    }

    private boolean waitPage(String suffix) throws Exception {
        try {
            waitFor("página " + suffix, 20, () -> String.valueOf(page.eval("location.pathname")).endsWith(suffix) && "complete".equals(page.eval("document.readyState")));
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /**
     * Clique REAL do mouse no centro do elemento: rola o elemento para a vista, converte o retângulo da página em
     * coordenadas de tela (moldura do Chrome incluída) e clica com o Robot.
     */
    private void clickInPage(String elementExpr, String... frameExpr) throws Exception {
        cdp.activate(labTarget);
        focusChrome(); // a janela do Studio pode estar à frente cobrindo parte da página
        String frame = frameExpr.length == 0 ? null : frameExpr[0];
        page.eval("(" + elementExpr + ").scrollIntoView({block: 'center', inline: 'nearest'})");
        sleep(250);
        String js = "(() => { const e = " + elementExpr + "; const r = e.getBoundingClientRect();"
                + (frame == null ? " const fx = 0, fy = 0;" : " const f = (" + frame + ").getBoundingClientRect(); const fx = f.left + (" + frame + ").clientLeft, fy = f.top + (" + frame + ").clientTop;")
                + " const bx = (window.outerWidth - window.innerWidth) / 2; const by = window.outerHeight - window.innerHeight - bx;"
                + " return [window.screenX + bx + fx + r.left + r.width / 2, window.screenY + by + fy + r.top + r.height / 2]; })()";
        @SuppressWarnings("unchecked")
        List<Object> xy = (List<Object>) page.eval(js);
        guardForeground("clique na página");
        int x = (int) Math.round(((Number) xy.get(0)).doubleValue());
        int y = (int) Math.round(((Number) xy.get(1)).doubleValue());
        guardPoint((int) Math.round(x / sc), (int) Math.round(y / sc), "clique na página", true);
        robot.mouseMove((int) Math.round(x / sc), (int) Math.round(y / sc)); // página: px físicos → coordenadas do Robot
        sleep(120);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        sleep(500);
    }

    /** Dá o foco à janela do Chrome com um clique na barra de endereço (fora da página; nenhuma aba é trocada). */
    private void focusChrome() {
        if (sc > 1) { // em 150% Studio e Chrome não cabem lado a lado: o Studio vai para trás antes de mexer no Chrome
            try {
                edt(() -> { // toBack() não basta no Windows: minimizado (as janelas do PDD, que são dele, vão junto)
                    window.frame().setExtendedState(java.awt.Frame.ICONIFIED);
                    return null;
                });
                sleep(500);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            sleep(300);
        }
        guardPoint((int) Math.round((CHROME_X + 400) / sc), (int) Math.round(63 / sc), "foco no Chrome de teste", true);
        robot.mouseMove((int) Math.round((CHROME_X + 400) / sc), (int) Math.round(63 / sc));
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        sleep(250);
    }

    /** Digitação REAL (teclas do sistema operacional), letras e dígitos. */
    private void typeText(String text) {
        for (char c : text.toCharArray()) {
            int code = KeyEvent.getExtendedKeyCodeForChar(Character.toLowerCase(c));
            if (Character.isUpperCase(c)) {
                robot.keyPress(KeyEvent.VK_SHIFT);
            }
            robot.keyPress(code);
            robot.keyRelease(code);
            if (Character.isUpperCase(c)) {
                robot.keyRelease(KeyEvent.VK_SHIFT);
            }
        }
        robot.waitForIdle();
        sleep(300);
    }

    /** Endereço na barra: colado da área de transferência (caracteres especiais da URL não dependem do layout do teclado). */
    private void typeAscii(String text) throws Exception {
        SwingUtilities.invokeAndWait(() -> java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(text), null));
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_V);
    }

    private void prepareEnvironment() throws Exception {
        Path hostJar = ext.resolve("java-core/native-host/target/nekkora-native-host.jar");
        run(List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ext.resolve("native-host/scripts/install-host.ps1").toString(), "-SkipBuild"));
        log("Native Host instalado pelo script oficial (install-host.ps1)");
        extId = run(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", hostJar.toString(),
                "com.nekkora.extension.host.tools.ExtensionId", ext.resolve("chrome-extension/manifest.json").toString())).trim();
        labServer = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar",
                ext.resolve("java-core/lab-server/target/nekkora-lab-server.jar").toString(), String.valueOf(LAB_PORT), ext.resolve("test-lab/pages").toString())
                .redirectErrorStream(true).redirectOutput(new File(out, "lab-server.log")).start();
        newProfile();
        testExtension = out.toPath().resolve("test-extension");
        copyExtension(ext.resolve("chrome-extension"), testExtension, null); // a extensão desta branch, como ela é (0.6.0)
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        waitFor("laboratório respondendo", 20, () -> http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(PAGE + "?v=base")).build(),
                java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode() == 200);
    }

    /** Perfil temporário novo, sem o gerenciador de senhas do Chrome (o balão "Salvar senha?" não aparece nas evidências). */
    private void newProfile() throws IOException {
        profile = Files.createTempDirectory("nekkora-s013-chrome");
        Files.createDirectories(profile.resolve("Default"));
        Files.writeString(profile.resolve("Default").resolve("Preferences"),
                "{\"credentials_enable_service\": false, \"profile\": {\"password_manager_enabled\": false}}");
    }

    /**
     * Cópia de homologação: idêntica, exceto pelo site de teste declarado e, na fase "antes", pela versão declarada
     * ({@code version} = 0.4.0: o Studio trata como extensão anterior e não pede a v2).
     */
    private static void copyExtension(Path from, Path to, String version) throws IOException {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path rel = from.relativize(p);
                if (rel.startsWith("test") || rel.startsWith("node_modules")) continue;
                Path dst = to.resolve(rel.toString());
                if (Files.isDirectory(p)) Files.createDirectories(dst);
                else Files.copy(p, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Path manifest = to.resolve("manifest.json");
        Map<String, Object> m = Json.parseObject(Files.readString(manifest));
        List<Object> hosts = new ArrayList<>(Json.array(m, "host_permissions"));
        hosts.add("http://site-a.nekkora.test/*");
        m.put("host_permissions", hosts);
        if (version != null) m.put("version", version);
        Files.writeString(manifest, Json.write(m));
    }

    private void launchChrome(String url) throws Exception {
        Path chromeExe;
        try (Stream<Path> s = Files.walk(ext.resolve(".tools/chrome-for-testing"), 4)) {
            chromeExe = s.filter(p -> p.getFileName().toString().equals("chrome.exe")).findFirst().orElseThrow();
        }
        int cdpPort;
        try (java.net.ServerSocket free = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            cdpPort = free.getLocalPort();
        }
        Files.createDirectories(hostDataDir());
        ProcessBuilder chromeBuilder = new ProcessBuilder(chromeExe.toString(), "--remote-debugging-port=" + cdpPort, "--user-data-dir=" + profile,
                "--disable-features=NetworkServiceSandbox", "--load-extension=" + testExtension, "--no-first-run", "--no-default-browser-check",
                "--disable-search-engine-choice-screen", "--lang=pt-BR", "--window-position=" + CHROME_X + ",0", "--window-size=1040,1000",
                "--host-resolver-rules=MAP site-a.nekkora.test 127.0.0.1,MAP site-b.nekkora.test 127.0.0.1,MAP site-x.nekkora.test 127.0.0.1", "about:blank")
                .redirectErrorStream(true).redirectOutput(new File(out, "chrome.log"));
        chromeBuilder.environment().put("NEKKORA_DATA_DIR", hostDataDir().toString()); // herdado pelo Native Host
        chrome = chromeBuilder.start();
        cdp = new Cdp(cdpPort);
        cdpPortUsed = cdpPort;
        waitFor("Chrome com DevTools", 30, () -> {
            try {
                return !cdp.targets().isEmpty();
            } catch (Exception e) {
                return false;
            }
        });
        sleep(1500);
        labTarget = cdp.open(url);
        page = cdp.page(labTarget);
        waitFor("página inicial", 20, () -> "complete".equals(page.eval("document.readyState")));
        log("Chrome for Testing: " + chromeExe.getParent().getParent().getFileName());
        sleep(1500);
    }

    /** "Acesso ao site" do Chrome (chrome://extensions) — sem automatizar o diálogo de permissão. */
    private void siteAccess(String pattern, boolean granted) throws Exception {
        Cdp.Target t = cdp.open("chrome://extensions/?id=" + extId);
        try (Cdp.Page p = cdp.page(t)) {
            waitFor("chrome://extensions", 15, () -> p.eval("!!(window.chrome && chrome.developerPrivate)") == Boolean.TRUE);
            p.eval("new Promise((ok, ko) => chrome.developerPrivate." + (granted ? "addHostPermission" : "removeHostPermission") + "(" + Json.write(extId) + ", "
                    + Json.write(pattern) + ", () => chrome.runtime.lastError ? ko(chrome.runtime.lastError.message) : ok(true)))");
        }
        cdp.close(t);
        log("Acesso ao site " + pattern + " → " + (granted ? "concedido" : "revogado") + " pelo controle do Chrome");
        cdp.activate(labTarget);
    }

    private String hostLogs() throws IOException {
        StringBuilder b = new StringBuilder();
        Path dir = hostDataDir().resolve("logs");
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                for (Path f : s.filter(p -> p.toString().endsWith(".log")).toList()) {
                    try {
                        b.append(Files.readString(f, StandardCharsets.ISO_8859_1));
                    } catch (IOException e) {
                        // em uso
                    }
                }
            }
        }
        return b.toString();
    }

    // ============================================================================== Studio

    private void createAutomation() throws Exception {
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_N);
        NekkoraDialog d = waitDialog("Nova automação");
        typeIntoFocused("Consulta de registros");
        press(KeyEvent.VK_ENTER);
        waitClosed(d);
        newSession("Autenticação");
        newSession("Consulta");
        newSession("Resultado");
    }

    private LogicalSession newSession(String name) throws Exception {
        focusStudio();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_N);
        NekkoraDialog d = waitDialog("Nova sessão");
        typeIntoFocused(name);
        press(KeyEvent.VK_ENTER);
        waitClosed(d);
        return edt(() -> {
            Automation a = window.studio().state().activeAutomation().orElseThrow();
            return a.sessions().get(a.sessions().size() - 1);
        });
    }

    private void startRecordingOn(String tabText, RecordingMode mode) throws Exception {
        focusStudio();
        click(recordButton());
        NekkoraDialog d = waitDialog("Iniciar gravação");
        waitFor("abas carregadas", 15, () -> edt(() -> radios(d).stream().anyMatch(b -> b.getText().contains(tabText) && b.isEnabled())));
        click(edt(() -> radios(d).stream().filter(b -> b.getText().contains(tabText) && b.isEnabled()).findFirst().orElseThrow()));
        NekkoraRadioButton m = edt(() -> find(d, NekkoraRadioButton.class, r -> mode.label().equals(r.getText())));
        if (m != null) {
            click(m);
        }
        clickButtonWithText(d, "Iniciar gravação");
        waitClosed(d);
        waitFor("gravando com etapas", 30, () -> stateIs(RecorderState.RECORDING) && edt(() -> window.studio().recorder().snapshot().steps().enabled()));
        sleep(800);
    }

    /** O Floating Recorder fica sobre o Studio (à esquerda), sem cobrir a página do Chrome onde o mouse vai clicar. */
    private void moveFloatingAway() throws Exception {
        edt(() -> {
            floating().setLocation(420, 470);
            return null;
        });
        sleep(400);
    }

    private void selectStep(Automation a, LogicalSession s, AutomationStep st) throws Exception {
        edt(() -> {
            window.studio().service().select(new Selection.OfStep(a.id(), s.id(), st.id()));
            return null;
        });
        sleep(600);
    }

    private List<String> names(LogicalSession s) throws Exception {
        return edt(() -> s.steps().stream().map(AutomationStep::name).toList());
    }

    private NekkoraToolbarButton recordButton() throws Exception {
        return edt(() -> find(window.frame().getContentPane(), NekkoraToolbarButton.class,
                b -> b.getAction() != null && StudioActions.RECORD.equals(b.getAction().getValue(com.nekkora.ui.action.NekkoraAction.ID))));
    }

    private String statusText() throws Exception {
        return edt(() -> window.studio().recorderPresenter().statusItem().getText());
    }

    private FloatingRecorderWindow floating() throws Exception {
        return edt(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof FloatingRecorderWindow f) return f;
            }
            return null;
        });
    }

    private boolean stateIs(RecorderState s) throws Exception {
        return edt(() -> window.studio().recorder().snapshot().state() == s);
    }

    private void waitState(RecorderState s) throws Exception {
        waitFor("estado " + s, 40, () -> stateIs(s));
    }

    private NekkoraDialog waitDialog(String title) throws Exception {
        long end = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < end) {
            NekkoraDialog d = edt(() -> {
                for (Window w : Window.getWindows()) {
                    if (w instanceof NekkoraDialog nd && nd.isShowing() && title.equals(nd.getTitle())) return nd;
                }
                return null;
            });
            if (d != null) {
                sleep(300);
                return d;
            }
            sleep(100);
        }
        throw new IllegalStateException("diálogo não apareceu: " + title);
    }

    private void waitClosed(Window d) throws Exception {
        waitFor("fechar diálogo", 15, () -> edt(() -> !d.isShowing()));
        sleep(250);
    }

    private static List<NekkoraRadioButton> radios(Container root) {
        List<NekkoraRadioButton> out = new ArrayList<>();
        collectRadios(root, out);
        return out;
    }

    private static void collectRadios(Component c, List<NekkoraRadioButton> out) {
        if (c instanceof NekkoraRadioButton r && r.getText() != null && r.getText().contains("—")) out.add(r);
        if (c instanceof Container ct) for (Component ch : ct.getComponents()) collectRadios(ch, out);
    }

    private void clickButtonWithText(Container root, String text) throws Exception {
        AbstractButton b = edt(() -> find(root, AbstractButton.class, x -> text.equals(x.getText()) && x.isShowing()));
        if (b == null) throw new IllegalStateException("botão não encontrado: " + text);
        click(b);
    }

    private void click(Component c) throws Exception {
        edt(() -> {
            if (c instanceof javax.swing.JComponent jc) jc.scrollRectToVisible(new Rectangle(0, 0, jc.getWidth(), jc.getHeight())); // painel rolável
            return null;
        });
        robot.waitForIdle();
        sleep(150);
        Rectangle r = edt(() -> new Rectangle(c.getLocationOnScreen(), c.getSize()));
        Rectangle win = edt(() -> {
            Window w = c instanceof Window x ? x : SwingUtilities.getWindowAncestor(c);
            return w == null ? new Rectangle() : w.getBounds();
        });
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        if (!screen.contains(r.x + r.width / 2, r.y + r.height / 2)) {
            throw new IllegalStateException("alvo do clique fora da tela (" + r + " em " + screen + ") — homologação interrompida SEM clicar: "
                    + (c instanceof AbstractButton b ? b.getText() : c.getClass().getSimpleName()));
        }
        if (!win.contains(r.x + r.width / 2, r.y + r.height / 2)) {
            String vpInfo = edt(() -> {
                javax.swing.JViewport vp = (javax.swing.JViewport) SwingUtilities.getAncestorOfClass(javax.swing.JViewport.class, c);
                return vp == null ? "sem rolagem" : "viewport " + vp.getSize() + " posição " + vp.getViewPosition() + " vista " + vp.getView().getSize()
                        + " preferida " + vp.getView().getPreferredSize() + " válida " + vp.getView().isValid();
            });
            throw new IllegalStateException("alvo do clique fora da própria janela (" + r + " em " + win + "; " + vpInfo + ") — homologação interrompida SEM clicar: "
                    + (c instanceof AbstractButton b ? b.getText() : c.getClass().getSimpleName()));
        }
        guardPoint(r.x + r.width / 2, r.y + r.height / 2, "clique");
        guardForeground("clique");
        robot.mouseMove(r.x + r.width / 2, r.y + r.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        sleep(250);
    }

    private void focusStudio() throws Exception {
        edt(() -> {
            if ((window.frame().getExtendedState() & java.awt.Frame.ICONIFIED) != 0) window.frame().setExtendedState(java.awt.Frame.NORMAL);
            window.frame().toFront();
            return null;
        });
        Rectangle b = edt(() -> window.frame().getBounds());
        sleep(300);
        guardForeground("foco no Studio"); // o Studio de teste TEM de estar à frente: nunca clicar numa janela da usuária
        robot.mouseMove(b.x + b.width / 2, b.y + 12);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        waitFor("Studio com foco", 10, () -> edt(() -> window.frame().isFocused() || window.frame().isActive()));
        sleep(300);
    }

    private void typeIntoFocused(String text) throws Exception {
        edt(() -> {
            Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (!(c instanceof JTextComponent t)) throw new IllegalStateException("foco não está em um campo de texto: " + c);
            t.setText(text);
            return null;
        });
        sleep(120);
    }

    private void press(int... keys) {
        guardForeground("tecla");
        for (int k : keys) robot.keyPress(k);
        for (int i = keys.length - 1; i >= 0; i--) robot.keyRelease(keys[i]);
        robot.waitForIdle();
        sleep(200);
    }

    /** Captura SÓ da janela do Studio (com o Floating Recorder por cima): nada da área de trabalho. */
    private void captureStudio(String name) throws Exception {
        robot.waitForIdle();
        sleep(400);
        guardForeground("captura " + name);
        Rectangle studio = edt(() -> window.frame().getBounds());
        Rectangle chromeArea = new Rectangle((int) (CHROME_X / sc), 0, (int) (1040 / sc), (int) (1000 / sc));
        Rectangle both = studio.union(chromeArea).intersection(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
        BufferedImage img = robot.createScreenCapture(both);
        if (!maskFields(img, both)) {
            log("captura " + name + " OMITIDA: não foi possível mascarar os campos da página");
            return;
        }
        save(img, name);
    }

    /**
     * Nenhum valor digitado em captura (nem fictício): cobre o retângulo de TODO campo editável da página do
     * laboratório — documento, iframes da mesma origem e shadow roots abertos — antes de gravar a imagem.
     */
    private boolean maskFields(BufferedImage img, Rectangle area) {
        if (page == null) return true;
        Object r = null;
        for (int attempt = 0; attempt < 3 && r == null; attempt++) {
            try {
                r = page.eval("(() => { const out = []; const bx = (window.outerWidth - window.innerWidth) / 2;"
                        + " const by = window.outerHeight - window.innerHeight - bx;"
                        + " const walk = (root, ox, oy) => { for (const e of root.querySelectorAll('input, textarea, [contenteditable=\"true\"], [contenteditable=\"\"], *')) {"
                        + "   if (e.shadowRoot) walk(e.shadowRoot, ox, oy);"
                        + "   if (e.tagName === 'IFRAME') { try { const d = e.contentDocument; if (d) { const fr = e.getBoundingClientRect(); walk(d, ox + fr.left + e.clientLeft, oy + fr.top + e.clientTop); } } catch (x) {} }"
                        + "   const t = (e.type || '').toLowerCase();"
                        + "   if (e.matches('textarea, [contenteditable=\"true\"], [contenteditable=\"\"]') || e.tagName === 'INPUT' && !['button','submit','reset','checkbox','radio','image','hidden'].includes(t)) {"
                        + "     const b = e.getBoundingClientRect(); if (b.width > 0 && b.height > 0) out.push([window.screenX + bx + ox + b.left, window.screenY + by + oy + b.top, b.width, b.height]); } } };"
                        + " walk(document, 0, 0); return JSON.stringify(out); })()");
            } catch (Exception e) {
                sleep(400);
            }
        }
        if (r == null) return false;
        double fx = img.getWidth() / (double) area.width;
        double fy = img.getHeight() / (double) area.height;
        java.awt.Graphics2D g = img.createGraphics();
        // só onde a PÁGINA aparece: as janelas do Studio (sempre à frente do Chrome nas capturas) ficam intactas
        java.awt.geom.Area clip = new java.awt.geom.Area(new Rectangle(0, 0, img.getWidth(), img.getHeight()));
        try {
            for (Rectangle w : edt(() -> {
                List<Rectangle> ws = new ArrayList<>();
                for (Window x : Window.getWindows()) if (x.isShowing()) ws.add(x.getBounds());
                return ws;
            })) {
                clip.subtract(new java.awt.geom.Area(new Rectangle((int) ((w.x - area.x) * fx), (int) ((w.y - area.y) * fy), (int) Math.ceil(w.width * fx),
                        (int) Math.ceil(w.height * fy))));
            }
        } catch (Exception e) {
            return false;
        }
        g.setClip(clip);
        g.setColor(new java.awt.Color(0x9A, 0xA0, 0xA6));
        for (Object o : Json.parse(String.valueOf(r)) instanceof List<?> l ? l : List.of()) {
            List<?> q = (List<?>) o;
            double x = ((Number) q.get(0)).doubleValue() / sc - area.x;
            double y = ((Number) q.get(1)).doubleValue() / sc - area.y;
            g.fillRect((int) Math.floor(x * fx) + 2, (int) Math.floor(y * fy) + 2, (int) Math.ceil(((Number) q.get(2)).doubleValue() / sc * fx) - 4,
                    (int) Math.ceil(((Number) q.get(3)).doubleValue() / sc * fy) - 4);
        }
        g.dispose();
        return true;
    }

    private void captureWindow(Window w, String name) throws Exception {
        robot.waitForIdle();
        sleep(250);
        // só a área visível da janela: no Windows 11 os limites incluem a borda invisível de redimensionamento (com o que está atrás)
        save(robot.createScreenCapture(edt(() -> {
            Rectangle b = w.getBounds();
            java.awt.Insets in = w.getInsets();
            int top = w instanceof java.awt.Frame f && f.isUndecorated() || w instanceof java.awt.Dialog d && d.isUndecorated() ? in.top : 1;
            return new Rectangle(b.x + in.left, b.y + top, b.width - in.left - in.right, b.height - top - in.bottom);
        })), name);
    }

    private void save(BufferedImage img, String name) throws IOException {
        File f = new File(out, "S013-" + name + ".png");
        ImageIO.write(img, "png", f);
        log("captura " + f.getName() + " (" + img.getWidth() + "x" + img.getHeight() + ")");
    }

    private static <T extends Component> T find(Container root, Class<T> type, java.util.function.Predicate<T> p) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c) && p.test(type.cast(c))) return type.cast(c);
            if (c instanceof Container ct) {
                T t = find(ct, type, p);
                if (t != null) return t;
            }
        }
        return null;
    }

    private static String texts(Container root) {
        StringBuilder b = new StringBuilder();
        collectTexts(root, b);
        return b.toString();
    }

    private static void collectTexts(Component c, StringBuilder b) {
        if (c instanceof javax.swing.JLabel l && l.getText() != null) b.append(l.getText()).append('\n');
        if (c instanceof AbstractButton ab && ab.getText() != null) b.append(ab.getText()).append('\n');
        if (c instanceof JTextComponent t && t.getText() != null) b.append(t.getText()).append('\n');
        if (c instanceof Container ct) for (Component ch : ct.getComponents()) collectTexts(ch, b);
    }

    private void check(String label, boolean ok) {
        checks++;
        if (!ok) failures++;
        log((ok ? "OK    " : "FALHA ") + label);
    }

    private volatile long lastLog = System.currentTimeMillis();

    private void log(String line) {
        lastLog = System.currentTimeMillis();
        System.out.println(line);
        log.add(line);
    }

    private void writeLog() {
        try (PrintWriter w = new PrintWriter(new File(out, "S013-homologacao-log.txt"), StandardCharsets.UTF_8)) {
            log.forEach(w::println);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void teardown() {
        if (foreground != null) foreground.destroy();
        try {
            if (page != null) page.close();
            if (harness != null) harness.close();
            if (panel != null) panel.close();
            if (cdp != null) cdp.close();
        } catch (RuntimeException ignored) {
            // encerramento
        }
        if (chrome != null) chrome.destroy();
        if (labServer != null) labServer.destroy();
        if (bridge != null) bridge.close();
    }

    private static String run(List<String> cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String outText = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) throw new IllegalStateException(String.join(" ", cmd) + " falhou:\n" + outText);
        return outText;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void waitFor(String what, int seconds, Callable<Boolean> cond) throws Exception {
        long end = System.currentTimeMillis() + seconds * 1000L;
        Exception last = null;
        while (System.currentTimeMillis() < end) {
            try {
                if (Boolean.TRUE.equals(cond.call())) return;
            } catch (Exception e) {
                last = e;
            }
            sleep(150);
        }
        diagnose(what);
        try {
            captureStudio("FALHA-" + what.replaceAll("[^A-Za-z0-9]+", "-"));
        } catch (Exception ignored) {
            // diagnóstico
        }
        throw new IllegalStateException("tempo esgotado aguardando: " + what + (last == null ? "" : " (" + last + ")"));
    }

    /** Estado de todas as peças quando uma espera falha (sem conteúdo digitado). */
    private void diagnose(String what) {
        try {
            log("DIAGNÓSTICO (" + what + "):");
            if (window != null) {
                log("  Recorder: " + edt(() -> {
                    var s = window.studio().recorder().snapshot();
                    return s.state() + " modo=" + s.mode() + " pendente=" + s.pendingMode() + " etapas=" + s.steps() + " motivo=" + s.reason();
                }));
                edt(() -> {
                    window.studio().state().activeAutomation().ifPresent(a -> a.sessions().forEach(x ->
                            log("  sessão " + x.name() + ": " + x.steps().stream().map(AutomationStep::name).toList())));
                    return null;
                });
            }
            if (harness != null) {
                log("  extensão: " + harness.eval("window.nekkora.call('studio.recordings', {}).then(r => JSON.stringify(r.result.map(x => [x.state, x.mode, x.reason, x.attached])))"));
            }
            if (page != null) {
                log("  página: " + page.eval("location.pathname"));
            }
            if (harness != null && labTab > 0) {
                log("  sensor: " + harness.eval("window.nekkora.call('page.diagnostics', {tabId: " + labTab + "}).then(r => JSON.stringify({ok: r.ok, rec: r.result && r.result.recorder, insp: r.result && r.result.inspectorMode, err: r.error}))"));
            }
        } catch (Exception e) {
            log("  (diagnóstico incompleto: " + e + ")");
        }
    }

    private Object waitValue(String what, int seconds, Callable<Object> v) throws Exception {
        AtomicReference<Object> ref = new AtomicReference<>();
        waitFor(what, seconds, () -> {
            Object x = v.call();
            ref.set(x);
            return x != null;
        });
        return ref.get();
    }

    private static <T> T edt(Callable<T> c) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return c.call();
        }
        AtomicReference<T> v = new AtomicReference<>();
        AtomicReference<Exception> err = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                v.set(c.call());
            } catch (Exception e) {
                err.set(e);
            }
        });
        if (err.get() != null) throw err.get();
        return v.get();
    }
}
