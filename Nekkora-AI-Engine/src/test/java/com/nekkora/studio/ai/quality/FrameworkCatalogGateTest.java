package com.nekkora.studio.ai.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.ai.ConsultaCase;
import com.nekkora.studio.ai.FichaCase;
import com.nekkora.studio.ai.capability.CatalogResolution;
import com.nekkora.studio.ai.capability.FrameworkCapabilityCatalog;
import com.nekkora.studio.ai.context.ProjectContext;
import com.nekkora.studio.ai.plan.ListFlowTest;
import com.nekkora.studio.ai.pycheck.PythonChecks;
import com.nekkora.studio.codegen.OutputWriter;
import com.nekkora.studio.pdd.DocumentDraft;
import com.nekkora.studio.validation.AutomationValidator;
import com.nekkora.studio.validation.RuleSet;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PACKAGING-001 — Validar automação com a versão do framework declarada pelo código gerado: o catálogo dessa versão
 * confere o V2; versão declarada sem catálogo bloqueia o V2 e NADA é dado por validado, mesmo com todas as verificações
 * do Python passando. Sem código gerado, o comportamento anterior.
 */
class FrameworkCatalogGateTest {
    @TempDir
    Path tmp;

    record Generated(FichaCase c, Path out) {}

    /** Código REAL do gerador (lista → planilha, contrato nekkora-rpa[excel]==0.3.0) gravado numa pasta. */
    Generated generated() throws Exception {
        FichaCase c = FichaCase.confirmed();
        ListFlowTest.Flow f = ListFlowTest.develop(c);
        Path out = tmp.resolve("fichas-python");
        OutputWriter.write(out, f.result().files());
        return new Generated(c, out);
    }

    static void declare(Path out, String framework) throws Exception {
        Path m = out.resolve("manifest.json");
        String text = Files.readString(m, StandardCharsets.UTF_8);
        String changed = text.replaceFirst("\"framework\"\\s*:\\s*\"[^\"]*\"", "\"framework\": \"" + framework + "\"");
        assertNotEquals(text, changed, "o manifesto gerado declara o framework");
        Files.writeString(m, changed, StandardCharsets.UTF_8);
    }

    /** Todas as verificações do Python passando (V4 e V5): o que um ambiente com a versão desconhecida instalada devolveria. */
    static final List<PythonChecks.Check> ALL_PASS = List.of(
            new PythonChecks.Check("compile", Layer.V4_CODE, PythonChecks.Status.PASS, 0, Map.of(), 1, ""),
            new PythonChecks.Check("mypy", Layer.V4_CODE, PythonChecks.Status.PASS, 0, Map.of(), 1, ""),
            new PythonChecks.Check("contract", Layer.V5_TESTS, PythonChecks.Status.PASS, 0, Map.of(), 1, ""));

    static AutomationQualityGate.Assessment assess(FichaCase c, Path out, String frameworkVersion) {
        CatalogResolution r = CatalogResolution.forGeneratedProject(out, FrameworkCapabilityCatalog.bundled());
        DocumentDraft d = c.reviewedDraft();
        ProjectContext ctx = ProjectContext.of(c.a, Optional.of(d));
        PythonChecks.Environment env = new PythonChecks.Environment(Path.of("python"), frameworkVersion, "0.17.1", "2.4.0", "3.12.10");
        return AutomationQualityGate.assess(new AutomationQualityGate.Inputs(c.a, new AutomationValidator(RuleSet.LATEST).validate(c.a), Optional.of(d),
                ListFlowTest.spec(c, ctx), r.catalog(), out, Optional.empty(), Optional.of(env), false, ALL_PASS, "", ConsultaCase.CLOCK.instant(), List.of(),
                Optional.empty(), "", r.problem()));
    }

    static QualityReport.LayerResult layer(AutomationQualityGate.Assessment as, Layer l) {
        return as.report().layers().get(l);
    }

    static boolean catalogMissing(AutomationQualityGate.Assessment as) {
        return as.report().issues().stream().anyMatch(i -> i.code().equals("FRAMEWORK_CATALOG_MISSING"));
    }

