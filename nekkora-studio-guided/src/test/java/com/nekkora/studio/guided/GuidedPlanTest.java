package com.nekkora.studio.guided;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.element.ManualLocator;
import com.nekkora.studio.domain.privacy.CapturePrivacy;
import com.nekkora.studio.validation.AutomationValidator;
import com.nekkora.studio.validation.RuleSet;
import com.nekkora.studio.validation.ValidationReport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * STUDIO-009 G1 — plano intermediário: conteúdo do caso de laboratório, schema = código, fixture congelada,
 * determinismo, ausência de valores e casos negativos (sem alvo, só manual, só descritor, frame/shadow, tipo incomum,
 * sensível, tecla fora do vocabulário, bloqueio estático).
 */
class GuidedPlanTest {
    private static final Path DIR = Path.of("src/test/resources/contracts/guided-debug-plan/1.0.0");
    private static final Path FIXTURE = DIR.resolve("fixtures/caso-consulta-registro.json");

    static GuidedPlan plan(Automation a) {
        ValidationReport r = new AutomationValidator(RuleSet.V1_1).validate(a);
        return GuidedPlan.of(a, r);
    }

    private static Map<String, Object> schema() throws Exception {
        return Json.parseObject(Files.readString(DIR.resolve("guided-debug-plan.schema.json"), StandardCharsets.UTF_8));
    }

    /** UUIDs e a revisão trocados por marcadores na ordem de aparição (a fixture não depende de ids aleatórios). */
    static String normalized(String json) {
        Map<String, String> ids = new LinkedHashMap<>();
        Matcher m = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matcher(json);
        StringBuilder b = new StringBuilder();
        while (m.find()) {
            String n = ids.computeIfAbsent(m.group(), k -> String.format("00000000-0000-4000-8000-%012d", ids.size() + 1));
            m.appendReplacement(b, n);
        }
        m.appendTail(b);
        return b.toString().replaceAll("sha256:[0-9a-f]{64}", "sha256:" + "0".repeat(64));
    }

    @SuppressWarnings("unchecked")
    private static List<String> enumAt(Map<String, Object> schema, String def, String prop) {
        Map<String, Object> d = Json.object(Json.object(schema, "$defs"), def);
        Map<String, Object> p = Json.object(Json.object(d, "properties"), prop);
        Object e = p.containsKey("items") ? Json.object(p, "items").get("enum") : p.get("enum");
        return ((List<Object>) e).stream().map(String::valueOf).toList();
    }

