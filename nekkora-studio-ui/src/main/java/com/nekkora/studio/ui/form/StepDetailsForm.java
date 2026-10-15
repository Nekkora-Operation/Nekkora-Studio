package com.nekkora.studio.ui.form;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.CapturedBaseline;
import com.nekkora.studio.domain.DomainValidationException;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepComment;
import com.nekkora.studio.domain.StepEdit;
import com.nekkora.studio.domain.StepField;
import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.OptionChoice;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.ManualLocator;
import com.nekkora.studio.domain.origin.StepOrigin;
import com.nekkora.studio.ui.ActionTypeStyle;
import com.nekkora.studio.ui.ElementPresentation;
import com.nekkora.studio.ui.StepPresentation;
import com.nekkora.studio.ui.StudioText;
import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.container.NekkoraSection;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.data.NekkoraBadge;
import com.nekkora.ui.data.NekkoraKeyValueView;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.input.NekkoraComboBox;
import com.nekkora.ui.input.NekkoraFormField;
import com.nekkora.ui.input.NekkoraRadioButton;
import com.nekkora.ui.input.NekkoraTextArea;
import com.nekkora.ui.input.NekkoraTextField;
import com.nekkora.ui.navigation.NekkoraTabs;
import com.nekkora.ui.typography.NekkoraParagraph;
import com.nekkora.ui.typography.NekkoraText;
import com.nekkora.ui.typography.TextTone;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;

/**
 * Detalhes e edição completa de uma etapa — a visualização expandida da STUDIO-001 evoluída para o editor da STUDIO-004: Geral, Parâmetros, Elemento, Comentários e Origem e alterações. Tudo o que
 * o usuário muda fica no formulário até "Salvar alterações", que aplica UMA edição validada pelo domínio — "Cancelar"
 * descarta tudo. O formulário não decide regras: as mensagens de validação vêm do domínio e aparecem no campo certo.
 * Nenhum identificador interno é exibido; o valor capturado de uma etapa gravada aparece ao lado do atual.
 */
public final class StepDetailsForm extends StudioForm<StepDetailsForm.Values> {

    /** Origem do valor configurada na aba Parâmetros. */
    public enum ValueMode {
        NONE("Nenhum"), LITERAL("Valor literal"), VARIABLE("Variável"), CREDENTIAL("Credencial");

        private final String label;

        ValueMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Localizador proposto: um já existente (mantém a data) ou um novo (a data é a da confirmação). */
    public record ManualLocatorItem(ManualLocator existing, ManualLocator.Syntax syntax, String expression, String note) {
        static ManualLocatorItem of(ManualLocator m) {
            return new ManualLocatorItem(m, m.syntax(), m.expression(), m.note());
        }
    }

    /** Comentário na edição (posição original ou -1 para novo), com as datas apenas para exibição. */
    public record CommentItem(int original, String text, Instant createdAt, Instant editedAt) {}

