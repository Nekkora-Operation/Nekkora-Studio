package com.nekkora.studio.app.evidence;

import com.nekkora.studio.app.StudioApplication;
import com.nekkora.studio.app.ui.StudioActions;
import com.nekkora.studio.bridge.BridgeServer;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.Automation;
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
import java.awt.Point;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Homologação REAL da STUDIO-002 no Windows: Studio real (janela, Bridge publicado no diretório padrão do usuário),
 * Chrome for Testing com a extensão carregada, Native Host instalado pelo script oficial, página do laboratório local.
 * O Studio é dirigido com teclado e mouse reais ({@link Robot}); o estado do navegador é conferido pela página
 * interna da extensão (CDP). Não é um teste automático: exige área de trabalho interativa.
 *
 * <p>Uso: {@code java -cp ... Studio002Homologation <saida> <repositorioDaExtensao>}.
 */
public final class Studio002Homologation {

    private static final int LAB_PORT = 18765;
    private static final String LAB = "http://127.0.0.1:" + LAB_PORT + "/lab/chrome002/continuous.html";
    private static final String SITE = "http://site-a.nekkora.test:" + LAB_PORT + "/lab/real001/site.html";

    private final File out;
    private final Path ext;
    private final Robot robot;
    private final List<String> log = new ArrayList<>();
    private int failures;
    private int checks;
    private StudioApplication.Window window;
    private BridgeServer bridge;
    private final AtomicBoolean exited = new AtomicBoolean();
    private Process chrome;
    private Process labServer;
    private Path profile;
    private Path testExtension;
    private Cdp cdp;
    private Cdp.Page harness;
    private Cdp.Page panel;
    private String extId;
    private int labTab;

