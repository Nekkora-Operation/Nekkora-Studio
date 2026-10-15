package com.nekkora.studio.ui.recorder;

import com.nekkora.tokens.Radius;
import com.nekkora.ui.dialog.NekkoraDialogPanel;
import com.nekkora.ui.theme.Scale;
import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JDialog;

/**
 * Floating Recorder: janela pequena, NÃO modal, vinculada à janela do Studio.
 *
 * <ul>
 *   <li>Não captura o foco ao aparecer ({@code autoRequestFocus=false}): o usuário continua no Chrome. Ao ser
 *       clicada, recebe o foco normalmente (teclado e leitores de tela).</li>
 *   <li>"Acima das outras janelas" é uma opção controlada pelo usuário (padrão: ligada), para poder liberar diálogos
 *       do Chrome — como o pedido de permissão — se a janela estiver por cima.</li>
 *   <li>Movível pelo cabeçalho; fechar NÃO encerra a gravação: pede a decisão ao usuário.</li>
 *   <li>Mesma identidade visual do Studio (painel de diálogo do design system, mesmo tema ao vivo).</li>
 * </ul>
 */
public final class FloatingRecorderWindow extends JDialog {

    public static final String TITLE = "Nekkora Recorder";
    private final NekkoraDialogPanel panel = new NekkoraDialogPanel(TITLE);
    private boolean placed;

    public FloatingRecorderWindow(Window owner, FloatingRecorderPanel content, Runnable onCloseRequested) {
        super(owner, TITLE, ModalityType.MODELESS);
        setUndecorated(true);
        try {
            setBackground(new java.awt.Color(0, 0, 0, 0));
        } catch (UnsupportedOperationException ignored) {
            // sem transparência: cantos retos
        }
        panel.setDescription("Gravação técnica controlada pelo Studio");
        panel.setContent(content);
        panel.getCloseButton().getAccessibleContext().setAccessibleName("Fechar o Recorder");
        panel.getCloseButton().addActionListener(e -> onCloseRequested.run());
        setContentPane(panel);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onCloseRequested.run(); // Alt+F4 também pede a decisão: nunca encerra a gravação em silêncio
            }
        });
        setAutoRequestFocus(false);
        setFocusableWindowState(true);
        setAlwaysOnTop(true);
        installDrag();
        getAccessibleContext().setAccessibleName(TITLE);
    }

    /** Mostra (ou restaura) sem tirar o foco do usuário. Na primeira vez, no canto inferior direito da área útil. */
    public void showFloating() {
        fit();
        if (!placed) {
            Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            int m = Scale.px(24);
            setLocation(usable.x + usable.width - getWidth() - m, usable.y + usable.height - getHeight() - m);
            placed = true;
        }
        if (isVisible()) {
            return;
        }
        // O Windows entrega o foco a uma janela recém-exibida cujo dono está ativo, mesmo com autoRequestFocus=false.
        // A janela aparece NÃO focável e volta a aceitar foco logo depois: só o recebe quando o usuário clica nela.
        setFocusableWindowState(false);
        setVisible(true);
        javax.swing.Timer t = new javax.swing.Timer(400, e -> setFocusableWindowState(true));
        t.setRepeats(false);
        t.start();
    }

    /** Ajusta a altura ao conteúdo (largura fixa e compacta). */
    public void fit() {
        int w = Scale.px(400);
        panel.setSize(w, Short.MAX_VALUE);
        panel.doLayout();
        Dimension pref = panel.getPreferredSize();
        setSize(new Dimension(w, Math.max(pref.height, Scale.px(200))));
        applyShape();
    }

    private void applyShape() {
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (gd.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT)) {
            float r = Scale.px(Radius.LARGE) * 2f;
            setShape(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), r, r));
        }
    }

    private void installDrag() {
        MouseAdapter drag = new MouseAdapter() {
            private Point start;

            @Override
            public void mousePressed(MouseEvent e) {
                start = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (start == null || e.getY() > Scale.px(72)) {
                    return;
                }
                Point loc = getLocation();
                setLocation(loc.x + e.getX() - start.x, loc.y + e.getY() - start.y);
            }
        };
        panel.addMouseListener(drag);
        panel.addMouseMotionListener(drag);
    }

    public NekkoraDialogPanel dialogPanel() {
        return panel;
    }
}
