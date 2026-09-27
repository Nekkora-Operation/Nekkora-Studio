package com.nekkora.ui.input;

import java.util.OptionalLong;
import javax.swing.SwingConstants;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/**
 * Campo de número inteiro: só aceita dígitos (e sinal, se {@link #setAllowNegative}), valida
 * mínimo/máximo e marca erro fora do intervalo. Setas ↑/↓ incrementam.
 */
public class NekkoraIntegerField extends NekkoraTextField {
    private long min = Long.MIN_VALUE;
    private long max = Long.MAX_VALUE;
    private boolean allowNegative;

    public NekkoraIntegerField() {
        this(null);
    }

    public NekkoraIntegerField(String placeholder) {
        super(placeholder);
        setHorizontalAlignment(SwingConstants.RIGHT);
        ((AbstractDocument) getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String s, AttributeSet a) throws BadLocationException {
                replace(fb, offset, 0, s, a);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String s, AttributeSet a)
                    throws BadLocationException {
                String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
                String next = cur.substring(0, offset) + (s == null ? "" : s) + cur.substring(offset + length);
                if (next.isEmpty() || next.matches(allowNegative ? "-?\\d*" : "\\d*")) {
                    super.replace(fb, offset, length, s, a);
                }
            }
        });
        onTextChange(t -> validateRange());
        NumericKeys.install(this, delta -> getValue().ifPresentOrElse(v -> setValue(v + delta), () -> setValue(Math.max(0, min))));
    }

    public void setRange(long min, long max) {
        this.min = min;
        this.max = max;
        setAllowNegative(min < 0);
        validateRange();
    }

    public void setAllowNegative(boolean allow) {
        this.allowNegative = allow;
    }

    public OptionalLong getValue() {
        String t = getText();
        if (t.isEmpty() || t.equals("-")) return OptionalLong.empty();
        try {
            return OptionalLong.of(Long.parseLong(t));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    public void setValue(long value) {
        setText(Long.toString(Math.max(min, Math.min(max, value))));
    }

    /** {@code true} se vazio ou dentro do intervalo. */
    public boolean isValueValid() {
        OptionalLong v = getValue();
        return v.isEmpty() || (v.getAsLong() >= min && v.getAsLong() <= max);
    }

    private void validateRange() {
        setError(!isValueValid());
    }
}
