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
 * Homologação REAL da STUDIO-005 no Windows: Studio de verdade, Chrome for Testing com a extensão, Native Host instalado
 * pelo script oficial, páginas FICTÍCIAS do laboratório e mouse/teclado do sistema operacional (Robot). Cenários 1 a 10
 * da missão: erro de autenticação, mensagem tardia, mensagem ambígua, validação, pesquisa com carregamento, SPA, iframe
 * autorizado e sem permissão, dados sensíveis, editor (aba Evidências, desfazer/refazer, duplicar, mover) e falhas
 * (pausa, desconexão, cancelamento). Nenhuma captura de tela é feita pelo produto: as imagens desta homologação são das
 * janelas do Studio, de páginas de teste.
 *
 * <p>Uso: {@code java ... Studio005Homologation <pasta-de-saída> <repositório-da-extensão>}
 */
public final class Studio005Homologation {

    private static final int LAB_PORT = 18765;
    private static final String BASE = "http://127.0.0.1:" + LAB_PORT + "/lab/studio005/";
    private static final String SITE = "http://site-a.nekkora.test:" + LAB_PORT + "/lab/studio003/consulta.html";
    /** Valores digitados na homologação (fictícios): NUNCA podem aparecer em etapas, logs, armazenamento ou tela. */
    private static final String USER = "anateste";
    /** Nome e código digitados no cenário 8 (fictícios). */
    private static final String NAME = "JoanaPrado";
    private static final String CODE = "Kq7Segredo";
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

