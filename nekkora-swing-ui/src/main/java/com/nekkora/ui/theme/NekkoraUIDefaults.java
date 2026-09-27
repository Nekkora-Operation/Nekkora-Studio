package com.nekkora.ui.theme;

import static com.nekkora.tokens.ColorToken.*;

import com.nekkora.tokens.ColorMath;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.Radius;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.IconSize;
import com.nekkora.ui.icon.NekkoraIcon;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.UIDefaults;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;

/**
 * Mapeia tokens Nekkora para as chaves de UI do Swing/FlatLaf. É aqui — e só aqui — que
 * componentes Swing "puros" (JTable, JTree, JScrollBar, JOptionPane...) recebem a identidade
 * Nekkora. Telas nunca chamam {@code setBackground}/{@code setFont}/{@code setBorder}.
 */
final class NekkoraUIDefaults {
    private NekkoraUIDefaults() {}

    /** Variáveis base do FlatLaf ({@code @background}...), para que as derivações internas usem a paleta. */
    static Map<String, String> baseVariables(NekkoraTheme t) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("@background", hex(t, BACKGROUND_CANVAS));
        m.put("@foreground", hex(t, TEXT_PRIMARY));
        m.put("@accentColor", hex(t, ACCENT));
        m.put("@selectionBackground", hex(t, SELECTION_BACKGROUND));
        m.put("@selectionForeground", hex(t, SELECTION_TEXT));
        m.put("@selectionInactiveBackground", hex(t, SELECTION_INACTIVE));
        m.put("@selectionInactiveForeground", hex(t, SELECTION_TEXT));
        m.put("@disabledForeground", hex(t, TEXT_DISABLED));
        m.put("@componentBackground", hex(t, FIELD_BACKGROUND));
        m.put("@buttonBackground", hex(t, ACTION_SECONDARY));
        m.put("@menuBackground", hex(t, BACKGROUND_ELEVATED));
        return m;
    }

    static void apply(UIDefaults d, NekkoraTheme t) {
        Scale.withPendingFactor(d.getFont("defaultFont"), () -> applyScaled(d, t));
    }

    private static void applyScaled(UIDefaults d, NekkoraTheme t) {
        // ------------------------------------------------------------ Fontes
        // "defaultFont" continua sendo a fonte do sistema: é dela que o FlatLaf calcula o fator
        // de escala (HiDPI). Todos os componentes recebem Inter explicitamente.
        Font body = NekkoraFonts.font(TypeStyle.BODY);
        FontUIResource bodyRes = new FontUIResource(body);
        for (String k : new String[] {"Label.font", "Button.font", "ToggleButton.font", "CheckBox.font",
                "RadioButton.font", "ComboBox.font", "List.font", "Table.font", "Tree.font", "TextField.font",
                "PasswordField.font", "FormattedTextField.font", "Spinner.font", "TextArea.font", "EditorPane.font",
                "TextPane.font", "Menu.font", "MenuItem.font", "CheckBoxMenuItem.font", "RadioButtonMenuItem.font",
                "PopupMenu.font", "TabbedPane.font", "ToolTip.font", "Panel.font", "OptionPane.font",
                "OptionPane.messageFont", "OptionPane.buttonFont", "TitledBorder.font", "ToolBar.font",
                "ProgressBar.font", "Slider.font", "Viewport.font", "ScrollPane.font", "FileChooser.font"}) {
            d.put(k, bodyRes);
        }
        d.put("TableHeader.font", new FontUIResource(NekkoraFonts.font(TypeStyle.LABEL)));
        d.put("MenuItem.acceleratorFont", new FontUIResource(NekkoraFonts.font(TypeStyle.CAPTION)));
        d.put("Menu.acceleratorFont", new FontUIResource(NekkoraFonts.font(TypeStyle.CAPTION)));
        d.put("TitlePane.font", new FontUIResource(NekkoraFonts.font(TypeStyle.LABEL)));

        // ------------------------------------------------------------ Geometria
        int arc = 2 * Radius.MEDIUM.px();
        d.put("Component.arc", arc);
        d.put("Button.arc", arc);
        d.put("TextComponent.arc", arc);
        d.put("CheckBox.arc", 2 * Radius.SMALL.px());
        d.put("ProgressBar.arc", 999);
        d.put("ScrollBar.thumbArc", 999);
        d.put("ScrollBar.trackArc", 999);
        d.put("Component.focusWidth", 2);
        d.put("Component.innerFocusWidth", 0);
        d.put("Component.borderWidth", 1);
        d.put("Button.innerFocusWidth", 0);
        d.put("ScrollBar.width", 10);
        d.put("ScrollBar.showButtons", false);
        d.put("ScrollBar.thumbInsets", new InsetsUIResource(2, 2, 2, 2));
        d.put("ScrollBar.trackInsets", new InsetsUIResource(0, 0, 0, 0));
        d.put("ScrollPane.smoothScrolling", true);
        d.put("SplitPane.dividerSize", 5);
        d.put("SplitPane.continuousLayout", true);
        d.put("TabbedPane.tabHeight", 34);
        d.put("TabbedPane.tabSelectionHeight", 2);
        d.put("TabbedPane.contentSeparatorHeight", 1);
        d.put("TabbedPane.showTabSeparators", false);
        d.put("TabbedPane.tabInsets", new InsetsUIResource(4, 14, 4, 14));
        d.put("TabbedPane.tabArc", 0);
        d.put("TabbedPane.closeArc", 999);
        d.put("Table.rowHeight", 32);
        d.put("Table.showHorizontalLines", true);
        d.put("Table.showVerticalLines", false);
        d.put("Table.intercellSpacing", new java.awt.Dimension(0, 1));
        d.put("Table.cellMargins", new InsetsUIResource(2, 12, 2, 12));
        d.put("TableHeader.height", 32);
        d.put("TableHeader.cellMargins", new InsetsUIResource(4, 12, 4, 12));
        d.put("Tree.rowHeight", 26);
        d.put("Tree.paintLines", false);
        d.put("Tree.showDefaultIcons", false);
        d.put("Tree.leftChildIndent", 8);
        d.put("Tree.rightChildIndent", 12);
        d.put("Tree.selectionArc", arc);
        d.put("Tree.selectionInsets", new InsetsUIResource(0, 4, 0, 4));
        d.put("List.selectionArc", arc);
        d.put("List.selectionInsets", new InsetsUIResource(0, 4, 0, 4));
        d.put("List.cellMargins", new InsetsUIResource(4, 10, 4, 10));
        d.put("ComboBox.selectionArc", arc);
        d.put("ComboBox.selectionInsets", new InsetsUIResource(0, 4, 0, 4));
        d.put("MenuItem.selectionArc", arc);
        d.put("MenuItem.selectionInsets", new InsetsUIResource(0, 4, 0, 4));
        d.put("MenuItem.margin", new InsetsUIResource(5, 8, 5, 8));
        d.put("MenuItem.minimumWidth", 180);
        d.put("PopupMenu.borderInsets", new InsetsUIResource(4, 0, 4, 0));
        d.put("PopupMenu.borderCornerRadius", 2 * Radius.LARGE.px());
        d.put("Popup.borderCornerRadius", 2 * Radius.LARGE.px());
        d.put("PopupMenu.hoverScrollArrowBackground", color(t, BACKGROUND_HOVER));
        d.put("ToolTip.borderCornerRadius", 2 * Radius.MEDIUM.px());
        d.put("ToolBar.borderMargins", new InsetsUIResource(4, 4, 4, 4));
        d.put("ToolBar.spacingBorderMargins", new InsetsUIResource(0, 0, 0, 0));
        d.put("Button.margin", new InsetsUIResource(4, 14, 4, 14));
        d.put("Button.minimumWidth", 72);
        d.put("TextComponent.selectAllOnFocusPolicy", "never");
        d.put("TextField.margin", new InsetsUIResource(5, 8, 5, 8));
        d.put("PasswordField.margin", new InsetsUIResource(5, 8, 5, 8));
        d.put("FormattedTextField.margin", new InsetsUIResource(5, 8, 5, 8));
        d.put("TextArea.margin", new InsetsUIResource(6, 8, 6, 8));
        d.put("PasswordField.showRevealButton", true);
        d.put("TitlePane.unifiedBackground", true);
        d.put("TitlePane.menuBarEmbedded", true);
        d.put("OptionPane.buttonMinimumWidth", 88);
        d.put("Caret.width", 1);

        // ------------------------------------------------------------ Cores gerais
        Color canvas = t.color(BACKGROUND_CANVAS);
        Color surface = t.color(BACKGROUND_SURFACE);
        Color elevated = t.color(BACKGROUND_ELEVATED);
        Color field = t.color(FIELD_BACKGROUND);
        Color text = t.color(TEXT_PRIMARY);
        Color hoverOnSurface = ColorMath.over(t.color(BACKGROUND_HOVER), surface);
        Color hoverOnElevated = ColorMath.over(t.color(BACKGROUND_HOVER), elevated);

        put(d, t, BACKGROUND_CANVAS, "Panel.background", "RootPane.background", "OptionPane.background",
                "ToolBar.background", "SplitPane.background", "TitlePane.background", "MenuBar.background",
                "Separator.background", "Desktop.background", "Viewport.background", "ScrollPane.background",
                "TabbedPane.background", "FileChooser.background");
        put(d, t, TEXT_PRIMARY, "Label.foreground", "Panel.foreground", "OptionPane.messageForeground",
                "TitlePane.foreground", "CheckBox.foreground", "RadioButton.foreground", "ToggleButton.foreground",
                "TabbedPane.foreground", "TabbedPane.selectedForeground", "Tree.foreground", "List.foreground",
                "Table.foreground", "TextField.foreground", "PasswordField.foreground", "TextArea.foreground",
                "FormattedTextField.foreground", "EditorPane.foreground", "TextPane.foreground",
                "ComboBox.foreground", "Spinner.foreground", "Menu.foreground", "MenuItem.foreground",
                "CheckBoxMenuItem.foreground", "RadioButtonMenuItem.foreground", "MenuBar.foreground",
                "TitledBorder.titleColor", "ToolBar.foreground");
        put(d, t, TEXT_DISABLED, "Label.disabledForeground", "Button.disabledText", "CheckBox.disabledText",
                "RadioButton.disabledText", "MenuItem.disabledForeground", "Menu.disabledForeground",
                "ComboBox.disabledForeground", "TextField.disabledForeground", "TabbedPane.disabledForeground",
                "TitlePane.inactiveForeground", "Component.disabledForeground");
        d.put("TitlePane.inactiveBackground", new ColorUIResource(canvas));
        put(d, t, TEXT_MUTED, "TextField.placeholderForeground", "PasswordField.placeholderForeground",
                "TextArea.placeholderForeground", "FormattedTextField.placeholderForeground",
                "ComboBox.placeholderForeground", "MenuItem.acceleratorForeground", "Menu.acceleratorForeground",
                "TitlePane.buttonForeground");
        put(d, t, TEXT_SECONDARY, "TabbedPane.foreground", "TableHeader.foreground", "Label.infoForeground");
        put(d, t, TEXT_LINK, "Component.linkColor", "Hyperlink.linkColor");

        // Bordas e foco
        put(d, t, BORDER_DEFAULT, "Component.borderColor", "Button.borderColor", "Separator.foreground",
                "Component.disabledBorderColor", "TitledBorder.borderColor", "Component.hoverBorderColor");
        d.put("Component.hoverBorderColor", new ColorUIResource(t.color(BORDER_STRONG)));
        put(d, t, BORDER_SUBTLE, "Table.gridColor", "TableHeader.separatorColor", "TableHeader.bottomSeparatorColor",
                "TabbedPane.contentAreaColor", "SplitPaneDivider.borderColor", "MenuBar.borderColor",
                "ToolBar.separatorColor", "PopupMenu.borderColor", "Popup.borderColor", "Menu.borderColor",
                "ToolTip.borderColor", "RootPane.borderColor");
        d.put("PopupMenu.borderColor", new ColorUIResource(t.color(BORDER_DEFAULT)));
        put(d, t, BORDER_FOCUS, "Component.focusedBorderColor", "Button.focusedBorderColor",
                "Button.default.focusedBorderColor", "TabbedPane.underlineColor", "TabbedPane.inactiveUnderlineColor",
                "ProgressBar.foreground", "Slider.thumbColor", "Slider.trackValueColor", "Component.accentColor",
                "TextField.caretForeground", "TextArea.caretForeground", "PasswordField.caretForeground",
                "FormattedTextField.caretForeground", "EditorPane.caretForeground", "TextPane.caretForeground");
        d.put("Component.focusColor", new ColorUIResource(t.color(FOCUS_RING)));
        d.put("Button.default.focusColor", new ColorUIResource(t.color(FOCUS_RING)));
        put(d, t, STATUS_ERROR, "Component.error.focusedBorderColor", "Component.error.borderColor");
        d.put("Component.error.focusedBorderColor", new ColorUIResource(t.color(STATUS_ERROR_SOLID)));
        put(d, t, STATUS_WARNING, "Component.warning.borderColor", "Component.warning.focusedBorderColor");

        // Campos
        put(d, t, FIELD_BACKGROUND, "TextField.background", "PasswordField.background", "TextArea.background",
                "FormattedTextField.background", "EditorPane.background", "TextPane.background", "ComboBox.background",
                "Spinner.background", "ComboBox.editableBackground", "ComboBox.buttonEditableBackground",
                "CheckBox.icon.background", "RadioButton.icon.background", "Spinner.buttonBackground");
        Color readOnly = ColorMath.mix(field, canvas, 0.4);
        for (String k : new String[] {"TextField", "PasswordField", "TextArea", "FormattedTextField", "EditorPane",
                "TextPane", "ComboBox", "Spinner"}) {
            d.put(k + ".inactiveBackground", new ColorUIResource(readOnly));
            d.put(k + ".disabledBackground", new ColorUIResource(readOnly));
        }
        d.put("ComboBox.buttonBackground", new ColorUIResource(field));
        d.put("ComboBox.buttonArrowColor", new ColorUIResource(t.color(TEXT_SECONDARY)));
        d.put("ComboBox.buttonHoverArrowColor", new ColorUIResource(text));
        d.put("ComboBox.buttonDisabledArrowColor", new ColorUIResource(t.color(TEXT_DISABLED)));
        d.put("ComboBox.popupBackground", new ColorUIResource(elevated));
        d.put("ComboBox.selectionBackground", new ColorUIResource(t.color(SELECTION_BACKGROUND)));
        d.put("ComboBox.selectionForeground", new ColorUIResource(t.color(SELECTION_TEXT)));
        d.put("ComboBox.buttonStyle", "button");
        d.put("Spinner.buttonArrowColor", new ColorUIResource(t.color(TEXT_SECONDARY)));
        d.put("Spinner.buttonHoverArrowColor", new ColorUIResource(text));
        put(d, t, SELECTION_BACKGROUND, "TextField.selectionBackground", "PasswordField.selectionBackground",
                "TextArea.selectionBackground", "FormattedTextField.selectionBackground",
                "EditorPane.selectionBackground", "TextPane.selectionBackground");
        put(d, t, SELECTION_TEXT, "TextField.selectionForeground", "PasswordField.selectionForeground",
                "TextArea.selectionForeground", "FormattedTextField.selectionForeground",
                "EditorPane.selectionForeground", "TextPane.selectionForeground");

        // Botões Swing "puros" (JOptionPane, JFileChooser) — os da lib pintam a si próprios
        d.put("Button.background", new ColorUIResource(t.color(ACTION_SECONDARY)));
        d.put("Button.foreground", new ColorUIResource(t.color(ACTION_SECONDARY_TEXT)));
        d.put("Button.borderColor", new ColorUIResource(t.color(ACTION_SECONDARY_BORDER)));
        d.put("Button.hoverBorderColor", new ColorUIResource(t.color(ACTION_SECONDARY_BORDER)));
        d.put("Button.hoverBackground", new ColorUIResource(t.color(ACTION_SECONDARY_HOVER)));
        d.put("Button.pressedBackground", new ColorUIResource(t.color(ACTION_SECONDARY_PRESSED)));
        d.put("Button.disabledBackground", new ColorUIResource(ColorMath.mix(t.color(ACTION_SECONDARY), canvas, 0.5)));
        d.put("Button.disabledBorderColor", new ColorUIResource(t.color(BORDER_SUBTLE)));
        d.put("Button.default.background", new ColorUIResource(t.color(ACTION_PRIMARY)));
        d.put("Button.default.foreground", new ColorUIResource(t.color(ACTION_PRIMARY_TEXT)));
        d.put("Button.default.hoverBackground", new ColorUIResource(t.color(ACTION_PRIMARY_HOVER)));
        d.put("Button.default.pressedBackground", new ColorUIResource(t.color(ACTION_PRIMARY_PRESSED)));
        d.put("Button.default.borderColor", new ColorUIResource(t.color(ACTION_PRIMARY)));
        d.put("Button.default.hoverBorderColor", new ColorUIResource(t.color(ACTION_PRIMARY_HOVER)));
        d.put("Button.default.boldText", false);
        d.put("Button.toolbar.hoverBackground", new ColorUIResource(hoverOnSurface));
        d.put("Button.toolbar.pressedBackground", new ColorUIResource(ColorMath.over(t.color(BACKGROUND_PRESSED), surface)));
        d.put("Button.toolbar.selectedBackground", new ColorUIResource(ColorMath.over(t.color(ACCENT_SOFT), surface)));
        d.put("ToggleButton.selectedBackground", new ColorUIResource(ColorMath.over(t.color(ACCENT_SOFT), surface)));
        d.put("ToggleButton.selectedForeground", new ColorUIResource(text));

        // Checkbox / radio (ícones padrão do FlatLaf recoloridos — a lib usa os seus)
        d.put("CheckBox.icon.borderColor", new ColorUIResource(t.color(BORDER_STRONG)));
        d.put("CheckBox.icon.hoverBorderColor", new ColorUIResource(t.color(TEXT_MUTED)));
        d.put("CheckBox.icon.focusedBorderColor", new ColorUIResource(t.color(BORDER_FOCUS)));
        d.put("CheckBox.icon.selectedBackground", new ColorUIResource(t.color(ACTION_PRIMARY)));
        d.put("CheckBox.icon.selectedBorderColor", new ColorUIResource(t.color(ACTION_PRIMARY)));
        d.put("CheckBox.icon.checkmarkColor", new ColorUIResource(t.color(ACTION_PRIMARY_TEXT)));
        d.put("CheckBox.icon.disabledBackground", new ColorUIResource(readOnly));
        d.put("CheckBox.icon.disabledBorderColor", new ColorUIResource(t.color(BORDER_SUBTLE)));
        d.put("CheckBox.icon.disabledCheckmarkColor", new ColorUIResource(t.color(TEXT_DISABLED)));
        d.put("CheckBox.icon.focusColor", new ColorUIResource(t.color(FOCUS_RING)));

        // Listas, tabelas, árvores
        put(d, t, BACKGROUND_SURFACE, "List.background", "Table.background", "Tree.background",
                "Tree.textBackground", "TableHeader.background", "TabbedPane.selectedBackground");
        d.put("TabbedPane.selectedBackground", null);
        d.put("TableHeader.background", new ColorUIResource(surface));
        d.put("Table.alternateRowColor", null);
        put(d, t, SELECTION_BACKGROUND, "List.selectionBackground", "Table.selectionBackground",
                "Tree.selectionBackground", "MenuItem.selectionBackground");
        put(d, t, SELECTION_TEXT, "List.selectionForeground", "Table.selectionForeground",
                "Tree.selectionForeground", "Tree.textForeground");
        d.put("Tree.textForeground", new ColorUIResource(text));
        put(d, t, SELECTION_INACTIVE, "List.selectionInactiveBackground", "Table.selectionInactiveBackground",
                "Tree.selectionInactiveBackground");
        put(d, t, TEXT_PRIMARY, "List.selectionInactiveForeground", "Table.selectionInactiveForeground",
                "Tree.selectionInactiveForeground");
        d.put("Table.focusCellBackground", new ColorUIResource(t.color(SELECTION_BACKGROUND)));
        d.put("Table.focusCellForeground", new ColorUIResource(t.color(SELECTION_TEXT)));
        d.put("Table.cellFocusColor", new ColorUIResource(t.color(BORDER_FOCUS)));
        d.put("Table.showCellFocusIndicator", false);
        d.put("Tree.showCellFocusIndicator", false);
        d.put("List.showCellFocusIndicator", false);
        d.put("Tree.hash", new ColorUIResource(t.color(BORDER_SUBTLE)));
        d.put("Tree.icon.expandedColor", new ColorUIResource(t.color(TEXT_MUTED)));
        d.put("Tree.icon.collapsedColor", new ColorUIResource(t.color(TEXT_MUTED)));
        d.put("Tree.expandedIcon", NekkoraIcon.of(IconName.CHEVRON_DOWN, IconSize.S)
                .withColor(TEXT_MUTED).withVariant(NekkoraIcon.Variant.OUTLINE).asUIResource());
        d.put("Tree.collapsedIcon", NekkoraIcon.of(IconName.CHEVRON_RIGHT, IconSize.S)
                .withColor(TEXT_MUTED).withVariant(NekkoraIcon.Variant.OUTLINE).asUIResource());

        // Abas
        d.put("TabbedPane.hoverColor", new ColorUIResource(t.color(BACKGROUND_HOVER)));
        d.put("TabbedPane.focusColor", new ColorUIResource(ColorMath.over(t.color(ACCENT_SOFT), canvas)));
        d.put("TabbedPane.selectedForeground", new ColorUIResource(text));
        d.put("TabbedPane.buttonHoverBackground", new ColorUIResource(t.color(BACKGROUND_HOVER)));
        d.put("TabbedPane.closeForeground", new ColorUIResource(t.color(TEXT_MUTED)));
        d.put("TabbedPane.closeHoverForeground", new ColorUIResource(text));
        d.put("TabbedPane.closeHoverBackground", new ColorUIResource(t.color(BACKGROUND_HOVER)));

        // Barras de rolagem
        d.put("ScrollBar.track", new ColorUIResource(ColorMath.alpha(canvas, 0)));
        d.put("ScrollBar.thumb", new ColorUIResource(t.color(BORDER_STRONG)));
        d.put("ScrollBar.hoverThumbColor", new ColorUIResource(t.color(TEXT_MUTED)));
        d.put("ScrollBar.pressedThumbColor", new ColorUIResource(t.color(TEXT_SECONDARY)));
        d.put("ScrollBar.hoverTrackColor", new ColorUIResource(t.color(BACKGROUND_HOVER)));

        // Divisor
        d.put("SplitPaneDivider.draggingColor", new ColorUIResource(t.color(BORDER_FOCUS)));
        d.put("SplitPaneDivider.gripColor", new ColorUIResource(t.color(TEXT_MUTED)));
        d.put("SplitPaneDivider.oneTouchArrowColor", new ColorUIResource(t.color(TEXT_MUTED)));
        d.put("SplitPaneDivider.style", "plain");

        // Menus e popups
        put(d, t, BACKGROUND_ELEVATED, "PopupMenu.background", "Menu.background", "MenuItem.background",
                "CheckBoxMenuItem.background", "RadioButtonMenuItem.background", "Popup.background",
                "ComboBox.popupBackground", "List.dropCellBackground");
        d.put("MenuBar.background", new ColorUIResource(canvas));
        d.put("Menu.background", new ColorUIResource(canvas));
        d.put("MenuBar.hoverBackground", new ColorUIResource(ColorMath.over(t.color(BACKGROUND_HOVER), canvas)));
        d.put("MenuItem.selectionBackground", new ColorUIResource(hoverOnElevated));
        d.put("Menu.selectionBackground", new ColorUIResource(hoverOnElevated));
        d.put("CheckBoxMenuItem.selectionBackground", new ColorUIResource(hoverOnElevated));
        d.put("RadioButtonMenuItem.selectionBackground", new ColorUIResource(hoverOnElevated));
        put(d, t, TEXT_PRIMARY, "MenuItem.selectionForeground", "Menu.selectionForeground",
                "CheckBoxMenuItem.selectionForeground", "RadioButtonMenuItem.selectionForeground");
        d.put("MenuItem.checkBackground", new ColorUIResource(t.color(ACCENT_SOFT)));
        d.put("MenuItem.underlineSelectionColor", new ColorUIResource(t.color(ACCENT)));
        d.put("Separator.foreground", new ColorUIResource(t.color(BORDER_SUBTLE)));
        d.put("PopupMenu.dropShadowPainted", true);
        d.put("Popup.dropShadowPainted", true);
        d.put("Popup.dropShadowColor", new ColorUIResource(t.color(SHADOW)));
        d.put("Popup.dropShadowOpacity", t.isDark() ? 0.5f : 0.18f);

        // Tooltip: invertido, discreto
        Color tipBg = t.isDark() ? ColorMath.mix(t.palette().highlight(), elevated, 0.12) : ColorMath.mix(text, canvas, 0.92);
        d.put("ToolTip.background", new ColorUIResource(tipBg));
        d.put("ToolTip.foreground", new ColorUIResource(t.isDark() ? text : Color.WHITE));
        d.put("ToolTip.borderColor", new ColorUIResource(t.isDark() ? t.color(BORDER_DEFAULT) : tipBg));

        // Progresso
        d.put("ProgressBar.background", new ColorUIResource(t.color(BORDER_SUBTLE)));
        d.put("ProgressBar.foreground", new ColorUIResource(t.color(ACCENT)));
        d.put("ProgressBar.selectionBackground", new ColorUIResource(text));
        d.put("ProgressBar.selectionForeground", new ColorUIResource(t.color(ACTION_PRIMARY_TEXT)));
        d.put("ProgressBar.horizontalSize", new java.awt.Dimension(146, 6));
        d.put("Slider.trackColor", new ColorUIResource(t.color(BORDER_DEFAULT)));
        d.put("Slider.focusedColor", new ColorUIResource(t.color(FOCUS_RING)));
        d.put("Slider.hoverThumbColor", new ColorUIResource(t.color(ACTION_PRIMARY_HOVER)));
        d.put("Slider.pressedThumbColor", new ColorUIResource(t.color(ACTION_PRIMARY_PRESSED)));

        // Ícones de diálogo do JOptionPane
        d.put("OptionPane.informationIcon", NekkoraIcon.of(IconName.INFO, IconSize.XL).withColor(STATUS_INFO).asUIResource());
        d.put("OptionPane.warningIcon", NekkoraIcon.of(IconName.ALERT, IconSize.XL).withColor(STATUS_WARNING).asUIResource());
        d.put("OptionPane.errorIcon", NekkoraIcon.of(IconName.X_CIRCLE, IconSize.XL).withColor(STATUS_ERROR).asUIResource());
        d.put("OptionPane.questionIcon", NekkoraIcon.of(IconName.INFO, IconSize.XL).withColor(ACCENT).asUIResource());
        d.put("OptionPane.messageAreaBorder", new javax.swing.plaf.BorderUIResource.EmptyBorderUIResource(new Insets(0, 0, 0, 0)));
    }

    private static void put(UIDefaults d, NekkoraTheme t, ColorToken token, String... keys) {
        ColorUIResource c = new ColorUIResource(t.color(token));
        for (String k : keys) d.put(k, c);
    }

    private static ColorUIResource color(NekkoraTheme t, ColorToken token) {
        return new ColorUIResource(t.color(token));
    }

    private static String hex(NekkoraTheme t, ColorToken token) {
        return ColorMath.toHex(t.color(token));
    }
}
