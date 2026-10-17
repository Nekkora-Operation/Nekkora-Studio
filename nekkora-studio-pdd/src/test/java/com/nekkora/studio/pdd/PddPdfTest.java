package com.nekkora.studio.pdd;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.StepContext;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.pdd.DocumentDraft.Annotation;
import com.nekkora.studio.pdd.DocumentDraft.Block;
import com.nekkora.studio.pdd.DocumentDraft.Region;
import com.nekkora.studio.pdd.DocumentDraft.SafeCapture;
import com.nekkora.studio.pdd.PddExporter.ExportException;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** STUDIO-010 G7/G8 — PDF local, versões, manifesto e verificações da exportação. */
class PddPdfTest {
    @TempDir
    Path tmp;
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC);

    /** Imagem sanitizada sintética do login: rótulos (portadores) e campos (molduras), em px do dispositivo. */
    static BufferedImage loginImage() {
        BufferedImage img = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 720; y++) for (int x = 0; x < 1280; x++) img.setRGB(x, y, VisualVerifier.OPAQUE);
        int[][] carriers = {{40, 118, 60, 20}, {290, 118, 52, 20}, {544, 142, 72, 36}};
        for (int[] c : carriers) for (int y = c[1]; y < c[1] + c[3]; y++) for (int x = c[0]; x < c[0] + c[2]; x++) img.setRGB(x, y, 0xF6F8FA);
        int[][] fields = {{40, 140, 240, 39}, {290, 140, 240, 39}};
        for (int[] f : fields) {
            for (int y = f[1]; y < f[1] + f[3]; y++) {
                for (int x = f[0]; x < f[0] + f[2]; x++) {
                    boolean edge = y == f[1] || y == f[1] + f[3] - 1 || x == f[0] || x == f[0] + f[2] - 1;
                    img.setRGB(x, y, edge ? VisualVerifier.FIELD_BORDER : VisualVerifier.FIELD_FILL);
                }
            }
        }
        return img;
    }

    static final List<Region> LOGIN_REGIONS = List.of(new Region("CARRIER", 40, 118, 60, 20), new Region("CARRIER", 290, 118, 52, 20),
            new Region("CARRIER", 544, 142, 72, 36), new Region("FIELD", 40, 140, 240, 39), new Region("FIELD", 290, 140, 240, 39));

    record Fixture(Automation automation, ProcessProjection projection, DocumentDraft draft, PddStore store) {}

    Fixture ready() throws Exception {
        Automation a = PddLab.consulta();
        ProcessProjection p = ProcessProjection.of(a);
        DocumentDraft d = DraftEdits.reviseSectionTitle(DraftBuilder.build(p), 1, "Autenticação", "TEST_AGENT");
        Block login = d.sections().get(0).blocks().get(0);
        d = DraftEdits.reviseBlockTitle(d, login.blockId(), "Realizar autenticação", "TEST_AGENT");
        PddStore store = new PddStore(tmp.resolve("dados"));
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        ImageIO.write(loginImage(), "png", out);
        String sha = store.putCapture(d.automationId(), out.toByteArray());
        SafeCapture c = new SafeCapture(sha, 1280, 720, 1.0, CLOCK.instant(), 1, "nekkora-visual/1", login.screenKey(), login.stepIds(), List.of(
                new Annotation(login.stepIds().get(0), Annotation.Status.PLACED, 40 / 1280.0, 140 / 720.0, 240 / 1280.0, 39 / 720.0, ""),
                new Annotation(login.stepIds().get(1), Annotation.Status.PLACED, 290 / 1280.0, 140 / 720.0, 240 / 1280.0, 39 / 720.0, ""),
                new Annotation(login.stepIds().get(2), Annotation.Status.OMITTED, 0, 0, 0, 0, "TARGET_AMBIGUOUS")), List.of(), LOGIN_REGIONS);
        d = DraftEdits.attachCapture(d, login.blockId(), c);
        return new Fixture(a, p, d, store);
    }

    static PdfRenderer.Fonts fonts() throws Exception {
        return PdfRenderer.Fonts.fromClasspath(PddPdfTest.class.getClassLoader());
    }

    @Test
    @DisplayName("G7 PDF: seções na ordem, títulos e instruções numeradas pesquisáveis, imagem indisponível declarada, Inter embutida, metadados controlados, sem sentinelas")
    void pdf() throws Exception {
        Fixture f = ready();
        Path dir = tmp.resolve("saida");
        PddExporter.Exported e = PddExporter.export(f.draft(), f.projection(), f.store(), dir, fonts(), CLOCK, null);
        assertEquals(dir.resolve("pdd-visual-v001.pdf"), e.pdf());
        try (PDDocument doc = Loader.loadPDF(e.pdf().toFile())) {
            String text = new PDFTextStripper().getText(doc);
            int a = text.indexOf("1. Autenticação");
            int b = text.indexOf("2. Consulta");
            int c = text.indexOf("3. Resultado");
            assertTrue(a >= 0 && a < b && b < c, "seções na ordem das sessões:\n" + text);
            for (String s : List.of("Realizar autenticação", "1. Informar o campo \"Usuário\"", "2. Informar a senha", "3. Selecionar \"Entrar\"",
                    "(marcador indisponível)", "Imagem indisponível", "Ações observadas na gravação", "Etapas declaradas no Studio",
                    "não comprova resultado de negócio")) {
                assertTrue(text.contains(s), "falta: " + s + "\n" + text);
            }
            for (String s : PddLab.SENTINELS) assertFalse(text.contains(s), "sentinela no PDF: " + s);
            assertEquals("PDD visual", doc.getDocumentInformation().getTitle());
            assertNull(doc.getDocumentInformation().getAuthor());
            assertNull(doc.getDocumentCatalog().getMetadata(), "sem XMP");
            PDFont font = doc.getPage(0).getResources().getFont(doc.getPage(0).getResources().getFontNames().iterator().next());
            assertTrue(font.getName().contains("Inter") && font.isEmbedded(), font.getName());
            BufferedImage page = new PDFRenderer(doc).renderImageWithDPI(0, 96);
            ImageIO.write(page, "png", Path.of("target", "pdd-pagina-1.png").toFile()); // para inspeção visual (dados sintéticos)
            int ink = 0;
            for (int y = 0; y < page.getHeight(); y += 2) for (int x = 0; x < page.getWidth(); x += 2) if ((page.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) ink++;
            assertTrue(ink > 2000, "página não está em branco");
        }
        String manifest = Files.readString(e.manifest());
        assertTrue(manifest.contains(f.projection().revision()) && manifest.contains("\"documentVersion\": 1"));
        for (String s : PddLab.SENTINELS) assertFalse(manifest.contains(s), "sentinela no manifesto: " + s);
        assertFalse(manifest.contains("Autenticação"), "manifesto sem textos");
    }

    @Test
    @DisplayName("G8 versões: v001, v002…; nunca sobrescreve (arquivo pré-existente pula para o próximo); anteriores intactos; nenhum temporário sobra")
    void versoes() throws Exception {
        Fixture f = ready();
        Path dir = tmp.resolve("saida");
        PddExporter.Exported v1 = PddExporter.export(f.draft(), f.projection(), f.store(), dir, fonts(), CLOCK, null);
        byte[] first = Files.readAllBytes(v1.pdf());
        assertEquals(2, PddExporter.export(f.draft(), f.projection(), f.store(), dir, fonts(), CLOCK, null).version());
        Files.writeString(dir.resolve("pdd-visual-v003.pdf"), "arquivo de outra pessoa");
        assertEquals(4, PddExporter.export(f.draft(), f.projection(), f.store(), dir, fonts(), CLOCK, null).version());
        assertArrayEquals(first, Files.readAllBytes(v1.pdf()), "v001 intacto");
        assertEquals("arquivo de outra pessoa", Files.readString(dir.resolve("pdd-visual-v003.pdf")));
        try (var s = Files.list(dir)) {
            assertTrue(s.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")), "sem temporários");
        }
    }

    @Test
    @DisplayName("G8 recusas: pendência que bloqueia, imagem adulterada ou ausente no armazenamento, cancelamento — nada é escrito")
    void recusas() throws Exception {
        Fixture f = ready();
        Path dir = tmp.resolve("saida");
        DocumentDraft pending = DraftBuilder.build(f.projection());
        assertEquals("DRAFT_HAS_BLOCKING_PENDING", assertThrows(ExportException.class,
                () -> PddExporter.export(pending, f.projection(), f.store(), dir, fonts(), CLOCK, null)).code());
        assertEquals("CANCELLED", assertThrows(ExportException.class,
                () -> PddExporter.export(f.draft(), f.projection(), f.store(), dir, fonts(), CLOCK, () -> true)).code());
        String sha = f.draft().sections().get(0).blocks().get(0).captureSha();
        Path file = f.store().folder(f.draft().automationId()).resolve("capturas").resolve(sha.substring(7) + ".png");
        BufferedImage leak = loginImage();
        leak.setRGB(900, 600, 0xE6007E);
        ImageIO.write(leak, "png", file.toFile());
        assertEquals("CAPTURE_REJECTED_CHECKSUM_MISMATCH", assertThrows(ExportException.class,
                () -> PddExporter.export(f.draft(), f.projection(), f.store(), dir, fonts(), CLOCK, null)).code(), "imagem trocada no disco");
        Files.delete(file);
        assertEquals("CAPTURE_FILE_MISSING", assertThrows(ExportException.class,
                () -> PddExporter.export(f.draft(), f.projection(), f.store(), dir, fonts(), CLOCK, null)).code());
        assertTrue(!Files.exists(dir) || Files.list(dir).findAny().isEmpty(), "nenhum arquivo escrito");
    }

    @Test
    @DisplayName("G7 várias páginas: seções e instruções continuam na ordem, nada é desenhado fora da área útil, rodapé \"Página n de N\" em todas")
    void variasPaginas() throws Exception {
        Automation a = Automation.create("Processo longo", "", PddLab.CLOCK);
        for (int s = 0; s < 6; s++) {
            var session = a.addSession("Consulta " + (char) ('A' + s), "");
            for (int i = 0; i < (s == 2 ? 40 : 3); i++) { // a sessão 3 tem um bloco maior que uma página
                PddLab.recorded(a, session, "x", ActionType.CLICK, PddLab.el("tgt-" + s + "-" + i, "BUTTON", "button", "Consultar", "#c"),
                        com.nekkora.studio.domain.action.ActionParameters.none(), "http://127.0.0.1/lab/p" + s + ".html", "Consulta");
            }
        }
        ProcessProjection p = ProcessProjection.of(a);
        DocumentDraft d = DraftBuilder.build(p);
        for (DocumentDraft.Section s : d.sections()) {
            d = DraftEdits.reviseSectionTitle(d, s.number(), "Consulta", "TEST_AGENT");
            for (Block b : s.blocks()) d = DraftEdits.reviseBlockTitle(d, b.blockId(), "Consultar registros", "TEST_AGENT");
        }
        PddExporter.Exported e = PddExporter.export(d, p, new PddStore(tmp.resolve("dados")), tmp.resolve("saida"), fonts(), CLOCK, null);
        try (PDDocument doc = Loader.loadPDF(e.pdf().toFile())) {
            int pages = doc.getNumberOfPages();
            assertTrue(pages >= 3, "páginas: " + pages);
            List<Float> ys = new java.util.ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void processTextPosition(org.apache.pdfbox.text.TextPosition t) {
                    ys.add(t.getYDirAdj());
                    super.processTextPosition(t);
                }
            };
            String text = stripper.getText(doc);
            float h = doc.getPage(0).getMediaBox().getHeight();
            assertTrue(ys.stream().allMatch(y -> y > 20 && y < h - 20), "texto fora da página");
            int last = -1;
            for (int s = 1; s <= 6; s++) {
                int at = text.indexOf(s + ". Consulta", last + 1);
                assertTrue(at > last, "seção " + s + " fora de ordem");
                last = at;
            }
            int from = text.indexOf("3. Consulta");
            for (int i = 1; i <= 40; i++) {
                int at = text.indexOf(i + ". Selecionar \"Consultar\"", from);
                assertTrue(at > from || i == 1 && at >= from, "instrução " + i + " fora de ordem");
                from = at;
            }
            for (int i = 1; i <= pages; i++) assertTrue(text.contains("Página " + i + " de " + pages), "rodapé da página " + i);
            assertTrue(Files.readString(e.manifest()).contains("\"pages\": " + pages));
        }
    }

    @Test
    @DisplayName("R2 (PDFBox 3.0.8): acentos e pontuação extraídos sem perda; imagem no PDF idêntica, pixel a pixel, à captura sanitizada guardada")
    void caracteresEImagemFiel() throws Exception {
        Fixture f = ready();
        Block login = f.draft().sections().get(0).blocks().get(0);
        String title = "Ação de conferência: área pública (opção única), após a emissão";
        DocumentDraft d = DraftEdits.reviseBlockTitle(f.draft(), login.blockId(), title, "TEST_AGENT");
        PddExporter.Exported e = PddExporter.export(d, f.projection(), f.store(), tmp.resolve("saida"), fonts(), CLOCK, null);
        try (PDDocument doc = Loader.loadPDF(e.pdf().toFile())) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains(title), "título com acentos e pontuação:\n" + text);
            for (String s : List.of("Autenticação", "Informar o campo \"Usuário\"", "Código do registro", "não comprova resultado de negócio")) {
                assertTrue(text.contains(s), "falta: " + s);
            }
            assertFalse(text.contains("\uFFFD"), "nenhum caractere de substituição");
            var res = doc.getPage(0).getResources();
            BufferedImage inPdf = null;
            for (var n : res.getXObjectNames()) {
                if (res.getXObject(n) instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject img) inPdf = img.getImage();
            }
            assertTrue(inPdf != null, "imagem no PDF");
            BufferedImage stored = ImageIO.read(new java.io.ByteArrayInputStream(f.store().capture(d.automationId(), login.captureSha()).orElseThrow()));
            // o PDF leva o recorte da área útil (união das regiões + margem): cada pixel dele existe igual na captura guardada
            int matches = 0;
            outer:
            for (int oy = 0; oy + inPdf.getHeight() <= stored.getHeight(); oy++) {
                for (int ox = 0; ox + inPdf.getWidth() <= stored.getWidth(); ox++) {
                    boolean same = true;
                    for (int y = 0; y < inPdf.getHeight() && same; y += 3) {
                        for (int x = 0; x < inPdf.getWidth() && same; x += 3) {
                            same = (inPdf.getRGB(x, y) & 0xFFFFFF) == (stored.getRGB(ox + x, oy + y) & 0xFFFFFF);
                        }
                    }
                    if (same) {
                        matches++;
                        for (int y = 0; y < inPdf.getHeight(); y++) {
                            for (int x = 0; x < inPdf.getWidth(); x++) {
                                assertEquals(stored.getRGB(ox + x, oy + y) & 0xFFFFFF, inPdf.getRGB(x, y) & 0xFFFFFF, "pixel " + x + "," + y);
                            }
                        }
                        break outer;
                    }
                }
            }
            assertEquals(1, matches, "a imagem do PDF é um recorte exato (sem perda) da captura sanitizada");
        }
    }

    @Test
    @DisplayName("G8 desatualizado: o processo mudou depois da exportação → o último PDF é indicado como desatualizado; o rascunho exige atualizar")
    void desatualizado() throws Exception {
        Fixture f = ready();
        Path dir = tmp.resolve("saida");
        PddExporter.export(f.draft(), f.projection(), f.store(), dir, fonts(), CLOCK, null);
        assertFalse(PddExporter.latest(dir, f.projection()).orElseThrow().outdated());
        f.automation().appendStep(f.automation().sessions().get(1).id(), StepDraft.builder().name("x").actionType(ActionType.WAIT_FOR)
                .context(new StepContext("Laboratório", "Consulta", PddLab.CONSULTA)).build());
        ProcessProjection now = ProcessProjection.of(f.automation());
        assertTrue(PddExporter.latest(dir, now).orElseThrow().outdated());
        assertEquals("DRAFT_HAS_BLOCKING_PENDING", assertThrows(ExportException.class,
                () -> PddExporter.export(f.draft(), now, f.store(), dir, fonts(), CLOCK, null)).code());
    }
}
