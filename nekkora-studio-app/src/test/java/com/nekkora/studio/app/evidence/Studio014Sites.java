package com.nekkora.studio.app.evidence;

import com.nekkora.studio.app.StudioApplication;
import com.nekkora.studio.app.debug.GuidedDebugPresenter;
import com.nekkora.studio.guided.GuidedPlan;
import com.nekkora.studio.recorder.GuidedDebugClient;
import com.nekkora.studio.ui.debug.GuidedDebugPanel;
import com.nekkora.studio.ui.debug.GuidedDebugWindow;
import com.nekkora.studio.domain.element.LocatorStrategy;
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
import com.nekkora.studio.ui.develop.DevelopmentPanel;
import com.nekkora.studio.ai.quality.QualityIssue;
import com.nekkora.studio.ai.quality.AssistanceRequest;
import com.nekkora.studio.ai.spec.FunctionalSpec;
import com.nekkora.studio.domain.action.ActionType;
import java.util.Iterator;
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
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * STUDIO-014 — objetivo final da usuária: TRÊS sites públicos de treinamento mapeados e desenvolvidos PELO STUDIO (Recorder
 * real, debug guiado, PDD, "Desenvolver automação" pela interface, "Validar automação") e o código gerado executado uma vez.
 * Projetos e código em {@code teste-rpa/<site>/}. Erros encontrados são corrigidos no produto, nunca no código gerado.
 */
public final class Studio014Sites {

    private static final int CHROME_X = 1180;

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
    private Cdp cdp;
    private Cdp.Page harness;
    private Cdp.Page panel;
    private String extId;
    private Cdp.Target labTarget;
    private Cdp.Page page;
    private int labTab;

