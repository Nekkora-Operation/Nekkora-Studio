package com.nekkora.ui.data;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.IconSize;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

/**
 * Lista Nekkora: itens com ícone, texto principal e secundário (uma ou duas linhas), hover e
 * ativação por Enter/duplo clique.
 */
public class NekkoraList<E> extends JList<E> {
    private Function<? super E, String> label = String::valueOf;
    private Function<? super E, String> secondary;
    private Function<? super E, IconName> icon;
    private Consumer<E> onActivate;
    private int hoverIndex = -1;
    private boolean twoLine;
    private boolean initialized;

    public NekkoraList() {
        this(List.of());
    }

    public NekkoraList(List<E> items) {
        super(new DefaultListModel<>());
        items.forEach(((DefaultListModel<E>) getModel())::addElement);
        initialized = true;
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setCellRenderer(new Renderer());
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = locationToIndex(e.getPoint());
                setHoverIndex(i >= 0 && getCellBounds(i, i).contains(e.getPoint()) ? i : -1);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setHoverIndex(-1);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && onActivate != null && getSelectedValue() != null) onActivate.accept(getSelectedValue());
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "nekkora.activate");
        getActionMap().put("nekkora.activate", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (onActivate != null && getSelectedValue() != null) onActivate.accept(getSelectedValue());
            }
        });
        applyStyle();
    }

    public void setItems(List<E> items) {
        DefaultListModel<E> m = new DefaultListModel<>();
        items.forEach(m::addElement);
        setModel(m);
    }

    public NekkoraList<E> withLabel(Function<? super E, String> provider) {
        this.label = provider;
        repaint();
        return this;
    }

    /** Texto secundário; com {@code twoLine}, fica abaixo do principal. */
    public NekkoraList<E> withSecondary(Function<? super E, String> provider, boolean twoLine) {
        this.secondary = provider;
        this.twoLine = twoLine;
        applyStyle();
        return this;
    }

    public NekkoraList<E> withIcon(Function<? super E, IconName> provider) {
        this.icon = provider;
        repaint();
        return this;
    }

    public void onItemActivated(Consumer<E> listener) {
        this.onActivate = listener;
    }

    public void setHoverIndex(int i) {
        if (i == hoverIndex) return;
        hoverIndex = i;
        repaint();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyStyle();
    }

    private void applyStyle() {
        if (!initialized) return;
        setFont(NekkoraFonts.font(TypeStyle.BODY));
        setFixedCellHeight(Scale.px(twoLine ? 48 : 32));
        setOpaque(false);
        if (getCellRenderer() instanceof Component c) javax.swing.SwingUtilities.updateComponentTreeUI(c);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Paint.prepare(g);
        g2.setColor(Tokens.color(ColorToken.BACKGROUND_SURFACE));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
        super.paintComponent(g);
    }

    private final class Renderer extends JComponent implements ListCellRenderer<E> {
        private E value;
        private boolean selected;
        private boolean hover;

        @Override
        public Component getListCellRendererComponent(JList<? extends E> list, E value, int index, boolean selected,
                                                      boolean focus) {
            this.value = value;
            this.selected = selected;
            this.hover = index == hoverIndex;
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Scale.px(160), Scale.px(twoLine ? 48 : 32));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Paint.prepare(g);
            int in = Scale.px(4), w = getWidth(), h = getHeight();
            float r = Scale.px(com.nekkora.tokens.Radius.MEDIUM);
            if (selected) {
                Paint.fillRound(g2, in, 1, w - 2 * in, h - 2, r, Tokens.color(NekkoraList.this.isFocusOwner()
                        ? ColorToken.SELECTION_BACKGROUND : ColorToken.SELECTION_INACTIVE));
            } else if (hover) {
                Paint.fillRound(g2, in, 1, w - 2 * in, h - 2, r, Tokens.color(ColorToken.BACKGROUND_HOVER));
            }
            int x = in + Scale.px(10);
            IconName ic = icon != null && value != null ? icon.apply(value) : null;
            if (ic != null) {
                int s = Scale.px(IconSize.M.px());
                Color c = Tokens.color(selected ? ColorToken.TEXT_PRIMARY : ColorToken.TEXT_SECONDARY);
                NekkoraIcon.of(ic, IconSize.M).paint(g2, x, (h - s) / 2, s, c, c);
                x += s + Scale.px(10);
            }
            String main = value == null ? "" : label.apply(value);
            String sec = secondary != null && value != null ? secondary.apply(value) : null;
            g2.setFont(NekkoraList.this.getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Tokens.color(isEnabled() ? ColorToken.TEXT_PRIMARY : ColorToken.TEXT_DISABLED));
            int avail = w - x - Scale.px(12);
            if (twoLine && sec != null) {
                java.awt.Font sf = NekkoraFonts.font(TypeStyle.BODY_S);
                FontMetrics fs = g2.getFontMetrics(sf);
                int total = fm.getHeight() + fs.getHeight();
                int y = (h - total) / 2;
                Paint.text(NekkoraList.this, g2, Paint.ellipsize(main, fm, avail), x, y + fm.getAscent());
                g2.setFont(sf);
                g2.setColor(Tokens.color(ColorToken.TEXT_SECONDARY));
                Paint.text(NekkoraList.this, g2, Paint.ellipsize(sec, fs, avail), x, y + fm.getHeight() + fs.getAscent());
            } else {
                int base = Paint.centeredBaseline(fm, 0, h);
                String m = Paint.ellipsize(main, fm, avail);
                Paint.text(NekkoraList.this, g2, m, x, base);
                if (sec != null) {
                    g2.setFont(NekkoraFonts.font(TypeStyle.BODY_S));
                    FontMetrics fs = g2.getFontMetrics();
                    g2.setColor(Tokens.color(ColorToken.TEXT_MUTED));
                    Paint.text(NekkoraList.this, g2, Paint.ellipsize(sec, fs, Math.max(0, avail - fm.stringWidth(m) - Scale.px(8))),
                            x + fm.stringWidth(m) + Scale.px(8), base);
                }
            }
            g2.dispose();
        }
    }
}
