package com.nekkora.ui.icon;

import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpretador mínimo do atributo {@code d} de SVG (M L H V C S Q T A Z, absolutos e relativos).
 * Permite que Swing use exatamente os mesmos desenhos da marca e dos ícones web.
 */
public final class SvgPath {
    private static final Pattern TOKEN =
            Pattern.compile("[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\\d*\\.\\d+|\\d+\\.?\\d*)(?:[eE][-+]?\\d+)?");

    private SvgPath() {}

    public static Path2D.Float parse(String d) {
        List<String> tokens = new ArrayList<>();
        Matcher m = TOKEN.matcher(d);
        while (m.find()) tokens.add(m.group());

        Path2D.Float p = new Path2D.Float();
        int i = 0;
        char cmd = 0;
        double x = 0, y = 0, startX = 0, startY = 0;
        double lastCx = 0, lastCy = 0; // último ponto de controle (para S/T)
        char lastCmd = 0;
        while (i < tokens.size()) {
            String t = tokens.get(i);
            if (Character.isLetter(t.charAt(0)) && t.length() == 1) {
                cmd = t.charAt(0);
                i++;
                if (cmd == 'Z' || cmd == 'z') {
                    p.closePath();
                    x = startX;
                    y = startY;
                    lastCmd = cmd;
                    continue;
                }
            } else if (cmd == 0) {
                throw new IllegalArgumentException("Caminho SVG sem comando inicial: " + d);
            }
            boolean rel = Character.isLowerCase(cmd);
            double ox = rel ? x : 0, oy = rel ? y : 0;
            switch (Character.toUpperCase(cmd)) {
                case 'M' -> {
                    x = ox + num(tokens, i++);
                    y = oy + num(tokens, i++);
                    p.moveTo(x, y);
                    startX = x;
                    startY = y;
                    cmd = rel ? 'l' : 'L'; // pares seguintes são linhas
                }
                case 'L' -> {
                    x = ox + num(tokens, i++);
                    y = oy + num(tokens, i++);
                    p.lineTo(x, y);
                }
                case 'H' -> {
                    x = ox + num(tokens, i++);
                    p.lineTo(x, y);
                }
                case 'V' -> {
                    y = oy + num(tokens, i++);
                    p.lineTo(x, y);
                }
                case 'C' -> {
                    double x1 = ox + num(tokens, i++), y1 = oy + num(tokens, i++);
                    double x2 = ox + num(tokens, i++), y2 = oy + num(tokens, i++);
                    x = ox + num(tokens, i++);
                    y = oy + num(tokens, i++);
                    p.curveTo(x1, y1, x2, y2, x, y);
                    lastCx = x2;
                    lastCy = y2;
                }
                case 'S' -> {
                    boolean prevCubic = "CcSs".indexOf(lastCmd) >= 0;
                    double x1 = prevCubic ? 2 * x - lastCx : x, y1 = prevCubic ? 2 * y - lastCy : y;
                    double x2 = ox + num(tokens, i++), y2 = oy + num(tokens, i++);
                    x = ox + num(tokens, i++);
                    y = oy + num(tokens, i++);
                    p.curveTo(x1, y1, x2, y2, x, y);
                    lastCx = x2;
                    lastCy = y2;
                }
                case 'Q' -> {
                    double x1 = ox + num(tokens, i++), y1 = oy + num(tokens, i++);
                    x = ox + num(tokens, i++);
                    y = oy + num(tokens, i++);
                    p.quadTo(x1, y1, x, y);
                    lastCx = x1;
                    lastCy = y1;
                }
                case 'T' -> {
                    boolean prevQuad = "QqTt".indexOf(lastCmd) >= 0;
                    double x1 = prevQuad ? 2 * x - lastCx : x, y1 = prevQuad ? 2 * y - lastCy : y;
                    x = ox + num(tokens, i++);
                    y = oy + num(tokens, i++);
                    p.quadTo(x1, y1, x, y);
                    lastCx = x1;
                    lastCy = y1;
                }
                case 'A' -> {
                    double rx = num(tokens, i++), ry = num(tokens, i++), phi = num(tokens, i++);
                    boolean large = num(tokens, i++) != 0, sweep = num(tokens, i++) != 0;
                    double nx = ox + num(tokens, i++), ny = oy + num(tokens, i++);
                    arcTo(p, x, y, rx, ry, phi, large, sweep, nx, ny);
                    x = nx;
                    y = ny;
                }
                default -> throw new IllegalArgumentException("Comando SVG não suportado: " + cmd);
            }
            lastCmd = cmd;
        }
        return p;
    }

    private static double num(List<String> tokens, int i) {
        return Double.parseDouble(tokens.get(i));
    }

    /** Arco elíptico SVG convertido em cúbicas (SVG 1.1, apêndice F.6.5). */
    private static void arcTo(Path2D p, double x0, double y0, double rx, double ry, double phiDeg,
                              boolean large, boolean sweep, double x, double y) {
        if (x0 == x && y0 == y) return;
        if (rx == 0 || ry == 0) {
            p.lineTo(x, y);
            return;
        }
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        double phi = Math.toRadians(phiDeg % 360);
        double cos = Math.cos(phi), sin = Math.sin(phi);
        double dx = (x0 - x) / 2, dy = (y0 - y) / 2;
        double x1p = cos * dx + sin * dy, y1p = -sin * dx + cos * dy;
        double lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
        if (lambda > 1) {
            double s = Math.sqrt(lambda);
            rx *= s;
            ry *= s;
        }
        double num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
        double den = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
        double coef = Math.sqrt(Math.max(0, num / den)) * (large == sweep ? -1 : 1);
        double cxp = coef * rx * y1p / ry, cyp = -coef * ry * x1p / rx;
        double cx = cos * cxp - sin * cyp + (x0 + x) / 2, cy = sin * cxp + cos * cyp + (y0 + y) / 2;
        double theta1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry);
        double dtheta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry);
        if (!sweep && dtheta > 0) dtheta -= 2 * Math.PI;
        else if (sweep && dtheta < 0) dtheta += 2 * Math.PI;

        int segments = (int) Math.ceil(Math.abs(dtheta) / (Math.PI / 2) - 1e-7);
        double delta = dtheta / segments;
        double t = 4.0 / 3.0 * Math.tan(delta / 4);
        double a = theta1;
        for (int s = 0; s < segments; s++) {
            double cosA = Math.cos(a), sinA = Math.sin(a);
            double b = a + delta;
            double cosB = Math.cos(b), sinB = Math.sin(b);
            double e1x = cosA - t * sinA, e1y = sinA + t * cosA;
            double e2x = cosB + t * sinB, e2y = sinB - t * cosB;
            p.curveTo(
                    cx + rx * e1x * cos - ry * e1y * sin, cy + rx * e1x * sin + ry * e1y * cos,
                    cx + rx * e2x * cos - ry * e2y * sin, cy + rx * e2x * sin + ry * e2y * cos,
                    cx + rx * cosB * cos - ry * sinB * sin, cy + rx * cosB * sin + ry * sinB * cos);
            a = b;
        }
    }

    private static double angle(double ux, double uy, double vx, double vy) {
        double a = Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy);
        return a;
    }
}
