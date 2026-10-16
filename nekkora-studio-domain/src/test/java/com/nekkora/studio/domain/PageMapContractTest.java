package com.nekkora.studio.domain;

import static com.nekkora.studio.domain.MiniJson.arr;
import static com.nekkora.studio.domain.MiniJson.obj;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.OptionChoice;
import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.element.ContextAnchor;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.element.ObservedObstacle;
import com.nekkora.studio.domain.origin.StepOrigin;
import com.nekkora.studio.domain.origin.StepProvenance;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fronteira com a Chrome Extension (PageMap 0.2.0): o {@link ElementReference} usa os mesmos valores
 * de enum do contrato e consegue representar um recorte sanitizado sem duplicar o PageMap. O mapeamento
 * abaixo é um protótipo de teste do adaptador que a STUDIO-002 implementará.
 */
class PageMapContractTest {

    private static Map<String, Object> resource(String name) throws IOException {
        try (InputStream in = PageMapContractTest.class.getResourceAsStream("/contracts/pagemap-0.2.0/" + name)) {
            return obj(MiniJson.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }

    private static Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toSet());
    }

    private static Set<String> strings(Object list) {
        return arr(list).stream().map(String::valueOf).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("CA-019 enums do ElementReference coincidem com o PageMap 0.2.0")
    void enumsCompativeis() throws IOException {
        Map<String, Object> e = resource("enums.json");
        assertEquals("https://nekkora.dev/contracts/page-map/0.2.0", e.get("schemaId"));
        assertEquals(strings(e.get("strategy")), names(LocatorStrategy.values()));
        assertEquals(strings(e.get("stability")), names(LocatorStability.values()));
        assertEquals(strings(e.get("accessStepType")), names(AccessStep.Type.values()));
        assertEquals(strings(e.get("obstacleSeverity")), names(ObservedObstacle.Severity.values()));
        assertTrue(strings(e.get("elementKind")).contains(ElementReference.UNKNOWN_KIND));
    }

    @Test
    @DisplayName("CA-019 fixture de enums continua igual ao contrato vivo da extensão (quando o repositório está ao lado)")
    void fixtureNaoDivergiuDoContrato() throws IOException {
        Path schema = Path.of("..", "..", "Nekkora-Extension-Chrome", "contracts", "page-map.schema.json");
        Assumptions.assumeTrue(Files.exists(schema), "Nekkora-Extension-Chrome não está ao lado deste repositório");
        Map<String, Object> s = obj(MiniJson.parse(Files.readString(schema)));
        // CHROME-SECURITY-001: a 0.3.0 só acrescenta privacy/text.suppressed — as enumerações verificadas aqui são as mesmas
        Assumptions.assumeTrue(java.util.Set.of("https://nekkora.dev/contracts/page-map/0.2.0", "https://nekkora.dev/contracts/page-map/0.3.0")
                .contains(s.get("$id")),
                "o contrato vivo já é outra versão — atualize o fixture em uma missão de integração");
        Map<String, Object> defs = obj(s.get("$defs"));
        Map<String, Object> e = resource("enums.json");
        assertEquals(strings(e.get("strategy")), strings(obj(defs.get("strategy")).get("enum")));
        assertEquals(strings(e.get("stability")), strings(obj(defs.get("stability")).get("enum")));
        assertEquals(strings(e.get("elementKind")), strings(obj(defs.get("elementKind")).get("enum")));
        Map<String, Object> access = obj(obj(obj(defs.get("AccessStep")).get("properties")).get("type"));
        assertEquals(strings(e.get("accessStepType")), strings(access.get("enum")));
    }

    @Test
    @DisplayName("8.1/10.1 'Selecionar São Paulo no campo Estado' é UMA etapa, com trajetória por iframe e contexto")
    void selectEmIframeViraUmaEtapa() throws IOException {
        Map<String, Object> enums = resource("enums.json");
        Map<String, Object> fx = resource("element-select-in-iframe.json");
        ElementReference ref = toReference(fx);

        assertTrue(strings(enums.get("elementKind")).contains(ref.semanticKind()));
        assertEquals("Estado", ref.accessibleName());
        assertEquals("select", ref.tag());
        assertEquals(List.of(AccessStep.Type.MAIN_DOCUMENT, AccessStep.Type.IFRAME, AccessStep.Type.ELEMENT),
                ref.accessPath().stream().map(AccessStep::type).toList());
        assertEquals("iframe#cadastro", ref.accessPath().get(1).label());
        assertEquals(2, ref.context().size(), "âncoras de contexto preservadas sem profundidade fixa");
        assertTrue(strings(enums.get("contextRelationType")).containsAll(ref.context().stream().map(ContextAnchor::relation).toList()));
        assertEquals(LocatorStrategy.ID, ref.preferredLocator().orElseThrow().strategy());
        assertEquals(3, ref.locators().size());
        // CHROME-SECURITY-001 (D5): a fixture 0.2.0 vem marcada com nekkora-privacy/1 — a referência é lida, mas id e name
        // não são mais exibidos como seguros (só a política vigente, nekkora-privacy/2, é reconhecida)
        assertEquals("estado", ref.htmlId().orElseThrow());
        assertEquals("uf", ref.htmlName().orElseThrow());
        assertTrue(ref.displayableHtmlId().isEmpty() && ref.displayableHtmlName().isEmpty());
        assertEquals("cap-lab-0001", ref.correlation().get(CorrelationKeys.Key.CAPTURE_ID).orElseThrow());

        Map<String, Object> chosen = arr(obj(fx.get("optionSet")).get("options")).stream().map(MiniJson::obj)
                .filter(o -> o.get("nodeId").equals(fx.get("chosenOptionNodeId"))).findFirst().orElseThrow();
        OptionChoice option = new OptionChoice((String) chosen.get("text"), (String) chosen.get("value"));

        Automation a = new AutomationCatalog(Clock.systemUTC()).create("Cadastro", null);
        LogicalSession s = a.addSession("Endereço", null);
        AutomationStep step = a.appendStep(s.id(), StepDraft.builder()
                .name("Selecionar " + option.label() + " no campo " + ref.accessibleName())
                .actionType(ActionType.SELECT_OPTION)
                .provenance(StepProvenance.of(StepOrigin.RECORDED, ref.correlation()))
                .element(ref)
                .parameters(ActionParameters.withOption(option))
                .build());

        assertEquals(1, s.stepCount(), "o option não vira uma etapa separada");
        assertEquals("Selecionar São Paulo no campo Estado", step.name());
        assertEquals("SP", step.parameters().option().orElseThrow().value());
        assertEquals("select", step.element().orElseThrow().tag());
        assertEquals(StepOrigin.RECORDED, step.provenance().origin());
    }

    /** Protótipo do adaptador PageMap → ElementReference (a versão de produção é da STUDIO-002). */
    private static ElementReference toReference(Map<String, Object> fx) {
        Map<String, Object> el = obj(fx.get("element"));
        Map<String, Object> attrs = obj(el.get("attributes"));
        Map<String, Object> locs = obj(el.get("locators"));
        EnumMap<CorrelationKeys.Key, String> keys = new EnumMap<>(CorrelationKeys.Key.class);
        keys.put(CorrelationKeys.Key.CAPTURE_ID, (String) fx.get("captureId"));
        keys.put(CorrelationKeys.Key.NODE_ID, (String) el.get("nodeId"));
        keys.put(CorrelationKeys.Key.DOCUMENT_ID, (String) el.get("documentId"));
        ElementReference.Builder b = ElementReference.builder()
                .accessibleName((String) obj(el.get("text")).get("accessibleName"))
                .semanticKind((String) el.get("kind"))
                .tag((String) el.get("tag"))
                .htmlId((String) attrs.get("id"))
                .htmlName((String) attrs.get("name"))
                .sensitive(Boolean.TRUE.equals(el.get("sensitive")))
                .privacyPolicy((String) fx.get("privacyPolicy"))
                .sourceContract(fx.get("schema") + "/" + fx.get("version"))
                .correlation(CorrelationKeys.of(keys));
        b.addLocator(locator(obj(locs.get("primary"))));
        if (locator(obj(locs.get("primary"))).validated()) b.preferLastLocator();
        for (Object f : arr(locs.get("fallbacks"))) b.addLocator(locator(obj(f)));
        for (Object o : arr(obj(fx.get("accessPath")).get("steps"))) {
            Map<String, Object> st = obj(o);
            b.addAccessStep(new AccessStep(AccessStep.Type.valueOf((String) st.get("type")), (String) st.get("label"),
                    (String) st.get("locator"), CorrelationKeys.none()));
        }
        for (Object o : arr(fx.get("context"))) {
            Map<String, Object> c = obj(o);
            b.addContext(new ContextAnchor((String) c.get("type"), (String) c.get("kind"), (String) c.get("name"), (String) c.get("locator")));
        }
        for (Object o : arr(fx.get("obstacles"))) {
            Map<String, Object> ob = obj(o);
            b.addObstacle(new ObservedObstacle((String) ob.get("type"), ObservedObstacle.Severity.valueOf((String) ob.get("severity")),
                    (String) ob.get("description")));
        }
        return b.build();
    }

    private static LocatorCandidate locator(Map<String, Object> l) {
        return new LocatorCandidate(LocatorStrategy.valueOf((String) l.get("strategy")), (String) l.get("semantic"),
                (String) l.get("css"), LocatorStability.valueOf((String) l.get("stability")),
                ((Double) l.get("matchCount")).intValue(), Boolean.TRUE.equals(l.get("matchesTarget")));
    }

    @Test
    @DisplayName("fixtures de contrato são pequenos (sem PageMaps extensos no Studio)")
    void fixturesPequenos() throws IOException {
        for (String f : List.of("enums.json", "element-select-in-iframe.json")) {
            try (InputStream in = PageMapContractTest.class.getResourceAsStream("/contracts/pagemap-0.2.0/" + f)) {
                assertTrue(in.readAllBytes().length < 8_192, f);
            }
        }
        assertFalse(resource("element-select-in-iframe.json").containsKey("elements"), "não é um PageMap inteiro");
    }
}
