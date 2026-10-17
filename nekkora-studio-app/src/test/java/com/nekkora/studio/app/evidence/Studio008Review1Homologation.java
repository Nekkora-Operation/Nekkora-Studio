package com.nekkora.studio.app.evidence;

import com.nekkora.studio.app.StudioApplication;
import com.nekkora.studio.app.StudioEnvironment;
import com.nekkora.studio.app.ui.StudioActions;
import com.nekkora.studio.app.workspace.ProjectState;
import com.nekkora.studio.bridge.BrowserLink;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.variable.SemanticType;
import com.nekkora.studio.domain.variable.Sensitivity;
import com.nekkora.studio.domain.variable.VariableDefinition;
import com.nekkora.studio.domain.variable.VariableDraft;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.variable.VariableScope;
import com.nekkora.studio.domain.variable.TextFormat;
import com.nekkora.studio.domain.variable.VariableType;
import com.nekkora.studio.persistence.ProjectFiles;
import com.nekkora.studio.persistence.ProjectFormat;
import com.nekkora.studio.persistence.ProjectRepository;
import com.nekkora.studio.semantics.SemanticContract;
import com.nekkora.studio.ui.LogicalSessionView;
import com.nekkora.studio.ui.VariablesPanel;
import com.nekkora.studio.ui.form.RenameVariableForm;
import com.nekkora.studio.ui.form.StepDetailsForm;
import com.nekkora.studio.ui.form.VariableForm;
import com.nekkora.studio.validation.Finding;
import com.nekkora.studio.validation.FindingCode;
import com.nekkora.studio.validation.RuleSet;
import com.nekkora.studio.validation.ValidationReport;
import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.dialog.NekkoraDialog;
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
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Homologação REAL, focada, da revisão 1 do PR #11 (STUDIO-008): só o que mudou na interface — o formato "Máscara de
 * CPF" no formulário real, o aviso {@code VARIABLE_REPRESENTATION_UNSPECIFIED} no painel de validação, a linha do painel
 * de variáveis, Desfazer/Refazer, a gravação 1.1.0 e o contrato semântico. Mesmo ambiente e mesmas travas de foco do
 * {@link Studio008Homologation} (dados isolados, Robot, sem navegador, dados FICTÍCIOS).
 *
 * <p>Uso: {@code java ... Studio008Review1Homologation <pasta-de-saída>}
 */
public final class Studio008Review1Homologation {

    private final File out;
    private final Robot robot;
    private final List<String> log = new ArrayList<>();
    private final List<String> logRecords = new CopyOnWriteArrayList<>();
    private final Map<String, Object> metrics = new LinkedHashMap<>();
    private int failures;
    private int checks;
    private StudioApplication.Window window;
    private Path projects;
    private Path dataDir;

