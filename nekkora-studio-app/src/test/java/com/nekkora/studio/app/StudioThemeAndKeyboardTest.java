package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.app.ui.StudioActions;
import com.nekkora.studio.app.workspace.Selection;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.ui.LogicalSessionView;
import com.nekkora.studio.ui.form.NameDescriptionForm;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.theme.ThemeManager;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** S001-015, S001-016 e S001-018 — temas e teclado. */
class StudioThemeAndKeyboardTest {
    private final StudioFixture fx = new StudioFixture();

    @AfterEach
    void tearDown() {
        Edt.theme(NekkoraThemes.SAKURA_DARK);
        fx.dispose();
    }

    @Test
    @DisplayName("S001-015 Sakura Dark é o tema da inicialização e pinta com os tokens do tema")
    void sakuraDark() {
        assertSame(NekkoraThemes.SAKURA_DARK, Edt.call(ThemeManager::current));
        fx.missionExample();
        BufferedImage img = Edt.render(fx.studio.view().component(), 1440, 900);
        int canvas = Edt.call(() -> ThemeManager.color(ColorToken.BACKGROUND_CANVAS).getRGB()) & 0xFFFFFF;
        int samples = 0;
        for (int x = 600; x < 1400; x += 40) if ((img.getRGB(x, 895) & 0xFFFFFF) == canvas) samples++;
        assertTrue(samples > 10, "barra de status usa background.canvas do Sakura Dark");
        int surface = Edt.call(() -> ThemeManager.color(ColorToken.BACKGROUND_SURFACE).getRGB()) & 0xFFFFFF;
        int surfaceSamples = 0;
        for (int y = 300; y < 800; y += 25) if ((img.getRGB(400, y) & 0xFFFFFF) == surface) surfaceSamples++;
        assertTrue(surfaceSamples > 0, "área da automação usa background.surface do Sakura Dark");
        assertTrue(Edt.call(() -> fx.studio.view().statusItems().get(3).getText()).equals("Sakura Dark"));
    }

    @Test
    @DisplayName("S001-016 alternar os quatro temas ao vivo não perde estado nem seleção")
    void alternanciaDeTemas() {
        Automation a = fx.missionExample();
        Edt.run(() -> fx.sessionViews().get(1).stepList().setSelectedIndex(1));
        Selection before = fx.studio.state().selection();
        String linesBefore = String.join("|", fx.stepLines());
        for (NekkoraTheme t : NekkoraThemes.all()) {
            Edt.theme(t);
            Edt.render(fx.studio.view().component(), 1440, 900);
            assertSame(t, Edt.call(ThemeManager::current));
            assertSame(a, fx.active());
            assertEquals(7, a.stepCount());
            assertEquals(before, fx.studio.state().selection(), t.id());
            assertEquals(linesBefore, String.join("|", fx.stepLines()), t.id());
            assertTrue(fx.inspectorText().contains("Preencher campo de pesquisa"), t.id());
            assertEquals(t.displayName(), Edt.call(() -> fx.studio.view().statusItems().get(3).getText()));
        }
        assertEquals(4, NekkoraThemes.all().size());
        Edt.run(StudioActions::cycleTheme); // Ctrl+Shift+T
        assertSame(NekkoraThemes.all().get(0), Edt.call(ThemeManager::current));
    }

    @Test
    @DisplayName("S001-018 atalhos de teclado acionam os fluxos (Ctrl+N, Ctrl+Shift+N, Ctrl+E, F2, Enter)")
    void atalhos() {
        JComponent root = fx.studio.view().component();
        fx.prompts.then(ScriptedPrompts.name("Por teclado", null));
        assertTrue(press(root, KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK)), "Ctrl+N");
        Automation a = fx.active();
        assertEquals("Por teclado", a.name());

