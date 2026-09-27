package com.nekkora.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.action.NekkoraAction;
import com.nekkora.ui.action.NekkoraActionRegistry;
import com.nekkora.ui.action.NekkoraKeys;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.IconSize;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.icon.SvgPath;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class IconAndActionTest {

    @Test
    void todosOsIconesTemDesenhoEPintam() {
        TestSupport.theme(NekkoraThemes.SAKURA_DARK);
        TestSupport.onEdt(() -> {
            JLabel host = new JLabel();
            for (IconName n : IconName.values()) {
                NekkoraIcon icon = NekkoraIcon.of(n, IconSize.XL);
                BufferedImage img = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
                icon.paintIcon(host, img.getGraphics(), 0, 0);
                assertTrue(hasInk(img), "ícone vazio: " + n.id());
            }
        });
    }

    @Test
    void svgPathInterpretaArcosERelativos() {
        Rectangle2D circle = SvgPath.parse("M2 12A10 10 0 1 0 22 12A10 10 0 1 0 2 12Z").getBounds2D();
        assertEquals(2, circle.getMinX(), 0.05);
        assertEquals(22, circle.getMaxX(), 0.05);
        assertEquals(2, circle.getMinY(), 0.05);
        assertEquals(22, circle.getMaxY(), 0.05);
        Rectangle2D rel = SvgPath.parse("m1 1 h4 v4 h-4z").getBounds2D();
        assertEquals(new Rectangle2D.Double(1, 1, 4, 4), rel);
        Rectangle2D compact = SvgPath.parse("M1.5.5L.5 2").getBounds2D();
        assertEquals(0.5, compact.getMinX(), 1e-6);
    }

    @Test
    void registroDeAcoes() {
        NekkoraActionRegistry r = new NekkoraActionRegistry();
        int[] runs = {0};
        NekkoraAction save = r.register(NekkoraAction.of("file.save", "Salvar arquivo", () -> runs[0]++)
                .icon(IconName.SAVE).category("Arquivo").shortcut(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK)));
        r.register(NekkoraAction.of("run.start", "Executar", () -> { }).category("Execução"));
        assertThrows(IllegalArgumentException.class, () -> r.register(NekkoraAction.of("file.save", "x", () -> { })));
        assertEquals(1, r.search("execucao").size(), "busca ignora acentos");
        assertEquals(2, r.search("").size());
        save.actionPerformed(null);
        save.setEnabled(false);
        save.actionPerformed(null);
        assertEquals(1, runs[0], "ação desabilitada não executa");
    }

    @Test
    void formataAtalhos() {
        String s = NekkoraKeys.format(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK));
        assertTrue(s.equals("Ctrl+Shift+S") || s.equals("⌃⇧S"), s);
    }

    private static boolean hasInk(BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) >>> 24) > 0) return true;
            }
        }
        return false;
    }
}
