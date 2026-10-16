package com.nekkora.studio.app.ui;

import com.nekkora.studio.app.workspace.Notice;
import com.nekkora.studio.app.workspace.Selection;
import com.nekkora.studio.app.workspace.StudioChange;
import com.nekkora.studio.app.workspace.StudioEvent;
import com.nekkora.studio.app.workspace.StudioState;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.ui.AutomationNavigator;
import com.nekkora.studio.ui.AutomationOverview;
import com.nekkora.studio.ui.PropertiesPresenter;
import com.nekkora.studio.ui.StudioText;
import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.action.NekkoraKeys;
import com.nekkora.ui.brand.NekkoraLogo;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.button.NekkoraIconButton;
import com.nekkora.ui.core.Severity;
import com.nekkora.ui.core.Status;
import com.nekkora.ui.feedback.NekkoraToast;
import com.nekkora.ui.feedback.NekkoraTooltips;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.navigation.NekkoraBreadcrumb;
import com.nekkora.ui.navigation.NekkoraPopupMenu;
import com.nekkora.ui.navigation.NekkoraSidebar;
import com.nekkora.ui.navigation.NekkoraSidebarGroup;
import com.nekkora.ui.navigation.NekkoraSidebarItem;
import com.nekkora.ui.navigation.NekkoraToolbar;
import com.nekkora.ui.navigation.NekkoraToolbarButton;
import com.nekkora.ui.state.NekkoraEmptyState;
import com.nekkora.ui.studio.NekkoraInspector;
import com.nekkora.ui.studio.NekkoraStatusBar;
import com.nekkora.ui.studio.NekkoraWorkArea;
import com.nekkora.ui.studio.NekkoraWorkbench;
import com.nekkora.ui.theme.ThemeListener;
import com.nekkora.ui.theme.ThemeManager;
import com.nekkora.ui.typography.NekkoraCaption;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Composição principal do Studio sobre o {@link NekkoraWorkbench}: navegação de automações | automação aberta
 * (abas) | propriedades, com toolbar e barra de status. Observa o {@link StudioState} e atualiza apenas a
 * parte afetada por cada {@link StudioEvent} — nada de reconstruir a aplicação a cada alteração.
 */
public final class StudioView {
    private final StudioState state;
    private final StudioController controller;
    private final StudioActions actions;
    private final NekkoraWorkbench workbench = new NekkoraWorkbench();
    private final AutomationNavigator navigator;
    private final NekkoraWorkArea workArea;
    private final NekkoraInspector inspector = new NekkoraInspector("Propriedades");
    private final PropertiesPresenter properties;
    private final Map<AutomationId, AutomationOverview> overviews = new LinkedHashMap<>();
    private final Map<AutomationId, String> tabKeys = new HashMap<>();
    private final NekkoraBreadcrumb breadcrumb = new NekkoraBreadcrumb();
    /** STUDIO-006: situação do projeto ATIVO (salvo, alterações não salvas, salvando, erro, conflito, recuperado). */
    private final NekkoraCaption projectStatus = NekkoraStatusBar.status(Status.DRAFT, "Nenhum projeto aberto");
    private final NekkoraCaption unsavedStatus = NekkoraStatusBar.item(IconName.ALERT, "Alterações não salvas");
    private final NekkoraCaption recoveryStatus = NekkoraStatusBar.item(IconName.HISTORY, "");
    private javax.swing.JMenuBar menuBar;
    private com.nekkora.ui.navigation.NekkoraMenu recentMenu;
    private final NekkoraCaption countStatus = NekkoraStatusBar.item(IconName.WORKFLOW, "");
    private final NekkoraCaption themeStatus = NekkoraStatusBar.item(IconName.SUN, "");
    private final java.util.List<JComponent> extraStatus = new java.util.ArrayList<>();
    private NekkoraStatusBar statusBar;
    private final ThemeListener themeListener = (oldTheme, newTheme) -> updateTheme();
    private final java.util.function.Consumer<StudioEvent> stateListener = this::onEvent;
    private boolean syncingTabs;
    private NekkoraToolbarButton undoButton;
    private NekkoraToolbarButton redoButton;
    private boolean closingTab;
    /** STUDIO-007: painel de validação abaixo da área de trabalho (como um painel de problemas), oculto até a primeira validação. */
    private final com.nekkora.studio.ui.ValidationPanel validationPanel;
    private final com.nekkora.ui.container.NekkoraPanel center = new com.nekkora.ui.container.NekkoraPanel(new java.awt.BorderLayout());
    private com.nekkora.ui.container.NekkoraSplitPane validationSplit;
    private boolean validationVisible;
    private com.nekkora.studio.validation.ValidationReport shownReport;

