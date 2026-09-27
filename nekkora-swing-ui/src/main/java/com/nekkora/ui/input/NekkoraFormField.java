package com.nekkora.ui.input;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Spacing;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.IconSize;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.typography.NekkoraCaption;
import com.nekkora.ui.typography.NekkoraLabel;
import com.nekkora.ui.typography.TextTone;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * Campo de formulário completo: rótulo (com indicador de obrigatório/opcional), controle,
 * texto de ajuda e mensagem de erro. O rótulo é associado ao controle para leitores de tela.
 *
 * <pre>{@code
 * NekkoraFormField nome = new NekkoraFormField("Nome", new NekkoraTextField("Ex.: Robô de faturas"))
 *         .required().helper("Aparece na lista de itens.");
 * nome.setError("Informe um nome.");
 * }</pre>
 */
public class NekkoraFormField extends JPanel {
    private final NekkoraLabel label;
    private final NekkoraCaption indicator;
    private final JComponent control;
    private final NekkoraCaption helper;
    private final NekkoraCaption error;
    private String helperText;

    public NekkoraFormField(String labelText, JComponent control) {
        super(new BorderLayout());
        setOpaque(false);
        this.control = control;

        label = new NekkoraLabel(labelText);
        label.setLabelFor(control instanceof JScrollPane sp ? sp.getViewport().getView() : control);
        indicator = new NekkoraCaption("");
        indicator.setVisible(false);
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        top.setOpaque(false);
        top.add(label);
        top.add(Box.createHorizontalStrut(Scale.px(4)));
        top.add(indicator);
        top.setBorder(BorderFactory.createEmptyBorder(0, Scale.px(2), Scale.px(Spacing.SPACE_2) - Scale.px(2), 0));

        helper = new NekkoraCaption("");
        helper.setTypeStyle(TypeStyle.BODY_S);
        helper.setVisible(false);
        error = new NekkoraCaption("", TextTone.ERROR);
        error.setTypeStyle(TypeStyle.BODY_S);
        error.setIcon(NekkoraIcon.of(IconName.X_CIRCLE, IconSize.S).withColor(ColorToken.STATUS_ERROR));
        error.setVisible(false);
        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(Scale.px(2), Scale.px(2), 0, 0));
        bottom.add(helper);
        bottom.add(error);

        add(top, BorderLayout.NORTH);
        add(control, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    public JComponent getControl() {
        return control;
    }

    /** Marca como obrigatório (indicador "*" e descrição acessível). */
    public NekkoraFormField required() {
        indicator.setText("*");
        indicator.setTone(TextTone.ERROR);
        indicator.setVisible(true);
        indicator.getAccessibleContext().setAccessibleName("obrigatório");
        return this;
    }

    /** Marca como opcional (indicador "(opcional)"). */
    public NekkoraFormField optional() {
        indicator.setText("(opcional)");
        indicator.setTone(TextTone.MUTED);
        indicator.setVisible(true);
        return this;
    }

    public NekkoraFormField helper(String text) {
        this.helperText = text;
        helper.setText(text);
        helper.setVisible(text != null && !error.isVisible());
        return this;
    }

    /** Mostra a mensagem de erro (e o estado de erro no controle). {@code null} limpa. */
    public void setError(String message) {
        boolean has = message != null && !message.isBlank();
        error.setText(has ? message : "");
        error.setVisible(has);
        helper.setVisible(!has && helperText != null);
        applyControlError(control, has);
        control.getAccessibleContext().setAccessibleDescription(has ? message : helperText);
        revalidate();
        repaint();
    }

    public boolean hasError() {
        return error.isVisible();
    }

    private static void applyControlError(Component c, boolean error) {
        if (c instanceof NekkoraTextField f) f.setError(error);
        else if (c instanceof NekkoraPasswordField p) p.setError(error);
        else if (c instanceof NekkoraComboBox<?> cb) cb.setError(error);
        else if (c instanceof JScrollPane sp) {
            FieldSupport.outline(sp, error ? FieldSupport.Validation.ERROR : FieldSupport.Validation.NONE);
            sp.repaint();
        } else if (c instanceof JComponent jc) {
            FieldSupport.outline(jc, error ? FieldSupport.Validation.ERROR : FieldSupport.Validation.NONE);
        }
    }
}
