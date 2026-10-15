package com.nekkora.studio.ui;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepId;
import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.button.NekkoraIconButton;
import com.nekkora.ui.container.NekkoraCard;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.data.NekkoraBadge;
import com.nekkora.ui.data.NekkoraList;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.typography.NekkoraParagraph;
import com.nekkora.ui.typography.NekkoraText;
import com.nekkora.ui.typography.TextTone;
import java.awt.CardLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;

/**
 * Uma sessão lógica e suas etapas, em ordem. É uma <b>visão</b> do domínio: não guarda cópia das etapas —
 * {@link #refresh(Automation)} relê a sessão e a sequência calculada. Card do design system com lista
 * navegável por teclado (setas, Enter abre os detalhes).
 */
public final class LogicalSessionView extends NekkoraCard {

    /** Intenções do usuário sobre a sessão. */
    public interface Listener {
        void sessionSelected(SessionId session);

        void stepSelected(SessionId session, StepId step);

        void stepOpened(SessionId session, StepId step);

        void addStepRequested(SessionId session);

        void renameSessionRequested(SessionId session);
    }

    private static final String LIST = "list";
    private static final String EMPTY = "empty";

    private final SessionId sessionId;
    private final Listener listener;
    private final NekkoraList<AutomationStep> steps = new NekkoraList<>();
    private final NekkoraParagraph description = new NekkoraParagraph("");
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final NekkoraIconButton addStep = new NekkoraIconButton(IconName.PLUS, "Adicionar etapa nesta sessão").withSize(ControlSize.SMALL);
    private final NekkoraIconButton rename = new NekkoraIconButton(IconName.EDIT, "Renomear sessão").withSize(ControlSize.SMALL);
    private final NekkoraBadge selectedBadge = new NekkoraBadge("Selecionada", Tone.ACCENT).withIcon(IconName.CHECK)
            .withSize(NekkoraBadge.Size.SMALL);
    private Map<StepId, Integer> numbers = Map.of();
    private int firstNumber = 1;
    private int refreshes;
    private boolean adjusting;
    private boolean highlighted;

    public LogicalSessionView(SessionId sessionId, Listener listener) {
        this.sessionId = sessionId;
        this.listener = listener;
        selectedBadge.setVisible(false);
        setHeaderActions(selectedBadge, addStep, rename);
        addStep.addActionListener(e -> listener.addStepRequested(sessionId));
        rename.addActionListener(e -> listener.renameSessionRequested(sessionId));
        setOnClick(() -> listener.sessionSelected(sessionId));

        steps.withLabel(this::labelOf).withSecondary(LogicalSessionView::detailOf, true).withIcon(s -> ActionTypeStyle.icon(s.actionType()));
        steps.addListSelectionListener(e -> {
            if (adjusting || e.getValueIsAdjusting()) return;
            AutomationStep s = steps.getSelectedValue();
            if (s != null) listener.stepSelected(sessionId, s.id());
        });
        steps.onItemActivated(s -> listener.stepOpened(sessionId, s.id()));

        NekkoraStack empty = NekkoraStack.horizontal(Spacing.SPACE_5).align(NekkoraStack.Align.CENTER);
        empty.add(NekkoraText.muted("Nenhuma etapa nesta sessão."));
        empty.add(NekkoraButton.ghost("Adicionar etapa").withIcon(IconName.PLUS).withSize(ControlSize.SMALL)
                .onClick(e -> listener.addStepRequested(sessionId)));
        empty.addGlue();

        body.setOpaque(false);
        body.add(steps, LIST);
        body.add(empty, EMPTY);
        NekkoraStack content = NekkoraStack.vertical(Spacing.SPACE_4);
        content.add(description);
        content.add(body);
        setContent(content);
    }

    public SessionId sessionId() {
        return sessionId;
    }

    /** Relê sessão e numeração do domínio. Preserva a etapa selecionada, se ainda existir. */
    public void refresh(Automation automation) {
        int first = 1;
        for (LogicalSession s : automation.sessions()) {
            if (s.id().equals(sessionId)) break;
            first += s.stepCount();
        }
        refresh(automation, first);
    }

