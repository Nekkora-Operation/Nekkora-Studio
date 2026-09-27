package com.nekkora.showcase.studio;

import com.nekkora.showcase.pages.DataPages;
import com.nekkora.showcase.pages.WorkflowSearchPages;
import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.NekkoraFrame;
import com.nekkora.ui.action.NekkoraAction;
import com.nekkora.ui.action.NekkoraActionRegistry;
import com.nekkora.ui.action.NekkoraKeys;
import com.nekkora.ui.brand.NekkoraLogo;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.button.NekkoraIconButton;
import com.nekkora.ui.container.NekkoraCard;
import com.nekkora.ui.container.NekkoraScrollPane;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.core.Severity;
import com.nekkora.ui.core.Status;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.data.NekkoraAvatar;
import com.nekkora.ui.data.NekkoraIconMarker;
import com.nekkora.ui.data.NekkoraStatusBadge;
import com.nekkora.ui.data.NekkoraTable;
import com.nekkora.ui.data.NekkoraTree;
import com.nekkora.ui.data.NekkoraTreeNode;
import com.nekkora.ui.dialog.NekkoraConfirmationDialog;
import com.nekkora.ui.feedback.NekkoraInlineAlert;
import com.nekkora.ui.feedback.NekkoraToast;
import com.nekkora.ui.file.NekkoraFilePathField;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.input.NekkoraCheckBox;
import com.nekkora.ui.input.NekkoraComboBox;
import com.nekkora.ui.input.NekkoraDateField;
import com.nekkora.ui.input.NekkoraDecimalField;
import com.nekkora.ui.input.NekkoraIntegerField;
import com.nekkora.ui.input.NekkoraSwitch;
import com.nekkora.ui.input.NekkoraTextArea;
import com.nekkora.ui.input.NekkoraTextField;
import com.nekkora.ui.input.NekkoraTimeField;
import com.nekkora.ui.navigation.NekkoraBreadcrumb;
import com.nekkora.ui.navigation.NekkoraPopupMenu;
import com.nekkora.ui.navigation.NekkoraSidebar;
import com.nekkora.ui.navigation.NekkoraSidebarGroup;
import com.nekkora.ui.navigation.NekkoraSidebarItem;
import com.nekkora.ui.navigation.NekkoraToolbar;
import com.nekkora.ui.navigation.NekkoraToolbarButton;
import com.nekkora.ui.search.NekkoraGlobalSearch;
import com.nekkora.ui.studio.NekkoraInspector;
import com.nekkora.ui.studio.NekkoraNavigator;
import com.nekkora.ui.studio.NekkoraPropertiesGroup;
import com.nekkora.ui.studio.NekkoraStatusBar;
import com.nekkora.ui.studio.NekkoraWorkArea;
import com.nekkora.ui.studio.NekkoraWorkbench;
import com.nekkora.ui.technical.NekkoraIdentifierLabel;
import com.nekkora.ui.technical.NekkoraJsonViewer;
import com.nekkora.ui.technical.NekkoraLogViewer;
import com.nekkora.ui.theme.ThemeManager;
import com.nekkora.ui.typography.NekkoraCaption;
import com.nekkora.ui.typography.NekkoraCodeText;
import com.nekkora.ui.typography.NekkoraHeading;
import com.nekkora.ui.typography.NekkoraText;
import com.nekkora.ui.typography.TextTone;
import com.nekkora.ui.workflow.NekkoraStepper;
import com.nekkora.ui.workflow.NekkoraTimeline;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.tree.TreePath;

/**
 * Tela fictícia de Studio montada <b>exclusivamente</b> com componentes da Nekkora Swing UI:
 * Sidebar + Navegador (árvore) + Abas + Área de trabalho + Inspector + Toolbar + Status + Diálogo.
 * Nenhuma cor, fonte, borda ou espaçamento é decidido aqui.
 */
public final class StudioDemo {
    private final NekkoraWorkbench workbench = new NekkoraWorkbench();
    private final NekkoraWorkArea workArea = new NekkoraWorkArea();
    private final NekkoraInspector inspector = new NekkoraInspector("Propriedades");
    private final NekkoraActionRegistry actions = new NekkoraActionRegistry();
    private final NekkoraTimeline history = new NekkoraTimeline();
    private final NekkoraCaption themeLabel = NekkoraStatusBar.item(IconName.SUN, "");
    private NekkoraToolbarButton runButton;
    private NekkoraTree tree;

