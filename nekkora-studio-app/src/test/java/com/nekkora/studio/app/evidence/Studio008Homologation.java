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
 * Homologação REAL da STUDIO-008 (Semantic Variables & Data Flow Engine) no Windows: Studio de verdade (dados isolados por
 * {@code NEKKORA_STUDIO_DATA_DIR}), mouse e teclado do sistema operacional (Robot), diálogos reais do design system e
 * diálogos de arquivo do sistema. Sem navegador: nenhum cenário grava, executa ou consulta página. Dados FICTÍCIOS.
 *
 * <p>Cenários: (1) declarar CPF sem valor; (2) usar em preencher; (3) fora de escopo e sem declaração; (4) saída de etapa
 * não comprovada; (5) renomear com Desfazer/Refazer; (6) salvar 1.1 (migração confirmada com cópia), reabrir e ler 1.0;
 * (7) projeto antigo não sobrescrito, não executado e sem pedido de credenciais; (8) quatro temas e 5.000 etapas.
 *
 * <p>As listas de escolha (tipo, significado, sensibilidade, escopo, origem) são ajustadas pela API do próprio componente;
 * cliques, digitação, atalhos, abas, botões e diálogos de arquivo usam o Robot.
 *
 * <p>Uso: {@code java ... Studio008Homologation <pasta-de-saída>}
 */
public final class Studio008Homologation {

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

    private Studio008Homologation(File out) throws AWTException {
        this.out = out;
        this.robot = new Robot();
        robot.setAutoDelay(25);
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Studio008Homologation h = new Studio008Homologation(out);
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
        // projeto da STUDIO-006 (formato 1.0.0, fictício, com referência de credencial): o "projeto antigo" do cenário 7
        Path old = projects.resolve("consulta-clientes-1.0.nekkora");
        Files.copy(Path.of("nekkora-studio-persistence/src/test/resources/contracts/nekkora-project/1.0.0/fixtures/valid/consulta-clientes.nekkora"), old);
        String oldSha = sha(old);
        long oldModified = Files.getLastModifiedTime(old).toMillis();
        Path big = projects.resolve("volume-5000.nekkora");
        writeVolume(big, 5_000);

        window = edt(() -> StudioApplication.launch(Optional.of(NekkoraThemes.SAKURA_DARK), BrowserLink.offline("DISABLED"), () -> { },
                StudioEnvironment.installation(), List.of()));
        edt(() -> {
            window.frame().setSize(1366, 900);
            window.frame().setLocation(0, 0);
            window.frame().toFront();
            return null;
        });
        waitFor("janela do Studio", 10, () -> edt(() -> window.frame().isShowing()));
        check("ação Variáveis e dependências na barra de ferramentas e atalho Ctrl+Shift+B", edt(() -> find(window.frame().getContentPane(),
                com.nekkora.ui.navigation.NekkoraToolbarButton.class, b -> b.getAction() != null
                        && StudioActions.VARIABLES.equals(b.getAction().getValue(com.nekkora.ui.action.NekkoraAction.ID))) != null
                && javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_B, com.nekkora.ui.action.NekkoraKeys.menuMask() | InputEvent.SHIFT_DOWN_MASK)
                        .equals(window.studio().view().actions().get(StudioActions.VARIABLES).shortcutKey())));

