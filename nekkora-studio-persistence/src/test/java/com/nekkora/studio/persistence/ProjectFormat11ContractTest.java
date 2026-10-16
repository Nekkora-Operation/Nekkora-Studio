package com.nekkora.studio.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.ModelVersion;
import com.nekkora.studio.domain.variable.SemanticAuthority;
import com.nekkora.studio.domain.variable.SemanticType;
import com.nekkora.studio.domain.variable.Sensitivity;
import com.nekkora.studio.domain.variable.TextFormat;
import com.nekkora.studio.domain.variable.VariableDefinition;
import com.nekkora.studio.domain.variable.VariableDraft;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.variable.VariableScope;
import com.nekkora.studio.domain.variable.VariableType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contrato versionado do formato {@code .nekkora} 1.1.0 (STUDIO-008, D-S008-06): o 1.0.0 mais UMA entrada,
 * {@code variables.json}. Schema próprio, fixtures válidas próprias (congeladas) e inválidas. O contrato 1.0.0 continua
 * intacto em {@link ProjectFormatContractTest}; aqui também se confere que o escritor continua produzindo 1.0.0, byte a
 * byte, para uma automação sem variáveis.
 *
 * <p>Regeneração SEPARADA da do 1.0.0 ({@code -Dnekkora.fixtures11.regenerate=true}): regenerar o 1.1.0 nunca toca nas
 * fixtures 1.0.0.
 */
class ProjectFormat11ContractTest {

    static final Path ROOT = Path.of("src", "test", "resources", "contracts", "nekkora-project", ProjectFormat.FORMAT_VERSION_1_1);
    static final Path VALID = ROOT.resolve("fixtures").resolve("valid");
    static final Path INVALID = ROOT.resolve("fixtures").resolve("invalid");
    static final String FIXTURE = "consulta-clientes-variaveis.nekkora";
    static final String RECOVERY_FIXTURE = "consulta-clientes-variaveis-recuperacao.nekkora";

    @TempDir
    Path dir;

    private final RichAutomation.TickClock clock = new RichAutomation.TickClock("2026-10-16T12:00:00Z");
    private final ProjectFiles files = new ProjectFiles(new ProjectRepository(), "teste");

    private static Map<String, Object> schema() throws IOException {
        return Json.parseObject(Files.readString(ROOT.resolve("nekkora-project.schema.json")));
    }

    private static Map<String, Object> schema10() throws IOException {
        return Json.parseObject(Files.readString(ProjectFormatContractTest.ROOT.resolve("nekkora-project.schema.json")));
    }

    static String definitionFor(String entry) {
        return entry.equals(ProjectFormat.VARIABLES) ? "Variables" : ProjectFormatContractTest.definitionFor(entry);
    }

    // ================================================================================================== schema

    @Test
    @DisplayName("schema 1.1.0: vocabulários das variáveis idênticos aos do domínio; o resto idêntico ao schema 1.0.0")
    void schemaIgualAoCodigo() throws Exception {
        Map<String, Object> defs = Json.object(schema(), "$defs");
        Map<String, Class<? extends Enum<?>>> enums = new LinkedHashMap<>();
        enums.put("VariableType", VariableType.class);
        enums.put("SemanticType", SemanticType.class);
        enums.put("SemanticAuthority", SemanticAuthority.class);
        enums.put("Sensitivity", Sensitivity.class);
        enums.put("VariableScopeKind", VariableScope.Kind.class);
        enums.put("VariableOriginKind", VariableOrigin.Kind.class);
        enums.put("TextFormat", TextFormat.class);
        for (Map.Entry<String, Class<? extends Enum<?>>> e : enums.entrySet()) {
            List<String> java = Arrays.stream(e.getValue().getEnumConstants()).map(Enum::name).toList();
            assertEquals(java, Json.array(Json.object(defs, e.getKey()), "enum"), e.getKey());
        }
        // tudo o que já existia no 1.0.0 continua igual — só Manifest, Automation e EntryPath mudam (versões e a entrada nova)
        Map<String, Object> defs10 = Json.object(schema10(), "$defs");
        Set<String> changed = Set.of("Manifest", "Automation", "EntryPath");
        for (String name : defs10.keySet()) {
            if (!changed.contains(name)) assertEquals(Json.write(defs10.get(name)), Json.write(defs.get(name)), name);
        }
        Map<String, Object> manifest = Json.object(Json.object(defs, "Manifest"), "properties");
        String pattern = Json.string(Json.object(manifest, "formatVersion"), "pattern");
        assertTrue(ProjectFormat.FORMAT_VERSION_1_1.matches(pattern));
        assertFalse(ProjectFormat.FORMAT_VERSION.matches(pattern), "1.0.x é descrito pelo schema 1.0.0");
        assertEquals(ModelVersion.V1_1, Json.object(Json.object(Json.object(manifest, "automationModel"), "properties"), "version").get("const"));
        assertEquals(ModelVersion.V1_1, Json.object(Json.object(Json.object(defs, "Automation"), "properties"), "modelVersion").get("const"));
        String entryPattern = Json.string(Json.object(defs, "EntryPath"), "pattern");
        for (String ok : List.of("variables.json", "automation.json", "steps/" + UUID.randomUUID() + ".json")) {
            assertTrue(ok.matches(entryPattern) && ProjectFormat.ENTRY_NAME.matcher(ok).matches(), ok);
        }
        for (String bad : List.of("variables/a.json", "valores.json", "../variables.json", "secrets.json")) {
            assertFalse(bad.matches(entryPattern) || ProjectFormat.ENTRY_NAME.matcher(bad).matches(), bad);
        }
        // a definição de variável não tem campo de valor de execução
        Map<String, Object> variable = Json.object(Json.object(defs, "Variable"), "properties");
        for (String forbidden : List.of("value", "runtimeValue", "secret", "example")) assertFalse(variable.containsKey(forbidden), forbidden);
    }

