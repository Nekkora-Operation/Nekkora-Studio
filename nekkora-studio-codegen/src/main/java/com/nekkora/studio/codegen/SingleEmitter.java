package com.nekkora.studio.codegen;

import com.nekkora.studio.codegen.SingleGenerator.Step;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.guided.GuidedPlan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Arquivos do projeto de EXECUÇÃO ÚNICA (1.1.0, framework 0.2.0) e da LISTA → PLANILHA (1.2.0, 0.3.0), determinísticos e
 * só com o que o pré-voo aprovou. NEKKORA-CODEGEN-QUALITY-001: Pages e processo saem do {@link CodePlan} pelo
 * {@link SemanticEmitter}; o manifesto descreve a arquitetura, o mapa traceId ↔ StepId e como cada alvo é localizado; o
 * README explica a automação. Entradas validadas antes de qualquer ação; segredos só pelo provedor; planilha só pelo
 * {@code XlsxResultSink} (tudo ou nada).
 *
 * <p>Rodada 3 (Q10/Q11, manifesto 1.4.0): código em INGLÊS no pacote {@code automation/} ({@code selectors.py},
 * {@code pages.py}, {@code process.py}); {@code selectors.json} DERIVADO do mesmo plano para o runtime 0.2.0/0.3.0 (que só
 * lê o JSON), com a equivalência .py ↔ JSON registrada; linha de comando {@code --input/--spreadsheet/--output} (os nomes
 * anteriores continuam aceitos); o manifesto registra o mapa de nomes (origem em português → identificador) e as chaves
 * tipadas. Nenhum módulo de topo com nome da biblioteca padrão.
 */
final class SingleEmitter {

    static final String MANIFEST_VERSION = "1.4.0";
    static final String TRACE_SCHEMA = "nekkora-studio/trace-map";
    static final String LAYOUT = "nekkora-studio/automation-package/1.0.0";
    static final String CONFIG_FILE = "config/execution.toml";

    private SingleEmitter() {}

    static Map<String, String> emit(Automation a, GuidedPlan plan, SingleConfig cfg, List<Step> steps, CodeNames names) {
        Map<String, String> files = new TreeMap<>();
        String processClass = processClass(cfg.processName());
        CodePlan code = CodePlan.build(cfg, steps, names, processClass);
        Map<String, Map<String, Object>> targets = targets(steps);
        files.put("selectors.json", selectors(targets));
        files.put(CONFIG_FILE, toml(cfg));
        files.put(CodePlan.INIT_FILE, PyWrap.wrap(SemanticEmitter.init(code)));
        files.put(CodePlan.SELECTORS_FILE, SemanticEmitter.selectors(code, targets));
        files.put(CodePlan.PAGES_FILE, PyWrap.wrap(SemanticEmitter.pages(code)));
        files.put(CodePlan.PROCESS_FILE, PyWrap.wrap(SemanticEmitter.process(code, cfg, steps)));
        files.put("main.py", PyWrap.wrap(main(cfg, code)));
        files.put("requirements.txt", InstallHint.requirementsTxtEnglish("CODEGEN-QUALITY-001", requirement(cfg)));
        files.put("README.md", readme(cfg, steps, code));
        files.put("manifest.json", manifest(a, plan, cfg, steps, code, names, targets, files));
        return files;
    }

    static String requirement(SingleConfig cfg) {
        return cfg.modern() ? SingleGenerator.FRAMEWORK_REQUIREMENT_LIST : SingleGenerator.FRAMEWORK_REQUIREMENT;
    }

    /**
     * Classe do processo: o nome de negócio em inglês + {@code Process} ({@code biografias_da_wikipedia} →
     * {@code WikipediaBiographiesProcess}). O nome de negócio em si ({@code RpaProcess.name}) não muda: identifica o
     * processo nas execuções e nos checkpoints.
     */
    static String processClass(String processName) {
        String c = EnglishNames.identifier(processName).pascal();
        if (c.isEmpty() || !Character.isLetter(c.charAt(0))) return "GeneratedProcess";
        if (c.length() > 53) c = c.substring(0, 53);
        return c + "Process";
    }

    /** Alvos do registro na ordem do modelo (uma chave por elemento). Fonte única do JSON e do módulo Python. */
    private static Map<String, Map<String, Object>> targets(List<Step> steps) {
        Map<String, Map<String, Object>> targets = new LinkedHashMap<>();
        for (Step s : steps) if (s.key() != null && s.target() != null && !targets.containsKey(s.key())) targets.put(s.key(), s.target());
        return targets;
    }