        // ================================================================ preparação: automação 1.0 salva
        focusStudio();
        createAutomation("Cadastro homologacao 008", "Cadastro");
        newSession("Pesquisa");
        Automation a = active();
        LogicalSession cadastro = edt(() -> a.sessions().get(0));
        LogicalSession pesquisa = edt(() -> a.sessions().get(1));
        edt(() -> {
            // etapas pelo mesmo caso de uso do diálogo "Nova etapa"
            window.studio().service().addManualStep(a.id(), cadastro.id(), "Preencher CPF", ActionType.FILL, null);
            window.studio().service().addManualStep(a.id(), cadastro.id(), "Ler protocolo", ActionType.OBSERVE, null);
            window.studio().service().addManualStep(a.id(), cadastro.id(), "Preencher protocolo", ActionType.FILL, null);
            window.studio().service().addManualStep(a.id(), pesquisa.id(), "Pesquisar protocolo", ActionType.FILL, null);
            window.studio().service().addManualStep(a.id(), pesquisa.id(), "Pesquisar cliente", ActionType.FILL, null);
            return null;
        });
        Path file = projects.resolve("cadastro-008.nekkora");
        focusStudioWorkspace();
        shortcut("Ctrl+S (Salvar como)", () -> fileChooser() != null, KeyEvent.VK_CONTROL, KeyEvent.VK_S);
        waitChooser("Salvar projeto como");
        typePath(file);
        waitFor("projeto salvo", 15, () -> Files.exists(file) && !state(a).dirty());
        check("sem variáveis o projeto é gravado em 1.0.0 (" + formatOf(file) + ")", ProjectFormat.FORMAT_VERSION.equals(formatOf(file))
                && !entries(file).containsKey(ProjectFormat.VARIABLES));
        String sha10 = sha(file);

        // ================================================================ Cenário 1 — declarar CPF sem valor
        focusStudioWorkspace();
        shortcut("Ctrl+Shift+B", () -> edt(() -> window.studio().view().variablesVisible()), "Variáveis e dependências", KeyEvent.VK_CONTROL,
                KeyEvent.VK_SHIFT, KeyEvent.VK_B);
        captureStudio("01-variaveis-vazio");
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
        check("formulário sem campo de valor de execução: só a declaração", edt(() -> !texts(form).toLowerCase().contains("valor de execução")
                && form.originInput().getSelectedValue() == VariableOrigin.Kind.INPUT));
        captureWindow(d, "01-nova-variavel-cpf");
        clickTab(form.tabs(), 1);
        edt(() -> {
            form.originInput().setSelectedItem(VariableOrigin.Kind.CONSTANT);
            return null;
        });
        click(edt(form::constantInput));
        typeIntoFocused("00000000000");
        captureWindow(d, "01-constante-nao-criptografada");
        clickButtonWithText(d, "Declarar variável");
        sleep(500);
        check("CPF com valor fixo recusado no campo (dados pessoais não podem ter valor fixo); nada declarado",
                edt(() -> d.isShowing() && form.visibleErrors().contains("não podem ter valor fixo") && a.variables().isEmpty()));
        captureWindow(d, "01-constante-sensivel-recusada");
        edt(() -> {
            form.originInput().setSelectedItem(VariableOrigin.Kind.INPUT);
            return null;
        });
        clickButtonWithText(d, "Declarar variável");
        waitClosed(d);
        VariableDefinition cpf = edt(() -> a.variableNamed("cliente.cpf").orElseThrow());
        check("CPF declarado como entrada, dados pessoais, significado confirmado, sem valor",
                cpf.origin().kind() == VariableOrigin.Kind.INPUT && cpf.sensitivity() == Sensitivity.PERSONAL_DATA && cpf.origin().constant() == null
                        && cpf.authority() == com.nekkora.studio.domain.variable.SemanticStatus.USER_CONFIRMED);
        check("painel lista a declaração: " + panelRow(0), panelRow(0).contains("cliente.cpf · Texto · CPF") && panelRow(0).contains("Dados pessoais"));
        captureStudio("01-cpf-declarado");

