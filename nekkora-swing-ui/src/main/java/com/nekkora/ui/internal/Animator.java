package com.nekkora.ui.internal;

import com.nekkora.tokens.Motion;
import com.nekkora.ui.core.NekkoraUI;
import java.util.function.DoubleConsumer;
import javax.swing.Timer;

/** Animação simples 0→1 com easing (ease-out), respeitando movimento reduzido. */
public final class Animator {
    private final Timer timer;
    private long start;
    private final int duration;
    private final DoubleConsumer onFrame;
    private Runnable onEnd;

    public Animator(Motion motion, DoubleConsumer onFrame) {
        this.duration = motion.millis();
        this.onFrame = onFrame;
        this.timer = new Timer(15, e -> tick());
    }

    public Animator onEnd(Runnable r) {
        this.onEnd = r;
        return this;
    }

    public void start() {
        if (NekkoraUI.isReducedMotion() || java.awt.GraphicsEnvironment.isHeadless()) {
            timer.stop();
            onFrame.accept(1.0);
            if (onEnd != null) onEnd.run();
            return;
        }
        start = System.currentTimeMillis();
        timer.restart();
    }

    public void stop() {
        timer.stop();
    }

    public boolean isRunning() {
        return timer.isRunning();
    }

    private void tick() {
        double t = Math.min(1.0, (System.currentTimeMillis() - start) / (double) duration);
        double eased = 1 - Math.pow(1 - t, 3);
        onFrame.accept(eased);
        if (t >= 1.0) {
            timer.stop();
            if (onEnd != null) onEnd.run();
        }
    }
}
