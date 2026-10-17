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
import com.nekkora.studio.app.debug.GuidedDebugPresenter;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.target.DecisionActor;
import com.nekkora.studio.domain.target.TargetDecision;
import com.nekkora.studio.guided.GuidedDebugRecord;
import com.nekkora.studio.guided.GuidedPlan;
import com.nekkora.studio.persistence.ProjectFormat;
import com.nekkora.studio.recorder.GuidedDebugClient;
import com.nekkora.studio.ui.debug.GuidedDebugPanel;
import com.nekkora.studio.ui.debug.GuidedDebugWindow;
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
 * Homologação REAL da STUDIO-009 no Windows: Studio de verdade, Chrome for Testing com a extensão DESTA branch, Native
 * Host instalado pelo script oficial, laboratório "Consulta de registro" (dados fictícios), mouse e teclado do sistema
 * (Robot). O operador é o Claude como AGENTE DE TESTE: o Studio roda em modo de laboratório explícito
 * ({@code -Dnekkora.studio.decisionActor=TEST_AGENT}) e toda decisão fica registrada como {@code TEST_AGENT} — nunca como
 * confirmação humana. As decisões do roteiro se baseiam no que a JANELA DO STUDIO mostra (status, verificações) e na
 * intenção da etapa; o gabarito do laboratório (oráculo no servidor) só é lido para CONFERIR depois, nunca para propor.
 *
 * <p>Uso: {@code java -Dnekkora.studio.decisionActor=TEST_AGENT ... Studio009Homologation <saída> <repositório-da-extensão>}
 */
public final class Studio009Homologation {

    private static final int LAB_PORT = 18765;
    private static final String BASE = "http://127.0.0.1:" + LAB_PORT + "/lab/";
    private static final String PAGE = BASE + "studio009/consulta.html";
    private static final String S3 = BASE;
    /** Digitado na GRAVAÇÃO (fictício): nunca pode aparecer em etapas, plano, registro, projeto ou logs. */
    private static final String TYPED = "REG-0001";
    private static final int CHROME_X = 880;

    private final File out;
    private final Path ext;
    private final boolean compat = false;
    /** Execução em 150% (JVM com -Dsun.java2d.uiScale=1.5): só o mapeamento e as capturas por tema. */
    private boolean scale;
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

