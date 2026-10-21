package com.nekkora.studio.codegen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.validation.AutomationValidator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * CODEGEN-QUALITY-001 rodada 5 (CQ-R5-01): a linha da planilha ({@code InputRow}) traz dados pessoais — o código gerado
 * não pode mostrá-los pelo repr automático. Roda o Python real do framework ({@code NEKKORA_TEST_PYTHON} ou a venv do
 * repositório irmão); sem ele, o teste é PULADO, nunca dado por aprovado.
 */
class TabularReprTest {

    private static final String PROBE = """
            import dataclasses, json, sys
            sys.path.insert(0, sys.argv[1])
            from automation.process import InputRow
            fields = dataclasses.fields(InputRow)
            row = InputRow(**{f.name: 'SENTINEL-CQ-R5-01-' + f.name for f in fields})
            shown = repr(row) + str(row) + f'{row}' + f'{row!r}' + repr([row]) + repr({'row': row})
            print(json.dumps([len(fields), 'SENTINEL-CQ-R5-01' in shown, all(getattr(row, f.name) == 'SENTINEL-CQ-R5-01-' + f.name for f in fields)]))
            """;

    static Path python() {
        String env = System.getenv("NEKKORA_TEST_PYTHON");
        if (env != null && !env.isBlank() && Files.isRegularFile(Path.of(env))) return Path.of(env);
        Path repo = Path.of("..", "..", "Nekkora-RPA-Framework").toAbsolutePath().normalize();
        for (Path p : List.of(repo.resolve(".venv/Scripts/python.exe"), repo.resolve(".venv/bin/python"))) {
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    @Test
    @DisplayName("[Python real] CQ-R5-01: repr/str da InputRow gerada não mostram os valores; os campos continuam com eles")
    void inputRowSemValoresNoRepr(@TempDir Path dir) throws Exception {
        Path py = python();
        Assumptions.assumeTrue(py != null, "sem o interpretador do framework neste ambiente");
        FormCase c = FormCase.confirmed();
        PythonGenerator.Result r = PythonGenerator.generate(c.a, new AutomationValidator().validate(c.a), CodegenConfig.parse(c.config()));
        assertTrue(r.ok(), r.blockers().toString());
        assertTrue(r.files().get(CodePlan.PROCESS_FILE).contains("@dataclass(frozen=True, repr=False)\nclass InputRow:"));
        OutputWriter.write(dir, r.files());
        ProcessBuilder pb = new ProcessBuilder(py.toString(), "-B", "-c", PROBE, dir.toString()).redirectErrorStream(true);
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "tempo esgotado");
        String last = out.substring(out.lastIndexOf('\n') + 1);
        assertTrue(last.matches("\\[[1-9]\\d*, false, true]"), "[campos, vazou, guardou]: " + out);
    }
}
