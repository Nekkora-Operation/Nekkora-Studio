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
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepOrigin;
import com.nekkora.studio.recorder.RecorderState;
import com.nekkora.studio.recorder.RecordingMode;
import com.nekkora.studio.ui.recorder.FloatingRecorderPanel;
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
 * Homologação REAL da STUDIO-003 no Windows: Studio real (janela, Bridge no diretório padrão do usuário), Chrome for
 * Testing com a extensão, Native Host instalado pelo script oficial e páginas do laboratório local. As interações na
 * página são feitas com MOUSE e TECLADO reais do sistema operacional ({@link Robot}) — as coordenadas dos elementos vêm
 * do DevTools, só para mirar o cursor. O Studio também é dirigido pelo Robot. Exige área de trabalho interativa.
 *
 * <p>Uso: {@code java -cp ... Studio003Homologation <saida> <repositorioDaExtensao>}.
 */
public final class Studio003Homologation {

    private static final int LAB_PORT = 18765;
    private static final String BASE = "http://127.0.0.1:" + LAB_PORT + "/lab/studio003/";
    private static final String SITE = "http://site-a.nekkora.test:" + LAB_PORT + "/lab/studio003/consulta.html";
    /** Valores digitados na homologação (fictícios): NUNCA podem aparecer em etapas, logs, armazenamento ou tela. */
    private static final String USER = "anateste";
    private static final String PASSWORD = "Senha2026Teste";
    private static final int CHROME_X = 880;

    private final File out;
    private final Path ext;
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

