package com.nekkora.ui.technical;

import com.nekkora.ui.core.NekkoraComponent;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Radius;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.internal.InteractionTracker;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;

/**
 * Identificador técnico compacto (UUID, hash): mostra início…fim em monoespaçada; o valor
 * completo fica no tooltip. Clique (ou Enter) copia.
 */
public class NekkoraIdentifierLabel extends NekkoraComponent {
    private final String value;
    private final int visibleChars;
    private final InteractionTracker tracker;
    private boolean copied;

    public NekkoraIdentifierLabel(String value) {
        this(value, 8);
    }

    public NekkoraIdentifierLabel(String value, int visibleChars) {
        this.value = value;
        this.visibleChars = visibleChars;
        setToolTipText(value + "  —  clique para copiar");
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tracker = InteractionTracker.install(this);
        getAccessibleContext().setAccessibleName("Identificador " + value);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                copy();
            }
        });
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "copy");
        getActionMap().put("copy", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copy();
            }
        });
    }

    /** Texto exibido (encurtado). */
    public String displayText() {
        if (value.length() <= visibleChars * 2 + 1) return value;
        return value.substring(0, visibleChars) + "…" + value.substring(value.length() - 4);
    }

    public String getValue() {
        return value;
    }

    private void copy() {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
            copied = true;
            repaint();
            javax.swing.Timer t = new javax.swing.Timer(1500, e -> {
                copied = false;
                repaint();
            });
            t.setRepeats(false);
            t.start();
        } catch (IllegalStateException | java.awt.HeadlessException ignored) {
            // área de transferência indisponível
        }
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(NekkoraFonts.font(TypeStyle.MONOSPACE));
        int fw = Math.round(Paint.focusWidth());
        return new Dimension(fm.stringWidth(displayText()) + Scale.px(8 + 12 + 4 + 8) + 2 * fw, Scale.px(22) + 2 * fw);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Paint.prepare(g);
        float fw = Paint.focusWidth();
        float x = fw, y = fw, w = getWidth() - 2 * fw, h = getHeight() - 2 * fw;
        float r = Scale.px(Radius.SMALL);
        if (tracker.isFocused()) Paint.focusRing(g2, x, y, w, h, r);
        Paint.fillRound(g2, x, y, w, h, r, Tokens.color(ColorToken.BACKGROUND_SUNKEN));
        Paint.strokeRound(g2, x, y, w, h, r, Tokens.color(tracker.isHover() ? ColorToken.BORDER_DEFAULT : ColorToken.BORDER_SUBTLE));
        g2.setFont(NekkoraFonts.font(TypeStyle.MONOSPACE));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Tokens.color(ColorToken.TEXT_SECONDARY));
        Paint.text(this, g2, displayText(), x + Scale.px(8), Paint.centeredBaseline(fm, Math.round(y), Math.round(h)));
        int s = Scale.px(12);
        java.awt.Color c = Tokens.color(copied ? ColorToken.STATUS_SUCCESS : ColorToken.TEXT_MUTED);
        NekkoraIcon.of(copied ? IconName.CHECK : IconName.COPY, 12).paint(g2, Math.round(x + w - Scale.px(8) - s),
                Math.round(y + (h - s) / 2), s, c, c);
        g2.dispose();
    }
}
