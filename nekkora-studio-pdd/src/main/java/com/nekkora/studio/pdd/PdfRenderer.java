package com.nekkora.studio.pdd;

import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.layout.MarkerLayout;
import com.nekkora.studio.pdd.DocumentDraft.Annotation;
import com.nekkora.studio.pdd.DocumentDraft.Block;
import com.nekkora.studio.pdd.DocumentDraft.Instruction;
import com.nekkora.studio.pdd.DocumentDraft.Region;
import com.nekkora.studio.pdd.DocumentDraft.SafeCapture;
import com.nekkora.studio.pdd.DocumentDraft.Section;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * STUDIO-010 G7 — PDF do PDD visual (PDFBox): seções na ordem das sessões; cada bloco com título e instruções numeradas
 * à esquerda e a imagem SANITIZADA à direita, com marcadores vetoriais numerados fora do controle (sem escondê-lo).
 * Texto pesquisável (Inter embutida como subconjunto). Metadados controlados: título fixo, produtor/criador
 * "Nekkora Studio", data — sem autor, assunto, palavras-chave, caminho ou XMP.
 *
 * <p>Só escreve textos EXPORTÁVEIS ({@link DocText#exportable}): um texto pendente ou fora da política lança, em vez de
 * sair no documento. As imagens recebidas já passaram pela 4ª barreira; o recorte só reduz a área mostrada.
 */
public final class PdfRenderer {
    /** Fontes do design system (Inter), injetadas pelo app. */
    public record Fonts(byte[] regular, byte[] semibold) {
        public static Fonts fromClasspath(ClassLoader cl) throws IOException {
            return new Fonts(read(cl, "com/nekkora/ui/fonts/Inter-Regular.ttf"), read(cl, "com/nekkora/ui/fonts/Inter-SemiBold.ttf"));
        }

        private static byte[] read(ClassLoader cl, String path) throws IOException {
            try (InputStream in = cl.getResourceAsStream(path)) {
                if (in == null) throw new IOException("fonte ausente: " + path);
                return in.readAllBytes();
            }
        }
    }

    /**
     * Rótulo da versão ("v001") e data do documento. {@code preview}: prévia no editor — texto pendente aparece como
     * "[Revisar] …" e texto fora da política como "[Texto recusado pela política]" (nunca o texto); o arquivo exportado
     * nunca usa a prévia.
     */
    public record Options(String versionLabel, LocalDate date, boolean preview) {
        public Options(String versionLabel, LocalDate date) {
            this(versionLabel, date, false);
        }
    }

    static final float W = PDRectangle.A4.getWidth();
    static final float H = PDRectangle.A4.getHeight();
    static final float M = 42;
    static final float LEFT_W = 200;
    static final float GAP = 16;
    static final float IMG_X = M + LEFT_W + GAP;
    static final float IMG_W = W - M - IMG_X;
    static final float IMG_MAX_H = 330;
    static final Color INK = new Color(0x1F, 0x23, 0x28);
    static final Color MUTED = new Color(0x57, 0x60, 0x6A);
    static final Color PINK = new Color(0xC2, 0x46, 0x6D);
    static final Color FRAME = new Color(0xD0, 0xD7, 0xDE);
    static final Color PANEL = new Color(0xF6, 0xF8, 0xFA);
    public static final String DOCUMENT_TITLE = "Processo documentado (PDD visual)";
    public static final String PROVENANCE = "Gerado pelo Nekkora Studio a partir das sessões e etapas mapeadas. As imagens são capturas do navegador "
            + "sanitizadas: só rótulos de interface aparecem; campos e o restante da tela foram ocultados. Este documento descreve o processo "
            + "mapeado e não comprova resultado de negócio.";
    /** STUDIO-012 PDD-01: documento com alguma imagem nekkora-visual/2 (representação documental). */
    public static final String PROVENANCE_V2 = "Gerado pelo Nekkora Studio a partir das sessões e etapas mapeadas. As imagens são representações "
            + "sanitizadas da tela: a estrutura (blocos, campos e botões) é redesenhada sem conteúdo e só rótulos de interface aparecem como na "
            + "página. Este documento descreve o processo mapeado e não comprova resultado de negócio.";
    /** STUDIO-013 (revisão): documento com alguma imagem nekkora-visual/4 (print real com o texto de dados mascarado). */
    public static final String PROVENANCE_V4 = "Gerado pelo Nekkora Studio a partir das sessões e etapas mapeadas. As imagens são capturas reais da "
            + "tela com os campos, os textos de dados, as tabelas, os frames e as imagens de conteúdo cobertos por faixas lisas; só a aparência "
            + "da tela e os rótulos de interface do vocabulário controlado aparecem como na página. Revise as imagens antes de compartilhar. "
            + "Este documento descreve o processo mapeado e não comprova resultado de negócio.";
    /** Revisão da usuária: documento com alguma imagem nekkora-visual/5 (print integral, com os dados da tela). */
    public static final String PROVENANCE_V5 = "Gerado pelo Nekkora Studio a partir das sessões e etapas mapeadas. Este documento contém imagens em "
            + "print integral: elas mostram a tela como estava, com os dados exibidos nela; só os campos de senha foram cobertos. Revise o "
            + "documento antes de compartilhar. Este documento descreve o processo mapeado e não comprova resultado de negócio.";
    /** Acrescentado quando o documento mistura print integral com imagens mascaradas ou sintéticas. */
    public static final String PROVENANCE_V5_MIXED = "As demais imagens trazem os dados cobertos ou são representações sintéticas.";
    /** Nota de cada bloco cuja imagem é print integral. */
    public static final String FULL_PRINT_NOTE = "Print integral: a imagem mostra os dados da tela; só senhas cobertas.";
    /** Documento que mistura imagens v3 com imagens anteriores (v2) do laboratório. */
    public static final String PROVENANCE_V2_MIXED = "Imagens anteriores do laboratório (representação documental) trazem os rótulos como na página.";
    /** NEKKORA-STUDIO-013: documento com alguma imagem nekkora-visual/3 (representação inteiramente sintética). */
    public static final String PROVENANCE_V3 = "Gerado pelo Nekkora Studio a partir das sessões e etapas mapeadas. As imagens são representações "
            + "sintéticas da tela desenhadas pelo Studio: nenhum pixel da página foi copiado; só rótulos de interface do vocabulário controlado "
            + "aparecem como texto, os demais textos viram barras neutras e os campos ficam vazios. Não é uma captura de tela real. Este "
            + "documento descreve o processo mapeado e não comprova resultado de negócio.";
    /** Acrescentado quando o documento tem algum marcador manual. */
    public static final String MANUAL_NOTE = "Marcadores tracejados foram posicionados manualmente na revisão e não comprovam o elemento.";

    private final PDDocument doc;
    private boolean preview;
    private final PDFont regular;
    private final PDFont bold;
    private final List<PDPage> pages = new ArrayList<>();
    private PDPageContentStream cs;
    private float y;

    private PdfRenderer(PDDocument doc, Fonts fonts) throws IOException {
        this.doc = doc;
        this.regular = PDType0Font.load(doc, new ByteArrayInputStream(fonts.regular()), true);
        this.bold = PDType0Font.load(doc, new ByteArrayInputStream(fonts.semibold()), true);
    }

    /**
     * Gera o PDF em memória.
     *
     * @param images imagens já verificadas, pelo sha-256 da captura (ausente → bloco com "Imagem indisponível")
     */
    public static byte[] render(DocumentDraft d, Map<String, BufferedImage> images, Fonts fonts, Options options) throws IOException {
        Objects.requireNonNull(d);
        try (PDDocument doc = new PDDocument()) {
            PdfRenderer r = new PdfRenderer(doc, fonts);
            r.preview = options.preview();
            r.body(d, images, options);
            r.footers();
            PDDocumentInformation info = new PDDocumentInformation();
            info.setTitle("PDD visual");
            info.setProducer("Nekkora Studio");
            info.setCreator("Nekkora Studio");
            Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
            cal.clear();
            cal.set(options.date().getYear(), options.date().getMonthValue() - 1, options.date().getDayOfMonth());
            info.setCreationDate(cal);
            doc.setDocumentInformation(info);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private void body(DocumentDraft d, Map<String, BufferedImage> images, Options o) throws IOException {
        newPage();
        text(bold, 18, INK, M, y - 18, DOCUMENT_TITLE);
        y -= 30;
        text(regular, 9.5f, MUTED, M, y - 9.5f, "Versão " + o.versionLabel() + " · " + o.date().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " · Nekkora Studio");
        y -= 22;
        boolean v2 = d.blocks().stream().map(b -> b.capture().map(d.captures()::get).orElse(null)).anyMatch(c -> c != null
                && VisualVerifier.POLICY_V2.equals(c.policy()));
        boolean v3 = d.blocks().stream().map(b -> b.capture().map(d.captures()::get).orElse(null)).anyMatch(c -> c != null
                && VisualVerifier.POLICY_V3.equals(c.policy()));
        boolean manual = d.blocks().stream().map(b -> b.capture().map(d.captures()::get).orElse(null)).anyMatch(c -> c != null && c.annotations().stream()
                .anyMatch(Annotation::manual));
        boolean v4 = d.blocks().stream().map(b -> b.capture().map(d.captures()::get).orElse(null)).anyMatch(c -> c != null
                && VisualVerifier.POLICY_V4.equals(c.policy()));
        boolean v5 = d.blocks().stream().map(b -> b.capture().map(d.captures()::get).orElse(null)).anyMatch(c -> c != null && c.fullPrint());
        boolean masked = d.blocks().stream().map(b -> b.capture().map(d.captures()::get).orElse(null)).anyMatch(c -> c != null && !c.fullPrint());
        String provenance = v5 ? (masked ? PROVENANCE_V5 + " " + PROVENANCE_V5_MIXED : PROVENANCE_V5) : v4 ? PROVENANCE_V4 : v3 && !v2 ? PROVENANCE_V3 : v3 ? PROVENANCE_V3 + " " + PROVENANCE_V2_MIXED : v2 ? PROVENANCE_V2 : PROVENANCE;
        for (String line : wrap(regular, 9, provenance + (manual ? " " + MANUAL_NOTE : ""), W - 2 * M)) {
            text(regular, 9, MUTED, M, y - 9, line);
            y -= 12.5f;
        }
        y -= 14;
        for (Section s : d.sections()) {
            ensure(60);
            text(bold, 14, INK, M, y - 14, s.number() + ". " + exportable(s.title(), preview));
            y -= 26;
            if (s.blocks().isEmpty()) {
                text(regular, 9.5f, MUTED, M, y - 9.5f, "Sessão sem etapas mapeadas.");
                y -= 20;
            }
            for (Block b : s.blocks()) block(d, b, images);
        }
    }

    private void block(DocumentDraft d, Block b, Map<String, BufferedImage> images) throws IOException {
        SafeCapture cap = b.capture().map(d.captures()::get).orElse(null);
        BufferedImage img = cap == null ? null : images.get(cap.sha256());
        Crop crop = img == null ? null : Crop.of(img, cap);
        float imgH = crop == null ? 110 : Math.min(IMG_MAX_H, IMG_W * crop.h / (float) crop.w);
        float imgW = crop == null ? IMG_W : imgH * crop.w / (float) crop.h;
        List<String[]> lines = new ArrayList<>(); // {texto, estilo}
        boolean recorded = b.instructions().stream().allMatch(i -> i.kind() == Instruction.Kind.OBSERVED);
        boolean declared = b.instructions().stream().allMatch(i -> i.kind() == Instruction.Kind.DECLARED);
        lines.add(new String[] {recorded ? "Ações observadas na gravação" : declared ? "Etapas declaradas no Studio" : "Ações gravadas e etapas declaradas", "nota"});
        if (cap != null && cap.fullPrint()) {
            for (String w : wrap(regular, 8.5f, FULL_PRINT_NOTE, LEFT_W)) lines.add(new String[] {w, "nota"});
        }
        for (Instruction i : b.instructions()) {
            List<String> w = wrap(regular, 10, i.ordinal() + ". " + exportable(i.text(), preview), LEFT_W);
            for (int k = 0; k < w.size(); k++) lines.add(new String[] {(k == 0 ? "" : "     ") + w.get(k), "texto"});
            Annotation mk = cap == null ? null : Pendings.marker(cap, i.stepId());
            if (cap != null && mk == null) lines.add(new String[] {"     (marcador indisponível)", "nota"});
            else if (mk != null && !Pendings.visibleInCrop(cap, mk)) lines.add(new String[] {"     (marcador fora do recorte)", "nota"});
            else if (mk != null && mk.manual()) lines.add(new String[] {"     (marcador posicionado manualmente)", "nota"});
        }
        float textH = 0;
        for (String[] l : lines) textH += "nota".equals(l[1]) ? 13 : 14.5f;
        List<String> title = wrap(bold, 11, exportable(b.title(), preview), W - 2 * M);
        float need = title.size() * 15 + 8 + Math.max(textH, imgH) + 22;
        ensure(Math.min(need, H - 2 * M - 30));
        for (String t : title) {
            text(bold, 11, INK, M, y - 11, t);
            y -= 15;
        }
        y -= 8;
        float top = y;
        // imagem (ou ausência declarada) à direita
        if (crop != null) {
            PDImageXObject x = LosslessFactory.createFromImage(doc, crop.image);
            float ix = IMG_X + (IMG_W - imgW);
            cs.drawImage(x, ix, top - imgH, imgW, imgH);
            stroke(FRAME, 0.8f, ix, top - imgH, imgW, imgH);
            // coordenadas da imagem com y para baixo (MarkerLayout): os números de todos os marcadores são postos juntos
            List<MarkerLayout.Box> obstacles = new ArrayList<>();
            float sx = imgW / crop.w;
            float sy = imgH / crop.h;
            for (Region r : cap.regions()) {
                if (!Crop.INTERACTIVE.contains(r.kind())) continue; // superfícies, mídia e frames não impedem o número do marcador
                obstacles.add(new MarkerLayout.Box((r.x() - crop.x) * sx, (r.y() - crop.y) * sy, r.width() * sx, r.height() * sy));
            }
            List<Annotation> shown = new ArrayList<>();
            List<Integer> numbers = new ArrayList<>();
            List<MarkerLayout.Box> boxes = new ArrayList<>();
            for (Instruction i : b.instructions()) {
                Annotation a = Pendings.marker(cap, i.stepId());
                if (a == null || !Pendings.visibleInCrop(cap, a)) continue;
                shown.add(a);
                numbers.add(i.ordinal());
                boxes.add(markerBox(crop, a, imgW, imgH));
            }
            List<MarkerLayout.Point> centers = MarkerLayout.place(boxes, new MarkerLayout.Box(0, 0, imgW, imgH), MARKER_R, MARKER_GAP, obstacles);
            for (int k = 0; k < shown.size(); k++) marker(shown.get(k), boxes.get(k), centers.get(k), numbers.get(k), ix, top);
        } else {
            fill(PANEL, IMG_X, top - imgH, IMG_W, imgH);
            stroke(FRAME, 0.8f, IMG_X, top - imgH, IMG_W, imgH);
            text(bold, 10, MUTED, IMG_X + 12, top - 30, "Imagem indisponível");
            int k = 0;
            for (String l : wrap(regular, 8.5f, "Nenhuma captura sanitizada foi associada a este bloco. Nenhuma imagem de outra etapa ou tela é usada no lugar.",
                    IMG_W - 24)) {
                text(regular, 8.5f, MUTED, IMG_X + 12, top - 48 - 11 * k++, l);
            }
        }
        // texto à esquerda (continua na página seguinte, se preciso)
        for (String[] l : lines) {
            float lh = "nota".equals(l[1]) ? 13 : 14.5f;
            if (y - lh < M + 30) {
                newPage();
                top = y;
            }
            if ("nota".equals(l[1])) text(regular, 8.5f, MUTED, M, y - 8.5f, l[0]);
            else text(regular, 10, INK, M, y - 10, l[0]);
            y -= lh;
        }
        y = Math.min(y, top - imgH) - 22;
    }

    /**
     * Marcador numerado: contorno no retângulo do alvo e o número FORA de qualquer controle — tenta à esquerda, acima, à
     * direita e abaixo, e fica na primeira posição que não toca campo, rótulo, outro marcador nem a borda da imagem.
     */
    static final float MARKER_R = 7;
    static final float MARKER_GAP = 4;

    /** Contorno do marcador na imagem (y para baixo), recortado à imagem (recorte manual pode cortar o alvo). */
    static MarkerLayout.Box markerBox(Crop crop, Annotation a, float iw, float ih) {
        double sx = iw / (double) crop.w;
        double sy = ih / (double) crop.h;
        double x0 = Math.max(0, (a.x() * crop.full.width - crop.x) * sx);
        double y0 = Math.max(0, (a.y() * crop.full.height - crop.y) * sy);
        double x1 = Math.min(iw, ((a.x() + a.width()) * crop.full.width - crop.x) * sx);
        double y1 = Math.min(ih, ((a.y() + a.height()) * crop.full.height - crop.y) * sy);
        return new MarkerLayout.Box(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
    }

    /** Contorno e número de um marcador; {@code c} = centro do número (MarkerLayout), em coordenadas da imagem. */
    private void marker(Annotation a, MarkerLayout.Box box, MarkerLayout.Point c, int n, float ix, float top) throws IOException {
        float rx = ix + (float) box.x();
        float rw = (float) box.width();
        float rh = (float) box.height();
        float ryTop = top - (float) box.y();
        if (a.manual()) cs.setLineDashPattern(new float[] {3, 2}, 0); // manual: tracejado (decisão visual, não prova do alvo)
        stroke(PINK, 1.2f, rx - 1.5f, ryTop - rh - 1.5f, rw + 3, rh + 3);
        if (a.manual()) cs.setLineDashPattern(new float[] {}, 0);
        float cx = ix + (float) c.x();
        float cy = top - (float) c.y();
        cs.setNonStrokingColor(PINK);
        circle(cx, cy, MARKER_R);
        cs.fill();
        String s = String.valueOf(n);
        float tw = bold.getStringWidth(s) / 1000 * 8;
        text(bold, 8, Color.WHITE, cx - tw / 2, cy - 2.9f, s);
    }

    private static String exportable(DocText t, boolean preview) {
        if (!t.exportable()) {
            if (!preview) throw new IllegalStateException("texto não exportável no documento");
            return PddText.problems(t.value()).isEmpty() ? "[Revisar] " + pdfText(t.value()) : "[Texto recusado pela política]";
        }
        return pdfText(t.value());
    }

    /**
     * Aspas tipográficas viram {@code "} no PDF: na Inter o glifo de U+201D é compartilhado e a extração de texto devolvia
     * outro caractere (U+02EE) — o documento precisa ser PESQUISÁVEL pelo mesmo texto que se lê.
     */
    static String pdfText(String s) {
        return s.replace('“', '"').replace('”', '"');
    }

    // ------------------------------------------------------------------------------------------- recorte

    /** Recorte da imagem: o escolhido no Studio; no print real, a tela inteira; nas demais, união das regiões e dos marcadores, com margem. */
    record Crop(BufferedImage image, int x, int y, int w, int h, java.awt.Dimension full) {
        /** Regiões que definem o recorte automático: o que a pessoa usa (na v2, a estrutura em volta é contexto). */
        static final List<String> INTERACTIVE = List.of("CARRIER", "FIELD", "CONTROL", "LABEL"); // LABEL: rótulo desenhado da v3

        static Crop of(BufferedImage img, SafeCapture c) {
            if (c.crop() != null) { // recorte escolhido no Studio: exatamente o que foi revisado
                DocumentDraft.Crop k = c.crop();
                return new Crop(img.getSubimage(k.x(), k.y(), k.width(), k.height()), k.x(), k.y(), k.width(), k.height(),
                        new java.awt.Dimension(img.getWidth(), img.getHeight()));
            }
            // print real (v4/v5): sem recorte escolhido, vai a tela inteira, como o editor mostra (revisão da usuária: o recorte
            // automático deixava só o miolo em volta dos campos, parecido com a imagem sintética)
            if (VisualVerifier.POLICY_V4.equals(c.policy()) || VisualVerifier.POLICY_V5.equals(c.policy())) {
                return new Crop(img, 0, 0, img.getWidth(), img.getHeight(), new java.awt.Dimension(img.getWidth(), img.getHeight()));
            }
            int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = 0, y1 = 0;
            for (Region r : c.regions()) {
                if (!INTERACTIVE.contains(r.kind())) continue;
                x0 = Math.min(x0, r.x());
                y0 = Math.min(y0, r.y());
                x1 = Math.max(x1, r.x() + r.width());
                y1 = Math.max(y1, r.y() + r.height());
            }
            for (Annotation a : c.annotations()) {
                if (a.status() != Annotation.Status.PLACED) continue;
                x0 = Math.min(x0, (int) (a.x() * img.getWidth()));
                y0 = Math.min(y0, (int) (a.y() * img.getHeight()));
                x1 = Math.max(x1, (int) Math.ceil((a.x() + a.width()) * img.getWidth()));
                y1 = Math.max(y1, (int) Math.ceil((a.y() + a.height()) * img.getHeight()));
            }
            if (x0 == Integer.MAX_VALUE) return new Crop(img, 0, 0, img.getWidth(), img.getHeight(), new java.awt.Dimension(img.getWidth(), img.getHeight()));
            int m = (int) Math.round((VisualVerifier.POLICY.equals(c.policy()) ? 32 : 48) * c.devicePixelRatio()); // v2: mais contexto em volta
            x0 = Math.max(0, x0 - m);
            y0 = Math.max(0, y0 - m);
            x1 = Math.min(img.getWidth(), Math.max(x1 + m, x0 + 240));
            y1 = Math.min(img.getHeight(), Math.max(y1 + m, y0 + 140));
            return new Crop(img.getSubimage(x0, y0, x1 - x0, y1 - y0), x0, y0, x1 - x0, y1 - y0, new java.awt.Dimension(img.getWidth(), img.getHeight()));
        }
    }

    // ------------------------------------------------------------------------------------------- página

    private void newPage() throws IOException {
        if (cs != null) cs.close();
        PDPage p = new PDPage(PDRectangle.A4);
        doc.addPage(p);
        pages.add(p);
        cs = new PDPageContentStream(doc, p);
        y = H - M;
    }

    private void ensure(float h) throws IOException {
        if (y - h < M + 30) newPage();
    }

    private void footers() throws IOException {
        cs.close();
        cs = null;
        int n = pages.size();
        for (int i = 0; i < n; i++) {
            try (PDPageContentStream f = new PDPageContentStream(doc, pages.get(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                String right = "Página " + (i + 1) + " de " + n;
                float tw = regular.getStringWidth(right) / 1000 * 8;
                f.beginText();
                f.setFont(regular, 8);
                f.setNonStrokingColor(MUTED);
                f.newLineAtOffset(W - M - tw, 24);
                f.showText(right);
                f.endText();
                f.beginText();
                f.setFont(regular, 7.5f);
                f.newLineAtOffset(M, 24);
                f.showText("Nekkora Studio · PDD visual · descreve o processo mapeado; não comprova resultado de negócio.");
                f.endText();
            }
        }
    }

    private void text(PDFont f, float size, Color c, float x, float yy, String s) throws IOException {
        cs.beginText();
        cs.setFont(f, size);
        cs.setNonStrokingColor(c);
        cs.newLineAtOffset(x, yy);
        cs.showText(s);
        cs.endText();
    }

    private void stroke(Color c, float width, float x, float yy, float w, float h) throws IOException {
        cs.setStrokingColor(c);
        cs.setLineWidth(width);
        cs.addRect(x, yy, w, h);
        cs.stroke();
    }

    private void fill(Color c, float x, float yy, float w, float h) throws IOException {
        cs.setNonStrokingColor(c);
        cs.addRect(x, yy, w, h);
        cs.fill();
    }

    private void circle(float cx, float cy, float r) throws IOException {
        float k = 0.552284749831f * r;
        cs.moveTo(cx - r, cy);
        cs.curveTo(cx - r, cy + k, cx - k, cy + r, cx, cy + r);
        cs.curveTo(cx + k, cy + r, cx + r, cy + k, cx + r, cy);
        cs.curveTo(cx + r, cy - k, cx + k, cy - r, cx, cy - r);
        cs.curveTo(cx - k, cy - r, cx - r, cy - k, cx - r, cy);
    }

    static List<String> wrap(PDFont f, float size, String text, float width) throws IOException {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : text.split(" ")) {
            String cand = line.length() == 0 ? w : line + " " + w;
            if (f.getStringWidth(cand) / 1000 * size > width && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(w);
            } else {
                line = new StringBuilder(cand);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
