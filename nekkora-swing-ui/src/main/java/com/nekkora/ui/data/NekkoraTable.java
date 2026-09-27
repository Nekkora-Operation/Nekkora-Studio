package com.nekkora.ui.data;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Spacing;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.core.Status;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.IconSize;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.container.NekkoraScrollPane;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

/**
 * Tabela Nekkora: cabeçalho, seleção, hover de linha, ordenação visual, estado vazio,
 * rolagem, menu de ações por linha e renderização de status.
 *
 * <p>Teclado: setas navegam, Enter ativa a linha ({@link #onRowActivated}), Shift+F10 ou a
 * tecla de contexto abrem o menu da linha ({@link #setRowMenu}).
 */
public class NekkoraTable extends JTable {
    private int hoverRow = -1;
    private String emptyTitle = "Nada por aqui";
    private String emptyDescription;
    private IconName emptyIcon = IconName.BOX;
    private boolean striped;
    private IntFunction<JPopupMenu> rowMenu;
    private IntConsumer onActivate;
    private boolean initialized;

    public NekkoraTable(TableModel model) {
        super(model);
        initialized = true;
        setAutoCreateRowSorter(true);
        setFillsViewportHeight(true);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setShowVerticalLines(false);
        getTableHeader().setReorderingAllowed(false);
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setHoverRow(rowAtPoint(e.getPoint()));
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
                    int row = rowAtPoint(e.getPoint());
                    if (row >= 0) onActivate.accept(convertRowIndexToModel(row));
                }
            }
        };
        addMouseListener(hover);
        addMouseMotionListener(hover);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "nekkora.activate", () -> {
            int row = getSelectedRow();
            if (row >= 0 && onActivate != null) onActivate.accept(convertRowIndexToModel(row));
        });
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_F10, KeyEvent.SHIFT_DOWN_MASK), "nekkora.menu", this::keyboardMenu);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0), "nekkora.menu", this::keyboardMenu);
        applyStyle();
        configureColumns();
    }

    // ------------------------------------------------------------------ API

    /** Estado vazio exibido quando não há linhas. */
    public void setEmptyState(IconName icon, String title, String description) {
        this.emptyIcon = icon;
        this.emptyTitle = title;
        this.emptyDescription = description;
        repaint();
    }

    /** Linhas zebradas (útil em tabelas largas). */
    public void setStriped(boolean striped) {
        this.striped = striped;
        repaint();
    }

    /** Menu de contexto por linha (índice do <b>modelo</b>). */
    public void setRowMenu(IntFunction<JPopupMenu> rowMenu) {
        this.rowMenu = rowMenu;
    }

    /** Duplo clique ou Enter na linha (índice do <b>modelo</b>). */
    public void onRowActivated(IntConsumer listener) {
        this.onActivate = listener;
    }

    /** Linha selecionada no modelo, ou -1. */
    public int getSelectedModelRow() {
        int r = getSelectedRow();
        return r < 0 ? -1 : convertRowIndexToModel(r);
    }

    /** Envolve em área rolável sem borda, com fundo de superfície. */
    public NekkoraScrollPane scrollable() {
        NekkoraScrollPane sp = new NekkoraScrollPane(this);
        sp.setColumnHeaderView(getTableHeader()); // disponível antes mesmo de ir para a tela
        sp.getViewport().setOpaque(true);
        sp.getViewport().setBackground(Tokens.color(ColorToken.BACKGROUND_SURFACE));
        return sp;
    }

    public int getHoverRow() {
        return hoverRow;
    }

    /** Força a linha em hover (showcase/testes). */
    public void setHoverRow(int row) {
        if (row == hoverRow) return;
        int old = hoverRow;
        hoverRow = row;
        repaintRow(old);
        repaintRow(row);
    }

    // ------------------------------------------------------------------ Internos

    @Override
    public void setModel(TableModel model) {
        super.setModel(model);
        if (initialized) configureColumns();
    }

    private void configureColumns() {
        if (!(getModel() instanceof NekkoraTableModel<?> m)) return;
        for (int i = 0; i < m.getColumnCount(); i++) {
            TableColumn col = getColumnModel().getColumn(i);
            NekkoraTableModel.Column<?> def = m.getColumn(i);
            if (def.width() > 0) {
                col.setPreferredWidth(Scale.px(def.width()));
                if (def.kind() == NekkoraTableModel.Kind.STATUS) col.setMinWidth(Scale.px(Math.min(def.width(), 110)));
            }
            col.setCellRenderer(renderer(def.kind()));
            col.setHeaderRenderer(new HeaderRenderer(def.kind() == NekkoraTableModel.Kind.NUMBER
                    ? SwingConstants.RIGHT : SwingConstants.LEADING));
            if (getRowSorter() instanceof javax.swing.DefaultRowSorter<?, ?> s) s.setSortable(i, def.sortable());
        }
    }

    private TableCellRenderer renderer(NekkoraTableModel.Kind kind) {
        return kind == NekkoraTableModel.Kind.STATUS ? new StatusRenderer() : new TextRenderer(kind);
    }

    private void repaintRow(int row) {
        if (row < 0 || row >= getRowCount()) return;
        Rectangle r = getCellRect(row, 0, true);
        repaint(0, r.y, getWidth(), r.height);
    }

    private void bind(KeyStroke ks, String name, Runnable r) {
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ks, name);
        getInputMap(WHEN_FOCUSED).put(ks, name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        });
    }

    private void maybeMenu(MouseEvent e) {
        if (!e.isPopupTrigger() || rowMenu == null) return;
        int row = rowAtPoint(e.getPoint());
        if (row < 0) return;
        setRowSelectionInterval(row, row);
        JPopupMenu menu = rowMenu.apply(convertRowIndexToModel(row));
        if (menu != null) menu.show(this, e.getX(), e.getY());
    }

    private void keyboardMenu() {
        int row = getSelectedRow();
        if (row < 0 || rowMenu == null) return;
        JPopupMenu menu = rowMenu.apply(convertRowIndexToModel(row));
        Rectangle r = getCellRect(row, 0, true);
        if (menu != null) menu.show(this, r.x + Scale.px(24), r.y + r.height);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyStyle();
    }

    private void applyStyle() {
        if (!initialized) return;
        setFont(NekkoraFonts.font(TypeStyle.BODY));
        setRowHeight(Scale.px(36));
        setShowVerticalLines(false);
        setShowHorizontalLines(true);
        setIntercellSpacing(new java.awt.Dimension(0, 1));
        if (getTableHeader() != null) getTableHeader().setFont(NekkoraFonts.font(TypeStyle.LABEL));
        if (getParent() instanceof javax.swing.JViewport vp) vp.setBackground(Tokens.color(ColorToken.BACKGROUND_SURFACE));
        configureColumns();
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component c = super.prepareRenderer(renderer, row, column);
        boolean selected = isRowSelected(row);
        if (!selected) {
            Color bg = Tokens.color(ColorToken.BACKGROUND_SURFACE);
            if (striped && row % 2 == 1) bg = com.nekkora.tokens.ColorMath.over(Tokens.color(ColorToken.STATUS_NEUTRAL_SOFT, 0.5), bg);
            if (row == hoverRow) bg = com.nekkora.tokens.ColorMath.over(Tokens.color(ColorToken.BACKGROUND_HOVER), bg);
            c.setBackground(bg);
        } else {
            c.setBackground(isFocusOwner() || !isShowing() ? Tokens.color(ColorToken.SELECTION_BACKGROUND) : Tokens.color(ColorToken.SELECTION_INACTIVE));
        }
        return c;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getRowCount() > 0) return;
        Graphics2D g2 = Paint.prepare(g);
        Rectangle vis = getVisibleRect();
        int icon = Scale.px(IconSize.XL.px());
        int y = vis.y + Math.max(Scale.px(24), vis.height / 2 - Scale.px(48));
        int cx = vis.x + vis.width / 2;
        NekkoraIcon.of(emptyIcon, IconSize.XL).paint(g2, cx - icon / 2, y, icon,
                Tokens.color(ColorToken.TEXT_MUTED), Tokens.color(ColorToken.ACCENT));
        y += icon + Scale.px(Spacing.SPACE_5);
        g2.setFont(NekkoraFonts.font(TypeStyle.H3));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Tokens.color(ColorToken.TEXT_PRIMARY));
        Paint.text(this, g2, emptyTitle, cx - fm.stringWidth(emptyTitle) / 2f, y + fm.getAscent());
        y += fm.getHeight() + Scale.px(4);
        if (emptyDescription != null) {
            g2.setFont(NekkoraFonts.font(TypeStyle.BODY_S));
            fm = g2.getFontMetrics();
            g2.setColor(Tokens.color(ColorToken.TEXT_SECONDARY));
            String d = Paint.ellipsize(emptyDescription, fm, vis.width - Scale.px(48));
            Paint.text(this, g2, d, cx - fm.stringWidth(d) / 2f, y + fm.getAscent());
        }
        g2.dispose();
    }

    /** Renderizador de texto por tipo de coluna. */
    private final class TextRenderer extends DefaultTableCellRenderer {
        private final NekkoraTableModel.Kind kind;

        TextRenderer(NekkoraTableModel.Kind kind) {
            this.kind = kind;
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(t, value, selected, false, row, column);
            l.setBorder(BorderFactory.createEmptyBorder(0, Scale.px(12), 0, Scale.px(12)));
            l.setHorizontalAlignment(kind == NekkoraTableModel.Kind.NUMBER ? SwingConstants.RIGHT : SwingConstants.LEFT);
            switch (kind) {
                case SECONDARY -> {
                    l.setFont(NekkoraFonts.font(TypeStyle.BODY_S));
                    l.setForeground(Tokens.color(ColorToken.TEXT_SECONDARY));
                }
                case MONO -> {
                    l.setFont(NekkoraFonts.font(TypeStyle.MONOSPACE));
                    l.setForeground(Tokens.color(ColorToken.TEXT_SECONDARY));
                }
                default -> {
                    l.setFont(NekkoraFonts.font(TypeStyle.BODY));
                    l.setForeground(Tokens.color(ColorToken.TEXT_PRIMARY));
                }
            }
            if (!t.isEnabled()) l.setForeground(Tokens.color(ColorToken.TEXT_DISABLED));
            l.setToolTipText(value == null ? null : value.toString());
            return l;
        }
    }

    /** Cabeçalho: delega ao do Look and Feel (ordenação, estilo) e só ajusta o alinhamento. */
    private final class HeaderRenderer implements TableCellRenderer {
        private final int alignment;

        HeaderRenderer(int alignment) {
            this.alignment = alignment;
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            Component c = getTableHeader().getDefaultRenderer()
                    .getTableCellRendererComponent(t, value, selected, focus, row, column);
            if (c instanceof JLabel l) {
                l.setHorizontalAlignment(alignment);
                l.setBorder(BorderFactory.createEmptyBorder(0, Scale.px(12), 0, Scale.px(12)));
            }
            return c;
        }
    }

    /** Renderizador de {@link Status} como badge. */
    private static final class StatusRenderer extends JPanel implements TableCellRenderer {
        private final NekkoraStatusBadge badge = new NekkoraStatusBadge(Status.PENDING);

        StatusRenderer() {
            super(new FlowLayout(FlowLayout.LEFT, 0, 0));
            add(badge);
        }

        /** O CellRendererPane só valida contêineres com peer; garante o layout em qualquer caso. */
        @Override
        public void setBounds(int x, int y, int w, int h) {
            super.setBounds(x, y, w, h);
            doLayout();
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            setBorder(BorderFactory.createEmptyBorder((t.getRowHeight() - badge.getPreferredSize().height) / 2, Scale.px(12), 0, 0));
            if (value instanceof Status s) {
                badge.setStatus(s);
                badge.setVisible(true);
            } else {
                badge.setVisible(false);
            }
            return this;
        }
    }

    /** Evita que o cabeçalho herde a borda padrão do FlatLaf em contêineres sem borda. */
    @Override
    protected void configureEnclosingScrollPane() {
        super.configureEnclosingScrollPane();
        if (getParent() instanceof javax.swing.JViewport vp && vp.getParent() instanceof javax.swing.JScrollPane sp) {
            JComponent corner = new JPanel(new BorderLayout());
            corner.setBackground(Tokens.color(ColorToken.BACKGROUND_SURFACE));
            sp.setCorner(javax.swing.ScrollPaneConstants.UPPER_RIGHT_CORNER, corner);
        }
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        Point p = e.getPoint();
        return rowAtPoint(p) < 0 ? null : super.getToolTipText(e);
    }
}
