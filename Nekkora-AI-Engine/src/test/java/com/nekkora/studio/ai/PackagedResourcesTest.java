package com.nekkora.studio.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * STUDIO-LEAN-001: o que vai para o jar a partir de {@code nekkora/ai/python} são só as ferramentas Python versionadas.
 * Caches locais de ferramentas ({@code .ruff_cache}, {@code __pycache__}, {@code .mypy_cache}, {@code .pytest_cache},
 * {@code *.pyc}) existem na máquina de quem desenvolve e NÃO podem ir para o artefato distribuído — o conteúdo do
 * release não pode depender da máquina que o gerou.
 */
class PackagedResourcesTest {
    private static final Pattern CACHE = Pattern.compile("(^|/)(\\.ruff_cache|__pycache__|\\.mypy_cache|\\.pytest_cache)(/|$)|\\.pyc$");

    @Test
    void recursosPythonEmpacotadosNaoLevamCachesLocais() throws IOException, URISyntaxException {
        URL url = getClass().getClassLoader().getResource("nekkora/ai/python");
        assertNotNull(url, "recursos Python do AI Engine");
        Path dir = Path.of(url.toURI());
        List<String> leaked;
        try (Stream<Path> s = Files.walk(dir)) {
            leaked = s.map(p -> dir.relativize(p).toString().replace('\\', '/')).filter(n -> CACHE.matcher(n).find()).sorted().toList();
        }
        assertEquals(List.of(), leaked, "cache local copiado para os recursos do jar");
    }
}
