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
 * NEKKORA-CODEGEN-QUALITY-001 — registro de rastreio de uma automação ({@code nekkora-studio/trace-registry}): o traceId
 * legível usado como {@code step_id} no código gerado é atribuído na 1ª geração de cada etapa e FICA — mudar o título do
 * PDD, a ordem das etapas ou o nome de uma Page não o muda. Etapa removida: o traceId é aposentado e nunca vai para outra
 * etapa (eventos e auditoria antigos continuam inequívocos). Só identificadores; nenhum valor.
 *
 * <p>1.1.0 (rodada 3, código em inglês): {@code convention} diz em que convenção os traceIds ativos estão. Um registro da
 * convenção anterior ({@code pt}, todo registro 1.0.0) NÃO fixa os ids da geração em inglês: eles viram os ids ANTERIORES
 * de cada etapa ({@code previous}), declarados no manifesto ({@code previousTraceIds}) e nunca reaproveitados. Nada é
 * migrado em silêncio: o código antigo continua com os ids dele até ser gerado de novo.
 */
public record TraceRegistry(AutomationId automationId, Map<String, String> active, Set<String> retired, Map<String, List<String>> previous,
                            String convention) {

    public static final String SCHEMA = "nekkora-studio/trace-registry";
    public static final String VERSION = "1.1.0";
    /** Convenção atual dos traceIds (inglês); {@code pt} = a de antes da rodada 3. */
    public static final String CONVENTION = "en";
    public static final String LEGACY_CONVENTION = "pt";

    public TraceRegistry {
        Objects.requireNonNull(automationId);
        active = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(active));
        retired = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(retired));
        Map<String, List<String>> p = new LinkedHashMap<>();
        if (previous != null) previous.forEach((k, v) -> p.put(k, List.copyOf(v)));
        previous = java.util.Collections.unmodifiableMap(p);
        convention = convention == null ? CONVENTION : convention;
    }

    /** Registro na convenção atual, sem ids anteriores. */
    public TraceRegistry(AutomationId automationId, Map<String, String> active, Set<String> retired) {
        this(automationId, active, retired, Map.of(), CONVENTION);
    }

    public static TraceRegistry empty(AutomationId id) {
        return new TraceRegistry(id, Map.of(), Set.of());
    }

    /**
     * Os nomes do plano com os traceIds já atribuídos (e os aposentados) desta automação. Na convenção anterior, os ids
     * ativos não são fixados: vão como ids anteriores de cada etapa (o gerador deriva os novos e os declara).
     */
    public CodeNames applyTo(CodeNames names) {
        if (CONVENTION.equals(convention)) return new CodeNames(names.pages(), names.methods(), active, retired, previous);
        Map<String, List<String>> prev = new LinkedHashMap<>();
        previous.forEach((k, v) -> prev.put(k, new ArrayList<>(v)));
        active.forEach((step, trace) -> {
            List<String> l = prev.computeIfAbsent(step, k -> new ArrayList<>());
            if (!l.contains(trace)) l.add(trace);
        });
        return new CodeNames(names.pages(), names.methods(), Map.of(), retired, prev);
    }

    /**
     * Depois de gerar: as etapas do manifesto ficam com o traceId que o código usa; as que estavam no registro e sumiram
     * do modelo são aposentadas; um id ativo da convenção anterior que mudou vira id ANTERIOR da etapa. Recusa (sem mudar
     * nada) um manifesto que dê o mesmo traceId a duas etapas.
     */
    public TraceRegistry updatedWith(Map<String, String> traceByStep, Collection<String> stepsInModel) {
        if (new LinkedHashSet<>(traceByStep.values()).size() != traceByStep.size()) throw new IllegalArgumentException("TRACE_ALIAS_CONFLITO");
        boolean legacy = !CONVENTION.equals(convention);
        Map<String, String> next = new LinkedHashMap<>();
        Set<String> gone = new LinkedHashSet<>(retired);
        Map<String, List<String>> prev = new LinkedHashMap<>();
        previous.forEach((k, v) -> {
            if (stepsInModel.contains(k)) prev.put(k, new ArrayList<>(v));
            else gone.addAll(v);
        });
        active.forEach((step, trace) -> {
            if (!stepsInModel.contains(step)) gone.add(trace);
        });
        traceByStep.forEach((step, trace) -> {
            if (!gone.contains(trace)) next.put(step, trace);
        });
        active.forEach((step, trace) -> {
            if (!stepsInModel.contains(step)) return;
            if (legacy) {
                // id da convenção anterior: fica como ANTERIOR da etapa (declarado e reservado), nunca como ativo
                if (!trace.equals(next.get(step))) {
                    List<String> l = prev.computeIfAbsent(step, k -> new ArrayList<>());
                    if (!l.contains(trace)) l.add(trace);
                }
            } else if (!next.containsKey(step) && !next.containsValue(trace)) {
                // etapa ainda no modelo mas fora do código desta revisão (não gerada): mantém a reserva
                next.put(step, trace);
            }
        });
        return new TraceRegistry(automationId, next, gone, prev, CONVENTION);
    }

    /** traceIds do {@code trace} de um manifesto 1.3.0+ desta automação (semente quando o registro do Studio sumiu). */
    public static Map<String, String> fromManifest(Path manifest, AutomationId id) {
        return seed(manifest, id, Set.of()).active();
    }

    /**
     * Semente pelo manifesto da pasta de saída: os traceIds, os anteriores ({@code previousTraceIds}) e a convenção (1.4.0
     * com {@code language: "en"} = atual; antes = a anterior).
     */
    public static TraceRegistry seed(Path manifest, AutomationId id, Set<String> retired) {
        Map<String, String> out = new LinkedHashMap<>();
        Map<String, List<String>> prev = new LinkedHashMap<>();
        String convention = LEGACY_CONVENTION;
        try {
            if (!Files.isRegularFile(manifest)) return new TraceRegistry(id, Map.of(), retired);
            Map<String, Object> m = Json.parseObject(Files.readString(manifest, StandardCharsets.UTF_8));
            if (!id.value().toString().equals(m.get("automationId")) || !(m.get("trace") instanceof Map<?, ?> t)) return new TraceRegistry(id, Map.of(), retired);
            if ("en".equals(m.get("language"))) convention = CONVENTION;
            if (!(t.get("mappings") instanceof List<?> list)) return new TraceRegistry(id, Map.of(), retired);
            for (Object o : list) {
                if (o instanceof Map<?, ?> x && x.get("studioStepId") instanceof String sid && x.get("traceId") instanceof String tid
                        && SingleConfig.TRACE.matcher(tid).matches() && !out.containsValue(tid)) {
                    out.put(sid, tid);
                    if (x.get("previousTraceIds") instanceof List<?> p) {
                        List<String> ids = new ArrayList<>();
                        for (Object y : p) if (y instanceof String s && SingleConfig.TRACE.matcher(s).matches()) ids.add(s);
                        if (!ids.isEmpty()) prev.put(sid, ids);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            return new TraceRegistry(id, Map.of(), retired); // manifesto ilegível: nada é presumido
        }
        return new TraceRegistry(id, out, retired, prev, convention);
    }

    public String toJson() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("schema", SCHEMA);
        doc.put("version", VERSION);
        doc.put("automationId", automationId.value().toString());
        doc.put("convention", convention);
        List<Object> entries = new ArrayList<>();
        active.forEach((step, trace) -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("studioStepId", step);
            e.put("traceId", trace);
            if (previous.containsKey(step)) e.put("previous", previous.get(step));
            entries.add(e);
        });
        // etapa com ids anteriores e sem id ativo (ainda não gerada nesta convenção)
        previous.forEach((step, ids) -> {
            if (active.containsKey(step)) return;
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("studioStepId", step);
            e.put("traceId", null);
            e.put("previous", ids);
            entries.add(e);
        });
        doc.put("active", entries);
        doc.put("retired", new ArrayList<>(retired));
        return Json.write(doc);
    }

    @SuppressWarnings("unchecked")
    public static TraceRegistry parse(String json) {
        Map<String, Object> m = Json.parseObject(json);
        Object version = m.get("version");
        if (!SCHEMA.equals(m.get("schema")) || !("1.0.0".equals(version) || VERSION.equals(version))) throw new IllegalArgumentException("TRACE_REGISTRY_VERSION");
        String convention = "1.0.0".equals(version) ? LEGACY_CONVENTION : (String) m.get("convention");
        if (!CONVENTION.equals(convention) && !LEGACY_CONVENTION.equals(convention)) throw new IllegalArgumentException("TRACE_REGISTRY_CONVENTION");
        AutomationId id = new AutomationId(UUID.fromString((String) m.get("automationId")));
        Map<String, String> active = new LinkedHashMap<>();
        Map<String, List<String>> previous = new LinkedHashMap<>();
        for (Object o : (List<Object>) m.get("active")) {
            Map<String, Object> e = (Map<String, Object>) o;
            String step = (String) e.get("studioStepId");
            UUID.fromString(step);
            Object t = e.get("traceId");
            if (t instanceof String trace) {
                if (!SingleConfig.TRACE.matcher(trace).matches() || active.containsValue(trace)) throw new IllegalArgumentException("TRACE_REGISTRY_ENTRY");
                active.put(step, trace);
            } else if (t != null && t != Json.NULL) {
                throw new IllegalArgumentException("TRACE_REGISTRY_ENTRY");
            }
            if (e.get("previous") instanceof List<?> p) {
                List<String> ids = new ArrayList<>();
                for (Object y : p) {
                    if (!(y instanceof String s) || !SingleConfig.TRACE.matcher(s).matches()) throw new IllegalArgumentException("TRACE_REGISTRY_ENTRY");
                    ids.add(s);
                }
                previous.put(step, ids);
            }
        }
        Set<String> retired = new LinkedHashSet<>();
        for (Object o : (List<Object>) m.get("retired")) retired.add((String) o);
        return new TraceRegistry(id, active, retired, previous, convention);
    }
}
