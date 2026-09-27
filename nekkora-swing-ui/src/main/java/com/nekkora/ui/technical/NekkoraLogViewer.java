package com.nekkora.ui.technical;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.container.NekkoraScrollPane;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Visualizador de logs: linhas com hora, nível e mensagem coloridos por nível, limite de
 * linhas e rolagem automática (pausada quando o usuário rola para cima).
 * {@link #append} pode ser chamado de qualquer thread.
 */
public class NekkoraLogViewer extends NekkoraScrollPane {

    public enum Level {
        DEBUG(ColorToken.TEXT_MUTED),
        INFO(ColorToken.STATUS_INFO),
        SUCCESS(ColorToken.STATUS_SUCCESS),
        WARN(ColorToken.STATUS_WARNING),
        ERROR(ColorToken.STATUS_ERROR);

        private final ColorToken token;

        Level(ColorToken token) {
            this.token = token;
        }
    }

    private record Entry(LocalTime time, Level level, String message) {}

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private JTextPane pane;
    private final List<Entry> entries = new ArrayList<>();
    private int maxLines = 5000;
    private boolean autoScroll = true;

    public NekkoraLogViewer() {
        super(new JTextPane());
        pane = (JTextPane) getViewport().getView();
        pane.setEditable(false);
        getVerticalScrollBar().addAdjustmentListener(e -> {
            if (e.getValueIsAdjusting()) {
                var sb = getVerticalScrollBar();
                autoScroll = sb.getValue() + sb.getVisibleAmount() >= sb.getMaximum() - Scale.px(4);
            }
        });
        style();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (pane != null) rerender(); // troca de tema
    }

    public void setMaxLines(int maxLines) {
        this.maxLines = maxLines;
    }

    public void append(Level level, String message) {
        Entry e = new Entry(LocalTime.now(), level, message);
        if (SwingUtilities.isEventDispatchThread()) add(e);
        else SwingUtilities.invokeLater(() -> add(e));
    }

    public void clear() {
        entries.clear();
        rerender();
    }

    /** Todo o texto (para copiar/exportar). */
    public String getText() {
        return pane.getText();
    }

    private void add(Entry e) {
        entries.add(e);
        if (entries.size() > maxLines) {
            entries.subList(0, entries.size() - maxLines).clear();
            rerender();
            return;
        }
        write(e);
        if (autoScroll) pane.setCaretPosition(pane.getDocument().getLength());
    }

    private void rerender() {
        style();
        pane.setText("");
        entries.forEach(this::write);
    }

    private void style() {
        pane.setFont(NekkoraFonts.font(TypeStyle.MONOSPACE));
        pane.setBackground(Tokens.color(ColorToken.BACKGROUND_SUNKEN));
        pane.setBorder(BorderFactory.createEmptyBorder(Scale.px(8), Scale.px(12), Scale.px(8), Scale.px(12)));
        getViewport().setBackground(Tokens.color(ColorToken.BACKGROUND_SUNKEN));
        getViewport().setOpaque(true);
    }

    private void write(Entry e) {
        StyledDocument doc = pane.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), TIME.format(e.time()) + "  ", attr(ColorToken.TEXT_MUTED, false));
            doc.insertString(doc.getLength(), String.format("%-7s", e.level().name()), attr(e.level().token, true));
            ColorToken msg = e.level() == Level.ERROR ? ColorToken.STATUS_ERROR
                    : e.level() == Level.DEBUG ? ColorToken.TEXT_SECONDARY : ColorToken.TEXT_PRIMARY;
            doc.insertString(doc.getLength(), e.message() + "\n", attr(msg, false));
        } catch (BadLocationException ignored) {
            // não ocorre: sempre no fim
        }
    }

    private static SimpleAttributeSet attr(ColorToken color, boolean bold) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, Tokens.color(color));
        StyleConstants.setBold(a, bold);
        return a;
    }
}
