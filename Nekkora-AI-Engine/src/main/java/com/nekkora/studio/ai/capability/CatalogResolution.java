package com.nekkora.studio.ai.capability;

import com.nekkora.studio.ai.deps.FrameworkRequirement;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * PACKAGING-001 — o catálogo contra o qual o código gerado em uma pasta é conferido. Quando o {@code manifest.json}
 * declara a versão do framework, vale SÓ o catálogo dessa versão: se o Studio não o tem (ou o requisito não é
 * reconhecido), o resultado traz o diagnóstico e o catálogo padrão serve apenas ao planejamento — nunca para aprovar o
 * código. Sem código gerado (ou manifesto sem o campo {@code framework}), o catálogo padrão, como antes.
 *
 * @param catalog  o catálogo a usar (o da versão declarada, ou o padrão)
 * @param declared o requisito declarado no manifesto, como gravado (vazio sem código gerado)
 * @param problem  vazio quando compatível; senão o diagnóstico de incompatibilidade
 */
public record CatalogResolution(FrameworkCapabilityCatalog catalog, Optional<String> declared, String problem) {

    public CatalogResolution {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(declared, "declared");
        problem = problem == null ? "" : problem;
    }

    public boolean compatible() {
        return problem.isEmpty();
    }

    public static CatalogResolution forGeneratedProject(Path outputDir, FrameworkCapabilityCatalog fallback) {
        Optional<String> declared = FrameworkRequirement.declaredIn(outputDir);
        if (declared.isEmpty()) return new CatalogResolution(fallback, declared, "");
        Optional<FrameworkRequirement> req = FrameworkRequirement.parse(declared.get());
        if (req.isEmpty()) {
            return new CatalogResolution(fallback, declared, "o manifest.json do código gerado declara um framework não reconhecido (\""
                    + abbreviate(declared.get()) + "\")");
        }
        return FrameworkCapabilityCatalog.forVersion(req.get().version()).map(c -> new CatalogResolution(c, declared, ""))
                .orElseGet(() -> new CatalogResolution(fallback, declared, "o código gerado exige " + req.get().pip()
                        + ", mas este Studio não tem o catálogo de capacidades do nekkora_rpa " + req.get().version()));
    }

    private static String abbreviate(String s) {
        String t = s.strip();
        return t.length() <= 60 ? t : t.substring(0, 60) + "…";
    }
}
