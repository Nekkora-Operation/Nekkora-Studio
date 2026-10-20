package com.nekkora.studio.ai.pycheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.ai.FichaCase;
import com.nekkora.studio.ai.PythonForTests;
import com.nekkora.studio.ai.plan.ListFlowTest;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.codegen.OutputWriter;
import com.nekkora.studio.codegen.SingleConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * NEKKORA-E2E-002 — TESTE AUTORIZADO pelo componente do Studio (sem a janela): o código gerado é lançado num
 * subprocesso isolado, acompanhado pelos eventos, e a planilha é conferida REABRINDO o arquivo; a proveniência sai
 * separada; código adulterado não roda; cancelar encerra a árvore e nada é gravado.
 */
class SupervisedRunTest {
    @TempDir
    Path tmp;

    record Setup(Path project, SingleConfig cfg, PythonChecks.Environment env) {}

    Setup setup() throws Exception {
        Path py = PythonForTests.framework();
        Assumptions.assumeTrue(py != null, "sem o interpretador do framework neste ambiente");
        ListFlowTest.Flow f = ListFlowTest.develop(FichaCase.confirmed());
        Path out = tmp.resolve("fichas-python");
        OutputWriter.write(out, f.result().files());
        return new Setup(out, f.plan().singleConfig().orElseThrow(), PythonChecks.resolve(Optional.of(py), out).orElseThrow());
    }

    Path lista(SingleConfig cfg, String... rows) throws Exception {
        Path p = tmp.resolve("lista-" + rows.length + ".csv");
        Files.writeString(p, String.join(",", cfg.inputs()) + "\n" + String.join("\n", rows) + "\n", StandardCharsets.UTF_8);
        return p;
    }

    static Process lab() throws Exception {
        Process lab = new ProcessBuilder(PythonForTests.framework().toString(), "-c", "import sys,time; sys.path.insert(0, sys.argv[1]); from lab.server import Lab; "
                + "Lab(port=18766).start(); print('pronto', flush=True); time.sleep(300)", PythonForTests.frameworkRepo().toString()).redirectErrorStream(true).start();
        assertEquals("pronto", new String(lab.getInputStream().readNBytes(6), StandardCharsets.UTF_8));
        return lab;
    }

    @Test
    @DisplayName("[Python real + laboratório] lança, acompanha, confere a planilha reaberta e monta a proveniência (principal × alternativa)")
    void executaEConfere() throws Exception {
        Setup s = setup();
        String o = FichaCase.ORIGIN;
        Path in = lista(s.cfg(), "Ana Exemplo," + o + "/leitura/ficha-ana.html", "Fabio Ficticio," + o + "/leitura/ficha-fabio.html",
                "Gina Inventada," + o + "/leitura/ficha-gina.html");
        Path sheet = tmp.resolve("saida").resolve("fichas.xlsx");
        Files.createDirectories(sheet.getParent());
        Process lab = lab();
        List<SupervisedRun.Progress> seen = new ArrayList<>();
        try {
            SupervisedRun.Outcome r = SupervisedRun.run(s.env(), new SupervisedRun.Request(s.project(), in, sheet, tmp.resolve("exec"), false,
                    Duration.ofMinutes(5)), seen::add, () -> false);
            assertEquals(SupervisedRun.Status.COMPLETED, r.status(), r.toString());
            assertTrue(r.proven(), r.toString());
            assertEquals("COMMITTED", r.outputStatus());
            assertEquals(3, r.verification().rows());
            assertEquals(List.of("URL", "Nome completo", "Nascimento", "Morte", "Nacionalidade", "Ocupação"), r.verification().headers());
            assertEquals(Map.of("SUCCEEDED", 3), r.counts());
            assertTrue(seen.stream().anyMatch(p -> p.succeeded() == 3), seen.toString());
            assertEquals(SupervisedRun.verifyGenerated(s.project()), r.codeRevision());
            String prov = RunProvenance.build(s.project(), tmp.resolve("exec"), sheet, s.cfg());
            Map<String, Object> doc = Json.parseObject(prov);
            List<Object> txs = (List<Object>) doc.get("transactions");
            assertEquals(3, txs.size());
            String all = prov;
            assertTrue(all.contains("\"ALTERNATIVA\"") && all.contains("\"PRINCIPAL\""), prov);
            assertFalse(all.contains("Georgina") || all.contains("1867") || all.contains("ficha-"), "a proveniência não leva valores (nem a chave)");
            // linhas da planilha na ordem da lista: 2 = Ana, 3 = Fábio, 4 = Gina (linha 1 = cabeçalho)
            // Fábio não tem "Nome completo": a coluna veio do título (alternativa); Gina não tem "Ocupação": veio de "Campo(s)"
            assertEquals("ALTERNATIVA", source(txs, 3, "Nome completo"));
            assertEquals("PRINCIPAL", source(txs, 3, "Ocupação"));
            assertEquals("ALTERNATIVA", source(txs, 4, "Ocupação"));
            assertEquals("PRINCIPAL", source(txs, 4, "Nome completo"));
            assertEquals("PRINCIPAL", source(txs, 2, "Nascimento"));
        } finally {
            lab.descendants().forEach(ProcessHandle::destroyForcibly);
            lab.destroyForcibly();
        }
    }

