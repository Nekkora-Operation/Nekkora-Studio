package com.nekkora.studio.pdd;

import com.nekkora.studio.pdd.DocumentDraft.Region;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * STUDIO-010 — QUARTA barreira da política visual {@code nekkora-visual/1} (o Studio não confia na extensão nem no host)
 * e verificação repetida antes de cada exportação (o arquivo no disco pode ter sido trocado).
 *
 * <p>Regra (a mesma do service worker e do host): PNG válido, dimensões declaradas, sha-256 declarado, tetos das regiões
 * e, pixel a pixel, fora dos retângulos de portadores SÓ as três cores sintéticas totalmente opacas: fundo opaco
 * {@code #D5D8DD}, preenchimento de campo {@code #FFFFFF} e borda de campo {@code #8A9099}.
 *
 * <p>STUDIO-012 PDD-01 — {@code nekkora-visual/2} (representação documental): o Studio REPINTA a tela esperada a partir das
 * regiões declaradas (página clara, superfícies por tom, mídia, frames, controles e campos, nessa ordem) e exige que cada
 * pixel fora dos portadores seja exatamente a cor esperada naquele ponto. As cores e a ordem são as mesmas do service
 * worker e do Native Host.
 *
 * <p>NEKKORA-STUDIO-013 — {@code nekkora-visual/3} (inteiramente sintética): a imagem é desenhada pelo próprio Studio
 * ({@link SyntheticRenderer}) a partir da descrição; esta barreira volta a valer no disco antes de cada exportação. Regra:
 * regiões na ordem e nos tetos da v3, geometria em múltiplos da escala, barras quantizadas, rótulos com texto INTEIRO do
 * vocabulário ({@link com.nekkora.studio.domain.privacy.CapturePrivacy#interfaceText}) e disjuntos; fora dos rótulos cada
 * pixel é exatamente a cor sintética esperada; dentro de um rótulo, cada pixel é uma MISTURA entre o fundo esperado naquele
 * ponto e a tinta do rótulo (o que o texto em tons de cinza produz) — nenhuma outra cor cabe ali.
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
    public static final String POLICY_V2 = "nekkora-visual/2";
    public static final int V2_PAGE = 0xFFFFFF;
    static final Map<String, Integer> V2_TONES = Map.of("LIGHT", 0xF4F6F8, "MID", 0xE6EAEE, "DARK", 0x5B6675);
    static final int V2_SURFACE_BORDER = 0xC9D1D9;
    static final int V2_MEDIA_FILL = 0xEDEFF2;
    static final int V2_FRAME_FILL = 0xF4F6F8;
    static final int V2_CONTROL_FILL = 0xE6EAEE;
    static final Map<String, Integer> V2_MAX = Map.of("SURFACE", 48, "MEDIA", 16, "FRAME", 16, "CONTROL", 32, "FIELD", MAX_REGIONS, "CARRIER", MAX_REGIONS);

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

    /** Verifica pela política da captura ({@code nekkora-visual/1} ou {@code /2}) e devolve a imagem decodificada. */
    public static BufferedImage verify(byte[] png, String declaredSha, int width, int height, double dpr, List<Region> regions, String policy)
            throws VisualPolicyException {
        if (POLICY_V3.equals(policy)) return verifyV3(png, declaredSha, width, height, dpr, regions);
        if (POLICY_V2.equals(policy)) return verifyV2(png, declaredSha, width, height, dpr, regions);
        if (!POLICY.equals(policy)) throw new VisualPolicyException("POLICY_UNKNOWN");
        return verify(png, declaredSha, width, height, dpr, regions);
    }

    /** Verifica (política {@code nekkora-visual/1}) e devolve a imagem decodificada (para desenhar na prévia/PDF), ou lança com o código. */
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
            if (!"CARRIER".equals(r.kind()) && !"FIELD".equals(r.kind())) throw new VisualPolicyException("REGION_KIND_UNKNOWN");
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

    private static BufferedImage decode(byte[] png, String declaredSha, int width, int height, double dpr) throws VisualPolicyException {
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
        return img;
    }

    /** v2: tetos, ordem de pintura, tom, sobreposições e repintura pixel a pixel. */
    static BufferedImage verifyV2(byte[] png, String declaredSha, int width, int height, double dpr, List<Region> regions) throws VisualPolicyException {
        BufferedImage img = decode(png, declaredSha, width, height, dpr);
        Map<String, Integer> count = new HashMap<>();
        int rank = 0;
        long area = 0;
        for (Region r : regions) {
            int k = Region.KINDS.indexOf(r.kind());
            if (k < rank) throw new VisualPolicyException("REGION_ORDER");
            rank = k;
            if (count.merge(r.kind(), 1, Integer::sum) > V2_MAX.get(r.kind())) throw new VisualPolicyException("TOO_MANY_REGIONS");
            if (r.x() + r.width() > width || r.y() + r.height() > height) throw new VisualPolicyException("REGION_OUT_OF_IMAGE");
            if ("CARRIER".equals(r.kind())) {
                if (r.height() > Math.ceil(CARRIER_MAX_CSS_HEIGHT * dpr) || r.width() > Math.ceil(width * CARRIER_MAX_WIDTH_FRACTION)) {
                    throw new VisualPolicyException("CARRIER_TOO_LARGE");
                }
                area += (long) r.width() * r.height();
            }
        }
        if (area > (long) ((long) width * height * CARRIERS_MAX_AREA_FRACTION)) throw new VisualPolicyException("CARRIER_TOO_LARGE");
        for (Region c : regions) {
            if (!"CARRIER".equals(c.kind())) continue;
            for (Region f : regions) {
                boolean content = "FIELD".equals(f.kind()) || "FRAME".equals(f.kind()) || "MEDIA".equals(f.kind());
                if (content && c.x() < f.x() + f.width() && f.x() < c.x() + c.width() && c.y() < f.y() + f.height() && f.y() < c.y() + c.height()) {
                    throw new VisualPolicyException("CARRIER_OVERLAPS_CONTENT");
                }
            }
        }
        if (violationsV2(img, regions) != 0) throw new VisualPolicyException("SANITIZATION_CHECK_FAILED");
        return img;
    }

    /** Pixels fora dos portadores diferentes da tela esperada da v2 (repintada na ordem declarada). */
    public static long violationsV2(BufferedImage img, List<Region> regions) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] expected = new int[w * h];
        Arrays.fill(expected, V2_PAGE);
        boolean[] in = new boolean[w * h];
        for (Region r : regions) {
            int x2 = r.x() + r.width();
            int y2 = r.y() + r.height();
            if ("CARRIER".equals(r.kind())) {
                for (int y = r.y(); y < y2; y++) Arrays.fill(in, y * w + r.x(), y * w + x2, true);
                continue;
            }
            int[] fb = v2Paint(r.kind(), r.tone());
            for (int y = r.y(); y < y2; y++) {
                for (int x = r.x(); x < x2; x++) expected[y * w + x] = y == r.y() || y == y2 - 1 || x == r.x() || x == x2 - 1 ? fb[1] : fb[0];
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
                if ((alpha && (argb >>> 24) != 0xFF) || (argb & 0xFFFFFF) != expected[y * w + x]) bad++;
            }
        }
        return bad;
    }

    /** {preenchimento, borda} de uma região sintética da v2. */
    public static int[] v2Paint(String kind, String tone) {
        return switch (kind) {
            case "SURFACE" -> new int[] {V2_TONES.get(tone), V2_SURFACE_BORDER};
            case "MEDIA" -> new int[] {V2_MEDIA_FILL, V2_SURFACE_BORDER};
            case "FRAME" -> new int[] {V2_FRAME_FILL, FIELD_BORDER};
            case "CONTROL" -> new int[] {V2_CONTROL_FILL, FIELD_BORDER};
            case "FIELD" -> new int[] {FIELD_FILL, FIELD_BORDER};
            default -> throw new IllegalArgumentException("tipo");
        };
    }

    // ------------------------------------------------------------------------------------------- nekkora-visual/3

    public static final String POLICY_V3 = "nekkora-visual/3";
    public static final int V3_PAGE = 0xFFFFFF;
    static final Map<String, Integer> V3_TONES = Map.of("LIGHT", 0xF4F6F8, "MID", 0xE6EAEE, "DARK", 0x3E4A5C);
    static final int V3_SURFACE_BORDER = 0xD3DAE1;
    static final int V3_MEDIA_FILL = 0xEDF0F3;
    static final int V3_MEDIA_BORDER = 0xC9D1D9;
    static final int V3_FRAME_FILL = 0xF4F6F8;
    static final int V3_CONTROL_FILL = 0xE3E8EE;
    static final int V3_LINE = 0x8A9099;
    static final int V3_BAR_DARK = 0xC8CFD8; // barra no lugar de texto escuro (fundo claro)
    static final int V3_BAR_LIGHT = 0x8792A2; // barra no lugar de texto claro (fundo escuro)
    static final int V3_INK_DARK = 0x1F2933;
    static final int V3_INK_LIGHT = 0xFFFFFF;
    public static final Map<String, Integer> V3_MAX = Map.of("SURFACE", 48, "MEDIA", 24, "FRAME", 16, "CONTROL", 48, "FIELD", 96, "TEXT", 240, "LABEL", 96);
    public static final int V3_TEXT_QUANTUM = 16;
    public static final int V3_MAX_PNG_BYTES = 8_000_000;
    /** Folga por canal na mistura fundo↔tinta (arredondamento do desenho em tons de cinza). */
    static final int V3_BLEND_TOLERANCE = 3;

    /** Espessura da borda das regiões sintéticas (px da imagem). */
    public static int v3Border(int scale) {
        return Math.max(1, scale);
    }

    /** {preenchimento, borda} de uma região sintética da v3 (TEXT e LABEL têm regra própria). */
    public static int[] v3Paint(String kind, String tone) {
        return switch (kind) {
            case "SURFACE" -> new int[] {V3_TONES.get(tone), V3_SURFACE_BORDER};
            case "MEDIA" -> new int[] {V3_MEDIA_FILL, V3_MEDIA_BORDER};
            case "FRAME" -> new int[] {V3_FRAME_FILL, V3_LINE};
            case "CONTROL" -> new int[] {V3_CONTROL_FILL, V3_LINE};
            case "FIELD" -> new int[] {FIELD_FILL, V3_LINE};
            default -> throw new IllegalArgumentException("tipo");
        };
    }

    public static int v3Bar(String ink) {
        return "LIGHT".equals(ink) ? V3_BAR_LIGHT : V3_BAR_DARK;
    }

    public static int v3Ink(String ink) {
        return "LIGHT".equals(ink) ? V3_INK_LIGHT : V3_INK_DARK;
    }

    /** v3: imagem desenhada pelo Studio a partir da descrição (dpr = escala inteira px da imagem / px CSS). */
    static BufferedImage verifyV3(byte[] png, String declaredSha, int width, int height, double dpr, List<Region> regions) throws VisualPolicyException {
        if (png == null || png.length == 0 || png.length > V3_MAX_PNG_BYTES) throw new VisualPolicyException("SANITIZED_IMAGE_TOO_LARGE");
        if (declaredSha != null && !declaredSha.equals(Canonical.sha256(png))) throw new VisualPolicyException("CHECKSUM_MISMATCH");
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            throw new VisualPolicyException("IMAGE_UNREADABLE");
        }
        if (img == null) throw new VisualPolicyException("IMAGE_UNREADABLE");
        int scale = (int) dpr;
        if (img.getWidth() != width || img.getHeight() != height || (scale != 1 && scale != 2) || scale != dpr || width % scale != 0 || height % scale != 0) {
            throw new VisualPolicyException("GEOMETRY_MISMATCH");
        }
        Map<String, Integer> count = new HashMap<>();
        int rank = 0;
        List<Region> labels = new java.util.ArrayList<>();
        for (Region r : regions) {
            int k = Region.V3_KINDS.indexOf(r.kind());
            if (k < 0) throw new VisualPolicyException("REGION_KIND_UNKNOWN");
            if (k < rank) throw new VisualPolicyException("REGION_ORDER");
            rank = k;
            if (count.merge(r.kind(), 1, Integer::sum) > V3_MAX.get(r.kind())) throw new VisualPolicyException("TOO_MANY_REGIONS");
            if ((long) r.x() + r.width() > width || (long) r.y() + r.height() > height) throw new VisualPolicyException("REGION_OUT_OF_IMAGE");
            if (r.x() % scale != 0 || r.y() % scale != 0 || r.width() % scale != 0 || r.height() % scale != 0) throw new VisualPolicyException("GEOMETRY_MISMATCH");
            if ("TEXT".equals(r.kind()) && r.width() % (V3_TEXT_QUANTUM * scale) != 0) throw new VisualPolicyException("TEXT_NOT_QUANTIZED");
            if ("LABEL".equals(r.kind())) {
                if (!r.text().equals(com.nekkora.studio.domain.privacy.CapturePrivacy.interfaceText(r.text()))) {
                    throw new VisualPolicyException("LABEL_NOT_INTERFACE_TEXT");
                }
                if (r.size() < 8 || r.size() > 40) throw new VisualPolicyException("REGION_KIND_UNKNOWN");
                for (Region o : labels) {
                    if (r.x() < o.x() + o.width() && o.x() < r.x() + r.width() && r.y() < o.y() + o.height() && o.y() < r.y() + r.height()) {
                        throw new VisualPolicyException("LABELS_OVERLAP");
                    }
                }
                labels.add(r);
            }
        }
        if (violationsV3(img, regions, scale) != 0) throw new VisualPolicyException("SANITIZATION_CHECK_FAILED");
        return img;
    }

    /** Pixels fora da regra da v3 (repintura exata fora dos rótulos; mistura fundo↔tinta dentro deles). */
    public static long violationsV3(BufferedImage img, List<Region> regions, int scale) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] expected = new int[w * h];
        Arrays.fill(expected, V3_PAGE);
        int[] ink = new int[w * h]; // 0 = fora de rótulo; senão 0x1000000 | tinta
        int bw = v3Border(scale);
        for (Region r : regions) {
            int x2 = r.x() + r.width();
            int y2 = r.y() + r.height();
            if ("LABEL".equals(r.kind())) {
                int c = 0x1000000 | v3Ink(r.ink());
                for (int y = r.y(); y < y2; y++) Arrays.fill(ink, y * w + r.x(), y * w + x2, c);
                continue;
            }
            if ("TEXT".equals(r.kind())) {
                int c = v3Bar(r.ink());
                for (int y = r.y(); y < y2; y++) Arrays.fill(expected, y * w + r.x(), y * w + x2, c);
                continue;
            }
            int[] fb = v3Paint(r.kind(), r.tone());
            boolean inner = r.width() > 2 * bw && r.height() > 2 * bw;
            for (int y = r.y(); y < y2; y++) {
                for (int x = r.x(); x < x2; x++) {
                    boolean edge = !inner || x < r.x() + bw || x >= x2 - bw || y < r.y() + bw || y >= y2 - bw;
                    expected[y * w + x] = edge ? fb[1] : fb[0];
                }
            }
        }
        boolean alpha = img.getColorModel().hasAlpha();
        long bad = 0;
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            img.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int argb = row[x];
                int i = y * w + x;
                if (alpha && (argb >>> 24) != 0xFF) {
                    bad++;
                } else if (ink[i] == 0) {
                    if ((argb & 0xFFFFFF) != expected[i]) bad++;
                } else if (!blend(argb & 0xFFFFFF, expected[i], ink[i] & 0xFFFFFF)) {
                    bad++;
                }
            }
        }
        return bad;
    }

    /** {@code v} está no segmento fundo→tinta (mesma fração α em todos os canais, com folga de arredondamento)? */
    static boolean blend(int v, int bg, int ink) {
        int[] vc = {v >> 16 & 0xFF, v >> 8 & 0xFF, v & 0xFF};
        int[] bc = {bg >> 16 & 0xFF, bg >> 8 & 0xFF, bg & 0xFF};
        int[] ic = {ink >> 16 & 0xFF, ink >> 8 & 0xFF, ink & 0xFF};
        int ref = 0;
        for (int c = 1; c < 3; c++) if (Math.abs(ic[c] - bc[c]) > Math.abs(ic[ref] - bc[ref])) ref = c;
        int span = ic[ref] - bc[ref];
        if (span == 0) return v == bg;
        double a = (vc[ref] - bc[ref]) / (double) span;
        if (a < -0.02 || a > 1.02) return false;
        a = Math.max(0, Math.min(1, a));
        for (int c = 0; c < 3; c++) {
            if (Math.abs(bc[c] + a * (ic[c] - bc[c]) - vc[c]) > V3_BLEND_TOLERANCE) return false;
        }
        return true;
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
