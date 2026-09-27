package com.nekkora.ui.studio;

import com.nekkora.tokens.Spacing;
import com.nekkora.ui.container.Background;
import com.nekkora.ui.container.NekkoraPanel;
import com.nekkora.ui.container.NekkoraScrollPane;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.data.NekkoraIconMarker;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.state.NekkoraEmptyState;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.typography.NekkoraCaption;
import com.nekkora.ui.typography.NekkoraHeading;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Painel "Inspector": propriedades do item selecionado, em grupos recolhíveis. Mostra um
 * estado vazio quando nada está selecionado ({@link #showEmpty()}).
 */
public class NekkoraInspector extends NekkoraPanel {
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final NekkoraStack groups = NekkoraStack.vertical(Spacing.SPACE_2);
    private final NekkoraIconMarker marker = new NekkoraIconMarker(IconName.BOX, Tone.ACCENT, false, 32);
    private final NekkoraHeading title = new NekkoraHeading("", NekkoraHeading.Level.H3);
    private final NekkoraCaption subtitle = new NekkoraCaption("");
    private final JPanel extras = new JPanel(new BorderLayout());

    public NekkoraInspector(String panelTitle) {
        super(new BorderLayout(), Background.CANVAS);
        add(new NekkoraPanelHeader(panelTitle), BorderLayout.NORTH);

        JPanel identity = new JPanel(new BorderLayout(Scale.px(Spacing.SPACE_4), 0));
        identity.setOpaque(false);
        JPanel texts = new JPanel();
        texts.setOpaque(false);
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        texts.add(title);
        texts.add(subtitle);
        identity.add(marker, BorderLayout.WEST);
        identity.add(texts, BorderLayout.CENTER);
        extras.setOpaque(false);
        identity.add(extras, BorderLayout.EAST);
        identity.setBorder(BorderFactory.createEmptyBorder(Scale.px(Spacing.SPACE_5), Scale.px(Spacing.SPACE_5), Scale.px(Spacing.SPACE_4), Scale.px(Spacing.SPACE_4)));

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.add(identity, BorderLayout.NORTH);
        groups.setPadding(Spacing.SPACE_1, Spacing.SPACE_3);
        NekkoraScrollPane scroll = NekkoraScrollPane.verticalOnly(groups);
        content.add(scroll, BorderLayout.CENTER);

        NekkoraEmptyState empty = new NekkoraEmptyState(IconName.SLIDERS, Tone.NEUTRAL, "Nada selecionado",
                "Selecione um item para ver e editar suas propriedades.");
        body.setOpaque(false);
        body.add(content, "content");
        body.add(empty, "empty");
        add(body, BorderLayout.CENTER);
        showEmpty();
        getAccessibleContext().setAccessibleName(panelTitle);
    }

    /** Identidade do item inspecionado. */
    public void setSubject(IconName icon, Tone tone, String titleText, String subtitleText) {
        marker.setIcon(icon);
        marker.setTone(tone);
        title.setText(titleText);
        subtitle.setText(subtitleText);
        subtitle.setVisible(subtitleText != null);
        cards.show(body, "content");
    }

    /** Componente à direita da identidade (ex.: status badge). */
    public void setSubjectExtra(JComponent c) {
        extras.removeAll();
        if (c != null) extras.add(c, BorderLayout.NORTH);
        extras.revalidate();
    }

    public NekkoraPropertiesGroup addGroup(String titleText) {
        NekkoraPropertiesGroup g = new NekkoraPropertiesGroup(titleText);
        groups.add(g);
        groups.revalidate();
        return g;
    }

    public void clearGroups() {
        groups.removeAll();
        groups.revalidate();
        groups.repaint();
    }

    public void showEmpty() {
        cards.show(body, "empty");
    }
}