    private Studio003Homologation(File out, Path ext) throws AWTException {
        this.out = out;
        this.ext = ext;
        this.robot = new Robot();
        robot.setAutoDelay(25);
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Studio003Homologation h = new Studio003Homologation(out, Path.of(args[1]).toAbsolutePath().normalize());
        int code = 1;
        try {
            h.run();
            code = h.failures == 0 ? 0 : 2;
        } catch (Throwable t) {
            h.log("ERRO " + t);
            for (StackTraceElement s : t.getStackTrace()) h.log("    at " + s);
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
        log("Tela: " + GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds() + " · java " + System.getProperty("java.version"));
        prepareEnvironment();
        bridge = BridgeServer.start(BridgeServer.Options.defaults(StudioApplication.VERSION));
        window = edt(() -> StudioApplication.launch(Optional.of(NekkoraThemes.SAKURA_DARK), bridge, bridge::close));
        edt(() -> {
            window.frame().setSize(870, 1000);
            window.frame().setLocation(0, 0);
            window.frame().toFront();
            return null;
        });
        waitFor("janela do Studio", 10, () -> edt(() -> window.frame().isShowing()));
        launchChrome(BASE + "login.html");
        connect();

        // ---- automação "Consulta de clientes" com as sessões Autenticação e Consulta, pelo teclado
        focusStudio();
        createAutomation();
        Automation a = edt(() -> window.studio().state().activeAutomation().orElseThrow());
        LogicalSession auth = a.sessions().get(0);
        LogicalSession consulta = a.sessions().get(1);

        // ================================================================ Cenário 1 — Login
        edt(() -> {
            window.studio().controller().selectSession(a.id(), auth.id());
            return null;
        });
        startRecordingOn("Login de teste", RecordingMode.CAPTURE_AND_INTERACT);
        FloatingRecorderPanel fp = edt(() -> window.studio().recorderPresenter().panel());
        moveFloatingAway();
        check("Floating Recorder não roubou o foco do Studio", edt(() -> !floating().isFocused()));
        clickInPage("document.getElementById('usuario')");
        typeText(USER);
        clickInPage("document.getElementById('senha')");
        typeText(PASSWORD);
        clickInPage("document.getElementById('entrar')");
        waitFor("3 etapas na sessão Autenticação", 30, () -> edt(auth::stepCount) == 3);
        check("Cenário 1: Preencher Usuário, Preencher Senha, Clicar em Entrar",
                names(auth).equals(List.of("Preencher campo Usuário", "Preencher campo Senha", "Clicar no botão Entrar")));
        AutomationStep senha = edt(() -> auth.steps().get(1));
        check("S003-013 senha sem valor literal no domínio", edt(() -> senha.parameters().isEmpty() && senha.element().orElseThrow().sensitive()));
        check("S003-034 etapas RECORDED pelo StepIntake", edt(() -> auth.steps().stream().allMatch(s -> s.provenance().origin() == StepOrigin.RECORDED)));
        AutomationStep entrar = edt(() -> auth.steps().get(2));
        check("S003-026 'Entrar' com localizador validado", edt(() -> entrar.element().orElseThrow().preferredLocator().isPresent()));
        check("S003-020 envio do formulário como evidência do clique (sem segundo clique)", edt(() ->
                entrar.provenance().recordingObservation().orElseThrow().evidence().contains("FORM_SUBMITTED")));
        check("S003-017 Floating Recorder com a contagem real", edt(() -> texts(fp)).contains("3 registradas"));
        check("S003-004 a página seguiu para a consulta (ação executada)", waitPage("consulta.html"));
        captureStudio("01-login-etapas");

        // ================================================================ Cenário 6/2 — Sessão Consulta
        edt(() -> {
            fp.sessionCombo().setSelectedItem(consulta);
            return null;
        });
        waitFor("sessão Consulta associada", 10, () -> edt(() -> consulta.id().equals(window.studio().recorder().snapshot().session())));
        sleep(600);
        clickInPage("document.getElementById('menu-clientes')");
        clickInPage("document.getElementById('estado')");
        sleep(400);
        press(KeyEvent.VK_DOWN);
        press(KeyEvent.VK_ENTER); // escolha pelo teclado no popup nativo do select
        clickInPage("document.getElementById('pesquisa')");
        typeText("silva");
        clickInPage("document.getElementById('pesquisar')");
        waitFor("4 etapas na sessão Consulta", 30, () -> edt(consulta::stepCount) == 4);
        check("Cenário 2/6: Clicar em Clientes, Selecionar São Paulo, Preencher Pesquisa, Clicar em Pesquisar na sessão Consulta",
                names(consulta).equals(List.of("Clicar no link Clientes", "Selecionar São Paulo no campo Estado", "Preencher campo Pesquisa",
                        "Clicar no botão Pesquisar")));
        check("S003-033 nenhuma etapa da Consulta caiu na Autenticação", edt(auth::stepCount) == 3);
        AutomationStep select = edt(() -> consulta.steps().get(1));
        check("S003-016 select e option em UMA etapa com a opção como parâmetro", edt(() ->
                "São Paulo".equals(select.parameters().option().orElseThrow().label()) && "SP".equals(select.parameters().option().orElseThrow().value())));
        selectStep(a, consulta, select);
        captureStudio("02-consulta-detalhes-select");

        // ================================================================ Cenário 3 — CAPTURE_ONLY
        click(edt(() -> fp.modeButton(RecordingMode.CAPTURE_ONLY)));
        waitFor("modo Capturar confirmado", 10, () -> edt(() -> window.studio().recorder().snapshot().mode() == RecordingMode.CAPTURE_ONLY
                && window.studio().recorder().snapshot().pendingMode() == null));
        String before = String.valueOf(page.eval("document.getElementById('linhas').textContent"));
        clickInPage("document.getElementById('adicionar')");
        waitFor("proposta registrada", 20, () -> edt(consulta::stepCount) == 5);
        check("Cenário 3/S003-003: CAPTURE_ONLY não executou a ação", before.equals(String.valueOf(page.eval("document.getElementById('linhas').textContent"))));
        AutomationStep proposta = edt(() -> consulta.steps().get(4));
        check("Cenário 3: etapa proposta (não executada)", edt(() -> proposta.provenance().recordingObservation().orElseThrow().observation()
                == RecordingObservation.Observation.PROPOSED && "Clicar no botão Adicionar linha".equals(proposta.name())));
        selectStep(a, consulta, proposta);
        captureStudio("03-capture-only-proposta");
        click(edt(() -> fp.modeButton(RecordingMode.CAPTURE_AND_INTERACT)));
        waitFor("modo Capturar e interagir", 10, () -> edt(() -> window.studio().recorder().snapshot().mode() == RecordingMode.CAPTURE_AND_INTERACT
                && window.studio().recorder().snapshot().pendingMode() == null));

        // ---- programático e ambíguo (S003-043 / S003-027)
        int beforeProg = edt(consulta::stepCount);
        clickInPage("document.getElementById('automatico')");
        sleep(1500);
        check("S003-043 eventos programáticos não viram etapas (só o clique real)", edt(consulta::stepCount) == beforeProg + 1);
        clickInPage("document.getElementById('detalhes')");
        waitFor("ambíguo registrado", 15, () -> edt(consulta::stepCount) == beforeProg + 2);
        AutomationStep amb = edt(() -> consulta.steps().get(beforeProg + 1));
        check("S003-027 alvo ambíguo marcado como não classificado, sem identidade inventada", edt(() -> amb.actionType().name().equals("UNKNOWN")
                && amb.element().orElseThrow().obstacles().stream().anyMatch(o -> o.type().equals("AMBIGUOUS_TARGET"))));

        // ---- finalizar: resumo com etapas confirmadas, sem PageMap
        click(edt(fp::finishButton));
        waitState(RecorderState.COMPLETED);
        sleep(600);
        check("Finalizar: captura de ações completa e PageMap não exportado", edt(() -> {
            var s = window.studio().recorder().snapshot().summary();
            return s != null && s.actionsCaptured() && !s.pageMapExported();
        }));
        captureWindow(floating(), "04-resumo-finalizado");
        clickButtonWithText(floating(), "Voltar ao Studio");
        waitState(RecorderState.IDLE);

        // ================================================================ Cenários 4 e 5 — iframe, Shadow DOM e SPA
        LogicalSession comp = newSession("Componentes");
        edt(() -> {
            window.studio().controller().selectSession(a.id(), comp.id());
            return null;
        });
        navigate(BASE + "frames.html?externo=" + java.net.URLEncoder.encode("http://site-x.nekkora.test:" + LAB_PORT + "/lab/studio003/frame-form.html",
                StandardCharsets.UTF_8));
        startRecordingOn("Formulário em iframe", RecordingMode.CAPTURE_AND_INTERACT);
        moveFloatingAway();
        clickInPage("document.getElementById('cadastro').contentDocument.getElementById('nome')", "document.getElementById('cadastro')");
        typeText("Maria");
        clickInPage("document.getElementById('cadastro').contentDocument.getElementById('salvar')", "document.getElementById('cadastro')");
        clickInPage("document.getElementById('componente').shadowRoot.getElementById('usuario')");
        typeText("comp");
        clickInPage("document.getElementById('componente').shadowRoot.querySelector('#entrar .icone')");
        waitFor("4 etapas de iframe/shadow", 30, () -> edt(comp::stepCount) == 4);
        check("Cenário 4: etapas do iframe com trajetória que entra no iframe", edt(() -> comp.steps().get(0).element().orElseThrow().accessPath().stream()
                .anyMatch(s -> s.type() == AccessStep.Type.IFRAME)));
        check("S003-024: Shadow DOM com trajetória pelo shadow root; clique no ícone identifica o botão", edt(() -> {
            AutomationStep btn = comp.steps().get(3);
            return btn.element().orElseThrow().accessPath().stream().anyMatch(s -> s.type() == AccessStep.Type.SHADOW_ROOT)
                    && "BUTTON".equals(btn.element().orElseThrow().semanticKind());
        }));
        selectStep(a, comp, edt(() -> comp.steps().get(0)));
        captureStudio("05-iframe-trajetoria");
        check("S003-025 iframe sem permissão: cobertura parcial declarada",
                edt(() -> "PARTIAL".equals(window.studio().recorder().snapshot().progress().coverage())));

        // SPA: mesma gravação, navegação pela própria página
        navigateByUser(BASE + "spa.html");
        clickInPage("document.getElementById('rota-clientes')");
        clickInPage("document.getElementById('filtro')");
        typeText("silva");
        clickInPage("document.getElementById('filtrar')");
        waitFor("etapas da SPA", 30, () -> edt(comp::stepCount) == 7);
        check("Cenário 5: ações antes e depois da troca de rota em ordem", names(comp).subList(4, 7)
                .equals(List.of("Clicar no botão Clientes", "Preencher campo Filtro", "Clicar no botão Filtrar")));

        // ================================================================ Cenário 7 — falhas
        // Desconexão: o usuário desliga e religa a conexão no painel; nada se duplica.
        int beforeDrop = edt(comp::stepCount);
        panel.eval("document.getElementById('studio-enabled').click()");
        waitFor("Recorder degradado pela perda do navegador", 30, () -> edt(() -> window.studio().recorder().snapshot().linkLost()));
        check("Cenário 7: desconexão mostrada como estado real", stateIs(RecorderState.DEGRADED));
        captureStudio("06-desconectado");
        panel.eval("document.getElementById('studio-enabled').click()");
        waitFor("reconexão e recuperação", 60, () -> stateIs(RecorderState.RECORDING));
        clickInPage("document.getElementById('filtrar')");
        waitFor("etapa depois da reconexão", 30, () -> edt(comp::stepCount) == beforeDrop + 1);
        sleep(1500);
        check("S003-029/030 após reconexão nenhuma etapa duplicada", edt(comp::stepCount) == beforeDrop + 1);
        // Fechamento da aba durante a gravação.
        cdp.close(labTarget);
        waitFor("aba fechada", 30, () -> stateIs(RecorderState.DEGRADED) && "TAB_CLOSED".equals(edt(() -> window.studio().recorder().snapshot().reason())));
        check("Cenário 7: aba fechada mostrada como estado real (TAB_CLOSED)", true);
        captureStudio("07-aba-fechada");
        click(edt(fp::finishButton));
        waitState(RecorderState.COMPLETED);
        clickButtonWithText(floating(), "Voltar ao Studio");
        waitState(RecorderState.IDLE);

        // Revogação de permissão (site-a) e cancelamento: só as etapas DESTA gravação somem.
        labTarget = cdp.open(SITE);
        page = cdp.page(labTarget);
        waitFor("site-a carregado", 20, () -> "complete".equals(page.eval("document.readyState")));
        sleep(1500);
        int total = edt(a::stepCount);
        startRecordingOn("site-a.nekkora.test", RecordingMode.CAPTURE_AND_INTERACT);
        moveFloatingAway();
        clickInPage("document.getElementById('exportar')");
        waitFor("etapa no site-a", 20, () -> edt(a::stepCount) == total + 1);
        siteAccess("http://site-a.nekkora.test/*", false);
        waitFor("captura interrompida pela revogação", 30, () -> stateIs(RecorderState.DEGRADED));
        check("Cenário 7 / S003-040: revogação → DEGRADED (PERMISSION_REVOKED)",
                "PERMISSION_REVOKED".equals(edt(() -> window.studio().recorder().snapshot().reason())));
        captureStudio("08-permissao-revogada");
        siteAccess("http://site-a.nekkora.test/*", true);
        click(edt(fp::cancelButton));
        waitState(RecorderState.CANCELLED);
        waitFor("etapas da gravação cancelada descartadas", 15, () -> edt(a::stepCount) == total);
        check("S003-038 cancelar descarta só a gravação cancelada (as anteriores ficam)", edt(a::stepCount) == total && edt(auth::stepCount) == 3);
        clickButtonWithText(floating(), "Voltar ao Studio");
        waitState(RecorderState.IDLE);

        // ================================================================ privacidade e IDs
        selectStep(a, auth, edt(() -> auth.steps().get(1)));
        String screen = edt(() -> texts(window.frame().getContentPane()));
        check("S003-036 nenhum identificador interno na tela", !screen.matches("(?s).*(rec-[0-9a-f]{8}|tgt-|ev-[0-9]|route-|cap-[0-9a-z]|doc:).*"));
        String storage = String.valueOf(harness.eval("chrome.storage.session.get(null).then(x => JSON.stringify(x))"));
        String domain = edt(() -> {
            StringBuilder b = new StringBuilder();
            for (LogicalSession s : a.sessions()) {
                for (AutomationStep st : s.steps()) {
                    b.append(st.name()).append(st.description()).append(st.parameters().value().map(v -> v.display()).orElse(""))
                            .append(st.observedOutcome()).append(st.element().map(e -> e.accessibleName() + e.htmlId().orElse("")).orElse(""));
                }
            }
            return b.toString();
        });
        String hostLogs = hostLogs();
        for (String secret : List.of(PASSWORD, USER)) {
            check("S003-037 '" + secret.charAt(0) + "…' ausente do domínio, da tela, do armazenamento da extensão e dos logs do host",
                    !domain.contains(secret) && !screen.contains(secret) && !storage.contains(secret) && !hostLogs.contains(secret));
        }
        captureStudio("09-detalhes-senha-sem-valor");

        // ---- quatro temas do Floating Recorder com etapas (S002-025 preservado)
        startRecordingOn("Consulta de clientes", RecordingMode.CAPTURE_AND_INTERACT);
        moveFloatingAway();
        for (NekkoraTheme t : NekkoraThemes.all()) {
            edt(() -> {
                ThemeManager.setTheme(t);
                return null;
            });
            sleep(700);
            captureWindow(floating(), "10-tema-" + t.id());
        }
        edt(() -> {
            ThemeManager.setTheme(NekkoraThemes.SAKURA_DARK);
            return null;
        });
        click(edt(fp::cancelButton));
        waitState(RecorderState.CANCELLED);
        clickButtonWithText(floating(), "Voltar ao Studio");
        captureStudio("11-automacao-final");
        log("Automação final:");
        for (LogicalSession s : edt(a::sessions)) {
            log("  Sessão " + s.name() + ": " + names(s));
        }
    }

    // ============================================================================== navegador

    private void connect() throws Exception {
        Cdp.Target harnessTarget = cdp.open("chrome-extension://" + extId + "/src/ui/harness.html");
        harness = cdp.page(harnessTarget);
        waitFor("harness da extensão", 20, () -> harness.eval("!!(window.nekkora && window.nekkora.call)") == Boolean.TRUE);
        labTab = ((Number) waitValue("aba do laboratório", 20,
                () -> harness.eval("chrome.tabs.query({}).then(t => (t.find(x => (x.url || '').startsWith('" + BASE + "')) || {}).id || null)"))).intValue();
        Cdp.Target panelTarget = cdp.open("chrome-extension://" + extId + "/src/ui/panel.html?tabId=" + labTab);
        panel = cdp.page(panelTarget);
        waitFor("painel", 20, () -> panel.eval("!!document.getElementById('studio-enabled')") == Boolean.TRUE);
        waitFor("painel inicializado", 20, () -> !"verificando…".equals(panel.eval("document.getElementById('host-status').textContent")));
        panel.eval("document.getElementById('studio-enabled').click()");
        waitFor("Studio conectado", 30, () -> statusText().startsWith("Navegador: conectado"));
        check("Conexão autenticada Studio ⇄ host ⇄ extensão", statusText().startsWith("Navegador: conectado (Nekkora Extension 0.4"));
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
        waitPage(url.substring(url.lastIndexOf('/') + 1));
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
        int x = (int) Math.round(((Number) xy.get(0)).doubleValue());
        int y = (int) Math.round(((Number) xy.get(1)).doubleValue());
        robot.mouseMove(x, y);
        sleep(120);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        sleep(500);
    }

    /** Dá o foco à janela do Chrome com um clique na barra de endereço (fora da página; nenhuma aba é trocada). */
    private void focusChrome() {
        robot.mouseMove(CHROME_X + 400, 63);
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
        profile = Files.createTempDirectory("nekkora-s003-chrome");
        // Perfil temporário sem o gerenciador de senhas do Chrome: o balão "Salvar senha?" não aparece nas evidências.
        Files.createDirectories(profile.resolve("Default"));
        Files.writeString(profile.resolve("Default").resolve("Preferences"),
                "{\"credentials_enable_service\": false, \"profile\": {\"password_manager_enabled\": false}}");
        testExtension = out.toPath().resolve("test-extension");
        copyExtension(ext.resolve("chrome-extension"), testExtension);
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        waitFor("laboratório respondendo", 20, () -> http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(BASE + "login.html")).build(),
                java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode() == 200);
    }

