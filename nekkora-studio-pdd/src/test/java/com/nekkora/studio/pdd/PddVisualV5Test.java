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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Revisão da usuária (19/10/2026) — {@code nekkora-visual/5} no Studio: PRINT INTEGRAL. A "tela" (toda da cor-sentinela)
 * passa como está; só as máscaras de senha (FIELD) e de frame (BLOCK) são repintadas e conferidas. Rascunho 1.2.0; PDF
 * com a procedência do print integral e a nota em cada bloco.
 */
class PddVisualV5Test {
    @TempDir
    Path tmp;
    static final int W = PddVisualV4Test.W;
    static final int H = PddVisualV4Test.H;

    static final List<Region> MASKS = List.of(new Region("BLOCK", 400, 40, 200, 120), new Region("FIELD", 40, 200, 280, 30));

    static String code(byte[] png, List<Region> regions) {
        return assertThrows(VisualPolicyException.class, () -> VisualVerifier.verify(png, Canonical.sha256(png), W, H, 1.5, regions,
                VisualVerifier.POLICY_V5)).code();
    }

    @Test
    @DisplayName("v5: a tela passa como está com a senha coberta; senha não coberta, pixel da página na máscara e máscara de texto (só da v4) são recusados")
    void quartaBarreira() throws Exception {
        BufferedImage painted = PddVisualV4Test.paint(MASKS);
        byte[] ok = PddVisualV4Test.png(painted);
        BufferedImage img = VisualVerifier.verify(ok, Canonical.sha256(ok), W, H, 1.5, MASKS, VisualVerifier.POLICY_V5);
        assertEquals(PddVisualV4Test.SENT, img.getRGB(10, 10) & 0xFFFFFF, "fora das máscaras: a tela real, dados inclusive");
        assertEquals("SANITIZATION_CHECK_FAILED", code(PddVisualV4Test.png(PddVisualV4Test.paint(MASKS.subList(0, 1))), MASKS), "senha declarada e não coberta");
        BufferedImage leak = PddVisualV4Test.paint(MASKS);
        leak.setRGB(100, 210, PddVisualV4Test.SENT);
        assertEquals("SANITIZATION_CHECK_FAILED", code(PddVisualV4Test.png(leak), MASKS));
        List<Region> text = new ArrayList<>(MASKS);
        text.add(new Region("TEXT", 40, 60, 200, 20, null, null, 0, null, "DARK", null));
        assertEquals("REGION_KIND_UNKNOWN", code(PddVisualV4Test.png(PddVisualV4Test.paint(text)), text));
        assertThrows(IllegalArgumentException.class, () -> new SafeCapture(null, "sha256:" + "a".repeat(64), W, H, 1, PddVisualV4Test.CLOCK.instant(), 0,
                VisualVerifier.POLICY_V5, "", List.of(), List.of(), List.of(), List.of(new Region("TEXT", 0, 0, 4, 4, null, null, 0, null, "DARK", null)), null),
                "v5 só tem as máscaras de senha e de frame");
    }

    @Test
    @DisplayName("print real (v4/v5) sem recorte escolhido: o PDF leva a tela inteira; recorte escolhido vale; v2 segue com o recorte automático")
    void telaInteiraSemRecorte() throws Exception {
        BufferedImage img = PddVisualV4Test.paint(MASKS);
        List<Annotation> anns = List.of(new Annotation(new com.nekkora.studio.domain.StepId(java.util.UUID.randomUUID()), Annotation.Status.PLACED, 40 / 640.0, 200 / 360.0,
                280 / 640.0, 30 / 360.0, ""));
        for (String policy : List.of(VisualVerifier.POLICY_V5, VisualVerifier.POLICY_V4)) {
            List<Region> regions = VisualVerifier.POLICY_V5.equals(policy) ? MASKS : PddVisualV4Test.MASKS;
            SafeCapture c = new SafeCapture("cap-" + "7".repeat(32), "sha256:" + "a".repeat(64), W, H, 1.5, PddVisualV4Test.CLOCK.instant(), 0, policy, "",
                    List.of(), anns, List.of(), regions, null);
            PdfRenderer.Crop k = PdfRenderer.Crop.of(img, c);
            assertEquals(List.of(0, 0, W, H), List.of(k.x(), k.y(), k.w(), k.h()), policy + ": a tela inteira, como no editor");
            PdfRenderer.Crop chosen = PdfRenderer.Crop.of(img, c.withCrop(new DocumentDraft.Crop(20, 180, 320, 80, "DEVELOPER")));
            assertEquals(List.of(20, 180, 320, 80), List.of(chosen.x(), chosen.y(), chosen.w(), chosen.h()), policy + ": o recorte escolhido");
        }
        SafeCapture v2 = new SafeCapture("cap-" + "8".repeat(32), "sha256:" + "a".repeat(64), W, H, 1.0, PddVisualV4Test.CLOCK.instant(), 0,
                VisualVerifier.POLICY_V2, "", List.of(), anns, List.of(), List.of(new Region("FIELD", 40, 200, 280, 30)), null);
        assertTrue(PdfRenderer.Crop.of(img, v2).w() < W, "v2: recorte automático em volta dos campos");
    }