    /** Valores do editor (sem normalização — o domínio normaliza e valida). */
    public record Values(String name, String description, ActionType actionType, SessionId session, ValueMode valueMode, String valueText,
                         String optionLabel, String optionValue, int preferredLocator, List<ManualLocatorItem> manualLocators,
                         List<CommentItem> comments) {

        /** Parâmetros pedidos no formulário, independentemente do tipo (o domínio confere a compatibilidade). */
        public ActionParameters parameters() {
            switch (valueMode) {
                case LITERAL -> {
                    return ActionParameters.withValue(new ValueSource.LiteralValue(valueText == null ? "" : valueText));
                }
                case VARIABLE -> {
                    return ActionParameters.withValue(new ValueSource.VariableReference(valueText == null ? "" : valueText.strip()));
                }
                case CREDENTIAL -> {
                    return ActionParameters.withValue(new ValueSource.CredentialReference(valueText == null ? "" : valueText.strip()));
                }
                default -> {
                    String label = optionLabel == null ? "" : optionLabel.strip();
                    return label.isEmpty() ? ActionParameters.none() : ActionParameters.withOption(new OptionChoice(label, optionValue == null ? "" : optionValue.strip()));
                }
            }
        }

        /** Os parâmetros pedidos não cabem no tipo escolhido (o usuário precisa decidir removê-los). */
        public boolean parametersIncompatible() {
            ActionParameters p = parameters();
            return p.value().isPresent() && !actionType.acceptsValue() || p.option().isPresent() && !actionType.acceptsOption();
        }

        /** Edição de domínio correspondente; {@code dropIncompatible} remove os parâmetros que o tipo não aceita. */
        public StepEdit toEdit(Automation automation, AutomationStep step, Instant now, boolean dropIncompatible) {
            ActionParameters p = parameters();
            if (dropIncompatible && parametersIncompatible()) p = ActionParameters.none();
            List<ManualLocator> locators = new ArrayList<>();
            for (ManualLocatorItem m : manualLocators) {
                locators.add(m.existing() != null ? m.existing() : new ManualLocator(m.syntax(), m.expression(), m.note(), now));
            }
            List<StepEdit.CommentEdit> c = new ArrayList<>();
            for (CommentItem i : comments) c.add(new StepEdit.CommentEdit(i.original(), i.text()));
            return StepEdit.of(automation, step).name(name).description(description).actionType(actionType).session(session).parameters(p)
                    .preferredLocator(preferredLocator).manualLocators(locators).comments(c);
        }
    }

    private final AutomationStep step;
    private final com.nekkora.studio.domain.StepId stepId;
    private final NekkoraTabs tabs = new NekkoraTabs();
    // geral: nome, tipo e descrição vêm do formulário básico de etapa (o mesmo da criação)
    private final StepForm basics;
    private final NekkoraTextField name;
    private final NekkoraComboBox<ActionType> type;
    private final NekkoraTextArea description;
    private final NekkoraComboBox<StepForm.SessionChoice> session;
    private final NekkoraFormField sessionField;
    private final List<ElementPresentation.Row> overviewRows;
    // parâmetros
    private final NekkoraStack parametersBody = NekkoraStack.vertical(Spacing.SPACE_5);
    private final List<NekkoraRadioButton> modes = new ArrayList<>();
    private final NekkoraTextField valueText = new NekkoraTextField("Ex.: cliente.usuario");
    private final NekkoraFormField valueField;
    private final NekkoraTextField optionLabel = new NekkoraTextField("Ex.: São Paulo");
    private final NekkoraFormField optionLabelField;
    private final NekkoraTextField optionValue = new NekkoraTextField("Ex.: SP");
    private final NekkoraFormField optionValueField;
    private final NekkoraParagraph valueHelp = new NekkoraParagraph("", TextTone.SECONDARY);
    // elemento
    private final List<NekkoraRadioButton> preferred = new ArrayList<>();
    private final NekkoraRadioButton noPreferred = new NekkoraRadioButton("Nenhum preferencial");
    private final List<ManualLocatorItem> manualLocators = new ArrayList<>();
    private final NekkoraStack manualList = NekkoraStack.vertical(Spacing.SPACE_3);
    private final NekkoraComboBox<ManualLocator.Syntax> manualSyntax = new NekkoraComboBox<>(List.of(ManualLocator.Syntax.values()));
    private final NekkoraTextField manualExpression = new NekkoraTextField("Ex.: form#login button[type=submit]");
    private final NekkoraTextField manualNote = new NekkoraTextField("Por que esta alternativa (opcional)");
    private NekkoraFormField locatorField;
    // comentários
    private final List<CommentItem> comments = new ArrayList<>();
    private final NekkoraStack commentList = NekkoraStack.vertical(Spacing.SPACE_4);
    private final NekkoraTextArea commentInput = FormKeys.tabMovesFocus(new NekkoraTextArea("Escreva uma observação sobre a etapa.", 3));
    private final NekkoraFormField commentField;
    private final NekkoraButton commentButton = NekkoraButton.secondary("Adicionar comentário").withIcon(IconName.PLUS).withSize(ControlSize.SMALL);
    private int editingComment = -1;
    // origem
    private final NekkoraStack originBody = NekkoraStack.vertical(Spacing.SPACE_5);

