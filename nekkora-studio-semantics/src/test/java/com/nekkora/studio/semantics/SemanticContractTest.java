package com.nekkora.studio.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepDraft;
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
import com.nekkora.studio.domain.variable.VariableScope;
import com.nekkora.studio.domain.variable.VariableType;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * STUDIO-008: contrato semântico {@code nekkora-studio/semantic-contract} 1.0.0 — schema igual ao código, saída conforme,
 * fixture congelada (bytes idênticos), determinismo, privacidade, separação do relatório de validação e independência.
 */
class SemanticContractTest {
    private static final Path DIR = Path.of("src/test/resources/contracts/semantic-contract/1.0.0");
    private static final Path FIXTURE = DIR.resolve("fixtures/exemplo-cadastro.json");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);

    private static Map<String, Object> schema() throws IOException {
        return Json.parseObject(Files.readString(DIR.resolve("semantic-contract.schema.json"), StandardCharsets.UTF_8));
    }

    private static AutomationStep step(Automation a, LogicalSession s, String name, ActionType type, ValueSource value) {
        StepDraft.Builder b = StepDraft.builder().name(name).actionType(type);
        if (value != null) b.parameters(ActionParameters.withValue(value));
        return a.appendStep(s.id(), b.build());
    }

    /** Automação fictícia com cada tipo de origem, escopo, uso e incerteza (nomes e valores só de laboratório). */
    static Automation exemplo() {
        Automation a = Automation.create("Cadastro de Pedidos", "Exemplo fictício", CLOCK);
        LogicalSession cadastro = a.addSession("Cadastro", "Dados do titular");
        LogicalSession pesquisa = a.addSession("Pesquisa", null);
        step(a, cadastro, "Abrir portal", ActionType.NAVIGATE, new ValueSource.VariableReference("portal.endereco"));
        step(a, cadastro, "Preencher CPF", ActionType.FILL, new ValueSource.VariableReference("cliente.cpf"));
        step(a, cadastro, "Preencher senha", ActionType.FILL, new ValueSource.CredentialReference("sistema.senha.ficticia"));
        AutomationStep ler = step(a, cadastro, "Ler protocolo", ActionType.OBSERVE, null);
        step(a, cadastro, "Preencher protocolo", ActionType.FILL, new ValueSource.VariableReference("resultado.protocolo"));
        step(a, cadastro, "Preencher observação", ActionType.FILL, new ValueSource.LiteralValue("texto literal fictício"));
        step(a, pesquisa, "Pesquisar nome", ActionType.FILL, new ValueSource.VariableReference("Cliente.Nome.Digitado"));
        step(a, pesquisa, "Pesquisar protocolo", ActionType.FILL, new ValueSource.VariableReference("resultado.protocolo"));
        step(a, pesquisa, "Clicar em Pesquisar", ActionType.CLICK, null);
        a.declareVariable(VariableDraft.of("portal.endereco", VariableType.STRING).withOrigin(VariableOrigin.constant("https://lab.exemplo.test/portal"))
                .withPurpose("Endereço do portal de laboratório"));
        a.declareVariable(VariableDraft.of("cliente.cpf", VariableType.STRING).withSemantic(SemanticType.BRAZILIAN_CPF, SemanticStatus.USER_CONFIRMED)
                .withSensitivity(Sensitivity.PERSONAL_DATA).withOrigin(VariableOrigin.input())
                .withConstraints(new VariableConstraints(true, 11, 11, TextFormat.DIGITS, null, null, null, null)));
        a.declareVariable(VariableDraft.of("resultado.protocolo", VariableType.STRING)
                .withSemantic(SemanticType.IDENTIFIER, SemanticStatus.PROPOSED_UNCONFIRMED).withSensitivity(Sensitivity.INTERNAL)
                .withScope(VariableScope.session(cadastro.id())).withOrigin(VariableOrigin.stepOutput(ler.id())));
        a.declareVariable(VariableDraft.of("pedido.valor", VariableType.DECIMAL).withSemantic(SemanticType.CURRENCY, SemanticStatus.USER_CONFIRMED)
                .withSensitivity(Sensitivity.INTERNAL).withOrigin(VariableOrigin.input())
                .withConstraints(new VariableConstraints(false, null, null, TextFormat.ANY, new BigDecimal("0.00"), new BigDecimal("99999.99"), 2, "BRL")));
        a.declareVariable(VariableDraft.of("cliente.ativo", VariableType.BOOLEAN));
        return a;
    }

    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    /** Troca cada UUID (gerado ao criar a automação) por um número na ordem em que aparece: o resto fica byte a byte. */
    static String normalized(String json) {
        Map<String, String> seen = new LinkedHashMap<>();
        Matcher m = UUID.matcher(json);
        StringBuilder b = new StringBuilder();
        while (m.find()) {
            String n = seen.computeIfAbsent(m.group(), k -> String.format("00000000-0000-0000-0000-%012d", seen.size() + 1));
            m.appendReplacement(b, n);
        }
        m.appendTail(b);
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> enumAt(Map<String, Object> schema, String def, String prop) {
        Map<String, Object> d = Json.object(Json.object(schema, "$defs"), def);
        return ((List<Object>) Json.object(Json.object(d, "properties"), prop).get("enum")).stream().map(String::valueOf).toList();
    }

    private static <E extends Enum<E>> List<String> names(E[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    @Test
    @DisplayName("schema 1.0.0 = código: vocabulários, exclusões e versões")
    void schemaIgualAoCodigo() throws Exception {
        Map<String, Object> s = schema();
        assertEquals(names(SemanticContract.UncertaintyCode.values()), enumAt(s, "Uncertainty", "code"));
        assertEquals(names(SemanticContract.ValueKind.values()), enumAt(s, "Step", "value"));
        assertEquals(names(SemanticContract.ProducerStatus.values()), enumAt(s, "Producer", "status"));
        assertEquals(names(VariableType.values()), enumAt(s, "Variable", "technicalType"));
        assertEquals(names(SemanticType.values()), enumAt(s, "Variable", "semanticType"));
        assertEquals(names(SemanticStatus.values()), enumAt(s, "Variable", "semanticStatus"));
        assertEquals(names(Sensitivity.values()), enumAt(s, "Variable", "sensitivity"));
        assertEquals(names(ActionType.values()), enumAt(s, "Step", "actionType"));
        Map<String, Object> contract = Json.object(Json.object(Json.object(s, "$defs"), "Contract"), "properties");
        assertEquals(SemanticContract.SCHEMA, Json.object(contract, "schema").get("const"));
        assertEquals(SemanticContract.VERSION, Json.object(contract, "version").get("const"));
        assertEquals(SemanticContract.DATA_FLOW_RULES, Json.object(contract, "dataFlowRules").get("const"));
        assertEquals(SemanticContract.EXCLUSIONS, Json.array(Json.object(Json.object(contract, "exclusions"), "items"), "enum"));
    }

    @Test
    @DisplayName("saída conforme ao schema e IDÊNTICA à fixture congelada; duas automações iguais → mesmos bytes")
    void fixtureCongelada() throws Exception {
        String json = SemanticContract.of(exemplo()).toJson();
        assertEquals(List.of(), new Schemas.SchemaValidator(schema()).validate(Json.parse(json), "Contract"));
        String normalized = normalized(json);
        if (Boolean.getBoolean("nekkora.fixtures11.regenerate")) {
            Files.createDirectories(FIXTURE.getParent());
            Files.writeString(FIXTURE, normalized, StandardCharsets.UTF_8);
        }
        assertEquals(Files.readString(FIXTURE, StandardCharsets.UTF_8).replace("\r\n", "\n"), normalized,
                "mudança no contrato exige nova versão e regeneração revisada (-Dnekkora.fixtures11.regenerate=true)");
        assertEquals(normalized, normalized(SemanticContract.of(exemplo()).toJson()), "determinístico");
        Automation a = exemplo();
        assertEquals(SemanticContract.of(a), SemanticContract.of(a));
        assertEquals(SemanticContract.of(a).toJson(), SemanticContract.of(a).toJson(), "mesmo estado → mesmos bytes");
    }

    @Test
    @DisplayName("uso derivado, produtora declarada e incertezas: nada é inferido nem certificado")
    void conteudo() {
        Automation a = exemplo();
        SemanticContract c = SemanticContract.of(a);
        assertEquals("1.1.0", c.modelVersion());
        SemanticContract.Variable protocolo = c.variables().get(2);
        assertEquals("resultado.protocolo", protocolo.name());
        assertEquals(2, protocolo.consumers().size(), "uso derivado pelo nome exato (inclusive fora do escopo)");
        assertEquals(SemanticContract.ProducerStatus.INVALID, protocolo.producer().status(), "uma consumidora está fora do escopo da variável");
        SemanticContract.Step undeclared = c.steps().get(6);
        assertEquals(SemanticContract.Reference.UNDECLARED, undeclared.reference());
        assertEquals(null, undeclared.variable(), "nome parecido não é associado");
        List<SemanticContract.UncertaintyCode> codes = c.uncertainties().stream().map(SemanticContract.Uncertainty::code).toList();
        assertTrue(codes.containsAll(List.of(SemanticContract.UncertaintyCode.INPUT_VALUE_UNKNOWN, SemanticContract.UncertaintyCode.STEP_OUTPUT_UNVERIFIED,
                SemanticContract.UncertaintyCode.ORIGIN_UNBOUND, SemanticContract.UncertaintyCode.SEMANTIC_UNCONFIRMED,
                SemanticContract.UncertaintyCode.SEMANTIC_UNSPECIFIED, SemanticContract.UncertaintyCode.UNDECLARED_REFERENCE,
                SemanticContract.UncertaintyCode.OUT_OF_SCOPE_REFERENCE, SemanticContract.UncertaintyCode.CREDENTIAL_UNRESOLVED)));
        // produtora coerente: consumidora na mesma sessão, depois dela
        a.removeStep(c.steps().get(7).id());
        assertEquals(SemanticContract.ProducerStatus.STATICALLY_CONSISTENT, SemanticContract.of(a).variables().get(2).producer().status());
        String json = SemanticContract.of(a).toJson();
        assertTrue(json.contains("\"runtimeVerification\": \"UNVERIFIED_AT_RUNTIME\""));
        for (String forbidden : List.of("VERIFIED_AT_RUNTIME\"", "READY_FOR_EXECUTION", "AI_VERIFIED", "\"executed\"", "\"verified\": true")) {
            assertFalse(json.replace("UNVERIFIED_AT_RUNTIME", "").contains(forbidden), forbidden);
        }
        // sem consumidoras: só declarada
        a.removeStep(c.steps().get(4).id());
        assertEquals(SemanticContract.ProducerStatus.DECLARED, SemanticContract.of(a).variables().get(2).producer().status());
        a.removeStep(c.steps().get(3).id());
        assertEquals(SemanticContract.ProducerStatus.INVALID, SemanticContract.of(a).variables().get(2).producer().status(), "produtora removida");
    }

    @Test
    @DisplayName("privacidade: sem valores fixos, literais, apelidos de credencial, finalidade, nomes de etapas/sessões/automação ou nomes não declarados")
    void semDadosPrivados() {
        Automation a = exemplo();
        VariableDefinition endereco = a.variables().get(0);
        assertTrue(endereco.origin().constant() != null);
        String json = SemanticContract.of(a).toJson();
        for (String secret : List.of("lab.exemplo.test", "texto literal fictício", "sistema.senha.ficticia", "Endereço do portal", "Cadastro de Pedidos",
                "Exemplo fictício", "Dados do titular", "\"Cadastro\"", "Pesquisa", "Preencher", "Ler protocolo", "Cliente.Nome.Digitado")) {
            assertFalse(json.contains(secret), "vazou: " + secret);
        }
        assertTrue(json.contains("\"constantDeclared\": true"));
        assertTrue(json.contains("\"name\": \"cliente.cpf\""), "o nome simbólico declarado faz parte do contrato");
        assertFalse(SemanticContract.of(a).toString().contains("cliente.cpf"), "toString sem nomes (logs)");
    }

    @Test
    @DisplayName("revisão P2: CPF sem representação declarada → incerteza REPRESENTATION_UNSPECIFIED; só dígitos ou com máscara → nenhuma")
    void representacao() throws Exception {
        Automation a = Automation.create("Cadastro fictício", "", CLOCK);
        a.addSession("Cadastro", null);
        VariableDefinition semFormato = a.declareVariable(VariableDraft.of("cliente.cpf", VariableType.STRING)
                .withSemantic(SemanticType.BRAZILIAN_CPF, SemanticStatus.USER_CONFIRMED).withSensitivity(Sensitivity.PERSONAL_DATA)
                .withOrigin(VariableOrigin.input()));
        VariableDefinition mascara = a.declareVariable(VariableDraft.of("titular.cpf", VariableType.STRING)
                .withSemantic(SemanticType.BRAZILIAN_CPF, SemanticStatus.USER_CONFIRMED).withSensitivity(Sensitivity.PERSONAL_DATA)
                .withOrigin(VariableOrigin.input())
                .withConstraints(new VariableConstraints(false, 14, 14, TextFormat.valueOf("BRAZILIAN_CPF_MASKED"), null, null, null, null)));
        SemanticContract c = SemanticContract.of(a);
        List<String> semFormatoCodes = c.uncertainties().stream().filter(u -> semFormato.id().equals(u.variable())).map(u -> u.code().name()).toList();
        List<String> mascaraCodes = c.uncertainties().stream().filter(u -> mascara.id().equals(u.variable())).map(u -> u.code().name()).toList();
        assertTrue(semFormatoCodes.contains("REPRESENTATION_UNSPECIFIED"), semFormatoCodes.toString());
        assertFalse(mascaraCodes.contains("REPRESENTATION_UNSPECIFIED"), mascaraCodes.toString());
        assertTrue(mascaraCodes.contains("INPUT_VALUE_UNKNOWN"), "representação declarada não é valor validado");
        assertEquals(List.of(), new Schemas.SchemaValidator(schema()).validate(Json.parse(c.toJson()), "Contract"));
        // a fixture congelada (CPF só com dígitos) não muda: nenhuma incerteza nova nela
        assertFalse(SemanticContract.of(exemplo()).toJson().contains("REPRESENTATION_UNSPECIFIED"));
    }

    @Test
    @DisplayName("separado do relatório de validação e só leitura: não altera a automação nem tem estado de validação")
    void separadoESoLeitura() {
        Automation a = exemplo();
        long revision = a.revision();
        Automation.Snapshot before = a.snapshot();
        String json = SemanticContract.of(a).toJson();
        assertEquals(revision, a.revision());
        assertEquals(before.revision(), a.snapshot().revision());
        // nenhum campo do relatório de validação (estado, apontamentos, severidades, versão das regras de validação)
        assertEquals(List.of("schema", "version", "automationId", "modelVersion", "dataFlowRules", "summary", "variables", "steps", "uncertainties",
                "exclusions"), List.copyOf(Json.parseObject(json).keySet()));
        for (String key : List.of("\"findings\"", "\"severity\"", "\"rules\"")) assertFalse(json.contains(key), key);
        Automation semVariaveis = Automation.create("Sem variáveis", "", CLOCK);
        SemanticContract vazio = SemanticContract.of(semVariaveis);
        assertEquals("1.0.0", vazio.modelVersion());
        assertTrue(vazio.variables().isEmpty() && vazio.steps().isEmpty() && vazio.uncertainties().isEmpty());
    }

    @Test
    @DisplayName("o motor do contrato não depende de interface, navegador, disco, rede, relógio nem threads")
    void independente() throws Exception {
        List<String> forbidden = List.of("java.awt", "javax.swing", "java.net", "java.nio.file", "java.io.File", "java.util.concurrent", "Thread",
                "ProcessBuilder", "Runtime.getRuntime", "com.nekkora.studio.bridge", "com.nekkora.studio.recorder", "com.nekkora.studio.persistence",
                "com.nekkora.studio.app", "com.nekkora.ui", "System.getenv", "System.currentTimeMillis", "Instant.now", "UUID.randomUUID", "Random",
                "purpose()", "constantValue()", ".constant())", "LiteralValue l ->", "alias()");
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(f, StandardCharsets.UTF_8).replace("case ValueSource.LiteralValue l -> ValueKind.LITERAL", "");
                for (String x : forbidden) assertFalse(src.contains(x), f.getFileName() + " usa " + x);
            }
        }
    }
}
