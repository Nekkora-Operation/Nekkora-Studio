package com.nekkora.studio.persistence;

import com.nekkora.studio.bridge.json.Json;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Adulteração de pacotes reais para os testes de entrada não confiável. */
final class Tamper {
    private Tamper() {}

    static Map<String, byte[]> entries(Path file) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipFile z = new ZipFile(file.toFile())) {
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                out.put(e.getName(), z.getInputStream(e).readAllBytes());
            }
        }
        return out;
    }

    /** Escreve as entradas como estão (nomes quaisquer, inclusive perigosos). */
    static Path zip(Path out, Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry ze = new ZipEntry(e.getKey());
                z.putNextEntry(ze);
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        Files.write(out, bytes.toByteArray());
        return out;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> json(byte[] data) {
        return (Map<String, Object>) Json.parse(new String(data, StandardCharsets.UTF_8));
    }

    static byte[] bytes(Object json) {
        return Json.write(json).getBytes(StandardCharsets.UTF_8);
    }

    /** Altera um documento JSON e (opcionalmente) recalcula o manifesto — para que só o CONTEÚDO esteja errado. */
    static void edit(Map<String, byte[]> entries, String path, Consumer<Map<String, Object>> change, boolean rehash) {
        Map<String, Object> doc = json(entries.get(path));
        change.accept(doc);
        entries.put(path, bytes(doc));
        if (rehash) rehash(entries);
    }

    @SuppressWarnings("unchecked")
    static void rehash(Map<String, byte[]> entries) {
        Map<String, Object> manifest = json(entries.get(ProjectFormat.MANIFEST));
        List<Object> list = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (e.getKey().equals(ProjectFormat.MANIFEST)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("path", e.getKey());
            m.put("sha256", ProjectPackage.sha256(e.getValue()));
            m.put("size", (long) e.getValue().length);
            list.add(m);
        }
        manifest.put("entries", list);
        entries.put(ProjectFormat.MANIFEST, bytes(manifest));
    }

    /** Primeiro caminho de etapa do pacote. */
    static String firstStep(Map<String, byte[]> entries) {
        return entries.keySet().stream().filter(k -> k.startsWith(ProjectFormat.STEPS_DIR)).findFirst().orElseThrow();
    }

    /** Pacote com duas entradas de MESMO nome (o ZipOutputStream recusa; troca-se o nome nos bytes). */
    static Path duplicated(Path out, Map<String, byte[]> entries, String name) throws IOException {
        String twin = name.substring(0, name.length() - 1) + "N"; // mesmo comprimento
        Map<String, byte[]> m = new LinkedHashMap<>(entries);
        m.put(twin, entries.get(name));
        zip(out, m);
        byte[] b = Files.readAllBytes(out);
        byte[] from = twin.getBytes(StandardCharsets.UTF_8);
        byte[] to = name.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i + from.length <= b.length; i++) {
            boolean match = true;
            for (int j = 0; j < from.length && match; j++) match = b[i + j] == from[j];
            if (match) System.arraycopy(to, 0, b, i, to.length);
        }
        Files.write(out, b);
        return out;
    }
}
