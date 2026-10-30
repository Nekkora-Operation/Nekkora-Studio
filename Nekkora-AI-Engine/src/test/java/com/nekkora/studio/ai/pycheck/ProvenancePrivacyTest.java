package com.nekkora.studio.ai.pycheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.ai.FichaCase;
import com.nekkora.studio.ai.plan.ListFlowTest;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.codegen.OutputWriter;
import com.nekkora.studio.codegen.SingleConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * CODEGEN-QUALITY-001 rodada 2 (CQ-09) — a proveniência não leva valores, provado sem Python e sem acaso: valores-
 * sentinela EXCLUSIVOS (que não existem em nenhum id, chave, localizador ou cabeçalho) são postos em todo lugar onde um
 * evento ou o estado da planilha poderia trazê-los, e a proveniência é conferida folha a folha pela estrutura
 * ({@link ProvenanceShape}). Controle negativo: um vazamento plantado é pego, e o diagnóstico não repete o valor.
 */
class ProvenancePrivacyTest {
    @TempDir
    Path tmp;

    static final List<String> SENTINELS = List.of("SENTINELA-Ωmega-7731", "Zuleica Sentinela Quatorze", "1899-12-31", "https://sentinela.invalid/x");

    @Test
    @DisplayName("[CQ-09] sentinelas exclusivos nos eventos e no estado da planilha: nenhuma folha da proveniência os carrega")
    void sentinelasNaoVazam() throws Exception {
        ListFlowTest.Flow f = ListFlowTest.develop(FichaCase.confirmed());
        Path project = tmp.resolve("fichas-python");
        OutputWriter.write(project, f.result().files());
        SingleConfig cfg = f.plan().singleConfig().orElseThrow();
        List<String> txs = List.of("tx-0123456789abcdef0123", "tx-fedcba9876543210fedc");
        Path run = tmp.resolve("exec");
        Path exec = Files.createDirectories(run.resolve("execucao"));
        // eventos: cada leitura que aconteceu, com o valor lido "escondido" nos campos que um evento poderia ter
        List<String> lines = new ArrayList<>();
        for (Object o : (List<?>) ((Map<?, ?>) Json.parseObject(f.result().files().get("manifest.json")).get("trace")).get("mappings")) {
            Map<?, ?> m = (Map<?, ?>) o;
            if (!"OBSERVE".equals(m.get("action"))) continue;
            for (String tx : txs) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("event_code", "ACTION_COMPLETED");
                e.put("transaction_id", tx);
                e.put("step_id", m.get("traceId"));
                e.put("action", String.valueOf(m.get("traceId")).contains("endereco") ? "current_address" : "read_items");
                e.put("value", SENTINELS.get(0));
                e.put("detail", SENTINELS.get(1));
                e.put("text", SENTINELS.get(2));
                e.put("url", SENTINELS.get(3));
                lines.add(Json.write(e));
            }
        }
        Files.write(exec.resolve("events.jsonl"), lines, StandardCharsets.UTF_8);
        // estado da planilha: as linhas preparadas TÊM valores (é o que vai para o Excel)
        Map<String, Object> rows = new LinkedHashMap<>();
        for (String tx : txs) rows.put(tx, Map.of("url", SENTINELS.get(3), "nome_completo", SENTINELS.get(1), "nascimento", SENTINELS.get(2)));
        Files.writeString(exec.resolve("fichas.xlsx-output.json"), Json.write(Map.of("committed_transactions", txs, "rows", rows)), StandardCharsets.UTF_8);

        String prov = RunProvenance.build(project, run, tmp.resolve("fichas.xlsx"), cfg);
        List<String> v = ProvenanceShape.violations(prov, project, cfg, Set.copyOf(txs), SENTINELS);
        assertEquals(List.of(), v, "a proveniência só tem estrutura");
        for (String s : SENTINELS) assertFalse(prov.contains(s), "sentinela fora da proveniência inteira (inclusive dos ids)");
        assertEquals(2, ((List<?>) Json.parseObject(prov).get("transactions")).size());
        assertTrue(prov.contains("\"PRINCIPAL\""), "as leituras foram de fato relacionadas às colunas");

        // controle negativo: um valor plantado num localizador e um campo extra são pegos — e o diagnóstico não repete o valor
        String leaked = prov.replaceFirst("\"source\":", "\"value\": \"" + SENTINELS.get(1) + "\", \"source\":");
        List<String> caught = ProvenanceShape.violations(leaked, project, cfg, Set.copyOf(txs), SENTINELS);
        assertFalse(caught.isEmpty(), "o vazamento plantado é detectado");
        assertTrue(caught.stream().anyMatch(x -> x.contains("chaves fora do esquema")), caught.toString());
        assertTrue(caught.stream().noneMatch(x -> SENTINELS.stream().anyMatch(x::contains)), "diagnóstico sanitizado: " + caught);
        String foreign = prov.replaceFirst("\"transactionId\":\\s*\"tx-0123456789abcdef0123\"", "\"transactionId\": \"" + SENTINELS.get(0) + "\"");
        assertTrue(ProvenanceShape.violations(foreign, project, cfg, Set.copyOf(txs), SENTINELS).stream().anyMatch(x -> x.contains("transactionId")),
                "id de transação fora do formato é pego (não é excluído da conferência)");
    }
}
