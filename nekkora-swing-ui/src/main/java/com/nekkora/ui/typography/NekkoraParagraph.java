package com.nekkora.ui.typography;

import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Tokens;
import java.awt.Dimension;
import javax.swing.JTextArea;

/** Parágrafo com quebra de linha automática (somente leitura, sem foco). */
public class NekkoraParagraph extends JTextArea {
    private TypeStyle style = TypeStyle.BODY;
    private TextTone tone = TextTone.SECONDARY;
    private int columnsHint = 48;
    private boolean estimating;

    public NekkoraParagraph(String text) {
        super(text);
        setEditable(false);
        setFocusable(false);
        setLineWrap(true);
        setWrapStyleWord(true);
        setOpaque(false);
        setBorder(null);
        setHighlighter(null);
        // texto estático: o caret não deve rolar a tela até o fim do parágrafo
        setCaret(new StaticCaret());
        applyStyle();
    }

    public NekkoraParagraph(String text, TextTone tone) {
        this(text);
        setTone(tone);
    }

    public void setTone(TextTone tone) {
        this.tone = tone;
        applyStyle();
    }

    public void setTypeStyle(TypeStyle style) {
        this.style = style;
        applyStyle();
    }

    /** Largura preferida aproximada, em caracteres (padrão 48). */
    public void setColumnsHint(int columns) {
        this.columnsHint = columns;
        revalidate();
    }

    @Override
    public Dimension getPreferredSize() {
        if (getParent() == null || getWidth() == 0) {
            // estimativa inicial razoável antes de haver largura real
            int w = getFontMetrics(getFont()).charWidth('n') * columnsHint;
            estimating = true;
            try {
                setSize(w, Short.MAX_VALUE);
            } finally {
                estimating = false;
            }
        }
        return super.getPreferredSize();
    }

    /**
     * Ao mudar de largura, a altura preferida muda (quebra de linha): pede novo layout. A estimativa feita em
     * {@link #getPreferredSize()} não conta como mudança real — senão um parágrafo posto em uma área de largura
     * zero (ex.: grupo recolhido) alternaria entre a estimativa e zero, revalidando para sempre na EDT.
     */
    @Override
    public void setBounds(int x, int y, int width, int height) {
        boolean widthChanged = width != getWidth();
        super.setBounds(x, y, width, height);
        if (!estimating && widthChanged && width > 0 && super.getPreferredSize().height != height) {
            javax.swing.SwingUtilities.invokeLater(this::revalidate);
        }
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyStyle();
    }

    private void applyStyle() {
        if (tone == null) return;
        setFont(NekkoraFonts.font(style));
        setForeground(Tokens.color(tone.token()));
        setOpaque(false);
        setBorder(null);
    }

    /** Texto estático: o caret nunca rola a tela (senão o parágrafo "puxa" a rolagem do pai). */
    private static final class StaticCaret extends javax.swing.text.DefaultCaret {
        StaticCaret() {
            setUpdatePolicy(NEVER_UPDATE);
        }

        @Override
        protected void adjustVisibility(java.awt.Rectangle nloc) {
            // não rola
        }
    }
}
