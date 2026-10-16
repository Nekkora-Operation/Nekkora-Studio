package com.nekkora.studio.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStatus;
import com.nekkora.studio.domain.ModelVersion;
import com.nekkora.studio.domain.StepField;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.element.ObservedObstacle;
import com.nekkora.studio.domain.evidence.EvidenceType;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary;
import com.nekkora.studio.domain.evidence.ObservedEvidence;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepOrigin;
import java.io.ByteArrayOutputStream;
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
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contrato versionado do formato {@code .nekkora} 1.0.0 (STUDIO-006, §5/§27): schema JSON, fixture válida congelada e
 * fixtures inválidas. O schema descreve a estrutura; o leitor do Studio é a autoridade (estrutura + regras semânticas).
 * As fixtures inválidas dizem, cada uma, o erro esperado do leitor e se o schema sozinho já a recusa.
 *
 * <p>A fixture válida é gerada uma vez a partir da automação completa do §36, com identidades determinísticas, e
 * congelada: {@code -Dnekkora.fixtures.regenerate=true} só deve ser usado numa mudança de formato revisada.
 */
class ProjectFormatContractTest {

    static final Path ROOT = Path.of("src", "test", "resources", "contracts", "nekkora-project", ProjectFormat.FORMAT_VERSION);
    static final Path VALID = ROOT.resolve("fixtures").resolve("valid");
    static final Path INVALID = ROOT.resolve("fixtures").resolve("invalid");
    static final String FIXTURE = "consulta-clientes.nekkora";
    static final String RECOVERY_FIXTURE = "consulta-clientes-recuperacao.nekkora";

    @TempDir
    Path dir;

    private final RichAutomation.TickClock clock = new RichAutomation.TickClock("2026-10-16T12:00:00Z");
    private final ProjectFiles files = new ProjectFiles(new ProjectRepository(), "teste");

    // ================================================================================================== schema

    private static Map<String, Object> schema() throws IOException {
        return Json.parseObject(Files.readString(ROOT.resolve("nekkora-project.schema.json")));
    }

    /** Definição do schema que valida uma entrada do pacote. */
    static String definitionFor(String entry) {
        if (entry.equals(ProjectFormat.MANIFEST)) return "Manifest";
        if (entry.equals(ProjectFormat.METADATA)) return "Metadata";
        if (entry.equals(ProjectFormat.AUTOMATION)) return "Automation";
        if (entry.equals(ProjectFormat.SESSIONS)) return "Sessions";
        if (entry.equals(ProjectFormat.UNATTRIBUTED)) return "UnattributedEvidence";
        if (ProjectFormat.STEP_ENTRY.matcher(entry).matches()) return "Step";
        if (ProjectFormat.EVIDENCE_ENTRY.matcher(entry).matches()) return "StepEvidence";
        throw new AssertionError("entrada sem definição: " + entry);
    }

