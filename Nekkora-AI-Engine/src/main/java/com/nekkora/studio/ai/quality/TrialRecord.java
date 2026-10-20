package com.nekkora.studio.ai.quality;

import com.nekkora.studio.bridge.json.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * NEKKORA-E2E-002 — registro do último TESTE AUTORIZADO de uma automação ({@code nekkora-studio/authorized-trial} 1.0.0):
 * a revisão do código que rodou (hash do manifesto), o desfecho do framework, a saída consolidada e a conferência da
 * planilha REABERTA. Só códigos, contagens, cabeçalhos (rótulos de interface) e hashes — nenhuma célula. É a ÚNICA
 * fonte de V6 = executado: o teste precisa ser da revisão atual, ter terminado {@code COMPLETED} com a saída
 * {@code COMMITTED} e a conferência ok.
 */
public record TrialRecord(String automationId, long automationRevision, String codeRevision, String at, String actor, String status, int exitCode,
                          Map<String, Integer> counts, String outputStatus, int outputRows, String outputDetail, boolean verified, String verifyCode,
                          int verifiedRows, List<String> headers, String spreadsheetSha256, long millis, String visibility) {

    public static final String SCHEMA = "nekkora-studio/authorized-trial";
    public static final String VERSION = "1.0.0";

    public TrialRecord {
        Objects.requireNonNull(automationId);
        counts = Map.copyOf(counts);
        headers = List.copyOf(headers);
        outputDetail = outputDetail == null ? "" : outputDetail;
        verifyCode = verifyCode == null ? "" : verifyCode;
        spreadsheetSha256 = spreadsheetSha256 == null ? "" : spreadsheetSha256;
    }

    /** Execução real comprovada: terminou, gravou a saída e a planilha reaberta conferiu. */
    public boolean proven() {
        return "COMPLETED".equals(status) && "COMMITTED".equals(outputStatus) && verified;
    }

    public String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schema", SCHEMA);
        m.put("version", VERSION);
        m.put("automationId", automationId);
        m.put("automationRevision", automationRevision);
        m.put("codeRevision", codeRevision);
        m.put("at", at);
        m.put("actor", actor);
        m.put("status", status);
        m.put("exitCode", exitCode);
        m.put("counts", new LinkedHashMap<String, Object>(counts));
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("status", outputStatus);
        o.put("rows", outputRows);
        o.put("detail", outputDetail);
        m.put("output", o);
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("ok", verified);
        v.put("code", verifyCode);
        v.put("rows", verifiedRows);
        v.put("headers", headers);
        v.put("sha256", spreadsheetSha256);
        m.put("verification", v);
        m.put("millis", millis);
        m.put("browser", visibility);
        return Json.write(m);
    }

    @SuppressWarnings("unchecked")
    public static TrialRecord parse(String json) {
        Map<String, Object> m = Json.parseObject(json);
        if (!SCHEMA.equals(m.get("schema")) || !VERSION.equals(m.get("version"))) throw new IllegalArgumentException("TRIAL_VERSION");
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (m.get("counts") instanceof Map<?, ?> c) c.forEach((k, x) -> counts.put(String.valueOf(k), x instanceof Number n ? n.intValue() : 0));
        Map<String, Object> o = (Map<String, Object>) m.get("output");
        Map<String, Object> v = (Map<String, Object>) m.get("verification");
        List<String> headers = new java.util.ArrayList<>();
        if (v.get("headers") instanceof List<?> h) h.forEach(x -> headers.add(String.valueOf(x)));
        return new TrialRecord(String.valueOf(m.get("automationId")), ((Number) m.get("automationRevision")).longValue(), String.valueOf(m.get("codeRevision")),
                String.valueOf(m.get("at")), String.valueOf(m.get("actor")), String.valueOf(m.get("status")), ((Number) m.get("exitCode")).intValue(), counts,
                String.valueOf(o.get("status")), ((Number) o.get("rows")).intValue(), String.valueOf(o.get("detail")), Boolean.TRUE.equals(v.get("ok")),
                String.valueOf(v.get("code")), ((Number) v.get("rows")).intValue(), headers, String.valueOf(v.get("sha256")), ((Number) m.get("millis")).longValue(),
                String.valueOf(m.get("browser")));
    }
}
