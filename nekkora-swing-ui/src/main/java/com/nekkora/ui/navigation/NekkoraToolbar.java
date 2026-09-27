package com.nekkora.ui.navigation;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.container.Background;
import com.nekkora.ui.container.NekkoraDivider;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JToolBar;

/**
 * Barra de ferramentas horizontal (não flutuante). Setas ←/→ movem o foco entre botões.
 *
 * <pre>{@code
 * NekkoraToolbar tb = new NekkoraToolbar();
 * tb.add(new NekkoraToolbarButton(IconName.PLAY, "Executar", true));
 * tb.addSeparator();
 * tb.addGlue();
 * tb.add(new NekkoraSearchField());
 * }</pre>
 */
public class NekkoraToolbar extends JToolBar {
    private Background background = Background.SURFACE;
    private boolean bottomBorder = true;
    private boolean initialized;

    public NekkoraToolbar() {
        super(HORIZONTAL);
        setFloatable(false);
        setRollover(true);
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        initialized = true;
        applyStyle();
        getAccessibleContext().setAccessibleName("Barra de ferramentas");
    }

    public void setSurface(Background background) {
        this.background = background;
        repaint();
    }

    public void setBottomBorder(boolean bottomBorder) {
        this.bottomBorder = bottomBorder;
        repaint();
    }

    /** Divisória vertical entre grupos de ações. */
    @Override
    public void addSeparator() {
        add(NekkoraDivider.vertical().withMargin(Spacing.SPACE_3));
    }

    /** Espaço elástico: o que vier depois vai para a direita. */
    public void addGlue() {
        add(Box.createHorizontalGlue());
    }

    public void addGap(Spacing gap) {
        add(Box.createHorizontalStrut(Scale.px(gap)));
    }

    @Override
    public Component add(Component comp) {
        Component c = super.add(comp);
        if (comp instanceof javax.swing.JComponent jc) jc.setAlignmentY(CENTER_ALIGNMENT);
        return c;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(d.width, Math.max(d.height, Scale.px(44)));
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyStyle();
    }

    private void applyStyle() {
        if (!initialized) return;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(Scale.px(4), Scale.px(8), Scale.px(4), Scale.px(8)));
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (background.token() != null) {
            g.setColor(Tokens.color(background.token()));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        if (bottomBorder) {
            g.setColor(Tokens.color(ColorToken.BORDER_SUBTLE));
            int t = Math.max(1, Scale.px(1));
            g.fillRect(0, getHeight() - t, getWidth(), t);
        }
    }
}
