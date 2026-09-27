package com.nekkora.showcase.pages;

import com.nekkora.showcase.kit.Page;
import com.nekkora.showcase.kit.RadiusSample;
import com.nekkora.showcase.kit.SpaceBar;
import com.nekkora.showcase.kit.Swatch;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Elevation;
import com.nekkora.tokens.Radius;
import com.nekkora.tokens.Spacing;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.typography.NekkoraCaption;
import com.nekkora.ui.typography.NekkoraCodeText;
import com.nekkora.ui.typography.NekkoraHeading;
import com.nekkora.ui.typography.NekkoraLabel;
import com.nekkora.ui.typography.NekkoraParagraph;
import com.nekkora.ui.typography.NekkoraText;
import com.nekkora.ui.typography.NekkoraTextLabel;
import com.nekkora.ui.typography.TextTone;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Fundamentos: cores, tipografia, espaço, raios e elevação. */
public final class FoundationPages {
    private FoundationPages() {}

    public static JComponent colors() {
        Page p = new Page("Cores", "Tokens semânticos: as telas pedem um papel (\"texto secundário\", \"ação primária\"), "
                + "nunca um valor. Troque o tema no topo e veja todos os papéis mudarem juntos.");
        p.section("Fundos", "Canvas é a janela; surface são painéis; elevated são popups e diálogos.",
                grid(ColorToken.BACKGROUND_CANVAS, ColorToken.BACKGROUND_SURFACE, ColorToken.BACKGROUND_ELEVATED,
                        ColorToken.BACKGROUND_SUNKEN, ColorToken.FIELD_BACKGROUND, ColorToken.BACKGROUND_HOVER));
        p.section("Texto", null, grid(ColorToken.TEXT_PRIMARY, ColorToken.TEXT_SECONDARY, ColorToken.TEXT_MUTED,
                ColorToken.TEXT_DISABLED, ColorToken.TEXT_LINK, ColorToken.TEXT_INVERSE));
        p.section("Bordas, foco e seleção", null, grid(ColorToken.BORDER_SUBTLE, ColorToken.BORDER_DEFAULT,
                ColorToken.BORDER_STRONG, ColorToken.BORDER_FOCUS, ColorToken.FOCUS_RING, ColorToken.SELECTION_BACKGROUND));
        p.section("Ações", null, grid(ColorToken.ACTION_PRIMARY, ColorToken.ACTION_PRIMARY_HOVER,
                ColorToken.ACTION_PRIMARY_PRESSED, ColorToken.ACTION_SECONDARY, ColorToken.ACTION_SECONDARY_HOVER,
                ColorToken.ACTION_DANGER));
        p.section("Status", "Texto/ícone, fundo suave e fundo sólido (texto branco com contraste AA).",
                grid(ColorToken.STATUS_SUCCESS, ColorToken.STATUS_WARNING, ColorToken.STATUS_ERROR, ColorToken.STATUS_INFO,
                        ColorToken.STATUS_NEUTRAL, ColorToken.ACCENT, ColorToken.STATUS_SUCCESS_SOFT,
                        ColorToken.STATUS_WARNING_SOFT, ColorToken.STATUS_ERROR_SOFT, ColorToken.STATUS_INFO_SOFT,
                        ColorToken.STATUS_NEUTRAL_SOFT, ColorToken.ACCENT_SOFT, ColorToken.STATUS_SUCCESS_SOLID,
                        ColorToken.STATUS_WARNING_SOLID, ColorToken.STATUS_ERROR_SOLID, ColorToken.STATUS_INFO_SOLID,
                        ColorToken.STATUS_NEUTRAL_SOLID, ColorToken.ACCENT_SOLID));
        return p;
    }

    private static JComponent grid(ColorToken... tokens) {
        JPanel g = new JPanel(new GridLayout(0, 6, 16, 16));
        g.setOpaque(false);
        for (ColorToken t : tokens) g.add(new Swatch(t));
        return g;
    }

    public static JComponent typography() {
        Page p = new Page("Tipografia", "Sora para títulos e display; Inter para interface e dados; JetBrains Mono para "
                + "código e identificadores. As telas escolhem um nível — nunca fonte ou tamanho.");
        NekkoraStack levels = NekkoraStack.vertical(Spacing.SPACE_5);
        for (TypeStyle s : TypeStyle.values()) {
            NekkoraStack row = NekkoraStack.horizontal(Spacing.SPACE_7).align(NekkoraStack.Align.CENTER);
            NekkoraCaption meta = new NekkoraCaption(s.name() + " · " + s.role().name().toLowerCase() + " · "
                    + (int) s.size() + "px · " + s.weight().value());
            row.add(new com.nekkora.showcase.kit.Fixed(meta, 260, 0));
            String sample = switch (s) {
                case MONOSPACE -> "run_7f3a91c2 · status=SUCCESS";
                case OVERLINE -> "SEÇÃO DE NAVEGAÇÃO";
                default -> "Automação com clareza";
            };
            row.add(new NekkoraTextLabel(sample, s, TextTone.PRIMARY));
            levels.add(row);
        }
        p.section("Níveis", null, levels);
        p.section("Componentes de texto", null, Page.column(
                new NekkoraHeading("NekkoraHeading — H1", NekkoraHeading.Level.H1),
                new NekkoraText("NekkoraText — corpo padrão para conteúdo de interface."),
                NekkoraText.secondary("NekkoraText secundário — metadados e apoio."),
                new NekkoraLabel("NekkoraLabel — rótulo de campo"),
                new NekkoraCaption("NekkoraCaption — atualizado há 3 minutos"),
                Page.tightRow(new NekkoraText("Identificador:"), new NekkoraCodeText("a1b2c3d4-9f0e")),
                new NekkoraParagraph("NekkoraParagraph — parágrafos quebram linha automaticamente e usam o tom "
                        + "secundário por padrão, para não competir com títulos e dados.")));
        return p;
    }

    public static JComponent spaceAndShape() {
        Page p = new Page("Espaço, forma e elevação", "Escala de espaço space.1–space.12, raios e elevação. "
                + "Tudo escala com o DPI (125%, 150%, 200%).");
        NekkoraStack bars = NekkoraStack.vertical(Spacing.SPACE_2);
        for (Spacing s : Spacing.values()) bars.add(new SpaceBar(s));
        p.section("Espaçamento", null, bars);
        List<Radius> radii = List.of(Radius.NONE, Radius.SMALL, Radius.MEDIUM, Radius.LARGE, Radius.FULL);
        NekkoraStack r = NekkoraStack.horizontal(Spacing.SPACE_6).align(NekkoraStack.Align.START);
        for (Radius rad : radii) r.add(Page.specimen(rad.key() + " · " + (rad == Radius.FULL ? "pílula" : rad.px() + "px"), new RadiusSample(rad)));
        r.addGlue();
        p.section("Raios", null, r);
        NekkoraStack e = NekkoraStack.horizontal(Spacing.SPACE_6).align(NekkoraStack.Align.START);
        for (Elevation el : Elevation.values()) e.add(Page.specimen(el.key(), new RadiusSample(Radius.LARGE, el)));
        e.addGlue();
        p.section("Elevação", "Usada com parcimônia: a maioria das superfícies não tem sombra.", e);
        return p;
    }
}