    /**
     * Relê a sessão sabendo o número global da sua primeira etapa (as etapas são numeradas em sequência na ordem das
     * sessões): não percorre a automação inteira.
     */
    public void refresh(Automation automation, int firstNumber) {
        refreshes++;
        LogicalSession session = automation.session(sessionId).orElseThrow();
        int number = automation.sessionNumber(sessionId);
        setTitle(session.name(), "Sessão " + number + " · " + StudioText.steps(session.stepCount()));
        getAccessibleContext().setAccessibleName("Sessão " + number + ": " + session.name());
        description.setText(session.description());
        description.setVisible(!session.description().isBlank());
        Map<StepId, Integer> n = new HashMap<>();
        int k = firstNumber;
        for (AutomationStep st : session.steps()) n.put(st.id(), k++);
        numbers = n;
        this.firstNumber = firstNumber;
        AutomationStep selected = steps.getSelectedValue();
        adjusting = true;
        try {
            steps.setItems(session.steps());
            if (selected != null) select(selected.id());
        } finally {
            adjusting = false;
        }
        steps.getAccessibleContext().setAccessibleName("Etapas da sessão " + session.name());
        cards.show(body, session.isEmpty() ? EMPTY : LIST);
        revalidate();
        repaint();
    }

    /**
     * Só a numeração mudou (uma sessão anterior ganhou etapas): recalcula os números e redesenha, sem recriar a lista
     * nem mexer na seleção.
     */
    public void renumber(int newFirstNumber) {
        if (newFirstNumber == firstNumber) return;
        int delta = newFirstNumber - firstNumber;
        Map<StepId, Integer> n = new HashMap<>();
        for (Map.Entry<StepId, Integer> e : numbers.entrySet()) n.put(e.getKey(), e.getValue() + delta);
        numbers = n;
        firstNumber = newFirstNumber;
        steps.repaint();
    }

    /** Quantas vezes a sessão foi relida do domínio (diagnóstico da atualização incremental). */
    public int refreshCount() {
        return refreshes;
    }

    /** Destaca a sessão (selecionada ou contendo a etapa selecionada) e seleciona a etapa, sem disparar eventos. */
    public void showSelection(boolean sessionHighlighted, StepId step) {
        if (highlighted != sessionHighlighted) {
            highlighted = sessionHighlighted;
            selectedBadge.setVisible(sessionHighlighted); // seleção indicada por texto, não só por cor
            revalidate();
        }
        adjusting = true;
        try {
            if (step == null) steps.clearSelection();
            else select(step);
        } finally {
            adjusting = false;
        }
    }

    private void select(StepId id) {
        List<AutomationStep> items = itemsInOrder();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(id)) {
                steps.setSelectedIndex(i);
                steps.ensureIndexIsVisible(i);
                return;
            }
        }
        steps.clearSelection();
    }

    /** Etapas exibidas, na ordem (para testes e acessibilidade). */
    public List<AutomationStep> itemsInOrder() {
        javax.swing.ListModel<AutomationStep> m = steps.getModel();
        java.util.ArrayList<AutomationStep> out = new java.util.ArrayList<>();
        for (int i = 0; i < m.getSize(); i++) out.add(m.getElementAt(i));
        return out;
    }

    /** Texto principal exibido para a etapa na lista ("01   Preencher usuário"). */
    public String labelOf(AutomationStep step) {
        return StudioText.number(numbers.getOrDefault(step.id(), 0)) + "   " + step.name();
    }

    /** Linha secundária exibida na lista: tipo · origem · estado (texto, não só cor). */
    public static String detailOf(AutomationStep step) {
        return step.actionType().label() + " · " + step.provenance().origin().label() + " · " + StudioText.stepState(step);
    }

    public NekkoraList<AutomationStep> stepList() {
        return steps;
    }

    public boolean isHighlighted() {
        return highlighted;
    }

    /** {@code true} quando a sessão não tem etapas e mostra o estado vazio. */
    public boolean isShowingEmptyState() {
        return itemsInOrder().isEmpty();
    }

    /** Layout com o card ocupando a largura disponível. */
    @Override
    public java.awt.Dimension getMaximumSize() {
        return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
