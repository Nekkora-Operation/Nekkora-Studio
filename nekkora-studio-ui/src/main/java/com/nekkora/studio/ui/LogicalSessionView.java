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
import com.nekkora.ui.action.NekkoraKeys;
import com.nekkora.ui.navigation.NekkoraPopupMenu;
import java.awt.CardLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

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

        // ---- STUDIO-004: edição (padrões mantêm os ouvintes anteriores funcionando)

        /** Editar a etapa (F2, menu de contexto). */
        default void editStepRequested(SessionId session, StepId step) {
            stepOpened(session, step);
        }

        /** Mover a etapa uma posição (Alt+↑ = -1, Alt+↓ = +1). */
        default void moveStepRequested(StepId step, int delta) {
        }

        /** Mover a etapa para outra sessão ou posição (diálogo). */
        default void moveStepToRequested(StepId step) {
        }

        default void duplicateStepRequested(StepId step) {
        }

        /** Excluir a etapa (Delete, menu de contexto) — sempre com confirmação. */
        default void deleteStepRequested(StepId step) {
        }

        /** Editar nome, descrição e observações da sessão. */
        default void editSessionRequested(SessionId session) {
            renameSessionRequested(session);
        }

        /** Mover a sessão uma posição (-1 para cima, +1 para baixo). */
        default void moveSessionRequested(SessionId session, int delta) {
        }

        /** Excluir a sessão (e as etapas dela) — sempre com confirmação. */
        default void deleteSessionRequested(SessionId session) {
        }
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
    private final NekkoraIconButton rename = new NekkoraIconButton(IconName.EDIT, "Editar sessão").withSize(ControlSize.SMALL);
    private final NekkoraIconButton more = new NekkoraIconButton(IconName.MORE_HORIZONTAL, "Mais ações da sessão").withSize(ControlSize.SMALL);
    private final NekkoraBadge selectedBadge = new NekkoraBadge("Selecionada", Tone.ACCENT).withIcon(IconName.CHECK)
            .withSize(NekkoraBadge.Size.SMALL);
    private Map<StepId, Integer> numbers = Map.of();
    private int firstNumber = 1;
    private int refreshes;
    private boolean adjusting;
    private boolean highlighted;
    private boolean editable = true;
    private boolean canMoveUp;
    private boolean canMoveDown;

    public LogicalSessionView(SessionId sessionId, Listener listener) {
        this.sessionId = sessionId;
        this.listener = listener;
        selectedBadge.setVisible(false);
        setHeaderActions(selectedBadge, addStep, rename, more);
        addStep.addActionListener(e -> listener.addStepRequested(sessionId));
        rename.addActionListener(e -> listener.editSessionRequested(sessionId));
        more.addActionListener(e -> sessionMenu().show(more, 0, more.getHeight()));
        setOnClick(() -> listener.sessionSelected(sessionId));

        steps.withLabel(this::labelOf).withSecondary(LogicalSessionView::detailOf, true).withIcon(s -> ActionTypeStyle.icon(s.actionType()));
        steps.addListSelectionListener(e -> {
            if (adjusting || e.getValueIsAdjusting()) return;
            AutomationStep s = steps.getSelectedValue();
            if (s != null) listener.stepSelected(sessionId, s.id());
        });
        steps.onItemActivated(s -> listener.stepOpened(sessionId, s.id()));
        installStepEditing();

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

    // ------------------------------------------------------------------ STUDIO-004: edição pelo teclado e menus

    /**
     * Atalhos da lista de etapas (somente com o foco NA lista — nunca num campo de texto): Delete exclui (com
     * confirmação), Alt+↑/Alt+↓ movem, Ctrl+D duplica, Shift+F10/tecla de menu abrem o menu de contexto.
     */
    private void installStepEditing() {
        bindStep(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "studio.step.delete", s -> listener.deleteStepRequested(s.id()));
        bindStep(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK), "studio.step.up", s -> listener.moveStepRequested(s.id(), -1));
        bindStep(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK), "studio.step.down", s -> listener.moveStepRequested(s.id(), 1));
        bindStep(NekkoraKeys.shortcut(KeyEvent.VK_D), "studio.step.duplicate", s -> listener.duplicateStepRequested(s.id()));
        // clique direito seleciona a etapa sob o ponteiro antes de abrir o menu
        steps.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger() || javax.swing.SwingUtilities.isRightMouseButton(e)) {
                    int i = steps.locationToIndex(e.getPoint());
                    if (i >= 0) steps.setSelectedIndex(i);
                }
            }
        });
        NekkoraPopupMenu.installContextMenu(steps, () -> {
            AutomationStep s = steps.getSelectedValue();
            return s == null ? null : stepMenu(s);
        });
    }

    private void bindStep(KeyStroke key, String name, java.util.function.Consumer<AutomationStep> action) {
        steps.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
        steps.getActionMap().put(name, new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                AutomationStep s = steps.getSelectedValue();
                if (s != null) action.accept(s); // bloqueada (gravação): o caso de uso recusa e explica o motivo
            }
        });
    }

    /** Menu de contexto de uma etapa (os mesmos casos de uso dos atalhos e do painel de propriedades). */
    public NekkoraPopupMenu stepMenu(AutomationStep s) {
        NekkoraPopupMenu m = new NekkoraPopupMenu().header(s.name());
        m.item("Editar etapa", IconName.EDIT, KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), () -> listener.editStepRequested(sessionId, s.id()));
        if (editable) {
            m.item("Mover para cima", IconName.ARROW_UP, KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK),
                    () -> listener.moveStepRequested(s.id(), -1));
            m.item("Mover para baixo", IconName.ARROW_DOWN, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK),
                    () -> listener.moveStepRequested(s.id(), 1));
            m.item("Mover para…", IconName.SORT, null, () -> listener.moveStepToRequested(s.id()));
            m.item("Duplicar", IconName.COPY, NekkoraKeys.shortcut(KeyEvent.VK_D), () -> listener.duplicateStepRequested(s.id()));
            m.separator();
            m.danger("Excluir etapa", IconName.TRASH, () -> listener.deleteStepRequested(s.id()));
        }
        return m;
    }

    /** Menu "Mais ações" da sessão. */
    public NekkoraPopupMenu sessionMenu() {
        NekkoraPopupMenu m = new NekkoraPopupMenu();
        m.item("Editar sessão", IconName.EDIT, null, () -> listener.editSessionRequested(sessionId));
        if (editable) {
            if (canMoveUp) m.item("Mover sessão para cima", IconName.ARROW_UP, null, () -> listener.moveSessionRequested(sessionId, -1));
            if (canMoveDown) m.item("Mover sessão para baixo", IconName.ARROW_DOWN, null, () -> listener.moveSessionRequested(sessionId, 1));
            m.separator();
            m.danger("Excluir sessão", IconName.TRASH, () -> listener.deleteSessionRequested(sessionId));
        }
        return m;
    }

    /**
     * Edição permitida (falso enquanto a automação recebe uma gravação): as ações de alterar ficam indisponíveis, mas a
     * sessão continua navegável e consultável.
     */
    public void setEditable(boolean value) {
        editable = value;
        addStep.setEnabled(value);
    }

    public boolean isEditable() {
        return editable;
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
        canMoveUp = number > 1;
        canMoveDown = number < automation.sessions().size();
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

    /**
     * Linha secundária exibida na lista: tipo · origem · estado (texto, não só cor) e, com evidências observadas
     * (STUDIO-005), o resumo curto da observação — "mudanças observadas", nunca "sucesso" ou "falha".
     */
    public static String detailOf(AutomationStep step) {
        String base = step.actionType().label() + " · " + step.provenance().origin().label() + " · " + StudioText.stepState(step);
        String observed = step.evidence().isEmpty() ? "" : EvidencePresentation.badge(step);
        return observed.isEmpty() ? base : base + " · " + observed;
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
