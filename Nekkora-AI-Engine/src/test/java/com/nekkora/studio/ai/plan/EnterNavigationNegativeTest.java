package com.nekkora.studio.ai.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.ai.FichaCase;
import com.nekkora.studio.ai.PythonForTests;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.codegen.OutputWriter;
import com.nekkora.studio.codegen.SingleConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * NEKKORA-CODEGEN-QUALITY-001 rodada 4 (revisão da rodada 3, A04) — testes NEGATIVOS da espera depois do Enter, com o
 * código GERADO executando de verdade (Python, Chromium, framework instalado): a busca que não troca de endereço, a que
 * leva a outro endereço e a que só chega depois do tempo de espera. Em todos, o item termina com erro classificado e
 * NADA é extraído da página (nenhuma leitura de dados depois da busca) e a planilha não é gravada (tudo ou nada).
 *
 * <p>O laboratório do framework não muda: o teste serve uma CÓPIA temporária da réplica de leitura ({@code /leitura/},
 * dados fictícios) com dois termos de busca adversos acrescentados no {@code busca.js} da cópia — "fica parado" (o Enter
 * não navega) e "demora muito" (navega só depois do tempo de espera da ação). Sem rede externa.
 */
public class EnterNavigationNegativeTest {
    @TempDir
    static Path tmp;

    static Path py;
    static Process server;
    static Path project;
    static List<String> inputs;

    @BeforeAll
    static void setUp() throws Exception {
        py = PythonForTests.framework();
        if (py == null) return;
        Path site = tmp.resolve("site");
        copy(PythonForTests.frameworkRepo().resolve("lab").resolve("static"), site);
        Path js = site.resolve("leitura").resolve("busca.js");
        String original = Files.readString(js, StandardCharsets.UTF_8);
        String go = "    location.href = '/leitura/' + (FICHAS[q] || 'nada.html');";
        assertTrue(original.contains(go), "réplica de leitura mudou: ajuste o teste");
        Files.writeString(js, original.replace(go, "    if (q === 'fica parado') return;\n"
                + "    if (q === 'demora muito') { setTimeout(() => { location.href = '/leitura/ficha-ana.html'; }, 25000); return; }\n" + go),
                StandardCharsets.UTF_8);
        server = new ProcessBuilder(py.toString(), "-m", "http.server", "18766", "--bind", "127.0.0.1", "--directory", site.toString())
                .redirectErrorStream(true).redirectOutput(tmp.resolve("server.log").toFile()).start();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        boolean up = false;
        for (int i = 0; i < 50 && !up; i++) {
            try {
                up = http.send(HttpRequest.newBuilder(URI.create(FichaCase.ORIGIN + "/leitura/busca.html")).build(), HttpResponse.BodyHandlers.discarding())
                        .statusCode() == 200;
            } catch (IOException e) {
                Thread.sleep(200);
            }
        }
        assertTrue(up, "servidor da cópia da réplica não respondeu");
        ListFlowTest.Flow f = ListFlowTest.develop(FichaCase.confirmed());
        project = tmp.resolve("projeto");
        OutputWriter.write(project, f.result().files());
        SingleConfig cfg = f.plan().singleConfig().orElseThrow();
        inputs = cfg.inputs();
        assertEquals(List.of("termo_de_busca", "endereco_esperado"), inputs);
    }

    @AfterAll
    static void tearDown() {
        if (server == null) return;
        server.descendants().forEach(ProcessHandle::destroyForcibly);
        server.destroyForcibly();
    }

    /** Uma execução com UMA linha; devolve o resumo impresso e os eventos JSONL. */
    private record Run(Map<String, Object> summary, List<Map<String, Object>> events, Path spreadsheet) {
        List<Map<String, Object>> failures() {
            return events.stream().filter(e -> "ACTION_FAILED".equals(e.get("event_code")) || "ITEM_FAILED".equals(e.get("event_code"))
                    || "ITEM_REJECTED".equals(e.get("event_code"))).toList();
        }

        boolean extracted() {
            // a extração dos dados da ficha é read_items (o endereço conferido é current_address, que não é extração)
            return events.stream().anyMatch(e -> "ACTION_STARTED".equals(e.get("event_code")) && "read_items".equals(e.get("action")));
        }
    }