    @Test
    @DisplayName("schema 1.0.0: vocabulários idênticos aos do domínio; constantes iguais às do leitor")
    void schemaIgualAoCodigo() throws Exception {
        Map<String, Object> defs = Json.object(schema(), "$defs");
        Map<String, Class<? extends Enum<?>>> enums = new LinkedHashMap<>();
        enums.put("AutomationStatus", AutomationStatus.class);
        enums.put("StepOrigin", StepOrigin.class);
        enums.put("StepField", StepField.class);
        enums.put("ActionType", ActionType.class);
        enums.put("CaptureMode", RecordingObservation.CaptureMode.class);
        enums.put("Observation", RecordingObservation.Observation.class);
        enums.put("BusinessOutcome", RecordingObservation.BusinessOutcome.class);
        enums.put("CorrelationKey", CorrelationKeys.Key.class);
        enums.put("LocatorStrategy", LocatorStrategy.class);
        enums.put("LocatorStability", LocatorStability.class);
        enums.put("AccessStepType", AccessStep.Type.class);
        enums.put("Severity", ObservedObstacle.Severity.class);
        enums.put("EvidenceType", EvidenceType.class);
        enums.put("EvidenceSource", EvidenceVocabulary.Source.class);
        enums.put("EvidenceRelation", EvidenceVocabulary.Relation.class);
        enums.put("EvidenceConfidence", EvidenceVocabulary.Confidence.class);
        enums.put("EvidenceClassification", EvidenceVocabulary.Classification.class);
        enums.put("EvidenceSignal", EvidenceVocabulary.Signal.class);
        enums.put("EvidenceLimitation", EvidenceVocabulary.Limitation.class);
        enums.put("StateAttribute", EvidenceVocabulary.StateAttribute.class);
        enums.put("StateValue", EvidenceVocabulary.StateValue.class);
        enums.put("MessageAvailability", ObservedEvidence.Message.Availability.class);
        for (Map.Entry<String, Class<? extends Enum<?>>> e : enums.entrySet()) {
            List<String> java = Arrays.stream(e.getValue().getEnumConstants()).map(Enum::name).toList();
            assertEquals(java, Json.array(Json.object(defs, e.getKey()), "enum"), e.getKey());
        }
        Map<String, Object> manifest = Json.object(Json.object(defs, "Manifest"), "properties");
        assertEquals(ProjectFormat.FORMAT, Json.object(manifest, "format").get("const"));
        assertEquals(ProjectFormat.PRIVACY_POLICY, Json.object(manifest, "privacyPolicy").get("const"));
        assertTrue(ProjectFormat.FORMAT_VERSION.matches(Json.string(Json.object(manifest, "formatVersion"), "pattern")));
        Map<String, Object> model = Json.object(Json.object(manifest, "automationModel"), "properties");
        assertEquals(ModelVersion.SCHEMA, Json.object(model, "schema").get("const"));
        assertEquals(ModelVersion.CURRENT, Json.object(model, "version").get("const"));
        String entryPattern = Json.string(Json.object(defs, "EntryPath"), "pattern");
        for (String ok : List.of("automation.json", "steps/" + UUID.randomUUID() + ".json", "evidence/unattributed.json")) {
            assertTrue(ok.matches(entryPattern) && ProjectFormat.ENTRY_NAME.matcher(ok).matches(), ok);
        }
        for (String bad : List.of("manifest.json", "../x.json", "/abs.json", "steps/x/../../a.json", "scripts/a.js", "steps/A.json")) {
            assertFalse(bad.matches(entryPattern), bad);
        }
    }

    // ================================================================================================== fixtures válidas

    /** Pacote com as entradas dadas, data fixa e ordem preservada (bytes reprodutíveis). */
    static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry ze = new ZipEntry(e.getKey());
                ze.setTime(315_532_800_000L);
                z.putNextEntry(ze);
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static final Pattern UUID_TEXT = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    /** Troca as identidades aleatórias por identidades fixas (na ordem em que aparecem), nos nomes e no conteúdo. */
    static Map<String, byte[]> deterministic(Map<String, byte[]> entries) {
        Map<String, String> ids = new LinkedHashMap<>();
        java.util.function.UnaryOperator<String> remap = text -> {
            Matcher m = UUID_TEXT.matcher(text);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String fixed = ids.computeIfAbsent(m.group(), k -> String.format("00000000-0000-4000-8000-%012d", ids.size() + 1));
                m.appendReplacement(sb, fixed);
            }
            m.appendTail(sb);
            return sb.toString();
        };
        Map<String, byte[]> out = new LinkedHashMap<>();
        // o manifesto primeiro: projectId e automationId recebem as primeiras identidades
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            out.put(remap.apply(e.getKey()), remap.apply(new String(e.getValue(), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
        }
        Tamper.rehash(out);
        return out;
    }

    private void regenerate() throws Exception {
        Automation a = RichAutomation.build(clock);
        ProjectFiles.Snapshot s = ProjectFiles.Snapshot.of(a, UUID.randomUUID().toString());
        Instant savedAt = Instant.parse("2026-10-16T13:00:00Z");
        Files.createDirectories(VALID);
        Path p = dir.resolve("p.nekkora");
        Files.write(p, files.serialize(s, ProjectFormat.Kind.PROJECT, null, savedAt));
        Files.write(VALID.resolve(FIXTURE), zip(deterministic(Tamper.entries(p))));
        Files.write(p, files.serialize(s, ProjectFormat.Kind.RECOVERY, new ProjectPackage.RecoveryOrigin("C:/Projetos/consulta-clientes.nekkora",
                new ProjectRepository.Fingerprint(1234, "ab".repeat(32))), savedAt));
        Files.write(VALID.resolve(RECOVERY_FIXTURE), zip(deterministic(Tamper.entries(p))));
    }

    @Test
    @DisplayName("fixtures válidas 1.0.0: abertas pelo Studio, conformes ao schema e reescritas de forma idêntica (formato estável)")
    void fixturesValidas() throws Exception {
        if (Boolean.getBoolean("nekkora.fixtures.regenerate")) regenerate();
        assertTrue(Files.exists(VALID.resolve(FIXTURE)), "fixture congelada ausente (gerar com -Dnekkora.fixtures.regenerate=true)");
        Validator v = new Validator(schema());
        for (String name : List.of(FIXTURE, RECOVERY_FIXTURE)) {
            Path p = VALID.resolve(name);
            Map<String, byte[]> entries = Tamper.entries(p);
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                List<String> errors = v.validate(Json.parse(new String(e.getValue(), StandardCharsets.UTF_8)), definitionFor(e.getKey()));
                assertTrue(errors.isEmpty(), name + " " + e.getKey() + ": " + errors);
            }
            Path copy = Files.copy(p, dir.resolve(name));
            ProjectFiles.Loaded loaded = name.equals(FIXTURE) ? files.load(copy, clock) : files.loadRecoveryUnchecked(copy, clock);
            assertEquals(name.equals(FIXTURE) ? ProjectFormat.Kind.PROJECT : ProjectFormat.Kind.RECOVERY, loaded.manifest().kind());
            assertEquals(9, loaded.automation().stepCount());
            // reescrever o que foi lido produz exatamente os mesmos documentos: nada se perde nem muda de forma
            Map<String, Object> again = ProjectCodec.documents(loaded.automation());
            for (Map.Entry<String, Object> d : again.entrySet()) {
                assertEquals(Json.parse(new String(entries.get(d.getKey()), StandardCharsets.UTF_8)), d.getValue() instanceof Map || d.getValue() instanceof List
                        ? Json.parse(Json.write(d.getValue())) : d.getValue(), name + " " + d.getKey());
            }
            assertEquals(entries.size() - 2, again.size(), "mesmas entradas (fora manifesto e metadados)");
        }
    }

