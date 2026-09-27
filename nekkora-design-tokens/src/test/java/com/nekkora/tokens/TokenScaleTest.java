package com.nekkora.tokens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenScaleTest {

    @Test
    void escalaDeEspacoCrescenteDe1a12() {
        assertEquals(12, Spacing.values().length);
        for (int i = 1; i < Spacing.values().length; i++) {
            assertTrue(Spacing.values()[i].px() > Spacing.values()[i - 1].px());
        }
        assertEquals("space.4", Spacing.of(4).key());
        assertThrows(IllegalArgumentException.class, () -> Spacing.of(13));
    }

    @Test
    void chavesDeCorUnicasEReversiveis() {
        for (ColorToken t : ColorToken.values()) {
            assertEquals(t, ColorToken.fromKey(t.key()));
        }
    }

    @Test
    void temasOficiais() {
        assertEquals(4, NekkoraThemes.all().size());
        assertEquals(NekkoraThemes.SAKURA_DARK, NekkoraThemes.defaultTheme());
        assertTrue(NekkoraThemes.byId("midnight-dark").isPresent());
        assertEquals(2, NekkoraThemes.all().stream().filter(NekkoraTheme::isDark).count());
    }

    @Test
    void hierarquiaTipograficaDecrescente() {
        TypeStyle[] order = {TypeStyle.DISPLAY_L, TypeStyle.DISPLAY_M, TypeStyle.H1, TypeStyle.H2, TypeStyle.H3};
        for (int i = 1; i < order.length; i++) {
            assertTrue(order[i].size() < order[i - 1].size());
        }
        assertEquals(FontRole.MONO, TypeStyle.MONOSPACE.role());
    }
}
