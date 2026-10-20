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
import com.nekkora.studio.ai.spec.FunctionalSpec;
import com.nekkora.studio.domain.action.ActionType;
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
 * Homologação REAL da STUDIO-014 no Windows: Studio de verdade (dados isolados), Chrome for Testing com a extensão DESTA
 * branch, Native Host do script oficial, laboratório local (dados fictícios) e mouse/teclado do sistema (Robot). Operador:
 * Claude como AGENTE DE TESTE (TEST_AGENT). Caso A (login → consultar → ler → sair, sem planilha: gravação, debug, PDD,
 * Desenvolver automação, Validar automação, execução no laboratório com oráculo do servidor), Caso C (PDD desatualizado,
 * efeito desconhecido, alvo em iframe), Caso D (IA opt-in com provedor MOCK rotulado — a integração real não é homologada
 * aqui) e Caso B (regressão tabular da E2E-001 na réplica local).
 *
 * <p>Uso: {@code java -Dnekkora.studio.decisionActor=TEST_AGENT ... Studio014Homologation <saída> <repositório-da-extensão>}
 */
public final class Studio014Homologation {

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

    private Studio014Homologation(File out, Path ext) throws AWTException {
        this.out = out;
        this.ext = ext;
        this.robot = new Robot();
        robot.setAutoDelay(25);
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Studio014Homologation h = new Studio014Homologation(out, Path.of(args[1]).toAbsolutePath().normalize());
        h.scale = args.length > 2 && "escala".equals(args[2]);
        h.sc = h.scale ? 1.5 : 1;
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

    // ============================================================================== roteiro STUDIO-014

    private void run() throws Exception {
        log("Tela: " + GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds() + " · java " + System.getProperty("java.version"));
        log("Operador: Claude como AGENTE DE TESTE (TEST_AGENT; nenhuma confirmação humana) · ator do Studio: "
                + System.getProperty("nekkora.studio.decisionActor"));
        log("Laboratório: " + PORTAL + " (dados 100% fictícios; oráculo do servidor /api/studio009/estado)");
        log("Python de verificação do Studio (NEKKORA_PYTHON): " + (System.getenv("NEKKORA_PYTHON") == null ? "não definido" : "definido"));
        SENTINELS.addAll(List.of(USER_VALUE, PASS_VALUE));
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
            window.studio().useAiProvider(mock); // Caso D: MOCK rotulado (sem chave real nesta máquina) — nunca o Claude Code
            return null;
        });
        waitFor("janela do Studio", 10, () -> edt(() -> window.frame().isShowing()));
        if (scale) {
            scaleRun();
            return;
        }
        launchChrome("about:blank");
        navigate(PORTAL);
        connect();
        oracleReset();

        // ================================================================ Caso A — gravação real (Recorder)
        focusStudio();
        createAutomation("Consulta de registro (laboratório)", List.of("Acesso", "Consulta"));
        Automation a = active();
        LogicalSession s1 = edt(() -> a.sessions().get(0));
        LogicalSession s2 = edt(() -> a.sessions().get(1));
        selectSession(a, s1);
        startRecordingOn("127.0.0.1", RecordingMode.CAPTURE_AND_INTERACT);
        moveFloatingAway();
        clickInPage(USER);
        insertText(USER_VALUE);
        clickInPage(PASS);
        insertText(PASS_VALUE);
        clickInPage(ENTER);
        waitPage("consulta.html");
        waitFor("3 etapas no Acesso", 30, () -> edt(s1::stepCount) >= 3);
        finishRecording();
        selectSession(a, s2);
        startRecordingOn("127.0.0.1", RecordingMode.CAPTURE_AND_INTERACT);
        moveFloatingAway();
        clickInPage(CODE);
        insertText(CODE_VALUE);
        clickInPage(QUERY);
        waitFor("resultado na página", 20, () -> page.eval("!!document.getElementById('resultado')") == Boolean.TRUE);
        clickInPage(RESULT);
        clickInPage(EXIT);
        waitPage("portal.html");
        waitFor("4 etapas na Consulta", 30, () -> edt(s2::stepCount) >= 4);
        finishRecording();
        log("  etapas: " + edt(() -> kinds(s1)) + " · " + edt(() -> kinds(s2)));
        check("A: Acesso = preencher, preencher, clicar; Consulta = preencher, clicar, interação (área do resultado), clicar",
                edt(() -> kinds(s1).equals(List.of("FILL", "FILL", "CLICK")) && kinds(s2).equals(List.of("FILL", "CLICK", "UNKNOWN", "CLICK"))));
        check("A: nenhum valor digitado no modelo (usuário, senha e código ficam fora)", SENTINELS.stream().noneMatch(edt(() -> domainText(a))::contains)
                && !edt(() -> domainText(a)).contains(CODE_VALUE));
        AutomationStep result = edt(() -> s2.steps().get(2));
        edt(() -> {
            // edição do Studio (TEST_AGENT): a área do resultado é LIDA — a etapa passa a Observar (mesma operação do editor de etapa)
            window.studio().service().updateStepBasics(a.id(), result.id(), "Ler o resultado da consulta", "", ActionType.OBSERVE);
            return null;
        });
        check("A: a área do resultado vira etapa Observar com o alvo gravado", edt(() -> kinds(s2).equals(List.of("FILL", "CLICK", "OBSERVE", "CLICK"))
                && s2.steps().get(2).element().isPresent()));
        Path project = out.toPath().resolve("projeto").resolve("consulta-registro.nekkora");
        Files.createDirectories(project.getParent());
        saveAs(project);
        check("A: projeto salvo (.nekkora)", Files.isRegularFile(project));
        // debug guiado: portal (etapas 1–3) e consulta (etapas 4–7), com o resultado na tela
        navigate(PORTAL);
        debugPass("portal", 0, 3);
        navigate(CONSULTA);
        page.eval("document.getElementById('registro-codigo').value = 'REG-0001'; document.getElementById('consultar').click(); true");
        waitFor("resultado (preparação do debug)", 20, () -> page.eval("!!document.getElementById('resultado')") == Boolean.TRUE);
        debugPass("consulta", 3, 7);
        check("A: todas as etapas com decisão CONFIRMED no debug guiado (TEST_AGENT)", edt(() -> a.sessions().stream().flatMap(x -> x.steps().stream())
                .allMatch(s -> s.targetDecision().map(d -> d.confirmed() && d.actor() == DecisionActor.TEST_AGENT).orElse(false))));
        save();
        int consultasDepoisDoDebug = oracleTotal();

