package com.nekkora.ui.button;

import static com.nekkora.tokens.ColorToken.*;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.nekkora.tokens.ColorMath;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.FontRole;
import com.nekkora.tokens.FontWeight;
import com.nekkora.tokens.Radius;
import com.nekkora.ui.core.Interaction;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.internal.InteractionTracker;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.internal.Spin;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.geom.Arc2D;
import javax.swing.AbstractAction;
import javax.swing.DefaultButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;

/**
 * Botão Nekkora. A tela escolhe a <b>intenção</b> ({@link Variant}) e o tamanho; cor, raio,
 * fonte, estados e foco vêm do tema.
 *
 * <pre>{@code
 * new NekkoraButton("Salvar", NekkoraButton.Variant.PRIMARY);
 * NekkoraButton.secondary("Cancelar");
 * NekkoraButton.danger("Excluir").withIcon(IconName.TRASH);
 * }</pre>
 *
 * <p>Estados: padrão, hover, pressionado, foco (anel), desabilitado, carregando
 * ({@link #setLoading}) e selecionado (quando {@link #setToggle(boolean) alternável}).
 * Teclado: Espaço e Enter acionam o botão focado.
 */
public class NekkoraButton extends JButton {

    /** Intenção do botão. */
    public enum Variant {
        /** A ação principal da área — no máximo uma por contexto. */
        PRIMARY,
        /** Ações de apoio. */
        SECONDARY,
        /** Ações discretas (toolbars, cabeçalhos, "Cancelar" em diálogos densos). */
        GHOST,
        /** Ações destrutivas. */
        DANGER,
        /** Navegação/ação com aparência de link. */
        LINK
    }

    private Variant variant;
    private ControlSize size = ControlSize.MEDIUM;
    private IconName leadingIcon;
    private IconName trailingIcon;
    private boolean loading;
    private boolean iconOnly;
    private InteractionTracker tracker;
    private Spin spin;

    public NekkoraButton(String text) {
        this(text, Variant.SECONDARY);
    }

    public NekkoraButton(String text, Variant variant) {
        this(text, null, variant);
    }

    public NekkoraButton(String text, IconName icon, Variant variant) {
        super(text);
        this.variant = variant;
        this.leadingIcon = icon;
        init();
    }

    public static NekkoraButton primary(String text) {
        return new NekkoraButton(text, Variant.PRIMARY);
    }

    public static NekkoraButton secondary(String text) {
        return new NekkoraButton(text, Variant.SECONDARY);
    }

    public static NekkoraButton ghost(String text) {
        return new NekkoraButton(text, Variant.GHOST);
    }

    public static NekkoraButton danger(String text) {
        return new NekkoraButton(text, Variant.DANGER);
    }

    public static NekkoraButton link(String text) {
        return new NekkoraButton(text, Variant.LINK);
    }

