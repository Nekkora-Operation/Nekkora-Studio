package com.nekkora.ui.input;

import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.internal.InteractionTracker;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.Cursor;
import javax.swing.ButtonGroup;
import javax.swing.JRadioButton;

/**
 * Opção exclusiva. Agrupe com {@link #group(NekkoraRadioButton...)}; setas movem entre as
 * opções do grupo.
 */
public class NekkoraRadioButton extends JRadioButton {
    private boolean initialized;

    public NekkoraRadioButton(String text) {
        this(text, false);
    }

    public NekkoraRadioButton(String text, boolean selected) {
        super(text, selected);
        InteractionTracker tracker = InteractionTracker.install(this);
        SelectionIcon icon = new SelectionIcon(SelectionIcon.Kind.RADIO, tracker, () -> false);
        setIcon(icon);
        setSelectedIcon(icon);
        setDisabledIcon(icon);
        setDisabledSelectedIcon(icon);
        setRolloverIcon(icon);
        setRolloverSelectedIcon(icon);
        setPressedIcon(icon);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        initialized = true;
        applyStyle();
    }

    /** Cria um grupo exclusivo com navegação por setas entre as opções. */
    public static ButtonGroup group(NekkoraRadioButton... buttons) {
        ButtonGroup g = new ButtonGroup();
        for (int i = 0; i < buttons.length; i++) {
            g.add(buttons[i]);
            NekkoraRadioButton prev = buttons[(i - 1 + buttons.length) % buttons.length];
            NekkoraRadioButton next = buttons[(i + 1) % buttons.length];
            bindArrow(buttons[i], "DOWN", next);
            bindArrow(buttons[i], "RIGHT", next);
            bindArrow(buttons[i], "UP", prev);
            bindArrow(buttons[i], "LEFT", prev);
        }
        return g;
    }

    private static void bindArrow(NekkoraRadioButton from, String key, NekkoraRadioButton to) {
        from.getInputMap().put(javax.swing.KeyStroke.getKeyStroke(key), "nekkora.radio." + key);
        from.getActionMap().put("nekkora.radio." + key, new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!to.isEnabled()) return;
                to.requestFocusInWindow();
                to.setSelected(true);
            }
        });
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyStyle();
    }

    private void applyStyle() {
        if (!initialized) return;
        setFont(NekkoraFonts.font(TypeStyle.BODY));
        setForeground(Tokens.color(com.nekkora.tokens.ColorToken.TEXT_PRIMARY));
        setIconTextGap(Scale.px(6));
        setOpaque(false);
        setFocusPainted(false);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(Scale.px(2), 0, Scale.px(2), Scale.px(4)));
    }
}
