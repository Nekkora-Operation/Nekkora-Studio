package com.nekkora.studio.ui.form;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.DomainValidationException;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.Texts;
import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.studio.domain.variable.SemanticStatus;
import com.nekkora.studio.domain.variable.SemanticType;
import com.nekkora.studio.domain.variable.Sensitivity;
import com.nekkora.studio.domain.variable.TextFormat;
import com.nekkora.studio.domain.variable.VariableConstraints;
import com.nekkora.studio.domain.variable.VariableDefinition;
import com.nekkora.studio.domain.variable.VariableDraft;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.variable.VariableScope;
import com.nekkora.studio.domain.variable.VariableType;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.core.Severity;
import com.nekkora.ui.feedback.NekkoraInlineAlert;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.input.NekkoraCheckBox;
import com.nekkora.ui.input.NekkoraComboBox;
import com.nekkora.ui.input.NekkoraFormField;
import com.nekkora.ui.input.NekkoraTextArea;
import com.nekkora.ui.input.NekkoraTextField;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.navigation.NekkoraTabs;
import com.nekkora.ui.typography.NekkoraParagraph;
import com.nekkora.ui.typography.TextTone;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/**
 * Declaração de uma variável (STUDIO-008): criar ou editar. Coleta só METADADOS — nome, tipo, significado e quem o
 * sustenta, finalidade, escopo, origem, sensibilidade e restrições. Não existe campo para um valor de execução; o valor
 * fixo só é aceito para variáveis públicas ou internas (regra do domínio, que é quem decide) e o formulário avisa que ele
 * fica gravado no projeto sem criptografia.
 *
 * <p>Três abas curtas (Declaração · Origem e escopo · Restrições) para caber em janelas baixas e a 150%.
 */
public final class VariableForm extends StudioForm<VariableForm.Values> {

    /** Escolha de escopo: {@code session} nulo = automação inteira. */
    public record ScopeChoice(SessionId session, String label) {}

    /** Escolha de etapa produtora. */
    public record ProducerChoice(StepId step, String label) {}

    /** Valores digitados (sem normalização — o domínio normaliza e decide). */
    public record Values(String name, VariableType type, SemanticType semantic, boolean confirmed, String purpose, ScopeChoice scope,
                         VariableOrigin.Kind origin, String constant, ProducerChoice producer, Sensitivity sensitivity, boolean required,
                         String minLength, String maxLength, TextFormat textFormat, String minimum, String maximum, String scale, String currency) {

        /**
         * Rascunho para o domínio.
         *
         * @throws DomainValidationException {@link ValidationProblem#VARIABLE_CONSTRAINT_INVALID} se um número não pôde ser lido;
         *                                   {@link ValidationProblem#VARIABLE_PRODUCER_NOT_FOUND} sem etapa produtora escolhida
         */
        public VariableDraft toDraft() {
            SemanticStatus authority = semantic == SemanticType.UNSPECIFIED ? SemanticStatus.UNSPECIFIED
                    : confirmed ? SemanticStatus.USER_CONFIRMED : SemanticStatus.PROPOSED_UNCONFIRMED;
            VariableOrigin o = switch (origin) {
                case INPUT -> VariableOrigin.input();
                case UNBOUND -> VariableOrigin.unbound();
                case CONSTANT -> VariableOrigin.constant(constant == null ? "" : constant);
                case STEP_OUTPUT -> {
                    if (producer == null || producer.step() == null) throw new DomainValidationException(ValidationProblem.VARIABLE_PRODUCER_NOT_FOUND);
                    yield VariableOrigin.stepOutput(producer.step());
                }
            };
            VariableConstraints c = new VariableConstraints(required, integer(minLength), integer(maxLength), textFormat, decimal(minimum), decimal(maximum),
                    integer(scale), currency == null || currency.isBlank() ? null : currency.strip());
            VariableScope s = scope == null || scope.session() == null ? VariableScope.automation() : VariableScope.session(scope.session());
            return new VariableDraft(name == null ? "" : name.strip(), type, semantic, authority, purpose, s, o, sensitivity, c);
        }

        private static Integer integer(String t) {
            if (t == null || t.isBlank()) return null;
            try {
                return Integer.valueOf(t.strip());
            } catch (NumberFormatException e) {
                throw new DomainValidationException(ValidationProblem.VARIABLE_CONSTRAINT_INVALID);
            }
        }

        private static BigDecimal decimal(String t) {
            if (t == null || t.isBlank()) return null;
            if (!t.strip().matches("-?[0-9]{1,30}(\\.[0-9]{1,18})?")) throw new DomainValidationException(ValidationProblem.VARIABLE_CONSTRAINT_INVALID);
            return new BigDecimal(t.strip());
        }
    }

