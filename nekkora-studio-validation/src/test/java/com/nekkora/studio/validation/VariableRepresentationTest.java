package com.nekkora.studio.validation;

import static com.nekkora.studio.validation.Fixtures.good;
import static com.nekkora.studio.validation.Fixtures.withElement;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.variable.SemanticStatus;
import com.nekkora.studio.domain.variable.SemanticType;
import com.nekkora.studio.domain.variable.Sensitivity;
import com.nekkora.studio.domain.variable.TextFormat;
import com.nekkora.studio.domain.variable.VariableConstraints;
import com.nekkora.studio.domain.variable.VariableDefinition;
import com.nekkora.studio.domain.variable.VariableDraft;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.variable.VariableType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * STUDIO-008, revisão do PR #11 (P2): significado confirmado ≠ representação declarada ≠ valor validado. Um CPF pode ser
 * escrito só com dígitos ou com máscara; o significado sozinho não diz qual a etapa espera, e o Studio não escolhe.
 *
 * <p>Escrito ANTES da correção, com o código e o formato pelo NOME (compila no código revisado e falha nele: lá o CPF
 * confirmado sem formato não gerava apontamento nenhum e não existia como declarar a máscara).
 */
class VariableRepresentationTest {
    private static final String CODE = "VARIABLE_REPRESENTATION_UNSPECIFIED";
    private static final String MASKED = "BRAZILIAN_CPF_MASKED";

    private final AutomationValidator v11 = new AutomationValidator(RuleSet.V1_1);

    private static VariableConstraints format(TextFormat f) {
        return new VariableConstraints(false, null, null, f, null, null, null, null);
    }

    private static Automation cpf(VariableConstraints constraints, SemanticStatus status) {
        Automation a = Automation.create("Cadastro fictício", "", Fixtures.CLOCK);
        a.addSession("Cadastro", null);
        a.appendStep(a.sessions().get(0).id(), withElement("Preencher CPF", ActionType.FILL, good(),
                ActionParameters.withValue(new ValueSource.VariableReference("cliente.cpf"))));
        a.declareVariable(VariableDraft.of("cliente.cpf", VariableType.STRING).withSemantic(SemanticType.BRAZILIAN_CPF, status)
                .withSensitivity(Sensitivity.PERSONAL_DATA).withOrigin(VariableOrigin.input()).withConstraints(constraints));
        return a;
    }

    private List<String> codes(Automation a) {
        VariableDefinition v = a.variables().get(0);
        return v11.validate(a).forVariable(v.id()).stream().map(f -> f.code().name()).toList();
    }

    @Test
    @DisplayName("CPF com significado CONFIRMADO e sem formato: o Validator informa que a representação não foi declarada")
    void cpfConfirmadoSemFormato() {
        Automation a = cpf(VariableConstraints.none(), SemanticStatus.USER_CONFIRMED);
        assertTrue(codes(a).contains(CODE), "significado confirmado não basta: " + codes(a));
        Finding f = v11.validate(a).findings().stream().filter(x -> x.code().name().equals(CODE)).findFirst().orElseThrow();
        assertEquals(FindingScope.VARIABLE, f.scope());
        assertEquals(Severity.WARNING, f.severity(), "não é bloqueio: o processo decide a regra, o Studio só aponta o que falta");
        assertTrue(f.code().limitsAssessment(), "sem a representação, a configuração não pode ser avaliada por completo");
        assertNotEquals(ValidationStatus.NO_STATIC_BLOCKERS, v11.validate(a).status());
        // proposto e não confirmado: também falta a representação (os dois apontamentos convivem)
        List<String> proposed = codes(cpf(VariableConstraints.none(), SemanticStatus.PROPOSED_UNCONFIRMED));
        assertTrue(proposed.contains(CODE) && proposed.contains("VARIABLE_SEMANTIC_UNCONFIRMED"), proposed.toString());
    }

    @Test
    @DisplayName("só dígitos ou com máscara: a representação está declarada e o apontamento some; o Studio não impõe nenhuma das duas")
    void representacaoDeclarada() {
        assertFalse(codes(cpf(format(TextFormat.DIGITS), SemanticStatus.USER_CONFIRMED)).contains(CODE));
        TextFormat masked = TextFormat.valueOf(MASKED);
        assertFalse(codes(cpf(format(masked), SemanticStatus.USER_CONFIRMED)).contains(CODE));
        assertTrue(masked.matches("123.456.789-09") && !masked.matches("12345678909"), "forma da máscara");
        assertTrue(TextFormat.DIGITS.matches("12345678909") && !TextFormat.DIGITS.matches("123.456.789-09"), "forma só dígitos");
        // um formato que não é representação de CPF não conta como declarado
        assertTrue(codes(cpf(format(TextFormat.LETTERS_AND_DIGITS), SemanticStatus.USER_CONFIRMED)).contains(CODE));
        // obrigatoriedade continua sendo do processo: o significado não torna o CPF obrigatório
        assertFalse(cpf(format(TextFormat.DIGITS), SemanticStatus.USER_CONFIRMED).variables().get(0).constraints().required());
    }

    @Test
    @DisplayName("valor validado só numa verificação futura: com a representação declarada, a entrada continua não disponível")
    void valorNaoValidado() {
        Automation a = cpf(format(TextFormat.DIGITS), SemanticStatus.USER_CONFIRMED);
        AutomationStep fill = a.sessions().get(0).steps().get(0);
        assertTrue(v11.validate(a).forStep(fill.id()).stream().anyMatch(f -> f.code() == FindingCode.INPUT_VALUE_NOT_AVAILABLE));
        assertNotEquals(ValidationStatus.NO_STATIC_BLOCKERS, v11.validate(a).status(), "nenhum valor foi comprovado");
    }

    @Test
    @DisplayName("significados com representação única ou já fixada pelas restrições não recebem o apontamento; regras 1.0.0 nunca o emitem")
    void outrosSignificados() {
        Automation a = Automation.create("Cadastro fictício", "", Fixtures.CLOCK);
        a.addSession("Cadastro", null);
        a.declareVariable(VariableDraft.of("cliente.email", VariableType.STRING).withSemantic(SemanticType.EMAIL, SemanticStatus.USER_CONFIRMED)
                .withSensitivity(Sensitivity.PERSONAL_DATA).withOrigin(VariableOrigin.input()));
        a.declareVariable(VariableDraft.of("pedido.data", VariableType.DATE).withSemantic(SemanticType.DATE_ISO, SemanticStatus.USER_CONFIRMED)
                .withOrigin(VariableOrigin.input()));
        a.declareVariable(VariableDraft.of("pedido.valor", VariableType.DECIMAL).withSemantic(SemanticType.CURRENCY, SemanticStatus.USER_CONFIRMED)
                .withOrigin(VariableOrigin.input())
                .withConstraints(new VariableConstraints(false, null, null, TextFormat.ANY, BigDecimal.ZERO, null, 2, "BRL")));
        a.declareVariable(VariableDraft.of("pedido.codigo", VariableType.STRING).withSemantic(SemanticType.IDENTIFIER, SemanticStatus.USER_CONFIRMED)
                .withOrigin(VariableOrigin.input()));
        assertTrue(v11.validate(a).findings().stream().noneMatch(f -> f.code().name().equals(CODE)));
        Automation cpf = cpf(VariableConstraints.none(), SemanticStatus.USER_CONFIRMED);
        assertTrue(new AutomationValidator().validate(cpf).findings().stream().noneMatch(f -> f.code().name().equals(CODE)), "relatório 1.0.0 intacto");
    }
}
