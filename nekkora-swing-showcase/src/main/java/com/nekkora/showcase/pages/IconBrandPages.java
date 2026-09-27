package com.nekkora.showcase.pages;

import com.nekkora.showcase.kit.Page;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.brand.NekkoraLogo;
import com.nekkora.ui.container.NekkoraCard;
import com.nekkora.ui.container.NekkoraPanel;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.data.NekkoraIconMarker;
import com.nekkora.ui.icon.IconCategory;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.IconSize;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.typography.NekkoraCaption;
import com.nekkora.ui.typography.TextTone;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Ícones e marca. */
public final class IconBrandPages {
    private IconBrandPages() {}

    public static JComponent icons() {
        Page p = new Page("Ícones", "Conjunto duotone da marca (os mesmos desenhos da versão web), vetorial e nítido em "
                + "qualquer DPI. Tamanhos S 14 · M 16 · L 20 · XL 24. A cor acompanha o texto do componente.");
        NekkoraStack sizes = NekkoraStack.horizontal(Spacing.SPACE_7).align(NekkoraStack.Align.END);
        for (IconSize s : new IconSize[] {IconSize.S, IconSize.M, IconSize.L, IconSize.XL, IconSize.XXL}) {
            JLabel l = new JLabel(NekkoraIcon.of(IconName.STUDIO, s));
            sizes.add(Page.specimen(s.name() + " · " + s.px(), l));
        }
        sizes.add(Page.specimen("duotone", new JLabel(NekkoraIcon.of(IconName.SERVER, IconSize.XL))));
        sizes.add(Page.specimen("outline", new JLabel(NekkoraIcon.of(IconName.SERVER, IconSize.XL).withVariant(NekkoraIcon.Variant.OUTLINE))));
        sizes.add(Page.specimen("marcador", new NekkoraIconMarker(IconName.SERVER, Tone.ACCENT)));
        sizes.addGlue();
        p.section("Tamanhos e estilos", null, sizes);
        for (IconCategory cat : IconCategory.values()) {
            JPanel grid = new JPanel(new GridLayout(0, 8, 12, 12));
            grid.setOpaque(false);
            int n = 0;
            for (IconName icon : IconName.values()) {
                if (icon.category() != cat) continue;
                n++;
                NekkoraPanel cell = new NekkoraPanel(new BorderLayout(0, 6));
                cell.setPadding(Spacing.SPACE_4);
                JLabel glyph = new JLabel(NekkoraIcon.of(icon, IconSize.XL), SwingConstants.CENTER);
                cell.add(glyph, BorderLayout.CENTER);
                NekkoraCaption name = new NekkoraCaption(icon.id(), TextTone.SECONDARY);
                name.setHorizontalAlignment(SwingConstants.CENTER);
                cell.add(name, BorderLayout.SOUTH);
                grid.add(cell);
            }
            if (n > 0) p.section(cat.label(), null, grid);
        }
        return p;
    }

    public static JComponent brand() {
        Page p = new Page("Marca", "Símbolo Cat-Eye (óculos com orelhas de gato: Neko + Observabilidade) e logotipo "
                + "monolinear. A marca é separada dos componentes funcionais e respeita o tema.");
        p.section("Variações", null, Page.column(
                Page.specimen("FULL + tagline", new NekkoraLogo(NekkoraLogo.Variant.FULL, 44).withTagline()),
                Page.row(Page.specimen("COMPACT", new NekkoraLogo(NekkoraLogo.Variant.COMPACT, 28)),
                        Page.specimen("SYMBOL", new NekkoraLogo(NekkoraLogo.Variant.SYMBOL, 40)),
                        Page.specimen("WORDMARK", new NekkoraLogo(NekkoraLogo.Variant.WORDMARK, 28)))));
        p.section("Tons", "BRAND usa as cores do tema; MONO uma cor só; INVERSE branco sobre fundo preenchido.",
                Page.row(Page.specimen("BRAND", new NekkoraLogo(NekkoraLogo.Variant.SYMBOL, 40, NekkoraLogo.Tone.BRAND)),
                        Page.specimen("MONO", new NekkoraLogo(NekkoraLogo.Variant.SYMBOL, 40, NekkoraLogo.Tone.MONO)),
                        Page.specimen("MONO (nome)", new NekkoraLogo(NekkoraLogo.Variant.COMPACT, 28, NekkoraLogo.Tone.MONO))));
        NekkoraStack apps = NekkoraStack.horizontal(Spacing.SPACE_7).align(NekkoraStack.Align.END);
        for (int s : new int[] {16, 24, 32, 48, 64, 96}) apps.add(Page.specimen(s + "px", new NekkoraLogo(NekkoraLogo.Variant.APP_ICON, s)));
        apps.addGlue();
        p.section("Ícone de aplicativo", "Gerado em vários tamanhos para a janela (NekkoraLogo.appIconImages()).", apps);
        NekkoraCard card = new NekkoraCard("Uso em cabeçalho", "A marca nunca decide cor sozinha: segue os tokens brand.*");
        card.setContent(new NekkoraLogo(NekkoraLogo.Variant.COMPACT, 24));
        p.section("Em contexto", null, card);
        return p;
    }
}