    public StudioView(StudioState state, StudioController controller) {
        this.state = state;
        this.controller = controller;
        this.actions = new StudioActions(controller);
        this.navigator = new AutomationNavigator(controller::newAutomation);
        navigator.onSelect(controller::openAutomation); // ativa a automação mantendo a seleção que ela já tinha
        navigator.onOpen(id -> {
            controller.openAutomation(id);
            focusOverview();
        });
        NekkoraEmptyState empty = new NekkoraEmptyState(IconName.WORKFLOW, "Nenhuma automação aberta",
                "Crie uma automação para organizar o processo em sessões e etapas, ou abra um projeto .nekkora salvo.");
        empty.addAction(NekkoraButton.primary("Nova automação").withIcon(IconName.PLUS).onClick(e -> controller.newAutomation()));
        empty.addAction(NekkoraButton.secondary("Abrir projeto…").withIcon(IconName.FOLDER_OPEN).onClick(e -> controller.openProject()));
        workArea = new NekkoraWorkArea(empty);
        workArea.setCanClose(this::onTabClosing);
        workArea.getTabs().addChangeListener(e -> onTabSelected());
        properties = new PropertiesPresenter(inspector, new PropertiesPresenter.Actions() {
            @Override
            public void addSession() {
                controller.newSession();
            }

            @Override
            public void renameSession(SessionId session) {
                controller.renameSession(session);
            }

            @Override
            public void addStep(SessionId session) {
                controller.newStep(session);
            }

            @Override
            public void openStep(StepId step) {
                controller.openStepDetails(step);
            }

            @Override
            public void editAutomation() {
                controller.editAutomation();
            }

            @Override
            public void editSession(SessionId session) {
                controller.editSession(session);
            }

            @Override
            public void moveSession(SessionId session, int delta) {
                controller.moveSession(session, delta);
            }

            @Override
            public void deleteSession(SessionId session) {
                controller.deleteSession(session);
            }

            @Override
            public void moveStep(StepId step, int delta) {
                controller.moveStep(step, delta);
            }

            @Override
            public void moveStepTo(StepId step) {
                controller.moveStepTo(step);
            }

            @Override
            public void duplicateStep(StepId step) {
                controller.duplicateStep(step);
            }

            @Override
            public void deleteStep(StepId step) {
                controller.deleteStep(step);
            }

            @Override
            public void restoreOriginal(StepId step, com.nekkora.studio.domain.StepField field) {
                controller.restoreOriginal(step, field);
            }
        });

        validationPanel = new com.nekkora.studio.ui.ValidationPanel(new com.nekkora.studio.ui.ValidationPanel.Actions() {
            @Override
            public void goTo(com.nekkora.studio.validation.Finding finding) {
                controller.goToFinding(finding);
            }

            @Override
            public void edit(com.nekkora.studio.validation.Finding finding) {
                controller.editFinding(finding);
            }

            @Override
            public void revalidate() {
                controller.validateActive();
            }

            @Override
            public void close() {
                hideValidation();
            }
        });
        controller.onValidationShown(this::showValidation);
        center.add(workArea, java.awt.BorderLayout.CENTER);

        workbench.setSidebar(sidebar());
        workbench.setToolbar(toolbar());
        workbench.setNavigator(navigator);
        workbench.setWorkspace(center);
        workbench.setInspector(inspector);
        workbench.setStatusBar(statusBar());
        actions.registry().bindShortcuts(workbench);
        workbench.getAccessibleContext().setAccessibleName("Nekkora Studio");

        state.addListener(stateListener);
        ThemeManager.addThemeListener(themeListener);
        refreshAll();
    }

    public JComponent component() {
        return workbench;
    }

    /** Para de observar estado e tema (ao fechar a janela). */
    public void dispose() {
        state.removeListener(stateListener);
        ThemeManager.removeThemeListener(themeListener);
    }

    // ------------------------------------------------------------------ estrutura

    private JComponent sidebar() {
        NekkoraSidebar s = new NekkoraSidebar();
        s.setHeader(new NekkoraLogo(NekkoraLogo.Variant.COMPACT, 22));
        NekkoraSidebarGroup g = s.addGroup("Studio");
        s.select(g.addItem(new NekkoraSidebarItem("automations", "Automações", IconName.WORKFLOW)));
        s.setCollapsed(true); // uma única área por enquanto: só ícones, mais espaço para a automação em 1366×768
        return s;
    }