    public static final String CONSTANT_WARNING = "O valor fixo fica gravado no arquivo do projeto, que não é criptografado. Use só para valores "
            + "públicos ou internos não sensíveis — nunca senhas, documentos ou dados pessoais.";

    private final NekkoraTabs tabs = new NekkoraTabs();
    private final NekkoraTextField name = new NekkoraTextField("Ex.: cliente.cpf");
    private final NekkoraFormField nameField;
    private final NekkoraComboBox<VariableType> type = new NekkoraComboBox<>(VariableType.values());
    private final NekkoraFormField typeField;
    private final NekkoraComboBox<SemanticType> semantic = new NekkoraComboBox<>(SemanticType.values());
    private final NekkoraFormField semanticField;
    private final NekkoraCheckBox confirmed = new NekkoraCheckBox("Confirmo o significado desta variável");
    private final NekkoraTextArea purpose = FormKeys.tabMovesFocus(new NekkoraTextArea("Para que serve a variável (sem exemplos reais).", 2));
    private final NekkoraFormField purposeField;
    private final NekkoraComboBox<Sensitivity> sensitivity = new NekkoraComboBox<>(Sensitivity.values());
    private final NekkoraFormField sensitivityField;
    private final NekkoraComboBox<ScopeChoice> scope;
    private final NekkoraFormField scopeField;
    private final NekkoraComboBox<VariableOrigin.Kind> origin = new NekkoraComboBox<>(VariableOrigin.Kind.values());
    private final NekkoraFormField originField;
    private final NekkoraTextField constant = new NekkoraTextField("Valor público ou interno");
    private final NekkoraFormField constantField;
    private final NekkoraInlineAlert constantWarning = new NekkoraInlineAlert(Severity.WARNING, "Não criptografado", CONSTANT_WARNING);
    private final NekkoraComboBox<ProducerChoice> producer;
    private final NekkoraFormField producerField;
    private final NekkoraParagraph originHelp = new NekkoraParagraph("", TextTone.SECONDARY);
    private final NekkoraCheckBox required = new NekkoraCheckBox("Valor obrigatório");
    private final NekkoraTextField minLength = new NekkoraTextField("Mín.");
    private final NekkoraTextField maxLength = new NekkoraTextField("Máx.");
    private final NekkoraFormField lengthField;
    private final NekkoraComboBox<TextFormat> textFormat = new NekkoraComboBox<>(TextFormat.values());
    private final NekkoraFormField formatField;
    private final NekkoraTextField minimum = new NekkoraTextField("Mínimo");
    private final NekkoraTextField maximum = new NekkoraTextField("Máximo");
    private final NekkoraFormField rangeField;
    private final NekkoraTextField scale = new NekkoraTextField("Ex.: 2");
    private final NekkoraFormField scaleField;
    private final NekkoraTextField currency = new NekkoraTextField("Ex.: BRL");
    private final NekkoraFormField currencyField;

