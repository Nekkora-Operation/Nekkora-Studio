package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.variable.SemanticStatus;
import com.nekkora.studio.domain.variable.SemanticType;
import com.nekkora.studio.domain.variable.Sensitivity;
import com.nekkora.studio.domain.variable.VariableDraft;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.variable.VariableType;
import com.nekkora.studio.ui.form.VariableForm;
import com.nekkora.ui.theme.Scale;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * STUDIO-008 a 150% (mesma JVM da execução {@code scale-150} do Surefire, {@code -Dflatlaf.uiScale=1.5}): aba Variáveis
 * aberta em 1366×768 lógicos com os botões inteiros, a área de trabalho utilizável e o formulário de variável sem cortes.
 */
class Variables150Test {
    private StudioFixture fx;

    @AfterEach
    void tearDown() {
        if (fx != null) fx.dispose();
    }

    @Test
    @DisplayName("STUDIO-008 a 150%, 1366×768 lógicos: painel de variáveis e formulário inteiros")
    void variaveis150() {
        org.junit.jupiter.api.Assumptions.assumeTrue("1.5".equals(System.getProperty("flatlaf.uiScale")), "executado apenas na execução scale-150");
        fx = new StudioFixture();
        assertEquals(1.5f, Edt.call(Scale::factor), 0.001f);
        fx.missionExample();
        Edt.run(() -> {
            var a = fx.active();
            var cpf = fx.studio.service().declareVariable(a.id(), VariableDraft.of("cliente.cpf", VariableType.STRING)
                    .withSemantic(SemanticType.BRAZILIAN_CPF, SemanticStatus.USER_CONFIRMED).withSensitivity(Sensitivity.PERSONAL_DATA)
                    .withOrigin(VariableOrigin.input()));
            fx.studio.service().setStepParameters(a.id(), a.sessions().get(0).steps().get(0).id(),
                    ActionParameters.withValue(new ValueSource.VariableReference(cpf.name())));
            fx.studio.controller().showVariables();
        });
        int w = Math.round(1366 * 1.5f), h = Math.round(768 * 1.5f);
        Edt.render(fx.studio.view().component(), w, h);
        Edt.run(() -> {
            assertTrue(fx.studio.view().variablesVisible());
            fx.studio.view().variablesPanel().variableList().setSelectedIndex(0);
            Rectangle screen = new Rectangle(0, 0, w, h);
            check(fx.studio.view().component(), fx.studio.view().component(), screen);
            assertTrue(fx.studio.view().variablesPanel().getHeight() >= Scale.px(200), "painel baixo demais: " + fx.studio.view().variablesPanel().getHeight());
            assertTrue(fx.studio.view().workArea().getHeight() >= Scale.px(200), "área de trabalho espremida: " + fx.studio.view().workArea().getHeight());
            // os botões do rodapé ficam inteiros DENTRO do painel (sem janela real, isShowing() não se aplica)
            var panel = fx.studio.view().variablesPanel();
            for (var b : java.util.List.of(panel.editButton(), panel.renameButton(), panel.deleteButton(), panel.goToButton(), panel.createButton())) {
                Rectangle r = SwingUtilities.convertRectangle(b.getParent(), b.getBounds(), panel);
                assertTrue(b.getWidth() > 0 && b.getHeight() > 0 && new Rectangle(0, 0, panel.getWidth(), panel.getHeight()).contains(r),
                        "botão cortado a 150%: " + b.getText() + " " + r + " em " + panel.getSize());
            }
        });
        // formulário de variável: cada aba cabe num diálogo grande a 150% (conteúdo rolável limitado)
        VariableForm form = Edt.call(() -> new VariableForm(fx.active(), fx.active().variables().get(0)));
        for (int tab = 0; tab < 3; tab++) {
            int t = tab;
            int height = Edt.call(() -> {
                form.tabs().setSelectedIndex(t);
                Edt.render(form, Scale.px(720), Scale.px(560));
                return form.getPreferredSize().height;
            });
            assertTrue(height > 0 && height <= Scale.px(720), "aba " + tab + ": " + height);
        }
    }

    /** Botões visíveis precisam estar inteiros dentro da janela e com espaço para o rótulo. */
    private static void check(Component root, Component c, Rectangle screen) {
        if (!c.isVisible() || c instanceof javax.swing.JViewport) return;
        if (c instanceof com.nekkora.ui.button.NekkoraButton b && b.getWidth() > 0 && b.getHeight() > 0) {
            Rectangle r = SwingUtilities.convertRectangle(b.getParent(), b.getBounds(), root);
            assertTrue(screen.contains(r), "botão fora da janela a 150%: " + b.getText() + " " + r);
            assertTrue(b.getWidth() >= b.getMinimumSize().width - 1, "botão comprimido: " + b.getText());
        }
        if (c instanceof Container ct) for (Component ch : ct.getComponents()) check(root, ch, screen);
    }
}
