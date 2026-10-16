package com.nekkora.studio.app.evidence;

import com.nekkora.studio.ui.ValidationPanel;
import com.nekkora.studio.validation.Finding;
import com.nekkora.studio.validation.FindingCode;
import com.nekkora.studio.validation.ValidationReport;
import com.nekkora.studio.validation.ValidationStatus;
import com.nekkora.tokens.NekkoraTheme;
import java.util.Set;
import com.nekkora.studio.app.StudioApplication;
import com.nekkora.studio.app.StudioEnvironment;
import com.nekkora.studio.app.recorder.RecorderPresenter;
import com.nekkora.studio.app.ui.StudioActions;
import com.nekkora.studio.app.workspace.ProjectService;
import com.nekkora.studio.app.workspace.ProjectState;
import com.nekkora.studio.app.workspace.Selection;
import com.nekkora.studio.bridge.BridgeServer;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.evidence.EvidenceType;
import com.nekkora.studio.domain.evidence.ObservedEvidence;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepOrigin;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import com.nekkora.studio.persistence.ProjectFiles;
import com.nekkora.studio.persistence.ProjectFormat;
import com.nekkora.studio.persistence.ProjectRepository;
import com.nekkora.studio.recorder.RecorderState;
import com.nekkora.studio.recorder.RecordingMode;
import com.nekkora.studio.ui.AutomationOverview;
import com.nekkora.studio.ui.LogicalSessionView;
import com.nekkora.studio.ui.form.StepDetailsForm;
import com.nekkora.studio.ui.recorder.FloatingRecorderPanel;
import com.nekkora.studio.ui.recorder.FloatingRecorderWindow;
import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.dialog.NekkoraDialog;
import com.nekkora.ui.input.NekkoraRadioButton;
import com.nekkora.ui.navigation.NekkoraToolbarButton;
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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Homologação REAL da STUDIO-007 (Automation Validation Engine) no Windows: Studio de verdade (dados e Bridge isolados por
 * {@code NEKKORA_STUDIO_DATA_DIR} e {@code NEKKORA_BRIDGE_DIR}), Chrome for Testing com a extensão da main, Native Host
 * instalado pelo script oficial, página FICTÍCIA do laboratório da STUDIO-005 e mouse/teclado do sistema operacional
 * (Robot). Menu Automação, atalho Ctrl+Shift+V, lista de apontamentos, editor de etapa existente, Ctrl+Z/Ctrl+Y, diálogos
 * de arquivo e troca de tema usados como o usuário. Cenários: (1) automação incompleta, (2) correção manual, (3)
 * Undo/Redo, (4) projeto salvo (inclusive um .nekkora da STUDIO-006), (5) evidências reais e (6) projeto extenso.
 *
 * <p>Uso: {@code java ... Studio007Homologation <pasta-de-saída> <repositório-da-extensão> <classpath-do-studio>}
 */
public final class Studio007Homologation {

    private static final int LAB_PORT = 18765;
    private static final String BASE = "http://127.0.0.1:" + LAB_PORT + "/lab/studio005/";
    /** Valores digitados na página (fictícios): NUNCA podem aparecer no arquivo do projeto, na recuperação, nos logs ou na tela. */
    private static final String USER = "anateste007";
    private static final String PASSWORD = "Senha2026Valid";
    private static final String NAME = "Login homologacao 007";
    private static final int CHROME_X = 880;

    private final File out;
    private final Path ext;
    private final String classpath;
    private final Robot robot;
    private final List<String> log = new ArrayList<>();
    private final List<String> logRecords = new CopyOnWriteArrayList<>();
    private int failures;
    private int checks;
    private volatile boolean exited;
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
    private Path projects;
    private Path dataDir;