    @Test
    @DisplayName("0.3.0[excel] declarado: V2 com o catálogo 0.3.0 e, com as verificações passando, V4/V5 validados")
    void versao030() throws Exception {
        Generated g = generated();
        AutomationQualityGate.Assessment as = assess(g.c(), g.out(), "0.3.0");
        assertTrue(as.code().exists() && as.code().current(), as.code().toString());
        assertFalse(catalogMissing(as));
        assertEquals(QualityReport.State.PASS, layer(as, Layer.V2_CAPABILITY).state(), layer(as, Layer.V2_CAPABILITY).summary());
        assertTrue(layer(as, Layer.V2_CAPABILITY).summary().contains("nekkora_rpa 0.3.0"), layer(as, Layer.V2_CAPABILITY).summary());
        assertEquals(QualityReport.State.PASS, layer(as, Layer.V4_CODE).state());
        assertEquals(QualityReport.State.PASS, layer(as, Layer.V5_TESTS).state());
    }

    @Test
    @DisplayName("0.2.0 declarado: V2 conferido com o catálogo 0.2.0 (o 0.3.0 não o substitui) e sem diagnóstico de incompatibilidade")
    void versao020() throws Exception {
        Generated g = generated();
        declare(g.out(), "nekkora-rpa==0.2.0");
        AutomationQualityGate.Assessment as = assess(g.c(), g.out(), "0.2.0");
        assertFalse(catalogMissing(as));
        assertTrue(layer(as, Layer.V2_CAPABILITY).summary().contains("nekkora_rpa 0.2.0"), layer(as, Layer.V2_CAPABILITY).summary());
        assertTrue(as.report().catalog().startsWith("nekkora-rpa 0.2.0"), as.report().catalog());
    }

    @Test
    @DisplayName("versão desconhecida declarada: V2 BLOQUEADO com o diagnóstico; V4/V5/V6 NÃO validados mesmo com tudo passando")
    void versaoDesconhecida() throws Exception {
        Generated g = generated();
        declare(g.out(), "nekkora-rpa[excel]==0.9.9");
        AutomationQualityGate.Assessment as = assess(g.c(), g.out(), "0.9.9");
        assertTrue(as.code().exists() && as.code().current(), "trocar a versão declarada não torna o código desatualizado: o bloqueio é do catálogo");
        QualityIssue issue = as.report().issues().stream().filter(i -> i.code().equals("FRAMEWORK_CATALOG_MISSING")).findFirst()
                .orElseThrow(() -> new AssertionError(as.report().issues().toString()));
        assertEquals(QualityIssue.Kind.BLOCKER, issue.kind());
        assertEquals(Layer.V2_CAPABILITY, issue.layer());
        assertTrue(issue.detail().contains("nekkora-rpa[excel]==0.9.9"), issue.detail());
        assertEquals(QualityReport.State.BLOCKED, layer(as, Layer.V2_CAPABILITY).state());
        assertTrue(layer(as, Layer.V2_CAPABILITY).summary().startsWith("incompatível"), layer(as, Layer.V2_CAPABILITY).summary());
        for (Layer l : List.of(Layer.V4_CODE, Layer.V5_TESTS, Layer.V6_EXECUTION)) {
            assertNotEquals(QualityReport.State.PASS, layer(as, l).state(), l + ": " + layer(as, l).summary());
        }
        assertTrue(as.report().checks().isEmpty(), "as verificações não entram no relatório como prova");
        assertFalse(as.report().catalog().contains(FrameworkCapabilityCatalog.bundled().fingerprint()), "o catálogo padrão não é registrado como o que conferiu");
    }

    @Test
    @DisplayName("projeto sem manifesto (código ainda não gerado): catálogo padrão, sem diagnóstico de incompatibilidade — como antes")
    void semManifesto() throws Exception {
        Generated g = generated();
        Files.delete(g.out().resolve("manifest.json"));
        AutomationQualityGate.Assessment as = assess(g.c(), g.out(), "0.3.0");
        assertFalse(as.code().exists());
        assertFalse(catalogMissing(as));
        assertTrue(layer(as, Layer.V2_CAPABILITY).summary().contains("nekkora_rpa " + FrameworkCapabilityCatalog.bundled().version()),
                layer(as, Layer.V2_CAPABILITY).summary());
        assertEquals("código ainda não gerado", layer(as, Layer.V4_CODE).summary());
    }
}