    public StepDetailsForm(Automation automation, AutomationStep step) {
        this.step = step;
        this.stepId = step.id();
        // ------------------------------------------------ Geral
        basics = StepForm.forEdit(step);
        name = basics.nameInput();
        type = basics.typeInput();
        description = basics.descriptionInput();
        List<StepForm.SessionChoice> choices = new ArrayList<>();
        StepForm.SessionChoice current = null;
        SessionId currentSession = automation.sessionOf(step.id()).orElseThrow().id();
        int n = 0;
        for (LogicalSession s : automation.sessions()) {
            StepForm.SessionChoice c = new StepForm.SessionChoice(s.id(), (++n) + " · " + s.name());
            choices.add(c);
            if (s.id().equals(currentSession)) current = c;
        }
        session = new NekkoraComboBox<>(choices);
        session.setLabelProvider(StepForm.SessionChoice::label);
        session.setSelectedItem(current);
        sessionField = new NekkoraFormField("Sessão", session).required().helper("Escolher outra sessão leva a etapa para o final dela.");
        NekkoraStack general = NekkoraStack.vertical(Spacing.SPACE_6);
        general.add(basics);
        general.add(sessionField);
        overviewRows = StepPresentation.overview(automation, step);
        NekkoraKeyValueView facts = new NekkoraKeyValueView();
        for (ElementPresentation.Row r : overviewRows) facts.add(r.label(), r.value());
        facts.add("Alterada manualmente", step.baseline().isEmpty() ? "Não se aplica (etapa manual)" : step.manualRevision().isPresent() ? "Sim" : "Não");
        general.add(new NekkoraSection("Resumo").setContent(facts));
        tabs.addTab("Geral", IconName.EDIT, general);

        // ------------------------------------------------ Parâmetros
        ButtonGroup modeGroup = new ButtonGroup();
        for (ValueMode m : ValueMode.values()) {
            NekkoraRadioButton r = new NekkoraRadioButton(m.label());
            r.getAccessibleContext().setAccessibleName("Origem do valor: " + m.label());
            modeGroup.add(r);
            modes.add(r);
            r.addActionListener(e -> refreshValueHelp());
        }
        boolean sensitive = step.element().map(ElementReference::sensitive).orElse(false);
        if (sensitive) {
            modes.get(ValueMode.LITERAL.ordinal()).setEnabled(false);
            modes.get(ValueMode.LITERAL.ordinal()).setToolTipText("Campo sensível: use uma variável ou uma credencial.");
        }
        valueField = new NekkoraFormField("Referência ou valor", valueText).optional();
        optionLabelField = new NekkoraFormField("Rótulo da opção", optionLabel).optional().helper("Texto visível, como \"São Paulo\".");
        optionValueField = new NekkoraFormField("Valor técnico", optionValue).optional().helper("Valor da opção na página, como \"SP\" (pode ficar vazio).");
        type.addActionListener(e -> refreshParameters());
        tabs.addTab("Parâmetros", IconName.SLIDERS, parametersBody);

        // ------------------------------------------------ Elemento
        tabs.addTab("Elemento", IconName.CODE, elementTab(step));

        // ------------------------------------------------ Comentários
        commentField = new NekkoraFormField("Comentário", commentInput.scrollable()).optional()
                .helper("Até " + StepComment.TEXT_MAX + " caracteres. Não escreva senhas, documentos ou dados pessoais.");
        commentButton.onClick(e -> commitComment());
        NekkoraStack commentsTab = NekkoraStack.vertical(Spacing.SPACE_6);
        commentsTab.add(commentList);
        commentsTab.add(commentField);
        NekkoraStack commentActions = NekkoraStack.horizontal(Spacing.SPACE_3);
        commentActions.add(commentButton);
        commentActions.addGlue();
        commentsTab.add(commentActions);
        tabs.addTab("Comentários", IconName.MESSAGE, commentsTab);

        // ------------------------------------------------ Origem e alterações
        tabs.addTab("Origem e alterações", IconName.HISTORY, originBody);
        add(tabs);

        load(step);
    }

