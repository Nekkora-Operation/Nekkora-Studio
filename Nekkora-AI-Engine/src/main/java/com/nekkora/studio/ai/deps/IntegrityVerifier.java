package com.nekkora.studio.ai.deps;

import com.nekkora.studio.ai.capability.FrameworkCapabilityCatalog;
import com.nekkora.studio.ai.pycheck.PythonChecks;
import com.nekkora.studio.bridge.json.Json;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PACKAGING-001 — IntegrityVerifier/CapabilityProbe: um interpretador SERVE ao requisito do projeto? Confere, no próprio
 * interpretador (sem rede): a versão instalada do {@code nekkora-rpa} pelos metadados da distribuição e pelo módulo;
 * que o módulo vem do PRÓPRIO ambiente (nunca de um checkout por acaso); os esquemas do pacote; o extra {@code excel}
 * quando exigido; ruff e mypy nas versões homologadas; {@code pip check}; e (verificação completa) a SONDA real contra o
 * catálogo DA MESMA VERSÃO. Qualquer divergência vira um diagnóstico único — e o código não é dado por verificado.
 */
public final class IntegrityVerifier {
    private IntegrityVerifier() {}

    public record Result(Diagnosis diagnosis, String detail, Optional<PythonChecks.Environment> environment) {
        public boolean ok() {
            return diagnosis == Diagnosis.READY;
        }
    }

    static final String SCRIPT = String.join("\n",
            "import importlib.metadata as m, importlib.resources as r, json, sys",
            "out = {'python': '%d.%d.%d' % sys.version_info[:3], 'prefix': sys.prefix}",
            "def ver(n):",
            "    try:",
            "        return m.version(n)",
            "    except m.PackageNotFoundError:",
            "        return None",
            "out['dist'] = ver('nekkora-rpa'); out['ruff'] = ver('ruff'); out['mypy'] = ver('mypy'); out['openpyxl'] = ver('openpyxl')",
            "try:",
            "    import nekkora_rpa",
            "    out['module'] = nekkora_rpa.__version__; out['file'] = nekkora_rpa.__file__",
            "    out['schemas'] = sorted(p.name for p in r.files('nekkora_rpa.contracts').iterdir() if p.name.endswith('.json'))",
            "except Exception as e:",
            "    out['import_error'] = type(e).__name__",
            "print(json.dumps(out))");

    /** Verificação rápida (uso diário) ou completa ({@code full}: + {@code pip check} + sonda contra o catálogo da versão). */
    @SuppressWarnings("unchecked")
    public static Result verify(Path python, FrameworkRequirement req, FrameworkArtifacts artifacts, boolean full) {
        Map<String, Object> m;
        try {
            ProcessRunner.Result r = ProcessRunner.runRaw(List.of(python.toString(), "-I", "-B", "-c", SCRIPT), Duration.ofSeconds(60));
            if (r.exit() != 0) return fail(Diagnosis.ENV_CORRUPTED, "o interpretador do ambiente não respondeu");
            String[] lines = r.output().strip().split("\\R");
            m = Json.parseObject(lines[lines.length - 1]);
        } catch (IOException | RuntimeException e) {
            return fail(Diagnosis.ENV_CORRUPTED, "o interpretador do ambiente não existe ou não roda");
        }
        String dist = (String) m.get("dist");
        if (dist == null || m.get("import_error") != null) return fail(Diagnosis.DEPENDENCY_MISSING, "nekkora-rpa ausente ou não importa");
        if (!req.version().equals(dist) || !req.version().equals(m.get("module"))) {
            return fail(Diagnosis.VERSION_MISMATCH, "instalado nekkora-rpa " + dist + "; o projeto exige " + req.version());
        }
        String file = String.valueOf(m.get("file"));
        String prefix = String.valueOf(m.get("prefix"));
        if (!Path.of(file).toAbsolutePath().normalize().startsWith(Path.of(prefix).toAbsolutePath().normalize())) {
            return fail(Diagnosis.ENV_CORRUPTED, "o nekkora_rpa importado vem de fora do ambiente (checkout/caminho local)");
        }
        if (req.excel() && m.get("openpyxl") == null) return fail(Diagnosis.DEPENDENCY_MISSING, "extra excel (openpyxl) ausente");
        if (!artifacts.ruff().equals(m.get("ruff")) || !artifacts.mypy().equals(m.get("mypy"))) {
            return fail(Diagnosis.DEPENDENCY_MISSING, "ferramentas de verificação ausentes ou em outra versão (ruff " + m.get("ruff") + ", mypy " + m.get("mypy") + ")");
        }
        List<Object> schemas = (List<Object>) m.get("schemas");
        List<String> expected = artifacts.forVersion(req.version()).map(FrameworkArtifacts.Artifact::schemas).orElse(List.of());
        if (!expected.isEmpty() && !expected.equals(schemas)) return fail(Diagnosis.ENV_CORRUPTED, "esquemas do pacote diferentes dos homologados");
        PythonChecks.Environment env = new PythonChecks.Environment(python, dist, String.valueOf(m.get("ruff")), String.valueOf(m.get("mypy")),
                String.valueOf(m.get("python")));
        if (full) {
            try {
                ProcessRunner.Result check = ProcessRunner.run(List.of(python.toString(), "-I", "-m", "pip", "check"), null, Duration.ofSeconds(60), () -> false, null);
                if (check.exit() != 0) return fail(Diagnosis.DEPENDENCY_MISSING, "pip check: " + firstLine(check.output()));
            } catch (IOException e) {
                return fail(Diagnosis.ENV_CORRUPTED, "pip check não rodou");
            }
            Optional<FrameworkCapabilityCatalog> catalog = FrameworkCapabilityCatalog.forVersion(req.version());
            if (catalog.isEmpty()) return fail(Diagnosis.UNSUPPORTED_VERSION, "o Studio não tem o catálogo da versão " + req.version());
            try {
                Optional<String> probe = PythonChecks.probe(env, Json.write(catalog.get().probeRequest()));
                if (probe.isEmpty()) return fail(Diagnosis.UNSUPPORTED_CAPABILITY, "a sonda do framework não rodou");
                List<FrameworkCapabilityCatalog.Mismatch> mism = catalog.get().compare(FrameworkCapabilityCatalog.Probe.parse(probe.get()));
                if (!mism.isEmpty()) return fail(Diagnosis.UNSUPPORTED_CAPABILITY, mism.size() + " divergência(s) com o catálogo " + req.version() + ": " + mism.get(0));
            } catch (IOException | RuntimeException e) {
                return fail(Diagnosis.UNSUPPORTED_CAPABILITY, "a sonda do framework falhou");
            }
        }
        return new Result(Diagnosis.READY, "nekkora-rpa " + dist + (req.excel() ? " [excel]" : "") + ", Python " + m.get("python"), Optional.of(env));
    }

    private static Result fail(Diagnosis d, String detail) {
        return new Result(d, detail, Optional.empty());
    }

    private static String firstLine(String s) {
        String[] l = s.strip().split("\\R");
        return l.length == 0 ? "" : l[0];
    }
}
