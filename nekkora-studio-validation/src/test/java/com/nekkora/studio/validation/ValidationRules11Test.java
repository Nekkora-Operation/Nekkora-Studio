package com.nekkora.studio.validation;

import static com.nekkora.studio.validation.Fixtures.good;
import static com.nekkora.studio.validation.Fixtures.manual;
import static com.nekkora.studio.validation.Fixtures.withElement;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.variable.SemanticStatus;
import com.nekkora.studio.domain.variable.SemanticType;
import com.nekkora.studio.domain.variable.Sensitivity;
import com.nekkora.studio.domain.variable.VariableDefinition;
import com.nekkora.studio.domain.variable.VariableDraft;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.variable.VariableScope;
import com.nekkora.studio.domain.variable.VariableType;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Regras {@code nekkora-validation-rules/1.1.0} (STUDIO-008): uso derivado, produtor só declarado, escopo, tipo, origem. */
class ValidationRules11Test {
    private final AutomationValidator v11 = new AutomationValidator(RuleSet.V1_1);

    private static Automation automation() {
        Automation a = Automation.create("Cadastro fictício", "", Fixtures.CLOCK);
        a.addSession("Cadastro", null);
        a.addSession("Pesquisa", null);
        return a;
    }

    private static AutomationStep fill(Automation a, int session, String name, String variable) {
        return a.appendStep(a.sessions().get(session).id(), withElement(name, ActionType.FILL, good(),
                ActionParameters.withValue(new ValueSource.VariableReference(variable))));
    }

    private static AutomationStep observe(Automation a, int session, String name) {
        return a.appendStep(a.sessions().get(session).id(), manual(name, ActionType.OBSERVE));
    }

    private static VariableDefinition input(Automation a, String name, VariableType type) {
        return a.declareVariable(VariableDraft.of(name, type).withOrigin(VariableOrigin.input()));
    }

    private static VariableDefinition output(Automation a, String name, AutomationStep producer, VariableScope scope) {
        return a.declareVariable(VariableDraft.of(name, VariableType.STRING).withOrigin(VariableOrigin.stepOutput(producer.id())).withScope(scope));
    }

    private Set<FindingCode> codes(Automation a, AutomationStep step) {
        return v11.validate(a).forStep(step.id()).stream().map(Finding::code).collect(Collectors.toSet());
    }

