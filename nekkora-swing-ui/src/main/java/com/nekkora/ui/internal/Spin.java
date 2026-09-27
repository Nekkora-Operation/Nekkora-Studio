package com.nekkora.ui.internal;

import com.nekkora.ui.core.NekkoraUI;
import java.awt.event.HierarchyEvent;
import javax.swing.JComponent;
import javax.swing.Timer;

/** Relógio de rotação para spinners/ícones "em execução"; só roda enquanto o componente está visível. */
public final class Spin {
    private final JComponent c;
    private final Timer timer;
    private boolean active;
    private double angle;

    public Spin(JComponent c) {
        this.c = c;
        this.timer = new Timer(33, e -> {
            angle = (angle + (NekkoraUI.isReducedMotion() ? 4 : 12)) % 360;
            c.repaint();
        });
        c.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) update();
        });
    }

    public void setActive(boolean active) {
        this.active = active;
        update();
    }

    public boolean isActive() {
        return active;
    }

    public double angle() {
        return angle;
    }

    private void update() {
        if (active && c.isShowing()) {
            if (!timer.isRunning()) timer.start();
        } else {
            timer.stop();
        }
    }
}
