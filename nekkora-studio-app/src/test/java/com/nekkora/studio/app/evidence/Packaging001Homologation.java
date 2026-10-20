package com.nekkora.studio.app.evidence;

import com.nekkora.studio.ai.quality.QualityIssue;
import com.nekkora.studio.app.StudioApplication;
import com.nekkora.studio.app.StudioEnvironment;
import com.nekkora.studio.bridge.BridgeServer;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.ui.develop.DevelopmentPanel;
import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.dialog.NekkoraDialog;
import java.awt.AWTException;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
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
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * NEKKORA-PACKAGING-001 (G5) — homologação REAL pela interface, operada pelo Claude como TEST_AGENT: Studio de verdade,
 * mouse/teclado do sistema (Robot), Python 3.12 do sistema SEM nekkora_rpa, nenhum clone nem checkout do framework no
 * caminho e NEKKORA_PYTHON ausente. Dois projetos reais já homologados (cópias): the-internet (execução única 1.1.0,
 * {@code nekkora-rpa==0.2.0}) e a lista da Wikipédia (lista → planilha 1.2.0, {@code nekkora-rpa[excel]==0.3.0}).
 *
 * <p>Modo {@code principal}: ambiente ausente → plano → Cancelar (nada instalado) → Preparar com confirmação → V4/V5 com a
 * versão exata; cancelamento durante a instalação; venv danificada; Python indicado com versão divergente; Python sem o
 * pacote recusado; Python global intocado. Modo {@code rede}: sem rede (proxy morto) → diagnóstico claro, nada gravado.
 */
public final class Packaging001Homologation {

    private final File out;
    private final Path base;
    private final String mode;
    private final Path studioData;
    private final Robot robot;
    private final List<String> log = new ArrayList<>();
    private int failures;
    private int checks;
    private StudioApplication.Window window;
    private BridgeServer bridge;

