package com.nekkora.ui.file;

import com.nekkora.ui.core.NekkoraComponent;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.FontWeight;
import com.nekkora.tokens.Radius;
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
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;

/** Item de arquivo recente: ícone, nome, caminho (truncado no meio) e quando foi aberto. */
public class NekkoraRecentFileItem extends NekkoraComponent {
    private final String name;
    private final String path;
    private final String when;
    private final IconName icon;
    private final InteractionTracker tracker;
    private Runnable onOpen;

    public NekkoraRecentFileItem(String name, String path, String when) {
        this(name, path, when, IconName.FILE);
    }

    public NekkoraRecentFileItem(String name, String path, String when, IconName icon) {
        this.name = name;
        this.path = path;
        this.when = when;
        this.icon = icon;
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(path);
        tracker = InteractionTracker.install(this);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (onOpen != null) onOpen.run();
            }
        });
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "open");
        getActionMap().put("open", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (onOpen != null) onOpen.run();
            }
        });
        getAccessibleContext().setAccessibleName(name + ", " + path);
    }

    public NekkoraRecentFileItem onOpen(Runnable action) {
        this.onOpen = action;
        return this;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Scale.px(320), Scale.px(52));
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Paint.prepare(g);
        float fw = Paint.focusWidth(), r = Scale.px(Radius.MEDIUM);
        float x0 = fw, y0 = fw, w = getWidth() - 2 * fw, h = getHeight() - 2 * fw;
        if (tracker.state() != Interaction.DEFAULT) Paint.fillRound(g2, x0, y0, w, h, r, Tokens.color(ColorToken.BACKGROUND_HOVER));
        if (tracker.isFocused()) Paint.focusRing(g2, x0, y0, w, h, r);
        int is = Scale.px(IconSize.L.px());
        int x = Math.round(x0) + Scale.px(10);
        Color ic = Tokens.color(ColorToken.TEXT_SECONDARY);
        NekkoraIcon.of(icon, IconSize.L).paint(g2, x, Math.round(y0 + (h - is) / 2), is, ic, ic);
        x += is + Scale.px(12);
        java.awt.Font nf = NekkoraFonts.font(TypeStyle.BODY, FontWeight.MEDIUM);
        java.awt.Font pf = NekkoraFonts.font(TypeStyle.CAPTION);
        FontMetrics nm = g2.getFontMetrics(nf), pm = g2.getFontMetrics(pf);
        int whenW = when == null ? 0 : pm.stringWidth(when) + Scale.px(12);
        int avail = Math.round(x0 + w) - x - Scale.px(10) - whenW;
        int total = nm.getHeight() + pm.getHeight();
        int ty = Math.round(y0 + (h - total) / 2);
        g2.setFont(nf);
        g2.setColor(Tokens.color(ColorToken.TEXT_PRIMARY));
        Paint.text(this, g2, Paint.ellipsize(name, nm, avail), x, ty + nm.getAscent());
        g2.setFont(pf);
        g2.setColor(Tokens.color(ColorToken.TEXT_MUTED));
        Paint.text(this, g2, Paint.ellipsizeMiddle(path, pm, avail), x, ty + nm.getHeight() + pm.getAscent());
        if (when != null) Paint.text(this, g2, when, x0 + w - Scale.px(10) - pm.stringWidth(when), ty + nm.getAscent());
        g2.dispose();
    }
}
