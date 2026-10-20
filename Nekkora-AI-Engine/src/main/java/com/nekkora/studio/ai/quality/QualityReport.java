package com.nekkora.studio.ai.quality;

import com.nekkora.studio.ai.pycheck.PythonChecks;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.StepId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * STUDIO-014 — relatório da garantia de qualidade ({@code nekkora-studio/quality-report/1.0.0}): o estado de CADA
 * camada, o que a fez ter esse estado, os apontamentos de V1–V6 e as verificações Python realmente executadas. Amarrado
 * às revisões exatas (modelo, processo, PDD, especificação, manifesto do código): reaberto noutra revisão, é mostrado como
 * desatualizado. Os apontamentos estruturais (V0) continuam no relatório da validação estrutural (STUDIO-007/008).
 *
 * <p>Vocabulário fechado: nunca "aprovado", "garantido", "pronto para produção" ou "testado em execução".
 */
public record QualityReport(String automationId, long automationRevision, String processRevision, String documentRevision, String specRevision,
                            String codeManifest, Instant at, Map<Layer, LayerResult> layers, List<QualityIssue> issues, List<PythonChecks.Check> checks,
                            String prompt, String catalog) {

    public static final String SCHEMA = "nekkora-studio/quality-report";
    public static final String VERSION = "1.0.0";

    /** Estado de uma camada. {@code PASS} só quando a verificação RODOU e não achou nada que impeça. */
    public enum State {
        PASS("✓"), ATTENTION("!"), BLOCKED("×"), HELP_REQUIRED("?"), SECURITY_BLOCKED("⛔"), NOT_CHECKED("—"), RUNNING("…");

        private final String symbol;

        State(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }
    }

    public record LayerResult(Layer layer, State state, String summary) {
        public LayerResult {
            Objects.requireNonNull(layer);
            Objects.requireNonNull(state);
            summary = summary == null ? "" : summary;
        }

        /** "✓ Modelo: sem bloqueios estáticos" — texto da linha do painel. */
        public String line() {
            return state.symbol() + " " + layer.code() + " " + layer.label() + ": " + summary;
        }
    }

    public QualityReport {
        layers = java.util.Collections.unmodifiableMap(new EnumMap<>(layers));
        issues = List.copyOf(issues);
        checks = List.copyOf(checks);
    }

    public LayerResult layer(Layer l) {
        return layers.get(l);
    }

    /** O relatório descreve exatamente estas revisões? */
    public boolean currentFor(long automationRevision, String processRevision, String documentRevision, String specRevision, String codeManifest) {
        return this.automationRevision == automationRevision && this.processRevision.equals(processRevision) && this.documentRevision.equals(documentRevision)
                && this.specRevision.equals(specRevision) && this.codeManifest.equals(codeManifest);
    }

    public String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schema", SCHEMA);
        m.put("version", VERSION);
        m.put("automationId", automationId);
        m.put("automationRevision", automationRevision);
        m.put("processRevision", processRevision);
        m.put("documentRevision", documentRevision);
        m.put("specRevision", specRevision);
        m.put("codeManifest", codeManifest);
        m.put("at", at.toString());
        m.put("prompt", prompt);
        m.put("catalog", catalog);
        List<Object> ls = new ArrayList<>();
        for (LayerResult r : layers.values()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("layer", r.layer().name());
            x.put("state", r.state().name());
            x.put("summary", r.summary());
            ls.add(x);
        }
        m.put("layers", ls);
        List<Object> is = new ArrayList<>();
        for (QualityIssue i : issues) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("code", i.code());
            x.put("layer", i.layer().name());
            x.put("kind", i.kind().name());
            x.put("stepId", i.step() == null ? Json.NULL : i.step().value().toString());
            x.put("blockId", i.blockId());
            x.put("title", i.title());
            x.put("detail", i.detail());
            x.put("guidance", i.guidance());
            x.put("capability", i.capability());
            is.add(x);
        }
        m.put("issues", is);
        List<Object> cs = new ArrayList<>();
        for (PythonChecks.Check c : checks) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", c.id());
            x.put("layer", c.layer().name());
            x.put("status", c.status().name());
            x.put("problems", c.problems());
            x.put("codes", new LinkedHashMap<>(c.codes()));
            x.put("millis", c.millis());
            x.put("reason", c.reason());
            cs.add(x);
        }
        m.put("checks", cs);
        return Json.write(m);
    }

    /** Leitura do relatório persistido (as ajudas e correções voltam só como texto: a próxima validação as recalcula). */
    @SuppressWarnings("unchecked")
    public static QualityReport parse(String json) {
        Map<String, Object> m = Json.parseObject(json);
        if (!SCHEMA.equals(m.get("schema")) || !VERSION.equals(m.get("version"))) throw new IllegalArgumentException("QUALITY_REPORT_VERSION");
        Map<Layer, LayerResult> layers = new EnumMap<>(Layer.class);
        for (Object o : (List<Object>) m.get("layers")) {
            Map<String, Object> x = (Map<String, Object>) o;
            Layer l = Layer.valueOf((String) x.get("layer"));
            layers.put(l, new LayerResult(l, State.valueOf((String) x.get("state")), (String) x.get("summary")));
        }
        List<QualityIssue> issues = new ArrayList<>();
        for (Object o : (List<Object>) m.get("issues")) {
            Map<String, Object> x = (Map<String, Object>) o;
            QualityIssue.Kind k = QualityIssue.Kind.valueOf((String) x.get("kind"));
            // ajuda/correção não são reconstituídas do arquivo: o item volta como diagnóstico até validar de novo
            QualityIssue.Kind shown = k == QualityIssue.Kind.HELP_REQUIRED || k == QualityIssue.Kind.AUTO_FIX_SAFE || k == QualityIssue.Kind.PROPOSE_REVIEW
                    ? QualityIssue.Kind.BLOCKER : k;
            Object step = x.get("stepId");
            issues.add(new QualityIssue((String) x.get("code"), Layer.valueOf((String) x.get("layer")), shown,
                    step == Json.NULL || step == null ? null : new StepId(UUID.fromString((String) step)), (String) x.get("blockId"), (String) x.get("title"),
                    (String) x.get("detail"), (String) x.get("guidance"), null, null, (String) x.get("capability")));
        }
        List<PythonChecks.Check> checks = new ArrayList<>();
        for (Object o : (List<Object>) m.get("checks")) {
            Map<String, Object> x = (Map<String, Object>) o;
            Map<String, Integer> codes = new LinkedHashMap<>();
            ((Map<String, Object>) x.get("codes")).forEach((k, v) -> codes.put(k, ((Number) v).intValue()));
            checks.add(new PythonChecks.Check((String) x.get("id"), Layer.valueOf((String) x.get("layer")), PythonChecks.Status.valueOf((String) x.get("status")),
                    ((Number) x.get("problems")).intValue(), codes, ((Number) x.get("millis")).longValue(), (String) x.get("reason")));
        }
        return new QualityReport((String) m.get("automationId"), ((Number) m.get("automationRevision")).longValue(), (String) m.get("processRevision"),
                (String) m.get("documentRevision"), (String) m.get("specRevision"), (String) m.get("codeManifest"), Instant.parse((String) m.get("at")), layers,
                issues, checks, (String) m.get("prompt"), (String) m.get("catalog"));
    }
}