    private Set<FindingCode> codes(Automation a, VariableDefinition v) {
        return v11.validate(a).forVariable(v.id()).stream().map(Finding::code).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("regras 1.0.0 continuam como eram: referência = resolução indisponível, sem escopo de variável")
    void regras10Intactas() {
        Automation a = automation();
        AutomationStep s = fill(a, 0, "Preencher CPF", "cliente.cpf");
        input(a, "cliente.cpf", VariableType.STRING);
        ValidationReport r = new AutomationValidator().validate(a);
        assertEquals(AutomationValidator.RULES_VERSION, r.rulesVersion());
        assertEquals(ValidationReport.VERSION, r.version());
        assertTrue(r.forStep(s.id()).stream().anyMatch(f -> f.code() == FindingCode.VARIABLE_RESOLUTION_UNAVAILABLE));
        assertTrue(r.findings().stream().noneMatch(f -> f.scope() == FindingScope.VARIABLE || f.code().since() == RuleSet.V1_1));
        assertFalse(r.toJson().contains("variableId"), "o relatório 1.0.0 não ganhou campos");
    }

    @Test
    @DisplayName("sem declaração: VARIABLE_NOT_DECLARED (bloqueio); nome parecido não é associado")
    void naoDeclarada() {
        Automation a = automation();
        AutomationStep s = fill(a, 0, "Preencher CPF", "Cliente.CPF");
        input(a, "cliente.cpf", VariableType.STRING);
        ValidationReport r = v11.validate(a);
        assertEquals(AutomationValidator.RULES_VERSION_1_1, r.rulesVersion());
        assertEquals(ValidationReport.VERSION_1_1, r.version());
        assertEquals(Set.of(FindingCode.VARIABLE_NOT_DECLARED), codes(a, s).stream().filter(c -> c.since() == RuleSet.V1_1).collect(Collectors.toSet()));
        assertFalse(codes(a, s).contains(FindingCode.VARIABLE_RESOLUTION_UNAVAILABLE), "1.1 confere contra a declaração");
        assertEquals(ValidationStatus.INCOMPLETE, r.status());
    }

    @Test
    @DisplayName("entrada declarada: sem bloqueio, mas a avaliação fica limitada (o valor só existe na execução)")
    void entrada() {
        Automation a = automation();
        AutomationStep s = fill(a, 0, "Preencher CPF", "cliente.cpf");
        VariableDefinition v = a.declareVariable(VariableDraft.of("cliente.cpf", VariableType.STRING)
                .withSemantic(SemanticType.BRAZILIAN_CPF, SemanticStatus.USER_CONFIRMED).withSensitivity(Sensitivity.PERSONAL_DATA)
                .withOrigin(VariableOrigin.input()));
        assertEquals(Set.of(FindingCode.INPUT_VALUE_NOT_AVAILABLE, FindingCode.LOCATOR_VALIDATED_AT_CAPTURE), codes(a, s));
        assertEquals(Set.of(), codes(a, v));
        Finding f = v11.validate(a).forStep(s.id()).stream().filter(x -> x.code() == FindingCode.INPUT_VALUE_NOT_AVAILABLE).findFirst().orElseThrow();
        assertEquals(v.id(), f.variable(), "o apontamento leva à variável (pela identidade)");
        assertEquals(ValidationStatus.NOT_FULLY_ASSESSABLE, v11.validate(a).status());
    }

    @Test
    @DisplayName("escopo: variável de outra sessão não é visível; tipo incompatível; sem origem")
    void escopoTipoOrigem() {
        Automation a = automation();
        AutomationStep fora = fill(a, 1, "Preencher na Pesquisa", "cadastro.codigo");
        a.declareVariable(VariableDraft.of("cadastro.codigo", VariableType.STRING).withOrigin(VariableOrigin.input())
                .withScope(VariableScope.session(a.sessions().get(0).id())));
        assertTrue(codes(a, fora).contains(FindingCode.VARIABLE_OUT_OF_SCOPE));

        AutomationStep booleano = fill(a, 0, "Preencher ativo", "cliente.ativo");
        a.declareVariable(VariableDraft.of("cliente.ativo", VariableType.BOOLEAN));
        assertTrue(codes(a, booleano).containsAll(Set.of(FindingCode.VARIABLE_TYPE_INCOMPATIBLE, FindingCode.VARIABLE_ORIGIN_UNBOUND)));

        AutomationStep nav = a.appendStep(a.sessions().get(0).id(), manual("Abrir portal", ActionType.NAVIGATE,
                ActionParameters.withValue(new ValueSource.VariableReference("pedido.quantidade"))));
        a.declareVariable(VariableDraft.of("pedido.quantidade", VariableType.INTEGER).withOrigin(VariableOrigin.constant("3")));
        assertTrue(codes(a, nav).contains(FindingCode.VARIABLE_TYPE_INCOMPATIBLE), "navegar só aceita texto");
        assertTrue(AutomationValidator.accepts(ActionType.FILL, VariableType.DATE));
        assertTrue(AutomationValidator.accepts(ActionType.UNKNOWN, VariableType.BOOLEAN));
        assertFalse(AutomationValidator.accepts(ActionType.PRESS_KEY, VariableType.DECIMAL));
    }

    @Test
    @DisplayName("saída de etapa: declarada antes = não comprovada; depois = bloqueio; a própria etapa = bloqueio; ciclo")
    void saidaDeEtapa() {
        Automation a = automation();
        AutomationStep ler = observe(a, 0, "Ler protocolo");
        AutomationStep usa = fill(a, 0, "Preencher protocolo", "resultado.protocolo");
        output(a, "resultado.protocolo", ler, VariableScope.automation());
        assertTrue(codes(a, usa).contains(FindingCode.STEP_OUTPUT_NOT_PROVEN));
        assertFalse(codes(a, usa).contains(FindingCode.DECLARED_PRODUCER_AFTER_CONSUMER));

        AutomationStep antes = fill(a, 1, "Preencher código", "consulta.codigo");
        AutomationStep depois = observe(a, 1, "Ler código");
        output(a, "consulta.codigo", depois, VariableScope.automation());
        assertTrue(codes(a, antes).contains(FindingCode.DECLARED_PRODUCER_AFTER_CONSUMER));

        AutomationStep propria = fill(a, 1, "Ler e preencher", "auto.valor");
        output(a, "auto.valor", propria, VariableScope.automation());
        assertTrue(codes(a, propria).contains(FindingCode.STEP_OUTPUT_SELF_CONSUMED));
        assertFalse(codes(a, propria).contains(FindingCode.DATA_DEPENDENCY_CYCLE), "laço da própria etapa tem código próprio");

        AutomationStep x = fill(a, 1, "Etapa X", "ciclo.a");
        AutomationStep y = fill(a, 1, "Etapa Y", "ciclo.b");
        output(a, "ciclo.a", y, VariableScope.automation());
        output(a, "ciclo.b", x, VariableScope.automation());
        assertTrue(codes(a, x).containsAll(Set.of(FindingCode.DECLARED_PRODUCER_AFTER_CONSUMER, FindingCode.DATA_DEPENDENCY_CYCLE)));
        assertTrue(codes(a, y).containsAll(Set.of(FindingCode.STEP_OUTPUT_NOT_PROVEN, FindingCode.DATA_DEPENDENCY_CYCLE)));
        assertEquals(Set.of(x.id(), y.id()), DataFlowAnalysis.of(a).cycleSteps().stream().filter(s -> !s.equals(propria.id())).collect(Collectors.toSet()));
    }

    @Test
    @DisplayName("produtora removida ou movida para fora do escopo; sessão do escopo removida — apontado, nunca corrigido em silêncio")
    void referenciasQuebradas() {
        Automation a = automation();
        AutomationStep ler = observe(a, 0, "Ler protocolo");
        AutomationStep usa = fill(a, 0, "Preencher protocolo", "resultado.protocolo");
        VariableDefinition v = output(a, "resultado.protocolo", ler, VariableScope.session(a.sessions().get(0).id()));
        a.moveStep(ler.id(), a.sessions().get(1).id(), 0);
        assertTrue(codes(a, v).contains(FindingCode.VARIABLE_PRODUCER_OUT_OF_SCOPE));
        assertTrue(codes(a, usa).contains(FindingCode.DECLARED_PRODUCER_MISSING));
        a.removeStep(ler.id());
        assertTrue(codes(a, v).contains(FindingCode.VARIABLE_PRODUCER_MISSING));
        assertEquals(v, a.variable(v.id()).orElseThrow(), "a declaração não muda sozinha");

        LogicalSession pesquisa = a.sessions().get(1);
        VariableDefinition sv = a.declareVariable(VariableDraft.of("pesquisa.termo", VariableType.STRING).withOrigin(VariableOrigin.input())
                .withScope(VariableScope.session(pesquisa.id())));
        a.removeSession(pesquisa.id());
        assertTrue(codes(a, sv).contains(FindingCode.VARIABLE_SCOPE_SESSION_MISSING));
        Finding f = v11.validate(a).forVariable(sv.id()).get(0);
        assertEquals(null, f.session(), "a sessão inexistente não é referenciada");
    }

    @Test
    @DisplayName("na variável: significado proposto limita; não especificado e sem uso informam; constante gravada sem criptografia")
    void apontamentosDaVariavel() {
        Automation a = automation();
        VariableDefinition proposta = a.declareVariable(VariableDraft.of("cliente.email", VariableType.STRING)
                .withSemantic(SemanticType.EMAIL, SemanticStatus.PROPOSED_UNCONFIRMED).withSensitivity(Sensitivity.PERSONAL_DATA)
                .withOrigin(VariableOrigin.input()));
        VariableDefinition constante = a.declareVariable(VariableDraft.of("portal.endereco", VariableType.STRING)
                .withOrigin(VariableOrigin.constant("https://lab.exemplo.test/portal")));
        assertEquals(Set.of(FindingCode.VARIABLE_SEMANTIC_UNCONFIRMED, FindingCode.VARIABLE_UNUSED), codes(a, proposta));
        assertEquals(Set.of(FindingCode.VARIABLE_SEMANTIC_UNSPECIFIED, FindingCode.CONSTANT_STORED_UNENCRYPTED, FindingCode.VARIABLE_UNUSED),
                codes(a, constante));
        ValidationReport r = v11.validate(a);
        // ordem: automação → variáveis (catálogo) → sessões → etapas
        List<FindingScope> scopes = r.findings().stream().map(Finding::scope).toList();
        assertTrue(scopes.lastIndexOf(FindingScope.VARIABLE) < scopes.indexOf(FindingScope.SESSION));
        assertEquals(List.of(proposta.id(), proposta.id(), constante.id(), constante.id(), constante.id()),
                r.findings().stream().filter(f -> f.scope() == FindingScope.VARIABLE).map(Finding::variable).toList());
        assertEquals(ValidationStatus.INCOMPLETE, r.status(), "sessões vazias + sem etapas");
    }

    @Test
    @DisplayName("credencial continua sendo só alias; campo sensível com variável continua apontado")
    void credencialECampoSensivel() {
        Automation a = automation();
        AutomationStep senha = a.appendStep(a.sessions().get(0).id(), withElement("Preencher senha", ActionType.FILL, Fixtures.password(),
                ActionParameters.withValue(new ValueSource.VariableReference("acesso.senha"))));
        a.declareVariable(VariableDraft.of("acesso.senha", VariableType.STRING).withSensitivity(Sensitivity.SECRET).withOrigin(VariableOrigin.input()));
        assertTrue(codes(a, senha).contains(FindingCode.SENSITIVE_FIELD_WITHOUT_CREDENTIAL));
        AutomationStep cred = a.appendStep(a.sessions().get(0).id(), withElement("Preencher senha 2", ActionType.FILL, Fixtures.password(),
                ActionParameters.withValue(new ValueSource.CredentialReference("sistema.senha"))));
        assertEquals(Set.of(FindingCode.CREDENTIAL_RESOLUTION_UNAVAILABLE, FindingCode.LOCATOR_VALIDATED_AT_CAPTURE), codes(a, cred));
    }
}
