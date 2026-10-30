package com.nekkora.studio.ai.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.ai.FichaCase;
import com.nekkora.studio.ai.PythonForTests;
import com.nekkora.studio.codegen.PythonGenerator;
import com.nekkora.studio.codegen.SingleConfig;
import com.nekkora.studio.codegen.SingleGenerator;
import com.nekkora.studio.validation.AutomationValidator;
import com.nekkora.studio.validation.RuleSet;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * INTEGRATION-CONSOLIDATION-001: texto do usuário (cabeçalho de coluna, rótulo da página, título do PDD) que entra numa
 * docstring do código gerado não pode quebrar o Python — barra invertida ({@code \N}, {@code \x}…), {@code """} e aspas no
 * fim são escapados. Oráculo: {@code ast.parse} do Python real em todos os {@code .py} gerados.
 */
class DocstringEscapeTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("[Python real] cabeçalhos com \\N, \\x e \"\"\" geram Python que compila (sem bloqueio e com o texto preservado)")
    void docstringComTextoDoUsuario() throws Exception {
        Path py = PythonForTests.framework();
        Assumptions.assumeTrue(py != null, "sem o interpretador do framework neste ambiente");
        ListFlowTest.Flow f = ListFlowTest.develop(FichaCase.confirmed());
        String json = f.plan().singleConfig().orElseThrow().toJson();
        String bs = "\\\\"; // uma barra invertida dentro de uma string JSON
        String changed = json.replace("\"header\": \"Nome completo\"", "\"header\": \"Nome" + bs + "Nascido" + bs + "x41\"")
                .replace("\"header\": \"Nacionalidade\"", "\"header\": \"Nacionalidade \\\"\\\"\\\" fim\\\"\"");
        assertTrue(!changed.equals(json) && changed.contains("Nascido"), "mutação aplicada");
        PythonGenerator.Result r = SingleGenerator.generate(f.c().a, new AutomationValidator(RuleSet.V1_1).validate(f.c().a), SingleConfig.parse(changed),
                f.plan().names());
        assertEquals(List.of(), r.blockers(), "cabeçalho legítimo não bloqueia");
        Path out = tmp.resolve("gerado");
        List<String> pyFiles = new ArrayList<>();
        for (Map.Entry<String, String> e : r.files().entrySet()) {
            Path p = out.resolve(e.getKey());
            Files.createDirectories(p.getParent());
            Files.writeString(p, e.getValue(), StandardCharsets.UTF_8);
            if (e.getKey().endsWith(".py")) pyFiles.add(p.toString());
        }
        String script = "import ast, sys\nbad = []\nfor f in sys.argv[1:]:\n    try:\n        ast.parse(open(f, encoding='utf-8').read(), f)\n"
                + "    except SyntaxError as e:\n        bad.append(f'{f}:{e.lineno}: {e.msg}')\nprint('|'.join(bad) or 'OK')\n";
        Path s = tmp.resolve("parse.py");
        Files.writeString(s, script, StandardCharsets.UTF_8);
        List<String> cmd = new ArrayList<>(List.of(py.toString(), "-I", s.toString()));
        cmd.addAll(pyFiles);
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String got = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS));
        assertEquals("OK", got, "todos os .py compilam");
        String process = r.files().get("automation/process.py");
        assertTrue(process.contains("Nome\\\\Nascido\\\\x41"), "o texto é preservado (escapado), não removido");
    }
}