    private void load(AutomationStep s) {
        name.setText(s.name());
        description.setText(s.description());
        type.setSelectedItem(s.actionType());
        setParameters(s.parameters());
        int pref = s.element().map(ElementReference::preferredLocatorIndex).orElse(-1);
        if (pref >= 0 && pref < preferred.size()) preferred.get(pref).setSelected(true);
        else noPreferred.setSelected(true);
        manualLocators.clear();
        for (ManualLocator m : s.manualLocators()) manualLocators.add(ManualLocatorItem.of(m));
        renderManualLocators();
        comments.clear();
        for (int i = 0; i < s.comments().size(); i++) {
            StepComment c = s.comments().get(i);
            comments.add(new CommentItem(i, c.text(), c.createdAt(), c.editedAt()));
        }
        renderComments();
        refreshParameters();
        renderOrigin();
    }

    // ------------------------------------------------------------------ parâmetros

    private void setParameters(ActionParameters p) {
        ValueMode mode = ValueMode.NONE;
        String text = "";
        if (p.value().isPresent()) {
            ValueSource v = p.value().get();
            if (v instanceof ValueSource.LiteralValue l) {
                mode = ValueMode.LITERAL;
                text = l.text();
            } else if (v instanceof ValueSource.VariableReference r) {
                mode = ValueMode.VARIABLE;
                text = r.name();
            } else if (v instanceof ValueSource.CredentialReference c) {
                mode = ValueMode.CREDENTIAL;
                text = c.alias();
            }
        }
        modes.get(mode.ordinal()).setSelected(true);
        valueText.setText(text);
        optionLabel.setText(p.option().map(OptionChoice::label).orElse(""));
        optionValue.setText(p.option().map(OptionChoice::value).orElse(""));
        refreshValueHelp();
    }

    private void refreshParameters() {
        ActionType t = (ActionType) type.getSelectedItem();
        parametersBody.removeAll();
        if (t != null && t.acceptsValue()) {
            NekkoraStack radios = NekkoraStack.horizontal(Spacing.SPACE_5);
            radios.add(new NekkoraText("Origem:"));
            for (NekkoraRadioButton r : modes) radios.add(r);
            radios.addGlue();
            parametersBody.add(radios);
            parametersBody.add(valueField);
            parametersBody.add(valueHelp);
        }
        if (t != null && t.acceptsOption()) {
            parametersBody.add(optionLabelField);
            parametersBody.add(optionValueField);
            parametersBody.add(new NekkoraParagraph("A opção é parâmetro desta etapa: nenhuma etapa separada é criada para ela.", TextTone.SECONDARY));
        }
        if (t != null && !t.acceptsValue() && !t.acceptsOption()) {
            parametersBody.add(new NekkoraParagraph("Este tipo de ação não recebe parâmetros.", TextTone.MUTED));
            if (currentMode() != ValueMode.NONE || !optionLabel.getText().isBlank()) {
                parametersBody.add(new NekkoraParagraph("Os parâmetros atuais não servem para este tipo. Ao salvar, o Studio pergunta se deve removê-los.",
                        TextTone.SECONDARY));
            }
        }
        parametersBody.revalidate();
        parametersBody.repaint();
    }

    private ValueMode currentMode() {
        for (int i = 0; i < modes.size(); i++) if (modes.get(i).isSelected()) return ValueMode.values()[i];
        return ValueMode.NONE;
    }

    private void refreshValueHelp() {
        ValueMode m = currentMode();
        valueText.setEnabled(m != ValueMode.NONE);
        valueHelp.setText(switch (m) {
            case NONE -> "Sem valor configurado.";
            case LITERAL -> "Texto fixo e não sensível, digitado por você. Nada observado na gravação é copiado para cá.";
            case VARIABLE -> "Nome de uma variável do processo, como cliente.nome ou pedido.numero.";
            case CREDENTIAL -> "Apelido de uma credencial, como sistema.senha. O Studio guarda só o apelido: a referência ainda não está "
                    + "vinculada a um cofre de credenciais.";
        });
    }

    // ------------------------------------------------------------------ elemento

