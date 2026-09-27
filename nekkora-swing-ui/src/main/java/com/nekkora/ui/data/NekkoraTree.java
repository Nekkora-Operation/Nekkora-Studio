package com.nekkora.ui.data;

import com.formdev.flatlaf.FlatClientProperties;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.IconSize;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.container.NekkoraScrollPane;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 * Árvore Nekkora (navegador de projetos/estruturas): seleção de linha inteira, hover, ícones e
 * status por nó ({@link NekkoraTreeNode}), menu de contexto e ativação por Enter/duplo clique.
 */
public class NekkoraTree extends JTree {
    private int hoverRow = -1;
    private Function<TreePath, JPopupMenu> nodeMenu;
    private Consumer<NekkoraTreeNode> onActivate;
    private boolean initialized;

    public NekkoraTree(NekkoraTreeNode root) {
        super(new DefaultTreeModel(root));
        initialized = true;
        getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        setRootVisible(true);
        setShowsRootHandles(true);
        setCellRenderer(new Renderer());
        setToggleClickCount(2);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setHoverRow(getRowForLocation0(e.getY()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setHoverRow(-1);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeMenu(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && onActivate != null) {
                    TreePath p = getPathForRow(getRowForLocation0(e.getY()));
                    if (p != null && p.getLastPathComponent() instanceof NekkoraTreeNode n && n.isLeaf()) onActivate.accept(n);
                }
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "nekkora.activate", () -> {
            if (getLastSelectedPathComponent() instanceof NekkoraTreeNode n && onActivate != null) onActivate.accept(n);
        });
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_F10, KeyEvent.SHIFT_DOWN_MASK), "nekkora.menu", this::keyboardMenu);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0), "nekkora.menu", this::keyboardMenu);
        applyStyle();
    }

    /** Menu de contexto para o nó (clique direito, Shift+F10). */
    public void setNodeMenu(Function<TreePath, JPopupMenu> menu) {
        this.nodeMenu = menu;
    }

    /** Enter ou duplo clique em um nó. */
    public void onNodeActivated(Consumer<NekkoraTreeNode> listener) {
        this.onActivate = listener;
    }

    public NekkoraTreeNode getSelectedNode() {
        return getLastSelectedPathComponent() instanceof NekkoraTreeNode n ? n : null;
    }

    /** Expande todos os nós. */
    public void expandAll() {
        for (int i = 0; i < getRowCount(); i++) expandRow(i);
    }

    /** Selecione e mostre um nó. */
    public void select(NekkoraTreeNode node) {
        TreePath p = new TreePath(((DefaultTreeModel) getModel()).getPathToRoot(node));
        setSelectionPath(p);
        scrollPathToVisible(p);
    }

    /** Envolve em área rolável sem borda. */
    public NekkoraScrollPane scrollable() {
        return new NekkoraScrollPane(this);
    }

    /** Força a linha em hover (showcase/testes). */
    public void setHoverRow(int row) {
        if (row == hoverRow) return;
        int old = hoverRow;
        hoverRow = row;
        repaintRow(old);
        repaintRow(row);
    }

    private int getRowForLocation0(int y) {
        int row = getClosestRowForLocation(0, y);
        Rectangle r = row < 0 ? null : getRowBounds(row);
        return r != null && y >= r.y && y < r.y + r.height ? row : -1;
    }

    private void repaintRow(int row) {
        Rectangle r = row < 0 ? null : getRowBounds(row);
        if (r != null) repaint(0, r.y, getWidth(), r.height);
    }

    private void bind(KeyStroke ks, String name, Runnable r) {
        getInputMap(WHEN_FOCUSED).put(ks, name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        });
    }

    private void maybeMenu(MouseEvent e) {
        if (!e.isPopupTrigger() || nodeMenu == null) return;
        int row = getRowForLocation0(e.getY());
        if (row < 0) return;
        setSelectionRow(row);
        JPopupMenu menu = nodeMenu.apply(getPathForRow(row));
        if (menu != null) menu.show(this, e.getX(), e.getY());
    }

    private void keyboardMenu() {
        TreePath p = getSelectionPath();
        if (p == null || nodeMenu == null) return;
        Rectangle r = getPathBounds(p);
        JPopupMenu menu = nodeMenu.apply(p);
        if (menu != null && r != null) menu.show(this, r.x + Scale.px(16), r.y + r.height);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyStyle();
    }

    private void applyStyle() {
        if (!initialized) return;
        setFont(NekkoraFonts.font(TypeStyle.BODY));
        setRowHeight(Scale.px(28));
        putClientProperty(FlatClientProperties.TREE_WIDE_SELECTION, true);
        putClientProperty("JTree.paintSelection", true);
        setOpaque(false);
        if (getCellRenderer() instanceof Component c) javax.swing.SwingUtilities.updateComponentTreeUI(c);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Paint.prepare(g);
        g2.setColor(Tokens.color(getClientProperty("nekkora.canvas") != null
                ? ColorToken.BACKGROUND_CANVAS : ColorToken.BACKGROUND_SURFACE));
        g2.fillRect(0, 0, getWidth(), getHeight());
        if (hoverRow >= 0 && !isRowSelected(hoverRow)) {
            Rectangle r = getRowBounds(hoverRow);
            if (r != null) {
                int in = Scale.px(4);
                Paint.fillRound(g2, in, r.y, getWidth() - 2 * in, r.height, Scale.px(com.nekkora.tokens.Radius.MEDIUM),
                        Tokens.color(ColorToken.BACKGROUND_HOVER));
            }
        }
        g2.dispose();
        super.paintComponent(g);
    }

    /** Pinta sobre o fundo {@code canvas} (use em navegadores laterais). */
    public void setCanvasBackground(boolean canvas) {
        putClientProperty("nekkora.canvas", canvas ? Boolean.TRUE : null);
        repaint();
    }

    /** Renderizador: ícone + rótulo + secundário + status/contador. */
    private static final class Renderer extends JComponent implements TreeCellRenderer {
        private NekkoraTreeNode node;
        private String text;
        private boolean selected;
        private boolean enabled = true;

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            this.node = value instanceof NekkoraTreeNode n ? n : null;
            this.text = node != null ? node.getLabel() : String.valueOf(value);
            this.selected = selected;
            this.enabled = tree.isEnabled();
            if (node == null && value instanceof TreeNode tn && !tn.isLeaf()) this.text = String.valueOf(value);
            setFont(tree.getFont());
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            int w = fm.stringWidth(text == null ? "" : text) + Scale.px(8);
            if (node != null && node.getIcon() != null) w += Scale.px(16 + 8);
            if (node != null && node.getSecondary() != null) {
                w += Scale.px(8) + getFontMetrics(NekkoraFonts.font(TypeStyle.BODY_S)).stringWidth(node.getSecondary());
            }
            if (node != null && node.getStatus() != null) w += Scale.px(14 + 8);
            if (node != null && node.getCount() != null) w += Scale.px(28);
            return new Dimension(w, Scale.px(28));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Paint.prepare(g);
            int x = 0, h = getHeight();
            Color fg = Tokens.color(enabled ? ColorToken.TEXT_PRIMARY : ColorToken.TEXT_DISABLED);
            if (node != null && node.getIcon() != null) {
                int s = Scale.px(IconSize.M.px());
                Color ic = node.getIconTone() != null && enabled ? Tokens.color(node.getIconTone().foreground())
                        : Tokens.color(selected ? ColorToken.TEXT_PRIMARY : ColorToken.TEXT_SECONDARY);
                NekkoraIcon.of(node.getIcon(), IconSize.M).paint(g2, x, (h - s) / 2, s, ic, ic);
                x += s + Scale.px(8);
            }
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(fg);
            Paint.text(this, g2, text == null ? "" : text, x, Paint.centeredBaseline(fm, 0, h));
            x += fm.stringWidth(text == null ? "" : text) + Scale.px(8);
            if (node != null && node.getSecondary() != null) {
                g2.setFont(NekkoraFonts.font(TypeStyle.BODY_S));
                FontMetrics fs = g2.getFontMetrics();
                g2.setColor(Tokens.color(ColorToken.TEXT_MUTED));
                Paint.text(this, g2, node.getSecondary(), x, Paint.centeredBaseline(fs, 0, h));
                x += fs.stringWidth(node.getSecondary()) + Scale.px(8);
            }
            if (node != null && node.getStatus() != null) {
                int s = Scale.px(14);
                Color c = Tokens.color(node.getStatus().tone().foreground());
                NekkoraIcon.of(node.getStatus().icon(), 14).paint(g2, x, (h - s) / 2, s, c, c);
                x += s + Scale.px(8);
            }
            if (node != null && node.getCount() != null) {
                String c = Integer.toString(node.getCount());
                g2.setFont(NekkoraFonts.font(TypeStyle.CAPTION));
                FontMetrics fc = g2.getFontMetrics();
                int bw = Math.max(Scale.px(20), fc.stringWidth(c) + Scale.px(10)), bh = Scale.px(16);
                Paint.fillRound(g2, x, (h - bh) / 2f, bw, bh, bh / 2f, Tokens.color(ColorToken.STATUS_NEUTRAL_SOFT));
                g2.setColor(Tokens.color(ColorToken.TEXT_SECONDARY));
                Paint.text(this, g2, c, x + (bw - fc.stringWidth(c)) / 2f, Paint.centeredBaseline(fc, 0, h));
            }
            g2.dispose();
        }
    }

    @Override
    public String convertValueToText(Object value, boolean selected, boolean expanded, boolean leaf, int row,
                                     boolean hasFocus) {
        return value instanceof NekkoraTreeNode n ? n.getLabel() : super.convertValueToText(value, selected, expanded, leaf, row, hasFocus);
    }

    /** Ícone padrão sugerido para pastas/itens (conveniência). */
    public static IconName folderIcon(boolean open) {
        return open ? IconName.FOLDER_OPEN : IconName.FOLDER;
    }
}
