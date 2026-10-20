package com.nekkora.studio.pdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.pdd.DocumentDraft.Annotation;
import com.nekkora.studio.pdd.DocumentDraft.Block;
import com.nekkora.studio.pdd.DocumentDraft.Region;
import com.nekkora.studio.pdd.DocumentDraft.SafeCapture;
import com.nekkora.studio.pdd.VisualVerifier.VisualPolicyException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * NEKKORA-STUDIO-013 (revisão da usuária) — {@code nekkora-visual/4} no Studio: print real com o texto de dados mascarado.
 * 4ª barreira (repintura das máscaras), rascunho 1.2.0, PDF com o texto de procedência da v4. Dados sintéticos: a "tela"
 * é toda da cor-sentinela, e nenhum pixel dela pode ficar dentro de uma máscara.
 */
class PddVisualV4Test {
    @TempDir
    Path tmp;
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC);
    static final int W = 640;
    static final int H = 360;
    static final int SENT = 0xE6007E;

    static final List<Region> MASKS = List.of(new Region("BLOCK", 400, 40, 200, 120), new Region("FIELD", 40, 120, 280, 30),
            new Region("FIELD", 40, 200, 280, 30), new Region("TEXT", 40, 60, 200, 20, null, null, 0, null, "DARK", null),
            new Region("TEXT", 420, 10, 160, 18, null, null, 0, null, "LIGHT", null));

    static BufferedImage paint(List<Region> masks) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) img.setRGB(x, y, SENT);
        for (Region r : masks) {
            int[] fb = VisualVerifier.v4Paint(r.kind(), r.ink());
            for (int y = r.y(); y < r.y() + r.height(); y++) {
                for (int x = r.x(); x < r.x() + r.width(); x++) {
                    img.setRGB(x, y, y == r.y() || y == r.y() + r.height() - 1 || x == r.x() || x == r.x() + r.width() - 1 ? fb[1] : fb[0]);
                }
            }
        }
        return img;
    }

    static byte[] png(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    static String code(byte[] png, List<Region> regions) {
        return assertThrows(VisualPolicyException.class, () -> VisualVerifier.verify(png, Canonical.sha256(png), W, H, 1.5, regions,
                VisualVerifier.POLICY_V4)).code();
    }

    @Test
    @DisplayName("v4: máscaras pintadas passam; pixel da página dentro de máscara, máscara não pintada, tipo de outra política e ordem trocada são recusados")
    void quartaBarreira() throws Exception {
        byte[] ok = png(paint(MASKS));
        assertEquals(W, VisualVerifier.verify(ok, Canonical.sha256(ok), W, H, 1.5, MASKS, VisualVerifier.POLICY_V4).getWidth());
        BufferedImage leak = paint(MASKS);
        leak.setRGB(500, 100, SENT);
        assertEquals("SANITIZATION_CHECK_FAILED", code(png(leak), MASKS));
        assertEquals("SANITIZATION_CHECK_FAILED", code(png(paint(MASKS.subList(0, 4))), MASKS), "linha declarada e não coberta");
        List<Region> carrier = new ArrayList<>(MASKS);
        carrier.add(new Region("CARRIER", 1, 1, 5, 5));
        assertEquals("REGION_KIND_UNKNOWN", code(ok, carrier));
        List<Region> order = new ArrayList<>(MASKS);
        order.add(0, order.remove(3));
        assertEquals("REGION_ORDER", code(png(paint(order)), order));
        assertEquals("CHECKSUM_MISMATCH", assertThrows(VisualPolicyException.class, () -> VisualVerifier.verify(ok, "sha256:" + "0".repeat(64), W, H, 1.5,
                MASKS, VisualVerifier.POLICY_V4)).code());
        assertThrows(IllegalArgumentException.class, () -> new SafeCapture(null, "sha256:" + "a".repeat(64), W, H, 1, CLOCK.instant(), 0,
                VisualVerifier.POLICY_V4, "", List.of(), List.of(), List.of(), List.of(new Region("CARRIER", 0, 0, 4, 4)), null), "v4 só tem máscaras");
        assertThrows(IllegalArgumentException.class, () -> new SafeCapture(null, "sha256:" + "a".repeat(64), W, H, 1, CLOCK.instant(), 0,
                VisualVerifier.POLICY_V2, "", List.of(), List.of(), List.of(), List.of(new Region("BLOCK", 0, 0, 4, 4)), null), "BLOCK só na v4");
    }

    @Test
    @DisplayName("v4: rascunho 1.2.0 relido igual; PDF com a procedência da v4 (captura real, dados cobertos, revisar antes de compartilhar)")
    void rascunhoEPdf() throws Exception {
        Automation a = PddLab.consulta();
        ProcessProjection p = ProcessProjection.of(a);
        DocumentDraft d = DraftEdits.reviseSectionTitle(DraftBuilder.build(p), 1, "Autenticação", "TEST_AGENT");
        Block login = d.sections().get(0).blocks().get(0);
        d = DraftEdits.reviseBlockTitle(d, login.blockId(), "Realizar autenticação", "TEST_AGENT");
        PddStore store = new PddStore(tmp.resolve("dados"));
        String sha = store.putCapture(d.automationId(), png(paint(MASKS)));
        SafeCapture c = new SafeCapture("cap-" + "4".repeat(32), sha, W, H, 1.5, CLOCK.instant(), 1, VisualVerifier.POLICY_V4, login.screenKey(),
                login.stepIds(), List.of(new Annotation(login.stepIds().get(0), Annotation.Status.PLACED, 40 / 640.0, 120 / 360.0, 280 / 640.0, 30 / 360.0, "")),
                List.of("TABLE_CONTENT_HIDDEN"), MASKS, null);
        d = DraftEdits.attachCapture(d, login.blockId(), c);
        String json = DraftJson.write(d);
        assertTrue(json.contains("\"1.2.0\""));
        assertEquals(json, DraftJson.write(DraftJson.read(json)));
        PddExporter.Exported e = PddExporter.export(d, p, store, tmp.resolve("saida"), PddPdfTest.fonts(), CLOCK, null);
        try (PDDocument doc = Loader.loadPDF(e.pdf().toFile())) {
            String text = new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
            assertTrue(text.contains("capturas reais da tela com os campos, os textos de dados"), text);
            assertTrue(text.contains("Revise as imagens antes de compartilhar"), text);
            assertFalse(text.contains("nenhum pixel da página foi copiado"), "não é a procedência da v3");
            for (String s : PddLab.SENTINELS) assertFalse(text.contains(s), "sentinela no PDF: " + s);
        }
    }
}