    private StudioDemo() {}

    /** Monta a tela (sem janela). */
    public static JComponent build() {
        return new StudioDemo().create();
    }

    /** Abre a tela em uma janela própria. */
    public static void open() {
        NekkoraFrame f = new NekkoraFrame("Nekkora Studio — tela fictícia");
        f.getContentPane().add(build(), BorderLayout.CENTER);
        f.sizeToScreen();
        f.setVisible(true);
    }

    private JComponent create() {
        registerActions();
        workbench.setSidebar(sidebar());
        workbench.setToolbar(toolbar());
        workbench.setNavigator(navigator());
        workbench.setWorkspace(workArea);
        workbench.setInspector(inspector);
        workbench.setStatusBar(statusBar());
        actions.bindShortcuts(workbench);
        openOverview();
        showInspectorFor(tree.getSelectedNode());
        return workbench;
    }

    // ------------------------------------------------------------------ Ações

    private void registerActions() {
        actions.register(NekkoraAction.of("file.save", "Salvar", () -> NekkoraToast.show(workbench, Severity.SUCCESS, "Alterações salvas", null))
                .icon(IconName.SAVE).shortcut(NekkoraKeys.shortcut(KeyEvent.VK_S)).category("Arquivo"));
        actions.register(NekkoraAction.of("run.start", "Executar", this::run)
                .icon(IconName.PLAY).shortcut(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0)).category("Execução"));
        actions.register(NekkoraAction.of("run.stop", "Parar", () -> NekkoraToast.show(workbench, Severity.WARNING, "Execução interrompida", null))
                .icon(IconName.STOP).category("Execução"));
        actions.register(NekkoraAction.of("view.theme", "Próximo tema", StudioDemo::cycleTheme)
                .icon(IconName.SUN).shortcut(NekkoraKeys.shortcut(KeyEvent.VK_T, KeyEvent.SHIFT_DOWN_MASK)).category("Exibir"));
        actions.register(NekkoraAction.of("item.delete", "Excluir", this::deleteSelected)
                .icon(IconName.TRASH).category("Item").danger());
    }

    private void run() {
        runButton.setLoading(true);
        Timer t = new Timer(1800, e -> {
            runButton.setLoading(false);
            history.addItem(LocalTime.now().withNano(0).toString(), "Execução manual concluída", "Disparada pela toolbar.", Status.SUCCESS);
            NekkoraToast.show(workbench, Severity.SUCCESS, "Execução concluída", "1.280 itens processados.");
        });
        t.setRepeats(false);
        t.start();
    }

    private void deleteSelected() {
        NekkoraTreeNode n = tree.getSelectedNode();
        if (n == null) return;
        if (NekkoraConfirmationDialog.confirmDanger(workbench, "Excluir \"" + n.getLabel() + "\"?",
                "O item e seu histórico serão removidos. Esta ação não pode ser desfeita.", "Excluir")) {
            NekkoraToast.show(workbench, Severity.SUCCESS, "Item excluído", n.getLabel());
        }
    }

    private static void cycleTheme() {
        List<NekkoraTheme> all = NekkoraThemes.all();
        ThemeManager.setTheme(all.get((all.indexOf(ThemeManager.current()) + 1) % all.size()));
    }

    // ------------------------------------------------------------------ Estrutura

    private JComponent sidebar() {
        NekkoraSidebar s = new NekkoraSidebar();
        s.setHeader(new NekkoraLogo(NekkoraLogo.Variant.COMPACT, 22));
        NekkoraSidebarGroup main = s.addGroup("Studio");
        main.addItem(new NekkoraSidebarItem("home", "Início", IconName.HOME));
        NekkoraSidebarItem projects = main.addItem(new NekkoraSidebarItem("projects", "Projetos", IconName.FOLDER));
        main.addItem(new NekkoraSidebarItem("runs", "Execuções", IconName.EXECUTIONS)).setCount(3);
        main.addItem(new NekkoraSidebarItem("envs", "Ambientes", IconName.SERVER));
        NekkoraSidebarGroup account = s.addGroup("Conta");
        account.addItem(new NekkoraSidebarItem("settings", "Configurações", IconName.SETTINGS));
        s.select(projects);
        NekkoraStack me = NekkoraStack.horizontal(Spacing.SPACE_4).align(NekkoraStack.Align.CENTER);
        me.setPadding(Spacing.SPACE_3, Spacing.SPACE_4);
        me.add(new NekkoraAvatar("Ana Souza", 28));
        NekkoraStack who = NekkoraStack.vertical(Spacing.SPACE_1);
        who.add(new NekkoraText("Ana Souza"));
        who.add(new NekkoraCaption("Workspace Financeiro"));
        me.add(who);
        s.setFooter(me);
        return s;
    }

    private JComponent toolbar() {
        NekkoraToolbar tb = new NekkoraToolbar();
        tb.add(new NekkoraBreadcrumb().item("Projetos", () -> { }).item("Financeiro", () -> { }).item("Conciliação bancária"));
        tb.addGlue();
        NekkoraGlobalSearch search = new NekkoraGlobalSearch("Buscar…  (Ctrl+K)");
        search.setProvider(WorkflowSearchPages::search);
        search.onOpen(item -> NekkoraToast.show(workbench, Severity.INFO, "Abrir: " + item.title(), null));
        NekkoraKeys.bind(workbench, NekkoraKeys.shortcut(KeyEvent.VK_K), "studio.search", () -> search.getField().requestFocusInWindow());
        tb.add(new com.nekkora.showcase.kit.Fixed(search, 260, 0));
        tb.addGap(Spacing.SPACE_4);
        tb.add(new NekkoraToolbarButton(actions.find("file.save").orElseThrow(), false));
        runButton = new NekkoraToolbarButton(actions.find("run.start").orElseThrow(), true);
        tb.add(runButton);
        tb.add(new NekkoraToolbarButton(actions.find("run.stop").orElseThrow(), false));
        tb.addSeparator();
        NekkoraToolbarButton nav = new NekkoraToolbarButton(IconName.PANEL_LEFT, "Navegador").withShortcut(NekkoraKeys.shortcut(KeyEvent.VK_B));
        nav.addActionListener(e -> workbench.toggleNavigator());
        NekkoraToolbarButton insp = new NekkoraToolbarButton(IconName.PANEL_RIGHT, "Propriedades")
                .withShortcut(NekkoraKeys.shortcut(KeyEvent.VK_B, KeyEvent.ALT_DOWN_MASK));
        insp.addActionListener(e -> workbench.toggleInspector());
        tb.add(nav);
        tb.add(insp);
        tb.add(themeMenuButton());
        return tb;
    }

    private JComponent themeMenuButton() {
        NekkoraIconButton b = new NekkoraIconButton(IconName.SUN, "Tema (Ctrl+Shift+T alterna)");
        b.addActionListener(e -> {
            NekkoraPopupMenu m = new NekkoraPopupMenu().header("Tema");
            for (NekkoraTheme t : NekkoraThemes.all()) {
                m.item(t.displayName(), t == ThemeManager.current() ? IconName.CHECK : (t.isDark() ? IconName.MOON : IconName.SUN),
                        null, () -> ThemeManager.setTheme(t));
            }
            m.show(b, 0, b.getHeight());
        });
        return b;
    }

    private JComponent navigator() {
        NekkoraNavigator nav = new NekkoraNavigator("Projetos");
        nav.setHeaderActions(new NekkoraIconButton(IconName.PLUS, "Novo projeto").withSize(ControlSize.SMALL),
                new NekkoraIconButton(IconName.REFRESH, "Atualizar").withSize(ControlSize.SMALL),
                new NekkoraIconButton(IconName.MORE_HORIZONTAL, "Mais").withSize(ControlSize.SMALL));
        tree = new NekkoraTree(DataPages.sampleTree());
        tree.setRootVisible(false);
        tree.expandAll();
        tree.setSelectionRow(1);
        tree.addTreeSelectionListener(e -> showInspectorFor(tree.getSelectedNode()));
        tree.onNodeActivated(n -> openNode(n));
        tree.setNodeMenu((TreePath p) -> new NekkoraPopupMenu()
                .item("Abrir", IconName.EXTERNAL_LINK, javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                        () -> openNode((NekkoraTreeNode) p.getLastPathComponent()))
                .item("Renomear", IconName.EDIT, javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), () -> { })
                .item("Duplicar", IconName.COPY, NekkoraKeys.shortcut(KeyEvent.VK_D), () -> { })
                .separator()
                .item(actions.find("item.delete").orElseThrow()));
        nav.enableSearch("Filtrar…", q -> { });
        nav.setContent(tree);
        return nav;
    }

    private JComponent statusBar() {
        NekkoraStatusBar bar = new NekkoraStatusBar();
        bar.addLeft(NekkoraStatusBar.status(Status.SUCCESS, "Sincronizado"));
        bar.addLeft(NekkoraStatusBar.item(IconName.USER, "Ana Souza"));
        bar.addRight(NekkoraStatusBar.item(IconName.CLOCK, "Última execução 14:32"));
        bar.addRight(themeLabel);
        updateThemeLabel();
        ThemeManager.addThemeListener((o, n) -> updateThemeLabel());
        return bar;
    }

    private void updateThemeLabel() {
        themeLabel.setText(ThemeManager.current().displayName());
    }

    // ------------------------------------------------------------------ Documentos

    private void openNode(NekkoraTreeNode n) {
        if (n == null || !n.isLeaf()) return;
        if (n.getIcon() == IconName.BRACES) {
            NekkoraJsonViewer json = new NekkoraJsonViewer();
            json.setJson("{\"ambiente\":\"producao\",\"tentativas\":3,\"timeout\":12.5,\"notificar\":[\"ana@empresa.com\"],\"ativo\":true}");
            workArea.openTab(n.getLabel(), n.getLabel(), IconName.BRACES, json);
        } else if ("Conciliação bancária".equals(n.getLabel())) {
            openOverview();
        } else {
            NekkoraLogViewer log = new NekkoraLogViewer();
            log.append(NekkoraLogViewer.Level.INFO, "Abrindo " + n.getLabel());
            log.append(NekkoraLogViewer.Level.SUCCESS, "Pronto para editar");
            workArea.openTab(n.getLabel(), n.getLabel(), IconName.WORKFLOW, log);
        }
    }

    private void openOverview() {
        NekkoraStack doc = NekkoraStack.vertical(Spacing.SPACE_8);
        doc.setPadding(Spacing.SPACE_8, Spacing.SPACE_9);

        NekkoraStack title = NekkoraStack.horizontal(Spacing.SPACE_5).align(NekkoraStack.Align.CENTER);
        title.add(new NekkoraIconMarker(IconName.WORKFLOW, Tone.ACCENT, true, 40));
        NekkoraStack texts = NekkoraStack.vertical(Spacing.SPACE_1);
        NekkoraStack name = NekkoraStack.horizontal(Spacing.SPACE_4).align(NekkoraStack.Align.CENTER);
        name.add(new NekkoraHeading("Conciliação bancária", NekkoraHeading.Level.H1));
        name.add(new NekkoraStatusBadge(Status.SUCCESS, "Publicado"));
        texts.add(name);
        texts.add(new NekkoraText("Compara extratos com lançamentos e aponta divergências.", TextTone.SECONDARY));
        title.add(texts);
        doc.add(title);
        NekkoraStack actionsRow = NekkoraStack.horizontal(Spacing.SPACE_4).align(NekkoraStack.Align.CENTER);
        actionsRow.add(NekkoraButton.primary("Executar agora").withIcon(IconName.PLAY).onClick(e -> run()));
        actionsRow.add(NekkoraButton.secondary("Histórico").withIcon(IconName.HISTORY));
        actionsRow.add(NekkoraButton.ghost("Duplicar").withIcon(IconName.COPY));
        actionsRow.addGlue();
        doc.add(actionsRow);

        NekkoraStepper steps = new NekkoraStepper("Origem", "Mapeamento", "Validação", "Publicação");
        steps.setCurrent(3);
        doc.add(steps);

        JPanel stats = new JPanel(new GridLayout(1, 3, 16, 0));
        stats.setOpaque(false);
        stats.add(stat("Execuções (24h)", "156", Status.INFO, "+12% que ontem"));
        stats.add(stat("Taxa de sucesso", "98,1%", Status.SUCCESS, "meta: 97%"));
        stats.add(stat("Itens com atenção", "3", Status.WARNING, "aguardando revisão"));
        doc.add(stats);

        doc.add(new NekkoraInlineAlert(Severity.INFO, "Dica", "Use F5 para executar e Ctrl+S para salvar. Ctrl+B recolhe o navegador."));

        NekkoraCard runs = new NekkoraCard("Execuções recentes", "Clique direito em uma linha para ações");
        NekkoraTable table = new NekkoraTable(DataPages.sampleModel());
        table.setRowMenu(r -> new NekkoraPopupMenu().item("Ver detalhes", IconName.EYE, null, () -> { })
                .item("Reexecutar", IconName.REFRESH, null, () -> { }));
        runs.setContent(new com.nekkora.showcase.kit.Fixed(table.scrollable(), 0, 300));
        doc.add(runs);

        history.addItem("14:32", "Execução concluída", "1.280 itens processados em 3 min 12 s.", Status.SUCCESS)
                .addItem("14:29", "Validação com atenção", "Uma etapa demorou mais que o esperado.", Status.WARNING)
                .addItem("09:10", "Versão 1.4 publicada", "Por Ana Souza.", Status.INFO);
        NekkoraCard hist = new NekkoraCard("Histórico", null);
        hist.setContent(history);
        doc.add(hist);

        workArea.openTab("Conciliação bancária", "Conciliação bancária", IconName.WORKFLOW, NekkoraScrollPane.verticalOnly(doc));
    }

    private static JComponent stat(String label, String value, Status status, String hint) {
        NekkoraCard c = new NekkoraCard();
        NekkoraStack s = NekkoraStack.vertical(Spacing.SPACE_2);
        s.add(new NekkoraText(label, TextTone.SECONDARY));
        s.add(new NekkoraHeading(value, NekkoraHeading.Level.DISPLAY_M));
        s.add(NekkoraStatusBar.status(status, hint));
        c.setContent(s);
        return c;
    }

    // ------------------------------------------------------------------ Inspector

    private void showInspectorFor(NekkoraTreeNode n) {
        inspector.clearGroups();
        if (n == null || !n.isLeaf()) {
            inspector.showEmpty();
            return;
        }
        boolean file = n.getIcon() == IconName.BRACES;
        inspector.setSubject(n.getIcon(), n.getIconTone() != null ? n.getIconTone() : Tone.ACCENT, n.getLabel(),
                file ? "Arquivo · Financeiro" : "Projeto · " + ((NekkoraTreeNode) n.getParent()).getLabel());
        inspector.setSubjectExtra(n.getStatus() == null ? null : new NekkoraStatusBadge(n.getStatus()).withSize(com.nekkora.ui.data.NekkoraBadge.Size.SMALL));

        NekkoraPropertiesGroup general = inspector.addGroup("Geral");
        NekkoraTextField name = new NekkoraTextField();
        name.setText(n.getLabel());
        general.addRow("Nome", name);
        general.addRow("Descrição", new NekkoraTextArea("Descreva o objetivo…", 3).scrollable());
        general.addRow("Modelo", new NekkoraComboBox<>(List.of("Planilha → sistema", "E-mail → pasta", "Em branco")));
        general.addRow("Ativo", new NekkoraSwitch(null, true));
        if (file) return;

        NekkoraPropertiesGroup exec = inspector.addGroup("Execução");
        NekkoraIntegerField retries = new NekkoraIntegerField();
        retries.setRange(0, 10);
        retries.setValue(3);
        exec.addRow("Tentativas", retries);
        NekkoraDecimalField timeout = new NekkoraDecimalField("0,0", Locale.forLanguageTag("pt-BR"));
        timeout.setScale(1);
        timeout.setValue(new java.math.BigDecimal("12.5"));
        exec.addRow("Tempo limite (s)", timeout);
        NekkoraDateField start = new NekkoraDateField();
        start.setValue(LocalDate.of(2026, 10, 1));
        exec.addRow("Início", start);
        NekkoraTimeField at = new NekkoraTimeField();
        at.setValue(LocalTime.of(6, 0));
        exec.addRow("Horário", at);
        exec.addRow("", new NekkoraCheckBox("Notificar em falha", true));

        NekkoraPropertiesGroup tech = inspector.addGroup("Técnico");
        tech.addRow("ID", new NekkoraIdentifierLabel("prj_7f3a91c2-5d1e-4b7a-9c61-2e4d8f7a1b3c"));
        NekkoraFilePathField folder = new NekkoraFilePathField(NekkoraFilePathField.Mode.FOLDER, "Pasta do projeto");
        folder.setPath(java.nio.file.Path.of("C:", "Nekkora", "financeiro"));
        tech.addFullRow(folder);
        tech.addRow("Versão", new NekkoraCodeText("1.4.0"));
        tech.setExpanded(false);

        NekkoraPropertiesGroup danger = inspector.addGroup("Zona de perigo");
        danger.addFullRow(NekkoraButton.danger("Excluir projeto").withIcon(IconName.TRASH).onClick(e -> deleteSelected()));
        danger.setExpanded(false);
    }
}