    private static <E extends Enum<E>> List<String> names(E[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    @Test
    @DisplayName("schema 1.0.0 = código: vocabulários do plano, exclusões e versão")
    void schemaIgualAoCodigo() throws Exception {
        Map<String, Object> s = schema();
        assertEquals(names(GuidedPlan.IdentitySource.values()), enumAt(s, "Target", "identitySource"));
        assertEquals(names(GuidedPlan.TargetState.values()), enumAt(s, "Target", "state"));
        assertEquals(names(GuidedPlan.Applicability.values()), enumAt(s, "Step", "applicability"));
        assertEquals(names(GuidedPlan.Criterion.values()), enumAt(s, "Step", "criteria"));
        assertEquals(names(GuidedPlan.Uncertainty.values()), enumAt(s, "Step", "uncertainties"));
        assertEquals(names(GuidedPlan.ValueKind.values()), enumAt(s, "Intent", "value"));
        assertEquals(names(ActionType.values()), enumAt(s, "Intent", "actionType"));
        assertEquals(names(LocatorStrategy.values()), enumAt(s, "Locator", "strategy"));
        assertEquals(names(LocatorStability.values()), enumAt(s, "Locator", "stability"));
        assertEquals(names(AccessStep.Type.values()), enumAt(s, "PathStep", "type"));
        assertEquals(names(com.nekkora.studio.domain.variable.VariableOrigin.Kind.values()), enumAt(s, "Variable", "origin"));
        Map<String, Object> plan = Json.object(Json.object(Json.object(s, "$defs"), "Plan"), "properties");
        assertEquals(names(com.nekkora.studio.validation.ValidationStatus.values()),
                Json.array(Json.object(Json.object(Json.object(plan, "staticValidation"), "properties"), "status"), "enum"));
        assertEquals(GuidedPlan.SCHEMA, Json.object(plan, "schema").get("const"));
        assertEquals(GuidedPlan.VERSION, Json.object(plan, "version").get("const"));
        assertEquals(GuidedPlan.EXCLUSIONS, Json.array(Json.object(Json.object(plan, "exclusions"), "items"), "enum"));
        @SuppressWarnings("unchecked")
        List<Object> keyAlternatives = (List<Object>) Json.object(Json.object(Json.object(Json.object(s, "$defs"), "Intent"), "properties"), "key").get("anyOf");
        assertEquals(GuidedPlan.KEY_VOCABULARY, java.util.Set.copyOf(Json.array(Json.object(Map.of("k", keyAlternatives.get(1)), "k"), "enum")));
    }

    @Test
    @DisplayName("caso de laboratório: conforme ao schema e IDÊNTICO à fixture congelada; mesmo estado → mesmos bytes e mesma revisão")
    void fixtureCongelada() throws Exception {
        GuidedPlan p = plan(LabCase.consulta());
        assertEquals(List.of(), new Schemas.SchemaValidator(schema()).validate(Json.parse(p.toJson()), "Plan"));
        String normalized = normalized(p.toJson());
        if (Boolean.getBoolean("nekkora.fixtures009.regenerate")) {
            Files.createDirectories(FIXTURE.getParent());
            Files.writeString(FIXTURE, normalized, StandardCharsets.UTF_8);
        }
        assertEquals(Files.readString(FIXTURE, StandardCharsets.UTF_8).replace("\r\n", "\n"), normalized,
                "mudança no contrato exige nova versão e regeneração revisada (-Dnekkora.fixtures009.regenerate=true)");
        Automation a = LabCase.consulta();
        assertEquals(plan(a).toJson(), plan(a).toJson(), "determinístico");
        assertEquals(plan(a).planRevision(), plan(a).planRevision());
        assertTrue(plan(a).toJson().contains("\"planRevision\": \"" + plan(a).planRevision() + "\""));
    }

    @Test
    @DisplayName("conteúdo: navegar sem alvo; preencher e clicar inspecionáveis pela identidade registrada; aguardar só pelo descritor")
    void conteudo() {
        GuidedPlan p = plan(LabCase.consulta());
        assertEquals(4, p.steps().size());
        GuidedPlan.Step navegar = p.steps().get(0);
        assertEquals(GuidedPlan.Applicability.NO_TARGET_REQUIRED, navegar.applicability());
        assertEquals(List.of(), navegar.criteria());
        assertTrue(navegar.uncertainties().contains(GuidedPlan.Uncertainty.VALUE_WITHHELD), "o endereço literal fica retido");

        GuidedPlan.Step preencher = p.steps().get(1);
        assertEquals(GuidedPlan.Applicability.INSPECT_TARGET, preencher.applicability());
        assertEquals(GuidedPlan.IdentitySource.TARGET_ID, preencher.target().identitySource());
        assertEquals(GuidedPlan.ValueKind.VARIABLE, preencher.intent().value());
        assertEquals("registro.codigo", preencher.intent().variableName());
        assertEquals(List.of(GuidedPlan.Criterion.CONTEXT_AUTHORIZED, GuidedPlan.Criterion.TARGET_IDENTITY_RESOLVED, GuidedPlan.Criterion.TARGET_UNIQUE,
                GuidedPlan.Criterion.TARGET_VISIBLE, GuidedPlan.Criterion.TARGET_ENABLED, GuidedPlan.Criterion.TARGET_EDITABLE,
                GuidedPlan.Criterion.TARGET_KIND_MATCHES_ACTION), preencher.criteria());
        assertEquals(List.of(), preencher.uncertainties());
        assertTrue(preencher.semanticUncertainties().contains(com.nekkora.studio.semantics.SemanticContract.UncertaintyCode.INPUT_VALUE_UNKNOWN),
                "a entrada não tem valor conhecido antes da execução");
        assertEquals(0, preencher.target().preferredLocator());
        assertTrue(preencher.target().locators().get(0).validatedAtCapture());

        GuidedPlan.Step clicar = p.steps().get(2);
        assertEquals(GuidedPlan.Applicability.INSPECT_TARGET, clicar.applicability());
        assertEquals("[data-testid=\"consultar\"]", clicar.target().locators().get(0).expression());

        GuidedPlan.Step aguardar = p.steps().get(3);
        assertEquals(GuidedPlan.IdentitySource.DESCRIPTOR, aguardar.target().identitySource());
        assertTrue(aguardar.uncertainties().containsAll(List.of(GuidedPlan.Uncertainty.IDENTITY_FROM_DESCRIPTOR_ONLY,
                GuidedPlan.Uncertainty.NO_VALIDATED_LOCATOR)));
        assertEquals(1, p.variables().size());
        assertEquals("INPUT", p.variables().get(0).origin());
    }

    @Test
    @DisplayName("sem valores: nenhum literal, finalidade, nome de etapa/sessão/automação, apelido de credencial nem chave de correlação no JSON")
    void semValores() {
        Automation a = LabCase.consulta();
        LogicalSession s = a.sessions().get(0);
        a.appendStep(s.id(), StepDraft.builder().name("Entrar com a credencial do portal").actionType(ActionType.FILL)
                .parameters(ActionParameters.withValue(new ValueSource.CredentialReference("portal.senha.lab"))).build());
        a.appendStep(s.id(), StepDraft.builder().name("Termo livre").actionType(ActionType.FILL)
                .parameters(ActionParameters.withValue(new ValueSource.LiteralValue("termo-ficticio-xyz"))).build());
        String json = plan(a).toJson();
        for (String forbidden : List.of(LabCase.LITERAL_URL, "termo-ficticio-xyz", "portal.senha.lab", "Código fictício do registro consultado",
                "Abrir a consulta", "Preencher código do registro", "Consulta de registro (laboratório)", "Caso vertical", "tgt-3f6c1a", "tgt-8d02b7",
                "rec-lab-009", "ev-1", "n-41", "cap-7", "Entrar com a credencial")) {
            assertFalse(json.contains(forbidden), "o plano não pode conter: " + forbidden);
        }
        GuidedPlan.Step credencial = plan(a).steps().get(4);
        assertEquals(GuidedPlan.ValueKind.CREDENTIAL, credencial.intent().value());
        assertNull(credencial.intent().variableName());
        assertTrue(credencial.semanticUncertainties().contains(com.nekkora.studio.semantics.SemanticContract.UncertaintyCode.CREDENTIAL_UNRESOLVED));
        assertTrue(plan(a).steps().get(5).uncertainties().contains(GuidedPlan.Uncertainty.VALUE_WITHHELD));
    }

    @Test
    @DisplayName("negativos: ação com alvo esperado sem referência → TARGET_MISSING, sem critérios; localizador manual não verificado não vira identidade")
    void semAlvo() {
        Automation a = LabCase.consulta();
        LogicalSession s = a.sessions().get(0);
        AutomationStep vazio = a.appendStep(s.id(), StepDraft.builder().name("Clicar em algo").actionType(ActionType.CLICK).build());
        AutomationStep aguardar = a.sessions().get(0).steps().get(3);
        assertThrows(com.nekkora.studio.domain.DomainValidationException.class, () -> a.addManualLocator(vazio.id(),
                new ManualLocator(ManualLocator.Syntax.CSS, "#consultar", "", Instant.parse("2026-10-17T12:00:00Z"))), "o domínio exige referência");
        a.addManualLocator(aguardar.id(), new ManualLocator(ManualLocator.Syntax.CSS, "#resultado", "", Instant.parse("2026-10-17T12:00:00Z")));
        GuidedPlan p = plan(a);
        GuidedPlan.Step v = p.step(vazio.id()).orElseThrow();
        assertEquals(GuidedPlan.Applicability.TARGET_MISSING, v.applicability());
        assertNull(v.target());
        assertTrue(v.uncertainties().contains(GuidedPlan.Uncertainty.NO_TARGET_REFERENCE));
        assertEquals(List.of(), v.criteria(), "nada a verificar no navegador: só Corrigir");
        GuidedPlan.Step m = p.step(aguardar.id()).orElseThrow();
        assertEquals(GuidedPlan.IdentitySource.DESCRIPTOR, m.target().identitySource(), "o manual NÃO promove a identidade");
        assertTrue(m.uncertainties().containsAll(List.of(GuidedPlan.Uncertainty.MANUAL_LOCATORS_UNVERIFIED, GuidedPlan.Uncertainty.IDENTITY_FROM_DESCRIPTOR_ONLY,
                GuidedPlan.Uncertainty.NO_VALIDATED_LOCATOR)));
        assertEquals("#resultado", m.target().manualLocators().get(0).expression());
        assertTrue(p.toJson().contains("\"verification\": \"NOT_VERIFIED\""));
    }

    @Test
    @DisplayName("negativos: alvo em iframe e shadow root exige critérios de alcance; preencher um botão é incomum; campo sensível declarado")
    void alcanceTipoSensivel() {
        Automation a = LabCase.consulta();
        LogicalSession s = a.sessions().get(0);
        ElementReference emFrame = ElementReference.builder().semanticKind("PASSWORD_INPUT").tag("input").sensitive(true).privacyPolicy(CapturePrivacy.VERSION)
                .addLocator(new LocatorCandidate(LocatorStrategy.NAME, "", "[name=\"senha\"]", LocatorStability.HEURISTIC_STABLE, 1, true)).preferLastLocator()
                .addAccessStep(AccessStep.of(AccessStep.Type.MAIN_DOCUMENT, "documento principal"))
                .addAccessStep(AccessStep.of(AccessStep.Type.IFRAME, "iframe#login"))
                .addAccessStep(AccessStep.of(AccessStep.Type.SHADOW_ROOT, "login-box"))
                .correlation(CorrelationKeys.of(Map.of(CorrelationKeys.Key.TARGET_ID, "tgt-0a1b2c"))).build();
        AutomationStep senha = a.appendStep(s.id(), StepDraft.builder().name("Senha").actionType(ActionType.FILL).element(emFrame).build());
        AutomationStep botao = a.appendStep(s.id(), StepDraft.builder().name("Preencher botão").actionType(ActionType.FILL)
                .element(LabCase.field("tgt-9", "BUTTON", "button", "Consultar", "#b", LocatorStrategy.ID)).build());
        AutomationStep tecla = a.appendStep(s.id(), StepDraft.builder().name("Enter").actionType(ActionType.PRESS_KEY)
                .parameters(ActionParameters.withValue(new ValueSource.LiteralValue("Enter")))
                .element(LabCase.field("tgt-3f6c1a", "SEARCH_INPUT", "input", "Código", "#registro-codigo", LocatorStrategy.ID)).build());
        AutomationStep outra = a.appendStep(s.id(), StepDraft.builder().name("F5").actionType(ActionType.PRESS_KEY)
                .parameters(ActionParameters.withValue(new ValueSource.LiteralValue("F5"))).build());
        GuidedPlan p = plan(a);
        GuidedPlan.Step sv = p.step(senha.id()).orElseThrow();
        assertTrue(sv.criteria().containsAll(List.of(GuidedPlan.Criterion.FRAME_CHAIN_REACHABLE, GuidedPlan.Criterion.SHADOW_SCOPE_REACHABLE)));
        assertTrue(sv.uncertainties().containsAll(List.of(GuidedPlan.Uncertainty.TARGET_IN_FRAME, GuidedPlan.Uncertainty.TARGET_IN_SHADOW_ROOT,
                GuidedPlan.Uncertainty.TARGET_SENSITIVE)));
        assertTrue(sv.target().sensitive());
        assertTrue(p.step(botao.id()).orElseThrow().uncertainties().contains(GuidedPlan.Uncertainty.ACTION_KIND_UNUSUAL));
        assertEquals("Enter", p.step(tecla.id()).orElseThrow().intent().key(), "tecla do vocabulário fechado é a própria ação");
        assertNull(p.step(outra.id()).orElseThrow().intent().key());
        assertTrue(p.step(outra.id()).orElseThrow().uncertainties().contains(GuidedPlan.Uncertainty.VALUE_WITHHELD), "fora do vocabulário: retida");
        assertFalse(GuidedPlan.unusual(ActionType.FILL, ElementReference.UNKNOWN_KIND), "tipo desconhecido nunca é afirmado como incomum");
        assertFalse(GuidedPlan.unusual(ActionType.CLICK, "TEXT_INPUT"), "clicar aceita qualquer tipo");
    }

    @Test
    @DisplayName("revisão do plano: muda com o conteúdo (outro preferencial, nova etapa), não com edições sem efeito no plano; estático ≠ execução")
    void revisao() {
        Automation a = LabCase.consulta();
        GuidedPlan antes = plan(a);
        AutomationStep preencher = a.sessions().get(0).steps().get(1);
        a.updateStepBasics(preencher.id(), "Outro nome qualquer", preencher.description(), preencher.actionType());
        GuidedPlan renomeado = plan(a);
        assertNotEquals(antes.automationRevision(), renomeado.automationRevision(), "a automação mudou");
        assertEquals(antes.planRevision(), renomeado.planRevision(), "o nome não faz parte do plano");
        // G9 (achado da homologação real): decidir o alvo leva o modelo a 1.2.0, mas a decisão é do REGISTRO do debug, não
        // do conteúdo do plano — a revisão não pode mudar, senão o plano validado nunca casa com o registro que o validou
        a.decideTarget(preencher.id(), com.nekkora.studio.domain.target.TargetDecision.Kind.CONFIRMED,
                com.nekkora.studio.domain.target.DecisionActor.TEST_AGENT, new com.nekkora.studio.domain.target.TargetDecision.Basis("TARGET_ID",
                        "RESOLVED", List.of(), Instant.parse("2026-10-17T12:00:00Z"), ""));
        GuidedPlan decidido = plan(a);
        assertEquals("1.2.0", decidido.modelVersion());
        assertEquals(antes.planRevision(), decidido.planRevision(), "decisões não mudam a revisão do plano");
        a.choosePreferredLocator(preencher.id(), 1);
        GuidedPlan outroPreferencial = plan(a);
        assertNotEquals(antes.planRevision(), outroPreferencial.planRevision());
        assertEquals(GuidedPlan.TargetState.PREFERRED_LOCATOR_CHANGED, outroPreferencial.step(preencher.id()).orElseThrow().target().state());
        assertTrue(antes.toJson().contains("\"meaning\": \"STATIC_ONLY\""));
        assertThrows(IllegalArgumentException.class, () -> GuidedPlan.of(a, new AutomationValidator(RuleSet.V1_1).validate(LabCase.consulta())),
                "relatório de outra automação");
    }
}
