package com.nekkora.studio.domain.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.layout.MarkerLayout.Box;
import com.nekkora.studio.domain.layout.MarkerLayout.Point;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Números dos marcadores do PDD: nunca um em cima do outro, sempre dentro da área visível (o recorte). */
class MarkerLayoutTest {
    static final double R = 7;
    static final double G = 3;

    static void assertSeparatedAndInside(List<Point> ps, Box area) {
        for (Point p : ps) {
            assertTrue(p.x() - R >= area.x() - 1e-6 && p.x() + R <= area.x() + area.width() + 1e-6 && p.y() - R >= area.y() - 1e-6
                    && p.y() + R <= area.y() + area.height() + 1e-6, "número fora da área: " + p + " em " + area);
        }
        for (int i = 0; i < ps.size(); i++) {
            for (int j = i + 1; j < ps.size(); j++) {
                double dist = Math.hypot(ps.get(i).x() - ps.get(j).x(), ps.get(i).y() - ps.get(j).y());
                assertTrue(dist >= 2 * R, "números " + (i + 1) + " e " + (j + 1) + " sobrepostos: " + ps.get(i) + " " + ps.get(j));
            }
        }
    }

    @Test
    @DisplayName("com espaço: o número fica à esquerda do contorno, no meio da altura (a posição de sempre)")
    void posicaoPreferida() {
        List<Point> ps = MarkerLayout.place(List.of(new Box(60, 40, 120, 20)), new Box(0, 0, 300, 200), R, G, List.of());
        assertEquals(new Point(60 - R - G, 50), ps.get(0));
    }

    @Test
    @DisplayName("recorte justo em volta de campos empilhados (Usuário, Senha, Entrar): números separados e dentro do recorte")
    void recorteJustoCamposEmpilhados() {
        // escala pequena do PDF: campos de 26 px a cada 35 px viram ~5 pt de altura, a 7 pt um do outro
        List<Box> fields = List.of(new Box(2, 2, 40, 5), new Box(2, 9, 40, 5), new Box(2, 16, 12, 4));
        Box crop = new Box(0, 0, 60, 24);
        List<Point> ps = MarkerLayout.place(fields, crop, R, G, List.of());
        assertSeparatedAndInside(ps, crop);
    }

    @Test
    @DisplayName("muitos marcadores no mesmo lugar: cada número acha um lugar livre enquanto houver espaço")
    void muitosNoMesmoLugar() {
        List<Box> same = new ArrayList<>();
        for (int i = 0; i < 6; i++) same.add(new Box(40, 40, 20, 10));
        Box area = new Box(0, 0, 200, 120);
        assertSeparatedAndInside(MarkerLayout.place(same, area, R, G, List.of()), area);
    }

    @Test
    @DisplayName("evita cobrir campos quando há lugar; sem lugar, prefere não sobrepor números a não cobrir campos")
    void evitaObstaculos() {
        Box field = new Box(0, 30, 50, 20); // campo à esquerda do contorno
        List<Point> ps = MarkerLayout.place(List.of(new Box(60, 30, 80, 20)), new Box(0, 0, 300, 200), R, G, List.of(field));
        Point p = ps.get(0);
        assertTrue(!(p.x() + R > field.x() && p.x() - R < field.x() + field.width() && p.y() + R > field.y() && p.y() - R < field.y() + field.height()),
                "o número cobriu o campo: " + p);
    }
}