    private JComponent elementTab(AutomationStep s) {
        NekkoraStack tab = NekkoraStack.vertical(Spacing.SPACE_6);
        Optional<ElementReference> captured = s.capturedElement();
        if (captured.isEmpty()) {
            tab.add(new NekkoraText("Nenhum elemento vinculado.", TextTone.MUTED));
            return tab;
        }
        ElementReference e = captured.get();
        NekkoraKeyValueView facts = new NekkoraKeyValueView();
        for (ElementPresentation.Row r : ElementPresentation.rows(e)) if (!r.label().equals("Localizador")) facts.add(r.label(), r.value());
        tab.add(facts);
        NekkoraStack list = NekkoraStack.vertical(Spacing.SPACE_3);
        ButtonGroup group = new ButtonGroup();
        group.add(noPreferred);
        for (int i = 0; i < e.locators().size(); i++) {
            LocatorCandidate c = e.locators().get(i);
            NekkoraRadioButton r = new NekkoraRadioButton(ElementPresentation.locatorLine(c));
            r.setToolTipText(ElementPresentation.locatorLine(c)); // linhas longas: texto completo na dica
            r.setEnabled(c.validated());
            r.getAccessibleContext().setAccessibleDescription(ElementPresentation.locatorStatus(c));
            group.add(r);
            preferred.add(r);
            NekkoraStack row = NekkoraStack.vertical(Spacing.SPACE_1);
            row.add(r);
            row.add(new NekkoraParagraph(ElementPresentation.locatorStatus(c), TextTone.SECONDARY));
            list.add(row);
        }
        list.add(noPreferred);
        locatorField = new NekkoraFormField("Localizadores capturados", list)
                .helper("Só um localizador validado na captura pode ser o preferencial. Nada é recalculado pelo Studio.");
        tab.add(locatorField);
        NekkoraSection manual = new NekkoraSection("Localizadores propostos manualmente",
                "Ficam como \"Não verificado\": digitar uma expressão não comprova que ela encontra o elemento.");
        NekkoraStack manualBody = NekkoraStack.vertical(Spacing.SPACE_4);
        manualBody.add(manualList);
        manualSyntax.setLabelProvider(ManualLocator.Syntax::label);
        manualSyntax.getAccessibleContext().setAccessibleName("Linguagem da expressão");
        NekkoraStack syntaxRow = NekkoraStack.horizontal(Spacing.SPACE_3).align(NekkoraStack.Align.CENTER);
        syntaxRow.add(new NekkoraText("Linguagem:"));
        syntaxRow.add(manualSyntax);
        syntaxRow.addGlue();
        manualBody.add(syntaxRow);
        manualBody.add(new NekkoraFormField("Expressão", manualExpression).helper("CSS ou XPath, sem quebras de linha (até 500 caracteres)."));
        manualBody.add(new NekkoraFormField("Observação", manualNote).optional());
        NekkoraStack addRow = NekkoraStack.horizontal(Spacing.SPACE_3);
        addRow.add(NekkoraButton.secondary("Adicionar proposta").withIcon(IconName.PLUS).withSize(ControlSize.SMALL).onClick(ev -> addManualLocator()));
        addRow.addGlue();
        manualBody.add(addRow);
        manual.setContent(manualBody);
        tab.add(manual);
        return tab;
    }

    private void addManualLocator() {
        if (locatorField != null) locatorField.setError(null);
        try {
            ManualLocator probe = new ManualLocator((ManualLocator.Syntax) manualSyntax.getSelectedItem(), manualExpression.getText(), manualNote.getText(),
                    Instant.EPOCH); // só valida; a data real é a da confirmação
            manualLocators.add(new ManualLocatorItem(null, probe.syntax(), probe.expression(), probe.note()));
            manualExpression.setText("");
            manualNote.setText("");
            renderManualLocators();
        } catch (DomainValidationException ex) {
            if (locatorField != null) locatorField.setError(ex.problem().message());
        }
    }

