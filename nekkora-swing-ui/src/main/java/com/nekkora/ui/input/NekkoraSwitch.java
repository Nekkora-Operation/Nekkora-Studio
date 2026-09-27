package com.nekkora.ui.input;

import static com.nekkora.tokens.ColorToken.*;

import com.nekkora.tokens.ColorMath;
import com.nekkora.tokens.Motion;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.core.Interaction;
import com.nekkora.ui.internal.Animator;
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
import java.awt.geom.Ellipse2D;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JToggleButton;

/**
 * Interruptor liga/desliga (efeito imediato — para escolhas que dependem de "Salvar", prefira
 * {@link NekkoraCheckBox}). Espaço/Enter alternam; o polegar desliza em {@link Motion#FAST}.
 */
public class NekkoraSwitch extends JToggleButton {
    private static final int TRACK_W = 32, TRACK_H = 18, GAP = 8;
    private final InteractionTracker tracker;
    private final Animator animator;
    private double position; // 0 = desligado, 1 = ligado
    private double from;
    private boolean initialized;

    public NekkoraSwitch() {
        this(null, false);
    }

    public NekkoraSwitch(String text, boolean selected) {
        super(text, selected);
        position = selected ? 1 : 0;
        tracker = InteractionTracker.install(this);
        animator = new Animator(Motion.FAST, t -> {
            position = from + ((isSelected() ? 1 : 0) - from) * t;
            repaint();
        });
        addItemListener(e -> {
            from = position;
            animator.start();
        });
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        getInputMap().put(javax.swing.KeyStroke.getKeyStroke("ENTER"), "nekkora.toggle");
        getActionMap().put("nekkora.toggle", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isEnabled()) doClick(0);
            }
        });
        initialized = true;
        applyStyle();
    }

    @Override
    public void setSelected(boolean b) {
        super.setSelected(b);
        if (!animator.isRunning()) position = b ? 1 : 0;
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        int fw = Math.round(Paint.focusWidth());
        int w = Scale.px(TRACK_W) + 2 * fw;
        int h = Math.max(Scale.px(TRACK_H) + 2 * fw, Scale.px(24));
        String text = getText();
        if (text != null && !text.isEmpty()) {
            FontMetrics fm = getFontMetrics(getFont());
            w += Scale.px(GAP) + fm.stringWidth(text) + Scale.px(2);
            h = Math.max(h, fm.getHeight());
        }
        return new Dimension(w, h);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyStyle();
    }

    private void applyStyle() {
        if (!initialized) return;
        setFont(NekkoraFonts.font(TypeStyle.BODY));
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setBorder(null);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Paint.prepare(g);
        try {
            float fw = Paint.focusWidth();
            float tw = Scale.pxf(TRACK_W), th = Scale.pxf(TRACK_H);
            float x = fw, y = (getHeight() - th) / 2f;
            if (isEnabled() && tracker.isFocused()) Paint.focusRing(g2, x, y, tw, th, th / 2);
            Graphics2D content = (Graphics2D) g2.create();
            if (!isEnabled()) Paint.disabledComposite(content);
            Interaction st = tracker.state();
            Color off = Tokens.color(BORDER_STRONG);
            Color on = Tokens.color(st == Interaction.HOVER ? ACTION_PRIMARY_HOVER : ACTION_PRIMARY);
            Paint.fillRound(content, x, y, tw, th, th / 2, ColorMath.mix(on, off, position));
            float pad = Scale.pxf(2);
            float d = th - 2 * pad;
            float tx = (float) (x + pad + (tw - 2 * pad - d) * position);
            content.setColor(isSelected() ? Tokens.color(ACTION_PRIMARY_TEXT) : Tokens.color(FIELD_BACKGROUND));
            if (!isSelected() && !Tokens.isDark()) content.setColor(java.awt.Color.WHITE);
            content.fill(new Ellipse2D.Float(tx, y + pad, d, d));
            String text = getText();
            if (text != null && !text.isEmpty()) {
                content.setFont(getFont());
                content.setColor(Tokens.color(TEXT_PRIMARY));
                FontMetrics fm = content.getFontMetrics();
                Paint.text(this, content, text, x + tw + fw + Scale.px(GAP) - fw, Paint.centeredBaseline(fm, 0, getHeight()));
            }
            content.dispose();
        } finally {
            g2.dispose();
        }
    }

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJToggleButton() {
                @Override
                public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.TOGGLE_BUTTON;
                }
            };
        }
        return accessibleContext;
    }
}