        assertFalse(press(root, KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK)), "Ctrl+E desabilitado sem sessões");
        fx.prompts.then(ScriptedPrompts.name("Login", null));
        assertTrue(press(root, KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)), "Ctrl+Shift+N");
        assertEquals(1, a.sessions().size());

        fx.prompts.then(ScriptedPrompts.step("Login", "Clicar em Entrar", ActionType.CLICK));
        assertTrue(press(root, KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK)), "Ctrl+E");
        assertEquals(1, a.stepCount());

        Edt.run(() -> fx.studio.controller().selectSession(a.id(), a.sessions().get(0).id()));
        fx.prompts.then(ScriptedPrompts.name("Autenticação", null));
        assertTrue(press(root, KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)), "F2 renomeia a sessão selecionada");
        assertEquals("Autenticação", a.sessions().get(0).name());

        LogicalSessionView v = fx.sessionViews().get(0);
        Edt.run(() -> v.stepList().setSelectedIndex(0));
        fx.prompts.thenCancel();
        assertTrue(press(v.stepList(), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)), "Enter na lista abre os detalhes");
        assertEquals("Clicar em Entrar", fx.prompts.last().title());
        assertTrue(fx.prompts.results.get(fx.prompts.results.size() - 1) == Boolean.FALSE, "Esc/cancelar não altera nada");
        assertEquals("Clicar em Entrar", a.sessions().get(0).steps().get(0).name());
    }

    @Test
    @DisplayName("S001-018 Tab e Shift+Tab atravessam as áreas de texto dos formulários")
    void tabNasAreasDeTexto() {
        NameDescriptionForm f = Edt.call(() -> new NameDescriptionForm("Nome", "", null, true));
        Edt.run(() -> {
            assertTrue(f.descriptionInput().getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)
                    .contains(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0)));
            assertTrue(f.descriptionInput().getFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS)
                    .contains(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK)));
            assertSame(f.nameInput(), f.initialFocus());
        });
    }

    @Test
    @DisplayName("S001-018 todo controle interativo da tela principal é alcançável por teclado e tem nome acessível")
    void focoEAcessibilidade() {
        fx.missionExample();
        Edt.run(() -> walk(fx.studio.view().component()));
    }

    private static void walk(java.awt.Component c) {
        if (!c.isVisible()) return; // ramo oculto (ex.: estado vazio em outra carta)
        // botões da Nekkora (os botões internos do Look and Feel — setas de rolagem/abas — têm alternativas de teclado próprias)
        if (c instanceof com.nekkora.ui.button.NekkoraButton b && b.isEnabled()) {
            boolean inToolbar = javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JToolBar.class, b) != null;
            if (inToolbar) {
                // FlatLaf mantém botões de toolbar fora da ordem de Tab; toda ação ali tem atalho de teclado
                String tip = String.valueOf(b.getToolTipText());
                assertTrue(b.isFocusable() || tip.contains("Ctrl+") || tip.contains("F2"), "ação de toolbar sem atalho: " + tip);
            } else {
                assertTrue(b.isFocusable(), "botão sem foco por teclado: " + b.getText());
            }
            String name = b.getAccessibleContext().getAccessibleName();
            assertTrue(name != null && !name.isBlank(), "botão sem nome acessível");
        }
        if (c instanceof java.awt.Container ct) for (java.awt.Component ch : ct.getComponents()) walk(ch);
    }

    /** Aciona o binding de teclado (como o teclado real faria) sem depender de foco de janela. */
    private static boolean press(JComponent scope, KeyStroke ks) {
        return Edt.call(() -> pressDeep(scope, ks));
    }

    private static boolean pressDeep(java.awt.Component c, KeyStroke ks) {
        if (c instanceof JComponent jc) {
            for (int cond : new int[] {JComponent.WHEN_FOCUSED, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, JComponent.WHEN_IN_FOCUSED_WINDOW}) {
                Object key = jc.getInputMap(cond).get(ks);
                if (key == null) continue;
                Action a = jc.getActionMap().get(key);
                if (a == null || !a.isEnabled()) continue;
                a.actionPerformed(new ActionEvent(jc, ActionEvent.ACTION_PERFORMED, String.valueOf(key)));
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("diálogo indisponível enquanto outro está aberto (sem reentrada)")
    void semReentrada() {
        fx.prompts.then(f -> {
            fx.studio.controller().newAutomation(); // segundo pedido durante o primeiro diálogo: ignorado
            ScriptedPrompts.name("Única", null).accept(f);
        });
        Edt.run(() -> fx.studio.controller().newAutomation());
        assertEquals(1, fx.prompts.shown.size());
        assertNull(fx.studio.state().currentOperation().orElse(null));
    }
}
