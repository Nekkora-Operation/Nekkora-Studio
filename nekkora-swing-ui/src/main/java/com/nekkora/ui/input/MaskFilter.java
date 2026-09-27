package com.nekkora.ui.input;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/**
 * Filtro de máscara simples: {@code #} = dígito, qualquer outro caractere é literal inserido
 * automaticamente. Ex.: {@code ##/##/####}, {@code ##:##}.
 */
public final class MaskFilter extends DocumentFilter {
    private final String mask;

    public MaskFilter(String mask) {
        this.mask = mask;
    }

    public String mask() {
        return mask;
    }

    @Override
    public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr) throws BadLocationException {
        replace(fb, offset, 0, text, attr);
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
            throws BadLocationException {
        String current = fb.getDocument().getText(0, fb.getDocument().getLength());
        String next = current.substring(0, offset) + (text == null ? "" : text) + current.substring(offset + length);
        String formatted = apply(digits(next));
        fb.replace(0, fb.getDocument().getLength(), formatted, attrs);
    }

    @Override
    public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
        String current = fb.getDocument().getText(0, fb.getDocument().getLength());
        String next = current.substring(0, offset) + current.substring(offset + length);
        // apagar um literal apaga também o dígito anterior
        if (length == 1 && offset < current.length() && mask.length() > offset && mask.charAt(offset) != '#' && offset > 0) {
            next = current.substring(0, offset - 1) + current.substring(offset + length);
        }
        fb.replace(0, fb.getDocument().getLength(), apply(digits(next)), null);
    }

    /** Aplica a máscara a uma sequência de dígitos. */
    public String apply(String digits) {
        StringBuilder out = new StringBuilder();
        int d = 0;
        for (int i = 0; i < mask.length() && d < digits.length(); i++) {
            char m = mask.charAt(i);
            if (m == '#') out.append(digits.charAt(d++));
            else out.append(m);
        }
        return out.toString();
    }

    static String digits(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) if (Character.isDigit(c)) b.append(c);
        return b.toString();
    }
}