    private Packaging001Homologation(File out, Path base, String mode) throws AWTException {
        this.out = out;
        this.base = base;
        this.mode = mode;
        this.studioData = base.resolve("dados do Studio");
        this.robot = new Robot();
        robot.setAutoDelay(25);
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Packaging001Homologation h = new Packaging001Homologation(out, Path.of(args[1]), args.length > 2 ? args[2] : "principal");
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

    // ============================================================================== roteiro

    private static final String ENV = "ENVIRONMENT_NOT_READY";
    private static final String CONFIRM = "Preparar o ambiente Python da automação?";

    private Path systemPython;

    private void run() throws Exception {
        log("Tela: " + GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds() + " · java " + System.getProperty("java.version")
                + " · modo " + mode);
        log("Operador: Claude como AGENTE DE TESTE (TEST_AGENT) · ator do Studio: " + System.getProperty("nekkora.studio.decisionActor"));
        log("Área isolada: " + base + " (espaço, acento e travessão no caminho)");
        check("pré: NEKKORA_PYTHON ausente (nenhum Python de desenvolvimento no caminho)", System.getenv("NEKKORA_PYTHON") == null);
        systemPython = Path.of(cmd(List.of("py", "-3.12", "-c", "import sys; print(sys.executable)")).strip());
        log("Python do sistema: " + systemPython + " · " + cmd(List.of(systemPython.toString(), "--version")).strip());
        String probe = cmdAny(List.of(systemPython.toString(), "-c", "import nekkora_rpa"));
        check("pré: o Python do sistema NÃO tem nekkora_rpa (nada instalado globalmente)", probe.contains("ModuleNotFoundError"));
        String freezeBefore = cmd(List.of(systemPython.toString(), "-m", "pip", "freeze", "--all"));
        log("pré: pacotes do Python do sistema: " + freezeBefore.lines().count());

        bridge = BridgeServer.start(BridgeServer.Options.defaults(StudioApplication.VERSION));
        window = edt(() -> StudioApplication.launch(Optional.of(NekkoraThemes.SAKURA_DARK), bridge, bridge::close, StudioEnvironment.at(studioData), List.of()));
        edt(() -> {
            window.frame().setSize(1180, 1000);
            window.frame().setLocation(0, 0);
            window.frame().toFront();
            return null;
        });
        waitFor("janela do Studio", 10, () -> edt(() -> window.frame().isShowing()));
        log("Studio " + StudioApplication.VERSION);

        Path p1 = base.resolve("projetos").resolve("the-internet").resolve("the-internet.nekkora");
        Path p2 = base.resolve("projetos").resolve("wikipedia-biografias").resolve("biografias.nekkora");
        if (mode.equals("rede")) {
            offline(p1);
        } else {
            Path env1 = legacyProject(p1);
            Path env2 = listProject(p2);
            check("compat: 0.2.0 e 0.3.0[excel] em venvs SEPARADAS (nenhuma migração silenciosa)", env1 != null && env2 != null && !env1.equals(env2));
            damaged(p2, env2);
            divergent(p1, env2);
        }

        String freezeAfter = cmd(List.of(systemPython.toString(), "-m", "pip", "freeze", "--all"));
        check("pós: Python do sistema intocado (pip freeze idêntico, sem nekkora_rpa)", freezeAfter.equals(freezeBefore)
                && cmdAny(List.of(systemPython.toString(), "-c", "import nekkora_rpa")).contains("ModuleNotFoundError"));
        try (Stream<Path> s = Files.walk(base.resolve("projetos"))) {
            check("pós: nenhuma venv dentro das pastas dos projetos", s.noneMatch(p -> p.getFileName().toString().equals("pyvenv.cfg")));
        }
    }

    /** the-internet: execução única 1.1.0 — o contrato legado {@code nekkora-rpa==0.2.0} é preservado. */
    private Path legacyProject(Path project) throws Exception {
        openByDialog(project);
        DevelopmentPanel dp = openDevelopment();
        DevelopmentPanel.Model m = develop(dp, "P1");
        String req = requirementOf(project, "the-internet-python");
        check("P1: o código gerado exige exatamente nekkora-rpa==0.2.0 (legado preservado)", "nekkora-rpa==0.2.0".equals(req));
        checkNotReady(m, req, "P1");
        captureWindow(devDialog(), "P1-01-ambiente-ausente");

        // plano + Cancelar: nada é instalado
        NekkoraDialog d = openPlan(dp);
        String plan = texts(d);
        log("  plano:\n" + plan.indent(4));
        check("P1: o plano mostra pacote/versão, origem conferida, Python base, destino e o que NÃO é enviado", plan.contains("nekkora-rpa 0.2.0")
                && plan.contains("SHA-256") && plan.contains("Python base:") && plan.contains("Destino:") && plan.contains("Nada do projeto"));
        captureWindow(d, "P1-02-plano-confirmacao");
        clickButtonWithText(d, "Cancelar");
        waitClosed(d);
        waitDevIdle("depois de cancelar a confirmação");
        check("P1: recusar a confirmação não cria venv nenhuma", venvs().isEmpty());

        // Preparar de verdade
        d = openPlan(dp);
        clickButtonWithText(d, "Preparar ambiente");
        waitClosed(d);
        waitFor("instalação em andamento", 60, () -> edt(() -> dp.model().statusTitle().startsWith("Preparando o ambiente")));
        waitFor("etapa 3 (dependências)", 300, () -> edt(() -> dp.model().statusText().contains("Etapa 3") || !window.studio().development().running()));
        log("  progresso: " + edt(() -> dp.model().statusText()));
        captureWindow(devDialog(), "P1-03-progresso");
        m = waitReady(dp, "0.2.0");
        captureWindow(devDialog(), "P1-04-ambiente-pronto");
        captureTabs(dp, "P1-04");
        checkReady(m, req, "0.2.0", "P1");
        Path env = onlyNew(List.of());
        checkEnvironmentRecord(env, req, "P1");
        closeDevelopment();
        validate("P1", "P1-05-validar");
        return env;
    }

    /** Wikipédia: lista → planilha 1.2.0 — {@code nekkora-rpa[excel]==0.3.0}; Cancelar no meio da instalação. */
    private Path listProject(Path project) throws Exception {
        List<Path> before = venvs();
        openByDialog(project);
        DevelopmentPanel dp = openDevelopment();
        DevelopmentPanel.Model m = develop(dp, "P2");
        String req = requirementOf(project, "biografias-python");
        check("P2: o código gerado exige exatamente nekkora-rpa[excel]==0.3.0", "nekkora-rpa[excel]==0.3.0".equals(req));
        checkNotReady(m, req, "P2");
        captureWindow(devDialog(), "P2-01-ambiente-ausente");

        // Cancelar DURANTE a instalação
        NekkoraDialog d = openPlan(dp);
        check("P2: o plano cita o extra excel", texts(d).contains("com o extra excel"));
        clickButtonWithText(d, "Preparar ambiente");
        waitClosed(d);
        waitFor("instalação nas dependências", 300, () -> edt(() -> dp.model().statusText().contains("Etapa 3") || !window.studio().development().running()));
        click(edt(() -> find(dp, AbstractButton.class, x -> "develop.cancel".equals(x.getName()))));
        m = waitDevIdle("cancelamento");
        log("  cancelado: " + m.statusTitle() + " · " + m.results());
        captureWindow(devDialog(), "P2-02-cancelado");
        check("P2: Cancelar interrompe a instalação com diagnóstico claro (CANCELLED)", m.results().stream().anyMatch(r -> r.contains("CANCELLED")));
        Path partial = onlyNew(before);
        check("P2: instalação cancelada não deixa ambiente dado como pronto (sem ambiente.json)", partial == null || !Files.exists(partial.getParent().resolve("ambiente.json")));
        check("P2: o projeto e o código gerado continuam como estavam", Files.isRegularFile(project) && Files.isRegularFile(project.resolveSibling("biografias-python").resolve("manifest.json")));

        d = openPlan(dp);
        clickButtonWithText(d, "Preparar ambiente");
        waitClosed(d);
        m = waitReady(dp, "0.3.0");
        captureWindow(devDialog(), "P2-03-ambiente-pronto");
        captureTabs(dp, "P2-03");
        checkReady(m, req, "0.3.0", "P2");
        Path env = onlyNew(before);
        checkEnvironmentRecord(env, req, "P2");
        closeDevelopment();
        validate("P2", "P2-04-validar");
        return env;
    }

    /** Venv danificada (módulo do framework apagado): diagnóstico, V4 não verificado, reparo pelo mesmo fluxo. */
    private void damaged(Path project, Path env) throws Exception {
        Path module = env.resolve("Lib").resolve("site-packages").resolve("nekkora_rpa").resolve("xlsx.py");
        check("N1: módulo nekkora_rpa.xlsx presente antes do dano", Files.isRegularFile(module));
        Files.delete(module);
        DevelopmentPanel dp = openDevelopment();
        DevelopmentPanel.Model m = develop(dp, "N1");
        QualityIssue issue = m.pending().stream().filter(i -> i.code().equals(ENV)).findFirst().orElse(null);
        log("  venv danificada: " + (issue == null ? "sem pendência" : issue.detail()));
        check("N1: venv danificada vira pendência do ambiente (não é aprovada)", issue != null);
        check("N1: código NÃO dado por verificado com a venv danificada", m.results().stream().noneMatch(r -> r.equals("✓ CODE_VALIDATED")));
        captureWindow(devDialog(), "N1-01-venv-danificada");
        NekkoraDialog d = openPlan(dp);
        clickButtonWithText(d, "Preparar ambiente");
        waitClosed(d);
        m = waitReady(dp, "0.3.0");
        check("N1: reparo pelo mesmo fluxo (Preparar ambiente) volta a verificar o código", m.results().contains("✓ CODE_VALIDATED") && Files.isRegularFile(module));
        captureWindow(devDialog(), "N1-02-reparado");
        closeDevelopment();
    }

    /** Python indicado em Opções avançadas: sem o pacote é recusado; com a versão errada vira diagnóstico, sem troca silenciosa. */
    private void divergent(Path legacy, Path env030) throws Exception {
        openByDialog(legacy);
        DevelopmentPanel dp = openDevelopment();
        waitDevIdle("abrir P1 de novo");
        chooseVerificationPython(dp, systemPython);
        NekkoraDialog refused = waitDialog("Python recusado");
        captureWindow(refused, "N2-01-python-sem-pacote-recusado");
        check("N2: Python sem nekkora_rpa/ruff/mypy é recusado e nada muda", texts(refused).contains("Nada foi alterado"));
        clickButtonWithText(refused, "Fechar");
        waitClosed(refused);

        Path py030 = env030.resolve("Scripts").resolve("python.exe");
        chooseVerificationPython(dp, py030);
        sleep(800);
        DevelopmentPanel.Model m = develop(dp, "N3");
        QualityIssue issue = m.pending().stream().filter(i -> i.code().equals(ENV)).findFirst().orElse(null);
        log("  Python indicado 0.3.0 para código 0.2.0: " + (issue == null ? "sem pendência" : issue.detail()) + " · python: " + m.python());
        check("N3: versão divergente do Python indicado vira diagnóstico (VERSION_MISMATCH), sem trocar de ambiente em silêncio",
                issue != null && issue.detail().contains("ersão") && m.python().contains("ambiente da automação"));
        check("N3: código NÃO dado por verificado com a versão errada", m.results().stream().noneMatch(r -> r.equals("✓ CODE_VALIDATED")));
        captureWindow(devDialog(), "N3-01-versao-divergente");
        closeDevelopment();
    }

    /** Sem rede (proxy morto só para este processo): diagnóstico claro; nada gravado como pronto. */
    private void offline(Path project) throws Exception {
        log("Rede: HTTPS_PROXY=" + System.getenv("HTTPS_PROXY") + " (porta fechada: simula rede indisponível)");
        openByDialog(project);
        DevelopmentPanel dp = openDevelopment();
        DevelopmentPanel.Model m = develop(dp, "R1");
        checkNotReady(m, "nekkora-rpa==0.2.0", "R1");
        NekkoraDialog d = openPlan(dp);
        clickButtonWithText(d, "Preparar ambiente");
        waitClosed(d);
        waitFor("instalação começar", 60, () -> edt(() -> window.studio().development().running()));
        m = waitDevIdle("falha sem rede", 900);
        log("  sem rede: " + m.statusTitle() + " · " + m.statusText() + " · " + m.results());
        captureWindow(devDialog(), "R1-01-sem-rede");
        check("R1: sem rede = diagnóstico de rede/proxy (não 'erro desconhecido')", m.results().stream().anyMatch(r -> r.contains("NETWORK_UNAVAILABLE")
                || r.contains("PROXY_OR_SSL")));
        check("R1: nenhum ambiente dado como pronto", !hasEnvironmentRecord());
        check("R1: a pendência continua com a ação de preparar (recuperável)", m.pending().stream().anyMatch(i -> i.code().equals(ENV)));
        closeDevelopment();
    }

    // ============================================================================== verificações

    private DevelopmentPanel.Model develop(DevelopmentPanel dp, String tag) throws Exception {
        waitDevIdle(tag + " análise inicial");
        click(edt(dp::developButton));
        DevelopmentPanel.Model m = waitDevIdle(tag + " desenvolver", 600);
        log("  " + tag + " pendências: " + m.pending().stream().map(i -> i.kind() + ":" + i.code()).toList());
        log("  " + tag + " resultado:\n" + String.join("\n", m.results()).indent(4));
        log("  " + tag + " python: " + m.python());
        return m;
    }

    private void checkNotReady(DevelopmentPanel.Model m, String req, String tag) {
        QualityIssue env = m.pending().stream().filter(i -> i.code().equals(ENV)).findFirst().orElse(null);
        check(tag + ": aviso 'Ambiente Python da automação' com a versão exata do código", env != null && env.kind() == QualityIssue.Kind.WARNING
                && env.assistance().map(a -> a.question().contains(req)).orElse(false));
        check(tag + ": nada é dado por aprovado sem ambiente (V4/V5 não verificados)", m.results().stream().anyMatch(r -> r.startsWith("— Código não verificado: ambiente Python da automação")));
        check(tag + ": a janela diz em que ambiente o código é verificado", m.answers().stream().anyMatch(a -> a.question().equals("Em que ambiente o código é verificado?")
                && a.answer().startsWith(req)));
    }

    private void checkReady(DevelopmentPanel.Model m, String req, String version, String tag) {
        check(tag + ": código verificado no ambiente preparado (CODE_VALIDATED, V4/V5)", m.results().contains("✓ CODE_VALIDATED"));
        check(tag + ": sem pendência de ambiente", m.pending().stream().noneMatch(i -> i.code().equals(ENV)));
        check(tag + ": versão visível na janela: nekkora-rpa " + version + " (venv da automação)", m.python().contains("nekkora-rpa " + version + " (venv da automação)")
                && m.python().startsWith(studioData.toString()));
        check(tag + ": resposta do ambiente cita o requisito exato", m.answers().stream().anyMatch(a -> a.question().equals("Em que ambiente o código é verificado?")
                && a.answer().startsWith(req + " — ")));
    }

    private void checkEnvironmentRecord(Path venv, String req, String tag) throws Exception {
        Path rec = venv == null ? null : venv.getParent().resolve("ambiente.json");
        check(tag + ": venv nova dentro da pasta de dados do Studio (caminho com acento)", venv != null && venv.startsWith(studioData));
        if (rec == null || !Files.isRegularFile(rec)) {
            check(tag + ": ambiente.json gravado", false);
            return;
        }
        String json = Files.readString(rec, StandardCharsets.UTF_8);
        log("  " + tag + " ambiente.json: " + json.replace("\n", " "));
        Map<?, ?> o = (Map<?, ?>) Json.parse(json);
        check(tag + ": ambiente.json registra o requisito, a origem homologada e o hash do wheel", req.equals(o.get("requirement")) && o.get("wheelSha256") != null && "BUNDLED".equals(o.get("source")));
    }

    private void validate(String tag, String shot) throws Exception {
        focusStudio();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_V);
        waitFor("camadas V0–V6", 300, () -> edt(() -> {
            String t = window.studio().view().validationPanel().layersText().getAccessibleContext().getAccessibleDescription();
            return t != null && t.contains("V6") && !t.contains("verificando") && !t.contains("… V4") && !t.contains("… V5")
                    && !window.studio().development().running();
        }));
        sleep(1500);
        String layers = edt(() -> window.studio().view().validationPanel().layersText().getAccessibleContext().getAccessibleDescription());
        log("  " + tag + " camadas: " + layers);
        check(tag + ": Validar automação — V4 Código verificado no ambiente da automação (sem 'ambiente não pronto')", layers.contains("✓ V4")
                && !layers.contains(ENV));
        check(tag + ": Validar automação — V5 sem falha", !layers.contains("× V5"));
        captureStudio(shot);
    }