    private Studio008Review1Homologation(File out) throws AWTException {
        this.out = out;
        this.robot = new Robot();
        robot.setAutoDelay(25);
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Studio008Review1Homologation h = new Studio008Review1Homologation(out);
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
        watchKeys();
        window = edt(() -> StudioApplication.launch(Optional.of(NekkoraThemes.SAKURA_DARK), BrowserLink.offline("DISABLED"), () -> { },
                StudioEnvironment.installation(), List.of()));
        edt(() -> {
            window.frame().setSize(1366, 900);
            window.frame().setLocation(0, 0);
            window.frame().toFront();
            return null;
        });
        waitFor("janela do Studio", 10, () -> edt(() -> window.frame().isShowing()));
        focusStudio();
        createAutomation("Cadastro revisao 008", "Cadastro");
        Automation a = active();
        LogicalSession cadastro = edt(() -> a.sessions().get(0));
        edt(() -> {
            window.studio().service().addManualStep(a.id(), cadastro.id(), "Preencher CPF", ActionType.FILL, null);
            return null;
        });

        // ================================================================ R1-1 — CPF confirmado, formato não declarado (formulário real)
        focusStudioWorkspace();
        shortcut("Ctrl+Shift+B", () -> edt(() -> window.studio().view().variablesVisible()), "Variáveis e dependências", KeyEvent.VK_CONTROL,
                KeyEvent.VK_SHIFT, KeyEvent.VK_B);
        click(edt(() -> window.studio().view().variablesPanel().createButton()));
        NekkoraDialog d = waitDialog("Nova variável");
        VariableForm form = edt(() -> find(d.getContentPane(), VariableForm.class, x -> true));
        typeIntoFocused("cliente.cpf");
        edt(() -> {
            form.typeInput().setSelectedItem(VariableType.STRING);
            form.semanticInput().setSelectedItem(SemanticType.BRAZILIAN_CPF);
            form.sensitivityInput().setSelectedItem(Sensitivity.PERSONAL_DATA);
            return null;
        });
        click(edt(form::confirmedInput));
        clickTab(form.tabs(), 2);
        List<TextFormat> offered = edt(() -> {
            List<TextFormat> l = new ArrayList<>();
            for (int i = 0; i < form.textFormatInput().getItemCount(); i++) l.add(form.textFormatInput().getItemAt(i));
            return l;
        });
        check("aba Restrições oferece 'Somente dígitos' e 'Máscara de CPF (000.000.000-00)'; o Studio não escolhe: continua 'Qualquer texto' "
                + offered, offered.contains(TextFormat.DIGITS) && offered.contains(TextFormat.BRAZILIAN_CPF_MASKED)
                && edt(() -> form.textFormatInput().getSelectedItem()) == TextFormat.ANY);
        captureWindow(d, "R1-01-formato-nao-escolhido");
        clickButtonWithText(d, "Declarar variável");
        waitClosed(d);
        VariableDefinition cpf = edt(() -> a.variableNamed("cliente.cpf").orElseThrow());
        check("CPF declarado: significado confirmado, formato não declarado (Qualquer texto), sem valor",
                cpf.authority() == com.nekkora.studio.domain.variable.SemanticStatus.USER_CONFIRMED && cpf.constraints().textFormat() == TextFormat.ANY
                && cpf.origin().constant() == null);
        setReferenceByEditor(cadastro, 0, "Preencher CPF", "cliente.cpf", null);
        validate();
        ValidationReport r1 = report();
        List<Finding> rep = r1.forVariable(cpf.id()).stream().filter(f -> f.code() == FindingCode.VARIABLE_REPRESENTATION_UNSPECIFIED).toList();
        check("R1-1 Validar (Ctrl+Shift+V real): VARIABLE_REPRESENTATION_UNSPECIFIED como AVISO que limita a avaliação, nunca bloqueio; estado "
                + r1.status(), rep.size() == 1 && rep.get(0).severity() == com.nekkora.studio.validation.Severity.WARNING
                && r1.status() != com.nekkora.studio.validation.ValidationStatus.NO_STATIC_BLOCKERS);
        int row = edt(() -> {
            var list = window.studio().view().validationPanel().findingList();
            for (int i = 0; i < list.getModel().getSize(); i++) {
                if (list.getModel().getElementAt(i).finding().code() == FindingCode.VARIABLE_REPRESENTATION_UNSPECIFIED) return i;
            }
            return 0;
        });
        edt(() -> {
            window.studio().view().validationPanel().findingList().setSelectedIndex(row);
            window.studio().view().validationPanel().findingList().ensureIndexIsVisible(row);
            return null;
        });
        String panel = edt(() -> texts(window.studio().view().validationPanel()));
        String shown = edt(() -> window.studio().view().validationPanel().findingList().getSelectedValue().finding().explanation());
        check("painel: apontamento selecionado com a explicação controlada ('" + shown + "') e 'Como corrigir' com a orientação, sem valores",
                shown.contains("a forma esperada não foi declarada") && panel.contains("escolha o formato que a etapa espera"));
        captureStudio("R1-02-aviso-representacao");
        String before = edt(() -> SemanticContract.of(a).toJson());
        Files.writeString(out.toPath().resolve("S008-R1-contrato-sem-formato.json"), before, StandardCharsets.UTF_8);
        check("contrato semântico: incerteza REPRESENTATION_UNSPECIFIED ao lado de INPUT_VALUE_UNKNOWN", before.contains("\"REPRESENTATION_UNSPECIFIED\"")
                && before.contains("\"INPUT_VALUE_UNKNOWN\""));

        // ================================================================ R1-2 — declarar a máscara pelo editor da variável
        focusStudioWorkspace();
        shortcut("Ctrl+Shift+B", () -> edt(() -> window.studio().view().variablesVisible()), "Variáveis e dependências", KeyEvent.VK_CONTROL,
                KeyEvent.VK_SHIFT, KeyEvent.VK_B);
        selectPanelRow(0);
        click(edt(() -> window.studio().view().variablesPanel().editButton()));
        NekkoraDialog e = waitDialog("Editar variável");
        VariableForm edit = edt(() -> find(e.getContentPane(), VariableForm.class, x -> true));
        clickTab(edit.tabs(), 2);
        edt(() -> {
            edit.textFormatInput().setSelectedItem(TextFormat.BRAZILIAN_CPF_MASKED);
            return null;
        });
        captureWindow(e, "R1-03-mascara-de-cpf");
        clickButtonWithText(e, "Salvar variável");
        waitClosed(e);
        selectPanelRow(0);
        String declaration = edt(() -> window.studio().view().variablesPanel().declarationLine().getText());
        check("variável editada: formato 'Máscara de CPF'; linha Declaração do painel: " + declaration,
                edt(() -> a.variable(cpf.id()).orElseThrow().constraints().textFormat()) == TextFormat.BRAZILIAN_CPF_MASKED
                && declaration.contains("Restrições: máscara de CPF (000.000.000-00)"));
        captureStudio("R1-04-variavel-com-mascara");
        validate();
        ValidationReport r2 = report();
        check("R1-2 Validar de novo: o aviso de representação some; a entrada continua NÃO disponível (representação declarada não é valor validado)",
                r2.forVariable(cpf.id()).stream().noneMatch(f -> f.code() == FindingCode.VARIABLE_REPRESENTATION_UNSPECIFIED)
                && r2.findings().stream().anyMatch(f -> f.code() == FindingCode.INPUT_VALUE_NOT_AVAILABLE)
                && r2.status() != com.nekkora.studio.validation.ValidationStatus.NO_STATIC_BLOCKERS);
        captureStudio("R1-05-representacao-declarada");
        String after = edt(() -> SemanticContract.of(a).toJson());
        Files.writeString(out.toPath().resolve("S008-R1-contrato-com-mascara.json"), after, StandardCharsets.UTF_8);
        check("contrato semântico: textFormat BRAZILIAN_CPF_MASKED, sem REPRESENTATION_UNSPECIFIED, entrada ainda desconhecida",
                after.contains("\"textFormat\": \"BRAZILIAN_CPF_MASKED\"") && !after.contains("REPRESENTATION_UNSPECIFIED") && after.contains("\"INPUT_VALUE_UNKNOWN\""));

        // ================================================================ R1-3 — Desfazer/Refazer
        focusStudioWorkspace();
        shortcut("Ctrl+Z", () -> edt(() -> a.variable(cpf.id()).orElseThrow().constraints().textFormat()) == TextFormat.ANY, KeyEvent.VK_CONTROL, KeyEvent.VK_Z);
        validate();
        check("Ctrl+Z real: formato volta a 'Qualquer texto' e o aviso reaparece", report().forVariable(cpf.id()).stream()
                .anyMatch(f -> f.code() == FindingCode.VARIABLE_REPRESENTATION_UNSPECIFIED));
        focusStudioWorkspace();
        shortcut("Ctrl+Y", () -> edt(() -> a.variable(cpf.id()).orElseThrow().constraints().textFormat()) == TextFormat.BRAZILIAN_CPF_MASKED, KeyEvent.VK_CONTROL,
                KeyEvent.VK_Y);
        check("Ctrl+Y real: máscara de volta", true);

        // ================================================================ R1-4 — salvar 1.1.0 com a máscara
        Path file = projects.resolve("cadastro-revisao-008.nekkora");
        focusStudioWorkspace();
        shortcut("Ctrl+S (Salvar como)", () -> fileChooser() != null, KeyEvent.VK_CONTROL, KeyEvent.VK_S);
        waitChooser("Salvar projeto como");
        typePath(file);
        waitFor("projeto salvo", 15, () -> Files.exists(file) && !state(a).dirty());
        String vars = new String(entries(file).get(ProjectFormat.VARIABLES), StandardCharsets.UTF_8);
        check("salvo em " + formatOf(file) + " com textFormat BRAZILIAN_CPF_MASKED; sem campo de valor", ProjectFormat.FORMAT_VERSION_1_1.equals(formatOf(file))
                && vars.contains("\"textFormat\":\"BRAZILIAN_CPF_MASKED\"") && !vars.contains("\"value\""));

        String logs = String.join("\n", logRecords);
        Files.writeString(out.toPath().resolve("S008-R1-logs-da-aplicacao.txt"), logs, StandardCharsets.UTF_8);
        check("logs só com códigos e contagens: sem nomes de variáveis", !logs.contains("cliente.cpf"));
        edt(() -> {
            window.studio().projects().dispose();
            return null;
        });
    }

