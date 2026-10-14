package com.nekkora.studio.ui;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.tokens.ControlSize;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.button.NekkoraIconButton;
import com.nekkora.ui.container.NekkoraPanel;
import com.nekkora.ui.data.NekkoraList;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.state.NekkoraEmptyState;
import com.nekkora.ui.studio.NekkoraNavigator;
import java.awt.CardLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Navegação entre as automações do ambiente de trabalho. Lista as automações reais do estado da
 * aplicação (nunca exemplos fictícios) e mostra um estado vazio com o próximo passo quando não há nenhuma.
 */
public final class AutomationNavigator extends NekkoraNavigator {
    private static final String LIST = "list";
    private static final String EMPTY = "empty";

    private final NekkoraList<Automation> list = new NekkoraList<>();
    private final CardLayout cards = new CardLayout();
    private final NekkoraPanel body = new NekkoraPanel(cards);
    private final NekkoraIconButton create = new NekkoraIconButton(IconName.PLUS, "Nova automação").withSize(ControlSize.SMALL);
    private Consumer<AutomationId> onSelect = id -> { };
    private Consumer<AutomationId> onOpen = id -> { };
    private boolean adjusting;

    public AutomationNavigator(Runnable onCreate) {
        super("Automações");
        setHeaderActions(create);
        create.addActionListener(e -> onCreate.run());
        list.withLabel(Automation::name).withSecondary(StudioText::summary, true).withIcon(a -> IconName.WORKFLOW);
        list.getAccessibleContext().setAccessibleName("Automações");
        list.addListSelectionListener(e -> {
            if (adjusting || e.getValueIsAdjusting() || list.getSelectedValue() == null) return;
            onSelect.accept(list.getSelectedValue().id());
        });
        list.onItemActivated(a -> onOpen.accept(a.id()));
        NekkoraEmptyState empty = new NekkoraEmptyState(IconName.WORKFLOW, "Nenhuma automação",
                "Crie a primeira automação para organizar sessões e etapas.");
        empty.addAction(NekkoraButton.primary("Nova automação").withIcon(IconName.PLUS).onClick(e -> onCreate.run()));
        body.add(new com.nekkora.ui.container.NekkoraScrollPane(list), LIST);
        body.add(empty, EMPTY);
        setContent(body);
        cards.show(body, EMPTY);
    }

    /** Seleção pelo usuário (clique ou setas). */
    public void onSelect(Consumer<AutomationId> listener) {
        this.onSelect = Objects.requireNonNull(listener);
    }

    /** Abertura explícita (Enter ou duplo clique). */
    public void onOpen(Consumer<AutomationId> listener) {
        this.onOpen = Objects.requireNonNull(listener);
    }

    /** Reflete as automações do estado e marca a ativa, sem disparar eventos de seleção. */
    public void show(List<Automation> automations, AutomationId active) {
        adjusting = true;
        try {
            list.setItems(automations);
            list.clearSelection();
            for (int i = 0; i < automations.size(); i++) {
                if (automations.get(i).id().equals(active)) {
                    list.setSelectedIndex(i);
                    list.ensureIndexIsVisible(i);
                }
            }
        } finally {
            adjusting = false;
        }
        cards.show(body, automations.isEmpty() ? EMPTY : LIST);
        revalidate();
        repaint();
    }

    public NekkoraList<Automation> automationList() {
        return list;
    }

    public NekkoraIconButton createButton() {
        return create;
    }

    /** Automações exibidas, na ordem. */
    public List<Automation> items() {
        List<Automation> out = new ArrayList<>();
        for (int i = 0; i < list.getModel().getSize(); i++) out.add(list.getModel().getElementAt(i));
        return out;
    }
}
