package com.nekkora.studio.ui.debug;

import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.container.NekkoraCollapsiblePanel;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.core.Severity;
import com.nekkora.ui.core.Status;
import com.nekkora.ui.data.NekkoraKeyValueView;
import com.nekkora.ui.data.NekkoraStatusBadge;
import com.nekkora.ui.feedback.NekkoraInlineAlert;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.input.NekkoraComboBox;
import com.nekkora.ui.input.NekkoraTextField;
import com.nekkora.ui.typography.NekkoraParagraph;
import com.nekkora.ui.typography.NekkoraText;
import com.nekkora.ui.typography.TextTone;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * STUDIO-009 G3 — conteúdo da janela flutuante do debug guiado. É uma VISÃO: recebe um {@link Card} pronto (o
 * apresentador decide o que mostrar a partir dos códigos) e devolve intenções ({@link Actions}). Somente componentes e
 * tokens do Nekkora Swing UI. Compacta: o essencial cabe; o técnico fica em "Detalhes" (recolhido).
 *
 * <p>Estados visuais distintos (§1.2): proposto, confirmado pelo ator, verificado tecnicamente, reconfirmado sem nova
 * pergunta, requer revisão e não verificável — cada um com rótulo e ícone (a cor nunca é a única pista).
 */
public final class GuidedDebugPanel extends JPanel {

    /** Intenções do usuário. Continuar NUNCA executa a ação da etapa: só registra a decisão sobre o alvo. */
    public interface Actions {
        void start(String tabRef);

        void continueStep();

        void correct();

        void pickInBrowser();

        void cancelPick();

        void examine(String candidateRef);

        void adopt(String candidateRef);

        void informLocator(String css);

        void applyCorrection();

        void discardCorrection();

        void previous();

        void next();

        void finish();

        /** G6: sonda de laboratório (só em aba LAB): operação explícita, com dado fictício por chave. */
        void probe(String valueKey);
    }

    /** Estado apresentado da etapa. */
    public enum Badge {
        CHECKING("Verificando…", Status.RUNNING),
        PROPOSED("Proposto · verificado tecnicamente", Status.INFO),
        CONFIRMED("Confirmado", Status.SUCCESS),
        REUSED("Reconfirmado tecnicamente", Status.SUCCESS),
        REVIEW("Requer revisão", Status.WARNING),
        NOT_VERIFIABLE("Não verificável", Status.FAILED),
        PICKING("Selecione no navegador", Status.PENDING),
        COMPARING("Compare antes de aplicar", Status.PENDING),
        IDLE("Debug guiado", Status.PENDING),
        FINISHED("Debug concluído", Status.INFO);

        final String label;
        final Status status;

        Badge(String label, Status status) {
            this.label = label;
            this.status = status;
        }

        public String label() {
            return label;
        }
    }

    /** Modo da janela. */
    public enum Mode { CHOOSE_TAB, STEP, CORRECTING, PICKING, COMPARING, FINISHED }

    public record Row(String key, String value) {}

    public record Candidate(String ref, String label, boolean confirmed) {}

    public record Tab(String tabRef, String label) {}

    /** Comparação antes × depois de uma correção. */
    public record Comparison(List<Row> before, List<Row> after, boolean compatible, String verdict) {
        public Comparison {
            before = List.copyOf(before);
            after = List.copyOf(after);
        }
    }

    /** Tudo o que a janela mostra. */
    public record Card(Mode mode, Badge badge, String title, String progress, String actor, String recognized, List<String> why, List<String> limits,
                       List<Row> details, List<Candidate> candidates, Comparison comparison, List<Tab> tabs, String message, boolean canContinue,
                       boolean canCorrect, boolean canPrevious, boolean canNext) {
        public Card {
            why = List.copyOf(why);
            limits = List.copyOf(limits);
            details = List.copyOf(details);
            candidates = List.copyOf(candidates);
            tabs = List.copyOf(tabs);
        }

        public static Card message(Mode mode, Badge badge, String title, String actor, String message) {
            return new Card(mode, badge, title, "", actor, "", List.of(), List.of(), List.of(), List.of(), null, List.of(), message, false, false, false, false);
        }
    }