    @Test
    @DisplayName("o que o Studio escreve hoje (projeto e recuperação) é conforme ao schema 1.0.0")
    void escritaAtualConforme() throws Exception {
        Validator v = new Validator(schema());
        Automation a = RichAutomation.build(clock);
        ProjectFiles.Snapshot s = ProjectFiles.Snapshot.of(a, UUID.randomUUID().toString());
        for (ProjectFormat.Kind kind : ProjectFormat.Kind.values()) {
            Path p = dir.resolve(kind + ".nekkora");
            Files.write(p, files.serialize(s, kind, kind == ProjectFormat.Kind.RECOVERY ? new ProjectPackage.RecoveryOrigin(null, null) : null, Instant.now()));
            for (Map.Entry<String, byte[]> e : Tamper.entries(p).entrySet()) {
                List<String> errors = v.validate(Json.parse(new String(e.getValue(), StandardCharsets.UTF_8)), definitionFor(e.getKey()));
                assertTrue(errors.isEmpty(), kind + " " + e.getKey() + ": " + errors);
            }
        }
    }

    // ================================================================================================== fixtures inválidas

    @Test
    @DisplayName("fixtures inválidas: o leitor recusa cada uma com o erro esperado; o schema recusa as estruturais")
    void fixturesInvalidas() throws Exception {
        Validator v = new Validator(schema());
        List<Path> cases;
        try (Stream<Path> s = Files.list(INVALID)) {
            cases = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        assertTrue(cases.size() >= 15, "casos: " + cases.size());
        for (Path c : cases) {
            Map<String, Object> spec = Json.parseObject(Files.readString(c));
            Map<String, byte[]> entries = Tamper.entries(VALID.resolve(Json.string(spec, "base")));
            List<String> changed = new ArrayList<>();
            for (Object o : Json.array(spec, "changes")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> change = (Map<String, Object>) o;
                String entry = resolve(entries, Json.string(change, "entry"));
                Object doc = Json.parse(new String(entries.get(entry), StandardCharsets.UTF_8));
                doc = apply(doc, Json.string(change, "pointer"), Json.string(change, "op"), change.get("value"));
                entries.put(entry, Json.write(doc).getBytes(StandardCharsets.UTF_8));
                changed.add(entry);
            }
            for (Map.Entry<String, Object> add : Json.object(spec, "addEntries").entrySet()) {
                entries.put(add.getKey(), String.valueOf(add.getValue()).getBytes(StandardCharsets.UTF_8));
            }
            if (Boolean.TRUE.equals(spec.get("rehash"))) Tamper.rehash(entries);
            Path p = dir.resolve(c.getFileName() + ".nekkora");
            Files.write(p, zip(entries));
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

    /** {@code step:N} / {@code evidence:N}: N-ésima etapa (ordem das sessões) / N-ésimo documento de evidências de etapa. */
    @SuppressWarnings("unchecked")
    static String resolve(Map<String, byte[]> entries, String entry) {
        if (!entry.contains(":")) return entry;
        int n = Integer.parseInt(entry.substring(entry.indexOf(':') + 1));
        Map<String, Object> sessions = (Map<String, Object>) Json.parse(new String(entries.get(ProjectFormat.SESSIONS), StandardCharsets.UTF_8));
        List<String> steps = new ArrayList<>();
        for (Object s : (List<Object>) sessions.get("sessions")) for (Object id : (List<Object>) ((Map<String, Object>) s).get("steps")) steps.add((String) id);
        if (entry.startsWith("step:")) return ProjectFormat.STEPS_DIR + steps.get(n) + ".json";
        List<String> withEvidence = steps.stream().filter(id -> entries.containsKey(ProjectFormat.EVIDENCE_DIR + id + ".json")).toList();
        return ProjectFormat.EVIDENCE_DIR + withEvidence.get(n) + ".json";
    }

    /** Aplica set/remove num ponteiro JSON (RFC 6901, sem escapes). */
    @SuppressWarnings("unchecked")
    static Object apply(Object doc, String pointer, String op, Object value) {
        String[] parts = pointer.substring(1).split("/");
        Object parent = doc;
        for (int i = 0; i < parts.length - 1; i++) {
            parent = parent instanceof List<?> l ? l.get(Integer.parseInt(parts[i])) : ((Map<String, Object>) parent).get(parts[i]);
            Objects.requireNonNull(parent, pointer);
        }
        String last = parts[parts.length - 1];
        if (parent instanceof List<?> l) {
            List<Object> list = (List<Object>) l;
            if (op.equals("remove")) list.remove(Integer.parseInt(last));
            else list.set(Integer.parseInt(last), value);
        } else {
            Map<String, Object> map = (Map<String, Object>) parent;
            if (op.equals("remove")) Objects.requireNonNull(map.remove(last), pointer);
            else map.put(last, value);
        }
        return doc;
    }

    // ================================================================================================== validador

    /**
     * Validador mínimo de JSON Schema 2020-12 — só as palavras-chave usadas pelo schema do projeto ({@code $ref} local,
     * type, const, enum, pattern, min/maxLength, minimum/maximum, required, properties, additionalProperties,
     * propertyNames, items, min/maxItems, anyOf). Palavra-chave desconhecida falha (o schema não pode usar algo que o
     * teste não confere).
     */
    static final class Validator {
        private static final java.util.Set<String> KNOWN = java.util.Set.of("$ref", "type", "const", "enum", "pattern", "minLength", "maxLength", "minimum",
                "maximum", "required", "properties", "additionalProperties", "propertyNames", "items", "minItems", "maxItems", "anyOf", "description");
        private final Map<String, Object> defs;

        Validator(Map<String, Object> schema) {
            this.defs = Json.object(schema, "$defs");
        }

        List<String> validate(Object value, String definition) {
            List<String> errors = new ArrayList<>();
            check(value, Json.object(defs, definition), "$", errors);
            return errors;
        }

        @SuppressWarnings("unchecked")
        private void check(Object v, Map<String, Object> s, String at, List<String> errors) {
            for (String k : s.keySet()) if (!KNOWN.contains(k)) throw new AssertionError("palavra-chave não suportada pelo validador: " + k);
            if (s.containsKey("$ref")) {
                String ref = (String) s.get("$ref");
                check(v, Json.object(defs, ref.substring("#/$defs/".length())), at, errors);
            }
            if (s.containsKey("anyOf")) {
                boolean ok = false;
                for (Object alt : (List<Object>) s.get("anyOf")) {
                    List<String> e = new ArrayList<>();
                    check(v, (Map<String, Object>) alt, at, e);
                    ok |= e.isEmpty();
                }
                if (!ok) errors.add(at + ": nenhuma alternativa");
            }
            if (s.containsKey("type")) {
                Object t = s.get("type");
                List<Object> types = t instanceof List<?> l ? (List<Object>) l : List.of(t);
                if (types.stream().noneMatch(x -> isType(v, (String) x))) {
                    errors.add(at + ": tipo " + types);
                    return;
                }
            }
            if (s.containsKey("const") && !jsonEquals(s.get("const"), v)) errors.add(at + ": const");
            if (s.containsKey("enum") && ((List<Object>) s.get("enum")).stream().noneMatch(x -> jsonEquals(x, v))) errors.add(at + ": enum");
            if (v instanceof String str) {
                if (s.containsKey("pattern") && !Pattern.compile((String) s.get("pattern")).matcher(str).find()) errors.add(at + ": pattern");
                int len = str.codePointCount(0, str.length());
                if (s.containsKey("minLength") && len < (Long) s.get("minLength")) errors.add(at + ": minLength");
                if (s.containsKey("maxLength") && len > (Long) s.get("maxLength")) errors.add(at + ": maxLength");
            }
            if (v instanceof Long n) {
                if (s.containsKey("minimum") && n < (Long) s.get("minimum")) errors.add(at + ": minimum");
                if (s.containsKey("maximum") && n > (Long) s.get("maximum")) errors.add(at + ": maximum");
            }
            if (v instanceof List<?> list) {
                if (s.containsKey("minItems") && list.size() < (Long) s.get("minItems")) errors.add(at + ": minItems");
                if (s.containsKey("maxItems") && list.size() > (Long) s.get("maxItems")) errors.add(at + ": maxItems");
                if (s.containsKey("items")) for (int i = 0; i < list.size(); i++) check(list.get(i), (Map<String, Object>) s.get("items"), at + "[" + i + "]", errors);
            }
            if (v instanceof Map<?, ?> raw) {
                Map<String, Object> obj = (Map<String, Object>) raw;
                if (s.containsKey("required")) for (Object r : (List<Object>) s.get("required")) if (!obj.containsKey(r)) errors.add(at + ": falta " + r);
                Map<String, Object> props = s.containsKey("properties") ? (Map<String, Object>) s.get("properties") : Map.of();
                for (Map.Entry<String, Object> e : obj.entrySet()) {
                    String p = at + "." + e.getKey();
                    if (s.containsKey("propertyNames")) check(e.getKey(), (Map<String, Object>) s.get("propertyNames"), p + "(nome)", errors);
                    if (props.containsKey(e.getKey())) {
                        check(e.getValue(), (Map<String, Object>) props.get(e.getKey()), p, errors);
                    } else if (Boolean.FALSE.equals(s.get("additionalProperties"))) {
                        errors.add(p + ": campo não permitido");
                    } else if (s.get("additionalProperties") instanceof Map<?, ?> extra) {
                        check(e.getValue(), (Map<String, Object>) extra, p, errors);
                    }
                }
            }
        }

        private static boolean isType(Object v, String type) {
            return switch (type) {
                case "null" -> v == Json.NULL || v == null;
                case "string" -> v instanceof String;
                case "integer" -> v instanceof Long;
                case "number" -> v instanceof Long || v instanceof Double;
                case "boolean" -> v instanceof Boolean;
                case "object" -> v instanceof Map;
                case "array" -> v instanceof List;
                default -> throw new AssertionError("tipo " + type);
            };
        }

        private static boolean jsonEquals(Object a, Object b) {
            return Json.write(a).equals(Json.write(b));
        }
    }

    @Test
    @DisplayName("o validador do teste recusa de fato (não aprova tudo)")
    void validadorRecusa() throws Exception {
        Validator v = new Validator(schema());
        assertFalse(v.validate(Json.parse("{}"), "Manifest").isEmpty());
        assertFalse(v.validate(Json.parse("{\"evidence\":[{\"x\":1}]}"), "UnattributedEvidence").isEmpty());
        assertTrue(v.validate(Json.parse("{\"evidence\":[]}"), "UnattributedEvidence").isEmpty());
        assertFalse(v.validate(Json.parse("{\"producer\":{\"application\":\"a\",\"version\":\"1\"},\"encrypted\":true,\"notice\":\"\"}"), "Metadata").isEmpty());
    }
}
