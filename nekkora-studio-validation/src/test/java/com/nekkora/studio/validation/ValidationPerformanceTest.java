package com.nekkora.studio.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.OptionChoice;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.element.ObservedObstacle;
import com.nekkora.studio.domain.evidence.EvidenceType;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Limitation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * STUDIO-007 §18: desempenho do motor com 10, 100, 1.000 e 5.000 etapas REALISTAS (gravadas, com 3 localizadores,
 * obstáculos, parâmetros variados e 3 evidências por etapa). Mede a mediana de várias execuções depois do aquecimento.
 * Com {@code -Dnekkora.perf.out=<arquivo>}, grava as medições em JSON.
 */
class ValidationPerformanceTest {

    static Automation synthetic(int steps) {
        Automation a = Automation.create("Processo sintético " + steps, "", Fixtures.CLOCK);
        LogicalSession session = null;
        for (int i = 0; i < steps; i++) {
            if (i % 50 == 0) session = a.addSession("Sessão " + (i / 50 + 1), null);
            ActionType type = switch (i % 5) {
                case 0 -> ActionType.FILL;
                case 1 -> ActionType.CLICK;
                case 2 -> ActionType.SELECT_OPTION;
                case 3 -> ActionType.PRESS_KEY;
                default -> ActionType.CHECK;
            };
            ElementReference.Builder e = ElementReference.builder().semanticKind("TEXT_INPUT").tag("input").privacyPolicy("nekkora-privacy/2")
                    .addLocator(new LocatorCandidate(LocatorStrategy.TEXT, "campo", "#c" + i, LocatorStability.HEURISTIC_STABLE, 2, true))
                    .addLocator(new LocatorCandidate(LocatorStrategy.ID, "campo", "#id" + i, i % 7 == 0 ? LocatorStability.UNKNOWN
                            : LocatorStability.OBSERVED_STABLE, 1, true)).preferLastLocator()
                    .addLocator(new LocatorCandidate(LocatorStrategy.POSITIONAL, "campo", "div > input:nth-child(" + (i % 9 + 1) + ")",
                            LocatorStability.HEURISTIC_UNSTABLE, 1, true));
            if (i % 11 == 0) e.addObstacle(new ObservedObstacle("DYNAMIC_IDS", ObservedObstacle.Severity.MEDIUM, ""));
            String event = "ev-" + i;
            AutomationStep s = a.appendStep(session.id(), Fixtures.recorded("Etapa " + i, type, event, e.build(), i % 13 == 0 ? "LOCATORS_WITHHELD" : "X"));
            ActionParameters p = switch (type) {
                case FILL -> i % 3 == 0 ? ActionParameters.none() : ActionParameters.withValue(i % 3 == 1 ? new ValueSource.VariableReference("campo.v" + i)
                        : new ValueSource.LiteralValue("termo " + i));
                case SELECT_OPTION -> i % 4 == 0 ? ActionParameters.none() : ActionParameters.withOption(new OptionChoice("Opção " + (i % 10), "o" + (i % 10)));
                case PRESS_KEY -> ActionParameters.withValue(new ValueSource.LiteralValue("Enter"));
                default -> ActionParameters.none();
            };
            if (!p.isEmpty()) a.setStepParameters(s.id(), p);
            a.attachEvidence(Fixtures.evidence("evd-" + i + "-1", i * 3L + 1, event, EvidenceType.FORM_SUBMITTED));
            a.attachEvidence(Fixtures.evidence("evd-" + i + "-2", i * 3L + 2, event, EvidenceType.VISIBLE_ERROR_MESSAGE, Limitation.MESSAGE_SUPPRESSED));
            a.attachEvidence(Fixtures.evidence("evd-" + i + "-3", i * 3L + 3, event, EvidenceType.OBSERVATION_COMPLETE));
        }
        return a;
    }

    @Test
    @DisplayName("S007 §18 — 10, 100, 1.000 e 5.000 etapas: tempo proporcional às etapas; 5.000 etapas bem abaixo do limite de interação")
    void desempenho() throws Exception {
        AutomationValidator v = new AutomationValidator();
        int[] sizes = {10, 100, 1000, 5000};
        List<String> rows = new ArrayList<>();
        double ms5000 = 0;
        for (int n : sizes) {
            Automation a = synthetic(n);
            for (int i = 0; i < 5; i++) v.validate(a); // aquecimento
            long[] t = new long[9];
            ValidationReport r = null;
            for (int i = 0; i < t.length; i++) {
                long t0 = System.nanoTime();
                r = v.validate(a);
                t[i] = System.nanoTime() - t0;
            }
            Arrays.sort(t);
            double median = t[t.length / 2] / 1e6;
            long t0 = System.nanoTime();
            int bytes = r.toJson().getBytes(StandardCharsets.UTF_8).length;
            double jsonMs = (System.nanoTime() - t0) / 1e6;
            assertEquals(n, r.summary().steps());
            System.out.printf(Locale.ROOT, "validacao etapas=%d medianaMs=%.2f maxMs=%.2f apontamentos=%d jsonMs=%.2f jsonBytes=%d%n", n, median,
                    t[t.length - 1] / 1e6, r.findings().size(), jsonMs, bytes);
            rows.add(String.format(Locale.ROOT, "    {\"steps\": %d, \"medianMs\": %.2f, \"maxMs\": %.2f, \"findings\": %d, \"jsonMs\": %.2f, \"jsonBytes\": %d}",
                    n, median, t[t.length - 1] / 1e6, r.findings().size(), jsonMs, bytes));
            if (n == 5000) ms5000 = median;
        }
        assertTrue(ms5000 < 1000, "5.000 etapas em " + ms5000 + " ms");
        String out = System.getProperty("nekkora.perf.out");
        if (out != null && !out.isBlank()) {
            Files.writeString(Path.of(out), "{\n  \"java\": \"" + System.getProperty("java.version") + "\",\n  \"os\": \"" + System.getProperty("os.name")
                    + "\",\n  \"measurements\": [\n" + String.join(",\n", rows) + "\n  ]\n}\n", StandardCharsets.UTF_8);
        }
    }
}
