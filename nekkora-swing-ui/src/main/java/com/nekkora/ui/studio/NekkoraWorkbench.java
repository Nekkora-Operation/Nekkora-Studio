package com.nekkora.ui.studio;

import com.nekkora.ui.action.NekkoraKeys;
import com.nekkora.ui.container.Background;
import com.nekkora.ui.container.NekkoraPanel;
import com.nekkora.ui.container.NekkoraSplitPane;
import com.nekkora.ui.theme.Scale;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Layout de estúdio: <b>Navegador | Área de trabalho | Inspector</b>, com toolbar no topo,
 * barra de status embaixo e navegação lateral opcional à esquerda. Painéis redimensionáveis e
 * recolhíveis (Ctrl+B navegador, Ctrl+Alt+B inspector). Funciona de 1366×768 a 4K.
 */
public class NekkoraWorkbench extends NekkoraPanel {
    private final JPanel center = new JPanel(new BorderLayout());
    private final NekkoraSplitPane outer;
    private final NekkoraSplitPane inner;
    private final JPanel navigatorSlot = new JPanel(new BorderLayout());
    private final JPanel workspaceSlot = new JPanel(new BorderLayout());
    private final JPanel inspectorSlot = new JPanel(new BorderLayout());
    private int navigatorWidth = 260;
    private int inspectorWidth = 320;
    private int lastOuter = -1;
    private int lastInner = -1;

    public NekkoraWorkbench() {
        super(new BorderLayout(), Background.CANVAS);
        for (JPanel p : new JPanel[] {center, navigatorSlot, workspaceSlot, inspectorSlot}) p.setOpaque(false);
        navigatorSlot.setMinimumSize(new Dimension(Scale.px(180), 0));
        inspectorSlot.setMinimumSize(new Dimension(Scale.px(240), 0));
        workspaceSlot.setMinimumSize(new Dimension(Scale.px(360), 0));
        inner = NekkoraSplitPane.horizontal(workspaceSlot, inspectorSlot);
        inner.setResizeWeight(1.0);
        outer = NekkoraSplitPane.horizontal(navigatorSlot, inner);
        outer.setResizeWeight(0.0);
        center.add(outer, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
        outer.addComponentListener(new java.awt.event.ComponentAdapter() {
            private boolean applied;

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (!applied && outer.getWidth() > 0) {
                    applied = true;
                    applyWidths();
                }
            }
        });
        NekkoraKeys.bind(this, NekkoraKeys.shortcut(KeyEvent.VK_B), "nekkora.workbench.navigator", this::toggleNavigator);
        NekkoraKeys.bind(this, NekkoraKeys.shortcut(KeyEvent.VK_B, InputEvent.ALT_DOWN_MASK), "nekkora.workbench.inspector", this::toggleInspector);
    }

    public void setSidebar(JComponent sidebar) {
        add(sidebar, BorderLayout.WEST);
    }

    public void setToolbar(JComponent toolbar) {
        center.add(toolbar, BorderLayout.NORTH);
    }

    public void setStatusBar(JComponent statusBar) {
        add(statusBar, BorderLayout.SOUTH);
    }

    public void setNavigator(JComponent navigator) {
        navigatorSlot.removeAll();
        navigatorSlot.add(navigator, BorderLayout.CENTER);
    }

    public void setWorkspace(JComponent workspace) {
        workspaceSlot.removeAll();
        workspaceSlot.add(workspace, BorderLayout.CENTER);
    }

    public void setInspector(JComponent inspector) {
        inspectorSlot.removeAll();
        inspectorSlot.add(inspector, BorderLayout.CENTER);
    }

    /** Larguras iniciais (px lógicos) dos painéis laterais. */
    public void setPanelWidths(int navigator, int inspector) {
        this.navigatorWidth = navigator;
        this.inspectorWidth = inspector;
        applyWidths();
    }

    public boolean isNavigatorVisible() {
        return navigatorSlot.isVisible();
    }

    public boolean isInspectorVisible() {
        return inspectorSlot.isVisible();
    }

    public void toggleNavigator() {
        setNavigatorVisible(!navigatorSlot.isVisible());
    }

    public void toggleInspector() {
        setInspectorVisible(!inspectorSlot.isVisible());
    }

    public void setNavigatorVisible(boolean visible) {
        if (!visible) lastOuter = outer.getDividerLocation();
        navigatorSlot.setVisible(visible);
        if (visible) outer.setDividerLocation(lastOuter > 0 ? lastOuter : Scale.px(navigatorWidth));
        revalidate();
    }

    public void setInspectorVisible(boolean visible) {
        if (!visible) lastInner = inner.getWidth() - inner.getDividerLocation();
        inspectorSlot.setVisible(visible);
        if (visible) inner.setDividerLocation(inner.getWidth() - (lastInner > 0 ? lastInner : Scale.px(inspectorWidth)));
        revalidate();
    }

    private void applyWidths() {
        if (outer.getWidth() <= 0) return;
        outer.setDividerLocation(Scale.px(navigatorWidth));
        int innerWidth = outer.getWidth() - Scale.px(navigatorWidth) - outer.getDividerSize();
        inner.setDividerLocation(Math.max(Scale.px(360), innerWidth - inner.getDividerSize() - Scale.px(inspectorWidth)));
    }
}
