package com.nekkora.ui.icon;

import java.awt.Shape;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Carrega {@code nekkora-icons.txt} (grade 24x24) uma única vez. */
final class IconRegistry {
    /** Camadas de um ícone, em coordenadas da grade 24x24. */
    record Glyph(List<Shape> duo, List<Shape> stroke, List<Shape> fill) {}

    private static final Map<String, Glyph> GLYPHS = load();

    private IconRegistry() {}

    static Glyph glyph(IconName name) {
        Glyph g = GLYPHS.get(name.id());
        if (g == null) throw new IllegalStateException("Ícone sem desenho: " + name.id());
        return g;
    }

    static boolean has(IconName name) {
        return GLYPHS.containsKey(name.id());
    }

    private static Map<String, Glyph> load() {
        Map<String, Glyph> map = new HashMap<>();
        try (InputStream in = IconRegistry.class.getResourceAsStream("/com/nekkora/ui/icons/nekkora-icons.txt")) {
            if (in == null) throw new IllegalStateException("nekkora-icons.txt não encontrado no classpath");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String name = null;
            List<Shape> duo = null, stroke = null, fill = null;
            for (String line; (line = r.readLine()) != null; ) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("@")) {
                    if (name != null) map.put(name, new Glyph(List.copyOf(duo), List.copyOf(stroke), List.copyOf(fill)));
                    name = line.substring(1);
                    duo = new ArrayList<>();
                    stroke = new ArrayList<>();
                    fill = new ArrayList<>();
                    continue;
                }
                if (name == null) continue;
                Shape s = SvgPath.parse(line.substring(2));
                switch (line.charAt(0)) {
                    case 'D' -> duo.add(s);
                    case 'F' -> fill.add(s);
                    default -> stroke.add(s);
                }
            }
            if (name != null) map.put(name, new Glyph(List.copyOf(duo), List.copyOf(stroke), List.copyOf(fill)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return map;
    }
}
