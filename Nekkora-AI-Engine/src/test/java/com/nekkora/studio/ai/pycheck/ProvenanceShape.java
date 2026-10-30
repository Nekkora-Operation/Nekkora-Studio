package com.nekkora.studio.ai.pycheck;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.codegen.SingleConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * CODEGEN-QUALITY-001 rodada 2 (CQ-09) — a proveniência do teste autorizado provada SEM VALORES pela ESTRUTURA: cada
 * folha do JSON pertence a um conjunto permitido (cabeçalho de coluna, StepId do manifesto, chave e localizador do
 * registro, id de transação no formato do framework e gravado no estado da planilha); nenhuma folha contém um valor-
 * sentinela. O diagnóstico diz o CAMINHO e a REGRA quebrada, nunca o conteúdo da folha (pode ser dado de negócio).
 */
final class ProvenanceShape {
    private ProvenanceShape() {}

    static final Pattern TX = Pattern.compile("tx-[0-9a-f]{20}");
    static final List<String> DOC_KEYS = List.of("schema", "version", "keyColumn", "transactions", "note");
    static final List<String> TX_KEYS = List.of("transactionId", "sheetRow", "fields");
    static final List<String> FIELD_KEYS = List.of("column", "stepId", "source", "key", "locator");
    static final Set<String> SOURCES = Set.of("PRINCIPAL", "ALTERNATIVA", "NOT_READ");

    /**
     * @param committed transações gravadas segundo o estado da planilha (o id de cada transação da proveniência tem de
     *                  estar aqui); vazio = só o formato é conferido
     * @param sentinels valores que NÃO podem aparecer em folha nenhuma (o diagnóstico cita o índice, não o valor)
     * @return violações como {@code caminho: regra}; vazio = a proveniência só tem estrutura
     */
    @SuppressWarnings("unchecked")
    static List<String> violations(String prov, Path project, SingleConfig cfg, Set<String> committed, List<String> sentinels) throws IOException {
        List<String> out = new ArrayList<>();
        Map<String, Object> selectors = Json.parseObject(Files.readString(project.resolve("selectors.json"), StandardCharsets.UTF_8));
        Map<String, Object> targets = (Map<String, Object>) selectors.get("targets");
        Set<String> steps = new HashSet<>();
        for (Object o : (List<Object>) Json.parseObject(Files.readString(project.resolve("manifest.json"), StandardCharsets.UTF_8)).get("steps")) {
            steps.add(String.valueOf(((Map<String, Object>) o).get("stepId")));
        }
        List<String> headers = cfg.output().columns().stream().map(SingleConfig.Column::header).toList();
        Map<String, Object> doc = Json.parseObject(prov);
        if (!List.copyOf(doc.keySet()).equals(DOC_KEYS)) out.add("$: chaves fora do esquema");
        if (!RunProvenance.SCHEMA.equals(doc.get("schema"))) out.add("$.schema: esquema inesperado");
        if (!"1.1.0".equals(doc.get("version"))) out.add("$.version: versão inesperada");
        if (!headers.contains(doc.get("keyColumn"))) out.add("$.keyColumn: não é um cabeçalho de coluna");
        sentinels(out, "$.note", doc.get("note"), sentinels);
        List<Object> txs = doc.get("transactions") instanceof List<?> l ? (List<Object>) l : List.of();
        for (int t = 0; t < txs.size(); t++) {
            String tp = "$.transactions[" + t + "]";
            Map<String, Object> tx = (Map<String, Object>) txs.get(t);
            if (!List.copyOf(tx.keySet()).equals(TX_KEYS)) out.add(tp + ": chaves fora do esquema");
            Object id = tx.get("transactionId");
            if (!(id instanceof String s) || !TX.matcher(s).matches()) out.add(tp + ".transactionId: fora do formato do framework");
            else if (!committed.isEmpty() && !committed.contains(s)) out.add(tp + ".transactionId: não está no estado da planilha");
            Object row = value(tx.get("sheetRow"));
            if (row != null && !(row instanceof Number)) out.add(tp + ".sheetRow: não é número de linha");
            List<Object> fields = tx.get("fields") instanceof List<?> l ? (List<Object>) l : List.of();
            if (fields.size() != headers.size()) out.add(tp + ".fields: uma entrada por coluna");
            for (int i = 0; i < fields.size(); i++) {
                String fp = tp + ".fields[" + i + "]";
                Map<String, Object> f = (Map<String, Object>) fields.get(i);
                if (!List.copyOf(f.keySet()).equals(FIELD_KEYS)) out.add(fp + ": chaves fora do esquema");
                if (i < headers.size() && !headers.get(i).equals(f.get("column"))) out.add(fp + ".column: não é o cabeçalho da coluna");
                if (value(f.get("stepId")) != null && !steps.contains(f.get("stepId"))) out.add(fp + ".stepId: não é etapa do manifesto");
                if (!SOURCES.contains(f.get("source"))) out.add(fp + ".source: fora do vocabulário");
                Object key = value(f.get("key"));
                if (key != null && !targets.containsKey(key)) out.add(fp + ".key: não é chave do registro");
                Object locator = value(f.get("locator"));
                if (locator != null) {
                    Object registered = key == null || !(targets.get(key) instanceof Map<?, ?> tm) ? null : tm.get("preferred");
                    if (registered == null || !Json.write(Map.of("x", locator)).equals(Json.write(Map.of("x", registered)))) {
                        out.add(fp + ".locator: não é o localizador do registro");
                    }
                }
                for (String k : FIELD_KEYS) if (!k.equals("locator")) sentinels(out, fp + "." + k, value(f.get(k)), sentinels);
                sentinels(out, fp + ".locator", locator == null ? null : Json.write(Map.of("x", locator)), sentinels);
            }
        }
        return out;
    }

    /** O {@code null} do JSON ({@link Json#NULL}) como {@code null} Java. */
    private static Object value(Object o) {
        return o == Json.NULL ? null : o;
    }

    private static void sentinels(List<String> out, String path, Object leaf, List<String> sentinels) {
        if (leaf == null) return;
        String text = String.valueOf(leaf);
        for (int i = 0; i < sentinels.size(); i++) if (text.contains(sentinels.get(i))) out.add(path + ": contém o valor-sentinela nº " + (i + 1));
    }
}
