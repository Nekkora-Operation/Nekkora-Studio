package com.nekkora.ui.container;

import com.nekkora.tokens.Spacing;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.typography.NekkoraHeading;
import com.nekkora.ui.typography.NekkoraParagraph;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Seção de página: título, descrição opcional, ações à direita e conteúdo abaixo. */
public class NekkoraSection extends NekkoraPanel {
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    private final JPanel content = new JPanel(new BorderLayout());

    public NekkoraSection(String title) {
        this(title, null);
    }

    public NekkoraSection(String title, String description) {
        super(new BorderLayout());
        JPanel texts = new JPanel();
        texts.setOpaque(false);
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        NekkoraHeading h = new NekkoraHeading(title, NekkoraHeading.Level.H2);
        h.setAlignmentX(LEFT_ALIGNMENT);
        texts.add(h);
        if (description != null) {
            NekkoraParagraph p = new NekkoraParagraph(description);
            p.setAlignmentX(LEFT_ALIGNMENT);
            p.setBorder(BorderFactory.createEmptyBorder(Scale.px(Spacing.SPACE_2), 0, 0, 0));
            texts.add(p);
        }
        actions.setOpaque(false);
        JPanel header = new JPanel(new BorderLayout(Scale.px(Spacing.SPACE_6), 0));
        header.setOpaque(false);
        header.add(texts, BorderLayout.CENTER);
        header.add(actions, BorderLayout.EAST);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, Scale.px(Spacing.SPACE_6), 0));
        content.setOpaque(false);
        add(header, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
    }

    public NekkoraSection setActions(JComponent... components) {
        actions.removeAll();
        for (int i = 0; i < components.length; i++) {
            if (i > 0) actions.add(javax.swing.Box.createHorizontalStrut(Scale.px(Spacing.SPACE_4)));
            actions.add(components[i]);
        }
        actions.revalidate();
        return this;
    }

    public NekkoraSection setContent(JComponent c) {
        content.removeAll();
        content.add(c, BorderLayout.CENTER);
        content.revalidate();
        return this;
    }
}
