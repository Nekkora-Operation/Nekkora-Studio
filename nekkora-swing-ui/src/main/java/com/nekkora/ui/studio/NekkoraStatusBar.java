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
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Barra de status inferior: itens curtos à esquerda (estado) e à direita (contexto). */
public class NekkoraStatusBar extends JPanel {
    private final JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, Scale.px(Spacing.SPACE_6), 0));
    private final JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, Scale.px(Spacing.SPACE_6), 0));

    public NekkoraStatusBar() {
        super(new BorderLayout());
        setOpaque(false);
        left.setOpaque(false);
        right.setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(Scale.px(1), Scale.px(Spacing.SPACE_4), 0, Scale.px(Spacing.SPACE_4)));
        add(left, BorderLayout.WEST);
        add(right, BorderLayout.EAST);
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

    public void addRight(JComponent c) {
        right.add(c);
        right.revalidate();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(super.getPreferredSize().width, Scale.px(28));
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(Tokens.color(ColorToken.BACKGROUND_CANVAS));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Tokens.color(ColorToken.BORDER_SUBTLE));
        g.fillRect(0, 0, getWidth(), Math.max(1, Scale.px(1)));
    }
}