    /** Versão visível: aba Plano (resposta "Em que ambiente…") e Opções avançadas (Python da automação · nekkora-rpa x.y.z). */
    private void captureTabs(DevelopmentPanel dp, String tag) throws Exception {
        selectTabOf(dp, 0);
        captureWindow(devDialog(), tag + "b-plano-ambiente");
        selectTabOf(dp, 3);
        captureWindow(devDialog(), tag + "c-opcoes-avancadas");
        selectTabOf(dp, 2);
    }

    private String requirementOf(Path project, String gen) throws Exception {
        Path manifest = project.resolveSibling(gen).resolve("manifest.json");
        if (!Files.isRegularFile(manifest)) return "";
        Object f = ((Map<?, ?>) Json.parse(Files.readString(manifest, StandardCharsets.UTF_8))).get("framework");
        return f instanceof String s ? s : "";
    }

    private List<Path> venvs() throws IOException {
        Path root = studioData.resolve("ambientes");
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> s = Files.walk(root, 3)) {
            return s.filter(p -> p.getFileName().toString().equals("venv") && Files.isDirectory(p)).toList();
        }
    }

    private boolean hasEnvironmentRecord() throws IOException {
        Path root = studioData.resolve("ambientes");
        if (!Files.isDirectory(root)) return false;
        try (Stream<Path> s = Files.walk(root, 3)) {
            return s.anyMatch(p -> p.getFileName().toString().equals("ambiente.json"));
        }
    }

    private Path onlyNew(List<Path> before) throws IOException {
        List<Path> now = new ArrayList<>(venvs());
        now.removeAll(before);
        return now.size() == 1 ? now.get(0) : null;
    }

    // ============================================================================== interface

    private NekkoraDialog openPlan(DevelopmentPanel dp) throws Exception {
        clickPendingButton(dp, ENV, "Preparar ambiente…");
        return waitDialog(CONFIRM);
    }

    private DevelopmentPanel.Model waitReady(DevelopmentPanel dp, String version) throws Exception {
        waitFor("ambiente " + version + " pronto e código verificado", 1200, () -> edt(() -> !window.studio().development().running()
                && (dp.model().python().contains("nekkora-rpa " + version) || dp.model().severity() == com.nekkora.ui.core.Severity.ERROR)));
        DevelopmentPanel.Model m = waitDevIdle("desenvolver depois de preparar", 600);
        log("  pronto: " + m.statusTitle() + " · python: " + m.python());
        log("  resultado:\n" + String.join("\n", m.results()).indent(4));
        return m;
    }

    private void chooseVerificationPython(DevelopmentPanel dp, Path python) throws Exception {
        AbstractButton b = edt(() -> find(dp, AbstractButton.class, x -> "Python para verificação…".equals(x.getText())));
        if (b == null) throw new IllegalStateException("botão Python para verificação… não encontrado");
        if (!edt(b::isShowing)) selectTabOf(dp, 3); // aba Opções avançadas (clique real na aba)
        waitFor("botão Python para verificação… visível", 5, () -> edt(b::isShowing));
        click(b);
        waitFor("diálogo Python para verificação", 15, () -> fileChooser() != null);
        sleep(500);
        typePath(python);
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

    private DevelopmentPanel.Model waitDevIdle(String what) throws Exception {
        return waitDevIdle(what, 180);
    }

    private DevelopmentPanel.Model waitDevIdle(String what, int seconds) throws Exception {
        sleep(600);
        waitFor(what, seconds, () -> edt(() -> !window.studio().development().running()));
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

    private void clickPendingButton(DevelopmentPanel dp, String code, String text) throws Exception {
        selectPending(dp, code);
        AbstractButton b = edt(() -> find(dp.pendingButtons(), AbstractButton.class, x -> text.equals(x.getText())));
        if (b == null) throw new IllegalStateException("botão da pendência não encontrado: " + code + " / " + text);
        click(b);
        sleep(600);
    }

    private Window fileChooser() throws Exception {
        return edt(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog dl && dl.isShowing() && find(dl, javax.swing.JFileChooser.class, x -> true) != null) return w;
            }
            return null;
        });
    }

    /** Caminho no campo do diálogo de arquivos (o caminho tem acento: o texto é posto no campo, depois Enter real). */
    private void typePath(Path p) throws Exception {
        Window w = fileChooser();
        JTextComponent field = edt(() -> find(w, JTextComponent.class, x -> x.isShowing() && x.isEditable()));
        click(field);
        edt(() -> {
            field.setText(p.toString());
            return null;
        });
        sleep(300);
        press(KeyEvent.VK_ENTER);
        waitFor("diálogo de arquivo fechado", 15, () -> fileChooser() == null);
    }

    private void openByDialog(Path file) throws Exception {
        focusStudio();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_O);
        waitFor("diálogo Abrir projeto", 15, () -> fileChooser() != null && "Abrir projeto".equals(((JDialog) fileChooser()).getTitle()));
        sleep(500);
        typePath(file);
        waitFor("projeto aberto " + file.getFileName(), 30, () -> edt(() -> window.studio().state().activeAutomation()
                .flatMap(x -> window.studio().state().project(x.id())).flatMap(com.nekkora.studio.app.workspace.ProjectState::path).map(file::equals)
                .orElse(false)));
        sleep(500);
        log("projeto aberto: " + file);
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

    private NekkoraDialog waitDialog(String title) throws Exception {
        return (NekkoraDialog) waitValue("diálogo " + title, 15, () -> edt(() -> {
            for (Window w : Window.getWindows()) if (w instanceof NekkoraDialog nd && nd.isShowing() && title.equals(nd.getTitle())) return nd;
            return null;
        }));
    }

    private void waitClosed(Window d) throws Exception {
        waitFor("fechar diálogo", 15, () -> edt(() -> !d.isShowing()));
        sleep(250);
    }

    private void clickButtonWithText(Container root, String text) throws Exception {
        AbstractButton b = edt(() -> find(root, AbstractButton.class, x -> text.equals(x.getText()) && x.isShowing()));
        if (b == null) throw new IllegalStateException("botão não encontrado: " + text);
        click(b);
    }

    private void click(Component c) throws Exception {
        edt(() -> {
            if (c instanceof javax.swing.JComponent jc) jc.scrollRectToVisible(new Rectangle(0, 0, jc.getWidth(), jc.getHeight()));
            return null;
        });
        robot.waitForIdle();
        sleep(150);
        Rectangle r = edt(() -> new Rectangle(c.getLocationOnScreen(), c.getSize()));
        Rectangle win = edt(() -> SwingUtilities.getWindowAncestor(c) == null ? null : SwingUtilities.getWindowAncestor(c).getBounds());
        if (win == null || !win.contains(r.x + r.width / 2, r.y + r.height / 2)) {
            throw new IllegalStateException("componente fora da sua janela (clique recusado): " + c.getClass().getSimpleName() + " " + r + " janela " + win);
        }
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

    private void press(int... keys) {
        guardForeground("tecla");
        for (int k : keys) robot.keyPress(k);
        for (int i = keys.length - 1; i >= 0; i--) robot.keyRelease(keys[i]);
        robot.waitForIdle();
        sleep(200);
    }

    // ============================================================================== proteção da máquina

    /** Antes de cada clique/tecla/captura, a janela em primeiro plano tem de ser desta JVM; senão a homologação para. */
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
                if (pid == ProcessHandle.current().pid()) return;
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
            Path script = Files.createTempFile("nkpkg-fg", ".ps1");
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

    // ============================================================================== capturas, log, processos

    private void captureStudio(String name) throws Exception {
        robot.waitForIdle();
        sleep(400);
        guardForeground("captura " + name);
        save(robot.createScreenCapture(edt(() -> window.frame().getBounds())), name);
    }

    private void captureWindow(Window w, String name) throws Exception {
        robot.waitForIdle();
        sleep(250);
        guardForeground("captura " + name);
        save(robot.createScreenCapture(edt(() -> {
            Rectangle b = w.getBounds();
            java.awt.Insets in = w.getInsets();
            int top = w instanceof java.awt.Frame f && f.isUndecorated() || w instanceof java.awt.Dialog d && d.isUndecorated() ? in.top : 1;
            return new Rectangle(b.x + in.left, b.y + top, b.width - in.left - in.right, b.height - top - in.bottom);
        })), name);
    }

    private void save(BufferedImage img, String name) throws IOException {
        File f = new File(out, "PKG-" + name + ".png");
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
        try (PrintWriter w = new PrintWriter(new File(out, "PKG-homologacao-log.txt"), StandardCharsets.UTF_8)) {
            log.forEach(w::println);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void teardown() {
        if (foreground != null) foreground.destroy();
        if (bridge != null) bridge.close();
    }

    private static String cmd(List<String> c) throws Exception {
        Process p = new ProcessBuilder(c).redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) throw new IllegalStateException(String.join(" ", c) + " falhou:\n" + o);
        return o;
    }

    private static String cmdAny(List<String> c) throws Exception {
        Process p = new ProcessBuilder(c).redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        return o;
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
        try {
            if (window != null) {
                log("DIAGNÓSTICO (" + what + "): " + edt(() -> {
                    var m = window.studio().development().panel().model();
                    return m.statusTitle() + " · " + m.statusText() + " · " + m.results();
                }));
                captureStudio("FALHA-" + what.replaceAll("[^A-Za-z0-9]+", "-"));
            }
        } catch (Exception ignored) {
            // diagnóstico
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
        if (SwingUtilities.isEventDispatchThread()) return c.call();
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