    private Studio014Sites(File out, Path ext) throws AWTException {
        this.out = out;
        this.ext = ext;
        this.robot = new Robot();
        robot.setAutoDelay(25);
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Studio014Sites h = new Studio014Sites(out, Path.of(args[1]).toAbsolutePath().normalize());
        int code = 1;
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

    // ============================================================================== roteiro: 3 sites públicos de treinamento

    /** -Dsites=quotes-toscrape,saucedemo: só esses sites nesta rodada (vazio = os três). */
    private static boolean want(String key) {
        String only = System.getProperty("sites", "");
        return only.isBlank() || List.of(only.split(",")).contains(key);
    }

    private void run() throws Exception {
        log("Tela: " + GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds() + " · java " + System.getProperty("java.version"));
        log("Operador: Claude como AGENTE DE TESTE (TEST_AGENT) · ator do Studio: " + System.getProperty("nekkora.studio.decisionActor"));
        log("Sites públicos de TREINAMENTO (credenciais de demonstração publicadas pelos próprios sites; nada de dado pessoal)");
        SENTINELS.addAll(List.of(IN_PASS, QT_USER, QT_PASS, SD_PASS));
        prepareEnvironment();
        Path studioData = Path.of(System.getenv().getOrDefault("NEKKORA_STUDIO_DATA_DIR", out.toPath().resolve("studio-data").toString()));
        Files.createDirectories(studioData);
        bridge = BridgeServer.start(BridgeServer.Options.defaults(StudioApplication.VERSION));
        window = edt(() -> StudioApplication.launch(Optional.of(NekkoraThemes.SAKURA_DARK), bridge, bridge::close,
                com.nekkora.studio.app.StudioEnvironment.at(studioData), List.of()));
        edt(() -> {
            window.frame().setSize(1180, 1000);
            window.frame().setLocation(0, 0);
            window.frame().toFront();
            return null;
        });
        waitFor("janela do Studio", 10, () -> edt(() -> window.frame().isShowing()));
        launchChrome("about:blank");
        for (String o : List.of(IN, QT, SD)) siteAccess(o + "/*", true);
        navigate(IN + "/login");
        connect();
        Files.createDirectories(DEST);

        if (want("the-internet")) {
        // ======================================================== 1) the-internet.herokuapp.com — login → área segura → sair
        expect.clear();
        expect.putAll(Map.of("Username", "document.getElementById('username')", "Password", "document.getElementById('password')",
                "Login", "document.querySelector('button[type=submit]')", "Logout", "document.querySelector('a[href=\"/logout\"]')"));
        focusStudio();
        createAutomation("Área segura (the-internet)", List.of("Acesso", "Área segura"));
        Automation a = active();
        LogicalSession a1 = edt(() -> a.sessions().get(0));
        LogicalSession a2 = edt(() -> a.sessions().get(1));
        selectSession(a, a1);
        startRecordingOn("the-internet", RecordingMode.CAPTURE_AND_INTERACT);
        moveFloatingAway();
        clickInPage(expect.get("Username"));
        insertText(IN_USER);
        clickInPage(expect.get("Password"));
        insertText(IN_PASS);
        clickInPage(expect.get("Login"));
        waitPage("/secure");
        waitFor("3 etapas", 30, () -> edt(a1::stepCount) >= 3);
        finishRecording();
        selectSession(a, a2);
        startRecordingOn("the-internet", RecordingMode.CAPTURE_ONLY); // LER sem executar: o link "Logout" prova a área segura
        moveFloatingAway();
        clickInPage(expect.get("Logout"));
        waitFor("captura do indicador", 30, () -> edt(a2::stepCount) >= 1);
        finishRecording();
        selectSession(a, a2);
        startRecordingOn("the-internet", RecordingMode.CAPTURE_AND_INTERACT);
        moveFloatingAway();
        clickInPage(expect.get("Logout"));
        waitPage("/login");
        waitFor("sair", 30, () -> edt(a2::stepCount) >= 2);
        finishRecording();
        toObserve(a, edt(() -> a2.steps().get(0)), "Ler o indicador da área segura");
        logKinds(a);
        Path p1 = DEST.resolve("the-internet").resolve("the-internet.nekkora");
        Files.createDirectories(p1.getParent());
        saveAs(p1);
        navigate(IN + "/login");
        debugPass("login", 0, 3);
        page.eval("document.getElementById('username').value = 'tomsmith'; document.getElementById('password').value = " + Json.write(IN_PASS)
                + "; document.querySelector('button[type=submit]').click(); true");
        waitPage("/secure");
        debugPass("área segura", 3, 5);
        save();
        developSite(a, p1, "the-internet", IN + "/login", List.of(), List.of("SD"));

        }
        if (want("quotes-toscrape")) {
        // ======================================================== 2) quotes.toscrape.com — login → próxima página → sair
        expect.clear();
        expect.putAll(Map.of("Username", "document.getElementById('username')", "Password", "document.getElementById('password')",
                "Login", "document.querySelector('input[type=submit]')", "Logout", "document.querySelector('a[href=\"/logout\"]')",
                "Next", "document.querySelector('li.next > a')"));
        // o próprio site rotula a senha com for="username": nomes acessíveis (spec e Chrome) "Username Password" e vazio
        expect.put("Username Password", expect.get("Username"));
        expect.put("", expect.get("Password"));
        navigate(QT + "/login");
        focusStudio();
        createAutomation("Citações (quotes.toscrape)", List.of("Acesso", "Navegação"));
        Automation b = active();
        LogicalSession b1 = edt(() -> b.sessions().get(0));
        LogicalSession b2 = edt(() -> b.sessions().get(1));
        selectSession(b, b1);
        startRecordingOn("quotes.toscrape", RecordingMode.CAPTURE_AND_INTERACT);
        moveFloatingAway();
        clickInPage(expect.get("Username"));
        insertText(QT_USER);
        clickInPage(expect.get("Password"));
        insertText(QT_PASS);
        clickInPage(expect.get("Login"));
        waitFor("lista de citações", 20, () -> page.eval("!!document.querySelector('a[href=\"/logout\"]')") == Boolean.TRUE);
        waitFor("3 etapas", 30, () -> edt(b1::stepCount) >= 3);
        finishRecording();
        selectSession(b, b2);
        startRecordingOn("quotes.toscrape", RecordingMode.CAPTURE_ONLY);
        moveFloatingAway();
        clickInPage(expect.get("Logout"));
        waitFor("captura do indicador", 30, () -> edt(b2::stepCount) >= 1);
        finishRecording();
        selectSession(b, b2);
        startRecordingOn("quotes.toscrape", RecordingMode.CAPTURE_AND_INTERACT);
        moveFloatingAway();
        clickInPage(expect.get("Next"));
        waitPage("/page/2/");
        clickInPage(expect.get("Logout"));
        waitFor("sair", 30, () -> edt(b2::stepCount) >= 3);
        finishRecording();
        toObserve(b, edt(() -> b2.steps().get(0)), "Ler o indicador de sessão aberta");
        logKinds(b);
        Path p2 = DEST.resolve("quotes-toscrape").resolve("quotes-toscrape.nekkora");
        Files.createDirectories(p2.getParent());
        saveAs(p2);
        navigate(QT + "/login");
        debugPass("login", 0, 3);
        page.eval("document.getElementById('username').value = " + Json.write(QT_USER) + "; document.getElementById('password').value = " + Json.write(QT_PASS)
                + "; document.querySelector('input[type=submit]').click(); true");
        waitFor("logado", 20, () -> page.eval("!!document.querySelector('a[href=\"/logout\"]')") == Boolean.TRUE);
        debugPass("lista", 3, 5);
        navigate(QT + "/page/2/");
        debugPass("página 2", 5, 6);
        save();
        developSite(b, p2, "quotes-toscrape", QT + "/login", List.of(), List.of(), () -> {
            navigate(QT + "/login");
            page.eval("document.getElementById('username').value = " + Json.write(QT_USER) + "; document.getElementById('password').value = "
                    + Json.write(QT_PASS) + "; document.querySelector('input[type=submit]').click(); true");
            waitFor("logado", 20, () -> page.eval("!!document.querySelector('a[href=\"/logout\"]')") == Boolean.TRUE);
            debugPass("Next reconfirmado", 4, 5);
        });

        }
        if (want("saucedemo")) {
        // ======================================================== 3) www.saucedemo.com — login → ler o menu da área logada → sair
        // "Add to cart" de UM produto só se distingue pelo nome do produto (id add-to-cart-<produto>): a privacidade retém esse
        // dado de negócio e os 6 botões "Add to cart" são idênticos — o Studio não grava alvo posicional. Cenário sem produto.
        expect.clear();
        expect.putAll(Map.of("Username", "document.getElementById('user-name')", "Password", "document.getElementById('password')",
                "Login", "document.getElementById('login-button')", "Open Menu", "document.getElementById('react-burger-menu-btn')",
                "Logout", "document.getElementById('logout_sidebar_link')"));
        navigate(SD + "/");
        focusStudio();
        createAutomation("Produtos (saucedemo)", List.of("Acesso", "Produtos", "Saída"));
        Automation c = active();
        LogicalSession c1 = edt(() -> c.sessions().get(0));
        LogicalSession c2 = edt(() -> c.sessions().get(1));
        LogicalSession c3 = edt(() -> c.sessions().get(2));
        selectSession(c, c1);
        startRecordingOn("saucedemo", RecordingMode.CAPTURE_AND_INTERACT);
        moveFloatingAway();
        clickInPage(expect.get("Username"));
        insertText(SD_USER);
        clickInPage(expect.get("Password"));
        insertText(SD_PASS);
        clickInPage(expect.get("Login"));
        waitPage("/inventory.html");
        waitFor("3 etapas", 30, () -> edt(c1::stepCount) >= 3);
        finishRecording();
        selectSession(c, c2);
        // LER sem agir: o botão "Open Menu" só existe na área logada (o título "Products" é texto não interativo: o Recorder
        // descarta clique nele — NON_INTERACTIVE)
        startRecordingOn("saucedemo", RecordingMode.CAPTURE_ONLY);
        moveFloatingAway();
        clickInPage(expect.get("Open Menu"));
        waitFor("captura do indicador", 30, () -> edt(c2::stepCount) >= 1);
        finishRecording();
        selectSession(c, c3);
        startRecordingOn("saucedemo", RecordingMode.CAPTURE_AND_INTERACT);
        moveFloatingAway();
        clickInPage(expect.get("Open Menu"));
        sleep(1200);
        clickInPage(expect.get("Logout"));
        waitPage("/");
        waitFor("2 etapas na saída", 30, () -> edt(c3::stepCount) >= 2);
        finishRecording();
        toObserve(c, edt(() -> c2.steps().get(0)), "Ler o indicador de sessão aberta");
        logKinds(c);
        Path p3 = DEST.resolve("saucedemo").resolve("saucedemo.nekkora");
        Files.createDirectories(p3.getParent());
        saveAs(p3);
        navigate(SD + "/");
        debugPass("login", 0, 3);
        page.eval("(() => { const set = (id, v) => { const e = document.getElementById(id); const s = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;"
                + " s.call(e, v); e.dispatchEvent(new Event('input', {bubbles: true})); }; set('user-name', " + Json.write(SD_USER) + "); set('password', "
                + Json.write(SD_PASS) + "); document.getElementById('login-button').click(); return true; })()");
        waitPage("/inventory.html");
        debugPass("produtos e menu", 3, 5);
        page.eval("document.getElementById('react-burger-menu-btn').click(); true");
        sleep(1200);
        debugPass("menu aberto", 5, 6);
        save();
        developSite(c, p3, "saucedemo", SD + "/", List.of("TARGET_HIDDEN"), List.of());
        check("privacidade: log sem as senhas de demonstração", SENTINELS.stream().noneMatch(String.join("\n", log)::contains));
        }
    }

    /**
     * Desenvolver automação pela interface (um clique) e as perguntas pela janela: endereço inicial (se mascarado),
     * autorização do site público de treinamento, confirmação de envio; validar; executar o código gerado UMA vez.
     */
    /** Passo da pessoa fora do "Desenvolver automação" (ex.: reconfirmar um alvo no debug guiado depois de "Revisar e aplicar"). */
    private interface Step {
        void run() throws Exception;
    }

    private void developSite(Automation a, Path project, String key, String start, List<String> postconditions, List<String> unused) throws Exception {
        developSite(a, project, key, start, postconditions, unused, null);
    }

    private void developSite(Automation a, Path project, String key, String start, List<String> postconditions, List<String> unused, Step reconfirm)
            throws Exception {
        reviewPddWindow(edt(() -> a.sessions().stream().map(LogicalSession::name).toList()), edt(() -> a.sessions().stream().map(s -> "Etapas: " + s.name()).toList()));
        DevelopmentPanel dp = openDevelopment();
        DevelopmentPanel.Model m = waitDevIdle("análise " + key);
        Iterator<String> posts = postconditions.iterator();
        for (int round = 0; round < 8; round++) {
            m = edt(dp::model);
            QualityIssue q = m.pending().stream().filter(i -> i.kind() == QualityIssue.Kind.HELP_REQUIRED || i.kind() == QualityIssue.Kind.SECURITY_BLOCKED)
                    .findFirst().orElse(null);
            if (q == null) break;
            log("  " + key + " · pergunta: " + q.kind() + " " + q.code() + " — " + q.assistance().map(AssistanceRequest::question).orElse(q.detail()));
            captureWindow(devDialog(), key + "-pergunta-" + round);
            switch (q.code()) {
                case "SPEC_START_URL_UNKNOWN" -> answerText(dp, q.code(), start);
                case "ORIGIN_NOT_AUTHORIZED_FOR_EXECUTION" -> {
                    clickPendingButton(dp, q.code(), "Autorizar como site público de treinamento…");
                    NekkoraDialog d = (NekkoraDialog) waitValue("confirmação", 15, () -> edt(() -> {
                        for (Window w : Window.getWindows()) if (w instanceof NekkoraDialog x && x.isShowing() && "Autorizar site público de treinamento?".equals(x.getTitle())) return x;
                        return null;
                    }));
                    captureWindow(d, key + "-autorizar-site");
                    clickButtonWithText(d, "Autorizar");
                    waitClosed(d);
                    waitDevIdle("depois da autorização");
                }
                case "SPEC_POSTCONDITION_MISSING" -> answerOption(dp, q.code(), posts.hasNext() ? posts.next() : "TARGET_HIDDEN");
                case "SPEC_EFFECT_UNKNOWN" -> answerOption(dp, q.code(), "NONE");
                case "SPEC_CONCLUSION_MISSING" -> answerOption(dp, q.code(), FunctionalSpec.NO_CONCLUSION);
                default -> {
                    log("  " + key + " · pendência sem resposta prevista: " + q.code() + " · " + q.detail());
                    round = 99;
                }
            }
        }
        m = waitDevIdle("resultado " + key);
        if (reconfirm != null && m.pending().stream().anyMatch(i -> i.code().equals("LOCATOR_POSITIONAL"))) {
            // proposta do Studio: trocar o localizador posicional pelo alternativo validado — e confirmar o alvo de novo no debug
            captureWindow(devDialog(), key + "-proposta-localizador");
            log("  " + key + " · proposta: " + m.pending().stream().filter(i -> i.code().equals("LOCATOR_POSITIONAL")).map(QualityIssue::title).toList()
                    + " → Revisar e aplicar; reconfirmar no debug guiado");
            clickPendingButton(dp, "LOCATOR_POSITIONAL", null);
            closeDevelopment();
            reconfirm.run();
            save();
            developSite(a, project, key, start, postconditions, unused, null);
            return;
        }
        if (m.pending().stream().anyMatch(i -> i.code().equals("ENVIRONMENT_NOT_READY"))) m = prepareAutomationEnvironment(dp, key);
        captureWindow(devDialog(), key + "-resultado");
        log("  " + key + " · pendências: " + m.pending().stream().map(i -> i.kind() + ":" + i.code()).toList());
        log("  " + key + " · resultado:\n" + String.join("\n", m.results()).indent(4));
        Path gen = project.resolveSibling(project.getFileName().toString().replace(".nekkora", "") + "-python");
        check(key + ": código gerado PELO STUDIO e verificado (CODE_VALIDATED) em " + gen.getFileName(), m.results().contains("✓ CODE_VALIDATED")
                && Files.isRegularFile(gen.resolve("manifest.json")));
        String code = dirText(gen);
        check(key + ": sem senhas no código; ambiente benchmark com a origem autorizada", SENTINELS.stream().noneMatch(code::contains)
                && code.contains("environment = \"benchmark\""));
        if (key.equals("the-internet")) {
            // CODEGEN-QUALITY-001 rodada 2: login e logout nunca repetidos (CQ-04); a leitura do Logout diz que é o texto do link (CQ-05)
            String pages = Files.readString(gen.resolve("automation/pages.py"), StandardCharsets.UTF_8);
            java.util.regex.Matcher clicks = java.util.regex.Pattern.compile("self\\.actions\\.click\\((?:[^()]|\\([^()]*(?:\\([^()]*\\))?[^()]*\\))*\\)").matcher(pages);
            List<String> calls = new ArrayList<>();
            while (clicks.find()) calls.add(clicks.group());
            log("  " + key + " · cliques: " + calls.stream().map(c -> c.replaceAll("\\s+", " ")).toList());
            check(key + ": CQ-04 login e logout sem idempotent=True (uma vez só)", calls.size() == 2 && calls.stream().noneMatch(c -> c.contains("idempotent=True")));
            check(key + ": CQ-05 a leitura do Logout é nomeada como texto do controle", pages.contains("def read_logout_link_text(") && !pages.contains("result_logout"));
            check(key + ": CQ-01/Q11 tela pela frase nominal da seção, em inglês (SecureAreaPage)", pages.contains("class SecureAreaPage(PageBase)"));
            // rodada 3: a pós-condição do login usa a chave tipada da tela seguinte (antes, a chave escrita como texto)
            check(key + ": Q10 pós-condição do login com SelectorKey (nenhuma chave como texto)",
                    pages.contains("postcondition=lambda: self.actions.exists(SelectorKey.SECURE_AREA_LOGOUT.value)") && !pages.contains("\"process."));
            String selectorsPy = Files.readString(gen.resolve("automation/selectors.py"), StandardCharsets.UTF_8);
            check(key + ": Q10 SelectorKey do campo de senha anotado para o S105 (é a chave, não a senha)",
                    selectorsPy.contains("ACCESS_PASSWORD = \"process.access.password\"  # noqa: S105 - element key, not a secret"));
        }
        closeDevelopment();
        save();
        // execução ÚNICA no site de treinamento (Chromium headless), segredos só por variável de ambiente
        Path runDir = Files.createTempDirectory("nk014-site-");
        Map<String, String> env = new java.util.HashMap<>();
        for (String line : code.lines().filter(l -> l.contains("required_secrets")).toList()) {
            java.util.regex.Matcher mm = java.util.regex.Pattern.compile("\"([a-z0-9_.]+)\"").matcher(line);
            while (mm.find()) {
                String alias = mm.group(1);
                String var = "NK_SECRET_" + alias.toUpperCase().replace('.', '_');
                env.put(var, alias.endsWith("senha") ? secretFor(key, true) : secretFor(key, false));
            }
        }
        String exec = runPython(List.of(gen.resolve("main.py").toString(), "--output", runDir.resolve("saida").toString(), "--channel", "chromium", "--headless"),
                gen, env);
        String last = exec.strip().lines().reduce((x, y) -> y).orElse("");
        log("  " + key + " · execução: " + last);
        String result = resultOutput(runDir.resolve("saida").resolve("resultados"));
        String events = dirText(runDir.resolve("saida").resolve("execucao"));
        check(key + ": a EXECUÇÃO do código gerado terminou COMPLETED com 1 transação bem-sucedida", last.contains("\"status\": \"COMPLETED\"")
                && last.contains("\"SUCCEEDED\": 1"));
        check(key + ": eventos sem senha", SENTINELS.stream().noneMatch(events::contains));
        Files.writeString(gen.getParent().resolve("execucao-resumo.txt"), last + "\n" + resultLine(result) + "\n", StandardCharsets.UTF_8);
        log("  " + key + " · leitura do processo: " + resultLine(result));
        deleteTree(runDir);
    }

    private static String resultLine(String json) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"(resultado[a-z0-9_]*)\": \"([^\"]{0,60})\"").matcher(json);
        return m.find() ? m.group(1) + " = " + m.group(2) : "(sem leitura)";
    }

    private String secretFor(String key, boolean password) {
        return switch (key) {
            case "the-internet" -> password ? IN_PASS : IN_USER;
            case "quotes-toscrape" -> password ? QT_PASS : QT_USER;
            default -> password ? SD_PASS : SD_USER;
        };
    }

    /** A etapa capturada (sem executar) passa a Observar — edição do Studio pelo agente de teste. */
    private void toObserve(Automation a, AutomationStep st, String name) throws Exception {
        edt(() -> {
            window.studio().service().updateStepBasics(a.id(), st.id(), name, "", ActionType.OBSERVE);
            return null;
        });
    }

    private void logKinds(Automation a) throws Exception {
        log("  " + edt(a::name) + ": " + edt(() -> a.sessions().stream().map(s -> s.name() + " " + kinds(s)).toList()));
        check(edt(a::name) + ": nenhuma senha no modelo", SENTINELS.stream().noneMatch(edt(() -> domainText(a))::contains));
    }

    // ============================================================================== apoio

    private static final String IN = "https://the-internet.herokuapp.com";
    private static final String QT = "https://quotes.toscrape.com";
    private static final String SD = "https://www.saucedemo.com";
    private static final String BASE = IN;
    /** Credenciais de DEMONSTRAÇÃO publicadas pelos próprios sites (não são dados de ninguém). */
    private static final String IN_USER = "tomsmith";
    private static final String IN_PASS = "SuperSecretPassword!";
    private static final String QT_USER = "agente014";
    private static final String QT_PASS = "senha014demo";
    private static final String SD_USER = "standard_user";
    private static final String SD_PASS = "secret_sauce";
    private static final List<String> SENTINELS = new ArrayList<>();
    /** Pasta pedida pela usuária: Documentos/teste-rpa (no Windows com OneDrive, a pasta Documentos fica dentro dele). */
    private static final Path DEST = Path.of(System.getenv().getOrDefault("NEKKORA_TESTE_RPA",
            Path.of(System.getProperty("user.home"), "OneDrive", "Documentos", "teste-rpa").toString()));
    private static final Path FRAMEWORK = Path.of("..", "Nekkora-RPA-Framework").toAbsolutePath().normalize();
    private static final Path PYTHON = Path.of(System.getenv().getOrDefault("NEKKORA_PYTHON", FRAMEWORK.resolve(".venv/Scripts/python.exe").toString()));
    private final Map<String, String> expect = new LinkedHashMap<>();

    private String expectedFor(AutomationStep s) {
        String name = s.element().map(ElementReference::accessibleName).orElse("");
        if (expect.containsKey(name)) return expect.get(name);
        if (s.element().map(e -> e.tag().equals("a") && name.matches("\\d*")).orElse(false)) return expect.get("cart");
        return null;
    }

    private void createAutomation(String name, List<String> sessions) throws Exception {
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_N);
        NekkoraDialog d = waitDialog("Nova automação");
        typeIntoFocused(name);
        press(KeyEvent.VK_ENTER);
        waitClosed(d);
        for (String s : sessions) newSession(s);
    }

    /** Debug guiado do Studio sobre as etapas [from, to): identidade comprovada → Continuar; senão Corrigir por seleção; o resto, Próxima. */
    private void debugPass(String label, int from, int to) throws Exception {
        Automation a = active();
        GuidedDebugPanel dp = openDebugAndStart(t -> t.label().contains("Laboratório local") || t.label().contains("127.0.0.1") || t.label().contains("Autorizada"), 0);
        List<AutomationStep> steps = edt(() -> a.sessions().stream().flatMap(x -> x.steps().stream()).toList());
        for (int i = 0; i < steps.size(); i++) {
            final int n = i;
            GuidedDebugPanel.Card card = waitCard(label + " etapa " + (i + 1), x -> x.mode() == GuidedDebugPanel.Mode.FINISHED
                    || x.mode() == GuidedDebugPanel.Mode.STEP && x.badge() != GuidedDebugPanel.Badge.CHECKING && x.progress().startsWith((n + 1) + " de"));
            if (card.mode() == GuidedDebugPanel.Mode.FINISHED) break;
            AutomationStep st = steps.get(i);
            if (i < from || i >= to) {
                if (i == steps.size() - 1) break; // última etapa fora da passagem: a sessão é encerrada abaixo
                click(edt(dp::nextButton));
                continue;
            }
            String expected = expectedFor(st);
            if (expected != null) {
                try {
                    waitFor("alvo esperado na página", 15, () -> page.eval("!!(" + expected + ")") == Boolean.TRUE);
                } catch (IllegalStateException e) {
                    log("  alvo esperado ausente em " + page.eval("location.href"));
                    throw e;
                }
            }
            GuidedDebugClient.Inspection r = lastInspection();
            boolean resolved = r != null && r.status() == GuidedDebugClient.Status.RESOLVED;
            log("  debug " + label + " etapa " + (i + 1) + " (" + st.actionType() + "): " + card.badge().label() + (r == null ? "" : " · " + r.status()));
            if (resolved && r.box() != null && expected != null && boxOn(r.box(), expected)) {
                if (card.badge() == GuidedDebugPanel.Badge.REUSED) {
                    waitCard("avanço", x -> x.mode() == GuidedDebugPanel.Mode.FINISHED || !x.progress().startsWith((n + 1) + " de"));
                } else {
                    click(edt(dp::continueButton));
                }
                continue;
            }
            log("  corrigir etapa " + (i + 1) + ": caixa " + (r == null ? null : r.box()) + " · página " + page.eval("location.href") + " · expr " + expected + " · inputs " + page.eval("document.querySelectorAll('input').length") + " · alvo "
                    + page.eval("JSON.stringify((e => e && e.getBoundingClientRect())(" + expected + "))"));
            click(edt(dp::correctButton));
            waitCard("corrigindo", x -> x.mode() == GuidedDebugPanel.Mode.CORRECTING);
            log("  em correção: página " + page.eval("location.href"));
            click(edt(dp::pickButton));
            waitCard("seleção no navegador", x -> x.mode() == GuidedDebugPanel.Mode.PICKING);
            clickInPage(expected);
            waitCard("comparação", x -> x.mode() == GuidedDebugPanel.Mode.COMPARING);
            click(edt(dp::applyButton));
            waitCard("alvo novo verificado", x -> x.mode() == GuidedDebugPanel.Mode.FINISHED || x.mode() == GuidedDebugPanel.Mode.STEP
                    && x.badge() != GuidedDebugPanel.Badge.CHECKING);
            GuidedDebugPanel.Card now = edt(dp::card);
            if (now.mode() == GuidedDebugPanel.Mode.STEP && now.progress().startsWith((n + 1) + " de")) {
                if (now.canContinue()) click(edt(dp::continueButton));
                else click(edt(dp::nextButton));
            }
        }
        if (edt(dp::card).mode() == GuidedDebugPanel.Mode.FINISHED) {
            click(edt(dp::finishButton));
        } else {
            edt(() -> {
                window.studio().guidedDebug().finish(); // etapas fora desta passagem ficam para a próxima (decisões já registradas)
                return null;
            });
        }
        waitDebugClosed();
    }

    /** Abre o PDD visual pelo menu e revisa (TEST_AGENT) títulos e instruções marcados — só rótulos, nenhum valor. */
    private void reviewPddWindow(List<String> sections, List<String> blocks) throws Exception {
        menuPath("Automação", "Gerar documentação", "PDD visual…");
        JDialog dlg = (JDialog) waitValue("janela do PDD", 15, () -> edt(() -> {
            for (Window w : Window.getWindows()) if (w instanceof JDialog d && d.isShowing() && d.getTitle().startsWith("PDD visual")) return d;
            return null;
        }));
        PddPanel p = edt(() -> window.studio().visualPdd().panel());
        reviewOpenPdd(p, sections, blocks);
        closePdd();
    }

    private void reviewOpenPdd(PddPanel p, List<String> sections, List<String> blocks) throws Exception {
        sleep(800);
        for (int i = 0; i < sections.size(); i++) {
            selectItem(p, "secao-" + (i + 1));
            setText(p.titleField(), sections.get(i));
            click(edt(p::saveTitleButton));
        }
        List<PddPanel.Item> items = edt(() -> model(p).items().stream().filter(x -> x.label().startsWith("    ")).toList());
        for (int i = 0; i < items.size(); i++) {
            selectItem(p, items.get(i).key());
            setText(p.titleField(), i < blocks.size() ? blocks.get(i) : "Etapas do processo");
            click(edt(p::saveTitleButton));
            for (int guard = 0; guard < 20; guard++) {
                List<PddPanel.InstructionRow> rows = edt(() -> model(p).instructions());
                int k = -1;
                for (int x = 0; x < rows.size(); x++) if (rows.get(x).needsAttention()) { k = x; break; }
                if (k < 0) break;
                final int idx = k;
                javax.swing.JTextField f = edt(() -> p.instructionFields().get(idx));
                Container row = edt(f::getParent);
                setText(f, "Executar a etapa indicada na tela"); // texto revisado (TEST_AGENT): só rótulo, nenhum valor
                click(edt(() -> find(row, AbstractButton.class, b -> "Salvar".equals(b.getText()))));
                sleep(400);
            }
        }
        log("  PDD revisado (TEST_AGENT): pendências " + model(p).pending() + " · pode exportar " + model(p).canExport());
        check("PDD: textos revisados; sem pendência que bloqueie (" + sections + ")", model(p).canExport());
    }

    private void closePdd() throws Exception {
        edt(() -> {
            for (Window w : Window.getWindows()) if (w instanceof JDialog d && d.isShowing() && d.getTitle().startsWith("PDD visual")) d.setVisible(false);
            return null;
        });
        sleep(400);
    }

    private DevelopmentPanel openDevelopment() throws Exception {
        menu("Automação", "Desenvolver automação");
        waitValue("janela Desenvolver automação", 15, this::devDialog);
        return edt(() -> window.studio().development().panel());
    }

    private JDialog devDialog() throws Exception {
        return edt(() -> {
            for (Window w : Window.getWindows()) if (w instanceof JDialog d && d.isShowing() && DevelopmentPanel.TITLE.equals(d.getTitle())) return d;
            return null;
        });
    }

    private void closeDevelopment() throws Exception {
        JDialog d = devDialog();
        if (d != null) clickButtonWithText(d, "Fechar");
        sleep(400);
    }

    /** Espera a análise terminar (sem trabalho em segundo plano) e devolve o estado da janela. */
    private DevelopmentPanel.Model waitDevIdle(String what) throws Exception {
        sleep(600);
        waitFor(what, 180, () -> edt(() -> !window.studio().development().running()));
        window.studio().development().awaitIdle();
        sleep(800);
        return edt(() -> window.studio().development().panel().model());
    }

    private void selectTabOf(DevelopmentPanel dp, int index) throws Exception {
        Rectangle r = edt(() -> {
            Rectangle tab = dp.tabs().getBoundsAt(index);
            java.awt.Point p = dp.tabs().getLocationOnScreen();
            return new Rectangle(p.x + tab.x, p.y + tab.y, tab.width, tab.height);
        });
        guardForeground("aba da janela Desenvolver");
        robot.mouseMove(r.x + r.width / 2, r.y + r.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        sleep(400);
    }

    /** Seleciona a pendência pelo código (clique REAL na linha da lista). */
    /**
     * PACKAGING-001: diretórios isolados começam sem a venv da automação — pendência "Ambiente Python da automação" →
     * "Preparar ambiente…" → confirmação do plano → instalação → Desenvolver de novo (V4/V5 com o ambiente novo).
     */
    private DevelopmentPanel.Model prepareAutomationEnvironment(DevelopmentPanel dp, String key) throws Exception {
        selectPending(dp, "ENVIRONMENT_NOT_READY");
        AbstractButton b = edt(() -> find(dp.pendingButtons(), AbstractButton.class, x -> x.getText() != null && x.getText().startsWith("Preparar ambiente")));
        if (b == null) throw new IllegalStateException("botão Preparar ambiente não encontrado na pendência");
        click(b);
        NekkoraDialog d = waitDialog("Preparar o ambiente Python da automação?");
        captureWindow(d, key + "-preparar-ambiente");
        clickButtonWithText(d, "Preparar ambiente");
        waitClosed(d);
        waitFor("ambiente pronto e código verificado", 1200, () -> edt(() -> !window.studio().development().running()
                && (dp.model().results().contains("✓ CODE_VALIDATED") || dp.model().severity() == com.nekkora.ui.core.Severity.ERROR)));
        DevelopmentPanel.Model m = waitDevIdle("resultado " + key + " com o ambiente preparado");
        log("  " + key + " · ambiente: " + m.python());
        return m;
    }

    private void selectPending(DevelopmentPanel dp, String code) throws Exception {
        selectTabOf(dp, 1);
        int index = (int) waitValue("pendência " + code, 15, () -> edt(() -> {
            var l = dp.pendingList();
            for (int i = 0; i < l.getModel().getSize(); i++) if (l.getModel().getElementAt(i).code().equals(code)) return i;
            return null;
        }));
        Rectangle r = edt(() -> {
            var l = dp.pendingList();
            l.ensureIndexIsVisible(index);
            Rectangle cell = l.getCellBounds(index, index);
            java.awt.Point o = l.getLocationOnScreen();
            return new Rectangle(o.x + cell.x, o.y + cell.y, cell.width, cell.height);
        });
        guardForeground("pendência");
        robot.mouseMove(r.x + Math.min(r.width / 2, 160), r.y + r.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        waitFor("pendência selecionada", 5, () -> edt(() -> dp.pendingList().getSelectedValue() != null && dp.pendingList().getSelectedValue().code().equals(code)));
        sleep(300);
    }

    /** Responde a uma pergunta com o botão da opção (clique real). */
    private void answerOption(DevelopmentPanel dp, String code, String value) throws Exception {
        selectPending(dp, code);
        QualityIssue q = edt(() -> dp.pendingList().getSelectedValue());
        String label = q.assistance().orElseThrow().options().stream().filter(o -> value.equals(o.value())).findFirst().orElseThrow().label();
        clickButtonWithText(edt(dp::pendingButtons), label);
        waitDevIdle("resposta " + code);
    }

    /** Resposta curta digitada no campo da pendência + Confirmar (clique real). */
    private void answerText(DevelopmentPanel dp, String code, String text) throws Exception {
        selectPending(dp, code);
        JTextComponent field = edt(() -> find(dp.pendingButtons(), JTextComponent.class, x -> "develop.answerText".equals(x.getName())));
        click(field);
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_A);
        typeAscii(text);
        sleep(200);
        AbstractButton ok = edt(() -> find(dp.pendingButtons(), AbstractButton.class, x -> x.getText() != null && x.getText().startsWith("Confirmar")));
        click(ok);
        sleep(800);
        if (!textShown("Endereço recusado")) waitDevIdle("resposta " + code);
    }

    /** Botão de uma pendência (Abrir PDD, Revisar e aplicar…); {@code null} = o botão de aplicar a correção/proposta. */
    private void clickPendingButton(DevelopmentPanel dp, String code, String text) throws Exception {
        selectPending(dp, code);
        AbstractButton b = edt(() -> find(dp.pendingButtons(), AbstractButton.class, x -> x.getText() != null && (text == null
                ? x.getText().startsWith("Revisar e aplicar") : text.equals(x.getText()))));
        if (b == null) throw new IllegalStateException("botão da pendência não encontrado: " + code + " / " + text);
        click(b);
        sleep(600);
    }

    private boolean textShown(String title) throws Exception {
        return edt(() -> {
            for (Window w : Window.getWindows()) if (w instanceof NekkoraDialog d && d.isShowing() && title.equals(d.getTitle())) return true;
            return false;
        });
    }

    private void closeTextDialog() throws Exception {
        NekkoraDialog d = edt(() -> {
            for (Window w : Window.getWindows()) if (w instanceof NekkoraDialog x && x.isShowing()) return x;
            return null;
        });
        if (d != null) {
            captureWindow(d, "A-01b-endereco-recusado");
            clickButtonWithText(d, "Fechar");
            waitClosed(d);
        }
    }

    /** Gerador AVANÇADO (configuração manual) pelo menu Automação → Avançado — o caminho legado continua funcionando. */
    private String generateByLegacyMenu(Path config, Path outDir) throws Exception {
        menuPath("Automação", "Avançado", "Gerador avançado (configuração manual)…");
        NekkoraDialog d = waitDialog("Gerar código Python");
        JTextComponent cfg = edt(() -> find(d, JTextComponent.class, f -> "codegen.config".equals(f.getName())));
        JTextComponent dir = edt(() -> find(d, JTextComponent.class, f -> "codegen.out".equals(f.getName())));
        JTextComponent res = edt(() -> find(d, JTextComponent.class, f -> "codegen.result".equals(f.getName())));
        setText(cfg, config.toString());
        setText(dir, outDir.toString());
        AbstractButton gb = edt(() -> find(d, AbstractButton.class, x -> "codegen.generate".equals(x.getName())));
        click(gb);
        String text = (String) waitValue("resultado da geração", 30, () -> edt(() -> res.getText().isBlank() ? null : res.getText()));
        captureWindow(d, "B-02-gerador-avancado");
        clickButtonWithText(d, "Fechar");
        waitClosed(d);
        return text;
    }

    private static boolean sameFiles(Path a, Path b) throws IOException {
        try (Stream<Path> s = Files.walk(a)) {
            for (Path f : s.filter(Files::isRegularFile).toList()) {
                Path rel = a.relativize(f);
                if (!Files.isRegularFile(b.resolve(rel)) || !java.util.Arrays.equals(Files.readAllBytes(f), Files.readAllBytes(b.resolve(rel)))) return false;
            }
        }
        return true;
    }

    private int oracleTotal() throws Exception {
        return ((Number) Json.parseObject(httpGet("http://127.0.0.1:18765/api/studio009/estado")).get("total")).intValue();
    }

    @SuppressWarnings("unchecked")
    private String oracleLastCode() throws Exception {
        List<Object> c = Json.array(Json.parseObject(httpGet("http://127.0.0.1:18765/api/studio009/estado")), "consultas");
        return c.isEmpty() ? "" : (String) ((Map<String, Object>) c.get(c.size() - 1)).get("codigo");
    }

    private void oracleReset() throws Exception {
        httpGet("http://127.0.0.1:18765/api/studio009/reset");
        log("Oráculo do laboratório zerado");
    }

    private static String httpGet(String url) throws Exception {
        java.net.http.HttpResponse<String> r = java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        return r.body();
    }

    private static String resultOutput(Path dir) throws IOException {
        StringBuilder b = new StringBuilder();
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path f : s.filter(p -> p.toString().endsWith(".json")).toList()) b.append(Files.readString(f, StandardCharsets.UTF_8));
        }
        return b.toString();
    }

    /** Roda o Python do framework (código gerado ou ferramenta) num processo separado; a saída fica só aqui (não vai ao log inteira). */
    private String runPython(List<String> args, Path cwd, Map<String, String> extraEnv) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(PYTHON.toString());
        cmd.add("-B");
        cmd.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(cwd.toFile()).redirectErrorStream(true);
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        pb.environment().putAll(extraEnv);
        Process p = pb.start();
        String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        log("  python " + Path.of(args.get(0)).getFileName() + " → código de saída " + code);
        return text;
    }

    private static void deleteTree(Path dir) {
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // temporário
        }
    }


    /** Texto inserido no campo focado por CDP (evento de entrada confiável; nada na área de transferência). */
    private void insertText(String text) throws Exception {
        page.send("Input.insertText", Json.obj().put("text", text).build());
        sleep(250);
    }

    private void finishRecording() throws Exception {
        sleep(1500);
        clickButtonWithText(floating(), "Finalizar");
        waitFor("gravação finalizada", 30, () -> !edt(() -> window.studio().recorder().snapshot().state().active()));
        sleep(600);
        clickButtonWithText(floating(), "Voltar ao Studio");
        sleep(1000);
    }

    private void saveAs(Path file) throws Exception {
        focusStudioWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_S);
        waitChooser("Salvar projeto como");
        typePath(file);
        waitFor("salvo", 20, () -> Files.isRegularFile(file));
        sleep(800);
    }

    private void save() throws Exception {
        focusStudioWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_S);
        sleep(1500);
        NekkoraDialog migrate = edt(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof NekkoraDialog nd && nd.isShowing() && "Atualizar o formato do projeto?".equals(nd.getTitle())) return nd;
            }
            return null;
        });
        if (migrate != null) {
            // decisões sobre o alvo exigem o formato 1.2.0; o produto guarda uma cópia do arquivo anterior antes de gravar
            log("  salvar: o Studio pede para atualizar o formato do projeto (decisões de alvo) — Atualizar e salvar");
            clickButtonWithText(migrate, "Atualizar e salvar");
            waitClosed(migrate);
        }
        waitFor("projeto salvo", 15, () -> edt(() -> !window.studio().state().activeAutomation()
                .flatMap(x -> window.studio().state().project(x.id())).map(com.nekkora.studio.app.workspace.ProjectState::dirty).orElse(true)));
    }

    private void reopen(Path file) throws Exception {
        menu("Arquivo", "Fechar projeto");
        sleep(1200);
        openByDialog(file);
        waitFor("projeto reaberto", 20, () -> edt(() -> window.studio().state().activeAutomation().isPresent()));
        sleep(800);
    }

    // --- debug guiado (mesmos apoios da homologação da STUDIO-009) ---
    private GuidedDebugPanel openDebugAndStart(java.util.function.Predicate<GuidedDebugPanel.Tab> which, int nth) throws Exception {
        focusStudio();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_D);
        GuidedDebugWindow gw = (GuidedDebugWindow) waitValue("janela do debug", 15, () -> edt(() -> {
            for (Window x : Window.getWindows()) {
                if (x instanceof GuidedDebugWindow g && g.isShowing()) return g;
            }
            return null;
        }));
        edt(() -> {
            gw.setLocation(20, 20); // sobre o Studio: a página do Chrome (estreita nesta tela) fica livre para os cliques
            return null;
        });
        GuidedDebugPanel dp = edt(() -> window.studio().guidedDebug().panel());
        GuidedDebugPanel.Card listed = waitCard("abas listadas", c -> c.mode() == GuidedDebugPanel.Mode.CHOOSE_TAB && !c.tabs().isEmpty());
        log("  abas do debug: " + listed.tabs().stream().map(GuidedDebugPanel.Tab::label).toList());
        List<GuidedDebugPanel.Tab> options = listed.tabs().stream().filter(which).toList();
        int index = listed.tabs().indexOf(options.get(nth));
        edt(() -> {
            dp.tabsInput().setSelectedIndex(index);
            return null;
        });
        click(edt(dp::startButton));
        return dp;
    }

    private GuidedDebugPanel.Card waitCard(String what, java.util.function.Predicate<GuidedDebugPanel.Card> p) throws Exception {
        try {
            return (GuidedDebugPanel.Card) waitValue(what, 30, () -> edt(() -> {
                GuidedDebugPanel.Card c = window.studio().guidedDebug().panel().card();
                return c != null && p.test(c) ? c : null;
            }));
        } catch (IllegalStateException e) {
            GuidedDebugPanel.Card c = edt(() -> window.studio().guidedDebug().panel().card());
            log("  cartão atual: " + (c == null ? "nenhum" : c.mode() + " · " + c.badge() + " · " + c.progress() + " · " + c.message()));
            captureStudio("FALHA-" + what.replaceAll("[^A-Za-z0-9]+", "-"));
            throw e;
        }
    }

    private void waitDebugClosed() throws Exception {
        waitFor("debug fechado", 15, () -> edt(() -> {
            for (Window x : Window.getWindows()) if (x instanceof GuidedDebugWindow g && g.isShowing()) return false;
            return true;
        }));
    }

    private GuidedDebugClient.Inspection lastInspection() throws Exception {
        return edt(() -> {
            java.lang.reflect.Field f = GuidedDebugPresenter.class.getDeclaredField("last");
            f.setAccessible(true);
            return (GuidedDebugClient.Inspection) f.get(window.studio().guidedDebug());
        });
    }

    /** O destaque (retângulo na tela informado pela extensão) está sobre o elemento esperado. */
    private boolean boxOn(GuidedDebugClient.Box box, String expr) throws Exception {
        @SuppressWarnings("unchecked")
        List<Object> e = (List<Object>) page.eval("((el) => { if (!el) return null; const r = el.getBoundingClientRect();"
                + " const bx = (window.outerWidth - window.innerWidth) / 2; const by = window.outerHeight - window.innerHeight - bx;"
                + " return [window.screenX + bx + r.left, window.screenY + by + r.top, r.width, r.height]; })(" + expr + ")");
        if (e == null || box == null) return false;
        Rectangle want = new Rectangle((int) Math.round(((Number) e.get(0)).doubleValue()) - 4, (int) Math.round(((Number) e.get(1)).doubleValue()) - 4,
                (int) Math.round(((Number) e.get(2)).doubleValue()) + 8, (int) Math.round(((Number) e.get(3)).doubleValue()) + 8);
        java.awt.Point center = new java.awt.Point(box.screenX() + box.width() / 2, box.screenY() + box.height() / 2);
        return want.contains(center);
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
    private void selectItem(PddPanel p, String key) throws Exception {
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
        return model(p).hasImage() && "Imagem sanitizada associada".equals(model(p).bannerTitle());
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
        if (foreground == null || !foreground.isAlive()) {
            Path script = Files.createTempFile("nk009-fg", ".ps1");
            Files.writeString(script, String.join(System.lineSeparator(),
                    "Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public static class NkFg {"
                            + " [DllImport(\"user32.dll\")] public static extern IntPtr GetForegroundWindow();"
                            + " [DllImport(\"user32.dll\")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid); }'",
                    "while ($true) { $l = [Console]::In.ReadLine(); if ($l -eq $null) { break }",
                    "  $p = [uint32]0; [void][NkFg]::GetWindowThreadProcessId([NkFg]::GetForegroundWindow(), [ref]$p)",
                    "  $path = ''; try { $path = (Get-Process -Id $p -ErrorAction Stop).Path } catch { }",
                    "  [Console]::Out.WriteLine(\"$p|$path\"); [Console]::Out.Flush() }"), StandardCharsets.UTF_8);
            foreground = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString()).start();
            fgIn = new java.io.BufferedWriter(new java.io.OutputStreamWriter(foreground.getOutputStream(), StandardCharsets.UTF_8));
            fgOut = new java.io.BufferedReader(new java.io.InputStreamReader(foreground.getInputStream(), StandardCharsets.UTF_8));
        }
        fgIn.write("?");
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
        log("  abas vistas pela extensão: " + harness.eval("chrome.tabs.query({}).then(t => JSON.stringify(t.map(x => ({u: x.url || null, p: x.pendingUrl || null, s: x.status}))))"));
        labTab = ((Number) waitValue("aba do laboratório", 20,
                () -> harness.eval("chrome.tabs.query({}).then(t => (t.find(x => (x.url || '').startsWith('" + BASE + "')) || {}).id || null)"))).intValue();
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
                edt(() -> {
                    window.frame().toBack();
                    return null;
                });
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            sleep(300);
        }
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
        profile = Files.createTempDirectory("nekkora-e2e001-chrome");
        // Perfil temporário sem o gerenciador de senhas do Chrome: o balão "Salvar senha?" não aparece nas evidências.
        Files.createDirectories(profile.resolve("Default"));
        Files.writeString(profile.resolve("Default").resolve("Preferences"),
                "{\"credentials_enable_service\": false, \"profile\": {\"password_manager_enabled\": false}}");
        testExtension = out.toPath().resolve("test-extension");
        copyExtension(ext.resolve("chrome-extension"), testExtension);
    }

    /** Cópia de homologação: idêntica, exceto pela origem exata do desafio declarada (permissão do próprio Chrome). */
    private static void copyExtension(Path from, Path to) throws IOException {
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
        hosts.addAll(List.of("https://the-internet.herokuapp.com/*", "https://quotes.toscrape.com/*", "https://www.saucedemo.com/*")); // sites públicos de treinamento escolhidos (pedido da usuária)
        m.put("host_permissions", hosts);
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
                "--disable-search-engine-choice-screen", "--lang=pt-BR", "--window-position=" + CHROME_X + ",0", "--window-size=740,1000",
                "--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE the-internet.herokuapp.com, EXCLUDE quotes.toscrape.com, EXCLUDE www.saucedemo.com, EXCLUDE localhost, EXCLUDE 127.0.0.1", "about:blank")
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
        guardForeground("clique");
        robot.mouseMove(r.x + r.width / 2, r.y + r.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        sleep(250);
    }

    private void focusStudio() throws Exception {
        edt(() -> {
            window.frame().toFront();
            return null;
        });
        Rectangle b = edt(() -> window.frame().getBounds());
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
        File f = new File(out, "SITES-" + name + ".png");
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

    private void log(String line) {
        System.out.println(line);
        log.add(line);
    }

    private void writeLog() {
        try (PrintWriter w = new PrintWriter(new File(out, "SITES-homologacao-log.txt"), StandardCharsets.UTF_8)) {
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
