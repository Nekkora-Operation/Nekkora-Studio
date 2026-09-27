package com.nekkora.ui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import java.awt.Dimension;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Cria, habilita/desabilita, dimensiona e pinta cada componente em cada tema — sem exceções. */
class ComponentSmokeTest {

    static Stream<NekkoraTheme> themes() {
        return NekkoraThemes.all().stream();
    }

    @ParameterizedTest
    @MethodSource("themes")
    void todosOsComponentesPintamEmTodosOsTemas(NekkoraTheme theme) {
        TestSupport.theme(theme);
        for (Map.Entry<String, Supplier<JComponent>> e : ComponentCatalog.all().entrySet()) {
            assertDoesNotThrow(() -> {
                JComponent c = TestSupport.onEdt(() -> e.getValue().get());
                Dimension pref = TestSupport.onEdt(c::getPreferredSize);
                assertTrue(pref.width >= 0 && pref.height >= 0, e.getKey());
                int w = Math.max(160, Math.min(900, pref.width)), h = Math.max(40, Math.min(700, pref.height));
                TestSupport.render(c, w, h);
                TestSupport.onEdt(() -> c.setEnabled(false));
                TestSupport.render(c, w, h);
            }, "Falhou: " + e.getKey() + " em " + theme.id());
        }
    }

    @Test
    void trocaDeTemaComTodosOsComponentesMontados() {
        TestSupport.theme(NekkoraThemes.SAKURA_DARK);
        JPanel host = TestSupport.onEdt(() -> {
            JPanel p = new JPanel();
            ComponentCatalog.all().values().forEach(s -> p.add(s.get()));
            return p;
        });
        for (NekkoraTheme t : NekkoraThemes.all()) {
            TestSupport.theme(t);
            assertDoesNotThrow(() -> {
                TestSupport.onEdt(() -> SwingUtilities.updateComponentTreeUI(host));
                TestSupport.render(host, 1600, 2400);
            }, t.id());
        }
        TestSupport.theme(NekkoraThemes.SAKURA_DARK);
    }

    @Test
    void componentesTemNomeAcessivelQuandoNecessario() {
        TestSupport.theme(NekkoraThemes.SAKURA_DARK);
        Map<String, Supplier<JComponent>> all = ComponentCatalog.all();
        for (String name : new String[] {"IconButton", "Avatar", "Badge", "StatusBadge", "Tag", "Spinner", "Logo", "IdentifierLabel"}) {
            JComponent c = TestSupport.onEdt(() -> all.get(name).get());
            String an = TestSupport.onEdt(() -> c.getAccessibleContext().getAccessibleName());
            assertFalse(an == null || an.isBlank(), name + " sem nome acessível");
        }
    }
}
