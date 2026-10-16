package com.nekkora.studio.ui;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepId;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.container.Background;
import com.nekkora.ui.container.NekkoraPanel;
import com.nekkora.ui.container.NekkoraScrollPane;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.core.Status;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.data.NekkoraIconMarker;
import com.nekkora.ui.data.NekkoraStatusBadge;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.state.NekkoraEmptyState;
import com.nekkora.ui.typography.NekkoraCaption;
import com.nekkora.ui.typography.NekkoraHeading;
import com.nekkora.ui.typography.NekkoraParagraph;
import com.nekkora.ui.typography.TextTone;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JPanel;

/**
 * Visão de uma automação: cabeçalho (nome, estado, resumo, ações) e o fluxo
 * <b>Automação → Sessões → Etapas</b> na ordem do domínio. {@link #refresh()} reconcilia as sessões
 * existentes (sem recriar a tela inteira) e {@link #showSelection} apenas destaca a seleção.
 */
public final class AutomationOverview extends NekkoraPanel {

    /** Intenções do usuário na visão da automação. */
    public interface Listener extends LogicalSessionView.Listener {
        void automationSelected();

        void addSessionRequested();

        /** Nova etapa sem sessão pré-escolhida (o formulário pergunta). */
        void addStepRequested();
    }

    private static final String SESSIONS = "sessions";
    private static final String EMPTY = "empty";

    private final Automation automation;
    private final Listener listener;
    private final NekkoraHeading title = new NekkoraHeading("", NekkoraHeading.Level.H1);
    private final NekkoraParagraph description = new NekkoraParagraph("");
    private final NekkoraCaption summary = new NekkoraCaption("", TextTone.SECONDARY);
    private final NekkoraButton newStep = NekkoraButton.secondary("Nova etapa").withIcon(IconName.PLUS);
    private final NekkoraStack sessionStack = NekkoraStack.vertical(Spacing.SPACE_6);
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final Map<SessionId, LogicalSessionView> views = new LinkedHashMap<>();
    private final com.nekkora.ui.feedback.NekkoraBanner lockBanner = new com.nekkora.ui.feedback.NekkoraBanner(com.nekkora.ui.core.Severity.INFO,
            "Gravação em andamento", "Esta automação está sendo gravada. Finalize ou cancele a gravação para reorganizar, mover, excluir ou editar as etapas com segurança. "
                    + "Você pode continuar navegando e consultando as propriedades.");
    private boolean editable = true;

    public AutomationOverview(Automation automation, Listener listener) {
        super(new BorderLayout(), Background.SURFACE);
        this.automation = automation;
        this.listener = listener;

        NekkoraStack doc = NekkoraStack.vertical(Spacing.SPACE_7);
        doc.setPadding(Spacing.SPACE_8, Spacing.SPACE_9);

        NekkoraStack heading = NekkoraStack.horizontal(Spacing.SPACE_5).align(NekkoraStack.Align.CENTER);
        heading.add(new NekkoraIconMarker(IconName.WORKFLOW, Tone.ACCENT, true, 40));
        NekkoraStack texts = NekkoraStack.vertical(Spacing.SPACE_1);
        NekkoraStack nameRow = NekkoraStack.horizontal(Spacing.SPACE_4).align(NekkoraStack.Align.CENTER);
        nameRow.add(title);
        nameRow.add(new NekkoraStatusBadge(Status.DRAFT, "Rascunho"));
        texts.add(nameRow);
        texts.add(summary);
        heading.add(texts);
        heading.addGlue();
        doc.add(heading);
        lockBanner.setVisible(false);
        lockBanner.getAccessibleContext().setAccessibleName("Edição bloqueada durante a gravação");
        doc.add(lockBanner);
        doc.add(description);

        NekkoraStack actions = NekkoraStack.horizontal(Spacing.SPACE_4).align(NekkoraStack.Align.CENTER);
        actions.add(NekkoraButton.primary("Nova sessão").withIcon(IconName.LAYERS).onClick(e -> listener.addSessionRequested()));
        newStep.onClick(e -> listener.addStepRequested());
        actions.add(newStep);
        actions.add(NekkoraButton.ghost("Propriedades da automação").withIcon(IconName.SLIDERS).onClick(e -> listener.automationSelected()));
        actions.addGlue();
        doc.add(actions);

        NekkoraEmptyState empty = new NekkoraEmptyState(IconName.LAYERS, "Nenhuma sessão ainda",
                "Divida a automação em sessões lógicas — por exemplo, Autenticação, Consulta e Resultado — e depois adicione as etapas.");
        empty.addAction(NekkoraButton.primary("Nova sessão").withIcon(IconName.PLUS).onClick(e -> listener.addSessionRequested()));
        body.setOpaque(false);
        body.add(sessionStack, SESSIONS);
        body.add(empty, EMPTY);
        doc.add(body);

        add(NekkoraScrollPane.verticalOnly(doc), BorderLayout.CENTER);
        getAccessibleContext().setAccessibleName("Automação");
        refresh();
    }

