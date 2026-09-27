package com.nekkora.ui.data;

import com.nekkora.ui.core.NekkoraComponent;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.FontRole;
import com.nekkora.tokens.FontWeight;
import com.nekkora.tokens.Radius;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.icon.IconName;
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
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;

/**
 * Tag: palavra-chave/filtro, cantos pequenos. Pode ser removível (botão "x"; Delete/Backspace
 * quando focada).
 */
public class NekkoraTag extends NekkoraComponent {
    private String text;
    private Tone tone;
    private IconName icon;
    private Runnable onRemove;
    private final InteractionTracker tracker;
    private boolean overClose;

    public NekkoraTag(String text) {
        this(text, Tone.NEUTRAL);
    }

    public NekkoraTag(String text, Tone tone) {
        this.text = text;
        this.tone = tone;
        tracker = InteractionTracker.install(this);
        getAccessibleContext().setAccessibleName(text);
    }

    public NekkoraTag withIcon(IconName icon) {
        this.icon = icon;
        revalidate();
        return this;
    }

    /** Torna removível: mostra "x" e chama {@code onRemove} ao clicar ou com Delete. */
    public NekkoraTag removable(Runnable onRemove) {
        this.onRemove = onRemove;
        setFocusable(true);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (isOverClose(e.getX()) && isEnabled()) onRemove.run();
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                boolean over = isOverClose(e.getX());
                if (over != overClose) {
                    overClose = over;
                    setCursor(Cursor.getPredefinedCursor(over ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    repaint();
                }
            }
        });
        for (int key : new int[] {KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE}) {
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), "nekkora.remove");
        }
        getActionMap().put("nekkora.remove", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isEnabled()) onRemove.run();
            }
        });
        getAccessibleContext().setAccessibleDescription("Pressione Delete para remover");
        revalidate();
        return this;
    }

    public String getText() {
        return text;
    }

    private boolean isOverClose(int x) {
        return onRemove != null && x >= getWidth() - Scale.px(20);
    }

    private java.awt.Font font() {
        return NekkoraFonts.font(FontRole.UI, FontWeight.MEDIUM, 12f);
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(font());
        int w = Scale.px(8) * 2 + fm.stringWidth(text);
        if (icon != null) w += Scale.px(14 + 4);
        if (onRemove != null) w += Scale.px(14);
        int fw = onRemove != null ? Math.round(Paint.focusWidth()) : 0;
        return new Dimension(w + 2 * fw, Scale.px(24) + 2 * fw);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Paint.prepare(g);
        try {
            float fw = onRemove != null ? Paint.focusWidth() : 0;
            float x0 = fw, y0 = fw, w = getWidth() - 2 * fw, h = getHeight() - 2 * fw;
            float r = Scale.px(Radius.SMALL);
            if (tracker.isFocused()) Paint.focusRing(g2, x0, y0, w, h, r);
            if (!isEnabled()) Paint.disabledComposite(g2);
            Paint.fillRound(g2, x0, y0, w, h, r, Tokens.color(tone.soft()));
            Paint.strokeRound(g2, x0, y0, w, h, r, com.nekkora.tokens.ColorMath.alpha(Tokens.color(tone.foreground()), 0.25));
            Color fg = Tokens.color(tone == Tone.NEUTRAL ? ColorToken.TEXT_PRIMARY : tone.foreground());
            g2.setFont(font());
            FontMetrics fm = g2.getFontMetrics();
            float x = x0 + Scale.px(8);
            if (icon != null) {
                int s = Scale.px(14);
                NekkoraIcon.of(icon, 14).paint(g2, Math.round(x), Math.round(y0 + (h - s) / 2), s, fg, fg);
                x += s + Scale.px(4);
            }
            g2.setColor(fg);
            Paint.text(this, g2, text, x, Paint.centeredBaseline(fm, Math.round(y0), Math.round(h)));
            if (onRemove != null) {
                int s = Scale.px(12);
                float cx = x0 + w - Scale.px(6) - s;
                if (overClose) Paint.fillRound(g2, cx - Scale.px(2), y0 + (h - s) / 2 - Scale.px(2), s + Scale.px(4), s + Scale.px(4), Scale.px(3), Tokens.color(ColorToken.BACKGROUND_HOVER));
                NekkoraIcon.of(IconName.CLOSE, 12).paint(g2, Math.round(cx), Math.round(y0 + (h - s) / 2), s, fg, fg);
            }
        } finally {
            g2.dispose();
        }
    }
}