    // ============================================================================== apoio

    private Automation active() throws Exception {
        return edt(() -> window.studio().state().activeAutomation().orElseThrow());
    }

    private ProjectState state(Automation a) throws Exception {
        return edt(() -> window.studio().state().project(a.id()).orElseThrow());
    }

    private void validate() throws Exception {
        focusStudioWorkspace();
        shortcut("Ctrl+Shift+V", () -> edt(() -> window.studio().view().validationVisible() && window.studio().view().validationPanel().isCurrent()),
                "Validar automação", KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_V);
    }

    private ValidationReport report() throws Exception {
        return edt(() -> window.studio().view().validationPanel().report().orElseThrow());
    }

    private static Set<FindingCode> codes(ValidationReport r, AutomationStep s) {
        return r.forStep(s.id()).stream().map(Finding::code).collect(Collectors.toSet());
    }

    private String panelRow(int i) throws Exception {
        return edt(() -> {
            VariablesPanel.Row r = window.studio().view().variablesPanel().rows().get(i);
            return r.title() + " · " + r.summary();
        });
    }

    /** Clique REAL na linha do painel de variáveis. */
    private void selectPanelRow(int index) throws Exception {
        var list = edt(() -> window.studio().view().variablesPanel().variableList());
        Rectangle r = edt(() -> {
            list.ensureIndexIsVisible(index);
            Rectangle cell = list.getCellBounds(index, index);
            java.awt.Point p = list.getLocationOnScreen();
            return new Rectangle(p.x + cell.x, p.y + cell.y, cell.width, cell.height);
        });
        robot.mouseMove(r.x + Math.min(r.width / 2, 200), r.y + r.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        waitFor("variável selecionada", 10, () -> edt(() -> list.getSelectedIndex() == index));
        sleep(300);
    }

    /** Editor de etapa (F2) → Parâmetros → Variável → digita a referência → Salvar alterações. */
    private void setReferenceByEditor(LogicalSession s, int index, String title, String reference, String capture) throws Exception {
        clickRow(s, index);
        shortcut("F2 (editor de etapa)", () -> dialogShowing(title), KeyEvent.VK_F2);
        NekkoraDialog editor = waitDialog(title);
        StepDetailsForm details = edt(() -> find(editor.getContentPane(), StepDetailsForm.class, x -> true));
        clickTab(details.tabs(), 1);
        click(edt(() -> details.modeInput(StepDetailsForm.ValueMode.VARIABLE)));
        click(edt(details::valueInput));
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_A);
        typeAscii(reference);
        sleep(300);
        if (capture != null) captureWindow(editor, capture);
        clickButtonWithText(editor, "Salvar alterações");
        waitClosed(editor);
    }

