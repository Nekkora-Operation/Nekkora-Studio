package com.nekkora.studio.codegen;

import com.nekkora.studio.bridge.json.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Grava o projeto gerado sem nunca destruir edição manual. Para cada arquivo:
 * <ul>
 *   <li>{@code UNCHANGED}: conteúdo igual ao novo;</li>
 *   <li>{@code WRITTEN}: arquivo novo, ou igual ao que o manifesto ANTERIOR registrou (não foi editado);</li>
 *   <li>{@code PROTECTED}: difere do que o gerador gravou → foi editado à mão (ou não é do gerador): o novo vai para
 *       {@code <arquivo>.novo} e a comparação para {@code <arquivo>.diff}; o manifesto anterior é preservado.</li>
 * </ul>
 * CODEGEN-QUALITY-001 rodada 3 (layout novo): arquivo da geração ANTERIOR que a nova não tem mais (ex.: {@code paginas.py}
 * quando o código passa para o pacote {@code automation/}) nunca é apagado:
 * <ul>
 *   <li>{@code RETIRED}: igual ao que o gerador gravou → movido intacto para {@code previous-generation/<versão>/}, com
 *       uma cópia do manifesto anterior (a geração anterior continua inteira e conferível);</li>
 *   <li>{@code KEPT}: editado à mão → fica onde está (nada é movido nem sobrescrito) e é informado.</li>
 * </ul>
 * Com algum arquivo {@code PROTECTED}, nada é aposentado (o manifesto anterior continua valendo).
 */
public final class OutputWriter {

    public enum Status { WRITTEN, UNCHANGED, PROTECTED, RETIRED, KEPT }

    /** Pasta onde os arquivos da geração anterior que saíram do projeto ficam guardados (por versão). */
    public static final String PREVIOUS = "previous-generation";

    public record Report(Map<String, Status> files) {
        public boolean protectedAny() {
            return files.containsValue(Status.PROTECTED);
        }
    }

    private OutputWriter() {}

