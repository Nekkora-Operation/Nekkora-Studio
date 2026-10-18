package com.nekkora.studio.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.privacy.CapturePrivacy;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** NEKKORA-STUDIO-012 G2: o Studio mostra a estratégia preferida, por que ela foi escolhida e em que documento o elemento está. */
class ElementPresentationTest {

    private static ElementReference element(LocatorStrategy strategy, String css, int matches, AccessStep... path) {
        ElementReference.Builder b = ElementReference.builder().semanticKind("TEXT_INPUT").tag("input").accessibleName("").privacyPolicy(CapturePrivacy.VERSION)
                .addLocator(new LocatorCandidate(strategy, "", css, LocatorStability.HEURISTIC_STABLE, matches, true));
        if (matches == 1) b.preferLastLocator(); // o domínio só aceita preferir um localizador validado
        for (AccessStep s : path) b.addAccessStep(s);
        return b.build();
    }

    private static String row(ElementReference e, String label) {
        return ElementPresentation.rows(e).stream().filter(r -> r.label().equals(label)).map(ElementPresentation.Row::value).findFirst().orElse("");
    }

    @Test
    @DisplayName("id técnico único: estratégia, motivo e documento principal")
    void idDoDesenvolvedor() {
        ElementReference e = element(LocatorStrategy.ID, "#dsLogin", 1, new AccessStep(AccessStep.Type.MAIN_DOCUMENT, "", "", null),
                new AccessStep(AccessStep.Type.ELEMENT, "input", "#dsLogin", null));
        assertTrue(row(e, "Localizador").startsWith("ID: "), row(e, "Localizador"));
        assertEquals("identificador do desenvolvedor aceito pela política, único e validado na captura", row(e, "Por que este"));
        assertEquals("Documento principal", row(e, "Escopo"));
    }

    @Test
    @DisplayName("posicional dentro de iframe: requer revisão com motivo e escopo do frame")
    void posicionalNoIframe() {
        ElementReference e = element(LocatorStrategy.POSITIONAL, "html > body > div:nth-of-type(3) > select", 1,
                new AccessStep(AccessStep.Type.MAIN_DOCUMENT, "", "", null), new AccessStep(AccessStep.Type.IFRAME, "iframe", "div > iframe", null),
                new AccessStep(AccessStep.Type.ELEMENT, "select", "", null));
        assertTrue(row(e, "Localizador").startsWith("Posição: "), row(e, "Localizador"));
        assertTrue(row(e, "Por que este").contains("requer revisão"), row(e, "Por que este"));
        assertEquals("Dentro de iframe", row(e, "Escopo"));
        assertEquals("Dentro de iframe (2 níveis)", ElementPresentation.scope(List.of(new AccessStep(AccessStep.Type.MAIN_DOCUMENT, "", "", null),
                new AccessStep(AccessStep.Type.IFRAME, "", "", null), new AccessStep(AccessStep.Type.IFRAME, "", "", null))));
    }

    @Test
    @DisplayName("sem localizador validado: nenhum preferido, revisão declarada")
    void semValidado() {
        ElementReference e = element(LocatorStrategy.NAME, "[name=\"dsCampo\"]", 2);
        assertTrue(row(e, "Localizador").endsWith("requer revisão"), row(e, "Localizador"));
        assertEquals("", row(e, "Por que este"));
    }
}