    private final Actions actions;
    private final NekkoraStatusBadge badge = new NekkoraStatusBadge(Status.PENDING, "");
    private final NekkoraText progress = new NekkoraText("", TextTone.MUTED);
    private final NekkoraText title = new NekkoraText("");
    private final NekkoraText actor = new NekkoraText("", TextTone.SECONDARY);
    private final NekkoraParagraph recognized = new NekkoraParagraph("");
    private final NekkoraInlineAlert message = new NekkoraInlineAlert(Severity.INFO, "", "");
    private final NekkoraStack why = NekkoraStack.vertical(Spacing.SPACE_1);
    private final NekkoraStack limits = NekkoraStack.vertical(Spacing.SPACE_1);
    private final NekkoraStack candidates = NekkoraStack.vertical(Spacing.SPACE_2);
    private final NekkoraStack comparison = NekkoraStack.vertical(Spacing.SPACE_2);
    private final NekkoraStack details = NekkoraStack.vertical(Spacing.SPACE_1);
    private final NekkoraCollapsiblePanel detailsPanel = new NekkoraCollapsiblePanel("Detalhes técnicos", details);
    private final NekkoraComboBox<Tab> tabs = new NekkoraComboBox<>();
    private final NekkoraTextField locator = new NekkoraTextField("Ex.: #registro-codigo", 18);
    private final NekkoraButton startButton = NekkoraButton.primary("Iniciar debug").withIcon(IconName.PLAY).withSize(ControlSize.SMALL);
    private final NekkoraButton continueButton = NekkoraButton.primary("Continuar").withIcon(IconName.CHECK).withSize(ControlSize.SMALL);
    private final NekkoraButton correctButton = NekkoraButton.secondary("Corrigir").withIcon(IconName.EDIT).withSize(ControlSize.SMALL);
    private final NekkoraButton pickButton = NekkoraButton.secondary("Selecionar no navegador").withIcon(IconName.SEARCH).withSize(ControlSize.SMALL);
    private final NekkoraButton locatorButton = NekkoraButton.ghost("Registrar localizador").withSize(ControlSize.SMALL);
    private final NekkoraButton cancelPickButton = NekkoraButton.ghost("Cancelar seleção").withIcon(IconName.CLOSE).withSize(ControlSize.SMALL);
    private final NekkoraButton applyButton = NekkoraButton.primary("Aplicar correção").withIcon(IconName.CHECK).withSize(ControlSize.SMALL);
    private final NekkoraButton discardButton = NekkoraButton.ghost("Descartar").withSize(ControlSize.SMALL);
    private final NekkoraButton previousButton = NekkoraButton.ghost("Anterior").withIcon(IconName.ARROW_LEFT).withSize(ControlSize.SMALL);
    private final NekkoraButton nextButton = NekkoraButton.ghost("Próxima").withIcon(IconName.ARROW_RIGHT).withSize(ControlSize.SMALL);
    private final NekkoraButton finishButton = NekkoraButton.ghost("Encerrar").withIcon(IconName.STOP).withSize(ControlSize.SMALL);
    private final NekkoraStack tabRow = NekkoraStack.horizontal(Spacing.SPACE_2);
    private final NekkoraStack decisionRow = NekkoraStack.horizontal(Spacing.SPACE_2);
    private final NekkoraStack correctionRow = NekkoraStack.vertical(Spacing.SPACE_2);
    private final NekkoraStack pickRow = NekkoraStack.horizontal(Spacing.SPACE_2);
    private final NekkoraStack compareRow = NekkoraStack.horizontal(Spacing.SPACE_2);
    private final NekkoraStack navRow = NekkoraStack.horizontal(Spacing.SPACE_2);
    private final NekkoraStack probeRow = NekkoraStack.horizontal(Spacing.SPACE_2);
    private final NekkoraComboBox<String> probeKeys = new NekkoraComboBox<>();
    private final NekkoraButton probeButton = NekkoraButton.secondary("Sonda de laboratório").withIcon(IconName.PLAY).withSize(ControlSize.SMALL);
    private final NekkoraText whyTitle = new NekkoraText("Por quê", TextTone.SECONDARY);
    private final NekkoraText limitsTitle = new NekkoraText("Limitações", TextTone.SECONDARY);
    private final NekkoraText candidatesTitle = new NekkoraText("Outros candidatos", TextTone.SECONDARY);
    private Card card;
    private final com.nekkora.ui.container.NekkoraScrollPane scroll;

