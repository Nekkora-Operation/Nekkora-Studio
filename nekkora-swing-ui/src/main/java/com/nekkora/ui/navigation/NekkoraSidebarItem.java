package com.nekkora.ui.navigation;

import com.nekkora.ui.core.NekkoraComponent;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.FontRole;
import com.nekkora.tokens.FontWeight;
import com.nekkora.tokens.Radius;
import com.nekkora.ui.core.Interaction;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.IconSize;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.internal.InteractionTracker;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/** Item de navegação da {@link NekkoraSidebar}: ícone, rótulo e contador opcional. */
public class NekkoraSidebarItem extends NekkoraComponent {
    private final String id;
    private String label;
    private IconName icon;
    private Integer count;
    private boolean selected;
    private boolean collapsed;
    private final InteractionTracker tracker;
    NekkoraSidebar sidebar;

    public NekkoraSidebarItem(String id, String label, IconName icon) {
        this.id = id;
        this.label = label;
        this.icon = icon;
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tracker = InteractionTracker.install(this);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (isEnabled()) activate();
            }
        });
        for (String k : new String[] {"ENTER", "SPACE"}) getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(k), "activate");
        getActionMap().put("activate", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isEnabled()) activate();
            }
        });
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DOWN"), "next");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("UP"), "previous");
        getActionMap().put("next", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (sidebar != null) sidebar.focusRelative(NekkoraSidebarItem.this, 1);
            }
        });
        getActionMap().put("previous", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (sidebar != null) sidebar.focusRelative(NekkoraSidebarItem.this, -1);
            }
        });
        getAccessibleContext().setAccessibleName(label);
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
        getAccessibleContext().setAccessibleName(label);
        repaint();
    }

    public void setIcon(IconName icon) {
        this.icon = icon;
        repaint();
    }

    /** Contador à direita (null esconde). */
    public void setCount(Integer count) {
        this.count = count;
        repaint();
    }

    public boolean isSelected() {
        return selected;
    }

    void setSelectedInternal(boolean selected) {
        this.selected = selected;
        repaint();
    }

    void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        setToolTipText(collapsed ? label : null);
        revalidate();
        repaint();
    }

    private void activate() {
        if (sidebar != null) sidebar.select(this);
        else setSelectedInternal(true);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Scale.px(collapsed ? 44 : 200), Scale.px(36));
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Paint.prepare(g);
        try {
            float fw = Paint.focusWidth();
            float x = fw, y = fw, w = getWidth() - 2 * fw, h = getHeight() - 2 * fw;
            float r = Scale.px(Radius.MEDIUM);
            Interaction st = tracker.state();
            if (!isEnabled()) Paint.disabledComposite(g2);
            if (selected) {
                Paint.fillRound(g2, x, y, w, h, r, Tokens.color(ColorToken.ACCENT_SOFT));
                g2.setColor(Tokens.color(ColorToken.ACCENT));
                float bh = h * 0.5f;
                g2.fill(new java.awt.geom.RoundRectangle2D.Float(x, y + (h - bh) / 2, Scale.px(3), bh, Scale.px(3), Scale.px(3)));
            } else if (st != Interaction.DEFAULT) {
                Paint.fillRound(g2, x, y, w, h, r, Tokens.color(st == Interaction.PRESSED ? ColorToken.BACKGROUND_PRESSED : ColorToken.BACKGROUND_HOVER));
            }
            if (tracker.isFocused()) Paint.focusRing(g2, x, y, w, h, r);

            int is = Scale.px(IconSize.L.px());
            float ix = collapsed ? x + (w - is) / 2 : x + Scale.px(12);
            Color ic = Tokens.color(selected ? ColorToken.ACCENT : st == Interaction.DEFAULT ? ColorToken.TEXT_SECONDARY : ColorToken.TEXT_PRIMARY);
            if (icon != null) NekkoraIcon.of(icon, IconSize.L).paint(g2, Math.round(ix), Math.round(y + (h - is) / 2), is, ic, ic);
            if (collapsed) {
                if (count != null && count > 0) {
                    float d = Scale.pxf(7);
                    g2.setColor(Tokens.color(ColorToken.ACCENT));
                    g2.fill(new java.awt.geom.Ellipse2D.Float(ix + is - d / 2, y + (h - is) / 2 - d / 3, d, d));
                }
                return;
            }
            g2.setFont(NekkoraFonts.font(FontRole.UI, selected ? FontWeight.SEMIBOLD : FontWeight.MEDIUM, 13f));
            FontMetrics fm = g2.getFontMetrics();
            float tx = ix + is + Scale.px(10);
            int countW = 0;
            String c = count == null || count <= 0 ? null : count > 99 ? "99+" : count.toString();
            java.awt.Font cf = NekkoraFonts.font(FontRole.UI, FontWeight.SEMIBOLD, 11f);
            if (c != null) countW = Math.max(Scale.px(20), g2.getFontMetrics(cf).stringWidth(c) + Scale.px(10));
            g2.setColor(Tokens.color(selected || st != Interaction.DEFAULT ? ColorToken.TEXT_PRIMARY : ColorToken.TEXT_SECONDARY));
            Paint.text(this, g2, Paint.ellipsize(label, fm, Math.round(x + w - tx - Scale.px(10) - countW)), tx,
                    Paint.centeredBaseline(fm, Math.round(y), Math.round(h)));
            if (c != null) {
                g2.setFont(cf);
                FontMetrics cm = g2.getFontMetrics();
                float bh = Scale.pxf(18), bx = x + w - Scale.px(10) - countW;
                Paint.fillRound(g2, bx, y + (h - bh) / 2, countW, bh, bh / 2, Tokens.color(selected ? ColorToken.ACCENT_SOLID : ColorToken.STATUS_NEUTRAL_SOFT));
                g2.setColor(Tokens.color(selected ? ColorToken.STATUS_ON_SOLID : ColorToken.TEXT_SECONDARY));
                Paint.text(this, g2, c, bx + (countW - cm.stringWidth(c)) / 2f, Paint.centeredBaseline(cm, Math.round(y), Math.round(h)));
            }
        } finally {
            g2.dispose();
        }
    }

    @Override
    protected AccessibleRole accessibleRole() {
        return AccessibleRole.PAGE_TAB;
    }

    @Override
    protected void accessibleStates(javax.accessibility.AccessibleStateSet states) {
        if (selected) states.add(AccessibleState.SELECTED);
    }
}
