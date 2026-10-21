package com.nekkora.studio.codegen;

import com.nekkora.studio.codegen.SingleGenerator.Step;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.guided.GuidedPlan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Arquivos do projeto de EXECUÇÃO ÚNICA (1.1.0, framework 0.2.0) e da LISTA → PLANILHA (1.2.0, 0.3.0), determinísticos e
 * só com o que o pré-voo aprovou. NEKKORA-CODEGEN-QUALITY-001: Pages e processo saem do {@link CodePlan} pelo
 * {@link SemanticEmitter} (nomes do PDD, {@code step_id} legível, docstrings); o manifesto 1.3.0 descreve a arquitetura,
 * o mapa traceId ↔ StepId e como cada alvo é localizado; o README explica a automação. Entradas validadas antes de
 * qualquer ação; segredos só pelo provedor; planilha só pelo {@code XlsxResultSink} (tudo ou nada).
 */
final class SingleEmitter {

    static final String MANIFEST_VERSION = "1.3.0";
    static final String TRACE_SCHEMA = "nekkora-studio/trace-map";

    private SingleEmitter() {}

    static Map<String, String> emit(Automation a, GuidedPlan plan, SingleConfig cfg, List<Step> steps, CodeNames names) {
        Map<String, String> files = new TreeMap<>();
        String processClass = processClass(cfg.processName());
        CodePlan code = CodePlan.build(cfg, steps, names, processClass);
        files.put("selectors.json", selectors(steps));
        files.put("config/execucao.toml", toml(cfg));
        files.put("paginas.py", PyWrap.wrap(SemanticEmitter.pages(code)));
        files.put("processo.py", PyWrap.wrap(SemanticEmitter.process(code, cfg, steps)));
        files.put("main.py", PyWrap.wrap(main(cfg, processClass)));
        files.put("requirements.txt", InstallHint.requirementsTxt("CODEGEN-QUALITY-001", requirement(cfg)));
        files.put("README.md", readme(cfg, steps, code));
        files.put("manifest.json", manifest(a, plan, cfg, steps, code, files));
        return files;
    }

    static String requirement(SingleConfig cfg) {
        return cfg.modern() ? SingleGenerator.FRAMEWORK_REQUIREMENT_LIST : SingleGenerator.FRAMEWORK_REQUIREMENT;
    }

    /** Classe do processo pelo nome de negócio ({@code biografias_da_wikipedia} → {@code BiografiasDaWikipedia}). */
    static String processClass(String processName) {
        String c = CodeNaming.pascal(processName);
        return c.isEmpty() || !Character.isLetter(c.charAt(0)) ? "ProcessoGerado" : c.length() > 60 ? c.substring(0, 60) : c;
    }

    private static String selectors(List<Step> steps) {
        Map<String, Object> targets = new LinkedHashMap<>();
        for (Step s : steps) if (s.key() != null && s.target() != null && !targets.containsKey(s.key())) targets.put(s.key(), s.target());
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("schema", "nekkora-rpa/selectors");
        doc.put("version", "1.1.0");
        doc.put("system", PythonGenerator.SYSTEM);
        doc.put("targets", targets);
        return Pretty.json(doc) + "\n";
    }

    private static String toml(SingleConfig cfg) {
        StringBuilder b = new StringBuilder();
        b.append("# GERADO pelo Nekkora Studio. Sem credenciais nem valores: segredos vêm de NK_SECRET_<NOME>.\n");
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
        if (cfg.list()) return "python main.py --entrada <lista.csv|lista.xlsx> --planilha <saida.xlsx> --saida <pasta>";
        return "python main.py --saida <pasta>" + (cfg.inputs().isEmpty() ? "" : " --entrada <entrada.json>");
    }