    public Automation automation() {
        return automation;
    }

    /** Relê o domínio: cabeçalho, sessões novas/alteradas e etapas. */
    public void refresh() {
        refreshHeader();
        List<SessionId> order = new ArrayList<>();
        for (LogicalSession s : automation.sessions()) order.add(s.id());
        // Mantém as MESMAS visões (sem recriar), agora na ordem do domínio; o mapa acompanha a ordem (STUDIO-004: a
        // reconciliação comparava a ordem de inserção do mapa, e subir e descer a mesma sessão deixava os cartões trocados).
        Map<SessionId, LogicalSessionView> ordered = new LinkedHashMap<>();
        for (SessionId id : order) {
            LogicalSessionView v = views.get(id);
            if (v == null) {
                v = new LogicalSessionView(id, listener);
                v.setEditable(editable);
            }
            ordered.put(id, v);
        }
        views.clear();
        views.putAll(ordered);
        List<LogicalSessionView> shown = sessionViews();
        if (!shown.equals(new ArrayList<>(views.values()))) {
            sessionStack.removeAll();
            for (LogicalSessionView v : views.values()) sessionStack.add(v);
        }
        int first = 1;
        for (LogicalSession s : automation.sessions()) {
            views.get(s.id()).refresh(automation, first);
            first += s.stepCount();
        }
        cards.show(body, order.isEmpty() ? EMPTY : SESSIONS);
        revalidate();
        repaint();
    }

    /**
     * Atualização restrita a uma sessão (STUDIO-002): relê só a sessão afetada e apenas RENUMERA as seguintes, se a
     * numeração delas mudou — as demais sessões não são reconstruídas e a seleção é preservada. Sessão nova, removida
     * ou reordenada cai na reconciliação completa.
     */
    public void refreshSession(SessionId affected) {
        refreshSessions(java.util.Set.of(affected));
    }

    /**
     * Atualização restrita a algumas sessões (STUDIO-004: origem e destino de uma movimentação): relê só as afetadas e
     * apenas RENUMERA as demais cuja numeração mudou. Estrutura diferente (sessão nova, removida ou reordenada) cai na
     * reconciliação completa.
     */
    public void refreshSessions(java.util.Set<SessionId> affectedSessions) {
        List<LogicalSession> sessions = automation.sessions();
        boolean sameStructure = sessions.size() == views.size() && sessionStack.getComponentCount() == sessions.size();
        if (sameStructure) {
            int i = 0;
            for (SessionId id : views.keySet()) {
                if (!id.equals(sessions.get(i++).id())) {
                    sameStructure = false;
                    break;
                }
            }
        }
        if (!sameStructure || affectedSessions.isEmpty() || !views.keySet().containsAll(affectedSessions)) {
            refresh();
            return;
        }
        refreshHeader();
        int first = 1;
        for (LogicalSession s : sessions) {
            LogicalSessionView v = views.get(s.id());
            if (affectedSessions.contains(s.id())) v.refresh(automation, first);
            else v.renumber(first); // só redesenha se a numeração mudou
            first += s.stepCount();
        }
        revalidate();
        repaint();
    }

    private void refreshHeader() {
        title.setText(automation.name());
        getAccessibleContext().setAccessibleName("Automação " + automation.name());
        description.setText(automation.description());
        description.setVisible(!automation.description().isBlank());
        summary.setText(StudioText.summary(automation));
        newStep.setEnabled(editable && !automation.sessions().isEmpty());
    }

    /**
     * Mostra a seleção: com {@code step} nulo, destaca a sessão; com etapa, seleciona a etapa na lista da sua
     * sessão. Ambos nulos limpam a seleção.
     */
    public void showSelection(SessionId session, StepId step) {
        for (LogicalSessionView v : views.values()) {
            boolean mine = v.sessionId().equals(session);
            v.showSelection(mine && step == null, mine ? step : null);
        }
    }

    public Optional<LogicalSessionView> sessionView(SessionId id) {
        return Optional.ofNullable(views.get(id));
    }

    /** Visões de sessão na ordem exibida. */
    public List<LogicalSessionView> sessionViews() {
        List<LogicalSessionView> out = new ArrayList<>();
        for (java.awt.Component c : sessionStack.getComponents()) out.add((LogicalSessionView) c);
        return out;
    }

    public boolean isShowingEmptyState() {
        return automation.sessions().isEmpty();
    }

    public NekkoraButton newStepButton() {
        return newStep;
    }

    /** STUDIO-004: bloqueio de edição enquanto a automação recebe uma gravação (navegação e consulta continuam). */
    public void setEditable(boolean value) {
        editable = value;
        lockBanner.setVisible(!value);
        for (LogicalSessionView v : views.values()) v.setEditable(value);
        newStep.setEnabled(value && !automation.sessions().isEmpty());
        revalidate();
        repaint();
    }

    public boolean isEditable() {
        return editable;
    }

    public com.nekkora.ui.feedback.NekkoraBanner lockBanner() {
        return lockBanner;
    }
}