    private Run run(String name, String term, String expectedAddress) throws Exception {
        Path list = tmp.resolve(name + ".csv");
        Files.writeString(list, String.join(",", inputs) + "\n" + term + "," + expectedAddress + "\n", StandardCharsets.UTF_8);
        Path xlsx = tmp.resolve(name + ".xlsx");
        Path exec = tmp.resolve("exec-" + name);
        String out = ListFlowTest.runPython(py, project, List.of("main.py", "--input", list.toString(), "--spreadsheet", xlsx.toString(), "--output",
                exec.toString(), "--channel", "chromium", "--headless"));
        String last = out.strip().lines().reduce((a, b) -> b).orElse("");
        Map<String, Object> summary = Json.parseObject(last);
        List<Map<String, Object>> events = new ArrayList<>();
        for (String l : Files.readAllLines(exec.resolve("execucao").resolve("events.jsonl"), StandardCharsets.UTF_8)) {
            if (!l.isBlank()) events.add(Json.parseObject(l));
        }
        return new Run(summary, events, xlsx);
    }

    @Test
    @DisplayName("Enter que NÃO troca de endereço: a espera termina com erro classificado, sem extração e sem planilha")
    void enterSemTrocaDeEndereco() throws Exception {
        Assumptions.assumeTrue(py != null, "sem o interpretador do framework neste ambiente");
        Run r = run("parado", "Fica Parado", FichaCase.ORIGIN + "/leitura/ficha-ana.html");
        assertFalse("COMPLETED".equals(r.summary().get("status")), r.summary().toString());
        Map<String, Object> enter = r.failures().stream().filter(e -> String.valueOf(e.get("step_id")).startsWith("search.")).findFirst()
                .orElseThrow(() -> new AssertionError("a falha não é do Enter da busca: " + r.failures()));
        // a espera do Enter é a pós-condição "o endereço mudou": não cumprida no tempo da ação → ACTION_TIMEOUT/postcondition_not_met
        assertEquals("ACTION_TIMEOUT", enter.get("error_code"), enter.toString());
        assertEquals("postcondition_not_met", enter.get("detail"), enter.toString());
        assertFalse(r.extracted(), "nada é lido da página quando a busca não chegou");
        assertFalse(Files.exists(r.spreadsheet()), "tudo ou nada: sem planilha");
    }

    @Test
    @DisplayName("Enter que leva a OUTRO endereço: conferência recusa (ENDERECO_DIFERENTE_DO_ESPERADO), sem extração e sem planilha")
    void enterParaEnderecoInesperado() throws Exception {
        Assumptions.assumeTrue(py != null, "sem o interpretador do framework neste ambiente");
        Run r = run("outro", "Ana Exemplo", FichaCase.ORIGIN + "/leitura/ficha-gina.html");
        assertEquals("COMPLETED_WITH_FAILURES", r.summary().get("status"), r.summary().toString());
        // recusa de negócio: TRANSACTION_COMPLETED com outcome BUSINESS_REJECTED e o código do processo no detalhe
        assertTrue(r.events().stream().anyMatch(e -> "TRANSACTION_COMPLETED".equals(e.get("event_code")) && "BUSINESS_REJECTED".equals(e.get("outcome"))
                && "BUSINESS_RULE_REJECTED".equals(e.get("error_code")) && "ENDERECO_DIFERENTE_DO_ESPERADO".equals(e.get("detail"))),
                "código de recusa claro: " + r.events().stream().filter(e -> String.valueOf(e.get("event_code")).startsWith("TRANSACTION")).toList());
        assertFalse(r.extracted(), "o endereço é conferido ANTES de qualquer leitura de dados");
        assertFalse(Files.exists(r.spreadsheet()), "tudo ou nada: sem planilha");
    }

    @Test
    @DisplayName("Enter cuja navegação chega DEPOIS do tempo de espera: erro classificado, a chegada tardia não vira sucesso")
    void enterQueChegaDepoisDoTempo() throws Exception {
        Assumptions.assumeTrue(py != null, "sem o interpretador do framework neste ambiente");
        Run r = run("lento", "Demora Muito", FichaCase.ORIGIN + "/leitura/ficha-ana.html");
        assertFalse("COMPLETED".equals(r.summary().get("status")), r.summary().toString());
        Map<String, Object> enter = r.failures().stream().filter(e -> String.valueOf(e.get("step_id")).startsWith("search.")).findFirst()
                .orElseThrow(() -> new AssertionError("a falha não é do Enter da busca: " + r.failures()));
        // a espera do Enter é a pós-condição "o endereço mudou": não cumprida no tempo da ação → ACTION_TIMEOUT/postcondition_not_met
        assertEquals("ACTION_TIMEOUT", enter.get("error_code"), enter.toString());
        assertEquals("postcondition_not_met", enter.get("detail"), enter.toString());
        assertFalse(r.extracted(), "nada é lido da página enquanto a busca não chegou");
        assertFalse(Files.exists(r.spreadsheet()), "tudo ou nada: sem planilha");
    }

    private static void copy(Path from, Path to) throws IOException {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : s.toList()) {
                Path t = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(t);
                else Files.copy(p, t);
            }
        }
    }
}
