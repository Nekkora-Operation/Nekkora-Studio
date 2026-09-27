package com.nekkora.ui.input;

import com.formdev.flatlaf.FlatClientProperties;
import com.nekkora.tokens.ControlSize;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.theme.Scale;
import java.awt.Dimension;
import javax.swing.JComponent;

/** Comportamentos comuns aos campos (altura por tamanho, estado de validação). */
final class FieldSupport {
    private FieldSupport() {}

    /** Estado de validação visual de um campo. */
    enum Validation { NONE, WARNING, ERROR }

    static Dimension height(Dimension pref, ControlSize size) {
        int h = Scale.height(size) + 2 * Math.round(Paint.focusWidth());
        return new Dimension(pref.width, Math.max(pref.height, h));
    }

    static void outline(JComponent c, Validation v) {
        c.putClientProperty(FlatClientProperties.OUTLINE, switch (v) {
            case ERROR -> FlatClientProperties.OUTLINE_ERROR;
            case WARNING -> FlatClientProperties.OUTLINE_WARNING;
            default -> null;
        });
    }
}
