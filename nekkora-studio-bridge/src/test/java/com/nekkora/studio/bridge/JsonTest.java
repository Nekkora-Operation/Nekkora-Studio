package com.nekkora.studio.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.bridge.json.JsonException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void leEEscreveSemPerda() {
        String text = "{\"a\":1,\"b\":-2.5,\"c\":\"ç\\\"\\n\\u00e9😀\",\"d\":[true,false,null],\"e\":{},\"f\":[]}";
        Map<String, Object> m = Json.parseObject(text);
        assertEquals(1L, m.get("a"));
        assertEquals(-2.5, m.get("b"));
        assertEquals("ç\"\né😀", m.get("c"));
        assertSame(Json.NULL, ((List<?>) m.get("d")).get(2));
        assertEquals(m, Json.parseObject(Json.write(m)));
        assertEquals("{\"x\":\"\\u0001\\u2028\"}", Json.write(Map.of("x", "\u0001\u2028")));
    }

    @Test
    void estrito() {
        for (String bad : new String[] {"{\"a\":1,\"a\":2}", "{\"a\":1}x", "{'a':1}", "{\"a\":01}", "{\"a\":\"\u0001\"}", "[1,]", "{\"a\":1,}",
            "{\"a\":tru}", "\"\\x\"", "", "{\"a\":1.}", "{\"a\":-}"}) {
            assertThrows(JsonException.class, () -> Json.parse(bad), bad);
        }
        assertThrows(JsonException.class, () -> Json.parse("[".repeat(100) + "]".repeat(100)), "profundidade limitada");
        assertThrows(JsonException.class, () -> Json.write(Map.of("x", Double.NaN)));
    }
}
