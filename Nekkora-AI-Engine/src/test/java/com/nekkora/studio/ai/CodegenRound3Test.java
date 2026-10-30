package com.nekkora.studio.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.ai.plan.ListFlowTest;
import com.nekkora.studio.ai.pycheck.PythonChecks;
import com.nekkora.studio.ai.quality.GeneratedCodeQuality;
import com.nekkora.studio.codegen.OutputWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * NEKKORA-CODEGEN-QUALITY-001 rodada 3 — G-Q12/G-Q13 com Python real: o contrato V5 do pacote automation/ (chaves
 * tipadas, .py ↔ JSON, mapa das chaves, biblioteca padrão não encoberta) com controles negativos, e o projeto ANTERIOR
 * (português, paginas.py na raiz) continuando aprovado em V4/V5 pelo Studio, sem migração.
 */
class CodegenRound3Test {
    @TempDir
    Path tmp;

    private Map<String, PythonChecks.Check> checks(Path py, Path dir) throws Exception {
        PythonChecks.Environment env = PythonChecks.resolve(Optional.of(py), dir).orElseThrow();
        Map<String, PythonChecks.Check> by = new LinkedHashMap<>();
        for (PythonChecks.Check c : PythonChecks.run(env, dir, () -> false)) by.put(c.id(), c);
        return by;
    }

    private Path fresh(String name) throws Exception {
        Path dir = tmp.resolve(name);
        OutputWriter.write(dir, ListFlowTest.develop(FichaCase.confirmed()).result().files());
        return dir;
    }

    @Test
    @DisplayName("[Python real] V4/V5/VQ no pacote automation/: tudo PASS e o contrato roda as 4 verificações novas + a de obrigatoriedade das colunas (12 no total)")
    void pacoteAprovado() throws Exception {
        Path py = PythonForTests.framework();
        Assumptions.assumeTrue(py != null, "sem o interpretador do framework neste ambiente");
        Map<String, PythonChecks.Check> by = checks(py, fresh("novo"));
        for (PythonChecks.Check c : by.values()) assertEquals(PythonChecks.Status.PASS, c.status(), c.id() + " " + c.codes() + " " + c.reason());
        assertEquals(12, by.get("CONTRACT_TESTS").codes().get("TOTAL"), by.get("CONTRACT_TESTS").codes().toString());
    }

    @Test
    @DisplayName("[Python real] controles negativos do contrato: JSON divergente do .py, selectors.py na raiz (encobre a stdlib), chave escrita como texto")
    void contratoPegaDivergencias() throws Exception {
        Path py = PythonForTests.framework();
        Assumptions.assumeTrue(py != null, "sem o interpretador do framework neste ambiente");
        // 1) selectors.json mudou e o .py não: os dois deixam de ser o mesmo registro
        Path a = fresh("json-divergente");
        Path json = a.resolve("selectors.json");
        String text = Files.readString(json, StandardCharsets.UTF_8);
        assertTrue(text.contains("h1#titulo"));
        Files.writeString(json, text.replace("h1#titulo", "h1#outro"), StandardCharsets.UTF_8);
        assertTrue(checks(py, a).get("CONTRACT_TESTS").codes().containsKey("SELECTORS_PY_JSON_DIFFER"));
        // 2) um selectors.py no TOPO do projeto encobre o módulo selectors da biblioteca padrão (subprocess/asyncio dependem dele)
        Path b = fresh("encobre-stdlib");
        Files.writeString(b.resolve("selectors.py"), "\"\"\"Não deveria existir aqui.\"\"\"\n", StandardCharsets.UTF_8);
        assertTrue(checks(py, b).get("CONTRACT_TESTS").codes().containsKey("PROJECT_SHADOWS_STDLIB"));
        // 3) chave de alvo escrita como texto numa tela (mesmo existindo no registro)
        Path c = fresh("chave-literal");
        Path pages = c.resolve("automation").resolve("pages.py");
        String src = Files.readString(pages, StandardCharsets.UTF_8);
        assertTrue(src.contains("SelectorKey.PROFILE_NATIONALITY.value"));
        Files.writeString(pages, src.replace("SelectorKey.PROFILE_NATIONALITY.value", "\"process.profile.nationality\""), StandardCharsets.UTF_8);
        Map<String, PythonChecks.Check> byC = checks(py, c);
        assertTrue(byC.get("CONTRACT_TESTS").codes().containsKey("LITERAL_TARGET_KEY_IN_PAGES"), byC.get("CONTRACT_TESTS").codes().toString());
        assertTrue(byC.get("CODE_QUALITY").codes().keySet().stream().anyMatch(k -> k.startsWith("CHAVE_DE_ALVO_LITERAL ")), byC.get("CODE_QUALITY").codes().toString());
    }

    @Test
    @DisplayName("[Python real] G-Q13: o projeto da rodada 2 (português, paginas.py, manifesto 1.3.0) continua aprovado em V4/V5 e é oferecido para gerar de novo")
    void projetoAnteriorAprovado() throws Exception {
        Path py = PythonForTests.framework();
        Assumptions.assumeTrue(py != null, "sem o interpretador do framework neste ambiente");
        Path src = Path.of("..", "docs", "codegen-quality-001", "human-review", "rodada-2", "final").toAbsolutePath().normalize();
        Path dir = tmp.resolve("rodada-2");
        try (var w = Files.walk(src)) {
            for (Path p : w.toList()) {
                Path to = dir.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(to);
                else Files.copy(p, to);
            }
        }
        Files.deleteIfExists(dir.resolve("SHA256SUMS.txt")); // da pasta de revisão, não do projeto
        Map<String, PythonChecks.Check> by = checks(py, dir);
        for (String id : List.of("PY_COMPILE", "PY_IMPORT", "RUFF_CHECK", "RUFF_FORMAT", "MYPY_STRICT", "CONTRACT_TESTS", "CODE_QUALITY")) {
            assertEquals(PythonChecks.Status.PASS, by.get(id).status(), id + " " + by.get(id).codes() + " " + by.get(id).reason());
        }
        assertEquals(7, by.get("CONTRACT_TESTS").codes().get("TOTAL"), "o layout anterior roda o contrato de antes");
        var issues = GeneratedCodeQuality.assess(dir, List.copyOf(by.values()), true).issues();
        assertTrue(issues.stream().anyMatch(i -> i.code().equals("CODIGO_EM_PORTUGUES") && i.fix() != null), "regeneração oferecida, nunca automática");
        assertTrue(Files.isRegularFile(dir.resolve("paginas.py")) && !Files.exists(dir.resolve("automation")), "nada migrado");
    }
}
