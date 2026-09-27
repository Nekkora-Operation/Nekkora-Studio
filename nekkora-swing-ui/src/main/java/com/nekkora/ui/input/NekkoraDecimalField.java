package com.nekkora.ui.input;

import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Optional;
import javax.swing.SwingConstants;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/** Campo decimal com separador do idioma (vírgula em pt-BR) e casas decimais limitadas. */
public class NekkoraDecimalField extends NekkoraTextField {
    private final char separator;
    private int scale = 2;
    private BigDecimal min;
    private BigDecimal max;

    public NekkoraDecimalField() {
        this(null, Locale.getDefault());
    }

    public NekkoraDecimalField(String placeholder, Locale locale) {
        super(placeholder);
        this.separator = DecimalFormatSymbols.getInstance(locale).getDecimalSeparator();
        setHorizontalAlignment(SwingConstants.RIGHT);
        ((AbstractDocument) getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String s, AttributeSet a) throws BadLocationException {
                replace(fb, offset, 0, s, a);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String s, AttributeSet a)
                    throws BadLocationException {
                String in = s == null ? "" : s.replace(separator == ',' ? '.' : ',', separator);
                String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
                String next = cur.substring(0, offset) + in + cur.substring(offset + length);
                String sep = java.util.regex.Pattern.quote(String.valueOf(separator));
                if (next.isEmpty() || next.matches("-?\\d*(" + sep + "\\d{0," + scale + "})?")) {
                    super.replace(fb, offset, length, in, a);
                }
            }
        });
        onTextChange(t -> setError(!isValueValid()));
    }

    /** Casas decimais aceitas (padrão 2). */
    public void setScale(int scale) {
        this.scale = scale;
    }

    public void setRange(BigDecimal min, BigDecimal max) {
        this.min = min;
        this.max = max;
        setError(!isValueValid());
    }

    public Optional<BigDecimal> getValue() {
        String t = getText();
        if (t.isEmpty() || t.equals("-")) return Optional.empty();
        try {
            return Optional.of(new BigDecimal(t.replace(separator, '.')));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public void setValue(BigDecimal value) {
        setText(value == null ? "" : value.setScale(scale, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', separator));
    }

    public boolean isValueValid() {
        Optional<BigDecimal> v = getValue();
        if (v.isEmpty()) return true;
        return (min == null || v.get().compareTo(min) >= 0) && (max == null || v.get().compareTo(max) <= 0);
    }
}
