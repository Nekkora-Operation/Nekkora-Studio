package com.nekkora.ui.workflow;

import com.nekkora.ui.core.NekkoraComponent;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.FontRole;
import com.nekkora.tokens.FontWeight;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Etapas de um fluxo (assistentes, progresso de processos). Cada etapa: concluída, atual,
 * futura ou com erro — sempre com número/ícone, não só cor.
 *
 * <pre>{@code
 * NekkoraStepper s = new NekkoraStepper("Origem", "Mapeamento", "Revisão", "Publicar");
 * s.setCurrent(1);
 * }</pre>
 */
public class NekkoraStepper extends NekkoraComponent {

    public enum StepState { COMPLETE, CURRENT, UPCOMING, ERROR }

    public enum Orientation { HORIZONTAL, VERTICAL }

    private final List<String> labels;
    private final List<String> descriptions;
    private final StepState[] states;
    private Orientation orientation = Orientation.HORIZONTAL;

    public NekkoraStepper(String... labels) {
        this(Arrays.asList(labels));
    }

    public NekkoraStepper(List<String> labels) {
        this.labels = List.copyOf(labels);
        this.descriptions = new ArrayList<>();
        labels.forEach(l -> descriptions.add(null));
        this.states = new StepState[labels.size()];
        setCurrent(0);
    }

    public NekkoraStepper withOrientation(Orientation o) {
        this.orientation = o;
        revalidate();
        repaint();
        return this;
    }

    /** Texto de apoio abaixo do rótulo (visível na vertical). */
    public void setDescription(int step, String text) {
        descriptions.set(step, text);
        repaint();
    }

    /** Marca etapas anteriores como concluídas, esta como atual e as seguintes como futuras. */
    public void setCurrent(int index) {
        for (int i = 0; i < states.length; i++) {
            states[i] = i < index ? StepState.COMPLETE : i == index ? StepState.CURRENT : StepState.UPCOMING;
        }
        getAccessibleContext().setAccessibleDescription(
                index < labels.size() ? "Etapa " + (index + 1) + " de " + labels.size() + ": " + labels.get(index) : null);
        repaint();
    }

    public void setState(int index, StepState state) {
        states[index] = state;
        repaint();
    }

    public StepState getState(int index) {
        return states[index];
    }

    public int getStepCount() {
        return labels.size();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(NekkoraFonts.font(TypeStyle.LABEL));
        if (orientation == Orientation.VERTICAL) {
            int w = 0;
            for (String l : labels) w = Math.max(w, fm.stringWidth(l));
            return new Dimension(Scale.px(24 + 12) + w + Scale.px(8), labels.size() * Scale.px(56));
        }
        int w = 0;
        for (String l : labels) w += Math.max(Scale.px(96), fm.stringWidth(l) + Scale.px(24));
        return new Dimension(w, Scale.px(24 + 8) + fm.getHeight() + Scale.px(4));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Paint.prepare(g);
        int n = labels.size();
        int d = Scale.px(24);
        java.awt.Font label = NekkoraFonts.font(TypeStyle.LABEL);
        java.awt.Font labelStrong = NekkoraFonts.font(TypeStyle.LABEL, FontWeight.SEMIBOLD);
        if (orientation == Orientation.HORIZONTAL) {
            float seg = getWidth() / (float) n;
            for (int i = 0; i < n; i++) {
                float cx = seg * i + seg / 2;
                if (i < n - 1) {
                    g2.setColor(Tokens.color(states[i] == StepState.COMPLETE ? ColorToken.ACCENT : ColorToken.BORDER_DEFAULT));
                    g2.fillRect(Math.round(cx + d / 2f + Scale.px(6)), d / 2 - Math.max(1, Scale.px(1)) / 2,
                            Math.round(seg - d - Scale.px(12)), Math.max(1, Scale.px(2)));
                }
                marker(g2, cx - d / 2f, 0, d, i);
                g2.setFont(states[i] == StepState.CURRENT ? labelStrong : label);
                FontMetrics fm = g2.getFontMetrics();
                String t = Paint.ellipsize(labels.get(i), fm, Math.round(seg) - Scale.px(8));
                g2.setColor(textColor(states[i]));
                Paint.text(this, g2, t, cx - fm.stringWidth(t) / 2f, d + Scale.px(8) + fm.getAscent());
            }
        } else {
            int row = Scale.px(56);
            for (int i = 0; i < n; i++) {
                int y = i * row;
                if (i < n - 1) {
                    g2.setColor(Tokens.color(states[i] == StepState.COMPLETE ? ColorToken.ACCENT : ColorToken.BORDER_DEFAULT));
                    g2.fillRect(d / 2 - Math.max(1, Scale.px(1)), y + d + Scale.px(4), Math.max(1, Scale.px(2)), row - d - Scale.px(8));
                }
                marker(g2, 0, y, d, i);
                g2.setFont(states[i] == StepState.CURRENT ? labelStrong : label);
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(textColor(states[i]));
                Paint.text(this, g2, labels.get(i), d + Scale.px(12), y + Paint.centeredBaseline(fm, 0, d));
                if (descriptions.get(i) != null) {
                    g2.setFont(NekkoraFonts.font(TypeStyle.CAPTION));
                    FontMetrics fs = g2.getFontMetrics();
                    g2.setColor(Tokens.color(ColorToken.TEXT_MUTED));
                    Paint.text(this, g2, descriptions.get(i), d + Scale.px(12), y + d + fs.getAscent());
                }
            }
        }
        g2.dispose();
    }