    private void renderManualLocators() {
        manualList.removeAll();
        if (manualLocators.isEmpty()) manualList.add(new NekkoraText("Nenhuma proposta.", TextTone.MUTED));
        for (int i = 0; i < manualLocators.size(); i++) {
            ManualLocatorItem m = manualLocators.get(i);
            int index = i;
            NekkoraStack row = NekkoraStack.horizontal(Spacing.SPACE_3).align(NekkoraStack.Align.CENTER);
            row.add(new NekkoraBadge(ManualLocator.Verification.NOT_VERIFIED.label(), Tone.WARNING).withSize(NekkoraBadge.Size.SMALL));
            row.add(new NekkoraText(m.syntax().label() + ": " + StudioText.technical(m.expression())
                    + (m.note().isBlank() ? "" : " — " + StudioText.technical(m.note()))));
            row.addGlue();
            row.add(NekkoraButton.ghost("Remover").withIcon(IconName.TRASH).withSize(ControlSize.SMALL).onClick(e -> {
                manualLocators.remove(index);
                renderManualLocators();
            }));
            manualList.add(row);
        }
        manualList.revalidate();
        manualList.repaint();
    }

    // ------------------------------------------------------------------ comentários

    private void commitComment() {
        commentField.setError(null);
        String text;
        try {
            text = StepComment.requireText(commentInput.getText());
        } catch (DomainValidationException ex) {
            commentField.setError(ex.problem().message());
            return;
        }
        if (editingComment >= 0) {
            CommentItem old = comments.get(editingComment);
            comments.set(editingComment, new CommentItem(old.original(), text, old.createdAt(), old.editedAt()));
            editingComment = -1;
            commentButton.setText("Adicionar comentário");
        } else {
            comments.add(new CommentItem(-1, text, null, null));
        }
        commentInput.setText("");
        renderComments();
    }

    private void renderComments() {
        commentList.removeAll();
        if (comments.isEmpty()) commentList.add(new NekkoraText("Nenhum comentário.", TextTone.MUTED));
        for (int i = 0; i < comments.size(); i++) {
            CommentItem c = comments.get(i);
            int index = i;
            NekkoraStack item = NekkoraStack.vertical(Spacing.SPACE_1);
            String when = c.createdAt() == null ? "Novo (ao salvar)" : "Criado em " + StudioText.dateTime(c.createdAt())
                    + (c.editedAt() != null ? " · editado em " + StudioText.dateTime(c.editedAt()) : "");
            item.add(new NekkoraText(when, TextTone.SECONDARY));
            item.add(new NekkoraParagraph(c.text(), TextTone.PRIMARY));
            NekkoraStack actions = NekkoraStack.horizontal(Spacing.SPACE_3);
            actions.add(NekkoraButton.ghost("Editar").withIcon(IconName.EDIT).withSize(ControlSize.SMALL).onClick(e -> {
                editingComment = index;
                commentInput.setText(c.text());
                commentButton.setText("Salvar comentário");
                commentInput.requestFocusInWindow();
            }));
            actions.add(NekkoraButton.ghost("Excluir").withIcon(IconName.TRASH).withSize(ControlSize.SMALL).onClick(e -> {
                comments.remove(index);
                if (editingComment == index) {
                    editingComment = -1;
                    commentButton.setText("Adicionar comentário");
                    commentInput.setText("");
                }
                renderComments();
            }));
            actions.addGlue();
            item.add(actions);
            commentList.add(item);
        }
        commentList.revalidate();
        commentList.repaint();
    }

    // ------------------------------------------------------------------ origem

    private void renderOrigin() {
        originBody.removeAll();
        NekkoraKeyValueView facts = new NekkoraKeyValueView();
        facts.add("Origem", originLabel(step));
        Optional<CapturedBaseline> b = step.baseline();
        facts.add("Alterada manualmente", b.isEmpty() ? "Não se aplica (etapa manual)" : step.manualRevision().isPresent() ? "Sim" : "Não");
        for (ElementPresentation.Row r : StepPresentation.recording(step)) facts.add(r.label(), r.value());
        originBody.add(facts);
        if (b.isEmpty()) {
            originBody.add(new NekkoraParagraph(step.derivedFrom().isPresent()
                    ? "Etapa duplicada manualmente: não representa uma interação observada pelo Recorder."
                    : "Etapa criada manualmente: não há valores capturados para comparar.", TextTone.SECONDARY));
        } else {
            NekkoraSection changes = new NekkoraSection("Valores capturados × atuais",
                    "Restaurar coloca o valor capturado no formulário; ele só vale ao salvar.");
            NekkoraStack list = NekkoraStack.vertical(Spacing.SPACE_4);
            for (StepField f : StepField.values()) list.add(fieldRow(f, b.get()));
            changes.setContent(list);
            originBody.add(changes);
        }
        originBody.revalidate();
        originBody.repaint();
    }

