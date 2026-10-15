package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.ui.AutomationOverview;
import com.nekkora.studio.ui.LogicalSessionView;
import com.nekkora.studio.ui.form.StudioForms;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * STUDIO-004 (seção 38): desempenho do editor com volumes SINTÉTICOS de teste (10 e 50 sessões; 100, 500 e 1.000
 * etapas), montados só neste teste — nada é criado na aplicação do usuário. Sem meta arbitrária: o teste mede, registra
 * em {@code target/studio-004-performance.json} e só falha em degradação grosseira (segundos) ou se a atualização deixar
 * de ser incremental.
 */
class EditorPerformanceTest {
    private final StudioFixture fx = new StudioFixture();

    @AfterEach
    void tearDown() {
        fx.dispose();
    }

    private record Volume(String label, int sessions, int stepsPerSession) {}

    @Test
    @DisplayName("S004 desempenho: reorganizar, mover, desfazer/refazer, abrir editor, memória e componentes atualizados")
    void medir() throws Exception {
        List<Volume> volumes = List.of(new Volume("10 sessões × 10 etapas (100)", 10, 10), new Volume("50 sessões × 10 etapas (500)", 50, 10),
                new Volume("10 sessões × 100 etapas (1.000)", 10, 100), new Volume("50 sessões × 20 etapas (1.000)", 50, 20));
        List<Map<String, Object>> results = new ArrayList<>();
        for (Volume v : volumes) results.add(measure(v));
        StringBuilder json = new StringBuilder("{\n  \"generatedBy\": \"EditorPerformanceTest\",\n  \"java\": \"")
                .append(System.getProperty("java.version")).append("\",\n  \"os\": \"").append(System.getProperty("os.name")).append("\",\n  \"results\": [\n");
        for (int i = 0; i < results.size(); i++) {
            json.append("    {");
            int k = 0;
            for (Map.Entry<String, Object> e : results.get(i).entrySet()) {
                if (k++ > 0) json.append(", ");
                json.append('"').append(e.getKey()).append("\": ");
                json.append(e.getValue() instanceof String s ? "\"" + s + "\"" : e.getValue());
            }
            json.append(i + 1 < results.size() ? "},\n" : "}\n");
        }
        json.append("  ]\n}\n");
        Path out = Path.of("target", "studio-004-performance.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, json.toString());
        System.out.println(json);
    }

    private Map<String, Object> measure(Volume v) {
        // dados sintéticos: montados direto no catálogo e no agregado (somente teste), antes de a automação ter tela
        Automation a = Edt.call(() -> fx.studio.state().catalog().create("Volume " + v.sessions() + "x" + v.stepsPerSession(), null));
        Edt.run(() -> {
            for (int s = 0; s < v.sessions(); s++) {
                LogicalSession session = a.addSession("Sessão " + (s + 1), null);
                for (int i = 0; i < v.stepsPerSession(); i++) {
                    a.appendStep(session.id(), StepDraft.manual("Etapa " + (s + 1) + "." + (i + 1), i % 2 == 0 ? ActionType.CLICK : ActionType.FILL, null));
                }
            }
        });
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("volume", v.label());
        r.put("sessions", v.sessions());
        r.put("steps", a.stepCount());

        long t0 = System.nanoTime();
        Edt.run(() -> fx.studio.controller().openAutomation(a.id()));
        Edt.run(() -> Edt.layout(fx.studio.view().component()));
        r.put("openMs", ms(t0));
        AutomationOverview overview = Edt.call(() -> fx.studio.view().overview(a.id()).orElseThrow());

        LogicalSession first = a.sessions().get(0);
        LogicalSession second = a.sessions().get(1);
        AutomationStep last = first.steps().get(first.stepCount() - 1);
        List<LogicalSessionView> views = Edt.call(overview::sessionViews);
        int[] before = views.stream().mapToInt(LogicalSessionView::refreshCount).toArray();

        t0 = System.nanoTime();
        Edt.run(() -> fx.studio.service().moveStep(a.id(), last.id(), first.id(), 0));
        r.put("reorderStepMs", ms(t0));
        t0 = System.nanoTime();
        Edt.run(() -> fx.studio.service().moveStep(a.id(), last.id(), second.id(), 0));
        r.put("moveBetweenSessionsMs", ms(t0));
        int refreshed = 0;
        for (int i = 0; i < views.size(); i++) refreshed += views.get(i).refreshCount() - before[i];
        r.put("sessionViewsReread", refreshed);
        assertEquals(3, refreshed, "incremental: 1 (reorganizar) + 2 (origem e destino); as demais só renumeram");

        t0 = System.nanoTime();
        Edt.run(() -> fx.studio.service().moveSession(a.id(), a.sessions().get(v.sessions() - 1).id(), 0));
        r.put("moveSessionMs", ms(t0));
        assertTrue(Edt.call(overview::sessionViews).containsAll(views), "reordenar sessões não recria as visões");

        t0 = System.nanoTime();
        Edt.run(() -> fx.studio.service().undo(a.id()));
        r.put("undoMs", ms(t0));
        t0 = System.nanoTime();
        Edt.run(() -> fx.studio.service().redo(a.id()));
        r.put("redoMs", ms(t0));
        Edt.run(() -> fx.studio.service().undo(a.id()));

        AutomationStep middle = a.sessions().get(v.sessions() / 2).steps().get(0);
        t0 = System.nanoTime();
        Edt.run(() -> Edt.layout(StudioForms.stepDetails(a, middle).form()));
        r.put("editorOpenMs", ms(t0));

        long used0 = settledHeap();
        Edt.run(() -> {
            for (int i = 0; i < 100; i++) fx.studio.service().moveStep(a.id(), middle.id(), a.sessionOf(middle.id()).orElseThrow().id(), i % 2);
        });
        r.put("history100OpsKb", Math.max(0, (settledHeap() - used0) / 1024));
        r.put("historyEntries", fx.studio.service().history().undoSize(a.id()));
        r.put("heapUsedMb", usedHeap() / (1024 * 1024));
        for (String k : List.of("openMs", "reorderStepMs", "moveBetweenSessionsMs", "moveSessionMs", "undoMs", "redoMs", "editorOpenMs")) {
            assertTrue((double) r.get(k) < 5000, k + " degradado: " + r.get(k) + " ms");
        }
        Edt.run(() -> fx.studio.controller().closeAutomation(a.id()));
        return r;
    }

    private static double ms(long t0) {
        return Math.round((System.nanoTime() - t0) / 10_000.0) / 100.0;
    }

    /** Heap ocupado depois de várias coletas (medição aproximada: o coletor não é determinístico). */
    private static long settledHeap() {
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            System.gc();
            try {
                Thread.sleep(40);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            best = Math.min(best, usedHeap());
        }
        return best;
    }

    private static long usedHeap() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    @SuppressWarnings("unused")
    private static String f(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }
}
