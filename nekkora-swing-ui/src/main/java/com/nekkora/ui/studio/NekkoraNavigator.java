package com.nekkora.ui.studio;

import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.container.Background;
import com.nekkora.ui.container.NekkoraPanel;
import com.nekkora.ui.data.NekkoraTree;
import com.nekkora.ui.input.NekkoraSearchField;
import com.nekkora.ui.theme.Scale;
import java.awt.BorderLayout;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JComponent;

/**
 * Painel "Navegador" do padrão Navigator | Workspace | Inspector: cabeçalho com ações,
 * busca opcional e conteúdo (normalmente uma {@link NekkoraTree}).
 */
public class NekkoraNavigator extends NekkoraPanel {
    private final NekkoraPanelHeader header;
    private final NekkoraPanel top = new NekkoraPanel(new BorderLayout());
    private NekkoraSearchField search;

    public NekkoraNavigator(String title) {
        super(new BorderLayout(), Background.CANVAS);
        header = new NekkoraPanelHeader(title);
        top.add(header, BorderLayout.NORTH);
        add(top, BorderLayout.NORTH);
        getAccessibleContext().setAccessibleName(title);
    }

    public void setHeaderActions(JComponent... actions) {
        header.setActions(actions);
    }

    /** Campo de filtro acima do conteúdo. */
    public NekkoraSearchField enableSearch(String placeholder, Consumer<String> onSearch) {
        search = new NekkoraSearchField(placeholder);
        search.setControlSize(ControlSize.SMALL);
        search.onSearch(onSearch);
        NekkoraPanel wrap = new NekkoraPanel(new BorderLayout());
        wrap.setBorder(BorderFactory.createEmptyBorder(Scale.px(Spacing.SPACE_3), Scale.px(Spacing.SPACE_4), Scale.px(Spacing.SPACE_2), Scale.px(Spacing.SPACE_4)));
        wrap.add(search, BorderLayout.CENTER);
        top.add(wrap, BorderLayout.SOUTH);
        return search;
    }

    public void setContent(JComponent content) {
        if (content instanceof NekkoraTree tree) {
            tree.setCanvasBackground(true);
            add(tree.scrollable(), BorderLayout.CENTER);
        } else {
            add(content, BorderLayout.CENTER);
        }
        revalidate();
    }

    public NekkoraSearchField getSearchField() {
        return search;
    }
}
