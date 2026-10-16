package com.nekkora.studio.app.evidence;

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
 * Homologação REAL da STUDIO-006 no Windows: Studio de verdade (diretório de dados isolado por
 * {@code NEKKORA_STUDIO_DATA_DIR}, Bridge isolado por {@code NEKKORA_BRIDGE_DIR}), Chrome for Testing com a extensão da
 * main, Native Host instalado pelo script oficial, página FICTÍCIA do laboratório e mouse/teclado do sistema
 * operacional (Robot). Diálogos de arquivo, menu Arquivo e atalhos usados como o usuário. Cenários: (1) gravar e
 * salvar, (2) editar, salvar, fechar e reabrir, (3) evidências NOT_VALIDATED preservadas, (4) falhas de salvamento e
 * encerramento com alterações, (5) recuperação depois de encerramento inesperado (processo morto) — em processos
 * filhos {@link Studio006RecoveryProbe} — e (6) compatibilidade com a nekkora-privacy/2.
 *
 * <p>Uso: {@code java ... Studio006Homologation <pasta-de-saída> <repositório-da-extensão> <classpath-do-studio>}
 */
public final class Studio006Homologation {

    private static final int LAB_PORT = 18765;
    private static final String BASE = "http://127.0.0.1:" + LAB_PORT + "/lab/studio005/";
    /** Valores digitados na página (fictícios): NUNCA podem aparecer no arquivo do projeto, na recuperação, nos logs ou na tela. */
    private static final String USER = "anateste006";
    private static final String PASSWORD = "Senha2026Proj";
    private static final String NAME = "Login homologacao 006";
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

