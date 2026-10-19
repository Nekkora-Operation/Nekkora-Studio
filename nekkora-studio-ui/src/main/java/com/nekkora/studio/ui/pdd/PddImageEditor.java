package com.nekkora.studio.ui.pdd;

import com.nekkora.studio.domain.layout.MarkerLayout;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.FontWeight;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.ThemeManager;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JComponent;

/**
 * STUDIO-012 PDD-01 — imagem SANITIZADA de um bloco, com o recorte e os marcadores, para "Selecionar área / Ajustar
 * recorte" e "Posicionar marcador". Só mostra o que já passou pela 4ª barreira; nada aqui lê a página nem a automação.
 *
 * <p>Mouse: arrastar desenha o retângulo (em px da imagem). Teclado (com foco): setas movem 8 px, Shift+setas mudam o
 * tamanho, Enter aplica, Esc sai do modo — o recorte e o marcador também são possíveis sem mouse.
 */
public final class PddImageEditor extends JComponent {

    /** O que um arrasto (ou Enter) define. */
    public enum Mode { VIEW, CROP, MARKER }

    /** Marcador desenhado: número da instrução e retângulo em px da imagem; manual = tracejado. */
    public record Mark(int ordinal, Rectangle box, boolean manual) {}

    static final int MAX_WIDTH = 520;
    static final int STEP = 8;

    private BufferedImage image;
    private Rectangle crop; // null = recorte automático
    private List<Mark> marks = List.of();
    private Mode mode = Mode.VIEW;
    private Rectangle pending; // retângulo em edição (px da imagem)
    private int[] anchor;
    private Consumer<Rectangle> onCommit = r -> { };
    private Runnable onExit = () -> { };

