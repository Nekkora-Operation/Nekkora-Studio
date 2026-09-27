package com.nekkora.ui.navigation;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.FontWeight;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.IconSize;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.typography.NekkoraText;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Trilha de navegação: itens anteriores são links; o último é o local atual. */
public class NekkoraBreadcrumb extends JPanel {
    private record Item(String label, Runnable action) {}

    private final List<Item> items = new ArrayList<>();

    public NekkoraBreadcrumb() {
        super(new FlowLayout(FlowLayout.LEFT, Scale.px(2), 0));
        setOpaque(false);
        getAccessibleContext().setAccessibleName("Você está em");
    }

    /** Item navegável (link). */
    public NekkoraBreadcrumb item(String label, Runnable action) {
        items.add(new Item(label, action));
        rebuild();
        return this;
    }

    /** Item sem ação (normalmente o local atual, por último). */
    public NekkoraBreadcrumb item(String label) {
        return item(label, null);
    }

    public void clear() {
        items.clear();
        rebuild();
    }

    private void rebuild() {
        removeAll();
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            boolean last = i == items.size() - 1;
            if (last || it.action() == null) {
                NekkoraText t = new NekkoraText(it.label());
                if (last) t.setWeight(FontWeight.SEMIBOLD);
                t.getAccessibleContext().setAccessibleDescription(last ? "página atual" : null);
                add(t);
            } else {
                NekkoraButton link = NekkoraButton.link(it.label()).withSize(ControlSize.SMALL);
                link.addActionListener(e -> it.action().run());
                add(link);
            }
            if (!last) add(new JLabel(NekkoraIcon.of(IconName.CHEVRON_RIGHT, IconSize.S).withColor(ColorToken.TEXT_MUTED)));
        }
        revalidate();
        repaint();
    }
}
