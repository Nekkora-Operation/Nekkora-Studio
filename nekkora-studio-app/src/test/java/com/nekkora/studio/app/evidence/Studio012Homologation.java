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
 * Homologação REAL da NEKKORA-STUDIO-012 no Windows: Studio de verdade (dados isolados), Chrome for Testing com a extensão
 * DESTA branch, Native Host do script oficial, mouse e teclado do sistema (Robot) e o laboratório sintético
 * {@code test-lab/pages/studio012} (estrutura do caso de reprodução, dados FICTÍCIOS): login com campos descendentes e
 * id/name técnicos, portal com iframes de menu e de conteúdo que trocam de documento, nível entre 15 listas iguais, menu
 * só por hover. Cobre G1 (Recorder com interação), G2 (localizadores), G3 (variáveis pela interface) e G4 (debug guiado
 * confirmando a opção). Opcionalmente abre, só para leitura, uma CÓPIA de projeto real (nenhuma captura de tela dela).
 * Operador: Claude como AGENTE DE TESTE (TEST_AGENT).
 *
 * <p>Uso: {@code java -Dnekkora.studio.decisionActor=TEST_AGENT ... Studio012Homologation <saída> <repositório-da-extensão> [cópia.nekkora]}
 */
public final class Studio012Homologation {

    private static final int LAB_PORT = 18765;
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

