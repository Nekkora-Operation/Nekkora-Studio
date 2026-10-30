package com.nekkora.studio.ai.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nekkora.studio.ai.ConsultaCase;
import com.nekkora.studio.ai.FichaCase;
import com.nekkora.studio.codegen.OutputWriter;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * NEKKORA-CODEGEN-QUALITY-001 — amostras do gerador oficial para os casos SINTÉTICOS (fichas → planilha 0.3.0;
 * consulta → execução única 0.2.0), gravadas em {@code target/codegen-samples} (ou em {@code -Dnekkora.codegen.samples})
 * para o diff "antes × depois" e a leitura humana; e o determinismo: o MESMO modelo gera os mesmos bytes.
 */
class CodegenSamplesTest {

    @Test
    @DisplayName("amostras dos casos sintéticos; mesma entrada → mesmos arquivos byte a byte")
    void gravar() throws Exception {
        String dir = System.getProperty("nekkora.codegen.samples");
        Path out = dir == null || dir.isBlank() ? Path.of("target", "codegen-samples") : Path.of(dir);
        ListFlowTest.Flow fichas = ListFlowTest.develop(FichaCase.confirmed());
        Map<String, String> again = com.nekkora.studio.codegen.SingleGenerator.generate(fichas.c().a,
                new com.nekkora.studio.validation.AutomationValidator(com.nekkora.studio.validation.RuleSet.V1_1).validate(fichas.c().a),
                fichas.plan().singleConfig().orElseThrow(), fichas.plan().names()).files();
        assertEquals(fichas.result().files(), again);
        OutputWriter.write(out.resolve("fichas"), fichas.result().files());
        OutputWriter.write(out.resolve("consulta"), SingleFlowTest.develop(ConsultaCase.confirmed()).result().files());
    }
}