    /** Cópia de homologação: idêntica, exceto pelo site de teste declarado (concedido/revogado pelo controle do Chrome). */
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
        hosts.add("http://site-a.nekkora.test/*");
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
        chrome = new ProcessBuilder(chromeExe.toString(), "--remote-debugging-port=" + cdpPort, "--user-data-dir=" + profile,
                "--disable-features=NetworkServiceSandbox", "--load-extension=" + testExtension, "--no-first-run", "--no-default-browser-check",
                "--disable-search-engine-choice-screen", "--lang=pt-BR", "--window-position=" + CHROME_X + ",0", "--window-size=1040,1000",
                "--host-resolver-rules=MAP site-a.nekkora.test 127.0.0.1,MAP site-x.nekkora.test 127.0.0.1", "about:blank")
                .redirectErrorStream(true).redirectOutput(new File(out, "chrome.log")).start();
        cdp = new Cdp(cdpPort);
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
        Path dir = Path.of(System.getenv("LOCALAPPDATA"), "Nekkora", "ExtensionChrome", "logs");
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
        typeIntoFocused("Consulta de clientes");
        press(KeyEvent.VK_ENTER);
        waitClosed(d);
        for (String s : List.of("Autenticação", "Consulta")) {
            newSession(s);
        }
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
        Rectangle r = edt(() -> new Rectangle(c.getLocationOnScreen(), c.getSize()));
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
        for (int k : keys) robot.keyPress(k);
        for (int i = keys.length - 1; i >= 0; i--) robot.keyRelease(keys[i]);
        robot.waitForIdle();
        sleep(200);
    }

    /** Captura SÓ da janela do Studio (com o Floating Recorder por cima): nada da área de trabalho. */
    private void captureStudio(String name) throws Exception {
        robot.waitForIdle();
        sleep(400);
        Rectangle studio = edt(() -> window.frame().getBounds());
        Rectangle chromeArea = new Rectangle(CHROME_X, 0, 1040, 1000);
        Rectangle both = studio.union(chromeArea).intersection(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
        save(robot.createScreenCapture(both), name);
    }

    private void captureWindow(Window w, String name) throws Exception {
        robot.waitForIdle();
        sleep(250);
        save(robot.createScreenCapture(edt(w::getBounds)), name);
    }

    private void save(BufferedImage img, String name) throws IOException {
        File f = new File(out, "S003-" + name + ".png");
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
        try (PrintWriter w = new PrintWriter(new File(out, "S003-homologacao-log.txt"), StandardCharsets.UTF_8)) {
            log.forEach(w::println);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void teardown() {
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