        // ================================================================ PDD visual (revisão pelo agente de teste)
        reviewPddWindow(List.of("Acesso", "Consulta"), List.of("Entrar no portal", "Consultar o registro e ler o resultado"));
        // ================================================================ Desenvolver automação — UI real
        Path gen = project.resolveSibling("consulta-registro-python");
        DevelopmentPanel dp = openDevelopment();
        DevelopmentPanel.Model m1 = waitDevIdle("análise inicial");
        captureWindow(devDialog(), "A-01-desenvolver-pergunta-endereco");
        log("  fases: " + edt(() -> dp.phasesText().getText()));
        log("  pendências: " + m1.pending().stream().map(i -> i.kind() + ":" + i.code()).toList());
        check("A: um clique já analisa (sem formulário nem *.codegen.json); a única pergunta é o endereço inicial (mascarado pela privacidade)",
                m1.pending().stream().filter(i -> i.kind() == QualityIssue.Kind.HELP_REQUIRED).map(QualityIssue::code).toList()
                        .equals(List.of("SPEC_START_URL_UNKNOWN")) && !Files.exists(gen));
        check("A: nenhum *.codegen.json pedido ou criado ao lado do projeto", !Files.exists(project.resolveSibling("consulta-registro.codegen.json")));
        answerText(dp, "SPEC_START_URL_UNKNOWN", "http://127.0.0.1:18765/lab/studio014/portal.html?x=1");
        check("A: endereço fora do modelo gravado é recusado (consulta pode carregar dados)", textShown("Endereço recusado"));
        closeTextDialog();
        answerText(dp, "SPEC_START_URL_UNKNOWN", PORTAL);
        DevelopmentPanel.Model m2 = waitDevIdle("geração");
        captureWindow(devDialog(), "A-02-desenvolver-resultado");
        log("  resultado:\n" + String.join("\n", m2.results()).indent(4));
        check("A: CODE_GENERATED pelo gerador oficial em <projeto>-python/", m2.results().contains("✓ CODE_GENERATED") && Files.isRegularFile(gen.resolve("manifest.json")));
        check("A: CODE_VALIDATED — compilação, importação, ruff, ruff format, mypy estrito e testes de contrato REAIS sem problema",
                m2.results().contains("✓ CODE_VALIDATED") && m2.results().stream().filter(r -> r.startsWith("✓ ") && r.contains("(") && r.endsWith("ms)")).count() == 6);
        check("A: variáveis derivadas registradas (desfazível) — entrada, saída e 2 credenciais, sem constantes",
                edt(() -> a.variables().stream().map(v -> v.name() + ":" + v.origin().kind()).sorted().toList())
                        .equals(List.of("codigo_do_registro:INPUT", "resultado_da_consulta:STEP_OUTPUT")) && m2.results().stream().anyMatch(r -> r.contains("Ctrl+Z")));
        selectTabOf(dp, 0);
        captureWindow(devDialog(), "A-03-o-que-vou-utilizar");
        // 1366×768: a janela no tamanho que caberia nessa tela (área útil ~1366×728) — botões visíveis e dentro dela
        edt(() -> {
            devDialog().setSize((int) Math.round(Math.min(1180, 1366 - 40) / 1.0), 728 - 40);
            devDialog().validate();
            return null;
        });
        sleep(700);
        captureWindow(devDialog(), "A-03b-desenvolver-1366x768");
        check("UX: em 1366×768 os botões Desenvolver, Abrir código, Validar novamente e Fechar ficam visíveis dentro da janela", edt(() -> {
            Rectangle w = devDialog().getBounds();
            for (String t : List.of("Desenvolver de novo", "Abrir código", "Validar novamente", "Fechar")) {
                AbstractButton b = find(devDialog(), AbstractButton.class, x -> t.equals(x.getText()) && x.isShowing());
                if (b == null) return false;
                Rectangle r = new Rectangle(b.getLocationOnScreen(), b.getSize());
                if (!w.contains(r)) return false;
            }
            return true;
        }));
        if (scale) {
            // 150% (JVM com uiScale 1.5): a mesma janela nos quatro temas
            for (int i = 0; i < 4; i++) {
                String theme = edt(() -> com.nekkora.ui.theme.ThemeManager.current().id());
                captureWindow(devDialog(), "E-desenvolver-150-" + theme.replaceAll("[^A-Za-z0-9]+", "-"));
                check("150% · tema " + theme + ": botões da janela visíveis e dentro dela", edt(() -> {
                    Rectangle w = devDialog().getBounds();
                    AbstractButton b = find(devDialog(), AbstractButton.class, x -> "Fechar".equals(x.getText()) && x.isShowing());
                    return b != null && w.contains(new Rectangle(b.getLocationOnScreen(), b.getSize()));
                }));
                edt(() -> {
                    com.nekkora.studio.app.ui.StudioActions.cycleTheme();
                    return null;
                });
                sleep(800);
            }
            return;
        }
        log("  o que vou utilizar: " + m2.uses().stream().map(DevelopmentPanel.Use::name).toList());
        check("A: 'O que vou utilizar' vem do catálogo real (import, arquivo e linha do pacote instalado)", m2.uses().stream().anyMatch(u ->
                u.name().equals("Actions.read_text") && u.where().contains("nekkora_rpa/libs/actions.py:")) && m2.uses().stream().noneMatch(u -> u.id().startsWith("tabular")));
        String code = dirText(gen);
        check("A: código gerado sem os valores digitados e sem localizador posicional", SENTINELS.stream().noneMatch(code::contains) && !code.contains(CODE_VALUE)
                && !code.contains("nth-of-type") && !code.contains("nth-child"));
        check("A: StepId de cada etapa no código", edt(() -> a.sessions().stream().flatMap(x -> x.steps().stream()).allMatch(s -> code.contains(s.id().value().toString()))));
        Files.copy(gen.resolve("paginas.py"), out.toPath().resolve("A-paginas.py"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(gen.resolve("processo.py"), out.toPath().resolve("A-processo.py"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(gen.resolve("manifest.json"), out.toPath().resolve("A-manifest.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        closeDevelopment();
        save();

        // ================================================================ Validar automação (Ctrl+Shift+V) — camadas
        focusStudioWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_V);
        String layers = (String) waitValue("camadas com V4/V5 verificadas", 120, () -> {
            String t = edt(() -> window.studio().view().validationPanel().layersText().getAccessibleContext().getAccessibleDescription());
            return t != null && t.contains("V4 Código Python") && !t.contains("verificando") && !t.contains("procurando") && !t.contains("aguardando") ? t : null;
        });
        captureStudio("A-04-validar-camadas");
        log("  camadas: " + layers);
        check("A: Validar automação — V1 PDD_READY, V4 e V5 com as ferramentas reais, V6 'não executada pelo Studio'", layers.contains("V1 PDD: PDD_READY")
                && layers.contains("V4 Código Python: 5 de 5 verificação(ões) sem problema")
                && layers.contains("V5 Testes: ") && layers.contains("V6 Execução real: não executada pelo Studio"));
        check("A: vocabulário honesto (nada de aprovado, garantido, pronto para produção, testado em execução)", !layers.toLowerCase().matches(
                ".*(aprovad|garantid|pronto para produ|testado em execu|tudo conforme).*"));

        // ================================================================ execução no LABORATÓRIO autorizado + oráculo independente
        Path runDir = Files.createTempDirectory("nk014-exec");
        Path entrada = runDir.resolve("entrada.json");
        Files.writeString(entrada, "{\"codigo_do_registro\": \"" + CODE_VALUE + "\"}", StandardCharsets.UTF_8);
        int before = oracleTotal();
        log("  oráculo antes: " + before + " consulta(s) (depois do debug: " + consultasDepoisDoDebug + ")");
        Map<String, String> env = Map.of("NK_SECRET_CREDENCIAL_USUARIO", USER_VALUE, "NK_SECRET_CREDENCIAL_SENHA", PASS_VALUE);
        String exec = runPython(List.of(gen.resolve("main.py").toString(), "--saida", runDir.resolve("saida").toString(), "--entrada", entrada.toString(),
                "--channel", "chromium", "--headless"), gen, env);
        log("  execução (resumo do processo): " + exec.strip().lines().reduce((x, y) -> y).orElse(""));
        int after = oracleTotal();
        String last = oracleLastCode();
        log("  oráculo depois: " + after + " consulta(s); última: " + (CODE_VALUE.equals(last) ? "o código da entrada" : "outro código"));
        check("A: EXECUÇÃO no laboratório — o oráculo do SERVIDOR registrou exatamente 1 consulta nova, com o código da entrada",
                after == before + 1 && CODE_VALUE.equals(last));
        String output = resultOutput(runDir.resolve("saida").resolve("resultados"));
        check("A: o resultado lido pelo processo é o que o oráculo responde para o registro (situação SUSPENSO)",
                output.contains("\"resultado_da_consulta\": \"Registro encontrado: situação SUSPENSO\""));
        String events = dirText(runDir.resolve("saida").resolve("execucao"));
        check("A: eventos do processo sem usuário, senha, código ou resultado", SENTINELS.stream().noneMatch(events::contains) && !events.contains(CODE_VALUE)
                && !events.contains("SUSPENSO"));
        Files.writeString(out.toPath().resolve("A-execucao-resumo.txt"), "oráculo antes=" + before + " depois=" + after + " último código confere="
                + CODE_VALUE.equals(last) + "\n" + exec.strip().lines().reduce((x, y) -> y).orElse("") + "\n", StandardCharsets.UTF_8);
        deleteTree(runDir);

        // ================================================================ Caso C1 — PDD desatualizado bloqueia; Abrir PDD; retoma
        byte[] manifestBefore = Files.readAllBytes(gen.resolve("manifest.json"));
        edt(() -> {
            window.studio().service().renameSession(a.id(), s2.id(), "Consulta de registro");
            return null;
        });
        DevelopmentPanel dp2 = openDevelopment();
        DevelopmentPanel.Model c1 = waitDevIdle("PDD desatualizado");
        captureWindow(devDialog(), "C1-01-pdd-desatualizado");
        check("C1: modelo mudou → PDD_OUTDATED bloqueia em Verificando PDD; nenhum arquivo muda",
                c1.pending().stream().anyMatch(i -> i.code().equals("PDD_OUTDATED")) && java.util.Arrays.equals(manifestBefore, Files.readAllBytes(gen.resolve("manifest.json"))));
        clickPendingButton(dp2, "PDD_OUTDATED", "Abrir PDD");
        PddPanel pp = edt(() -> window.studio().visualPdd().panel());
        waitFor("PDD aberto", 15, () -> edt(() -> pp.isShowing()));
        click(edt(pp::refreshButton));
        sleep(800);
        reviewOpenPdd(pp, List.of("Acesso", "Consulta de registro"), List.of("Entrar no portal", "Consultar o registro e ler o resultado"));
        closePdd();
        DevelopmentPanel dp3 = openDevelopment();
        DevelopmentPanel.Model c1b = waitDevIdle("retomada");
        check("C1: depois de atualizar o PDD, a análise retoma e o código é regenerado na revisão nova",
                c1b.results().contains("✓ CODE_GENERATED") && !java.util.Arrays.equals(manifestBefore, Files.readAllBytes(gen.resolve("manifest.json"))));
        closeDevelopment();
        save();

        // ================================================================ Caso C2 + D — efeito desconhecido ("Processar"), ajuda e IA (mock)
        navigate(CONSULTA);
        focusStudio();
        createAutomation("Processar registro (laboratório)", List.of("Processamento"));
        Automation c = active();
        LogicalSession p1 = edt(() -> c.sessions().get(0));
        selectSession(c, p1);
        startRecordingOn("127.0.0.1", RecordingMode.CAPTURE_AND_INTERACT);
        moveFloatingAway();
        clickInPage(CODE);
        insertText(CODE_VALUE);
        clickInPage(PROCESS);
        clickInPage(EXIT);
        waitPage("portal.html");
        waitFor("3 etapas", 30, () -> edt(p1::stepCount) >= 3);
        finishRecording();
        check("C2: o botão \"Calcular\" foi gravado com o rótulo (de interface, mas fora do vocabulário de efeitos)", "Calcular".equals(edt(() -> p1.steps()
                .get(1).element().map(ElementReference::accessibleName).orElse(""))));
        Path project2 = out.toPath().resolve("projeto").resolve("processar-registro.nekkora");
        saveAs(project2);
        navigate(CONSULTA);
        debugPass("processar", 0, 3);
        save();
        reviewPddWindow(List.of("Processamento"), List.of("Processar o registro"));
        DevelopmentPanel dq = openDevelopment();
        DevelopmentPanel.Model q1 = waitDevIdle("perguntas");
        log("  pendências: " + q1.pending().stream().map(i -> i.kind() + ":" + i.code()).toList());
        check("C2: perguntas objetivas por etapa — efeito do clique sem rótulo, conclusão e endereço inicial; nenhum arquivo",
                q1.pending().stream().map(QualityIssue::code).toList().containsAll(List.of("SPEC_EFFECT_UNKNOWN", "SPEC_CONCLUSION_MISSING", "SPEC_START_URL_UNKNOWN"))
                        && !Files.exists(project2.resolveSibling("processar-registro-python")));
        selectPending(dq, "SPEC_EFFECT_UNKNOWN");
        captureWindow(devDialog(), "C2-01-pergunta-efeito");
        // Caso D: Solicitar ajuda → prévia do envio → "Não enviar" (nada sai) → de novo → Enviar (MOCK) → proposta → Revisar e aplicar
        int callsBefore = mock.calls();
        clickButtonWithText(dq, "Solicitar ajuda (IA opcional)");
        NekkoraDialog consent = waitDialog("Enviar para análise por IA?");
        captureWindow(consent, "D-01-previa-do-envio");
        String preview = edt(() -> texts(consent));
        check("D: a prévia mostra o que vai e o que NÃO vai (sem URL, valores, credenciais)", preview.contains("Não vai:") && !preview.contains("127.0.0.1")
                && SENTINELS.stream().noneMatch(preview::contains));
        clickButtonWithText(consent, "Não enviar");
        waitClosed(consent);
        check("D: recusar o envio não chama o provedor", mock.calls() == callsBefore);
        NekkoraDialog consent2 = null;
        for (int attempt = 0; attempt < 3 && consent2 == null; attempt++) {
            clickButtonWithText(dq, "Solicitar ajuda (IA opcional)");
            try {
                consent2 = waitDialog("Enviar para análise por IA?");
            } catch (IllegalStateException e) {
                log("  a prévia não abriu nesta tentativa; de novo");
            }
        }
        clickButtonWithText(consent2, "Enviar esta análise");
        waitClosed(consent2);
        DevelopmentPanel.Model d1 = waitDevIdle("propostas da IA (mock)");
        captureWindow(devDialog(), "D-02-proposta-da-ia-mock");
        log("  propostas: " + d1.pending().stream().filter(i -> i.code().equals("AI_PROPOSAL")).map(QualityIssue::title).toList() + " · " + d1.statusText());
        check("D: provedor MOCK chamado uma vez; as propostas passam pelo verificador (StepId inventado recusado)", mock.calls() == callsBefore + 1
                && d1.pending().stream().filter(i -> i.code().equals("AI_PROPOSAL")).count() == 1 && d1.statusText().contains("Recusadas pelo verificador: 1"));
        clickPendingButton(dq, "AI_PROPOSAL", null);
        DevelopmentPanel.Model d2 = waitDevIdle("proposta aceita");
        check("D: proposta aceita vira decisão da especificação com ator e base PROPOSAL_ACCEPTED",
                edt(() -> window.studio().development().store().spec(c.id()).decisions().stream().anyMatch(x -> x.basis() == FunctionalSpec.Basis.PROPOSAL_ACCEPTED
                        && x.actor() == FunctionalSpec.Actor.TEST_AGENT && x.field() == FunctionalSpec.Field.EFFECT)));
        // perguntas restantes respondidas pelos botões
        answerOption(dq, "SPEC_POSTCONDITION_MISSING", "NEXT_TARGET_VISIBLE");
        answerOption(dq, "SPEC_CONCLUSION_MISSING", FunctionalSpec.NO_CONCLUSION);
        answerText(dq, "SPEC_START_URL_UNKNOWN", CONSULTA);
        DevelopmentPanel.Model q2 = waitDevIdle("geração depois das respostas");
        captureWindow(devDialog(), "C2-02-gerado-apos-respostas");
        Path gen2 = project2.resolveSibling("processar-registro-python");
        check("C2: respondidas as perguntas, a análise retoma e gera — o envio sai uma vez, confirmado pela etapa seguinte",
                q2.results().contains("✓ CODE_GENERATED") && calcularConfirmedOnce(Files.readString(gen2.resolve("paginas.py"))));
        closeDevelopment();
        save();

        // ================================================================ Caso C3 — alvo dentro de iframe (não suportado pelo runtime 0.2.0)
        navigate(BASE + "studio012/portal.html");
        focusStudio();
        createAutomation("Menu em iframe (laboratório)", List.of("Menu"));
        Automation f = active();
        LogicalSession m0 = edt(() -> f.sessions().get(0));
        selectSession(f, m0);
        startRecordingOn("127.0.0.1", RecordingMode.CAPTURE_AND_INTERACT);
        moveFloatingAway();
        clickInPage(MENU + ".contentDocument.querySelector('a[href$=\"inicio.html\"]')", MENU);
        waitFor("clique no iframe", 30, () -> edt(m0::stepCount) >= 1);
        finishRecording();
        Path project3 = out.toPath().resolve("projeto").resolve("menu-iframe.nekkora");
        saveAs(project3);
        reviewPddWindow(List.of("Menu"), List.of("Abrir o início pelo menu"));
        DevelopmentPanel dr = openDevelopment();
        DevelopmentPanel.Model r1 = waitDevIdle("iframe");
        log("  pendências: " + r1.pending().stream().map(i -> i.kind() + ":" + i.code()).toList());
        // com o PDD ainda pendente, o diagnóstico das capacidades (V2) continua e aponta o alvo em iframe
        boolean frameBlocked = r1.pending().stream().anyMatch(i -> i.code().equals("TARGET_IN_FRAME_NOT_SUPPORTED") && i.kind() == QualityIssue.Kind.HELP_REQUIRED
                && i.assistance().map(h -> h.options().stream().anyMatch(o -> "SEE_EXTENDING".equals(o.studioAction()))).orElse(false));
        selectPending(dr, "TARGET_IN_FRAME_NOT_SUPPORTED");
        captureWindow(devDialog(), "C3-01-iframe-nao-suportado");
        check("C3: alvo em iframe → pedido de ajuda (rever a etapa ou estender o framework pelo guia); nenhum código", frameBlocked
                && !Files.exists(project3.resolveSibling("menu-iframe-python")));
        closeDevelopment();

        // ================================================================ Caso B — regressão tabular (projeto real da E2E-001, réplica local)
        Path studioRepo = Path.of("").toAbsolutePath();
        Path tab = out.toPath().resolve("projeto").resolve("rpa-challenge.nekkora");
        Files.copy(studioRepo.resolve("docs/e2e-001/evidence/projeto/rpa-challenge.nekkora"), tab, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(studioRepo.resolve("docs/e2e-001/evidence/g6-laboratorio/replica.codegen.json"), tab.resolveSibling("rpa-challenge.codegen.json"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        openByDialog(tab);
        Automation t = active();
        reviewPddWindow(List.of("Preparação", "Preenchimento por registro", "Conclusão"), List.of("Baixar a planilha e iniciar o desafio",
                "Preencher o formulário com um registro e enviar", "Ler o resultado exibido"));
        DevelopmentPanel db = openDevelopment();
        DevelopmentPanel.Model b1 = waitDevIdle("tabular");
        for (int round = 0; round < 4; round++) {
            DevelopmentPanel.Model now = edt(db::model);
            QualityIssue q = now.pending().stream().filter(i -> i.kind() == QualityIssue.Kind.HELP_REQUIRED).findFirst().orElse(null);
            if (q == null) break;
            String value = q.code().equals("SPEC_EFFECT_UNKNOWN") ? "NONE" : q.code().equals("SPEC_POSTCONDITION_MISSING") ? "FIRST_FIELD_REPLACED" : null;
            log("  pergunta do caso tabular: " + q.code() + " → " + value);
            if (value == null) break;
            answerOption(db, q.code(), value);
            waitDevIdle("resposta tabular");
        }
        DevelopmentPanel.Model b2 = edt(db::model);
        captureWindow(devDialog(), "B-01-tabular-gerado");
        Path genB = tab.resolveSibling("rpa-challenge-python");
        check("B: projeto da E2E-001 com a configuração DECLARADA → lote tabular pelo gerador STUDIO-011", b2.results().contains("✓ CODE_GENERATED")
                && b2.answers().stream().anyMatch(x -> x.answer().startsWith("Processo em lote")));
        closeDevelopment();
        Path legacy = out.toPath().resolve("B-legado");
        String r = generateByLegacyMenu(tab.resolveSibling("rpa-challenge.codegen.json"), legacy);
        check("B: o gerador avançado (configuração manual, menu Avançado) continua funcionando", r.contains("GERADO em"));
        check("B: mesmos bytes do gerador antigo em todos os arquivos", sameFiles(legacy, genB));
        String replica = runPython(List.of(studioRepo.resolve("docs/e2e-001/tools/g6_replica.py").toString(), "--framework", FRAMEWORK.toString(),
                "--gerado", genB.toString(), "--python", PYTHON.toString(), "--evidencias", out.toPath().resolve("B-replica").toString()), studioRepo, Map.of());
        log("  réplica: " + replica.strip().lines().reduce((x, y) -> y).orElse(""));
        String lastLine = replica.strip().lines().reduce((x, y) -> y).orElse("{}");
        Map<String, Object> rep = lastLine.startsWith("{") ? Json.parseObject(lastLine) : Map.of("exit", 1);
        @SuppressWarnings("unchecked")
        Map<String, Object> or = (Map<String, Object>) rep.get("oraculo");
        check("B: código tabular gerado roda na RÉPLICA local e o oráculo DELA confere (1 início, 10 submissões em ordem, valores corretos)",
                Integer.valueOf(0).equals(((Number) rep.get("exit")).intValue()) && or != null && ((Number) or.get("inicios")).intValue() == 1
                        && ((Number) or.get("submissoes")).intValue() == 10 && Boolean.TRUE.equals(or.get("rodadas_em_ordem")) && Boolean.TRUE.equals(or.get("todas_corretas")));
        check("privacidade: log da homologação sem usuário, senha ou código", SENTINELS.stream().noneMatch(String.join("\n", log)::contains)
                && !String.join("\n", log).contains(CODE_VALUE));
    }

    // ============================================================================== apoio STUDIO-014

    private static final String BASE = "http://127.0.0.1:18765/lab/";
    private static final String PORTAL = BASE + "studio014/portal.html";
    private static final String CONSULTA = BASE + "studio014/consulta.html";
    private static final String USER = "document.getElementById('usuario')";
    private static final String PASS = "document.getElementById('senha')";
    private static final String ENTER = "document.getElementById('entrar')";
    private static final String CODE = "document.getElementById('registro-codigo')";
    private static final String QUERY = "document.getElementById('consultar')";
    private static final String RESULT = "document.getElementById('resultado')";
    private static final String EXIT = "document.getElementById('sair')";
    private static final String PROCESS = "document.getElementById('processar')";
    private static final String MENU = "document.querySelector('iframe[name=menu]')";
    /** Valores FICTÍCIOS de teste (nunca podem aparecer em modelo, código, eventos, log ou captura). */
    private static final String USER_VALUE = "agente.teste.014";
    private static final String PASS_VALUE = "senha-ficticia-014";
    private static final String CODE_VALUE = "REG-0003";
    private static final List<String> SENTINELS = new ArrayList<>();
    private static final Path FRAMEWORK = Path.of("..", "Nekkora-RPA-Framework").toAbsolutePath().normalize();
    private static final Path PYTHON = Path.of(System.getenv().getOrDefault("NEKKORA_PYTHON", FRAMEWORK.resolve(".venv/Scripts/python.exe").toString()));
    private final MockAi mock = new MockAi();

    /**
     * Provedor de IA de TESTE — rotulado como mock em tudo o que produz. Propõe o efeito do clique sem rótulo e inventa um
     * StepId (que o verificador do Studio TEM de recusar). Não é a Anthropic, nem o Claude Code.
     */
    static final class MockAi implements com.nekkora.studio.ai.provider.AIProvider {
        private final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();

        int calls() {
            return calls.get();
        }

        public String id() {
            return "mock:homologacao (NÃO é o provedor real)";
        }

        public boolean available() {
            return true;
        }

        public Response call(Request request, java.util.function.BooleanSupplier cancelled) {
            calls.incrementAndGet();
            Map<String, Object> payload = Json.parseObject(request.payload());
            String step = null;
            for (Object q : Json.array(payload, "openQuestions")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) q;
                if ("SPEC_EFFECT_UNKNOWN".equals(m.get("code"))) step = (String) m.get("stepId");
            }
            String input = "{\"proposals\": [" + (step == null ? "" : "{\"kind\": \"EFFECT\", \"stepId\": \"" + step
                    + "\", \"value\": \"EXTERNAL\", \"rationale\": \"Botão de ação sem rótulo de consulta: tratar como envio.\"},")
                    + "{\"kind\": \"EFFECT\", \"stepId\": \"00000000-0000-4000-8000-000000000000\", \"value\": \"NONE\"}]}";
            return new Response(input, 0, 0, "mock");
        }
    }

    /** O clique em "Calcular" (efeito externo) sai sem idempotent=True e com a confirmação pela etapa seguinte (Sair). */
    private static boolean calcularConfirmedOnce(String pages) {
        int i = pages.indexOf("self.k(\"calcular\")");
        if (i < 0) return false;
        int end = pages.indexOf("# etapa", i);
        String call = pages.substring(i, end < 0 ? pages.length() : end);
        return call.contains("postcondition=lambda: a.exists(\"processo.") && call.contains(".sair\")") && !call.contains("idempotent=True");
    }

    /**
     * 150% (JVM com uiScale 1.5): Studio e Chrome não cabem lado a lado nesta tela — como nas homologações anteriores, abre
     * o projeto SALVO pela rodada completa (com os dados do Studio dela) e confere a janela Desenvolver e a aba Validação
     * nos quatro temas.
     */
    private void scaleRun() throws Exception {
        Path project = Path.of(System.getenv("NEKKORA_S014_PROJECT"));
        edt(() -> {
            window.frame().setSize(1180, 660);
            return null;
        });
        openByDialog(project);
        DevelopmentPanel dp = openDevelopment();
        DevelopmentPanel.Model m = waitDevIdle("análise em 150%");
        log("  150%: " + m.statusTitle() + " · " + m.results().stream().filter(r -> r.startsWith("✓ CODE")).toList());
        check("150%: Desenvolver automação conclui na revisão salva (CODE_VALIDATED)", m.results().contains("✓ CODE_VALIDATED"));
        for (int i = 0; i < 4; i++) {
            String theme = edt(() -> com.nekkora.ui.theme.ThemeManager.current().id());
            for (int tab = 0; tab < 3; tab++) {
                selectTabOf(dp, tab);
                captureWindow(devDialog(), "E-150-" + theme + "-aba-" + tab);
            }
            check("150% · tema " + theme + ": Desenvolver de novo, Abrir código, Validar novamente e Fechar visíveis dentro da janela", edt(() -> {
                Rectangle w = devDialog().getBounds();
                for (String t : List.of("Desenvolver de novo", "Abrir código", "Validar novamente", "Fechar")) {
                    AbstractButton b = find(devDialog(), AbstractButton.class, x -> t.equals(x.getText()) && x.isShowing());
                    if (b == null || !w.contains(new Rectangle(b.getLocationOnScreen(), b.getSize()))) return false;
                }
                return true;
            }));
            edt(() -> {
                com.nekkora.studio.app.ui.StudioActions.cycleTheme();
                return null;
            });
            sleep(800);
        }
        closeDevelopment();
        focusStudioWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_V);
        String layers = (String) waitValue("camadas em 150%", 120, () -> {
            String t = edt(() -> window.studio().view().validationPanel().layersText().getAccessibleContext().getAccessibleDescription());
            return t != null && t.contains("V5 Testes") && !t.contains("verificando") && !t.contains("procurando") && !t.contains("aguardando") ? t : null;
        });
        captureStudio("E-150-validar-camadas");
        check("150%: aba Validação com as camadas (V4 e V5 verificadas, V6 não executada)", layers.contains("V4 Código Python: 5 de 5")
                && layers.contains("V6 Execução real: não executada pelo Studio"));
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
            click(edt(dp::correctButton));
            waitCard("corrigindo", x -> x.mode() == GuidedDebugPanel.Mode.CORRECTING);
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

    private String expectedFor(AutomationStep s) {
        String name = s.element().map(ElementReference::accessibleName).orElse("");
        return switch (name) {
            case "Usuário" -> USER;
            case "Senha" -> PASS;
            case "Entrar" -> ENTER;
            case "Código do registro" -> CODE;
            case "Consultar" -> QUERY;
            case "Resultado da consulta" -> RESULT;
            case "Sair" -> EXIT;
            default -> s.actionType() == ActionType.CLICK ? PROCESS : null;
        };
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


    // ============================================================================== apoio reaproveitado

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
        hosts.add("https://rpachallenge.com/*"); // concessão da usuária (missão E2E-001) materializada na construção de teste
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
                "--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE rpachallenge.com, EXCLUDE localhost, EXCLUDE 127.0.0.1", "about:blank")
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
        File f = new File(out, "S014-" + name + ".png");
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
        try (PrintWriter w = new PrintWriter(new File(out, "S014-homologacao-log.txt"), StandardCharsets.UTF_8)) {
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
