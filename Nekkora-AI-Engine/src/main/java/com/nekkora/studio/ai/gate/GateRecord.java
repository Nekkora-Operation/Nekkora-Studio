package com.nekkora.studio.ai.gate;

import com.nekkora.studio.bridge.json.Json;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Registro persistido do gate ({@code nekkora-studio/pdd-gate-record/1.0.0}). Vale SÓ para as revisões que carrega:
 * {@link #currentFor} compara com o estado atual e qualquer diferença (modelo, variável, alvo, decisão do debug, PDD ou
 * especificação) torna o registro desatualizado. Sem textos, sem valores: revisões, contagens e códigos.
 */
public record GateRecord(PddGate.State state, String automationId, long automationRevision, String processRevision, String documentRevision,
                         String specRevision, int coveredSteps, int totalSteps, List<String> reviewers, List<String> blockers, Instant evaluatedAt,
                         String evaluator) {

    public static final String SCHEMA = "nekkora-studio/pdd-gate-record";
    public static final String VERSION = "1.0.0";

    public GateRecord {
        Objects.requireNonNull(state);
        reviewers = List.copyOf(reviewers);
        blockers = List.copyOf(blockers);
    }

    /** O registro descreve exatamente estas revisões? */
    public boolean currentFor(long automationRevision, String processRevision, String documentRevision, String specRevision) {
        return this.automationRevision == automationRevision && this.processRevision.equals(processRevision)
                && this.documentRevision.equals(documentRevision) && this.specRevision.equals(specRevision);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schema", SCHEMA);
        m.put("version", VERSION);
        m.put("state", state.name());
        m.put("automationId", automationId);
        m.put("automationRevision", automationRevision);
        m.put("processRevision", processRevision);
        m.put("documentRevision", documentRevision);
        m.put("specRevision", specRevision);
        m.put("coveredSteps", coveredSteps);
        m.put("totalSteps", totalSteps);
        m.put("reviewers", reviewers);
        m.put("blockers", blockers);
        m.put("evaluatedAt", evaluatedAt.toString());
        m.put("evaluator", evaluator);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static GateRecord fromMap(Map<String, Object> m) {
        if (!SCHEMA.equals(m.get("schema")) || !VERSION.equals(m.get("version"))) throw new IllegalArgumentException("GATE_RECORD_VERSION");
        if (!Set.of("schema", "version", "state", "automationId", "automationRevision", "processRevision", "documentRevision", "specRevision",
                "coveredSteps", "totalSteps", "reviewers", "blockers", "evaluatedAt", "evaluator").equals(m.keySet())) {
            throw new IllegalArgumentException("GATE_RECORD_FIELDS");
        }
        List<String> rev = new ArrayList<>();
        for (Object o : (List<Object>) m.get("reviewers")) rev.add((String) o);
        List<String> bl = new ArrayList<>();
        for (Object o : (List<Object>) m.get("blockers")) bl.add((String) o);
        return new GateRecord(PddGate.State.valueOf((String) m.get("state")), (String) m.get("automationId"), ((Number) m.get("automationRevision")).longValue(),
                (String) m.get("processRevision"), (String) m.get("documentRevision"), (String) m.get("specRevision"),
                ((Number) m.get("coveredSteps")).intValue(), ((Number) m.get("totalSteps")).intValue(), rev, bl, Instant.parse((String) m.get("evaluatedAt")),
                (String) m.get("evaluator"));
    }

    public String toJson() {
        return Json.write(toMap());
    }

    public static GateRecord parse(String json) {
        return fromMap(Json.parseObject(json));
    }
}
