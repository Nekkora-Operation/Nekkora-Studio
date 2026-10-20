package com.nekkora.studio.ai.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.ai.PythonForTests;
import com.nekkora.studio.domain.action.ActionType;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FrameworkCapabilityCatalogTest {

    @Test
    @DisplayName("catálogo do jar: versão 0.2.0, capacidades com import, assinatura, código e documentação reais")
    void bundled() {
        FrameworkCapabilityCatalog c = FrameworkCapabilityCatalog.bundled();
        assertEquals("0.2.0", c.version());
        assertTrue(c.fingerprint().matches("sha256:[0-9a-f]{64}"));
        FrameworkCapabilityCatalog.Capability click = c.find("actions.click").orElseThrow();
        assertEquals("from nekkora_rpa import Actions", click.importLine());
        assertTrue(click.signature().contains("postcondition"), click.signature());
        assertEquals("nekkora_rpa/libs/actions.py", click.source().file());
        assertEquals(FrameworkCapabilityCatalog.Effect.MAY_AFFECT_TARGET, click.effect());
        assertTrue(click.whereToRead("0.2.0").contains("docs/contracts.md"));
        for (FrameworkCapabilityCatalog.Capability x : c.all()) {
            assertFalse(x.signature().isBlank(), x.id());
            assertTrue(x.source().line() > 0, x.id());
            assertTrue(x.docs().section().startsWith("#"), x.id());
        }
    }

    @Test
    @DisplayName("capacidade inexistente → UNSUPPORTED_CAPABILITY com o caminho de extensão; ação sem tradução → vazio")
    void recusa() {
        FrameworkCapabilityCatalog c = FrameworkCapabilityCatalog.bundled();
        FrameworkCapabilityCatalog.Unsupported u = c.require("actions.drag_and_drop").orElseThrow();
        assertEquals("UNSUPPORTED_CAPABILITY", u.code());
        assertEquals("docs/extending.md", u.extending().file());
        assertTrue(c.require("actions.fill").isEmpty());
        assertTrue(c.forAction(ActionType.SELECT_OPTION).isEmpty(), "SELECT_OPTION não tem Lib nesta versão");
        assertEquals("actions.read_text", c.forAction(ActionType.OBSERVE).get(0).id());
    }

    @Test
    @DisplayName("sonda divergente: outra versão, assinatura diferente e capacidade ausente viram achados")
    void divergencia() {
        FrameworkCapabilityCatalog c = FrameworkCapabilityCatalog.bundled();
        Map<String, String> sig = new HashMap<>();
        for (FrameworkCapabilityCatalog.Capability x : c.all()) sig.put(x.id(), x.signature());
        assertTrue(c.compare(new FrameworkCapabilityCatalog.Probe("0.2.0", "3.12.10", sig, List.of(), c.fingerprint())).isEmpty());
        sig.put("actions.click", "(key: 'str') -> 'None'");
        List<FrameworkCapabilityCatalog.Mismatch> m = c.compare(new FrameworkCapabilityCatalog.Probe("0.3.0", "3.12.10", sig, List.of("actions.mark"), ""));
        assertTrue(m.contains(new FrameworkCapabilityCatalog.Mismatch("FRAMEWORK_VERSION_MISMATCH", null)), m.toString());
        assertTrue(m.contains(new FrameworkCapabilityCatalog.Mismatch("SIGNATURE_MISMATCH", "actions.click")), m.toString());
        assertTrue(m.contains(new FrameworkCapabilityCatalog.Mismatch("CAPABILITY_MISSING_IN_INSTALLED", "actions.mark")), m.toString());
    }

    @Test
    @DisplayName("[Python real] a sonda do nekkora_rpa instalado confere com o catálogo, e o catálogo está em dia com o gerador")
    void sondaReal() throws Exception {
        Path py = PythonForTests.framework();
        Assumptions.assumeTrue(py != null, "sem o interpretador do framework neste ambiente");
        FrameworkCapabilityCatalog c = FrameworkCapabilityCatalog.bundled();
        String out = PythonForTests.run(py, List.of("-I", "-B", Path.of("src/main/resources/nekkora/ai/python/sonda_framework.py").toString()),
                com.nekkora.studio.bridge.json.Json.write(c.probeRequest()));
        FrameworkCapabilityCatalog.Probe p = FrameworkCapabilityCatalog.Probe.parse(out.strip());
        assertEquals(List.of(), c.compare(p));
        assertEquals(c.fingerprint(), p.fingerprint());
        String check = PythonForTests.run(py, List.of("-B", Path.of("tools/gerar_catalogo.py").toString(), "--framework-repo",
                PythonForTests.frameworkRepo().toString(), "--check"), "");
        assertTrue(check.contains("em dia"), check);
    }
}
