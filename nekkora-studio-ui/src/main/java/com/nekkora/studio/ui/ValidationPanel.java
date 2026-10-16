package com.nekkora.studio.ui;

import com.nekkora.studio.validation.Finding;
import com.nekkora.studio.validation.FindingField;
import com.nekkora.studio.validation.FindingScope;
import com.nekkora.studio.validation.Severity;
import com.nekkora.studio.validation.ValidationReport;
import com.nekkora.studio.validation.ValidationStatus;
import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.button.NekkoraIconButton;
import com.nekkora.ui.container.Background;
import com.nekkora.ui.container.NekkoraPanel;
import com.nekkora.ui.container.NekkoraScrollPane;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.data.NekkoraList;
import com.nekkora.ui.feedback.NekkoraInlineAlert;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.input.NekkoraComboBox;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.typography.NekkoraText;
import com.nekkora.ui.typography.TextTone;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/**
 * Painel "Validação da automação" (STUDIO-007). Apresenta um {@link ValidationReport} sem editá-lo e sem editar a
 * automação: navegar até o apontamento e abrir o editor de etapa EXISTENTE são pedidos ao Studio ({@link Actions}).
 *
 * <p>Nunca apresenta um resultado antigo como atual: quando a automação muda depois da validação, o painel mostra
 * "Resultado desatualizado" em destaque (título do estado, aviso e descrição acessível) até o usuário validar de novo. A
 * severidade nunca depende só da cor: cada linha começa com símbolo e nome (× Bloqueio, ! Aviso, i Informação).
 *
 * <p>Layout compacto para a coluna central em janelas estreitas ou baixas: linhas únicas com reticências (texto completo
 * na dica e no nome acessível) e altura mínima que nunca sobrepõe cabeçalho e rodapé.
 */
public final class ValidationPanel extends NekkoraPanel {

    /** Pedidos do usuário. */
    public interface Actions {
        /** Selecionar o lugar do apontamento (automação, sessão ou etapa). */
        void goTo(Finding finding);

        /** Abrir o formulário de edição de etapa já existente. */
        void edit(Finding finding);

        void revalidate();

        void close();
    }

    /**
     * Linha apresentável. O lugar ("Sessão 1 · Etapa 05 — Preencher pesquisa") vem do estado ATUAL da automação; o
     * relatório em si não guarda nomes.
     *
     * @param editable a etapa existe e pode ser editada agora (não está recebendo uma gravação)
     */
    public record Item(Finding finding, String place, boolean editable) {}

    /** Filtro por severidade. */
    public enum Filter {
        ALL("Todos os apontamentos", null), BLOCKERS("Bloqueios", Severity.BLOCKER), WARNINGS("Avisos", Severity.WARNING),
        INFOS("Informações", Severity.INFO);

        private final String label;
        private final Severity severity;

        Filter(String label, Severity severity) {
            this.label = label;
            this.severity = severity;
        }

        public String label() {
            return label;
        }

        boolean accepts(Item i) {
            return severity == null || i.finding().severity() == severity;
        }
    }

    /** Campos em que o formulário de etapa resolve o apontamento (tipo, parâmetros, localizadores propostos). */
    private static final Set<FindingField> EDITABLE_FIELDS = Set.of(FindingField.ACTION_TYPE, FindingField.PARAMETERS, FindingField.LOCATORS);

    public static final String TITLE = "Validação da automação";
    public static final String NO_EXECUTION = "Esta análise não executou nenhuma etapa e não consultou o navegador.";
    public static final String STALE_TITLE = "Resultado desatualizado";
    public static final String STALE_MESSAGE = "A automação mudou depois desta validação. Valide novamente para ver o diagnóstico atual.";

    private final Actions actions;
    private final NekkoraStack head = NekkoraStack.vertical(Spacing.SPACE_3);
    private final NekkoraStack foot = NekkoraStack.vertical(Spacing.SPACE_3);
    private final NekkoraInlineAlert status = new NekkoraInlineAlert(com.nekkora.ui.core.Severity.INFO, "", "");
    private final NekkoraInlineAlert stale = new NekkoraInlineAlert(com.nekkora.ui.core.Severity.WARNING, STALE_TITLE, STALE_MESSAGE);
    private final NekkoraText counts = new NekkoraText("");
    private final NekkoraComboBox<Filter> filter = new NekkoraComboBox<>(Filter.values());
    private final NekkoraList<Item> list = new NekkoraList<>();
    private final NekkoraText empty = new NekkoraText("Nenhum apontamento nesta seleção.", TextTone.MUTED);
    private final NekkoraText guidance = new NekkoraText("", TextTone.SECONDARY);
    private final NekkoraButton goTo = NekkoraButton.secondary("Ir para etapa").withIcon(IconName.ARROW_RIGHT).withSize(ControlSize.SMALL);
    private final NekkoraButton edit = NekkoraButton.secondary("Editar etapa").withIcon(IconName.EDIT).withSize(ControlSize.SMALL);
    private final NekkoraIconButton again = new NekkoraIconButton(IconName.REFRESH, "Validar novamente (Ctrl+Shift+V)");
    private String subject = "";
    private List<Item> items = List.of();
    private ValidationReport report;
    private boolean current;