    private Studio002Homologation(File out, Path ext) throws AWTException {
        this.out = out;
        this.ext = ext;
        this.robot = new Robot();
        robot.setAutoDelay(40);
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Studio002Homologation h = new Studio002Homologation(out, Path.of(args[1]).toAbsolutePath().normalize());
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

        // ---- S002-001: Studio abre sem Chrome, sem login
        bridge = BridgeServer.start(BridgeServer.Options.defaults(StudioApplication.VERSION));
        log("Bridge: " + bridge.status() + " transporte=" + bridge.transport());
        check("D-004 Bridge publicado por Unix Domain Socket no Windows", "unix".equals(bridge.transport()));
        window = edt(() -> StudioApplication.launch(Optional.of(NekkoraThemes.SAKURA_DARK), bridge, () -> {
            bridge.close();
            exited.set(true);
        }));
        edt(() -> {
            window.frame().setSize(1100, 1000);
            window.frame().setLocation(0, 0);
            window.frame().toFront();
            return null;
        });
        waitFor("janela do Studio", 10, () -> edt(() -> window.frame().isShowing()));
        sleep(800);
        check("S002-001 Studio abre sem Chrome e sem login", edt(() -> window.frame().getTitle().equals("Nekkora Studio")));
        check("S002-001 estado 'Aguardando conexão com a extensão'", statusText().equals("Navegador: aguardando conexão com a extensão"));
        captureScreen("01-studio-sem-chrome");

        // ---- Chrome com a extensão; conexão ligada pelo interruptor do painel
        launchChrome();
        Cdp.Target harnessTarget = cdp.open("chrome-extension://" + extId + "/src/ui/harness.html");
        harness = cdp.page(harnessTarget);
        waitFor("harness da extensão", 20, () -> harness.eval("!!(window.nekkora && window.nekkora.call)") == Boolean.TRUE);
        try {
            labTab = ((Number) waitValue("aba do laboratório", 20, () -> harness.eval("chrome.tabs.query({}).then(t => (t.find(x => (x.url || '').startsWith('" + LAB + "')) || {}).id || null)"))).intValue();
        } catch (IllegalStateException e) {
            log("abas vistas pela extensão: " + harness.eval("chrome.tabs.query({}).then(t => JSON.stringify(t.map(x => [x.id, x.url, x.pendingUrl, x.status])))"));
            throw e;
        }
        Cdp.Target panelTarget = cdp.open("chrome-extension://" + extId + "/src/ui/panel.html?tabId=" + labTab);
        panel = cdp.page(panelTarget);
        waitFor("painel carregado", 20, () -> panel.eval("!!document.getElementById('studio-enabled')") == Boolean.TRUE);
        waitFor("painel inicializado", 20, () -> !"verificando…".equals(panel.eval("document.getElementById('host-status').textContent")));
        panel.eval("document.getElementById('studio-enabled').click()"); // o interruptor do painel (gesto do usuário)
        try {
            waitFor("Studio conectado", 30, () -> statusText().startsWith("Navegador: conectado"));
        } catch (IllegalStateException e) {
            log("canal na extensão: " + harness.eval("window.nekkora.call('studio.link.get', {}).then(r => JSON.stringify(r))"));
            log("painel: " + panel.eval("document.getElementById('studio-state').textContent + ' / ' + document.getElementById('studio-enabled').checked"));
            throw e;
        }
        check("S002-003 conexão autenticada Studio ⇄ host ⇄ extensão", statusText().startsWith("Navegador: conectado (Nekkora Extension 0.4"));
        log("painel: " + panel.eval("document.getElementById('studio-state').textContent"));
        check("Painel da extensão mostra 'Conectada ao Nekkora Studio'", "Conectada ao Nekkora Studio".equals(panel.eval("document.getElementById('studio-state').textContent")));
        capturePage(panelTarget, panel, "02b-painel-extensao-conectado");
        activateLabTab();
        captureScreen("02-conectado");

        // ---- automação e sessões pelo teclado
        focusStudio();
        createAutomation();
        Automation a = edt(() -> window.studio().state().activeAutomation().orElseThrow());
        int stepsBefore = a.stepCount();
        long exportsBefore = exportedPageMaps();

        // ---- Gravar → diálogo → aba autorizada → Iniciar
        edt(() -> {
            window.studio().controller().selectSession(a.id(), a.sessions().get(0).id());
            return null;
        });
        startRecordingOn("Captura contínua", true);
        FloatingRecorderPanel fp = edt(() -> window.studio().recorderPresenter().panel());
        check("Gravar → Gravando somente após confirmação", "Gravando".equals(recordLabel()));
        check("Floating Recorder visível", floating() != null && edt(() -> floating().isShowing()));
        check("Floating Recorder não roubou o foco", edt(() -> !floating().isFocused() && window.frame().isActive()));
        check("S002-008 consumidor próprio na CaptureSession", consumers().contains("studio-recorder:"));
        captureScreen("04-floating-gravando");

        // ---- Pausa / Continuar (S002-011/012)
        click(edt(fp::pauseResumeButton));
        waitState(RecorderState.PAUSED);
        check("S002-011 pausa confirmada: consumidor liberado", !consumers().contains("studio-recorder:"));
        captureScreen("05-pausado");
        click(edt(fp::pauseResumeButton));
        waitState(RecorderState.RECORDING);
        waitFor("consumidor readquirido", 15, () -> consumers().contains("studio-recorder:"));
        check("S002-012 retomada confirmada com captura restabelecida", consumers().contains("studio-recorder:"));

        // ---- Modo (S002-013)
        click(edt(() -> fp.modeButton(RecordingMode.CAPTURE_ONLY)));
        waitFor("modo confirmado", 10, () -> edt(() -> window.studio().recorder().snapshot().mode() == RecordingMode.CAPTURE_ONLY
                && window.studio().recorder().snapshot().pendingMode() == null));
        check("S002-013 modo 'Capturar' confirmado pela extensão", modeInExtension().equals("CAPTURE_ONLY"));
        // ---- troca de sessão sem nova captura (17.3)
        String sessionBefore = sessionIdInCapture();
        edt(() -> {
            fp.sessionCombo().setSelectedIndex(1);
            return null;
        });
        waitFor("associação", 5, () -> edt(() -> a.sessions().get(1).id().equals(window.studio().recorder().snapshot().session())));
        check("Associação a outra sessão sem nova captura", sessionBefore.equals(sessionIdInCapture()));
        captureScreen("06-modo-e-sessao");

        // ---- mover, recolher e restaurar (S002-024)
        Point before = edt(() -> floating().getLocation());
        drag(before.x + 120, before.y + 20, before.x - 280, before.y - 220);
        Point after = edt(() -> floating().getLocation());
        check("S002-024 janela movida pelo cabeçalho", !before.equals(after));
        click(edt(fp::collapseButton));
        waitFor("recolhida", 5, () -> edt(() -> !floating().isVisible()));
        check("S002-024 recolher esconde o Recorder (gravação continua)", stateIs(RecorderState.RECORDING));
        click(recordButton());
        waitFor("restaurada", 5, () -> edt(() -> floating().isVisible()));
        check("S002-024 restaurada pelo botão Gravando do Studio", edt(() -> floating().isVisible()));
        check("Always on top ligado por padrão", edt(() -> floating().isAlwaysOnTop()));

        // ---- quatro temas (S002-025)
        for (NekkoraTheme t : NekkoraThemes.all()) {
            edt(() -> {
                ThemeManager.setTheme(t);
                return null;
            });
            sleep(700);
            captureWindow(floating(), "07-tema-" + t.id());
        }
        edt(() -> {
            ThemeManager.setTheme(NekkoraThemes.SAKURA_DARK);
            return null;
        });
        check("S002-025 quatro temas aplicados ao Floating Recorder", true);

        // ---- Finalizar sem exportar (S002-014)
        click(edt(fp::finishButton));
        waitState(RecorderState.COMPLETED);
        sleep(600);
        captureScreen("08-resumo-finalizado");
        check("S002-014 consumidor liberado após finalizar", !consumers().contains("studio-recorder:"));
        check("S002-014 nenhum PageMap exportado", exportedPageMaps() == exportsBefore);
        check("S002-029 nenhuma etapa artificial", edt(a::stepCount) == stepsBefore);
        clickButtonWithText(floating(), "Voltar ao Studio");
        waitState(RecorderState.IDLE);
        check("Volta ao Studio (Recorder oculto)", edt(() -> !floating().isVisible()) && "Gravar".equals(recordLabel()));

        // ---- coexistência com a inspeção manual (S002-009/010)
        panel.eval("document.getElementById('btn-inspect').click()");
        waitFor("inspeção ativa", 30, () -> "ACTIVE".equals(call("inspection.state", Map.of("tabId", labTab)).get("state")));
        startRecordingOn("Captura contínua", false);
        check("S002-009 Recorder e inspeção coexistem", consumers().contains("inspection") && consumers().contains("studio-recorder:"));
        click(edt(fp::pauseResumeButton));
        waitState(RecorderState.PAUSED);
        check("S002-009 pausa do Recorder preserva a inspeção", consumers().contains("inspection") && !consumers().contains("studio-recorder:"));
        captureScreen("09-coexistencia-pausado");
        click(edt(fp::finishButton));
        waitState(RecorderState.COMPLETED);
        check("S002-010 inspeção continua ativa após finalizar o Recorder", "ACTIVE".equals(call("inspection.state", Map.of("tabId", labTab)).get("state")));
        clickButtonWithText(floating(), "Voltar ao Studio");
        waitState(RecorderState.IDLE);
        panel.eval("document.getElementById('btn-inspect').click()");
        waitFor("inspeção desativada", 20, () -> "INACTIVE".equals(call("inspection.state", Map.of("tabId", labTab)).get("state")));

        // ---- permissão (cenário 24.4)
        permissionScenario();

        // ---- Chrome encerrado durante a gravação (S002-019)
        chromeClosedScenario();

        // ---- encerramento normal do Studio com gravação (18.1)
        normalExitScenario();

        // ---- encerramento ABRUPTO do Studio (S002-018) com outro consumidor ativo
        crashScenario();
    }

