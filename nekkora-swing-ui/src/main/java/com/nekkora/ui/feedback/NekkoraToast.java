package com.nekkora.ui.feedback;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.Elevation;
import com.nekkora.tokens.FontWeight;
import com.nekkora.tokens.Motion;
import com.nekkora.tokens.Radius;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.button.NekkoraIconButton;
import com.nekkora.ui.core.Severity;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.IconSize;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.internal.Animator;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import com.nekkora.ui.typography.NekkoraParagraph;
import com.nekkora.ui.typography.NekkoraText;
import com.nekkora.ui.typography.TextTone;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import javax.accessibility.AccessibleRole;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Toast: confirmação breve e não bloqueante, no canto inferior direito da janela. Some sozinho
 * (pausa com o mouse em cima). Para erros que exigem ação, prefira um diálogo ou alerta.
 *
 * <pre>{@code
 * NekkoraToast.show(anyComponent, Severity.SUCCESS, "Projeto salvo", null);
 * }</pre>
 */
public class NekkoraToast extends JPanel {
    private static final WeakHashMap<JRootPane, List<NekkoraToast>> STACKS = new WeakHashMap<>();
    private static final int WIDTH = 360;
    private final Severity severity;
    private final Timer autoClose;
    private float alpha = 1f;
    private JRootPane root;

    public NekkoraToast(Severity severity, String title, String message) {
        super(new BorderLayout(Scale.px(Spacing.SPACE_5), 0));
        this.severity = severity;
        setOpaque(false);
        int m = shadow();
        setBorder(BorderFactory.createEmptyBorder(Scale.px(12) + m / 2, Scale.px(14) + m / 2, Scale.px(12) + m, Scale.px(8) + m / 2));
        JLabel icon = new JLabel(NekkoraIcon.of(severity.icon(), IconSize.L).withColor(severity.tone().foreground()));
        icon.setVerticalAlignment(JLabel.TOP);
        JPanel texts = new JPanel();
        texts.setOpaque(false);
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        NekkoraText t = new NekkoraText(title);
        t.setWeight(FontWeight.SEMIBOLD);
        t.setAlignmentX(LEFT_ALIGNMENT);
        texts.add(t);
        if (message != null) {
            NekkoraParagraph p = new NekkoraParagraph(message, TextTone.SECONDARY);
            p.setColumnsHint(34);
            p.setAlignmentX(LEFT_ALIGNMENT);
            texts.add(p);
        }
        NekkoraIconButton close = new NekkoraIconButton(IconName.CLOSE, "Fechar notificação").withSize(ControlSize.SMALL);
        close.addActionListener(e -> dismiss());
        JPanel east = new JPanel(new BorderLayout());
        east.setOpaque(false);
        east.add(close, BorderLayout.NORTH);
        add(icon, BorderLayout.WEST);
        add(texts, BorderLayout.CENTER);
        add(east, BorderLayout.EAST);
        autoClose = new Timer(severity == Severity.ERROR ? 8000 : 4500, e -> dismiss());
        autoClose.setRepeats(false);
        MouseAdapter pause = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                autoClose.stop();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                autoClose.restart();
            }
        };
        addMouseListener(pause);
        getAccessibleContext().setAccessibleName(severity.label() + ": " + title);
    }

    /** Mostra um toast na janela de {@code anyComponent}. */
    public static NekkoraToast show(Component anyComponent, Severity severity, String title, String message) {
        NekkoraToast toast = new NekkoraToast(severity, title, message);
        JRootPane root = SwingUtilities.getRootPane(anyComponent);
        if (root == null) return toast;
        toast.root = root;
        List<NekkoraToast> stack = STACKS.computeIfAbsent(root, r -> new ArrayList<>());
        stack.add(toast);
        root.getLayeredPane().add(toast, JLayeredPane.POPUP_LAYER);
        layout(root);
        toast.alpha = 0f;
        new Animator(Motion.NORMAL, t -> {
            toast.alpha = (float) t;
            toast.repaint();
        }).start();
        toast.autoClose.start();
        return toast;
    }

    /** Fecha o toast. */
    public void dismiss() {
        autoClose.stop();
        if (root == null) return;
        JRootPane r = root;
        new Animator(Motion.FAST, t -> {
            alpha = (float) (1 - t);
            repaint();
        }).onEnd(() -> {
            r.getLayeredPane().remove(this);
            List<NekkoraToast> stack = STACKS.get(r);
            if (stack != null) stack.remove(this);
            layout(r);
            r.getLayeredPane().repaint();
        }).start();
        root = null;
    }

    public Severity getSeverity() {
        return severity;
    }

    private static void layout(JRootPane root) {
        List<NekkoraToast> stack = STACKS.getOrDefault(root, List.of());
        JLayeredPane lp = root.getLayeredPane();
        int margin = Scale.px(Spacing.SPACE_7);
        int y = lp.getHeight() - margin;
        for (int i = stack.size() - 1; i >= 0; i--) {
            NekkoraToast t = stack.get(i);
            Dimension d = t.getPreferredSize();
            int w = Math.min(Scale.px(WIDTH), lp.getWidth() - 2 * margin);
            d = new Dimension(w, t.heightFor(w));
            y -= d.height;
            t.setBounds(lp.getWidth() - margin - d.width, y, d.width, d.height);
            y -= Scale.px(Spacing.SPACE_4);
        }
        lp.revalidate();
    }

    private int heightFor(int width) {
        setSize(width, Short.MAX_VALUE);
        doLayout();
        return getPreferredSize().height;
    }

    private static int shadow() {
        return Scale.px(Elevation.LEVEL_2.blur() / 2 + Elevation.LEVEL_2.offsetY());
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
        super.paint(g2);
        g2.dispose();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Paint.prepare(g);
        int m = shadow();
        float x = m / 2f, y = m / 2f, w = getWidth() - m, h = getHeight() - m * 1.5f;
        float r = Scale.px(Radius.LARGE);
        Paint.shadow(g2, x, y, w, h, r, Elevation.LEVEL_2);
        Paint.fillRound(g2, x, y, w, h, r, Tokens.color(ColorToken.BACKGROUND_ELEVATED));
        Paint.strokeRound(g2, x, y, w, h, r, Tokens.color(ColorToken.BORDER_DEFAULT));
        g2.setColor(Tokens.color(severity.tone().solid()));
        g2.fill(new java.awt.geom.RoundRectangle2D.Float(x, y + Scale.px(10), Scale.px(3), h - Scale.px(20), Scale.px(3), Scale.px(3)));
        g2.dispose();
    }

    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJPanel() {
                @Override
                public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.ALERT;
                }
            };
        }
        return accessibleContext;
    }

    /** Botão de ação dentro do toast (ex.: "Desfazer"). */
    public NekkoraToast withAction(String label, Runnable action) {
        NekkoraButton b = NekkoraButton.link(label).withSize(ControlSize.SMALL);
        b.addActionListener(e -> {
            action.run();
            dismiss();
        });
        ((JPanel) ((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.CENTER)).add(b);
        if (root != null) layout(root);
        return this;
    }
}
