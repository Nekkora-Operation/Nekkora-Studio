package com.nekkora.ui.navigation;

import com.formdev.flatlaf.FlatClientProperties;
import com.nekkora.tokens.FontRole;
import com.nekkora.tokens.FontWeight;
import com.nekkora.ui.action.NekkoraKeys;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.IconSize;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.theme.NekkoraFonts;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.util.function.IntPredicate;
import javax.swing.JTabbedPane;

/**
 * Abas. Estilo sublinhado (padrão) ou "card" para áreas de documento. Com
 * {@link #setClosable} ganha botão fechar (e Ctrl+W); Ctrl+Tab / Ctrl+Shift+Tab alternam.
 */
public class NekkoraTabs extends JTabbedPane {

    public enum Style { UNDERLINED, CARD }

    private IntPredicate onClose;
    private boolean initialized;
    private Style style = Style.UNDERLINED;

    public NekkoraTabs() {
        this(Style.UNDERLINED);
    }

    public NekkoraTabs(Style style) {
        super(TOP, SCROLL_TAB_LAYOUT);
        this.style = style;
        initialized = true;
        applyStyle();
        NekkoraKeys.bind(this, NekkoraKeys.shortcut(KeyEvent.VK_TAB), "nekkora.tabs.next", () -> step(1));
        NekkoraKeys.bind(this, NekkoraKeys.shortcut(KeyEvent.VK_TAB, KeyEvent.SHIFT_DOWN_MASK), "nekkora.tabs.prev", () -> step(-1));
    }

    public void addTab(String title, IconName icon, Component content) {
        addTab(title, icon == null ? null : NekkoraIcon.of(icon, IconSize.M), content);
    }

    /**
     * Abas fecháveis. {@code onClose} recebe o índice e devolve {@code true} para fechar
     * (use para confirmar alterações não salvas).
     */
    public void setClosable(boolean closable, IntPredicate onClose) {
        this.onClose = onClose;
        putClientProperty(FlatClientProperties.TABBED_PANE_TAB_CLOSABLE, closable);
        putClientProperty(FlatClientProperties.TABBED_PANE_TAB_CLOSE_TOOLTIPTEXT, "Fechar");
        putClientProperty(FlatClientProperties.TABBED_PANE_TAB_CLOSE_CALLBACK,
                (java.util.function.BiConsumer<JTabbedPane, Integer>) (tabs, index) -> closeTab(index));
        if (closable) {
            NekkoraKeys.bind(this, NekkoraKeys.shortcut(KeyEvent.VK_W), "nekkora.tabs.close", () -> {
                if (getSelectedIndex() >= 0) closeTab(getSelectedIndex());
            });
        }
    }

    /** Fecha a aba respeitando o callback de confirmação. */
    public void closeTab(int index) {
        if (index < 0 || index >= getTabCount()) return;
        if (onClose == null || onClose.test(index)) removeTabAt(index);
    }

    public void setStyle(Style style) {
        this.style = style;
        applyStyle();
    }

    private void step(int delta) {
        int n = getTabCount();
        if (n > 1) setSelectedIndex(Math.floorMod(getSelectedIndex() + delta, n));
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyStyle();
    }

    private void applyStyle() {
        if (!initialized) return;
        setFont(NekkoraFonts.font(FontRole.UI, FontWeight.MEDIUM, 13f));
        putClientProperty(FlatClientProperties.TABBED_PANE_TAB_TYPE,
                style == Style.CARD ? FlatClientProperties.TABBED_PANE_TAB_TYPE_CARD
                        : FlatClientProperties.TABBED_PANE_TAB_TYPE_UNDERLINED);
        putClientProperty(FlatClientProperties.TABBED_PANE_SHOW_CONTENT_SEPARATOR, true);
        putClientProperty(FlatClientProperties.TABBED_PANE_SCROLL_BUTTONS_PLACEMENT, FlatClientProperties.TABBED_PANE_PLACEMENT_BOTH);
        putClientProperty("JTabbedPane.tabIconPlacement", LEADING);
    }
}