    @Test
    @DisplayName("v5: rascunho 1.2.0 relido igual; PDF declara o print integral, com nota no bloco e a mistura com imagens mascaradas")
    void rascunhoEPdf() throws Exception {
        Automation a = PddLab.consulta();
        ProcessProjection p = ProcessProjection.of(a);
        DocumentDraft d = DraftEdits.reviseSectionTitle(DraftBuilder.build(p), 1, "Autenticação", "TEST_AGENT");
        Block login = d.sections().get(0).blocks().get(0);
        d = DraftEdits.reviseBlockTitle(d, login.blockId(), "Realizar autenticação", "TEST_AGENT");
        PddStore store = new PddStore(tmp.resolve("dados"));
        String sha = store.putCapture(d.automationId(), PddVisualV4Test.png(PddVisualV4Test.paint(MASKS)));
        SafeCapture c = new SafeCapture("cap-" + "5".repeat(32), sha, W, H, 1.5, PddVisualV4Test.CLOCK.instant(), 1, VisualVerifier.POLICY_V5,
                login.screenKey(), login.stepIds(), List.of(new Annotation(login.stepIds().get(0), Annotation.Status.PLACED, 40 / 640.0, 120 / 360.0,
                        280 / 640.0, 30 / 360.0, "")), List.of("FRAMES_HIDDEN"), MASKS, null);
        assertTrue(c.fullPrint());
        d = DraftEdits.attachCapture(d, login.blockId(), c);
        String json = DraftJson.write(d);
        assertTrue(json.contains("\"1.2.0\""));
        assertEquals(json, DraftJson.write(DraftJson.read(json)));
        PddExporter.Exported e = PddExporter.export(d, p, store, tmp.resolve("saida"), PddPdfTest.fonts(), PddVisualV4Test.CLOCK, null);
        try (PDDocument doc = Loader.loadPDF(e.pdf().toFile())) {
            String t = new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
            assertTrue(t.contains("Este documento contém imagens em print integral"), t);
            assertTrue(t.contains("só os campos de senha foram cobertos"), t);
            assertTrue(t.contains("Print integral: a imagem mostra os dados da tela"), "nota no bloco: " + t);
            assertFalse(t.contains("As demais imagens"), "só print integral: sem a frase da mistura");
            assertFalse(t.contains("capturas reais da tela com os campos, os textos de dados"), "não é a procedência da v4");
        }
        // um segundo bloco com print MASCARADO: a procedência diz que as demais imagens trazem os dados cobertos
        Block other = d.sections().stream().flatMap(s -> s.blocks().stream()).filter(b -> !b.blockId().equals(login.blockId())).findFirst().orElseThrow();
        String sha4 = store.putCapture(d.automationId(), PddVisualV4Test.png(PddVisualV4Test.paint(PddVisualV4Test.MASKS)));
        SafeCapture m = new SafeCapture("cap-" + "6".repeat(32), sha4, W, H, 1.5, PddVisualV4Test.CLOCK.instant(), 1, VisualVerifier.POLICY_V4,
                other.screenKey(), other.stepIds(), List.of(), List.of(), PddVisualV4Test.MASKS, null);
        d = DraftEdits.attachCapture(d, other.blockId(), m);
        PddExporter.Exported e2 = PddExporter.export(d, p, store, tmp.resolve("saida2"), PddPdfTest.fonts(), PddVisualV4Test.CLOCK, null);
        try (PDDocument doc = Loader.loadPDF(e2.pdf().toFile())) {
            String t = new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
            assertTrue(t.contains("As demais imagens trazem os dados cobertos"), t);
        }
    }
}