    public GuidedDebugPanel(Actions actions) {
        super(new BorderLayout(0, Spacing.SPACE_3.px()));
        this.actions = Objects.requireNonNull(actions);
        setOpaque(false);
        // cabeçalho fixo · conteúdo rolável · ações fixas: com muito conteúdo (alerta, candidatos, detalhes) as ações
        // (Continuar/Corrigir, Anterior/Próxima/Encerrar) nunca saem da tela
        NekkoraStack top = NekkoraStack.vertical(Spacing.SPACE_3);
        NekkoraStack root = NekkoraStack.vertical(Spacing.SPACE_3);
        NekkoraStack actionsStack = NekkoraStack.vertical(Spacing.SPACE_3);
        NekkoraStack head = NekkoraStack.horizontal(Spacing.SPACE_2).align(NekkoraStack.Align.CENTER);
        head.add(badge);
        head.addGlue();
        head.add(progress);
        top.add(head);
        top.add(title);
        top.add(actor);
        root.add(recognized);
        root.add(message);
        root.add(whyTitle);
        root.add(why);
        root.add(limitsTitle);
        root.add(limits);
        root.add(candidatesTitle);
        root.add(candidates);
        root.add(comparison);
        root.add(detailsPanel);

        tabs.setLabelProvider(t -> t == null ? "" : t.label());
        tabs.getAccessibleContext().setAccessibleName("Aba do Chrome para o debug");
        tabRow.add(tabs);
        tabRow.add(startButton);
        actionsStack.add(tabRow);

        decisionRow.add(continueButton);
        decisionRow.add(correctButton);
        actionsStack.add(decisionRow);

        NekkoraStack correctionButtons = NekkoraStack.horizontal(Spacing.SPACE_2);
        correctionButtons.add(pickButton);
        correctionRow.add(correctionButtons);
        NekkoraStack manual = NekkoraStack.horizontal(Spacing.SPACE_2);
        locator.getAccessibleContext().setAccessibleName("Localizador CSS proposto (não verificado)");
        manual.add(locator);
        manual.add(locatorButton);
        correctionRow.add(manual);
        actionsStack.add(correctionRow);

        pickRow.add(cancelPickButton);
        actionsStack.add(pickRow);
        compareRow.add(applyButton);
        compareRow.add(discardButton);
        actionsStack.add(compareRow);

        probeKeys.getAccessibleContext().setAccessibleName("Dado fictício do laboratório");
        probeRow.add(probeKeys);
        probeRow.add(probeButton);
        probeRow.setVisible(false);
        actionsStack.add(probeRow);
        probeButton.addActionListener(e -> actions.probe(probeKeys.isVisible() ? probeKeys.getSelectedValue() : null));
        probeButton.getAccessibleContext().setAccessibleDescription("Executa UMA operação no laboratório local com dado fictício. Nunca disponível fora do laboratório.");

        navRow.add(previousButton);
        navRow.add(nextButton);
        navRow.addGlue();
        navRow.add(finishButton);
        actionsStack.add(navRow);
        add(top, BorderLayout.NORTH);
        scroll = com.nekkora.ui.container.NekkoraScrollPane.verticalOnly(root);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        add(scroll, BorderLayout.CENTER);
        add(actionsStack, BorderLayout.SOUTH);

        startButton.addActionListener(e -> {
            Tab t = tabs.getSelectedValue();
            if (t != null) actions.start(t.tabRef());
        });
        continueButton.addActionListener(e -> actions.continueStep());
        correctButton.addActionListener(e -> actions.correct());
        pickButton.addActionListener(e -> actions.pickInBrowser());
        cancelPickButton.addActionListener(e -> actions.cancelPick());
        locatorButton.addActionListener(e -> {
            String css = locator.getText() == null ? "" : locator.getText().strip();
            if (!css.isEmpty()) actions.informLocator(css);
        });
        applyButton.addActionListener(e -> actions.applyCorrection());
        discardButton.addActionListener(e -> actions.discardCorrection());
        previousButton.addActionListener(e -> actions.previous());
        nextButton.addActionListener(e -> actions.next());
        finishButton.addActionListener(e -> actions.finish());
        continueButton.getAccessibleContext().setAccessibleDescription("Confirma que este é o elemento da etapa. Nada é executado na página.");
        correctButton.getAccessibleContext().setAccessibleDescription("Rejeita a proposta e abre as opções de correção.");
        show(Card.message(Mode.CHOOSE_TAB, Badge.IDLE, "Debug guiado", "", ""));
    }

