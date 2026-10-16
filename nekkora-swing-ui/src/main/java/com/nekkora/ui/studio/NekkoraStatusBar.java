package com.nekkora.ui.studio;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.core.Status;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.IconSize;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import com.nekkora.ui.typography.NekkoraCaption;
import com.nekkora.ui.typography.TextTone;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.LayoutManager;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Barra de status inferior: itens curtos à esquerda (estado) e à direita (contexto).
 *
 * <p>Sem espaço para tudo (janela estreita, escala de 150%), nada se sobrepõe: o lado esquerdo (estado) tem prioridade
 * sobre o direito (contexto) e, em cada lado, os itens acrescentados por último saem primeiro — inteiros, nunca cortados
 * pela metade. Quando tudo cabe, as posições são as mesmas de um {@code BorderLayout} WEST/EAST com {@code FlowLayout}.
 */
public class NekkoraStatusBar extends JPanel {
    private final JPanel left = new JPanel(new Row(false, Scale.px(Spacing.SPACE_6)));
    private final JPanel right = new JPanel(new Row(true, Scale.px(Spacing.SPACE_6)));

    public NekkoraStatusBar() {
        super(null);
        setLayout(new Sides());
        setOpaque(false);
        left.setOpaque(false);
        right.setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(Scale.px(1), Scale.px(Spacing.SPACE_4), 0, Scale.px(Spacing.SPACE_4)));
        add(left);
        add(right);
        getAccessibleContext().setAccessibleName("Barra de status");
    }

    /** Texto com ícone opcional. */
    public static NekkoraCaption item(IconName icon, String text) {
        NekkoraCaption c = new NekkoraCaption(text, TextTone.SECONDARY);
        if (icon != null) c.setIcon(NekkoraIcon.of(icon, IconSize.S));
        c.setIconTextGap(Scale.px(5));
        return c;
    }

    /** Item de status (ícone e cor do {@link Status}). */
    public static NekkoraCaption status(Status status, String text) {
        NekkoraCaption c = new NekkoraCaption(text, TextTone.SECONDARY);
        c.setIcon(NekkoraIcon.of(status.icon(), IconSize.S).withColor(status.tone().foreground()));
        c.setIconTextGap(Scale.px(5));
        return c;
    }

    public void addLeft(JComponent c) {
        left.add(c);
        left.revalidate();
    }

    /** Item à esquerda numa posição (ordem = prioridade: os primeiros permanecem quando falta espaço). */
    public void addLeft(JComponent c, int index) {
        left.add(c, Math.max(0, Math.min(index, left.getComponentCount())));
        left.revalidate();
    }

    /** O item está sendo mostrado (cabe na largura atual). */
    public static boolean shown(Component c) {
        return c.isVisible() && c.getWidth() > 0;
    }

    public void addRight(JComponent c) {
        right.add(c);
        right.revalidate();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(super.getPreferredSize().width, Scale.px(28));
    }

    /** Esquerda com a largura preferida (ou a disponível); direita com o que sobrar — sem sobreposição. */
    private final class Sides implements LayoutManager {
        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Insets in = parent.getInsets();
            Dimension l = left.getPreferredSize();
            Dimension r = right.getPreferredSize();
            return new Dimension(in.left + l.width + r.width + in.right, in.top + Math.max(l.height, r.height) + in.bottom);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            Insets in = parent.getInsets();
            return new Dimension(in.left + in.right, in.top + in.bottom);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets in = parent.getInsets();
            int avail = Math.max(0, parent.getWidth() - in.left - in.right);
            int h = Math.max(0, parent.getHeight() - in.top - in.bottom);
            int lw = Math.min(left.getPreferredSize().width, avail);
            int rw = Math.min(right.getPreferredSize().width, avail - lw);
            left.setBounds(in.left, in.top, lw, h);
            right.setBounds(in.left + avail - rw, in.top, rw, h);
        }
    }

    /**
     * Uma única linha, como {@code FlowLayout} (mesmo espaçamento e alinhamento), mas o item que não cabe — e os
     * seguintes — fica com tamanho zero em vez de ir para uma segunda linha meio visível.
     */
    private static final class Row implements LayoutManager {
        private final boolean alignRight;
        private final int gap;

        Row(boolean alignRight, int gap) {
            this.alignRight = alignRight;
            this.gap = gap;
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Insets in = parent.getInsets();
            int w = 0;
            int h = 0;
            int n = 0;
            for (Component c : parent.getComponents()) {
                if (!c.isVisible()) continue;
                Dimension d = c.getPreferredSize();
                w += d.width + (n++ > 0 ? gap : 0);
                h = Math.max(h, d.height);
            }
            return new Dimension(in.left + in.right + w + gap * 2, in.top + in.bottom + h);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            Insets in = parent.getInsets();
            return new Dimension(in.left + in.right, in.top + in.bottom);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets in = parent.getInsets();
            int max = parent.getWidth() - (in.left + in.right + gap * 2);
            java.util.List<Component> fits = new java.util.ArrayList<>();
            int used = 0;
            int rowH = 0;
            boolean full = false;
            for (Component c : parent.getComponents()) {
                if (!c.isVisible()) continue;
                Dimension d = c.getPreferredSize();
                int need = used + (fits.isEmpty() ? 0 : gap) + d.width;
                if (full || need > max) {
                    full = true;
                    c.setBounds(0, 0, 0, 0);
                    continue;
                }
                fits.add(c);
                used = need;
                rowH = Math.max(rowH, d.height);
            }
            int x = in.left + gap + (alignRight ? max - used : 0);
            for (Component c : fits) {
                Dimension d = c.getPreferredSize();
                c.setBounds(x, in.top + (rowH - d.height) / 2, d.width, d.height);
                x += d.width + gap;
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(Tokens.color(ColorToken.BACKGROUND_CANVAS));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Tokens.color(ColorToken.BORDER_SUBTLE));
        g.fillRect(0, 0, getWidth(), Math.max(1, Scale.px(1)));
    }
}