    private static String selectors(Map<String, Map<String, Object>> targets) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("schema", "nekkora-rpa/selectors");
        doc.put("version", "1.1.0");
        doc.put("system", CodePlan.SYSTEM);
        doc.put("targets", targets);
        return Pretty.json(doc) + "\n";
    }

    /**
     * Equivalência .py ↔ JSON: SHA-256 do JSON canônico (chaves ordenadas, sem espaços, UTF-8 — o {@code json.dumps(...,
     * sort_keys=True, ensure_ascii=False, separators=(",", ":"))} do Python) dos alvos SEM a procedência. O Studio recalcula a
     * partir de {@code TARGETS} e do {@code selectors.json} e compara com este valor.
     */
    static String equivalence(Map<String, Map<String, Object>> targets) {
        Map<String, Object> clean = new LinkedHashMap<>();
        targets.forEach((k, v) -> {
            Map<String, Object> t = new LinkedHashMap<>(v);
            t.remove("provenance");
            clean.put(k, t);
        });
        return "sha256:" + CodegenConfig.sha256(canonical(clean));
    }

    static String canonical(Object v) {
        StringBuilder b = new StringBuilder();
        canonical(b, v);
        return b.toString();
    }

    private static void canonical(StringBuilder b, Object v) {
        if (v == null) {
            b.append("null");
        } else if (v instanceof String s) {
            b.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> b.append("\\\"");
                    case '\\' -> b.append("\\\\");
                    case '\n' -> b.append("\\n");
                    case '\r' -> b.append("\\r");
                    case '\t' -> b.append("\\t");
                    case '\b' -> b.append("\\b");
                    case '\f' -> b.append("\\f");
                    default -> {
                        if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                        else b.append(c);
                    }
                }
            }
            b.append('"');
        } else if (v instanceof Boolean || v instanceof Integer || v instanceof Long) {
            b.append(v);
        } else if (v instanceof Map<?, ?> m) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            m.forEach((k, x) -> sorted.put(String.valueOf(k), x));
            b.append('{');
            int i = 0;
            for (Map.Entry<String, Object> e : sorted.entrySet()) {
                if (i++ > 0) b.append(',');
                canonical(b, e.getKey());
                b.append(':');
                canonical(b, e.getValue());
            }
            b.append('}');
        } else if (v instanceof List<?> l) {
            b.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) b.append(',');
                canonical(b, l.get(i));
            }
            b.append(']');
        } else {
            throw new IllegalArgumentException("tipo não suportado no JSON canônico");
        }
    }

    private static String toml(SingleConfig cfg) {
        StringBuilder b = new StringBuilder();
        b.append("# GENERATED by Nekkora Studio. No credentials and no values: secrets come from NK_SECRET_<NAME>.\n");
        b.append("[runtime]\n");
        b.append("environment = ").append(Py.str(cfg.environment())).append('\n');
        b.append("allowed_origins = [").append(String.join(", ", cfg.allowedOrigins().stream().map(Py::str).toList())).append("]\n");
        b.append("base_url = ").append(Py.str(cfg.startUrl())).append('\n');
        b.append("process_name = ").append(Py.str(cfg.processName())).append("\n\n");
        b.append("[timeouts]\naction_ms = 8000\nnavigation_ms = 30000\ntransaction_ms = 120000\n\n");
        b.append("[retry]\nmax_attempts = 2\ninitial_backoff_ms = 200\nmax_backoff_ms = 1000\n\n");
        b.append("[batch]\nmax_transaction_attempts = 1\nmax_consecutive_technical_failures = 1\n\n");
        b.append("[browser]\nchannel = \"chrome\"\nheadless = false\naccept_downloads = false\n\n");
        b.append("[paths]\nselectors = \"../selectors.json\"\n");
        return b.toString();
    }

    /** Ordem natural dos nomes importados (a do isort do ruff): "Tela2" antes de "Tela10" e de "TelaAcesso". */
    static int natural(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char x = a.charAt(i);
            char y = b.charAt(j);
            if (Character.isDigit(x) && Character.isDigit(y)) {
                int ei = i;
                int ej = j;
                while (ei < a.length() && Character.isDigit(a.charAt(ei))) ei++;
                while (ej < b.length() && Character.isDigit(b.charAt(ej))) ej++;
                int c = new java.math.BigInteger(a.substring(i, ei)).compareTo(new java.math.BigInteger(b.substring(j, ej)));
                if (c != 0) return c;
                i = ei;
                j = ej;
            } else {
                if (x != y) return Character.compare(x, y);
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    // ------------------------------------------------------------------------------------------------ main.py
    static String command(SingleConfig cfg) {
        if (cfg.list()) return "python main.py --input <list.csv|list.xlsx> --spreadsheet <output.xlsx> --output <folder>";
        return "python main.py --output <folder>" + (cfg.inputs().isEmpty() ? "" : " --input <inputs.json>");
    }

    /** Linha de comando (vai ao manifesto: o Studio escolhe os nomes das opções por ele; os anteriores continuam aceitos). */
    static Map<String, Object> cli(SingleConfig cfg) {
        Map<String, Object> c = new LinkedHashMap<>();
        Map<String, String> options = new LinkedHashMap<>();
        Map<String, String> legacy = new LinkedHashMap<>();
        if (cfg.list() || !cfg.inputs().isEmpty()) {
            options.put("input", "--input");
            legacy.put("--entrada", "--input");
        }
        if (cfg.list()) {
            options.put("spreadsheet", "--spreadsheet");
            legacy.put("--planilha", "--spreadsheet");
        }
        options.put("output", "--output");
        legacy.put("--saida", "--output");
        c.put("options", options);
        c.put("legacyAliases", legacy);
        return c;
    }

    private static String main(SingleConfig cfg, CodePlan code) {
        boolean inputs = !cfg.inputs().isEmpty();
        String processClass = code.process().className();
        StringBuilder b = new StringBuilder(SemanticEmitter.HEADER);
        if (cfg.list()) {
            b.append("\"\"\"Runs the process over a list.\n\nUsage: ").append(command(cfg)).append("\n    [--channel chrome|chromium] [--headless] [--console]\n\n");
            b.append("The list has one column per input: ").append(String.join(", ", cfg.inputs())).append(".\n");
            b.append("The spreadsheet is written only if ALL items succeed.\n");
            b.append("The previous option names (--entrada, --planilha, --saida) are still accepted.\n\"\"\"\n\n");
        } else {
            b.append("\"\"\"Single run.\n\nUsage: ").append(command(cfg)).append("\n    [--channel chrome|chromium] [--headless] [--console]\n\n");
            b.append("The previous option names (").append(inputs ? "--entrada, --saida" : "--saida").append(") are still accepted.\n\"\"\"\n\n");
        }
        b.append("from __future__ import annotations\n\nimport argparse\nimport json\nimport sys\nfrom pathlib import Path\n\n");
        if (cfg.list()) {
            b.append("from nekkora_rpa import Engine, EnvSecretProvider, load_config\n");
            b.append("from nekkora_rpa.errors import NekkoraRpaError\n");
            b.append("from nekkora_rpa.tabular import Column, TabularInputProvider, TabularSchema, read_tabular\n");
            b.append("from nekkora_rpa.xlsx import ColumnKind, OutputColumn, OverwritePolicy, XlsxResultSink, XlsxSchema\n\n");
            b.append("from automation.process import INPUT_COLUMNS, ").append(processClass).append("\n\n");
        } else {
            b.append("from nekkora_rpa import Engine, EnvSecretProvider, SingleInputProvider, TransactionItem, load_config\n");
            b.append("from nekkora_rpa.data import JsonDirectoryResultSink\n\n");
            b.append("from automation.process import ").append(processClass).append("\n\n");
        }
        b.append("HERE = Path(__file__).resolve().parent\n");
        b.append("EXIT_CODES = {\"COMPLETED\": 0, \"COMPLETED_WITH_FAILURES\": 1, \"STOPPED\": 2, \"INIT_FAILED\": 3, \"CANCELLED\": 4}\n");
        if (cfg.list()) {
            b.append("INPUT_LIST = TabularSchema(tuple(Column(name) for name in INPUT_COLUMNS), reject_extra_columns=True)\n");
            b.append("SPREADSHEET = XlsxSchema(\n    (\n");
            for (SingleConfig.Column c : cfg.output().columns()) {
                String kind = switch (c.kind()) {
                    case "iso_date" -> "ColumnKind.ISO_DATE";
                    case "url" -> "ColumnKind.URL";
                    default -> "ColumnKind.TEXT";
                };
                b.append("        OutputColumn(").append(Py.str(c.header())).append(", ").append(Py.str(c.field())).append(", ").append(kind).append("),\n");
            }
            b.append("    ),\n    key_field=").append(Py.str(cfg.output().keyField())).append(",\n    sheet=").append(Py.str(cfg.output().sheet())).append(",\n)\n");
            b.append("\"\"\"Output spreadsheet: the column headers are the contract of the spreadsheet; the fields are the row keys.\"\"\"\n");
        }
        if (!cfg.list() && inputs) {
            b.append("\n\ndef read_inputs(path: Path) -> dict[str, object]:\n");
            b.append("    \"\"\"JSON object with the inputs of the run; the values are never printed or logged.\"\"\"\n");
            b.append("    data: object = json.loads(path.read_text(encoding=\"utf-8\"))\n");
            b.append("    if not isinstance(data, dict):\n        raise SystemExit(\"input: the file must be a JSON object\")\n");
            b.append("    return {str(key): value for key, value in data.items()}\n");
        }
        b.append("\n\ndef main(argv: list[str] | None = None) -> int:\n");
        b.append("    \"\"\"Reads the arguments, runs the process and prints the summary (no values); returns the exit code.\"\"\"\n");
        b.append("    parser = argparse.ArgumentParser(description=").append(Py.str("Generated process: " + cfg.processName())).append(")\n");
        if (cfg.list()) {
            b.append("    parser.add_argument(\"--input\", \"--entrada\", dest=\"input\", required=True, type=Path, help=\"CSV or XLSX list (one row per item)\")\n");
            b.append("    parser.add_argument(\n        \"--spreadsheet\", \"--planilha\", dest=\"spreadsheet\", required=True, type=Path, help=\"output .xlsx spreadsheet\"\n    )\n");
            b.append("    parser.add_argument(\"--output\", \"--saida\", dest=\"output\", required=True, type=Path, help=\"run folder (events, checkpoint)\")\n");
        } else {
            b.append("    parser.add_argument(\n        \"--output\", \"--saida\", dest=\"output\", required=True, type=Path, help=\"run folder (results, events, checkpoint)\"\n    )\n");
            if (inputs) b.append("    parser.add_argument(\"--input\", \"--entrada\", dest=\"input\", required=True, type=Path, help=\"JSON object with the inputs\")\n");
        }
        b.append("    parser.add_argument(\"--channel\", choices=[\"chromium\", \"chrome\"])\n");
        b.append("    parser.add_argument(\"--headless\", action=\"store_true\")\n    parser.add_argument(\"--console\", action=\"store_true\")\n");
        b.append("    args = parser.parse_args(argv)\n");
        b.append("    run_folder = args.output.resolve()\n    run_folder.mkdir(parents=True, exist_ok=True)\n");
        b.append("    overrides: dict[str, object] = {}\n    if args.channel:\n        overrides[\"browser.channel\"] = args.channel\n");
        b.append("    if args.headless:\n        overrides[\"browser.headless\"] = True\n");
        b.append("    config = load_config(HERE / ").append(Py.str(CONFIG_FILE.substring(0, CONFIG_FILE.indexOf('/')))).append(" / ")
                .append(Py.str(CONFIG_FILE.substring(CONFIG_FILE.indexOf('/') + 1))).append(", overrides)\n");
        String resultKeys;
        if (cfg.list()) {
            b.append("    try:\n        input_list = read_tabular(args.input, INPUT_LIST)\n");
            b.append("        sink = XlsxResultSink(\n            args.spreadsheet.resolve(),\n            SPREADSHEET,\n            state_dir=run_folder / \"execucao\",\n");
            b.append("            overwrite=OverwritePolicy.REPLACE,\n            expected_rows=len(input_list.rows),\n        )\n");
            b.append("    except NekkoraRpaError as error:\n");
            b.append("        print(json.dumps({\"status\": \"INPUT_INVALID\", \"code\": error.code.value, \"detail\": error.detail}))\n        return 5\n");
            b.append("    process = ").append(processClass).append("()\n");
            b.append("    engine = Engine(\n        process,\n        TabularInputProvider(input_list),\n        sink,\n        config,\n");
            resultKeys = "(\"execution_id\", \"status\", \"stop_code\", \"stop_detail\", \"counts\", \"output\")";
        } else {
            b.append("    process = ").append(processClass).append("()\n");
            b.append("    item = TransactionItem(\"execucao-unica\", ").append(inputs ? "read_inputs(args.input)" : "{}").append(")\n");
            b.append("    engine = Engine(\n        process,\n        SingleInputProvider(item),\n        JsonDirectoryResultSink(run_folder / \"resultados\"),\n        config,\n");
            resultKeys = "(\"execution_id\", \"status\", \"stop_code\", \"stop_detail\", \"counts\")";
        }
        b.append("        secrets=EnvSecretProvider(process.required_secrets),\n        run_dir=run_folder / \"execucao\",\n        console=args.console,\n    )\n");
        b.append("    summary = engine.run()\n");
        b.append("    fields = summary.to_dict()\n");
        b.append("    published = ").append(resultKeys).append('\n');
        b.append("    print(json.dumps({key: fields[key] for key in published}))\n");
        b.append("    return EXIT_CODES[summary.status.value]\n\n\n");
        b.append("if __name__ == \"__main__\":\n    sys.exit(main())\n");
        return b.toString();
    }

    // ------------------------------------------------------------------------------------------------ README.md
    private static String readme(SingleConfig cfg, List<Step> steps, CodePlan code) {
        Set<String> secrets = new TreeSet<>();
        for (Step s : steps) if (s.credential() != null) secrets.add(s.credential());
        StringBuilder b = new StringBuilder();
        b.append("<!-- GENERATED by Nekkora Studio (\"Desenvolver automação\"). -->\n# ").append(code.process().className()).append("\n\n");
        b.append("**What it does.** ").append(code.process().summary()).append(' ');
        if (cfg.list()) {
            b.append("Each row of the input list (CSV or XLSX with the columns ").append(String.join(", ", cfg.inputs().stream().map(x -> "`" + x + "`").toList()))
                    .append(") becomes a transaction; the output `.xlsx` spreadsheet has the columns ")
                    .append(String.join(", ", cfg.output().columns().stream().map(c -> "`" + c.header() + "`").toList()))
                    .append(" and is written only if ALL items succeed (all or nothing, by the nekkora_rpa `XlsxResultSink`).\n\n");
        } else {
            b.append(cfg.inputs().isEmpty() ? "No inputs.\n\n"
                    : "Inputs (JSON file in `--input`): " + String.join(", ", cfg.inputs().stream().map(x -> "`" + x + "`").toList()) + ".\n\n");
        }
        if (!secrets.isEmpty()) {
            b.append("**Secrets** (environment variables, never in the project): ")
                    .append(String.join(", ", secrets.stream().map(x -> "`NK_SECRET_" + x.toUpperCase().replace('.', '_').replace('-', '_') + "`").toList()))
                    .append(".\n\n");
        }
        b.append("Names in the code are in English. Page labels, list columns, spreadsheet headers, secret names and rejection codes\n");
        b.append("stay as they are: they are data and contracts (the mapping is in `manifest.json` → `naming`).\n\n");
        b.append("## How it is organized\n\n");
        b.append("| File | Content |\n|---|---|\n");
        b.append("| `").append(CodePlan.PAGES_FILE).append("` | one class per screen: ").append(String.join(", ", code.pages().stream().map(p -> "`" + p.className() + "`").toList()))
                .append(" |\n");
        b.append("| `").append(CodePlan.PROCESS_FILE).append("` | `").append(code.process().className()).append("`: the order of the screens")
                .append(cfg.spreadsheet() ? " and the spreadsheet row" : "").append(" |\n");
        b.append("| `").append(CodePlan.SELECTORS_FILE).append("` | `SelectorKey` (one key per element) and how each element is located |\n");
        b.append("| `main.py` | command line |\n");
        b.append("| `selectors.json` | the same targets, read by the nekkora_rpa runtime (generated from the same plan; never edit by hand) |\n");
        b.append("| `manifest.json` | revisions, hashes, `step_id` ↔ Studio step map, names and keys |\n\n");
        b.append("## Screens and what each method does\n\n");
        for (CodePlan.Page p : code.pages()) {
            b.append("- **`").append(p.className()).append("`** — ").append(p.summary()).append('\n');
            for (CodePlan.Method m : p.methods()) b.append("  - `").append(m.name()).append("()` — ").append(m.summary()).append('\n');
        }
        b.append("\n## How each element is located\n\n| Key | Strategy | Reading |\n|---|---|---|\n");
        Set<String> shown = new LinkedHashSet<>();
        for (CodePlan.Selector sel : code.selectors()) {
            Step s = steps.stream().filter(x -> sel.key().equals(x.key()) && x.target() != null).findFirst().orElse(null);
            if (s == null || !shown.add(sel.key())) continue;
            b.append("| `SelectorKey.").append(sel.member()).append("` | ").append(LocatorPolicy.describe(s.target())).append(" | ")
                    .append(LocatorPolicy.kind(s.target()).label()).append(" |\n");
        }
        b.append("\nnekkora_rpa acts only if the locator finds EXACTLY one element with the expected identity; none, or more than one, is an "
                + "error (it never picks the first one).\n\n## Run\n\n");
        b.append(InstallHint.readmeBlockEnglish(requirement(cfg), command(cfg) + " --console"));
        b.append(cfg.list() ? "JSONL events without values, checkpoint and summary in `<folder>/execucao/`.\n"
                : "Output: `resultados/` (the outcome, with the readings) and `execucao/` (JSONL events without values, checkpoint, summary).\n");
        b.append("Generating code is not running it: run only in an authorized environment (").append(cfg.environment()).append(").\n");
        return b.toString();
    }

    // ------------------------------------------------------------------------------------------------ manifest.json
    private static String manifest(Automation a, GuidedPlan plan, SingleConfig cfg, List<Step> steps, CodePlan code, CodeNames names,
                                   Map<String, Map<String, Object>> targets, Map<String, String> files) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schema", "nekkora-studio/codegen-manifest");
        m.put("version", MANIFEST_VERSION);
        m.put("generator", cfg.modern() ? SingleGenerator.GENERATOR_LIST : SingleGenerator.GENERATOR);
        m.put("framework", requirement(cfg));
        m.put("execution", cfg.execution());
        m.put("language", "en");
        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("schema", LAYOUT);
        layout.put("package", CodePlan.PACKAGE);
        layout.put("modules", List.of(CodePlan.INIT_FILE, CodePlan.SELECTORS_FILE, CodePlan.PAGES_FILE, CodePlan.PROCESS_FILE));
        layout.put("runner", "main.py");
        layout.put("config", CONFIG_FILE);
        m.put("layout", layout);
        m.put("automationId", a.id().value().toString());
        m.put("modelVersion", a.modelVersion());
        m.put("automationRevision", a.revision());
        m.put("planRevision", plan.planRevision());
        m.put("configRevision", cfg.revision());
        Map<String, Object> pdd = new LinkedHashMap<>();
        pdd.put("gate", cfg.pdd().gate());
        pdd.put("processRevision", cfg.pdd().processRevision());
        pdd.put("documentRevision", cfg.pdd().documentRevision());
        pdd.put("specRevision", cfg.pdd().specRevision());
        m.put("pdd", pdd);
        m.put("command", command(cfg));
        m.put("cli", cli(cfg));
        if (cfg.modern() && cfg.spreadsheet()) {
            List<Object> cols = new ArrayList<>();
            for (SingleConfig.Column c : cfg.output().columns()) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("header", c.header());
                x.put("field", c.field());
                x.put("kind", c.kind());
                x.put("stepId", c.step());
                x.put("fallbackStepId", c.fallbackStep());
                cols.add(x);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("format", "xlsx");
            out.put("sheet", cfg.output().sheet());
            out.put("keyField", cfg.output().keyField());
            out.put("columns", cols);
            m.put("output", out);
        }
        Map<String, String> traceOf = new LinkedHashMap<>();
        code.traces().forEach(t -> traceOf.put(t.stepId(), t.traceId()));
        Map<String, CodePlan.Retry> retryOf = new LinkedHashMap<>();
        for (CodePlan.Page p : code.pages()) for (CodePlan.Method mm : p.methods()) for (CodePlan.Op op : mm.ops()) {
            if (op.retry() != null) retryOf.put(op.step().step().id().value().toString(), op.retry());
        }
        List<Object> list = new ArrayList<>();
        for (Step s : steps) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("stepId", s.step().id().value().toString());
            e.put("traceId", traceOf.get(s.step().id().value().toString()));
            e.put("page", s.group().page());
            e.put("method", s.group().method());
            e.put("action", s.step().actionType().name());
            e.put("key", s.key());
            e.put("input", s.input());
            e.put("credential", s.credential() != null);
            e.put("output", s.output());
            e.put("effect", s.effect() == null ? null : s.effect().effect());
            e.put("postcondition", s.effect() == null ? null : s.effect().postcondition());
            // CQ-04: repetição depois de resultado incerto (idempotent=True) só com SAFE; o motivo fica registrado
            e.put("retry", retryOf.containsKey(s.step().id().value().toString()) ? retryOf.get(s.step().id().value().toString()).name() : null);
            if (cfg.modern()) {
                e.put("read", s.read() == null ? null : s.read().kind());
                e.put("optional", s.read() != null && s.read().optional());
                e.put("expected", s.read() == null ? null : s.read().expected());
            }
            e.put("capability", capability(s, cfg.modern()));
            e.put("decisionActor", s.step().targetDecision().map(d -> d.actor().name()).orElse(null));
            list.add(e);
        }
        m.put("steps", list);
        m.put("trace", trace(code));
        m.put("architecture", architecture(code));
        m.put("naming", naming(cfg, code, names));
        m.put("selectors", selectorMap(code, targets));
        m.put("locators", locators(steps));
        Map<String, Object> hashes = new LinkedHashMap<>();
        for (Map.Entry<String, String> f : files.entrySet()) {
            if (!f.getKey().equals("manifest.json")) hashes.put(f.getKey(), "sha256:" + CodegenConfig.sha256(f.getValue()));
        }
        m.put("files", hashes);
        m.put("exclusions", List.of("INPUT_VALUES", "CREDENTIAL_VALUES", "LITERAL_VALUES", "HAND_WRITTEN_SELECTORS", "POSITIONAL_LOCATORS", "URL_QUERIES",
                "STUDIO_STEP_IDS_IN_CODE"));
        return Pretty.json(m) + "\n";
    }

    private static Map<String, Object> trace(CodePlan code) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("schema", TRACE_SCHEMA);
        t.put("version", "1.1.0");
        List<Object> mappings = new ArrayList<>();
        for (CodePlan.Trace x : code.traces()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("traceId", x.traceId());
            e.put("studioStepId", x.stepId());
            e.put("action", x.action());
            e.put("targetKey", x.targetKey());
            e.put("pythonSymbol", x.symbol());
            // rodada 3: os ids que esta etapa já teve (ex.: em português) — correlação de eventos antigos, nunca reaproveitados
            if (!x.previous().isEmpty()) e.put("previousTraceIds", x.previous());
            mappings.add(e);
        }
        t.put("mappings", mappings);
        Map<String, Object> start = new LinkedHashMap<>();
        start.put("traceId", CodePlan.START_TRACE);
        start.put("meaning", "open the start address (no Studio step)");
        start.put("previousTraceIds", CodePlan.PREVIOUS_START_TRACES);
        t.put("synthetic", List.of(start));
        return t;
    }

    private static Map<String, Object> architecture(CodePlan code) {
        Map<String, Object> a = new LinkedHashMap<>();
        List<Object> pages = new ArrayList<>();
        for (CodePlan.Page p : code.pages()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("class", p.className());
            x.put("prefix", p.prefix());
            x.put("identityKey", p.identity() == null ? null : "SelectorKey." + p.identity());
            x.put("nameSource", p.nameSource().name());
            x.put("summary", p.summary());
            x.put("boundary", p.boundary());
            List<Object> methods = new ArrayList<>();
            for (CodePlan.Method m : p.methods()) {
                Map<String, Object> y = new LinkedHashMap<>();
                y.put("name", m.name());
                y.put("nameSource", m.nameSource().name());
                y.put("summary", m.summary());
                y.put("parameters", m.params().stream().map(CodePlan.Param::name).toList());
                y.put("returns", m.result().type());
                y.put("traceIds", m.ops().stream().map(CodePlan.Op::traceId).toList());
                methods.add(y);
            }
            x.put("methods", methods);
            pages.add(x);
        }
        a.put("pages", pages);
        List<Object> models = new ArrayList<>();
        for (CodePlan.Model md : code.models()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("name", md.name());
            x.put("readBy", md.page() + "." + md.method());
            x.put("fields", md.fields().stream().map(f -> f.name() + (f.optional() ? "?" : "")).toList());
            models.add(x);
        }
        a.put("models", models);
        a.put("process", code.process().className());
        return a;
    }

    /**
     * Mapa de nomes (rodada 3, Q11): de onde cada identificador inglês saiu — o texto do PDD, a variável do modelo ou o rótulo
     * da tela — e as palavras sem tradução no glossário (ficaram como estão: pendência para a pessoa, nunca invenção).
     */
    private static Map<String, Object> naming(SingleConfig cfg, CodePlan code, CodeNames names) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("glossary", EnglishNames.GLOSSARY);
        List<Object> untranslated = new ArrayList<>();
        Map<String, Object> process = new LinkedHashMap<>();
        process.put("from", cfg.processName());
        process.put("python", code.process().className());
        n.put("process", process);
        for (String w : EnglishNames.identifier(cfg.processName()).untranslated()) untranslated.add(pending(w, "process", code.process().className()));
        List<Object> pages = new ArrayList<>();
        for (CodePlan.Page p : code.pages()) {
            CodeNames.PageInfo info = names.page(p.className());
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("python", p.className());
            x.put("from", info.nameFrom());
            x.put("source", p.nameSource().name());
            pages.add(x);
            for (String w : info.untranslated()) untranslated.add(pending(w, "page", p.className()));
            for (CodePlan.Method m : p.methods()) {
                CodeNames.MethodInfo mi = names.method(p.className(), m.name());
                for (String w : mi.untranslated()) untranslated.add(pending(w, "method", p.className() + "." + m.name()));
            }
        }
        n.put("pages", pages);
        List<Object> ids = new ArrayList<>();
        code.identifiers().forEach((model, py) -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("model", model);
            x.put("python", py);
            x.put("role", cfg.inputs().contains(model) ? "input" : "output");
            ids.add(x);
            for (String w : EnglishNames.identifier(model).untranslated()) untranslated.add(pending(w, "variable", py));
        });
        n.put("identifiers", ids);
        if (cfg.spreadsheet()) {
            List<Object> cols = new ArrayList<>();
            for (SingleConfig.Column c : cfg.output().columns()) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("header", c.header());
                x.put("field", c.field());
                cols.add(x);
                for (String w : EnglishNames.noun(c.header()).untranslated()) untranslated.add(pending(w, "column", c.field()));
            }
            n.put("columns", cols);
        }
        for (CodePlan.Selector s : code.selectors()) {
            String slug = s.key().substring(s.key().lastIndexOf('.') + 1);
            for (String w : EnglishNames.noun(slug).untranslated()) untranslated.add(pending(w, "selector", "SelectorKey." + s.member()));
        }
        n.put("untranslated", untranslated);
        return n;
    }

    private static Map<String, Object> pending(String word, String kind, String where) {
        Map<String, Object> x = new LinkedHashMap<>();
        x.put("word", word);
        x.put("kind", kind);
        x.put("python", where);
        return x;
    }

    /** Chaves tipadas (Q10): membro ↔ chave canônica ↔ etapas (traceId e StepId), e a equivalência .py ↔ JSON. */
    private static Map<String, Object> selectorMap(CodePlan code, Map<String, Map<String, Object>> targets) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("module", CodePlan.SELECTORS_FILE);
        s.put("enum", "SelectorKey");
        s.put("registry", "selectors.json");
        s.put("system", CodePlan.SYSTEM);
        s.put("equivalence", equivalence(targets));
        List<Object> keys = new ArrayList<>();
        for (CodePlan.Selector x : code.selectors()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("member", x.member());
            e.put("key", x.key());
            e.put("traceIds", x.traceIds());
            e.put("studioStepIds", x.stepIds());
            keys.add(e);
        }
        s.put("keys", keys);
        return s;
    }

    private static List<Object> locators(List<Step> steps) {
        List<Object> out = new ArrayList<>();
        for (Step s : steps) {
            if (s.key() == null || s.target() == null) continue;
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("key", s.key());
            x.put("kind", LocatorPolicy.kind(s.target()).name());
            x.put("description", LocatorPolicy.describe(s.target()));
            out.add(x);
        }
        return out;
    }

    /** Capacidade do framework usada pela etapa (o mesmo id do catálogo do AI Engine). */
    static String capability(Step s, boolean modern) {
        if (!modern) return capability(s.step().actionType());
        return switch (s.step().actionType()) {
            case NAVIGATE -> "actions.navigate";
            case FILL -> "actions.fill";
            case CLICK -> "actions.click";
            case WAIT_FOR -> "actions.wait_visible";
            case PRESS_KEY -> "actions.press";
            case OBSERVE -> s.read() != null && "address".equals(s.read().kind()) ? "actions.current_address" : "actions.read_items";
            default -> null;
        };
    }

    static String capability(ActionType t) {
        return switch (t) {
            case NAVIGATE -> "actions.navigate";
            case FILL -> "actions.fill";
            case CLICK -> "actions.click";
            case WAIT_FOR -> "actions.wait_visible";
            case OBSERVE -> "actions.read_text";
            default -> null;
        };
    }
}
