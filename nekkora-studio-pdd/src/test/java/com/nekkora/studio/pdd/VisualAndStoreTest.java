package com.nekkora.studio.pdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.pdd.DocumentDraft.Block;
import com.nekkora.studio.pdd.DocumentDraft.Region;
import com.nekkora.studio.pdd.VisualVerifier.VisualPolicyException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** STUDIO-010 G4 (4ª barreira no Studio) e armazenamento auxiliar do rascunho. */
class VisualAndStoreTest {
    @TempDir
    Path tmp;

    static final List<Region> REGIONS = List.of(new Region("CARRIER", 10, 10, 40, 20), new Region("FIELD", 60, 10, 80, 20));

    static BufferedImage conforming() {
        BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 100; y++) for (int x = 0; x < 200; x++) img.setRGB(x, y, VisualVerifier.OPAQUE);
        for (int y = 10; y < 30; y++) for (int x = 10; x < 50; x++) img.setRGB(x, y, 0x1F2328);
        for (int y = 10; y < 30; y++) {
            for (int x = 60; x < 140; x++) img.setRGB(x, y, y == 10 || y == 29 || x == 60 || x == 139 ? VisualVerifier.FIELD_BORDER : VisualVerifier.FIELD_FILL);
        }
        return img;
    }

    static byte[] png(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("4ª barreira: imagem conforme passa; 1 pixel fora da regra, sha divergente, portador grande e dimensões erradas são recusados")
    void quartaBarreira() throws Exception {
        byte[] ok = png(conforming());
        assertEquals(200, VisualVerifier.verify(ok, Canonical.sha256(ok), 200, 100, 1.0, REGIONS).getWidth());
        BufferedImage leak = conforming();
        leak.setRGB(150, 70, 0xE6007E);
        byte[] bad = png(leak);
        assertEquals("SANITIZATION_CHECK_FAILED", assertThrows(VisualPolicyException.class,
                () -> VisualVerifier.verify(bad, Canonical.sha256(bad), 200, 100, 1.0, REGIONS)).code());
        assertEquals("CHECKSUM_MISMATCH", assertThrows(VisualPolicyException.class,
                () -> VisualVerifier.verify(ok, "sha256:" + "0".repeat(64), 200, 100, 1.0, REGIONS)).code());
        assertEquals("CARRIER_TOO_LARGE", assertThrows(VisualPolicyException.class,
                () -> VisualVerifier.verify(ok, null, 200, 100, 1.0, List.of(new Region("CARRIER", 0, 0, 150, 20)))).code());
        assertEquals("GEOMETRY_MISMATCH", assertThrows(VisualPolicyException.class, () -> VisualVerifier.verify(ok, null, 201, 100, 1.0, REGIONS)).code());
        assertEquals("IMAGE_UNREADABLE", assertThrows(VisualPolicyException.class,
                () -> VisualVerifier.verify(new byte[] {1, 2, 3}, null, 200, 100, 1.0, REGIONS)).code());
    }

    @Test
    @DisplayName("armazenamento: rascunho volta igual; nome do arquivo é só o sha; retenção apaga o que o rascunho não usa; descartar apaga tudo")
    void armazenamento() throws Exception {
        ProcessProjection p = ProcessProjection.of(PddLab.consulta());
        DocumentDraft d = DraftBuilder.build(p);
        PddStore store = new PddStore(tmp);
        AutomationId id = d.automationId();
        byte[] img = png(conforming());
        String sha = store.putCapture(id, img);
        String orphan = store.putCapture(id, png(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)));
        Block login = d.sections().get(0).blocks().get(0);
        DocumentDraft.SafeCapture c = new DocumentDraft.SafeCapture(sha, 200, 100, 1.0, java.time.Instant.parse("2026-10-17T12:00:00Z"), 1, "nekkora-visual/1",
                login.screenKey(), login.stepIds(), List.of(), List.of(), REGIONS);
        DocumentDraft w = DraftEdits.attachCapture(d, login.blockId(), c);
        store.saveDraft(w);
        assertEquals(DraftJson.write(w), DraftJson.write(store.loadDraft(id).orElseThrow()));
        try (var files = Files.walk(store.folder(id))) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                String n = f.getFileName().toString();
                assertTrue(n.equals("rascunho.json") || n.matches("[0-9a-f]{64}\\.png"), "nome sem dado: " + n);
            }
        }
        assertEquals(1, store.retain(w), "a imagem órfã sai");
        assertTrue(store.capture(id, sha).isPresent());
        assertFalse(store.capture(id, orphan).isPresent());
        String json = Files.readString(store.folder(id).resolve("rascunho.json"));
        for (String s : PddLab.SENTINELS) assertFalse(json.contains(s), s);
        store.discard(id);
        assertFalse(Files.exists(store.folder(id)));
    }
}