    private Studio012Homologation(File out, Path ext) throws AWTException {
        this.out = out;
        this.ext = ext;
        this.robot = new Robot();
        robot.setAutoDelay(25);
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Studio012Homologation h = new Studio012Homologation(out, Path.of(args[1]).toAbsolutePath().normalize());
        LEGACY = args.length > 2 ? Path.of(args[2]) : null;
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

    private void run() throws Exception {
        log("Tela: " + GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds() + " · java " + System.getProperty("java.version"));
        log("Operador: Claude como AGENTE DE TESTE (TEST_AGENT; nenhuma confirmação humana) · ator do Studio: "
                + System.getProperty("nekkora.studio.decisionActor"));
        log("Laboratório sintético STUDIO-012 (" + BASE + "studio012/): estrutura do caso de reprodução, dados FICTÍCIOS");
        SENTINELS.addAll(List.of(USER, PASSWORD, "Senha2026x"));
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
        navigate(PAGE);
        connect();
        check("G4: aba do laboratório reconhecida como LAB (loopback)",
                "LAB".equals(harness.eval("window.nekkora.call('access.describe', {tabId: " + labTab + "}).then(r => r.kind || (r.result && r.result.kind))")));

        // ================================================================ G1/G2 — gravação COM interação no Studio real
        focusStudio();
        createS012Automation("Portal de auditoria (sintético)");
        Automation a = active();
        LogicalSession s1 = a.sessions().get(0);
        selectSession(a, s1);
        captureStudio("S012-01-antes-de-gravar");
        startRecordingOn("127.0.0.1", RecordingMode.CAPTURE_AND_INTERACT);
        moveFloatingAway();
        clickInPage("document.getElementById('ds_login')");
        insertText(USER);
        clickInPage("document.getElementById('passwordTemp')");
        insertText(PASSWORD);
        clickInPage("document.getElementById('Button_DoLogin')");
        waitPage("portal.html");
        sleep(2000); // frames carregados e inventariados
        clickInPage(MENU + ".contentDocument.querySelector('#secaoAuditoria > a')", MENU);
        waitFor("seção Auditoria aberta (frame do menu recarregado)", 15, () -> String.valueOf(page.eval(MENU + ".contentWindow.location.search")).contains("auditoria"));
        sleep(1500);
        clickInPage(MENU + ".contentDocument.querySelector('#secaoAuditoria .sub a')", MENU);
        waitFor("tela da solicitação no frame de conteúdo", 15,
                () -> String.valueOf(page.eval(CONTENT + ".contentWindow.location.pathname")).endsWith("solicitacao.html"));
        sleep(1500);
        clickInPage(CONTENT + ".contentDocument.querySelectorAll('select')[6]", CONTENT); // abre a lista (popup nativo)
        sleep(500);
        typeText("Auditoria C");
        press(KeyEvent.VK_ENTER); // confirma a opção no popup nativo
        sleep(800);
        clickInPage(CONTENT + ".contentDocument.querySelector('[name=Button_DoGravar]')", CONTENT);
        waitFor("etapas gravadas", 40, () -> edt(s1::stepCount) >= 7);
        finishRecording();
        log("  etapas: " + edt(() -> kinds(s1)));
        String oracle = String.valueOf(page.eval(CONTENT + ".contentWindow.sessionStorage.getItem('nk012.nivel')"));
        check("G4 oráculo: a PÁGINA registrou o nível Auditoria Carefy (independente da gravação)", "Auditoria Carefy".equals(oracle));
        List<AutomationStep> st = edt(() -> List.copyOf(s1.steps()));
        List<String> kinds = edt(() -> kinds(s1));
        check("G1: login → menu (iframe que recarrega) → tela no outro iframe → nível → Gravar gravados COM interação, na ordem",
                kinds.subList(0, 5).equals(List.of("FILL", "FILL", "CLICK", "CLICK", "CLICK")) && kinds.contains("SELECT_OPTION")
                        && "CLICK".equals(kinds.get(kinds.size() - 1)));
        check("G1: todas as etapas observadas com interação (nenhuma proposta sem interação)", edt(() -> st.stream()
                .allMatch(s -> s.provenance().recordingObservation().map(r -> r.observation() == RecordingObservation.Observation.INTERACTION_OBSERVED)
                        .orElse(false))));
        for (AutomationStep s : st) {
            ElementReference el = s.element().orElse(null);
            log("  " + s.actionType() + " · preferido " + (el == null ? "-" : el.preferredLocator().map(l -> l.strategy().name()).orElse("nenhum"))
                    + " · " + (el == null ? "-" : com.nekkora.studio.ui.ElementPresentation.scope(el.accessPath())));
        }
        check("G2: usuário (descendente de envoltórios, id técnico 'ds…') com ID preferido, não XPath", strategyOf(st.get(0)) == LocatorStrategy.ID);
        check("G2: senha localizada por id/name (não posicional); id fora do descritor por ser sensível",
                List.of(LocatorStrategy.ID, LocatorStrategy.NAME).contains(strategyOf(st.get(1))) && edt(() -> st.get(1).element().orElseThrow().htmlId().isEmpty()));
        check("G1: Auditoria e Solicitação em estudo DENTRO do iframe do menu; nível e Gravar no iframe de conteúdo",
                st.subList(3, st.size()).stream().allMatch(s -> s.element().map(e -> e.accessPath().stream().anyMatch(p -> p.type() == AccessStep.Type.IFRAME))
                        .orElse(false)));
        AutomationStep select = st.stream().filter(s -> s.actionType() == com.nekkora.studio.domain.action.ActionType.SELECT_OPTION).findFirst().orElseThrow();
        check("G1: opção escolhida identificada e RETIDA (privacidade): configure no Studio",
                select.parameters().option().isEmpty() && edt(() -> select.provenance().recordingObservation().orElseThrow().limitations().contains("OPTION_WITHHELD")));
        check("privacidade: nenhum valor digitado no modelo", SENTINELS.stream().noneMatch(edt(() -> domainText(a))::contains));
        // a usuária configura a opção na etapa (operação do Studio pelo TEST_AGENT)
        edt(() -> {
            window.studio().service().setStepParameters(a.id(), select.id(), com.nekkora.studio.domain.action.ActionParameters.withOption(
                    new com.nekkora.studio.domain.action.OptionChoice("Auditoria Carefy", "")));
            return null;
        });
        selectStep(a, s1, st.get(0));
        captureStudio("S012-02-login-por-id");
        selectStep(a, s1, select);
        captureStudio("S012-03-nivel-no-iframe");

        // ================================================================ G3 — variáveis pela interface
        Map<String, Object> var = variablesByGui(a);
        check("G3: Finalidade com forma de valor recusada no campo (nada declarado)", Boolean.TRUE.equals(var.get("purposeRefused")));
        check("G3: segredo sem campo de valor; ajuda da fonte de segredo (NK_SECRET_<NOME>)", Boolean.TRUE.equals(var.get("secretHelp")));
        check("G3: segredo declarado com Finalidade descritiva", edt(() -> a.variableNamed("portal.senha").map(v -> v.origin().kind()
                == com.nekkora.studio.domain.variable.VariableOrigin.Kind.INPUT && v.sensitivity() == com.nekkora.studio.domain.variable.Sensitivity.SECRET)
                .orElse(false)));
        edt(() -> {
            window.studio().service().setStepParameters(a.id(), st.get(1).id(), com.nekkora.studio.domain.action.ActionParameters.withValue(
                    new com.nekkora.studio.domain.action.ValueSource.VariableReference("portal.senha")));
            return null;
        });
        check("G3: vínculo explícito da etapa Preencher senha à variável aparece em \"onde é usada\"",
                edt(() -> com.nekkora.studio.ui.form.VariableForm.usageText(a, a.variableNamed("portal.senha").orElseThrow()).startsWith("Usada em 1 etapa")));

        // ================================================================ salvar e reabrir
        Path project = out.toPath().resolve("projeto").resolve("portal-sintetico.nekkora");
        Files.createDirectories(project.getParent());
        saveAs(project);
        check("projeto salvo (.nekkora)", Files.isRegularFile(project));
        int steps = edt(a::stepCount);
        StepId firstId = st.get(0).id();
        reopen(project);
        Automation b = active();
        check("reaberto íntegro: mesmas etapas, StepIds, opção configurada e variável",
                edt(() -> b.stepCount() == steps && b.sessions().get(0).steps().get(0).id().equals(firstId) && b.variables().size() == 1
                        && b.sessions().get(0).steps().stream().anyMatch(s -> s.parameters().option().map(o -> "Auditoria Carefy".equals(o.label())).orElse(false))));
        String nekkora = zipText(project);
        check("privacidade: .nekkora sem valores digitados", SENTINELS.stream().noneMatch(nekkora::contains));

        // ================================================================ G4 — debug guiado confirma o nível (variante com identificador)
        navigate(BASE + "studio012/solicitacao.html?variante=nome");
        focusStudio();
        createS012Automation("Nível de auditoria (sintético)");
        Automation c = active();
        LogicalSession n1 = c.sessions().get(0);
        selectSession(c, n1);
        startRecordingOn("127.0.0.1", RecordingMode.CAPTURE_AND_INTERACT);
        moveFloatingAway();
        clickInPage("document.querySelector('[name=ddlTipo]')");
        sleep(500);
        typeText("Auditoria C");
        press(KeyEvent.VK_ENTER);
        sleep(800);
        clickInPage("document.querySelector('[name=Button_DoGravar]')");
        waitFor("seleção + gravar", 30, () -> edt(n1::stepCount) >= 2);
        finishRecording();
        AutomationStep sel2 = edt(() -> n1.steps().stream().filter(s -> s.actionType() == com.nekkora.studio.domain.action.ActionType.SELECT_OPTION)
                .reduce((x, y) -> y).orElseThrow());
        check("G2: lista com name técnico ('ddl' + vocabulário) preferida por NAME", strategyOf(sel2) == LocatorStrategy.NAME);
        edt(() -> {
            window.studio().service().setStepParameters(c.id(), sel2.id(), com.nekkora.studio.domain.action.ActionParameters.withOption(
                    new com.nekkora.studio.domain.action.OptionChoice("Auditoria Carefy", "")));
            // as outras etapas saem do roteiro do debug: só a seleção é conferida aqui
            for (AutomationStep s : List.copyOf(n1.steps())) if (!s.id().equals(sel2.id())) window.studio().service().removeStep(c.id(), s.id());
            return null;
        });
        openDebugAndStart(t -> t.label().contains("Laboratório local") || t.label().contains("127.0.0.1"), 0);
        GuidedDebugPanel.Card card = waitCard("conferência da opção", x -> x.mode() == GuidedDebugPanel.Mode.STEP && x.badge() != GuidedDebugPanel.Badge.CHECKING);
        log("  debug: " + card.badge() + " · " + card.message());
        check("G4: debug confirma a opção configurada — existe e está selecionada agora (só contagem e sim/não vieram da página)",
                card.badge() == GuidedDebugPanel.Badge.PROPOSED && card.message().contains("«Auditoria Carefy»: existe na lista e está selecionada agora"));
        captureStudio("S012-04-debug-confirma-nivel");
        edt(() -> {
            window.studio().guidedDebug().finish();
            return null;
        });
        waitDebugClosed();

        // ================================================================ G1 — submenu só por hover
        navigate(BASE + "studio012/portal.html");
        sleep(1500);
        focusStudio();
        createS012Automation("Cadastros por hover (sintético)");
        Automation h = active();
        LogicalSession h1 = h.sessions().get(0);
        selectSession(h, h1);
        startRecordingOn("127.0.0.1", RecordingMode.CAPTURE_AND_INTERACT);
        moveFloatingAway();
        hoverThenClick(MENU + ".contentDocument.querySelector('li.flutuante > a')", MENU + ".contentDocument.querySelector('li.flutuante .sub a')", MENU);
        waitFor("clique no item do submenu", 30, () -> edt(h1::stepCount) >= 1);
        finishRecording();
        AutomationStep hov = edt(() -> h1.steps().get(h1.steps().size() - 1));
        check("G1: só o clique observado vira etapa (passar o mouse não é inventado como ação)", edt(() -> kinds(h1).equals(List.of("CLICK"))));
        check("G1: alvo revelado por :hover declarado como dependência de outra interação (revisão)", edt(() -> hov.element().orElseThrow().obstacles().stream()
                .anyMatch(o -> o.type().equals("INTERACTION_DEPENDENCY") && o.description().contains(":hover"))));
        selectStep(h, h1, hov);
        captureStudio("S012-05-hover-requer-revisao");

        // ================================================================ trilha da usuária: cópia do projeto real, só leitura
        if (LEGACY != null) {
            String before = sha(LEGACY);
            openByDialog(LEGACY);
            waitFor("cópia aberta", 20, () -> edt(() -> window.studio().state().activeAutomation().map(x -> x.stepCount() == 9).orElse(false)));
            check("trilha da usuária: cópia do projeto de reprodução abre no Studio novo (9 etapas)", true);
            menu("Arquivo", "Fechar projeto");
            sleep(1500);
            NekkoraDialog ask = edt(() -> {
                for (Window w : Window.getWindows()) if (w instanceof NekkoraDialog nd && nd.isShowing()) return nd;
                return null;
            });
            check("trilha da usuária: nada a salvar ao fechar (somente leitura)", ask == null);
            check("trilha da usuária: arquivo da cópia inalterado (hash)", before.equals(sha(LEGACY)));
            log("  nenhuma captura de tela da cópia do projeto real (pode conter dados de negócio)");
        }
        check("privacidade: log da homologação sem valores", SENTINELS.stream().noneMatch(String.join("\n", log)::contains));
    }

    // ============================================================================== apoio STUDIO-012

    private static final String USER = "auditor.teste";
    private static final String PASSWORD = "Fict1c1a012";
    private static final String MENU = "document.querySelector('iframe[name=menu]')";
    private static final String CONTENT = "document.querySelector('iframe[name=conteudo]')";
    private static Path LEGACY;

    private static String sha(Path p) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
    }

