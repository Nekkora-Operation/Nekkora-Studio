package com.nekkora.studio.app.evidence;

import com.nekkora.studio.ui.form.StepForm;
import com.nekkora.studio.ui.form.VariableForm;
import com.nekkora.studio.domain.variable.VariableType;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.action.ValueSource;
import javax.swing.JComboBox;
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
 * NEKKORA-E2E-002 — homologação REAL operada pelo Claude como TEST_AGENT: Studio de verdade, Chrome for Testing com a
 * extensão desta branch, Native Host oficial e mouse/teclado do sistema (Robot) sobre quatro artigos biográficos públicos
 * de https://pt.wikipedia.org (somente pesquisa e leitura). Nada de .nekkora, PDD, configuração ou Python escritos à mão.
 */
public final class CodegenQuality001Homologation {

    private static final int CHROME_X = 600;

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

    private CodegenQuality001Homologation(File out, Path ext) throws AWTException {
        this.out = out;
        this.ext = ext;
        this.robot = new Robot();
        robot.setAutoDelay(25);
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        CodegenQuality001Homologation h = new CodegenQuality001Homologation(out, Path.of(args[1]).toAbsolutePath().normalize());
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

    // ============================================================================== roteiro: NEKKORA-E2E-002

    /** Os quatro artigos (lista de entrada controlada): termo pesquisado e caminho do artigo. Nenhum dado biográfico. */
    private static final String[][] ARTICLES = {{"Machado de Assis", "/wiki/Machado_de_Assis"}, {"Clarice Lispector", "/wiki/Clarice_Lispector"},
            {"Marie Curie", "/wiki/Marie_Curie"}, {"Albert Einstein", "/wiki/Albert_Einstein"}};
    private static final String MAIN = "https://pt.wikipedia.org/wiki/Wikip%C3%A9dia:P%C3%A1gina_principal";
    /** Textos de artigo que NUNCA podem aparecer no modelo, no PDD ou no código (o Recorder não guarda conteúdo). */
    private static final List<String> PAGE_TEXT = List.of("Joaquim Maria", "21 de junho de 1839", "Chaya", "Skłodowska", "escritor", "brasileiro");
    private final Map<StepId, String> expectedJs = new LinkedHashMap<>();
    private Path projectFile;
    private Automation auto;
    private LogicalSession sIni;
    private LogicalSession sPes;
    private LogicalSession sExt;
    private List<AutomationStep> readSteps = List.of();
    private AutomationStep addressStep;
    private AutomationStep enterStep;

    private void run() throws Exception {
        log("Tela: " + GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds() + " · java " + System.getProperty("java.version"));
        log("Operador: Claude como AGENTE DE TESTE (TEST_AGENT) · ator do Studio: " + System.getProperty("nekkora.studio.decisionActor"));
        log("Site público de documentação: " + WP + " — só pesquisa e leitura; quatro artigos; nada é editado nem enviado");
        log("IA: provedor REAL configurado por ambiente (chave nunca impressa): " + (System.getenv("NEKKORA_ANTHROPIC_API_KEY") != null ? "presente" : "AUSENTE"));
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
        navigate(MAIN);
        connect();
        projectFile = DEST.resolve("wikipedia-biografias").resolve("biografias.nekkora");
        deleteTree(projectFile.getParent()); // saída de uma rodada anterior deste teste
        Files.createDirectories(projectFile.getParent());

        // ================================================================ G1 — automação, sessões, gravação
        focusStudio();
        createAutomation("Biografias da Wikipédia", List.of("Inicialização", "Pesquisa", "Extração", "Transferência de dados (Excel)", "Encerramento"));
        auto = active();
        sIni = edt(() -> auto.sessions().get(0));
        sPes = edt(() -> auto.sessions().get(1));
        sExt = edt(() -> auto.sessions().get(2));
        check("G1: automação com as cinco sessões funcionais do PDD", edt(() -> auto.sessions().stream().map(LogicalSession::name).toList())
                .equals(List.of("Inicialização", "Pesquisa", "Extração", "Transferência de dados (Excel)", "Encerramento")));

        // C02 — campo de busca editável (Capturar e interagir): clique, digitação real, Enter
        selectSession(auto, sPes);
        startRecordingOn("pt.wikipedia.org", RecordingMode.CAPTURE_AND_INTERACT);
        moveFloatingAway();
        clickInPage("document.getElementById('searchInput') || document.querySelector('header input[name=search]')");
        typeText("Machado de Assis");
        press(KeyEvent.VK_ENTER);
        waitPage("/wiki/Machado_de_Assis");
        waitFor("busca gravada (preencher + Enter)", 30, () -> edt(sPes::stepCount) >= 2);
        finishRecording();
        log("  Pesquisa: " + edt(() -> kinds(sPes)));
        AutomationStep fill = edt(() -> sPes.steps().get(0));
        AutomationStep enter = edt(() -> sPes.steps().get(1));
        check("C02: busca = Preencher + Pressionar Enter (o Enter não vira clique inventado)", edt(() -> kinds(sPes)).equals(List.of("FILL", "PRESS_KEY")));
        check("C02: valor digitado fora do modelo", !edt(() -> domainText(auto)).contains("Machado de Assis"));
        logTarget("busca", fill);
        check("C02: campo recriado identificado sem posição (localizador preferido único e não posicional)", edt(() -> fill.element().flatMap(e -> e.preferredLocator())
                .map(l -> l.strategy() != LocatorStrategy.POSITIONAL && l.matchCount() == 1).orElse(false)));
        log("  Enter: observação " + edt(() -> enter.provenance().recordingObservation().map(r -> r.evidence().toString()).orElse("nenhuma")));
        check("C02: Enter observado como envio da busca (FORM_SUBMITTED/NAVIGATION_FOLLOWED)", edt(() -> enter.provenance().recordingObservation()
                .map(r -> r.evidence().contains("NAVIGATION_FOLLOWED") || r.evidence().contains("FORM_SUBMITTED")).orElse(false)));
        expectedJs.put(fill.id(), SEARCH_JS);
        expectedJs.put(enter.id(), SEARCH_JS);

        // C03 — etapa do ENDEREÇO (Observar sem alvo), criada no editor, ANTES das leituras: será a coluna URL conferida
        selectSession(auto, sExt);
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_E);
        NekkoraDialog nd = waitDialog("Nova etapa");
        edt(() -> {
            StepForm f = find(nd, StepForm.class, x -> true);
            JComboBox<StepForm.SessionChoice> sc = f.sessionInput();
            for (int i = 0; i < sc.getItemCount(); i++) if (sc.getItemAt(i).label().endsWith("Extração")) sc.setSelectedIndex(i);
            f.typeInput().setSelectedItem(ActionType.OBSERVE);
            return null;
        });
        typeIntoFocused("Ler o endereço da página");
        captureWindow(nd, "G1-01-nova-etapa-endereco");
        press(KeyEvent.VK_ENTER);
        waitClosed(nd);
        AutomationStep address = edt(() -> sExt.steps().get(0));
        check("C03: etapa Observar sem alvo (endereço da página) criada pelo editor", edt(() -> address.actionType() == ActionType.OBSERVE && address.element().isEmpty()));

        // C03 — Selecionar dado: título e células da infocaixa (texto NÃO interativo), nada é clicado
        selectSession(auto, sExt);
        startRecordingOn("pt.wikipedia.org", RecordingMode.SELECT_DATA);
        moveFloatingAway();
        String url0 = String.valueOf(page.eval("location.href"));
        clickInPage(H1_JS);
        for (String h : List.of("Nome completo", "Nascimento", "Morte", "Nacionalidade", "Ocupação")) {
            clickInPage(cell(h) + ".querySelector('a') || " + cell(h)); // clicar até no link da data: em Selecionar dado, nada navega
        }
        waitFor("seis dados selecionados", 30, () -> edt(sExt::stepCount) >= 7);
        check("C03: Selecionar dado não executa nada (link da data não navegou)", url0.equals(String.valueOf(page.eval("location.href"))));
        navigate(WP + "/wiki/Marie_Curie"); // a fonte alternativa da Ocupação (D-E2E2-02) só existe em quem não tem a linha Ocupação
        clickInPage(cell("Campo(s)"));
        waitFor("Campo(s) selecionado", 30, () -> edt(sExt::stepCount) >= 8);
        finishRecording();
        log("  Extração: " + edt(() -> kinds(sExt)) + " · " + edt(() -> sExt.steps().stream().map(AutomationStep::name).toList()));
        List<AutomationStep> reads = edt(() -> List.copyOf(sExt.steps().subList(1, sExt.steps().size())));
        readSteps = reads;
        addressStep = address;
        enterStep = enter;
        check("C03: sete leituras Observar com alvo (título + seis células), nenhuma interação", edt(() -> reads.stream().allMatch(s -> s.actionType() == ActionType.OBSERVE
                && s.element().isPresent())) && reads.size() == 7);
        for (AutomationStep s : reads) logTarget("leitura", s);
        check("C03: cada leitura tem localizador preferido único e sem posição (ID do título; linha da tabela pelo rótulo)", edt(() -> reads.stream().allMatch(s ->
                s.element().flatMap(e -> e.preferredLocator()).map(l -> l.strategy() != LocatorStrategy.POSITIONAL && l.matchCount() == 1).orElse(false))));
        check("C03: as células são ancoradas pelo cabeçalho da linha (ROW_CONTEXT com o rótulo)", edt(() -> reads.subList(1, reads.size()).stream().allMatch(s ->
                s.element().flatMap(e -> e.preferredLocator()).map(l -> l.strategy() == LocatorStrategy.ROW_CONTEXT && l.expression().contains("th[normalize-space(.)=")).orElse(false))));
        check("C03: nenhum texto do artigo no modelo (só elementos e rótulos de linha)", PAGE_TEXT.stream().noneMatch(edt(() -> domainText(auto))::contains));
        expectedJs.put(reads.get(0).id(), H1_JS);
        String[] heads = {"Nome completo", "Nascimento", "Morte", "Nacionalidade", "Ocupação", "Campo(s)"};
        for (int i = 0; i < heads.length; i++) expectedJs.put(reads.get(i + 1).id(), cell(heads[i]));
        captureStudio("G1-02-gravacao-pesquisa-e-extracao");

        // variável de ENTRADA para a conferência do endereço (Variáveis e dependências) e ligação no editor da etapa
        focusStudioWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_B);
        waitFor("Variáveis e dependências", 10, () -> edt(() -> window.studio().view().variablesVisible()));
        click(edt(() -> window.studio().view().variablesPanel().createButton()));
        NekkoraDialog vd = waitDialog("Nova variável");
        VariableForm vf = edt(() -> find(vd.getContentPane(), VariableForm.class, x -> true));
        typeIntoFocused("endereco_do_artigo");
        edt(() -> {
            vf.typeInput().setSelectedItem(VariableType.STRING);
            vf.originInput().setSelectedItem(VariableOrigin.Kind.INPUT);
            return null;
        });
        captureWindow(vd, "G1-03-variavel-endereco");
        clickButtonWithText(vd, "Declarar variável");
        waitClosed(vd);
        check("variável de entrada endereco_do_artigo declarada sem valor", edt(() -> auto.variableNamed("endereco_do_artigo").map(v -> v.origin().kind()
                == VariableOrigin.Kind.INPUT).orElse(false)));
        clickRow(sExt, 0);
        press(KeyEvent.VK_F2);
        NekkoraDialog ed = waitDialog("Ler o endereço da página");
        StepDetailsForm df = edt(() -> find(ed.getContentPane(), StepDetailsForm.class, x -> true));
        clickTab(df, 1);
        click(edt(() -> df.modeInput(StepDetailsForm.ValueMode.VARIABLE)));
        waitFor("variáveis declaradas no editor", 5, () -> edt(() -> df.declaredVariableInput().isShowing()));
        edt(() -> {
            df.declaredVariableInput().setSelectedItem("endereco_do_artigo");
            return null;
        });
        captureWindow(ed, "G1-04-conferencia-endereco");
        clickButtonWithText(ed, "Salvar alterações");
        waitClosed(ed);
        check("C04: o endereço lido será CONFERIDO com a entrada (Observar com valor = variável)", edt(() -> new ValueSource.VariableReference("endereco_do_artigo")
                .equals(auto.step(address.id()).orElseThrow().parameters().value().orElse(null))));
        saveAs(projectFile);
        check("projeto salvo pelo Studio (.nekkora)", Files.isRegularFile(projectFile));