    private void createAutomation(String name, String session) throws Exception {
        shortcut("Ctrl+N", () -> dialogShowing("Nova automação"), KeyEvent.VK_CONTROL, KeyEvent.VK_N);
        NekkoraDialog d = waitDialog("Nova automação");
        typeIntoFocused(name);
        press(KeyEvent.VK_ENTER);
        waitClosed(d);
        newSession(session);
    }

    private void newSession(String name) throws Exception {
        focusStudio();
        shortcut("Ctrl+Shift+N", () -> dialogShowing("Nova sessão"), KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_N);
        NekkoraDialog d = waitDialog("Nova sessão");
        typeIntoFocused(name);
        press(KeyEvent.VK_ENTER);
        waitClosed(d);
    }

    private void closeActiveProject() throws Exception {
        focusStudioWorkspace();
        menu("Arquivo", "Fechar projeto");
        sleep(800);
        if (!openDialogs().isEmpty()) throw new IllegalStateException("fechar pediu confirmação inesperada: " + openDialogs());
    }

    private void openByDialog(Path file) throws Exception {
        focusStudio();
        shortcut("Ctrl+O", () -> fileChooser() != null, KeyEvent.VK_CONTROL, KeyEvent.VK_O);
        waitChooser("Abrir projeto");
        typePath(file);
        waitFor("projeto aberto " + file.getFileName(), 30, () -> edt(() -> window.studio().state().activeAutomation()
                .flatMap(x -> window.studio().state().project(x.id())).flatMap(ProjectState::path).map(file::equals).orElse(false)));
        sleep(500);
    }