    public static Report write(Path dir, Map<String, String> files) throws IOException {
        Files.createDirectories(dir);
        Map<String, String> previous = previousHashes(dir.resolve("manifest.json"));
        String previousManifest = Files.isRegularFile(dir.resolve("manifest.json")) ? Files.readString(dir.resolve("manifest.json"), StandardCharsets.UTF_8) : null;
        Map<String, Status> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> f : files.entrySet()) {
            if (f.getKey().equals("manifest.json")) continue;
            out.put(f.getKey(), one(dir, f.getKey(), f.getValue(), previous));
        }
        boolean blocked = out.containsValue(Status.PROTECTED);
        if (!blocked && previousManifest != null) retire(dir, files, previous, previousManifest, out);
        String manifest = files.get("manifest.json");
        if (manifest != null) {
            Path m = dir.resolve("manifest.json");
            if (blocked) {
                atomic(dir.resolve("manifest.json.novo"), manifest);
                out.put("manifest.json", Status.PROTECTED);
            } else if (Files.exists(m) && Files.readString(m, StandardCharsets.UTF_8).equals(manifest)) {
                out.put("manifest.json", Status.UNCHANGED);
            } else {
                atomic(m, manifest);
                out.put("manifest.json", Status.WRITTEN);
            }
        }
        return new Report(out);
    }

    private static Status one(Path dir, String rel, String content, Map<String, String> previous) throws IOException {
        Path target = dir.resolve(rel).normalize();
        if (!target.startsWith(dir.normalize())) throw new IOException("caminho fora da pasta de saída");
        Files.createDirectories(target.getParent());
        if (!Files.exists(target)) {
            atomic(target, content);
            return Status.WRITTEN;
        }
        String current = Files.readString(target, StandardCharsets.UTF_8);
        if (current.equals(content)) {
            Files.deleteIfExists(sibling(target, ".novo"));
            Files.deleteIfExists(sibling(target, ".diff"));
            return Status.UNCHANGED;
        }
        String recorded = previous.get(rel);
        if (recorded != null && recorded.equals("sha256:" + CodegenConfig.sha256(current))) {
            atomic(target, content);
            return Status.WRITTEN;
        }
        atomic(sibling(target, ".novo"), content);
        atomic(sibling(target, ".diff"), Diff.unified(rel, current, content));
        return Status.PROTECTED;
    }

    /** Arquivos do manifesto anterior que a geração nova não tem: guardados por versão (ou mantidos, se editados). */
    private static void retire(Path dir, Map<String, String> files, Map<String, String> previous, String previousManifest, Map<String, Status> out)
            throws IOException {
        Path version = null;
        for (Map.Entry<String, String> e : previous.entrySet()) {
            String rel = e.getKey();
            if (files.containsKey(rel)) continue;
            Path target = dir.resolve(rel).normalize();
            if (!target.startsWith(dir.normalize()) || !Files.isRegularFile(target)) continue;
            String current = Files.readString(target, StandardCharsets.UTF_8);
            if (!e.getValue().equals("sha256:" + CodegenConfig.sha256(current))) {
                out.put(rel, Status.KEPT);
                continue;
            }
            if (version == null) {
                version = dir.resolve(PREVIOUS).resolve(versionName(previousManifest));
                Files.createDirectories(version);
                atomic(version.resolve("manifest.json"), previousManifest);
            }
            Path moved = version.resolve(rel).normalize();
            Files.createDirectories(moved.getParent());
            Files.move(target, moved, StandardCopyOption.REPLACE_EXISTING);
            out.put(rel, Status.RETIRED);
        }
    }

    /** Nome da pasta da versão anterior: manifesto e revisão da automação ({@code manifest-1.3.0-rev52}). */
    static String versionName(String manifest) {
        try {
            Map<String, Object> m = Json.parseObject(manifest);
            String v = String.valueOf(m.get("version")).replaceAll("[^0-9.]", "");
            String r = String.valueOf(m.get("automationRevision")).replaceAll("[^0-9]", "");
            return "manifest-" + (v.isEmpty() ? "unknown" : v) + (r.isEmpty() ? "" : "-rev" + r);
        } catch (RuntimeException e) {
            return "manifest-unknown";
        }
    }

    private static Path sibling(Path p, String suffix) {
        return p.resolveSibling(p.getFileName() + suffix);
    }

    private static Map<String, String> previousHashes(Path manifest) {
        Map<String, String> out = new LinkedHashMap<>();
        if (!Files.exists(manifest)) return out;
        try {
            Map<String, Object> m = Json.parseObject(Files.readString(manifest, StandardCharsets.UTF_8));
            Map<String, Object> files = Json.object(m, "files");
            if (files != null) for (Map.Entry<String, Object> e : files.entrySet()) if (e.getValue() instanceof String s) out.put(e.getKey(), s);
        } catch (IOException | RuntimeException e) {
            return Map.of(); // manifesto ilegível: nada é presumido gerado → todo arquivo diferente fica protegido
        }
        return out;
    }

    static void atomic(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling("." + target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Diff de linhas (LCS) no formato unificado simples — suficiente para arquivos gerados pequenos. */
    static final class Diff {
        private Diff() {}

        static String unified(String name, String before, String after) {
            String[] a = before.split("\n", -1);
            String[] b = after.split("\n", -1);
            int[][] lcs = new int[a.length + 1][b.length + 1];
            for (int i = a.length - 1; i >= 0; i--)
                for (int j = b.length - 1; j >= 0; j--)
                    lcs[i][j] = a[i].equals(b[j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            List<String> out = new ArrayList<>();
            out.add("--- " + name + " (atual, editado)");
            out.add("+++ " + name + " (gerado agora)");
            int i = 0;
            int j = 0;
            while (i < a.length || j < b.length) {
                if (i < a.length && j < b.length && a[i].equals(b[j])) {
                    i++;
                    j++;
                } else if (j < b.length && (i == a.length || lcs[i][j + 1] >= lcs[i + 1][j])) {
                    out.add("+" + b[j++]);
                } else {
                    out.add("-" + a[i++]);
                }
            }
            return String.join("\n", out) + "\n";
        }
    }
}
