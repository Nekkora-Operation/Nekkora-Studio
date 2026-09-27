package com.nekkora.ui.feedback;

import com.formdev.flatlaf.FlatClientProperties;
import com.nekkora.tokens.ColorToken;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.Dimension;
import javax.swing.JProgressBar;

/** Barra de progresso fina e arredondada. Indeterminada para esperas sem estimativa. */
public class NekkoraProgressBar extends JProgressBar {
    private Tone tone;
    private boolean initialized;

    public NekkoraProgressBar() {
        this(0, 100);
    }

    public NekkoraProgressBar(int min, int max) {
        super(min, max);
        initialized = true;
        applyStyle();
    }

    public static NekkoraProgressBar indeterminate() {
        NekkoraProgressBar b = new NekkoraProgressBar();
        b.setIndeterminate(true);
        return b;
    }

    /** Colore pela semântica (ex.: {@code Tone.SUCCESS} ao concluir, {@code ERROR} em falha). */
    public void setTone(Tone tone) {
        this.tone = tone;
        applyStyle();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(d.width, isStringPainted() ? d.height : Scale.px(6));
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyStyle();
    }

    private void applyStyle() {
        if (!initialized) return;
        putClientProperty(FlatClientProperties.PROGRESS_BAR_LARGE_HEIGHT, false);
        setForeground(Tokens.color(tone == null ? ColorToken.ACCENT : tone.solid()));
        setBackground(Tokens.color(ColorToken.BORDER_SUBTLE));
        setBorder(null);
    }
}
