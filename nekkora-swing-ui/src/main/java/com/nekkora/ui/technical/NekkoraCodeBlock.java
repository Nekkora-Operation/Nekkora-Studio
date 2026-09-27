package com.nekkora.ui.technical;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.Radius;
import com.nekkora.ui.button.NekkoraIconButton;
import com.nekkora.ui.container.Background;
import com.nekkora.ui.container.NekkoraPanel;
import com.nekkora.ui.container.NekkoraScrollPane;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.typography.NekkoraCaption;
import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

/** Bloco de código somente leitura com rótulo de linguagem e botão copiar. */
public class NekkoraCodeBlock extends NekkoraPanel {
    private final NekkoraMonospaceTextArea area;

    public NekkoraCodeBlock(String code, String language) {
        super(new BorderLayout(), Background.SUNKEN);
        setRadius(Radius.MEDIUM);
        setOutline(ColorToken.BORDER_SUBTLE);
        area = new NekkoraMonospaceTextArea(code, 0);
        area.setEditable(false);
        area.setOpaque(false);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(Scale.px(4), Scale.px(12), 0, Scale.px(4)));
        header.add(new NekkoraCaption(language == null ? "" : language.toUpperCase()), BorderLayout.WEST);
        NekkoraIconButton copy = new NekkoraIconButton(IconName.COPY, "Copiar código").withSize(ControlSize.SMALL);
        copy.addActionListener(e -> {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(area.getText()), null);
            } catch (IllegalStateException | java.awt.HeadlessException ignored) {
                // indisponível
            }
        });
        header.add(copy, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        NekkoraScrollPane sp = new NekkoraScrollPane(area);
        add(sp, BorderLayout.CENTER);
    }

    public void setCode(String code) {
        area.setText(code);
        area.setCaretPosition(0);
    }

    public String getCode() {
        return area.getText();
    }

    /** Área interna (para ajustar linhas visíveis). */
    public NekkoraMonospaceTextArea getTextArea() {
        return area;
    }
}
