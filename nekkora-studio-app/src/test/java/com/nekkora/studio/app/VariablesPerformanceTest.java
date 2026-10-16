package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.variable.SemanticStatus;
import com.nekkora.studio.domain.variable.SemanticType;
import com.nekkora.studio.domain.variable.Sensitivity;
import com.nekkora.studio.domain.variable.VariableDefinition;
import com.nekkora.studio.domain.variable.VariableDraft;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.variable.VariableType;
import com.nekkora.studio.persistence.ProjectFiles;
import com.nekkora.studio.persistence.ProjectFormat;
import com.nekkora.studio.persistence.ProjectRepository;
import com.nekkora.studio.semantics.SemanticContract;
import com.nekkora.studio.ui.VariablesPanel;
import com.nekkora.studio.validation.AutomationValidator;
import com.nekkora.studio.validation.DataFlowAnalysis;
import com.nekkora.studio.validation.RuleSet;
import com.nekkora.studio.validation.ValidationReport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * STUDIO-008: medição com 10, 100, 1.000 e 5.000 etapas (uma variável para cada 5 etapas, até o limite; 4 de cada 5
 * etapas de preenchimento usando uma variável; entradas, valores fixos e saídas de etapa). Os números vão para
 * {@code target/studio-008-performance.json} e para o relatório; os limites abaixo são folgados (detectam regressão
 * quadrática), não são metas do produto.
 */
class VariablesPerformanceTest {