    private Studio005Homologation(File out, Path ext) throws AWTException {
        this.out = out;
        this.ext = ext;
        this.robot = new Robot();
        robot.setAutoDelay(25);
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Studio005Homologation h = new Studio005Homologation(out, Path.of(args[1]).toAbsolutePath().normalize());
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
        launchChrome(BASE + "login-erro.html");
        connect();

        focusStudio();
        createAutomation();
        Automation a = edt(() -> window.studio().state().activeAutomation().orElseThrow());
        LogicalSession auth = a.sessions().get(0);
        LogicalSession consulta = a.sessions().get(1);

        // ================================================================ Cenário 1 — Erro de autenticação
        selectSession(a, auth);
        startRecordingOn("Login com erro (STUDIO-005)", RecordingMode.CAPTURE_AND_INTERACT);
        FloatingRecorderPanel fp = edt(() -> window.studio().recorderPresenter().panel());
        moveFloatingAway();
        check("S005-003 evidence/1 negociada com a extensão real", edt(() -> window.studio().recorder().snapshot().evidence().enabled()));
        clickInPage("document.getElementById('usuario')");
        typeText(USER);
        clickInPage("document.getElementById('senha')");
        typeText(PASSWORD);
        clickInPage("document.getElementById('entrar')");
        waitFor("3 etapas na Autenticação", 30, () -> edt(auth::stepCount) == 3);
        AutomationStep entrar = edt(() -> auth.steps().get(2));
        ObservedEvidence erro = waitEvidence("alerta de erro do Entrar", entrar, EvidenceType.VISIBLE_ERROR_MESSAGE);
        check("Cenário 1: mensagem de erro associada ao clique em Entrar (indicador de erro, texto liberado)",
                erro.classification() == EvidenceVocabulary.Classification.ERROR_INDICATOR && "Usuário ou senha inválidos.".equals(erro.message().text()));
        waitEvidence("fim da observação do Entrar", entrar, EvidenceType.OBSERVATION_COMPLETE);
        check("Cenário 1: envio do formulário observado diretamente", has(entrar, EvidenceType.FORM_SUBMITTED));
        ObservationSummary s1 = edt(entrar::observation);
        check("S005-034 resumo estruturado: mudanças observadas, indicador de erro, negócio NÃO validado",
                s1.status() == ObservationSummary.Status.OBSERVED && s1.error() && s1.businessOutcome() == RecordingObservation.BusinessOutcome.NOT_VALIDATED);
        check("S005-027/033 a etapa não foi editada pela evidência (nome, tipo, original)", edt(() -> "Clicar no botão Entrar".equals(entrar.name())
                && entrar.manualRevision().isEmpty()));
        check("S005-020 mensagem que já estava na tela não virou evidência", edt(() -> a.sessions().stream().flatMap(s -> s.steps().stream())
                .flatMap(st -> st.evidence().stream()).noneMatch(e -> e.message().text() != null && e.message().text().contains("manutenção"))));
        check("S005-062 (bloqueio S004) automação bloqueada para edição enquanto recebe evidências", edt(() -> window.studio().service().isLocked(a.id())));
        waitFor("Floating Recorder com evidências", 10, () -> edt(() -> texts(fp)).contains("registradas") && edt(() -> texts(fp)).contains("Última observação"));
        check("S005-057 Floating Recorder mostra Evidências e Última observação", edt(() -> texts(fp)).matches("(?s).*Evidências.*\\d+ registradas.*"));
        captureWindow(floating(), "01-floating-evidencias");
        selectStep(a, auth, entrar);
        String insp1 = edt(() -> texts(window.studio().view().inspector()));
        check("S005-058 painel: grupo Evidências com a mensagem e 'Resultado de negócio: Não validado'",
                insp1.contains("Mensagem de erro exibida") && insp1.contains("Usuário ou senha inválidos.") && insp1.contains("Não validado"));
        captureStudio("02-login-erro-painel");

        // ================================================================ Cenário 2 — Mensagem tardia (1 s)
        navigateByUser(BASE + "tardia.html");
        int c2 = edt(auth::stepCount);
        long clicked = System.currentTimeMillis();
        clickInPage("document.getElementById('enviar')");
        waitFor("etapa Enviar", 20, () -> edt(auth::stepCount) == c2 + 1);
        AutomationStep enviar = edt(() -> auth.steps().get(c2));
        ObservedEvidence tardia = waitEvidence("confirmação tardia", enviar, EvidenceType.VISIBLE_STATUS_MESSAGE);
        long atraso = tardia.observedAt().toEpochMilli() - clicked; // relógio da mesma máquina (o clique é feito pelo Robot)
        check("Cenário 2: mensagem 1 s depois associada à etapa (status, não sucesso de negócio)", "Pedido registrado com sucesso.".equals(tardia.message().text())
                && tardia.classification() == EvidenceVocabulary.Classification.STATUS_INDICATOR);
        // medido do INÍCIO do gesto do Robot (foco, rolagem e mira tomam ~0,9 s antes do clique em si) até o observedAt real
        log("  do início do gesto de clique até a mensagem tardia observada: " + atraso + " ms (o clique em si acontece ~0,9 s depois do início)");
        check("Cenário 2: observada depois do atraso de 1 s da página e dentro da janela de 3 s", atraso >= 1000 && atraso < 3900);
        check("S005-034/064 'sucesso' no texto não vira resultado de negócio",
                edt(() -> enviar.observation().businessOutcome() == RecordingObservation.BusinessOutcome.NOT_VALIDATED));

        // ================================================================ Cenário 3 — Mensagem ambígua
        navigateByUser(BASE + "ambigua.html");
        int c3 = edt(auth::stepCount);
        int amb0 = edt(() -> a.unattributedEvidence().size());
        clickInPage("document.getElementById('atualizar')");
        clickInPage("document.getElementById('sincronizar')");
        waitFor("2 etapas", 20, () -> edt(auth::stepCount) == c3 + 2);
        waitFor("evidência ambígua", 20, () -> edt(() -> a.unattributedEvidence().size()) > amb0);
        ObservedEvidence amb = edt(() -> a.unattributedEvidence().get(a.unattributedEvidence().size() - 1));
        check("Cenário 3: um aviso com duas ações candidatas fica SEM etapa (nenhuma escolhida)", amb.eventId() == null
                && amb.confidence() == EvidenceVocabulary.Confidence.AMBIGUOUS && amb.candidates() == 2);
        check("Cenário 3: nenhuma das duas etapas recebeu o aviso", edt(() -> auth.steps().subList(c3, c3 + 2).stream()
                .allMatch(st -> st.evidence().stream().noneMatch(e -> e.type() == EvidenceType.VISIBLE_STATUS_MESSAGE))));
        edt(() -> {
            window.studio().controller().selectAutomation(a.id());
            return null;
        });
        sleep(500);
        check("Cenário 3: painel da automação lista a evidência sem etapa definida", edt(() -> texts(window.studio().view().inspector()))
                .contains("nenhuma etapa foi escolhida"));
        captureStudio("03-ambigua-sem-etapa");

        // ================================================================ Cenário 4 — Validação
        edt(() -> {
            fp.sessionCombo().setSelectedItem(consulta); // a sessão da gravação muda pelo Floating Recorder
            return null;
        });
        waitFor("sessão Consulta associada", 10, () -> edt(() -> consulta.id().equals(window.studio().recorder().snapshot().session())));
        navigateByUser(BASE + "validacao.html");
        clickInPage("document.getElementById('cep')");
        typeText("123");
        clickInPage("document.getElementById('enviar')");
        waitFor("2 etapas na Consulta", 20, () -> edt(consulta::stepCount) == 2);
        AutomationStep cep = edt(() -> consulta.steps().get(0));
        AutomationStep cadastrar = edt(() -> consulta.steps().get(1));
        ObservedEvidence vcep = waitEvidence("validação do CEP", cep, EvidenceType.VALIDATION_FEEDBACK);
        ObservedEvidence vmail = waitEvidence("validação nativa do e-mail", cadastrar, EvidenceType.VALIDATION_FEEDBACK);
        check("Cenário 4: CEP inválido ao sair do campo (aria-invalid, observado diretamente)", "CEP".equals(vcep.field())
                && vcep.confidence() == EvidenceVocabulary.Confidence.DIRECTLY_OBSERVED && "CEP inválido.".equals(vcep.message().text()));
        check("Cenário 4: e-mail obrigatório pela validação nativa (sem reportValidity)", "E-mail".equals(vmail.field())
                && vmail.signals().contains(EvidenceVocabulary.Signal.NATIVE_INVALID));
        check("Cenário 4: formulário não enviado", "".equals(page.eval("document.getElementById('resultado').textContent")));
        selectStep(a, consulta, cep);
        captureStudio("04-validacao");

        // ================================================================ Cenário 5 — Pesquisa com carregamento
        navigateByUser(BASE + "pesquisa.html");
        int c5 = edt(consulta::stepCount);
        clickInPage("document.getElementById('termo')");
        typeText("contrato");
        clickInPage("document.getElementById('pesquisar')");
        waitFor("etapas da pesquisa", 20, () -> edt(consulta::stepCount) == c5 + 2);
        AutomationStep pesquisar = edt(() -> consulta.steps().get(c5 + 1));
        waitEvidence("carregamento", pesquisar, EvidenceType.LOADING_INDICATOR);
        waitEvidence("fim do carregamento", pesquisar, EvidenceType.LOADING_FINISHED);
        waitEvidence("conteúdo atualizado", pesquisar, EvidenceType.CONTENT_CHANGED);
        ObservedEvidence contagem = waitEvidence("contagem", pesquisar, EvidenceType.VISIBLE_STATUS_MESSAGE);
        waitEvidence("fim da observação", pesquisar, EvidenceType.OBSERVATION_COMPLETE);
        check("Cenário 5: carregamento → fim → conteúdo → contagem, sem concluir sucesso", "3 resultados encontrados.".equals(contagem.message().text())
                && edt(() -> pesquisar.observation().status() == ObservationSummary.Status.OBSERVED && !pesquisar.observation().error()));
        check("Cenário 5: conteúdo da lista não saiu da página", edt(() -> pesquisar.evidence().stream().noneMatch(e -> String.valueOf(e).contains("Contrato Alfa"))));
        selectStep(a, consulta, pesquisar);
        captureStudio("05-pesquisa-carregamento");

        // ================================================================ Cenário 6 — SPA
        navigateByUser(BASE + "spa.html");
        int c6 = edt(consulta::stepCount);
        clickInPage("document.getElementById('rota-clientes')");
        waitFor("etapa Clientes", 20, () -> edt(consulta::stepCount) == c6 + 1);
        AutomationStep clientes = edt(() -> consulta.steps().get(c6));
        ObservedEvidence rota = waitEvidence("troca de rota", clientes, EvidenceType.ROUTE_CHANGED);
        check("Cenário 6: rota alterada no mesmo documento (History API)", rota.navigation() != null && !rota.navigation().newDocument());
        sleep(QUIET_MS);
        clickInPage("document.getElementById('filtros')");
        waitFor("etapa Filtros", 20, () -> edt(consulta::stepCount) == c6 + 2);
        AutomationStep filtros = edt(() -> consulta.steps().get(c6 + 1));
        ObservedEvidence estado = waitEvidence("estado do botão", filtros, EvidenceType.ELEMENT_STATE_CHANGED);
        check("Cenário 6: aria-expanded false → true observado no próprio botão", estado.state() != null
                && estado.state().attribute() == EvidenceVocabulary.StateAttribute.ARIA_EXPANDED && estado.confidence() == EvidenceVocabulary.Confidence.DIRECTLY_OBSERVED);
        sleep(QUIET_MS);
        clickInPage("document.getElementById('outro-documento')");
        waitFor("etapa do link", 20, () -> edt(consulta::stepCount) == c6 + 3);
        AutomationStep link = edt(() -> consulta.steps().get(c6 + 2));
        waitEvidence("novo documento", link, EvidenceType.DOCUMENT_CHANGED);
        ObservedEvidence corte = waitEvidence("observação cortada pela navegação", link, EvidenceType.OBSERVATION_INCOMPLETE);
        check("Cenário 6: novo documento e janela INCOMPLETA (WINDOW_CUT_BY_NAVIGATION)",
                corte.limitations().contains(EvidenceVocabulary.Limitation.WINDOW_CUT_BY_NAVIGATION));
        selectStep(a, consulta, filtros);
        captureStudio("06-spa-estado");

        // ================================================================ Cenário 7 — iframe autorizado e sem permissão
        LogicalSession quadros = newSession("Quadros");
        edt(() -> {
            fp.sessionCombo().setSelectedItem(quadros);
            return null;
        });
        waitFor("sessão Quadros associada", 10, () -> edt(() -> quadros.id().equals(window.studio().recorder().snapshot().session())));
        navigateByUser(BASE + "iframe.html?externo=" + java.net.URLEncoder.encode("http://site-x.nekkora.test:" + LAB_PORT + "/lab/studio005/quadro.html",
                StandardCharsets.UTF_8));
        clickInPage("document.getElementById('quadro').contentDocument.getElementById('salvar')", "document.getElementById('quadro')");
        waitFor("etapa no iframe", 20, () -> edt(quadros::stepCount) == 1);
        AutomationStep salvar = edt(() -> quadros.steps().get(0));
        ObservedEvidence noQuadro = waitEvidence("erro no iframe", salvar, EvidenceType.VISIBLE_ERROR_MESSAGE);
        check("Cenário 7: mensagem do iframe autorizado é da ação no iframe (documento FRAME)", noQuadro.document().frame()
                && "Erro ao salvar o rascunho.".equals(noQuadro.message().text()));
        clickInPage("document.getElementById('externo')"); // origem SEM permissão: o Chrome não injeta o content script
        sleep(QUIET_MS);
        check("Cenário 7: clique no iframe sem permissão não gerou etapa nem evidência", edt(quadros::stepCount) == 1);
        clickInPage("document.getElementById('principal-no-quadro')");
        waitFor("etapa do principal", 20, () -> edt(quadros::stepCount) >= 2);
        AutomationStep principal = edt(() -> quadros.steps().get(quadros.stepCount() - 1));
        waitEvidence("fim da observação do principal", principal, EvidenceType.OBSERVATION_COMPLETE);
        check("Cenário 7: aviso escrito no iframe pelo principal NÃO foi atribuído à ação do principal", edt(() ->
                principal.observation().status() == ObservationSummary.Status.NOTHING_OBSERVED));
        // a cobertura vem na fotografia da gravação feita pela extensão: a troca de modo (ida e volta) traz a fotografia atual
        click(edt(() -> fp.modeButton(RecordingMode.CAPTURE_ONLY)));
        waitFor("modo Capturar confirmado", 10, () -> edt(() -> window.studio().recorder().snapshot().mode() == RecordingMode.CAPTURE_ONLY
                && window.studio().recorder().snapshot().pendingMode() == null));
        check("Cenário 7: iframe sem permissão → cobertura parcial declarada", edt(() -> "PARTIAL".equals(window.studio().recorder().snapshot().progress().coverage())));
        click(edt(() -> fp.modeButton(RecordingMode.CAPTURE_AND_INTERACT)));
        waitFor("modo Capturar e interagir", 10, () -> edt(() -> window.studio().recorder().snapshot().mode() == RecordingMode.CAPTURE_AND_INTERACT
                && window.studio().recorder().snapshot().pendingMode() == null));
        selectStep(a, quadros, salvar);
        captureStudio("07-iframe");

        // ================================================================ Cenário 8 — Dados sensíveis
        navigateByUser(BASE + "sensivel.html");
        int c8 = edt(quadros::stepCount);
        clickInPage("document.getElementById('nome')");
        typeText(NAME);
        clickInPage("document.getElementById('codigo')");
        typeText(CODE);
        List<String> buttons = List.of("cpf", "email", "token", "eco", "senha", "longo", "marcacao");
        for (String b : buttons) {
            clickInPage("document.getElementById('" + b + "')");
            sleep(QUIET_MS);
        }
        waitFor("etapas do cenário sensível", 30, () -> edt(quadros::stepCount) == c8 + 2 + buttons.size());
        List<ObservedEvidence> msgs = edt(() -> quadros.steps().subList(c8, quadros.stepCount()).stream().flatMap(st -> st.evidence().stream())
                .filter(e -> e.type() == EvidenceType.VISIBLE_ERROR_MESSAGE).toList());
        List<String> liberadas = msgs.stream().filter(e -> e.message().text() != null).map(e -> e.message().text()).toList();
        check("Cenário 8: 7 mensagens observadas; só 'Senha incorreta.' (sem a marcação HTML) teve o texto liberado",
                msgs.size() == buttons.size() && liberadas.equals(List.of("Senha incorreta.")));
        check("Cenário 8: as outras 6 com o texto omitido por privacidade", msgs.stream()
                .filter(e -> e.limitations().contains(EvidenceVocabulary.Limitation.MESSAGE_SUPPRESSED)).count() == buttons.size() - 1);
        AutomationStep cpfStep = edt(() -> quadros.steps().get(c8 + 2));
        selectStep(a, quadros, cpfStep);
        captureStudio("08-sensivel-omitido");

        // ---- encerra a gravação (resumo com evidências)
        click(edt(fp::finishButton));
        waitState(RecorderState.COMPLETED);
        sleep(800);
        check("S005-039/057 resumo com evidências e negócio não validado", edt(() -> texts(fp)).contains("Evidências observadas")
                && edt(() -> texts(fp)).contains("Resultado de negócio: não validado"));
        captureWindow(floating(), "09-resumo-com-evidencias");
        clickButtonWithText(floating(), "Voltar ao Studio");
        waitState(RecorderState.IDLE);

        // ---- privacidade (domínio, tela, armazenamento da extensão, logs do host)
        String screen = edt(() -> texts(window.frame().getContentPane()));
        String storage = String.valueOf(harness.eval("chrome.storage.session.get(null).then(x => JSON.stringify(x))"));
        String domain = domainText(a) + edt(() -> {
            StringBuilder b = new StringBuilder();
            for (LogicalSession s : a.sessions()) for (AutomationStep st : s.steps()) for (ObservedEvidence e : st.evidence()) b.append(e);
            for (ObservedEvidence e : a.unattributedEvidence()) b.append(e);
            return b.toString();
        });
        String hostLogs = hostLogs();
        for (String secret : List.of(PASSWORD, USER, NAME, CODE, "123.456.789-09", "ana.souza@exemplo.com", "eyJhbGciOiJIUzI1NiJ9", "<b>")) {
            check("S005-047 '" + secret.substring(0, 3) + "…' ausente do domínio, da tela, do armazenamento da extensão e dos logs do host",
                    !domain.contains(secret) && !screen.contains(secret) && !storage.contains(secret) && !hostLogs.contains(secret));
        }
        check("S005-050 nenhum identificador interno na tela", !screen.matches("(?s).*(rec-[0-9a-f]{8}|tgt-|ev-[0-9a-z]|evd-|route-|cap-[0-9a-z]|doc:).*"));

        // ================================================================ Cenário 9 — Editor
        int idx = edt(() -> auth.steps().indexOf(entrar));
        clickRow(auth, idx);
        press(KeyEvent.VK_F2);
        NekkoraDialog d9 = waitDialog("Clicar no botão Entrar");
        StepDetailsForm f9 = edt(() -> find(d9, StepDetailsForm.class, x -> true));
        check("S005-058/062 aba Evidências é a última (abas anteriores nas mesmas posições)", edt(() -> f9.tabs().getTabCount() == 6
                && "Origem e alterações".equals(f9.tabs().getTitleAt(4)) && "Evidências".equals(f9.tabs().getTitleAt(StepDetailsForm.EVIDENCE_TAB))));
        clickTab(f9, StepDetailsForm.EVIDENCE_TAB);
        String tab = edt(() -> texts((Container) f9.tabs().getComponentAt(StepDetailsForm.EVIDENCE_TAB)));
        check("Cenário 9: linha do tempo com horário real, mensagem e negócio não validado", tab.contains("Linha do tempo") && tab.contains("Usuário ou senha inválidos.")
                && tab.contains("Não validado") && tab.matches("(?s).*\\d{2}:\\d{2}:\\d{2},\\d{3}.*"));
        captureWindow(d9, "10-aba-evidencias");
        press(KeyEvent.VK_ESCAPE);
        waitClosed(d9);
        int n0 = edt(() -> entrar.evidence().size());
        edt(() -> {
            window.studio().service().updateStepBasics(a.id(), entrar.id(), "Entrar no sistema", "", com.nekkora.studio.domain.action.ActionType.CLICK);
            return null;
        });
        focusStudioWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_Z);
        waitFor("desfeito", 10, () -> edt(() -> "Clicar no botão Entrar".equals(entrar.name())));
        check("S005-031 desfazer não apagou evidências", edt(() -> entrar.evidence().size()) == n0);
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_Y);
        waitFor("refeito", 10, () -> edt(() -> "Entrar no sistema".equals(entrar.name())));
        check("S005-032 refazer não reaplicou nem duplicou evidências", edt(() -> entrar.evidence().size()) == n0);
        AutomationStep copia = edt(() -> window.studio().service().duplicateStep(a.id(), entrar.id()));
        check("S005-030 duplicada sem evidências (não é interação observada)", edt(() -> copia.evidence().isEmpty()
                && copia.observation().status() == ObservationSummary.Status.NOT_APPLICABLE));
        edt(() -> {
            window.studio().service().moveStep(a.id(), entrar.id(), consulta.id(), 0);
            return null;
        });
        check("S005-029 mover preserva as evidências", edt(() -> entrar.evidence().size()) == n0 && edt(() -> consulta.steps().get(0) == entrar));

        // ================================================================ Cenário 10 — Falhas
        LogicalSession falhas = newSession("Falhas");
        selectSession(a, falhas);
        navigate(BASE + "tardia.html");
        startRecordingOn("Mensagem tardia (STUDIO-005)", RecordingMode.CAPTURE_AND_INTERACT);
        moveFloatingAway();
        clickInPage("document.getElementById('enviar')");
        waitFor("etapa Enviar", 20, () -> edt(falhas::stepCount) == 1);
        AutomationStep enviar10 = edt(() -> falhas.steps().get(0));
        click(edt(fp::pauseResumeButton));
        waitState(RecorderState.PAUSED);
        ObservedEvidence pausa = waitEvidence("observação cortada pela pausa", enviar10, EvidenceType.OBSERVATION_INCOMPLETE);
        check("Cenário 10: pausa corta a janela (INCOMPLETA, WINDOW_CUT_BY_PAUSE)", pausa.limitations().contains(EvidenceVocabulary.Limitation.WINDOW_CUT_BY_PAUSE));
        sleep(1500);
        check("Cenário 10: mensagem que apareceu durante a pausa não virou evidência", edt(() -> enviar10.evidence().stream()
                .noneMatch(e -> e.type() == EvidenceType.VISIBLE_STATUS_MESSAGE)));
        click(edt(fp::pauseResumeButton));
        waitState(RecorderState.RECORDING);
        // desconexão durante a observação e reconexão: nada duplicado
        clickInPage("document.getElementById('enviar')");
        waitFor("segunda etapa", 20, () -> edt(falhas::stepCount) == 2);
        panel.eval("document.getElementById('studio-enabled').click()");
        waitFor("Recorder degradado pela perda do navegador", 30, () -> edt(() -> window.studio().recorder().snapshot().linkLost()));
        panel.eval("document.getElementById('studio-enabled').click()");
        waitFor("reconexão", 60, () -> stateIs(RecorderState.RECORDING));
        AutomationStep enviar2 = edt(() -> falhas.steps().get(1));
        sleep(4000);
        List<String> ids = edt(() -> enviar2.evidence().stream().map(ObservedEvidence::evidenceId).toList());
        check("S005-025 após reconexão nenhuma evidência duplicada", ids.size() == new java.util.HashSet<>(ids).size());
        log("  evidências da etapa depois da reconexão: " + edt(() -> enviar2.evidence().stream().map(e -> e.type().name()).toList()));
        captureStudio("11-falhas");
        int total = edt(a::stepCount);
        click(edt(fp::cancelButton));
        waitState(RecorderState.CANCELLED);
        waitFor("gravação cancelada descartada", 15, () -> edt(falhas::stepCount) == 0);
        check("S005-040 cancelar descarta etapas e evidências desta gravação (as anteriores ficam)", edt(a::stepCount) == total - 2
                && edt(() -> entrar.evidence().size()) == n0);
        clickButtonWithText(floating(), "Voltar ao Studio");
        waitState(RecorderState.IDLE);

        captureStudio("12-automacao-final");
        log("Automação final:");
        for (LogicalSession s : edt(a::sessions)) {
            log("  Sessão " + s.name() + ": " + names(s));
        }
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
        profile = Files.createTempDirectory("nekkora-s005-chrome");
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

    private void createAutomation() throws Exception {
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_N);
        NekkoraDialog d = waitDialog("Nova automação");
        typeIntoFocused("Login com evidências");
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
        File f = new File(out, "S005-" + name + ".png");
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
        try (PrintWriter w = new PrintWriter(new File(out, "S005-homologacao-log.txt"), StandardCharsets.UTF_8)) {
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
