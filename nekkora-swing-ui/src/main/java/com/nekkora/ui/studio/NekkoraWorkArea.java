package com.nekkora.ui.studio;

import com.nekkora.ui.container.Background;
import com.nekkora.ui.container.NekkoraPanel;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.navigation.NekkoraTabs;
import com.nekkora.ui.state.NekkoraEmptyState;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Área de trabalho em abas (documentos abertos). Abrir um id já aberto apenas o seleciona.
 * Sem abas, mostra o estado vazio configurável.
 */
public class NekkoraWorkArea extends NekkoraPanel {
    private final NekkoraTabs tabs = new NekkoraTabs(NekkoraTabs.Style.CARD);
    private final Map<String, Component> open = new LinkedHashMap<>();
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private Predicate<String> canClose = id -> true;

    public NekkoraWorkArea() {
        this(new NekkoraEmptyState(IconName.WORKSPACE, "Nenhum item aberto", "Abra um item no navegador para começar."));
    }

    public NekkoraWorkArea(JComponent emptyState) {
        super(new BorderLayout(), Background.SURFACE);
        tabs.setClosable(true, index -> {
            String id = idAt(index);
            if (id != null && !canClose.test(id)) return false;
            open.remove(id);
            javax.swing.SwingUtilities.invokeLater(this::updateCard);
            return true;
        });
        body.setOpaque(false);
        body.add(tabs, "tabs");
        body.add(emptyState, "empty");
        add(body, BorderLayout.CENTER);
        updateCard();
    }

    /** Abre (ou seleciona) um documento. */
    public void openTab(String id, String title, IconName icon, Component content) {
        Component existing = open.get(id);
        if (existing != null) {
            tabs.setSelectedComponent(existing);
            return;
        }
        open.put(id, content);
        tabs.addTab(title, icon, content);
        tabs.setSelectedComponent(content);
        updateCard();
    }

    /** Confirmação antes de fechar (ex.: alterações não salvas). */
    public void setCanClose(Predicate<String> canClose) {
        this.canClose = canClose;
    }

    public boolean isOpen(String id) {
        return open.containsKey(id);
    }

    public int getOpenCount() {
        return open.size();
    }

    public NekkoraTabs getTabs() {
        return tabs;
    }

    private String idAt(int index) {
        Component c = tabs.getComponentAt(index);
        return open.entrySet().stream().filter(e -> e.getValue() == c).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    private void updateCard() {
        cards.show(body, open.isEmpty() ? "empty" : "tabs");
    }
}