    // ================================================================================================== fixtures válidas

    private void regenerate() throws Exception {
        Automation a = RichAutomation.withVariables(clock);
        ProjectFiles.Snapshot s = ProjectFiles.Snapshot.of(a, UUID.randomUUID().toString());
        Instant savedAt = Instant.parse("2026-10-16T13:00:00Z");
        Files.createDirectories(VALID);
        Path p = dir.resolve("p.nekkora");
        Files.write(p, files.serialize(s, ProjectFormat.Kind.PROJECT, null, savedAt));
        Files.write(VALID.resolve(FIXTURE), ProjectFormatContractTest.zip(ProjectFormatContractTest.deterministic(Tamper.entries(p))));
        Files.write(p, files.serialize(s, ProjectFormat.Kind.RECOVERY, new ProjectPackage.RecoveryOrigin("C:/Projetos/consulta-clientes.nekkora",
                new ProjectRepository.Fingerprint(1234, "ab".repeat(32))), savedAt));
        Files.write(VALID.resolve(RECOVERY_FIXTURE), ProjectFormatContractTest.zip(ProjectFormatContractTest.deterministic(Tamper.entries(p))));
    }

    @Test
    @DisplayName("fixtures válidas 1.1.0: abertas, conformes ao schema 1.1.0 e reescritas de forma idêntica")
    void fixturesValidas() throws Exception {
        if (Boolean.getBoolean("nekkora.fixtures11.regenerate")) regenerate();
        assertTrue(Files.exists(VALID.resolve(FIXTURE)), "fixture congelada ausente (gerar com -Dnekkora.fixtures11.regenerate=true)");
        ProjectFormatContractTest.Validator v = new ProjectFormatContractTest.Validator(schema());
        for (String name : List.of(FIXTURE, RECOVERY_FIXTURE)) {
            Path p = VALID.resolve(name);
            Map<String, byte[]> entries = Tamper.entries(p);
            assertTrue(entries.containsKey(ProjectFormat.VARIABLES), name);
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                List<String> errors = v.validate(Json.parse(new String(e.getValue(), StandardCharsets.UTF_8)), definitionFor(e.getKey()));
                assertTrue(errors.isEmpty(), name + " " + e.getKey() + ": " + errors);
            }
            Path copy = Files.copy(p, dir.resolve(name));
            ProjectFiles.Loaded loaded = name.equals(FIXTURE) ? files.load(copy, clock) : files.loadRecoveryUnchecked(copy, clock);
            assertEquals(ProjectFormat.FORMAT_VERSION_1_1, loaded.manifest().formatVersion());
            Automation a = loaded.automation();
            assertEquals(9, a.stepCount());
            assertEquals(8, a.variables().size());
            assertEquals(ModelVersion.V1_1, a.modelVersion());
            VariableDefinition usuario = a.variableNamed("cliente.usuario").orElseThrow();
            assertEquals(1, a.consumersOf(usuario.id()).size(), "a etapa que usa a variável continua ligada a ela");
            assertTrue(usuario.updatedAt().isAfter(usuario.createdAt()));
            Map<String, Object> again = ProjectCodec.documents(a);
            for (Map.Entry<String, Object> d : again.entrySet()) {
                assertEquals(Json.parse(new String(entries.get(d.getKey()), StandardCharsets.UTF_8)), Json.parse(Json.write(d.getValue())), name + " " + d.getKey());
            }
            assertEquals(entries.size() - 2, again.size(), "mesmas entradas (fora manifesto e metadados)");
        }
    }

    @Test
    @DisplayName("o que o Studio escreve com variáveis é 1.1.0 e conforme ao schema 1.1.0; nenhum valor de entrada é gravado")
    void escritaAtualConforme() throws Exception {
        ProjectFormatContractTest.Validator v = new ProjectFormatContractTest.Validator(schema());
        Automation a = RichAutomation.withVariables(clock);
        ProjectFiles.Snapshot s = ProjectFiles.Snapshot.of(a, UUID.randomUUID().toString());
        for (ProjectFormat.Kind kind : ProjectFormat.Kind.values()) {
            Path p = dir.resolve(kind + ".nekkora");
            Files.write(p, files.serialize(s, kind, kind == ProjectFormat.Kind.RECOVERY ? new ProjectPackage.RecoveryOrigin(null, null) : null, Instant.now()));
            Map<String, byte[]> entries = Tamper.entries(p);
            assertEquals(ProjectFormat.FORMAT_VERSION_1_1, Json.parseObject(new String(entries.get(ProjectFormat.MANIFEST), StandardCharsets.UTF_8))
                    .get("formatVersion"));
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                List<String> errors = v.validate(Json.parse(new String(e.getValue(), StandardCharsets.UTF_8)), definitionFor(e.getKey()));
                assertTrue(errors.isEmpty(), kind + " " + e.getKey() + ": " + errors);
            }
            String variables = new String(entries.get(ProjectFormat.VARIABLES), StandardCharsets.UTF_8);
            assertTrue(variables.contains("\"constant\":\"termo de pesquisa\""), "constante pública declarada é gravada (não criptografada)");
            assertFalse(variables.contains("\"value\""), "nenhum campo de valor");
        }
    }

    @Test
    @DisplayName("sem variáveis o escritor continua produzindo o 1.0.0 congelado, byte a byte")
    void semVariaveisContinua10ByteAByte() throws Exception {
        Automation a = RichAutomation.build(clock);
        ProjectFiles.Snapshot s = ProjectFiles.Snapshot.of(a, UUID.randomUUID().toString());
        Path p = dir.resolve("p.nekkora");
        Files.write(p, files.serialize(s, ProjectFormat.Kind.PROJECT, null, Instant.parse("2026-10-16T13:00:00Z")));
        byte[] fresh = ProjectFormatContractTest.zip(ProjectFormatContractTest.deterministic(Tamper.entries(p)));
        byte[] frozen = Files.readAllBytes(ProjectFormatContractTest.VALID.resolve(ProjectFormatContractTest.FIXTURE));
        assertArrayEquals(frozen, fresh);
    }

    @Test
    @DisplayName("formato acompanha o conteúdo: 1.0 → declarar → 1.1; remover a última variável → 1.0 de novo")
    void versaoMinima() throws Exception {
        Path p10 = Files.copy(ProjectFormatContractTest.VALID.resolve(ProjectFormatContractTest.FIXTURE), dir.resolve("antigo.nekkora"));
        Automation a = files.load(p10, clock).automation();
        assertEquals(ModelVersion.CURRENT, a.modelVersion());
        VariableDefinition v = a.declareVariable(VariableDraft.of("cliente.email", VariableType.STRING)
                .withSemantic(SemanticType.EMAIL, SemanticAuthority.USER_CONFIRMED).withSensitivity(Sensitivity.PERSONAL_DATA)
                .withOrigin(VariableOrigin.input()));
        Path p = dir.resolve("novo.nekkora");
        Files.write(p, files.serialize(ProjectFiles.Snapshot.of(a, UUID.randomUUID().toString()), ProjectFormat.Kind.PROJECT, null, Instant.now()));
        ProjectFiles.Loaded loaded = files.load(p, clock);
        assertEquals(ProjectFormat.FORMAT_VERSION_1_1, loaded.manifest().formatVersion());
        assertEquals(List.of(v), loaded.automation().variables());
        a.removeVariable(v.id(), false);
        Files.write(p, files.serialize(ProjectFiles.Snapshot.of(a, UUID.randomUUID().toString()), ProjectFormat.Kind.PROJECT, null, Instant.now()));
        assertEquals(ProjectFormat.FORMAT_VERSION, files.load(p, clock).manifest().formatVersion());
        assertFalse(Tamper.entries(p).containsKey(ProjectFormat.VARIABLES));
    }

    @Test
    @DisplayName("cópia de segurança da migração: só do conteúdo esperado, nunca substitui um arquivo existente")
    void copiaDeSeguranca() throws Exception {
        Path file = Files.copy(ProjectFormatContractTest.VALID.resolve(ProjectFormatContractTest.FIXTURE), dir.resolve("projeto.nekkora"));
        ProjectRepository repo = new ProjectRepository();
        ProjectRepository.Fingerprint fp = repo.fingerprintOf(file);
        ProjectException changed = assertThrows(ProjectException.class, () -> repo.backupCopy(file, new ProjectRepository.Fingerprint(fp.size(), "00".repeat(32)),
                "formato 1.0.0"));
        assertEquals(ProjectException.Error.CONFLICT, changed.error());
        assertFalse(Files.exists(dir.resolve("projeto (formato 1.0.0).nekkora")), "conteúdo diferente do esperado: nada copiado");
        Path first = repo.backupCopy(file, fp, "formato 1.0.0");
        Path second = repo.backupCopy(file, fp, "formato 1.0.0");
        assertEquals(dir.resolve("projeto (formato 1.0.0).nekkora"), first);
        assertEquals(dir.resolve("projeto (formato 1.0.0) 2.nekkora"), second);
        assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(first));
        try (Stream<Path> s = Files.list(dir)) {
            assertEquals(3, s.count(), "nenhum temporário deixado");
        }
    }

    // ================================================================================================== fixtures inválidas

    @Test
    @DisplayName("fixtures inválidas 1.1.0: o leitor recusa cada uma com o erro esperado; o schema recusa as estruturais")
    void fixturesInvalidas() throws Exception {
        ProjectFormatContractTest.Validator v = new ProjectFormatContractTest.Validator(schema());
        List<Path> cases;
        try (Stream<Path> s = Files.list(INVALID)) {
            cases = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        assertTrue(cases.size() >= 12, "casos: " + cases.size());
        for (Path c : cases) {
            Map<String, Object> spec = Json.parseObject(Files.readString(c));
            Map<String, byte[]> entries = Tamper.entries(VALID.resolve(Json.string(spec, "base")));
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
            for (Object remove : Json.array(spec, "removeEntries")) {
                entries.remove((String) remove);
                // o manifesto deixa de declarar a entrada removida (o pacote fica coerente; só falta o conteúdo exigido)
                Map<String, Object> m = Json.parseObject(new String(entries.get(ProjectFormat.MANIFEST), StandardCharsets.UTF_8));
                Json.array(m, "entries").removeIf(e -> remove.equals(((Map<?, ?>) e).get("path")));
                entries.put(ProjectFormat.MANIFEST, Json.write(m).getBytes(StandardCharsets.UTF_8));
            }
            if (Boolean.TRUE.equals(spec.get("rehash"))) Tamper.rehash(entries);
            Path p = dir.resolve(c.getFileName() + ".nekkora");
            Files.write(p, ProjectFormatContractTest.zip(entries));
            ProjectException e = assertThrows(ProjectException.class, () -> files.load(p, clock), c.getFileName().toString());
            assertEquals(Json.string(spec, "expectedError"), e.error().name(), c.getFileName() + ": " + e.getMessage());
            Object schemaRejects = spec.get("schemaRejects");
            if (schemaRejects instanceof Boolean rejects) {
                boolean anyError = false;
                for (String entry : changed) {
                    anyError |= !v.validate(Json.parse(new String(entries.get(entry), StandardCharsets.UTF_8)), definitionFor(entry)).isEmpty();
                }
                assertEquals(rejects, anyError, c.getFileName() + ": schema " + (rejects ? "deveria recusar" : "não cobre esta regra (semântica)"));
            }
        }
    }
}
