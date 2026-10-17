package com.nekkora.studio.pdd;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import com.nekkora.studio.pdd.DocumentDraft.Block;
import com.nekkora.studio.pdd.DocumentDraft.Pending;
import com.nekkora.studio.pdd.DocumentDraft.SafeCapture;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * STUDIO-010 G7/G8 — exportação LOCAL do PDD visual.
 *
 * <ul>
 *   <li>Só com o rascunho sem pendências que bloqueiam e a revisão do processo atual.</li>
 *   <li>Cada imagem é REVERIFICADA pela política visual ({@link VisualVerifier}) a partir do armazenamento.</li>
 *   <li>Versões {@code pdd-visual-v001.pdf}, {@code v002}… no diretório escolhido: nunca sobrescreve um arquivo existente
 *       (mover sem substituir; se aparecer um igual no meio do caminho, tenta o número seguinte).</li>
 *   <li>Escreve um temporário no MESMO diretório, reabre e verifica (páginas, texto extraído sem padrão proibido, metadados)
 *       e só então move; qualquer falha apaga o temporário — nenhum documento parcial fica com nome de versão.</li>
 *   <li>Manifesto {@code pdd-visual-vNNN.manifest.json} sem nenhum texto: revisões, hashes das imagens, páginas, contagens.
 *       Com ele o Studio diz se um PDF ficou desatualizado.</li>
 * </ul>
 */
public final class PddExporter {
    public static final String BASE = "pdd-visual";
    public static final String MANIFEST_SCHEMA = "nekkora-studio/pdd-visual-manifest";
    public static final String MANIFEST_VERSION = "1.0.0";
    private static final Pattern NAME = Pattern.compile(BASE + "-v(\\d{3})\\.pdf");

    private PddExporter() {}

    /** Exportação recusada (código estável). */
    public static final class ExportException extends Exception {
        private final String code;

