package com.nekkora.studio.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.ModelVersion;
import com.nekkora.studio.domain.StepField;
import com.nekkora.studio.domain.target.DecisionActor;
import com.nekkora.studio.domain.target.TargetDecision;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contrato versionado do formato {@code .nekkora} 1.2.0 (STUDIO-009, D-S009-07): o 1.1.0 mais, em cada etapa, o alvo
 * substituto e a decisão sobre o alvo; catálogo de variáveis opcional. Os contratos 1.0.0 e 1.1.0 continuam intactos
 * (seus testes e fixtures); aqui também se confere que sem decisões o escritor continua produzindo 1.0.0/1.1.0.
 *
 * <p>Regeneração SEPARADA ({@code -Dnekkora.fixtures12.regenerate=true}): nunca toca nas fixtures 1.0.0 e 1.1.0.
 */
class ProjectFormat12ContractTest {

    static final Path ROOT = Path.of("src", "test", "resources", "contracts", "nekkora-project", ProjectFormat.FORMAT_VERSION_1_2);
    static final Path VALID = ROOT.resolve("fixtures").resolve("valid");
    static final Path INVALID = ROOT.resolve("fixtures").resolve("invalid");
    static final String FIXTURE = "consulta-clientes-decisoes.nekkora";
    static final String RECOVERY_FIXTURE = "consulta-clientes-decisoes-recuperacao.nekkora";
    private static final String PROJECT = UUID.randomUUID().toString();

    @TempDir
    Path dir;

    private final RichAutomation.TickClock clock = new RichAutomation.TickClock("2026-10-17T12:00:00Z");
    private final ProjectFiles files = new ProjectFiles(new ProjectRepository(), "teste");

    private static Map<String, Object> schema() throws IOException {
        return Json.parseObject(Files.readString(ROOT.resolve("nekkora-project.schema.json")));
    }

    private static Map<String, Object> schema11() throws IOException {
        return Json.parseObject(Files.readString(ProjectFormat11ContractTest.ROOT.resolve("nekkora-project.schema.json")));
    }

    // ================================================================================================== schema

    @Test
    @DisplayName("schema 1.2.0: decisão/ator/campos iguais ao código; tudo o que já existia no 1.1.0 continua igual, salvo versões, etapa e StepField")
    void schemaIgualAoCodigo() throws Exception {
        Map<String, Object> defs = Json.object(schema(), "$defs");
        assertEquals(Arrays.stream(DecisionActor.values()).map(Enum::name).toList(), Json.array(Json.object(defs, "DecisionActor"), "enum"));
        assertEquals(Arrays.stream(TargetDecision.Kind.values()).map(Enum::name).toList(), Json.array(Json.object(defs, "DecisionKind"), "enum"));
        assertEquals(Arrays.stream(StepField.values()).map(Enum::name).toList(), Json.array(Json.object(defs, "StepField"), "enum"));
        assertFalse(Json.write(Json.object(defs, "DecisionActor")).contains("HUMAN") || Json.write(Json.object(defs, "DecisionActor")).contains("USER_CONFIRMED"),
                "nenhum rótulo de confirmação humana");
        Map<String, Object> defs11 = Json.object(schema11(), "$defs");
        Set<String> changed = Set.of("Manifest", "Automation", "Step", "StepField");
        for (String name : defs11.keySet()) {
            if (!changed.contains(name)) assertEquals(Json.write(defs11.get(name)), Json.write(defs.get(name)), name);
        }
        Map<String, Object> manifest = Json.object(Json.object(defs, "Manifest"), "properties");
        String pattern = Json.string(Json.object(manifest, "formatVersion"), "pattern");
        assertTrue(ProjectFormat.FORMAT_VERSION_1_2.matches(pattern));
        assertFalse(ProjectFormat.FORMAT_VERSION_1_1.matches(pattern) || ProjectFormat.FORMAT_VERSION.matches(pattern));
        assertEquals(ModelVersion.V1_2, Json.object(Json.object(Json.object(defs, "Automation"), "properties"), "modelVersion").get("const"));
        Map<String, Object> decision = Json.object(Json.object(defs, "TargetDecision"), "properties");
        for (String forbidden : List.of("value", "text", "evidence", "statements", "pageText")) {
            assertFalse(decision.containsKey(forbidden) || Json.object(Json.object(decision, "basis"), "properties").containsKey(forbidden), forbidden);
        }
    }

    // ================================================================================================== fixtures válidas