    private List<String> openDialogs() throws Exception {
        return edt(() -> {
            List<String> titles = new ArrayList<>();
            for (Window w : Window.getWindows()) if (w instanceof java.awt.Dialog dl && dl.isShowing()) titles.add(dl.getTitle());
            return titles;
        });
    }

    private List<Path> listProjects() throws IOException {
        try (Stream<Path> s = Files.list(projects)) {
            return s.toList();
        }
    }

    private List<String> recoveryTexts() throws IOException {
        List<String> texts = new ArrayList<>();
        if (!Files.isDirectory(dataDir)) return texts;
        try (Stream<Path> s = Files.walk(dataDir)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".nekkora")).toList()) {
                StringBuilder b = new StringBuilder();
                entries(p).values().forEach(v -> b.append(new String(v, StandardCharsets.UTF_8)));
                texts.add(b.toString());
            }
        }
        return texts;
    }

    /** Projeto sintético (fictício) de N etapas com N/5 variáveis, gravado pelo formato real (fora da EDT). */
    private static void writeVolume(Path file, int steps) throws Exception {
        Automation a = Automation.create("Volume homologacao 008", "Dados fictícios para medir desempenho", Clock.systemUTC());
        LogicalSession s = null;
        List<AutomationStep> producers = new ArrayList<>();
        int vars = steps / 5;
        for (int i = 0; i < steps; i++) {
            if (i % 50 == 0) s = a.addSession("Bloco " + (i / 50 + 1), null);
            boolean uses = i % 5 != 0;
            StepDraft.Builder b = StepDraft.builder().name("Etapa " + (i + 1)).actionType(uses ? ActionType.FILL : ActionType.OBSERVE);
            if (uses) b.parameters(ActionParameters.withValue(new ValueSource.VariableReference("campo.v" + (i % vars))));
            AutomationStep st = a.appendStep(s.id(), b.build());
            if (!uses) producers.add(st);
        }
        for (int v = 0; v < vars; v++) {
            VariableDraft d = VariableDraft.of("campo.v" + v, VariableType.STRING).withSensitivity(Sensitivity.INTERNAL);
            d = v % 2 == 0 ? d.withOrigin(VariableOrigin.input()) : d.withOrigin(VariableOrigin.stepOutput(producers.get(v % producers.size()).id()));
            a.declareVariable(d);
        }
        new ProjectFiles(new ProjectRepository(), "homologacao-008").save(file, ProjectFiles.Snapshot.of(a, java.util.UUID.randomUUID().toString()), null,
                false, Instant.now(), Clock.systemUTC());
    }

    private static String formatOf(Path file) throws IOException {
        String manifest = new String(entries(file).get(ProjectFormat.MANIFEST), StandardCharsets.UTF_8);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"formatVersion\":\"([^\"]+)\"").matcher(manifest);
        return m.find() ? m.group(1) : "";
    }

    static Map<String, byte[]> entries(Path file) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipFile z = new ZipFile(file.toFile())) {
            for (ZipEntry e : java.util.Collections.list(z.entries())) out.put(e.getName(), z.getInputStream(e).readAllBytes());
        }
        return out;
    }

    static String sha(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private long edtNanos(Runnable r) throws Exception {
        return edt(() -> {
            long t = System.nanoTime();
            r.run();
            return System.nanoTime() - t;
        });
    }

    private void writeMetrics() throws IOException {
        StringBuilder b = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, Object> e : metrics.entrySet()) b.append(i++ == 0 ? "" : ", ").append('"').append(e.getKey()).append("\": ").append(e.getValue());
        Files.writeString(out.toPath().resolve("S008-metricas.json"), b.append("}\n").toString(), StandardCharsets.UTF_8);
        log("métricas: " + b);
    }

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

    /** Menu da barra (mouse): "Arquivo" → item. */
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

    private void focusStudioWorkspace() throws Exception {
        edt(() -> {
            window.studio().state().activeAutomation().ifPresent(a -> window.studio().controller().selectAutomation(a.id()));
            return null;
        });
        focusStudio();
        sleep(300);
    }

    /** Clique REAL na linha da lista de etapas da sessão (dá o foco à lista, como o usuário). */
    private void clickRow(LogicalSession s, int index) throws Exception {
        focusStudio();
        Automation a = active();
        LogicalSessionView v = edt(() -> window.studio().view().overview(a.id()).orElseThrow().sessionView(s.id()).orElseThrow());
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

    /** Clique REAL numa aba. */
    private void clickTab(javax.swing.JTabbedPane tabs, int index) throws Exception {
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

    private NekkoraDialog waitDialog(String title) throws Exception {
        NekkoraDialog d = (NekkoraDialog) waitValue("diálogo " + title, 15, () -> edt(() -> {
            for (Window w : Window.getWindows()) if (w instanceof NekkoraDialog nd && nd.isShowing() && title.equals(nd.getTitle())) return nd;
            return null;
        }));
        sleep(300);
        return d;
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
        requireActive(c);
        Rectangle r = edt(() -> new Rectangle(c.getLocationOnScreen(), c.getSize()));
        robot.mouseMove(r.x + r.width / 2, r.y + r.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        sleep(250);
    }

    /**
     * Segurança: o Robot só clica se a janela do componente (Studio ou diálogo dele) é a janela ATIVA — com outra janela
     * por cima (menu do sistema, outro programa), a homologação para em vez de clicar fora do Studio.
     */
    private void requireActive(Component c) throws Exception {
        Window w = edt(() -> c instanceof Window x ? x : SwingUtilities.getWindowAncestor(c));
        if (w == null) return;
        // a janela ativa precisa ser do Studio (a própria, um diálogo ou uma notificação dele); null = outro programa na frente
        Callable<Boolean> ours = () -> edt(() -> KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() != null);
        long end = System.currentTimeMillis() + 3000;
        while (!ours.call() && System.currentTimeMillis() < end) {
            edt(() -> {
                w.toFront();
                w.requestFocus();
                return null;
            });
            sleep(200);
        }
        if (!ours.call()) throw new IllegalStateException("outro programa está ativo sobre o Studio: homologação interrompida sem clicar");
    }

    /** Atalho com efeito conferido: repete (até 3 vezes) só se o efeito não apareceu — teclas podem ser perdidas pelo sistema. */
    private void shortcut(String what, Callable<Boolean> effect, int... keys) throws Exception {
        shortcut(what, effect, null, keys);
    }

    /**
     * Idem, com um caminho alternativo REAL (botão da barra de ferramentas pelo nome acessível) quando a combinação não chega
     * à JVM — por exemplo, capturada por um atalho global de outro programa. Fica registrado no log.
     */
    private void shortcut(String what, Callable<Boolean> effect, String toolbarFallback, int... keys) throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            requireActive(edt(window::frame));
            if (attempt > 1 && !dialogOpenNow()) focusStudio();
            probeKeyboard();
            int before = keysSeen.size();
            press(keys);
            long end = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < end) {
                if (Boolean.TRUE.equals(effect.call())) {
                    if (attempt > 1) log("atalho repetido (" + what + "): " + attempt + " tentativas");
                    return;
                }
                sleep(150);
            }
            log("atalho sem efeito (" + what + "), tentativa " + attempt + "; teclas recebidas pela JVM: " + keysSeen.subList(before, keysSeen.size()));
        }
        if (toolbarFallback == null) throw new IllegalStateException("atalho sem efeito: " + what);
        AbstractButton b = edt(() -> find(window.frame().getContentPane(), AbstractButton.class, x -> x.isShowing()
                && toolbarFallback.equals(x.getAccessibleContext().getAccessibleName())));
        if (b == null) throw new IllegalStateException("atalho sem efeito e botão não encontrado: " + what);
        log("atalho " + what + " não chegou ao Studio: usado o botão \"" + toolbarFallback + "\" da barra de ferramentas");
        click(b);
        waitFor(what + " (botão)", 10, effect);
    }

    /** Há um diálogo do Studio aberto (aí o foco está nele, não na janela principal). */
    private boolean dialogOpenNow() throws Exception {
        return edt(() -> {
            for (Window w : Window.getWindows()) if (w instanceof java.awt.Dialog d && d.isShowing()) return true;
            return false;
        });
    }

    /**
     * Sonda inofensiva antes de um atalho: pressiona Shift sozinho e confere que a JVM recebeu. Se não recebeu, o teclado
     * não está com o Studio (outro programa na frente): a homologação para em vez de mandar atalhos para outro programa.
     */
    private void probeKeyboard() throws Exception {
        int before = keysSeen.size();
        press(KeyEvent.VK_SHIFT);
        long end = System.currentTimeMillis() + 1500;
        while (keysSeen.size() == before && System.currentTimeMillis() < end) sleep(50);
        if (keysSeen.size() == before) throw new IllegalStateException("o teclado não está com o Studio: homologação interrompida sem enviar o atalho");
    }

    /** Teclas recebidas pela JVM (código + modificadores; nunca texto). */
    private final List<String> keysSeen = new CopyOnWriteArrayList<>();

    private void watchKeys() {
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e instanceof KeyEvent k && k.getID() == KeyEvent.KEY_PRESSED) {
                keysSeen.add(KeyEvent.getModifiersExText(k.getModifiersEx()) + "+" + KeyEvent.getKeyText(k.getKeyCode()));
            }
        }, java.awt.AWTEvent.KEY_EVENT_MASK);
    }

    private boolean dialogShowing(String title) throws Exception {
        return edt(() -> {
            for (Window w : Window.getWindows()) if (w instanceof NekkoraDialog nd && nd.isShowing() && title.equals(nd.getTitle())) return true;
            return false;
        });
    }

    private void focusStudio() throws Exception {
        edt(() -> {
            window.frame().toFront();
            return null;
        });
        // clique na barra de título: dá ao Studio o primeiro plano DO SISTEMA (o "ativo" do Java não garante isso logo após
        // abrir — as teclas iriam para o programa que estivesse na frente)
        Rectangle b = edt(() -> window.frame().getBounds());
        robot.mouseMove(b.x + b.width / 2, b.y + 12);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        waitFor("Studio com foco", 10, () -> edt(() -> window.frame().isFocused() || window.frame().isActive()));
        sleep(300);
    }

    /** Texto no campo com foco (como colado pelo usuário: independe do layout do teclado). */
    private void typeIntoFocused(String text) throws Exception {
        edt(() -> {
            Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (!(c instanceof JTextComponent t)) throw new IllegalStateException("foco não está em um campo de texto: " + c);
            t.selectAll();
            return null;
        });
        typeAscii(text);
        sleep(120);
    }

    private void typeAscii(String text) throws Exception {
        SwingUtilities.invokeAndWait(() -> java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(text), null));
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_V);
    }

    private void press(int... keys) {
        for (int k : keys) robot.keyPress(k);
        for (int i = keys.length - 1; i >= 0; i--) robot.keyRelease(keys[i]);
        robot.waitForIdle();
        sleep(200);
    }

    /** Captura SÓ do conteúdo da janela do Studio (rootPane): nada da área de trabalho nem de outras janelas. */
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
        File f = new File(out, "S008-" + name + ".png"); // name já começa com R1-
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
        try (PrintWriter w = new PrintWriter(new File(out, "S008-R1-homologacao-log.txt"), StandardCharsets.UTF_8)) {
            log.forEach(w::println);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        log("DIAGNÓSTICO (" + what + "): diálogos abertos " + openDialogs());
        try {
            captureStudio("FALHA-" + what.replaceAll("[^A-Za-z0-9]+", "-"));
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
