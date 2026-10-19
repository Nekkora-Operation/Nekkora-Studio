package com.nekkora.studio.pdd;

import com.nekkora.studio.domain.privacy.CapturePrivacy;
import com.nekkora.studio.pdd.DocumentDraft.Region;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * NEKKORA-STUDIO-013 — desenha a imagem da política {@code nekkora-visual/3} a partir da DESCRIÇÃO recebida da extensão
 * (geometria em px CSS inteiros, tipos, tons e rótulos do vocabulário). Nenhum pixel da página existe neste caminho: a
 * extensão não captura a tela e não envia imagem.
 *
 * <p>Antes de desenhar, a descrição passa pela mesma regra do host (tipos e ordem, tetos, geometria, barras quantizadas e
 * texto de rótulo aprovado por {@link CapturePrivacy#interfaceText}); rótulos que se sobrepõem a um rótulo anterior são
 * descartados (a verificação exige rótulos disjuntos). A escala é 2 (1 quando o viewport passa de 2048 px CSS), para o
 * texto ficar nítido no PDF. A imagem pronta passa ainda pela 4ª barreira ({@link VisualVerifier#verifyV3}).
 */
public final class SyntheticRenderer {
    public static final String POLICY = VisualVerifier.POLICY_V3;
    private static final Map<String, Font> FONTS = new HashMap<>();

    private SyntheticRenderer() {}

    /** Descrição v3 (px CSS). */
    public record Description(int width, int height, double dpr, List<Region> regions) {
        public Description {
            regions = List.copyOf(regions);
        }
    }

    /** Imagem desenhada: PNG, sha-256, dimensões, escala (px da imagem por px CSS) e regiões em px da imagem. */
    public record Rendered(byte[] png, String sha256, int width, int height, int scale, List<Region> regions, int droppedLabels) {
        public Rendered {
            regions = List.copyOf(regions);
        }
    }

    /** Valida (código estável em {@link VisualVerifier.VisualPolicyException}) e desenha. */
    public static Rendered render(Description d) throws VisualVerifier.VisualPolicyException {
        List<Region> css = normalize(d);
        int scale = d.width() > 2048 || d.height() > 2048 ? 1 : 2;
        int w = d.width() * scale;
        int h = d.height() * scale;
        List<Region> px = new ArrayList<>();
        int dropped = 0;
        List<Region> labels = new ArrayList<>();
        for (Region r : css) {
            Region s = scaled(r, scale);
            if ("LABEL".equals(s.kind())) {
                if (labels.stream().anyMatch(o -> overlaps(o, s))) {
                    dropped++; // fidelidade e verificação exata: um rótulo por pixel
                    continue;
                }
                labels.add(s);
            }
            px.add(s);
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setColor(new Color(VisualVerifier.V3_PAGE));
            g.fillRect(0, 0, w, h);
            int bw = VisualVerifier.v3Border(scale);
            for (Region r : px) {
                switch (r.kind()) {
                    case "TEXT" -> {
                        g.setColor(new Color(VisualVerifier.v3Bar(r.ink())));
                        g.fillRect(r.x(), r.y(), r.width(), r.height());
                    }
                    case "LABEL" -> { } // depois de tudo: por cima
                    default -> {
                        int[] fb = VisualVerifier.v3Paint(r.kind(), r.tone());
                        g.setColor(new Color(fb[1]));
                        g.fillRect(r.x(), r.y(), r.width(), r.height());
                        if (r.width() > 2 * bw && r.height() > 2 * bw) {
                            g.setColor(new Color(fb[0]));
                            g.fillRect(r.x() + bw, r.y() + bw, r.width() - 2 * bw, r.height() - 2 * bw);
                        }
                    }
                }
            }
            // texto em tons de cinza (nunca subpixel/LCD: a verificação exige mistura entre o fundo e a tinta)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            for (Region r : labels) drawLabel(g, r, scale);
        } finally {
            g.dispose();
        }
        byte[] png;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            png = out.toByteArray();
        } catch (IOException e) {
            throw new VisualVerifier.VisualPolicyException("IMAGE_UNREADABLE");
        }
        return new Rendered(png, Canonical.sha256(png), w, h, scale, px, dropped);
    }

    /** Regra da descrição (px CSS), igual à do service worker e do host. */
    static List<Region> normalize(Description d) throws VisualVerifier.VisualPolicyException {
        if (d.width() < 1 || d.height() < 1 || d.width() > 8192 || d.height() > 8192 || !(d.dpr() > 0 && d.dpr() <= 4)) {
            throw new VisualVerifier.VisualPolicyException("GEOMETRY_MISMATCH");
        }
        Map<String, Integer> count = new HashMap<>();
        int rank = 0;
        for (Region r : d.regions()) {
            int k = Region.V3_KINDS.indexOf(r.kind());
            if (k < 0) throw new VisualVerifier.VisualPolicyException("REGION_KIND_UNKNOWN");
            if (k < rank) throw new VisualVerifier.VisualPolicyException("REGION_ORDER");
            rank = k;
            if (count.merge(r.kind(), 1, Integer::sum) > VisualVerifier.V3_MAX.get(r.kind())) throw new VisualVerifier.VisualPolicyException("TOO_MANY_REGIONS");
            if ((long) r.x() + r.width() > d.width() || (long) r.y() + r.height() > d.height()) throw new VisualVerifier.VisualPolicyException("REGION_OUT_OF_VIEWPORT");
            if ("TEXT".equals(r.kind()) && r.width() % VisualVerifier.V3_TEXT_QUANTUM != 0) throw new VisualVerifier.VisualPolicyException("TEXT_NOT_QUANTIZED");
            if ("LABEL".equals(r.kind())) {
                if (!r.text().equals(CapturePrivacy.interfaceText(r.text()))) throw new VisualVerifier.VisualPolicyException("LABEL_NOT_INTERFACE_TEXT");
                if (r.size() < 8 || r.size() > 40) throw new VisualVerifier.VisualPolicyException("REGION_KIND_UNKNOWN");
            }
        }
        return d.regions();
    }

    private static Region scaled(Region r, int s) {
        return new Region(r.kind(), r.x() * s, r.y() * s, r.width() * s, r.height() * s, r.tone(), r.text(), r.size(), r.weight(), r.ink(), r.align());
    }

    static boolean overlaps(Region a, Region b) {
        return a.x() < b.x() + b.width() && b.x() < a.x() + a.width() && a.y() < b.y() + b.height() && b.y() < a.y() + a.height();
    }

    private static void drawLabel(Graphics2D g, Region r, int scale) {
        Font base = font("BOLD".equals(r.weight()));
        float px = r.size() * scale;
        int pad = 2 * scale;
        Font f = base.deriveFont(px);
        FontMetrics fm = g.getFontMetrics(f);
        int avail = Math.max(1, r.width() - 2 * pad);
        int tw = fm.stringWidth(r.text());
        if (tw > avail) {
            float fit = Math.max(6f * scale, px * avail / tw);
            f = base.deriveFont(fit);
            fm = g.getFontMetrics(f);
            tw = fm.stringWidth(r.text());
        }
        Graphics2D t = (Graphics2D) g.create();
        try {
            t.clipRect(r.x(), r.y(), r.width(), r.height());
            t.setFont(f);
            t.setColor(new Color(VisualVerifier.v3Ink(r.ink())));
            int x = "CENTER".equals(r.align()) ? r.x() + Math.max(pad, (r.width() - tw) / 2) : r.x() + pad;
            int y = r.y() + (r.height() - fm.getHeight()) / 2 + fm.getAscent();
            t.drawString(r.text(), x, y);
        } finally {
            t.dispose();
        }
    }

    /** Inter do design system (classpath do nekkora-swing-ui); sem ela, a fonte lógica sem serifa (a verificação não depende da fonte). */
    static synchronized Font font(boolean bold) {
        String name = bold ? "Inter-SemiBold.ttf" : "Inter-Regular.ttf";
        return FONTS.computeIfAbsent(name, n -> {
            try (InputStream in = SyntheticRenderer.class.getClassLoader().getResourceAsStream("com/nekkora/ui/fonts/" + n)) {
                if (in != null) return Font.createFont(Font.TRUETYPE_FONT, in);
            } catch (IOException | FontFormatException e) {
                // segue para a fonte lógica
            }
            return new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, 12);
        });
    }
}
