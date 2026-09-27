package com.nekkora.ui.workflow;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.FontWeight;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.core.Status;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import com.nekkora.ui.typography.NekkoraCaption;
import com.nekkora.ui.typography.NekkoraParagraph;
import com.nekkora.ui.typography.NekkoraText;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Linha do tempo vertical (histórico, auditoria, eventos de execução). */
public class NekkoraTimeline extends JPanel {
    private final List<Item> items = new ArrayList<>();

    public NekkoraTimeline() {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    /** Adiciona um evento. {@code status} define ícone e cor do marcador. */
    public NekkoraTimeline addItem(String time, String title, String description, Status status) {
        if (!items.isEmpty()) items.get(items.size() - 1).last = false;
        Item it = new Item(time, title, description, status);
        items.add(it);
        it.setAlignmentX(LEFT_ALIGNMENT);
        add(it);
        revalidate();
        return this;
    }

    /** Um evento da linha do tempo. */
    private static final class Item extends JPanel {
        private final Status status;
        boolean last = true;

        Item(String time, String title, String description, Status status) {
            super(new BorderLayout(Scale.px(Spacing.SPACE_5), 0));
            this.status = status;
            setOpaque(false);
            JPanel texts = new JPanel();
            texts.setOpaque(false);
            texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
            NekkoraText t = new NekkoraText(title);
            t.setWeight(FontWeight.MEDIUM);
            t.setAlignmentX(LEFT_ALIGNMENT);
            texts.add(t);
            if (description != null) {
                NekkoraParagraph p = new NekkoraParagraph(description);
                p.setAlignmentX(LEFT_ALIGNMENT);
                texts.add(p);
            }
            NekkoraCaption c = new NekkoraCaption(time);
            c.setAlignmentX(LEFT_ALIGNMENT);
            texts.add(c);
            texts.setBorder(BorderFactory.createEmptyBorder(Scale.px(2), 0, Scale.px(Spacing.SPACE_6), 0));
            add(new Marker(), BorderLayout.WEST);
            add(texts, BorderLayout.CENTER);
            getAccessibleContext().setAccessibleName(status.label() + ": " + title + ", " + time);
        }

        private final class Marker extends JComponent {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(Scale.px(24), Scale.px(24));
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Paint.prepare(g);
                int d = Scale.px(24);
                if (!last) {
                    g2.setColor(Tokens.color(ColorToken.BORDER_DEFAULT));
                    int t = Math.max(1, Scale.px(2));
                    g2.fillRect((d - t) / 2, d + Scale.px(4), t, getHeight() - d - Scale.px(6));
                }
                Paint.fillRound(g2, 0, 0, d, d, d / 2f, Tokens.color(ColorToken.BACKGROUND_SURFACE));
                Paint.fillRound(g2, 0, 0, d, d, d / 2f, Tokens.color(status.tone().soft()));
                int is = Scale.px(14);
                Color c = Tokens.color(status.tone().foreground());
                NekkoraIcon.of(status.icon(), 14).paint(g2, (d - is) / 2, (d - is) / 2, is, c, c);
                g2.dispose();
            }
        }
    }
}
