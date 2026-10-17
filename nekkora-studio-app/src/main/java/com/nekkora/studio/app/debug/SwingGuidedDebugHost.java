package com.nekkora.studio.app.debug;

import com.nekkora.studio.ui.debug.GuidedDebugPanel;
import com.nekkora.studio.ui.debug.GuidedDebugWindow;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.function.Supplier;

/** Debug guiado numa janela real ({@link GuidedDebugWindow}), criada na primeira exibição. */
public final class SwingGuidedDebugHost implements GuidedDebugPresenter.Host {
    private final Supplier<Window> owner;
    private final GuidedDebugPanel panel;
    private final Runnable onClose;
    private GuidedDebugWindow window;

    public SwingGuidedDebugHost(Supplier<Window> owner, GuidedDebugPanel panel, Runnable onClose) {
        this.owner = owner;
        this.panel = panel;
        this.onClose = onClose;
    }

    public GuidedDebugWindow window() {
        if (window == null) {
            window = new GuidedDebugWindow(owner.get(), panel, onClose);
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
    public void fit() {
        if (window != null) {
            window.fit();
        }
    }

    @Override
    public void avoid(Rectangle target) {
        if (window != null) {
            window.avoid(target);
        }
    }

    @Override
    public void dispose() {
        if (window != null) {
            window.dispose();
        }
    }
}