    private Studio009Homologation(File out, Path ext) throws AWTException {
        this.out = out;
        this.ext = ext;
        this.robot = new Robot();
        robot.setAutoDelay(25);
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Studio009Homologation h = new Studio009Homologation(out, Path.of(args[1]).toAbsolutePath().normalize());
        h.scale = args.length > 2 && "escala".equals(args[2]);
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
        log("Operador: Claude como AGENTE DE TESTE (decisões TEST_AGENT; nenhuma confirmação humana) · ator do Studio: "
                + System.getProperty("nekkora.studio.decisionActor"));
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
        launchChrome(PAGE + "?v=base");
        connect();
        check("Ator das decisões no Studio = agente de teste", edt(() -> window.studio().guidedDebug().actor()) == DecisionActor.TEST_AGENT);

        // ================================================================ H1 — Mapeamento pelo Recorder (processo real)
        focusStudio();
        createAutomation();
        Automation a = edt(() -> window.studio().state().activeAutomation().orElseThrow());
        LogicalSession consulta = a.sessions().get(0);
        selectSession(a, consulta);
        startRecordingOn("127.0.0.1", RecordingMode.CAPTURE_AND_INTERACT);
        moveFloatingAway();
        clickInPage("document.getElementById('registro-codigo')");
        typeText(TYPED);
        clickInPage("document.querySelector('[data-testid=consultar]')");
        waitFor("2 etapas gravadas", 30, () -> edt(consulta::stepCount) >= 2);
        sleep(1500);
        clickButtonWithText(floating(), "Finalizar");
        waitFor("gravação finalizada", 30, () -> !edt(() -> window.studio().recorder().snapshot().state().active()));
        sleep(800);
        List<String> kinds = edt(() -> consulta.steps().stream().map(st -> st.actionType().name()).toList());
        log("  etapas gravadas: " + edt(() -> names(consulta)) + " " + kinds);
        check("H1: gravação = preencher (código) e clicar (Consultar)", kinds.equals(List.of("FILL", "CLICK")));
        check("H1: cada etapa gravada traz identidade lógica registrada (targetId) — o vínculo para o debug", edt(() -> consulta.steps().stream()
                .allMatch(st -> st.element().flatMap(e -> e.correlation().get(CorrelationKeys.Key.TARGET_ID)).isPresent())));
        check("H1: o valor digitado não está no modelo", !edt(() -> domainText(a)).contains(TYPED));
        // a etapa de preencher passa a usar a variável de entrada (declarada no Studio)
        edt(() -> {
            var v = window.studio().service().declareVariable(a.id(), com.nekkora.studio.domain.variable.VariableDraft.of("registro.codigo",
                    com.nekkora.studio.domain.variable.VariableType.STRING).withSemantic(com.nekkora.studio.domain.variable.SemanticType.IDENTIFIER,
                    com.nekkora.studio.domain.variable.SemanticStatus.USER_CONFIRMED).withSensitivity(com.nekkora.studio.domain.variable.Sensitivity.INTERNAL)
                    .withOrigin(com.nekkora.studio.domain.variable.VariableOrigin.input()));
            window.studio().service().setStepParameters(a.id(), consulta.steps().get(0).id(), com.nekkora.studio.domain.action.ActionParameters.withValue(
                    new com.nekkora.studio.domain.action.ValueSource.VariableReference(v.name())));
            return null;
        });
        captureStudio("H1-mapeamento");

        // ================================================================ H2 — Plano intermediário
        String planJson = edt(() -> GuidedPlan.of(a, new AutomationValidator(RuleSet.V1_1).validate(a)).toJson());
        Files.writeString(out.toPath().resolve("S009-H2-plano.json"), planJson, StandardCharsets.UTF_8);
        check("H2: plano com 2 etapas inspecionáveis pela identidade registrada", planJson.split("\"applicability\": \"INSPECT_TARGET\"", -1).length - 1 == 2
                && planJson.contains("\"identitySource\": \"TARGET_ID\""));
        check("H2: plano sem o valor digitado e sem chaves de correlação", !planJson.contains(TYPED) && !planJson.contains("tgt-"));

        // ================================================================ H3 — 1ª passagem: proposta, sondas, Continuar
        oracle("/api/studio009/reset");
        navigate(PAGE + "?v=base"); // estado limpo da página (contadores de eventos zerados)
        GuidedDebugPanel dp = openDebugAndStart();
        waitCard("etapa 1 verificada", c -> c.mode() == GuidedDebugPanel.Mode.STEP && c.badge() == GuidedDebugPanel.Badge.PROPOSED);
        GuidedDebugPanel.Card c1 = edt(dp::card);
        log("  etapa 1: " + c1.title() + " · " + c1.recognized() + " · por quê: " + c1.why());
        check("H3: etapa 1 proposta e verificada tecnicamente (RESOLVED), Continuar habilitado", c1.canContinue());
        check("H3: destaque rosa visível no Chrome sobre o campo", debugBoxVisible());
        check("H3: destacar não gerou clique/foco/entrada/envio na página", interactions() == 0);
        check("H3: oráculo: nenhuma consulta feita pelo debug", oracle("/api/studio009/estado").contains("\"total\":0"));
        check("H3: a janela do debug não cobre o elemento destacado", !debugWindowCoversTarget());
        captureStudio("H3-01-proposta-campo");
        // sonda de laboratório (operação explícita, dado fictício por CHAVE)
        edt(() -> {
            dp.probeKeysInput().setSelectedItem("lab:registro-ativo");
            return null;
        });
        click(edt(dp::probeButton));
        waitCard("sonda preencher", c -> c.message().startsWith("Operação efetuada no laboratório"));
        check("H3/G6: sonda preencheu o campo com o dado do catálogo (lido na página do laboratório)", "REG-0001".equals(page.eval(
                "document.getElementById('registro-codigo').value")));
        click(edt(dp::continueButton));
        waitCard("etapa 2 verificada", c -> c.title().contains("Consultar") && c.badge() == GuidedDebugPanel.Badge.PROPOSED);
        captureStudio("H3-02-proposta-botao");
        check("H3: até aqui, nenhuma consulta no oráculo (sonda de preencher não consulta)", oracle("/api/studio009/estado").contains("\"total\":0"));
        click(edt(dp::probeButton));
        waitCard("sonda clicar", c -> c.message().startsWith("Operação efetuada no laboratório"));
        waitFor("oráculo registra 1 consulta", 10, () -> oracle("/api/studio009/estado").contains("\"total\":1"));
        String state = oracle("/api/studio009/estado");
        check("H3/G6: oráculo independente — exatamente 1 consulta, com o código do catálogo", state.contains("\"total\":1") && state.contains("REG-0001"));
        waitFor("resultado na página", 10, () -> "ATIVO".equals(page.eval("document.getElementById('resultado').getAttribute('data-situacao')")));
        captureStudio("H3-03-sonda-resultado");
        click(edt(dp::continueButton));
        waitCard("fim da 1ª passagem", c -> c.mode() == GuidedDebugPanel.Mode.FINISHED);
        check("H3: 2 decisões CONFIRMED registradas como TEST_AGENT", edt(() -> consulta.steps().stream().allMatch(st -> st.targetDecision()
                .map(d -> d.confirmed() && d.actor() == DecisionActor.TEST_AGENT).orElse(false))));
        captureStudio("H3-04-fim");

        // ================================================================ H4 — 2ª passagem: reuso sem nova pergunta
        click(edt(dp::finishButton));
        int before = edt(() -> window.studio().guidedDebug().journal().size());
        oracle("/api/studio009/reset");
        GuidedDebugPanel dp2 = openDebugAndStart();
        waitCard("etapa reconfirmada", c -> c.badge() == GuidedDebugPanel.Badge.REUSED);
        captureStudio("H4-01-reconfirmado");
        waitCard("fim da 2ª passagem sem perguntas", c -> c.mode() == GuidedDebugPanel.Mode.FINISHED);
        long reused = edt(() -> window.studio().guidedDebug().journal().stream().skip(before).filter(e -> "REUSE".equals(e.outcome())).count());
        check("H4: segunda passagem reconfirmou as 2 etapas sem perguntar (REUSE ×2) e terminou sozinha", reused == 2);
        check("H4: nada consultado na 2ª passagem", oracle("/api/studio009/estado").contains("\"total\":0"));

        click(edt(dp2::finishButton));
        waitDebugClosed();
        if (scale) {
            themes("150");
            return;
        }

        String only = System.getProperty("nekkora.s009.only", "");
        if (!only.isEmpty()) { // execução focada (diagnóstico): só os blocos pedidos, sem salvar
            for (String b : only.split(",")) {
                switch (b) {
                    case "h6" -> h6Variants();
                    case "h13b" -> h13Picks();
                    case "h10" -> h10Navigation();
                    case "h11" -> h11TabClosed();
                    case "h16" -> h16Disconnect();
                    case "temas" -> themes("100");
                    default -> throw new IllegalArgumentException(b);
                }
            }
            return;
        }
        Path file = h5SaveReopen();
        h6Variants();
        h13Picks();
        h10Navigation();
        h11TabClosed();
        h16Disconnect();
        h7Correction(file);
        themes("100");
        h15Privacy(file);
    }