    /** Mostra um cartão (na EDT). */
    public void show(Card c) {
        Card previous = this.card;
        this.card = Objects.requireNonNull(c);
        if (previous == null || previous.mode() != c.mode() || !previous.title().equals(c.title())) {
            javax.swing.SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0)); // outra etapa/modo: do topo
        }
        badge.setStatus(c.badge().status, c.badge().label());
        progress.setText(c.progress());
        progress.setVisible(!c.progress().isEmpty());
        title.setText(c.title());
        actor.setText(c.actor().isEmpty() ? "" : "Decisões registradas como: " + c.actor());
        actor.setVisible(!c.actor().isEmpty());
        recognized.setText(c.recognized());
        recognized.setVisible(!c.recognized().isEmpty());
        message.setVisible(!c.message().isEmpty());
        message.setSeverity(c.badge() == Badge.REVIEW || c.badge() == Badge.NOT_VERIFIABLE ? Severity.WARNING : Severity.INFO);
        message.setTitle("");
        message.setMessage(c.message());
        lines(why, c.why());
        whyTitle.setVisible(!c.why().isEmpty());
        lines(limits, c.limits());
        limitsTitle.setVisible(!c.limits().isEmpty());
        candidates.removeAll();
        for (Candidate cand : c.candidates()) {
            NekkoraStack row = NekkoraStack.horizontal(Spacing.SPACE_2).align(NekkoraStack.Align.CENTER);
            row.add(new NekkoraText(cand.label() + (cand.confirmed() ? "" : " · plausível")));
            row.addGlue();
            NekkoraButton look = NekkoraButton.ghost("Destacar").withSize(ControlSize.SMALL);
            look.addActionListener(e -> actions.examine(cand.ref()));
            NekkoraButton use = NekkoraButton.secondary("Usar este").withSize(ControlSize.SMALL);
            use.addActionListener(e -> actions.adopt(cand.ref()));
            row.add(look);
            row.add(use);
            candidates.add(row);
        }
        boolean showCandidates = !c.candidates().isEmpty() && (c.mode() == Mode.CORRECTING || c.mode() == Mode.STEP && c.badge() == Badge.REVIEW);
        candidates.setVisible(showCandidates);
        candidatesTitle.setVisible(showCandidates);
        comparison.removeAll();
        if (c.comparison() != null) {
            comparison.add(new NekkoraText("Antes", TextTone.SECONDARY));
            comparison.add(kv(c.comparison().before()));
            comparison.add(new NekkoraText("Depois (escolhido no navegador)", TextTone.SECONDARY));
            comparison.add(kv(c.comparison().after()));
            comparison.add(new NekkoraParagraph(c.comparison().verdict(), c.comparison().compatible() ? TextTone.SECONDARY : TextTone.ERROR));
        }
        comparison.setVisible(c.comparison() != null);
        details.removeAll();
        details.add(kv(c.details()));
        detailsPanel.setVisible(!c.details().isEmpty());
        List<Tab> current = new ArrayList<>();
        for (int i = 0; i < tabs.getItemCount(); i++) current.add(tabs.getItemAt(i));
        if (!current.equals(c.tabs())) {
            tabs.removeAllItems();
            c.tabs().forEach(tabs::addItem);
        }
        tabRow.setVisible(c.mode() == Mode.CHOOSE_TAB);
        startButton.setEnabled(!c.tabs().isEmpty());
        decisionRow.setVisible(c.mode() == Mode.STEP);
        continueButton.setEnabled(c.canContinue());
        correctButton.setEnabled(c.canCorrect());
        correctionRow.setVisible(c.mode() == Mode.CORRECTING);
        pickRow.setVisible(c.mode() == Mode.PICKING);
        compareRow.setVisible(c.mode() == Mode.COMPARING);
        applyButton.setEnabled(c.comparison() != null && c.comparison().compatible());
        navRow.setVisible(c.mode() != Mode.CHOOSE_TAB);
        previousButton.setVisible(c.mode() != Mode.FINISHED);
        nextButton.setVisible(c.mode() != Mode.FINISHED);
        previousButton.setEnabled(c.canPrevious());
        nextButton.setEnabled(c.canNext());
        revalidate();
        repaint();
    }

    private static void lines(NekkoraStack stack, List<String> lines) {
        stack.removeAll();
        for (String l : lines) stack.add(new NekkoraParagraph("• " + l, TextTone.SECONDARY));
        stack.setVisible(!lines.isEmpty());
    }

    private static JComponent kv(List<Row> rows) {
        NekkoraKeyValueView v = new NekkoraKeyValueView();
        for (Row r : rows) v.add(r.key(), r.value());
        return v;
    }

    public Card card() {
        return card;
    }

    // ---- acesso para testes de interface e homologação (componentes reais)

    public NekkoraButton continueButton() {
        return continueButton;
    }

    public NekkoraButton correctButton() {
        return correctButton;
    }

    public NekkoraButton pickButton() {
        return pickButton;
    }

    public NekkoraButton applyButton() {
        return applyButton;
    }

    public NekkoraButton discardButton() {
        return discardButton;
    }

    public NekkoraButton nextButton() {
        return nextButton;
    }

    public NekkoraButton previousButton() {
        return previousButton;
    }

    public NekkoraButton finishButton() {
        return finishButton;
    }

    public NekkoraButton startButton() {
        return startButton;
    }

    public NekkoraButton cancelPickButton() {
        return cancelPickButton;
    }

    public NekkoraComboBox<Tab> tabsInput() {
        return tabs;
    }

    public NekkoraTextField locatorInput() {
        return locator;
    }

    public NekkoraButton locatorButton() {
        return locatorButton;
    }

    public NekkoraStatusBadge badge() {
        return badge;
    }

    /**
     * G6: mostra (ou esconde, com {@code label} vazio) a sonda de laboratório da etapa atual. {@code keys} vazio: operação
     * sem dado (clicar, Enter).
     */
    public void setProbe(String label, List<String> keys) {
        boolean visible = label != null && !label.isEmpty();
        probeRow.setVisible(visible);
        probeButton.setText(visible ? label : "Sonda de laboratório");
        List<String> current = new ArrayList<>();
        for (int i = 0; i < probeKeys.getItemCount(); i++) current.add(probeKeys.getItemAt(i));
        if (!current.equals(keys)) {
            probeKeys.removeAllItems();
            keys.forEach(probeKeys::addItem);
        }
        probeKeys.setVisible(!keys.isEmpty());
        revalidate();
    }

    public NekkoraButton probeButton() {
        return probeButton;
    }

    public NekkoraComboBox<String> probeKeysInput() {
        return probeKeys;
    }
}
