package com.nekkora.ui.internal;

import com.nekkora.ui.core.Interaction;
import com.nekkora.ui.core.NekkoraUI;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/** Acompanha hover/pressed/foco de um componente pintado pela lib e pede repaint. */
public final class InteractionTracker extends MouseAdapter {
    private final JComponent c;
    private boolean hover;
    private boolean pressed;

    private InteractionTracker(JComponent c) {
        this.c = c;
    }

    public static InteractionTracker install(JComponent c) {
        InteractionTracker t = new InteractionTracker(c);
        c.addMouseListener(t);
        c.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                c.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                c.repaint();
            }
        });
        return t;
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        hover = true;
        c.repaint();
    }

    @Override
    public void mouseExited(MouseEvent e) {
        hover = false;
        pressed = false;
        c.repaint();
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && c.isEnabled()) {
            pressed = true;
            c.repaint();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        pressed = false;
        c.repaint();
    }

    public boolean isHover() {
        return forced() == Interaction.HOVER || (forced() == null && hover);
    }

    public boolean isPressed() {
        if (forced() == Interaction.PRESSED) return true;
        if (forced() != null) return false;
        if (c instanceof AbstractButton b) return b.getModel().isArmed() && b.getModel().isPressed() || pressed && hover;
        return pressed && hover;
    }

    /** Foco visível: foco real ou forçado pelo showcase. */
    public boolean isFocused() {
        return Boolean.TRUE.equals(c.getClientProperty(NekkoraUI.FORCED_FOCUS)) || (forced() == null && c.isFocusOwner());
    }

    /** Estado dominante para pintura. */
    public Interaction state() {
        if (!c.isEnabled()) return Interaction.DEFAULT;
        if (isPressed()) return Interaction.PRESSED;
        if (isHover()) return Interaction.HOVER;
        return Interaction.DEFAULT;
    }

    private Interaction forced() {
        Object v = c.getClientProperty(NekkoraUI.FORCED_INTERACTION);
        return v instanceof Interaction i ? i : null;
    }
}