        // ================================================================ Cenário 2 — usar em FILL (editor de etapa)
        AutomationStep cpfStep = edt(() -> cadastro.steps().get(0));
        clickRow(cadastro, 0);
        shortcut("F2 (editor de etapa)", () -> dialogShowing("Preencher CPF"), KeyEvent.VK_F2);
        NekkoraDialog editor = waitDialog("Preencher CPF");
        StepDetailsForm details = edt(() -> find(editor.getContentPane(), StepDetailsForm.class, x -> true));
        clickTab(details.tabs(), 1);
        click(edt(() -> details.modeInput(StepDetailsForm.ValueMode.VARIABLE)));
        waitFor("lista de variáveis declaradas", 5, () -> edt(() -> details.declaredVariableInput().isShowing()));
        check("editor de etapa lista só as variáveis declaradas visíveis", edt(() -> details.declaredVariableInput().getItemCount() == 2
                && "cliente.cpf".equals(details.declaredVariableInput().getItemAt(1))));
        edt(() -> {
            details.declaredVariableInput().setSelectedItem("cliente.cpf");
            return null;
        });
        check("escolher preenche a referência e mostra a declaração: " + edt(details::declaredVariableInfo),
                edt(() -> "cliente.cpf".equals(details.valueInput().getText()) && details.declaredVariableInfo().startsWith("Declarada: Texto · CPF")));
        captureWindow(editor, "02-editor-variavel-declarada");
        clickButtonWithText(editor, "Salvar alterações");
        waitClosed(editor);
        check("etapa usa a variável declarada", edt(() -> new ValueSource.VariableReference("cliente.cpf")
                .equals(a.step(cpfStep.id()).orElseThrow().parameters().value().orElse(null))));
        validate();
        ValidationReport r2 = report();
        check("validação 1.1: entrada não disponível (limita a avaliação), sem 'não declarada'", r2.rulesVersion().equals("nekkora-validation-rules/1.1.0")
                && codes(r2, cpfStep).contains(FindingCode.INPUT_VALUE_NOT_AVAILABLE) && !codes(r2, cpfStep).contains(FindingCode.VARIABLE_NOT_DECLARED));
        captureStudio("02-validacao-entrada");

        // ================================================================ Cenário 3 — fora de escopo e sem declaração
        AutomationStep ler = edt(() -> cadastro.steps().get(1));
        edt(() -> {
            // declaração pelo serviço (o mesmo caso de uso do formulário): escopo da sessão Cadastro, saída declarada de "Ler protocolo"
            window.studio().service().declareVariable(a.id(), VariableDraft.of("resultado.protocolo", VariableType.STRING)
                    .withSemantic(SemanticType.IDENTIFIER, com.nekkora.studio.domain.variable.SemanticStatus.PROPOSED_UNCONFIRMED)
                    .withSensitivity(Sensitivity.INTERNAL).withScope(VariableScope.session(cadastro.id())).withOrigin(VariableOrigin.stepOutput(ler.id())));
            return null;
        });
        AutomationStep foraStep = edt(() -> pesquisa.steps().get(0));
        AutomationStep semDeclaracao = edt(() -> pesquisa.steps().get(1));
        setReferenceByEditor(pesquisa, 0, "Pesquisar protocolo", "resultado.protocolo", "03-editor-fora-do-escopo");
        setReferenceByEditor(pesquisa, 1, "Pesquisar cliente", "Cliente.Nome", "03-editor-nao-declarada");
        validate();
        ValidationReport r3 = report();
        check("fora de escopo: VARIABLE_OUT_OF_SCOPE (bloqueio)", codes(r3, foraStep).contains(FindingCode.VARIABLE_OUT_OF_SCOPE));
        check("sem declaração (nome parecido não associado): VARIABLE_NOT_DECLARED", codes(r3, semDeclaracao).contains(FindingCode.VARIABLE_NOT_DECLARED));
        captureStudio("03-validacao-escopo-e-declaracao");

