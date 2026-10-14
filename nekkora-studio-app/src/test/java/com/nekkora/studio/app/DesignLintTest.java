package com.nekkora.studio.app;

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

/** Regra do design system (DesignLintTest do Showcase) aplicada às telas da aplicação (CA-015). */
class DesignLintTest {
    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("\\.setBackground\\("),
            Pattern.compile("\\.setForeground\\("),
            Pattern.compile("\\.setFont\\((?!NekkoraFonts\\.font\\()"),
            Pattern.compile("\\.setBorder\\("),
            Pattern.compile("BorderFactory\\."),
            Pattern.compile("new Color\\("),
            Pattern.compile("new Font\\("),
            Pattern.compile("Color\\.(RED|GREEN|BLUE|WHITE|BLACK|GRAY|PINK|ORANGE|YELLOW)"),
            Pattern.compile("#[0-9a-fA-F]{6}\\b"),
            Pattern.compile("Box\\.create(Horizontal|Vertical)Strut\\("),
            Pattern.compile("setBounds\\("),
            Pattern.compile("setLayout\\(null\\)"),
            Pattern.compile("UIManager\\.put\\("));

    @Test
    @DisplayName("CA-015 telas da aplicação não usam APIs visuais diretas")
    void telasNaoDecidemCorFonteBordaOuEspaco() throws IOException {
        List<String> violations = new ArrayList<>();
        Path src = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(src)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(f);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.strip().startsWith("//") || line.strip().startsWith("*")) continue;
                    for (Pattern p : FORBIDDEN) {
                        if (p.matcher(line).find()) violations.add(src.relativize(f) + ":" + (i + 1) + "  " + line.strip());
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "APIs visuais diretas na aplicação:\n" + String.join("\n", violations));
    }
}
