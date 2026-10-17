package com.nekkora.studio.guided;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.target.DecisionActor;
import com.nekkora.studio.domain.target.TargetDecision;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** STUDIO-009 G7 — registro do debug: conforme ao schema, determinístico, só códigos, ator explícito; nada da página. */
class GuidedDebugRecordTest {
    private static final Path DIR = Path.of("src/test/resources/contracts/guided-debug-record/1.0.0");
    private static final Path FIXTURE = DIR.resolve("fixtures/passagem-laboratorio.json");
    private static final Instant AT = Instant.parse("2026-10-17T12:00:05Z");

    @Test
    @DisplayName("passagem de laboratório: conforme ao schema e IDÊNTICA à fixture; decisão final do agente de teste; nada de valores")
    void registro() throws Exception {
        Automation a = LabCase.consulta();
        AutomationStep fill = a.sessions().get(0).steps().get(1);
        AutomationStep click = a.sessions().get(0).steps().get(2);
        a.decideTarget(fill.id(), TargetDecision.Kind.CONFIRMED, DecisionActor.TEST_AGENT,
                new TargetDecision.Basis("TARGET_ID", "RESOLVED", List.of(), AT, ""));
        GuidedPlan plan = GuidedPlanTest.plan(a);
        List<GuidedDebugRecord.Entry> entries = List.of(
                new GuidedDebugRecord.Entry(fill.id(), AT, GuidedDebugRecord.Kind.VERIFICATION, "RESOLVED", "TARGET_ID", List.of(), "NO_DECISION", List.of(),
                        DecisionActor.TEST_AGENT),
                new GuidedDebugRecord.Entry(fill.id(), AT, GuidedDebugRecord.Kind.DECISION, "CONFIRMED", "TARGET_ID", List.of(), "", List.of(),
                        DecisionActor.TEST_AGENT),
                new GuidedDebugRecord.Entry(click.id(), AT, GuidedDebugRecord.Kind.VERIFICATION, "AMBIGUOUS", "DESCRIPTOR",
                        List.of("DESCRIPTOR_ONLY", "texto livre da página", "TARGET_AMBIGUOUS"), "NO_DECISION", List.of(), DecisionActor.TEST_AGENT),
                new GuidedDebugRecord.Entry(click.id(), AT, GuidedDebugRecord.Kind.PROBE, "RESOLVED", "", List.of(), "PERFORMED", List.of(),
                        DecisionActor.TEST_AGENT));
        String json = GuidedDebugRecord.toJson(plan, a, DecisionActor.TEST_AGENT, Instant.parse("2026-10-17T12:10:00Z"), entries);
        Map<String, Object> schema = Json.parseObject(Files.readString(DIR.resolve("guided-debug-record.schema.json"), StandardCharsets.UTF_8));
        assertEquals(List.of(), new Schemas.SchemaValidator(schema).validate(Json.parse(json), "Record"));
        String normalized = GuidedPlanTest.normalized(json);
        if (Boolean.getBoolean("nekkora.fixtures009.regenerate")) {
            Files.createDirectories(FIXTURE.getParent());
            Files.writeString(FIXTURE, normalized, StandardCharsets.UTF_8);
        }
        assertEquals(Files.readString(FIXTURE, StandardCharsets.UTF_8).replace("\r\n", "\n"), normalized);
        for (String forbidden : List.of("texto livre", "registro.codigo", "Código do registro", "Consultar", "tgt-", LabCase.LITERAL_URL, "HUMAN", "AI_VERIFIED")) {
            assertFalse(json.contains(forbidden), forbidden);
        }
        assertThrows(IllegalArgumentException.class, () -> new GuidedDebugRecord.Entry(fill.id(), AT, GuidedDebugRecord.Kind.DECISION, "parece certo", "",
                List.of(), "", List.of(), DecisionActor.DEVELOPER), "status só como código");
    }
}
