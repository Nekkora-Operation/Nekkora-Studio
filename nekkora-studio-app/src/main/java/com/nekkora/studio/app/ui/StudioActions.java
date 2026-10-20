package com.nekkora.studio.app.ui;

import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.action.NekkoraAction;
import com.nekkora.ui.action.NekkoraActionRegistry;
import com.nekkora.ui.action.NekkoraKeys;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.theme.ThemeManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.KeyStroke;

/**
 * Ações do Studio (rótulo, ícone, atalho) registradas no {@link NekkoraActionRegistry} do design system.
 * A ação futura (executar) existe apenas <b>desabilitada</b>: não tem efeito e não produz nenhum resultado fictício.
 * "Gravar" é real desde a STUDIO-002; abrir, salvar, salvar como e fechar projeto, desde a STUDIO-006.
 */
public final class StudioActions {
    public static final String NEW_AUTOMATION = "studio.automation.new";
    public static final String NEW_SESSION = "studio.session.new";
    public static final String NEW_STEP = "studio.step.new";
    public static final String RENAME_SESSION = "studio.session.rename";
    /** STUDIO-004: editar o item selecionado (F2), desfazer (Ctrl+Z) e refazer (Ctrl+Y). */
    public static final String EDIT = "studio.edit.selected";
    public static final String UNDO = "studio.edit.undo";
    public static final String REDO = "studio.edit.redo";
    public static final String NEXT_THEME = "studio.view.theme";
    public static final String RECORD = "studio.recorder.record";
    public static final String RUN = "studio.future.run";
    /** STUDIO-006: projetos. */
    public static final String SAVE = "studio.project.save";
    public static final String SAVE_AS = "studio.project.saveAs";
    public static final String OPEN = "studio.project.open";
    public static final String CLOSE_PROJECT = "studio.project.close";
    public static final String RECOVERY = "studio.project.recovery";
    public static final String EXIT = "studio.exit";
    /** STUDIO-007: validação estrutural da automação ativa (não executa nada). */
    public static final String VALIDATE = "studio.automation.validate";
    /** STUDIO-008: variáveis declaradas e dependências da automação ativa. */
    public static final String VARIABLES = "studio.automation.variables";
    /** STUDIO-009: debug guiado no navegador (inspecionar, destacar, confirmar/corrigir — nunca executar). */
    public static final String GUIDED_DEBUG = "studio.automation.guidedDebug";
    /** STUDIO-010: Automação → Gerar documentação → PDD visual. */
    public static final String VISUAL_PDD = "studio.automation.visualPdd";
    /** STUDIO-011 (NEKKORA-E2E-001): gerador com configuração manual — desde a STUDIO-014, ferramenta AVANÇADA. */
    public static final String PYTHON_CODEGEN = "studio.automation.pythonCodegen";
    /** STUDIO-014: Automação → Desenvolver automação (um clique, sem formulário). */
    public static final String DEVELOP = "studio.automation.develop";

    /** Ações que dependem de missões futuras e permanecem desabilitadas. */
    public static final List<String> FUTURE = List.of(RUN);
    /** Rótulo do botão de gravação (muda só com a confirmação da extensão). */
    public static final String RECORD_LABEL = "Gravar";

    private final NekkoraActionRegistry registry = new NekkoraActionRegistry();
    private Runnable recordHandler = () -> { };

