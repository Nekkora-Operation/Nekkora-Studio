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
    private final NekkoraCaption draftStatus = NekkoraStatusBar.status(Status.DRAFT, "Rascunho em memória");
    private final NekkoraCaption unsavedStatus = NekkoraStatusBar.item(IconName.ALERT, "Alterações não salvas");
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
                "Crie uma automação para organizar o processo em sessões e etapas. Tudo fica em um rascunho em memória.");
        empty.addAction(NekkoraButton.primary("Nova automação").withIcon(IconName.PLUS).onClick(e -> controller.newAutomation()));
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

        workbench.setSidebar(sidebar());
        workbench.setToolbar(toolbar());
        workbench.setNavigator(navigator);
        workbench.setWorkspace(workArea);
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
        draftStatus.setToolTipText("As automações ficam apenas na memória: o salvamento chega em uma versão futura.");
        bar.addLeft(draftStatus);
        bar.addLeft(unsavedStatus);
        bar.addLeft(countStatus);
        bar.addRight(themeStatus);
        statusBar = bar;
        return bar;
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
        if (e.has(StudioChange.UNSAVED_CHANGES) || e.has(StudioChange.CATALOG)) refreshStatus();
        if (e.has(StudioChange.NOTICE)) state.lastNotice().ifPresent(this::toast);
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
            if (i >= 0) workArea.getTabs().setTitleAt(i, en.getValue().automation().name());
        }
    }

    /** STUDIO-004: automação recebendo gravação — navegação e consulta continuam, edição fica bloqueada. */
    private void refreshLocks() {
        for (Map.Entry<AutomationId, AutomationOverview> en : overviews.entrySet()) {
            en.getValue().setEditable(!controller.service().isLocked(en.getKey()));
        }
    }

    private void refreshStatus() {
        unsavedStatus.setVisible(state.hasUnsavedChanges());
        countStatus.setText(StudioText.automations(state.automations().size()) + " em memória");
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
            for (AutomationId id : state.openAutomations()) {
                AutomationOverview o = overviews.computeIfAbsent(id, this::createOverview);
                String key = tabKeys.computeIfAbsent(id, k -> k.value().toString());
                if (!workArea.isOpen(key)) workArea.openTab(key, o.automation().name(), IconName.WORKFLOW, o);
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
        return List.of(draftStatus, unsavedStatus, countStatus, themeStatus);
    }

    /** Item adicional da barra de status (ex.: conexão com o navegador, STUDIO-002). */
    public void addStatusItem(JComponent item) {
        extraStatus.add(item);
        statusBar.addLeft(item);
        statusBar.revalidate();
    }

    public List<JComponent> extraStatusItems() {
        return List.copyOf(extraStatus);
    }
}
