package com.nekkora.studio.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.origin.StepOrigin;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Matriz STUDIO-001 — regras do modelo de domínio, sem Swing. */
class AutomationModelTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);
    private final AutomationCatalog catalog = new AutomationCatalog(clock);

    @Test
    @DisplayName("S001-003 criar automação com nome e descrição atualiza o domínio")
    void criarAutomacao() {
        Automation a = catalog.create("  Consulta de clientes ", "Consulta o cadastro de clientes.");
        assertEquals("Consulta de clientes", a.name());
        assertEquals("Consulta o cadastro de clientes.", a.description());
        assertEquals(AutomationStatus.DRAFT, a.status());
        assertEquals(ModelVersion.CURRENT, a.modelVersion());
        assertEquals(clock.instant(), a.createdAt());
        assertTrue(a.sessions().isEmpty());
        assertEquals(List.of(a), catalog.all());
        assertSame(a, catalog.require(a.id()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "linha\nquebrada", "tab\tno meio"})
    @DisplayName("S001-004 nome inválido é recusado com mensagem para o usuário")
    void nomeInvalido(String name) {
        DomainValidationException e = assertThrows(DomainValidationException.class, () -> catalog.create(name, null));
        assertEquals(ValidationProblem.Field.NAME, e.problem().field());
        assertFalse(e.getMessage().isBlank());
        assertTrue(catalog.isEmpty(), "nada pode ser criado quando a validação falha");
    }

    @Test
    @DisplayName("S001-004 nome longo demais, descrição longa demais e nome repetido")
    void outrosLimites() {
        assertEquals(ValidationProblem.NAME_TOO_LONG,
                assertThrows(DomainValidationException.class, () -> catalog.create("x".repeat(Texts.NAME_MAX + 1), null)).problem());
        assertEquals(ValidationProblem.DESCRIPTION_TOO_LONG,
                assertThrows(DomainValidationException.class, () -> catalog.create("Ok", "y".repeat(Texts.DESCRIPTION_MAX + 1))).problem());
        catalog.create("Faturas", null);
        assertEquals(ValidationProblem.AUTOMATION_NAME_IN_USE,
                assertThrows(DomainValidationException.class, () -> catalog.create(" faturas ", null)).problem());
        assertEquals(1, catalog.size());
    }

    @Test
    @DisplayName("S001-005 duas automações têm estados isolados")
    void isolamentoEntreAutomacoes() {
        Automation a = catalog.create("A", null);
        Automation b = catalog.create("B", null);
        LogicalSession sa = a.addSession("Login", null);
        a.appendStep(sa.id(), StepDraft.manual("Preencher usuário", ActionType.FILL, null));
        assertEquals(1, a.stepCount());
        assertEquals(0, b.stepCount());
        assertTrue(b.sessions().isEmpty());
        assertTrue(b.session(sa.id()).isEmpty());
        assertEquals(ValidationProblem.SESSION_NOT_FOUND,
                assertThrows(DomainValidationException.class,
                        () -> b.appendStep(sa.id(), StepDraft.manual("x", ActionType.CLICK, null))).problem());
        assertEquals(List.of(a, b), catalog.all());
    }

    @Test
    @DisplayName("S001-006 criar sessão associa à automação certa")
    void criarSessao() {
        Automation a = catalog.create("A", null);
        LogicalSession s = a.addSession("Autenticação", "Entrar no sistema");
        assertEquals("Autenticação", s.name());
        assertEquals("Entrar no sistema", s.description());
        assertEquals(1, a.sessionNumber(s.id()));
        assertSame(s, a.session(s.id()).orElseThrow());
        assertTrue(s.isEmpty());
    }

    @Test
    @DisplayName("S001-007 sessões Login, Consulta e Resultado preservam a ordem")
    void ordemDasSessoes() {
        Automation a = catalog.create("A", null);
        LogicalSession login = a.addSession("Login", null);
        LogicalSession consulta = a.addSession("Consulta", null);
        LogicalSession resultado = a.addSession("Resultado", null);
        assertEquals(List.of("Login", "Consulta", "Resultado"), a.sessions().stream().map(LogicalSession::name).toList());
        assertEquals(1, a.sessionNumber(login.id()));
        assertEquals(2, a.sessionNumber(consulta.id()));
        assertEquals(3, a.sessionNumber(resultado.id()));
        assertEquals(ValidationProblem.SESSION_NAME_IN_USE,
                assertThrows(DomainValidationException.class, () -> a.addSession("LOGIN", null)).problem());
    }

    @Test
    @DisplayName("S001-006 renomear sessão (alteração básica do nome)")
    void renomearSessao() {
        Automation a = catalog.create("A", null);
        LogicalSession s = a.addSession("Login", null);
        a.addSession("Consulta", null);
        long rev = a.revision();
        a.renameSession(s.id(), "Autenticação");
        assertEquals("Autenticação", s.name());
        assertTrue(a.revision() > rev);
        assertThrows(DomainValidationException.class, () -> a.renameSession(s.id(), " "));
        assertEquals(ValidationProblem.SESSION_NAME_IN_USE,
                assertThrows(DomainValidationException.class, () -> a.renameSession(s.id(), "consulta")).problem());
        a.renameSession(s.id(), "autenticação"); // a própria sessão pode mudar a caixa do nome
        assertEquals("autenticação", s.name());
    }

    @Test
    @DisplayName("S001-008 adicionar etapa manual à sessão")
    void adicionarEtapa() {
        Automation a = catalog.create("A", null);
        LogicalSession s = a.addSession("Login", null);
        AutomationStep step = a.appendStep(s.id(), StepDraft.manual("Preencher usuário", ActionType.FILL, "Campo do login"));
        assertEquals("Preencher usuário", step.name());
        assertEquals(ActionType.FILL, step.actionType());
        assertEquals(StepOrigin.MANUAL, step.provenance().origin());
        assertTrue(step.provenance().correlation().isEmpty());
        assertTrue(step.element().isEmpty(), "etapa manual não exige elemento");
        assertTrue(step.parameters().isEmpty());
        assertEquals(List.of(step), s.steps());
        assertSame(s, a.sessionOf(step.id()).orElseThrow());
    }

    @Test
    @DisplayName("S001-008 etapa sem tipo, sem sessão ou com sessão inexistente é recusada")
    void etapaInvalida() {
        Automation a = catalog.create("A", null);
        LogicalSession s = a.addSession("Login", null);
        assertEquals(ValidationProblem.ACTION_TYPE_REQUIRED,
                assertThrows(DomainValidationException.class, () -> a.appendStep(s.id(), StepDraft.manual("x", null, null))).problem());
        assertEquals(ValidationProblem.SESSION_REQUIRED,
                assertThrows(DomainValidationException.class, () -> a.appendStep(null, StepDraft.manual("x", ActionType.CLICK, null))).problem());
        assertEquals(ValidationProblem.SESSION_NOT_FOUND,
                assertThrows(DomainValidationException.class,
                        () -> a.appendStep(SessionId.newId(), StepDraft.manual("x", ActionType.CLICK, null))).problem());
        assertEquals(ValidationProblem.NAME_REQUIRED,
                assertThrows(DomainValidationException.class, () -> a.appendStep(s.id(), StepDraft.manual(" ", ActionType.CLICK, null))).problem());
        assertTrue(s.isEmpty());
    }

    @Test
    @DisplayName("S001-009 sequência explícita e numeração contínua calculada")
    void sequencia() {
        Automation a = MissionExample.build(catalog);
        List<StepPosition> seq = a.sequence();
        assertEquals(7, seq.size());
        for (int i = 0; i < seq.size(); i++) assertEquals(i + 1, seq.get(i).number());
        List<String> names = seq.stream().map(p -> a.step(p.stepId()).orElseThrow().name()).toList();
        assertEquals(MissionExample.STEP_NAMES, names);
        StepPosition quarta = seq.get(3);
        assertEquals(2, quarta.sessionNumber());
        assertEquals(1, quarta.numberInSession());
        assertEquals("Abrir menu Clientes", a.step(quarta.stepId()).orElseThrow().name());
    }

    @Test
    @DisplayName("S001-009 numeração visual não é identidade: renomear não altera ids nem ordem")
    void numeracaoNaoEIdentidade() {
        Automation a = MissionExample.build(catalog);
        List<StepPosition> before = a.sequence();
        StepId second = before.get(1).stepId();
        a.updateStepBasics(second, "Preencher senha do usuário", "", ActionType.FILL);
        assertEquals(before, a.sequence());
        assertEquals(2, a.positionOf(second).orElseThrow().number());
        assertNotEquals(before.get(0).stepId(), before.get(1).stepId());
    }

    @Test
    @DisplayName("S001-010 etapa da sessão A não aparece na sessão B")
    void isolamentoEntreSessoes() {
        Automation a = catalog.create("A", null);
        LogicalSession sa = a.addSession("A", null);
        LogicalSession sb = a.addSession("B", null);
        AutomationStep step = a.appendStep(sa.id(), StepDraft.manual("Clicar em Entrar", ActionType.CLICK, null));
        assertEquals(List.of(step), sa.steps());
        assertTrue(sb.steps().isEmpty());
        assertEquals(0, sb.positionOf(step.id()));
        assertTrue(sb.step(step.id()).isEmpty());
    }

    @Test
    @DisplayName("S001-014 tipos distintos são preservados por etapa")
    void tiposDeAcao() {
        Automation a = catalog.create("A", null);
        LogicalSession s = a.addSession("S", null);
        for (ActionType t : ActionType.values()) a.appendStep(s.id(), StepDraft.manual("Etapa " + t.label(), t, null));
        assertEquals(List.of(ActionType.values()), s.steps().stream().map(AutomationStep::actionType).toList());
        for (ActionType t : ActionType.values()) {
            assertFalse(t.label().isBlank());
            assertFalse(t.description().isBlank());
        }
        assertFalse(ActionType.manualChoices().contains(ActionType.UNKNOWN));
    }

    @Test
    @DisplayName("edição básica de etapa atualiza nome, descrição e tipo e valida")
    void edicaoBasica() {
        Automation a = catalog.create("A", null);
        LogicalSession s = a.addSession("S", null);
        AutomationStep step = a.appendStep(s.id(), StepDraft.manual("Clicar", ActionType.CLICK, null));
        a.updateStepBasics(step.id(), "Clicar em Pesquisar", "Botão principal", ActionType.CLICK);
        assertEquals("Clicar em Pesquisar", step.name());
        assertEquals("Botão principal", step.description());
        assertThrows(DomainValidationException.class, () -> a.updateStepBasics(step.id(), "", "", ActionType.CLICK));
        assertEquals("Clicar em Pesquisar", step.name(), "falha de validação não altera nada");
        assertEquals(ValidationProblem.STEP_NOT_FOUND,
                assertThrows(DomainValidationException.class,
                        () -> a.updateStepBasics(StepId.newId(), "x", "", ActionType.CLICK)).problem());
    }

    @Test
    @DisplayName("revisão e data de atualização mudam a cada operação")
    void metadados() {
        Clock[] now = {Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC)};
        Clock moving = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now[0].instant();
            }
        };
        Automation a = Automation.create("A", null, moving);
        assertEquals(0, a.revision());
        now[0] = Clock.offset(now[0], java.time.Duration.ofMinutes(5));
        a.addSession("S", null);
        assertEquals(1, a.revision());
        assertEquals(Instant.parse("2026-10-14T12:05:00Z"), a.updatedAt());
        assertEquals(Instant.parse("2026-10-14T12:00:00Z"), a.createdAt());
    }

    @Test
    @DisplayName("coleções do domínio são somente leitura para quem está fora do agregado")
    void encapsulamento() {
        Automation a = MissionExample.build(catalog);
        assertThrows(UnsupportedOperationException.class, () -> a.sessions().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.sessions().get(0).steps().clear());
        assertThrows(UnsupportedOperationException.class, () -> catalog.all().clear());
    }
}
