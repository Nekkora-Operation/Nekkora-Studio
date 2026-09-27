package com.nekkora.ui.navigation;

import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.action.NekkoraAction;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.typography.NekkoraLabel;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;

/**
 * Menu suspenso / de contexto. Itens com ícone, atalho, cabeçalhos de seção e itens
 * destrutivos. Setas navegam, Enter executa, Esc fecha (padrão Swing).
 *
 * <pre>{@code
 * NekkoraPopupMenu menu = new NekkoraPopupMenu()
 *         .item("Renomear", IconName.EDIT, NekkoraKeys.shortcut(KeyEvent.VK_R), this::renomear)
 *         .item("Duplicar", IconName.COPY, null, this::duplicar)
 *         .separator()
 *         .danger("Excluir", IconName.TRASH, this::excluir);
 * NekkoraPopupMenu.installContextMenu(arvore, () -> menu);
 * }</pre>
 */
public class NekkoraPopupMenu extends JPopupMenu {

    public NekkoraPopupMenu item(String label, IconName icon, KeyStroke shortcut, Runnable action) {
        NekkoraMenuItem mi = new NekkoraMenuItem(label, icon, shortcut);
        mi.addActionListener(e -> action.run());
        add(mi);
        return this;
    }

    public NekkoraPopupMenu item(NekkoraAction action) {
        add(new NekkoraMenuItem(action));
        return this;
    }

    public NekkoraPopupMenu danger(String label, IconName icon, Runnable action) {
        NekkoraMenuItem mi = new NekkoraMenuItem(label, icon, null).danger();
        mi.addActionListener(e -> action.run());
        add(mi);
        return this;
    }

    public NekkoraPopupMenu separator() {
        addSeparator();
        return this;
    }

    /** Título de seção (não clicável). */
    public NekkoraPopupMenu header(String title) {
        NekkoraLabel l = NekkoraLabel.overline(title);
        l.setBorder(BorderFactory.createEmptyBorder(6, 12, 2, 12));
        add(l);
        return this;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setFont(NekkoraFonts.font(TypeStyle.BODY));
    }

    /**
     * Instala um menu de contexto: clique direito, Shift+F10 e a tecla de menu.
     * O {@code factory} é chamado a cada abertura (itens podem depender da seleção).
     */
    public static void installContextMenu(JComponent target, Supplier<JPopupMenu> factory) {
        target.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybe(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybe(e);
            }

            private void maybe(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                JPopupMenu m = factory.get();
                if (m != null) m.show(target, e.getX(), e.getY());
            }
        });
        AbstractAction open = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JPopupMenu m = factory.get();
                Rectangle r = target.getVisibleRect();
                if (m != null) m.show(target, r.x + 16, r.y + 16);
            }
        };
        target.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_F10, KeyEvent.SHIFT_DOWN_MASK), "nekkora.context");
        target.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0), "nekkora.context");
        target.getActionMap().put("nekkora.context", open);
    }
}
