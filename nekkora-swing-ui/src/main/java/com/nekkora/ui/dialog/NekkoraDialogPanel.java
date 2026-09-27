package com.nekkora.ui.dialog;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.Radius;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.button.NekkoraIconButton;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.data.NekkoraIconMarker;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import com.nekkora.ui.typography.NekkoraHeading;
import com.nekkora.ui.typography.NekkoraParagraph;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Anatomia de um diálogo (cabeçalho, conteúdo, rodapé de ações), independente da janela.
 * Usado por {@link NekkoraDialog} e exibido "em linha" no Showcase.
 */
public class NekkoraDialogPanel extends JPanel {
    private final NekkoraHeading title;
    private final NekkoraParagraph description;
    private final JPanel headerRow;
    private final JPanel content = new JPanel(new BorderLayout());
    private final JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, Scale.px(Spacing.SPACE_4), 0));
    private final NekkoraIconButton close;
    private NekkoraIconMarker marker;

    public NekkoraDialogPanel(String titleText) {
        super(new BorderLayout());
        setOpaque(false);
        int p = Scale.px(Spacing.SPACE_8);
        setBorder(BorderFactory.createEmptyBorder(p - Scale.px(4), p, p - Scale.px(4), p - Scale.px(8)));

        title = new NekkoraHeading(titleText, NekkoraHeading.Level.H2);
        description = new NekkoraParagraph("");
        description.setVisible(false);
        close = new NekkoraIconButton(IconName.CLOSE, "Fechar (Esc)").withSize(ControlSize.SMALL);

        JPanel texts = new JPanel();
        texts.setOpaque(false);
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        title.setAlignmentX(LEFT_ALIGNMENT);
        description.setAlignmentX(LEFT_ALIGNMENT);
        texts.add(title);
        texts.add(Box.createVerticalStrut(Scale.px(Spacing.SPACE_2)));
        texts.add(description);

        headerRow = new JPanel(new BorderLayout(Scale.px(Spacing.SPACE_5), 0));
        headerRow.setOpaque(false);
        headerRow.add(texts, BorderLayout.CENTER);
        JPanel closeWrap = new JPanel(new BorderLayout());
        closeWrap.setOpaque(false);
        closeWrap.add(close, BorderLayout.NORTH);
        headerRow.add(closeWrap, BorderLayout.EAST);
        headerRow.setBorder(BorderFactory.createEmptyBorder(0, 0, Scale.px(Spacing.SPACE_6), 0));

        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, Scale.px(8)));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(Scale.px(Spacing.SPACE_7), 0, 0, Scale.px(4)));

        add(headerRow, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
    }

    public void setTitle(String text) {
        title.setText(text);
    }

    public String getTitle() {
        return title.getText();
    }

    public void setDescription(String text) {
        description.setText(text);
        description.setVisible(text != null && !text.isEmpty());
    }

    /** Marcador de ícone à esquerda do título (ex.: alerta em confirmações destrutivas). */
    public void setMarker(IconName icon, Tone tone) {
        if (marker != null) headerRow.remove(marker);
        marker = new NekkoraIconMarker(icon, tone, false, 36);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(marker, BorderLayout.NORTH);
        headerRow.add(wrap, BorderLayout.WEST);
    }

    public void setContent(JComponent c) {
        content.removeAll();
        if (c != null) content.add(c, BorderLayout.CENTER);
        content.setVisible(c != null);
        content.revalidate();
    }

    /** Adiciona um botão ao rodapé (ordem: da esquerda para a direita). */
    public void addFooterButton(NekkoraButton b) {
        footer.add(b);
        footer.revalidate();
    }

    public NekkoraIconButton getCloseButton() {
        return close;
    }

    public void setCloseVisible(boolean visible) {
        close.setVisible(visible);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Paint.prepare(g);
        float r = Scale.px(Radius.LARGE);
        Paint.fillRound(g2, 0, 0, getWidth(), getHeight(), r, Tokens.color(ColorToken.BACKGROUND_ELEVATED));
        Paint.strokeRound(g2, 0, 0, getWidth(), getHeight(), r, Tokens.color(ColorToken.BORDER_DEFAULT));
        g2.dispose();
    }
}