    private JComponent fieldRow(StepField f, CapturedBaseline b) {
        String original = StepPresentation.capturedValue(step, f);
        String now = StepPresentation.currentValue(step, f);
        boolean modified = step.modified(f);
        NekkoraStack row = NekkoraStack.horizontal(Spacing.SPACE_4).align(NekkoraStack.Align.CENTER);
        NekkoraStack texts = NekkoraStack.vertical(Spacing.SPACE_1);
        texts.add(new NekkoraText(f.label() + (modified ? " — modificado manualmente" : " — igual ao capturado"), TextTone.PRIMARY));
        texts.add(new NekkoraParagraph("Original: " + original, TextTone.SECONDARY)); // valores longos quebram a linha
        if (modified) texts.add(new NekkoraParagraph("Atual: " + now, TextTone.SECONDARY));
        row.add(new NekkoraBadge(modified ? "Alterado" : "Capturado", modified ? Tone.WARNING : Tone.NEUTRAL).withSize(NekkoraBadge.Size.SMALL));
        row.add(texts);
        row.addGlue();
        if (modified) {
            NekkoraButton restore = NekkoraButton.ghost("Restaurar original").withIcon(IconName.REFRESH).withSize(ControlSize.SMALL);
            restore.getAccessibleContext().setAccessibleName("Restaurar " + f.label().toLowerCase(java.util.Locale.ROOT) + " original");
            restore.onClick(e -> restore(f, b));
            row.add(restore);
        }
        return row;
    }

    /** Coloca o valor capturado do campo no formulário (aplicado ao salvar). */
    public void restore(StepField f, CapturedBaseline b) {
        switch (f) {
            case NAME -> name.setText(b.name());
            case DESCRIPTION -> description.setText(b.description());
            case ACTION_TYPE -> type.setSelectedItem(b.actionType());
            case PARAMETERS -> setParameters(b.parameters());
            case PREFERRED_LOCATOR -> {
                if (b.preferredLocator() >= 0 && b.preferredLocator() < preferred.size()) preferred.get(b.preferredLocator()).setSelected(true);
                else noPreferred.setSelected(true);
            }
        }
        refreshParameters();
    }

    private static String originLabel(AutomationStep s) {
        if (s.provenance().origin() == StepOrigin.RECORDED) return "Gravada pelo Nekkora Recorder";
        if (s.derivedFrom().isPresent()) return "Manual — duplicada de outra etapa";
        return s.provenance().origin().label();
    }

    // ------------------------------------------------------------------ StudioForm

    @Override
    public Values values() {
        StepForm.SessionChoice c = (StepForm.SessionChoice) session.getSelectedItem();
        int pref = -1;
        for (int i = 0; i < preferred.size(); i++) if (preferred.get(i).isSelected()) pref = i;
        return new Values(name.getText(), description.getText(), (ActionType) type.getSelectedItem(), c == null ? null : c.id(), currentMode(),
                valueText.getText(), optionLabel.getText(), optionValue.getText(), pref, List.copyOf(manualLocators), List.copyOf(comments));
    }

    /** Aba que contém o campo do problema (para mostrar a mensagem onde o usuário a vê). */
    @Override
    public void showProblem(ValidationProblem problem) {
        super.showProblem(problem);
        int tab = switch (problem.field()) {
            case VALUE -> 1;
            case LOCATOR -> 2;
            case COMMENT -> 3;
            default -> 0;
        };
        tabs.setSelectedIndex(tab);
    }

