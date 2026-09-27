package com.nekkora.ui.feedback;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.FontWeight;
import com.nekkora.tokens.Radius;
import com.nekkora.tokens.Spacing;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.button.NekkoraIconButton;
import com.nekkora.ui.core.Severity;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.IconSize;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import com.nekkora.ui.typography.NekkoraParagraph;
import com.nekkora.ui.typography.NekkoraText;
import com.nekkora.ui.typography.TextTone;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.accessibility.AccessibleRole;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Alerta em linha: mensagem contextual dentro do conteúdo (ex.: acima de um formulário).
 * Ícone + cor + texto; ação e botão de fechar opcionais.
 */
public class NekkoraInlineAlert extends JPanel {
    private Severity severity;
    private final JLabel icon = new JLabel();
    private final NekkoraText title = new NekkoraText("");
    private final NekkoraParagraph message = new NekkoraParagraph("");
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, Scale.px(Spacing.SPACE_4), 0));
    private final JPanel east = new JPanel(new BorderLayout());
    protected boolean rounded = true;

    public NekkoraInlineAlert(Severity severity, String titleText, String messageText) {
        super(new BorderLayout(Scale.px(Spacing.SPACE_5), 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(Scale.px(10), Scale.px(12), Scale.px(10), Scale.px(8)));
        title.setWeight(FontWeight.SEMIBOLD);
        message.setTone(TextTone.SECONDARY);
        message.setTypeStyle(TypeStyle.BODY);
        icon.setVerticalAlignment(JLabel.TOP);
        icon.setBorder(BorderFactory.createEmptyBorder(Scale.px(1), 0, 0, 0));

        JPanel texts = new JPanel();
        texts.setOpaque(false);
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        title.setAlignmentX(LEFT_ALIGNMENT);
        message.setAlignmentX(LEFT_ALIGNMENT);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        actions.setOpaque(false);
        actions.setVisible(false);
        texts.add(title);
        texts.add(message);
        texts.add(actions);
        east.setOpaque(false);

        add(icon, BorderLayout.WEST);
        add(texts, BorderLayout.CENTER);
        add(east, BorderLayout.EAST);
        setSeverity(severity);
        setTitle(titleText);
        setMessage(messageText);
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
        icon.setIcon(NekkoraIcon.of(severity.icon(), IconSize.M).withColor(severity.tone().foreground()));
        getAccessibleContext().setAccessibleDescription(severity.label());
        repaint();
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setTitle(String text) {
        title.setText(text);
        title.setVisible(text != null && !text.isEmpty());
    }

    public void setMessage(String text) {
        message.setText(text);
        message.setVisible(text != null && !text.isEmpty());
    }

    /** Ação em forma de link (ex.: "Tentar novamente"). */
    public NekkoraInlineAlert addAction(String label, Runnable action) {
        NekkoraButton b = NekkoraButton.link(label).withSize(ControlSize.SMALL);
        b.addActionListener(e -> action.run());
        actions.add(b);
        actions.setVisible(true);
        return this;
    }

    /** Botão de fechar (esconde o alerta e chama {@code onDismiss}, se houver). */
    public NekkoraInlineAlert dismissible(Runnable onDismiss) {
        NekkoraIconButton close = new NekkoraIconButton(IconName.CLOSE, "Fechar").withSize(ControlSize.SMALL);
        close.addActionListener(e -> {
            setVisible(false);
            if (getParent() != null) getParent().revalidate();
            if (onDismiss != null) onDismiss.run();
        });
        east.add(close, BorderLayout.NORTH);
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Paint.prepare(g);
        float r = rounded ? Scale.px(Radius.MEDIUM) : 0;
        Paint.fillRound(g2, 0, 0, getWidth(), getHeight(), r, Tokens.color(ColorToken.BACKGROUND_SURFACE));
        Paint.fillRound(g2, 0, 0, getWidth(), getHeight(), r, Tokens.color(severity.tone().soft()));
        Paint.strokeRound(g2, 0, 0, getWidth(), getHeight(), r,
                com.nekkora.tokens.ColorMath.alpha(Tokens.color(severity.tone().foreground()), 0.30));
        g2.dispose();
    }

    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJPanel() {
                @Override
                public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.ALERT;
                }
            };
        }
        return accessibleContext;
    }
}