    /**
     * @param automation a automação (sessões e etapas para escopo e produtora)
     * @param existing   a variável em edição, ou {@code null} para uma nova
     */
    public VariableForm(Automation automation, VariableDefinition existing) {
        type.setLabelProvider(t -> t == null ? "" : t.label());
        semantic.setLabelProvider(t -> t == null ? "" : t.label());
        sensitivity.setLabelProvider(t -> t == null ? "" : t.label());
        origin.setLabelProvider(t -> t == null ? "" : t.label());
        textFormat.setLabelProvider(t -> t == null ? "" : t.label());
        List<ScopeChoice> scopes = new ArrayList<>();
        scopes.add(new ScopeChoice(null, "Automação inteira"));
        int n = 0;
        for (LogicalSession s : automation.sessions()) scopes.add(new ScopeChoice(s.id(), "Sessão " + (++n) + " — " + s.name()));
        scope = new NekkoraComboBox<>(scopes);
        scope.setLabelProvider(c -> c == null ? "" : c.label());
        List<ProducerChoice> producers = new ArrayList<>();
        producers.add(new ProducerChoice(null, "Escolha a etapa"));
        java.util.Map<StepId, String> places = com.nekkora.studio.ui.VariablePresentation.places(automation);
        for (LogicalSession s : automation.sessions()) {
            for (AutomationStep st : s.steps()) producers.add(new ProducerChoice(st.id(), places.get(st.id())));
        }
        producer = new NekkoraComboBox<>(producers);
        producer.setLabelProvider(c -> c == null ? "" : c.label());

        // ------------------------------------------------ Declaração
        nameField = new NekkoraFormField("Nome", name).required()
                .helper("Letras, números, ponto, hífen ou sublinhado, começando por letra. As etapas usam a variável por este nome.");
        typeField = new NekkoraFormField("Tipo técnico", type).required();
        semanticField = new NekkoraFormField("Significado", semantic).optional()
                .helper("Vocabulário fechado. O Studio não deduz o significado pelo nome nem pela página.");
        confirmed.getAccessibleContext().setAccessibleDescription("Sem a confirmação, o significado fica como proposto e a validação aponta.");
        purposeField = new NekkoraFormField("Finalidade", purpose.scrollable()).optional()
                .helper("Contexto para quem revisa. Até " + Texts.DESCRIPTION_MAX + " caracteres; não escreva dados reais.");
        sensitivityField = new NekkoraFormField("Sensibilidade", sensitivity).required()
                .helper("CPF e e-mail exigem no mínimo Dado pessoal. Dados pessoais e segredos não podem ter valor fixo.");
        NekkoraStack declaration = NekkoraStack.vertical(Spacing.SPACE_5);
        declaration.add(nameField);
        declaration.add(typeField);
        declaration.add(semanticField);
        declaration.add(confirmed);
        declaration.add(sensitivityField);
        declaration.add(purposeField);
        tabs.addTab("Declaração", IconName.EDIT, declaration);

        // ------------------------------------------------ Origem e escopo
        scopeField = new NekkoraFormField("Escopo", scope).required().helper("Onde a variável é visível: na automação inteira ou numa sessão.");
        originField = new NekkoraFormField("Origem", origin).required();
        constantField = new NekkoraFormField("Valor fixo", constant).optional();
        producerField = new NekkoraFormField("Etapa de origem", producer).optional()
                .helper("Declaração sua: o Studio não comprova que esta etapa produz o valor.");
        NekkoraStack originTab = NekkoraStack.vertical(Spacing.SPACE_5);
        originTab.add(scopeField);
        originTab.add(originField);
        originTab.add(originHelp);
        originTab.add(constantField);
        originTab.add(constantWarning);
        originTab.add(producerField);
        tabs.addTab("Origem e escopo", IconName.LAYERS, originTab);

        // ------------------------------------------------ Restrições
        NekkoraStack lengths = NekkoraStack.horizontal(Spacing.SPACE_4);
        lengths.add(minLength);
        lengths.add(maxLength);
        minLength.getAccessibleContext().setAccessibleName("Comprimento mínimo");
        maxLength.getAccessibleContext().setAccessibleName("Comprimento máximo");
        lengthField = new NekkoraFormField("Comprimento (texto)", lengths).optional();
        formatField = new NekkoraFormField("Formato (texto)", textFormat).optional();
        NekkoraStack range = NekkoraStack.horizontal(Spacing.SPACE_4);
        range.add(minimum);
        range.add(maximum);
        minimum.getAccessibleContext().setAccessibleName("Valor mínimo");
        maximum.getAccessibleContext().setAccessibleName("Valor máximo");
        rangeField = new NekkoraFormField("Limites (número)", range).optional().helper("Decimais com ponto, sem arredondamento.");
        scaleField = new NekkoraFormField("Casas decimais (decimal)", scale).optional();
        currencyField = new NekkoraFormField("Moeda (valor monetário)", currency).optional().helper("Código ISO 4217, como BRL.");
        NekkoraStack constraintsTab = NekkoraStack.vertical(Spacing.SPACE_5);
        constraintsTab.add(required);
        constraintsTab.add(lengthField);
        constraintsTab.add(formatField);
        constraintsTab.add(rangeField);
        constraintsTab.add(scaleField);
        constraintsTab.add(currencyField);
        tabs.addTab("Restrições", IconName.SLIDERS, constraintsTab);
        add(tabs);

        origin.addActionListener(e -> refreshOrigin());
        semantic.addActionListener(e -> refreshSemantic());
        type.addActionListener(e -> refreshType());
        load(existing);
    }

