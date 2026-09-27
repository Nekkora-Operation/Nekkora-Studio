package com.nekkora.ui.container;

import com.nekkora.ui.core.NekkoraComponent;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Spacing;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.core.Interaction;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.IconSize;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.internal.InteractionTracker;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/**
 * Painel recolhível: cabeçalho clicável (chevron + título + extras) e conteúdo. Enter/Espaço
 * no cabeçalho alternam. Base do grupo de propriedades do Inspector.
 */
public class NekkoraCollapsiblePanel extends NekkoraPanel {
    private final Header header;
    private final JPanel extras = new JPanel(new FlowLayout(FlowLayout.RIGHT, Scale.px(4), 0));
    private final JPanel body = new JPanel(new BorderLayout());
    private final List<Consumer<Boolean>> listeners = new ArrayList<>();
    private boolean expanded = true;

    public NekkoraCollapsiblePanel(String title) {
        this(title, null);
    }

    public NekkoraCollapsiblePanel(String title, JComponent content) {
        super(new BorderLayout());
        header = new Header(title);
        extras.setOpaque(false);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(header, BorderLayout.CENTER);
        top.add(extras, BorderLayout.EAST);
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(Scale.px(Spacing.SPACE_2), Scale.px(Spacing.SPACE_2),
                Scale.px(Spacing.SPACE_4), Scale.px(Spacing.SPACE_2)));
        add(top, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        if (content != null) setContent(content);
    }

    public void setContent(JComponent content) {
        body.removeAll();
        body.add(content, BorderLayout.CENTER);
        body.revalidate();
    }

    /** Componentes à direita do cabeçalho (badge, botão de ação). */
    public void setHeaderExtras(JComponent... components) {
        extras.removeAll();
        for (JComponent c : components) extras.add(c);
        extras.revalidate();
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        if (this.expanded == expanded) return;
        this.expanded = expanded;
        body.setVisible(expanded);
        header.repaint();
        header.getAccessibleContext().firePropertyChange(
                javax.accessibility.AccessibleContext.ACCESSIBLE_STATE_PROPERTY,
                expanded ? AccessibleState.COLLAPSED : AccessibleState.EXPANDED,
                expanded ? AccessibleState.EXPANDED : AccessibleState.COLLAPSED);
        revalidate();
        repaint();
        listeners.forEach(l -> l.accept(expanded));
    }

    public void toggle() {
        setExpanded(!expanded);
    }

    public void onToggle(Consumer<Boolean> listener) {
        listeners.add(listener);
    }

    /** Acesso ao cabeçalho (foco, testes). */
    public JComponent getHeader() {
        return header;
    }

    private final class Header extends NekkoraComponent {
        private final String title;
        private final InteractionTracker tracker;

        Header(String title) {
            this.title = title;
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            tracker = InteractionTracker.install(this);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    toggle();
                    requestFocusInWindow();
                }
            });
            for (String k : new String[] {"ENTER", "SPACE"}) getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(k), "toggle");
            getActionMap().put("toggle", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    toggle();
                }
            });
            getAccessibleContext().setAccessibleName(title);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Scale.px(120), Scale.px(32));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Paint.prepare(g);
            float r = Scale.px(com.nekkora.tokens.Radius.MEDIUM);
            float fw = Paint.focusWidth();
            Interaction st = tracker.state();
            if (st != Interaction.DEFAULT) {
                Paint.fillRound(g2, fw, fw, getWidth() - 2 * fw, getHeight() - 2 * fw, r, Tokens.color(ColorToken.BACKGROUND_HOVER));
            }
            if (tracker.isFocused()) Paint.focusRing(g2, fw, fw, getWidth() - 2 * fw, getHeight() - 2 * fw, r);
            int icon = Scale.px(IconSize.S.px());
            int x = Scale.px(Spacing.SPACE_3);
            NekkoraIcon.of(IconName.CHEVRON_RIGHT, IconSize.S).withRotation(expanded ? 90 : 0)
                    .paint(g2, x, (getHeight() - icon) / 2, icon, Tokens.color(ColorToken.TEXT_MUTED), Tokens.color(ColorToken.TEXT_MUTED));
            g2.setFont(NekkoraFonts.font(TypeStyle.LABEL, com.nekkora.tokens.FontWeight.SEMIBOLD));
            g2.setColor(Tokens.color(ColorToken.TEXT_PRIMARY));
            FontMetrics fm = g2.getFontMetrics();
            Paint.text(this, g2, Paint.ellipsize(title, fm, getWidth() - x - icon - Scale.px(12)), x + icon + Scale.px(6),
                    Paint.centeredBaseline(fm, 0, getHeight()));
            g2.dispose();
        }

        @Override
        protected AccessibleRole accessibleRole() {
            return AccessibleRole.PUSH_BUTTON;
        }

        @Override
        protected void accessibleStates(javax.accessibility.AccessibleStateSet states) {
            states.add(expanded ? AccessibleState.EXPANDED : AccessibleState.COLLAPSED);
        }
    }
}
