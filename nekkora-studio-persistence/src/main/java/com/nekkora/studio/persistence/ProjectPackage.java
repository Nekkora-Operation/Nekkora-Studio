package com.nekkora.studio.persistence;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.bridge.json.JsonException;
import com.nekkora.studio.domain.ModelVersion;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Pacote {@code .nekkora}: contêiner ZIP com documentos JSON (STUDIO-006, §4/§5/§17).
 *
 * <p><b>Escrita</b>: entradas em ordem fixa, data fixa (nenhuma informação do relógio local no contêiner), manifesto
 * primeiro, com o SHA-256 e o tamanho de cada documento. O SHA-256 detecta corrupção ACIDENTAL: sem assinatura, um
 * arquivo e os seus checksums podem ser alterados juntos — não é garantia de autenticidade. O pacote não é criptografado.
 *
 * <p><b>Leitura</b>: o pacote é entrada não confiável. Nada é extraído para o disco; só as entradas cujo NOME segue o
 * padrão fechado ({@link ProjectFormat#ENTRY_NAME}) são aceitas — o que exclui caminhos absolutos, {@code ..},
 * diretórios e profundidade —, sem repetição, sem faltar nem sobrar em relação ao manifesto, com o tamanho LIDO de fato
 * limitado por entrada e no total (bombas de descompressão param no limite, sem consumir memória além dele) e com o
 * checksum conferido antes de qualquer interpretação.
 */
public final class ProjectPackage {
    /** Data fixa das entradas do contêiner (o ZIP exige uma; nenhuma vem do relógio local). */
    private static final long ENTRY_TIME = 315_532_800_000L; // 1980-01-01T00:00:00Z

    /** Manifesto lido e conferido. {@code recoveryOf} só existe numa fotografia de recuperação. */
    public record Manifest(ProjectFormat.Kind kind, String formatVersion, String privacyPolicy, String projectId, String automationId, Instant createdAt,
                           Instant savedAt, RecoveryOrigin recoveryOf) {}

    /**
     * Origem de uma fotografia de recuperação: o projeto confirmado do qual derivam as alterações não salvas (ou nenhum,
     * se nunca foi salvo) e o conteúdo dele naquele momento — para que salvar a recuperação no arquivo original detecte
     * uma alteração externa feita depois.
     */
    public record RecoveryOrigin(String projectPath, ProjectRepository.Fingerprint base) {}

    /** Pacote lido: manifesto + documentos JSON já interpretados (ainda não validados como projeto). */
    public record Contents(Manifest manifest, Map<String, Object> documents) {}

    private ProjectPackage() {}

    // ================================================================================================== escrita

    /**
     * Bytes do pacote.
     *
     * @param documents  documentos de conteúdo ({@link ProjectCodec#documents})
     * @param recoveryOf origem da fotografia de recuperação (obrigatória em {@link ProjectFormat.Kind#RECOVERY}; nula em projeto)
     */
    static byte[] write(ProjectFormat.Kind kind, String projectId, String automationId, Instant createdAt, Instant savedAt, String producerVersion,
            RecoveryOrigin recoveryOf, Map<String, Object> documents) throws ProjectException {
        if ((kind == ProjectFormat.Kind.RECOVERY) != (recoveryOf != null)) throw new IllegalArgumentException("origem da recuperação");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        Map<String, Object> metadata = new LinkedHashMap<>();
        Map<String, Object> producer = new LinkedHashMap<>();
        producer.put("application", "Nekkora Studio");
        producer.put("version", producerVersion);
        metadata.put("producer", producer);
        metadata.put("encrypted", false);
        metadata.put("notice", "Arquivo local de trabalho, não criptografado e legível por ferramentas apropriadas. Não guarde senhas, tokens ou "
                + "outros segredos em projetos.");
        entries.put(ProjectFormat.METADATA, utf8(Json.write(metadata)));
        for (Map.Entry<String, Object> d : documents.entrySet()) entries.put(d.getKey(), utf8(Json.write(d.getValue())));
        List<Object> list = new ArrayList<>();
        long total = 0;
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (!ProjectFormat.ENTRY_NAME.matcher(e.getKey()).matches()) throw new ProjectException(ProjectException.Error.UNSAFE_ENTRY, e.getKey());
            if (e.getValue().length > ProjectFormat.MAX_ENTRY_BYTES) throw new ProjectException(ProjectException.Error.TOO_LARGE, e.getKey());
            total += e.getValue().length;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("path", e.getKey());
            m.put("sha256", sha256(e.getValue()));
            m.put("size", (long) e.getValue().length);
            list.add(m);
        }
        if (total > ProjectFormat.MAX_TOTAL_BYTES) throw new ProjectException(ProjectException.Error.TOO_LARGE, "projeto");
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("format", ProjectFormat.FORMAT);
        String formatVersion = ProjectFormat.versionFor(documents); // a menor versão que representa o conteúdo
        manifest.put("formatVersion", formatVersion);
        manifest.put("kind", kind.name());
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("schema", ModelVersion.SCHEMA);
        model.put("version", ProjectFormat.modelFor(formatVersion));
        manifest.put("automationModel", model);
        manifest.put("privacyPolicy", ProjectFormat.PRIVACY_POLICY);
        manifest.put("projectId", projectId);
        manifest.put("automationId", automationId);
        manifest.put("createdAt", createdAt.toString());
        manifest.put("savedAt", savedAt.toString());
        if (recoveryOf == null) {
            manifest.put("recoveryOf", Json.NULL);
        } else {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("projectPath", recoveryOf.projectPath() == null ? Json.NULL : recoveryOf.projectPath());
            r.put("baseSize", recoveryOf.base() == null ? Json.NULL : (Object) recoveryOf.base().size());
            r.put("baseSha256", recoveryOf.base() == null ? Json.NULL : recoveryOf.base().sha256());
            manifest.put("recoveryOf", r);
        }
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("maxEntryBytes", (long) ProjectFormat.MAX_ENTRY_BYTES);
        limits.put("maxTotalBytes", ProjectFormat.MAX_TOTAL_BYTES);
        limits.put("maxSteps", (long) ProjectFormat.MAX_STEPS);
        manifest.put("limits", limits);
        manifest.put("entries", list);
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.setLevel(6);
            put(zip, ProjectFormat.MANIFEST, utf8(Json.write(manifest)));
            for (Map.Entry<String, byte[]> e : entries.entrySet()) put(zip, e.getKey(), e.getValue());
        } catch (IOException e) {
            throw new ProjectException(ProjectException.Error.WRITE_FAILED, "pacote", e);
        }
        return out.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setTime(ENTRY_TIME);
        zip.putNextEntry(e);
        zip.write(data);
        zip.closeEntry();
    }

    // ================================================================================================== leitura

    /** Lê e confere o pacote (estrutura, manifesto, checksums, JSON). Não valida o conteúdo como projeto. */
    public static Contents read(Path file) throws ProjectException {
        try {
            if (java.nio.file.Files.size(file) > ProjectFormat.MAX_FILE_BYTES) throw new ProjectException(ProjectException.Error.TOO_LARGE, "arquivo");
        } catch (IOException e) {
            throw ProjectRepository.map(e, ProjectException.Error.CORRUPTED);
        }
        Map<String, byte[]> raw = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(file.toFile(), StandardCharsets.UTF_8)) {
            int count = 0;
            long total = 0;
            Set<String> names = new HashSet<>();
            Enumeration<? extends ZipEntry> en = zip.entries();
            List<ZipEntry> entries = new ArrayList<>();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (++count > ProjectFormat.MAX_ENTRIES) throw new ProjectException(ProjectException.Error.TOO_LARGE, "entradas");
                String name = e.getName();
                if (e.isDirectory() || !ProjectFormat.ENTRY_NAME.matcher(name).matches()) {
                    throw new ProjectException(ProjectException.Error.UNSAFE_ENTRY, safeName(name));
                }
                if (!names.add(name)) throw new ProjectException(ProjectException.Error.DUPLICATE_ENTRY, name);
                if (e.getMethod() != ZipEntry.DEFLATED && e.getMethod() != ZipEntry.STORED) {
                    throw new ProjectException(ProjectException.Error.UNSAFE_ENTRY, name);
                }
                entries.add(e);
            }
            if (!names.contains(ProjectFormat.MANIFEST)) throw new ProjectException(ProjectException.Error.NOT_A_PROJECT, ProjectFormat.MANIFEST);
            for (ZipEntry e : entries) {
                byte[] data = readBounded(zip, e, ProjectFormat.MAX_TOTAL_BYTES - total);
                total += data.length;
                raw.put(e.getName(), data);
            }
        } catch (ZipException e) {
            throw new ProjectException(ProjectException.Error.CORRUPTED, "contêiner", e);
        } catch (IOException e) {
            throw new ProjectException(ProjectException.Error.CORRUPTED, "contêiner", e);
        }
        return contents(raw);
    }

    private static byte[] readBounded(ZipFile zip, ZipEntry e, long remaining) throws IOException, ProjectException {
        long limit = Math.min(ProjectFormat.MAX_ENTRY_BYTES, remaining);
        // o tamanho declarado não é confiável: lê-se no máximo limite+1 bytes de fato
        try (InputStream in = zip.getInputStream(e)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(Math.max(e.getSize(), 0), 64 * 1024));
            byte[] buf = new byte[16 * 1024];
            long read = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                read += n;
                if (read > limit) throw new ProjectException(ProjectException.Error.TOO_LARGE, e.getName());
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static Contents contents(Map<String, byte[]> raw) throws ProjectException {
        Object manifestJson = json(raw.get(ProjectFormat.MANIFEST), ProjectFormat.MANIFEST);
        if (!(manifestJson instanceof Map<?, ?> mm) || !ProjectFormat.FORMAT.equals(((Map<?, ?>) mm).get("format"))) {
            throw new ProjectException(ProjectException.Error.NOT_A_PROJECT, ProjectFormat.MANIFEST);
        }
        // a versão é conferida ANTES do resto: um formato mais novo pode ter campos que esta versão não conhece
        Object fv = mm.get("formatVersion");
        String formatVersion = fv instanceof String s ? s : null;
        switch (ProjectFormat.compatibility(formatVersion)) {
            case NEWER -> throw new ProjectException(ProjectException.Error.NEWER_VERSION, "formatVersion");
            case OLDER, INVALID -> throw new ProjectException(ProjectException.Error.UNSUPPORTED_VERSION, "formatVersion");
            case SUPPORTED -> { }
        }
        Doc m = Doc.of(manifestJson, ProjectFormat.MANIFEST, "format", "formatVersion", "kind", "automationModel", "privacyPolicy", "projectId",
                "automationId", "createdAt", "savedAt", "recoveryOf", "limits", "entries");
        Doc model = m.obj("automationModel", "schema", "version");
        // cada versão do formato tem exatamente um modelo: 1.0.x → 1.0.0, 1.1.x → 1.1.0
        if (!ModelVersion.SCHEMA.equals(model.str("schema")) || !ProjectFormat.modelFor(formatVersion).equals(model.str("version"))) {
            throw new ProjectException(ProjectException.Error.UNSUPPORTED_VERSION, "automationModel");
        }
        if (!ProjectFormat.PRIVACY_POLICY.equals(m.str("privacyPolicy"))) {
            throw new ProjectException(ProjectException.Error.PRIVACY_POLICY_UNSUPPORTED, "privacyPolicy");
        }
        m.obj("limits", "maxEntryBytes", "maxTotalBytes", "maxSteps"); // informativo: o leitor usa os PRÓPRIOS limites
        ProjectFormat.Kind kind = m.enumValue("kind", ProjectFormat.Kind.class);
        RecoveryOrigin recoveryOf = null;
        if (!m.isNull("recoveryOf")) {
            Doc r = m.obj("recoveryOf", "projectPath", "baseSize", "baseSha256");
            if (r.isNull("baseSize") != r.isNull("baseSha256") || (r.isNull("projectPath") && !r.isNull("baseSha256"))) {
                throw Doc.invalid(r.where(), "origem incompleta");
            }
            ProjectRepository.Fingerprint base = r.isNull("baseSha256") ? null : new ProjectRepository.Fingerprint(r.lng("baseSize"), r.str("baseSha256"));
            recoveryOf = new RecoveryOrigin(r.strOrNull("projectPath"), base);
        }
        if ((kind == ProjectFormat.Kind.RECOVERY) != (recoveryOf != null)) throw Doc.invalid(m.at("recoveryOf"), "origem de recuperação incoerente");
        String projectId = ProjectCodec.uuid(m.str("projectId"), m.at("projectId")).toString();
        String automationId = ProjectCodec.uuid(m.str("automationId"), m.at("automationId")).toString();
        Set<String> declared = new java.util.LinkedHashSet<>();
        for (Object o : m.list("entries", ProjectFormat.MAX_ENTRIES)) {
            Doc e = Doc.of(o, m.at("entries"), "path", "sha256", "size");
            String path = e.str("path");
            if (!ProjectFormat.ENTRY_NAME.matcher(path).matches() || ProjectFormat.MANIFEST.equals(path)) {
                throw new ProjectException(ProjectException.Error.UNSAFE_ENTRY, safeName(path));
            }
            if (!declared.add(path)) throw new ProjectException(ProjectException.Error.DUPLICATE_ENTRY, path);
            byte[] data = raw.get(path);
            if (data == null) throw new ProjectException(ProjectException.Error.MISSING_ENTRY, path);
            if (data.length != e.lng("size") || !sha256(data).equals(e.str("sha256"))) {
                throw new ProjectException(ProjectException.Error.CHECKSUM_MISMATCH, path);
            }
        }
        for (String name : raw.keySet()) {
            if (!ProjectFormat.MANIFEST.equals(name) && !declared.contains(name)) throw new ProjectException(ProjectException.Error.UNEXPECTED_ENTRY, name);
        }
        for (String required : List.of(ProjectFormat.METADATA, ProjectFormat.AUTOMATION, ProjectFormat.SESSIONS, ProjectFormat.UNATTRIBUTED)) {
            if (!declared.contains(required)) throw new ProjectException(ProjectException.Error.MISSING_ENTRY, required);
        }
        // STUDIO-008: o catálogo de variáveis é obrigatório em 1.1.x e não existe em 1.0.x (nada é ignorado em silêncio);
        // STUDIO-009: em 1.2.x ele é opcional
        boolean variables = declared.contains(ProjectFormat.VARIABLES);
        if (ProjectFormat.requiresVariables(formatVersion) && !variables) throw new ProjectException(ProjectException.Error.MISSING_ENTRY, ProjectFormat.VARIABLES);
        if (!ProjectFormat.allowsVariables(formatVersion) && variables) {
            throw new ProjectException(ProjectException.Error.UNEXPECTED_ENTRY, ProjectFormat.VARIABLES);
        }
        Doc meta = Doc.of(json(raw.get(ProjectFormat.METADATA), ProjectFormat.METADATA), ProjectFormat.METADATA, "producer", "encrypted", "notice");
        meta.obj("producer", "application", "version");
        if (meta.bool("encrypted")) throw new ProjectException(ProjectException.Error.UNSUPPORTED_VERSION, "metadata.encrypted");
        Map<String, Object> docs = new LinkedHashMap<>();
        for (String path : declared) {
            if (!ProjectFormat.METADATA.equals(path)) docs.put(path, json(raw.get(path), path));
        }
        Manifest manifest = new Manifest(kind, m.str("formatVersion"), m.str("privacyPolicy"), projectId, automationId, m.instant("createdAt"),
                m.instant("savedAt"), recoveryOf);
        return new Contents(manifest, docs);
    }

    private static Object json(byte[] data, String where) throws ProjectException {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException e) {
            throw new ProjectException(ProjectException.Error.INVALID_JSON, where);
        }
        try {
            return Json.parse(text);
        } catch (JsonException e) {
            throw new ProjectException(ProjectException.Error.INVALID_JSON, where);
        }
    }

    /** Nome de entrada recusada, só com caracteres seguros para mensagens e logs. */
    private static String safeName(String name) {
        String n = name == null ? "" : name.replaceAll("[^A-Za-z0-9._/-]", "?");
        return n.length() > 60 ? n.substring(0, 60) + "…" : n;
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
