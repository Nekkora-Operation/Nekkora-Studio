package com.nekkora.studio.pdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.pdd.DocumentDraft.Annotation;
import com.nekkora.studio.pdd.DocumentDraft.Block;
import com.nekkora.studio.pdd.DocumentDraft.Crop;
import com.nekkora.studio.pdd.DocumentDraft.Region;
import com.nekkora.studio.pdd.DocumentDraft.SafeCapture;
import com.nekkora.studio.pdd.DraftEdits.DraftException;
import com.nekkora.studio.pdd.PddExporter.ExportException;
import com.nekkora.studio.pdd.VisualVerifier.VisualPolicyException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * STUDIO-012 PDD-01 — representação documental ({@code nekkora-visual/2}) no Studio: 4ª barreira, rascunho 1.1.0 (e o
 * 1.0.0 intacto), referência própria por captura, recorte, marcadores manuais, PDF e manifesto. Dados sintéticos.
 */
class PddVisualV2Test {
    @TempDir
    Path tmp;
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC);
    static final int W = 1280;
    static final int H = 720;

    /** Login redesenhado: faixa escura, cartão claro, logotipo, dois campos, dois rótulos e o botão Entrar (portador). */
    static final List<Region> LOGIN_V2 = List.of(new Region("SURFACE", 0, 0, 1280, 80, "DARK"), new Region("SURFACE", 360, 100, 560, 520, "LIGHT"),
            new Region("MEDIA", 380, 110, 120, 40), new Region("FIELD", 440, 250, 400, 30), new Region("FIELD", 440, 330, 400, 30),
            new Region("CARRIER", 440, 226, 70, 20), new Region("CARRIER", 440, 306, 60, 20), new Region("CARRIER", 760, 400, 80, 28));

    /** Pinta como a extensão: página, sintéticas na ordem e "pixels reais" nos portadores (botão escuro, rótulos claros). */
    static BufferedImage paint(List<Region> regions) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) img.setRGB(x, y, VisualVerifier.V2_PAGE);
        for (Region r : regions) {
            for (int y = r.y(); y < r.y() + r.height(); y++) {
                for (int x = r.x(); x < r.x() + r.width(); x++) {
                    if ("CARRIER".equals(r.kind())) {
                        img.setRGB(x, y, r.width() == 80 ? 0x333333 : 0xEFEFEF);
                        continue;
                    }
                    int[] fb = VisualVerifier.v2Paint(r.kind(), r.tone());
                    boolean edge = y == r.y() || y == r.y() + r.height() - 1 || x == r.x() || x == r.x() + r.width() - 1;
                    img.setRGB(x, y, edge ? fb[1] : fb[0]);
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

    record Fixture(Automation automation, ProcessProjection projection, DocumentDraft draft, PddStore store, Block login) {}

    Fixture ready(String policy) throws Exception {
        Automation a = PddLab.consulta();
        ProcessProjection p = ProcessProjection.of(a);
        DocumentDraft d = DraftEdits.reviseSectionTitle(DraftBuilder.build(p), 1, "Autenticação", "TEST_AGENT");
        Block login = d.sections().get(0).blocks().get(0);
        d = DraftEdits.reviseBlockTitle(d, login.blockId(), "Realizar autenticação", "TEST_AGENT");
        PddStore store = new PddStore(tmp.resolve("dados"));
        boolean v2 = VisualVerifier.POLICY_V2.equals(policy);
        byte[] bytes = v2 ? png(paint(LOGIN_V2)) : png(PddPdfTest.loginImage());
        String sha = store.putCapture(d.automationId(), bytes);
        List<Annotation> anns = List.of(
                new Annotation(login.stepIds().get(0), Annotation.Status.PLACED, 440 / 1280.0, 250 / 720.0, 400 / 1280.0, 30 / 720.0, ""),
                new Annotation(login.stepIds().get(1), Annotation.Status.PLACED, 440 / 1280.0, 330 / 720.0, 400 / 1280.0, 30 / 720.0, ""),
                new Annotation(login.stepIds().get(2), Annotation.Status.OMITTED, 0, 0, 0, 0, "TARGET_AMBIGUOUS"));
        SafeCapture c = v2
                ? new SafeCapture("cap-" + "1".repeat(32), sha, W, H, 1.0, CLOCK.instant(), 1, policy, login.screenKey(), login.stepIds(), anns, List.of(),
                        LOGIN_V2, null)
                : new SafeCapture(sha, W, H, 1.0, CLOCK.instant(), 1, policy, login.screenKey(), login.stepIds(), anns, List.of(), PddPdfTest.LOGIN_REGIONS);
        d = DraftEdits.attachCapture(d, login.blockId(), c);
        return new Fixture(a, p, d, store, d.block(login.blockId()).orElseThrow());
    }

    // ------------------------------------------------------------------------------------------- 4ª barreira

    @Test
    @DisplayName("v2: representação conforme passa; pixel real fora do portador, cinza da v1, tom trocado, ordem trocada, portador sobre frame e política desconhecida são recusados")
    void quartaBarreiraV2() throws Exception {
        byte[] ok = png(paint(LOGIN_V2));
        assertEquals(W, VisualVerifier.verify(ok, Canonical.sha256(ok), W, H, 1.0, LOGIN_V2, VisualVerifier.POLICY_V2).getWidth());
        BufferedImage leak = paint(LOGIN_V2);
        leak.setRGB(600, 500, 0xE6007E); // dentro do cartão
        assertEquals("SANITIZATION_CHECK_FAILED", code(png(leak), LOGIN_V2, VisualVerifier.POLICY_V2));
        BufferedImage gray = paint(LOGIN_V2);
        gray.setRGB(5, 700, VisualVerifier.OPAQUE);
        assertEquals("SANITIZATION_CHECK_FAILED", code(png(gray), LOGIN_V2, VisualVerifier.POLICY_V2));
        List<Region> tone = new ArrayList<>(LOGIN_V2);
        tone.set(0, new Region("SURFACE", 0, 0, 1280, 80, "LIGHT"));
        assertEquals("SANITIZATION_CHECK_FAILED", code(ok, tone, VisualVerifier.POLICY_V2));
        List<Region> order = new ArrayList<>(LOGIN_V2);
        order.add(0, order.remove(3));
        assertEquals("REGION_ORDER", code(png(paint(order)), order, VisualVerifier.POLICY_V2));
        List<Region> frame = new ArrayList<>(LOGIN_V2);
        frame.add(3, new Region("FRAME", 740, 390, 200, 60));
        assertEquals("CARRIER_OVERLAPS_CONTENT", code(png(paint(frame)), frame, VisualVerifier.POLICY_V2));
        assertEquals("POLICY_UNKNOWN", code(ok, LOGIN_V2, "nekkora-visual/9"));
        assertEquals("REGION_KIND_UNKNOWN", code(ok, LOGIN_V2, VisualVerifier.POLICY), "região da v2 declarada como v1");
    }

    private static String code(byte[] png, List<Region> regions, String policy) {
        return assertThrows(VisualPolicyException.class, () -> VisualVerifier.verify(png, Canonical.sha256(png), W, H, 1.0, regions, policy)).code();
    }

    // ------------------------------------------------------------------------------------------- rascunho

    @Test
    @DisplayName("compatibilidade: rascunho só com v1 continua 1.0.0 (sem campos novos, ida e volta igual); v2/recorte/manual sobem para 1.1.0 e voltam iguais")
    void versoesDoRascunho() throws Exception {
        Fixture v1 = ready(VisualVerifier.POLICY);
        String j1 = DraftJson.write(v1.draft());
        assertTrue(j1.contains("\"version\": \"1.0.0\""));
        for (String k : List.of("\"ref\"", "\"crop\"", "\"provenance\"", "\"tone\"")) assertFalse(j1.contains(k), "1.0.0 sem " + k);
        assertEquals(j1, DraftJson.write(DraftJson.read(j1)));
        Fixture v2 = ready(VisualVerifier.POLICY_V2);
        DocumentDraft d = DraftEdits.setCrop(v2.draft(), v2.login().blockId(), new Crop(360, 80, 600, 400, "TEST_AGENT"));
        d = DraftEdits.placeManualMarker(d, v2.login().blockId(), v2.login().stepIds().get(2), 760 / 1280.0, 400 / 720.0, 80 / 1280.0, 28 / 720.0,
                "TEST_AGENT");
        String j2 = DraftJson.write(d);
        assertTrue(j2.contains("\"version\": \"1.1.0\"") && j2.contains("\"ref\": \"cap-") && j2.contains("\"tone\": \"DARK\"")
                && j2.contains("\"provenance\": \"MANUAL\"") && j2.contains("\"crop\": {"), j2);
        DocumentDraft back = DraftJson.read(j2);
        assertEquals(j2, DraftJson.write(back), "ida e volta sem perda");
        assertEquals(d.revision(), back.revision());
        // leitura estrita
        assertThrows(IllegalArgumentException.class, () -> DraftJson.read(j1.replace("\"kind\": \"FIELD\"", "\"kind\": \"FIELD\", \"tone\": \"MID\"")),
                "1.0.0 com campo do 1.1.0");
        assertThrows(IllegalArgumentException.class, () -> DraftJson.read(j2.replace("\"ref\": \"cap-", "\"xref\": \"cap-")), "1.1.0 sem ref");
        assertThrows(IllegalArgumentException.class, () -> DraftJson.read(j2.replace("\"reviewer\": \"TEST_AGENT\"\n      }", "\"reviewer\": \"HUMANO\"\n      }")),
                "revisor fora do vocabulário");
        assertThrows(IllegalArgumentException.class, () -> DraftJson.read(j2.replace("\"tone\": \"DARK\"", "\"tone\": \"GREEN\"")), "tom fora da paleta");
        assertThrows(IllegalArgumentException.class, () -> DraftJson.read(j2.replace("\"width\": 600", "\"width\": 6000")), "recorte fora da imagem");
        String v2in100 = j2.replace("\"version\": \"1.1.0\"", "\"version\": \"1.0.0\"");
        assertThrows(IllegalArgumentException.class, () -> DraftJson.read(v2in100), "1.0.0 não carrega v2");
    }

    @Test
    @DisplayName("a mesma imagem em dois blocos não colide mais (referência própria); cada bloco segue sem CAPTURE_MISMATCH")
    void mesmaImagemEmDoisBlocos() throws Exception {
        Fixture f = ready(VisualVerifier.POLICY);
        DocumentDraft d = DraftEdits.split(f.draft(), f.projection(), f.login().blockId(), 2);
        Block a = d.sections().get(0).blocks().get(0);
        Block b = d.sections().get(0).blocks().get(1);
        byte[] bytes = png(PddPdfTest.loginImage());
        String sha = f.store().putCapture(d.automationId(), bytes);
        d = DraftEdits.attachCapture(d, a.blockId(), new SafeCapture(sha, W, H, 1.0, CLOCK.instant(), 1, VisualVerifier.POLICY, a.screenKey(), a.stepIds(),
                List.of(), List.of(), PddPdfTest.LOGIN_REGIONS));
        d = DraftEdits.attachCapture(d, b.blockId(), new SafeCapture(sha, W, H, 1.0, CLOCK.instant(), 1, VisualVerifier.POLICY, b.screenKey(), b.stepIds(),
                List.of(), List.of(), PddPdfTest.LOGIN_REGIONS));
        String ra = d.block(a.blockId()).orElseThrow().captureSha();
        String rb = d.block(b.blockId()).orElseThrow().captureSha();
        assertNotEquals(ra, rb);
        assertEquals(sha, ra, "o primeiro mantém a forma 1.0.0");
        assertTrue(rb.startsWith("cap-"));
        assertEquals(sha, d.captures().get(rb).sha256(), "mesmo PNG");
        assertTrue(d.pending(f.projection()).stream().noneMatch(p -> p.code().equals("CAPTURE_MISMATCH")), String.valueOf(d.pending(f.projection())));
        assertEquals(Set.of(sha), d.referencedImages());
        assertEquals(DraftJson.write(d), DraftJson.write(DraftJson.read(DraftJson.write(d))));
    }

    @Test
    @DisplayName("marcador manual: só sem marcador automático, dentro da imagem e do recorte, MANUAL+revisor; substitui o anterior; removível; recorte novo descarta os de fora")
    void marcadoresManuais() throws Exception {
        Fixture f = ready(VisualVerifier.POLICY_V2);
        String blk = f.login().blockId();
        StepId usuario = f.login().stepIds().get(0);
        StepId entrar = f.login().stepIds().get(2);
        assertEquals("MARKER_ALREADY_PLACED", assertThrows(DraftException.class,
                () -> DraftEdits.placeManualMarker(f.draft(), blk, usuario, 0.1, 0.1, 0.1, 0.1, "DEVELOPER")).code(), "o automático nunca é sobrescrito");
        assertEquals("STEP_NOT_IN_BLOCK", assertThrows(DraftException.class,
                () -> DraftEdits.placeManualMarker(f.draft(), blk, new StepId(java.util.UUID.randomUUID()), 0.1, 0.1, 0.1, 0.1, "DEVELOPER")).code());
        assertEquals("MARKER_INVALID", assertThrows(DraftException.class,
                () -> DraftEdits.placeManualMarker(f.draft(), blk, entrar, 0.95, 0.1, 0.2, 0.1, "DEVELOPER")).code(), "fora da imagem");
        assertEquals("MARKER_INVALID", assertThrows(DraftException.class,
                () -> DraftEdits.placeManualMarker(f.draft(), blk, entrar, 0.5, 0.5, 0.1, 0.1, "HUMANO")).code(), "revisor fora do vocabulário");
        assertTrue(f.draft().pending(f.projection()).stream().anyMatch(p -> p.code().equals("MARKER_UNAVAILABLE") && p.instruction() == 3));
        DocumentDraft d0 = DraftEdits.placeManualMarker(f.draft(), blk, entrar, 760 / 1280.0, 400 / 720.0, 80 / 1280.0, 28 / 720.0, "DEVELOPER");
        DocumentDraft d = DraftEdits.placeManualMarker(d0, blk, entrar, 750 / 1280.0, 396 / 720.0, 100 / 1280.0, 36 / 720.0, "TEST_AGENT");
        SafeCapture c = d.captures().get(d.block(blk).orElseThrow().captureSha());
        List<Annotation> manual = c.annotations().stream().filter(Annotation::manual).toList();
        assertEquals(1, manual.size(), "o manual anterior foi substituído");
        assertEquals("TEST_AGENT", manual.get(0).reviewer());
        assertTrue(c.annotations().stream().anyMatch(a -> a.stepId().equals(entrar) && !a.manual() && a.status() == Annotation.Status.OMITTED),
                "o motivo automático (OMITTED) continua registrado");
        assertTrue(d.pending(f.projection()).stream().noneMatch(p -> p.code().equals("MARKER_UNAVAILABLE")));
        assertEquals("MARKER_OUTSIDE_CROP", assertThrows(DraftException.class, () -> DraftEdits.placeManualMarker(
                DraftEdits.setCrop(f.draft(), blk, new Crop(360, 200, 500, 200, "DEVELOPER")), blk, entrar, 760 / 1280.0, 400 / 720.0, 80 / 1280.0,
                28 / 720.0, "DEVELOPER")).code());
        DocumentDraft cropped = DraftEdits.setCrop(d, blk, new Crop(360, 200, 300, 90, "DEVELOPER")); // deixa o botão e a senha de fora
        SafeCapture cc = cropped.captures().get(cropped.block(blk).orElseThrow().captureSha());
        assertTrue(cc.annotations().stream().noneMatch(Annotation::manual), "manual fora do recorte novo é descartado");
        assertTrue(cropped.pending(f.projection()).stream().anyMatch(p -> p.code().equals("MARKER_OUTSIDE_CROP") && !p.blocking()),
                "automático cortado é avisado: " + cropped.pending(f.projection()));
        assertEquals("CROP_OUT_OF_IMAGE", assertThrows(DraftException.class, () -> DraftEdits.setCrop(d, blk, new Crop(1000, 0, 400, 100, "DEVELOPER"))).code());
        assertThrows(IllegalArgumentException.class, () -> new Crop(0, 0, 20, 20, "DEVELOPER"), "recorte menor que o mínimo");
        DocumentDraft removed = DraftEdits.removeManualMarker(d, blk, entrar);
        assertTrue(removed.pending(f.projection()).stream().anyMatch(p -> p.code().equals("MARKER_UNAVAILABLE")));
        assertEquals("MARKER_NOT_FOUND", assertThrows(DraftException.class, () -> DraftEdits.removeManualMarker(removed, blk, entrar)).code());
        // nada disso mexe na automação
        assertEquals(f.projection().revision(), ProcessProjection.of(f.automation()).revision());
    }

    @Test
    @DisplayName("captura nova substitui só o bloco escolhido e leva embora recorte e marcadores manuais da anterior; bloco alterado na automação perde tudo")
    void substituicaoEInvalidacao() throws Exception {
        Fixture f = ready(VisualVerifier.POLICY_V2);
        String blk = f.login().blockId();
        DocumentDraft d = DraftEdits.placeManualMarker(DraftEdits.setCrop(f.draft(), blk, new Crop(360, 80, 600, 400, "DEVELOPER")), blk,
                f.login().stepIds().get(2), 760 / 1280.0, 400 / 720.0, 80 / 1280.0, 28 / 720.0, "DEVELOPER");
        List<Region> alt = new ArrayList<>(LOGIN_V2);
        alt.set(1, new Region("SURFACE", 360, 100, 560, 510, "LIGHT")); // a tela mudou um pouco: outra imagem
        byte[] bytes = png(paint(alt));
        String sha = f.store().putCapture(d.automationId(), bytes);
        SafeCapture fresh = new SafeCapture(null, sha, W, H, 1.0, CLOCK.instant().plusSeconds(60), 2, VisualVerifier.POLICY_V2, f.login().screenKey(),
                f.login().stepIds(), List.of(), List.of(), alt, null);
        fresh = DraftEdits.withRef(fresh, DraftEdits.freshRef(fresh, blk));
        DocumentDraft replaced = DraftEdits.dropUnreferencedCaptures(DraftEdits.attachCapture(d, blk, fresh));
        SafeCapture now = replaced.captures().get(replaced.block(blk).orElseThrow().captureSha());
        assertEquals(sha, now.sha256());
        assertEquals(null, now.crop());
        assertTrue(now.annotations().stream().noneMatch(Annotation::manual));
        assertEquals(1, replaced.captures().size(), "a anterior saiu do rascunho");
        // os outros blocos não mudam
        for (Block b : d.blocks()) if (!b.blockId().equals(blk)) assertEquals(b, replaced.block(b.blockId()).orElseThrow());
        // automação muda no bloco → captura (e o que ela carrega) não volta
        Automation a = f.automation();
        a.removeStep(f.login().stepIds().get(2));
        DocumentDraft refreshed = DraftEdits.refresh(d, f.projection(), ProcessProjection.of(a));
        assertTrue(refreshed.blocks().stream().allMatch(b -> b.capture().isEmpty() || !b.stepIds().contains(f.login().stepIds().get(2))));
    }

    @Test
    @DisplayName("retenção segura para desfazer: a imagem que o histórico ainda usa fica no disco; a que ninguém usa sai; a chave é o PNG, não a referência")
    void retencao() throws Exception {
        Fixture f = ready(VisualVerifier.POLICY_V2);
        String sha = f.draft().captures().values().iterator().next().sha256();
        DocumentDraft removed = DraftEdits.dropUnreferencedCaptures(DraftEdits.detachCapture(f.draft(), f.login().blockId()));
        assertEquals(0, f.store().retain(f.draft()), "1.1.0: a referência cap-… aponta para o PNG certo");
        assertTrue(f.store().capture(f.draft().automationId(), sha).isPresent());
        assertEquals(0, f.store().retain(removed, f.draft().referencedImages()), "o desfazer ainda precisa dela");
        assertTrue(f.store().capture(f.draft().automationId(), sha).isPresent());
        assertEquals(1, f.store().retain(removed, Set.of()));
        assertTrue(f.store().capture(f.draft().automationId(), sha).isEmpty());
    }

    // ------------------------------------------------------------------------------------------- PDF

    @Test
    @DisplayName("PDF v2: texto de procedência novo, recorte exato, marcador manual declarado, imagem clara (sem o cinza da v1); manifesto 1.1.0")
    void pdfV2() throws Exception {
        Fixture f = ready(VisualVerifier.POLICY_V2);
        String blk = f.login().blockId();
        DocumentDraft d = DraftEdits.setCrop(f.draft(), blk, new Crop(360, 80, 600, 400, "DEVELOPER"));
        d = DraftEdits.placeManualMarker(d, blk, f.login().stepIds().get(2), 760 / 1280.0, 400 / 720.0, 80 / 1280.0, 28 / 720.0, "DEVELOPER");
        PddExporter.Exported e = PddExporter.export(d, f.projection(), f.store(), tmp.resolve("saida"), PddPdfTest.fonts(), CLOCK, null);
        try (PDDocument doc = Loader.loadPDF(e.pdf().toFile())) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.replaceAll("\\s+", " ").contains("representações sanitizadas da tela"), text);
            assertTrue(text.contains("(marcador posicionado manualmente)"), text);
            assertTrue(text.replaceAll("\\s+", " ").contains("não comprovam o elemento"), "nota dos marcadores manuais");
            assertFalse(text.contains("(marcador indisponível)"), "o manual supre o automático ausente");
            for (String s : PddLab.SENTINELS) assertFalse(text.contains(s), "sentinela no PDF: " + s);
            PDImageXObject img = null;
            for (COSName n : doc.getPage(0).getResources().getXObjectNames()) {
                if (doc.getPage(0).getResources().getXObject(n) instanceof PDImageXObject x) img = x;
            }
            assertEquals(600, img.getWidth(), "recorte exato");
            assertEquals(400, img.getHeight());
            BufferedImage page = new PDFRenderer(doc).renderImageWithDPI(0, 96);
            ImageIO.write(page, "png", Path.of("target", "pdd-v2-pagina-1.png").toFile()); // inspeção visual (dados sintéticos)
            assertEquals(0, exactV1Gray(img.getImage()), "o cinza de ocultação da v1 não existe na imagem do PDF");
        }
        String manifest = Files.readString(e.manifest());
        assertTrue(manifest.contains("\"version\": \"1.1.0\"") && manifest.contains("\"visualPolicies\": [\"nekkora-visual/2\"]")
                && manifest.contains("\"markersManual\": 1") && !manifest.contains("cap-"), manifest);
    }

    @Test
    @DisplayName("antes × depois no PDF: v1 = imagem cinza e botão sem rótulo; v2 = imagem clara e botão legível; v1 continua exportando como antes (manifesto 1.0.0)")
    void antesEDepois() throws Exception {
        Fixture v1 = ready(VisualVerifier.POLICY);
        PddExporter.Exported e1 = PddExporter.export(v1.draft(), v1.projection(), v1.store(), tmp.resolve("antes"), PddPdfTest.fonts(), CLOCK, null);
        Fixture v2 = ready(VisualVerifier.POLICY_V2);
        PddExporter.Exported e2 = PddExporter.export(v2.draft(), v2.projection(), v2.store(), tmp.resolve("depois"), PddPdfTest.fonts(), CLOCK, null);
        double gray1, gray2;
        try (PDDocument a = Loader.loadPDF(e1.pdf().toFile()); PDDocument b = Loader.loadPDF(e2.pdf().toFile())) {
            BufferedImage i1 = image(a).getImage();
            BufferedImage i2 = image(b).getImage();
            gray1 = exactV1Gray(i1) / (double) (i1.getWidth() * i1.getHeight());
            gray2 = exactV1Gray(i2) / (double) (i2.getWidth() * i2.getHeight());
            assertTrue(new PDFTextStripper().getText(a).replaceAll("\\s+", " ").contains("campos e o restante da tela foram ocultados"), "v1: texto de antes");
            assertFalse(new PDFTextStripper().getText(b).contains("tracejados"), "sem marcador manual, sem a nota");
        }
        assertTrue(gray1 > 0.5, "v1: a imagem do PDF é quase toda cinza (" + gray1 + ")");
        assertEquals(0.0, gray2, "v2: nenhum pixel do cinza de ocultação");
        String m1 = Files.readString(e1.manifest());
        assertTrue(m1.contains("\"version\": \"1.0.0\"") && m1.contains("\"visualPolicy\": \"nekkora-visual/1\"") && !m1.contains("visualPolicies"), m1);
    }

    @Test
    @DisplayName("imagem v2 adulterada no disco (pixel real no cartão) → exportação recusada pela 4ª barreira; nada com nome de versão")
    void adulteradaNoDisco() throws Exception {
        Fixture f = ready(VisualVerifier.POLICY_V2);
        SafeCapture c = f.draft().captures().values().iterator().next();
        Path file = tmp.resolve("dados").resolve("pdd").resolve(f.draft().automationId().value().toString()).resolve("capturas")
                .resolve(c.sha256().substring(7) + ".png");
        BufferedImage img = ImageIO.read(file.toFile());
        img.setRGB(600, 500, 0xE6007E);
        ImageIO.write(img, "png", file.toFile());
        Path out = tmp.resolve("saida");
        assertEquals("CAPTURE_REJECTED_CHECKSUM_MISMATCH", assertThrows(ExportException.class,
                () -> PddExporter.export(f.draft(), f.projection(), f.store(), out, PddPdfTest.fonts(), CLOCK, null)).code());
        assertFalse(Files.exists(out.resolve("pdd-visual-v001.pdf")));
    }

    /** Pixels exatamente no cinza de ocultação #D5D8DD (na imagem embutida no PDF, sem a suavização da página). */
    static long exactV1Gray(BufferedImage img) {
        long n = 0;
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) if ((img.getRGB(x, y) & 0xFFFFFF) == VisualVerifier.OPAQUE) n++;
        return n;
    }

    /** A (única) imagem da primeira página. */
    static PDImageXObject image(PDDocument doc) throws Exception {
        for (COSName n : doc.getPage(0).getResources().getXObjectNames()) {
            if (doc.getPage(0).getResources().getXObject(n) instanceof PDImageXObject x) return x;
        }
        throw new AssertionError("sem imagem");
    }
}