    private void load(VariableDefinition v) {
        VariableDraft d = v == null ? VariableDraft.of("", VariableType.STRING).withOrigin(VariableOrigin.input()) : v.draft();
        name.setText(d.name());
        type.setSelectedItem(d.type());
        semantic.setSelectedItem(d.semantic());
        confirmed.setSelected(d.authority() == SemanticStatus.USER_CONFIRMED); // confirmar é sempre um ato explícito
        purpose.setText(d.purpose());
        sensitivity.setSelectedItem(d.sensitivity());
        for (int i = 0; i < scope.getItemCount(); i++) {
            ScopeChoice c = scope.getItemAt(i);
            if (java.util.Objects.equals(c.session(), d.scope().session())) scope.setSelectedIndex(i);
        }
        origin.setSelectedItem(d.origin().kind());
        constant.setText(d.origin().constantValue().orElse(""));
        producer.setSelectedIndex(0);
        d.origin().producerStep().ifPresent(p -> {
            for (int i = 0; i < producer.getItemCount(); i++) if (p.equals(producer.getItemAt(i).step())) producer.setSelectedIndex(i);
        });
        VariableConstraints c = d.constraints();
        required.setSelected(c.required());
        minLength.setText(c.minLength() == null ? "" : c.minLength().toString());
        maxLength.setText(c.maxLength() == null ? "" : c.maxLength().toString());
        textFormat.setSelectedItem(c.textFormat());
        minimum.setText(c.minimum() == null ? "" : c.minimum().toPlainString());
        maximum.setText(c.maximum() == null ? "" : c.maximum().toPlainString());
        scale.setText(c.scale() == null ? "" : c.scale().toString());
        currency.setText(c.currency() == null ? "" : c.currency());
        name.selectAll();
        refreshOrigin();
        refreshSemantic();
        refreshType();
    }

    private void refreshOrigin() {
        VariableOrigin.Kind k = origin.getSelectedValue();
        boolean isConstant = k == VariableOrigin.Kind.CONSTANT;
        constantField.setVisible(isConstant);
        constantWarning.setVisible(isConstant);
        producerField.setVisible(k == VariableOrigin.Kind.STEP_OUTPUT);
        originHelp.setText(k == null ? "" : switch (k) {
            case INPUT -> "O valor será fornecido na execução. O Studio não conhece nem guarda esse valor.";
            case CONSTANT -> "Valor fixo declarado por você, na forma canônica do tipo.";
            case STEP_OUTPUT -> "O valor vem de uma etapa anterior. A ligação é declarada, não comprovada.";
            case UNBOUND -> "Ainda sem origem: a validação aponta bloqueio nas etapas que usam a variável.";
        });
        revalidate();
        repaint();
    }

    private void refreshSemantic() {
        confirmed.setEnabled(semantic.getSelectedValue() != SemanticType.UNSPECIFIED);
    }

    private void refreshType() {
        VariableType t = type.getSelectedValue();
        boolean text = t == VariableType.STRING;
        minLength.setEnabled(text);
        maxLength.setEnabled(text);
        textFormat.setEnabled(text);
        minimum.setEnabled(t != null && t.numeric());
        maximum.setEnabled(t != null && t.numeric());
        scale.setEnabled(t == VariableType.DECIMAL);
        currency.setEnabled(t == VariableType.DECIMAL);
    }

