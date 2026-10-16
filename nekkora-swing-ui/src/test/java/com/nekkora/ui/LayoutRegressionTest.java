package com.nekkora.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.dialog.NekkoraDialogPanel;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.state.NekkoraEmptyState;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.typography.NekkoraHeading;
import com.nekkora.ui.typography.NekkoraParagraph;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regressões de layout encontradas ao montar o Nekkora Studio (STUDIO-001) em 1366×768: cada caso reproduz
 * o defeito observado na aplicação real.
 */
class LayoutRegressionTest {

    @Test
    @DisplayName("NekkoraParagraph em área de largura zero não revalida para sempre (travava a EDT)")
    void paragrafoEmLarguraZero() throws Exception {
        TestSupport.theme(NekkoraThemes.SAKURA_DARK);
        AtomicInteger revalidations = new AtomicInteger();
        NekkoraParagraph p = TestSupport.onEdt(() -> new NekkoraParagraph("Texto que quebra em várias linhas quando a largura é pequena.") {
            @Override
            public void revalidate() {
                revalidations.incrementAndGet();
                super.revalidate();
            }
        });
        JPanel host = TestSupport.onEdt(() -> {
            JPanel h = new JPanel(null);
            h.add(p);
            return h;
        });
        for (int round = 0; round < 5; round++) {
            TestSupport.onEdt(() -> {
                p.getPreferredSize();      // o layout pede o tamanho preferido...
                p.setBounds(0, 0, 0, 0);   // ...e a área disponível tem largura zero (ex.: grupo recolhido)
            });
            SwingUtilities.invokeAndWait(() -> { }); // processa o que tiver sido agendado
        }
        int afterWarmUp = revalidations.get();
        TestSupport.onEdt(() -> {
            p.getPreferredSize();
            p.setBounds(0, 0, 0, 0);
        });
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(afterWarmUp, revalidations.get(), "a estimativa de tamanho não pode agendar novas validações");
        assertTrue(host.getComponentCount() == 1);
    }

    @Test
    @DisplayName("NekkoraEmptyState em painel estreito mantém o ícone e quebra o texto na largura disponível")
    void estadoVazioEstreito() {
        TestSupport.theme(NekkoraThemes.SAKURA_DARK);
        NekkoraEmptyState e = TestSupport.onEdt(() -> new NekkoraEmptyState(IconName.WORKFLOW, Tone.ACCENT, "Nenhuma automação",
                "Crie a primeira automação para organizar sessões e etapas."));
        int width = Scale.px(240);
        TestSupport.render(e, width, Scale.px(400));
        TestSupport.render(e, width, Scale.px(400)); // segunda passada: descrição já recalculada
        TestSupport.onEdt(() -> {
            Component glyph = find(e, c -> c.getClass().getSimpleName().equals("Glyph"));
            assertEquals(Scale.px(64), glyph.getHeight(), "o ícone não pode colapsar");
            Component description = find(e, c -> c instanceof com.nekkora.ui.typography.NekkoraText);
            assertTrue(description.getWidth() <= width, "descrição maior que o painel: " + description.getWidth());
            assertTrue(description.getX() >= 0, "descrição cortada à esquerda");
        });
    }

