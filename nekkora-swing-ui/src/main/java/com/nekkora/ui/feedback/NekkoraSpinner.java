package com.nekkora.ui.feedback;

import com.nekkora.ui.core.NekkoraComponent;
import com.nekkora.tokens.ColorMath;
import com.nekkora.tokens.ColorToken;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.internal.Spin;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;

/** Indicador de atividade circular. Só anima enquanto está visível. */
public class NekkoraSpinner extends NekkoraComponent {

    public enum Size {
        SMALL(14, 1.8f),
        MEDIUM(20, 2.2f),
        LARGE(32, 3f);

        private final int px;
        private final float stroke;

        Size(int px, float stroke) {
            this.px = px;
            this.stroke = stroke;
        }
    }

    private final Size size;
    private final Spin spin = new Spin(this);

    public NekkoraSpinner() {
        this(Size.MEDIUM);
    }

    public NekkoraSpinner(Size size) {
        this.size = size;
        spin.setActive(true);
        getAccessibleContext().setAccessibleName("Carregando");
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Scale.px(size.px), Scale.px(size.px));
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Paint.prepare(g);
        float s = Math.min(getWidth(), getHeight());
        float st = Scale.pxf(size.stroke);
        float x = (getWidth() - s) / 2f + st / 2, y = (getHeight() - s) / 2f + st / 2, d = s - st;
        Color c = Tokens.color(ColorToken.ACCENT);
        g2.setStroke(Paint.stroke(size.stroke));
        g2.setColor(ColorMath.alpha(c, 0.2));
        g2.draw(new Arc2D.Float(x, y, d, d, 0, 360, Arc2D.OPEN));
        g2.setColor(c);
        g2.draw(new Arc2D.Float(x, y, d, d, (float) -spin.angle(), 110, Arc2D.OPEN));
        g2.dispose();
    }

    @Override
    protected AccessibleRole accessibleRole() {
        return AccessibleRole.PROGRESS_BAR;
    }
}
