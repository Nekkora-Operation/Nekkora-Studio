package com.nekkora.ui.input;

import com.formdev.flatlaf.FlatClientProperties;
import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.IconSize;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Scale;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;
import java.util.function.Function;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;

/**
 * Seleção em lista suspensa. Rótulos e ícones por item vêm de funções — sem subclasses de
 * renderer nas telas.
 *
 * <pre>{@code
 * NekkoraComboBox<Env> env = new NekkoraComboBox<>(Env.values());
 * env.setLabelProvider(Env::displayName);
 * env.setIconProvider(e -> IconName.SERVER);
 * }</pre>
 */
public class NekkoraComboBox<E> extends JComboBox<E> {
    private ControlSize size = ControlSize.MEDIUM;
    private Function<? super E, String> labelProvider = String::valueOf;
    private Function<? super E, IconName> iconProvider;
    private boolean initialized;

    public NekkoraComboBox() {
        super();
        init();
    }

    @SafeVarargs
    public NekkoraComboBox(E... items) {
        super(items);
        init();
    }

    public NekkoraComboBox(List<E> items) {
        super();
        items.forEach(this::addItem);
        init();
    }

    private void init() {
        initialized = true;
        setRenderer(new Renderer());
        setMaximumRowCount(10);
        applyStyle();
    }

    public void setPlaceholder(String placeholder) {
        putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
    }

    public void setLabelProvider(Function<? super E, String> provider) {
        this.labelProvider = provider;
        repaint();
    }

    public void setIconProvider(Function<? super E, IconName> provider) {
        this.iconProvider = provider;
        repaint();
    }

    public void setError(boolean error) {
        FieldSupport.outline(this, error ? FieldSupport.Validation.ERROR : FieldSupport.Validation.NONE);
    }

    public void setControlSize(ControlSize size) {
        this.size = size;
        revalidate();
    }

    @SuppressWarnings("unchecked")
    public E getSelectedValue() {
        return (E) getSelectedItem();
    }

    @Override
    public Dimension getPreferredSize() {
        return isPreferredSizeSet() ? super.getPreferredSize() : FieldSupport.height(super.getPreferredSize(), size);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyStyle();
    }

    private void applyStyle() {
        if (!initialized) return;
        setFont(NekkoraFonts.font(TypeStyle.BODY));
        if (getRenderer() instanceof java.awt.Component rc) javax.swing.SwingUtilities.updateComponentTreeUI(rc);
    }

    private final class Renderer extends DefaultListCellRenderer {
        @Override
        @SuppressWarnings("unchecked")
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                      boolean focus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
            E item = (E) value;
            l.setText(item == null ? "" : labelProvider.apply(item));
            IconName icon = item != null && iconProvider != null ? iconProvider.apply(item) : null;
            l.setIcon(icon == null ? null : NekkoraIcon.of(icon, IconSize.M));
            l.setIconTextGap(Scale.px(8));
            if (index >= 0) l.setBorder(javax.swing.BorderFactory.createEmptyBorder(Scale.px(5), Scale.px(8), Scale.px(5), Scale.px(8)));
            return l;
        }
    }
}
