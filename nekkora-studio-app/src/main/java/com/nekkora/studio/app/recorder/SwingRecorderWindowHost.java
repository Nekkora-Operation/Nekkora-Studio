package com.nekkora.studio.app.recorder;

import com.nekkora.studio.ui.recorder.FloatingRecorderPanel;
import com.nekkora.studio.ui.recorder.FloatingRecorderWindow;
import java.awt.Window;
import java.util.function.Supplier;

/** Floating Recorder numa janela real ({@link FloatingRecorderWindow}), criada na primeira exibição. */
public final class SwingRecorderWindowHost implements RecorderWindowHost {
    private final Supplier<Window> owner;
    private final FloatingRecorderPanel panel;
    private final Runnable onClose;
    private FloatingRecorderWindow window;
    private boolean onTop = true;

    public SwingRecorderWindowHost(Supplier<Window> owner, FloatingRecorderPanel panel, Runnable onClose) {
        this.owner = owner;
        this.panel = panel;
        this.onClose = onClose;
    }

    private FloatingRecorderWindow window() {
        if (window == null) {
            window = new FloatingRecorderWindow(owner.get(), panel, onClose);
            window.setAlwaysOnTop(onTop);
        }
        return window;
    }

    @Override
    public void show() {
        window().showFloating();
    }

    @Override
    public void hide() {
        if (window != null) {
            window.setVisible(false);
        }
    }

    @Override
    public boolean isShowing() {
        return window != null && window.isVisible();
    }

    @Override
    public void setAlwaysOnTop(boolean value) {
        onTop = value;
        if (window != null) {
            window.setAlwaysOnTop(value);
        }
    }

    @Override
    public boolean isAlwaysOnTop() {
        return onTop;
    }

    @Override
    public void contentChanged() {
        if (window != null && window.isVisible()) {
            window.fit();
        }
    }

    @Override
    public void dispose() {
        if (window != null) {
            window.dispose();
            window = null;
        }
    }

    /** A janela real (evidências). */
    public FloatingRecorderWindow windowForEvidence() {
        return window;
    }
}