    public PddImageEditor() {
        setFocusable(true);
        getAccessibleContext().setAccessibleName("Imagem sanitizada do bloco");
        getAccessibleContext().setAccessibleDescription("No modo de seleção: setas movem, Shift+setas mudam o tamanho, Enter aplica, Esc cancela.");
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (mode == Mode.VIEW || image == null) return;
                anchor = toImage(e.getX(), e.getY());
                pending = new Rectangle(anchor[0], anchor[1], 0, 0);
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (anchor == null) return;
                int[] p = toImage(e.getX(), e.getY());
                pending = new Rectangle(Math.min(anchor[0], p[0]), Math.min(anchor[1], p[1]), Math.abs(p[0] - anchor[0]), Math.abs(p[1] - anchor[1]));
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (anchor == null) return;
                anchor = null;
                commit();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                repaint();
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (mode == Mode.VIEW || image == null) return;
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    exit();
                    e.consume();
                    return;
                }
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    commit();
                    e.consume();
                    return;
                }
                if (pending == null) pending = start();
                int dx = e.getKeyCode() == KeyEvent.VK_LEFT ? -STEP : e.getKeyCode() == KeyEvent.VK_RIGHT ? STEP : 0;
                int dy = e.getKeyCode() == KeyEvent.VK_UP ? -STEP : e.getKeyCode() == KeyEvent.VK_DOWN ? STEP : 0;
                if (dx == 0 && dy == 0) return;
                if (e.isShiftDown()) {
                    pending.width = Math.max(STEP, pending.width + dx);
                    pending.height = Math.max(STEP, pending.height + dy);
                } else {
                    pending.x += dx;
                    pending.y += dy;
                }
                pending = clamp(pending);
                e.consume();
                repaint();
            }
        });
    }

    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public javax.accessibility.AccessibleRole getAccessibleRole() {
                    return javax.accessibility.AccessibleRole.CANVAS;
                }
            };
        }
        return accessibleContext;
    }

    /** Conteúdo a mostrar (na EDT). {@code image == null} esconde o componente. */
    public void setContent(BufferedImage image, Rectangle crop, List<Mark> marks) {
        this.image = image;
        this.crop = crop == null ? null : new Rectangle(crop);
        this.marks = List.copyOf(marks);
        setVisible(image != null);
        revalidate();
        repaint();
    }

    /** Entra num modo de seleção; o retângulo confirmado vai a {@code onCommit} (px da imagem). */
    public void setMode(Mode m, Consumer<Rectangle> onCommit, Runnable onExit) {
        this.mode = Objects.requireNonNull(m);
        this.onCommit = onCommit == null ? r -> { } : onCommit;
        this.onExit = onExit == null ? () -> { } : onExit;
        this.pending = null;
        setCursor(java.awt.Cursor.getPredefinedCursor(m == Mode.VIEW ? java.awt.Cursor.DEFAULT_CURSOR : java.awt.Cursor.CROSSHAIR_CURSOR));
        repaint();
    }

    public Mode mode() {
        return mode;
    }

    /** Retângulo em edição (testes e roteiro de homologação). */
    public Rectangle pending() {
        return pending == null ? null : new Rectangle(pending);
    }

    /** Define o retângulo em edição e confirma (o mesmo caminho do mouse/Enter; usado pelo roteiro de homologação). */
    public void select(Rectangle r) {
        pending = clamp(new Rectangle(r));
        commit();
    }

    private void commit() {
        Rectangle r = pending;
        pending = null;
        if (r != null && r.width > 1 && r.height > 1 && mode != Mode.VIEW) {
            Consumer<Rectangle> c = onCommit;
            exitQuietly();
            c.accept(r);
        }
        repaint();
    }

    private void exit() {
        exitQuietly();
        onExit.run();
    }

    private void exitQuietly() {
        mode = Mode.VIEW;
        pending = null;
        anchor = null;
        setCursor(java.awt.Cursor.getDefaultCursor());
    }

    private Rectangle start() {
        if (mode == Mode.CROP && crop != null) return new Rectangle(crop);
        int w = Math.max(STEP, image.getWidth() / (mode == Mode.CROP ? 2 : 8));
        int h = Math.max(STEP, image.getHeight() / (mode == Mode.CROP ? 2 : 12));
        return new Rectangle((image.getWidth() - w) / 2, (image.getHeight() - h) / 2, w, h);
    }

    private Rectangle clamp(Rectangle r) {
        int x = Math.max(0, Math.min(r.x, image.getWidth() - 1));
        int y = Math.max(0, Math.min(r.y, image.getHeight() - 1));
        int w = Math.max(1, Math.min(r.width, image.getWidth() - x));
        int h = Math.max(1, Math.min(r.height, image.getHeight() - y));
        return new Rectangle(x, y, w, h);
    }

    private double scale() {
        return image == null ? 1 : Math.min(1.0, Math.max(1, getWidth() - 2) / (double) image.getWidth());
    }

    private int[] toImage(int x, int y) {
        double s = scale();
        return new int[] {Math.max(0, Math.min(image.getWidth(), (int) Math.round((x - 1) / s))),
            Math.max(0, Math.min(image.getHeight(), (int) Math.round((y - 1) / s)))};
    }

    @Override
    public Dimension getPreferredSize() {
        if (image == null) return new Dimension(0, 0);
        int w = Math.min(MAX_WIDTH, image.getWidth());
        return new Dimension(w + 2, (int) Math.round(image.getHeight() * (w / (double) image.getWidth())) + 2);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(MAX_WIDTH + 2, getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (image == null) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double s = scale();
        int w = (int) Math.round(image.getWidth() * s);
        int h = (int) Math.round(image.getHeight() * s);
        g2.drawImage(image, 1, 1, w, h, null);
        g2.setColor(ThemeManager.current().color(ColorToken.BORDER_DEFAULT));
        g2.drawRect(0, 0, w + 1, h + 1);
        if (crop != null) { // fora do recorte: esmaecido (a parte que NÃO vai ao PDF)
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
            g2.setColor(ThemeManager.current().color(ColorToken.TEXT_PRIMARY).darker());
            Rectangle c = view(crop, s);
            g2.fillRect(1, 1, w, c.y - 1);
            g2.fillRect(1, c.y + c.height, w, h + 1 - c.y - c.height);
            g2.fillRect(1, c.y, c.x - 1, c.height);
            g2.fillRect(c.x + c.width, c.y, w + 1 - c.x - c.width, c.height);
            g2.setComposite(AlphaComposite.SrcOver);
        }
        g2.setFont(NekkoraFonts.font(TypeStyle.CAPTION, FontWeight.SEMIBOLD));
        Color accent = ThemeManager.current().color(ColorToken.ACCENT_SOLID); // o marcador do PDF é o rosa oficial; no editor, o acento do tema
        // números: mesma regra do PDF (MarkerLayout) — nunca um em cima do outro, dentro do recorte quando há
        int r = 8;
        List<MarkerLayout.Point> centers = badgeCenters(s, w, h, r);
        for (int k = 0; k < marks.size(); k++) {
            Mark m = marks.get(k);
            Rectangle v = view(m.box(), s);
            g2.setColor(accent);
            g2.setStroke(m.manual() ? new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {4f, 3f}, 0f)
                    : new BasicStroke(1.6f));
            g2.drawRect(v.x - 2, v.y - 2, v.width + 3, v.height + 3);
            int cx = (int) Math.round(centers.get(k).x());
            int cy = (int) Math.round(centers.get(k).y());
            g2.fillOval(cx - r, cy - r, 2 * r, 2 * r);
            g2.setColor(ThemeManager.current().color(ColorToken.ACTION_PRIMARY_TEXT));
            String n = String.valueOf(m.ordinal());
            g2.drawString(n, cx - g2.getFontMetrics().stringWidth(n) / 2, cy + g2.getFontMetrics().getAscent() / 2 - 1);
        }
        if (pending != null) {
            Rectangle v = view(pending, s);
            g2.setColor(ThemeManager.current().color(ColorToken.BORDER_FOCUS));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {6f, 4f}, 0f));
            g2.drawRect(v.x, v.y, v.width, v.height);
        }
        if (isFocusOwner()) {
            g2.setColor(ThemeManager.current().color(ColorToken.BORDER_FOCUS));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRect(0, 0, w + 1, h + 1);
        }
        g2.dispose();
    }

    /** Centros dos números (coordenadas da tela do editor). */
    List<MarkerLayout.Point> badgeCenters(double s, int w, int h, int r) {
        Rectangle area = crop != null ? view(crop, s) : new Rectangle(1, 1, w, h);
        List<MarkerLayout.Box> boxes = new java.util.ArrayList<>();
        for (Mark m : marks) {
            Rectangle v = view(m.box(), s);
            boxes.add(new MarkerLayout.Box(v.x - 2, v.y - 2, v.width + 3, v.height + 3));
        }
        return MarkerLayout.place(boxes, new MarkerLayout.Box(area.x, area.y, area.width, area.height), r, 4, List.of());
    }

    /** Centros dos números como estão na tela agora (testes de interface). */
    public List<MarkerLayout.Point> badgeCenters() {
        if (image == null) return List.of();
        double s = scale();
        return badgeCenters(s, (int) Math.round(image.getWidth() * s), (int) Math.round(image.getHeight() * s), 8);
    }

    private static Rectangle view(Rectangle r, double s) {
        return new Rectangle(1 + (int) Math.round(r.x * s), 1 + (int) Math.round(r.y * s), Math.max(1, (int) Math.round(r.width * s)),
                Math.max(1, (int) Math.round(r.height * s)));
    }
}
