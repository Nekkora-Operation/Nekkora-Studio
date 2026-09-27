package com.nekkora.ui.input;

import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.internal.InteractionTracker;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.Cursor;
import javax.swing.JCheckBox;

/**
 * Caixa de seleção. Suporta estado indeterminado ({@link #setIndeterminate}) para "selecionar
 * todos" parciais. Espaço alterna.
 */
public class NekkoraCheckBox extends JCheckBox {
    private boolean indeterminate;
    private boolean initialized;

    public NekkoraCheckBox(String text) {
        this(text, false);
    }

    public NekkoraCheckBox(String text, boolean selected) {
        super(text, selected);
        InteractionTracker tracker = InteractionTracker.install(this);
        SelectionIcon icon = new SelectionIcon(SelectionIcon.Kind.CHECK, tracker, () -> indeterminate);
        setIcon(icon);
        setSelectedIcon(icon);
        setDisabledIcon(icon);
        setDisabledSelectedIcon(icon);
        setRolloverIcon(icon);
        setRolloverSelectedIcon(icon);
        setPressedIcon(icon);
        setFocusPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addItemListener(e -> {
            if (indeterminate) {
                indeterminate = false;
                repaint();
            }
        });
        initialized = true;
        applyStyle();
    }

    public boolean isIndeterminate() {
        return indeterminate;
    }

    public void setIndeterminate(boolean indeterminate) {
        this.indeterminate = indeterminate;
        getAccessibleContext().setAccessibleDescription(indeterminate ? "parcialmente selecionado" : null);
        repaint();
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
