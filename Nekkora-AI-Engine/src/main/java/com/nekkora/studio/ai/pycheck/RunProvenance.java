package com.nekkora.studio.ai.pycheck;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.codegen.SingleConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NEKKORA-E2E-002 — PROVENIÊNCIA de um teste autorizado, montada pelo Studio DEPOIS da execução e SEPARADA da planilha:
 * para cada transação, a linha (pela chave — ex.: o endereço do artigo) → cada coluna → a etapa ({@code StepId}) que
 * realmente forneceu o dado (a principal ou a fonte alternativa declarada) → a chave do alvo e o localizador do
 * registro (região/elemento). Fontes: os eventos do framework (quais leituras aconteceram em cada transação, sem
 * valores), o registro de alvos do código gerado e o estado da planilha (a linha preparada de cada transação). Nenhum
 * valor além da chave da linha.
 */
public final class RunProvenance {
    private RunProvenance() {}

    public static final String SCHEMA = "nekkora-studio/trial-provenance";

    @SuppressWarnings("unchecked")
    public static String build(Path project, Path runDir, Path spreadsheet, SingleConfig cfg) throws IOException {
        Map<String, Object> selectors = Json.parseObject(Files.readString(project.resolve("selectors.json"), StandardCharsets.UTF_8));
        Map<String, Map<String, Object>> targetByStep = new LinkedHashMap<>();
        Map<String, String> keyByStep = new LinkedHashMap<>();
        for (Map.Entry<String, Object> t : ((Map<String, Object>) selectors.get("targets")).entrySet()) {
            Map<String, Object> target = (Map<String, Object>) t.getValue();
            if (target.get("provenance") instanceof Map<?, ?> p && p.get("studioStepId") instanceof String sid) {
                targetByStep.put(sid, target);
                keyByStep.put(sid, t.getKey());
            }
        }
        // leituras que ACONTECERAM em cada transação (ACTION_COMPLETED de read_items/current_address, pelo step_id)
        Map<String, Set<String>> readsByTx = new LinkedHashMap<>();
        Path events = runDir.resolve("execucao").resolve("events.jsonl");
        if (Files.isRegularFile(events)) {
            for (String line : Files.readAllLines(events, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                Map<String, Object> e = Json.parseObject(line);
                if (!"ACTION_COMPLETED".equals(e.get("event_code")) || !(e.get("transaction_id") instanceof String tx) || !(e.get("step_id") instanceof String sid)) continue;
                if ("read_items".equals(e.get("action")) || "current_address".equals(e.get("action"))) readsByTx.computeIfAbsent(tx, k -> new LinkedHashSet<>()).add(sid);
            }
        }
        // linhas preparadas pela planilha (estado do XlsxResultSink): transação → valores na ordem das colunas
        String stem = spreadsheet.getFileName().toString().replaceFirst("(?i)\\.xlsx$", "");
        Path state = runDir.resolve("execucao").resolve(stem + ".xlsx-output.json");
        Map<String, Object> rows = Files.isRegularFile(state)
                ? (Map<String, Object>) Json.parseObject(Files.readString(state, StandardCharsets.UTF_8)).get("rows") : Map.of();
        int keyIndex = 0;
        for (int i = 0; i < cfg.output().columns().size(); i++) if (cfg.output().columns().get(i).field().equals(cfg.output().keyField())) keyIndex = i;
        List<Object> txs = new ArrayList<>();
        for (Map.Entry<String, Object> r : rows.entrySet()) {
            List<Object> values = (List<Object>) r.getValue();
            Set<String> read = readsByTx.getOrDefault(r.getKey(), Set.of());
            List<Object> fields = new ArrayList<>();
            for (SingleConfig.Column c : cfg.output().columns()) {
                boolean primary = read.contains(c.step());
                String used = primary ? c.step() : c.fallbackStep() != null && read.contains(c.fallbackStep()) ? c.fallbackStep() : null;
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("column", c.header());
                f.put("stepId", used);
                f.put("source", used == null ? "NOT_READ" : primary ? "PRINCIPAL" : "ALTERNATIVA");
                f.put("key", used == null ? null : keyByStep.get(used));
                Object locator = used == null || targetByStep.get(used) == null ? null : targetByStep.get(used).get("preferred");
                f.put("locator", locator);
                fields.add(f);
            }
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("transactionId", r.getKey());
            t.put("rowKey", values.size() > keyIndex ? values.get(keyIndex) : null);
            t.put("fields", fields);
            txs.add(t);
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("schema", SCHEMA);
        doc.put("version", "1.0.0");
        doc.put("keyColumn", cfg.output().columns().get(keyIndex).header());
        doc.put("transactions", txs);
        doc.put("note", "Sem valores além da chave da linha. Coluna → etapa (StepId) que forneceu o dado nesta transação → alvo (chave e localizador do "
                + "registro). Fonte alternativa só quando a principal não foi lida (alvo ausente na página).");
        return Json.write(doc);
    }
}