    private Studio007Homologation(File out, Path ext, String classpath) throws AWTException {
        this.out = out;
        this.ext = ext;
        this.classpath = classpath;
        this.robot = new Robot();
        robot.setAutoDelay(25);
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Studio007Homologation h = new Studio007Homologation(out, Path.of(args[1]).toAbsolutePath().normalize(), args[2]);
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
        dataDir = Path.of(System.getenv("NEKKORA_STUDIO_DATA_DIR")).toAbsolutePath().normalize();
        projects = out.toPath().resolve("projetos").toAbsolutePath();
        Files.createDirectories(projects);
        captureLogs();
        prepareEnvironment();
        bridge = BridgeServer.start(BridgeServer.Options.defaults(StudioApplication.VERSION));
        window = edt(() -> StudioApplication.launch(Optional.of(NekkoraThemes.SAKURA_DARK), bridge, () -> exited = true, StudioEnvironment.installation(),
                List.of()));
        edt(() -> {
            window.frame().setSize(870, 1000);
            window.frame().setLocation(0, 0);
            window.frame().toFront();
            return null;
        });
        waitFor("janela do Studio", 10, () -> edt(() -> window.frame().isShowing()));
        launchChrome(BASE + "login-erro.html");
        connect();

        // ================================================================ Cenário 1 — Automação incompleta
        focusStudio();
        createAutomation("Consulta homologacao 007", "Pesquisa");
        Automation a = edt(() -> window.studio().state().activeAutomation().orElseThrow());
        LogicalSession pesquisa = edt(() -> a.sessions().get(0));
        edt(() -> {
            // etapas criadas pelo mesmo caso de uso do diálogo "Nova etapa" (ações sem os parâmetros necessários)
            window.studio().service().addManualStep(a.id(), pesquisa.id(), "Abrir portal", ActionType.NAVIGATE, null);
            window.studio().service().addManualStep(a.id(), pesquisa.id(), "Preencher pesquisa", ActionType.FILL, null);
            window.studio().service().addManualStep(a.id(), pesquisa.id(), "Selecionar estado", ActionType.SELECT_OPTION, null);
            window.studio().service().addManualStep(a.id(), pesquisa.id(), "Aguardar resultado", ActionType.WAIT_FOR, null);
            return null;
        });
        AutomationStep fill = edt(() -> pesquisa.steps().get(1));
        long generation = edt(() -> window.studio().state().project(a.id()).orElseThrow().generation());
        String undoLabel = edt(() -> window.studio().service().history().undoLabel(a.id()).orElse(""));
        check("Menu Automação com Nova sessão, Nova etapa e Validar automação; botão Validar automação na barra", edt(() -> {
            var bar = window.frame().getJMenuBar();
            JMenu m = bar.getMenu(1);
            return "Automação".equals(m.getText()) && m.getItemCount() == 4 && "Validar automação".equals(m.getItem(3).getText());
        }) && edt(() -> find(window.frame().getContentPane(), NekkoraToolbarButton.class, b -> b.getAction() != null
                && StudioActions.VALIDATE.equals(b.getAction().getValue(com.nekkora.ui.action.NekkoraAction.ID)))) != null);
        menu("Automação", "Validar automação");
        waitFor("painel de validação", 10, () -> edt(() -> window.studio().view().validationVisible()));
        ValidationReport r1 = report(a);
        check("S007-044 painel aberto pelo menu real com o relatório da automação ativa (" + r1.findings().size() + " apontamentos)",
                edt(() -> panel().findingList().getModel().getSize()) == r1.findings().size() && panelTexts().contains("Estado: Configuração incompleta"));
        check("S007-006/010/015/016 apontamentos: valor obrigatório, opção, endereço de navegação, condição de espera não modelada",
                codes(r1, fill).contains(FindingCode.REQUIRED_VALUE_MISSING) && r1.findings().stream().map(Finding::code).toList()
                        .containsAll(List.of(FindingCode.OPTION_NOT_CONFIGURED, FindingCode.NAVIGATION_TARGET_MISSING, FindingCode.WAIT_CONDITION_NOT_MODELED)));
        check("Severidade em texto e símbolo (não só cor): linha '× Bloqueio · Sessão 1 · Etapa 01 — Abrir portal'",
                edt(() -> rowText(0)).startsWith("×  Bloqueio · Sessão 1 · Etapa 01 — Abrir portal"));
        check("Observação 'não executou nenhuma etapa e não consultou o navegador' visível no painel", panelTexts().contains(ValidationPanel.NO_EXECUTION));
        check("S007-039/040 validar não marcou alteração (mesma geração) nem entrou no desfazer", edt(() -> window.studio().state().project(a.id())
                .orElseThrow().generation()) == generation && edt(() -> window.studio().service().history().undoLabel(a.id()).orElse("")).equals(undoLabel));
        captureStudio("01-automacao-incompleta");
        for (int i = 0; i < 4; i++) {
            NekkoraTheme t = edt(com.nekkora.ui.theme.ThemeManager::current);
            captureStudio("01-tema-" + t.id());
            focusStudioWorkspace();
            press(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_T); // atalho real de troca de tema
            sleep(400);
        }
        check("S007-048 painel nos quatro temas (troca real por Ctrl+Shift+T) sem erro", edt(() -> panel().isShowing()));

        // ================================================================ Cenário 2 — Correção manual
        int fillRow = edt(() -> indexOf(FindingCode.REQUIRED_VALUE_MISSING, fill));
        clickFindingRow(fillRow);
        click(edt(() -> panel().goToButton()));
        waitFor("etapa selecionada", 5, () -> edt(() -> window.studio().state().selection()) instanceof Selection.OfStep s && s.step().equals(fill.id()));
        check("S007-045 selecionar o apontamento e 'Ir para etapa' (mouse real) seleciona a etapa no Studio", true);
        click(edt(() -> panel().editButton()));
        NekkoraDialog d = waitDialog("Preencher pesquisa");
        StepDetailsForm f = edt(() -> find(d, StepDetailsForm.class, x -> true));
        clickTab(f, 1);
        click(edt(() -> f.modeInput(StepDetailsForm.ValueMode.VARIABLE)));
        click(edt(f::valueInput));
        typeIntoFocused("cliente.nome");
        captureWindow(d, "02-editor-de-etapa-existente");
        clickButtonWithText(d, "Salvar alterações");
        waitClosed(d);
        waitFor("relatório desatualizado", 5, () -> !edt(() -> panel().isCurrent()));
        check("S007-046 depois da edição o painel declara 'Resultado desatualizado' (nunca o antigo como atual)",
                edt(() -> panel().staleAlert().isVisible()) && panelTexts().contains(ValidationPanel.STALE_TITLE));
        captureStudio("02-resultado-desatualizado");
        validateByShortcut();
        ValidationReport r2 = report(a);
        check("S007-034 validar de novo (Ctrl+Shift+V real): o bloqueio de valor some; a variável vira dependência não comprovada",
                !codes(r2, fill).contains(FindingCode.REQUIRED_VALUE_MISSING) && codes(r2, fill).contains(FindingCode.VARIABLE_RESOLUTION_UNAVAILABLE)
                && edt(() -> panel().isCurrent()));
        captureStudio("02-corrigido");

        // ================================================================ Cenário 3 — Undo/Redo
        focusStudioWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_Z);
        waitFor("desfeito", 10, () -> edt(() -> fill.parameters().isEmpty()));
        check("S007-035 Ctrl+Z real: relatório anterior desatualizado", !edt(() -> panel().isCurrent()));
        validateByShortcut();
        check("S007-035 validar depois do Ctrl+Z: o bloqueio REAPARECE", codes(report(a), fill).contains(FindingCode.REQUIRED_VALUE_MISSING));
        captureStudio("03-depois-do-desfazer");
        focusStudioWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_Y);
        waitFor("refeito", 10, () -> edt(() -> !fill.parameters().isEmpty()));
        validateByShortcut();
        check("S007-036 Ctrl+Y real e validar: o bloqueio some de novo", !codes(report(a), fill).contains(FindingCode.REQUIRED_VALUE_MISSING));

        // ================================================================ Cenário 4 — Projeto salvo
        Path file = projects.resolve("consulta-007.nekkora");
        focusStudioWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_S);
        waitChooser("Salvar projeto como");
        typePath(file);
        waitFor("projeto salvo", 30, () -> Files.exists(file) && edt(() -> window.studio().state().project(a.id()).map(p -> !p.dirty()).orElse(false)));
        String shaSaved = sha(file);
        validateByShortcut();
        ValidationReport saved = report(a);
        check("S007-039 validar o projeto salvo: continua 'Salvo', aba sem *", projectStatus().equals("Salvo — consulta-007.nekkora") && tabTitle(a).equals(a.name()));
        menu("Arquivo", "Fechar projeto");
        waitFor("projeto fechado", 10, () -> edt(() -> window.studio().state().catalog().find(a.id()).isEmpty()));
        check("S007-039 fechar depois de validar: nenhum pedido de salvamento", edt(() -> dialogOpen("Fechar projeto?")) == false
                && projectStatus().equals("Nenhum projeto aberto") && sha(file).equals(shaSaved));
        menu("Arquivo", "Projetos recentes", "consulta-007.nekkora");
        waitFor("projeto reaberto", 30, () -> edt(() -> window.studio().state().activeAutomation().isPresent()));
        Automation reopened = edt(() -> window.studio().state().activeAutomation().orElseThrow());
        validateByShortcut();
        check("S007-037 projeto reaberto valida com os MESMOS apontamentos e ids", report(reopened).toJson().equals(saved.toJson()));
        check("S007-038 o arquivo não mudou (SHA-256) e nenhum arquivo de validação foi gravado ao lado", sha(file).equals(shaSaved) && leftovers().isEmpty()
                && !Files.exists(dataDir.resolve("validation")));
        Path fixture = projects.resolve("projeto-studio-006.nekkora");
        Files.copy(Path.of(System.getProperty("user.dir")).resolve("../nekkora-studio-persistence/src/test/resources/contracts/nekkora-project/1.0.0/fixtures/valid/consulta-clientes.nekkora")
                .normalize(), fixture, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        String shaFixture = sha(fixture);
        focusStudioWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_O);
        waitChooser("Abrir projeto");
        typePath(fixture);
        waitFor("projeto da STUDIO-006 aberto", 30, () -> edt(() -> window.studio().state().activeAutomation().map(x -> !x.id().equals(reopened.id())).orElse(false)));
        Automation old = edt(() -> window.studio().state().activeAutomation().orElseThrow());
        validateByShortcut();
        ValidationReport rOld = report(old);
        log("  projeto da STUDIO-006: " + rOld.summary() + " estado " + rOld.status());
        check("S007-037 projeto .nekkora 1.0.0 da STUDIO-006 (fixture congelada) aberto pelo diálogo real e validado: " + rOld.summary().steps() + " etapas",
                rOld.summary().steps() == edt(old::stepCount) && rOld.automationId().equals(old.id()));
        captureStudio("04-projeto-studio-006-validado");
        menu("Arquivo", "Fechar projeto");
        waitFor("fechado", 10, () -> edt(() -> window.studio().state().catalog().find(old.id()).isEmpty()));
        check("S007-038/039 fechar o projeto da STUDIO-006 depois de validar: sem perguntas, arquivo intacto (SHA-256)",
                sha(fixture).equals(shaFixture) && !edt(() -> dialogOpen("Fechar projeto?")));

        // ================================================================ Cenário 5 — Evidências (laboratório fictício da STUDIO-005)
        focusStudio();
        createAutomation(NAME, "Autenticação");
        Automation e = edt(() -> window.studio().state().activeAutomation().orElseThrow());
        LogicalSession auth = edt(() -> e.sessions().get(0));
        selectSession(e, auth);
        startRecordingOn("127.0.0.1", RecordingMode.CAPTURE_AND_INTERACT);
        FloatingRecorderPanel fp = edt(() -> window.studio().recorderPresenter().panel());
        moveFloatingAway();
        clickInPage("document.getElementById('usuario')");
        typeText(USER);
        clickInPage("document.getElementById('senha')");
        typeText(PASSWORD);
        waitFor("2 etapas gravadas", 30, () -> edt(auth::stepCount) == 2);
        validateByShortcut();
        check("Validar DURANTE a gravação é permitido (consulta) e não interfere: continua gravando", stateIs(RecorderState.RECORDING)
                && edt(() -> panel().isCurrent()));
        clickInPage("document.getElementById('entrar')");
        waitFor("3 etapas gravadas", 30, () -> edt(auth::stepCount) == 3);
        check("S007-047 etapa recebida do Recorder depois da validação: resultado desatualizado", !edt(() -> panel().isCurrent()));
        AutomationStep entrar = edt(() -> auth.steps().get(2));
        waitFor("fim da observação do Entrar", 25, () -> edt(() -> entrar.evidence().stream().anyMatch(x -> x.type() == EvidenceType.OBSERVATION_COMPLETE)));
        click(edt(fp::finishButton));
        waitState(RecorderState.COMPLETED);
        waitFor("quiescência", 30, () -> !edt(() -> RecorderPresenter.busyForSave(window.studio().recorder().snapshot(), e.id())));
        AbstractButton back = edt(() -> find(floating(), AbstractButton.class, x -> "Voltar ao Studio".equals(x.getText()) && x.isShowing()));
        if (back != null) click(back);
        validateByShortcut();
        ValidationReport r5 = report(e);
        Set<FindingCode> entrarCodes = codes(r5, entrar);
        log("  Entrar: " + entrarCodes);
        check("S007-028 evidência real observada vira INFORMAÇÃO (sinais e indicador de erro), nunca bloqueio nem aviso",
                entrarCodes.contains(FindingCode.OBSERVATION_SIGNALS) && entrarCodes.contains(FindingCode.OBSERVATION_ERROR_INDICATOR)
                && r5.forStep(entrar.id()).stream().filter(x -> x.field() == com.nekkora.studio.validation.FindingField.OBSERVATION)
                        .allMatch(x -> x.severity() == com.nekkora.studio.validation.Severity.INFO));
        check("S007-030 o diagnóstico não inventa resultado de negócio: NOT_VALIDATED; texto 'não conclui que a operação falhou, nem que foi validada'",
                edt(() -> entrar.observation().businessOutcome()) == RecordingObservation.BusinessOutcome.NOT_VALIDATED
                && FindingCode.OBSERVATION_ERROR_INDICATOR.explanation().contains("não conclui que a operação falhou, nem que foi validada"));
        check("Evidência não resolve bloqueio: os campos gravados continuam sem valor (o Recorder nunca guarda o que foi digitado)",
                codes(r5, edt(() -> auth.steps().get(0))).contains(FindingCode.REQUIRED_VALUE_MISSING) && r5.status() == ValidationStatus.INCOMPLETE);
        String visible = panelTexts();
        String json = r5.toJson();
        check("S007-026/027 nada da página nem do que foi digitado no painel ou no relatório", !visible.contains(USER) && !visible.contains(PASSWORD)
                && !json.contains(USER) && !json.contains(PASSWORD) && !visible.contains("inválidos") && !json.contains("inválidos") && !json.contains(NAME));
        captureStudio("05-evidencias-validadas");

        // ================================================================ Cenário 6 — Projeto extenso
        Path big = projects.resolve("extenso-5000.nekkora");
        writeSynthetic(big, 5000);
        focusStudioWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_O);
        waitChooser("Abrir projeto");
        typePath(big);
        waitFor("projeto extenso aberto", 60, () -> edt(() -> window.studio().state().activeAutomation().map(x -> x.stepCount() == 5000).orElse(false)));
        Automation x = edt(() -> window.studio().state().activeAutomation().orElseThrow());
        long[] maxGap = {0};
        java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
        Thread ping = new Thread(() -> {
            while (!stop.get()) {
                long t0 = System.nanoTime();
                try {
                    javax.swing.SwingUtilities.invokeAndWait(() -> { });
                } catch (Exception ignored) {
                    return;
                }
                maxGap[0] = Math.max(maxGap[0], System.nanoTime() - t0);
                sleep(5);
            }
        }, "medidor-edt");
        ping.start();
        long t0 = System.nanoTime();
        validateByShortcut();
        long total = (System.nanoTime() - t0) / 1_000_000;
        stop.set(true);
        ping.join();
        long engine = edt(() -> {
            long s0 = System.nanoTime();
            new com.nekkora.studio.validation.AutomationValidator().validate(x);
            return (System.nanoTime() - s0) / 1_000_000;
        });
        ValidationReport r6 = report(x);
        long gapMs = maxGap[0] / 1_000_000;
        log("  5.000 etapas: motor " + engine + " ms; maior espera da EDT durante Validar (atalho real, painel com " + r6.findings().size()
                + " linhas) " + gapMs + " ms; atalho→painel " + total + " ms");
        check("S007 §18 projeto extenso (5.000 etapas sintéticas, sem dados reais) validado; interface responsiva (maior espera da EDT " + gapMs + " ms < 1000 ms)",
                r6.summary().steps() == 5000 && gapMs < 1000 && edt(() -> panel().findingList().getModel().getSize()) == r6.findings().size());
        clickFindingRow(edt(() -> panel().findingList().getModel().getSize()) - 1 > 40 ? 40 : 0);
        check("Lista de 5.000 etapas navegável com o mouse", edt(() -> panel().findingList().getSelectedIndex() >= 0));
        captureStudio("06-projeto-extenso");
        check("Logs do Studio sem conteúdo do projeto", logRecords.stream().noneMatch(l -> l.contains(USER) || l.contains(PASSWORD) || l.contains("cliente.nome")));
        Files.write(new File(out, "S007-logs-do-studio.txt").toPath(), logRecords, StandardCharsets.UTF_8);
    }

    // ============================================================================== apoio STUDIO-007

    private ValidationPanel panel() {
        return window.studio().view().validationPanel();
    }

    private ValidationReport report(Automation a) throws Exception {
        return edt(() -> window.studio().validation().result(a.id()).orElseThrow().report());
    }

    private static Set<FindingCode> codes(ValidationReport r, AutomationStep s) {
        return r.forStep(s.id()).stream().map(Finding::code).collect(java.util.stream.Collectors.toSet());
    }

    private String panelTexts() throws Exception {
        return edt(() -> texts(panel()));
    }

    private String rowText(int index) {
        var list = panel().findingList();
        var item = list.getModel().getElementAt(index);
        list.getCellRenderer().getListCellRendererComponent(list, item, index, false, false);
        return list.getAccessibleContext().getAccessibleChild(index).getAccessibleContext().getAccessibleName();
    }

    private int indexOf(FindingCode code, AutomationStep step) {
        var list = panel().findingList();
        for (int i = 0; i < list.getModel().getSize(); i++) {
            Finding f = list.getModel().getElementAt(i).finding();
            if (f.code() == code && step.id().equals(f.step())) return i;
        }
        throw new IllegalStateException("apontamento não listado: " + code);
    }

    /** Clique REAL numa linha da lista de apontamentos. */
    private void clickFindingRow(int index) throws Exception {
        var list = edt(() -> panel().findingList());
        Rectangle r = edt(() -> {
            list.ensureIndexIsVisible(index);
            Rectangle cell = list.getCellBounds(index, index);
            java.awt.Point p = list.getLocationOnScreen();
            return new Rectangle(p.x + cell.x, p.y + cell.y, cell.width, cell.height);
        });
        robot.mouseMove(r.x + Math.min(r.width / 2, 160), r.y + r.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        waitFor("apontamento selecionado", 10, () -> edt(() -> list.getSelectedIndex() == index));
        sleep(300);
    }

    /** Ctrl+Shift+V real com o foco na área de trabalho; espera o painel mostrar o relatório do estado atual. */
    private void validateByShortcut() throws Exception {
        focusStudioWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_V);
        waitFor("relatório atual no painel", 20, () -> edt(() -> window.studio().view().validationVisible() && panel().isCurrent()));
        sleep(300);
    }

    private boolean dialogOpen(String title) {
        for (Window w : Window.getWindows()) if (w instanceof NekkoraDialog nd && nd.isShowing() && title.equals(nd.getTitle())) return true;
        return false;
    }

    /** Projeto sintético (fictício) com etapas manuais variadas, gravado pelo formato real (fora da EDT). */
    private static void writeSynthetic(Path file, int steps) throws Exception {
        Automation a = Automation.create("Processo sintetico " + steps, "Dados fictícios para medir desempenho", Clock.systemUTC());
        LogicalSession s = null;
        for (int i = 0; i < steps; i++) {
            if (i % 50 == 0) s = a.addSession("Bloco " + (i / 50 + 1), null);
            ActionType t = switch (i % 5) {
                case 0 -> ActionType.FILL;
                case 1 -> ActionType.CLICK;
                case 2 -> ActionType.SELECT_OPTION;
                case 3 -> ActionType.NAVIGATE;
                default -> ActionType.WAIT_FOR;
            };
            AutomationStep st = a.appendStep(s.id(), com.nekkora.studio.domain.StepDraft.builder().name("Etapa " + (i + 1)).actionType(t).build());
            if (t == ActionType.FILL && i % 3 != 0) {
                a.setStepParameters(st.id(), com.nekkora.studio.domain.action.ActionParameters.withValue(
                        new com.nekkora.studio.domain.action.ValueSource.VariableReference("campo.v" + i)));
            } else if (t == ActionType.NAVIGATE) {
                a.setStepParameters(st.id(), com.nekkora.studio.domain.action.ActionParameters.withValue(
                        new com.nekkora.studio.domain.action.ValueSource.LiteralValue("https://lab.exemplo.test/p" + i)));
            }
        }
        new ProjectFiles(new ProjectRepository(), "homologacao-007").save(file, ProjectFiles.Snapshot.of(a, java.util.UUID.randomUUID().toString()), null,
                false, Instant.now(), Clock.systemUTC());
    }

    // ============================================================================== apoio STUDIO-006

    private void captureLogs() {
        java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
        java.util.logging.SimpleFormatter f = new java.util.logging.SimpleFormatter();
        root.addHandler(new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord r) {
                logRecords.add(r.getLevel() + " " + r.getLoggerName() + " " + f.formatMessage(r));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    private String projectStatus() throws Exception {
        return edt(() -> window.studio().view().statusItems().get(0).getText());
    }

    private String tabTitle(Automation a) throws Exception {
        return edt(() -> {
            var tabs = window.studio().view().workArea().getTabs();
            for (int i = 0; i < tabs.getTabCount(); i++) {
                String t = tabs.getTitleAt(i);
                if (t.equals(a.name()) || t.equals(a.name() + " *")) return t;
            }
            return "";
        });
    }

    /** Diálogo de arquivos do sistema (JFileChooser) aberto, ou null. */
    private Window fileChooser() throws Exception {
        return edt(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof javax.swing.JDialog d && d.isShowing() && find(d, javax.swing.JFileChooser.class, x -> true) != null) return w;
            }
            return null;
        });
    }

    private Window waitChooser(String title) throws Exception {
        waitFor("diálogo " + title, 15, () -> fileChooser() != null && title.equals(((javax.swing.JDialog) fileChooser()).getTitle()));
        sleep(500);
        return fileChooser();
    }

    /** Digita o caminho no campo "Nome do arquivo" do diálogo (colado, como o usuário faria) e confirma com Enter. */
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

    /** Menu da barra (mouse): "Arquivo" → item (→ subitem). */
    private void menu(String... path) throws Exception {
        focusStudio();
        JMenu top = edt(() -> {
            var bar = window.frame().getJMenuBar();
            for (int i = 0; i < bar.getMenuCount(); i++) if (path[0].equals(bar.getMenu(i).getText())) return bar.getMenu(i);
            return null;
        });
        click(top);
        waitFor("menu " + path[0], 5, () -> edt(top::isPopupMenuVisible));
        JMenu current = top;
        for (int i = 1; i < path.length; i++) {
            String name = path[i];
            JMenu parent = current;
            waitFor("item " + name, 5, () -> edt(() -> findItem(parent, name)) != null);
            JMenuItem item = edt(() -> findItem(parent, name));
            click(item);
            if (item instanceof JMenu sub) {
                waitFor("submenu " + name, 5, () -> edt(sub::isPopupMenuVisible));
                current = sub;
            }
        }
        sleep(300);
    }

    private static JMenuItem findItem(JMenu menu, String text) {
        for (Component c : menu.getPopupMenu().getComponents()) {
            if (c instanceof JMenuItem it && text.equals(it.getText()) && it.isShowing()) return it;
        }
        return null;
    }

    private List<Path> leftovers() throws IOException {
        try (Stream<Path> s = Files.list(projects)) {
            return s.filter(p -> !p.getFileName().toString().endsWith(".nekkora")).toList();
        }
    }

    static Map<String, byte[]> entries(Path file) throws IOException {
        Map<String, byte[]> m = new LinkedHashMap<>();
        try (ZipFile z = new ZipFile(file.toFile())) {
            for (ZipEntry e : java.util.Collections.list(z.entries())) m.put(e.getName(), z.getInputStream(e).readAllBytes());
        }
        return m;
    }

    private static String joined(Map<String, byte[]> entries) {
        StringBuilder b = new StringBuilder();
        for (byte[] v : entries.values()) b.append(new String(v, StandardCharsets.UTF_8)).append('\n');
        return b.toString();
    }

    static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    static String sha(Path file) throws Exception {
        return sha(Files.readAllBytes(file));
    }

    static String sha(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    /** Cópia adulterada de um projeto com o manifesto RECALCULADO: só o conteúdo fica inválido. */
    @SuppressWarnings("unchecked")
    private static Path tamper(Path from, Path to, java.util.function.BiConsumer<String, Map<String, Object>> change) throws Exception {
        Map<String, byte[]> e = entries(from);
        for (Map.Entry<String, byte[]> en : e.entrySet()) {
            if (en.getKey().equals("manifest.json")) continue;
            Map<String, Object> doc = Json.parseObject(new String(en.getValue(), StandardCharsets.UTF_8));
            String before = Json.write(doc);
            change.accept(en.getKey(), doc);
            if (!Json.write(doc).equals(before)) en.setValue(Json.write(doc).getBytes(StandardCharsets.UTF_8));
        }
        Map<String, Object> manifest = Json.parseObject(new String(e.get("manifest.json"), StandardCharsets.UTF_8));
        change.accept("manifest.json", manifest);
        List<Object> list = new ArrayList<>();
        for (Map.Entry<String, byte[]> en : e.entrySet()) {
            if (en.getKey().equals("manifest.json")) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("path", en.getKey());
            m.put("sha256", sha(en.getValue()));
            m.put("size", (long) en.getValue().length);
            list.add(m);
        }
        manifest.put("entries", list);
        e.put("manifest.json", Json.write(manifest).getBytes(StandardCharsets.UTF_8));
        Files.write(to, zip(e));
        return to;
    }

    // ============================================================================== Studio

    private void createAutomation(String name, String session) throws Exception {
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_N);
        NekkoraDialog d = waitDialog("Nova automação");
        typeIntoFocused(name);
        press(KeyEvent.VK_ENTER);
        waitClosed(d);
        newSession(session);
    }

    // ============================================================================== apoio STUDIO-005

    private static final long QUIET_MS = 3300;

    private boolean has(AutomationStep st, EvidenceType t) throws Exception {
        return edt(() -> st.evidence().stream().anyMatch(e -> e.type() == t));
    }

    private ObservedEvidence waitEvidence(String what, AutomationStep st, EvidenceType t) throws Exception {
        waitFor(what, 25, () -> has(st, t));
        return edt(() -> st.evidence().stream().filter(e -> e.type() == t).findFirst().orElseThrow());
    }

    // ============================================================================== apoio STUDIO-004

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
        profile = Files.createTempDirectory("nekkora-s006-chrome");
        // Perfil temporário sem o gerenciador de senhas do Chrome: o balão "Salvar senha?" não aparece nas evidências.
        Files.createDirectories(profile.resolve("Default"));
        Files.writeString(profile.resolve("Default").resolve("Preferences"),
                "{\"credentials_enable_service\": false, \"profile\": {\"password_manager_enabled\": false}}");
        testExtension = out.toPath().resolve("test-extension");
        copyExtension(ext.resolve("chrome-extension"), testExtension);
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        waitFor("laboratório respondendo", 20, () -> http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(BASE + "login-erro.html")).build(),
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

    /** Captura SÓ do conteúdo da janela do Studio (sem as bordas do sistema): nada da área de trabalho nem de outras janelas. */
    private void captureStudio(String name) throws Exception {
        robot.waitForIdle();
        sleep(400);
        Rectangle content = edt(() -> new Rectangle(window.frame().getRootPane().getLocationOnScreen(), window.frame().getRootPane().getSize()));
        save(robot.createScreenCapture(content), name);
    }

    private void captureWindow(Window w, String name) throws Exception {
        robot.waitForIdle();
        sleep(250);
        javax.swing.JRootPane root = edt(() -> ((javax.swing.RootPaneContainer) w).getRootPane());
        save(robot.createScreenCapture(edt(() -> new Rectangle(root.getLocationOnScreen(), root.getSize()))), name);
    }

    private void save(BufferedImage img, String name) throws IOException {
        File f = new File(out, "S007-" + name + ".png");
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
        try (PrintWriter w = new PrintWriter(new File(out, "S007-homologacao-log.txt"), StandardCharsets.UTF_8)) {
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