    public ValidationPanel(Actions actions) {
        super(new BorderLayout());
        this.actions = Objects.requireNonNull(actions);
        setSurface(Background.SURFACE);
        setPadding(Spacing.SPACE_5, Spacing.SPACE_6);
        getAccessibleContext().setAccessibleName(TITLE);

        NekkoraStack titleRow = NekkoraStack.horizontal(Spacing.SPACE_4).align(NekkoraStack.Align.CENTER);
        titleRow.add(new NekkoraText(TITLE, NekkoraText.Size.LARGE, TextTone.PRIMARY));
        titleRow.addGlue();
        again.getAccessibleContext().setAccessibleName("Validar novamente");
        again.addActionListener(e -> actions.revalidate());
        titleRow.add(again);
        NekkoraIconButton close = new NekkoraIconButton(IconName.CLOSE, "Fechar a validação (Esc)");
        close.getAccessibleContext().setAccessibleName("Fechar a validação");
        close.addActionListener(e -> actions.close());
        titleRow.add(close);
        head.add(titleRow);
        head.add(status); // estado + explicação + "não executou nenhuma etapa" (sempre visível)
        stale.addAction("Validar novamente", actions::revalidate);
        stale.setVisible(false);
        head.add(stale);
        counts.getAccessibleContext().setAccessibleName("Totais da validação");
        head.add(counts);
        // filtro em linha própria: na coluna central estreita ele nunca é empurrado para fora
        NekkoraStack filterRow = NekkoraStack.horizontal(Spacing.SPACE_4).align(NekkoraStack.Align.CENTER);
        filterRow.add(new NekkoraText("Mostrar:"));
        filter.setLabelProvider(f -> f == null ? "" : f.label());
        filter.getAccessibleContext().setAccessibleName("Mostrar apontamentos");
        filter.addActionListener(e -> applyFilter());
        filterRow.add(filter);
        filterRow.addGlue();
        head.add(filterRow);
        add(head, BorderLayout.NORTH);

        list.withLabel(i -> i.finding().severity().symbol() + "  " + i.finding().severity().label() + " · " + i.place())
                .withSecondary(i -> i.finding().explanation(), true)
                .withIcon(i -> switch (i.finding().severity()) {
                    case BLOCKER -> IconName.X_CIRCLE;
                    case WARNING -> IconName.ALERT;
                    case INFO -> IconName.INFO;
                });
        list.getAccessibleContext().setAccessibleName("Apontamentos da validação");
        list.onItemActivated(i -> actions.goTo(i.finding()));
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) refreshSelection();
        });
        NekkoraPanel middle = new NekkoraPanel(new BorderLayout());
        middle.add(new NekkoraScrollPane(list), BorderLayout.CENTER);
        empty.setVisible(false);
        middle.add(empty, BorderLayout.NORTH);
        add(middle, BorderLayout.CENTER);

        guidance.getAccessibleContext().setAccessibleName("Como corrigir");
        foot.add(guidance);
        NekkoraStack buttons = NekkoraStack.horizontal(Spacing.SPACE_4);
        goTo.onClick(e -> selected().ifPresent(i -> actions.goTo(i.finding())));
        edit.onClick(e -> selected().ifPresent(i -> actions.edit(i.finding())));
        goTo.getAccessibleContext().setAccessibleDescription("Seleciona no Studio o lugar do apontamento.");
        edit.getAccessibleContext().setAccessibleDescription("Abre o editor da etapa para corrigir o apontamento.");
        buttons.add(goTo);
        buttons.add(edit);
        buttons.addGlue();
        foot.add(buttons);
        add(foot, BorderLayout.SOUTH);

        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "validation.close");
        getActionMap().put("validation.close", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                actions.close();
            }
        });
        refreshSelection();
    }

    /** Altura mínima: cabeçalho + rodapé + uma linha da lista — cabeçalho e rodapé nunca se sobrepõem. */
    @Override
    public Dimension getMinimumSize() {
        Insets in = getInsets();
        int h = head.getPreferredSize().height + foot.getPreferredSize().height + Scale.px(56) + in.top + in.bottom;
        return new Dimension(Scale.px(200), h);
    }

    /**
     * Mostra um relatório.
     *
     * @param automationName nome ATUAL da automação
     * @param items          linhas, na ordem do relatório
     * @param current        o relatório corresponde ao estado atual (senão: desatualizado)
     */
    public void show(String automationName, ValidationReport report, List<Item> items, boolean current) {
        Finding keep = selected().map(Item::finding).orElse(null);
        this.report = Objects.requireNonNull(report);
        this.items = List.copyOf(items);
        this.subject = automationName == null ? "" : automationName;
        status.setSeverity(switch (report.status()) {
            case INCOMPLETE -> com.nekkora.ui.core.Severity.ERROR;
            case NOT_FULLY_ASSESSABLE -> com.nekkora.ui.core.Severity.WARNING;
            case NO_STATIC_BLOCKERS -> com.nekkora.ui.core.Severity.INFO; // nunca "sucesso": não é pronta para executar
        });
        ValidationReport.Summary s = report.summary();
        String line = Severity.BLOCKER.symbol() + " " + Severity.BLOCKER.count(s.blockers()) + "   " + Severity.WARNING.symbol() + " "
                + Severity.WARNING.count(s.warnings()) + "   " + Severity.INFO.symbol() + " " + Severity.INFO.count(s.infos());
        String full = line + " · " + StudioText.steps(s.steps()) + " " + (s.steps() == 1 ? "analisada" : "analisadas") + ", "
                + s.stepsWithoutFindings() + " sem apontamentos · regras " + report.rulesVersion();
        counts.setText(line);
        counts.setToolTipText(full);
        counts.getAccessibleContext().setAccessibleDescription(full);
        setCurrent(current);
        applyFilter();
        if (keep != null) {
            for (int i = 0; i < list.getModel().getSize(); i++) {
                if (list.getModel().getElementAt(i).finding().id().equals(keep.id())) {
                    list.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    /** Automação ativa ainda sem validação (ou nenhuma automação ativa): nada de relatório antigo de outra automação. */
    public void showNotValidated(String automationName) {
        this.report = null;
        this.items = List.of();
        this.subject = automationName == null ? "" : automationName;
        status.setSeverity(com.nekkora.ui.core.Severity.INFO);
        status.setTitle(subject.isEmpty() ? "Nenhuma automação ativa" : "Automação ainda não validada");
        status.setMessage((subject.isEmpty() ? "" : subject + ": ") + "use Validar automação (Ctrl+Shift+V) para o diagnóstico estrutural do estado atual.");
        stale.setVisible(false);
        counts.setText("");
        counts.setToolTipText(null);
        current = false;
        getAccessibleContext().setAccessibleDescription("Sem validação");
        list.setItems(List.of());
        empty.setVisible(true);
        refreshSelection();
        revalidate();
        repaint();
    }

    /** O relatório mostrado deixou (ou voltou a ser) o diagnóstico do estado atual. */
    public void setCurrent(boolean current) {
        this.current = current;
        if (report == null) return;
        ValidationStatus st = report.status();
        status.setTitle(current ? "Estado: " + st.label() : STALE_TITLE + " — último estado: " + st.label());
        status.setMessage((subject.isEmpty() ? "" : subject + ": ") + (current ? st.explanation() : STALE_MESSAGE) + " " + NO_EXECUTION);
        stale.setVisible(!current);
        getAccessibleContext().setAccessibleDescription(current ? st.label() : STALE_TITLE);
        revalidate();
        repaint();
    }

    public boolean isCurrent() {
        return current;
    }

    private void applyFilter() {
        Filter f = filter.getSelectedValue() == null ? Filter.ALL : filter.getSelectedValue();
        List<Item> shown = items.stream().filter(f::accepts).toList();
        list.setItems(shown);
        empty.setVisible(shown.isEmpty());
        if (!shown.isEmpty() && list.getSelectedIndex() < 0) list.setSelectedIndex(0);
        refreshSelection();
    }

    private Optional<Item> selected() {
        return Optional.ofNullable(list.getSelectedValue());
    }

    private void refreshSelection() {
        Item i = list.getSelectedValue();
        String how = i == null ? "" : "Como corrigir: " + i.finding().guidance();
        guidance.setText(how);
        guidance.setToolTipText(how.isEmpty() ? null : how);
        guidance.getAccessibleContext().setAccessibleDescription(how);
        goTo.setEnabled(i != null);
        goTo.setText(i == null || i.finding().scope() == FindingScope.STEP ? "Ir para etapa" : switch (i.finding().scope()) {
            case SESSION -> "Ir para sessão";
            case VARIABLE -> "Ir para variável";
            default -> "Ir para automação";
        });
        edit.setEnabled(i != null && i.editable() && i.finding().scope() == FindingScope.STEP && EDITABLE_FIELDS.contains(i.finding().field()));
    }

    // ------------------------------------------------------------------ acesso para testes/evidências

    public NekkoraList<Item> findingList() {
        return list;
    }

    public NekkoraComboBox<Filter> filter() {
        return filter;
    }

    public NekkoraInlineAlert statusAlert() {
        return status;
    }

    public NekkoraInlineAlert staleAlert() {
        return stale;
    }

    public NekkoraText counts() {
        return counts;
    }

    public NekkoraText guidance() {
        return guidance;
    }

    public NekkoraButton goToButton() {
        return goTo;
    }

    public NekkoraButton editButton() {
        return edit;
    }

    public NekkoraIconButton revalidateButton() {
        return again;
    }

    public Optional<ValidationReport> report() {
        return Optional.ofNullable(report);
    }
}