    @Override
    protected NekkoraFormField fieldFor(ValidationProblem.Field field) {
        return switch (field) {
            case SESSION -> sessionField;
            case VALUE -> ((ActionType) type.getSelectedItem()).acceptsValue() ? valueField : optionLabelField;
            case LOCATOR -> locatorField != null ? locatorField : basics.mainField();
            case COMMENT -> commentField;
            default -> basics.fieldFor(field);
        };
    }

    @Override
    protected NekkoraFormField mainField() {
        return basics.mainField();
    }

    @Override
    public JComponent initialFocus() {
        return name;
    }

    @Override
    protected NekkoraFormField[] fields() {
        List<NekkoraFormField> all = new ArrayList<>(java.util.Arrays.asList(basics.fields()));
        all.addAll(java.util.Arrays.asList(sessionField, valueField, optionLabelField, optionValueField, locatorField, commentField));
        return all.toArray(new NekkoraFormField[0]);
    }

    // ------------------------------------------------------------------ acesso (testes e acessibilidade)

    public NekkoraTabs tabs() {
        return tabs;
    }

    public com.nekkora.studio.domain.StepId stepId() {
        return stepId;
    }

    /** Campos básicos (nome, tipo, descrição). */
    public StepForm basics() {
        return basics;
    }

    /** Linhas do resumo exibidas (para verificar que correspondem à etapa). */
    public List<ElementPresentation.Row> overviewRows() {
        return overviewRows;
    }

    public NekkoraTextField nameInput() {
        return name;
    }

    public NekkoraTextArea descriptionInput() {
        return description;
    }

    public NekkoraComboBox<ActionType> typeInput() {
        return type;
    }

    public NekkoraComboBox<StepForm.SessionChoice> sessionInput() {
        return session;
    }

    public NekkoraRadioButton modeInput(ValueMode mode) {
        return modes.get(mode.ordinal());
    }

    public NekkoraTextField valueInput() {
        return valueText;
    }

    public NekkoraTextField optionLabelInput() {
        return optionLabel;
    }

    public NekkoraTextField optionValueInput() {
        return optionValue;
    }

    /** Opções de localizador preferencial (na ordem capturada). */
    public List<NekkoraRadioButton> preferredInputs() {
        return preferred;
    }

    public NekkoraTextField manualExpressionInput() {
        return manualExpression;
    }

    public NekkoraComboBox<ManualLocator.Syntax> manualSyntaxInput() {
        return manualSyntax;
    }

    public void addManualLocatorFromInputs() {
        addManualLocator();
    }

    public NekkoraTextArea commentInput() {
        return commentInput;
    }

    public void commitCommentFromInput() {
        commitComment();
    }

    /** Começa a editar o comentário na posição indicada (como o botão Editar). */
    public void editComment(int index) {
        editingComment = index;
        commentInput.setText(comments.get(index).text());
        commentButton.setText("Salvar comentário");
    }

    public void removeComment(int index) {
        comments.remove(index);
        renderComments();
    }

    public NekkoraFormField commentFieldView() {
        return commentField;
    }

    public NekkoraFormField locatorFieldView() {
        return locatorField;
    }

    public String originText() {
        return textOf(originBody);
    }

    private static String textOf(java.awt.Container c) {
        StringBuilder b = new StringBuilder();
        for (java.awt.Component ch : c.getComponents()) {
            if (ch instanceof NekkoraText t) b.append(t.getText()).append('\n');
            else if (ch instanceof javax.swing.text.JTextComponent t) b.append(t.getText()).append('\n');
            else if (ch instanceof NekkoraKeyValueView kv) b.append(kvText(kv));
            if (ch instanceof java.awt.Container cc && !(ch instanceof NekkoraKeyValueView)) b.append(textOf(cc));
        }
        return b.toString();
    }

    private static String kvText(NekkoraKeyValueView kv) {
        StringBuilder b = new StringBuilder();
        for (java.awt.Component ch : kv.getComponents()) {
            if (ch instanceof javax.swing.JLabel l) b.append(l.getText()).append('\n');
            if (ch instanceof javax.swing.text.JTextComponent t) b.append(t.getText()).append('\n');
        }
        return b.toString();
    }
}
