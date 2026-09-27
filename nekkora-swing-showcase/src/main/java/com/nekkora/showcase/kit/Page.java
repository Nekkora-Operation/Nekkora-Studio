package com.nekkora.showcase.kit;

import com.nekkora.tokens.Spacing;
import com.nekkora.ui.container.NekkoraDivider;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.typography.NekkoraHeading;
import com.nekkora.ui.typography.NekkoraLabel;
import com.nekkora.ui.typography.NekkoraParagraph;
import javax.swing.JComponent;

/** Página do showcase: título, introdução e seções (título + descrição + exemplos). */
public class Page extends NekkoraStack {

    public Page(String title, String intro) {
        super(Axis.VERTICAL, Spacing.SPACE_8);
        setPadding(Spacing.SPACE_9, Spacing.SPACE_10);
        NekkoraStack head = NekkoraStack.vertical(Spacing.SPACE_3);
        head.add(new NekkoraHeading(title, NekkoraHeading.Level.DISPLAY_M));
        if (intro != null) head.add(new NekkoraParagraph(intro));
        add(head);
    }

    /** Seção com título, descrição opcional e conteúdo. */
    public Page section(String title, String description, JComponent content) {
        NekkoraStack s = NekkoraStack.vertical(Spacing.SPACE_5);
        s.add(NekkoraDivider.horizontal().withMargin(Spacing.SPACE_2));
        NekkoraStack head = NekkoraStack.vertical(Spacing.SPACE_2);
        head.add(new NekkoraHeading(title, NekkoraHeading.Level.H2));
        if (description != null) head.add(new NekkoraParagraph(description));
        s.add(head);
        s.add(content);
        add(s);
        return this;
    }

    /** Linha de exemplos lado a lado (alinhados ao topo). */
    public static NekkoraStack row(JComponent... items) {
        NekkoraStack r = NekkoraStack.horizontal(Spacing.SPACE_7).align(Align.START);
        r.addAll(items);
        r.addGlue();
        return r;
    }

    /** Linha com espaçamento menor (botões, badges). */
    public static NekkoraStack tightRow(JComponent... items) {
        NekkoraStack r = NekkoraStack.horizontal(Spacing.SPACE_4).align(Align.CENTER);
        r.addAll(items);
        r.addGlue();
        return r;
    }

    /** Coluna de exemplos. */
    public static NekkoraStack column(JComponent... items) {
        NekkoraStack c = NekkoraStack.vertical(Spacing.SPACE_5);
        c.addAll(items);
        return c;
    }

    /** Exemplo com legenda abaixo. */
    public static NekkoraStack specimen(String caption, JComponent c) {
        NekkoraStack s = NekkoraStack.vertical(Spacing.SPACE_3).align(Align.START);
        s.add(c);
        s.add(NekkoraLabel.overline(caption));
        return s;
    }
}
