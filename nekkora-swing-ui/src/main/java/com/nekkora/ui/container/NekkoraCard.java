package com.nekkora.ui.container;

import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Elevation;
import com.nekkora.tokens.Radius;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.core.Interaction;
import com.nekkora.ui.core.NekkoraUI;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import com.nekkora.ui.typography.NekkoraHeading;
import com.nekkora.ui.typography.NekkoraText;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/**
 * Card: agrupa conteúdo relacionado em uma superfície com contorno sutil e cantos grandes.
 * Pode ter cabeçalho (título, subtítulo, ações), elevação e ser clicável ({@link #setOnClick}).
 *
 * <pre>{@code
 * NekkoraCard card = new NekkoraCard("Execuções", "Últimas 24 horas");
 * card.setHeaderActions(new NekkoraIconButton(IconName.MORE_HORIZONTAL, "Mais opções"));
 * card.setContent(tabela);
 * }</pre>
 */
public class NekkoraCard extends NekkoraPanel {
    private final JPanel body = new JPanel(new BorderLayout());
    private JPanel header;
    private NekkoraHeading title;
    private NekkoraText subtitle;
    private JPanel actions;
    private Elevation elevation = Elevation.LEVEL_0;
    private Spacing padding = Spacing.SPACE_6;
    private Runnable onClick;
    private boolean hover;

    public NekkoraCard() {
        super(new BorderLayout());
        body.setOpaque(false);
        add(body, BorderLayout.CENTER);
        setSurface(Background.SURFACE);
        setOutline(ColorToken.BORDER_SUBTLE);
        setRadius(Radius.LARGE);
        applyPadding();
    }

    public NekkoraCard(String titleText, String subtitleText) {
        this();
        setTitle(titleText, subtitleText);
    }

    /** Título (H3) e subtítulo opcional no topo do card. */
    public void setTitle(String titleText, String subtitleText) {
        ensureHeader();
        title.setText(titleText);
        subtitle.setText(subtitleText);
        subtitle.setVisible(subtitleText != null);
    }

    /** Ações no canto direito do cabeçalho (botões de ícone, menus). */
    public void setHeaderActions(JComponent... components) {
        ensureHeader();
        actions.removeAll();
        for (JComponent c : components) actions.add(c);
        actions.revalidate();
    }

    /** Conteúdo principal do card. */
    public void setContent(JComponent content) {
        body.removeAll();
        body.add(content, BorderLayout.CENTER);
        body.revalidate();
        body.repaint();
    }

    /** Rodapé (ex.: ações do card). */
    public void setFooter(JComponent footer) {
        footer.setBorder(BorderFactory.createEmptyBorder(Scale.px(Spacing.SPACE_5), 0, 0, 0));
        body.add(footer, BorderLayout.SOUTH);
    }

    @Override
    public NekkoraPanel setPadding(Spacing padding) {
        this.padding = padding;
        applyPadding();
        return this;
    }

    public void setElevation(Elevation elevation) {
        this.elevation = elevation;
        applyPadding();
        repaint();
    }

    /** Torna o card clicável (hover, cursor de mão, Enter/Espaço quando focado). */
    public void setOnClick(Runnable action) {
        boolean first = onClick == null;
        this.onClick = action;
        if (!first) return;
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (onClick != null) onClick.run();
            }
        });
        for (String key : new String[] {"ENTER", "SPACE"}) {
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), "nekkora.activate");
        }
        getActionMap().put("nekkora.activate", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (onClick != null) onClick.run();
            }
        });
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
    }

    private void ensureHeader() {
        if (header != null) return;
        header = new JPanel(new BorderLayout(Scale.px(Spacing.SPACE_5), 0));
        header.setOpaque(false);
        JPanel texts = new JPanel();
        texts.setOpaque(false);
        texts.setLayout(new javax.swing.BoxLayout(texts, javax.swing.BoxLayout.Y_AXIS));
        title = new NekkoraHeading("", NekkoraHeading.Level.H3);
        subtitle = NekkoraText.secondary("");
        subtitle.setTypeStyle(com.nekkora.tokens.TypeStyle.BODY_S);
        texts.add(title);
        texts.add(subtitle);
        actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, Scale.px(4), 0));
        actions.setOpaque(false);
        header.add(texts, BorderLayout.CENTER);
        header.add(actions, BorderLayout.EAST);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, Scale.px(Spacing.SPACE_5), 0));
        add(header, BorderLayout.NORTH);
    }

    private int shadowMargin() {
        return elevation == Elevation.LEVEL_0 ? 0 : Scale.px(elevation.blur() / 2 + elevation.offsetY());
    }

    private void applyPadding() {
        if (padding == null) return;
        int p = Scale.px(padding), m = shadowMargin();
        setBorder(BorderFactory.createEmptyBorder(p + m / 2, p + m / 2, p + m, p + m / 2));
    }

    @Override
    protected void applyStyle() {
        super.applyStyle();
        applyPadding();
    }

    private boolean isHover() {
        return hover || getClientProperty(NekkoraUI.FORCED_INTERACTION) == Interaction.HOVER;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Paint.prepare(g);
        int m = shadowMargin();
        float x = m / 2f, y = m / 2f, w = getWidth() - m, h = getHeight() - m * 1.5f;
        float r = Scale.px(Radius.LARGE);
        Paint.shadow(g2, x, y, w, h, r, elevation);
        Paint.fillRound(g2, x, y, w, h, r, Tokens.color(ColorToken.BACKGROUND_SURFACE));
        if (onClick != null && isHover()) Paint.fillRound(g2, x, y, w, h, r, Tokens.color(ColorToken.BACKGROUND_HOVER));
        boolean focused = onClick != null && (isFocusOwner() || Boolean.TRUE.equals(getClientProperty(NekkoraUI.FORCED_FOCUS)));
        ColorToken border = focused ? ColorToken.BORDER_FOCUS
                : onClick != null && isHover() ? ColorToken.BORDER_DEFAULT : ColorToken.BORDER_SUBTLE;
        Paint.strokeRound(g2, x, y, w, h, r, Tokens.color(border), focused ? Scale.pxf(1.5f) : Scale.pxf(1));
        g2.dispose();
    }
}
