package com.nekkora.studio.ai.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.ai.spec.FunctionalSpec;
import com.nekkora.studio.domain.AutomationId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** O endereço gravado é um MODELO (nekkora-privacy/2): origem lida do modelo; a resposta da pessoa é conferida contra ele. */
class StartUrlTest {
    static final String TEMPLATE = "http://127.0.0.1:18765/{…}/{…}/portal.html";

    @Test
    @DisplayName("origem de um endereço-modelo; resposta casa com o modelo só com a mesma origem, os mesmos trechos e sem consulta")
    void modelo() {
        assertEquals("http://127.0.0.1:18765", ProjectContext.origin(TEMPLATE));
        assertEquals("https://exemplo.test", ProjectContext.origin("https://Exemplo.TEST/{…}?q={…}"));
        assertTrue(ProjectContext.matchesTemplate("http://127.0.0.1:18765/lab/studio014/portal.html", TEMPLATE));
        assertFalse(ProjectContext.matchesTemplate("http://127.0.0.1:18765/lab/studio014/outra.html", TEMPLATE), "trecho conhecido diferente");
        assertFalse(ProjectContext.matchesTemplate("http://127.0.0.1:18765/lab/portal.html", TEMPLATE), "número de trechos diferente");
        assertFalse(ProjectContext.matchesTemplate("http://127.0.0.2:18765/lab/studio014/portal.html", TEMPLATE), "outra origem");
        assertFalse(ProjectContext.matchesTemplate("http://127.0.0.1:18765/lab/studio014/portal.html?usuario=x", TEMPLATE), "consulta pode ter dados");
        assertFalse(ProjectContext.matchesTemplate("http://127.0.0.1:18765/lab/studio014/portal.html#x", TEMPLATE));
        assertFalse(ProjectContext.matchesTemplate("", TEMPLATE));
    }

    @Test
    @DisplayName("decisão START_URL: só http(s) sem consulta, sem etapa; o contrato recusa o resto")
    void decisao() {
        AutomationId id = new AutomationId(UUID.randomUUID());
        FunctionalSpec s = FunctionalSpec.empty(id).with(new FunctionalSpec.Decision(FunctionalSpec.Field.START_URL, null,
                "http://127.0.0.1:18765/lab/studio014/portal.html", FunctionalSpec.Actor.DEVELOPER, FunctionalSpec.Basis.ANSWER, null, Instant.EPOCH));
        assertEquals(s, FunctionalSpec.parse(s.toJson()));
        assertThrows(IllegalArgumentException.class, () -> new FunctionalSpec.Decision(FunctionalSpec.Field.START_URL, null, "javascript:alert(1)",
                FunctionalSpec.Actor.DEVELOPER, FunctionalSpec.Basis.ANSWER, null, Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new FunctionalSpec.Decision(FunctionalSpec.Field.START_URL, null, "http://h/p?cpf=1",
                FunctionalSpec.Actor.DEVELOPER, FunctionalSpec.Basis.ANSWER, null, Instant.EPOCH));
    }
}