        // ================================================================ Cenário 4 — saída de etapa não comprovada
        AutomationStep usaProtocolo = edt(() -> cadastro.steps().get(2));
        setReferenceByEditor(cadastro, 2, "Preencher protocolo", "resultado.protocolo", null);
        validate();
        ValidationReport r4 = report();
        check("saída de etapa anterior: STEP_OUTPUT_NOT_PROVEN (aviso que limita), nunca 'comprovada'",
                codes(r4, usaProtocolo).contains(FindingCode.STEP_OUTPUT_NOT_PROVEN) && r4.findings().stream()
                        .anyMatch(f -> f.code() == FindingCode.VARIABLE_SEMANTIC_UNCONFIRMED));
        captureStudio("04-validacao-saida-nao-comprovada");
        focusStudioWorkspace();
        shortcut("Ctrl+Shift+B", () -> edt(() -> window.studio().view().variablesVisible()), "Variáveis e dependências", KeyEvent.VK_CONTROL,
                KeyEvent.VK_SHIFT, KeyEvent.VK_B);
        selectPanelRow(1);
        check("painel: origem declarada 'não comprovada': " + edt(() -> window.studio().view().variablesPanel().originLine().getText()),
                edt(() -> window.studio().view().variablesPanel().originLine().getText().contains("(não comprovada)")));
        captureStudio("04-variaveis-saida-de-etapa");
        SemanticContract contract = edt(() -> SemanticContract.of(a));
        String contractJson = contract.toJson();
        Files.writeString(out.toPath().resolve("S008-contrato-semantico.json"), contractJson, StandardCharsets.UTF_8);
        check("contrato semântico: produtora sempre UNVERIFIED_AT_RUNTIME, sem nomes de etapas", contractJson.contains("UNVERIFIED_AT_RUNTIME")
                && !contractJson.contains("Ler protocolo") && !contractJson.contains("Cliente.Nome"));

        // ================================================================ Cenário 5 — renomear com Desfazer/Refazer
        selectPanelRow(0);
        shortcut("F2 (renomear variável)", () -> dialogShowing("Renomear variável"), KeyEvent.VK_F2);
        NekkoraDialog rename = waitDialog("Renomear variável");
        RenameVariableForm rf = edt(() -> find(rename.getContentPane(), RenameVariableForm.class, x -> true));
        check("prévia antes de confirmar: " + edt(rf::previewText), "1 etapa passa a usar o novo nome, na mesma operação.".equals(edt(rf::previewText)));
        typeIntoFocused("titular.cpf");
        captureWindow(rename, "05-renomear-previa");
        press(KeyEvent.VK_ENTER);
        waitClosed(rename);
        check("renomeado junto com a referência da etapa", edt(() -> new ValueSource.VariableReference("titular.cpf")
                .equals(a.step(cpfStep.id()).orElseThrow().parameters().value().orElse(null)) && a.variable(cpf.id()).orElseThrow().name().equals("titular.cpf")));
        captureStudio("05-renomeado");
        focusStudioWorkspace();
        shortcut("Ctrl+Z", () -> edt(() -> a.variable(cpf.id()).orElseThrow().name().equals("cliente.cpf")), KeyEvent.VK_CONTROL, KeyEvent.VK_Z);
        check("Ctrl+Z: nome e referência voltam juntos", edt(() -> new ValueSource.VariableReference("cliente.cpf")
                .equals(a.step(cpfStep.id()).orElseThrow().parameters().value().orElse(null))));
        captureStudio("05-depois-do-desfazer");
        shortcut("Ctrl+Y", () -> edt(() -> a.variable(cpf.id()).orElseThrow().name().equals("titular.cpf")), KeyEvent.VK_CONTROL, KeyEvent.VK_Y);
        check("Ctrl+Y: renomeado de novo", edt(() -> new ValueSource.VariableReference("titular.cpf")
                .equals(a.step(cpfStep.id()).orElseThrow().parameters().value().orElse(null))));

