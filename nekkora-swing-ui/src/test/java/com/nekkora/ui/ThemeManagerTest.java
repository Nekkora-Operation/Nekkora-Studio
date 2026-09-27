package com.nekkora.ui;

import static com.nekkora.ui.TestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.FlatLaf;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.theme.ThemeListener;
import com.nekkora.ui.theme.ThemeManager;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

class ThemeManagerTest {

    @Test
    void instalaLookAndFeelNekkoraComFontesDaMarca() {
        TestSupport.theme(NekkoraThemes.SAKURA_DARK);
        onEdt(() -> {
            assertInstanceOf(FlatLaf.class, UIManager.getLookAndFeel());
            assertTrue(UIManager.getLookAndFeel().getName().contains("Sakura Dark"));
            assertEquals("Inter", UIManager.getFont("Label.font").getFamily());
            assertEquals(ThemeManager.color(ColorToken.BACKGROUND_CANVAS), UIManager.getColor("Panel.background"));
        });
    }

    @Test
    void trocaDeTemaAoVivoAtualizaDefaultsENotifica() {
        TestSupport.theme(NekkoraThemes.SAKURA_DARK);
        List<String> events = new ArrayList<>();
        ThemeListener l = (o, n) -> events.add(o.id() + "->" + n.id());
        ThemeManager.addThemeListener(l);
        try {
            for (NekkoraTheme t : NekkoraThemes.all()) {
                TestSupport.theme(t);
                onEdt(() -> {
                    assertEquals(t, ThemeManager.current());
                    assertEquals(t.color(ColorToken.TEXT_PRIMARY), UIManager.getColor("Label.foreground"));
                    assertEquals(t.isDark(), FlatLaf.isLafDark());
                });
            }
            assertEquals(NekkoraThemes.all().size(), events.size());
        } finally {
            ThemeManager.removeThemeListener(l);
        }
    }

    @Test
    void componenteExistenteAcompanhaTrocaDeTema() {
        TestSupport.theme(NekkoraThemes.SAKURA_DARK);
        NekkoraButton b = onEdt(() -> NekkoraButton.primary("Salvar"));
        JPanel host = onEdt(() -> {
            JPanel p = new JPanel();
            p.add(b);
            return p;
        });
        Color before = pixelAtCenter(host, b);
        TestSupport.theme(NekkoraThemes.PORCELAIN_LIGHT);
        onEdt(() -> SwingUtilities.updateComponentTreeUI(host));
        Color after = pixelAtCenter(host, b);
        assertNotEquals(before, after, "o botão deve ser repintado com a cor do novo tema");
        TestSupport.theme(NekkoraThemes.SAKURA_DARK);
    }

    @Test
    void setThemePodeSerChamadoForaDaEdt() {
        TestSupport.theme(NekkoraThemes.SAKURA_DARK);
        ThemeManager.setTheme(NekkoraThemes.MIDNIGHT_DARK); // thread de teste, não EDT
        assertEquals(NekkoraThemes.MIDNIGHT_DARK, ThemeManager.current());
        TestSupport.theme(NekkoraThemes.SAKURA_DARK);
    }

    private static Color pixelAtCenter(JPanel host, NekkoraButton b) {
        var img = TestSupport.render(host, 200, 60);
        return new Color(img.getRGB(b.getX() + 12, b.getY() + b.getHeight() / 2));
    }
}
