package com.nekkora.ui.technical;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.container.NekkoraScrollPane;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import javax.swing.BorderFactory;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Visualizador de JSON somente leitura: formata (indentação 2) e colore chaves, textos,
 * números e literais. Tolerante: JSON inválido é exibido como texto.
 */
public class NekkoraJsonViewer extends NekkoraScrollPane {
    private JTextPane pane;
    private String json = "";

    public NekkoraJsonViewer() {
        super(new JTextPane());
        pane = (JTextPane) getViewport().getView();
        pane.setEditable(false);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (pane != null) render(); // troca de tema
    }

    public void setJson(String json) {
        this.json = json == null ? "" : json;
        render();
    }

    public String getFormatted() {
        return pane.getText();
    }

    /** Formata JSON com indentação de 2 espaços (sem validar). */
    public static String format(String json) {
        StringBuilder out = new StringBuilder();
        int indent = 0;
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\\' && i + 1 < json.length()) out.append(json.charAt(++i));
                else if (c == '"') inString = false;
                continue;
            }
            switch (c) {
                case '"' -> {
                    inString = true;
                    out.append(c);
                }
                case '{', '[' -> {
                    char close = c == '{' ? '}' : ']';
                    int j = i + 1;
                    while (j < json.length() && Character.isWhitespace(json.charAt(j))) j++;
                    if (j < json.length() && json.charAt(j) == close) {
                        out.append(c).append(close);
                        i = j;
                    } else {
                        out.append(c).append('\n').append("  ".repeat(++indent));
                    }
                }
                case '}', ']' -> out.append('\n').append("  ".repeat(Math.max(0, --indent))).append(c);
                case ',' -> out.append(",\n").append("  ".repeat(indent));
                case ':' -> out.append(": ");
                default -> {
                    if (!Character.isWhitespace(c)) out.append(c);
                }
            }
        }
        return out.toString();
    }

    private void render() {
        pane.setFont(NekkoraFonts.font(TypeStyle.MONOSPACE));
        pane.setBackground(Tokens.color(ColorToken.BACKGROUND_SUNKEN));
        pane.setBorder(BorderFactory.createEmptyBorder(Scale.px(8), Scale.px(12), Scale.px(8), Scale.px(12)));
        getViewport().setBackground(Tokens.color(ColorToken.BACKGROUND_SUNKEN));
        getViewport().setOpaque(true);
        String text = format(json.strip());
        pane.setText("");
        StyledDocument doc = pane.getStyledDocument();
        try {
            int i = 0;
            while (i < text.length()) {
                char c = text.charAt(i);
                int j = i + 1;
                ColorToken color;
                if (c == '"') {
                    while (j < text.length() && text.charAt(j) != '"') j += text.charAt(j) == '\\' ? 2 : 1;
                    j = Math.min(text.length(), j + 1);
                    int k = j;
                    while (k < text.length() && text.charAt(k) == ' ') k++;
                    color = k < text.length() && text.charAt(k) == ':' ? ColorToken.TEXT_LINK : ColorToken.STATUS_SUCCESS;
                } else if (c == '-' || Character.isDigit(c)) {
                    while (j < text.length() && "0123456789.eE+-".indexOf(text.charAt(j)) >= 0) j++;
                    color = ColorToken.STATUS_WARNING;
                } else if (Character.isLetter(c)) {
                    while (j < text.length() && Character.isLetter(text.charAt(j))) j++;
                    color = ColorToken.STATUS_INFO;
                } else {
                    color = "{}[],:".indexOf(c) >= 0 ? ColorToken.TEXT_MUTED : ColorToken.TEXT_PRIMARY;
                }
                SimpleAttributeSet a = new SimpleAttributeSet();
                StyleConstants.setForeground(a, Tokens.color(color));
                doc.insertString(doc.getLength(), text.substring(i, j), a);
                i = j;
            }
        } catch (BadLocationException ignored) {
            // não ocorre
        }
        pane.setCaretPosition(0);
    }
}