    private void regenerate() throws Exception {
        Automation a = RichAutomation.withDecisions(clock);
        ProjectFiles.Snapshot s = ProjectFiles.Snapshot.of(a, UUID.randomUUID().toString());
        Instant savedAt = Instant.parse("2026-10-17T13:00:00Z");
        Files.createDirectories(VALID);
        Path p = dir.resolve("p.nekkora");
        Files.write(p, files.serialize(s, ProjectFormat.Kind.PROJECT, null, savedAt));
        Files.write(VALID.resolve(FIXTURE), ProjectFormatContractTest.zip(ProjectFormatContractTest.deterministic(Tamper.entries(p))));
        Files.write(p, files.serialize(s, ProjectFormat.Kind.RECOVERY, new ProjectPackage.RecoveryOrigin("C:/Projetos/consulta-clientes.nekkora",
                new ProjectRepository.Fingerprint(1234, "ab".repeat(32))), savedAt));
        Files.write(VALID.resolve(RECOVERY_FIXTURE), ProjectFormatContractTest.zip(ProjectFormatContractTest.deterministic(Tamper.entries(p))));
    }

    @Test
    @DisplayName("fixtures válidas 1.2.0: abertas, conformes ao schema 1.2.0, decisões e substituto preservados, reescritas de forma idêntica")
    void fixturesValidas() throws Exception {
        if (Boolean.getBoolean("nekkora.fixtures12.regenerate")) regenerate();
        assertTrue(Files.exists(VALID.resolve(FIXTURE)), "fixture congelada ausente (gerar com -Dnekkora.fixtures12.regenerate=true)");
        ProjectFormatContractTest.Validator v = new ProjectFormatContractTest.Validator(schema());
        for (String name : List.of(FIXTURE, RECOVERY_FIXTURE)) {
            Path p = VALID.resolve(name);
            Map<String, byte[]> entries = Tamper.entries(p);
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                List<String> errors = v.validate(Json.parse(new String(e.getValue(), StandardCharsets.UTF_8)), ProjectFormat11ContractTest.definitionFor(e.getKey()));
                assertTrue(errors.isEmpty(), name + " " + e.getKey() + ": " + errors);
            }
            Path copy = Files.copy(p, dir.resolve(name));
            ProjectFiles.Loaded loaded = name.equals(FIXTURE) ? files.load(copy, clock) : files.loadRecoveryUnchecked(copy, clock);
            assertEquals(ProjectFormat.FORMAT_VERSION_1_2, loaded.manifest().formatVersion());
            Automation a = loaded.automation();
            assertEquals(ModelVersion.V1_2, a.modelVersion());
            AutomationStep entrar = RichAutomation.step(a, "Clicar no botão Entrar");
            assertEquals(DecisionActor.TEST_AGENT, entrar.targetDecision().orElseThrow().actor());
            assertEquals(List.of("LOOKALIKES_PRESENT"), entrar.targetDecision().orElseThrow().basis().reasons());
            AutomationStep pesquisar = RichAutomation.step(a, "Clicar no botão Pesquisar");
            assertEquals("#pesquisar-v2", pesquisar.element().orElseThrow().preferredLocator().orElseThrow().expression(), "alvo substituto efetivo");
            assertEquals("#pesquisar", pesquisar.capturedElement().orElseThrow().preferredLocator().orElseThrow().expression(), "captura intacta");
            assertTrue(pesquisar.modified(StepField.TARGET));
            assertEquals(TargetDecision.Kind.REJECTED, RichAutomation.step(a, "Selecionar São Paulo no campo Estado").targetDecision().orElseThrow().kind());
            Map<String, Object> again = ProjectCodec.documents(a);
            for (Map.Entry<String, Object> d : again.entrySet()) {
                assertEquals(Json.parse(new String(entries.get(d.getKey()), StandardCharsets.UTF_8)), Json.parse(Json.write(d.getValue())), name + " " + d.getKey());
            }
        }
    }

    @Test
    @DisplayName("versão mínima: decidir → 1.2.0; desfazer a decisão → volta ao 1.1.0 byte a byte; restaurar o alvo original → sem 1.2")
    void versaoMinima() throws Exception {
        Automation a = RichAutomation.withVariables(clock);
        Automation.Snapshot before = a.snapshot();
        Path p = dir.resolve("p.nekkora");
        Files.write(p, files.serialize(ProjectFiles.Snapshot.of(a, PROJECT), ProjectFormat.Kind.PROJECT, null, Instant.parse("2026-10-17T13:00:00Z")));
        Map<String, byte[]> v11 = Tamper.entries(p);
        a.decideTarget(RichAutomation.id(a, "Clicar no botão Entrar"), TargetDecision.Kind.CONFIRMED, DecisionActor.DEVELOPER,
                TargetDecision.Basis.notInspected());
        Files.write(p, files.serialize(ProjectFiles.Snapshot.of(a, PROJECT), ProjectFormat.Kind.PROJECT, null, Instant.parse("2026-10-17T13:00:00Z")));
        assertEquals(ProjectFormat.FORMAT_VERSION_1_2, files.load(p, clock).manifest().formatVersion());
        a.restore(before);
        Files.write(p, files.serialize(ProjectFiles.Snapshot.of(a, PROJECT), ProjectFormat.Kind.PROJECT, null, Instant.parse("2026-10-17T13:00:00Z")));
        Map<String, byte[]> again = Tamper.entries(p);
        assertEquals(List.copyOf(v11.keySet()), List.copyOf(again.keySet()));
        assertEquals(ProjectFormat.FORMAT_VERSION_1_1, Json.parseObject(new String(again.get(ProjectFormat.MANIFEST), StandardCharsets.UTF_8))
                .get("formatVersion"), "sem decisões, de volta ao 1.1.0");
        for (String e : v11.keySet()) {
            // manifesto e automation.json registram revisão/instantes; as etapas, sessões e variáveis voltam idênticas
            if (!e.equals(ProjectFormat.MANIFEST) && !e.equals(ProjectFormat.AUTOMATION)) assertArrayEquals(v11.get(e), again.get(e), e);
        }
        Automation b = RichAutomation.build(clock);
        b.replaceTarget(RichAutomation.id(b, "Clicar no botão Entrar"), RichAutomation.element("Entrar", "BUTTON", "button", "entrar-v2", false, false),
                DecisionActor.DEVELOPER, TargetDecision.Basis.notInspected());
        assertEquals(ModelVersion.V1_2, b.modelVersion(), "1.2 com decisões, mesmo sem variáveis");
        Files.write(p, files.serialize(ProjectFiles.Snapshot.of(b, PROJECT), ProjectFormat.Kind.PROJECT, null, Instant.now()));
        assertFalse(Tamper.entries(p).containsKey(ProjectFormat.VARIABLES), "catálogo opcional no 1.2");
        Automation reopened = files.load(p, clock).automation();
        assertTrue(reopened.restoreOriginal(RichAutomation.id(reopened, "Clicar no botão Entrar"), StepField.TARGET));
        assertEquals(ModelVersion.CURRENT, reopened.modelVersion());
    }

    // ================================================================================================== fixtures inválidas

    @Test
    @DisplayName("fixtures inválidas 1.2.0: cada uma recusada com o erro esperado; o schema recusa as estruturais")
    void fixturesInvalidas() throws Exception {
        ProjectFormatContractTest.Validator v = new ProjectFormatContractTest.Validator(schema());
        List<Path> cases;
        try (Stream<Path> s = Files.list(INVALID)) {
            cases = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        assertTrue(cases.size() >= 8, "casos: " + cases.size());
        for (Path c : cases) {
            Map<String, Object> spec = Json.parseObject(Files.readString(c));
            Path base = Json.string(spec, "base").startsWith("1.1:") ? ProjectFormat11ContractTest.VALID.resolve(Json.string(spec, "base").substring(4))
                    : VALID.resolve(Json.string(spec, "base"));
            Map<String, byte[]> entries = Tamper.entries(base);
            List<String> changed = new ArrayList<>();
            for (Object o : Json.array(spec, "changes")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> change = (Map<String, Object>) o;
                String entry = ProjectFormatContractTest.resolve(entries, Json.string(change, "entry"));
                Object doc = Json.parse(new String(entries.get(entry), StandardCharsets.UTF_8));
                doc = ProjectFormatContractTest.apply(doc, Json.string(change, "pointer"), Json.string(change, "op"), change.get("value"));
                entries.put(entry, Json.write(doc).getBytes(StandardCharsets.UTF_8));
                changed.add(entry);
            }
            Tamper.rehash(entries);
            Path p = dir.resolve(c.getFileName() + ".nekkora");
            Files.write(p, ProjectFormatContractTest.zip(entries));
            ProjectException e = assertThrows(ProjectException.class, () -> files.load(p, clock), c.getFileName().toString());
            assertEquals(Json.string(spec, "expectedError"), e.error().name(), c.getFileName() + ": " + e.getMessage());
            Object schemaRejects = spec.get("schemaRejects");
            if (schemaRejects instanceof Boolean rejects && !Json.string(spec, "base").startsWith("1.1:")) {
                boolean anyError = false;
                for (String entry : changed) {
                    anyError |= !v.validate(Json.parse(new String(entries.get(entry), StandardCharsets.UTF_8)),
                            ProjectFormat11ContractTest.definitionFor(entry)).isEmpty();
                }
                assertEquals(rejects, anyError, c.getFileName() + ": schema " + (rejects ? "deveria recusar" : "não cobre esta regra (semântica)"));
            }
        }
    }
}