        // ================================================================ Cenário 6 — salvar 1.1 (migração), reabrir e ler 1.0
        check("arquivo 1.0 intocado até salvar", sha10.equals(sha(file)));
        focusStudioWorkspace();
        shortcut("Ctrl+S (migração)", () -> dialogShowing("Atualizar o formato do projeto?"), KeyEvent.VK_CONTROL, KeyEvent.VK_S);
        NekkoraDialog migrate = waitDialog("Atualizar o formato do projeto?");
        captureWindow(migrate, "06-confirmar-migracao");
        clickButtonWithText(migrate, "Cancelar");
        waitClosed(migrate);
        sleep(500);
        check("cancelar a migração: arquivo 1.0 intacto, nenhuma cópia, continua não salvo", sha10.equals(sha(file)) && listProjects().size() == 3
                && state(a).dirty());
        focusStudioWorkspace();
        shortcut("Ctrl+S (migração)", () -> dialogShowing("Atualizar o formato do projeto?"), KeyEvent.VK_CONTROL, KeyEvent.VK_S);
        migrate = waitDialog("Atualizar o formato do projeto?");
        clickButtonWithText(migrate, "Atualizar e salvar");
        waitClosed(migrate);
        waitFor("salvo em 1.1", 15, () -> !state(a).dirty());
        Path backup = projects.resolve("cadastro-008 (formato 1.0.0).nekkora");
        check("cópia de segurança idêntica ao arquivo 1.0.0", Files.exists(backup) && sha10.equals(sha(backup)));
        check("arquivo do projeto agora em 1.1.0 com variables.json", ProjectFormat.FORMAT_VERSION_1_1.equals(formatOf(file))
                && entries(file).containsKey(ProjectFormat.VARIABLES));
        String vars = new String(entries(file).get(ProjectFormat.VARIABLES), StandardCharsets.UTF_8);
        check("variables.json só com declarações: sem valores (nenhum dígito de CPF, nenhum campo de valor)", !vars.contains("00000000000")
                && !vars.contains("\"value\"") && vars.contains("\"constant\":null"));
        captureStudio("06-salvo-1.1");
        closeActiveProject();
        openByDialog(file);
        Automation reopened = active();
        check("reaberto (1.1): 2 variáveis, referências preservadas, formato no estado = 1.1.0", edt(() -> reopened.variables().size() == 2
                && state(reopened).fileFormat().orElse("").equals(ProjectFormat.FORMAT_VERSION_1_1)));
        closeActiveProject();
        openByDialog(backup);
        Automation legacy = active();
        check("cópia 1.0.0 aberta pelo leitor 1.0 (sem variáveis, formato 1.0.0)", edt(() -> legacy.variables().isEmpty()
                && state(legacy).fileFormat().orElse("").equals(ProjectFormat.FORMAT_VERSION)));
        captureStudio("06-copia-1.0-aberta");
        closeActiveProject();

        // ================================================================ Cenário 7 — projeto antigo: não sobrescrito, não executado, sem credenciais
        openByDialog(old);
        Automation antigo = active();
        check("projeto da STUDIO-006 aberto; nenhum diálogo pedindo credenciais", edt(() -> openDialogs().isEmpty()));
        validate();
        ValidationReport r7 = report();
        check("regras 1.1 sobre o projeto antigo: credencial só alias (CREDENTIAL_RESOLUTION_UNAVAILABLE) e referência sem declaração apontada",
                r7.findings().stream().anyMatch(f -> f.code() == FindingCode.CREDENTIAL_RESOLUTION_UNAVAILABLE)
                        && r7.findings().stream().anyMatch(f -> f.code() == FindingCode.VARIABLE_NOT_DECLARED));
        check("Executar continua indisponível", !edt(() -> window.studio().view().actions().get(StudioActions.RUN).isEnabled()));
        captureStudio("07-projeto-antigo-validado");
        check("validar não altera o projeto antigo (sem *, sem gravação)", !state(antigo).dirty() && oldSha.equals(sha(old))
                && oldModified == Files.getLastModifiedTime(old).toMillis());
        closeActiveProject();
        check("fechado sem gravar: arquivo antigo idêntico e ainda 1.0.0", oldSha.equals(sha(old)) && ProjectFormat.FORMAT_VERSION.equals(formatOf(old)));