        public ExportException(String code) {
            super(code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** Resultado: arquivo, versão e manifesto. */
    public record Exported(Path pdf, Path manifest, int version, int pages, String documentRevision) {}

    /** Cancelamento cooperativo (o usuário fechou ou desistiu no meio). */
    public interface Cancel {
        boolean requested();
    }

    public static Exported export(DocumentDraft d, ProcessProjection current, PddStore store, Path dir, PdfRenderer.Fonts fonts, Clock clock, Cancel cancel)
            throws ExportException, IOException {
        List<Pending> pending = d.pending(current);
        if (!Pendings.exportable(pending)) throw new ExportException("DRAFT_HAS_BLOCKING_PENDING");
        Map<String, BufferedImage> images = new LinkedHashMap<>();
        for (Block b : d.blocks()) {
            if (b.capture().isEmpty()) continue;
            SafeCapture c = d.captures().get(b.captureSha());
            byte[] png = store.capture(d.automationId(), c.sha256()).orElseThrow(() -> new ExportException("CAPTURE_FILE_MISSING"));
            try {
                images.put(c.sha256(), VisualVerifier.verify(png, c.sha256(), c.width(), c.height(), c.devicePixelRatio(), c.regions()));
            } catch (VisualVerifier.VisualPolicyException e) {
                throw new ExportException("CAPTURE_REJECTED_" + e.code());
            }
        }
        if (cancel != null && cancel.requested()) throw new ExportException("CANCELLED");
        Files.createDirectories(dir);
        int version = nextVersion(dir);
        LocalDate date = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        byte[] pdf = PdfRenderer.render(d, images, fonts, new PdfRenderer.Options(label(version), date));
        Path tmp = Files.createTempFile(dir, ".pdd-visual-", ".tmp");
        try {
            Files.write(tmp, pdf);
            int pages = verifyPdf(tmp);
            if (cancel != null && cancel.requested()) throw new ExportException("CANCELLED");
            Path target;
            while (true) {
                target = dir.resolve(BASE + "-" + label(version) + ".pdf");
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE); // sem REPLACE_EXISTING: nunca sobrescreve
                    break;
                } catch (FileAlreadyExistsException e) {
                    version++;
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    if (Files.exists(target)) {
                        version++;
                        continue;
                    }
                    Files.move(tmp, target);
                    break;
                }
            }
            String rev = d.revision();
            Path manifest = dir.resolve(BASE + "-" + label(version) + ".manifest.json");
            PddStore.atomicWrite(manifest, manifest(d, version, pages, rev, clock).getBytes(StandardCharsets.UTF_8));
            return new Exported(target, manifest, version, pages, rev);
        } catch (ExportException | IOException | RuntimeException e) {
            throw e;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Reabre o PDF e confere: tem páginas, o texto extraído não tem padrão proibido, metadados só os previstos. */
    static int verifyPdf(Path file) throws ExportException, IOException {
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            int pages = doc.getNumberOfPages();
            if (pages < 1) throw new ExportException("PDF_EMPTY");
            String text = new PDFTextStripper().getText(doc);
            for (String line : text.split("\\R")) {
                String l = line.strip();
                if (l.isEmpty() || l.startsWith("Versão ") || l.startsWith("Página ")) continue; // linhas fixas com data/número de página
                if (DisplaySafety.looksSensitive(l) || DisplaySafety.looksLikeInternalIdentifier(l) || l.matches(".*\\d{3,}.*")) {
                    throw new ExportException("PDF_TEXT_POLICY");
                }
            }
            var info = doc.getDocumentInformation();
            if (info.getAuthor() != null || info.getSubject() != null || info.getKeywords() != null || doc.getDocumentCatalog().getMetadata() != null) {
                throw new ExportException("PDF_METADATA_POLICY");
            }
            return pages;
        }
    }

    static String manifest(DocumentDraft d, int version, int pages, String documentRevision, Clock clock) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schema", MANIFEST_SCHEMA);
        m.put("version", MANIFEST_VERSION);
        m.put("documentVersion", version);
        m.put("exportedAt", clock.instant().toString());
        m.put("status", "EXPORTED");
        m.put("processRevision", d.processRevision());
        m.put("documentRevision", documentRevision);
        m.put("pages", pages);
        m.put("sections", d.sections().size());
        m.put("blocks", d.blocks().size());
        List<Object> caps = new ArrayList<>();
        d.blocks().forEach(b -> b.capture().ifPresent(caps::add));
        m.put("captures", caps);
        long missing = d.pending(null).stream().filter(p -> p.code().equals("CAPTURE_MISSING")).count();
        long markers = d.pending(null).stream().filter(p -> p.code().equals("MARKER_UNAVAILABLE")).count();
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("blocksWithoutImage", missing);
        limits.put("markersUnavailable", markers);
        m.put("limitations", limits);
        m.put("visualPolicy", VisualVerifier.POLICY);
        m.put("exclusions", List.of("VALUES", "VARIABLE_NAMES", "CREDENTIALS", "SELECTORS", "TECHNICAL_IDS", "PAGE_TEXT", "BUSINESS_OUTCOME"));
        StringBuilder b = new StringBuilder();
        DraftJson.Pretty.value(b, m, 0);
        return b.append('\n').toString();
    }

    /** Próximo número livre (o maior existente + 1). */
    static int nextVersion(Path dir) throws IOException {
        int max = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, BASE + "-v*.pdf")) {
            for (Path p : ds) {
                Matcher m = NAME.matcher(p.getFileName().toString());
                if (m.matches()) max = Math.max(max, Integer.parseInt(m.group(1)));
            }
        }
        if (max >= 999) throw new IOException("limite de versões");
        return max + 1;
    }

    static String label(int v) {
        return String.format("v%03d", v);
    }

    /** Situação do último PDF exportado neste diretório para a revisão atual do processo. */
    public record Latest(Path pdf, int version, boolean outdated) {}

    public static Optional<Latest> latest(Path dir, ProcessProjection current) throws IOException {
        if (!Files.isDirectory(dir)) return Optional.empty();
        int v = nextVersion(dir) - 1;
        while (v >= 1) {
            Path m = dir.resolve(BASE + "-" + label(v) + ".manifest.json");
            Path pdf = dir.resolve(BASE + "-" + label(v) + ".pdf");
            if (Files.exists(m) && Files.exists(pdf)) {
                Map<String, Object> j = Json.parseObject(Files.readString(m, StandardCharsets.UTF_8));
                return Optional.of(new Latest(pdf, v, !current.revision().equals(Json.string(j, "processRevision"))));
            }
            v--;
        }
        return Optional.empty();
    }
}