    // ============================================================================== cenários

    private void permissionScenario() throws Exception {
        siteAccess("http://site-a.nekkora.test/*", false); // o site começa SEM acesso (retido pelo usuário)
        sleep(1000);
        Cdp.Target site = cdp.open(SITE);
        sleep(2500);
        focusStudio();
        click(recordButton());
        NekkoraDialog d = waitDialog("Iniciar gravação");
        waitFor("abas carregadas", 15, () -> edt(() -> !radios(d).isEmpty()));
        NekkoraRadioButton denied = edt(() -> radios(d).stream().filter(r -> r.getText().contains("site-a.nekkora.test")).findFirst().orElse(null));
        check("S002-007 site sem autorização listado como 'Não autorizada' e desabilitado",
                denied != null && edt(() -> !denied.isEnabled() && denied.getText().contains("Não autorizada")));
        check("Orientação para autorizar pela extensão", edt(() -> texts(d).contains("Abra a Nekkora Extension no Chrome e conceda acesso ao site")));
        captureScreen("10-site-nao-autorizado");
        press(KeyEvent.VK_ESCAPE);
        waitClosed(d);

        siteAccess("http://site-a.nekkora.test/*", true); // controle "Acesso ao site" do Chrome (sem automatizar o diálogo)
        sleep(1500);
        cdp.activate(site);
        sleep(800);
        startRecordingOn("site-a.nekkora.test", false);
        check("S002-008 gravação em site autorizado pelo usuário", stateIs(RecorderState.RECORDING));
        siteAccess("http://site-a.nekkora.test/*", false);
        waitFor("captura interrompida", 20, () -> stateIs(RecorderState.DEGRADED));
        check("S002-027 revogação: Recorder interrompido (PERMISSION_REVOKED)",
                "PERMISSION_REVOKED".equals(edt(() -> window.studio().recorder().snapshot().reason())));
        captureScreen("11-permissao-revogada");
        FloatingRecorderPanel fp = edt(() -> window.studio().recorderPresenter().panel());
        click(edt(fp::finishButton));
        waitState(RecorderState.COMPLETED);
        check("Finalização registra a interrupção",
                edt(() -> window.studio().recorder().snapshot().summary().limitations().contains("INTERRUPTED")));
        clickButtonWithText(floating(), "Voltar ao Studio");
        waitState(RecorderState.IDLE);
        cdp.close(site);
        activateLabTab();
    }

