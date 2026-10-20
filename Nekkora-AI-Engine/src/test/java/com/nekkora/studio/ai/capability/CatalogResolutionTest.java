package com.nekkora.studio.ai.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PACKAGING-001 — o catálogo do código gerado vem da versão declarada no manifesto; sem catálogo dela, diagnóstico (nunca o padrão em silêncio). */
class CatalogResolutionTest {
    @TempDir
    Path tmp;

    static final FrameworkCapabilityCatalog FALLBACK = FrameworkCapabilityCatalog.bundled();

    Path generated(String name, String manifestJson) throws Exception {
        Path out = tmp.resolve(name);
        Files.createDirectories(out);
        if (manifestJson != null) Files.writeString(out.resolve("manifest.json"), manifestJson, StandardCharsets.UTF_8);
        return out;
    }

    static String manifest(String framework) {
        return "{\"schema\":\"nekkora-codegen/manifest\",\"automationId\":\"x\",\"framework\":\"" + framework + "\"}";
    }

    @Test
    @DisplayName("0.2.0 declarado → catálogo 0.2.0 (compatível; o 0.3.0 não o substitui)")
    void versao020() throws Exception {
        CatalogResolution r = CatalogResolution.forGeneratedProject(generated("legado", manifest("nekkora-rpa==0.2.0")), FALLBACK);
        assertTrue(r.compatible(), r.problem());
        assertEquals("0.2.0", r.catalog().version());
        assertEquals(Optional.of("nekkora-rpa==0.2.0"), r.declared());
    }

    @Test
    @DisplayName("0.3.0[excel] declarado → catálogo 0.3.0 (compatível)")
    void versao030() throws Exception {
        CatalogResolution r = CatalogResolution.forGeneratedProject(generated("lista", manifest("nekkora-rpa[excel]==0.3.0")), FALLBACK);
        assertTrue(r.compatible(), r.problem());
        assertEquals("0.3.0", r.catalog().version());
    }

    @Test
    @DisplayName("versão desconhecida declarada → incompatível, com a versão exata no diagnóstico; o padrão só fica para o planejamento")
    void versaoDesconhecida() throws Exception {
        CatalogResolution r = CatalogResolution.forGeneratedProject(generated("futura", manifest("nekkora-rpa[excel]==0.9.9")), FALLBACK);
        assertFalse(r.compatible());
        assertTrue(r.problem().contains("nekkora-rpa[excel]==0.9.9") && r.problem().contains("catálogo") && r.problem().contains("0.9.9"), r.problem());
        assertSame(FALLBACK, r.catalog());
        assertFalse(FrameworkCapabilityCatalog.forVersion("0.9.9").isPresent(), "pré-condição: o Studio não tem o catálogo 0.9.9");
    }

    @Test
    @DisplayName("requisito declarado mas não reconhecido (faixa em vez de versão exata) → incompatível")
    void requisitoNaoReconhecido() throws Exception {
        CatalogResolution r = CatalogResolution.forGeneratedProject(generated("faixa", manifest("nekkora-rpa>=0.3")), FALLBACK);
        assertFalse(r.compatible());
        assertTrue(r.problem().contains("não reconhecido") && r.problem().contains("nekkora-rpa>=0.3"), r.problem());
    }

    @Test
    @DisplayName("sem manifesto (código ainda não gerado), sem pasta ou manifesto sem o campo framework → catálogo padrão, como antes")
    void semManifesto() throws Exception {
        for (Path out : new Path[] {generated("vazia", null), null, tmp.resolve("nao-existe"),
                generated("sem-campo", "{\"schema\":\"nekkora-codegen/manifest\",\"automationId\":\"x\"}")}) {
            CatalogResolution r = CatalogResolution.forGeneratedProject(out, FALLBACK);
            assertTrue(r.compatible(), String.valueOf(out));
            assertSame(FALLBACK, r.catalog());
            assertEquals(Optional.empty(), r.declared());
        }
    }
}
