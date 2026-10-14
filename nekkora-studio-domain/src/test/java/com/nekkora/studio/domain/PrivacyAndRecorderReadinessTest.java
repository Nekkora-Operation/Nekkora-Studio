package com.nekkora.studio.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.OptionChoice;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.origin.StepOrigin;
import com.nekkora.studio.domain.origin.StepProvenance;
import com.nekkora.studio.domain.port.StepIntake;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Privacidade (P7, seção 15) e preparação para o Recorder (seção 23, CA-019). */
class PrivacyAndRecorderReadinessTest {
    private final AutomationCatalog catalog = new AutomationCatalog(Clock.systemUTC());

    @Test
    @DisplayName("S001-013 identidades internas não se revelam em toString")
    void idsNaoVazamPorToString() {
        Automation a = MissionExample.build(catalog);
        LogicalSession s = a.sessions().get(0);
        AutomationStep step = s.steps().get(0);
        for (Object o : new Object[] {a.id(), s.id(), step.id(), a, s, step, CorrelationKeys.none().with(CorrelationKeys.Key.TARGET_ID, "tgt-123abc")}) {
            String text = String.valueOf(o);
            assertFalse(DisplaySafety.UUID.matcher(text).find(), text);
            assertFalse(text.contains("tgt-123abc"), text);
        }
        assertEquals(a.id(), AutomationId.parse(a.id().value().toString()), "o valor continua disponível para correlação");
    }

    @Test
    @DisplayName("CA-019 uma etapa RECORDED entra pela mesma operação de domínio, sem UI")
    void etapaGravadaPelaPorta() {
        Automation a = catalog.create("Gravada", null);
        LogicalSession s = a.addSession("Login", null);
        StepIntake intake = (automationId, sessionId, draft) -> catalog.require(automationId).appendStep(sessionId, draft);
        CorrelationKeys keys = CorrelationKeys.none()
                .with(CorrelationKeys.Key.RECORDING_ID, "rec-001")
                .with(CorrelationKeys.Key.TARGET_ID, "tgt-9f2c");
        ElementReference user = ElementReference.builder().accessibleName("Usuário").semanticKind("TEXT_INPUT").tag("input")
                .htmlId("usuario").privacyPolicy(DisplaySafety.KNOWN_PRODUCER_POLICY)
                .addLocator(new LocatorCandidate(LocatorStrategy.LABEL, "campo \"Usuário\"", "", LocatorStability.HEURISTIC_STABLE, 1, true))
                .preferLastLocator().correlation(keys).build();
        AutomationStep step = intake.appendStep(a.id(), s.id(), StepDraft.builder()
                .name("Preencher usuário").actionType(ActionType.FILL)
                .provenance(StepProvenance.of(StepOrigin.RECORDED, keys))
                .element(user)
                .parameters(ActionParameters.withValue(new ValueSource.CredentialReference("sistema.usuario")))
                .context(new StepContext("Navegador", "Login", "https://sistema.exemplo/login"))
                .build());
        assertEquals(StepOrigin.RECORDED, step.provenance().origin());
        assertEquals("rec-001", step.provenance().correlation().get(CorrelationKeys.Key.RECORDING_ID).orElseThrow());
        assertEquals(1, a.positionOf(step.id()).orElseThrow().number());
        assertEquals("Credencial sistema.usuario", step.parameters().value().orElseThrow().display());
    }

