package com.nekkora.studio.validation;

import static com.nekkora.studio.validation.Fixtures.good;
import static com.nekkora.studio.validation.Fixtures.manual;
import static com.nekkora.studio.validation.Fixtures.withElement;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.variable.SemanticStatus;
import com.nekkora.studio.domain.variable.SemanticType;
import com.nekkora.studio.domain.variable.Sensitivity;
import com.nekkora.studio.domain.variable.VariableDraft;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.variable.VariableScope;
import com.nekkora.studio.domain.variable.VariableType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * STUDIO-008: contrato do relatório 1.1.0 (regras {@code nekkora-validation-rules/1.1.0}) — schema igual ao catálogo
 * completo, saída conforme, fixture congelada, privacidade (nomes de variáveis e valores fixos nunca aparecem).
 * Regeneração separada da do 1.0.0: {@code -Dnekkora.fixtures11.regenerate=true}.
 */
class ValidationContract11Test {
    private static final Path DIR = Path.of("src/test/resources/contracts/validation-report/1.1.0");
    private static final Path FIXTURE = DIR.resolve("fixtures/exemplo-variaveis.json");

    private static Map<String, Object> schema() throws IOException {
        return Json.parseObject(Files.readString(DIR.resolve("validation-report.schema.json"), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static List<String> enumOf(Map<String, Object> schema, String def) {
        return ((List<Object>) Json.object(Json.object(schema, "$defs"), def).get("enum")).stream().map(String::valueOf).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<String> propEnum(Map<String, Object> schema, String prop) {
        Map<String, Object> finding = Json.object(Json.object(schema, "$defs"), "Finding");
        return ((List<Object>) Json.object(Json.object(finding, "properties"), prop).get("enum")).stream().map(String::valueOf).toList();
    }

    /** Automação fictícia que exercita as regras de variáveis (nomes e valores só de laboratório). */
    static Automation exemplo() {
        Automation a = Automation.create("Cadastro de Pedidos", "Exemplo fictício", Fixtures.CLOCK);
        LogicalSession cadastro = a.addSession("Cadastro", null);
        LogicalSession pesquisa = a.addSession("Pesquisa", null);
        AutomationStep cpf = a.appendStep(cadastro.id(), withElement("Preencher CPF", ActionType.FILL, good(),
                ActionParameters.withValue(new ValueSource.VariableReference("cliente.cpf"))));
        AutomationStep ler = a.appendStep(cadastro.id(), manual("Ler protocolo", ActionType.OBSERVE));
        a.appendStep(cadastro.id(), withElement("Preencher protocolo", ActionType.FILL, good(),
                ActionParameters.withValue(new ValueSource.VariableReference("resultado.protocolo"))));
        a.appendStep(cadastro.id(), withElement("Preencher ativo", ActionType.FILL, good(),
                ActionParameters.withValue(new ValueSource.VariableReference("cliente.ativo"))));
        a.appendStep(cadastro.id(), withElement("Preencher nome", ActionType.FILL, good(),
                ActionParameters.withValue(new ValueSource.VariableReference("Cliente.Nome"))));
        a.appendStep(cadastro.id(), manual("Abrir portal", ActionType.NAVIGATE,
                ActionParameters.withValue(new ValueSource.VariableReference("portal.endereco"))));
        a.appendStep(pesquisa.id(), withElement("Pesquisar protocolo", ActionType.FILL, good(),
                ActionParameters.withValue(new ValueSource.VariableReference("resultado.protocolo"))));
        a.declareVariable(VariableDraft.of("cliente.cpf", VariableType.STRING).withSemantic(SemanticType.BRAZILIAN_CPF, SemanticStatus.USER_CONFIRMED)
                .withSensitivity(Sensitivity.PERSONAL_DATA).withOrigin(VariableOrigin.input()));
        a.declareVariable(VariableDraft.of("resultado.protocolo", VariableType.STRING)
                .withSemantic(SemanticType.IDENTIFIER, SemanticStatus.PROPOSED_UNCONFIRMED).withSensitivity(Sensitivity.INTERNAL)
                .withScope(VariableScope.session(cadastro.id())).withOrigin(VariableOrigin.stepOutput(ler.id())));
        a.declareVariable(VariableDraft.of("cliente.ativo", VariableType.BOOLEAN));
        a.declareVariable(VariableDraft.of("portal.endereco", VariableType.STRING).withOrigin(VariableOrigin.constant("https://lab.exemplo.test/portal")));
        a.declareVariable(VariableDraft.of("pedido.quantidade", VariableType.INTEGER).withOrigin(VariableOrigin.constant("3")));
        assertEquals(cpf.id(), a.sessions().get(0).steps().get(0).id());
        return a;
    }

    @Test
    @DisplayName("schema 1.1.0 = catálogo completo: códigos, escopos, campos, detalhes, estados e versões")
    void schemaIgualAoCodigo() throws Exception {
        Map<String, Object> s = schema();
        assertEquals(FindingCode.of(RuleSet.V1_1).stream().map(Enum::name).toList(), enumOf(s, "Code"));
        assertEquals(Arrays.stream(FindingCode.values()).map(Enum::name).toList(), enumOf(s, "Code"), "1.1.0 é o catálogo completo");
        assertEquals(List.copyOf(FindingDetails.all()), enumOf(s, "Detail"));
        assertEquals(Arrays.stream(ValidationStatus.values()).map(Enum::name).toList(), enumOf(s, "Status"));
        assertEquals(Arrays.stream(Severity.values()).map(Enum::name).toList(), propEnum(s, "severity"));
        assertEquals(FindingScope.of(RuleSet.V1_1).stream().map(Enum::name).toList(), propEnum(s, "scope"));
        assertEquals(FindingField.of(RuleSet.V1_1).stream().map(Enum::name).toList(), propEnum(s, "field"));
        Map<String, Object> report = Json.object(Json.object(Json.object(s, "$defs"), "Report"), "properties");
        assertEquals(AutomationValidator.RULES_VERSION_1_1, Json.object(report, "rules").get("const"));
        assertEquals(RuleSet.V1_1.version(), AutomationValidator.RULES_VERSION_1_1);
        assertEquals(ValidationReport.VERSION_1_1, Json.object(report, "version").get("const"));
        assertEquals(RuleSet.V1_1.reportVersion(), ValidationReport.VERSION_1_1);
    }

    @Test
    @DisplayName("saída 1.1.0 conforme ao schema e IDÊNTICA à fixture congelada; mesma automação → mesmos bytes")
    void fixtureCongelada() throws Exception {
        String json = new AutomationValidator(RuleSet.V1_1).validate(exemplo()).toJson();
        assertEquals(List.of(), new Schemas.SchemaValidator(schema()).validate(Json.parse(json), "Report"));
        String normalized = ValidationContractTest.normalized(json);
        if (Boolean.getBoolean("nekkora.fixtures11.regenerate")) {
            Files.createDirectories(FIXTURE.getParent());
            Files.writeString(FIXTURE, normalized, StandardCharsets.UTF_8);
        }
        assertEquals(Files.readString(FIXTURE, StandardCharsets.UTF_8).replace("\r\n", "\n"), normalized,
                "mudança no relatório exige nova versão das regras/contrato e regeneração revisada (-Dnekkora.fixtures11.regenerate=true)");
        assertEquals(normalized, ValidationContractTest.normalized(new AutomationValidator(RuleSet.V1_1).validate(exemplo()).toJson()));
    }

    @Test
    @DisplayName("o exemplo cobre as famílias de regras 1.1.0 e o schema recusa escopo/código inventados")
    void cobertura() throws Exception {
        ValidationReport r = new AutomationValidator(RuleSet.V1_1).validate(exemplo());
        Set<FindingCode> found = r.findings().stream().map(Finding::code).collect(Collectors.toCollection(() -> EnumSet.noneOf(FindingCode.class)));
        assertTrue(found.containsAll(Set.of(FindingCode.VARIABLE_NOT_DECLARED, FindingCode.VARIABLE_OUT_OF_SCOPE, FindingCode.VARIABLE_TYPE_INCOMPATIBLE,
                FindingCode.VARIABLE_ORIGIN_UNBOUND, FindingCode.STEP_OUTPUT_NOT_PROVEN, FindingCode.INPUT_VALUE_NOT_AVAILABLE,
                FindingCode.VARIABLE_SEMANTIC_UNCONFIRMED, FindingCode.VARIABLE_SEMANTIC_UNSPECIFIED, FindingCode.CONSTANT_STORED_UNENCRYPTED,
                FindingCode.VARIABLE_UNUSED)), found.toString());
        assertFalse(found.contains(FindingCode.VARIABLE_RESOLUTION_UNAVAILABLE));
        Schemas.SchemaValidator v = new Schemas.SchemaValidator(schema());
        String ok = r.toJson();
        assertFalse(v.validate(Json.parse(ok.replaceFirst("\"scope\": \"VARIABLE\"", "\"scope\": \"VALUE\"")), "Report").isEmpty());
        assertFalse(v.validate(Json.parse(ok.replaceFirst("\"code\": \"VARIABLE_UNUSED\"", "\"code\": \"VARIABLE_VERIFIED\"")), "Report").isEmpty());
        assertFalse(v.validate(Json.parse(ok.replace("\"status\": \"INCOMPLETE\"", "\"status\": \"AI_VERIFIED\"")), "Report").isEmpty());
    }

    @Test
    @DisplayName("privacidade: nenhum nome de variável, valor fixo, nome de etapa ou sessão no relatório 1.1.0")
    void semDadosPrivados() {
        String json = new AutomationValidator(RuleSet.V1_1).validate(exemplo()).toJson();
        for (String secret : List.of("cliente.cpf", "resultado.protocolo", "cliente.ativo", "Cliente.Nome", "portal.endereco", "pedido.quantidade",
                "lab.exemplo.test", "\"3\"", "Preencher CPF", "Cadastro de Pedidos", "Pesquisa", "Exemplo fictício")) {
            assertFalse(json.contains(secret), "vazou: " + secret);
        }
    }
}
