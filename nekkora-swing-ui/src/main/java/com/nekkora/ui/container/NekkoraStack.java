package com.nekkora.ui.container;

import com.nekkora.tokens.Spacing;
import com.nekkora.ui.theme.Scale;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;

/**
 * Empilha componentes na vertical ou horizontal com espaçamento da escala — substitui
 * {@code Box.createVerticalStrut(12)} espalhados pelas telas.
 *
 * <pre>{@code
 * NekkoraStack form = NekkoraStack.vertical(Spacing.SPACE_6);
 * form.add(nome); form.add(email);
 * }</pre>
 */
public class NekkoraStack extends NekkoraPanel {

    public enum Axis { VERTICAL, HORIZONTAL }

    /** Alinhamento no eixo cruzado. {@link #STRETCH} ocupa toda a largura/altura. */
    public enum Align { START, CENTER, END, STRETCH }

    public NekkoraStack(Axis axis, Spacing gap) {
        super(null);
        setLayout(new StackLayout(axis, gap, axis == Axis.VERTICAL ? Align.STRETCH : Align.CENTER));
    }

    public static NekkoraStack vertical(Spacing gap) {
        return new NekkoraStack(Axis.VERTICAL, gap);
    }

    public static NekkoraStack horizontal(Spacing gap) {
        return new NekkoraStack(Axis.HORIZONTAL, gap);
    }

    public NekkoraStack align(Align align) {
        ((StackLayout) getLayout()).align = align;
        revalidate();
        return this;
    }

    public NekkoraStack gap(Spacing gap) {
        ((StackLayout) getLayout()).gap = gap;
        revalidate();
        return this;
    }

    /** Adiciona vários componentes de uma vez. */
    public NekkoraStack addAll(Component... components) {
        for (Component c : components) add(c);
        return this;
    }

    /** Espaço elástico: empurra os próximos componentes para o fim. */
    public NekkoraStack addGlue() {
        add(new Glue());
        return this;
    }

    static final class Glue extends Component {}

    /** Layout do stack (visível para uso isolado). */
    public static final class StackLayout implements LayoutManager2 {
        private final Axis axis;
        private Spacing gap;
        private Align align;

        public StackLayout(Axis axis, Spacing gap, Align align) {
            this.axis = axis;
            this.gap = gap;
            this.align = align;
        }

        private boolean vertical() {
            return axis == Axis.VERTICAL;
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return size(parent, false);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return size(parent, true);
        }

        @Override
        public Dimension maximumLayoutSize(Container target) {
            return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        private Dimension size(Container parent, boolean min) {
            int main = 0, cross = 0, n = 0;
            int g = gap == null ? 0 : Scale.px(gap);
            for (Component c : parent.getComponents()) {
                if (!c.isVisible() || c instanceof Glue) continue;
                Dimension d = min ? c.getMinimumSize() : c.getPreferredSize();
                main += vertical() ? d.height : d.width;
                cross = Math.max(cross, vertical() ? d.width : d.height);
                n++;
            }
            main += Math.max(0, n - 1) * g;
            Insets in = parent.getInsets();
            return vertical()
                    ? new Dimension(cross + in.left + in.right, main + in.top + in.bottom)
                    : new Dimension(main + in.left + in.right, cross + in.top + in.bottom);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets in = parent.getInsets();
            int w = parent.getWidth() - in.left - in.right, h = parent.getHeight() - in.top - in.bottom;
            int g = gap == null ? 0 : Scale.px(gap);
            int avail = vertical() ? h : w, crossAvail = vertical() ? w : h;
            int used = 0, visible = 0, glues = 0;
            for (Component c : parent.getComponents()) {
                if (!c.isVisible()) continue;
                if (c instanceof Glue) {
                    glues++;
                    continue;
                }
                Dimension d = c.getPreferredSize();
                used += vertical() ? d.height : d.width;
                visible++;
            }
            used += Math.max(0, visible - 1) * g;
            int extra = Math.max(0, avail - used);
            int pos = vertical() ? in.top : in.left;
            boolean first = true;
            for (Component c : parent.getComponents()) {
                if (!c.isVisible()) continue;
                if (c instanceof Glue) {
                    pos += glues == 0 ? 0 : extra / glues;
                    continue;
                }
                if (!first) pos += g;
                first = false;
                Dimension d = c.getPreferredSize();
                Dimension max = c.getMaximumSize();
                int m = vertical() ? d.height : d.width;
                int cross = align == Align.STRETCH ? crossAvail
                        : Math.min(crossAvail, vertical() ? d.width : d.height);
                if (align == Align.STRETCH) cross = Math.min(cross, Math.max(vertical() ? max.width : max.height, 0));
                int crossPos = switch (align) {
                    case CENTER -> (crossAvail - cross) / 2;
                    case END -> crossAvail - cross;
                    default -> 0;
                };
                if (vertical()) c.setBounds(in.left + crossPos, pos, cross, m);
                else c.setBounds(pos, in.top + crossPos, m, cross);
                pos += m;
            }
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {}

        @Override
        public void addLayoutComponent(Component comp, Object constraints) {}

        @Override
        public void removeLayoutComponent(Component comp) {}

        @Override
        public float getLayoutAlignmentX(Container target) {
            return 0;
        }

        @Override
        public float getLayoutAlignmentY(Container target) {
            return 0;
        }

        @Override
        public void invalidateLayout(Container target) {}
    }
}