    public StudioActions(StudioController controller) {
        registry.register(NekkoraAction.of(NEW_AUTOMATION, "Nova automação", controller::newAutomation)
                .icon(IconName.PLUS).shortcut(NekkoraKeys.shortcut(KeyEvent.VK_N)).category("Automação"));
        registry.register(NekkoraAction.of(NEW_SESSION, "Nova sessão", controller::newSession)
                .icon(IconName.LAYERS).shortcut(NekkoraKeys.shortcut(KeyEvent.VK_N, InputEvent.SHIFT_DOWN_MASK)).category("Automação"));
        registry.register(NekkoraAction.of(NEW_STEP, "Nova etapa", () -> controller.newStep(null))
                .icon(IconName.LIST).shortcut(NekkoraKeys.shortcut(KeyEvent.VK_E)).category("Automação"));
        registry.register(NekkoraAction.of(RENAME_SESSION, "Renomear sessão", controller::renameSelectedSession)
                .icon(IconName.EDIT).category("Automação"));
        registry.register(NekkoraAction.of(EDIT, "Editar", controller::editSelected).icon(IconName.EDIT)
                .shortcut(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)).category("Edição")
                .description("Editar a automação, a sessão ou a etapa selecionada."));
        // Atalhos da janela: dentro de um campo de texto, Ctrl+Z/Ctrl+Y desfazem a digitação do próprio campo (o campo
        // trata o atalho antes) — e, por segurança, nunca disparam um desfazer estrutural com o foco num campo.
        registry.register(NekkoraAction.of(UNDO, "Desfazer", () -> {
            if (!typing()) controller.undo();
        }).icon(IconName.ARROW_LEFT).shortcut(NekkoraKeys.shortcut(KeyEvent.VK_Z)).category("Edição")
                .description("Desfazer a última edição desta automação (somente em memória)."));
        registry.register(NekkoraAction.of(REDO, "Refazer", () -> {
            if (!typing()) controller.redo();
        }).icon(IconName.ARROW_RIGHT).shortcut(NekkoraKeys.shortcut(KeyEvent.VK_Y)).category("Edição")
                .description("Refazer a última edição desfeita desta automação."));
        registry.register(NekkoraAction.of(NEXT_THEME, "Próximo tema", StudioActions::cycleTheme)
                .icon(IconName.SUN).shortcut(NekkoraKeys.shortcut(KeyEvent.VK_T, InputEvent.SHIFT_DOWN_MASK)).category("Exibir"));
        registry.register(NekkoraAction.of(RECORD, RECORD_LABEL, () -> recordHandler.run()).icon(IconName.PLAY_CIRCLE)
                .shortcut(NekkoraKeys.shortcut(KeyEvent.VK_R, InputEvent.SHIFT_DOWN_MASK)).category("Gravação")
                .description("Iniciar uma gravação técnica numa aba autorizada do Chrome."));
        registry.register(NekkoraAction.of(OPEN, "Abrir projeto…", controller::openProject).icon(IconName.FOLDER_OPEN)
                .shortcut(NekkoraKeys.shortcut(KeyEvent.VK_O)).category("Arquivo").description("Abrir um projeto .nekkora salvo neste computador."));
        registry.register(NekkoraAction.of(SAVE, "Salvar", controller::saveActive).icon(IconName.SAVE)
                .shortcut(NekkoraKeys.shortcut(KeyEvent.VK_S)).category("Arquivo")
                .description("Salvar a automação ativa no arquivo do projeto (arquivo local, não criptografado)."));
        registry.register(NekkoraAction.of(SAVE_AS, "Salvar como…", controller::saveActiveAs).icon(IconName.SAVE)
                .shortcut(NekkoraKeys.shortcut(KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK)).category("Arquivo")
                .description("Salvar a automação ativa em outro arquivo, mantendo a mesma identidade."));
        registry.register(NekkoraAction.of(CLOSE_PROJECT, "Fechar projeto", controller::closeActiveProject).icon(IconName.CLOSE).category("Arquivo")
                .description("Fechar o projeto ativo (pergunta antes se houver alterações não salvas)."));
        registry.register(NekkoraAction.of(RECOVERY, "Recuperação automática…", controller::configureRecovery).icon(IconName.HISTORY).category("Arquivo")
                .description("Ativar, desativar ou limpar as cópias de recuperação das alterações não salvas."));
        registry.register(NekkoraAction.of(EXIT, "Sair", controller::requestExit).icon(IconName.X_CIRCLE).category("Arquivo")
                .description("Fechar o Nekkora Studio."));
        registry.register(NekkoraAction.of(VALIDATE, "Validar automação", controller::validateActive).icon(IconName.CHECK_CIRCLE)
                .shortcut(NekkoraKeys.shortcut(KeyEvent.VK_V, InputEvent.SHIFT_DOWN_MASK)).category("Automação")
                .description("Diagnóstico estrutural da automação ativa: parâmetros, elementos, localizadores e dependências. Não executa nenhuma etapa."));
        registry.register(NekkoraAction.of(VARIABLES, "Variáveis e dependências…", controller::showVariables).icon(IconName.BRACES)
                .shortcut(NekkoraKeys.shortcut(KeyEvent.VK_B, InputEvent.SHIFT_DOWN_MASK)).category("Automação")
                .description("Declarar e revisar as variáveis do processo e as etapas que as usam. Nenhum valor de execução é guardado."));
        registry.register(NekkoraAction.of(GUIDED_DEBUG, "Depurar no navegador…", () -> guidedDebugHandler.run()).icon(IconName.SEARCH)
                .shortcut(NekkoraKeys.shortcut(KeyEvent.VK_D, InputEvent.SHIFT_DOWN_MASK)).category("Automação")
                .description("Reencontrar e destacar no Chrome o alvo de cada etapa, com Continuar ou Corrigir. Nada é clicado, preenchido ou enviado."));
        registry.register(NekkoraAction.of(VISUAL_PDD, "PDD visual…", () -> visualPddHandler.run()).icon(IconName.FILE).category("Automação")
                .description("Documento PDF do processo mapeado: sessões, instruções e capturas sanitizadas do laboratório. Não altera a automação."));
        registry.register(NekkoraAction.of(DEVELOP, "Desenvolver automação", () -> developHandler.run()).icon(IconName.PLAY_CIRCLE)
                .shortcut(NekkoraKeys.shortcut(KeyEvent.VK_G, InputEvent.SHIFT_DOWN_MASK)).category("Automação")
                .description("Analisa o PDD e a gravação, escolhe as Libs do framework, deriva variáveis e gera o Python pelo gerador oficial. Nada é executado."));
        registry.register(NekkoraAction.of(PYTHON_CODEGEN, "Gerador avançado (configuração manual)…", () -> pythonCodegenHandler.run()).icon(IconName.FILE)
                .category("Automação").description("Ferramenta avançada: gera a partir de um arquivo de configuração escrito à mão (lote tabular). "
                        + "O caminho principal é Desenvolver automação."));
        future(RUN, "Executar", IconName.PLAY, "A execução de automações chega em uma próxima versão do Studio.");
        for (String id : List.of(NEW_SESSION, NEW_STEP, RENAME_SESSION, RECORD, EDIT, UNDO, REDO, SAVE, SAVE_AS, CLOSE_PROJECT, VALIDATE, VARIABLES,
                GUIDED_DEBUG, VISUAL_PDD, PYTHON_CODEGEN, DEVELOP)) {
            get(id).setEnabled(false);
        }
    }

    private Runnable guidedDebugHandler = () -> { };

    /** Liga "Depurar no navegador" ao apresentador do debug guiado (STUDIO-009). */
    /** STUDIO-010: abre a preparação do PDD visual. */
    public void onVisualPdd(Runnable handler) {
        this.visualPddHandler = handler == null ? () -> { } : handler;
    }

    private Runnable visualPddHandler = () -> { };

    /** STUDIO-011: abre a geração do código Python. */
    public void onPythonCodegen(Runnable handler) {
        this.pythonCodegenHandler = handler == null ? () -> { } : handler;
    }

    private Runnable pythonCodegenHandler = () -> { };

    /** STUDIO-014: abre "Desenvolver automação". */
    public void onDevelop(Runnable handler) {
        this.developHandler = handler == null ? () -> { } : handler;
    }

    private Runnable developHandler = () -> { };

    public void onGuidedDebug(Runnable handler) {
        this.guidedDebugHandler = handler == null ? () -> { } : handler;
    }

    /** Liga o botão Gravar ao Recorder (STUDIO-002). */
    public void onRecord(Runnable handler) {
        this.recordHandler = handler == null ? () -> { } : handler;
    }

    private void future(String id, String label, IconName icon, String why) {
        NekkoraAction a = NekkoraAction.of(id, label, () -> { }).icon(icon).category("Próximas versões").description(why);
        a.setEnabled(false);
        registry.register(a);
    }

    public NekkoraActionRegistry registry() {
        return registry;
    }

    public NekkoraAction get(String id) {
        return registry.find(id).orElseThrow();
    }

    /** O foco está num campo de texto (o atalho pertence ao campo). */
    private static boolean typing() {
        return java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof javax.swing.text.JTextComponent;
    }

    /** Alterna para o próximo dos quatro temas, ao vivo. */
    public static void cycleTheme() {
        List<NekkoraTheme> all = NekkoraThemes.all();
        ThemeManager.setTheme(all.get((all.indexOf(ThemeManager.current()) + 1) % all.size()));
    }
}