    private void chromeClosedScenario() throws Exception {
        focusStudio();
        startRecordingOn("Captura contínua", false);
        harness.close();
        panel.close();
        cdp.close();
        chrome.destroy(); // o usuário fecha o Chrome
        chrome.waitFor();
        waitFor("degradado por perda do navegador", 30, () -> edt(() -> window.studio().recorder().snapshot().linkLost()));
        check("S002-019 Chrome encerrado: Recorder DEGRADED, nada dado como finalizado", stateIs(RecorderState.DEGRADED));
        check("S002-019 Studio volta a aguardar a extensão", statusText().startsWith("Navegador: aguardando conexão com a extensão"));
        sleep(500);
        captureScreen("12-chrome-encerrado");
        FloatingRecorderPanel fp = edt(() -> window.studio().recorderPresenter().panel());
        check("Opção 'Encerrar no Studio' oferecida", edt(() -> fp.abandonButton().isVisible()));
        click(edt(fp::abandonButton));
        waitState(RecorderState.ERROR);
        clickButtonWithText(floating(), "Voltar ao Studio");
        waitState(RecorderState.IDLE);
        // Chrome de volta com o mesmo perfil: a conexão volta sozinha (interruptor persistido).
        launchChrome();
        harness = cdp.page(cdp.open("chrome-extension://" + extId + "/src/ui/harness.html"));
        waitFor("harness", 20, () -> harness.eval("!!(window.nekkora && window.nekkora.call)") == Boolean.TRUE);
        labTab = ((Number) waitValue("aba do laboratório", 20, () -> harness.eval("chrome.tabs.query({}).then(t => (t.find(x => (x.url || '').startsWith('" + LAB + "')) || {}).id || null)"))).intValue();
        panel = cdp.page(cdp.open("chrome-extension://" + extId + "/src/ui/panel.html?tabId=" + labTab));
        waitFor("reconexão automática do Studio", 60, () -> statusText().startsWith("Navegador: conectado"));
        check("Reconexão automática após reabrir o Chrome", statusText().startsWith("Navegador: conectado"));
        activateLabTab();
    }

    private void normalExitScenario() throws Exception {
        focusStudio();
        startRecordingOn("Captura contínua", false);
        focusStudio();
        edt(() -> {
            SwingUtilities.invokeLater(() -> window.studio().controller().requestExit());
            return null;
        });
        NekkoraDialog unsaved = waitDialogOptional("Fechar sem salvar?", 5);
        if (unsaved != null) {
            clickButtonWithText(unsaved, "Fechar e descartar"); // aviso da STUDIO-001 (automações só em memória)
        }
        NekkoraDialog d = waitDialog("Há uma gravação técnica em andamento");
        captureScreen("13-sair-com-gravacao");
        clickButtonWithText(d, "Finalizar e sair");
        waitFor("Studio encerrado", 20, exited::get);
        Map<String, Object> rec = lastRecording();
        check("18.1 encerramento normal: gravação finalizada no navegador", rec != null && "COMPLETED".equals(rec.get("state")));
        check("18.1 consumidor liberado", !consumers().contains("studio-recorder:"));
        check("18.1 credencial invalidada (arquivo de descoberta removido)",
                !Files.exists(Path.of(System.getenv("LOCALAPPDATA"), "Nekkora", "Studio", "bridge", "studio-bridge.json")));
    }

