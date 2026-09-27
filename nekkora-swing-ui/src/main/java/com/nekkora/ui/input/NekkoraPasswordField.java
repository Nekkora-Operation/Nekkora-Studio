package com.nekkora.ui.input;

import com.formdev.flatlaf.FlatClientProperties;
import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.theme.NekkoraFonts;
import java.awt.Dimension;
import javax.swing.JPasswordField;

/** Campo de senha com botão "mostrar senha" e aviso de Caps Lock. */
public class NekkoraPasswordField extends JPasswordField {
    private ControlSize size = ControlSize.MEDIUM;
    private boolean initialized;

    public NekkoraPasswordField() {
        this(null);
    }

    public NekkoraPasswordField(String placeholder) {
        initialized = true;
        putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        putClientProperty("JPasswordField.showRevealButton", true);
        putClientProperty("JPasswordField.showCapsLock", true);
        applyStyle();
    }

    public void setError(boolean error) {
        FieldSupport.outline(this, error ? FieldSupport.Validation.ERROR : FieldSupport.Validation.NONE);
    }

    public void setControlSize(ControlSize size) {
        this.size = size;
        revalidate();
    }

    @Override
    public Dimension getPreferredSize() {
        return isPreferredSizeSet() ? super.getPreferredSize() : FieldSupport.height(super.getPreferredSize(), size);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyStyle();
    }

    private void applyStyle() {
        if (!initialized) return;
        setFont(NekkoraFonts.font(TypeStyle.BODY));
    }
}
