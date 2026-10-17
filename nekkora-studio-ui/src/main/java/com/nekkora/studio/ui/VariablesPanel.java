package com.nekkora.studio.ui;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.variable.VariableDefinition;
import com.nekkora.studio.domain.variable.VariableId;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.validation.DataFlowAnalysis;
import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.button.NekkoraIconButton;
import com.nekkora.ui.container.Background;
import com.nekkora.ui.container.NekkoraPanel;
import com.nekkora.ui.container.NekkoraScrollPane;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.data.NekkoraList;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.typography.NekkoraText;
import com.nekkora.ui.typography.TextTone;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/**
 * Painel "Variáveis e dependências" (STUDIO-008). Lista as variáveis DECLARADAS da automação ativa, com o uso derivado
 * (etapas que as referenciam pelo nome exato) e a origem declarada. Não edita nada sozinho: criar, editar, renomear,
 * excluir e ir até uma etapa são pedidos ao Studio ({@link Actions}), que passam pelo histórico e pelo bloqueio de
 * gravação como qualquer edição.
 *
 * <p>Nunca mostra um valor de execução (o Studio não tem nenhum) nem o valor fixo nas linhas da lista. Mesmo layout
 * compacto do painel de validação: linhas únicas com reticências (texto completo na dica e no nome acessível).
 */
public final class VariablesPanel extends NekkoraPanel {

    /** Pedidos do usuário. */
    public interface Actions {
        void create();

        void edit(VariableId variable);

        void rename(VariableId variable);

        void delete(VariableId variable);

        /** Selecionar a etapa no Studio. */
        void goToStep(StepId step);

        void close();
    }

    /**
     * Linha apresentável (montada do estado ATUAL).
     *
     * @param consumers etapas que usam a variável (ordem das etapas)
     */
    public record Row(VariableDefinition variable, String title, String summary, String origin, String scope, String constraints, String semantic,
                      List<StepId> consumers, List<String> consumerPlaces) {}

    public static final String TITLE = "Variáveis e dependências";
    public static final String NOTICE = "Declarações do processo. O Studio não guarda valores de execução e não executa nada; a origem de saída "
            + "de etapa é declarada, não comprovada.";

    private final Actions actions;
    private final NekkoraStack head = NekkoraStack.vertical(Spacing.SPACE_3);
    private final NekkoraStack foot = NekkoraStack.vertical(Spacing.SPACE_2);
    private final NekkoraText counts = new NekkoraText("");
    private final NekkoraList<Row> list = new NekkoraList<>();
    private final NekkoraText empty = new NekkoraText("Nenhuma variável declarada. Use Nova variável para declarar a primeira.", TextTone.MUTED);
    private final NekkoraText originLine = new NekkoraText("", TextTone.SECONDARY);
    private final NekkoraText declarationLine = new NekkoraText("", TextTone.SECONDARY);
    private final NekkoraText usageLine = new NekkoraText("", TextTone.SECONDARY);
    private final NekkoraButton create = NekkoraButton.secondary("Nova variável").withIcon(IconName.PLUS).withSize(ControlSize.SMALL);
    private final NekkoraButton edit = NekkoraButton.secondary("Editar").withIcon(IconName.EDIT).withSize(ControlSize.SMALL);
    private final NekkoraButton rename = NekkoraButton.secondary("Renomear").withSize(ControlSize.SMALL);
    private final NekkoraButton delete = NekkoraButton.secondary("Excluir").withIcon(IconName.TRASH).withSize(ControlSize.SMALL);
    private final NekkoraButton goTo = NekkoraButton.secondary("Ir para uso").withIcon(IconName.ARROW_RIGHT).withSize(ControlSize.SMALL);
    private List<Row> rows = List.of();
    private boolean editable;