    private LocatorStrategy strategyOf(AutomationStep s) throws Exception {
        return edt(() -> s.element().flatMap(ElementReference::preferredLocator).map(LocatorCandidate::strategy).orElse(null));
    }

    private void createS012Automation(String name) throws Exception {
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_N);
        NekkoraDialog d = waitDialog("Nova automação");
        typeIntoFocused(name);
        press(KeyEvent.VK_ENTER);
        waitClosed(d);
        newSession("Gravação");
    }

    /** Ponto de tela (coordenadas do Robot) do centro de um elemento, opcionalmente dentro de um iframe. */
    private int[] screenPoint(String elementExpr, String frame) throws Exception {
        String js = "(() => { const e = " + elementExpr + "; const r = e.getBoundingClientRect();"
                + (frame == null ? " const fx = 0, fy = 0;" : " const f = (" + frame + ").getBoundingClientRect(); const fx = f.left + (" + frame + ").clientLeft, fy = f.top + (" + frame + ").clientTop;")
                + " const bx = (window.outerWidth - window.innerWidth) / 2; const by = window.outerHeight - window.innerHeight - bx;"
                + " return [window.screenX + bx + fx + r.left + r.width / 2, window.screenY + by + fy + r.top + r.height / 2]; })()";
        @SuppressWarnings("unchecked")
        List<Object> xy = (List<Object>) page.eval(js);
        return new int[] {(int) Math.round(((Number) xy.get(0)).doubleValue() / sc), (int) Math.round(((Number) xy.get(1)).doubleValue() / sc)};
    }

    /** Gesto REAL de menu por hover: o ponteiro para sobre o gatilho, o item aparece, o ponteiro vai até ele e clica. */
    private void hoverThenClick(String triggerExpr, String itemExpr, String frame) throws Exception {
        cdp.activate(labTarget);
        focusChrome();
        int[] t = screenPoint(triggerExpr, frame);
        guardForeground("hover no menu");
        robot.mouseMove(t[0], t[1]);
        sleep(700);
        int[] i = screenPoint(itemExpr, frame); // só tem caixa com o submenu aberto
        for (int k = 1; k <= 6; k++) { // caminho contínuo, sem sair do item do menu
            robot.mouseMove(t[0] + (i[0] - t[0]) * k / 6, t[1] + (i[1] - t[1]) * k / 6);
            sleep(60);
        }
        sleep(300);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        sleep(800);
    }

    /** Clique REAL numa aba de qualquer NekkoraTabs. */
    private void clickTabOf(com.nekkora.ui.navigation.NekkoraTabs tabs, int index) throws Exception {
        Rectangle r = edt(() -> {
            Rectangle tab = tabs.getBoundsAt(index);
            java.awt.Point p = tabs.getLocationOnScreen();
            return new Rectangle(p.x + tab.x, p.y + tab.y, tab.width, tab.height);
        });
        robot.mouseMove(r.x + r.width / 2, r.y + r.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        waitFor("aba " + index, 5, () -> edt(() -> tabs.getSelectedIndex() == index));
        sleep(300);
    }

    /** G3 pela interface: Finalidade com cara de valor recusada; segredo sem valor; declaração descritiva. */
    private Map<String, Object> variablesByGui(Automation a) throws Exception {
        Map<String, Object> r = new LinkedHashMap<>();
        edt(() -> {
            window.studio().controller().showVariables();
            return null;
        });
        sleep(600);
        click(edt(() -> window.studio().view().variablesPanel().createButton()));
        NekkoraDialog d = waitDialog("Nova variável");
        com.nekkora.studio.ui.form.VariableForm form = edt(() -> find(d.getContentPane(), com.nekkora.studio.ui.form.VariableForm.class, x -> true));
        typeIntoFocused("portal.senha");
        edt(() -> {
            form.sensitivityInput().setSelectedItem(com.nekkora.studio.domain.variable.Sensitivity.SECRET);
            form.originInput().setSelectedItem(com.nekkora.studio.domain.variable.VariableOrigin.Kind.CONSTANT);
            return null;
        });
        sleep(300);
        boolean constantLocked = edt(() -> !form.constantInput().isEnabled() && form.originHelp().getText().contains("não podem ter valor constante"));
        captureWindow(d, "S012-06-segredo-sem-valor-constante");
        edt(() -> {
            form.originInput().setSelectedItem(com.nekkora.studio.domain.variable.VariableOrigin.Kind.INPUT);
            return null;
        });
        sleep(300);
        r.put("secretHelp", constantLocked && edt(() -> form.originHelp().getText().contains("NK_SECRET_")));
        captureWindow(d, "S012-07-segredo-entrada-na-execucao");
        clickTabOf(form.tabs(), 1);
        click(edt(form::purposeInput));
        typeText("Senha2026x"); // forma de VALOR na Finalidade (o erro relatado na vistoria)
        clickButtonWithText(d, "Declarar variável");
        sleep(600);
        r.put("purposeRefused", edt(() -> d.isShowing() && form.visibleErrors().contains("finalidade parece conter um dado sensível") && a.variables().isEmpty()
                && form.tabs().getSelectedIndex() == 1));
        captureWindow(d, "S012-08-finalidade-com-valor-recusada");
        edt(() -> {
            form.purposeInput().setText("");
            return null;
        });
        click(edt(form::purposeInput));
        typeText("Senha de acesso ao portal");
        captureWindow(d, "S012-09-finalidade-descritiva");
        clickButtonWithText(d, "Declarar variável");
        waitClosed(d);
        return r;
    }

    // ============================================================================== apoio E2E-001

    private static final String BASE = "http://127.0.0.1:" + LAB_PORT + "/lab/";
    private static final String PAGE = BASE + "studio012/login.html";
    private static final List<String> LABELS = List.of("First Name", "Last Name", "Company Name", "Role in Company", "Address", "Email", "Phone Number");
    private static final List<String> VARS = List.of("first_name", "last_name", "company_name", "role_in_company", "address", "email", "phone_number");
    private static final String DOWNLOAD = "document.querySelector('a[href$=\"downloadFiles/challenge.xlsx\"]')";
    private static final String SUBMIT = "document.querySelector('input[type=submit]')";
    private static final String START = "[...document.querySelectorAll('button')].find(b => b.textContent.trim().toLowerCase() === 'start')";
    private static Path TRANSIENT;
    private static final List<String> SENTINELS = new ArrayList<>();

    /** Elemento esperado (só a HOMOLOGAÇÃO usa isto, para conferir o destaque): o input irmão do rótulo. */
    private static String field(String label) {
        return "[...document.querySelectorAll('label')].find(l => l.textContent.trim() === '" + label + "').parentElement.querySelector('input')";
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

    private void createAutomation() throws Exception {
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_N);
        NekkoraDialog d = waitDialog("Nova automação");
        typeIntoFocused("RPA Challenge — Input Forms");
        press(KeyEvent.VK_ENTER);
        waitClosed(d);
        newSession("Preparação");
        newSession("Preenchimento por registro");
        newSession("Conclusão");
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

    /** Revisão (TEST_AGENT) dos textos que o PDD deixou para revisar: só rótulos do vocabulário, nenhum valor. */
    private void reviewPdd(PddPanel p) throws Exception {
        Map<String, String> sections = Map.of("secao-1", "Preparação", "secao-2", "Preenchimento por registro", "secao-3", "Conclusão");
        for (Map.Entry<String, String> e : sections.entrySet()) {
            selectItem(p, e.getKey());
            setText(p.titleField(), e.getValue());
            click(edt(p::saveTitleButton));
        }
        List<PddPanel.Item> blocks = edt(() -> model(p).items().stream().filter(i -> i.label().startsWith("    ")).toList());
        List<String> titles = List.of("Baixar a planilha e iniciar o desafio", "Preencher o formulário com um registro e enviar", "Ler o resultado exibido");
        for (int i = 0; i < blocks.size() && i < titles.size(); i++) {
            selectItem(p, blocks.get(i).key());
            setText(p.titleField(), titles.get(i));
            click(edt(p::saveTitleButton));
        }
        for (PddPanel.Item block : blocks) {
            selectItem(p, block.key());
            for (int guard = 0; guard < 20; guard++) {
                List<PddPanel.InstructionRow> rows = edt(() -> model(p).instructions());
                int k = -1;
                for (int x = 0; x < rows.size(); x++) if (rows.get(x).needsAttention()) { k = x; break; }
                if (k < 0) break;
                final int idx = k;
                javax.swing.JTextField f = edt(() -> p.instructionFields().get(idx)); // relido a cada volta: o painel se redesenha
                Container row = edt(f::getParent);
                String text = instructionFor(blocks.indexOf(block), idx);
                if (text != null) {
                    setText(f, text);
                    click(edt(() -> find(row, AbstractButton.class, b -> "Salvar".equals(b.getText()))));
                } else {
                    click(edt(() -> find(row, AbstractButton.class, b -> "Aceitar".equals(b.getText()))));
                }
                sleep(500);
            }
        }
        log("  PDD: " + model(p).titleState() + " · pode exportar: " + model(p).canExport() + " · pendências " + model(p).pending()
                + " · " + model(p).bannerTitle());
    }

    /** Texto revisado (TEST_AGENT) — só rótulos de interface; nunca valores. {@code null} = aceitar o derivado. */
    private String instructionFor(int block, int ordinalIndex) throws Exception {
        if (block == 0 && ordinalIndex == 0) return "Selecionar o link de download da planilha";
        if (block == 1) {
            Automation a = active();
            AutomationStep s = edt(() -> a.sessions().get(1).steps().get(ordinalIndex));
            if (s.actionType() == com.nekkora.studio.domain.action.ActionType.FILL) {
                for (LocatorCandidate l : s.element().orElseThrow().locators()) {
                    for (String lb : LABELS) if (l.strategy() == LocatorStrategy.LABEL && l.expression().contains("\"" + lb + "\"")) {
                        return "Informar o campo \"" + lb + "\" com a coluna correspondente da planilha";
                    }
                }
            }
        }
        return null;
    }

    /**
     * Passagem do debug guiado do Studio: identidade comprovada sobre o elemento certo → Continuar; ambígua ou não
     * encontrada → Corrigir → Selecionar no navegador (clique real no elemento) → comparar → Aplicar (TEST_AGENT).
     * Devolve a posição REAL na tela do elemento esperado de cada etapa (para provar a troca de posição entre passagens).
     */
    private Map<String, Rectangle> validationPass(String label, Automation a) throws Exception {
        Map<String, Rectangle> where = new LinkedHashMap<>();
        GuidedDebugPanel dp = openDebugAndStart(t -> t.label().contains("rpachallenge"), 0);
        List<AutomationStep> steps = edt(() -> a.sessions().stream().flatMap(x -> x.steps().stream()).toList());
        int direct = 0;
        int corrected = 0;
        for (int i = 0; i < steps.size(); i++) {
            final int n = i;
            GuidedDebugPanel.Card card = waitCard(label + " etapa " + (i + 1), x -> x.mode() == GuidedDebugPanel.Mode.FINISHED
                    || x.mode() == GuidedDebugPanel.Mode.STEP && x.badge() != GuidedDebugPanel.Badge.CHECKING && x.progress().startsWith((n + 1) + " de"));
            if (card.mode() == GuidedDebugPanel.Mode.FINISHED) break;
            AutomationStep st = steps.get(i);
            String expected = st.element().isPresent() || st.actionType() != com.nekkora.studio.domain.action.ActionType.OBSERVE ? expectedFor(st) : null;
            GuidedDebugClient.Inspection r = lastInspection();
            boolean resolved = r != null && r.status() == GuidedDebugClient.Status.RESOLVED;
            log("  " + label + " etapa " + (i + 1) + " (" + st.actionType() + "): " + card.badge().label()
                    + (r == null ? " · sem inspeção" : " · " + r.status() + " · " + r.reasons()));
            if (expected == null) {
                click(edt(dp::nextButton)); // etapa declarada sem alvo: nada a destacar
                continue;
            }
            where.put(st.id().value().toString(), elementRect(expected));
            if (resolved && r.box() != null && boxOn(r.box(), expected)) {
                direct++;
                if (card.badge() == GuidedDebugPanel.Badge.REUSED) {
                    waitCard("avanço", x -> x.mode() == GuidedDebugPanel.Mode.FINISHED || !x.progress().startsWith((n + 1) + " de"));
                } else {
                    click(edt(dp::continueButton));
                }
                continue;
            }
            check("G4 " + label + " etapa " + (i + 1) + ": sem prova de identidade, o debug oferece Corrigir (nunca Continuar sobre alvo incerto)",
                    card.canCorrect() && !(card.canContinue() && !resolved));
            click(edt(dp::correctButton));
            waitCard("corrigindo", x -> x.mode() == GuidedDebugPanel.Mode.CORRECTING);
            click(edt(dp::pickButton));
            waitCard("seleção no navegador", x -> x.mode() == GuidedDebugPanel.Mode.PICKING);
            clickInPage(expected);
            GuidedDebugPanel.Card cmp = waitCard("comparação", x -> x.mode() == GuidedDebugPanel.Mode.COMPARING);
            log("    comparação: " + cmp.comparison().verdict() + " · compatível " + cmp.comparison().compatible());
            check("G4 " + label + " etapa " + (i + 1) + ": elemento escolhido no navegador compatível com a ação", cmp.comparison().compatible());
            click(edt(dp::applyButton));
            waitCard("alvo novo verificado", x -> x.mode() == GuidedDebugPanel.Mode.FINISHED || x.mode() == GuidedDebugPanel.Mode.STEP
                    && x.badge() != GuidedDebugPanel.Badge.CHECKING);
            GuidedDebugPanel.Card now = edt(dp::card);
            if (now.mode() == GuidedDebugPanel.Mode.STEP && now.progress().startsWith((n + 1) + " de")) {
                if (now.badge() == GuidedDebugPanel.Badge.REUSED) {
                    waitCard("avanço", x -> x.mode() == GuidedDebugPanel.Mode.FINISHED || !x.progress().startsWith((n + 1) + " de"));
                } else if (now.canContinue()) {
                    click(edt(dp::continueButton));
                } else {
                    // correção aplicada (decisão registrada) mas a reverificação não comprova a identidade agora: o produto
                    // não deixa Continuar; avança e o estado fica registrado como está
                    log("    após a correção: " + now.badge().label() + " · " + now.message() + " — avança com Próxima");
                    reviewAfterCorrection++;
                    click(edt(dp::nextButton));
                }
            }
            corrected++;
        }
        waitCard("fim da passagem", x -> x.mode() == GuidedDebugPanel.Mode.FINISHED);
        log("  " + label + ": " + direct + " confirmada(s) diretamente, " + corrected + " por correção guiada");
        captureStudio("G4-" + label.replaceAll("[^A-Za-z0-9]+", "-"));
        click(edt(dp::finishButton));
        waitDebugClosed();
        return where;
    }

    /** Posição real na tela do elemento (só a homologação usa: prova de que as posições mudaram). */
    private Rectangle elementRect(String expr) throws Exception {
        @SuppressWarnings("unchecked")
        List<Object> e = (List<Object>) page.eval("((el) => { if (!el) return null; const r = el.getBoundingClientRect();"
                + " return [r.left, r.top, r.width, r.height]; })(" + expr + ")");
        if (e == null) return new Rectangle();
        return new Rectangle((int) Math.round(((Number) e.get(0)).doubleValue()), (int) Math.round(((Number) e.get(1)).doubleValue()),
                (int) Math.round(((Number) e.get(2)).doubleValue()), (int) Math.round(((Number) e.get(3)).doubleValue()));
    }

    private String expectedFor(AutomationStep s) throws Exception {
        if (s.actionType() == com.nekkora.studio.domain.action.ActionType.FILL) {
            String xp = s.element().flatMap(ElementReference::preferredLocator).map(LocatorCandidate::expression).orElse("");
            for (String l : LABELS) if (xp.contains("\"" + l + "\"")) return field(l);
            return null;
        }
        String name = s.element().map(ElementReference::accessibleName).orElse("");
        if (name.equalsIgnoreCase("submit")) return SUBMIT;
        if (name.equalsIgnoreCase("start")) return START;
        return DOWNLOAD;
    }

    private String codegenConfig(Automation a, StepId observe) throws Exception {
        String download = edt(() -> a.sessions().get(0).steps().get(0).id().value().toString());
        StringBuilder cols = new StringBuilder();
        for (int i = 0; i < LABELS.size(); i++) {
            if (i > 0) cols.append(",\n      ");
            cols.append("{\"header\": \"").append(LABELS.get(i)).append("\", \"variable\": \"").append(VARS.get(i)).append('"')
                    .append(LABELS.get(i).equals("Phone Number") ? ", \"integerAsText\": true" : "").append('}');
        }
        return """
                {"schema": "nekkora-studio/codegen-config", "version": "1.0.0", "processName": "rpa_challenge_input_forms",
                 "environment": "benchmark", "allowedOrigins": ["https://rpachallenge.com"], "startUrl": "https://rpachallenge.com/",
                 "sessions": {"init": [1], "perRecord": 2, "end": [3]}, "initAdvance": "REPLACED", "observations": true,
                 "input": {"format": "xlsx", "sheet": "Sheet1", "source": "DOWNLOAD", "downloadStep": "%s", "fileName": "challenge.xlsx",
                   "columns": [
                      %s]},
                 "textObservations": [{"stepId": "%s",
                   "pattern": "^Your success rate is \\\\d+(\\\\.\\\\d+)?%% \\\\( \\\\d+ out of \\\\d+ fields\\\\) in \\\\d+ milliseconds$"}]}
                """.formatted(download, cols, observe.value());
    }

    /** Automação → Gerar código Python… : preenche os campos da janela e clica Gerar (GUI real). */
    private String generateByMenu(Path config, Path outDir) throws Exception {
        menu("Automação", "Gerar código Python…");
        NekkoraDialog d = waitDialog("Gerar código Python");
        JTextComponent cfg = edt(() -> find(d, JTextComponent.class, f -> "codegen.config".equals(f.getName())));
        JTextComponent dir = edt(() -> find(d, JTextComponent.class, f -> "codegen.out".equals(f.getName())));
        JTextComponent res = edt(() -> find(d, JTextComponent.class, f -> "codegen.result".equals(f.getName())));
        setText(cfg, config.toString());
        setText(dir, outDir.toString());
        AbstractButton gb = edt(() -> find(d, AbstractButton.class, x -> "codegen.generate".equals(x.getName())));
        log("  diálogo de geração: " + edt(() -> d.getBounds() + " modal=" + d.isModal() + " · Gerar " + (gb == null ? "ausente"
                : gb.isShowing() ? gb.getLocationOnScreen() + " " + gb.getSize() + " habilitado=" + gb.isEnabled() : "oculto")));
        captureWindow(d, "G5-dialogo-" + (generations + 1));
        click(gb);
        String text = (String) waitValue("resultado da geração", 30, () -> edt(() -> res.getText().isBlank() ? null : res.getText()));
        captureWindow(d, "G5-gerar-" + (++generations));
        clickButtonWithText(d, "Fechar");
        waitClosed(d);
        return text;
    }

    private int generations;
    private int reviewAfterCorrection;

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
        labServer = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar",
                ext.resolve("java-core/lab-server/target/nekkora-lab-server.jar").toString(), String.valueOf(LAB_PORT), ext.resolve("test-lab/pages").toString())
                .redirectErrorStream(true).redirectOutput(new File(out, "lab-server.log")).start();
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        waitFor("laboratório respondendo", 20, () -> http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(PAGE)).build(),
                java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode() == 200);
        log("Laboratório local na porta " + LAB_PORT);
        profile = Files.createTempDirectory("nekkora-s012-chrome");
        // Perfil temporário sem o gerenciador de senhas do Chrome: o balão "Salvar senha?" não aparece nas evidências.
        Files.createDirectories(profile.resolve("Default"));
        Files.writeString(profile.resolve("Default").resolve("Preferences"),
                "{\"credentials_enable_service\": false, \"profile\": {\"password_manager_enabled\": false}}");
        testExtension = out.toPath().resolve("test-extension");
        copyExtension(ext.resolve("chrome-extension"), testExtension);
    }

    /** Cópia de homologação idêntica à extensão da branch (o laboratório é loopback: LAB, sem permissão extra). */
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
                "--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE localhost, EXCLUDE 127.0.0.1", "about:blank")
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
        File f = new File(out, name + ".png");
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
        try (PrintWriter w = new PrintWriter(new File(out, "S010-homologacao-log.txt"), StandardCharsets.UTF_8)) {
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