    // ============================================================================== H5 — salvar 1.2.0 e reabrir

    private Path h5SaveReopen() throws Exception {
        Path file = out.toPath().resolve("consulta-009.nekkora");
        Files.deleteIfExists(file);
        Automation a = active();
        focusStudioWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_S);
        waitChooser("Salvar projeto como");
        typePath(file);
        waitFor("projeto salvo", 15, () -> Files.exists(file) && !edt(() -> window.studio().state().project(a.id()).orElseThrow().dirty()));
        String fmt = formatOf(file);
        String all = zipText(file);
        check("H5: com decisões o projeto é gravado em 1.2.0 (" + fmt + ")", ProjectFormat.FORMAT_VERSION_1_2.equals(fmt));
        check("H5: as decisões estão no arquivo com o ator TEST_AGENT, sem confirmação humana", all.contains("\"targetDecision\"")
                && all.contains("TEST_AGENT") && !all.contains("HUMAN_CONFIRMED") && !all.contains("USER_CONFIRMED\",\"actor"));
        check("H5: o arquivo não guarda o valor digitado na gravação nem o dado da sonda", !all.contains(TYPED));
        menu("Arquivo", "Fechar projeto");
        sleep(800);
        openByDialog(file);
        Automation r = active();
        check("H5: reaberto, as 2 decisões TEST_AGENT continuam nas etapas", edt(() -> r.sessions().get(0).steps().stream().allMatch(st -> st.targetDecision()
                .map(d -> d.confirmed() && d.actor() == DecisionActor.TEST_AGENT).orElse(false))));
        navigate(PAGE + "?v=base");
        oracle("/api/studio009/reset");
        int before = edt(() -> window.studio().guidedDebug().journal().size());
        GuidedDebugPanel dp = openDebugAndStart();
        waitCard("fim da passagem após reabrir", c -> c.mode() == GuidedDebugPanel.Mode.FINISHED);
        long reused = edt(() -> window.studio().guidedDebug().journal().stream().skip(before).filter(e -> "REUSE".equals(e.outcome())).count());
        check("H5: depois de reabrir o arquivo, as 2 etapas são reconfirmadas sem pergunta (REUSE ×2)", reused == 2);
        click(edt(dp::finishButton));
        waitDebugClosed();
        return file;
    }

    // ============================================================================== H6/H13 — variantes da página

    private void h6Variants() throws Exception {
        for (String v : List.of("id-mudado", "reordenado", "errado", "oculto", "desabilitado", "rerender", "tardio", "removido", "iframe", "iframe-aninhado",
                "iframe-ambiguo", "shadow-aberto", "shadow-fechado", "rotulo-suprimido", "duplicado")) {
            variantPass(v);
        }
        check("H6/H13: nenhuma variante gerou consulta no oráculo", oracle("/api/studio009/estado").contains("\"total\":0"));
    }

    /**
     * Uma passagem sem decisões sobre a variante: para cada etapa, o que o debug comprova, onde destaca e se reconfirma.
     * Invariante de segurança: "comprovado" (RESOLVED) só sobre o elemento CERTO; ambíguo nunca é comprovado; estado
     * inadequado (oculto/desabilitado) nunca é reconfirmado sem pergunta.
     */
    private void variantPass(String v) throws Exception {
        navigate(PAGE + "?v=" + v);
        int touched = interactions();
        GuidedDebugPanel dp = openDebugAndStart();
        for (int i = 0; i < 2; i++) {
            GuidedDebugPanel.Card c = settled(v, i);
            GuidedDebugClient.Inspection r = lastInspection();
            if (("removido".equals(v) || "tardio".equals(v)) && (r == null || r.status() != GuidedDebugClient.Status.RESOLVED) && c.badge() != GuidedDebugPanel.Badge.REUSED) {
                log("  " + v + " etapa " + (i + 1) + ": " + describe(c, r) + " — aguardando o elemento voltar e verificando de novo");
                sleep(4000);
                click(edt(i == 0 ? dp::nextButton : dp::previousButton));
                settled(v, i == 0 ? 1 : 0);
                if (edt(dp::card).badge() != GuidedDebugPanel.Badge.REUSED) click(edt(i == 0 ? dp::previousButton : dp::nextButton));
                c = settled(v, i);
                r = lastInspection();
            }
            boolean resolved = r != null && r.status() == GuidedDebugClient.Status.RESOLVED;
            String expr = expected(v, i);
            log("  " + v + " etapa " + (i + 1) + ": " + describe(c, r));
            if (v.equals("errado") || v.equals("id-mudado") || v.equals("duplicado") || v.equals("iframe-ambiguo") || i == 1 && (v.equals("oculto") || v.equals("desabilitado"))) {
                captureStudio("H13-" + v + "-etapa" + (i + 1));
            }
            if (expr == null) {
                check("H13 " + v + " etapa " + (i + 1) + ": indistinguível NÃO é comprovado nem reconfirmado", !resolved && c.badge() != GuidedDebugPanel.Badge.REUSED
                        && !c.canContinue());
                check("H13 " + v + " etapa " + (i + 1) + ": sem destaque sólido e sem sonda", !debugBoxVisible() && !edt(() -> dp.probeButton().isShowing()));
            } else if (resolved) {
                check("H13 " + v + " etapa " + (i + 1) + ": comprovado SOBRE o elemento certo", boxOn(r.box(), expr));
            }
            if (i == 1 && (v.equals("oculto") || v.equals("desabilitado"))) {
                check("H13 " + v + ": estado inadequado nunca é reconfirmado sem pergunta", c.badge() != GuidedDebugPanel.Badge.REUSED);
            }
            if (c.badge() == GuidedDebugPanel.Badge.REUSED) {
                final int n = i;
                waitCard(v + " avanço automático", x -> x.mode() == GuidedDebugPanel.Mode.FINISHED || !x.progress().startsWith((n + 1) + " de"));
            } else if (i == 0) {
                click(edt(dp::nextButton));
            }
        }
        check("H13 " + v + ": o debug não interagiu com a página (nenhum clique, foco, entrada ou envio)", interactions() == touched);
        click(edt(dp::finishButton));
        waitDebugClosed();
    }

    private GuidedDebugPanel.Card settled(String v, int i) throws Exception {
        return waitCard(v + " etapa " + (i + 1), x -> x.mode() == GuidedDebugPanel.Mode.STEP && x.badge() != GuidedDebugPanel.Badge.CHECKING
                && x.progress().startsWith((i + 1) + " de"));
    }

    private static String describe(GuidedDebugPanel.Card c, GuidedDebugClient.Inspection r) {
        return c.badge().label() + (r == null ? " · sem inspeção" : " · " + r.status() + " · fonte " + r.identitySource() + " · " + r.reasons()
                + String.format(java.util.Locale.ROOT, " · pontuação %.2f", r.score())) + (c.canContinue() ? " · Continuar habilitado" : "");
    }

    /** Elemento CERTO de cada etapa, por variante (null = indistinguível: nada pode ser comprovado). */
    private static String expected(String v, int step) {
        String doc = switch (v) {
            case "iframe" -> "document.querySelector('iframe').contentDocument";
            case "iframe-aninhado" -> "document.querySelector('iframe').contentDocument.querySelector('iframe').contentDocument";
            case "shadow-aberto" -> "document.querySelector('nk-consulta').shadowRoot";
            case "iframe-ambiguo" -> null;
            case "duplicado" -> step == 0 ? "DUP" : null; // o campo do 1º formulário mantém o id gravado; os botões não têm nada que os distinga
            case "shadow-fechado" -> "SHADOW";
            default -> "document";
        };
        if (doc == null) return null;
        if (doc.equals("DUP")) return "document.querySelectorAll('form')[0].querySelector('input')";
        if (doc.equals("SHADOW")) return "document.querySelector('nk-consulta')"; // raiz fechada: só o hospedeiro é observável
        return doc + ".querySelector(" + (step == 0 ? "'form input'" : "'form button[type=submit]'") + ")";
    }

    /** O destaque (retângulo na tela informado pela extensão) está sobre o elemento esperado. */
    private boolean boxOn(GuidedDebugClient.Box box, String expr) throws Exception {
        @SuppressWarnings("unchecked")
        List<Object> e = (List<Object>) page.eval("((el) => { if (!el) return null; const r = el.getBoundingClientRect(); let x = r.left, y = r.top;"
                + " let w = el.ownerDocument.defaultView; while (w.frameElement) { const f = w.frameElement; const fr = f.getBoundingClientRect();"
                + " x += fr.left + f.clientLeft; y += fr.top + f.clientTop; w = w.parent; }"
                + " const bx = (window.outerWidth - window.innerWidth) / 2; const by = window.outerHeight - window.innerHeight - bx;"
                + " return [window.screenX + bx + x, window.screenY + by + y, r.width, r.height]; })(" + expr + ")");
        if (e == null) {
            log("    elemento esperado não existe na página");
            return false;
        }
        Rectangle want = new Rectangle((int) Math.round(((Number) e.get(0)).doubleValue()) - 4, (int) Math.round(((Number) e.get(1)).doubleValue()) - 4,
                (int) Math.round(((Number) e.get(2)).doubleValue()) + 8, (int) Math.round(((Number) e.get(3)).doubleValue()) + 8);
        if (want.width <= 8 && want.height <= 8) {
            log("    elemento sem área na página (oculto): não há onde destacar; vale a recusa de reconfirmar");
            return true;
        }
        if (box == null) {
            log("    comprovado sem retângulo de destaque (esperado em " + want + ")");
            return false;
        }
        java.awt.Point center = new java.awt.Point(box.screenX() + box.width() / 2, box.screenY() + box.height() / 2);
        log("    destaque na tela " + new Rectangle(box.screenX(), box.screenY(), box.width(), box.height()) + " · esperado " + want);
        return want.contains(center);
    }

    private GuidedDebugClient.Inspection lastInspection() throws Exception {
        return edt(() -> {
            java.lang.reflect.Field f = GuidedDebugPresenter.class.getDeclaredField("last");
            f.setAccessible(true);
            return (GuidedDebugClient.Inspection) f.get(window.studio().guidedDebug());
        });
    }

    // ============================================================================== H13b/H8 — escolher no navegador em quadro e shadow

    private void h13Picks() throws Exception {
        // quadro: H8 (escolha incompatível recusada) + escolha compatível dentro do iframe
        navigate(PAGE + "?v=iframe");
        oracle("/api/studio009/reset");
        String frame = "document.querySelector('iframe')";
        String inner = frame + ".contentDocument";
        int touched = interactions() + interactions(frame + ".contentWindow");
        GuidedDebugPanel dp = openDebugAndStart();
        GuidedDebugPanel.Card c = settled("iframe", 0);
        check("H13b iframe: etapa sem identidade comprovada oferece Corrigir", c.canCorrect() && !c.canContinue());
        click(edt(dp::correctButton));
        waitCard("corrigindo", x -> x.mode() == GuidedDebugPanel.Mode.CORRECTING);
        click(edt(dp::pickButton));
        waitCard("seleção no navegador", x -> x.mode() == GuidedDebugPanel.Mode.PICKING);
        clickInPage(inner + ".querySelector('form button[type=submit]')", frame);
        GuidedDebugPanel.Card bad = waitCard("comparação incompatível", x -> x.mode() == GuidedDebugPanel.Mode.COMPARING);
        captureStudio("H8-escolha-incompativel");
        check("H8: botão escolhido (dentro do quadro) para \"preencher\": incompatível e Aplicar desabilitado", !bad.comparison().compatible()
                && !edt(() -> dp.applyButton().isEnabled()));
        click(edt(dp::discardButton));
        waitCard("descartado", x -> x.mode() == GuidedDebugPanel.Mode.CORRECTING);
        check("H8: descartar não alterou o alvo", edt(() -> step(0).replacementTarget().isEmpty()));
        click(edt(dp::pickButton));
        waitCard("seleção no navegador", x -> x.mode() == GuidedDebugPanel.Mode.PICKING);
        clickInPage(inner + ".querySelector('form input')", frame);
        GuidedDebugPanel.Card ok = waitCard("comparação", x -> x.mode() == GuidedDebugPanel.Mode.COMPARING);
        log("  iframe: depois = " + ok.comparison().after());
        captureStudio("H13b-iframe-comparar");
        check("H13b iframe: campo escolhido DENTRO do quadro é compatível (Aplicar habilitado)", ok.comparison().compatible() && edt(() -> dp.applyButton().isEnabled()));
        click(edt(dp::discardButton));
        waitCard("descartado", x -> x.mode() == GuidedDebugPanel.Mode.CORRECTING);
        check("H13b iframe: escolher não clicou, não focou nem enviou nada (página e quadro)", interactions() + interactions(frame + ".contentWindow") == touched);
        click(edt(dp::finishButton));
        waitDebugClosed();

        // shadow aberto: escolha compatível dentro da raiz
        navigate(PAGE + "?v=shadow-aberto");
        touched = interactions();
        final GuidedDebugPanel dps = openDebugAndStart();
        settled("shadow-aberto", 0);
        click(edt(dps::correctButton));
        waitCard("corrigindo", x -> x.mode() == GuidedDebugPanel.Mode.CORRECTING);
        click(edt(dps::pickButton));
        waitCard("seleção no navegador", x -> x.mode() == GuidedDebugPanel.Mode.PICKING);
        clickInPage("document.querySelector('nk-consulta').shadowRoot.querySelector('form input')");
        ok = waitCard("comparação", x -> x.mode() == GuidedDebugPanel.Mode.COMPARING);
        log("  shadow aberto: depois = " + ok.comparison().after());
        captureStudio("H13b-shadow-comparar");
        check("H13b shadow aberto: campo escolhido dentro da raiz é compatível", ok.comparison().compatible());
        click(edt(dps::discardButton));
        waitCard("descartado", x -> x.mode() == GuidedDebugPanel.Mode.CORRECTING);
        check("H13b shadow aberto: escolher não interagiu com a página", interactions() == touched);
        check("H13b: oráculo — nenhuma consulta", oracle("/api/studio009/estado").contains("\"total\":0"));
        click(edt(dps::finishButton));
        waitDebugClosed();
    }

    private int interactions(String win) throws Exception {
        Object v = page.eval("(() => { const c = (" + win + ").__nk009 || {}; return (c.pointerdown||0)+(c.mousedown||0)+(c.click||0)+(c.focusin||0)+(c.input||0)"
                + "+(c.change||0)+(c.submit||0)+(c.keydown||0); })()");
        return ((Number) v).intValue();
    }

    // ============================================================================== H10 — navegação durante o debug

    private void h10Navigation() throws Exception {
        navigate(PAGE + "?v=id-mudado");
        GuidedDebugPanel dp = openDebugAndStart();
        GuidedDebugPanel.Card c = settled("id-mudado", 0);
        String epoch = detail(c, "Época da página");
        GuidedDebugWindow w = debugWindow();
        navigateByUser(PAGE + "?v=id-mudado&nova=1"); // o usuário navega no Chrome (barra de endereço)
        GuidedDebugPanel.Card after = waitCard("reinspeção após navegar", x -> x.mode() == GuidedDebugPanel.Mode.STEP && x.badge() != GuidedDebugPanel.Badge.CHECKING
                && !detail(x, "Época da página").equals(epoch));
        log("  navegação: época " + epoch + " → " + detail(after, "Época da página") + " · " + after.badge().label());
        check("H10: navegar na aba fez o debug verificar de novo na página nova (época mudou), sem reaproveitar a página anterior", true);
        check("H10: a janela do debug não roubou o foco do Chrome ao se atualizar", !edt(w::isFocused) && edt(() -> !window.frame().isFocused()));
        captureStudio("H10-reinspecao-apos-navegar");
        click(edt(dp::finishButton));
        waitDebugClosed();
    }

    private static String detail(GuidedDebugPanel.Card c, String key) {
        for (GuidedDebugPanel.Row r : c.details()) if (r.key().equals(key)) return r.value();
        return "";
    }

    // ============================================================================== H11 — aba fechada

    private void h11TabClosed() throws Exception {
        Cdp.Target other = cdp.open(BASE + "studio009/consulta.html?v=base&aba=outra");
        sleep(1500);
        cdp.activate(labTarget);
        GuidedDebugPanel dp = openDebugAndStart(t -> true, 1);
        waitCard("etapa verificada na outra aba", x -> x.mode() == GuidedDebugPanel.Mode.STEP && x.badge() != GuidedDebugPanel.Badge.CHECKING);
        cdp.close(other);
        GuidedDebugPanel.Card c = waitCard("sessão encerrada pela aba fechada", x -> x.mode() == GuidedDebugPanel.Mode.FINISHED);
        log("  aba fechada: " + c.title() + " · " + c.message());
        check("H11: fechar a aba encerra a sessão de debug e a janela diz por quê", c.badge() == GuidedDebugPanel.Badge.NOT_VERIFIABLE && !c.canContinue());
        captureStudio("H11-aba-fechada");
        click(edt(dp::finishButton));
        waitDebugClosed();
    }

    // ============================================================================== H16 — desconexão do navegador

    private void h16Disconnect() throws Exception {
        navigate(PAGE + "?v=id-mudado");
        GuidedDebugPanel dp = openDebugAndStart();
        settled("id-mudado", 0);
        panel.eval("document.getElementById('studio-enabled').click()"); // desliga "Conectar ao Nekkora Studio"
        waitFor("Studio desconectado", 20, () -> !statusText().startsWith("Navegador: conectado"));
        sleep(2500);
        GuidedDebugPanel.Card c = edt(dp::card);
        log("  desconectado: " + c.mode() + " · " + c.badge().label() + " · " + c.message());
        check("H16: sem conexão, nada pode ser confirmado com base numa verificação antiga", !c.canContinue());
        captureStudio("H16-desconectado");
        click(edt(dp::finishButton));
        waitDebugClosed();
        panel.eval("document.getElementById('studio-enabled').click()");
        waitFor("Studio reconectado", 30, () -> statusText().startsWith("Navegador: conectado"));
        navigate(PAGE + "?v=base");
        GuidedDebugPanel dp2 = openDebugAndStart();
        GuidedDebugPanel.Card again = settled("base após reconectar", 0);
        GuidedDebugClient.Inspection r = lastInspection();
        log("  após reconectar: " + describe(again, r));
        check("H16: depois de reconectar, a sessão órfã é encerrada e uma nova sessão inspeciona normalmente (identidade comprovada)",
                r != null && r.status() == GuidedDebugClient.Status.RESOLVED);
        click(edt(dp2::finishButton));
        waitDebugClosed();
    }

    // ============================================================================== H7/H8/H9 — correção no navegador

    private void h7Correction(Path file) throws Exception {
        navigate(PAGE + "?v=duplicado");
        oracle("/api/studio009/reset");
        int touched = interactions();
        GuidedDebugPanel dp = openDebugAndStart();
        List<String> forms = List.of("document.querySelectorAll('form')[0].querySelector('input')",
                "document.querySelectorAll('form')[0].querySelector('button[type=submit]')");
        // etapa 1: o campo do 1º formulário mantém o id gravado — identidade comprovada; o agente confere ONDE está o destaque
        GuidedDebugPanel.Card c1 = settled("duplicado", 0);
        GuidedDebugClient.Inspection r1 = lastInspection();
        log("  duplicado etapa 1: " + describe(c1, r1));
        boolean onFirst = r1 != null && r1.status() == GuidedDebugClient.Status.RESOLVED && boxOn(r1.box(), forms.get(0));
        check("H7 etapa 1: identidade comprovada sobre o campo do 1º formulário (id gravado) e Continuar disponível", onFirst && c1.canContinue());
        if (c1.badge() != GuidedDebugPanel.Badge.REUSED && c1.canContinue()) click(edt(dp::continueButton));
        check("H7 etapa 1: decisão CONFIRMED do TEST_AGENT", edt(() -> step(0).targetDecision().map(d -> d.confirmed() && d.actor() == DecisionActor.TEST_AGENT)
                .orElse(false)));
        // etapa 2: os dois botões são indistinguíveis — revisão; correção pela escolha no navegador
        GuidedDebugPanel.Card c = settled("duplicado", 1);
        log("  duplicado etapa 2: " + describe(c, lastInspection()));
        captureStudio("H7-duplicado-etapa2-revisao");
        check("H7 etapa 2: botão duplicado pede revisão (não proposto, Continuar desabilitado, " + c.candidates().size() + " candidatos, sem destaque sólido)",
                c.badge() != GuidedDebugPanel.Badge.PROPOSED && c.badge() != GuidedDebugPanel.Badge.REUSED && !c.canContinue() && c.canCorrect()
                        && c.candidates().size() >= 2 && !debugBoxVisible());
        click(edt(dp::correctButton));
        waitCard("corrigindo", x -> x.mode() == GuidedDebugPanel.Mode.CORRECTING);
        check("H7 etapa 2: rejeição registrada como TEST_AGENT", edt(() -> step(1).targetDecision().map(d -> !d.confirmed()
                && d.actor() == DecisionActor.TEST_AGENT).orElse(false)));
        click(edt(dp::pickButton));
        waitCard("seleção no navegador", x -> x.mode() == GuidedDebugPanel.Mode.PICKING);
        clickInPage(forms.get(1));
        GuidedDebugPanel.Card cmp = waitCard("comparação", x -> x.mode() == GuidedDebugPanel.Mode.COMPARING);
        captureStudio("H7-comparar-etapa2");
        check("H7 etapa 2: comparação antes × depois compatível, Aplicar habilitado", cmp.comparison().compatible() && edt(() -> dp.applyButton().isEnabled()));
        click(edt(dp::applyButton));
        GuidedDebugPanel.Card again = waitCard("novo alvo verificado", x -> x.mode() == GuidedDebugPanel.Mode.FINISHED || x.mode() == GuidedDebugPanel.Mode.STEP
                && x.badge() != GuidedDebugPanel.Badge.CHECKING);
        log("  etapa 2 após aplicar: " + again.mode() + " · " + again.badge().label() + " · " + describe(again, lastInspection()));
        if (again.mode() == GuidedDebugPanel.Mode.STEP && again.badge() == GuidedDebugPanel.Badge.PROPOSED) click(edt(dp::continueButton));
        check("H7 etapa 2: alvo substituído; decisão CONFIRMED do TEST_AGENT; capturado original intacto", edt(() -> step(1).replacementTarget().isPresent()
                && step(1).capturedElement().isPresent() && step(1).targetDecision().map(d -> d.confirmed() && d.actor() == DecisionActor.TEST_AGENT).orElse(false)));
        waitCard("fim da correção", x -> x.mode() == GuidedDebugPanel.Mode.FINISHED);
        check("H7: escolher no navegador não clicou, não focou e não enviou nada na página", interactions() == touched);
        check("H7: oráculo — nenhuma consulta durante a correção", oracle("/api/studio009/estado").contains("\"total\":0"));
        captureStudio("H7-fim");
        click(edt(dp::finishButton));
        waitDebugClosed();

        // H9: desfazer/refazer a substituição (atalhos reais do Studio)
        focusStudioWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_Z);
        waitFor("desfeito", 10, () -> edt(() -> step(1).replacementTarget().isEmpty()));
        check("H9: Ctrl+Z desfez a substituição da etapa 2 (volta ao alvo gravado)", edt(() -> step(1).replacementTarget().isEmpty()
                && step(1).capturedElement().isPresent()));
        captureStudio("H9-desfeito");
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_Y);
        waitFor("refeito", 10, () -> edt(() -> step(1).replacementTarget().isPresent()));
        check("H9: Ctrl+Y refez a substituição", edt(() -> step(1).replacementTarget().isPresent()));

        // salvar a correção (mesmo arquivo; 1.2.0)
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_S);
        Automation a = active();
        waitFor("salvo", 15, () -> !edt(() -> window.studio().state().project(a.id()).orElseThrow().dirty()));
        String all = zipText(file);
        check("H7: substituições salvas em 1.2.0 (targetReplacement no arquivo)", ProjectFormat.FORMAT_VERSION_1_2.equals(formatOf(file))
                && all.contains("\"targetReplacement\""));
    }

    private AutomationStep step(int i) throws Exception {
        return edt(() -> window.studio().state().activeAutomation().orElseThrow().sessions().get(0).steps().get(i));
    }

    // ============================================================================== H14 — temas e escala

    private void themes(String scaleLabel) throws Exception {
        navigate(PAGE + "?v=id-mudado");
        GuidedDebugPanel dp = openDebugAndStart();
        settled("id-mudado", 0);
        GuidedDebugWindow w = debugWindow();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        for (NekkoraTheme t : NekkoraThemes.all()) {
            edt(() -> {
                ThemeManager.setTheme(t);
                return null;
            });
            sleep(700);
            Rectangle b = edt(w::getBounds);
            String name = t.toString().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
            captureWindow(w, "H14-" + scaleLabel + "-" + name);
            check("H14 " + scaleLabel + "% · " + t + ": janela inteira dentro da tela (" + b + ")", screen.contains(b));
            check("H14 " + scaleLabel + "% · " + t + ": ações visíveis (Corrigir/Encerrar)", edt(() -> dp.correctButton().isShowing() && dp.finishButton().isShowing()));
        }
        edt(() -> {
            ThemeManager.setTheme(NekkoraThemes.SAKURA_DARK);
            return null;
        });
        check("H14 " + scaleLabel + "%: a janela do debug não cobre o elemento destacado", !debugWindowCoversTarget());
        click(edt(dp::finishButton));
        waitDebugClosed();
    }

    // ============================================================================== H15 — privacidade e registro

    private void h15Privacy(Path file) throws Exception {
        String record = edt(() -> window.studio().guidedDebug().recordJson().orElseThrow());
        Files.writeString(out.toPath().resolve("S009-registro-debug.json"), record, StandardCharsets.UTF_8);
        check("G7: registro do debug sem o valor digitado, sem nomes e com ator TEST_AGENT", !record.contains(TYPED) && !record.contains("Consultar")
                && record.contains("\"operator\": \"TEST_AGENT\"") && !record.contains("HUMAN"));
        check("G7: registro sem chaves de correlação e sem textos da página", !record.contains("tgt-") && !record.contains("Ana Souza")
                && !record.contains("Código do registro"));
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("projeto .nekkora", zipText(file));
        sources.put("registro do debug", record);
        sources.put("plano", Files.readString(out.toPath().resolve("S009-H2-plano.json"), StandardCharsets.UTF_8));
        sources.put("log da homologação", String.join("\n", log));
        sources.put("logs do host", hostLogs());
        sources.put("dados do Studio", dirText(Path.of(System.getenv().getOrDefault("NEKKORA_STUDIO_DATA_DIR", out.toPath().resolve("sem-dados").toString()))));
        sources.put("dados do host", dirText(hostDataDir()));
        for (Map.Entry<String, String> e : sources.entrySet()) {
            String txt = e.getValue();
            check("H15 " + e.getKey() + ": sem o valor digitado/sondado (REG-0001) e sem o nome fictício do rótulo suprimido", !txt.contains(TYPED)
                    && !txt.contains("Ana Souza"));
        }
    }

    private static String dirText(Path dir) throws IOException {
        StringBuilder b = new StringBuilder();
        if (!Files.isDirectory(dir)) return "";
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
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

    private GuidedDebugWindow debugWindow() throws Exception {
        return edt(() -> {
            for (Window x : Window.getWindows()) {
                if (x instanceof GuidedDebugWindow g && g.isShowing()) return g;
            }
            return null;
        });
    }

    private void waitDebugClosed() throws Exception {
        waitFor("janela do debug fechada", 10, () -> debugWindow() == null);
        sleep(300);
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

    private GuidedDebugPanel openDebugAndStart() throws Exception {
        return openDebugAndStart(t -> true, 0);
    }

    private GuidedDebugPanel openDebugAndStart(java.util.function.Predicate<GuidedDebugPanel.Tab> which, int nth) throws Exception {
        focusStudio();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_D);
        GuidedDebugWindow w = (GuidedDebugWindow) waitValue("janela do debug", 15, () -> edt(() -> {
            for (Window x : Window.getWindows()) {
                if (x instanceof GuidedDebugWindow g && g.isShowing()) return g;
            }
            return null;
        }));
        GuidedDebugPanel dp = edt(() -> window.studio().guidedDebug().panel());
        GuidedDebugPanel.Card listed = waitCard("abas listadas", c -> c.mode() == GuidedDebugPanel.Mode.CHOOSE_TAB && c.tabs().size() > nth);
        List<GuidedDebugPanel.Tab> options = listed.tabs().stream().filter(which).toList();
        int index = listed.tabs().indexOf(options.get(nth));
        edt(() -> {
            dp.tabsInput().setSelectedIndex(index);
            return null;
        });
        click(edt(dp::startButton));
        log("  janela do debug em " + edt(w::getBounds));
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
            log("  cartão atual: " + (c == null ? "nenhum" : c.mode() + " · " + c.badge() + " · " + c.progress() + " · " + c.title() + " · " + c.message()));
            captureStudio("FALHA-" + what.replaceAll("[^A-Za-z0-9]+", "-"));
            log("  quadros: " + harness.eval("window.nekkora.call('frames.list', {tabId: " + labTab + "}).then(r => JSON.stringify(r))"));
            log("  debugs: " + harness.eval("window.nekkora.call('studio.debugs', {}).then(r => JSON.stringify(r))"));
            throw e;
        }
    }

    private boolean debugBoxVisible() throws Exception {
        Object v = harness.eval("window.nekkora.call('page.diagnostics', {tabId: " + labTab + "}).then(r => !!(r.result && r.result.highlight && r.result.highlight.boxes"
                + " && r.result.highlight.boxes.debug && r.result.highlight.boxes.debug.visible))");
        return Boolean.TRUE.equals(v);
    }

    private int interactions() throws Exception {
        Object v = page.eval("(() => { const c = window.__nk009 || {}; return (c.pointerdown||0)+(c.mousedown||0)+(c.click||0)+(c.focusin||0)+(c.input||0)"
                + "+(c.change||0)+(c.submit||0)+(c.keydown||0); })()");
        return ((Number) v).intValue();
    }

    private boolean debugWindowCoversTarget() throws Exception {
        Object r = harness.eval("window.nekkora.call('page.diagnostics', {tabId: " + labTab + "}).then(r => JSON.stringify(r.result.highlight.boxes.debug.rect))");
        if (r == null || "undefined".equals(String.valueOf(r))) return false;
        Map<String, Object> rect = Json.parseObject(String.valueOf(r));
        @SuppressWarnings("unchecked")
        List<Object> origin = (List<Object>) page.eval("[window.screenX + (window.outerWidth - window.innerWidth) / 2, window.screenY + window.outerHeight"
                + " - window.innerHeight - (window.outerWidth - window.innerWidth) / 2]");
        Rectangle target = new Rectangle((int) (((Number) origin.get(0)).doubleValue() + ((Number) rect.get("left")).doubleValue()),
                (int) (((Number) origin.get(1)).doubleValue() + ((Number) rect.get("top")).doubleValue()), ((Number) rect.get("width")).intValue(),
                ((Number) rect.get("height")).intValue());
        Rectangle win = edt(() -> {
            for (Window x : Window.getWindows()) {
                if (x instanceof GuidedDebugWindow g && g.isShowing()) return g.getBounds();
            }
            return new Rectangle();
        });
        log("  alvo na tela " + target + " · janela do debug " + win);
        return win.intersects(target);
    }

    private String oracle(String path) throws Exception {
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        return http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + LAB_PORT + path)).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString()).body();
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
        profile = Files.createTempDirectory("nekkora-s009-chrome");
        // Perfil temporário sem o gerenciador de senhas do Chrome: o balão "Salvar senha?" não aparece nas evidências.
        Files.createDirectories(profile.resolve("Default"));
        Files.writeString(profile.resolve("Default").resolve("Preferences"),
                "{\"credentials_enable_service\": false, \"profile\": {\"password_manager_enabled\": false}}");
        testExtension = out.toPath().resolve("test-extension");
        copyExtension(ext.resolve("chrome-extension"), testExtension);
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        waitFor("laboratório respondendo", 20, () -> http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(PAGE + "?v=base")).build(),
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
        Files.createDirectories(hostDataDir());
        ProcessBuilder chromeBuilder = new ProcessBuilder(chromeExe.toString(), "--remote-debugging-port=" + cdpPort, "--user-data-dir=" + profile,
                "--disable-features=NetworkServiceSandbox", "--load-extension=" + testExtension, "--no-first-run", "--no-default-browser-check",
                "--disable-search-engine-choice-screen", "--lang=pt-BR", "--window-position=" + CHROME_X + ",0", "--window-size=1040,1000",
                "--host-resolver-rules=MAP site-a.nekkora.test 127.0.0.1,MAP site-x.nekkora.test 127.0.0.1", "about:blank")
                .redirectErrorStream(true).redirectOutput(new File(out, "chrome.log"));
        chromeBuilder.environment().put("NEKKORA_DATA_DIR", hostDataDir().toString()); // herdado pelo Native Host
        chrome = chromeBuilder.start();
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
        typeIntoFocused("Consulta de registro");
        press(KeyEvent.VK_ENTER);
        waitClosed(d);
        newSession("Consulta");
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
        File f = new File(out, "S009-" + name + ".png");
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
        try (PrintWriter w = new PrintWriter(new File(out, "S009-homologacao-log.txt"), StandardCharsets.UTF_8)) {
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
