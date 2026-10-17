package com.nekkora.studio.pdd;

import com.nekkora.studio.pdd.DocumentDraft.Region;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * STUDIO-010 — QUARTA barreira da política visual {@code nekkora-visual/1} (o Studio não confia na extensão nem no host)
 * e verificação repetida antes de cada exportação (o arquivo no disco pode ter sido trocado).
 *
 * <p>Regra (a mesma do service worker e do host): PNG válido, dimensões declaradas, sha-256 declarado, tetos das regiões
 * e, pixel a pixel, fora dos retângulos de portadores SÓ as três cores sintéticas totalmente opacas: fundo opaco
 * {@code #D5D8DD}, preenchimento de campo {@code #FFFFFF} e borda de campo {@code #8A9099}.
 */
public final class VisualVerifier {
    public static final String POLICY = "nekkora-visual/1";
    public static final int OPAQUE = 0xD5D8DD;
    public static final int FIELD_FILL = 0xFFFFFF;
    public static final int FIELD_BORDER = 0x8A9099;
    public static final int MAX_PNG_BYTES = 150_000;
    static final int MAX_REGIONS = 64;
    static final int CARRIER_MAX_CSS_HEIGHT = 64;
    static final double CARRIER_MAX_WIDTH_FRACTION = 0.6;
    static final double CARRIERS_MAX_AREA_FRACTION = 0.2;

    private VisualVerifier() {}

    /** Imagem recusada (código estável; nunca um trecho da imagem). */
    public static final class VisualPolicyException extends Exception {
        private final String code;

        VisualPolicyException(String code) {
            super(code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** Verifica e devolve a imagem decodificada (para desenhar na prévia/PDF), ou lança com o código. */
    public static BufferedImage verify(byte[] png, String declaredSha, int width, int height, double dpr, List<Region> regions)
            throws VisualPolicyException {
        if (png == null || png.length == 0 || png.length > MAX_PNG_BYTES) throw new VisualPolicyException("SANITIZED_IMAGE_TOO_LARGE");
        if (declaredSha != null && !declaredSha.equals(Canonical.sha256(png))) throw new VisualPolicyException("CHECKSUM_MISMATCH");
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            throw new VisualPolicyException("IMAGE_UNREADABLE");
        }
        if (img == null) throw new VisualPolicyException("IMAGE_UNREADABLE");
        if (img.getWidth() != width || img.getHeight() != height || !(dpr > 0 && dpr <= 4)) throw new VisualPolicyException("GEOMETRY_MISMATCH");
        int carriers = 0;
        int fields = 0;
        long area = 0;
        for (Region r : regions) {
            if (r.x() + r.width() > width || r.y() + r.height() > height) throw new VisualPolicyException("REGION_OUT_OF_IMAGE");
            if ("CARRIER".equals(r.kind())) {
                if (r.height() > Math.ceil(CARRIER_MAX_CSS_HEIGHT * dpr) || r.width() > Math.ceil(width * CARRIER_MAX_WIDTH_FRACTION)) {
                    throw new VisualPolicyException("CARRIER_TOO_LARGE");
                }
                carriers++;
                area += (long) r.width() * r.height();
            } else {
                fields++;
            }
        }
        if (carriers > MAX_REGIONS || fields > MAX_REGIONS || area > (long) ((long) width * height * CARRIERS_MAX_AREA_FRACTION)) {
            throw new VisualPolicyException("CARRIER_TOO_LARGE");
        }
        if (violations(img, regions) != 0) throw new VisualPolicyException("SANITIZATION_CHECK_FAILED");
        return img;
    }

    /** Pixels fora dos portadores que não são uma das três cores sintéticas opacas. */
    public static long violations(BufferedImage img, List<Region> regions) {
        int w = img.getWidth();
        int h = img.getHeight();
        boolean[] in = new boolean[w * h];
        for (Region r : regions) {
            if (!"CARRIER".equals(r.kind())) continue;
            for (int y = r.y(); y < r.y() + r.height(); y++) {
                for (int x = r.x(); x < r.x() + r.width(); x++) in[y * w + x] = true;
            }
        }
        boolean alpha = img.getColorModel().hasAlpha();
        long bad = 0;
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            img.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                if (in[y * w + x]) continue;
                int argb = row[x];
                int rgb = argb & 0xFFFFFF;
                if ((alpha && (argb >>> 24) != 0xFF) || (rgb != OPAQUE && rgb != FIELD_FILL && rgb != FIELD_BORDER)) bad++;
            }
        }
        return bad;
    }
}