    @Test
    @DisplayName("Títulos em fonte de display têm folga para o texto desenhado (última letra não é cortada)")
    void tituloDeDisplayComFolga() {
        TestSupport.theme(NekkoraThemes.SAKURA_DARK);
        NekkoraDialogPanel panel = TestSupport.onEdt(() -> {
            NekkoraDialogPanel p = new NekkoraDialogPanel("Nova etapa");
            p.setMarker(IconName.PLUS, Tone.ACCENT);
            p.setDescription("Descrição do diálogo.");
            return p;
        });
        TestSupport.render(panel, Scale.px(540), Scale.px(240));
        TestSupport.onEdt(() -> {
            NekkoraHeading title = (NekkoraHeading) find(panel, c -> c instanceof NekkoraHeading);
            int measured = title.getFontMetrics(title.getFont()).stringWidth(title.getText());
            // medido no Windows: o texto desenhado chega a ~4% além da métrica (92px → 96px em "Nova etapa")
            assertTrue(title.getWidth() >= Math.ceil(measured * 1.045), "título sem folga: " + title.getWidth() + " para " + measured);
            NekkoraHeading h3 = new NekkoraHeading("Resumo", NekkoraHeading.Level.H3);
            assertEquals(h3.getFontMetrics(h3.getFont()).stringWidth("Resumo"), h3.getPreferredSize().width - h3.getInsets().left
                    - h3.getInsets().right, "fonte de interface não muda");
        });
    }

    private static Component find(Container root, java.util.function.Predicate<Component> p) {
        for (Component c : root.getComponents()) {
            if (p.test(c)) return c;
            if (c instanceof Container ct) {
                Component f = find(ct, p);
                if (f != null) return f;
            }
        }
        return null;
    }

    @Test
    @DisplayName("STUDIO-006: barra de status estreita (1366×768 a 150%) não sobrepõe itens; esquerda tem prioridade; larga = posições do BorderLayout")
    void barraDeStatusSemSobreposicao() throws Exception {
        TestSupport.theme(NekkoraThemes.SAKURA_DARK);
        java.util.List<javax.swing.JComponent> items = new java.util.ArrayList<>();
        com.nekkora.ui.studio.NekkoraStatusBar bar = TestSupport.onEdt(() -> {
            com.nekkora.ui.studio.NekkoraStatusBar b = new com.nekkora.ui.studio.NekkoraStatusBar();
            for (String t : java.util.List.of("Alterações não salvas — sem arquivo", "Navegador: conectado (Nekkora Extension 0.4.0)", "Alterações não salvas",
                    "1 automação no Studio")) {
                javax.swing.JComponent c = com.nekkora.ui.studio.NekkoraStatusBar.item(IconName.ALERT, t);
                items.add(c);
                b.addLeft(c);
            }
            for (String t : java.util.List.of("Recuperação desativada", "Sakura Dark")) {
                javax.swing.JComponent c = com.nekkora.ui.studio.NekkoraStatusBar.item(IconName.SUN, t);
                items.add(c);
                b.addRight(c);
            }
            return b;
        });
        int wide = TestSupport.onEdt(() -> bar.getPreferredSize().width) + 40;
        for (int width : new int[] {wide, 910, 870, 600, 300, 0}) {
            java.util.List<java.awt.Rectangle> shown = TestSupport.onEdt(() -> {
                bar.setSize(width, Scale.px(28));
                bar.doLayout();
                for (Component side : bar.getComponents()) ((Container) side).doLayout();
                java.util.List<java.awt.Rectangle> out = new java.util.ArrayList<>();
                for (javax.swing.JComponent c : items) {
                    if (!com.nekkora.ui.studio.NekkoraStatusBar.shown(c)) continue;
                    java.awt.Rectangle r = SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), bar);
                    assertTrue(r.x >= 0 && r.x + r.width <= width, "item dentro da barra (" + width + "): " + r);
                    assertEquals(c.getPreferredSize().width, r.width, "item inteiro, nunca cortado");
                    out.add(r);
                }
                return out;
            });
            for (int i = 0; i < shown.size(); i++) {
                for (int j = i + 1; j < shown.size(); j++) assertTrue(!shown.get(i).intersects(shown.get(j)), width + ": " + shown.get(i) + " × " + shown.get(j));
            }
            if (width == wide) assertEquals(items.size(), shown.size(), "com espaço, tudo aparece");
            if (width == 870) {
                assertTrue(TestSupport.onEdt(() -> com.nekkora.ui.studio.NekkoraStatusBar.shown(items.get(0))), "o primeiro item (situação do projeto) fica");
            }
        }
    }
}