    private static String main(SingleConfig cfg, String processClass) {
        boolean inputs = !cfg.inputs().isEmpty();
        StringBuilder b = new StringBuilder(SemanticEmitter.HEADER);
        if (cfg.list()) {
            b.append("\"\"\"Execução do processo em lista.\n\nUso: ").append(command(cfg)).append("\n    [--channel chrome|chromium] [--headless] [--console]\n\n");
            b.append("A lista tem uma coluna por entrada: ").append(String.join(", ", cfg.inputs())).append(".\n");
            b.append("A planilha só é gravada se TODOS os itens derem certo.\n\"\"\"\n\n");
        } else {
            b.append("\"\"\"Execução única.\n\nUso: ").append(command(cfg)).append("\n    [--channel chrome|chromium] [--headless] [--console]\n\"\"\"\n\n");
        }
        b.append("from __future__ import annotations\n\nimport argparse\nimport json\nimport sys\nfrom pathlib import Path\n\n");
        if (cfg.list()) {
            b.append("from nekkora_rpa import Engine, EnvSecretProvider, load_config\n");
            b.append("from nekkora_rpa.errors import NekkoraRpaError\n");
            b.append("from nekkora_rpa.tabular import Column, TabularInputProvider, TabularSchema, read_tabular\n");
            b.append("from nekkora_rpa.xlsx import ColumnKind, OutputColumn, OverwritePolicy, XlsxResultSink, XlsxSchema\n\n");
            b.append("from processo import ENTRADAS, ").append(processClass).append("\n\n");
        } else {
            b.append("from nekkora_rpa import Engine, EnvSecretProvider, SingleInputProvider, TransactionItem, load_config\n");
            b.append("from nekkora_rpa.data import JsonDirectoryResultSink\n\n");
            b.append("from processo import ").append(processClass).append("\n\n");
        }
        b.append("AQUI = Path(__file__).resolve().parent\n");
        b.append("CODIGO_DE_SAIDA = {\"COMPLETED\": 0, \"COMPLETED_WITH_FAILURES\": 1, \"STOPPED\": 2, \"INIT_FAILED\": 3, \"CANCELLED\": 4}\n");
        if (cfg.list()) {
            b.append("LISTA = TabularSchema(tuple(Column(nome) for nome in ENTRADAS), reject_extra_columns=True)\n");
            b.append("PLANILHA = XlsxSchema(\n    (\n");
            for (SingleConfig.Column c : cfg.output().columns()) {
                String kind = switch (c.kind()) {
                    case "iso_date" -> "ColumnKind.ISO_DATE";
                    case "url" -> "ColumnKind.URL";
                    default -> "ColumnKind.TEXT";
                };
                b.append("        OutputColumn(").append(Py.str(c.header())).append(", ").append(Py.str(c.field())).append(", ").append(kind).append("),\n");
            }
            b.append("    ),\n    key_field=").append(Py.str(cfg.output().keyField())).append(",\n    sheet=").append(Py.str(cfg.output().sheet())).append(",\n)\n");
        }
        if (!cfg.list() && inputs) {
            b.append("\n\ndef ler_entrada(caminho: Path) -> dict[str, object]:\n");
            b.append("    \"\"\"Objeto JSON com as entradas da execução; os valores nunca são impressos nem registrados.\"\"\"\n");
            b.append("    dados: object = json.loads(caminho.read_text(encoding=\"utf-8\"))\n");
            b.append("    if not isinstance(dados, dict):\n        raise SystemExit(\"entrada: o arquivo precisa ser um objeto JSON\")\n");
            b.append("    return {str(chave): valor for chave, valor in dados.items()}\n");
        }
        b.append("\n\ndef main(argv: list[str] | None = None) -> int:\n");
        b.append("    \"\"\"Lê os argumentos, executa o processo e imprime o resumo (sem valores); devolve o código de saída.\"\"\"\n");
        b.append("    parser = argparse.ArgumentParser(description=").append(Py.str("Processo gerado: " + cfg.processName())).append(")\n");
        if (cfg.list()) {
            b.append("    parser.add_argument(\"--entrada\", required=True, type=Path, help=\"lista CSV ou XLSX (uma linha por item)\")\n");
            b.append("    parser.add_argument(\"--planilha\", required=True, type=Path, help=\"planilha .xlsx de saída\")\n");
            b.append("    parser.add_argument(\"--saida\", required=True, type=Path, help=\"pasta da execução (eventos, checkpoint)\")\n");
        } else {
            b.append("    parser.add_argument(\"--saida\", required=True, type=Path, help=\"pasta da execução (resultados, eventos, checkpoint)\")\n");
            if (inputs) b.append("    parser.add_argument(\"--entrada\", required=True, type=Path, help=\"objeto JSON com as entradas\")\n");
        }
        b.append("    parser.add_argument(\"--channel\", choices=[\"chromium\", \"chrome\"])\n");
        b.append("    parser.add_argument(\"--headless\", action=\"store_true\")\n    parser.add_argument(\"--console\", action=\"store_true\")\n");
        b.append("    args = parser.parse_args(argv)\n");
        b.append("    pasta = args.saida.resolve()\n    pasta.mkdir(parents=True, exist_ok=True)\n");
        b.append("    ajustes: dict[str, object] = {}\n    if args.channel:\n        ajustes[\"browser.channel\"] = args.channel\n");
        b.append("    if args.headless:\n        ajustes[\"browser.headless\"] = True\n");
        b.append("    config = load_config(AQUI / \"config\" / \"execucao.toml\", ajustes)\n");
        String resultKeys;
        if (cfg.list()) {
            b.append("    try:\n        lista = read_tabular(args.entrada, LISTA)\n");
            b.append("        destino = XlsxResultSink(\n            args.planilha.resolve(),\n            PLANILHA,\n            state_dir=pasta / \"execucao\",\n");
            b.append("            overwrite=OverwritePolicy.REPLACE,\n            expected_rows=len(lista.rows),\n        )\n");
            b.append("    except NekkoraRpaError as erro:\n");
            b.append("        print(json.dumps({\"status\": \"INPUT_INVALID\", \"code\": erro.code.value, \"detail\": erro.detail}))\n        return 5\n");
            b.append("    processo = ").append(processClass).append("()\n");
            b.append("    engine = Engine(\n        processo,\n        TabularInputProvider(lista),\n        destino,\n        config,\n");
            resultKeys = "(\"execution_id\", \"status\", \"stop_code\", \"stop_detail\", \"counts\", \"output\")";
        } else {
            b.append("    processo = ").append(processClass).append("()\n");
            b.append("    item = TransactionItem(\"execucao-unica\", ").append(inputs ? "ler_entrada(args.entrada)" : "{}").append(")\n");
            b.append("    engine = Engine(\n        processo,\n        SingleInputProvider(item),\n        JsonDirectoryResultSink(pasta / \"resultados\"),\n        config,\n");
            resultKeys = "(\"execution_id\", \"status\", \"stop_code\", \"stop_detail\", \"counts\")";
        }
        b.append("        secrets=EnvSecretProvider(processo.required_secrets),\n        run_dir=pasta / \"execucao\",\n        console=args.console,\n    )\n");
        b.append("    resumo = engine.run()\n");
        b.append("    campos = resumo.to_dict()\n");
        b.append("    publicados = ").append(resultKeys).append('\n');
        b.append("    print(json.dumps({chave: campos[chave] for chave in publicados}))\n");
        b.append("    return CODIGO_DE_SAIDA[resumo.status.value]\n\n\n");
        b.append("if __name__ == \"__main__\":\n    sys.exit(main())\n");
        return b.toString();
    }