    public VariablesPanel(Actions actions) {
        super(new BorderLayout());
        this.actions = Objects.requireNonNull(actions);
        setSurface(Background.SURFACE);
        setPadding(Spacing.SPACE_5, Spacing.SPACE_6);
        getAccessibleContext().setAccessibleName(TITLE);

        NekkoraStack titleRow = NekkoraStack.horizontal(Spacing.SPACE_4).align(NekkoraStack.Align.CENTER);
        titleRow.add(new NekkoraText(TITLE, NekkoraText.Size.LARGE, TextTone.PRIMARY));
        titleRow.addGlue();
        create.onClick(e -> actions.create());
        create.getAccessibleContext().setAccessibleDescription("Declara uma variável nesta automação.");
        titleRow.add(create);
        NekkoraIconButton close = new NekkoraIconButton(IconName.CLOSE, "Fechar variáveis (Esc)");
        close.getAccessibleContext().setAccessibleName("Fechar variáveis");
        close.addActionListener(e -> actions.close());
        titleRow.add(close);
        head.add(titleRow);
        NekkoraText notice = new NekkoraText(NOTICE, TextTone.SECONDARY);
        notice.setToolTipText(NOTICE);
        notice.getAccessibleContext().setAccessibleDescription(NOTICE);
        head.add(notice);
        counts.getAccessibleContext().setAccessibleName("Totais das variáveis");
        head.add(counts);
        add(head, BorderLayout.NORTH);

        list.withLabel(Row::title).withSecondary(Row::summary, true)
                .withIcon(r -> r.consumers().isEmpty() ? IconName.INFO : r.variable().origin().kind() == VariableOrigin.Kind.UNBOUND ? IconName.ALERT
                        : IconName.CODE);
        list.getAccessibleContext().setAccessibleName("Variáveis declaradas");
        list.onItemActivated(r -> {
            if (editable) actions.edit(r.variable().id());
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) refreshSelection();
        });
        NekkoraPanel middle = new NekkoraPanel(new BorderLayout());
        middle.add(new NekkoraScrollPane(list), BorderLayout.CENTER);
        empty.setVisible(false);
        middle.add(empty, BorderLayout.NORTH);
        add(middle, BorderLayout.CENTER);

