package com.nekkora.ui.container;

import com.nekkora.tokens.ColorToken;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import javax.swing.BorderFactory;
import javax.swing.JSplitPane;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

/**
 * Divisão redimensionável: linha de 1px (border.subtle) com área de arraste confortável;
 * destaca em foco/arraste. Setas + F8 ajustam pelo teclado (padrão do Swing).
 */
public class NekkoraSplitPane extends JSplitPane {
    private final boolean initialized;

    public NekkoraSplitPane(int orientation, Component first, Component second) {
        super(orientation, true, first, second);
        initialized = true;
        applyStyle();
    }

    public static NekkoraSplitPane horizontal(Component left, Component right) {
        return new NekkoraSplitPane(HORIZONTAL_SPLIT, left, right);
    }

    public static NekkoraSplitPane vertical(Component top, Component bottom) {
        return new NekkoraSplitPane(VERTICAL_SPLIT, top, bottom);
    }

    /** Proporção inicial (0..1) aplicada quando o componente ganha tamanho. */
    public NekkoraSplitPane withProportion(double proportion) {
        setResizeWeight(proportion);
        addHierarchyListener(e -> {
            if (isShowing() && getWidth() > 0) setDividerLocation(proportion);
        });
        return this;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyStyle();
    }

    private void applyStyle() {
        if (!initialized) return;
        setBorder(null);
        setDividerSize(Scale.px(5));
        setContinuousLayout(true);
        setOpaque(false);
        setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new Divider(this);
            }
        });
        setBorder(null);
    }

    private static final class Divider extends BasicSplitPaneDivider {
        private boolean hover;

        Divider(BasicSplitPaneUI ui) {
            super(ui);
            setBorder(BorderFactory.createEmptyBorder());
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        public void paint(Graphics g) {
            boolean horizontal = orientation == JSplitPane.HORIZONTAL_SPLIT;
            boolean active = hover || isMouseOver() || (splitPane != null && splitPane.hasFocus());
            g.setColor(Tokens.color(active ? ColorToken.BORDER_FOCUS : ColorToken.BORDER_SUBTLE));
            int t = Math.max(1, Scale.px(active ? 2 : 1));
            if (horizontal) g.fillRect((getWidth() - t) / 2, 0, t, getHeight());
            else g.fillRect(0, (getHeight() - t) / 2, getWidth(), t);
        }

        @Override
        public Cursor getCursor() {
            return Cursor.getPredefinedCursor(orientation == JSplitPane.HORIZONTAL_SPLIT
                    ? Cursor.E_RESIZE_CURSOR : Cursor.N_RESIZE_CURSOR);
        }
    }
}
