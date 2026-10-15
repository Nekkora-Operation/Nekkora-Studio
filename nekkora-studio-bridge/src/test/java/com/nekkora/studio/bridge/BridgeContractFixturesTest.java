package com.nekkora.studio.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.bridge.json.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato nekkora-bridge/1 compartilhado com a extensão (mesmo modelo do PageMap): fixtures copiadas para cá e
 * conferidas contra o repositório da extensão quando ele está ao lado.
 */
class BridgeContractFixturesTest {

    private static final Path LOCAL = Path.of("src", "test", "resources", "contracts");
    private static final Path SIBLING = Path.of("..", "..", "Nekkora-Extension-Chrome", "contracts");

    private static List<Path> files(Path root) throws Exception {
        try (Stream<Path> s = Files.walk(root.resolve("studio-bridge"))) {
            return s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    @Test
    @DisplayName("fixtures válidas: o Studio lê todas; envelopes e vocabulário dentro do que o Studio conhece")
    void fixturesValidasSaoCompreendidas() throws Exception {
        List<Path> valid = new ArrayList<>();
        for (Path p : files(LOCAL)) if (p.toString().contains("valid") && !p.toString().contains("invalid")) valid.add(p);
        assertTrue(valid.size() >= 20, "fixtures: " + valid.size());
        for (Path p : valid) {
            Map<String, Object> m = Json.parseObject(Files.readString(p));
            assertEquals("nekkora-bridge/1", m.get("protocol"), p.toString());
            String kind = Json.string(m, "kind");
            String type = Json.string(m, "type");
            switch (kind) {
                case "command", "response" -> assertTrue(BridgeProtocol.COMMANDS.contains(type), p + ": " + type);
                case "event" -> assertTrue(BridgeProtocol.EVENTS.contains(type), p + ": " + type);
                case "handshake" -> assertTrue(type.startsWith("bridge."), p.toString());
                default -> throw new AssertionError(p + ": kind " + kind);
            }
            assertEquals(m, Json.parseObject(Json.write(m)), "ida e volta sem perda: " + p);
        }
    }

    @Test
    @DisplayName("vocabulário do Studio = contrato da extensão (comandos, eventos, estados e modos)")
    void vocabularioIgualAoContrato() throws Exception {
        Map<String, Object> schema = Json.parseObject(Files.readString(LOCAL.resolve("studio-bridge.schema.json")));
        Map<String, Object> defs = Json.object(schema, "$defs");
        assertEquals(new TreeSet<>(BridgeProtocol.COMMANDS), enumOf(defs, "CommandType"));
        assertEquals(new TreeSet<>(BridgeProtocol.EVENTS), enumOf(defs, "EventType"));
        assertEquals(new TreeSet<>(BridgeProtocol.RECORDING_STATES), enumOf(defs, "RecordingState"));
        assertEquals(new TreeSet<>(BridgeProtocol.MODES), enumOf(defs, "Mode"));
        TreeSet<String> codes = enumOf(defs, "ErrorCode");
        for (String c : codes) {
            assertEquals(c, BridgeError.of(c).name(), "código do contrato conhecido pelo Studio: " + c);
        }
    }

    private static TreeSet<String> enumOf(Map<String, Object> defs, String name) {
        TreeSet<String> out = new TreeSet<>();
        for (Object o : Json.array(Json.object(defs, name), "enum")) out.add(String.valueOf(o));
        return out;
    }

    /** Conteúdo com quebras de linha normalizadas (cada repositório pode convertê-las no checkout). */
    private static String text(Path p) throws Exception {
        return Files.readString(p).replace("\r\n", "\n");
    }

    @Test
    @DisplayName("fixtures e schema idênticos aos do repositório da extensão (quando está ao lado)")
    void naoDivergiuDoRepositorioDaExtensao() throws Exception {
        Assumptions.assumeTrue(Files.exists(SIBLING.resolve("studio-bridge.schema.json")), "Nekkora-Extension-Chrome não está ao lado deste repositório");
        assertEquals(text(SIBLING.resolve("studio-bridge.schema.json")), text(LOCAL.resolve("studio-bridge.schema.json")),
                "schema copiado diverge do contrato vivo");
        List<Path> theirs = files(SIBLING);
        List<Path> ours = files(LOCAL);
        assertEquals(theirs.size(), ours.size(), "mesmo conjunto de fixtures");
        for (int i = 0; i < theirs.size(); i++) {
            assertEquals(SIBLING.relativize(theirs.get(i)).toString(), LOCAL.relativize(ours.get(i)).toString());
            assertEquals(text(theirs.get(i)), text(ours.get(i)), "fixture divergente: " + ours.get(i));
        }
    }
}
