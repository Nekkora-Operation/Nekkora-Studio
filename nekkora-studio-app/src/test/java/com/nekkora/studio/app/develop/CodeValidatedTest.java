package com.nekkora.studio.app.develop;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.ai.pycheck.PythonChecks;
import com.nekkora.studio.ai.quality.Layer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * INTEGRATION-CONSOLIDATION-001: a janela Desenvolver e a camada VQ da validação dão o mesmo veredito. Um achado só de
 * legibilidade da AST (WARNING na validação, ex.: Page1 quando a seção do PDD não tem título) não pode virar "código com
 * problemas na verificação" nem tirar CODE_VALIDATED; um achado objetivo (BLOCKER na validação) continua barrando.
 */
class CodeValidatedTest {

    private static List<PythonChecks.Check> base() {
        List<PythonChecks.Check> l = new ArrayList<>();
        for (String id : List.of("PY_COMPILE", "PY_IMPORT", "RUFF_CHECK", "RUFF_FORMAT", "MYPY_STRICT", "CONTRACT_TESTS")) {
            l.add(new PythonChecks.Check(id, Layer.V4_CODE, PythonChecks.Status.PASS, 0, Map.of(), 1, ""));
        }
        return l;
    }

    private static PythonChecks.Check quality(PythonChecks.Status st, Map<String, Integer> codes, String reason) {
        return new PythonChecks.Check("CODE_QUALITY", Layer.VQ_GENERATION, st, codes.values().stream().mapToInt(Integer::intValue).sum(), codes, 1, reason);
    }

    @Test
    @DisplayName("achado só de legibilidade (NOME_GENERICO em Page1) = aviso: código continua verificado")
    void avisoNaoBarra() {
        List<PythonChecks.Check> c = base();
        c.add(quality(PythonChecks.Status.FAIL, Map.of("NOME_GENERICO automation/pages.py:Page1", 1), ""));
        assertTrue(DevelopmentPresenter.checksPass(c));
    }

    @Test
    @DisplayName("achado objetivo (SELETOR_NO_CODIGO) continua barrando, mesmo junto de avisos")
    void objetivoBarra() {
        List<PythonChecks.Check> c = base();
        c.add(quality(PythonChecks.Status.FAIL, Map.of("NOME_GENERICO automation/pages.py:Page1", 1, "SELETOR_NO_CODIGO automation/pages.py:Page1.open", 1), ""));
        assertFalse(DevelopmentPresenter.checksPass(c));
    }

    @Test
    @DisplayName("análise que não terminou, outra verificação falha ou nenhuma verificação: não validado")
    void conservador() {
        List<PythonChecks.Check> c = base();
        c.add(quality(PythonChecks.Status.ERROR, Map.of(), "TIMEOUT"));
        assertFalse(DevelopmentPresenter.checksPass(c));
        List<PythonChecks.Check> d = base();
        d.set(2, new PythonChecks.Check("RUFF_CHECK", Layer.V4_CODE, PythonChecks.Status.FAIL, 1, Map.of("E501", 1), 1, ""));
        assertFalse(DevelopmentPresenter.checksPass(d));
        assertFalse(DevelopmentPresenter.checksPass(List.of()));
    }
}