        originLine.getAccessibleContext().setAccessibleName("Origem declarada");
        declarationLine.getAccessibleContext().setAccessibleName("Declaração");
        usageLine.getAccessibleContext().setAccessibleName("Usada por");
        foot.add(originLine);
        foot.add(declarationLine);
        foot.add(usageLine);
        NekkoraStack buttons = NekkoraStack.horizontal(Spacing.SPACE_4);
        edit.onClick(e -> selected().ifPresent(r -> actions.edit(r.variable().id())));
        rename.onClick(e -> selected().ifPresent(r -> actions.rename(r.variable().id())));
        delete.onClick(e -> selected().ifPresent(r -> actions.delete(r.variable().id())));
        goTo.onClick(e -> selected().filter(r -> !r.consumers().isEmpty()).ifPresent(r -> actions.goToStep(r.consumers().get(0))));
        rename.getAccessibleContext().setAccessibleDescription("Renomeia a variável e as referências das etapas que a usam, numa só operação.");
        delete.getAccessibleContext().setAccessibleDescription("Exclui a declaração; se estiver em uso, pede confirmação antes de desligar as etapas.");
        goTo.getAccessibleContext().setAccessibleDescription("Seleciona a primeira etapa que usa a variável.");
        buttons.add(edit);
        buttons.add(rename);
        buttons.add(delete);
        buttons.add(goTo);
        buttons.addGlue();
        foot.add(buttons);
        add(foot, BorderLayout.SOUTH);

        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "variables.close");
        getActionMap().put("variables.close", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                actions.close();
            }
        });
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "variables.rename");
        getActionMap().put("variables.rename", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (rename.isEnabled()) rename.doClick();
            }
        });
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "variables.delete");
        getActionMap().put("variables.delete", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (delete.isEnabled()) delete.doClick();
            }
        });
        refreshSelection();
    }

    /** Altura mínima: cabeçalho + rodapé + uma linha — nunca se sobrepõem. */
    @Override
    public Dimension getMinimumSize() {
        Insets in = getInsets();
        int h = head.getPreferredSize().height + foot.getPreferredSize().height + Scale.px(56) + in.top + in.bottom;
        return new Dimension(Scale.px(200), h);
    }

    /** Linhas do estado atual (índices montados uma vez: linear no tamanho da automação). */
    public static List<Row> rows(Automation a) {
        DataFlowAnalysis flow = DataFlowAnalysis.of(a);
        Map<StepId, String> places = VariablePresentation.places(a);
        List<Row> out = new ArrayList<>(a.variables().size());
        for (VariableDefinition v : a.variables()) {
            List<StepId> consumers = flow.consumersOf(v.id()).stream().map(DataFlowAnalysis.Consumption::step).toList();
            out.add(new Row(v, VariablePresentation.title(v), VariablePresentation.summary(a, v, flow), VariablePresentation.origin(v, flow, places),
                    VariablePresentation.scope(a, v), VariablePresentation.constraints(v), VariablePresentation.semantic(v), consumers,
                    consumers.stream().map(places::get).toList()));
        }
        return out;
    }

    /**
     * Mostra as variáveis da automação ativa.
     *
     * @param editable a automação pode ser editada agora (não está recebendo uma gravação)
     */
    public void show(Automation a, boolean editable) {
        VariableId keep = selected().map(r -> r.variable().id()).orElse(null);
        this.editable = editable;
        this.rows = rows(a);
        long unused = rows.stream().filter(r -> r.consumers().isEmpty()).count();
        String line = rows.size() == 1 ? "1 variável" : rows.size() + " variáveis";
        counts.setText(line + (rows.isEmpty() ? "" : " · " + (rows.size() - unused) + " em uso · " + unused + " sem uso") + " · " + a.name());
        counts.setToolTipText(counts.getText());
        list.setItems(rows);
        empty.setVisible(rows.isEmpty());
        create.setEnabled(editable);
        int index = 0;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).variable().id().equals(keep)) index = i;
        if (!rows.isEmpty()) list.setSelectedIndex(index);
        refreshSelection();
        revalidate();
        repaint();
    }

    /** Nenhuma automação ativa. */
    public void showNothing() {
        rows = List.of();
        list.setItems(List.of());
        counts.setText("Nenhuma automação ativa");
        empty.setVisible(false);
        create.setEnabled(false);
        refreshSelection();
    }

    /** Seleciona a variável (por exemplo, ao chegar de um apontamento da validação). */
    public void select(VariableId id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).variable().id().equals(id)) {
                list.setSelectedIndex(i);
                list.ensureIndexIsVisible(i);
            }
        }
    }

    private Optional<Row> selected() {
        return Optional.ofNullable(list.getSelectedValue());
    }

    private void refreshSelection() {
        Row r = list.getSelectedValue();
        set(originLine, r == null ? "" : "Origem: " + r.origin());
        set(declarationLine, r == null ? "" : "Significado: " + r.semantic() + " · Escopo: " + r.scope() + " · Sensibilidade: "
                + r.variable().sensitivity().label() + " · Restrições: " + r.constraints());
        String usage = "";
        if (r != null) {
            usage = r.consumerPlaces().isEmpty() ? "Usada por: nenhuma etapa"
                    : "Usada por: " + String.join("; ", r.consumerPlaces().subList(0, Math.min(3, r.consumerPlaces().size())))
                            + (r.consumerPlaces().size() > 3 ? " e mais " + (r.consumerPlaces().size() - 3) : "");
        }
        set(usageLine, usage);
        edit.setEnabled(r != null && editable);
        rename.setEnabled(r != null && editable);
        delete.setEnabled(r != null && editable);
        goTo.setEnabled(r != null && !r.consumers().isEmpty());
    }

    private static void set(NekkoraText t, String text) {
        t.setText(text);
        t.setToolTipText(text.isEmpty() ? null : text);
        t.getAccessibleContext().setAccessibleDescription(text);
    }

    // ------------------------------------------------------------------ acesso para testes/evidências

    public NekkoraList<Row> variableList() {
        return list;
    }

    public List<Row> rows() {
        return rows;
    }

    public NekkoraText counts() {
        return counts;
    }

    public NekkoraText originLine() {
        return originLine;
    }

    public NekkoraText declarationLine() {
        return declarationLine;
    }

    public NekkoraText usageLine() {
        return usageLine;
    }

    public NekkoraButton createButton() {
        return create;
    }

    public NekkoraButton editButton() {
        return edit;
    }

    public NekkoraButton renameButton() {
        return rename;
    }

    public NekkoraButton deleteButton() {
        return delete;
    }

    public NekkoraButton goToButton() {
        return goTo;
    }
}
