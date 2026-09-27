package com.nekkora.ui.dialog;

import com.nekkora.tokens.Spacing;
import com.nekkora.ui.theme.Scale;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

/**
 * Popover: painel leve ancorado a um componente (filtros, detalhes rápidos). Fecha com Esc ou
 * clique fora. Para decisões que bloqueiam o fluxo, use {@link NekkoraDialog}.
 */
public class NekkoraPopover extends JPopupMenu {

    public NekkoraPopover(JComponent content) {
        setLayout(new BorderLayout());
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        int p = Scale.px(Spacing.SPACE_6);
        wrap.setBorder(BorderFactory.createEmptyBorder(p - Scale.px(4), p, p, p));
        wrap.add(content, BorderLayout.CENTER);
        add(wrap, BorderLayout.CENTER);
    }

    /** Mostra abaixo do {@code anchor}, alinhado à esquerda. */
    public void showBelow(JComponent anchor) {
        show(anchor, 0, anchor.getHeight() + Scale.px(4));
    }

    public static NekkoraPopover show(JComponent anchor, JComponent content) {
        NekkoraPopover p = new NekkoraPopover(content);
        p.showBelow(anchor);
        return p;
    }
}