    private Color textColor(StepState s) {
        return Tokens.color(switch (s) {
            case CURRENT -> ColorToken.TEXT_PRIMARY;
            case COMPLETE -> ColorToken.TEXT_SECONDARY;
            case ERROR -> ColorToken.STATUS_ERROR;
            default -> ColorToken.TEXT_MUTED;
        });
    }

    private void marker(Graphics2D g2, float x, float y, int d, int i) {
        StepState s = states[i];
        switch (s) {
            case COMPLETE -> {
                g2.setColor(Tokens.color(ColorToken.ACCENT));
                g2.fill(new Ellipse2D.Float(x, y, d, d));
                int is = Math.round(d * 0.6f);
                Color c = Tokens.color(ColorToken.ACTION_PRIMARY_TEXT);
                NekkoraIcon.of(IconName.CHECK, 14).paint(g2, Math.round(x + (d - is) / 2f), Math.round(y + (d - is) / 2f), is, c, c);
            }
            case ERROR -> {
                g2.setColor(Tokens.color(ColorToken.STATUS_ERROR_SOLID));
                g2.fill(new Ellipse2D.Float(x, y, d, d));
                int is = Math.round(d * 0.6f);
                Color c = Tokens.color(ColorToken.STATUS_ON_SOLID);
                NekkoraIcon.of(IconName.CLOSE, 14).paint(g2, Math.round(x + (d - is) / 2f), Math.round(y + (d - is) / 2f), is, c, c);
            }
            default -> {
                boolean current = s == StepState.CURRENT;
                if (current) {
                    g2.setColor(Tokens.color(ColorToken.FOCUS_RING));
                    float halo = Scale.pxf(3);
                    g2.fill(new Ellipse2D.Float(x - halo, y - halo, d + 2 * halo, d + 2 * halo));
                }
                g2.setColor(Tokens.color(ColorToken.BACKGROUND_SURFACE));
                g2.fill(new Ellipse2D.Float(x, y, d, d));
                Paint.strokeRound(g2, x, y, d, d, d / 2f, Tokens.color(current ? ColorToken.ACCENT : ColorToken.BORDER_STRONG), Scale.pxf(2));
                g2.setFont(NekkoraFonts.font(FontRole.UI, FontWeight.SEMIBOLD, 11.5f));
                FontMetrics fm = g2.getFontMetrics();
                String t = Integer.toString(i + 1);
                g2.setColor(Tokens.color(current ? ColorToken.TEXT_LINK : ColorToken.TEXT_MUTED));
                Paint.text(this, g2, t, x + (d - fm.stringWidth(t)) / 2f, y + Paint.centeredBaseline(fm, 0, d));
            }
        }
    }
}
