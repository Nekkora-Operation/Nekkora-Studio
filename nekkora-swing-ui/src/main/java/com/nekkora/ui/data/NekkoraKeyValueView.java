package com.nekkora.ui.data;

import com.nekkora.tokens.Spacing;
import com.nekkora.ui.technical.NekkoraCopyableValue;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.typography.NekkoraCodeText;
import com.nekkora.ui.typography.NekkoraText;
import com.nekkora.ui.typography.TextTone;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Pares chave → valor (detalhes de um item, metadados). A coluna de chaves se ajusta à maior
 * chave; valores podem ser texto, monoespaçado, copiável ou qualquer componente.
 */
public class NekkoraKeyValueView extends JPanel {
    private int row;

    public NekkoraKeyValueView() {
        super(new GridBagLayout());
        setOpaque(false);
    }

    public NekkoraKeyValueView add(String key, String value) {
        return add(key, new NekkoraText(value == null ? "—" : value));
    }

    public NekkoraKeyValueView addMono(String key, String value) {
        return add(key, new NekkoraCodeText(value, false));
    }

    public NekkoraKeyValueView addCopyable(String key, String value) {
        return add(key, new NekkoraCopyableValue(value));
    }

    public NekkoraKeyValueView add(String key, JComponent value) {
        NekkoraText k = new NekkoraText(key, TextTone.SECONDARY);
        GridBagConstraints c = new GridBagConstraints();
        int gap = Scale.px(Spacing.SPACE_3);
        c.gridy = row;
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(row == 0 ? 0 : gap, 0, 0, Scale.px(Spacing.SPACE_7));
        super.add(k, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(row == 0 ? 0 : gap, 0, 0, 0);
        super.add(value, c);
        row++;
        return this;
    }
}
