package com.nekkora.ui.state;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.IconSize;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import com.nekkora.ui.typography.NekkoraHeading;
import com.nekkora.ui.typography.NekkoraText;
import com.nekkora.ui.typography.TextTone;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Estado vazio: explica por que não há conteúdo e oferece o próximo passo. Centralizado,
 * largura de leitura limitada.
 *
 * <pre>{@code
 * NekkoraEmptyState vazio = new NekkoraEmptyState(IconName.FOLDER, "Nenhum projeto ainda",
 *         "Crie o primeiro projeto para começar.");
 * vazio.addAction(NekkoraButton.primary("Novo projeto").withIcon(IconName.PLUS));
 * }</pre>
 */
public class NekkoraEmptyState extends JPanel {
    private final JPanel column = new JPanel();
    private final Glyph glyph;
    private final NekkoraHeading title;
    private final NekkoraText description;
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, Scale.px(Spacing.SPACE_4), 0));

    public NekkoraEmptyState(IconName icon, String titleText, String descriptionText) {
        this(icon, Tone.ACCENT, titleText, descriptionText);
    }

    public NekkoraEmptyState(IconName icon, Tone tone, String titleText, String descriptionText) {
        super(new GridBagLayout());
        setOpaque(false);
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        glyph = new Glyph(icon, tone);
        title = new NekkoraHeading(titleText, NekkoraHeading.Level.H2);
        description = new NekkoraText("", TextTone.SECONDARY);
        description.setHorizontalAlignment(NekkoraText.CENTER);
        setDescription(descriptionText);
        actions.setOpaque(false);
        actions.setVisible(false);
        for (JComponent c : new JComponent[] {glyph, title, description, actions}) c.setAlignmentX(CENTER_ALIGNMENT);
        column.add(glyph);
        column.add(Box.createVerticalStrut(Scale.px(Spacing.SPACE_6)));
        column.add(title);
        column.add(Box.createVerticalStrut(Scale.px(Spacing.SPACE_3)));
        column.add(description);
        column.add(Box.createVerticalStrut(Scale.px(Spacing.SPACE_7)));
        column.add(actions);
        add(column);
        getAccessibleContext().setAccessibleName(titleText);
        getAccessibleContext().setAccessibleDescription(descriptionText);
    }

    public NekkoraEmptyState addAction(NekkoraButton button) {
        actions.add(button);
        actions.setVisible(true);
        return this;
    }

    /** Conteúdo extra abaixo do texto (ex.: spinner). */
    public void setExtra(JComponent c) {
        c.setAlignmentX(CENTER_ALIGNMENT);
        column.add(c, 3);
        column.add(Box.createVerticalStrut(Scale.px(Spacing.SPACE_5)), 3);
    }

    public void setTitle(String text) {
        title.setText(text);
    }

    /** Descrição centralizada, com quebra de linha em ~340px. */
    public void setDescription(String text) {
        description.setVisible(text != null);
        if (text == null) return;
        String esc = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        description.setText("<html><div style='text-align:center;width:" + Scale.px(340) + "px'>" + esc + "</div></html>");
        description.getAccessibleContext().setAccessibleName(text);
    }

    /** Substitui o ícone do topo por outro componente (ex.: spinner). */
    protected void replaceGlyph(JComponent c) {
        int i = java.util.Arrays.asList(column.getComponents()).indexOf(glyph);
        column.remove(glyph);
        c.setAlignmentX(CENTER_ALIGNMENT);
        column.add(c, i);
    }

    /** Ícone em um círculo suave do tom. */
    private static final class Glyph extends JComponent {
        private final IconName icon;
        private final Tone tone;

        Glyph(IconName icon, Tone tone) {
            this.icon = icon;
            this.tone = tone;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Scale.px(64), Scale.px(64));
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Paint.prepare(g);
            int s = Math.min(getWidth(), getHeight());
            int x = (getWidth() - s) / 2;
            Paint.fillRound(g2, x, 0, s, s, s / 2f, Tokens.color(tone.soft()));
            int is = Scale.px(IconSize.XL.px() + 4);
            java.awt.Color c = Tokens.color(tone == Tone.NEUTRAL ? ColorToken.TEXT_SECONDARY : tone.foreground());
            NekkoraIcon.of(icon, IconSize.XL.px() + 4).paint(g2, x + (s - is) / 2, (s - is) / 2, is, c, c);
            g2.dispose();
        }
    }
}
