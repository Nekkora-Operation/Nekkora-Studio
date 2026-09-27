package com.nekkora.ui.navigation;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.container.Background;
import com.nekkora.ui.container.NekkoraPanel;
import com.nekkora.ui.container.NekkoraScrollPane;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JComponent;

/**
 * Navegação lateral principal: cabeçalho (logo), grupos de itens, rodapé. Recolhível para só
 * ícones ({@link #setCollapsed}). Setas ↑/↓ movem entre itens; Enter/Espaço selecionam.
 *
 * <pre>{@code
 * NekkoraSidebar nav = new NekkoraSidebar();
 * nav.setHeader(new NekkoraLogo(NekkoraLogo.Variant.FULL, 24));
 * NekkoraSidebarGroup g = nav.addGroup("Trabalho");
 * g.addItem(new NekkoraSidebarItem("home", "Início", IconName.HOME));
 * nav.onSelect(item -> mostrar(item.getId()));
 * }</pre>
 */
public class NekkoraSidebar extends NekkoraPanel {
    private final NekkoraStack groupsStack = NekkoraStack.vertical(Spacing.SPACE_5);
    private final List<NekkoraSidebarGroup> groups = new ArrayList<>();
    private final List<Consumer<NekkoraSidebarItem>> listeners = new ArrayList<>();
    private final NekkoraPanel headerSlot = new NekkoraPanel(new BorderLayout());
    private final NekkoraPanel footerSlot = new NekkoraPanel(new BorderLayout());
    private NekkoraSidebarItem selected;
    private boolean collapsed;

    public NekkoraSidebar() {
        super(new BorderLayout(), Background.CANVAS);
        groupsStack.setPadding(Spacing.SPACE_2, Spacing.SPACE_3);
        NekkoraScrollPane scroll = NekkoraScrollPane.verticalOnly(groupsStack);
        headerSlot.setBorder(BorderFactory.createEmptyBorder(Scale.px(16), Scale.px(16), Scale.px(12), Scale.px(12)));
        footerSlot.setBorder(BorderFactory.createEmptyBorder(Scale.px(8), Scale.px(8), Scale.px(12), Scale.px(8)));
        add(headerSlot, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(footerSlot, BorderLayout.SOUTH);
        getAccessibleContext().setAccessibleName("Navegação principal");
    }

    public void setHeader(JComponent header) {
        headerSlot.removeAll();
        headerSlot.add(header, BorderLayout.CENTER);
        headerSlot.revalidate();
    }

    public void setFooter(JComponent footer) {
        footerSlot.removeAll();
        footerSlot.add(footer, BorderLayout.CENTER);
        footerSlot.revalidate();
    }

    public NekkoraSidebarGroup addGroup(String title) {
        NekkoraSidebarGroup g = new NekkoraSidebarGroup(title);
        groups.add(g);
        groupsStack.add(g);
        groupsStack.revalidate();
        return g;
    }

    /** Todos os itens, na ordem de exibição. */
    public List<NekkoraSidebarItem> getItems() {
        List<NekkoraSidebarItem> all = new ArrayList<>();
        groups.forEach(g -> all.addAll(g.getItems()));
        all.forEach(i -> i.sidebar = this);
        return all;
    }

    public Optional<NekkoraSidebarItem> findItem(String id) {
        return getItems().stream().filter(i -> i.getId().equals(id)).findFirst();
    }

    public NekkoraSidebarItem getSelectedItem() {
        return selected;
    }

    public void select(String id) {
        findItem(id).ifPresent(this::select);
    }

    public void select(NekkoraSidebarItem item) {
        getItems();
        if (selected == item) return;
        if (selected != null) selected.setSelectedInternal(false);
        selected = item;
        if (item != null) item.setSelectedInternal(true);
        listeners.forEach(l -> l.accept(item));
    }

    public void onSelect(Consumer<NekkoraSidebarItem> listener) {
        listeners.add(listener);
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    /** Recolhe para só ícones (tooltips com os rótulos). */
    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        getItems();
        groups.forEach(g -> g.setCollapsed(collapsed));
        headerSlot.setVisible(!collapsed || headerSlot.getComponentCount() > 0);
        revalidate();
        repaint();
    }

    void focusRelative(NekkoraSidebarItem from, int delta) {
        List<NekkoraSidebarItem> items = getItems();
        int i = items.indexOf(from);
        for (int k = 1; k <= items.size(); k++) {
            NekkoraSidebarItem next = items.get(Math.floorMod(i + delta * k, items.size()));
            if (next.isEnabled()) {
                next.requestFocusInWindow();
                return;
            }
        }
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(Scale.px(collapsed ? 64 : 232), d.height);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(getPreferredSize().width, 0);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(Tokens.color(ColorToken.BORDER_SUBTLE));
        g.fillRect(getWidth() - Math.max(1, Scale.px(1)), 0, Math.max(1, Scale.px(1)), getHeight());
    }
}