        // ================================================================ Cenário 8 — quatro temas e 5.000 etapas
        long t0 = System.nanoTime();
        openByDialog(big);
        metrics.put("abrir5000Ms", (System.nanoTime() - t0) / 1_000_000);
        Automation volume = active();
        check("projeto de 5.000 etapas e 1.000 variáveis aberto", edt(() -> volume.stepCount() == 5_000 && volume.variables().size() == 1_000));
        focusStudioWorkspace();
        long t1 = System.nanoTime();
        shortcut("Ctrl+Shift+B (5.000)", () -> edt(() -> window.studio().view().variablesVisible()
                && window.studio().view().variablesPanel().rows().size() == 1_000), "Variáveis e dependências", KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT,
                KeyEvent.VK_B);
        metrics.put("painelVariaveis5000Ms", (System.nanoTime() - t1) / 1_000_000);
        long t2 = System.nanoTime();
        long edt0 = edtNanos(() -> window.studio().controller().validateActive());
        metrics.put("validar5000EdtMs", edt0 / 1_000_000);
        waitFor("relatório 5.000", 20, () -> edt(() -> window.studio().view().validationVisible()));
        metrics.put("validar5000Ms", (System.nanoTime() - t2) / 1_000_000);
        long renameEdt = edtNanos(() -> window.studio().service().renameVariable(volume.id(), volume.variables().get(0).id(), "renomeada.volume"));
        metrics.put("renomear5000EdtMs", renameEdt / 1_000_000);
        check("5.000 etapas: abrir, painel, validar e renomear sem travar (" + metrics + ")", (long) metrics.get("painelVariaveis5000Ms") < 5_000
                && (long) metrics.get("validar5000EdtMs") < 2_000 && (long) metrics.get("renomear5000EdtMs") < 2_000);
        focusStudioWorkspace();
        shortcut("Ctrl+Shift+B", () -> edt(() -> window.studio().view().variablesVisible()), "Variáveis e dependências", KeyEvent.VK_CONTROL,
                KeyEvent.VK_SHIFT, KeyEvent.VK_B);
        captureStudio("08-5000-etapas-variaveis");
        for (NekkoraTheme theme : NekkoraThemes.all()) {
            edt(() -> {
                com.nekkora.ui.theme.ThemeManager.setTheme(theme);
                return null;
            });
            sleep(600);
            check("tema " + theme.displayName() + ": painel de variáveis visível", edt(() -> window.studio().view().variablesPanel().isShowing()));
            captureStudio("08-tema-" + theme.id());
        }
        edt(() -> {
            com.nekkora.ui.theme.ThemeManager.setTheme(NekkoraThemes.SAKURA_DARK);
            return null;
        });

        // ================================================================ privacidade dos registros
        String logs = String.join("\n", logRecords);
        Files.writeString(out.toPath().resolve("S008-logs-da-aplicacao.txt"), logs, StandardCharsets.UTF_8);
        check("logs só com códigos e contagens: sem nomes de variáveis nem valores", !logs.contains("cliente.cpf") && !logs.contains("titular.cpf")
                && !logs.contains("resultado.protocolo") && !logs.contains("00000000000"));
        List<String> recovery = recoveryTexts();
        check("recuperação sem valores de execução nem o valor fixo recusado (" + recovery.size() + " fotografia(s))",
                recovery.stream().noneMatch(t -> t.contains("00000000000") || t.contains("\"value\"")));
        writeMetrics();
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
        sleep(200);
        if (!edt(() -> window.frame().isActive())) {
            Rectangle b = edt(() -> window.frame().getBounds());
            robot.mouseMove(b.x + b.width / 2, b.y + 12);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        }
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
        File f = new File(out, "S008-" + name + ".png");
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
        try (PrintWriter w = new PrintWriter(new File(out, "S008-homologacao-log.txt"), StandardCharsets.UTF_8)) {
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
