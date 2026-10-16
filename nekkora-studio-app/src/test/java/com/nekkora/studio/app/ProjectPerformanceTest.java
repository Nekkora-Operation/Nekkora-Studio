package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepContext;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.element.ContextAnchor;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.evidence.EvidenceType;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary;
import com.nekkora.studio.domain.evidence.ObservedEvidence;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepProvenance;
import com.nekkora.studio.domain.privacy.CapturePrivacy;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import com.nekkora.studio.persistence.ProjectFiles;
import com.nekkora.studio.persistence.ProjectFormat;
import com.nekkora.studio.persistence.ProjectPackage;
import com.nekkora.studio.persistence.ProjectRecovery;
import com.nekkora.studio.persistence.ProjectRepository;
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
 * STUDIO-006 §39: medição de projetos de 10, 100, 1.000 e 5.000 etapas realistas (elemento capturado com 3 localizadores,
 * trajetória e contexto; 1 em cada 3 etapas com 3 evidências; comentários e edições). Os números vão para
 * {@code target/studio-006-performance.json} e para o relatório; não são limites do produto.
 */
class ProjectPerformanceTest {

    @TempDir
    Path dir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);
    private final ProjectFiles files = new ProjectFiles(new ProjectRepository(), "medição");
    private static final String URL = CapturePrivacy.urlTemplate("http://127.0.0.1:18765/lab/studio003/clientes.html");

    private Automation build(int steps) {
        Automation a = Automation.create("Volume " + steps, "Medição de desempenho", clock);
        int sessions = Math.max(1, steps / 25);
        List<LogicalSession> ss = new ArrayList<>();
        for (int s = 0; s < sessions; s++) ss.add(a.addSession("Sessão " + (s + 1), "Sessão de medição"));
        for (int i = 0; i < steps; i++) {
            String ev = "ev-" + i;
            CorrelationKeys keys = CorrelationKeys.of(Map.of(CorrelationKeys.Key.RECORDING_ID, "rec-1", CorrelationKeys.Key.RECORDED_EVENT_ID, ev,
                    CorrelationKeys.Key.TARGET_ID, "tgt-" + i, CorrelationKeys.Key.NODE_ID, "n" + i));
            ElementReference e = ElementReference.builder().accessibleName("Salvar").semanticKind("BUTTON").tag("button").htmlId("salvar")
                    .privacyPolicy(DisplaySafety.KNOWN_PRODUCER_POLICY).sourceContract("nekkora-bridge/1#studio.recording.step").correlation(keys)
                    .addLocator(new LocatorCandidate(LocatorStrategy.ID, "button {id=salvar}", "#salvar", LocatorStability.HEURISTIC_STABLE, 1, true))
                    .preferLastLocator()
                    .addLocator(new LocatorCandidate(LocatorStrategy.POSITIONAL, "html >> body >> form {nth-of-type 1} >> button {nth-of-type 2}",
                            "html > body > form:nth-of-type(1) > button:nth-of-type(2)", LocatorStability.HEURISTIC_UNSTABLE, 1, true))
                    .addLocator(new LocatorCandidate(LocatorStrategy.TEXT, "button {text=\"Salvar\"}", "//button[normalize-space(.)=\"Salvar\"]",
                            LocatorStability.UNKNOWN, 3, true))
                    .addAccessStep(AccessStep.of(AccessStep.Type.MAIN_DOCUMENT, "Documento")).addContext(new ContextAnchor("IN_FORM", "FORM", "Cadastro", ""))
                    .addStabilityNote("Localizador preferencial executado na página real e único").build();
            RecordingObservation obs = new RecordingObservation(RecordingObservation.CaptureMode.CAPTURE_AND_INTERACT,
                    RecordingObservation.Observation.INTERACTION_OBSERVED, null, List.of("FORM_SUBMITTED"), List.of());
            AutomationStep st = a.appendStep(ss.get(i % sessions).id(), StepDraft.builder().name("Clicar no botão Salvar").actionType(ActionType.CLICK)
                    .description("Interação observada no navegador e encaminhada à página.").provenance(StepProvenance.recorded(keys, obs)).element(e)
                    .context(new StepContext("Navegador", "Cadastro", URL)).build());
            if (i % 3 == 0) {
                for (int k = 0; k < 3; k++) {
                    a.attachEvidence(new ObservedEvidence("evd-" + i + "-" + k, i * 3L + k + 1, "rec-1", ev, Instant.parse("2026-10-16T12:00:00Z"),
                            k == 2 ? EvidenceType.OBSERVATION_COMPLETE : EvidenceType.VISIBLE_STATUS_MESSAGE, EvidenceVocabulary.Source.FEEDBACK_OBSERVER,
                            EvidenceVocabulary.Relation.AFTER_ACTION, k == 2 ? EvidenceVocabulary.Confidence.DIRECTLY_OBSERVED
                                    : EvidenceVocabulary.Confidence.TEMPORALLY_ASSOCIATED, null, null, null, ObservedEvidence.Message.of("ok"), null, null, null,
                            List.of(EvidenceVocabulary.Signal.ROLE_STATUS), 0, k == 2 ? 2 : 0, List.of()));
                }
            }
            if (i % 10 == 0) {
                a.addComment(st.id(), "Revisar esta etapa com a equipe");
                a.updateStepBasics(st.id(), "Salvar cadastro " + i, "Etapa revisada", ActionType.CLICK);
            }
        }
        return a;
    }

    private static long median(LongSupplier s) {
        long[] v = new long[3];
        for (int i = 0; i < 3; i++) v[i] = s.getAsLong();
        Arrays.sort(v);
        return v[1];
    }

    private static long heapUsed() {
        Runtime r = Runtime.getRuntime();
        for (int i = 0; i < 3; i++) System.gc();
        return r.totalMemory() - r.freeMemory();
    }

    private Map<String, Object> measure(int steps) throws Exception {
        Automation a = build(steps);
        String pid = UUID.randomUUID().toString();
        Path file = dir.resolve("volume-" + steps + ProjectFormat.SUFFIX);
        // aquecimento
        files.save(file, ProjectFiles.Snapshot.of(a, pid), null, true, Instant.now(), clock);
        long snapshotNs = median(() -> Edt.call(() -> {
            long t = System.nanoTime();
            ProjectFiles.Snapshot.of(a, pid);
            return System.nanoTime() - t;
        }));
        ProjectFiles.Snapshot snap = ProjectFiles.Snapshot.of(a, pid);
        long serializeNs = median(() -> {
            long t = System.nanoTime();
            try {
                files.serialize(snap, ProjectFormat.Kind.PROJECT, null, Instant.now());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return System.nanoTime() - t;
        });
        long[] fp = {0};
        long saveNs = median(() -> {
            long t = System.nanoTime();
            try {
                fp[0] = files.save(file, snap, files.repository().fingerprintOf(file), false, Instant.now(), clock).fingerprint().size();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return System.nanoTime() - t;
        });
        long readPackageNs = median(() -> {
            long t = System.nanoTime();
            try {
                ProjectPackage.read(file);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return System.nanoTime() - t;
        });
        long[] loadedSteps = {0};
        long loadNs = median(() -> {
            long t = System.nanoTime();
            try {
                loadedSteps[0] = files.load(file, clock).automation().stepCount();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return System.nanoTime() - t;
        });
        assertEquals(steps, loadedSteps[0]);
        long before = heapUsed();
        Automation held = files.load(file, clock).automation();
        long after = heapUsed();
        assertEquals(steps, held.stepCount());
        ProjectRecovery recovery = new ProjectRecovery(dir.resolve("recovery-" + steps), files);
        long recoveryWriteNs = median(() -> {
            long t = System.nanoTime();
            try {
                recovery.write(snap, System.nanoTime(), new ProjectPackage.RecoveryOrigin(null, null), Instant.now(), clock);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return System.nanoTime() - t;
        });
        long t = System.nanoTime();
        int offered = recovery.scan(clock).size();
        long recoveryScanNs = System.nanoTime() - t;
        assertEquals(1, offered);
        int evidences = a.sessions().stream().flatMap(s -> s.steps().stream()).mapToInt(s -> s.evidence().size()).sum();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("etapas", steps);
        m.put("sessoes", a.sessions().size());
        m.put("evidencias", evidences);
        m.put("arquivoBytes", fp[0]);
        m.put("fotografiaEdtMs", ms(snapshotNs));
        m.put("serializacaoMs", ms(serializeNs));
        m.put("salvarComReleituraMs", ms(saveNs));
        m.put("leituraPacoteMs", ms(readPackageNs));
        m.put("abrirCompletoMs", ms(loadNs));
        m.put("validacaoEReconstrucaoMs", ms(loadNs - readPackageNs));
        m.put("memoriaProjetoAbertoMB", Math.max(0, (after - before)) / 1024 / 1024.0);
        m.put("recuperacaoGravarMs", ms(recoveryWriteNs));
        m.put("recuperacaoVarrerMs", ms(recoveryScanNs));
        return m;
    }

    private static double ms(long ns) {
        return Math.round(ns / 10_000.0) / 100.0;
    }

    @Test
    @DisplayName("S006 §39 — desempenho com 10, 100, 1.000 e 5.000 etapas (EDT só na fotografia)")
    void medir() throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        for (int n : List.of(10, 100, 1_000, 5_000)) results.add(measure(n));
        StringBuilder json = new StringBuilder("{\n  \"generatedBy\": \"ProjectPerformanceTest\",\n  \"java\": \"").append(System.getProperty("java.version"))
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
        Path out = Path.of("target", "studio-006-performance.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, json.toString());
        System.out.println(json);
        // guarda-corpo: a EDT só fotografa; mesmo 5.000 etapas não podem travá-la de forma perceptível
        assertTrue((double) results.get(3).get("fotografiaEdtMs") < 1_000, "fotografia na EDT: " + results.get(3).get("fotografiaEdtMs"));
    }
}
