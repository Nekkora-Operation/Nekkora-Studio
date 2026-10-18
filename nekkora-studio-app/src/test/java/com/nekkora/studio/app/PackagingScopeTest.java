package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * STUDIO-012 PDD-01 (achado A3) — o jar empacotado saía sem {@code nekkora-studio-semantics}: o app o declarava com
 * escopo {@code test}, e essa declaração direta vence a dependência transitiva de um módulo de produção
 * ({@code nekkora-studio-guided}). Os testes não percebiam (o módulo estava no classpath de teste). Regra: nenhum
 * módulo do Studio de que um módulo de produção do app depende pode estar declarado como {@code test} no app.
 */
class PackagingScopeTest {

    /** artifactId → escopo ("compile" se ausente) das dependências {@code nekkora-studio-*} de um pom. */
    static Map<String, String> studioDeps(Path pom) throws Exception {
        var f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var doc = f.newDocumentBuilder().parse(pom.toFile());
        Map<String, String> out = new LinkedHashMap<>();
        NodeList deps = doc.getElementsByTagName("dependency");
        for (int i = 0; i < deps.getLength(); i++) {
            Element d = (Element) deps.item(i);
            if (!"dependencies".equals(d.getParentNode().getNodeName()) || !"project".equals(d.getParentNode().getParentNode().getNodeName())) continue;
            String id = text(d, "artifactId");
            if (id == null || !id.startsWith("nekkora-studio-")) continue;
            String scope = text(d, "scope");
            out.put(id, scope == null ? "compile" : scope);
        }
        return out;
    }

    private static String text(Element e, String tag) {
        NodeList n = e.getElementsByTagName(tag);
        return n.getLength() == 0 ? null : n.item(0).getTextContent().strip();
    }

    @Test
    @DisplayName("A3: nenhum módulo usado em produção (direta ou transitivamente) está com escopo test no app — o jar sombreado o inclui")
    void modulosDeProducaoNaoFicamComEscopoDeTeste() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Map<String, String> app = studioDeps(root.resolve("nekkora-studio-app/pom.xml"));
        List<String> problems = new ArrayList<>();
        List<String> queue = new ArrayList<>(app.entrySet().stream().filter(e -> !"test".equals(e.getValue())).map(Map.Entry::getKey).toList());
        for (int i = 0; i < queue.size(); i++) {
            Path pom = root.resolve(queue.get(i)).resolve("pom.xml");
            if (!Files.exists(pom)) continue;
            for (Map.Entry<String, String> d : studioDeps(pom).entrySet()) {
                if ("test".equals(d.getValue())) continue;
                if ("test".equals(app.get(d.getKey()))) problems.add(queue.get(i) + " → " + d.getKey());
                if (!queue.contains(d.getKey())) queue.add(d.getKey());
            }
        }
        assertTrue(problems.isEmpty(), "módulos de produção com escopo test no app (sairiam do jar): " + problems);
        assertTrue(queue.contains("nekkora-studio-semantics"), "o caso do achado é coberto: " + queue);
    }
}
