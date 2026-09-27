package com.nekkora.ui.dialog;

import com.nekkora.tokens.Radius;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.theme.Scale;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.BooleanSupplier;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Diálogo modal Nekkora: cabeçalho com título/descrição, conteúdo e ações no rodapé.
 * Esc cancela, Enter aciona a ação primária, o foco volta ao componente de origem ao fechar.
 *
 * <pre>{@code
 * NekkoraDialog d = new NekkoraDialog(parent, "Novo projeto");
 * d.setDescription("Dê um nome e escolha a pasta.");
 * d.setContent(form);
 * d.addSecondary("Cancelar");
 * d.addPrimary("Criar", () -> validar());   // false mantém aberto
 * if (d.showDialog() == NekkoraDialog.Result.CONFIRMED) { ... }
 * }</pre>
 */
public class NekkoraDialog extends JDialog {

    public enum Result { CONFIRMED, CANCELLED }

    /** Larguras de diálogo. */
    public enum Size {
        SMALL(420),
        MEDIUM(540),
        LARGE(760);

        private final int width;

        Size(int width) {
            this.width = width;
        }
    }

    private final NekkoraDialogPanel panel;
    private final Component origin;
    private Result result = Result.CANCELLED;
    private Size size = Size.MEDIUM;
    private NekkoraButton primary;

    public NekkoraDialog(Component parent, String title) {
        super(parent == null ? null : SwingUtilities.getWindowAncestor(parent), title, ModalityType.APPLICATION_MODAL);
        this.origin = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        panel = new NekkoraDialogPanel(title);
        setUndecorated(true);
        setContentPane(panel);
        try {
            setBackground(new java.awt.Color(0, 0, 0, 0));
        } catch (UnsupportedOperationException ignored) {
            // sem transparência: cantos retos
        }
        panel.getCloseButton().addActionListener(e -> cancel());
        JComponent root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "nekkora.cancel");
        root.getActionMap().put("nekkora.cancel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cancel();
            }
        });
        installDrag();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (origin != null && origin.isShowing()) origin.requestFocusInWindow();
            }
        });
        getAccessibleContext().setAccessibleName(title);
    }

    public NekkoraDialogPanel getPanel() {
        return panel;
    }

    public void setDescription(String text) {
        panel.setDescription(text);
    }

    public void setContent(JComponent content) {
        panel.setContent(content);
    }

    public void setDialogSize(Size size) {
        this.size = size;
    }

    public void setMarker(IconName icon, Tone tone) {
        panel.setMarker(icon, tone);
    }

    /** Botão secundário que cancela e fecha. */
    public NekkoraButton addSecondary(String label) {
        NekkoraButton b = NekkoraButton.secondary(label);
        b.addActionListener(e -> cancel());
        panel.addFooterButton(b);
        return b;
    }

    /** Ação primária; {@code validate} devolve {@code false} para manter o diálogo aberto. */
    public NekkoraButton addPrimary(String label, BooleanSupplier validate) {
        return addConfirm(NekkoraButton.primary(label), validate);
    }

    public NekkoraButton addPrimary(String label) {
        return addPrimary(label, () -> true);
    }

    /** Ação primária destrutiva (vermelha). */
    public NekkoraButton addDanger(String label, BooleanSupplier validate) {
        return addConfirm(NekkoraButton.danger(label), validate);
    }

    private NekkoraButton addConfirm(NekkoraButton b, BooleanSupplier validate) {
        b.addActionListener(e -> {
            if (validate == null || validate.getAsBoolean()) confirm();
        });
        panel.addFooterButton(b);
        primary = b;
        getRootPane().setDefaultButton(b);
        return b;
    }

    public NekkoraButton getPrimaryButton() {
        return primary;
    }

    /** Mostra (modal) e devolve o resultado. */
    public Result showDialog() {
        pack();
        Dimension pref = getPreferredSize();
        int w = Scale.px(size.width);
        setSize(new Dimension(w, Math.max(pref.height, getHeightForWidth(w))));
        applyShape();
        setLocationRelativeTo(getOwner());
        setVisible(true);
        return result;
    }

    public void confirm() {
        result = Result.CONFIRMED;
        dispose();
    }

    public void cancel() {
        result = Result.CANCELLED;
        dispose();
    }

    public Result getResult() {
        return result;
    }

    private int getHeightForWidth(int w) {
        panel.setSize(w, Short.MAX_VALUE);
        panel.doLayout();
        return panel.getPreferredSize().height;
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
                if (start == null || e.getY() > Scale.px(72)) return;
                Point loc = getLocation();
                setLocation(loc.x + e.getX() - start.x, loc.y + e.getY() - start.y);
            }
        };
        panel.addMouseListener(drag);
        panel.addMouseMotionListener(drag);
    }
}
