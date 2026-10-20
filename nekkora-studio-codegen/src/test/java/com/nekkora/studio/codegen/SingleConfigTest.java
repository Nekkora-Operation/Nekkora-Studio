package com.nekkora.studio.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** STUDIO-014 — leitura ESTRITA da configuração interna 1.1.0 (execução única); a 1.0.0 continua sendo a do lote. */
class SingleConfigTest {
    static final String S1 = "11111111-1111-4111-8111-111111111111";
    static final String S2 = "22222222-2222-4222-8222-222222222222";
    static final String SHA = "sha256:" + "a".repeat(64);

    static SingleConfig valid() {
        return SingleConfig.of("consulta", "lab", List.of("http://127.0.0.1:18765"), "http://127.0.0.1:18765/lab/portal.html",
                List.of(new SingleConfig.Group("TelaConsulta", "consulta", "consulta", List.of(S1, S2))), List.of("codigo"), Map.of(),
                Map.of(S2, new SingleConfig.Effect("EXTERNAL", "TARGET_HIDDEN")), new SingleConfig.Pdd("PDD_READY", SHA, SHA, SHA));
    }

    static String code(String json) {
        return assertThrows(CodegenConfig.Invalid.class, () -> SingleConfig.parse(json)).code();
    }

    @Test
    @DisplayName("1.1.0 relida igual, com revisão; recusas: campo extra, gate não pronto, início com consulta, envio sem confirmação, versão errada")
    void estrita() {
        SingleConfig c = valid();
        assertEquals(c.toJson(), SingleConfig.parse(c.toJson()).toJson());
        assertEquals(c.revision(), SingleConfig.parse(c.toJson()).revision());
        String j = c.toJson();
        assertEquals("CONFIG_UNKNOWN_FIELD", code(j.replaceFirst("\\{", "{\"extra\": 1, ")));
        assertEquals("CONFIG_PDD_NOT_READY", code(j.replace("\"PDD_READY\"", "\"PDD_BLOCKED\"")));
        assertEquals("CONFIG_START_URL", code(j.replace("/lab/portal.html\"", "/lab/portal.html?cpf=1\"")));
        assertEquals("CONFIG_EFFECTS_POSTCONDITION", code(j.replace("\"TARGET_HIDDEN\"", "null")));
        assertEquals("CONFIG_VERSION", code(j.replace("\"1.1.0\"", "\"1.0.0\"")));
        assertEquals("CONFIG_ENVIRONMENT", code(j.replace("\"lab\"", "\"producao\"")));
        assertEquals("CONFIG_GROUPS_STEPS", code(j.replace("\"" + S2 + "\"\n", "\"" + S1 + "\"\n")));
    }
}