        // ================================================================ G2 — debug guiado nos quatro artigos
        navigate(MAIN);
        debugArticle("Página principal");
        if (Boolean.getBoolean("diagBusca")) return; // diagnóstico rápido da busca
        for (String[] art : ARTICLES) {
            navigate(WP + art[1]);
            debugArticle(art[0]);
        }
        check("G2: todas as etapas com alvo têm decisão CONFIRMED (TEST_AGENT)", edt(() -> auto.sessions().stream().flatMap(x -> x.steps().stream())
                .filter(s -> s.element().isPresent()).allMatch(s -> s.targetDecision().map(d -> d.confirmed() && d.actor() == DecisionActor.TEST_AGENT).orElse(false))));
        save();
        e2eSecondPart();
    }

    private static final String SEARCH_JS = "document.querySelector('header input[name=search]')";
    private static final String H1_JS = "document.getElementById('firstHeading')";

    private static String cell(String header) {
        return "[...document.querySelectorAll('table.infobox tr')].filter(r => r.querySelector(':scope > th') && r.querySelector(':scope > th').innerText.trim() === "
                + Json.write(header) + ").map(r => r.querySelector(':scope > td'))[0]";
    }

    private void logTarget(String what, AutomationStep s) throws Exception {
        String info = edt(() -> s.element().map(e -> e.semanticKind() + " · " + e.preferredLocator().map(l -> l.strategy() + " " + l.expression() + " ×" + l.matchCount())
                .orElse("sem localizador preferido")).orElse("sem alvo"));
        log("  " + what + " " + edt(s::name) + ": " + info);
    }

    /**
     * Debug guiado no artigo aberto: cada etapa com alvo esperado PRESENTE na página é confirmada se a identidade foi
     * comprovada no elemento certo (senão corrigida por seleção); alvo AUSENTE na página (fonte alternativa) deve vir
     * NOT_FOUND e é pulado — nunca "confirmado" num elemento parecido.
     */
    private void debugArticle(String label) throws Exception {
        GuidedDebugPanel dp = openDebugAndStart(t -> t.label().contains("pt.wikipedia.org") || t.label().contains("Autorizada"), 0);
        List<AutomationStep> steps = edt(() -> auto.sessions().stream().flatMap(x -> x.steps().stream()).filter(x -> x.element().isPresent()).toList()); // o debug só percorre etapas com alvo
        for (int i = 0; i < steps.size(); i++) {
            final int n = i;
            GuidedDebugPanel.Card card = waitCard(label + " etapa " + (i + 1), x -> x.mode() == GuidedDebugPanel.Mode.FINISHED
                    || x.mode() == GuidedDebugPanel.Mode.STEP && x.badge() != GuidedDebugPanel.Badge.CHECKING && x.progress().startsWith((n + 1) + " de"));
            if (card.mode() == GuidedDebugPanel.Mode.FINISHED) break;
            AutomationStep st = steps.get(i);
            String js = expectedJs.get(st.id());
            boolean search = js != null && js.equals(SEARCH_JS);
            boolean present = js != null && !search && page.eval("!!(" + js + ")") == Boolean.TRUE;
            GuidedDebugClient.Inspection r = lastInspection();
            log("  debug " + label + " etapa " + (i + 1) + " " + edt(st::name) + ": " + card.badge().label() + (r == null ? "" : " · " + r.status())
                    + (js == null ? "" : present ? " · alvo presente" : search ? " · busca" : " · alvo AUSENTE nesta página"));
            boolean main = label.equals("Página principal");
            if (main && !search) { // na página principal só a busca é conferida
                if (!card.canNext()) break;
                click(edt(dp::nextButton));
                continue;
            }
            if (js == null || search) { // a busca é confirmada na página principal, onde é usada
                if (r != null && r.status() != GuidedDebugClient.Status.RESOLVED && main) {
                    log("    por quê (busca): " + card.why());
                    log("    inspeção: " + r);
                    log("    campos name=search: " + page.eval("JSON.stringify([...document.querySelectorAll('input[name=search]')].map(e => { const c = [];"
                            + " for (let p = e.parentElement; p && p !== document.body; p = p.parentElement) c.push(p.tagName.toLowerCase() + (p.getAttribute('role') ? '[role=' + p.getAttribute('role') + ']' : '')"
                            + " + (p.id ? '#' + p.id : '')); return {role: e.getAttribute('role'), type: e.type, aria: e.getAttribute('aria-label'), visivel: !!e.offsetParent, cadeia: c.join(' < ')}; }))"));
                }
                if (search && main && r != null && r.status() == GuidedDebugClient.Status.RESOLVED && r.box() != null && boxOn(r.box(), js)) {
                    click(edt(dp::continueButton));
                    continue;
                }
                if (i == steps.size() - 1) break;
                click(edt(dp::nextButton));
                continue;
            }
            if (!present) {
                check("G2: " + label + " · " + edt(st::name) + " ausente na página → não confirmado num elemento parecido (" + (r == null ? "—" : r.status()) + ")",
                        r == null || r.status() != GuidedDebugClient.Status.RESOLVED);
                if (i == steps.size() - 1) break;
                click(edt(dp::nextButton));
                continue;
            }
            boolean ok = r != null && r.status() == GuidedDebugClient.Status.RESOLVED && r.box() != null && boxOn(r.box(), js);
            if (!ok) log("    por quê: " + card.title() + " · " + card.why() + " · caixa " + (r == null ? null : r.box()));
            check("G2: " + label + " · " + edt(st::name) + " identidade comprovada no elemento certo", ok);
            if (ok) {
                if (card.badge() == GuidedDebugPanel.Badge.REUSED) {
                    waitCard("avanço", x -> x.mode() == GuidedDebugPanel.Mode.FINISHED || !x.progress().startsWith((n + 1) + " de"));
                } else {
                    click(edt(dp::continueButton));
                }
            } else {
                if (i == steps.size() - 1) break;
                click(edt(dp::nextButton));
            }
        }
        if (edt(dp::card).mode() == GuidedDebugPanel.Mode.FINISHED) {
            click(edt(dp::finishButton));
        } else {
            edt(() -> {
                window.studio().guidedDebug().finish();
                return null;
            });
        }
        waitDebugClosed();
    }

    // ============================================================================== parte 2: PDD → Desenvolver (IA real) → teste autorizado

    private final Path PROJECT_DIR = DEST.resolve("wikipedia-biografias");
    private final Path SHEET = PROJECT_DIR.resolve("biografias.xlsx");
    private final Path INPUT = PROJECT_DIR.resolve("artigos.csv");
    private static final List<String> COLUMNS = List.of("URL", "Nome completo", "Nascimento", "Morte", "Nacionalidade", "Ocupação");

    private void e2eSecondPart() throws Exception {
        // ================================================================ G3 — PDD revisado pela interface
        reviewPddWindow(List.of("Inicialização do processo", "Pesquisa do artigo", "Extração dos dados", "Transferência de dados (Excel)", "Encerramento"),
                List.of("Pesquisar o artigo pelo nome", "Ler o endereço e os dados da ficha"));
        save();

        // ================================================================ G4 — Desenvolver automação: perguntas, IA real, decisões
        DevelopmentPanel dp = openDevelopment();
        waitDevIdle("análise inicial");
        logPending(dp, "inicial");
        captureWindow(devDialog(), "G4-01-desenvolver-inicial");
        answerSetupQuestions(dp);
        if (Boolean.getBoolean("nekkora.homolog.ai")) aiRound(dp); // CODEGEN-QUALITY-001: sem IA (não é necessária para nomear)
        answerRemaining(dp);
        DevelopmentPanel.Model m = waitDevIdle("resultado");
        if (m.pending().stream().anyMatch(i -> i.code().equals("ENVIRONMENT_NOT_READY"))) m = prepareAutomationEnvironment(dp);
        captureWindow(devDialog(), "G4-05-resultado");
        log("  pendências finais: " + m.pending().stream().map(i -> i.kind() + ":" + i.code()).toList());
        log("  resultado:\n" + String.join("\n", m.results()).indent(4));
        Path gen = PROJECT_DIR.resolve("biografias-python");
        check("G4: código gerado PELO STUDIO e verificado (CODE_VALIDATED)", m.results().contains("✓ CODE_VALIDATED") && Files.isRegularFile(gen.resolve("manifest.json")));
        String code = dirText(gen);
        check("G4: código sem texto de artigo e sem 'wikipedia' em nome de função do framework", PAGE_TEXT.stream().noneMatch(code::contains));
        check("G4: código usa a planilha do framework (XlsxResultSink) e as seis colunas na ordem", code.contains("XlsxResultSink") && columnsInOrder(code));
        check("G4: só a origem autorizada no código", code.contains(WP) && !code.contains("http://"));
        codegenQualityChecks(dp, gen);
        closeDevelopment();
        save();

        // ================================================================ G5 — PDF com o apêndice "Dados do processo"
        exportPdf();

        // ================================================================ G3b — PDD desatualizado bloqueia o desenvolvimento
        stalePddBlocks();

        // ================================================================ G6 — teste autorizado pela interface
        dp = openDevelopment();
        waitDevIdle("reabrir");
        click(edt(dp::developButton)); // o modelo mudou (etapa acrescentada + desfeita): gerar e verificar de novo antes de executar
        m = waitDevIdle("desenvolver de novo", 300);
        check("G6: código de novo CODE_VALIDATED na revisão atual", m.results().contains("✓ CODE_VALIDATED"));
        // C11: progresso visível e CANCELAR pela interface — a planilha não é gravada
        DevelopmentPanel live = dp;
        supervisedRunByUi(live);
        waitFor("progresso do teste autorizado", 120, () -> edt(() -> live.model().statusTitle().startsWith("Teste autorizado em andamento")
                && live.model().statusText().contains("Itens iniciados: 1")));
        log("  progresso: " + edt(() -> live.model().statusText()));
        bringDevelopmentToFront(); // o Chrome da execução abriu à frente: a pessoa volta ao Studio para cancelar
        captureWindow(devDialog(), "G6-02b-progresso");
        click(edt(() -> find(live, AbstractButton.class, x -> "develop.cancel".equals(x.getName()))));
        m = waitDevIdle("teste cancelado", 120);
        log("  cancelado: " + m.statusTitle() + " · " + m.results().stream().findFirst().orElse(""));
        captureWindow(devDialog(), "G6-02c-cancelado");
        check("C11: Cancelar interrompe o teste autorizado e a planilha NÃO é gravada", m.results().stream().anyMatch(r -> r.contains("CANCELLED"))
                && !Files.exists(SHEET));
        supervisedRunByUi(dp);
        m = waitDevIdle("teste autorizado", 1500);
        captureWindow(devDialog(), "G6-03-teste-resultado");
        log("  resultado do teste:\n" + String.join("\n", m.results()).indent(4));
        check("G6: teste autorizado terminou com planilha gravada e conferida", m.statusTitle().startsWith("Teste autorizado terminou: planilha gravada e conferida"));
        check("G6: planilha no destino escolhido", Files.isRegularFile(SHEET));
        closeDevelopment();

        // V6 na validação
        focusStudioWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_V); // Validar automação
        waitFor("camadas V0–V6", 120, () -> edt(() -> {
            String t = window.studio().view().validationPanel().layersText().getAccessibleContext().getAccessibleDescription();
            return t != null && t.contains("V6") && !t.contains("verificando") && !t.contains("procurando") && !t.contains("aguardando")
                    && !window.studio().development().running();
        }));
        sleep(1500);
        String layers = edt(() -> window.studio().view().validationPanel().layersText().getAccessibleContext().getAccessibleDescription());
        log("  camadas: " + layers);
        check("G6: V6 Execução real comprovada pelo teste autorizado desta revisão", layers.contains("✓ V6 Execução real"));
        check("G6: V3 descreve lista → planilha", layers.contains("V3 Plano: Lista → planilha"));
        check("CQ: VQ Qualidade da geração aprovada na validação (critérios objetivos; legibilidade = titular)", layers.contains("✓ VQ Qualidade da geração"));
        copyForHumanReview(PROJECT_DIR.resolve("biografias-python"), "final");
        captureStudio("G6-04-validacao-v6");

        // oráculo: os dados VISÍVEIS de cada artigo, lidos pelo CDP na página (para o conferidor independente)
        List<Object> pages = new ArrayList<>();
        for (String[] art : ARTICLES) {
            navigate(WP + art[1]);
            pages.add(page.eval("(() => { const rows = {}; for (const r of document.querySelectorAll('table.infobox tr')) { const th = r.querySelector(':scope > th');"
                    + " const td = r.querySelector(':scope > td'); if (!th || !td) continue; const lis = [...td.querySelectorAll('li')].filter(li => li.offsetParent !== null && !li.querySelector('li'));"
                    + " rows[th.innerText.trim()] = lis.length ? lis.map(li => li.innerText.trim()).join('\\n') : td.innerText; }"
                    + " return {url: location.href, h1: document.getElementById('firstHeading').innerText, rows}; })()"));
        }
        Files.writeString(out.toPath().resolve("oraculo-pagina.json"), Json.write(pages), StandardCharsets.UTF_8);
        log("  oráculo: dados visíveis dos 4 artigos gravados para o conferidor independente (fora do relatório)");
        save();
    }

    private boolean columnsInOrder(String code) {
        int at = -1;
        for (String c : COLUMNS) {
            int i = code.indexOf("\"" + c + "\"", at + 1);
            if (i < 0) return false;
            at = i;
        }
        return true;
    }

    private void logPending(DevelopmentPanel dp, String when) throws Exception {
        DevelopmentPanel.Model m = edt(dp::model);
        log("  pendências (" + when + "): " + m.pending().stream().map(i -> i.kind() + ":" + i.code() + (i.step() == null ? "" : "@" + stepLabel(i.step()))).toList());
    }

    private String stepLabel(StepId id) {
        try {
            return edt(() -> auto.step(id).map(AutomationStep::name).orElse("?"));
        } catch (Exception e) {
            return "?";
        }
    }

    /** Endereço inicial e autorização do site público (respostas da pessoa; não são perguntas para a IA). */
    private void answerSetupQuestions(DevelopmentPanel dp) throws Exception {
        for (int round = 0; round < 4; round++) {
            DevelopmentPanel.Model m = edt(dp::model);
            QualityIssue q = m.pending().stream().filter(i -> i.code().equals("SPEC_START_URL_UNKNOWN") || i.code().equals("ORIGIN_NOT_AUTHORIZED_FOR_EXECUTION"))
                    .findFirst().orElse(null);
            if (q == null) return;
            log("  pergunta: " + q.code() + " — " + q.assistance().map(AssistanceRequest::question).orElse(q.detail()));
            if (q.code().equals("SPEC_START_URL_UNKNOWN")) {
                answerText(dp, q.code(), MAIN);
            } else {
                clickPendingButton(dp, q.code(), "Autorizar como site público de treinamento…");
                NekkoraDialog d = (NekkoraDialog) waitValue("confirmação", 15, () -> edt(() -> {
                    for (Window w : Window.getWindows()) if (w instanceof NekkoraDialog x && x.isShowing() && "Autorizar site público de treinamento?".equals(x.getTitle())) return x;
                    return null;
                }));
                captureWindow(d, "G4-02-autorizar-site");
                clickButtonWithText(d, "Autorizar");
                waitClosed(d);
                waitDevIdle("depois da autorização");
            }
        }
    }

    /** O valor que a pessoa decidiu (D-E2E2-01/02) para cada decisão; a proposta da IA só é aplicada se coincidir. */
    private String expectedDecision(String kind, StepId step) throws Exception {
        int r = -1;
        for (int i = 0; i < readSteps.size(); i++) if (readSteps.get(i).id().equals(step)) r = i;
        return switch (kind) {
            case "DATA_FLOW" -> "LIST_XLSX";
            case "EFFECT" -> enterStep.id().equals(step) ? "NONE" : null;
            case "OUTPUT_COLUMN" -> r == 0 ? FunctionalSpec.FALLBACK + readSteps.get(1).id().value()
                    : r == 6 ? FunctionalSpec.FALLBACK + readSteps.get(5).id().value()
                    : r > 0 ? FunctionalSpec.COLUMN + COLUMNS.get(r)
                    : addressStep.id().equals(step) ? FunctionalSpec.COLUMN + "URL" : null;
            case "VARIABLE_TYPE" -> r == 2 || r == 3 ? "DATE" : r >= 0 ? "TEXT" : null;
            default -> null;
        };
    }

    /** Ajuda opcional por IA REAL: consentimento na janela, propostas verificadas, aplicação só do que a pessoa decidiu. */
    private void aiRound(DevelopmentPanel dp) throws Exception {
        String help = edt(() -> find(devDialog(), AbstractButton.class, b -> b.getText() != null && b.getText().startsWith("Solicitar ajuda")).getText());
        log("  botão de ajuda: " + help);
        check("G4: IA real disponível na interface (provedor configurado por ambiente)", help.equals("Solicitar ajuda (IA opcional)"));
        clickButtonWithText(devDialog(), help);
        NekkoraDialog consent = waitDialog("Enviar para análise por IA?");
        String shown = edt(() -> texts(consent));
        check("G4: a prévia do envio não contém texto de artigo nem chave", PAGE_TEXT.stream().noneMatch(shown::contains)
                && (System.getenv("NEKKORA_ANTHROPIC_API_KEY") == null || !shown.contains(System.getenv("NEKKORA_ANTHROPIC_API_KEY"))));
        captureWindow(consent, "G4-03-consentimento-ia");
        clickButtonWithText(consent, "Enviar esta análise");
        waitClosed(consent);
        DevelopmentPanel.Model m = waitDevIdle("análise por IA", 240);
        log("  IA: " + m.statusTitle() + " — " + m.statusText());
        check("G4: a análise por IA concluiu (resposta verificada)", m.statusTitle().startsWith("Propostas da IA"));
        captureWindow(devDialog(), "G4-04-propostas-ia");
        for (QualityIssue p : edt(() -> dp.model().pending().stream().filter(i -> i.code().equals("AI_PROPOSAL") && i.fix() != null).toList())) {
            String[] parts = p.fix().id().split(":", 3);
            log("  IA propôs: " + parts[1] + (p.step() == null ? "" : " @ " + stepLabel(p.step())) + " = " + shortValue(parts[2]) + " — " + p.detail());
        }
        int applied = 0;
        int declined = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int round = 0; round < 30; round++) {
            List<QualityIssue> props = edt(() -> dp.model().pending().stream().filter(i -> i.code().equals("AI_PROPOSAL") && i.fix() != null).toList());
            QualityIssue next = null;
            for (QualityIssue p : props) {
                String key = p.fix().id() + "@" + p.step();
                if (seen.add(key)) {
                    next = p;
                    break;
                }
            }
            if (next == null) break;
            String[] parts = next.fix().id().split(":", 3);
            String want = expectedDecision(parts[1], next.step());
            boolean ok = parts[2].equals(want);
            log("  proposta IA: " + parts[1] + (next.step() == null ? "" : " @ " + stepLabel(next.step())) + " = " + shortValue(parts[2]) + " → "
                    + (ok ? "APLICAR (coincide com a decisão da pessoa)" : "NÃO aplicar (decisão da pessoa: " + shortValue(want) + ")"));
            if (!ok) {
                declined++;
                continue;
            }
            QualityIssue target = next;
            selectPendingWhere(dp, "proposta", i -> i.code().equals("AI_PROPOSAL") && i.fix() != null && i.fix().id().equals(target.fix().id())
                    && java.util.Objects.equals(i.step(), target.step()));
            AbstractButton b = edt(() -> find(dp.pendingButtons(), AbstractButton.class, x -> x.getText() != null && x.getText().startsWith("Revisar e aplicar")));
            click(b);
            waitDevIdle("proposta aplicada");
            applied++;
        }
        log("  propostas da IA: aplicadas " + applied + " · não aplicadas " + declined);
        Path calls = Path.of(System.getenv().getOrDefault("NEKKORA_STUDIO_DATA_DIR", ""));
        String ev = "";
        if (Files.isDirectory(calls)) {
            try (Stream<Path> st = Files.walk(calls)) {
                for (Path f : st.filter(x -> x.getFileName().toString().equals("ia-chamadas.jsonl")).toList()) ev += Files.readString(f, StandardCharsets.UTF_8);
            }
        }
        log("  registro da chamada: " + ev.strip().replaceAll("\"payloadSha256\":\"[0-9a-f]+\"", "\"payloadSha256\":\"…\""));
        check("G4: chamada real registrada (RESPONSE_VERIFIED, modelo informado, sem chave)", ev.contains("RESPONSE_VERIFIED") && !ev.contains("\"model\":\"\"")
                && (System.getenv("NEKKORA_ANTHROPIC_API_KEY") == null || !ev.contains(System.getenv("NEKKORA_ANTHROPIC_API_KEY"))));
        Files.writeString(out.toPath().resolve("ia-chamadas.jsonl"), ev, StandardCharsets.UTF_8);
    }

    private static String shortValue(String v) {
        return v == null ? "—" : v.startsWith(FunctionalSpec.FALLBACK) ? "fonte alternativa" : v;
    }

    /** O que a IA não resolveu, a pessoa responde pela janela (as mesmas decisões). */
    private void answerRemaining(DevelopmentPanel dp) throws Exception {
        for (int round = 0; round < 25; round++) {
            DevelopmentPanel.Model m = edt(dp::model);
            QualityIssue q = m.pending().stream().filter(i -> i.kind() == QualityIssue.Kind.HELP_REQUIRED || i.kind() == QualityIssue.Kind.SECURITY_BLOCKED
                    || i.code().equals("SPEC_DATA_FLOW_OPTIONAL") || i.code().equals("SPEC_OUTPUT_COLUMN_REVIEW")).findFirst().orElse(null);
            if (q == null) return;
            String what = q.code() + (q.step() == null ? "" : " @ " + stepLabel(q.step()));
            String value = switch (q.code()) {
                case "SPEC_DATA_FLOW_OPTIONAL" -> "LIST_XLSX";
                case "SPEC_EFFECT_UNKNOWN" -> "NONE";
                case "SPEC_POSTCONDITION_MISSING" -> "NEXT_TARGET_VISIBLE";
                case "SPEC_CONCLUSION_MISSING" -> FunctionalSpec.NO_CONCLUSION;
                case "SPEC_OUTPUT_COLUMN_UNKNOWN", "SPEC_OUTPUT_COLUMN_REVIEW" -> expectedDecision("OUTPUT_COLUMN", q.step());
                default -> null;
            };
            log("  pergunta: " + what + " — " + q.assistance().map(AssistanceRequest::question).orElse(q.detail()) + " → " + shortValue(value));
            if (value == null) {
                captureWindow(devDialog(), "G4-pendencia-sem-resposta-" + round);
                return;
            }
            QualityIssue target = q;
            selectPendingWhere(dp, what, i -> i.code().equals(target.code()) && java.util.Objects.equals(i.step(), target.step()));
            QualityIssue sel = edt(() -> dp.pendingList().getSelectedValue());
            String label = sel.assistance().orElseThrow().options().stream().filter(o -> value.equals(o.value())).findFirst().orElseThrow().label();
            clickButtonWithText(edt(dp::pendingButtons), label);
            waitDevIdle("resposta " + q.code());
        }
    }

    private QualityIssue selectPendingWhere(DevelopmentPanel dp, String what, java.util.function.Predicate<QualityIssue> p) throws Exception {
        selectTabOf(dp, 1);
        int index = (int) waitValue("pendência " + what, 15, () -> edt(() -> {
            var l = dp.pendingList();
            for (int i = 0; i < l.getModel().getSize(); i++) if (p.test(l.getModel().getElementAt(i))) return i;
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
        waitFor("pendência selecionada", 5, () -> edt(() -> dp.pendingList().getSelectedValue() != null && p.test(dp.pendingList().getSelectedValue())));
        sleep(300);
        return edt(() -> dp.pendingList().getSelectedValue());
    }

    private DevelopmentPanel.Model waitDevIdle(String what, int seconds) throws Exception {
        sleep(600);
        waitFor(what, seconds, () -> edt(() -> !window.studio().development().running()));
        window.studio().development().awaitIdle();
        sleep(800);
        return edt(() -> window.studio().development().panel().model());
    }

    private void exportPdf() throws Exception {
        menuPath("Automação", "Gerar documentação", "PDD visual…");
        JDialog dlg = (JDialog) waitValue("janela do PDD", 15, () -> edt(() -> {
            for (Window w : Window.getWindows()) if (w instanceof JDialog d && d.isShowing() && d.getTitle().startsWith("PDD visual")) return d;
            return null;
        }));
        PddPanel p = edt(() -> window.studio().visualPdd().panel());
        sleep(800);
        Path pdfDir = out.toPath().resolve("pdf");
        Files.createDirectories(pdfDir);
        click(edt(p::exportButton));
        waitChooser("Pasta para o PDF (versões anteriores nunca são substituídas)");
        typePath(pdfDir);
        waitFor("PDF exportado", 30, () -> "PDF exportado".equals(model(p).bannerTitle()));
        captureWindow(dlg, "G5-01-pdf-exportado");
        Path pdf;
        try (Stream<Path> st = Files.list(pdfDir)) {
            pdf = st.filter(x -> x.toString().endsWith(".pdf")).sorted().reduce((a, b) -> b).orElseThrow();
        }
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf.toFile())) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
            Files.writeString(out.toPath().resolve("G5-pdf-texto-extraido.txt"), text, StandardCharsets.UTF_8);
            String flat = text.replaceAll("\\s+", " ");
            check("G5: PDF com o apêndice Dados do processo", flat.contains("Dados do processo") && flat.contains("tudo ou nada"));
            check("G5: PDF lista as seis colunas na ordem", columnsInOrder(flat.replace("1. URL", "\"URL\"").replace("2. Nome completo", "\"Nome completo\"")
                    .replace("3. Nascimento", "\"Nascimento\"").replace("4. Morte", "\"Morte\"").replace("5. Nacionalidade", "\"Nacionalidade\"")
                    .replace("6. Ocupação", "\"Ocupação\"")));
            check("G5: PDF declara as fontes alternativas (título e Campo(s))", flat.contains("se não existir na página: título da página")
                    && flat.contains("se não existir na página: linha “Campo(s)”"));
            check("G5: PDF sem texto de artigo", PAGE_TEXT.stream().noneMatch(flat::contains));
        }
        closePdd();
    }

    /** "Executar teste autorizado": lista de entrada e planilha escolhidas nos diálogos do sistema, confirmação e Chrome visível. */
    private void supervisedRunByUi(DevelopmentPanel dp) throws Exception {
        click(edt(dp::supervisedRunButton));
        Window ch = (Window) waitValue("diálogo da lista de entrada", 20, () -> {
            Window w = fileChooser();
            return w != null && ((JDialog) w).getTitle().startsWith("Lista de entrada do teste autorizado") ? w : null;
        });
        String title = edt(() -> ((JDialog) ch).getTitle());
        log("  " + title);
        java.util.regex.Matcher mm = java.util.regex.Pattern.compile("\\(colunas: (.*)\\)").matcher(title);
        check("G6: o Studio pede a lista com as colunas das variáveis de entrada", mm.find());
        List<String> cols = List.of(mm.group(1).split(", "));
        StringBuilder csv = new StringBuilder(String.join(",", cols)).append("\r\n");
        for (String[] art : ARTICLES) {
            List<String> row = new ArrayList<>();
            for (String c : cols) row.add(c.contains("endereco") ? WP + art[1] : art[0]);
            csv.append(String.join(",", row)).append("\r\n");
        }
        Files.writeString(INPUT, csv.toString(), StandardCharsets.UTF_8);
        log("  lista de entrada (dados de teste do agente, não código): " + cols + " · 4 linhas");
        captureWindow(ch, "G6-01-escolher-lista");
        typePathIn(ch, INPUT);
        waitChooser("Planilha de saída do teste autorizado");
        Files.deleteIfExists(SHEET);
        typePathIn(fileChooser(), SHEET);
        NekkoraDialog c = waitDialog("Executar teste autorizado?");
        String msg = edt(() -> texts(c));
        log("  confirmação: " + msg.replaceAll("\\s+", " "));
        check("G6: a confirmação diz o site, o destino, a lista e que não é produção", msg.contains(WP) && msg.contains(SHEET.toString()) && msg.contains("4 linha")
                && msg.contains("Não é execução de produção"));
        captureWindow(c, "G6-02-confirmar-execucao");
        clickButtonWithText(c, "Executar");
        waitClosed(c);
    }

    /** Digita o caminho no diálogo de arquivo indicado e espera ESTE diálogo fechar (outro pode abrir em seguida). */
    private void typePathIn(Window w, Path p) throws Exception {
        JTextComponent field = edt(() -> find(w, JTextComponent.class, x -> x.isShowing() && x.isEditable()));
        click(field);
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_A);
        typeAscii(p.toString());
        sleep(300);
        press(KeyEvent.VK_ENTER);
        waitFor("diálogo de arquivo fechado", 15, () -> !edt(w::isShowing));
        sleep(500);
    }

    /** Acrescentar uma etapa pelo editor (Ctrl+E) depois do PDD revisado: Desenvolver recusa (PDD_OUTDATED); Ctrl+Z volta. */
    private void stalePddBlocks() throws Exception {
        int before = edt(sExt::stepCount);
        selectSession(auto, sExt);
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_E);
        NekkoraDialog nd = waitDialog("Nova etapa");
        edt(() -> {
            StepForm f = find(nd, StepForm.class, x -> true);
            JComboBox<StepForm.SessionChoice> sc = f.sessionInput();
            for (int i = 0; i < sc.getItemCount(); i++) if (sc.getItemAt(i).label().endsWith("Extração")) sc.setSelectedIndex(i);
            f.typeInput().setSelectedItem(ActionType.OBSERVE);
            return null;
        });
        typeIntoFocused("Ler o resumo");
        press(KeyEvent.VK_ENTER);
        waitClosed(nd);
        waitFor("etapa acrescentada", 10, () -> edt(sExt::stepCount) == before + 1);
        DevelopmentPanel dp = openDevelopment();
        waitDevIdle("análise com o PDD desatualizado");
        click(edt(dp::developButton));
        DevelopmentPanel.Model m = waitDevIdle("desenvolver com o PDD desatualizado");
        log("  PDD desatualizado: " + m.statusTitle() + " · " + m.pending().stream().filter(i -> i.kind() == QualityIssue.Kind.BLOCKER).map(QualityIssue::code).toList());
        captureWindow(devDialog(), "G3-02-pdd-desatualizado-bloqueia");
        check("G3: PDD desatualizado bloqueia — nenhum código gerado (PDD_OUTDATED)", !m.results().contains("✓ CODE_GENERATED")
                && m.pending().stream().anyMatch(i -> i.code().equals("PDD_OUTDATED")));
        closeDevelopment();
        focusStudioWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_Z);
        waitFor("etapa desfeita", 10, () -> edt(sExt::stepCount) == before);
        check("G3: Ctrl+Z desfaz a etapa acrescentada (o PDD volta a valer)", true);
        save();
    }

    /** Traz a janela Desenvolver à frente sem clicar em outra janela: ALT libera a troca de primeiro plano no Windows. */
    private void bringDevelopmentToFront() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyRelease(KeyEvent.VK_ALT);
            robot.waitForIdle();
            edt(() -> {
                JDialog d = devDialog();
                d.toFront();
                d.requestFocus();
                return null;
            });
            sleep(500);
            if (edt(() -> devDialog().isActive())) return;
        }
        throw new IllegalStateException("a janela Desenvolver não voltou à frente");
    }

    // ============================================================================== apoio

    private static final String WP = "https://pt.wikipedia.org";
    private static final String BASE = WP;
    private static final boolean GRANT_IN_MANIFEST = !Boolean.getBoolean("panelGrant");
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

    /**
     * Diretórios isolados começam sem a venv da automação: o caminho da pessoa é a pendência "Ambiente Python da automação"
     * → "Preparar ambiente…" → confirmação do plano → instalação → Desenvolver de novo (V4/V5/VQ com o ambiente novo).
     */
    private DevelopmentPanel.Model prepareAutomationEnvironment(DevelopmentPanel dp) throws Exception {
        selectPending(dp, "ENVIRONMENT_NOT_READY");
        AbstractButton b = edt(() -> find(dp.pendingButtons(), AbstractButton.class, x -> x.getText() != null && x.getText().startsWith("Preparar ambiente")));
        if (b == null) throw new IllegalStateException("botão Preparar ambiente não encontrado na pendência");
        click(b);
        NekkoraDialog d = waitDialog("Preparar o ambiente Python da automação?");
        log("  plano do ambiente:\n" + texts(d).indent(4));
        captureWindow(d, "G4-04-preparar-ambiente");
        clickButtonWithText(d, "Preparar ambiente");
        waitClosed(d);
        waitFor("ambiente pronto e código verificado", 1200, () -> edt(() -> !window.studio().development().running()
                && (dp.model().results().contains("✓ CODE_VALIDATED") || dp.model().severity() == com.nekkora.ui.core.Severity.ERROR)));
        DevelopmentPanel.Model m = waitDevIdle("resultado com o ambiente preparado");
        log("  ambiente: " + m.python());
        return m;
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
        robot.mouseMove((int) Math.round(1500 / sc), (int) Math.round(63 / sc)); // barra de endereço, fora da área do Studio
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
        if (GRANT_IN_MANIFEST) hosts.add(WP + "/*"); // só quando a rodada não usa o fluxo "Autorizar este site" do painel
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
                "--disable-search-engine-choice-screen", "--lang=pt-BR", "--window-position=" + CHROME_X + ",0", "--window-size=1320,1000",
                "--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE pt.wikipedia.org, EXCLUDE localhost, EXCLUDE 127.0.0.1", "about:blank")
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
            floating().setLocation(10, 560);
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

    // ============================================================================== NEKKORA-CODEGEN-QUALITY-001

    private static final java.util.regex.Pattern UUID_RE = java.util.regex.Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    /** Q01/Q02/Q03 no código que o Studio acabou de gerar pela interface; "Revisar código"; cópia para a titular. */
    @SuppressWarnings("unchecked")
    private void codegenQualityChecks(DevelopmentPanel dp, Path gen) throws Exception {
        String pages = Files.readString(gen.resolve("paginas.py"), StandardCharsets.UTF_8);
        String process = Files.readString(gen.resolve("processo.py"), StandardCharsets.UTF_8);
        Map<String, Object> manifest = Json.parseObject(Files.readString(gen.resolve("manifest.json"), StandardCharsets.UTF_8));
        java.util.regex.Matcher classes = java.util.regex.Pattern.compile("class (\\w+)\\(").matcher(pages);
        List<String> found = new ArrayList<>();
        while (classes.find()) found.add(classes.group(1));
        java.util.regex.Matcher defs = java.util.regex.Pattern.compile("    def (\\w+)\\(").matcher(pages);
        List<String> methods = new ArrayList<>();
        while (defs.find()) methods.add(defs.group(1));
        log("  classes em paginas.py: " + found + " · métodos: " + methods);
        check("CQ: duas Pages por evidência (busca e ficha separadas pela troca de documento)", pages.contains("class PaginaPesquisa(PaginaBase)")
                && pages.contains("class PaginaFicha(PaginaBase)"));
        check("CQ-01: a tela da ficha pelo DONO dos dados no PDD (\"os dados da ficha\"): PaginaFicha, ler_dados_da_ficha, DadosDaFicha",
                pages.contains("def ler_dados_da_ficha(self) -> DadosDaFicha:") && pages.contains("class DadosDaFicha:") && process.contains("dados_da_ficha"));
        check("CQ: método da busca nomeado pelo passo do PDD (pesquisar_artigo)", pages.contains("def pesquisar_artigo(self, termo_de_busca: str) -> None:"));
        check("CQ: leituras da ficha num método que devolve dados tipados (dataclass)", pages.contains("@dataclass(frozen=True)"));
        check("CQ-02: API do framework com nomes legíveis (self.acoes / self.elemento); act e k só na PaginaBase",
                pages.contains("class PaginaBase(BasePage):") && pages.split("self\\.act\\b", -1).length == 2 && pages.split("self\\.k\\(", -1).length == 2);
        check("CQ-03: docstring da tela diz onde está no PDD e o papel; sem comentário solto da espera",
                pages.contains("\"\"\"Tela da seção “Pesquisa do artigo” do PDD: pesquisa o artigo pelo nome.\"\"\"") && !pages.contains("# a página está pronta"));
        check("CQ-07: coluna vazia recusa com o NOME do campo (nunca o conteúdo)", process.contains("raise BusinessRuleRejected(f\"DADO_OBRIGATORIO_VAZIO:{campo}\")"));
        check("CQ-08: abertura inicial na convenção (processo.abrir_pagina_inicial) e o id anterior declarado no manifesto",
                process.contains("step_id=\"processo.abrir_pagina_inicial\"") && !process.contains("\"INICIO\"")
                && Json.write(manifest.get("trace")).contains("\"previousTraceIds\""));
        check("CQ: nenhum UUID nem comentário por etapa no corpo das Pages e do processo", !UUID_RE.matcher(pages + process).find()
                && !(pages + process).contains("# etapa"));
        check("CQ: sem nomes opacos (a = self.act, etapas_sessao_N, fill, press_key, campo_s, TelaPesquisa)", !java.util.regex.Pattern.compile("\\ba = self\\.act\\b").matcher(pages + process).find()
                && !(pages + process).contains("etapas_sessao_") && !(pages + process).contains("\"fill\"") && !(pages + process).contains("press_key")
                && !(pages + process).contains("campo_s") && !(pages + process).contains("TelaPesquisa"));
        Map<String, Object> trace = (Map<String, Object>) manifest.get("trace");
        List<Object> mappings = (List<Object>) trace.get("mappings");
        java.util.Set<String> ids = new java.util.HashSet<>();
        java.util.Set<String> stepsMapped = new java.util.HashSet<>();
        for (Object o : mappings) {
            Map<String, Object> x = (Map<String, Object>) o;
            ids.add(String.valueOf(x.get("traceId")));
            stepsMapped.add(String.valueOf(x.get("studioStepId")));
        }
        java.util.regex.Matcher sid = java.util.regex.Pattern.compile("step_id=\"([^\"]+)\"").matcher(pages + process);
        boolean allMapped = true;
        while (sid.find()) allMapped &= ids.contains(sid.group(1)) || sid.group(1).equals(com.nekkora.studio.codegen.CodePlan.START_TRACE);
        check("CQ: step_id legível e mapeado 1:1 para a etapa do Studio (manifesto 1.3.0)", "1.3.0".equals(manifest.get("version")) && allMapped
                && ids.size() == mappings.size() && stepsMapped.size() == mappings.size() && mappings.size() == ((List<Object>) manifest.get("steps")).size());
        check("CQ: processo com o nome de negócio (BiografiasDaWikipedia)", process.contains("class BiografiasDaWikipedia(RpaProcess)"));
        check("CQ: requisito do framework preservado (nekkora-rpa[excel]==0.3.0) e planilha pelo XlsxResultSink",
                "nekkora-rpa[excel]==0.3.0".equals(manifest.get("framework")) && Files.readString(gen.resolve("main.py"), StandardCharsets.UTF_8).contains("XlsxResultSink("));
        boolean preview = edt(() -> dp.model().answers().stream().anyMatch(a -> a.question().startsWith("Como o código fica organizado")
                && a.answer().contains("PaginaPesquisa")));
        check("CQ: prévia da arquitetura na aba Plano (Pages, métodos e de onde vêm os nomes)", preview);
        AbstractButton review = edt(() -> find(devDialog(), AbstractButton.class, x -> "develop.review".equals(x.getName())));
        click(review);
        NekkoraDialog d = waitDialog("Revisar código gerado");
        String shown = edt(() -> texts(d));
        captureWindow(d, "CQ-01-revisar-codigo");
        check("CQ: Revisar código mostra arquitetura, mapa step_id → etapa e localizadores", shown.contains("ARQUITETURA") && shown.contains("STEP_ID NO CÓDIGO")
                && shown.contains("COMO CADA ELEMENTO É LOCALIZADO") && shown.contains("class PaginaFicha"));
        clickButtonWithText(d, "Fechar");
        waitClosed(d);
        copyForHumanReview(gen, "desenvolver");
    }

    /** Cópia ORIGINAL (bytes do Studio) da pasta gerada + SHA-256 de cada arquivo, para a revisão humana. */
    private void copyForHumanReview(Path gen, String moment) throws Exception {
        String base = System.getProperty("nekkora.homolog.review");
        if (base == null || base.isBlank()) return;
        Path to = Path.of(base).resolve(moment);
        deleteTree(to);
        StringBuilder sums = new StringBuilder();
        try (Stream<Path> st = Files.walk(gen)) {
            for (Path p : st.sorted().toList()) {
                Path rel = gen.relativize(p);
                if (rel.toString().startsWith(".venv") || rel.toString().contains("__pycache__")) continue;
                Path dst = to.resolve(rel.toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dst);
                } else {
                    Files.createDirectories(dst.getParent());
                    Files.copy(p, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    sums.append(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))))
                            .append("  ").append(rel.toString().replace('\\', '/')).append('\n');
                }
            }
        }
        Files.writeString(to.resolve("SHA256SUMS.txt"), sums.toString(), StandardCharsets.UTF_8);
        log("  revisão humana: cópia original em " + to + " (" + sums.toString().lines().count() + " arquivos com SHA-256)");
    }
}