    @Override
    public Values values() {
        return new Values(name.getText(), type.getSelectedValue(), semantic.getSelectedValue(), confirmed.isSelected(), purpose.getText(),
                scope.getSelectedValue(), origin.getSelectedValue(), constant.getText(), producer.getSelectedValue(), sensitivity.getSelectedValue(),
                required.isSelected(), minLength.getText(), maxLength.getText(), textFormat.getSelectedValue(), minimum.getText(), maximum.getText(),
                scale.getText(), currency.getText());
    }

    /** Cada problema no campo que o resolve (e a aba dele em primeiro plano). */
    @Override
    public void showProblem(ValidationProblem problem) {
        NekkoraFormField f = switch (problem) {
            case VARIABLE_SEMANTIC_INCOMPATIBLE, VARIABLE_AUTHORITY_INVALID -> semanticField;
            case VARIABLE_SENSITIVITY_TOO_LOW -> sensitivityField;
            case VARIABLE_CONSTANT_FORBIDDEN, VARIABLE_CONSTANT_INVALID, VARIABLE_CONSTANT_LOOKS_SENSITIVE -> constantField.isVisible() ? constantField
                    : originField;
            case VARIABLE_PRODUCER_NOT_FOUND, VARIABLE_PRODUCER_OUT_OF_SCOPE -> producerField;
            case VARIABLE_SCOPE_SESSION_NOT_FOUND -> scopeField;
            case VARIABLE_CONSTRAINT_INVALID -> rangeField;
            case VARIABLE_PURPOSE_LOOKS_SENSITIVE, DESCRIPTION_TOO_LONG -> purposeField;
            default -> fieldFor(problem.field());
        };
        f = f == null ? nameField : f;
        f.setError(problem.message());
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (javax.swing.SwingUtilities.isDescendingFrom(f, tabs.getComponentAt(i))) tabs.setSelectedIndex(i);
        }
    }

    @Override
    protected NekkoraFormField fieldFor(ValidationProblem.Field field) {
        return switch (field) {
            case DESCRIPTION -> purposeField;
            case SESSION -> scopeField;
            default -> nameField;
        };
    }

    @Override
    protected NekkoraFormField mainField() {
        return nameField;
    }

    @Override
    public JComponent initialFocus() {
        return name;
    }

    @Override
    protected NekkoraFormField[] fields() {
        return new NekkoraFormField[] {nameField, typeField, semanticField, purposeField, sensitivityField, scopeField, originField, constantField,
                producerField, lengthField, formatField, rangeField, scaleField, currencyField};
    }

    // ------------------------------------------------------------------ acesso para testes/evidências

    public NekkoraTabs tabs() {
        return tabs;
    }

    public NekkoraTextField nameInput() {
        return name;
    }

    public NekkoraComboBox<VariableType> typeInput() {
        return type;
    }

    public NekkoraComboBox<SemanticType> semanticInput() {
        return semantic;
    }

    public NekkoraCheckBox confirmedInput() {
        return confirmed;
    }

    public NekkoraComboBox<Sensitivity> sensitivityInput() {
        return sensitivity;
    }

    public NekkoraComboBox<ScopeChoice> scopeInput() {
        return scope;
    }

    public NekkoraComboBox<VariableOrigin.Kind> originInput() {
        return origin;
    }

    public NekkoraTextField constantInput() {
        return constant;
    }

    public NekkoraInlineAlert constantWarning() {
        return constantWarning;
    }

    public NekkoraComboBox<ProducerChoice> producerInput() {
        return producer;
    }

    public NekkoraCheckBox requiredInput() {
        return required;
    }

    public NekkoraTextField minLengthInput() {
        return minLength;
    }

    public NekkoraTextField maxLengthInput() {
        return maxLength;
    }

    public NekkoraComboBox<TextFormat> textFormatInput() {
        return textFormat;
    }

    public NekkoraTextField minimumInput() {
        return minimum;
    }

    public NekkoraTextField maximumInput() {
        return maximum;
    }

    public NekkoraTextField scaleInput() {
        return scale;
    }

    public NekkoraTextField currencyInput() {
        return currency;
    }
}
