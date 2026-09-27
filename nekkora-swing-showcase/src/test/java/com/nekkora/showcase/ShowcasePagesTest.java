package com.nekkora.showcase;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nekkora.showcase.studio.StudioDemo;
import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.theme.ThemeManager;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.stream.Stream;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Todas as páginas do Showcase e a tela Studio montam e pintam em todos os temas (headless). */
class ShowcasePagesTest {

    static Stream<NekkoraTheme> themes() {
        return NekkoraThemes.all().stream();
    }

    @ParameterizedTest
    @MethodSource("themes")
    void paginasETelaStudio(NekkoraTheme theme) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            if (ThemeManager.isInstalled()) ThemeManager.setTheme(theme);
            else ThemeManager.install(theme);
            for (ShowcaseApp.PageDef p : ShowcaseApp.PAGES) {
                assertDoesNotThrow(() -> paint(p.factory().get(), 1280, 2000), p.id());
            }
            assertDoesNotThrow(() -> paint(StudioDemo.build(), 1440, 900), "studio");
        });
        assertEquals(theme, ThemeManager.current());
    }

    private static void paint(JComponent c, int w, int h) {
        c.setSize(w, h);
        layout(c);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        c.printAll(g);
        g.dispose();
    }

    private static void layout(Component c) {
        if (c instanceof Container ct) {
            ct.doLayout();
            for (Component ch : ct.getComponents()) layout(ch);
        }
    }
}
