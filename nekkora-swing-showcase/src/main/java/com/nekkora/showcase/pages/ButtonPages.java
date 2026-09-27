package com.nekkora.showcase.pages;

import com.nekkora.showcase.kit.Page;
import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.button.NekkoraIconButton;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.core.Interaction;
import com.nekkora.ui.core.NekkoraUI;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.typography.NekkoraLabel;
import javax.swing.JComponent;

/** Botões: variantes × estados, tamanhos, composição com ícone. */
public final class ButtonPages {
    private ButtonPages() {}

    public static JComponent buttons() {
        Page p = new Page("Botões", "A tela escolhe a intenção — PRIMARY, SECONDARY, GHOST, DANGER ou LINK. "
                + "Estados, cores, foco e raio vêm do tema. Espaço e Enter acionam o botão focado.");
        NekkoraStack gallery = NekkoraStack.vertical(Spacing.SPACE_5);
        NekkoraStack header = NekkoraStack.horizontal(Spacing.SPACE_4).align(NekkoraStack.Align.CENTER);
        header.add(new com.nekkora.showcase.kit.Fixed(NekkoraLabel.overline("Variante"), 110, 0));
        for (String s : new String[] {"Padrão", "Hover", "Pressionado", "Foco", "Desabilitado", "Carregando"}) {
            header.add(new com.nekkora.showcase.kit.Fixed(NekkoraLabel.overline(s), 120, 0));
        }
        gallery.add(header);
        for (NekkoraButton.Variant v : NekkoraButton.Variant.values()) {
            NekkoraStack row = NekkoraStack.horizontal(Spacing.SPACE_4).align(NekkoraStack.Align.CENTER);
            row.add(new com.nekkora.showcase.kit.Fixed(new NekkoraLabel(v.name()), 110, 0));
            for (int state = 0; state < 6; state++) {
                NekkoraButton b = new NekkoraButton(label(v), v);
                switch (state) {
                    case 1 -> NekkoraUI.forceInteraction(b, Interaction.HOVER);
                    case 2 -> NekkoraUI.forceInteraction(b, Interaction.PRESSED);
                    case 3 -> NekkoraUI.forceInteraction(b, Interaction.FOCUSED);
                    case 4 -> b.setEnabled(false);
                    case 5 -> b.setLoading(true);
                    default -> { }
                }
                row.add(new com.nekkora.showcase.kit.Fixed(b, 120, 0));
            }
            row.addGlue();
            gallery.add(row);
        }
        p.section("Variantes e estados", "Estados forçados apenas para documentação.", gallery);

        p.section("Tamanhos", null, Page.tightRow(
                NekkoraButton.primary("Pequeno").withSize(ControlSize.SMALL),
                NekkoraButton.primary("Médio"),
                NekkoraButton.primary("Grande").withSize(ControlSize.LARGE)));
        p.section("Composição", "Ícone + texto, texto + ícone à direita e só ícone (com tooltip obrigatório).",
                Page.tightRow(
                        NekkoraButton.primary("Novo projeto").withIcon(IconName.PLUS),
                        NekkoraButton.secondary("Exportar").withIcon(IconName.DOWNLOAD),
                        NekkoraButton.ghost("Próximo").withTrailingIcon(IconName.ARROW_RIGHT),
                        NekkoraButton.danger("Excluir").withIcon(IconName.TRASH),
                        NekkoraButton.link("Ver detalhes").withTrailingIcon(IconName.EXTERNAL_LINK),
                        new NekkoraIconButton(IconName.REFRESH, "Atualizar"),
                        new NekkoraIconButton(IconName.SETTINGS, "Configurações", NekkoraButton.Variant.SECONDARY),
                        new NekkoraIconButton(IconName.MORE_HORIZONTAL, "Mais opções").withSize(ControlSize.SMALL)));
        NekkoraButton toggle = NekkoraButton.ghost("Mostrar grade").withIcon(IconName.GRID);
        toggle.setToggle(true);
        toggle.setSelected(true);
        NekkoraButton toggleOff = NekkoraButton.ghost("Mostrar régua").withIcon(IconName.SLIDERS);
        toggleOff.setToggle(true);
        p.section("Alternável", "Botões ghost podem ter estado ligado/desligado (ex.: opções de visualização).",
                Page.tightRow(toggle, toggleOff));
        return p;
    }

    private static String label(NekkoraButton.Variant v) {
        return switch (v) {
            case PRIMARY -> "Salvar";
            case SECONDARY -> "Cancelar";
            case GHOST -> "Opções";
            case DANGER -> "Excluir";
            case LINK -> "Saiba mais";
        };
    }
}
