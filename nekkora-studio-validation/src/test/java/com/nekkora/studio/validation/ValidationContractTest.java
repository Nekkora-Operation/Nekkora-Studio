package com.nekkora.studio.validation;

import static com.nekkora.studio.validation.Fixtures.add;
import static com.nekkora.studio.validation.Fixtures.good;
import static com.nekkora.studio.validation.Fixtures.locator;
import static com.nekkora.studio.validation.Fixtures.manual;
import static com.nekkora.studio.validation.Fixtures.recorded;
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
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.element.ManualLocator;
import com.nekkora.studio.domain.element.ObservedObstacle;
import com.nekkora.studio.domain.evidence.EvidenceType;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Limitation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * STUDIO-007: contrato do relatório ({@code nekkora-studio/validation-report} 1.0.0) — schema igual ao código, saída
 * conforme ao schema, fixture congelada (bytes idênticos), privacidade e independência de interface/navegador/disco.
 */
class ValidationContractTest {
    private static final Path DIR = Path.of("src/test/resources/contracts/validation-report/1.0.0");
    private static final Path FIXTURE = DIR.resolve("fixtures/exemplo-consulta-de-clientes.json");

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

    private static <E extends Enum<E>> List<String> names(E[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    /** Automação fictícia que exercita as famílias de regras (dados do laboratório, nada real). */
    static Automation exemplo() {
        Automation a = Automation.create("Consulta de Clientes", "Exemplo fictício", Fixtures.CLOCK);
        LogicalSession login = a.addSession("Autenticação", null);
        a.appendStep(login.id(), recorded("Preencher usuário", ActionType.FILL, "ev-1", good()));
        a.appendStep(login.id(), withElement("Preencher senha", ActionType.FILL, Fixtures.password(),
                ActionParameters.withValue(new ValueSource.CredentialReference("sistema.senha"))));
        ElementReference entrar = ElementReference.builder().semanticKind("BUTTON").tag("button")
                .addLocator(new LocatorCandidate(LocatorStrategy.TEXT, "botão Entrar", "//button[text()='Entrar']", LocatorStability.HEURISTIC_STABLE, 2, true))
                .addObstacle(new ObservedObstacle("DYNAMIC_IDS", ObservedObstacle.Severity.MEDIUM, "ids gerados")).build();
        AutomationStep click = a.appendStep(login.id(), recorded("Clicar em Entrar", ActionType.CLICK, "ev-2", entrar, "LOCATORS_WITHHELD"));
        a.addManualLocator(click.id(), new ManualLocator(ManualLocator.Syntax.CSS, "form#login button[type=submit]", "proposta do analista", Fixtures.T));
        a.attachEvidence(Fixtures.evidence("evd-1", 1, "ev-2", EvidenceType.VISIBLE_ERROR_MESSAGE, Limitation.MESSAGE_SUPPRESSED));
        a.attachEvidence(Fixtures.evidence("evd-2", 2, "ev-2", EvidenceType.OBSERVATION_COMPLETE));
        a.attachEvidence(Fixtures.evidence("evd-3", 3, null, EvidenceType.AMBIGUOUS_FEEDBACK)); // sem etapa definida: não atribuída
        LogicalSession consulta = a.addSession("Consulta", null);
        a.appendStep(consulta.id(), manual("Abrir Clientes", ActionType.NAVIGATE,
                ActionParameters.withValue(new ValueSource.LiteralValue("https://lab.exemplo.test/clientes"))));
        a.appendStep(consulta.id(), withElement("Preencher pesquisa", ActionType.FILL, good(),
                ActionParameters.withValue(new ValueSource.VariableReference("cliente.nome"))));
        a.appendStep(consulta.id(), recorded("Selecionar estado", ActionType.SELECT_OPTION, "ev-3", good(), "OPTION_WITHHELD"));
        a.appendStep(consulta.id(), manual("Aguardar resultado", ActionType.WAIT_FOR));
        a.appendStep(consulta.id(), withElement("Clicar em Pesquisar", ActionType.CLICK, ElementReference.builder().semanticKind("BUTTON")
                .addLocator(locator(LocatorStrategy.POSITIONAL, LocatorStability.HEURISTIC_UNSTABLE, 1, true)).preferLastLocator().build()));
        a.addSession("Encerramento", null);
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

    @Test
    @DisplayName("schema 1.0.0 = código das regras 1.0.0: códigos, detalhes, severidades, escopos, campos e estados")
    void schemaIgualAoCodigo() throws Exception {
        // STUDIO-008 (ajuste autorizado): o catálogo ganhou os códigos 1.1.0 no FIM; o schema 1.0.0 é comparado com o
        // subconjunto das regras 1.0.0 — mesmos códigos, na mesma ordem (o schema 1.1.0 tem o seu próprio teste)
        Map<String, Object> s = schema();
        assertEquals(FindingCode.of(RuleSet.V1_0).stream().map(Enum::name).toList(), enumOf(s, "Code"), "códigos (na ordem das regras)");
        assertEquals(List.copyOf(FindingDetails.all()), enumOf(s, "Detail"));
        assertEquals(names(ValidationStatus.values()), enumOf(s, "Status"));
        assertEquals(names(Severity.values()), propEnum(s, "severity"));
        assertEquals(FindingScope.of(RuleSet.V1_0).stream().map(Enum::name).toList(), propEnum(s, "scope"));
        assertEquals(FindingField.of(RuleSet.V1_0).stream().map(Enum::name).toList(), propEnum(s, "field"));
        assertEquals(names(FindingCode.values()).subList(0, FindingCode.of(RuleSet.V1_0).size()), enumOf(s, "Code"),
                "os códigos 1.1.0 só foram acrescentados ao fim");
        Map<String, Object> report = Json.object(Json.object(s, "$defs"), "Report");
        assertEquals(AutomationValidator.RULES_VERSION, Json.object(Json.object(report, "properties"), "rules").get("const"));
        assertEquals(ValidationReport.VERSION, Json.object(Json.object(report, "properties"), "version").get("const"));
    }

    @Test
    @DisplayName("S007-031/032 — saída conforme ao schema e IDÊNTICA à fixture congelada (normalizadas só as identidades geradas)")
    void fixtureCongelada() throws Exception {
        String json = new AutomationValidator().validate(exemplo()).toJson();
        List<String> errors = new Schemas.SchemaValidator(schema()).validate(Json.parse(json), "Report");
        assertEquals(List.of(), errors);
        String normalized = normalized(json);
        if (Boolean.getBoolean("nekkora.fixtures.regenerate")) {
            Files.createDirectories(FIXTURE.getParent());
            Files.writeString(FIXTURE, normalized, StandardCharsets.UTF_8);
        }
        assertEquals(Files.readString(FIXTURE, StandardCharsets.UTF_8).replace("\r\n", "\n"), normalized,
                "mudança no relatório exige nova versão das regras/contrato e regeneração revisada (-Dnekkora.fixtures.regenerate=true)");
        assertEquals(normalized, normalized(new AutomationValidator().validate(exemplo()).toJson()), "outra automação igual: mesmos bytes");
    }

    @Test
    @DisplayName("o validador de schema do teste recusa de fato")
    void validadorRecusa() throws Exception {
        Schemas.SchemaValidator v = new Schemas.SchemaValidator(schema());
        assertFalse(v.validate(Json.parse("{}"), "Report").isEmpty());
        String ok = new AutomationValidator().validate(exemplo()).toJson();
        assertFalse(v.validate(Json.parse(ok.replace("\"status\": \"INCOMPLETE\"", "\"status\": \"READY_FOR_EXECUTION\"")), "Report").isEmpty(),
                "não existe estado 'pronto para executar'");
        assertFalse(v.validate(Json.parse(ok.replace("\"code\": \"REQUIRED_VALUE_MISSING\"", "\"code\": \"QUALQUER_COISA\"")), "Report").isEmpty());
        assertFalse(v.validate(Json.parse(ok.replace("\"detail\": \"DYNAMIC_IDS\"", "\"detail\": \"texto livre\"")), "Report").isEmpty());
    }

    @Test
    @DisplayName("S007-027 — nenhum dado privado no relatório: valores, nomes de variáveis e credenciais, nomes de etapas, localizadores, comentários, textos do produtor")
    void s007027SemDadosPrivados() {
        Automation a = exemplo();
        String json = new AutomationValidator().validate(a).toJson();
        for (String secret : List.of("sistema.senha", "cliente.nome", "lab.exemplo.test", "Preencher senha", "Consulta de Clientes", "Autenticação",
                "#entrar", "//button", "form#login", "proposta do analista", "ids gerados", "alerta do formulário", "Entrar", "Senha", "botão",
                "Exemplo fictício", "rec-1", "ev-2", "evd-")) {
            assertFalse(json.contains(secret), "vazou: " + secret);
        }
        assertTrue(json.contains("CREDENTIAL_RESOLUTION_UNAVAILABLE") && json.contains("VARIABLE_RESOLUTION_UNAVAILABLE"), "a dependência é apontada sem o nome");
    }

    @Test
    @DisplayName("S007-041/042/043 — o motor não depende de interface, navegador, Native Host, disco, rede nem threads (Java puro sobre o domínio)")
    void independente() throws Exception {
        List<String> forbidden = List.of("java.awt", "javax.swing", "java.net", "java.nio.file", "java.io.File", "java.util.concurrent", "Thread",
                "ProcessBuilder", "Runtime.getRuntime", "com.nekkora.studio.bridge", "com.nekkora.studio.recorder", "com.nekkora.studio.persistence",
                "com.nekkora.studio.app", "com.nekkora.ui", "System.getenv", "System.currentTimeMillis", "Instant.now", "UUID.randomUUID", "Random");
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(f, StandardCharsets.UTF_8);
                for (String x : forbidden) assertFalse(src.contains(x), f.getFileName() + " usa " + x);
            }
        }
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        assertTrue(pom.contains("<artifactId>nekkora-studio-domain</artifactId>"));
        assertTrue(pom.replaceAll("(?s)<dependency>\\s*<!--[^>]*-->\\s*<groupId>com.nekkora</groupId>\\s*<artifactId>nekkora-studio-bridge</artifactId>\\s*<scope>test</scope>", "")
                .split("nekkora-studio-bridge").length == 1, "o bridge só entra nos testes");
    }
}
