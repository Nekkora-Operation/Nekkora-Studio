package com.nekkora.studio.domain.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * Posição dos NÚMEROS dos marcadores do PDD (o círculo com a ordem da instrução), a mesma regra no editor do Studio e no
 * PDF. Coordenadas com y para BAIXO, na escala de quem desenha.
 *
 * <p>Regra: para cada marcador, na ordem, tenta posições em volta do contorno (esquerda, acima, direita, abaixo, cantos)
 * e, depois, cantos por dentro dele. Vale a primeira que fica INTEIRA dentro da área visível (o recorte, quando há) e não
 * encosta em nenhum número já posto; numa primeira passada também evita os obstáculos (campos e outros contornos). Se
 * nenhuma serve, usa o ponto livre da área mais perto da posição preferida. Números nunca ficam um em cima do outro
 * enquanto houver espaço na área.
 *
 * <p>Antes, o editor punha o número sempre à esquerda do contorno e o PDF, sem lugar livre, voltava à primeira posição:
 * campos empilhados (Usuário, Senha) num recorte justo ficavam com os números sobrepostos.
 */
public final class MarkerLayout {

    /** Retângulo (y para baixo). */
    public record Box(double x, double y, double width, double height) {
        boolean overlaps(Box o) {
            return x < o.x + o.width && o.x < x + width && y < o.y + o.height && o.y < y + height;
        }

        boolean inside(Box area) {
            return x >= area.x - 1e-6 && y >= area.y - 1e-6 && x + width <= area.x + area.width + 1e-6 && y + height <= area.y + area.height + 1e-6;
        }
    }

    /** Centro do número. */
    public record Point(double x, double y) {}

    private MarkerLayout() {}

    /**
     * @param marks     contornos dos marcadores, na ordem em que são numerados
     * @param area      área visível onde os números precisam caber
     * @param radius    raio do círculo do número
     * @param gap       folga entre o círculo e o contorno (e entre dois círculos)
     * @param obstacles o que o número deveria evitar cobrir (campos, botões); os próprios contornos já entram
     * @return o centro de cada número, na mesma ordem
     */
    public static List<Point> place(List<Box> marks, Box area, double radius, double gap, List<Box> obstacles) {
        List<Box> avoid = new ArrayList<>(obstacles);
        avoid.addAll(marks);
        List<Box> badges = new ArrayList<>();
        List<Point> out = new ArrayList<>();
        double d = 2 * radius;
        for (Box m : marks) {
            List<Point> c = candidates(m, radius, gap);
            Point chosen = null;
            for (int pass = 0; pass < 2 && chosen == null; pass++) {
                for (Point p : c) {
                    Box b = badge(p, radius, gap);
                    if (!b.inside(area) || badges.stream().anyMatch(b::overlaps)) continue;
                    if (pass == 0 && avoid.stream().anyMatch(o -> o != m && b.overlaps(o))) continue;
                    chosen = p;
                    break;
                }
            }
            if (chosen == null) chosen = stepped(clamp(c.get(0), area, radius, gap), area, radius, gap, badges, d);
            badges.add(badge(chosen, radius, gap));
            out.add(chosen);
        }
        return out;
    }

    private static List<Point> candidates(Box m, double r, double g) {
        double left = m.x() - r - g;
        double right = m.x() + m.width() + r + g;
        double above = m.y() - r - g;
        double below = m.y() + m.height() + r + g;
        double midY = m.y() + m.height() / 2;
        double inL = m.x() + r + 1;
        double inR = m.x() + m.width() - r - 1;
        double inT = m.y() + r + 1;
        double inB = m.y() + m.height() - r - 1;
        return List.of(new Point(left, midY), new Point(m.x() + r, above), new Point(right, midY), new Point(m.x() + r, below), new Point(left, above),
                new Point(right, above), new Point(left, below), new Point(right, below), new Point(inL, inT), new Point(inR, inT), new Point(inL, inB),
                new Point(inR, inB));
    }

    /** O círculo com a folga em volta. */
    private static Box badge(Point p, double r, double g) {
        double h = g / 2;
        return new Box(p.x() - r - h, p.y() - r - h, 2 * r + g, 2 * r + g);
    }

    private static Point clamp(Point p, Box area, double r, double g) {
        double m = r + g / 2;
        double x = Math.max(area.x() + m, Math.min(area.x() + area.width() - m, p.x()));
        double y = Math.max(area.y() + m, Math.min(area.y() + area.height() - m, p.y()));
        return new Point(x, y);
    }

    /** Sem posição livre em volta: o ponto livre da área mais perto da posição preferida (varredura de 1 em 1). */
    private static Point stepped(Point start, Box area, double r, double g, List<Box> badges, double d) {
        double m = r + g / 2;
        Point best = null;
        double bestDist = Double.MAX_VALUE;
        for (double y = area.y() + m; y <= area.y() + area.height() - m + 1e-6; y += 1) {
            for (double x = area.x() + m; x <= area.x() + area.width() - m + 1e-6; x += 1) {
                double dist = Math.hypot(x - start.x(), y - start.y());
                if (dist >= bestDist) continue;
                Box b = badge(new Point(x, y), r, g);
                if (badges.stream().noneMatch(b::overlaps)) {
                    best = new Point(x, y);
                    bestDist = dist;
                }
            }
        }
        return best != null ? best : start; // área cheia de números: não há lugar sem sobreposição
    }
}
