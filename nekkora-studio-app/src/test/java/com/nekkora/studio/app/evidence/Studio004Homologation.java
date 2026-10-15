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
 * Homologação REAL da STUDIO-004 no Windows: Studio de verdade, Chrome for Testing com a extensão, Native Host instalado
 * pelo script oficial e mouse/teclado do sistema operacional (Robot). Primeiro uma gravação real produz a automação
 * (STUDIO-003); depois os cenários 1 a 9 da missão editam essa automação pela interface: propriedades, ordem das
 * sessões, mover etapa, parâmetros (variável e credencial), localizadores, comentários, procedência, desfazer/refazer e
 * o bloqueio durante uma gravação real. Dados digitados fictícios; capturas recortadas às janelas.
 *
 * <p>Uso: {@code java ... Studio004Homologation <pasta-de-saída> <repositório-da-extensão>}
 */
public final class Studio004Homologation {

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

    private Studio004Homologation(File out, Path ext) throws AWTException {
        this.out = out;
        this.ext = ext;
        this.robot = new Robot();
        robot.setAutoDelay(25);
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Studio004Homologation h = new Studio004Homologation(out, Path.of(args[1]).toAbsolutePath().normalize());
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

        // ================================================================ Preparação: automação produzida por gravação real (STUDIO-003)
        focusStudio();
        createAutomation();
        Automation a = edt(() -> window.studio().state().activeAutomation().orElseThrow());
        LogicalSession auth = a.sessions().get(0);
        LogicalSession consulta = a.sessions().get(1);
        edt(() -> {
            window.studio().controller().selectSession(a.id(), auth.id());
            return null;
        });
        startRecordingOn("Login de teste", RecordingMode.CAPTURE_AND_INTERACT);
        FloatingRecorderPanel fp = edt(() -> window.studio().recorderPresenter().panel());
        moveFloatingAway();
        clickInPage("document.getElementById('usuario')");
        typeText(USER);
        clickInPage("document.getElementById('senha')");
        typeText(PASSWORD);
        clickInPage("document.getElementById('entrar')");
        waitPage("consulta.html");
        clickInPage("document.getElementById('menu-clientes')"); // ainda na sessão Autenticação: será movida no cenário 3
        waitFor("4 etapas na Autenticação", 30, () -> edt(auth::stepCount) == 4);
        edt(() -> {
            fp.sessionCombo().setSelectedItem(consulta);
            return null;
        });
        waitFor("sessão Consulta associada", 10, () -> edt(() -> consulta.id().equals(window.studio().recorder().snapshot().session())));
        sleep(600);
        clickInPage("document.getElementById('estado')");
        sleep(400);
        press(KeyEvent.VK_DOWN);
        press(KeyEvent.VK_ENTER);
        clickInPage("document.getElementById('pesquisa')");
        typeText("silva");
        clickInPage("document.getElementById('pesquisar')");
        waitFor("3 etapas na Consulta", 30, () -> edt(consulta::stepCount) == 3);
        click(edt(fp::finishButton));
        waitState(RecorderState.COMPLETED);
        clickButtonWithText(floating(), "Voltar ao Studio");
        waitState(RecorderState.IDLE);
        waitFor("edição liberada depois da gravação", 10, () -> !edt(() -> window.studio().service().isLocked(a.id())));
        log("Automação gravada: Autenticação " + names(auth) + " · Consulta " + names(consulta));
        check("Preparação: automação gravada pela STUDIO-003 (7 etapas RECORDED)", edt(a::stepCount) == 7
                && edt(() -> a.sessions().stream().flatMap(s -> s.steps().stream()).allMatch(s -> s.provenance().origin() == StepOrigin.RECORDED)));
        Map<StepId, AutomationStep> identities = edt(() -> {
            Map<StepId, AutomationStep> m = new LinkedHashMap<>();
            a.sessions().forEach(s -> s.steps().forEach(st -> m.put(st.id(), st)));
            return m;
        });

        // ================================================================ Cenário 1 — Editar automação gravada
        focusStudioWorkspace(); // automação selecionada; foco fora de campos de texto
        press(KeyEvent.VK_F2);
        NekkoraDialog d1 = waitDialog("Propriedades da automação");
        typeIntoFocused("Consulta de clientes PF");
        press(KeyEvent.VK_TAB);
        typeIntoFocused("Automação destinada à consulta de informações cadastrais.");
        captureWindow(d1, "01-editar-automacao");
        clickButtonWithText(d1, "Salvar alterações");
        waitClosed(d1);
        check("Cenário 1: nome e descrição alterados", "Consulta de clientes PF".equals(edt(a::name))
                && "Automação destinada à consulta de informações cadastrais.".equals(edt(a::description)));
        check("Cenário 1: todas as sessões e etapas preservadas (mesmas identidades)", edt(() -> a.sessions().size() == 2 && a.stepCount() == 7
                && identities.entrySet().stream().allMatch(en -> a.step(en.getKey()).orElse(null) == en.getValue())));
        check("Cenário 1: aba e navegação mostram o novo nome", "Consulta de clientes PF".equals(edt(() ->
                window.studio().view().workArea().getTabs().getTitleAt(window.studio().view().workArea().getTabs().indexOfComponent(overview(a))))));
        captureStudio("01-automacao-editada");

        // ================================================================ Cenário 2 — Reorganizar sessões
        selectSession(a, consulta);
        click(inspectorButton("Mover sessão para cima"));
        waitFor("Consulta primeiro", 10, () -> edt(() -> a.sessions().get(0) == consulta));
        check("Cenário 2: ordem Consulta, Autenticação", edt(() -> a.sessions().stream().map(LogicalSession::name).toList())
                .equals(List.of("Consulta", "Autenticação")));
        check("Cenário 2: numeração recalculada (Consulta 01–03, Autenticação 04–07)", lines(a).get(0).startsWith("01")
                && lines(a).get(0).contains("Selecionar") && lines(a).get(3).startsWith("04") && lines(a).get(3).contains("Usuário"));
        captureStudio("02-sessoes-reorganizadas");
        selectSession(a, consulta);
        click(inspectorButton("Mover sessão para baixo"));
        waitFor("ordem original", 10, () -> edt(() -> a.sessions().get(0) == auth));
        check("Cenário 2: ordem original restaurada", edt(() -> a.sessions().stream().map(LogicalSession::name).toList())
                .equals(List.of("Autenticação", "Consulta")) && lines(a).get(0).equals("01   Preencher campo Usuário"));

        // ================================================================ Cenário 3 — Mover "Clicar no link Clientes"
        AutomationStep clientes = edt(() -> auth.steps().get(3));
        check("Cenário 3: etapa gravada na Autenticação", "Clicar no link Clientes".equals(edt(clientes::name)));
        clickRow(auth, 3);
        click(inspectorButton("Mover para…"));
        NekkoraDialog d3 = waitDialog("Mover etapa");
        MoveStepForm mf = edt(() -> find(d3, MoveStepForm.class, x -> true));
        edt(() -> {
            for (int i = 0; i < mf.sessionInput().getItemCount(); i++) {
                if (mf.sessionInput().getItemAt(i).label().endsWith("· Consulta")) mf.sessionInput().setSelectedIndex(i);
            }
            mf.positionInput().setSelectedIndex(0);
            return null;
        });
        captureWindow(d3, "03-mover-etapa");
        clickButtonWithText(d3, "Mover etapa");
        waitClosed(d3);
        check("Cenário 3: Clientes agora é a 1ª etapa da Consulta", edt(() -> consulta.steps().get(0) == clientes));
        check("Cenário 3: mesma identidade e procedência RECORDED", edt(() -> a.step(clientes.id()).orElse(null) == clientes
                && clientes.provenance().origin() == StepOrigin.RECORDED && clientes.provenance().recordingObservation().isPresent()));
        check("Cenário 3: numeração global 01–07 contínua", lines(a).equals(List.of("01   Preencher campo Usuário", "02   Preencher campo Senha",
                "03   Clicar no botão Entrar", "04   Clicar no link Clientes", "05   Selecionar São Paulo no campo Estado", "06   Preencher campo Pesquisa",
                "07   Clicar no botão Pesquisar")));
        captureStudio("03-etapa-movida");

        // ================================================================ Cenário 4 — Parâmetros (variável e credencial)
        AutomationStep usuario = edt(() -> auth.steps().get(0));
        clickRow(auth, 0);
        press(KeyEvent.VK_F2);
        NekkoraDialog d4 = waitDialog("Preencher campo Usuário");
        StepDetailsForm f4 = edt(() -> find(d4, StepDetailsForm.class, x -> true));
        clickTab(f4, 1);
        click(edt(() -> f4.modeInput(StepDetailsForm.ValueMode.VARIABLE)));
        click(edt(f4::valueInput));
        typeIntoFocused("cliente.usuario");
        captureWindow(d4, "04-parametro-variavel");
        clickButtonWithText(d4, "Salvar alterações");
        waitClosed(d4);
        AutomationStep senha = edt(() -> auth.steps().get(1));
        clickRow(auth, 1);
        press(KeyEvent.VK_F2);
        NekkoraDialog d4b = waitDialog("Preencher campo Senha");
        StepDetailsForm f4b = edt(() -> find(d4b, StepDetailsForm.class, x -> true));
        clickTab(f4b, 1);
        check("Cenário 4: valor literal indisponível no campo de senha", !edt(() -> f4b.modeInput(StepDetailsForm.ValueMode.LITERAL).isEnabled()));
        click(edt(() -> f4b.modeInput(StepDetailsForm.ValueMode.CREDENTIAL)));
        click(edt(f4b::valueInput));
        typeIntoFocused("sistema.senha");
        captureWindow(d4b, "04-parametro-credencial");
        clickButtonWithText(d4b, "Salvar alterações");
        waitClosed(d4b);
        check("Cenário 4: Usuário → Variável cliente.usuario", "Variável cliente.usuario".equals(edt(() -> usuario.parameters().value().orElseThrow().display())));
        check("Cenário 4: Senha → Credencial sistema.senha (só o apelido)", "Credencial sistema.senha".equals(edt(() -> senha.parameters().value().orElseThrow().display())));
        String domainText = domainText(a);
        check("Cenário 4: nenhum valor secreto armazenado (senha e usuário digitados na gravação ausentes)",
                !domainText.contains(PASSWORD) && !domainText.contains(USER));
        captureStudio("04-parametros-no-painel");

        // ================================================================ Cenário 5 — Revisar localizadores
        AutomationStep entrar = edt(() -> auth.steps().get(2));
        var captured = edt(() -> entrar.capturedElement().orElseThrow());
        int originalPreferred = edt(captured::preferredLocatorIndex);
        clickRow(auth, 2);
        press(KeyEvent.VK_F2);
        NekkoraDialog d5 = waitDialog("Clicar no botão Entrar");
        StepDetailsForm f5 = edt(() -> find(d5, StepDetailsForm.class, x -> true));
        clickTab(f5, 2);
        int candidates = edt(() -> f5.preferredInputs().size());
        int alternative = edt(() -> {
            for (int i = 0; i < f5.preferredInputs().size(); i++) {
                if (i != originalPreferred && f5.preferredInputs().get(i).isEnabled()) return i;
            }
            return -1;
        });
        log("Localizadores capturados: " + candidates + "; preferencial original: " + originalPreferred + "; alternativa validada: " + alternative);
        check("Cenário 5: candidatos visíveis no editor", candidates > 0);
        check("Cenário 5: candidatos não validados não podem ser escolhidos", edt(() -> {
            for (int i = 0; i < f5.preferredInputs().size(); i++) {
                if (f5.preferredInputs().get(i).isEnabled() != captured.locators().get(i).validated()) return false;
            }
            return true;
        }));
        if (alternative >= 0) click(edt(() -> f5.preferredInputs().get(alternative)));
        captureWindow(d5, "05-localizadores");
        clickButtonWithText(d5, "Salvar alterações");
        waitClosed(d5);
        if (alternative >= 0) {
            check("Cenário 5: localizador validado escolhido como preferencial", edt(() -> entrar.element().orElseThrow().preferredLocatorIndex()) == alternative);
        } else {
            log("Cenário 5: a captura real tem um único candidato validado — a escolha permanece a original");
        }
        check("Cenário 5: ElementReference capturado preservado (mesmo objeto, preferencial original)", edt(() ->
                entrar.capturedElement().orElseThrow() == captured && captured.preferredLocatorIndex() == originalPreferred));

        // ================================================================ Cenário 6 — Comentários
        AutomationStep pesquisar = edt(() -> consulta.steps().get(3));
        clickRow(consulta, 3);
        press(KeyEvent.VK_F2);
        NekkoraDialog d6 = waitDialog("Clicar no botão Pesquisar");
        StepDetailsForm f6 = edt(() -> find(d6, StepDetailsForm.class, x -> true));
        clickTab(f6, 3);
        click(edt(f6::commentInput));
        typeIntoFocused("Aguardar carregamento dos resultados.");
        clickButtonWithText(d6, "Adicionar comentário");
        clickButtonWithText(d6, "Salvar alterações");
        waitClosed(d6);
        java.time.Instant created = edt(() -> pesquisar.comments().get(0).createdAt());
        clickRow(consulta, 3);
        press(KeyEvent.VK_F2);
        NekkoraDialog d6b = waitDialog("Clicar no botão Pesquisar");
        StepDetailsForm f6b = edt(() -> find(d6b, StepDetailsForm.class, x -> true));
        clickTab(f6b, 3);
        clickButtonWithText(d6b, "Editar");
        click(edt(f6b::commentInput));
        typeIntoFocused("Aguardar a tabela de resultados.");
        clickButtonWithText(d6b, "Salvar comentário");
        captureWindow(d6b, "06-comentario");
        clickButtonWithText(d6b, "Salvar alterações");
        waitClosed(d6b);
        check("Cenário 6: comentário editado com a data de criação preservada", edt(() -> pesquisar.comments().size() == 1
                && "Aguardar a tabela de resultados.".equals(pesquisar.comments().get(0).text()) && created.equals(pesquisar.comments().get(0).createdAt())));
        clickRow(consulta, 3);
        press(KeyEvent.VK_ALT, KeyEvent.VK_UP); // mover pelo teclado com o foco na lista
        waitFor("Pesquisar subiu", 10, () -> edt(() -> consulta.steps().get(2) == pesquisar));
        check("Cenário 6: o comentário acompanha a etapa movida", edt(() -> "Aguardar a tabela de resultados.".equals(pesquisar.comments().get(0).text())));
        press(KeyEvent.VK_ALT, KeyEvent.VK_DOWN);
        waitFor("Pesquisar de volta", 10, () -> edt(() -> consulta.steps().get(3) == pesquisar));

        // ================================================================ Cenário 9 — Procedência (antes do 7, que desfaz edições)
        clickRow(auth, 2);
        press(KeyEvent.VK_F2);
        NekkoraDialog d9 = waitDialog("Clicar no botão Entrar");
        typeIntoFocused("Confirmar autenticação");
        clickButtonWithText(d9, "Salvar alterações");
        waitClosed(d9);
        String inspector = edt(() -> texts(window.studio().view().inspector()));
        check("Cenário 9: continua RECORDED e identificada como alterada", edt(() -> entrar.provenance().origin() == StepOrigin.RECORDED
                && entrar.manualRevision().isPresent()) && inspector.contains("Alterada manualmente"));
        check("Cenário 9: original × atual na interface", inspector.contains("Nome original") && inspector.contains("Clicar no botão Entrar")
                && inspector.contains("Nome atual") && inspector.contains("Confirmar autenticação"));
        check("Cenário 9: resultado de negócio continua não validado", inspector.contains("Não validado") && edt(() ->
                entrar.provenance().recordingObservation().orElseThrow().businessOutcome() == RecordingObservation.BusinessOutcome.NOT_VALIDATED));
        clickRow(auth, 2);
        press(KeyEvent.VK_F2);
        NekkoraDialog d9b = waitDialog("Confirmar autenticação");
        StepDetailsForm f9 = edt(() -> find(d9b, StepDetailsForm.class, x -> true));
        clickTab(f9, 4);
        captureWindow(d9b, "09-origem-e-alteracoes");
        clickButtonWithText(d9b, "Cancelar");
        waitClosed(d9b);
        captureStudio("09-procedencia-no-painel");

        // ================================================================ Cenário 7 — Desfazer / refazer
        int steps = edt(a::stepCount);
        AutomationStep estado = edt(() -> consulta.steps().get(1));
        clickRow(consulta, 1);
        press(KeyEvent.VK_F2);
        NekkoraDialog d7 = waitDialog("Selecionar São Paulo no campo Estado");
        typeIntoFocused("Selecionar Estado");
        clickButtonWithText(d7, "Salvar alterações");
        waitClosed(d7);
        clickRow(consulta, 1);
        press(KeyEvent.VK_ALT, KeyEvent.VK_DOWN);
        waitFor("Estado desceu", 10, () -> edt(() -> consulta.steps().get(2) == estado));
        clickRow(consulta, 2);
        press(KeyEvent.VK_DELETE);
        NekkoraDialog d7c = waitDialog("Excluir etapa?");
        captureWindow(d7c, "07-confirmar-exclusao");
        clickButtonWithText(d7c, "Excluir");
        waitClosed(d7c);
        check("Cenário 7: renomeada, movida e excluída", edt(a::stepCount) == steps - 1 && edt(() -> a.step(estado.id()).isEmpty()));
        focusStudioWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_Z);
        waitFor("exclusão desfeita", 10, () -> edt(() -> a.step(estado.id()).orElse(null) == estado));
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_Z);
        waitFor("movimentação desfeita", 10, () -> edt(() -> consulta.steps().get(1) == estado));
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_Z);
        waitFor("renomeação desfeita", 10, () -> "Selecionar São Paulo no campo Estado".equals(edt(estado::name)));
        check("Cenário 7: Ctrl+Z ×3 volta ao estado anterior com a mesma etapa", edt(a::stepCount) == steps);
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_Y);
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_Y);
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_Y);
        waitFor("refeito", 10, () -> edt(() -> a.step(estado.id()).isEmpty()));
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_Z);
        waitFor("exclusão desfeita de novo", 10, () -> edt(() -> a.step(estado.id()).orElse(null) == estado));
        check("Cenário 7: nenhuma entidade perdida ou duplicada", edt(() -> {
            List<StepId> ids = a.sessions().stream().flatMap(s -> s.steps().stream()).map(AutomationStep::id).toList();
            return ids.size() == steps && new java.util.HashSet<>(ids).size() == steps
                    && identities.entrySet().stream().allMatch(en -> a.step(en.getKey()).orElse(null) == en.getValue());
        }));
        captureStudio("07-desfazer-refazer");

        // ================================================================ Cenário 8 — Proteção durante a gravação
        edt(() -> {
            window.studio().controller().selectSession(a.id(), consulta.id());
            return null;
        });
        cdp.activate(labTarget);
        navigate(BASE + "consulta.html");
        startRecordingOn("Consulta de clientes", RecordingMode.CAPTURE_AND_INTERACT);
        edt(() -> {
            floating().setLocation(420, 640); // abaixo das listas de etapas: os cliques reais nas linhas não caem no Recorder
            return null;
        });
        sleep(400);
        check("Cenário 8: automação bloqueada enquanto grava", edt(() -> window.studio().service().isLocked(a.id())));
        int beforeLock = edt(a::stepCount);
        clickRow(auth, 0);
        press(KeyEvent.VK_DELETE);
        sleep(600);
        check("Cenário 8: Delete não exclui e explica o motivo", edt(a::stepCount) == beforeLock
                && "Edição indisponível durante a gravação".equals(edt(() -> window.studio().state().lastNotice().map(n -> n.title()).orElse(""))));
        clickRow(auth, 0);
        press(KeyEvent.VK_ALT, KeyEvent.VK_DOWN);
        sleep(400);
        check("Cenário 8: Alt+↓ não move", edt(() -> auth.steps().get(0) == usuario));
        check("Cenário 8: aviso de bloqueio na automação", edt(() -> overview(a).lockBanner().isShowing()));
        captureStudio("08-bloqueio-durante-gravacao");
        clickInPage("document.getElementById('pesquisar')");
        waitFor("etapa gravada durante o bloqueio", 30, () -> edt(a::stepCount) == beforeLock + 1);
        check("Cenário 8: o Recorder continua registrando", edt(() -> consulta.steps().get(consulta.stepCount() - 1).provenance().origin() == StepOrigin.RECORDED));
        click(edt(fp::finishButton));
        waitState(RecorderState.COMPLETED);
        clickButtonWithText(floating(), "Voltar ao Studio");
        waitState(RecorderState.IDLE);
        waitFor("edição liberada", 10, () -> !edt(() -> window.studio().service().isLocked(a.id())));
        clickRow(auth, 0);
        press(KeyEvent.VK_ALT, KeyEvent.VK_DOWN);
        waitFor("Alt+↓ volta a funcionar", 10, () -> edt(() -> auth.steps().get(1) == usuario));
        check("Cenário 8: depois de finalizar, a edição volta a ficar disponível", edt(() -> !overview(a).lockBanner().isShowing()));
        press(KeyEvent.VK_ALT, KeyEvent.VK_UP);

        // ================================================================ privacidade e temas
        String screen = edt(() -> texts(window.frame().getContentPane()));
        check("S004-053 nenhum identificador interno na tela", !screen.matches("(?s).*(rec-[0-9a-f]{8}|tgt-|ev-[0-9]|route-|cap-[0-9a-z]|doc:).*"));
        String all = domainText(a) + screen;
        check("S004-053 usuário e senha digitados na gravação ausentes do domínio e da tela", !all.contains(PASSWORD) && !all.contains(USER));
        clickRow(auth, 2);
        for (NekkoraTheme t : NekkoraThemes.all()) {
            edt(() -> {
                ThemeManager.setTheme(t);
                return null;
            });
            sleep(700);
            captureStudio("10-tema-" + t.id());
        }
        edt(() -> {
            ThemeManager.setTheme(NekkoraThemes.SAKURA_DARK);
            return null;
        });
        log("Automação final:");
        for (LogicalSession s : edt(a::sessions)) {
            log("  Sessão " + s.name() + ": " + names(s));
        }
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
        profile = Files.createTempDirectory("nekkora-s004-chrome");
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
        File f = new File(out, "S004-" + name + ".png");
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
        try (PrintWriter w = new PrintWriter(new File(out, "S004-homologacao-log.txt"), StandardCharsets.UTF_8)) {
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
