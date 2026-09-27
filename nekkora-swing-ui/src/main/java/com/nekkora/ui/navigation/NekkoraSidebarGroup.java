package com.nekkora.ui.navigation;

import com.nekkora.tokens.Spacing;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.typography.NekkoraLabel;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;

/** Grupo de itens da sidebar com título em caixa alta. */
public class NekkoraSidebarGroup extends NekkoraStack {
    private final NekkoraLabel title;
    private final List<NekkoraSidebarItem> items = new ArrayList<>();

    public NekkoraSidebarGroup(String titleText) {
        super(Axis.VERTICAL, Spacing.SPACE_1);
        title = NekkoraLabel.overline(titleText == null ? "" : titleText);
        title.setBorder(BorderFactory.createEmptyBorder(Scale.px(8), Scale.px(14), Scale.px(4), 0));
        title.setVisible(titleText != null);
        add(title);
    }

    public NekkoraSidebarItem addItem(NekkoraSidebarItem item) {
        items.add(item);
        add(item);
        return item;
    }

    public List<NekkoraSidebarItem> getItems() {
        return List.copyOf(items);
    }

    void setCollapsed(boolean collapsed) {
        title.setVisible(!collapsed && !title.getText().isEmpty());
        items.forEach(i -> i.setCollapsed(collapsed));
    }
}