    private void init() {
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setBorder(null);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tracker = InteractionTracker.install(this);
        spin = new Spin(this);
        // Enter também aciona o botão focado (Espaço já é padrão do Swing)
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "nekkora.click");
        getActionMap().put("nekkora.click", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isEnabled()) doClick(0);
            }
        });
        applyStyle();
    }

    // ------------------------------------------------------------------ API fluente

    public NekkoraButton withIcon(IconName icon) {
        setLeadingIcon(icon);
        return this;
    }

    public NekkoraButton withTrailingIcon(IconName icon) {
        setTrailingIcon(icon);
        return this;
    }

    public NekkoraButton withSize(ControlSize size) {
        setControlSize(size);
        return this;
    }

    public NekkoraButton onClick(ActionListener l) {
        addActionListener(l);
        return this;
    }

    // ------------------------------------------------------------------ Propriedades

    public Variant getVariant() {
        return variant;
    }

    public void setVariant(Variant variant) {
        this.variant = variant;
        applyStyle();
        repaint();
    }

    public ControlSize getControlSize() {
        return size;
    }

    public void setControlSize(ControlSize size) {
        this.size = size;
        applyStyle();
        revalidate();
        repaint();
    }

    public IconName getLeadingIcon() {
        return leadingIcon;
    }

    public void setLeadingIcon(IconName icon) {
        this.leadingIcon = icon;
        revalidate();
        repaint();
    }

    public IconName getTrailingIcon() {
        return trailingIcon;
    }

    public void setTrailingIcon(IconName icon) {
        this.trailingIcon = icon;
        revalidate();
        repaint();
    }

    public boolean isLoading() {
        return loading;
    }

    /** Estado "carregando": mostra spinner e ignora cliques, sem parecer desabilitado. */
    public void setLoading(boolean loading) {
        this.loading = loading;
        spin.setActive(loading);
        setCursor(Cursor.getPredefinedCursor(loading ? Cursor.WAIT_CURSOR : Cursor.HAND_CURSOR));
        getAccessibleContext().setAccessibleDescription(loading ? "Carregando" : null);
        repaint();
    }

    /** Torna o botão alternável (liga/desliga), com estado {@link #isSelected()}. */
    public void setToggle(boolean toggle) {
        boolean selected = isSelected();
        setModel(toggle ? new JToggleButton.ToggleButtonModel() : new DefaultButtonModel());
        setSelected(toggle && selected);
    }

    protected void setIconOnly(boolean iconOnly) {
        this.iconOnly = iconOnly;
        revalidate();
    }

    public boolean isIconOnly() {
        return iconOnly;
    }

    @Override
    protected void fireActionPerformed(ActionEvent event) {
        if (!loading) super.fireActionPerformed(event);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyStyle();
    }

    private void applyStyle() {
        if (variant == null) return;
        setFont(NekkoraFonts.font(FontRole.UI, FontWeight.MEDIUM, fontSize()));
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setBorder(null);
    }

    private float fontSize() {
        return switch (size) {
            case SMALL -> 12f;
            case MEDIUM -> 13f;
            case LARGE -> 14f;
        };
    }

    // ------------------------------------------------------------------ Medidas

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        int fw = Math.round(Paint.focusWidth());
        int h = Scale.height(size);
        if (iconOnly) return new Dimension(h + 2 * fw, h + 2 * fw);
        FontMetrics fm = getFontMetrics(getFont());
        String text = getText();
        int w = text == null || text.isEmpty() ? 0 : fm.stringWidth(text);
        int icon = Scale.px(size.iconSize());
        int gap = Scale.px(size == ControlSize.SMALL ? 6 : 8);
        if (hasLeading()) w += icon + (w > 0 ? gap : 0);
        if (trailingIcon != null) w += icon + gap;
        int pad = variant == Variant.LINK ? Scale.px(2) : Scale.px(size.paddingX());
        w += 2 * pad;
        if (variant != Variant.LINK && text != null && !text.isEmpty()) w = Math.max(w, Scale.px(64));
        return new Dimension(w + 2 * fw, h + 2 * fw);
    }

    @Override
    public Dimension getMinimumSize() {
        return isMinimumSizeSet() ? super.getMinimumSize() : getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return isMaximumSizeSet() ? super.getMaximumSize() : getPreferredSize();
    }

    private boolean hasLeading() {
        return leadingIcon != null || loading;
    }

    // ------------------------------------------------------------------ Pintura

    /** Cores resolvidas para o estado atual: {fundo, texto, borda}. */
    protected Color[] resolveColors(Interaction state) {
        boolean selected = getModel().isSelected();
        ColorToken bg, fg, border = null;
        switch (variant) {
            case PRIMARY -> {
                bg = state == Interaction.PRESSED ? ACTION_PRIMARY_PRESSED
                        : state == Interaction.HOVER ? ACTION_PRIMARY_HOVER : ACTION_PRIMARY;
                fg = ACTION_PRIMARY_TEXT;
            }
            case SECONDARY -> {
                bg = state == Interaction.PRESSED ? ACTION_SECONDARY_PRESSED
                        : state == Interaction.HOVER ? ACTION_SECONDARY_HOVER : ACTION_SECONDARY;
                fg = ACTION_SECONDARY_TEXT;
                border = ACTION_SECONDARY_BORDER;
            }
            case DANGER -> {
                bg = state == Interaction.PRESSED ? ACTION_DANGER_PRESSED
                        : state == Interaction.HOVER ? ACTION_DANGER_HOVER : ACTION_DANGER;
                fg = ACTION_DANGER_TEXT;
            }
            case LINK -> {
                bg = null;
                fg = TEXT_LINK;
            }
            default -> { // GHOST
                if (selected) {
                    bg = ACCENT_SOFT;
                    fg = TEXT_PRIMARY;
                } else {
                    bg = state == Interaction.PRESSED ? BACKGROUND_PRESSED
                            : state == Interaction.HOVER ? BACKGROUND_HOVER : null;
                    fg = state == Interaction.DEFAULT ? TEXT_SECONDARY : TEXT_PRIMARY;
                }
            }
        }
        return new Color[] {
                bg == null ? null : Tokens.color(bg),
                Tokens.color(fg),
                border == null ? null : Tokens.color(border)};
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Paint.prepare(g);
        try {
            float fw = Paint.focusWidth();
            float x = fw, y = fw, w = getWidth() - 2 * fw, h = getHeight() - 2 * fw;
            float r = Scale.px(size == ControlSize.SMALL ? Radius.SMALL : Radius.MEDIUM);
            Interaction state = tracker.state();
            Color[] c = resolveColors(state);

            if (tracker.isFocused() && isEnabled()) Paint.focusRing(g2, x, y, w, h, r);
            if (!isEnabled()) Paint.disabledComposite(g2);
            else if (loading) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));

            Paint.fillRound(g2, x, y, w, h, r, c[0]);
            Paint.strokeRound(g2, x, y, w, h, r, c[2]);
            paintContent(g2, x, y, w, h, c[1], state);
        } finally {
            g2.dispose();
        }
    }

    private void paintContent(Graphics2D g, float x, float y, float w, float h, Color fg, Interaction state) {
        FontMetrics fm = g.getFontMetrics(getFont());
        g.setFont(getFont());
        String text = iconOnly ? null : getText();
        boolean hasText = text != null && !text.isEmpty();
        int icon = Scale.px(size.iconSize());
        int gap = Scale.px(size == ControlSize.SMALL ? 6 : 8);
        int pad = variant == Variant.LINK ? Scale.px(2) : Scale.px(size.paddingX());
        int maxText = Math.max(0, Math.round(w) - 2 * pad - (hasLeading() ? icon + gap : 0) - (trailingIcon != null ? icon + gap : 0));
        if (hasText) text = Paint.ellipsize(text, fm, maxText);
        int textW = hasText ? fm.stringWidth(text) : 0;
        int total = textW + (hasLeading() ? icon + (hasText ? gap : 0) : 0) + (trailingIcon != null ? icon + gap : 0);
        float cx = x + (w - total) / 2f;
        float iconY = y + (h - icon) / 2f;

        if (hasLeading()) {
            if (loading) paintSpinner(g, cx, iconY, icon, fg);
            else NekkoraIcon.of(leadingIcon, size.iconSize()).paint(g, Math.round(cx), Math.round(iconY), icon, fg, fg);
            cx += icon + (hasText ? gap : 0);
        }
        if (hasText) {
            int baseline = Paint.centeredBaseline(fm, Math.round(y), Math.round(h));
            g.setColor(fg);
            FlatUIUtils.drawStringUnderlineCharAt(this, g, text, getDisplayedMnemonicIndex(), Math.round(cx), baseline);
            if (variant == Variant.LINK && state != Interaction.DEFAULT) {
                int uy = baseline + Scale.px(2);
                g.fillRect(Math.round(cx), uy, textW, Math.max(1, Scale.px(1)));
            }
            cx += textW + gap;
        }
        if (trailingIcon != null) {
            NekkoraIcon.of(trailingIcon, size.iconSize()).paint(g, Math.round(cx), Math.round(iconY), icon, fg, fg);
        }
    }

    private void paintSpinner(Graphics2D g, float x, float y, int s, Color fg) {
        float inset = s * 0.12f;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setStroke(Paint.stroke(size == ControlSize.SMALL ? 1.6f : 2f));
        g2.setColor(ColorMath.alpha(fg, 0.25));
        g2.draw(new Arc2D.Float(x + inset, y + inset, s - 2 * inset, s - 2 * inset, 0, 360, Arc2D.OPEN));
        g2.setColor(fg);
        g2.draw(new Arc2D.Float(x + inset, y + inset, s - 2 * inset, s - 2 * inset, (float) -spin.angle(), 100, Arc2D.OPEN));
        g2.dispose();
    }
}
