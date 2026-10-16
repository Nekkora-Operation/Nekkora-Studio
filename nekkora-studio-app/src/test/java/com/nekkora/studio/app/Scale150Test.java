package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.ui.LogicalSessionView;
import com.nekkora.studio.ui.form.FormSpec;
import com.nekkora.studio.ui.form.StudioForms;
import com.nekkora.ui.theme.Scale;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * S001-017 — escala de 150%. Roda em uma JVM própria com {@code -Dflatlaf.uiScale=1.5} (execução
 * {@code scale-150} do Surefire): o fator só pode ser escolhido antes de instalar o Look and Feel.
 */
class Scale150Test {
    private StudioFixture fx;

    @AfterEach
    void tearDown() {
        if (fx != null) fx.dispose();
    }

    @Test
    @DisplayName("S001-017 a 150%, 1366×768 lógicos: controles principais visíveis e não cortados")
    void escala150() {
        org.junit.jupiter.api.Assumptions.assumeTrue("1.5".equals(System.getProperty("flatlaf.uiScale")),
                "executado apenas na execução scale-150");
        fx = new StudioFixture();
        assertEquals(1.5f, Edt.call(Scale::factor), 0.001f);
        fx.missionExample();
        int w = Math.round(1366 * 1.5f), h = Math.round(768 * 1.5f);
        Edt.render(fx.studio.view().component(), w, h);
        Edt.run(() -> {
            Rectangle screen = new Rectangle(0, 0, w, h);
            check(fx.studio.view().component(), fx.studio.view().component(), screen);
            LogicalSessionView first = fx.overview().sessionViews().get(0);
            assertTrue(first.getWidth() > Scale.px(300), "cartão de sessão estreito demais: " + first.getWidth());
            assertTrue(fx.studio.view().inspector().getWidth() >= Scale.px(240));
        });
        // formulários altos continuam inteiros na tela (conteúdo rolável limitado)
        for (FormSpec<?> spec : Edt.call(() -> java.util.List.of(StudioForms.newAutomation(), StudioForms.newStep(fx.active(), null),
                StudioForms.stepDetails(fx.active(), fx.active().sessions().get(0).steps().get(0))))) {
            int height = Edt.call(() -> spec.form().getPreferredSize().height);
            assertTrue(height > 0, spec.title());
        }
    }

    @Test
    @DisplayName("STUDIO-007 a 150%, 1366×768 lógicos: painel de validação aberto com os botões inteiros e a área de trabalho utilizável")
    void validacao150() {
        org.junit.jupiter.api.Assumptions.assumeTrue("1.5".equals(System.getProperty("flatlaf.uiScale")),
                "executado apenas na execução scale-150");
        fx = new StudioFixture();
        fx.missionExample();
        Edt.run(() -> fx.studio.controller().validateActive());
        int w = Math.round(1366 * 1.5f), h = Math.round(768 * 1.5f);
        Edt.render(fx.studio.view().component(), w, h);
        Edt.run(() -> {
            assertTrue(fx.studio.view().validationVisible());
            Rectangle screen = new Rectangle(0, 0, w, h);
            check(fx.studio.view().component(), fx.studio.view().component(), screen);
            assertTrue(fx.studio.view().validationPanel().getHeight() >= Scale.px(200), "painel baixo demais: " + fx.studio.view().validationPanel().getHeight());
            assertTrue(fx.studio.view().workArea().getHeight() >= Scale.px(200), "área de trabalho espremida: " + fx.studio.view().workArea().getHeight());
            assertTrue(fx.studio.view().validationPanel().findingList().getModel().getSize() > 0);
        });
    }

    /** Botões visíveis precisam estar inteiros dentro da janela e com espaço para o rótulo. */
    private static void check(Component root, Component c, Rectangle screen) {
        if (!c.isVisible() || c instanceof javax.swing.JViewport) return; // conteúdo rolável é alcançado rolando
        if (c instanceof com.nekkora.ui.button.NekkoraButton b && b.getWidth() > 0 && b.getHeight() > 0) {
            Rectangle r = SwingUtilities.convertRectangle(b.getParent(), b.getBounds(), root);
            assertTrue(screen.contains(r), "botão fora da janela a 150%: " + b.getText() + " " + r);
            assertTrue(b.getWidth() >= b.getMinimumSize().width - 1, "botão comprimido: " + b.getText());
        }
        if (c instanceof Container ct) for (Component ch : ct.getComponents()) check(root, ch, screen);
    }
}
