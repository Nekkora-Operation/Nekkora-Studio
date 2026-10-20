package com.nekkora.studio.pdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.pdd.DocumentDraft.Annotation;
import com.nekkora.studio.pdd.DocumentDraft.Block;
import com.nekkora.studio.pdd.DocumentDraft.Crop;
import com.nekkora.studio.pdd.DocumentDraft.Region;
import com.nekkora.studio.pdd.DocumentDraft.SafeCapture;
import com.nekkora.studio.pdd.SyntheticRenderer.Description;
import com.nekkora.studio.pdd.SyntheticRenderer.Rendered;
import com.nekkora.studio.pdd.VisualVerifier.VisualPolicyException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
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
 * NEKKORA-STUDIO-013 — {@code nekkora-visual/3} no Studio: a imagem é DESENHADA aqui a partir da descrição (nenhum pixel
 * da página existe), a 4ª barreira reverifica no disco, o rascunho sobe para 1.2.0 só com v3, e o PDF declara que a imagem
 * é sintética. Dados 100% sintéticos.
 */
class PddVisualV3Test {
    @TempDir
    Path tmp;
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC);

    /** Login sintético (px CSS): faixa escura, cartão, logotipo, frame, botão, dois campos, barras de texto e rótulos. */
    static final List<Region> LOGIN = List.of(
            new Region("SURFACE", 0, 0, 640, 40, "DARK"),
            new Region("SURFACE", 160, 60, 320, 280, "LIGHT"),
            new Region("MEDIA", 8, 4, 32, 32),
            new Region("FRAME", 500, 260, 120, 80),
            new Region("CONTROL", 380, 280, 80, 32),
            new Region("FIELD", 180, 130, 280, 28),
            new Region("FIELD", 180, 200, 280, 28),
            Region.text(180, 72, 128, 10, "DARK"),
            Region.text(48, 14, 96, 10, "LIGHT"),
            Region.label(180, 108, 80, 18, "Usuário", 14, "NORMAL", "DARK", "START"),
            Region.label(180, 178, 80, 18, "Senha", 14, "NORMAL", "DARK", "START"),
            Region.label(380, 280, 80, 32, "Entrar", 14, "BOLD", "DARK", "CENTER"),
            Region.label(500, 8, 120, 24, "Sair", 13, "NORMAL", "LIGHT", "CENTER"));

    static Description login() {
        return new Description(640, 360, 1.25, LOGIN);
    }

    static String code(Rendered r, List<Region> regions) {
        return assertThrows(VisualPolicyException.class, () -> VisualVerifier.verify(r.png(), r.sha256(), r.width(), r.height(), r.scale(), regions,
                VisualVerifier.POLICY_V3)).code();
    }

    static byte[] png(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("v3: desenho determinístico em escala 2 passa na 4ª barreira; o texto dos rótulos aparece como tinta sobre o fundo esperado")
    void desenhaEVerifica() throws Exception {
        Rendered r = SyntheticRenderer.render(login());
        assertEquals(2, r.scale());
        assertEquals(1280, r.width());
        assertEquals(720, r.height());
        assertEquals(LOGIN.size(), r.regions().size());
        BufferedImage img = VisualVerifier.verify(r.png(), r.sha256(), r.width(), r.height(), r.scale(), r.regions(), VisualVerifier.POLICY_V3);
        // dentro de "Entrar" há tinta (texto desenhado), fora dos rótulos só cores da paleta
        long ink = 0;
        Region entrar = r.regions().get(11);
        for (int y = entrar.y(); y < entrar.y() + entrar.height(); y++) {
            for (int x = entrar.x(); x < entrar.x() + entrar.width(); x++) if ((img.getRGB(x, y) & 0xFFFFFF) == VisualVerifier.V3_INK_DARK) ink++;
        }
        assertTrue(ink > 20, "o rótulo foi desenhado: " + ink);
        assertEquals(SyntheticRenderer.render(login()).sha256(), r.sha256(), "mesma descrição, mesma imagem (na mesma máquina)");
    }

    @Test
    @DisplayName("v3: pixel de outra cor fora dos rótulos, cor fora da mistura dentro de um rótulo, PNG trocado e região adulterada são recusados")
    void quartaBarreira() throws Exception {
        Rendered r = SyntheticRenderer.render(login());
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(r.png()));
        BufferedImage leak = copy(img);
        leak.setRGB(400, 300, 0xE6007E); // dentro do cartão
        byte[] l = png(leak);
        assertEquals("SANITIZATION_CHECK_FAILED", code(new Rendered(l, Canonical.sha256(l), r.width(), r.height(), 2, r.regions(), 0), r.regions()));
        BufferedImage inLabel = copy(img);
        Region senha = r.regions().get(10);
        inLabel.setRGB(senha.x() + 3, senha.y() + 3, 0x00C81E); // verde dentro do rótulo: não é mistura fundo↔tinta
        byte[] il = png(inLabel);
        assertEquals("SANITIZATION_CHECK_FAILED", code(new Rendered(il, Canonical.sha256(il), r.width(), r.height(), 2, r.regions(), 0), r.regions()));
        assertEquals("CHECKSUM_MISMATCH", code(new Rendered(r.png(), "sha256:" + "0".repeat(64), r.width(), r.height(), 2, r.regions(), 0), r.regions()));
        List<Region> name = new ArrayList<>(r.regions());
        Region s = name.get(10);
        name.set(10, Region.label(s.x(), s.y(), s.width(), s.height(), "Maria Souza", 14, "NORMAL", "DARK", "START"));
        assertEquals("LABEL_NOT_INTERFACE_TEXT", code(r, name));
        List<Region> carrier = new ArrayList<>(r.regions());
        carrier.add(new Region("CARRIER", 0, 0, 10, 10));
        assertEquals("REGION_KIND_UNKNOWN", code(r, carrier), "portador de pixels não existe na v3");
        List<Region> odd = new ArrayList<>(r.regions());
        odd.set(5, new Region("FIELD", 361, 260, 560, 56));
        assertEquals("GEOMETRY_MISMATCH", code(r, odd), "geometria fora da escala");
        List<Region> overlap = new ArrayList<>(r.regions());
        overlap.add(Region.label(senha.x() + 4, senha.y(), 40, 36, "Salvar", 12, "NORMAL", "DARK", "START"));
        assertEquals("LABELS_OVERLAP", code(r, overlap));
        assertEquals("GEOMETRY_MISMATCH", assertThrows(VisualPolicyException.class,
                () -> VisualVerifier.verify(r.png(), r.sha256(), r.width(), r.height(), 1.5, r.regions(), VisualVerifier.POLICY_V3)).code());
    }

    @Test
    @DisplayName("descrição: texto de rótulo fora do vocabulário, barra não quantizada, ordem e geometria são recusados antes de desenhar")
    void descricaoRecusada() {
        List<Region> bad = new ArrayList<>(LOGIN);
        bad.set(9, Region.label(180, 108, 80, 18, "123.456.789-09", 14, "NORMAL", "DARK", "START"));
        assertEquals("LABEL_NOT_INTERFACE_TEXT", assertThrows(VisualPolicyException.class, () -> SyntheticRenderer.render(new Description(640, 360, 1, bad)))
                .code());
        List<Region> bar = new ArrayList<>(LOGIN);
        bar.set(7, Region.text(180, 72, 130, 10, "DARK"));
        assertEquals("TEXT_NOT_QUANTIZED", assertThrows(VisualPolicyException.class, () -> SyntheticRenderer.render(new Description(640, 360, 1, bar))).code());
        List<Region> order = new ArrayList<>(LOGIN);
        order.add(0, order.remove(9));
        assertEquals("REGION_ORDER", assertThrows(VisualPolicyException.class, () -> SyntheticRenderer.render(new Description(640, 360, 1, order))).code());
        assertEquals("REGION_OUT_OF_VIEWPORT", assertThrows(VisualPolicyException.class,
                () -> SyntheticRenderer.render(new Description(400, 360, 1, LOGIN))).code());
        assertThrows(IllegalArgumentException.class, () -> new Region("TEXT", 0, 0, 16, 8, null, "Senha", 0, null, "DARK", null), "barra nunca leva texto");
        assertThrows(IllegalArgumentException.class, () -> Region.label(0, 0, 10, 10, "Senha", 14, "NORMAL", "#ff0000", "START"), "nunca a cor real");
    }

    @Test
    @DisplayName("rótulos sobrepostos: o posterior é descartado no desenho (verificação exige rótulos disjuntos)")
    void rotulosSobrepostos() throws Exception {
        List<Region> list = new ArrayList<>(LOGIN);
        list.add(Region.label(190, 110, 60, 18, "Salvar", 12, "NORMAL", "DARK", "START"));
        Rendered r = SyntheticRenderer.render(new Description(640, 360, 1, list));
        assertEquals(1, r.droppedLabels());
        VisualVerifier.verify(r.png(), r.sha256(), r.width(), r.height(), r.scale(), r.regions(), VisualVerifier.POLICY_V3);
    }

    @Test
    @DisplayName("viewport largo (> 2048 px CSS) desenha em escala 1")
    void escalaUm() throws Exception {
        Rendered r = SyntheticRenderer.render(new Description(2560, 300, 1, List.of(new Region("FIELD", 10, 10, 100, 20))));
        assertEquals(1, r.scale());
        assertEquals(2560, r.width());
        VisualVerifier.verify(r.png(), r.sha256(), r.width(), r.height(), 1, r.regions(), VisualVerifier.POLICY_V3);
    }

    @Test
    @DisplayName("rascunho: v3 grava 1.2.0 e relê igual; 1.1.0 com v3 e v2 com TEXT/LABEL são recusados; v2 sozinha continua 1.1.0")
    void rascunho12() throws Exception {
        Fixture f = ready();
        String json = DraftJson.write(f.draft());
        assertTrue(json.contains("\"version\": \"1.2.0\"") || json.contains("\"version\":\"1.2.0\""), json.substring(0, 120));
        assertTrue(json.contains("\"Entrar\""), "o rótulo (vocabulário) faz parte do rascunho");
        DocumentDraft back = DraftJson.read(json);
        assertEquals(DraftJson.write(back), json);
        assertThrows(IllegalArgumentException.class, () -> DraftJson.read(json.replace("1.2.0", "1.1.0")));
        // v3 só com regiões que o 1.1.0 também conhece: só a coerência versão↔política impede a leitura errada
        Fixture plain = ready(List.of(new Region("SURFACE", 0, 0, 640, 40, "DARK"), new Region("FIELD", 180, 130, 280, 28)));
        String plainJson = DraftJson.write(plain.draft());
        assertTrue(plainJson.contains("1.2.0"));
        assertThrows(IllegalArgumentException.class, () -> DraftJson.read(plainJson.replace("\"1.2.0\"", "\"1.1.0\"")), "1.1.0 com captura v3");
        assertThrows(IllegalArgumentException.class, () -> DraftJson.read(plainJson.replace("\"nekkora-visual/3\"", "\"nekkora-visual/2\"")), "1.2.0 sem v3");
        assertThrows(IllegalArgumentException.class, () -> new SafeCapture(null, "sha256:" + "a".repeat(64), 10, 10, 1, CLOCK.instant(), 0,
                VisualVerifier.POLICY_V2, "", List.of(), List.of(), List.of(), List.of(Region.text(0, 0, 16, 4, "DARK")), null));
        assertThrows(IllegalArgumentException.class, () -> new SafeCapture(null, "sha256:" + "a".repeat(64), 10, 10, 1, CLOCK.instant(), 0,
                VisualVerifier.POLICY_V3, "", List.of(), List.of(), List.of(), List.of(new Region("CARRIER", 0, 0, 4, 4)), null));
    }

    @Test
    @DisplayName("PDF v3: procedência sintética declarada, recorte e marcador manual mantidos, nada além do vocabulário vira texto")
    void pdfV3() throws Exception {
        Fixture f = ready();
        String blk = f.login().blockId();
        DocumentDraft d = DraftEdits.setCrop(f.draft(), blk, new Crop(320, 100, 700, 620, "DEVELOPER"));
        d = DraftEdits.placeManualMarker(d, blk, f.login().stepIds().get(2), 380 / 640.0, 280 / 360.0, 80 / 640.0, 32 / 360.0, "DEVELOPER");
        PddExporter.Exported e = PddExporter.export(d, f.projection(), f.store(), tmp.resolve("saida"), PddPdfTest.fonts(), CLOCK, null);
        try (PDDocument doc = Loader.loadPDF(e.pdf().toFile())) {
            String text = new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
            assertTrue(text.contains("nenhum pixel da página foi copiado"), text);
            assertTrue(text.contains("Não é uma captura de tela real"), text);
            assertTrue(text.contains("(marcador posicionado manualmente)"), text);
            for (String s : PddLab.SENTINELS) assertFalse(text.contains(s), "sentinela no PDF: " + s);
        }
        String manifest = Files.readString(e.manifest());
        assertTrue(manifest.contains("nekkora-visual/3"), manifest);
        // PNG trocado no disco depois da captura: a exportação recusa
        Path dir = tmp.resolve("dados");
        try (var files = Files.walk(dir)) {
            Path pngFile = files.filter(p -> p.toString().endsWith(".png")).findFirst().orElseThrow();
            BufferedImage img = ImageIO.read(pngFile.toFile());
            img.setRGB(5, 700, 0x123456);
            Files.write(pngFile, png(img));
        }
        DocumentDraft finalD = d;
        assertThrows(PddExporter.ExportException.class,
                () -> PddExporter.export(finalD, f.projection(), f.store(), tmp.resolve("saida2"), PddPdfTest.fonts(), CLOCK, null));
    }

    record Fixture(Automation automation, ProcessProjection projection, DocumentDraft draft, PddStore store, Block login) {}

    Fixture ready() throws Exception {
        return ready(LOGIN);
    }

    Fixture ready(List<Region> regions) throws Exception {
        Automation a = PddLab.consulta();
        ProcessProjection p = ProcessProjection.of(a);
        DocumentDraft d = DraftEdits.reviseSectionTitle(DraftBuilder.build(p), 1, "Autenticação", "TEST_AGENT");
        Block login = d.sections().get(0).blocks().get(0);
        d = DraftEdits.reviseBlockTitle(d, login.blockId(), "Realizar autenticação", "TEST_AGENT");
        PddStore store = new PddStore(tmp.resolve("dados"));
        Rendered r = SyntheticRenderer.render(new Description(640, 360, 1.25, regions));
        String sha = store.putCapture(d.automationId(), r.png());
        List<Annotation> anns = List.of(
                new Annotation(login.stepIds().get(0), Annotation.Status.PLACED, 180 / 640.0, 130 / 360.0, 280 / 640.0, 28 / 360.0, ""),
                new Annotation(login.stepIds().get(1), Annotation.Status.PLACED, 180 / 640.0, 200 / 360.0, 280 / 640.0, 28 / 360.0, ""),
                new Annotation(login.stepIds().get(2), Annotation.Status.OMITTED, 0, 0, 0, 0, "TARGET_AMBIGUOUS"));
        SafeCapture c = new SafeCapture("cap-" + "3".repeat(32), sha, r.width(), r.height(), r.scale(), CLOCK.instant(), 1, VisualVerifier.POLICY_V3,
                login.screenKey(), login.stepIds(), anns, List.of("TABLE_CONTENT_HIDDEN"), r.regions(), null);
        d = DraftEdits.attachCapture(d, login.blockId(), c);
        return new Fixture(a, p, d, store, d.block(login.blockId()).orElseThrow());
    }

    static BufferedImage copy(BufferedImage img) {
        BufferedImage c = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        c.getGraphics().drawImage(img, 0, 0, null);
        return c;
    }
}
