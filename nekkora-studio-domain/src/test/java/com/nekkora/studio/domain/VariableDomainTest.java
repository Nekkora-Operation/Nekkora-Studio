package com.nekkora.studio.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepProvenance;
import com.nekkora.studio.domain.variable.SemanticAuthority;
import com.nekkora.studio.domain.variable.SemanticType;
import com.nekkora.studio.domain.variable.Sensitivity;
import com.nekkora.studio.domain.variable.TextFormat;
import com.nekkora.studio.domain.variable.VariableConstraints;
import com.nekkora.studio.domain.variable.VariableDefinition;
import com.nekkora.studio.domain.variable.VariableDraft;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.variable.VariableScope;
import com.nekkora.studio.domain.variable.VariableType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** STUDIO-008: variáveis declaradas no domínio — identidade, coerência, operações atômicas e histórico. Dados fictícios. */
class VariableDomainTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);

    private Automation automation() {
        Automation a = Automation.create("Cadastro de clientes", null, clock);
        a.addSession("Autenticação", null);
        a.addSession("Cadastro", null);
        return a;
    }

    private static ValidationProblem refused(Runnable r) {
        return assertThrows(DomainValidationException.class, r::run).problem();
    }

    /** cliente.cpf do exemplo-guia: STRING, CPF, entrada, dados pessoais, confirmado, 11 dígitos — sem CPF algum. */
    private static VariableDraft cpf() {
        return new VariableDraft("cliente.cpf", VariableType.STRING, SemanticType.BRAZILIAN_CPF, SemanticAuthority.USER_CONFIRMED,
                "Documento do cliente consultado", VariableScope.automation(), VariableOrigin.input(), Sensitivity.PERSONAL_DATA,
                new VariableConstraints(true, 11, 11, TextFormat.DIGITS, null, null, null, null));
    }

    private static StepDraft recorded(String name, ActionType type, String event) {
        CorrelationKeys keys = CorrelationKeys.of(Map.of(CorrelationKeys.Key.RECORDING_ID, "rec-1", CorrelationKeys.Key.RECORDED_EVENT_ID, event));
        return StepDraft.builder().name(name).actionType(type).provenance(StepProvenance.recorded(keys, new RecordingObservation(
                RecordingObservation.CaptureMode.CAPTURE_AND_INTERACT, RecordingObservation.Observation.INTERACTION_OBSERVED, null, List.of(),
                List.of()))).build();
    }

    @Test
    @DisplayName("declarar: identidade estável, modelo passa a 1.1.0, metadados preservados; nenhum valor guardado para entrada")
    void declarar() {
        Automation a = automation();
        assertEquals(ModelVersion.CURRENT, a.modelVersion(), "sem variáveis: 1.0.0");
        long revision = a.revision();
        VariableDefinition v = a.declareVariable(cpf());
        assertEquals(ModelVersion.V1_1, a.modelVersion());
        assertEquals(revision + 1, a.revision());
        assertEquals(List.of(v), a.variables());
        assertSame(v, a.variableNamed("cliente.cpf").orElseThrow());
        assertTrue(a.variableNamed("Cliente.CPF").isEmpty(), "resolução por nome exato");
        assertEquals(VariableOrigin.Kind.INPUT, v.origin().kind());
        assertTrue(v.origin().constantValue().isEmpty(), "entrada não tem valor");
        assertFalse(v.toString().contains("cliente") || v.origin().toString().contains("cliente"), "toString sem nome nem valor");
    }

    @Test
    @DisplayName("nomes: sintaxe de referência (o ponto faz parte do nome), únicos sem distinção de maiúsculas entre TODOS os escopos")
    void nomes() {
        Automation a = automation();
        a.declareVariable(cpf());
        assertEquals(ValidationProblem.VARIABLE_NAME_IN_USE, refused(() -> a.declareVariable(VariableDraft.of("CLIENTE.CPF", VariableType.STRING))));
        SessionId cadastro = a.sessions().get(1).id();
        assertEquals(ValidationProblem.VARIABLE_NAME_IN_USE, refused(() -> a.declareVariable(VariableDraft.of("cliente.cpf", VariableType.STRING)
                .withScope(VariableScope.session(cadastro)))), "escopo não permite repetir o nome");
        for (String bad : List.of("1cliente", "cliente cpf", "", "cliente/cpf", "x".repeat(65))) {
            assertEquals(ValidationProblem.VARIABLE_NAME_INVALID, refused(() -> a.declareVariable(VariableDraft.of(bad, VariableType.STRING))), bad);
        }
        a.declareVariable(VariableDraft.of("cliente.cpf.anterior", VariableType.STRING)); // ponto é parte do nome, não acesso
        assertEquals(2, a.variables().size());
    }

    @Test
    @DisplayName("significado: compatível com o tipo; sensibilidade mínima (CPF/e-mail = dados pessoais); autoridade coerente; nada inferido pelo nome")
    void significado() {
        Automation a = automation();
        assertEquals(ValidationProblem.VARIABLE_SEMANTIC_INCOMPATIBLE, refused(() -> a.declareVariable(cpf().withType(VariableType.INTEGER))),
                "CPF é texto (zeros à esquerda)");
        assertEquals(ValidationProblem.VARIABLE_SENSITIVITY_TOO_LOW, refused(() -> a.declareVariable(cpf().withSensitivity(Sensitivity.PUBLIC))));
        assertEquals(ValidationProblem.VARIABLE_AUTHORITY_INVALID, refused(() -> a.declareVariable(VariableDraft.of("x", VariableType.STRING)
                .withSemantic(SemanticType.UNSPECIFIED, SemanticAuthority.USER_CONFIRMED))));
        assertEquals(ValidationProblem.VARIABLE_AUTHORITY_INVALID, refused(() -> a.declareVariable(VariableDraft.of("x", VariableType.STRING)
                .withSemantic(SemanticType.FREE_TEXT, SemanticAuthority.UNSPECIFIED))));
        VariableDefinition named = a.declareVariable(VariableDraft.of("cliente.email", VariableType.STRING));
        assertEquals(SemanticType.UNSPECIFIED, named.semantic(), "o nome não define o significado");
        assertEquals(SemanticAuthority.UNSPECIFIED, named.authority());
        VariableDefinition proposed = a.declareVariable(VariableDraft.of("contato", VariableType.STRING).withSemantic(SemanticType.EMAIL,
                SemanticAuthority.PROPOSED_UNCONFIRMED).withSensitivity(Sensitivity.PERSONAL_DATA));
        assertEquals(SemanticAuthority.PROPOSED_UNCONFIRMED, proposed.authority());
    }

    @Test
    @DisplayName("restrições tipadas e coerentes: comprimento/formato só em texto, limites só em número, escala só em decimal, moeda obrigatória em valor monetário")
    void restricoes() {
        Automation a = automation();
        VariableConstraints length = new VariableConstraints(true, 5, 2, TextFormat.ANY, null, null, null, null);
        assertEquals(ValidationProblem.VARIABLE_CONSTRAINT_INVALID, refused(() -> a.declareVariable(VariableDraft.of("a", VariableType.STRING).withConstraints(length))));
        VariableConstraints onInteger = new VariableConstraints(false, 1, 3, TextFormat.ANY, null, null, null, null);
        assertEquals(ValidationProblem.VARIABLE_CONSTRAINT_INVALID, refused(() -> a.declareVariable(VariableDraft.of("b", VariableType.INTEGER).withConstraints(onInteger))));
        VariableConstraints fractionalBound = new VariableConstraints(false, null, null, TextFormat.ANY, new BigDecimal("0.5"), null, null, null);
        assertEquals(ValidationProblem.VARIABLE_CONSTRAINT_INVALID, refused(() -> a.declareVariable(VariableDraft.of("c", VariableType.INTEGER).withConstraints(fractionalBound))));
        VariableDraft money = VariableDraft.of("pagamento.valor_total", VariableType.DECIMAL).withSemantic(SemanticType.CURRENCY, SemanticAuthority.USER_CONFIRMED);
        assertEquals(ValidationProblem.VARIABLE_CONSTRAINT_INVALID, refused(() -> a.declareVariable(money)), "moeda e escala obrigatórias");
        assertEquals(ValidationProblem.VARIABLE_CONSTRAINT_INVALID, refused(() -> a.declareVariable(money.withConstraints(
                new VariableConstraints(true, null, null, TextFormat.ANY, null, null, 2, "XYZ")))), "moeda ISO 4217");
        VariableDefinition ok = a.declareVariable(money.withConstraints(new VariableConstraints(true, null, null, TextFormat.ANY, BigDecimal.ZERO, null, 2,
                "BRL")));
        assertEquals("BRL", ok.constraints().currency());
        assertEquals(Integer.valueOf(2), ok.constraints().scale());
    }

    @Test
    @DisplayName("constantes: só não sensíveis, na forma canônica do tipo (decimal exato, sem arredondar; inteiro; data ISO), dentro das restrições; CPF nunca")
    void constantes() {
        Automation a = automation();
        VariableDefinition tentativas = a.declareVariable(VariableDraft.of("tentativas", VariableType.INTEGER).withOrigin(VariableOrigin.constant("0")));
        assertEquals("0", tentativas.origin().constant());
        assertEquals("7", a.declareVariable(VariableDraft.of("limite", VariableType.INTEGER).withOrigin(VariableOrigin.constant("007"))).origin().constant());
        VariableConstraints scale2 = new VariableConstraints(false, null, null, TextFormat.ANY, null, null, 2, null);
        assertEquals("1.50", a.declareVariable(VariableDraft.of("taxa", VariableType.DECIMAL).withConstraints(scale2).withOrigin(VariableOrigin.constant("1.5")))
                .origin().constant(), "escala explícita");
        assertEquals(ValidationProblem.VARIABLE_CONSTANT_INVALID, refused(() -> a.declareVariable(VariableDraft.of("taxa2", VariableType.DECIMAL)
                .withConstraints(scale2).withOrigin(VariableOrigin.constant("1.505")))), "nunca arredonda em silêncio");
        assertEquals(ValidationProblem.VARIABLE_CONSTANT_INVALID, refused(() -> a.declareVariable(VariableDraft.of("taxa3", VariableType.DECIMAL)
                .withOrigin(VariableOrigin.constant("1e3")))), "sem notação científica");
        assertEquals("2026-01-31", a.declareVariable(VariableDraft.of("data.corte", VariableType.DATE).withOrigin(VariableOrigin.constant("2026-01-31")))
                .origin().constant());
        assertEquals(ValidationProblem.VARIABLE_CONSTANT_INVALID, refused(() -> a.declareVariable(VariableDraft.of("data.ruim", VariableType.DATE)
                .withOrigin(VariableOrigin.constant("31/01/2026")))));
        assertEquals("00123", a.declareVariable(VariableDraft.of("codigo", VariableType.STRING).withOrigin(VariableOrigin.constant("00123"))).origin().constant(),
                "zeros à esquerda preservados em texto");
        assertEquals(ValidationProblem.VARIABLE_CONSTANT_FORBIDDEN, refused(() -> a.declareVariable(cpf().withName("cpf.fixo")
                .withOrigin(VariableOrigin.constant("00000000000")))), "dado pessoal nunca é constante");
        assertEquals(ValidationProblem.VARIABLE_CONSTANT_FORBIDDEN, refused(() -> a.declareVariable(VariableDraft.of("token", VariableType.STRING)
                .withSensitivity(Sensitivity.SECRET).withOrigin(VariableOrigin.constant("abc")))));
        assertEquals(ValidationProblem.VARIABLE_CONSTANT_LOOKS_SENSITIVE, refused(() -> a.declareVariable(VariableDraft.of("documento", VariableType.STRING)
                .withOrigin(VariableOrigin.constant("123.456.789-09")))), "um CPF em variável pública também é recusado");
        VariableConstraints max3 = new VariableConstraints(false, null, null, TextFormat.ANY, null, new BigDecimal("3"), null, null);
        assertEquals(ValidationProblem.VARIABLE_CONSTANT_INVALID, refused(() -> a.declareVariable(VariableDraft.of("t2", VariableType.INTEGER)
                .withConstraints(max3).withOrigin(VariableOrigin.constant("4")))));
    }

    @Test
    @DisplayName("escopo e origem STEP_OUTPUT: sessão e etapa produtora existentes; produtora dentro do escopo de sessão")
    void escopoEProdutora() {
        Automation a = automation();
        SessionId auth = a.sessions().get(0).id();
        SessionId cadastro = a.sessions().get(1).id();
        AutomationStep consulta = a.appendStep(cadastro, StepDraft.builder().name("Consultar cliente").actionType(ActionType.CLICK).build());
        assertEquals(ValidationProblem.VARIABLE_SCOPE_SESSION_NOT_FOUND, refused(() -> a.declareVariable(VariableDraft.of("x", VariableType.STRING)
                .withScope(VariableScope.session(SessionId.newId())))));
        assertEquals(ValidationProblem.VARIABLE_PRODUCER_NOT_FOUND, refused(() -> a.declareVariable(VariableDraft.of("x", VariableType.STRING)
                .withOrigin(VariableOrigin.stepOutput(StepId.newId())))));
        assertEquals(ValidationProblem.VARIABLE_PRODUCER_OUT_OF_SCOPE, refused(() -> a.declareVariable(VariableDraft.of("x", VariableType.STRING)
                .withScope(VariableScope.session(auth)).withOrigin(VariableOrigin.stepOutput(consulta.id())))));
        VariableDefinition id = a.declareVariable(VariableDraft.of("resultado.cliente_id", VariableType.STRING).withScope(VariableScope.session(cadastro))
                .withOrigin(VariableOrigin.stepOutput(consulta.id())));
        assertEquals(consulta.id(), id.origin().producer());
        assertTrue(id.scope().visibleIn(cadastro) && !id.scope().visibleIn(auth), "SESSION visível só na própria sessão");
    }

    @Test
    @DisplayName("renomear: atualiza TODAS as referências efetivas numa operação; original capturado e procedência intactos; recusa não muda nada")
    void renomear() {
        Automation a = automation();
        SessionId auth = a.sessions().get(0).id();
        VariableDefinition v = a.declareVariable(cpf());
        AutomationStep gravada = a.appendStep(auth, recorded("Preencher campo CPF", ActionType.FILL, "ev-1"));
        AutomationStep manual = a.appendStep(auth, StepDraft.builder().name("Preencher CPF de novo").actionType(ActionType.FILL).build());
        a.setStepParameters(gravada.id(), ActionParameters.withValue(new ValueSource.VariableReference("cliente.cpf")));
        a.setStepParameters(manual.id(), ActionParameters.withValue(new ValueSource.VariableReference("cliente.cpf")));
        a.declareVariable(VariableDraft.of("outro", VariableType.STRING));
        var baseline = gravada.baseline().orElseThrow();
        var provenance = gravada.provenance();
        assertEquals(List.of(gravada.id(), manual.id()), a.consumersOf(v.id()));
        long revision = a.revision();
        assertEquals(ValidationProblem.VARIABLE_NAME_IN_USE, refused(() -> a.renameVariable(v.id(), "OUTRO")));
        assertEquals(revision, a.revision(), "recusa: nada mudou");
        assertEquals("cliente.cpf", ((ValueSource.VariableReference) gravada.parameters().value().orElseThrow()).name());
        assertEquals(2, a.renameVariable(v.id(), "cliente.documento"));
        assertEquals(revision + 1, a.revision(), "UMA operação (um desfazer)");
        assertEquals(v.id(), a.variableNamed("cliente.documento").orElseThrow().id(), "mesma identidade");
        for (AutomationStep s : List.of(gravada, manual)) {
            assertEquals("cliente.documento", ((ValueSource.VariableReference) s.parameters().value().orElseThrow()).name());
        }
        assertEquals(baseline, gravada.baseline().orElseThrow(), "original capturado intacto");
        assertEquals(provenance, gravada.provenance(), "procedência intacta");
        assertTrue(gravada.modified(StepField.PARAMETERS), "a referência é uma alteração manual rastreável");
        assertEquals(List.of(gravada.id(), manual.id()), a.consumersOf(v.id()));
    }

    @Test
    @DisplayName("excluir: usada = recusada sem confirmação; com confirmação os vínculos são descartados (etapas ficam, sem valor; nada vira literal)")
    void excluir() {
        Automation a = automation();
        SessionId auth = a.sessions().get(0).id();
        VariableDefinition v = a.declareVariable(cpf());
        AutomationStep fill = a.appendStep(auth, StepDraft.builder().name("Preencher CPF").actionType(ActionType.FILL).build());
        a.setStepParameters(fill.id(), ActionParameters.withValue(new ValueSource.VariableReference("cliente.cpf")));
        long revision = a.revision();
        assertEquals(ValidationProblem.VARIABLE_IN_USE, refused(() -> a.removeVariable(v.id(), false)));
        assertEquals(revision, a.revision());
        assertEquals(1, a.removeVariable(v.id(), true));
        assertTrue(a.variables().isEmpty());
        assertEquals(1, a.stepCount(), "a etapa continua");
        assertTrue(fill.parameters().isEmpty(), "sem valor — nunca um literal");
        assertEquals(ModelVersion.CURRENT, a.modelVersion());
        VariableDefinition unused = a.declareVariable(VariableDraft.of("livre", VariableType.STRING));
        assertEquals(0, a.removeVariable(unused.id(), false));
    }

    @Test
    @DisplayName("desfazer/refazer pela fotografia: declaração, edição e renomeação voltam com as MESMAS identidades e referências")
    void historico() {
        Automation a = automation();
        SessionId auth = a.sessions().get(0).id();
        AutomationStep fill = a.appendStep(auth, StepDraft.builder().name("Preencher CPF").actionType(ActionType.FILL).build());
        Automation.Snapshot empty = a.snapshot();
        VariableDefinition v = a.declareVariable(cpf());
        a.setStepParameters(fill.id(), ActionParameters.withValue(new ValueSource.VariableReference("cliente.cpf")));
        Automation.Snapshot declared = a.snapshot();
        a.renameVariable(v.id(), "cliente.documento");
        Automation.Snapshot renamed = a.snapshot();
        a.restore(declared);
        assertEquals("cliente.cpf", a.variables().get(0).name());
        assertEquals("cliente.cpf", ((ValueSource.VariableReference) fill.parameters().value().orElseThrow()).name(), "referência volta junto");
        a.restore(empty);
        assertTrue(a.variables().isEmpty());
        assertEquals(ModelVersion.CURRENT, a.modelVersion());
        a.restore(renamed);
        assertEquals(v.id(), a.variables().get(0).id(), "mesma identidade");
        assertEquals("cliente.documento", ((ValueSource.VariableReference) fill.parameters().value().orElseThrow()).name());
    }

    @Test
    @DisplayName("editar sem diferença não conta revisão; editar com novo nome = renomeação atômica")
    void editar() {
        Automation a = automation();
        VariableDefinition v = a.declareVariable(cpf());
        long revision = a.revision();
        assertFalse(a.updateVariable(v.id(), cpf()));
        assertEquals(revision, a.revision());
        assertTrue(a.updateVariable(v.id(), cpf().withSemantic(SemanticType.BRAZILIAN_CPF, SemanticAuthority.PROPOSED_UNCONFIRMED)));
        VariableDefinition after = a.variable(v.id()).orElseThrow();
        assertEquals(SemanticAuthority.PROPOSED_UNCONFIRMED, after.authority());
        assertEquals(v.createdAt(), after.createdAt());
        assertNotEquals(v, after);
        assertEquals(ValidationProblem.VARIABLE_NOT_FOUND, refused(() -> a.updateVariable(new com.nekkora.studio.domain.variable.VariableId(
                java.util.UUID.randomUUID()), cpf())));
    }

    @Test
    @DisplayName("reconstrução: modelo 1.0.0 recusa variáveis; 1.1.0 reaplica as MESMAS regras (constante fora da forma canônica, nome repetido)")
    void reconstrucao() {
        Automation a = automation();
        VariableDefinition v = a.declareVariable(VariableDraft.of("tentativas", VariableType.INTEGER).withOrigin(VariableOrigin.constant("0")));
        AutomationRestoration.AutomationData base = new AutomationRestoration.AutomationData(a.id(), a.name(), a.description(), AutomationStatus.DRAFT,
                a.createdAt(), a.updatedAt(), ModelVersion.V1_1, a.revision(), List.of(), List.of(), List.of(v));
        assertEquals(List.of(v), AutomationRestoration.restore(base, clock).variables());
        AutomationRestoration.AutomationData oldModel = new AutomationRestoration.AutomationData(a.id(), a.name(), a.description(), AutomationStatus.DRAFT,
                a.createdAt(), a.updatedAt(), ModelVersion.CURRENT, a.revision(), List.of(), List.of(), List.of(v));
        assertEquals("MODEL_VERSION_UNSUPPORTED", assertThrows(AutomationRestoration.RestoreException.class,
                () -> AutomationRestoration.restore(oldModel, clock)).reason());
        VariableDefinition notCanonical = new VariableDefinition(v.id(), v.name(), v.type(), v.semantic(), v.authority(), v.purpose(), v.scope(),
                VariableOrigin.constant("00"), v.sensitivity(), v.constraints(), v.createdAt(), v.updatedAt());
        assertEquals("TEXT_NOT_NORMALIZED", assertThrows(AutomationRestoration.RestoreException.class, () -> AutomationRestoration.restore(
                new AutomationRestoration.AutomationData(a.id(), a.name(), "", AutomationStatus.DRAFT, a.createdAt(), a.updatedAt(), ModelVersion.V1_1, 1,
                        List.of(), List.of(), List.of(notCanonical)), clock)).reason());
        VariableDefinition twin = new VariableDefinition(new com.nekkora.studio.domain.variable.VariableId(java.util.UUID.randomUUID()), "TENTATIVAS",
                v.type(), v.semantic(), v.authority(), v.purpose(), v.scope(), v.origin(), v.sensitivity(), v.constraints(), v.createdAt(), v.updatedAt());
        assertEquals("VARIABLE_NAME_IN_USE", assertThrows(AutomationRestoration.RestoreException.class, () -> AutomationRestoration.restore(
                new AutomationRestoration.AutomationData(a.id(), a.name(), "", AutomationStatus.DRAFT, a.createdAt(), a.updatedAt(), ModelVersion.V1_1, 1,
                        List.of(), List.of(), List.of(v, twin)), clock)).reason());
    }
}
