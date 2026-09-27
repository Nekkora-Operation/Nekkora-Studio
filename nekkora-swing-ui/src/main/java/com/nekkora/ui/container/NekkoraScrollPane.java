package com.nekkora.ui.container;

import com.nekkora.ui.theme.Scale;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.UIManager;

/**
 * Área rolável: barras finas e arredondadas, rolagem suave, sem borda por padrão.
 * Com {@link #setFieldBorder(boolean)} vira "campo" (borda com foco/erro — usado pela
 * {@code NekkoraTextArea}).
 */
public class NekkoraScrollPane extends JScrollPane {
    private boolean fieldBorder;
    private final boolean initialized;

    public NekkoraScrollPane(Component view) {
        this(view, false);
    }

    public NekkoraScrollPane(Component view, boolean fieldBorder) {
        super(view);
        this.fieldBorder = fieldBorder;
        initialized = true;
        applyStyle();
    }

    /**
     * Rolagem só vertical: o conteúdo acompanha a largura visível (quebra/encolhe em vez de
     * vazar para a direita). Ideal para painéis laterais e formulários.
     */
    public static NekkoraScrollPane verticalOnly(Component content) {
        NekkoraScrollPane sp = new NekkoraScrollPane(new WidthTrackingPanel(content));
        sp.setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        return sp;
    }

    private static final class WidthTrackingPanel extends javax.swing.JPanel implements javax.swing.Scrollable {
        WidthTrackingPanel(Component content) {
            super(new java.awt.BorderLayout());
            setOpaque(false);
            add(content, java.awt.BorderLayout.NORTH);
        }

        @Override
        public java.awt.Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) {
            return Scale.px(16);
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) {
            return Math.max(r.height - Scale.px(32), Scale.px(16));
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof javax.swing.JViewport vp && getPreferredSize().height < vp.getHeight();
        }
    }

    public void setFieldBorder(boolean fieldBorder) {
        this.fieldBorder = fieldBorder;
        applyStyle();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyStyle();
    }

    private void applyStyle() {
        if (!initialized) return;
        setBorder(fieldBorder ? UIManager.getBorder("ScrollPane.border") : BorderFactory.createEmptyBorder());
        getVerticalScrollBar().setUnitIncrement(Scale.px(16));
        getHorizontalScrollBar().setUnitIncrement(Scale.px(16));
        setOpaque(false);
        getViewport().setOpaque(false);
        if (getViewport().getView() instanceof javax.swing.JComponent v && v.isOpaque()) {
            getViewport().setOpaque(true);
            getViewport().setBackground(v.getBackground());
        }
    }
}