    private void crashScenario() throws Exception {
        panel.eval("document.getElementById('btn-inspect').click()");
        waitFor("inspeção ativa", 30, () -> "ACTIVE".equals(call("inspection.state", Map.of("tabId", labTab)).get("state")));
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", System.getProperty("java.class.path"),
                StudioCrashProbe.class.getName(), "Captura contínua", "10000");
        pb.redirectErrorStream(true);
        Process probe = pb.start();
        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(probe.getInputStream(), StandardCharsets.UTF_8));
        String line;
        boolean ready = false;
        long deadline = System.currentTimeMillis() + 90_000;
        while (System.currentTimeMillis() < deadline && (line = r.readLine()) != null) {
            if (line.contains("READY")) {
                ready = true;
                break;
            }
        }
        check("Studio (processo separado) gravando com a inspeção ativa", ready && consumers().contains("studio-recorder:") && consumers().contains("inspection"));
        long t0 = System.currentTimeMillis();
        probe.destroyForcibly(); // encerramento abrupto: sem aviso, sem cancelamento
        probe.waitFor();
        waitFor("concessão expirada", 60, () -> {
            Map<String, Object> rec = lastRecording();
            return rec != null && "EXPIRED".equals(rec.get("state"));
        });
        long ms = System.currentTimeMillis() - t0;
        log("concessão expirada " + ms + " ms após a queda (concessão de 10000 ms)");
        check("S002-018 sem gravação órfã: consumidor do Recorder liberado", !consumers().contains("studio-recorder:"));
        check("S002-018 demais consumidores preservados (inspeção)", consumers().contains("inspection")
                && "ACTIVE".equals(call("inspection.state", Map.of("tabId", labTab)).get("state")));
        cdp.activate(cdp.targets().stream().filter(t -> t.url().startsWith(LAB)).findFirst().orElseThrow());
        sleep(800);
        captureScreen("14-studio-derrubado-concessao-expirada");
        panel.eval("document.getElementById('btn-inspect').click()");
    }

    // ============================================================================== Studio pelo teclado/mouse

    private void createAutomation() throws Exception {
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_N);
        NekkoraDialog d = waitDialog("Nova automação");
        typeIntoFocused("Consulta de clientes");
        press(KeyEvent.VK_ENTER);
        waitClosed(d);
        for (String s : List.of("Autenticação", "Consulta")) {
            press(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_N);
            d = waitDialog("Nova sessão");
            typeIntoFocused(s);
            press(KeyEvent.VK_ENTER);
            waitClosed(d);
        }
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_E);
        d = waitDialog("Nova etapa");
        typeIntoFocused("Preencher usuário");
        press(KeyEvent.VK_ENTER);
        waitClosed(d);
        check("Automação com 2 sessões e 1 etapa manual criada pelo teclado", edt(() -> {
            Automation a = window.studio().state().activeAutomation().orElseThrow();
            return a.sessions().size() == 2 && a.stepCount() == 1;
        }));
    }

    private void startRecordingOn(String tabText, boolean capture) throws Exception {
        focusStudio();
        click(recordButton());
        NekkoraDialog d = waitDialog("Iniciar gravação");
        waitFor("abas carregadas", 15, () -> edt(() -> radios(d).stream().anyMatch(b -> b.getText().contains(tabText) && b.isEnabled())));
        NekkoraRadioButton tab = edt(() -> radios(d).stream().filter(b -> b.getText().contains(tabText) && b.isEnabled()).findFirst().orElseThrow());
        click(tab);
        if (capture) {
            captureScreen("03-dialogo-iniciar-gravacao");
            String all = edt(() -> texts(d));
            check("S002-006 lista de abas sem URL completa", !all.contains("http://") && !all.contains("https://"));
        }
        clickButtonWithText(d, "Iniciar gravação");
        waitClosed(d);
        waitState(RecorderState.RECORDING);
        sleep(600);
    }

    private NekkoraToolbarButton recordButton() throws Exception {
        return edt(() -> find(window.frame().getContentPane(), NekkoraToolbarButton.class,
                b -> b.getAction() != null && StudioActions.RECORD.equals(b.getAction().getValue(com.nekkora.ui.action.NekkoraAction.ID))));
    }

    private String recordLabel() throws Exception {
        return edt(() -> String.valueOf(window.studio().view().actions().get(StudioActions.RECORD).getValue(javax.swing.Action.NAME)));
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
        waitFor("estado " + s, 30, () -> stateIs(s));
    }

    // ============================================================================== navegador (CDP)

    private void prepareEnvironment() throws Exception {
        Path hostJar = ext.resolve("java-core/native-host/target/nekkora-native-host.jar");
        run(List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ext.resolve("native-host/scripts/install-host.ps1").toString(), "-SkipBuild"));
        log("Native Host instalado pelo script oficial (install-host.ps1)");
        extId = run(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", hostJar.toString(),
                "com.nekkora.extension.host.tools.ExtensionId", ext.resolve("chrome-extension/manifest.json").toString())).trim();
        log("Extensão: " + extId);
        labServer = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar",
                ext.resolve("java-core/lab-server/target/nekkora-lab-server.jar").toString(), String.valueOf(LAB_PORT), ext.resolve("test-lab/pages").toString())
                .redirectErrorStream(true).redirectOutput(new File(out, "lab-server.log")).start();
        profile = Files.createTempDirectory("nekkora-s002-chrome");
        // Cópia de homologação da extensão: idêntica, exceto pelo site de teste declarado em host_permissions. O Chrome
        // só permite conceder/reter pelo controle "Acesso ao site" permissões DECLARADAS (como na homologação REAL-001):
        // o site começa retido, é concedido por esse controle e revogado de novo — sem automatizar diálogos.
        testExtension = out.toPath().resolve("test-extension");
        copyExtension(ext.resolve("chrome-extension"), testExtension);
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        waitFor("laboratório respondendo", 20, () -> http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(LAB)).build(),
                java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode() == 200);
    }

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

    private void launchChrome() throws Exception {
        Path chromeExe;
        try (Stream<Path> s = Files.walk(ext.resolve(".tools/chrome-for-testing"), 4)) {
            chromeExe = s.filter(p -> p.getFileName().toString().equals("chrome.exe")).findFirst().orElseThrow();
        }
        int cdpPort;
        try (java.net.ServerSocket free = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            cdpPort = free.getLocalPort(); // porta livre a cada abertura (a anterior pode estar presa por um Chrome encerrando)
        }
        chrome = new ProcessBuilder(chromeExe.toString(), "--remote-debugging-port=" + cdpPort, "--user-data-dir=" + profile,
                // O executável fica em .tools: o sandbox do serviço de rede não o acessa e o serviço reinicia (primeira
                // navegação pendente). Desligado SÓ esse sandbox, só neste perfil temporário de homologação.
                "--disable-features=NetworkServiceSandbox",
                "--load-extension=" + testExtension, "--no-first-run", "--no-default-browser-check", "--disable-search-engine-choice-screen",
                "--lang=pt-BR", "--window-position=880,0", "--window-size=1040,1000",
                "--host-resolver-rules=MAP site-a.nekkora.test 127.0.0.1", "about:blank").redirectErrorStream(true).redirectOutput(new File(out, "chrome.log")).start();
        cdp = new Cdp(cdpPort);
        waitFor("Chrome com DevTools", 30, () -> {
            try {
                return !cdp.targets().isEmpty();
            } catch (Exception e) {
                return false;
            }
        });
        sleep(1500);
        cdp.open(LAB); // a página do laboratório, aberta como o usuário abriria uma aba
        log("Chrome for Testing: " + chromeExe.getParent().getParent().getFileName());
        sleep(1500);
    }

    private void activateLabTab() throws Exception {
        cdp.activate(cdp.targets().stream().filter(t -> t.url() != null && t.url().startsWith(LAB)).findFirst().orElseThrow());
        sleep(500);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String cmd, Map<String, Object> args) throws Exception {
        Object r = harness.eval("window.nekkora.call(" + quote(cmd) + ", " + Json.write(args) + ")");
        Map<String, Object> m = (Map<String, Object>) r;
        if (m == null || !Boolean.TRUE.equals(m.get("ok"))) {
            throw new IllegalStateException(cmd + " falhou: " + m);
        }
        Object result = m.get("result");
        return result instanceof Map ? (Map<String, Object>) result : Map.of("value", String.valueOf(result));
    }

    private String consumers() throws Exception {
        Map<String, Object> s = call("session.status", Map.of("tabId", labTab));
        Object session = s.get("session");
        return session instanceof Map<?, ?> m ? String.valueOf(m.get("consumers")) : "[]";
    }

    private String sessionIdInCapture() throws Exception {
        Map<String, Object> s = call("session.status", Map.of("tabId", labTab));
        Object session = s.get("session");
        return session instanceof Map<?, ?> m ? String.valueOf(m.get("sessionId")) : "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> lastRecording() throws Exception {
        Object r = harness.eval("window.nekkora.call('studio.recordings', {})");
        List<Object> list = (List<Object>) ((Map<String, Object>) r).get("result");
        return list.isEmpty() ? null : (Map<String, Object>) list.get(list.size() - 1);
    }

    private String modeInExtension() throws Exception {
        Map<String, Object> rec = lastRecording();
        return rec == null ? "" : String.valueOf(rec.get("mode"));
    }

    /** "Acesso ao site" do Chrome (chrome://extensions), como na homologação REAL-001 — sem automatizar o diálogo de permissão. */
    private void siteAccess(String pattern, boolean granted) throws Exception {
        Cdp.Target t = cdp.open("chrome://extensions/?id=" + extId);
        try (Cdp.Page p = cdp.page(t)) {
            waitFor("chrome://extensions", 15, () -> p.eval("!!(window.chrome && chrome.developerPrivate)") == Boolean.TRUE);
            p.eval("new Promise((ok, ko) => chrome.developerPrivate." + (granted ? "addHostPermission" : "removeHostPermission") + "(" + quote(extId) + ", "
                    + quote(pattern) + ", () => chrome.runtime.lastError ? ko(chrome.runtime.lastError.message) : ok(true)))");
        }
        cdp.close(t);
        log("Acesso ao site " + pattern + " → " + (granted ? "concedido" : "revogado") + " pelo controle do Chrome");
    }

    private long exportedPageMaps() throws IOException {
        Path dir = Path.of(System.getenv("LOCALAPPDATA"), "Nekkora", "ExtensionChrome", "pagemaps");
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).count();
        }
    }

    // ============================================================================== apoio

    private NekkoraDialog waitDialog(String title) throws Exception {
        NekkoraDialog d = waitDialogOptional(title, 15);
        if (d == null) throw new IllegalStateException("diálogo não apareceu: " + title);
        return d;
    }

    private NekkoraDialog waitDialogOptional(String title, int seconds) throws Exception {
        long end = System.currentTimeMillis() + seconds * 1000L;
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
        return null;
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

    private void drag(int fromX, int fromY, int toX, int toY) {
        robot.mouseMove(fromX, fromY);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        for (int i = 1; i <= 10; i++) {
            robot.mouseMove(fromX + (toX - fromX) * i / 10, fromY + (toY - fromY) * i / 10);
            sleep(30);
        }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        sleep(300);
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
        sleep(150);
    }

    private void captureScreen(String name) throws Exception {
        robot.waitForIdle();
        sleep(300);
        Rectangle r = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        save(robot.createScreenCapture(r), name);
    }

    /** Captura de uma página do Chrome pelo DevTools (painel da extensão aberto como aba). */
    private void capturePage(Cdp.Target t, Cdp.Page p, String name) throws Exception {
        cdp.activate(t);
        sleep(600);
        Map<String, Object> r = p.send("Page.captureScreenshot", Json.obj().put("format", "png").build());
        byte[] png = java.util.Base64.getDecoder().decode(Json.string(Json.object(r, "result"), "data"));
        Files.write(new File(out, "S002-" + name + ".png").toPath(), png);
        log("captura S002-" + name + ".png (painel da extensão)");
    }

    private void captureWindow(Window w, String name) throws Exception {
        robot.waitForIdle();
        sleep(250);
        save(robot.createScreenCapture(edt(w::getBounds)), name);
    }

    private void save(BufferedImage img, String name) throws IOException {
        File f = new File(out, "S002-" + name + ".png");
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
        if (c instanceof JTextComponent t && t.getText() != null) b.append(t.getText()).append('\n'); // parágrafos (avisos)
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
        try (PrintWriter w = new PrintWriter(new File(out, "S002-homologacao-log.txt"), StandardCharsets.UTF_8)) {
            log.forEach(w::println);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void teardown() {
        try {
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

    private static String quote(String s) {
        return Json.write(s);
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
        throw new IllegalStateException("tempo esgotado aguardando: " + what + (last == null ? "" : " (" + last + ")"));
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
            return c.call(); // reentrante: já na EDT
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