    private JComponent toolbar() {
        NekkoraToolbar tb = new NekkoraToolbar();
        breadcrumb.getAccessibleContext().setAccessibleName("Local atual");
        tb.add(breadcrumb);
        tb.addGlue();
        tb.add(new NekkoraToolbarButton(actions.get(StudioActions.NEW_AUTOMATION), true));
        tb.add(new NekkoraToolbarButton(actions.get(StudioActions.NEW_SESSION), true));
        tb.add(new NekkoraToolbarButton(actions.get(StudioActions.NEW_STEP), true));
        tb.addSeparator();
        undoButton = new NekkoraToolbarButton(actions.get(StudioActions.UNDO), true);
        redoButton = new NekkoraToolbarButton(actions.get(StudioActions.REDO), true);
        tb.add(undoButton);
        tb.add(redoButton);
        tb.addSeparator();
        NekkoraToolbarButton record = new NekkoraToolbarButton(actions.get(StudioActions.RECORD), true);
        record.getAccessibleContext().setAccessibleDescription("Gravação técnica numa aba autorizada do Chrome; mostra o Recorder quando há gravação.");
        tb.add(record);
        tb.add(new NekkoraToolbarButton(actions.get(StudioActions.SAVE), true));
        NekkoraToolbarButton validate = new NekkoraToolbarButton(actions.get(StudioActions.VALIDATE), true);
        validate.getAccessibleContext().setAccessibleDescription(String.valueOf(actions.get(StudioActions.VALIDATE).getValue(javax.swing.Action.SHORT_DESCRIPTION)));
        tb.add(validate);
        for (String id : StudioActions.FUTURE) {
            NekkoraToolbarButton b = new NekkoraToolbarButton(actions.get(id), false);
            NekkoraTooltips.install(b, actions.get(id).getValue(javax.swing.Action.NAME) + " — "
                    + actions.get(id).getValue(javax.swing.Action.SHORT_DESCRIPTION), null);
            b.getAccessibleContext().setAccessibleDescription(String.valueOf(actions.get(id).getValue(javax.swing.Action.SHORT_DESCRIPTION)));
            tb.add(b);
        }
        tb.addSeparator();
        NekkoraToolbarButton nav = new NekkoraToolbarButton(IconName.PANEL_LEFT, "Navegação")
                .withShortcut(NekkoraKeys.shortcut(KeyEvent.VK_B));
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

    private JComponent statusBar() {
        NekkoraStatusBar bar = new NekkoraStatusBar();
        bar.addLeft(projectStatus);
        bar.addLeft(unsavedStatus);
        bar.addLeft(countStatus);
        bar.addRight(recoveryStatus);
        bar.addRight(themeStatus);
        statusBar = bar;
        return bar;
    }

    /**
     * STUDIO-006: barra de menus "Arquivo" (a janela real a instala; testes a inspecionam sem janela). Os atalhos são os
     * das ações (Ctrl+O, Ctrl+S, Ctrl+Shift+S), sem conflito com os existentes.
     */
    public javax.swing.JMenuBar menuBar() {
        if (menuBar != null) return menuBar;
        com.nekkora.ui.navigation.NekkoraMenu file = new com.nekkora.ui.navigation.NekkoraMenu("Arquivo");
        file.setMnemonic(KeyEvent.VK_A);
        file.add(new com.nekkora.ui.navigation.NekkoraMenuItem("Novo projeto", IconName.PLUS, actions.get(StudioActions.NEW_AUTOMATION).shortcutKey()) {
            {
                addActionListener(e -> controller.newAutomation());
            }
        });
        file.item(actions.get(StudioActions.OPEN));
        file.separator();
        file.item(actions.get(StudioActions.SAVE));
        file.item(actions.get(StudioActions.SAVE_AS));
        file.separator();
        recentMenu = new com.nekkora.ui.navigation.NekkoraMenu("Projetos recentes");
        recentMenu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                rebuildRecent();
            }

            @Override
            public void menuDeselected(javax.swing.event.MenuEvent e) {
            }

            @Override
            public void menuCanceled(javax.swing.event.MenuEvent e) {
            }
        });
        rebuildRecent();
        file.add(recentMenu);
        file.separator();
        file.item(actions.get(StudioActions.CLOSE_PROJECT));
        file.item(actions.get(StudioActions.RECOVERY));
        file.separator();
        file.item(actions.get(StudioActions.EXIT));
        // STUDIO-007: menu Automação (Nova sessão, Nova etapa, Validar automação)
        com.nekkora.ui.navigation.NekkoraMenu automation = new com.nekkora.ui.navigation.NekkoraMenu("Automação");
        automation.setMnemonic(KeyEvent.VK_U);
        automation.item(actions.get(StudioActions.NEW_SESSION));
        automation.item(actions.get(StudioActions.NEW_STEP));
        automation.separator();
        automation.item(actions.get(StudioActions.VALIDATE));
        menuBar = new javax.swing.JMenuBar();
        menuBar.add(file);
        menuBar.add(automation);
        menuBar.getAccessibleContext().setAccessibleName("Menu do Nekkora Studio");
        return menuBar;
    }

    /** Projetos recentes: disponíveis abrem; removidos aparecem indisponíveis, com a opção de retirar da lista. */
    void rebuildRecent() {
        if (recentMenu == null) return;
        recentMenu.removeAll();
        java.util.List<com.nekkora.studio.app.project.RecentProjects.Entry> entries = controller.projects().recents().entries();
        if (entries.isEmpty()) {
            javax.swing.JMenuItem none = new javax.swing.JMenuItem("Nenhum projeto recente");
            none.setEnabled(false);
            recentMenu.add(none);
            return;
        }
        for (com.nekkora.studio.app.project.RecentProjects.Entry en : entries) {
            String name = en.path().getFileName().toString();
            if (en.available()) {
                com.nekkora.ui.navigation.NekkoraMenuItem item = new com.nekkora.ui.navigation.NekkoraMenuItem(name, IconName.FILE, null);
                item.setToolTipText(en.path().toString());
                item.addActionListener(e -> controller.openRecent(en.path()));
                recentMenu.add(item);
            } else {
                com.nekkora.ui.navigation.NekkoraMenuItem item = new com.nekkora.ui.navigation.NekkoraMenuItem(name + " (indisponível) — retirar da lista",
                        IconName.X_CIRCLE, null);
                item.setToolTipText("O arquivo não existe mais: " + en.path());
                item.addActionListener(e -> controller.projects().recents().remove(en.path()));
                recentMenu.add(item);
            }
        }
    }

    public com.nekkora.ui.navigation.NekkoraMenu recentMenu() {
        menuBar();
        return recentMenu;
    }

    // ------------------------------------------------------------------ estado → tela

    private void onEvent(StudioEvent e) {
        if (e.has(StudioChange.CATALOG)) {
            refreshNavigator();
            refreshTabTitles();
        }
        if (e.has(StudioChange.CONTENT) && e.automation() != null) {
            AutomationOverview o = overviews.get(e.automation());
            if (o != null) {
                // só as sessões afetadas (STUDIO-004: origem e destino de uma movimentação) e a numeração das demais
                if (!e.sessions().isEmpty()) o.refreshSessions(e.sessions());
                else o.refresh();
            }
        }
        if (e.has(StudioChange.EVIDENCE) && e.automation() != null && !e.has(StudioChange.CONTENT)) {
            // STUDIO-005: só a apresentação das evidências das sessões afetadas (as etapas em si não mudaram)
            AutomationOverview o = overviews.get(e.automation());
            if (o != null) {
                if (!e.sessions().isEmpty()) o.refreshSessions(e.sessions());
                else o.refresh();
            }
            refreshSelection();
        }
        if (e.has(StudioChange.EDIT_LOCK)) {
            refreshLocks();
            refreshToolbar();
            refreshSelection();
        }
        if (e.has(StudioChange.HISTORY)) refreshToolbar();
        if (e.has(StudioChange.OPEN_AUTOMATIONS) || e.has(StudioChange.ACTIVE_AUTOMATION)) syncTabs();
        if (e.has(StudioChange.ACTIVE_AUTOMATION)) {
            refreshNavigator();
            refreshToolbar();
        }
        if (e.has(StudioChange.CONTENT)) refreshToolbar();
        if (e.has(StudioChange.SELECTION) || e.has(StudioChange.ACTIVE_AUTOMATION) || e.has(StudioChange.CONTENT)) {
            refreshSelection();
        }
        if (e.has(StudioChange.UNSAVED_CHANGES) || e.has(StudioChange.CATALOG) || e.has(StudioChange.PROJECT)) {
            refreshStatus();
            refreshTabTitles();
            refreshToolbar();
        }
        if (e.has(StudioChange.NOTICE)) state.lastNotice().ifPresent(this::toast);
        if (e.has(StudioChange.CATALOG)) {
            for (AutomationId id : List.copyOf(overviews.keySet())) {
                if (state.catalog().find(id).isEmpty()) controller.validation().forget(id); // projeto fechado
            }
        }
        if (e.has(StudioChange.VALIDATION) || e.has(StudioChange.ACTIVE_AUTOMATION) || e.has(StudioChange.CATALOG) || e.has(StudioChange.CONTENT)
                || e.has(StudioChange.EVIDENCE) || e.has(StudioChange.UNSAVED_CHANGES) || e.has(StudioChange.PROJECT) || e.has(StudioChange.EDIT_LOCK)) {
            refreshValidation(e.has(StudioChange.VALIDATION) || e.has(StudioChange.ACTIVE_AUTOMATION) || e.has(StudioChange.EDIT_LOCK));
        }
    }

    // ------------------------------------------------------------------ STUDIO-007: validação

    /** Mostra o painel de validação abaixo da área de trabalho. */
    public void showValidation() {
        if (!validationVisible) {
            center.removeAll();
            workArea.setMinimumSize(new java.awt.Dimension(0, com.nekkora.ui.theme.Scale.px(160)));
            validationSplit = com.nekkora.ui.container.NekkoraSplitPane.vertical(workArea, validationPanel);
            validationSplit.setResizeWeight(0.55); // a área de trabalho fica com 55% da altura (e com o que sobrar ao redimensionar)
            center.add(validationSplit, java.awt.BorderLayout.CENTER);
            validationVisible = true;
            placeDivider(validationSplit);
            center.revalidate();
            center.repaint();
        }
        refreshValidation(true);
        SwingUtilities.invokeLater(() -> validationPanel.findingList().requestFocusInWindow());
    }

    /** Divisor em 55% da altura real — já, se a altura é conhecida, ou na primeira vez que o painel ganhar tamanho. */
    private static void placeDivider(com.nekkora.ui.container.NekkoraSplitPane split) {
        java.awt.Container parent = split.getParent();
        int h = parent == null ? 0 : parent.getHeight();
        if (h > 0) {
            split.setDividerLocation((int) Math.round(h * 0.55));
            return;
        }
        split.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (split.getHeight() <= 0) return;
                split.removeComponentListener(this);
                split.setDividerLocation((int) Math.round(split.getHeight() * 0.55));
            }
        });
    }

    /** Fecha o painel (o relatório continua guardado e pode ser reaberto validando de novo). */
    public void hideValidation() {
        if (!validationVisible) return;
        center.removeAll();
        center.add(workArea, java.awt.BorderLayout.CENTER);
        validationVisible = false;
        center.revalidate();
        center.repaint();
        focusOverview();
    }

    public boolean validationVisible() {
        return validationVisible;
    }

    public com.nekkora.studio.ui.ValidationPanel validationPanel() {
        return validationPanel;
    }

    /**
     * Atualiza o painel. Com um relatório novo (ou outra automação ativa) as linhas são refeitas; numa alteração da
     * automação só a atualidade muda — o painel passa a dizer "Resultado desatualizado" sem refazer milhares de linhas.
     */
    private void refreshValidation(boolean rebuild) {
        if (!validationVisible) return;
        Optional<Automation> active = state.activeAutomation();
        Optional<com.nekkora.studio.app.workspace.ValidationService.Result> r = active.flatMap(a -> controller.validation().result(a.id()));
        if (active.isEmpty() || r.isEmpty()) {
            shownReport = null;
            validationPanel.showNotValidated(active.map(Automation::name).orElse(""));
            return;
        }
        if (rebuild || r.get().report() != shownReport) {
            shownReport = r.get().report();
            validationPanel.show(active.get().name(), shownReport, validationItems(active.get(), shownReport), r.get().current());
        } else {
            validationPanel.setCurrent(r.get().current());
        }
    }

    /** Linhas com o lugar de cada apontamento no estado ATUAL (o relatório não guarda nomes). */
    private List<com.nekkora.studio.ui.ValidationPanel.Item> validationItems(Automation a, com.nekkora.studio.validation.ValidationReport report) {
        boolean editable = !controller.service().isLocked(a.id());
        java.util.List<com.nekkora.studio.ui.ValidationPanel.Item> out = new java.util.ArrayList<>(report.findings().size());
        for (com.nekkora.studio.validation.Finding f : report.findings()) {
            String place;
            boolean exists = true;
            switch (f.scope()) {
                case AUTOMATION -> place = "Automação";
                case SESSION -> place = "Sessão " + f.sessionNumber() + a.session(f.session()).map(s -> " — " + s.name()).orElse(" (removida)");
                default -> {
                    Optional<com.nekkora.studio.domain.AutomationStep> st = a.step(f.step());
                    exists = st.isPresent();
                    place = "Sessão " + f.sessionNumber() + " · Etapa " + String.format("%02d", f.stepNumber())
                            + st.map(s -> " — " + s.name()).orElse(" (removida)");
                }
            }
            out.add(new com.nekkora.studio.ui.ValidationPanel.Item(f, place, editable && exists));
        }
        return out;
    }

    private void refreshAll() {
        refreshNavigator();
        syncTabs();
        refreshToolbar();
        refreshSelection();
        refreshStatus();
        updateTheme();
    }

    private void refreshNavigator() {
        navigator.show(state.automations(), state.activeId().orElse(null));
    }

    private void refreshToolbar() {
        Optional<Automation> a = state.activeAutomation();
        breadcrumb.clear();
        breadcrumb.item("Automações");
        a.ifPresent(x -> breadcrumb.item(x.name()));
        breadcrumb.revalidate();
        breadcrumb.repaint();
        actions.get(StudioActions.NEW_SESSION).setEnabled(a.isPresent());
        actions.get(StudioActions.NEW_STEP).setEnabled(a.map(x -> !x.sessions().isEmpty()).orElse(false));
        actions.get(StudioActions.RENAME_SESSION).setEnabled(a.map(x -> !x.sessions().isEmpty()).orElse(false));
        actions.get(StudioActions.EDIT).setEnabled(a.isPresent());
        actions.get(StudioActions.SAVE).setEnabled(a.isPresent());
        actions.get(StudioActions.SAVE_AS).setEnabled(a.isPresent());
        actions.get(StudioActions.CLOSE_PROJECT).setEnabled(a.isPresent());
        actions.get(StudioActions.VALIDATE).setEnabled(a.isPresent());
        boolean canUndo = a.map(x -> controller.service().canUndo(x.id())).orElse(false);
        boolean canRedo = a.map(x -> controller.service().canRedo(x.id())).orElse(false);
        actions.get(StudioActions.UNDO).setEnabled(canUndo);
        actions.get(StudioActions.REDO).setEnabled(canRedo);
        a.ifPresent(x -> {
            String locked = controller.service().isLocked(x.id()) ? " — indisponível durante a gravação" : "";
            undoButton.setToolTipText(controller.service().history().undoLabel(x.id()).map(l -> "Desfazer: " + l).orElse("Nada para desfazer") + locked
                    + " (" + com.nekkora.ui.action.NekkoraKeys.format(actions.get(StudioActions.UNDO).shortcutKey()) + ")");
            redoButton.setToolTipText(controller.service().history().redoLabel(x.id()).map(l -> "Refazer: " + l).orElse("Nada para refazer") + locked
                    + " (" + com.nekkora.ui.action.NekkoraKeys.format(actions.get(StudioActions.REDO).shortcutKey()) + ")");
        });
    }

    /** STUDIO-004: nome das abas acompanha a automação (renomear não cria outra automação). */
    private void refreshTabTitles() {
        for (Map.Entry<AutomationId, AutomationOverview> en : overviews.entrySet()) {
            int i = workArea.getTabs().indexOfComponent(en.getValue());
            if (i >= 0) workArea.getTabs().setTitleAt(i, tabTitle(en.getKey()));
        }
    }

    /** STUDIO-004: automação recebendo gravação — navegação e consulta continuam, edição fica bloqueada. */
    private void refreshLocks() {
        for (Map.Entry<AutomationId, AutomationOverview> en : overviews.entrySet()) {
            en.getValue().setEditable(!controller.service().isLocked(en.getKey()));
        }
    }

    /** STUDIO-006: o asterisco indica alterações ainda não salvas no arquivo. */
    private String tabTitle(AutomationId id) {
        String name = state.catalog().find(id).map(Automation::name).orElse("");
        return state.project(id).map(com.nekkora.studio.app.workspace.ProjectState::dirty).orElse(false) ? name + " *" : name;
    }

    private void refreshStatus() {
        unsavedStatus.setVisible(state.hasUnsavedChanges());
        countStatus.setText(StudioText.automations(state.automations().size()) + " no Studio");
        Optional<com.nekkora.studio.app.workspace.ProjectState> p = state.activeId().flatMap(state::project);
        if (state.activeId().isEmpty()) {
            projectStatus.setText("Nenhum projeto aberto");
            projectStatus.setToolTipText(null);
        } else {
            com.nekkora.studio.app.workspace.ProjectState.Status st = p.map(com.nekkora.studio.app.workspace.ProjectState::status)
                    .orElse(com.nekkora.studio.app.workspace.ProjectState.Status.UNSAVED);
            String file = p.flatMap(com.nekkora.studio.app.workspace.ProjectState::path).map(x -> x.getFileName().toString()).orElse(null);
            projectStatus.setText(file == null && st != com.nekkora.studio.app.workspace.ProjectState.Status.SAVING ? st.label() + " — sem arquivo"
                    : st.label() + (file == null ? "" : " — " + file));
            projectStatus.setToolTipText(p.flatMap(com.nekkora.studio.app.workspace.ProjectState::lastError).map(err -> err.message())
                    .orElse("Projetos são arquivos locais .nekkora, não criptografados. Não guarde segredos neles."));
        }
        boolean recovery = controller.projects().settings().recoveryEnabled() && controller.projects().recovery().isPresent();
        recoveryStatus.setText(recovery ? "Recuperação ativa" : "Recuperação desativada");
        recoveryStatus.setToolTipText(controller.projects().recovery().isEmpty() ? "Sem diretório de dados local nesta sessão."
                : "Arquivo → Recuperação automática… (cópias locais das alterações não salvas)");
    }

    private void updateTheme() {
        themeStatus.setText(ThemeManager.current().displayName());
    }

    private void refreshSelection() {
        Optional<Automation> active = state.activeAutomation();
        if (active.isEmpty()) {
            properties.showNothing();
            return;
        }
        Automation a = active.get();
        AutomationOverview overview = overviews.get(a.id());
        boolean editable = !controller.service().isLocked(a.id());
        Selection sel = state.selection();
        switch (sel) {
            case Selection.OfSession s -> {
                if (overview != null) overview.showSelection(s.session(), null);
                properties.showSession(a, s.session(), editable);
            }
            case Selection.OfStep s -> {
                if (overview != null) overview.showSelection(s.session(), s.step());
                properties.showStep(a, s.step(), editable);
            }
            default -> {
                if (overview != null) overview.showSelection(null, null);
                properties.showAutomation(a, editable);
            }
        }
    }

    // ------------------------------------------------------------------ abas

    private void syncTabs() {
        syncingTabs = true;
        try {
            for (Map.Entry<AutomationId, String> en : tabKeys.entrySet()) {
                if (state.openAutomations().contains(en.getKey()) || !workArea.isOpen(en.getValue())) continue;
                int index = workArea.getTabs().indexOfComponent(overviews.get(en.getKey()));
                closingTab = true;
                try {
                    workArea.getTabs().closeTab(index); // fechada pelo estado (não pelo X): remove a aba correspondente
                } finally {
                    closingTab = false;
                }
            }
            // STUDIO-006: projeto fechado (retirado do catálogo) ou recarregado — a visão antiga não é reaproveitada
            for (java.util.Iterator<Map.Entry<AutomationId, AutomationOverview>> it = overviews.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<AutomationId, AutomationOverview> en = it.next();
                boolean gone = state.catalog().find(en.getKey()).map(a -> a != en.getValue().automation()).orElse(true);
                if (gone && !state.openAutomations().contains(en.getKey())) {
                    it.remove();
                    tabKeys.remove(en.getKey());
                }
            }
            for (AutomationId id : state.openAutomations()) {
                AutomationOverview o = overviews.computeIfAbsent(id, this::createOverview);
                String key = tabKeys.computeIfAbsent(id, k -> k.value().toString());
                if (!workArea.isOpen(key)) workArea.openTab(key, tabTitle(id), IconName.WORKFLOW, o);
            }
            state.activeId().ifPresent(id -> {
                AutomationOverview o = overviews.get(id);
                if (o != null) workArea.getTabs().setSelectedComponent(o);
            });
        } finally {
            syncingTabs = false;
        }
    }

    private AutomationOverview createOverview(AutomationId id) {
        AutomationOverview o = newOverview(id);
        o.setEditable(!controller.service().isLocked(id));
        return o;
    }

    private AutomationOverview newOverview(AutomationId id) {
        Automation a = state.catalog().require(id);
        return new AutomationOverview(a, new AutomationOverview.Listener() {
            @Override
            public void automationSelected() {
                controller.selectAutomation(id);
            }

            @Override
            public void addSessionRequested() {
                controller.newSession();
            }

            @Override
            public void addStepRequested() {
                controller.newStep(null);
            }

            @Override
            public void sessionSelected(SessionId session) {
                controller.selectSession(id, session);
            }

            @Override
            public void stepSelected(SessionId session, StepId step) {
                controller.selectStep(id, session, step);
            }

            @Override
            public void stepOpened(SessionId session, StepId step) {
                controller.selectStep(id, session, step);
                controller.openStepDetails(step);
            }

            @Override
            public void addStepRequested(SessionId session) {
                controller.newStep(session);
            }

            @Override
            public void renameSessionRequested(SessionId session) {
                controller.renameSession(session);
            }

            @Override
            public void editStepRequested(SessionId session, StepId step) {
                controller.selectStep(id, session, step);
                controller.openStepDetails(step);
            }

            @Override
            public void moveStepRequested(StepId step, int delta) {
                controller.moveStep(step, delta);
            }

            @Override
            public void moveStepToRequested(StepId step) {
                controller.moveStepTo(step);
            }

            @Override
            public void duplicateStepRequested(StepId step) {
                controller.duplicateStep(step);
            }

            @Override
            public void deleteStepRequested(StepId step) {
                controller.deleteStep(step);
            }

            @Override
            public void editSessionRequested(SessionId session) {
                controller.editSession(session);
            }

            @Override
            public void moveSessionRequested(SessionId session, int delta) {
                controller.moveSession(session, delta);
            }

            @Override
            public void deleteSessionRequested(SessionId session) {
                controller.deleteSession(session);
            }
        });
    }

    private void onTabSelected() {
        if (syncingTabs || closingTab) return;
        Component c = workArea.getTabs().getSelectedComponent();
        overviews.entrySet().stream().filter(en -> en.getValue() == c).map(Map.Entry::getKey).findFirst()
                .ifPresent(controller::openAutomation);
    }

    private boolean onTabClosing(String key) {
        AutomationId id = tabKeys.entrySet().stream().filter(en -> en.getValue().equals(key)).map(Map.Entry::getKey).findFirst().orElse(null);
        if (id == null || !state.openAutomations().contains(id)) return true;
        closingTab = true;
        SwingUtilities.invokeLater(() -> {
            closingTab = false;
            controller.closeAutomation(id);
        });
        return true;
    }

    private void focusOverview() {
        state.activeId().map(overviews::get).ifPresent(o -> o.sessionViews().stream().findFirst()
                .ifPresentOrElse(v -> v.stepList().requestFocusInWindow(), o::requestFocusInWindow));
    }

    private void toast(Notice n) {
        Severity s = switch (n.kind()) {
            case SUCCESS -> Severity.SUCCESS;
            case INFO -> Severity.INFO;
            case WARNING -> Severity.WARNING;
            case ERROR -> Severity.ERROR;
        };
        NekkoraToast.show(workbench, s, n.title(), n.message());
    }

    // ------------------------------------------------------------------ acesso para testes/evidências

    public AutomationNavigator navigator() {
        return navigator;
    }

    public NekkoraWorkArea workArea() {
        return workArea;
    }

    public NekkoraInspector inspector() {
        return inspector;
    }

    public NekkoraWorkbench workbench() {
        return workbench;
    }

    public StudioActions actions() {
        return actions;
    }

    public Optional<AutomationOverview> overview(AutomationId id) {
        return Optional.ofNullable(overviews.get(id));
    }

    public List<NekkoraCaption> statusItems() {
        return List.of(projectStatus, unsavedStatus, countStatus, themeStatus, recoveryStatus);
    }

    /**
     * Item adicional da barra de status (ex.: conexão com o navegador, STUDIO-002), logo depois da situação do projeto:
     * em janela estreita, os itens mais à direita de cada lado saem primeiro.
     */
    public void addStatusItem(JComponent item) {
        extraStatus.add(item);
        statusBar.addLeft(item, extraStatus.size());
        statusBar.revalidate();
    }

    public List<JComponent> extraStatusItems() {
        return List.copyOf(extraStatus);
    }
}
