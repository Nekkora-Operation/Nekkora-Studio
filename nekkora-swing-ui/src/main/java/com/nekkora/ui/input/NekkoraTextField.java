package com.nekkora.ui.input;

import com.formdev.flatlaf.FlatClientProperties;
import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.IconSize;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.theme.NekkoraFonts;
import java.awt.Dimension;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Campo de texto de uma linha.
 *
 * <p>Estados: padrão, hover, foco, preenchido, erro ({@link #setError}), aviso, desabilitado e
 * somente leitura ({@link #setReadOnly}). Para rótulo, ajuda e mensagem de erro, envolva em
 * {@link NekkoraFormField}.
 */
public class NekkoraTextField extends JTextField {
    private ControlSize size = ControlSize.MEDIUM;
    private IconName leadingIcon;
    private boolean error;
    private boolean warning;
    private boolean initialized;

    public NekkoraTextField() {
        this(null, 0);
    }

    public NekkoraTextField(String placeholder) {
        this(placeholder, 0);
    }

    public NekkoraTextField(String placeholder, int columns) {
        super(columns);
        initialized = true;
        setPlaceholder(placeholder);
        applyStyle();
    }

    public void setPlaceholder(String placeholder) {
        putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
    }

    public String getPlaceholder() {
        return (String) getClientProperty(FlatClientProperties.PLACEHOLDER_TEXT);
    }

    /** Ícone à esquerda (ex.: {@link IconName#SEARCH}). */
    public void setLeadingIcon(IconName icon) {
        this.leadingIcon = icon;
        applyStyle();
    }

    /** Componente à direita (ex.: um botão de ícone, uma unidade). */
    public void setTrailingComponent(JComponent c) {
        putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, c);
    }

    /** Componente à esquerda (substitui o ícone). */
    public void setLeadingComponent(JComponent c) {
        putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_COMPONENT, c);
    }

    /** Mostra o botão "limpar" quando há texto. */
    public void setClearable(boolean clearable) {
        putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, clearable);
    }

    public boolean isError() {
        return error;
    }

    /** Estado de erro (borda e foco em vermelho). A mensagem fica no {@link NekkoraFormField}. */
    public void setError(boolean error) {
        this.error = error;
        updateOutline();
        getAccessibleContext().setAccessibleDescription(error ? "Campo com erro" : null);
    }

    public void setWarning(boolean warning) {
        this.warning = warning;
        updateOutline();
    }

    /** Somente leitura: o texto pode ser selecionado e copiado, mas não editado. */
    public void setReadOnly(boolean readOnly) {
        setEditable(!readOnly);
    }

    public boolean isReadOnly() {
        return !isEditable();
    }

    public ControlSize getControlSize() {
        return size;
    }

    public void setControlSize(ControlSize size) {
        this.size = size;
        revalidate();
    }

    /** Notifica a cada alteração de texto. */
    public void onTextChange(Consumer<String> listener) {
        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                listener.accept(getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                listener.accept(getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                listener.accept(getText());
            }
        });
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

    private void updateOutline() {
        FieldSupport.outline(this, error ? FieldSupport.Validation.ERROR
                : warning ? FieldSupport.Validation.WARNING : FieldSupport.Validation.NONE);
    }

    private void applyStyle() {
        if (!initialized) return;
        setFont(NekkoraFonts.font(TypeStyle.BODY));
        putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                leadingIcon == null ? null : NekkoraIcon.of(leadingIcon, IconSize.M).withColor(com.nekkora.tokens.ColorToken.TEXT_MUTED));
    }
}