    @Test
    @DisplayName("15.2 campo sensível nunca recebe valor literal; referência é aceita")
    void campoSensivel() {
        Automation a = catalog.create("Login", null);
        LogicalSession s = a.addSession("Login", null);
        ElementReference password = ElementReference.builder().accessibleName("Senha").semanticKind("PASSWORD_INPUT").tag("input")
                .sensitive(true).htmlId("senha").privacyPolicy(DisplaySafety.KNOWN_PRODUCER_POLICY).build();
        DomainValidationException e = assertThrows(DomainValidationException.class, () -> a.appendStep(s.id(), StepDraft.builder()
                .name("Preencher senha").actionType(ActionType.FILL).element(password)
                .parameters(ActionParameters.withValue(new ValueSource.LiteralValue("minhaSenha"))).build()));
        assertEquals(ValidationProblem.LITERAL_ON_SENSITIVE_TARGET, e.problem());
        assertTrue(s.isEmpty());
        AutomationStep ok = a.appendStep(s.id(), StepDraft.builder()
                .name("Preencher senha").actionType(ActionType.FILL).element(password)
                .parameters(ActionParameters.withValue(new ValueSource.CredentialReference("sistema.senha"))).build());
        assertTrue(ok.element().orElseThrow().displayableHtmlId().isEmpty(), "atributos de elemento sensível não são exibidos");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "contato ana@x.com",
            "CPF 123.456.789-09",
            "4111 1111 1111 1111",
            "Bearer abcdefghijklmnopqrstuvwxyz012345",
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abcdefgh"})
    @DisplayName("15.2 literal com aparência de dado sensível é recusado")
    void literalSensivel(String text) {
        assertEquals(ValidationProblem.LITERAL_LOOKS_SENSITIVE,
                assertThrows(DomainValidationException.class, () -> new ValueSource.LiteralValue(text)).problem());
    }

    @Test
    @DisplayName("parâmetros incompatíveis com o tipo de ação são recusados")
    void parametrosIncompativeis() {
        Automation a = catalog.create("P", null);
        LogicalSession s = a.addSession("S", null);
        assertEquals(ValidationProblem.VALUE_NOT_ACCEPTED, assertThrows(DomainValidationException.class, () -> a.appendStep(s.id(),
                StepDraft.builder().name("Clicar").actionType(ActionType.CLICK)
                        .parameters(ActionParameters.withValue(new ValueSource.LiteralValue("x"))).build())).problem());
        assertEquals(ValidationProblem.OPTION_NOT_ACCEPTED, assertThrows(DomainValidationException.class, () -> a.appendStep(s.id(),
                StepDraft.builder().name("Preencher").actionType(ActionType.FILL)
                        .parameters(ActionParameters.withOption(new OptionChoice("SP", "SP"))).build())).problem());
        AutomationStep fill = a.appendStep(s.id(), StepDraft.builder().name("Pesquisar").actionType(ActionType.FILL)
                .parameters(ActionParameters.withValue(new ValueSource.VariableReference("cliente.nome"))).build());
        assertEquals(ValidationProblem.VALUE_NOT_ACCEPTED, assertThrows(DomainValidationException.class,
                () -> a.updateStepBasics(fill.id(), "Pesquisar", "", ActionType.CLICK)).problem());
        assertEquals(ActionType.FILL, fill.actionType());
        assertThrows(DomainValidationException.class, () -> new ValueSource.VariableReference("tem espaço"));
    }

    @Test
    @DisplayName("15.1 id/name HTML só são apresentáveis com política reconhecida e texto seguro")
    void atributosHtml() {
        ElementReference noPolicy = ElementReference.builder().htmlId("btn-login").build();
        assertTrue(noPolicy.displayableHtmlId().isEmpty(), "sem política do produtor, não exibe");
        ElementReference ok = ElementReference.builder().htmlId("btn-login").htmlName("username")
                .privacyPolicy(DisplaySafety.KNOWN_PRODUCER_POLICY).build();
        assertEquals("btn-login", ok.displayableHtmlId().orElseThrow());
        assertEquals("username", ok.displayableHtmlName().orElseThrow());
        ElementReference dynamic = ElementReference.builder().htmlId("ember-" + UUID.randomUUID()).htmlName("ana@x.com")
                .privacyPolicy(DisplaySafety.KNOWN_PRODUCER_POLICY).build();
        assertTrue(dynamic.displayableHtmlId().isEmpty());
        assertTrue(dynamic.displayableHtmlName().isEmpty());
        assertTrue(dynamic.htmlId().isPresent(), "o dado técnico permanece no domínio para correlação");
    }

    @Test
    @DisplayName("localizador preferencial precisa estar validado")
    void preferencialValidado() {
        assertThrows(IllegalArgumentException.class, () -> ElementReference.builder()
                .addLocator(new LocatorCandidate(LocatorStrategy.TEXT, "texto", "", LocatorStability.UNKNOWN, 3, true))
                .preferLastLocator().build());
        assertTrue(ElementReference.builder()
                .addLocator(new LocatorCandidate(LocatorStrategy.TEXT, "texto", "", LocatorStability.UNKNOWN, 3, true))
                .build().preferredLocator().isEmpty());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Preencher usuário|true",
            "Clicar em Entrar|true",
            "btn-login|true",
            "Pedido 2024 de clientes|true",
            "3f2b8c1e-9a4d-4e7b-8c2a-1b3c4d5e6f70|false",
            "tgt-a1b2c3|false",
            "doc:frame-1|false",
            "a3f9c2e81b7d4c6e|false",
            "AbCdEf0123456789XyZaBcDe9|false",
            "ana@x.com|false"})
    @DisplayName("DisplaySafety separa texto de usuário de identificadores internos e dados sensíveis")
    void displaySafety(String text, boolean safe) {
        assertEquals(safe, DisplaySafety.isSafeToDisplay(text), text);
    }

    @Test
    @DisplayName("chaves de correlação são imutáveis")
    void correlacaoImutavel() {
        CorrelationKeys k = CorrelationKeys.of(Map.of(CorrelationKeys.Key.NODE_ID, "n-1"));
        assertThrows(UnsupportedOperationException.class, () -> k.asMap().clear());
        CorrelationKeys k2 = k.with(CorrelationKeys.Key.FRAME_ID, "f-1");
        assertEquals(1, k.asMap().size());
        assertEquals(2, k2.asMap().size());
    }
}
