package com.nekkora.ui.input;

import static com.nekkora.tokens.ColorToken.*;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Radius;
import com.nekkora.ui.core.Interaction;
import com.nekkora.ui.internal.InteractionTracker;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import javax.swing.AbstractButton;
import javax.swing.Icon;

/** Ícones de checkbox e radio pintados com tokens (tamanho 16, anel de foco próprio). */
final class SelectionIcon implements Icon {
    enum Kind { CHECK, RADIO }

    private static final int SIZE = 16;
    private final Kind kind;
    private final InteractionTracker tracker;
    private final java.util.function.BooleanSupplier indeterminate;

    SelectionIcon(Kind kind, InteractionTracker tracker, java.util.function.BooleanSupplier indeterminate) {
        this.kind = kind;
        this.tracker = tracker;
        this.indeterminate = indeterminate;
    }

    @Override
    public int getIconWidth() {
        return Scale.px(SIZE) + 2 * Math.round(Paint.focusWidth());
    }

    @Override
    public int getIconHeight() {
        return getIconWidth();
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        AbstractButton b = (AbstractButton) c;
        boolean selected = b.isSelected() || indeterminate.getAsBoolean();
        boolean enabled = b.isEnabled();
        Graphics2D g2 = Paint.prepare(g);
        try {
            float fw = Paint.focusWidth();
            float s = Scale.pxf(SIZE);
            float bx = x + fw, by = y + fw;
            float r = kind == Kind.RADIO ? s / 2 : Scale.pxf(Radius.SMALL.px());
            if (enabled && tracker.isFocused()) Paint.focusRing(g2, bx, by, s, s, r);
            if (!enabled) Paint.disabledComposite(g2);
            Interaction st = tracker.state();

            if (selected) {
                ColorToken fill = st == Interaction.PRESSED ? ACTION_PRIMARY_PRESSED
                        : st == Interaction.HOVER ? ACTION_PRIMARY_HOVER : ACTION_PRIMARY;
                if (kind == Kind.RADIO) {
                    Paint.fillRound(g2, bx, by, s, s, r, Tokens.color(fill));
                    float d = s * 0.4f;
                    g2.setColor(Tokens.color(ACTION_PRIMARY_TEXT));
                    g2.fill(new Ellipse2D.Float(bx + (s - d) / 2, by + (s - d) / 2, d, d));
                } else {
                    Paint.fillRound(g2, bx, by, s, s, r, Tokens.color(fill));
                    g2.setColor(Tokens.color(ACTION_PRIMARY_TEXT));
                    g2.setStroke(Paint.stroke(2f));
                    Path2D p = new Path2D.Float();
                    if (indeterminate.getAsBoolean()) {
                        p.moveTo(bx + s * 0.28, by + s * 0.5);
                        p.lineTo(bx + s * 0.72, by + s * 0.5);
                    } else {
                        p.moveTo(bx + s * 0.26, by + s * 0.52);
                        p.lineTo(bx + s * 0.43, by + s * 0.69);
                        p.lineTo(bx + s * 0.75, by + s * 0.33);
                    }
                    g2.draw(p);
                }
            } else {
                Color bg = Tokens.color(FIELD_BACKGROUND);
                Paint.fillRound(g2, bx, by, s, s, r, bg);
                if (st == Interaction.HOVER || st == Interaction.PRESSED) {
                    Paint.fillRound(g2, bx, by, s, s, r, Tokens.color(BACKGROUND_HOVER));
                }
                ColorToken border = st == Interaction.DEFAULT ? BORDER_STRONG : TEXT_MUTED;
                Paint.strokeRound(g2, bx, by, s, s, r, Tokens.color(border), Scale.pxf(1.5f));
            }
        } finally {
            g2.dispose();
        }
    }
}
