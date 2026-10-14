package com.nekkora.studio.domain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** S001-019 — o domínio é Java puro: não conhece Swing, AWT, FlatLaf nem o design system. */
class DomainLayerTest {
    private static final Pattern FORBIDDEN = Pattern.compile(
            "^\\s*import\\s+(static\\s+)?(javax\\.swing|java\\.awt|com\\.formdev|com\\.nekkora\\.ui|com\\.nekkora\\.tokens|com\\.nekkora\\.studio\\.(ui|app))\\b");

    @Test
    @DisplayName("S001-019 código do domínio não importa Swing/AWT/FlatLaf/design system/UI/app")
    void dominioSemSwing() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(f);
                for (int i = 0; i < lines.size(); i++) {
                    if (FORBIDDEN.matcher(lines.get(i)).find()) violations.add(f + ":" + (i + 1) + "  " + lines.get(i).strip());
                    if (lines.get(i).contains("javax.swing.") || lines.get(i).contains("java.awt.")) {
                        violations.add(f + ":" + (i + 1) + "  " + lines.get(i).strip());
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "Dependências proibidas no domínio:\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("S001-019 o pom do domínio só depende de JUnit (escopo de teste)")
    void pomSemDependenciasDeUi() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        String deps = pom.substring(pom.indexOf("<dependencies>"), pom.indexOf("</dependencies>"));
        assertTrue(deps.contains("junit-jupiter"));
        for (String forbidden : List.of("flatlaf", "nekkora-swing-ui", "nekkora-design-tokens", "nekkora-studio-ui", "nekkora-studio-app")) {
            assertTrue(!deps.contains(forbidden), "domínio não pode depender de " + forbidden);
        }
        assertTrue(deps.split("<dependency>").length - 1 == 1, "apenas a dependência de testes é permitida");
    }
}
