package com.nekkora.studio.ai.deps;

import com.nekkora.studio.bridge.json.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PACKAGING-001 — o contrato de framework que um projeto gerado EXIGE: versão exata do {@code nekkora-rpa} e extras.
 * Vem do {@code manifest.json} do próprio projeto gerado (campo {@code framework}, gravado pelo gerador): reabrir um
 * projeto nunca troca a versão — um projeto 0.2.0 continua 0.2.0 até ser gerado de novo por decisão da pessoa.
 */
public record FrameworkRequirement(String version, List<String> extras) {

    private static final Pattern REQ = Pattern.compile("^nekkora-rpa(?:\\[([a-z0-9]+(?:,[a-z0-9]+)*)\\])?==(\\d+\\.\\d+\\.\\d+)$");

    public FrameworkRequirement {
        extras = List.copyOf(extras);
    }

    /** {@code nekkora-rpa==0.2.0} ou {@code nekkora-rpa[excel]==0.3.0}; qualquer outra forma é recusada. */
    public static Optional<FrameworkRequirement> parse(String requirement) {
        Matcher m = requirement == null ? null : REQ.matcher(requirement.strip());
        if (m == null || !m.matches()) return Optional.empty();
        return Optional.of(new FrameworkRequirement(m.group(2), m.group(1) == null ? List.of() : List.of(m.group(1).split(","))));
    }

    /** O requisito gravado no {@code manifest.json} do projeto gerado (vazio se não há código gerado ou o campo falta). */
    public static Optional<FrameworkRequirement> ofGeneratedProject(Path projectDir) {
        Path manifest = projectDir == null ? null : projectDir.resolve("manifest.json");
        if (manifest == null || !Files.isRegularFile(manifest)) return Optional.empty();
        try {
            Map<String, Object> m = Json.parseObject(Files.readString(manifest, StandardCharsets.UTF_8));
            return m.get("framework") instanceof String s ? parse(s) : Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    public String pip() {
        return "nekkora-rpa" + (extras.isEmpty() ? "" : "[" + String.join(",", extras) + "]") + "==" + version;
    }

    /** Nome CURTO da pasta do ambiente: versão e extras (0.2.0 e 0.3.0 nunca dividem a mesma venv; caminhos curtos no Windows). */
    public String key() {
        return version + (extras.isEmpty() ? "" : "-" + String.join("-", extras));
    }

    public boolean excel() {
        return extras.contains("excel");
    }
}