    // ------------------------------------------------------------------------------------------------ README.md
    private static String readme(SingleConfig cfg, List<Step> steps, CodePlan code) {
        Set<String> secrets = new TreeSet<>();
        for (Step s : steps) if (s.credential() != null) secrets.add(s.credential());
        StringBuilder b = new StringBuilder();
        b.append("<!-- GERADO pelo Nekkora Studio (\"Desenvolver automação\"). -->\n# ").append(code.process().className()).append("\n\n");
        b.append("**O que faz.** ").append(code.process().summary()).append(' ');
        if (cfg.list()) {
            b.append("Cada linha da lista de entrada (CSV ou XLSX com as colunas ").append(String.join(", ", cfg.inputs().stream().map(x -> "`" + x + "`").toList()))
                    .append(") vira uma transação; a planilha `.xlsx` de saída tem as colunas ")
                    .append(String.join(", ", cfg.output().columns().stream().map(c -> "`" + c.header() + "`").toList()))
                    .append(" e só é gravada se TODOS os itens derem certo (tudo ou nada, pelo `XlsxResultSink` do nekkora_rpa).\n\n");
        } else {
            b.append(cfg.inputs().isEmpty() ? "Sem entradas.\n\n"
                    : "Entradas (arquivo JSON em `--entrada`): " + String.join(", ", cfg.inputs().stream().map(x -> "`" + x + "`").toList()) + ".\n\n");
        }
        if (!secrets.isEmpty()) {
            b.append("**Segredos** (variáveis de ambiente, nunca no projeto): ")
                    .append(String.join(", ", secrets.stream().map(x -> "`NK_SECRET_" + x.toUpperCase().replace('.', '_').replace('-', '_') + "`").toList()))
                    .append(".\n\n");
        }
        b.append("## Como está organizado\n\n");
        b.append("| Arquivo | Conteúdo |\n|---|---|\n");
        b.append("| `paginas.py` | uma classe por tela: ").append(String.join(", ", code.pages().stream().map(p -> "`" + p.className() + "`").toList())).append(" |\n");
        b.append("| `processo.py` | `").append(code.process().className()).append("`: a ordem das telas").append(cfg.spreadsheet() ? " e a linha da planilha" : "")
                .append(" |\n");
        b.append("| `main.py` | linha de comando |\n| `selectors.json` | como cada elemento é localizado (o código só usa chaves) |\n");
        b.append("| `manifest.json` | revisões, hashes, mapa `step_id` ↔ etapa do Studio |\n\n");
        b.append("## Telas e o que cada método faz\n\n");
        for (CodePlan.Page p : code.pages()) {
            b.append("- **`").append(p.className()).append("`** — ").append(p.summary()).append('\n');
            for (CodePlan.Method m : p.methods()) b.append("  - `").append(m.name()).append("()` — ").append(m.summary()).append('\n');
        }
        b.append("\n## Como cada elemento é localizado\n\n| Chave | Estratégia | Leitura |\n|---|---|---|\n");
        for (Step s : steps) {
            if (s.key() == null || s.target() == null) continue;
            b.append("| `").append(s.key()).append("` | ").append(LocatorPolicy.describe(s.target())).append(" | ").append(LocatorPolicy.kind(s.target()).label())
                    .append(" |\n");
        }
        b.append("\nO nekkora_rpa só age se o localizador encontrar EXATAMENTE um elemento com a identidade esperada; nenhum, ou mais de um, é erro "
                + "(nunca escolhe o primeiro).\n\n## Executar\n\n");
        b.append(InstallHint.readmeBlock(requirement(cfg), command(cfg) + " --console"));
        b.append(cfg.list() ? "Eventos JSONL sem valores, checkpoint e resumo em `<pasta>/execucao/`.\n"
                : "Saída: `resultados/` (o desfecho, com as leituras) e `execucao/` (eventos JSONL sem valores, checkpoint, resumo).\n");
        b.append("Gerar código não é executar: rode somente em ambiente autorizado (").append(cfg.environment()).append(").\n");
        return b.toString();
    }

    // ------------------------------------------------------------------------------------------------ manifest.json
    private static String manifest(Automation a, GuidedPlan plan, SingleConfig cfg, List<Step> steps, CodePlan code, Map<String, String> files) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schema", "nekkora-studio/codegen-manifest");
        m.put("version", MANIFEST_VERSION);
        m.put("generator", cfg.modern() ? SingleGenerator.GENERATOR_LIST : SingleGenerator.GENERATOR);
        m.put("framework", requirement(cfg));
        m.put("execution", cfg.execution());
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
        t.put("version", "1.0.0");
        List<Object> mappings = new ArrayList<>();
        for (CodePlan.Trace x : code.traces()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("traceId", x.traceId());
            e.put("studioStepId", x.stepId());
            e.put("action", x.action());
            e.put("targetKey", x.targetKey());
            e.put("pythonSymbol", x.symbol());
            mappings.add(e);
        }
        t.put("mappings", mappings);
        Map<String, Object> start = new LinkedHashMap<>();
        start.put("traceId", CodePlan.START_TRACE);
        start.put("meaning", "abrir o endereço inicial (sem etapa do Studio)");
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
            x.put("identity", p.identity());
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
