package com.nekkora.studio.ui.debug;

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
 * STUDIO-009 G3 — janela flutuante do debug guiado: pequena, NÃO modal, acima das outras janelas, movível pelo cabeçalho,
 * sem roubar o foco do Chrome ao aparecer ou se reposicionar (mesmo padrão do Floating Recorder). Afasta-se do elemento
 * destacado ({@link #avoid}) para nunca tapá-lo de forma permanente.
 */
public final class GuidedDebugWindow extends JDialog {

    public static final String TITLE = "Nekkora · Debug guiado";
    private final NekkoraDialogPanel panel = new NekkoraDialogPanel(TITLE);
    private boolean placed;
    private boolean moved;

    public GuidedDebugWindow(Window owner, GuidedDebugPanel content, Runnable onCloseRequested) {
        super(owner, TITLE, ModalityType.MODELESS);
        setUndecorated(true);
        try {
            setBackground(new java.awt.Color(0, 0, 0, 0));
        } catch (UnsupportedOperationException ignored) {
            // sem transparência: cantos retos
        }
        panel.setDescription("Inspeciona e destaca no Chrome; nada é executado na página");
        panel.setContent(content);
        panel.getCloseButton().getAccessibleContext().setAccessibleName("Encerrar o debug guiado");
        panel.getCloseButton().addActionListener(e -> onCloseRequested.run());
        setContentPane(panel);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onCloseRequested.run();
            }
        });
        setAutoRequestFocus(false);
        setFocusableWindowState(true);
        setAlwaysOnTop(true);
        installDrag();
        getAccessibleContext().setAccessibleName(TITLE);
    }

    /** Mostra sem tirar o foco do usuário (primeira vez: canto superior direito da área útil). */
    public void showFloating() {
        fit();
        if (!placed) {
            Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            int m = Scale.px(24);
            setLocation(usable.x + usable.width - getWidth() - m, usable.y + m);
            placed = true;
        }
        if (isVisible()) {
            return;
        }
        setFocusableWindowState(false);
        setVisible(true);
        javax.swing.Timer t = new javax.swing.Timer(400, e -> setFocusableWindowState(true));
        t.setRepeats(false);
        t.start();
    }

    /** Ajusta a altura ao conteúdo (largura fixa e compacta; altura limitada à área útil). */
    public void fit() {
        int w = Scale.px(420);
        panel.setSize(w, Short.MAX_VALUE);
        panel.doLayout();
        Dimension pref = panel.getPreferredSize();
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(new Dimension(w, Math.min(usable.height - Scale.px(32), Math.max(pref.height, Scale.px(220)))));
        applyShape();
    }

    /**
     * Se a janela cobre o elemento destacado (retângulo em coordenadas de tela), move-se para o lado oposto da área útil,
     * sem pegar o foco. Respeita a posição escolhida pelo usuário enquanto ela não cobre o alvo.
     */
    public void avoid(Rectangle target) {
        if (target == null || !isVisible() || !getBounds().intersects(target)) {
            return;
        }
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int m = Scale.px(24);
        int left = usable.x + m;
        int right = usable.x + usable.width - getWidth() - m;
        int x = target.getCenterX() > usable.getCenterX() ? left : right;
        int y = Math.max(usable.y + m, Math.min(getY(), usable.y + usable.height - getHeight() - m));
        Rectangle candidate = new Rectangle(x, y, getWidth(), getHeight());
        if (candidate.intersects(target)) {
            // alvo largo: abaixo ou acima dele
            int below = target.y + target.height + m;
            y = below + getHeight() <= usable.y + usable.height ? below : Math.max(usable.y + m, target.y - getHeight() - m);
        }
        setLocation(x, y);
    }

    public boolean movedByUser() {
        return moved;
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
                moved = true;
            }
        };
        panel.addMouseListener(drag);
        panel.addMouseMotionListener(drag);
    }

    public NekkoraDialogPanel dialogPanel() {
        return panel;
    }
}
