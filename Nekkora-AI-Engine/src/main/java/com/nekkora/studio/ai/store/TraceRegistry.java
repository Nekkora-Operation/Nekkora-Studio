package com.nekkora.studio.ai.store;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.codegen.CodeNames;
import com.nekkora.studio.codegen.SingleConfig;
import com.nekkora.studio.domain.AutomationId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * NEKKORA-CODEGEN-QUALITY-001 — registro de rastreio de uma automação ({@code nekkora-studio/trace-registry/1.0.0}): o
 * traceId legível usado como {@code step_id} no código gerado é atribuído na 1ª geração de cada etapa e FICA — mudar o
 * título do PDD, a ordem das etapas ou o nome de uma Page não o muda. Etapa removida: o traceId é aposentado e nunca vai
 * para outra etapa (eventos e auditoria antigos continuam inequívocos). Só identificadores; nenhum valor.
 */
public record TraceRegistry(AutomationId automationId, Map<String, String> active, Set<String> retired) {

    public static final String SCHEMA = "nekkora-studio/trace-registry";
    public static final String VERSION = "1.0.0";

    public TraceRegistry {
        Objects.requireNonNull(automationId);
        active = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(active));
        retired = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(retired));
    }

    public static TraceRegistry empty(AutomationId id) {
        return new TraceRegistry(id, Map.of(), Set.of());
    }

    /** Os nomes do plano com os traceIds já atribuídos (e os aposentados) desta automação. */
    public CodeNames applyTo(CodeNames names) {
        return new CodeNames(names.pages(), names.methods(), active, retired);
    }

    /**
     * Depois de gerar: as etapas do manifesto ficam com o traceId que o código usa; as que estavam no registro e sumiram
     * do modelo são aposentadas. Recusa (sem mudar nada) um manifesto que dê o mesmo traceId a duas etapas.
     */
    public TraceRegistry updatedWith(Map<String, String> traceByStep, Collection<String> stepsInModel) {
        if (new LinkedHashSet<>(traceByStep.values()).size() != traceByStep.size()) throw new IllegalArgumentException("TRACE_ALIAS_CONFLITO");
        Map<String, String> next = new LinkedHashMap<>();
        Set<String> gone = new LinkedHashSet<>(retired);
        active.forEach((step, trace) -> {
            if (!stepsInModel.contains(step)) gone.add(trace);
        });
        traceByStep.forEach((step, trace) -> {
            if (!gone.contains(trace)) next.put(step, trace);
        });
        // etapa ainda no modelo mas fora do código desta revisão (não gerada): mantém a reserva
        active.forEach((step, trace) -> {
            if (stepsInModel.contains(step) && !next.containsKey(step) && !next.containsValue(trace)) next.put(step, trace);
        });
        return new TraceRegistry(automationId, next, gone);
    }

    /** traceIds do {@code trace} de um manifesto 1.3.0 desta automação (semente quando o registro do Studio sumiu). */
    @SuppressWarnings("unchecked")
    public static Map<String, String> fromManifest(Path manifest, AutomationId id) {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            if (!Files.isRegularFile(manifest)) return out;
            Map<String, Object> m = Json.parseObject(Files.readString(manifest, StandardCharsets.UTF_8));
            if (!id.value().toString().equals(m.get("automationId")) || !(m.get("trace") instanceof Map<?, ?> t)) return out;
            if (!(t.get("mappings") instanceof List<?> list)) return out;
            for (Object o : list) {
                if (o instanceof Map<?, ?> x && x.get("studioStepId") instanceof String sid && x.get("traceId") instanceof String tid
                        && SingleConfig.TRACE.matcher(tid).matches() && !out.containsValue(tid)) {
                    out.put(sid, tid);
                }
            }
        } catch (IOException | RuntimeException e) {
            return Map.of(); // manifesto ilegível: nada é presumido
        }
        return out;
    }

    public String toJson() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("schema", SCHEMA);
        doc.put("version", VERSION);
        doc.put("automationId", automationId.value().toString());
        List<Object> entries = new ArrayList<>();
        active.forEach((step, trace) -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("studioStepId", step);
            e.put("traceId", trace);
            entries.add(e);
        });
        doc.put("active", entries);
        doc.put("retired", new ArrayList<>(retired));
        return Json.write(doc);
    }

    @SuppressWarnings("unchecked")
    public static TraceRegistry parse(String json) {
        Map<String, Object> m = Json.parseObject(json);
        if (!SCHEMA.equals(m.get("schema")) || !VERSION.equals(m.get("version"))) throw new IllegalArgumentException("TRACE_REGISTRY_VERSION");
        AutomationId id = new AutomationId(UUID.fromString((String) m.get("automationId")));
        Map<String, String> active = new LinkedHashMap<>();
        for (Object o : (List<Object>) m.get("active")) {
            Map<String, Object> e = (Map<String, Object>) o;
            String step = (String) e.get("studioStepId");
            String trace = (String) e.get("traceId");
            UUID.fromString(step);
            if (!SingleConfig.TRACE.matcher(trace).matches() || active.containsValue(trace)) throw new IllegalArgumentException("TRACE_REGISTRY_ENTRY");
            active.put(step, trace);
        }
        Set<String> retired = new LinkedHashSet<>();
        for (Object o : (List<Object>) m.get("retired")) retired.add((String) o);
        return new TraceRegistry(id, active, retired);
    }
}