    @TempDir
    Path dir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);
    private final ProjectFiles files = new ProjectFiles(new ProjectRepository(), "medição");

    static Automation build(int steps, Clock clock) {
        return build(steps, clock, false);
    }

    /** @param shared todas as etapas de preenchimento usam a MESMA variável (pior caso de renomear) */
    static Automation build(int steps, Clock clock, boolean shared) {
        Automation a = Automation.create("Volume " + steps, "Medição de desempenho", clock);
        int sessions = Math.max(1, steps / 25);
        List<LogicalSession> ss = new ArrayList<>();
        for (int s = 0; s < sessions; s++) ss.add(a.addSession("Sessão " + (s + 1), ""));
        int vars = Math.max(2, Math.min(2_000, steps / 5));
        List<AutomationStep> producers = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            LogicalSession s = ss.get(i * sessions / steps); // sessões contíguas: a ordem linear acompanha i
            boolean uses = i % 5 != 0;
            StepDraft.Builder b = StepDraft.builder().name("Etapa " + i).actionType(uses ? ActionType.FILL : ActionType.OBSERVE);
            if (uses) b.parameters(ActionParameters.withValue(new ValueSource.VariableReference("var." + (shared ? 0 : i % vars))));
            AutomationStep st = a.appendStep(s.id(), b.build());
            if (!uses) producers.add(st);
        }
        for (int v = 0; v < vars; v++) {
            VariableDraft d = VariableDraft.of("var." + v, VariableType.STRING).withSensitivity(Sensitivity.INTERNAL);
            d = switch (v % 3) {
                case 0 -> d.withOrigin(VariableOrigin.input()).withSemantic(SemanticType.IDENTIFIER, SemanticStatus.USER_CONFIRMED);
                case 1 -> d.withOrigin(VariableOrigin.constant("valor fixo " + v));
                default -> d.withOrigin(VariableOrigin.stepOutput(producers.get(v % producers.size()).id()));
            };
            a.declareVariable(d);
        }
        return a;
    }

    private static long median(LongSupplier s) {
        long[] v = new long[5];
        for (int i = 0; i < v.length; i++) v[i] = s.getAsLong();
        Arrays.sort(v);
        return v[2];
    }

    private static double ms(long ns) {
        return Math.round(ns / 10_000.0) / 100.0;
    }

    private Map<String, Object> measure(int steps) throws Exception {
        Automation a = build(steps, clock);
        AutomationValidator v = new AutomationValidator(RuleSet.V1_1);
        for (int i = 0; i < 3; i++) {
            v.validate(a);
            SemanticContract.of(a).toJson();
        }
        ValidationReport[] report = new ValidationReport[1];
        long validateNs = median(() -> {
            long t = System.nanoTime();
            report[0] = v.validate(a);
            return System.nanoTime() - t;
        });
        long flowNs = median(() -> {
            long t = System.nanoTime();
            DataFlowAnalysis.of(a);
            return System.nanoTime() - t;
        });
        String[] contract = new String[1];
        long contractNs = median(() -> {
            long t = System.nanoTime();
            contract[0] = SemanticContract.of(a).toJson();
            return System.nanoTime() - t;
        });
        long rowsNs = median(() -> {
            long t = System.nanoTime();
            VariablesPanel.rows(a);
            return System.nanoTime() - t;
        });
        VariableDefinition most = a.variables().get(1);
        int consumers = a.consumersOf(most.id()).size();
        int[] round = {0};
        long renameNs = median(() -> {
            long t = System.nanoTime();
            a.renameVariable(most.id(), "renomeada." + (round[0]++));
            return System.nanoTime() - t;
        });
        Automation all = build(steps, clock, true);
        VariableDefinition everywhere = all.variables().get(0);
        int sharedConsumers = all.consumersOf(everywhere.id()).size();
        long renameSharedNs = median(() -> {
            long t = System.nanoTime();
            all.renameVariable(everywhere.id(), "compartilhada." + (round[0]++));
            return System.nanoTime() - t;
        });
        Automation.Snapshot before = all.snapshot();
        long undoNs = median(() -> {
            long t = System.nanoTime();
            all.restore(before);
            return System.nanoTime() - t;
        });
        String pid = UUID.randomUUID().toString();
        Path file = dir.resolve("volume-" + steps + ProjectFormat.SUFFIX);
        files.save(file, ProjectFiles.Snapshot.of(a, pid), null, true, Instant.now(), clock);
        long saveNs = median(() -> {
            long t = System.nanoTime();
            try {
                files.save(file, ProjectFiles.Snapshot.of(a, pid), files.repository().fingerprintOf(file), false, Instant.now(), clock);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return System.nanoTime() - t;
        });
        int[] loaded = {0};
        long loadNs = median(() -> {
            long t = System.nanoTime();
            try {
                loaded[0] = files.load(file, clock).automation().variables().size();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return System.nanoTime() - t;
        });
        assertEquals(a.variables().size(), loaded[0]);
        assertEquals(steps, report[0].summary().steps());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("etapas", steps);
        m.put("variaveis", a.variables().size());
        m.put("referencias", DataFlowAnalysis.of(a).consumptions().size());
        m.put("apontamentos", report[0].findings().size());
        m.put("validacao11Ms", ms(validateNs));
        m.put("fluxoDeDadosMs", ms(flowNs));
        m.put("contratoSemanticoMs", ms(contractNs));
        m.put("contratoBytes", contract[0].getBytes(StandardCharsets.UTF_8).length);
        m.put("linhasDoPainelMs", ms(rowsNs));
        m.put("renomearConsumidoras", consumers);
        m.put("renomearMs", ms(renameNs));
        m.put("renomearCompartilhadaConsumidoras", sharedConsumers);
        m.put("renomearCompartilhadaMs", ms(renameSharedNs));
        m.put("desfazerRestauracaoMs", ms(undoNs));
        m.put("arquivoBytes", Files.size(file));
        m.put("salvarComReleituraMs", ms(saveNs));
        m.put("abrirCompletoMs", ms(loadNs));
        return m;
    }

    @Test
    @DisplayName("S008 — desempenho com 10, 100, 1.000 e 5.000 etapas: validação 1.1, fluxo de dados, contrato, painel, renomear, salvar e abrir")
    void medir() throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        for (int n : List.of(10, 100, 1_000, 5_000)) results.add(measure(n));
        StringBuilder json = new StringBuilder("{\n  \"generatedBy\": \"VariablesPerformanceTest\",\n  \"java\": \"").append(System.getProperty("java.version"))
                .append("\",\n  \"os\": \"").append(System.getProperty("os.name")).append("\",\n  \"results\": [\n");
        for (int i = 0; i < results.size(); i++) {
            json.append("    {");
            int k = 0;
            for (Map.Entry<String, Object> e : results.get(i).entrySet()) {
                if (k++ > 0) json.append(", ");
                json.append('"').append(e.getKey()).append("\": ").append(e.getValue());
            }
            json.append(i + 1 < results.size() ? "},\n" : "}\n");
        }
        json.append("  ]\n}\n");
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target", "studio-008-performance.json"), json.toString(), StandardCharsets.UTF_8);
        Map<String, Object> big = results.get(3);
        assertTrue((double) big.get("validacao11Ms") < 1_500, "validação 1.1 com 5.000 etapas: " + big.get("validacao11Ms") + " ms");
        assertTrue((double) big.get("contratoSemanticoMs") < 1_500, "contrato com 5.000 etapas: " + big.get("contratoSemanticoMs") + " ms");
        assertTrue((double) big.get("linhasDoPainelMs") < 1_500, "painel com 5.000 etapas: " + big.get("linhasDoPainelMs") + " ms");
        assertTrue((double) big.get("renomearMs") < 1_500, "renomear com 5.000 etapas: " + big.get("renomearMs") + " ms");
        assertTrue((double) big.get("renomearCompartilhadaMs") < 1_500, "renomear usada por 4.000 etapas: " + big.get("renomearCompartilhadaMs") + " ms");
    }
}
