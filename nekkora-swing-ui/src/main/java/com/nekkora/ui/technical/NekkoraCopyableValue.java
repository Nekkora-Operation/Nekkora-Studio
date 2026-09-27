package com.nekkora.ui.technical;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.ControlSize;
import com.nekkora.ui.button.NekkoraIconButton;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.typography.NekkoraCodeText;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.JPanel;
import javax.swing.Timer;

/** Valor técnico com botão de copiar (confirma com um "check" por 1,5 s). */
public class NekkoraCopyableValue extends JPanel {
    private final NekkoraCodeText text;
    private final NekkoraIconButton copy;
    private String value;

    public NekkoraCopyableValue(String value) {
        super(new FlowLayout(FlowLayout.LEFT, Scale.px(2), 0));
        setOpaque(false);
        this.value = value;
        text = new NekkoraCodeText(value, false);
        copy = new NekkoraIconButton(IconName.COPY, "Copiar").withSize(ControlSize.SMALL);
        copy.addActionListener(e -> copyToClipboard());
        add(text);
        add(copy);
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
        text.setText(value);
    }

    public void copyToClipboard() {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
        } catch (IllegalStateException | java.awt.HeadlessException ignored) {
            return; // área de transferência indisponível
        }
        copy.setLeadingIcon(IconName.CHECK);
        copy.setLabel("Copiado");
        copy.setForeground(com.nekkora.ui.theme.Tokens.color(ColorToken.STATUS_SUCCESS));
        Timer t = new Timer(1500, e -> {
            copy.setLeadingIcon(IconName.COPY);
            copy.setLabel("Copiar");
        });
        t.setRepeats(false);
        t.start();
    }
}
