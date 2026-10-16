package com.nekkora.studio.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.Automation;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * STUDIO-006: o arquivo é entrada NÃO confiável (§17). Cada adulteração produz um erro estável e nada é devolvido — a
 * leitura é integral ou recusada.
 */
class ProjectValidationTest {

    @TempDir
    Path dir;

    private final RichAutomation.TickClock clock = new RichAutomation.TickClock("2026-10-16T12:00:00Z");
    private final ProjectFiles files = new ProjectFiles(new ProjectRepository(), "teste");
    private Map<String, byte[]> valid;

    @BeforeEach
    void projetoValido() throws Exception {
        Path file = dir.resolve("valido.nekkora");
        files.save(file, ProjectFiles.Snapshot.of(RichAutomation.build(clock), UUID.randomUUID().toString()), null, false, Instant.now(), clock);
        valid = Tamper.entries(file);
    }

    private ProjectException.Error load(Map<String, byte[]> entries) throws Exception {
        Path p = Tamper.zip(dir.resolve("t-" + UUID.randomUUID() + ".nekkora"), entries);
        return assertThrows(ProjectException.class, () -> files.load(p, clock)).error();
    }

    private Map<String, byte[]> copy() {
        return new LinkedHashMap<>(valid);
    }

    private ProjectException.Error edited(String path, Consumer<Map<String, Object>> change) throws Exception {
        Map<String, byte[]> e = copy();
        Tamper.edit(e, path, change, true);
        return load(e);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> obj(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o) {
        return (List<Object>) o;
    }

    @Test
    @DisplayName("controle: o pacote original abre")
    void controle() throws Exception {
        Path p = Tamper.zip(dir.resolve("ok.nekkora"), copy());
        assertEquals(8, files.load(p, clock).automation().stepCount() - 1, "8 etapas + a duplicada");
    }

    @Test
    @DisplayName("S006-046 — manifesto ausente, de outro formato, com campo desconhecido ou sem campos")
    void s006046Manifesto() throws Exception {
        Map<String, byte[]> e = copy();
        e.remove(ProjectFormat.MANIFEST);
        assertEquals(ProjectException.Error.NOT_A_PROJECT, load(e));
        assertEquals(ProjectException.Error.NOT_A_PROJECT, edited(ProjectFormat.MANIFEST, m -> m.put("format", "outro/formato")));
        assertEquals(ProjectException.Error.INVALID_CONTENT, edited(ProjectFormat.MANIFEST, m -> m.put("assinatura", "x")));
        assertEquals(ProjectException.Error.INVALID_CONTENT, edited(ProjectFormat.MANIFEST, m -> m.remove("projectId")));
        assertEquals(ProjectException.Error.INVALID_CONTENT, edited(ProjectFormat.MANIFEST, m -> m.put("kind", "PROGRAMA")));
    }

    @Test
    @DisplayName("S006-047 — conteúdo alterado sem atualizar o manifesto: checksum incompatível")
    void s006047Checksum() throws Exception {
        Map<String, byte[]> e = copy();
        Tamper.edit(e, Tamper.firstStep(e), d -> d.put("createdAt", "2020-01-01T00:00:00Z"), false);
        assertEquals(ProjectException.Error.CHECKSUM_MISMATCH, load(e));
    }

    @Test
    @DisplayName("S006-048 — JSON inválido ou UTF-8 inválido num documento (manifesto coerente)")
    void s006048JsonInvalido() throws Exception {
        Map<String, byte[]> e = copy();
        e.put(Tamper.firstStep(e), "{\"stepId\":".getBytes(StandardCharsets.UTF_8));
        Tamper.rehash(e);
        assertEquals(ProjectException.Error.INVALID_JSON, load(e));
        Map<String, byte[]> u = copy();
        u.put(ProjectFormat.AUTOMATION, new byte[] {'{', '"', (byte) 0xC3, (byte) 0x28, '"', ':', '1', '}'});
        Tamper.rehash(u);
        assertEquals(ProjectException.Error.INVALID_JSON, load(u));
        Map<String, byte[]> d = copy();
        d.put(ProjectFormat.AUTOMATION, "{\"name\":\"a\",\"name\":\"b\"}".getBytes(StandardCharsets.UTF_8));
        Tamper.rehash(d);
        assertEquals(ProjectException.Error.INVALID_JSON, load(d), "chave duplicada");
    }

    @Test
    @DisplayName("S006-049 — versão do formato mais nova, mais antiga ou inválida; modelo de automação desconhecido")
    void s006049Versao() throws Exception {
        assertEquals(ProjectException.Error.NEWER_VERSION, edited(ProjectFormat.MANIFEST, m -> m.put("formatVersion", "2.0.0")));
        assertEquals(ProjectException.Error.NEWER_VERSION, edited(ProjectFormat.MANIFEST, m -> {
            m.put("formatVersion", "1.1.0");
            m.put("campoDoFuturo", true); // um formato mais novo pode ter campos novos: recusado ANTES, sem descartar nada
        }));
        assertEquals(ProjectException.Error.UNSUPPORTED_VERSION, edited(ProjectFormat.MANIFEST, m -> m.put("formatVersion", "0.9.0")));
        assertEquals(ProjectException.Error.UNSUPPORTED_VERSION, edited(ProjectFormat.MANIFEST, m -> m.put("formatVersion", "um")));
        assertEquals(ProjectException.Error.UNSUPPORTED_VERSION, edited(ProjectFormat.MANIFEST, m -> obj(m.get("automationModel")).put("version", "2.0.0")));
        assertEquals(ProjectException.Error.UNSUPPORTED_VERSION, edited(ProjectFormat.AUTOMATION, m -> m.put("modelVersion", "1.1.0")));
    }

    @Test
    @DisplayName("S006-050 — identidade duplicada (sessão, etapa referenciada duas vezes, evidência)")
    void s006050IdentidadeDuplicada() throws Exception {
        assertEquals(ProjectException.Error.INTEGRITY, edited(ProjectFormat.SESSIONS, m -> {
            List<Object> s = list(m.get("sessions"));
            obj(s.get(1)).put("steps", list(obj(s.get(0)).get("steps")));
        }));
        Map<String, byte[]> e = copy();
        Tamper.edit(e, ProjectFormat.SESSIONS, m -> obj(list(m.get("sessions")).get(1)).put("sessionId", obj(list(m.get("sessions")).get(0)).get("sessionId")),
                false);
        Tamper.edit(e, ProjectFormat.AUTOMATION, m -> list(m.get("sessions")).set(1, list(m.get("sessions")).get(0)), true);
        assertEquals(ProjectException.Error.INTEGRITY, load(e));
        assertEquals(ProjectException.Error.INTEGRITY, edited(ProjectFormat.UNATTRIBUTED, m -> obj(list(m.get("evidence")).get(0)).put("evidenceId", "evd-1")));
    }

    @Test
    @DisplayName("S006-051 — referência inexistente: etapa sem arquivo, arquivo sem sessão, preferencial fora da lista, evidência de outra etapa")
    void s006051Referencias() throws Exception {
        assertEquals(ProjectException.Error.INTEGRITY, edited(ProjectFormat.SESSIONS,
                m -> list(obj(list(m.get("sessions")).get(0)).get("steps")).add(UUID.randomUUID().toString())));
        Map<String, byte[]> orphan = copy();
        orphan.put(ProjectFormat.STEPS_DIR + UUID.randomUUID() + ".json", valid.get(Tamper.firstStep(valid)));
        Tamper.rehash(orphan);
        assertEquals(ProjectException.Error.INTEGRITY, load(orphan));
        assertEquals(ProjectException.Error.INTEGRITY, edited(Tamper.firstStep(valid), m -> obj(m.get("effective")).put("preferredLocator", 7L)));
        String evidence = valid.keySet().stream().filter(k -> k.startsWith("evidence/") && !k.endsWith("unattributed.json")).findFirst().orElseThrow();
        assertEquals(ProjectException.Error.INTEGRITY, edited(evidence, m -> obj(list(m.get("evidence")).get(0)).put("eventId", "ev-1")));
        assertEquals(ProjectException.Error.INTEGRITY, edited(evidence, m -> {
            List<Object> l = list(m.get("evidence"));
            Object first = l.remove(0);
            l.add(first); // fora da ordem técnica: recusado, nunca reordenado
        }));
    }

    @Test
    @DisplayName("S006-052 — ZIP malicioso: Zip Slip, caminho absoluto, diretório, duplicata, entrada inesperada ou faltante, não-ZIP, truncado")
    void s006052ZipMalicioso() throws Exception {
        for (String name : List.of("../fora.json", "/etc/projeto.json", "C:\\Windows\\x.json", "steps/../../x.json", "steps/sub/x.json", "Manifest.json",
                "steps/", "metadata.json.exe")) {
            Map<String, byte[]> e = copy();
            e.put(name, "{}".getBytes(StandardCharsets.UTF_8));
            assertEquals(ProjectException.Error.UNSAFE_ENTRY, load(e), name);
        }
        Path dup = Tamper.duplicated(dir.resolve("dup.nekkora"), copy(), ProjectFormat.AUTOMATION);
        assertEquals(ProjectException.Error.DUPLICATE_ENTRY, assertThrows(ProjectException.class, () -> files.load(dup, clock)).error());
        Map<String, byte[]> extra = copy();
        extra.put(ProjectFormat.STEPS_DIR + UUID.randomUUID() + ".json", "{}".getBytes(StandardCharsets.UTF_8));
        assertEquals(ProjectException.Error.UNEXPECTED_ENTRY, load(extra), "entrada não declarada no manifesto");
        Map<String, byte[]> missing = copy();
        missing.remove(ProjectFormat.SESSIONS);
        assertEquals(ProjectException.Error.MISSING_ENTRY, load(missing));
        Path notZip = Files.writeString(dir.resolve("texto.nekkora"), "isto não é um projeto");
        assertEquals(ProjectException.Error.CORRUPTED, assertThrows(ProjectException.class, () -> files.load(notZip, clock)).error());
        byte[] whole = Files.readAllBytes(Tamper.zip(dir.resolve("inteiro.nekkora"), copy()));
        Path truncated = Files.write(dir.resolve("truncado.nekkora"), java.util.Arrays.copyOf(whole, whole.length / 2));
        assertEquals(ProjectException.Error.CORRUPTED, assertThrows(ProjectException.class, () -> files.load(truncated, clock)).error());
    }

    @Test
    @DisplayName("S006-053 — limites: bomba de descompressão parada no limite de leitura; arquivo grande demais")
    void s006053Limites() throws Exception {
        Map<String, byte[]> bomb = copy();
        bomb.put(Tamper.firstStep(bomb), new byte[ProjectFormat.MAX_ENTRY_BYTES + 1]); // zeros: compacta para poucos KB
        Tamper.rehash(bomb);
        Path p = Tamper.zip(dir.resolve("bomba.nekkora"), bomb);
        assertTrue(Files.size(p) < 100_000, "compactado: " + Files.size(p));
        assertEquals(ProjectException.Error.TOO_LARGE, assertThrows(ProjectException.class, () -> files.load(p, clock)).error());
        Path big = dir.resolve("grande.nekkora");
        try (RandomAccessFile f = new RandomAccessFile(big.toFile(), "rw")) {
            f.setLength(ProjectFormat.MAX_FILE_BYTES + 1);
        }
        assertEquals(ProjectException.Error.TOO_LARGE, assertThrows(ProjectException.class, () -> files.load(big, clock)).error());
    }

    @Test
    @DisplayName("campos desconhecidos são recusados (nunca descartados); campos obrigatórios ausentes também")
    void camposDesconhecidos() throws Exception {
        assertEquals(ProjectException.Error.INVALID_CONTENT, edited(ProjectFormat.AUTOMATION, m -> m.put("executar", "rm -rf")));
        assertEquals(ProjectException.Error.INVALID_CONTENT, edited(Tamper.firstStep(valid), m -> obj(m.get("original")).remove("baseline")));
        assertEquals(ProjectException.Error.INVALID_CONTENT, edited(Tamper.firstStep(valid),
                m -> obj(obj(m.get("original")).get("capturedElement")).put("script", "alert(1)")));
    }

    @Test
    @DisplayName("S006-023 (leitura) — um arquivo não pode declarar localizador manual verificado")
    void manualNuncaVerificado() throws Exception {
        String step = stepWithManualLocator();
        assertEquals(ProjectException.Error.INVALID_CONTENT,
                edited(step, m -> obj(list(obj(m.get("effective")).get("manualLocators")).get(0)).put("verification", "VALIDATED")));
    }

    @Test
    @DisplayName("S006-071 — privacidade v2: dado da página fora da política nos campos capturados é recusado (não sanitizado em silêncio)")
    void s006071Privacidade() throws Exception {
        String step = Tamper.firstStep(valid);
        assertEquals(ProjectException.Error.PRIVACY_VIOLATION, edited(step,
                m -> obj(obj(m.get("original")).get("capturedElement")).put("accessibleName", "Consultar cliente Bartolomeu Quixadá")));
        assertEquals(ProjectException.Error.PRIVACY_VIOLATION, edited(step,
                m -> obj(list(obj(obj(m.get("original")).get("capturedElement")).get("locators")).get(2)).put("expression", "button[data-client^=joao-oliveira]")));
        assertEquals(ProjectException.Error.PRIVACY_VIOLATION, edited(step,
                m -> obj(obj(m.get("original")).get("capturedElement")).put("tag", "ficha-cliente-joao")));
        assertEquals(ProjectException.Error.PRIVACY_VIOLATION, edited(step,
                m -> obj(obj(m.get("original")).get("context")).put("location", "http://127.0.0.1:18765/clientes/joao-oliveira")));
        assertEquals(ProjectException.Error.PRIVACY_VIOLATION, edited(step,
                m -> obj(obj(m.get("original")).get("capturedElement")).put("htmlId", "cliente-anacleto-prudencio")));
        assertEquals(ProjectException.Error.PRIVACY_VIOLATION, edited(ProjectFormat.UNATTRIBUTED,
                m -> obj(obj(list(m.get("evidence")).get(0)).get("document")).put("url", "http://127.0.0.1:18765/clientes/joao-oliveira?cpf=1")));
        String evidence = valid.keySet().stream().filter(k -> k.startsWith("evidence/") && !k.endsWith("unattributed.json")).findFirst().orElseThrow();
        assertEquals(ProjectException.Error.INVALID_CONTENT, edited(evidence,
                m -> obj(obj(list(m.get("evidence")).get(1)).get("message")).put("text", "Usuário ou senha inválidos.")), "texto omitido não volta");
    }

    @Test
    @DisplayName("S006-072 — política de privacidade anterior (ou desconhecida) declarada no manifesto: recusada, sem migração silenciosa")
    void s006072PoliticaAntiga() throws Exception {
        assertEquals(ProjectException.Error.PRIVACY_POLICY_UNSUPPORTED, edited(ProjectFormat.MANIFEST, m -> m.put("privacyPolicy", "nekkora-privacy/1")));
        assertEquals(ProjectException.Error.PRIVACY_POLICY_UNSUPPORTED, edited(ProjectFormat.MANIFEST, m -> m.put("privacyPolicy", "nekkora-privacy/3")));
    }

    @Test
    @DisplayName("S006-073 — nenhum segredo: credencial é só apelido; literal com aparência de segredo recusado; o formato não tem campo de valor secreto")
    void s006073SemSegredos() throws Exception {
        String usuario = stepWithManualLocator(); // "Preencher campo Usuário": variável
        assertEquals(ProjectException.Error.INVALID_CONTENT, edited(usuario, m -> obj(obj(m.get("effective")).get("parameters")).put("value",
                new LinkedHashMap<>(Map.of("kind", "CREDENTIAL", "alias", "sistema.senha", "secret", "hunter2")))));
        assertEquals(ProjectException.Error.INVALID_CONTENT, edited(usuario, m -> obj(obj(m.get("effective")).get("parameters")).put("value",
                new LinkedHashMap<>(Map.of("kind", "LITERAL", "text", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abcdefghijklmnop")))));
        String all = new String(String.join("\n", valid.values().stream().map(b -> new String(b, StandardCharsets.UTF_8)).toList()));
        assertTrue(all.contains("\"alias\":\"sistema.senha\""));
        assertFalse(all.toLowerCase().contains("\"password\"") || all.contains("\"secret\"") || all.contains("\"token\""));
    }

    @Test
    @DisplayName("S006-054 — nada parcial: um erro na última etapa recusa o projeto inteiro")
    void s006054IntegralOuRecusado() throws Exception {
        Map<String, byte[]> e = copy();
        String last = e.keySet().stream().filter(k -> k.startsWith(ProjectFormat.STEPS_DIR)).reduce((x, y) -> y).orElseThrow();
        Tamper.edit(e, last, m -> obj(m.get("effective")).put("name", ""), true);
        assertEquals(ProjectException.Error.INVALID_CONTENT, load(e));
    }

    /** Caminho do documento da etapa "Preencher campo Usuário" (a que tem localizador manual). */
    private String stepWithManualLocator() {
        for (Map.Entry<String, byte[]> en : valid.entrySet()) {
            if (en.getKey().startsWith(ProjectFormat.STEPS_DIR) && new String(en.getValue(), StandardCharsets.UTF_8).contains("form#login")) {
                return en.getKey();
            }
        }
        throw new AssertionError("etapa com localizador manual");
    }

    @Test
    @DisplayName("Automation.Snapshot (desfazer/refazer) não é o formato: o documento é próprio e não contém histórico")
    void historicoNaoPersistido() {
        Automation a = RichAutomation.build(clock);
        java.util.Set<String> keys = new java.util.TreeSet<>();
        ProjectCodec.documents(a).values().forEach(d -> keysOf(d, keys));
        for (String k : keys) assertFalse(k.toLowerCase().matches(".*(undo|redo|history|snapshot).*"), "chave de histórico no documento: " + k);
        assertTrue(keys.contains("effective") && keys.contains("original"));
    }

    private static void keysOf(Object v, java.util.Set<String> out) {
        if (v instanceof Map<?, ?> m) {
            m.forEach((k, x) -> {
                out.add(String.valueOf(k));
                keysOf(x, out);
            });
        } else if (v instanceof List<?> l) {
            l.forEach(x -> keysOf(x, out));
        }
    }
}
