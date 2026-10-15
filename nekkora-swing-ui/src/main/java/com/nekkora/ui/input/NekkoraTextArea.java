package com.nekkora.ui.input;

import com.formdev.flatlaf.FlatClientProperties;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.container.NekkoraScrollPane;
import com.nekkora.ui.theme.NekkoraFonts;
import javax.swing.JTextArea;

/**
 * Área de texto com quebra de linha. Coloque-a na tela com {@link #scrollable()} para ter a
 * borda de campo e rolagem.
 */
public class NekkoraTextArea extends JTextArea {
    private boolean initialized;

    public NekkoraTextArea() {
        this(null, 4);
    }

    public NekkoraTextArea(String placeholder, int rows) {
        super(rows, 0);
        initialized = true;
        setLineWrap(true);
        setWrapStyleWord(true);
        putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        applyStyle();
        TextUndo.install(this);
    }

    /** Envolve em um painel rolável com borda de campo (foco/erro aparecem na borda). */
    public NekkoraScrollPane scrollable() {
        NekkoraScrollPane sp = new NekkoraScrollPane(this, true);
        sp.setFieldBorder(true);
        return sp;
    }

    public void setReadOnly(boolean readOnly) {
        setEditable(!readOnly);
    }

    public void setError(boolean error) {
        FieldSupport.outline(this, error ? FieldSupport.Validation.ERROR : FieldSupport.Validation.NONE);
        if (getParent() != null && getParent().getParent() instanceof javax.swing.JScrollPane sp) {
            FieldSupport.outline(sp, error ? FieldSupport.Validation.ERROR : FieldSupport.Validation.NONE);
        }
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