    private Studio006Homologation(File out, Path ext, String classpath) throws AWTException {
        this.out = out;
        this.ext = ext;
        this.classpath = classpath;
        this.robot = new Robot();
        robot.setAutoDelay(25);
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Studio006Homologation h = new Studio006Homologation(out, Path.of(args[1]).toAbsolutePath().normalize(), args[2]);
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
        check("S006-061 diretório de dados do Studio = NEKKORA_STUDIO_DATA_DIR (isolado da instalação pessoal)",
                edt(() -> window.studio().projects().recovery().orElseThrow().directory().startsWith(dataDir)));
        check("S006-061 recuperação automática começa DESATIVADA (sem consentimento)", !edt(() -> window.studio().projects().settings().recoveryEnabled())
                && edt(() -> texts(window.frame().getContentPane())).contains("Recuperação desativada"));
        launchChrome(BASE + "login-erro.html");
        connect();

        // ================================================================ Cenário 1 — Gravar e salvar
        focusStudio();
        createAutomation(NAME, "Autenticação");
        Automation a = edt(() -> window.studio().state().activeAutomation().orElseThrow());
        LogicalSession auth = a.sessions().get(0);
        check("S006-002 novo projeto = 'Alterações não salvas — sem arquivo' e aba com *", projectStatus().equals("Alterações não salvas — sem arquivo")
                && tabTitle(a).endsWith(" *"));
        selectSession(a, auth);
        startRecordingOn("127.0.0.1", RecordingMode.CAPTURE_AND_INTERACT);
        FloatingRecorderPanel fp = edt(() -> window.studio().recorderPresenter().panel());
        moveFloatingAway();
        clickInPage("document.getElementById('usuario')");
        typeText(USER);
        clickInPage("document.getElementById('senha')");
        typeText(PASSWORD);
        clickInPage("document.getElementById('entrar')");
        waitFor("3 etapas gravadas", 30, () -> edt(auth::stepCount) == 3);
        AutomationStep entrar = edt(() -> auth.steps().get(2));
        waitFor("fim da observação do Entrar", 25, () -> edt(() -> entrar.evidence().stream().anyMatch(e -> e.type() == EvidenceType.OBSERVATION_COMPLETE)));
        Path file = projects.resolve("login-006.nekkora");
        focusStudioWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_S);
        waitFor("aviso de gravação em andamento", 10, () -> edt(() -> window.studio().state().lastNotice().map(n -> n.message()).orElse(""))
                .equals(ProjectService.RECORDING_MESSAGE));
        check("S006-060 Ctrl+S durante a gravação: mensagem exata da missão; nenhum diálogo, nenhum arquivo", !Files.exists(file) && fileChooser() == null);
        check("S006-060 salvar NÃO finalizou nem cancelou a gravação", stateIs(RecorderState.RECORDING));
        captureStudio("01-salvar-durante-gravacao");
        click(edt(fp::finishButton));
        waitState(RecorderState.COMPLETED);
        waitFor("quiescência (etapas e evidências confirmadas)", 30, () -> !edt(() -> RecorderPresenter.busyForSave(window.studio().recorder().snapshot(), a.id())));
        AbstractButton back = edt(() -> find(floating(), AbstractButton.class, x -> "Voltar ao Studio".equals(x.getText()) && x.isShowing()));
        if (back != null) click(back);
        waitFor("Recorder livre", 20, () -> stateIs(RecorderState.IDLE) || stateIs(RecorderState.COMPLETED));
        List<String> idsBefore = edt(() -> auth.steps().stream().map(s -> s.id().value().toString()).toList());
        List<String> evBefore = edt(() -> entrar.evidence().stream().map(ObservedEvidence::evidenceId).toList());
        focusStudioWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_S);
        Window chooser = waitChooser("Salvar projeto como");
        captureWindow(chooser, "02-salvar-como");
        typePath(file);
        waitFor("projeto salvo", 30, () -> Files.exists(file) && edt(() -> window.studio().state().project(a.id()).map(p -> !p.dirty()).orElse(false)));
        check("S006-039 Salvar como pelo diálogo do sistema: arquivo .nekkora criado, estado 'Salvo — login-006.nekkora', aba sem *",
                projectStatus().equals("Salvo — login-006.nekkora") && tabTitle(a).equals(NAME));
        long snapMs = edt(() -> window.studio().projects().lastSnapshotNanos()) / 1_000_000;
        log("  fotografia na EDT: " + snapMs + " ms (o resto do salvamento roda fora da interface)");
        check("S006-059 tempo da fotografia na EDT < 100 ms", snapMs < 100);
        Map<String, byte[]> entries = entries(file);
        check("S006-038 pacote só com as entradas do formato (manifesto, metadados, automação, sessões, etapas, evidências)",
                entries.keySet().stream().allMatch(n -> n.matches("(manifest|metadata|automation|sessions)\\.json|steps/[0-9a-f-]{36}\\.json|evidence/([0-9a-f-]{36}|unattributed)\\.json"))
                && entries.keySet().stream().filter(n -> n.startsWith("steps/")).count() == 3);
        String all = joined(entries);
        check("S006-073 valores digitados na página ausentes do arquivo (usuário e senha)", !all.contains(USER) && !all.contains(PASSWORD));
        check("S006-071 sem executáveis, scripts, imagens, PageMaps ou recursos externos no arquivo", !all.contains("<script") && !all.contains("data:image")
                && !all.toLowerCase().contains("pagemap") && entries.values().stream().noneMatch(b -> b.length > 3 && b[0] == (byte) 0x89 && b[1] == 'P'));
        Map<String, Object> manifest = Json.parseObject(new String(entries.get("manifest.json"), StandardCharsets.UTF_8));
        Map<String, Object> metadata = Json.parseObject(new String(entries.get("metadata.json"), StandardCharsets.UTF_8));
        check("S006-071 manifesto: formato 1.0.0, PROJECT, nekkora-privacy/2; metadados: não criptografado, com aviso",
                "1.0.0".equals(manifest.get("formatVersion")) && "PROJECT".equals(manifest.get("kind")) && "nekkora-privacy/2".equals(manifest.get("privacyPolicy"))
                && Boolean.FALSE.equals(metadata.get("encrypted")) && String.valueOf(metadata.get("notice")).contains("não criptografado"));
        ProjectFiles.Loaded independent = new ProjectFiles(new ProjectRepository(), "homologacao").load(file, Clock.systemUTC());
        LogicalSession iAuth = independent.automation().sessions().get(0);
        check("S006-010/014/028 leitura independente: mesmas identidades, etapas GRAVADAS, mesmas evidências",
                iAuth.steps().stream().map(s -> s.id().value().toString()).toList().equals(idsBefore)
                && iAuth.steps().stream().allMatch(s -> s.provenance().origin() == StepOrigin.RECORDED)
                && iAuth.steps().get(2).evidence().stream().map(ObservedEvidence::evidenceId).toList().equals(evBefore));
        check("S006-065 Projetos recentes registra o arquivo", edt(() -> window.studio().projects().recents().find(file).isPresent()));
        captureStudio("03-salvo");

        // ================================================================ Cenário 2 — Editar, salvar, fechar e reabrir
        AutomationStep usuario = edt(() -> auth.steps().get(0));
        edt(() -> {
            window.studio().service().updateStepBasics(a.id(), entrar.id(), "Entrar no sistema", "Botão de acesso", ActionType.CLICK);
            window.studio().service().addComment(a.id(), entrar.id(), "Revisar a mensagem de erro com a equipe");
            window.studio().service().setStepParameters(a.id(), usuario.id(), com.nekkora.studio.domain.action.ActionParameters.withValue(
                    new com.nekkora.studio.domain.action.ValueSource.VariableReference("cliente.usuario")));
            window.studio().service().addManualLocator(a.id(), entrar.id(), new com.nekkora.studio.domain.element.ManualLocator(
                    com.nekkora.studio.domain.element.ManualLocator.Syntax.CSS, "form button.entrar", "proposto na homologação", Instant.now()));
            return null;
        });
        waitFor("aba com *", 5, () -> tabTitle(a).endsWith(" *"));
        check("S006-055 edição marca a automação: aba com * e 'Alterações não salvas — login-006.nekkora'",
                projectStatus().equals("Alterações não salvas — login-006.nekkora"));
        captureStudio("04-alteracoes-nao-salvas");
        focusStudioWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_Z);
        waitFor("desfeito (localizador manual, a última edição)", 10, () -> edt(() -> entrar.manualLocators().isEmpty()));
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_Y);
        waitFor("refeito (localizador manual)", 10, () -> edt(() -> entrar.manualLocators().size() == 1));
        check("S006-036 Ctrl+Z / Ctrl+Y reais desfazem e refazem a última edição", edt(() -> entrar.comments().size() == 1));
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_S);
        waitFor("salvo de novo", 30, () -> edt(() -> window.studio().state().project(a.id()).map(p -> !p.dirty()).orElse(false)));
        check("S006-038 Ctrl+S com arquivo associado: salva sem perguntar; aba sem *", fileChooser() == null && tabTitle(a).equals(NAME)
                && projectStatus().equals("Salvo — login-006.nekkora"));
        String shaSaved = sha(file);
        menu("Arquivo", "Fechar projeto");
        waitFor("projeto fechado", 10, () -> edt(() -> window.studio().state().catalog().find(a.id()).isEmpty()));
        check("Cenário 2: Fechar projeto salvo — sem perguntas, retirado do Studio", projectStatus().equals("Nenhum projeto aberto"));
        menu("Arquivo", "Projetos recentes", "login-006.nekkora");
        waitFor("projeto reaberto", 30, () -> edt(() -> window.studio().state().activeAutomation().isPresent()));
        Automation r = edt(() -> window.studio().state().activeAutomation().orElseThrow());
        LogicalSession rAuth = edt(() -> r.sessions().get(0));
        AutomationStep rEntrar = edt(() -> rAuth.steps().get(2));
        check("S006-008/010/032/065 reaberto por Projetos recentes: mesma identidade da automação e das etapas (nenhum ID novo)", r.id().equals(a.id())
                && edt(() -> rAuth.steps().stream().map(s -> s.id().value().toString()).toList()).equals(idsBefore));
        check("S006-015/024/025 edições preservadas, original intacto (nome capturado 'Clicar no botão Entrar')", edt(() -> "Entrar no sistema".equals(rEntrar.name())
                && "Clicar no botão Entrar".equals(rEntrar.baseline().orElseThrow().name()) && rEntrar.comments().size() == 1));
        check("S006-018 parâmetro alterado preservado (variável cliente.usuario), sem valor digitado", edt(() -> rAuth.steps().get(0).parameters().value()
                .map(v -> v instanceof com.nekkora.studio.domain.action.ValueSource.VariableReference vr && vr.name().equals("cliente.usuario")).orElse(false)));
        check("S006-023 localizador manual preservado e ainda NÃO VERIFICADO", edt(() -> rEntrar.manualLocators().size() == 1
                && rEntrar.manualLocators().get(0).verification() == com.nekkora.studio.domain.element.ManualLocator.Verification.NOT_VERIFIED
                && rEntrar.manualLocators().get(0).expression().equals("form button.entrar")));
        check("S006-035 histórico de desfazer começa vazio no projeto aberto", !edt(() -> window.studio().service().canUndo(r.id())));
        check("S006-080 abrir não altera o arquivo (mesmo SHA-256) e não executa nada (Recorder livre)", sha(file).equals(shaSaved)
                && (stateIs(RecorderState.IDLE) || stateIs(RecorderState.COMPLETED)));
        captureStudio("05-reaberto");

        // ================================================================ Cenário 3 — Evidências NOT_VALIDATED preservadas
        check("S006-027/028/029 mesmas evidências (identidades e ordem) depois de salvar e reabrir",
                edt(() -> rEntrar.evidence().stream().map(ObservedEvidence::evidenceId).toList()).equals(evBefore));
        check("S006-031 resultado de negócio continua NOT_VALIDATED; texto da página continua ausente (SUPPRESSED/NONE, sem texto)", edt(() ->
                rEntrar.observation().businessOutcome() == RecordingObservation.BusinessOutcome.NOT_VALIDATED
                && rEntrar.evidence().stream().allMatch(e -> e.message().text() == null)));
        check("S006-033 evidências não se multiplicam: duplicar não copia evidências", edt(() -> {
            AutomationStep copy = window.studio().service().duplicateStep(r.id(), rEntrar.id());
            boolean ok = copy.evidence().isEmpty();
            window.studio().service().undo(r.id());
            return ok;
        }));
        selectStep(r, rAuth, rEntrar);
        String insp = edt(() -> texts(window.studio().view().inspector()));
        check("Cenário 3: painel mostra as evidências e 'Não validado' sem o texto da página", insp.contains("Não validado") && !insp.contains("inválidos"));
        captureStudio("06-evidencias-reabertas");

        // ================================================================ Cenário 6 — Compatibilidade com a nekkora-privacy/2
        check("S006-071 elemento capturado declara a política do produtor conhecido (nekkora-privacy/2) depois de reaberto",
                edt(() -> rAuth.steps().stream().allMatch(s -> s.capturedElement().flatMap(e -> e.privacyPolicy()).map(DisplaySafety.KNOWN_PRODUCER_POLICY::equals)
                        .orElse(false))));
        Path v1 = tamper(file, projects.resolve("politica-v1.nekkora"), (name, doc) -> {
            if (name.equals("manifest.json")) doc.put("privacyPolicy", "nekkora-privacy/1");
        });
        Path leak = tamper(file, projects.resolve("dado-da-pagina.nekkora"), (name, doc) -> {
            if (name.startsWith("steps/") && doc.get("original") instanceof Map<?, ?> o && o.get("capturedElement") instanceof Map<?, ?> el) {
                @SuppressWarnings("unchecked")
                Map<String, Object> e = (Map<String, Object>) el;
                e.put("accessibleName", "Consultar cliente Bartolomeu Quixadá");
            }
        });
        Path script = projects.resolve("com-script.nekkora");
        Map<String, byte[]> withScript = new LinkedHashMap<>(entries(file));
        withScript.put("scripts/executar.js", "fetch('https://exemplo.invalid/')".getBytes(StandardCharsets.UTF_8));
        Files.write(script, zip(withScript));
        int catalog = edt(() -> window.studio().state().catalog().all().size());
        for (Object[] c : new Object[][] {{v1, "política de privacidade"}, {leak, "nekkora-privacy/2"}, {script, "entrada não permitida"}}) {
            Path p = (Path) c[0];
            focusStudioWorkspace();
            press(KeyEvent.VK_CONTROL, KeyEvent.VK_O);
            waitChooser("Abrir projeto");
            typePath(p);
            waitFor("recusa de " + p.getFileName(), 20, () -> edt(() -> window.studio().state().lastNotice().map(n -> n.title() + " " + n.message()).orElse(""))
                    .contains((String) c[1]));
            check("S006-052/071/072 " + p.getFileName() + " recusado ('" + c[1] + "'), nada carregado", edt(() -> window.studio().state().catalog().all().size()) == catalog);
        }
        captureStudio("07-recusa-privacidade");

        // ================================================================ Cenário 4 — Falhas de salvamento
        edt(() -> {
            window.studio().service().updateStepBasics(r.id(), rEntrar.id(), "Entrar no sistema", "Botão de acesso (revisado)", ActionType.CLICK);
            return null;
        });
        String before4 = sha(file);
        Files.setAttribute(file, "dos:readonly", true);
        focusStudioWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_S);
        waitFor("erro ao salvar (somente leitura)", 30, () -> projectStatus().startsWith("Erro ao salvar"));
        String notice = edt(() -> window.studio().state().lastNotice().map(n -> n.message()).orElse(""));
        log("  aviso: " + notice);
        check("S006-042 arquivo somente leitura: erro claro, arquivo anterior preservado (mesmo SHA-256), alterações continuam abertas",
                sha(file).equals(before4) && notice.contains("preservado") && edt(() -> window.studio().state().project(r.id()).orElseThrow().dirty()));
        check("S006-042 nenhum temporário deixado na pasta", leftovers().isEmpty());
        captureStudio("08-erro-somente-leitura");
        Files.setAttribute(file, "dos:readonly", false);
        Process lock = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", "$f=[System.IO.File]::Open('" + file + "','Open','Read','None'); "
                + "Write-Output LOCKED; Start-Sleep -Seconds 300").redirectErrorStream(true).start();
        String locked = new String(lock.getInputStream().readNBytes(6), StandardCharsets.UTF_8);
        log("  outro programa com o arquivo aberto (FileShare.None): " + locked);
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_S);
        waitFor("erro ao salvar (em uso)", 30, () -> projectStatus().startsWith("Erro ao salvar") && !edt(() -> window.studio().state().project(r.id()).orElseThrow().saving()));
        notice = edt(() -> window.studio().state().lastNotice().map(n -> n.message()).orElse(""));
        log("  aviso: " + notice);
        lock.destroyForcibly().waitFor();
        check("S006-042 arquivo em uso por outro programa: erro, arquivo anterior preservado, alterações abertas", sha(file).equals(before4)
                && edt(() -> window.studio().state().project(r.id()).orElseThrow().dirty()) && leftovers().isEmpty());
        // alteração externa (outro programa grava uma versão diferente do projeto)
        ProjectFiles other = new ProjectFiles(new ProjectRepository(), "outro-programa");
        ProjectFiles.Loaded ext0 = other.load(file, Clock.systemUTC());
        other.save(file, ProjectFiles.Snapshot.of(ext0.automation(), ext0.manifest().projectId()), ext0.fingerprint(), true, Instant.now(), Clock.systemUTC());
        String external = sha(file);
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_S);
        NekkoraDialog conflict = waitDialog("Arquivo alterado fora do Studio");
        captureWindow(conflict, "09-conflito");
        check("S006-041 conflito detectado antes de sobrescrever: o arquivo externo continua intacto", sha(file).equals(external));
        clickButtonWithText(conflict, "Salvar com outro nome");
        waitClosed(conflict);
        waitChooser("Salvar projeto como");
        Path copy = projects.resolve("login-006-revisado.nekkora");
        typePath(copy);
        waitFor("salvo com outro nome", 30, () -> Files.exists(copy) && edt(() -> !window.studio().state().project(r.id()).orElseThrow().dirty()));
        check("S006-041/039 'Salvar com outro nome' preserva as duas versões; o projeto passa a usar o novo arquivo", sha(file).equals(external)
                && projectStatus().equals("Salvo — login-006-revisado.nekkora"));

        // encerramento com alterações: Cancelar, Salvar todos com falha (não fecha), Salvar todos com sucesso (fecha)
        edt(() -> {
            window.studio().service().addComment(r.id(), rEntrar.id(), "Segunda revisão");
            return null;
        });
        menu("Arquivo", "Sair");
        NekkoraDialog exit1 = waitDialog("Sair do Nekkora Studio?");
        check("S006-068 Sair com alterações: [Salvar todos] [Descartar alterações] [Cancelar], com aviso de arquivo não criptografado",
                edt(() -> texts(exit1)).contains("Salvar todos") && edt(() -> texts(exit1)).contains("Descartar alterações") && edt(() -> texts(exit1)).contains("Cancelar")
                && edt(() -> texts(exit1)).contains("não criptografados"));
        captureWindow(exit1, "10-sair-com-alteracoes");
        clickButtonWithText(exit1, "Cancelar");
        waitClosed(exit1);
        check("S006-070 Cancelar: o Studio continua aberto com as alterações", !exited && edt(() -> window.frame().isShowing()));
        Files.setAttribute(copy, "dos:readonly", true);
        menu("Arquivo", "Sair");
        NekkoraDialog exit2 = waitDialog("Sair do Nekkora Studio?");
        clickButtonWithText(exit2, "Salvar todos");
        waitClosed(exit2);
        waitFor("aviso 'O Studio continua aberto'", 30, () -> edt(() -> window.studio().state().lastNotice().map(n -> n.title()).orElse(""))
                .equals("O Studio continua aberto"));
        check("S006-069 Salvar todos com falha: o Studio NÃO encerra", !exited && edt(() -> window.frame().isShowing()));
        String toasts = edt(() -> texts(window.frame().getLayeredPane()));
        check("Revisão 1: quando o Salvar todos falha, os avisos mostram 'O Studio continua aberto' e nenhum 'Encerrando o Studio'",
                toasts.contains("O Studio continua aberto") && !toasts.contains("Encerrando o Studio"));
        captureStudio("11-sair-falha-nao-encerra");
        Files.setAttribute(copy, "dos:readonly", false);
        menu("Arquivo", "Sair");
        NekkoraDialog exit3 = waitDialog("Sair do Nekkora Studio?");
        clickButtonWithText(exit3, "Salvar todos");
        waitFor("Studio encerrado", 30, () -> exited);
        ProjectFiles.Loaded finalCopy = new ProjectFiles(new ProjectRepository(), "homologacao").load(copy, Clock.systemUTC());
        check("S006-069 Salvar todos com sucesso: salvo e encerrado", finalCopy.automation().step(rEntrar.id()).orElseThrow().comments().size() == 2);

        // logs do Studio: sem conteúdo do projeto e sem caminhos
        String logs = String.join("\n", logRecords);
        Files.writeString(out.toPath().resolve("S006-logs-do-studio.txt"), logs);
        check("S006-074 logs do Studio registram as operações de projeto", logs.contains("projeto operacao=salvar resultado=ok")
                && logs.contains("projeto operacao=abrir resultado=erro"));
        check("S006-074 logs sem valores digitados, nomes do projeto, comentários ou caminhos", !logs.contains(USER) && !logs.contains(PASSWORD)
                && !logs.contains(NAME) && !logs.contains("Revisar a mensagem") && !logs.contains("login-006") && !logs.contains(projects.toString())
                && !logs.contains("Bartolomeu"));

        // ================================================================ Cenário 5 — Recuperação depois de encerramento inesperado
        recoveryScenario(copy);
    }

    // ============================================================================== cenário 5 (processos filhos)

    private void recoveryScenario(Path source) throws Exception {
        Path project = projects.resolve("recuperacao.nekkora");
        Files.copy(source, project, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Path data = out.toPath().resolve("data-recuperacao").toAbsolutePath();
        String before = sha(project);
        Process crash = probe("crash", data, project);
        String ready = waitLine(crash, "READY", 180);
        check("S006-061 Studio (processo filho) abriu o projeto, consentiu a recuperação pelo diálogo e gravou a fotografia pelo temporizador", ready != null);
        crash.destroyForcibly(); // encerramento inesperado: TerminateProcess, sem ganchos de saída
        crash.waitFor();
        log("  processo do Studio encerrado à força (exit " + crash.exitValue() + ")");
        check("S006-063 o arquivo do projeto NÃO foi alterado pela recuperação nem pela queda (mesmo SHA-256)", sha(project).equals(before));
        List<Path> snaps;
        try (Stream<Path> s = Files.walk(data.resolve("recovery"))) {
            snaps = s.filter(p -> p.toString().endsWith(".nekkora")).toList();
        }
        check("S006-061 fotografia de recuperação no diretório próprio (" + snaps.size() + ")", snaps.size() == 1);
        String snapText = joined(entries(snaps.get(0)));
        check("S006-073 fotografia de recuperação sem valores digitados", !snapText.contains(USER) && !snapText.contains(PASSWORD));
        Process restart = probe("restart", data, project);
        String done = waitLine(restart, "DONE", 240);
        restart.waitFor();
        check("S006-062 reinício concluído (oferta, recuperação, salvamento)", done != null && restart.exitValue() == 0);
        ProjectFiles.Loaded after = new ProjectFiles(new ProjectRepository(), "homologacao").load(project, Clock.systemUTC());
        check("S006-062 depois de salvar o recuperado, o arquivo tem a alteração feita antes da queda", after.automation().sessions().get(0).steps().stream()
                .anyMatch(s -> s.name().equals(Studio006RecoveryProbe.RECOVERED_NAME)));
        try (Stream<Path> s = Files.walk(data.resolve("recovery"))) {
            check("S006-064 fotografias superadas pelo salvamento confirmado foram apagadas", s.noneMatch(p -> p.toString().endsWith(".nekkora")));
        }
        exitScenarios(data, project);
    }

    /**
     * Revisão 1 do PR #8 (P1-B), com processos REAIS que terminam por {@code System.exit}: Sair → Descartar alterações;
     * salvar e sair logo em seguida; e um novo processo no mesmo diretório de dados, que não pode oferecer nada.
     */
    private void exitScenarios(Path data, Path project) throws Exception {
        log("Revisão 1 (P1-B) — encerramento real com fotografias de recuperação");
        String before = sha(project);
        Process discard = probe("exit-discard", data, project);
        String exited = waitLine(discard, "EXIT", 240);
        boolean ended = discard.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
        check("P1-B cenário A: Sair → Descartar alterações encerrou o processo do Studio (System.exit, código 0)", exited != null && ended && discard.exitValue() == 0);
        check("P1-B cenário A: descartar não alterou o arquivo do projeto (mesmo SHA-256)", sha(project).equals(before));
        check("P1-B cenário A: nenhuma fotografia no diretório de dados depois do encerramento", recoveryFiles(data).isEmpty());
        Process save = probe("exit-save", data, project);
        exited = waitLine(save, "EXIT", 240);
        ended = save.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
        check("P1-B cenário B: salvar e sair encerrou o processo do Studio (System.exit, código 0)", exited != null && ended && save.exitValue() == 0);
        ProjectFiles.Loaded saved = new ProjectFiles(new ProjectRepository(), "homologacao").load(project, Clock.systemUTC());
        check("P1-B cenário B: o arquivo tem a alteração salva antes de sair", saved.automation().sessions().get(0).steps().stream()
                .anyMatch(s -> s.name().equals("Alteração salva antes de sair")));
        check("P1-B cenário B: nenhuma fotografia no diretório de dados depois do encerramento", recoveryFiles(data).isEmpty());
        Process none = probe("none", data, project);
        String done = waitLine(none, "DONE", 120);
        ended = none.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
        check("P1-B: novo processo concluído sem oferta de recuperação", done != null && ended && none.exitValue() == 0);
    }

    private static List<Path> recoveryFiles(Path data) throws IOException {
        if (!Files.isDirectory(data.resolve("recovery"))) return List.of();
        try (Stream<Path> s = Files.walk(data.resolve("recovery"))) {
            return s.filter(Files::isRegularFile).toList();
        }
    }

    private Process probe(String phase, Path data, Path project) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Dfile.encoding=UTF-8",
                "-Dstdout.encoding=UTF-8", "-cp", classpath, Studio006RecoveryProbe.class.getName(), phase, project.toString(), out.toString());
        pb.environment().put("NEKKORA_STUDIO_DATA_DIR", data.toString());
        pb.redirectErrorStream(true);
        return pb.start();
    }

    /** Lê a saída do filho (repassando OK/FALHA para o relatório) até a linha {@code marker}. */
    private String waitLine(Process p, String marker, int seconds) throws Exception {
        AtomicReference<String> found = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try (var r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("OK    ") || line.startsWith("FALHA ")) {
                        check("[filho] " + line.substring(6), line.startsWith("OK"));
                    } else if (line.startsWith("  ") || line.startsWith("captura") || line.startsWith("ERRO") || line.startsWith("    at")) {
                        log("  [filho] " + line.strip());
                        if (line.startsWith("ERRO")) failures++;
                    }
                    if (line.startsWith(marker)) {
                        found.set(line);
                        if (marker.equals("READY")) return; // o processo será morto
                    }
                }
            } catch (IOException e) {
                // processo encerrado
            }
        }, "saida-" + marker);
        t.setDaemon(true);
        t.start();
        t.join(seconds * 1000L);
        return found.get();
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
        File f = new File(out, "S006-" + name + ".png");
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
        try (PrintWriter w = new PrintWriter(new File(out, "S006-homologacao-log.txt"), StandardCharsets.UTF_8)) {
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
