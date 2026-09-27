package com.nekkora.ui;

import static com.nekkora.ui.TestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.input.MaskFilter;
import com.nekkora.ui.input.NekkoraDateField;
import com.nekkora.ui.input.NekkoraDecimalField;
import com.nekkora.ui.input.NekkoraIntegerField;
import com.nekkora.ui.input.NekkoraTimeField;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Campos especializados: filtros, máscaras, validação e teclas de incremento. */
class InputFieldTest {

    @BeforeAll
    static void theme() {
        TestSupport.theme(NekkoraThemes.SAKURA_DARK);
    }

    @Test
    void inteiroRejeitaLetrasEValidaIntervalo() {
        onEdt(() -> {
            NekkoraIntegerField f = new NekkoraIntegerField();
            f.setRange(1, 10);
            f.replaceSelection("4a2");
            assertEquals("", f.getText(), "entrada inválida é recusada inteira");
            f.setText("42");
            assertFalse(f.isValueValid());
            assertTrue(f.isError());
            f.setValue(5);
            assertTrue(f.isValueValid());
            assertTrue(Keys.press(f, "UP"));
            assertEquals(6L, f.getValue().getAsLong());
            f.setValue(99);
            assertEquals(10L, f.getValue().getAsLong(), "setValue respeita o máximo");
        });
    }

    @Test
    void decimalUsaVirgulaEmPtBr() {
        onEdt(() -> {
            NekkoraDecimalField f = new NekkoraDecimalField(null, Locale.forLanguageTag("pt-BR"));
            f.replaceSelection("12.5");
            assertEquals("12,5", f.getText(), "ponto vira vírgula");
            assertEquals(new BigDecimal("12.5"), f.getValue().orElseThrow());
            f.setText("1,234");
            assertEquals("12,5", f.getText(), "no máximo 2 casas");
        });
    }

    @Test
    void mascaraDeDataEValidacao() {
        onEdt(() -> {
            NekkoraDateField f = new NekkoraDateField();
            f.replaceSelection("27092026");
            assertEquals("27/09/2026", f.getText());
            assertEquals(LocalDate.of(2026, 9, 27), f.getValue().orElseThrow());
            f.setText("");
            f.replaceSelection("31022026");
            assertTrue(f.getValue().isEmpty(), "31/02 não existe");
            assertTrue(f.isError());
        });
    }

    @Test
    void horaComMascaraESetas() {
        onEdt(() -> {
            NekkoraTimeField f = new NekkoraTimeField();
            f.replaceSelection("0930");
            assertEquals(LocalTime.of(9, 30), f.getValue().orElseThrow());
            assertTrue(Keys.press(f, "shift UP"));
            assertEquals(LocalTime.of(9, 40), f.getValue().orElseThrow());
        });
    }

    @Test
    void mascaraGenerica() {
        MaskFilter m = new MaskFilter("##.###-###");
        assertEquals("12.345-678", m.apply("12345678"));
        assertEquals("12.3", m.apply("123"));
    }
}
