package com.nekkora.studio.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PACKAGING-001: os arquivos gerados não dependem mais de wheel local nem do checkout do framework. */
class InstallHintTest {

    @Test
    @DisplayName("requirements.txt: a ÚLTIMA linha é o requisito exato do contrato (0.2.0 legado preservado); nenhum wheel local nem constraints do checkout")
    void requisitos() {
        for (String req : List.of(PythonGenerator.FRAMEWORK_REQUIREMENT, SingleGenerator.FRAMEWORK_REQUIREMENT, SingleGenerator.FRAMEWORK_REQUIREMENT_LIST)) {
            String txt = InstallHint.requirementsTxt("X", req);
            String[] lines = txt.strip().split("\n");
            assertEquals(req, lines[lines.length - 1]);
            assertFalse(txt.contains(".whl") || txt.contains("constraints/") || txt.contains("git+") || txt.contains("github"), txt);
            assertTrue(txt.contains("Preparar ambiente") && txt.contains("pip install \"" + req + "\""), txt);
            for (String l : lines) assertTrue(l.startsWith("#") || l.equals(req), "só comentário e o requisito: " + l);
        }
        assertEquals("nekkora-rpa[excel]==0.2.0", PythonGenerator.FRAMEWORK_REQUIREMENT, "tabular 1.0.0 (E2E-001) continua 0.2.0");
        assertEquals("nekkora-rpa==0.2.0", SingleGenerator.FRAMEWORK_REQUIREMENT, "execução única 1.1.0 continua 0.2.0");
        assertEquals("nekkora-rpa[excel]==0.3.0", SingleGenerator.FRAMEWORK_REQUIREMENT_LIST, "lista → planilha 1.2.0 continua 0.3.0[excel]");
    }

    @Test
    @DisplayName("README: Preparar ambiente do Studio e pip com a versão exata; sem caminho de checkout")
    void readme() {
        String r = InstallHint.readmeBlock("nekkora-rpa[excel]==0.3.0", "python main.py --console");
        assertTrue(r.contains("Preparar ambiente") && r.contains("pip install \"nekkora-rpa[excel]==0.3.0\"") && r.contains(".venv/Scripts/python main.py"), r);
        assertFalse(r.contains(".whl") || r.contains("constraints/"), r);
    }
}