    @SuppressWarnings("unchecked")
    static String source(List<Object> txs, int sheetRow, String column) {
        for (Object t : txs) {
            Map<String, Object> m = (Map<String, Object>) t;
            if (!(m.get("sheetRow") instanceof Number n) || n.intValue() != sheetRow) continue;
            for (Object f : (List<Object>) m.get("fields")) {
                Map<String, Object> x = (Map<String, Object>) f;
                if (column.equals(x.get("column"))) return String.valueOf(x.get("source"));
            }
        }
        return "";
    }

    @Test
    @DisplayName("código adulterado depois da geração não roda (hash do manifesto)")
    void codigoAdulteradoRecusado() throws Exception {
        Setup s = setup();
        Path pages = s.project().resolve("paginas.py");
        Files.writeString(pages, Files.readString(pages) + "\n# editado à mão\n");
        SupervisedRun.Refused e = assertThrows(SupervisedRun.Refused.class, () -> SupervisedRun.verifyGenerated(s.project()));
        assertEquals("CODE_CHANGED_SINCE_GENERATION", e.code());
        assertThrows(SupervisedRun.Refused.class, () -> SupervisedRun.run(s.env(), new SupervisedRun.Request(s.project(), pages, tmp.resolve("x.xlsx"),
                tmp.resolve("exec"), false, Duration.ofMinutes(1)), p -> { }, () -> false));
    }

    @Test
    @DisplayName("[Python real + laboratório] cancelar encerra o processo (e o navegador) e a planilha NÃO é gravada")
    void cancelar() throws Exception {
        Setup s = setup();
        String o = FichaCase.ORIGIN;
        Path in = lista(s.cfg(), "Ana Exemplo," + o + "/leitura/ficha-ana.html", "Fabio Ficticio," + o + "/leitura/ficha-fabio.html");
        Path sheet = tmp.resolve("cancelado.xlsx");
        Process lab = lab();
        AtomicBoolean cancel = new AtomicBoolean();
        try {
            SupervisedRun.Outcome r = SupervisedRun.run(s.env(), new SupervisedRun.Request(s.project(), in, sheet, tmp.resolve("exec-c"), false,
                    Duration.ofMinutes(5)), p -> {
                        if (p.started() >= 1) cancel.set(true);
                    }, cancel::get);
            assertEquals(SupervisedRun.Status.CANCELLED, r.status(), r.toString());
            assertFalse(r.proven());
            assertFalse(Files.exists(sheet), "nada parcial");
        } finally {
            lab.descendants().forEach(ProcessHandle::destroyForcibly);
            lab.destroyForcibly();
        }
    }

    @Test
    @DisplayName("o subprocesso não herda chaves de API nem variáveis de segredo")
    void ambienteMinimo() {
        assertFalse(SupervisedRun.ENV_KEEP.stream().anyMatch(k -> k.contains("KEY") || k.contains("SECRET") || k.contains("TOKEN") || k.contains("ANTHROPIC")));
    }
}
