package com.nekkora.ui.studio;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import com.nekkora.ui.typography.NekkoraLabel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Graphics;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Cabeçalho compacto de painel lateral: título em caixa alta + ações à direita + linha inferior. */
public class NekkoraPanelHeader extends JPanel {
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, Scale.px(2), 0));

    public NekkoraPanelHeader(String title) {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(Scale.px(6), Scale.px(Spacing.SPACE_5), Scale.px(6), Scale.px(Spacing.SPACE_3)));
        actions.setOpaque(false);
        add(NekkoraLabel.overline(title), BorderLayout.CENTER);
        add(actions, BorderLayout.EAST);
    }

    public void setActions(JComponent... components) {
        actions.removeAll();
        for (JComponent c : components) actions.add(c);
        actions.revalidate();
    }

    @Override
    public java.awt.Dimension getPreferredSize() {
        java.awt.Dimension d = super.getPreferredSize();
        return new java.awt.Dimension(d.width, Math.max(d.height, Scale.px(40)));
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(Tokens.color(ColorToken.BORDER_SUBTLE));
        int t = Math.max(1, Scale.px(1));
        g.fillRect(0, getHeight() - t, getWidth(), t);
    }
}
