package com.nekkora.ui.studio;

import com.nekkora.tokens.Spacing;
import com.nekkora.ui.container.NekkoraCollapsiblePanel;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.typography.NekkoraText;
import com.nekkora.ui.typography.TextTone;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Grupo de propriedades do Inspector: recolhível, linhas "rótulo | editor" alinhadas. Em
 * painéis estreitos, a coluna de rótulos cede espaço (até 40%) e os editores encolhem —
 * sem nunca achatar a altura.
 *
 * <pre>{@code
 * NekkoraPropertiesGroup g = new NekkoraPropertiesGroup("Geral");
 * g.addRow("Nome", new NekkoraTextField());
 * g.addRow("Ativo", new NekkoraSwitch());
 * }</pre>
 */
public class NekkoraPropertiesGroup extends NekkoraCollapsiblePanel {
    private final List<Component[]> rows = new ArrayList<>();
    private final JPanel grid = new JPanel(new RowsLayout());

    public NekkoraPropertiesGroup(String title) {
        super(title);
        grid.setOpaque(false);
        setContent(grid);
    }

    /** Linha com rótulo à esquerda e editor à direita (o rótulo é associado ao editor). */
    public NekkoraPropertiesGroup addRow(String label, JComponent editor) {
        NekkoraText l = new NekkoraText(label, TextTone.SECONDARY);
        l.setLabelFor(editor);
        grid.add(l);
        grid.add(editor);
        rows.add(new Component[] {l, editor});
        grid.revalidate();
        return this;
    }

    /** Linha que ocupa a largura toda (ex.: área de texto, alerta). */
    public NekkoraPropertiesGroup addFullRow(JComponent component) {
        grid.add(component);
        rows.add(new Component[] {null, component});
        grid.revalidate();
        return this;
    }

    /** Layout de duas colunas com altura sempre preferida. */
    private final class RowsLayout implements LayoutManager {
        private int labelWidth(int total) {
            int w = 0;
            for (Component[] r : rows) if (r[0] != null) w = Math.max(w, r[0].getPreferredSize().width);
            return total <= 0 ? w : Math.min(w, (int) (total * 0.4));
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Insets in = parent.getInsets();
            int gap = Scale.px(Spacing.SPACE_3), col = Scale.px(Spacing.SPACE_5), lw = labelWidth(0);
            int w = 0, h = 0;
            for (int i = 0; i < rows.size(); i++) {
                Component[] r = rows.get(i);
                Dimension e = r[1].getPreferredSize();
                int rowW = r[0] == null ? e.width : lw + col + e.width;
                int rowH = r[0] == null ? e.height : Math.max(e.height, r[0].getPreferredSize().height);
                w = Math.max(w, rowW);
                h += rowH + (i > 0 ? gap : 0);
            }
            return new Dimension(Math.min(w, Scale.px(360)) + in.left + in.right, h + in.top + in.bottom);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            Dimension p = preferredLayoutSize(parent);
            return new Dimension(Scale.px(120), p.height);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets in = parent.getInsets();
            int x0 = in.left + Scale.px(Spacing.SPACE_2), width = parent.getWidth() - x0 - in.right;
            int gap = Scale.px(Spacing.SPACE_3), col = Scale.px(Spacing.SPACE_5), lw = labelWidth(width);
            int y = in.top;
            for (Component[] r : rows) {
                Dimension e = r[1].getPreferredSize();
                if (r[0] == null) {
                    r[1].setBounds(x0, y, width, e.height);
                    y += e.height + gap;
                    continue;
                }
                Dimension l = r[0].getPreferredSize();
                int rowH = Math.max(e.height, l.height);
                r[0].setBounds(x0, y + (rowH - l.height) / 2, lw, l.height);
                int ex = x0 + lw + col, ew = Math.max(0, width - lw - col);
                boolean fill = !(r[1] instanceof javax.swing.AbstractButton);
                r[1].setBounds(ex, y + (rowH - e.height) / 2, fill ? ew : Math.min(ew, e.width), e.height);
                y += rowH + gap;
            }
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {}

        @Override
        public void removeLayoutComponent(Component comp) {}
    }
}
