package com.nekkora.studio.domain;

import com.nekkora.studio.domain.privacy.CapturePrivacy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * CHROME-SECURITY-001 — 4ª barreira: a política do Studio produz exatamente os resultados dos vetores compartilhados com a
 * extensão, e o vocabulário/vetores copiados são idênticos aos do repositório da extensão quando ele está ao lado.
 */
class CapturePrivacyTest {

    private static final Path LOCAL_VECTORS = Path.of("src", "test", "resources", "contracts", "privacy", "privacy-v2-vectors.json");
    private static final Path LOCAL_VOCABULARY = Path.of("src", "main", "resources", "privacy", "ui-vocabulary.json");
    private static final Path SIBLING = Path.of("..", "..", "Nekkora-Extension-Chrome", "contracts", "privacy");

    private static String text(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    @Test
    @DisplayName("SEC001-067 — vetores compartilhados: texto de interface, identificadores, classes, URLs, expressões, atributos, portadores")
    void sharedVectors() throws Exception {
        Map<String, Object> v = MiniJson.obj(MiniJson.parse(Files.readString(LOCAL_VECTORS)));
        assertEquals(CapturePrivacy.VERSION, v.get("policy"));
        for (Object o : MiniJson.arr(v.get("interfaceText"))) {
            Map<String, Object> c = MiniJson.obj(o);
            assertEquals(text(c.get("out")), CapturePrivacy.interfaceText(text(c.get("in"))), "interfaceText " + c.get("in"));
        }
        for (Object o : MiniJson.arr(v.get("identifiers"))) {
            Map<String, Object> c = MiniJson.obj(o);
            assertEquals(text(c.get("out")), CapturePrivacy.identifier(text(c.get("in"))), "identifier " + c.get("in"));
        }
        for (Object o : MiniJson.arr(v.get("classes"))) {
            Map<String, Object> c = MiniJson.obj(o);
            assertEquals(text(c.get("out")), CapturePrivacy.classes(text(c.get("in"))), "classes " + c.get("in"));
        }
        for (Object o : MiniJson.arr(v.get("urls"))) {
            Map<String, Object> c = MiniJson.obj(o);
            assertEquals(text(c.get("out")), CapturePrivacy.urlTemplate(text(c.get("in"))), "urlTemplate " + c.get("in"));
        }
        for (Object o : MiniJson.arr(v.get("expressions"))) {
            Map<String, Object> c = MiniJson.obj(o);
            assertEquals(c.get("out"), CapturePrivacy.expressionSafe(text(c.get("in"))), "expressionSafe " + c.get("in"));
        }
        for (Object o : MiniJson.arr(v.get("attributes"))) {
            Map<String, Object> c = MiniJson.obj(o);
            assertEquals(text(c.get("out")), CapturePrivacy.attribute(text(c.get("name")), text(c.get("value")), text(c.get("tag")), text(c.get("type"))),
                    "attribute " + c.get("name"));
        }
        for (Object o : MiniJson.arr(v.get("carriers"))) {
            Map<String, Object> c = MiniJson.obj(o);
            assertEquals(c.get("out"), CapturePrivacy.isCarrier(text(c.get("tag")), text(c.get("role")), Boolean.TRUE.equals(c.get("hasHref"))),
                    "isCarrier " + c.get("tag"));
        }
        for (Object o : MiniJson.arr(v.get("tags"))) {
            Map<String, Object> c = MiniJson.obj(o);
            assertEquals(text(c.get("out")), CapturePrivacy.tagName(text(c.get("in"))), "tagName " + c.get("in"));
        }
    }

    @Test
    @DisplayName("SEC001-067 — vocabulário e vetores copiados são idênticos aos da extensão (quando o repositório está ao lado)")
    void copiesMatchTheExtension() throws Exception {
        if (!Files.isDirectory(SIBLING)) {
            return; // ambiente sem o repositório irmão (ex.: Linux de referência): as cópias valem por si
        }
        // conteúdo idêntico; o fim de linha depende do checkout (core.autocrlf) e não faz parte do contrato
        assertEquals(lf(Files.readString(SIBLING.resolve("ui-vocabulary.json"))), lf(Files.readString(LOCAL_VOCABULARY)), "rode a cópia de contracts/privacy");
        assertEquals(lf(Files.readString(SIBLING.resolve("privacy-v2-vectors.json"))), lf(Files.readString(LOCAL_VECTORS)));
        assertEquals(lf(Files.readString(SIBLING.resolve("structural-attributes.json"))),
                lf(Files.readString(Path.of("src", "main", "resources", "privacy", "structural-attributes.json"))), "rode a cópia de contracts/privacy");
    }

    private static String lf(String s) {
        return s.replace("\r\n", "\n");
    }

    @Test
    @DisplayName("SEC001-070 — nada de expansão em execução; sobrenomes comuns ficam fora")
    void closedVocabulary() throws Exception {
        int before = CapturePrivacy.vocabularySize();
        CapturePrivacy.vocabularyOnly("Quixadá Quixadá");
        assertEquals(before, CapturePrivacy.vocabularySize());
        Map<String, Object> voc = MiniJson.obj(MiniJson.parse(Files.readString(LOCAL_VOCABULARY)));
        List<Object> excluded = MiniJson.arr(voc.get("excludedOnPurpose"));
        for (Object o : excluded) {
            assertNull(CapturePrivacy.interfaceText(String.valueOf(o)), String.valueOf(o));
        }
        assertFalse(CapturePrivacy.vocabularyOnly("o cliente joão já existe"));
    }
}
