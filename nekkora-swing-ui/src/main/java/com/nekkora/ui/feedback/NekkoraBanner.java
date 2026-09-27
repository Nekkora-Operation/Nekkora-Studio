package com.nekkora.ui.feedback;

import com.nekkora.ui.core.Severity;
import com.nekkora.ui.theme.Scale;
import javax.swing.BorderFactory;

/**
 * Banner: aviso de largura total no topo de uma janela/área (ex.: "Você está offline").
 * Mesma anatomia do {@link NekkoraInlineAlert}, sem cantos arredondados.
 */
public class NekkoraBanner extends NekkoraInlineAlert {

    public NekkoraBanner(Severity severity, String title, String message) {
        super(severity, title, message);
        rounded = false;
        setBorder(BorderFactory.createEmptyBorder(Scale.px(8), Scale.px(16), Scale.px(8), Scale.px(12)));
    }
}
