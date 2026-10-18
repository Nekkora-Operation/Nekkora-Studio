package com.nekkora.studio.pdd;

import com.nekkora.studio.domain.AutomationId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * STUDIO-010 — armazenamento AUXILIAR do PDD (o {@code .nekkora} não muda): por automação,
 * {@code <dados>/pdd/<automationId>/rascunho.json} ({@code nekkora-studio/pdd-visual/1.0.0}) e
 * {@code capturas/<sha256>.png} — SÓ imagens que já passaram pela 4ª barreira ({@link VisualVerifier}).
 *
 * <p>Escrita atômica (temporário no mesmo diretório + mover). Retenção: só ficam as imagens que o rascunho referencia;
 * "descartar rascunho" apaga a pasta da automação. Nenhuma imagem bruta passa por aqui.
 */
public final class PddStore {
    private final Path root;

    public PddStore(Path dataDir) {
        this.root = dataDir.resolve("pdd");
    }

    public Path folder(AutomationId id) {
        return root.resolve(id.value().toString());
    }

    public void saveDraft(DocumentDraft d) throws IOException {
        Path dir = folder(d.automationId());
        Files.createDirectories(dir);
        atomicWrite(dir.resolve("rascunho.json"), DraftJson.write(d).getBytes(StandardCharsets.UTF_8));
    }

    public Optional<DocumentDraft> loadDraft(AutomationId id) throws IOException {
        Path f = folder(id).resolve("rascunho.json");
        if (!Files.exists(f)) return Optional.empty();
        return Optional.of(DraftJson.read(Files.readString(f, StandardCharsets.UTF_8)));
    }

    /** Guarda uma imagem JÁ verificada; o nome é o sha-256 do conteúdo (sem dado nenhum no nome do arquivo). */
    public String putCapture(AutomationId id, byte[] verifiedPng) throws IOException {
        String sha = Canonical.sha256(verifiedPng);
        Path dir = folder(id).resolve("capturas");
        Files.createDirectories(dir);
        Path f = dir.resolve(sha.substring(7) + ".png");
        if (!Files.exists(f)) atomicWrite(f, verifiedPng);
        return sha;
    }

    public Optional<byte[]> capture(AutomationId id, String sha) throws IOException {
        if (sha == null || !sha.matches("sha256:[0-9a-f]{64}")) return Optional.empty();
        Path f = folder(id).resolve("capturas").resolve(sha.substring(7) + ".png");
        return Files.exists(f) ? Optional.of(Files.readAllBytes(f)) : Optional.empty();
    }

    /** Apaga as imagens que o rascunho não referencia mais. Devolve quantas foram apagadas. */
    public int retain(DocumentDraft d) throws IOException {
        return retain(d, Set.of());
    }

    /**
     * Apaga as imagens que nem o rascunho nem {@code alsoKeep} (sha-256 de PNGs ainda alcançáveis, ex.: pelo desfazer da
     * sessão) referenciam. A chave é o sha-256 do PNG — não a referência do bloco (no rascunho 1.1.0 elas diferem).
     */
    public int retain(DocumentDraft d, Set<String> alsoKeep) throws IOException {
        Path dir = folder(d.automationId()).resolve("capturas");
        if (!Files.isDirectory(dir)) return 0;
        Set<String> keep = new HashSet<>();
        d.referencedImages().forEach(s -> keep.add(s.substring(7) + ".png"));
        alsoKeep.stream().filter(s -> s.matches("sha256:[0-9a-f]{64}")).forEach(s -> keep.add(s.substring(7) + ".png"));
        int n = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path f : ds) {
                if (!keep.contains(f.getFileName().toString())) {
                    Files.deleteIfExists(f);
                    n++;
                }
            }
        }
        return n;
    }

    /** Descarta o rascunho e as imagens da automação. */
    public void discard(AutomationId id) throws IOException {
        Path dir = folder(id);
        if (!Files.exists(dir)) return;
        try (var s = Files.walk(dir)) {
            for (Path p : s.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }

    static void atomicWrite(Path target, byte[] bytes) throws IOException {
        Path tmp = Files.createTempFile(target.getParent(), ".pdd-", ".tmp");
        try {
            Files.write(tmp, bytes);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
