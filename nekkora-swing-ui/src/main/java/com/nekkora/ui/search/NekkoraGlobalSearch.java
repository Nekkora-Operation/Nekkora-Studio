package com.nekkora.ui.search;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.input.NekkoraSearchField;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

/**
 * Busca global: campo + lista de resultados flutuante. ↑/↓ navegam sem tirar o foco do campo,
 * Enter abre, Esc fecha. O provedor devolve resultados para o termo (a aplicação decide onde
 * buscar). Base visual da futura Command Palette.
 */
public class NekkoraGlobalSearch extends JPanel {
    private final NekkoraSearchField field;
    private final DefaultListModel<SearchItem> model = new DefaultListModel<>();
    private final JList<SearchItem> list = new JList<>(model);
    private final JPopupMenu popup = new JPopupMenu();
    private final JLabel empty = new JLabel();
    private Function<String, List<SearchItem>> provider = q -> List.of();
    private Consumer<SearchItem> onOpen;
    private String query = "";

    public NekkoraGlobalSearch(String placeholder) {
        super(new BorderLayout());
        setOpaque(false);
        field = new NekkoraSearchField(placeholder);
        add(field, BorderLayout.CENTER);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFocusable(false);
        list.setOpaque(false);
        NekkoraSearchResult row = new NekkoraSearchResult();
        list.setCellRenderer((ListCellRenderer<SearchItem>) (l, value, index, selected, focus) -> {
            row.set(value, query, selected);
            return row;
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                open();
            }
        });
        empty.setBorder(BorderFactory.createEmptyBorder(Scale.px(12), Scale.px(14), Scale.px(12), Scale.px(14)));
        popup.setFocusable(false);
        popup.setLayout(new BorderLayout());

        field.onSearch(this::search);
        bind(KeyEvent.VK_DOWN, () -> move(1));
        bind(KeyEvent.VK_UP, () -> move(-1));
        field.addActionListener(e -> {
            if (popup.isVisible() && list.getSelectedValue() != null) open();
        });
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "nekkora.search.close");
        field.getActionMap().put("nekkora.search.close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (popup.isVisible()) popup.setVisible(false);
                else field.clear();
            }
        });
    }

    public void setProvider(Function<String, List<SearchItem>> provider) {
        this.provider = provider;
    }

    public void onOpen(Consumer<SearchItem> listener) {
        this.onOpen = listener;
    }

    public NekkoraSearchField getField() {
        return field;
    }

    /** Resultados atuais (testes). */
    public List<SearchItem> getResults() {
        java.util.ArrayList<SearchItem> out = new java.util.ArrayList<>();
        for (int i = 0; i < model.size(); i++) out.add(model.get(i));
        return out;
    }

    public void search(String q) {
        this.query = q;
        model.clear();
        if (q.isBlank()) {
            popup.setVisible(false);
            return;
        }
        provider.apply(q).forEach(model::addElement);
        if (!model.isEmpty()) list.setSelectedIndex(0);
        showPopup();
    }

    private void showPopup() {
        if (!field.isShowing()) return;
        popup.removeAll();
        Component content;
        if (model.isEmpty()) {
            empty.setText("Nenhum resultado para \"" + query + "\"");
            empty.setFont(NekkoraFonts.font(TypeStyle.BODY));
            empty.setForeground(Tokens.color(ColorToken.TEXT_SECONDARY));
            content = empty;
        } else {
            list.setVisibleRowCount(Math.min(8, model.size()));
            content = list;
        }
        popup.add(content, BorderLayout.CENTER);
        int w = Math.max(field.getWidth(), Scale.px(420));
        popup.setPopupSize(new Dimension(w, content.getPreferredSize().height + Scale.px(10)));
        popup.show(field, 0, field.getHeight() + Scale.px(2));
        field.requestFocusInWindow();
    }

    private void move(int delta) {
        if (!popup.isVisible() || model.isEmpty()) return;
        int i = Math.floorMod(list.getSelectedIndex() + delta, model.size());
        list.setSelectedIndex(i);
        list.ensureIndexIsVisible(i);
    }

    private void open() {
        SearchItem it = list.getSelectedValue();
        popup.setVisible(false);
        if (it != null && onOpen != null) onOpen.accept(it);
    }

    private void bind(int key, Runnable r) {
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), "nekkora.search." + key);
        field.getActionMap().put("nekkora.search." + key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        });
    }
}
