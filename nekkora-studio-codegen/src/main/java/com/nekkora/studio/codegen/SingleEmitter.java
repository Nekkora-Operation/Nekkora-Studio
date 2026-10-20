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

/**
 * Textos do projeto de EXECUÇÃO ÚNICA (determinísticos; só o que o pré-voo aprovou). Uma Page por tela, um método por
 * grupo de etapas consecutivas da tela; o processo chama os métodos na ordem do modelo dentro de UMA transação
 * ({@code SingleInputProvider}); entradas validadas antes de qualquer ação; segredos só pelo provedor; saídas lidas
 * (Observar) entregues ao destino de resultados. O {@code StepId} de cada etapa vai em cada chamada do framework.
 *
 * <p>NEKKORA-E2E-002 (configuração 1.2.0): leituras pelo {@code read_items}/{@code current_address} com a normalização
 * genérica do framework ({@code nekkora_rpa.text}), Enter pelo {@code press}, lista de entrada pelo {@code tabular} e
 * planilha pelo {@code XlsxResultSink} (tudo ou nada). A linha da planilha é montada pela regra declarada (coluna ←
 * leitura, ou a fonte alternativa quando o alvo da principal falta); dado obrigatório vazio rejeita o item.
 */
final class SingleEmitter {

    static final String HEADER = "# GERADO pelo Nekkora Studio (STUDIO-014, \"Desenvolver automação\") do modelo validado e do PDD pronto.\n"
            + "# NÃO EDITE: correções vão para o modelo ou o PDD e o código é gerado de novo.\n"
            + "# Arquivo editado à mão não é sobrescrito.\n";

    private SingleEmitter() {}

    /** Um grupo com as etapas prontas. */
    private record G(SingleConfig.Group group, List<Step> steps) {
        List<String> inputs() {
            List<String> out = new ArrayList<>();
            for (Step s : steps) if (s.input() != null && !out.contains(s.input())) out.add(s.input());
            return out;
        }

        boolean secrets() {
            return steps.stream().anyMatch(s -> s.credential() != null);
        }

        boolean outputs() {
            return steps.stream().anyMatch(s -> s.output() != null);
        }
    }

    static Map<String, String> emit(Automation a, GuidedPlan plan, SingleConfig cfg, List<Step> steps) {
        Map<String, String> files = new TreeMap<>();
        List<G> groups = new ArrayList<>();
        for (SingleConfig.Group g : cfg.groups()) {
            List<Step> mine = steps.stream().filter(s -> s.group().equals(g)).toList();
            groups.add(new G(g, mine));
        }
        files.put("selectors.json", selectors(steps));
        files.put("config/execucao.toml", toml(cfg));
        if (cfg.modern()) {
            files.put("paginas.py", PyWrap.wrap(Modern.pages(groups.stream().map(x -> new Modern.G(x.group(), x.steps())).toList())));
            files.put("processo.py", PyWrap.wrap(Modern.process(cfg, groups.stream().map(x -> new Modern.G(x.group(), x.steps())).toList(), steps)));
            files.put("main.py", PyWrap.wrap(Modern.main(cfg)));
            files.put("requirements.txt", InstallHint.requirementsTxt("NEKKORA-E2E-002", SingleGenerator.FRAMEWORK_REQUIREMENT_LIST));
        } else {
            files.put("paginas.py", PyWrap.wrap(pages(groups)));
            files.put("processo.py", PyWrap.wrap(process(cfg, groups, steps)));
            files.put("main.py", PyWrap.wrap(main(cfg)));
            files.put("requirements.txt", InstallHint.requirementsTxt("STUDIO-014", SingleGenerator.FRAMEWORK_REQUIREMENT));
        }
        files.put("README.md", readme(cfg, steps));
        files.put("manifest.json", manifest(a, plan, cfg, steps, files));
        return files;
    }

    private static String selectors(List<Step> steps) {
        Map<String, Object> targets = new LinkedHashMap<>();
        for (Step s : steps) if (s.key() != null) targets.put(s.key(), s.target());
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("schema", "nekkora-rpa/selectors");
        doc.put("version", "1.1.0");
        doc.put("system", PythonGenerator.SYSTEM);
        doc.put("targets", targets);
        return Pretty.json(doc) + "\n";
    }

    private static String toml(SingleConfig cfg) {
        StringBuilder b = new StringBuilder();
        b.append("# GERADO pelo Nekkora Studio (STUDIO-014). Sem credenciais nem valores: segredos vêm de NK_SECRET_<NOME>.\n");
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

    // ------------------------------------------------------------------------------------------------ paginas.py
    private static String pages(List<G> groups) {
        StringBuilder b = new StringBuilder();
        b.append(HEADER);
        b.append("\"\"\"Pages geradas: uma por tela do processo; intenções da tela por CHAVE do registro de alvos (sem seletores).\"\"\"\n\n");
        b.append("from __future__ import annotations\n\n");
        boolean secrets = groups.stream().anyMatch(G::secrets);
        b.append("from nekkora_rpa.pages import BasePage\n");
        if (secrets) b.append("from nekkora_rpa.security.secrets import SecretProvider\n");
        Map<String, List<G>> byPage = new LinkedHashMap<>();
        for (G g : groups) byPage.computeIfAbsent(g.group().page(), k -> new ArrayList<>()).add(g);
        int n = 0;
        for (Map.Entry<String, List<G>> e : byPage.entrySet()) {
            n++;
            List<G> list = e.getValue();
            String prefix = list.get(0).group().prefix();
            String identity = list.stream().flatMap(g -> g.steps().stream()).filter(s -> s.key() != null).map(s -> rel(s.key())).findFirst().orElse("");
            b.append("\n\nclass ").append(e.getKey()).append("(BasePage):\n");
            b.append("    \"\"\"Tela ").append(n).append(" do processo.\"\"\"\n\n");
            b.append("    prefix = ").append(Py.str(PythonGenerator.SYSTEM + "." + prefix)).append('\n');
            b.append("    identity = ").append(Py.str(identity)).append('\n');
            for (G g : list) method(b, g);
        }
        return b.toString();
    }

    private static void method(StringBuilder b, G g) {
        List<String> params = new ArrayList<>();
        for (String in : g.inputs()) params.add(in + ": str");
        if (g.secrets()) params.add("segredos: SecretProvider");
        b.append("\n    def ").append(g.group().method()).append("(self").append(params.isEmpty() ? "" : ", " + String.join(", ", params)).append(") -> ")
                .append(g.outputs() ? "dict[str, str]" : "None").append(":\n");
        b.append("        a = self.act\n");
        if (g.outputs()) b.append("        saidas: dict[str, str] = {}\n");
        for (Step s : g.steps()) {
            String sid = Py.str(s.step().id().value().toString());
            String k = s.key() == null ? null : "self.k(" + Py.str(rel(s.key())) + ")";
            b.append("        # etapa ").append(s.step().id().value()).append(" · ").append(s.step().actionType().name()).append('\n');
            switch (s.step().actionType()) {
                case NAVIGATE -> b.append("        a.navigate(").append(Py.str(s.navigateUrl())).append(", step_id=").append(sid).append(")\n");
                case FILL -> {
                    String value = s.input() != null ? s.input() : "segredos.get(" + Py.str(s.credential()) + ")";
                    b.append("        a.fill(").append(k).append(", ").append(value).append(", step_id=").append(sid).append(")\n");
                }
                case CLICK -> click(b, s, k, sid);
                case WAIT_FOR -> b.append("        a.wait_visible(").append(k).append(", step_id=").append(sid).append(")\n");
                case OBSERVE -> b.append("        saidas[").append(Py.str(s.output())).append("] = a.read_text(").append(k).append(", step_id=").append(sid)
                        .append(")\n");
                default -> throw new IllegalStateException("ação não suportada passou pelo pré-voo");
            }
        }
        if (g.outputs()) b.append("        return saidas\n");
    }

    private static void click(StringBuilder b, Step s, String k, String sid) {
        SingleConfig.Effect e = s.effect();
        if ("NONE".equals(e.effect())) {
            // sem efeito de negócio: idempotente; se a etapa seguinte é de outra tela, ela prova que o clique avançou
            boolean otherScreen = s.nextKey() != null && !s.nextKey().startsWith(PythonGenerator.SYSTEM + "." + s.group().prefix() + ".");
            b.append("        a.click(").append(k).append(", step_id=").append(sid).append(", idempotent=True");
            if (otherScreen) b.append(", postcondition=lambda: a.exists(").append(Py.str(s.nextKey())).append(")");
            b.append(")\n");
            return;
        }
        // efeito externo: uma vez só (não idempotente), concluído apenas com a confirmação declarada na especificação
        switch (e.postcondition()) {
            case "TARGET_HIDDEN" -> b.append("        a.click(").append(k).append(", step_id=").append(sid).append(", postcondition=lambda: not a.is_visible(")
                    .append(k).append("))\n");
            case "FIRST_FIELD_REPLACED" -> {
                b.append("        marca = a.mark(").append(Py.str(s.firstFillKey())).append(", step_id=").append(sid).append(")\n");
                b.append("        a.click(").append(k).append(", step_id=").append(sid).append(", postcondition=lambda: a.replaced(marca))\n");
            }
            case "NEXT_TARGET_VISIBLE" -> b.append("        a.click(").append(k).append(", step_id=").append(sid).append(", postcondition=lambda: a.exists(")
                    .append(Py.str(s.nextKey())).append("))\n");
            default -> throw new IllegalStateException("confirmação desconhecida passou pelo pré-voo");
        }
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

    private static String rel(String key) {
        return key.substring(key.lastIndexOf('.') + 1);
    }

    // ------------------------------------------------------------------------------------------------ processo.py
    private static String process(SingleConfig cfg, List<G> groups, List<Step> steps) {
        Set<String> secrets = new java.util.TreeSet<>();
        for (Step s : steps) if (s.credential() != null) secrets.add(s.credential());
        boolean inputs = !cfg.inputs().isEmpty();
        boolean outputs = groups.stream().anyMatch(G::outputs);
        StringBuilder b = new StringBuilder();
        b.append(HEADER);
        b.append("\"\"\"Processo gerado (execução única): INIT abre o início; PROCESS faz as etapas na ordem do modelo; END fecha.\"\"\"\n\n");
        b.append("from __future__ import annotations\n\n");
        if (inputs) b.append("from collections.abc import Mapping\nfrom dataclasses import dataclass\n\n");
        b.append("from nekkora_rpa import ProcessResult, RpaProcess, Runtime, TransactionItem\n");
        List<String> errs = new ArrayList<>();
        if (inputs) errs.add("BusinessRuleRejected");
        if (!secrets.isEmpty()) errs.add("InvalidConfiguration");
        if (!errs.isEmpty()) b.append("from nekkora_rpa.errors import ").append(String.join(", ", errs)).append('\n');
        if (!secrets.isEmpty()) b.append("from nekkora_rpa.security.secrets import SecretProvider\n");
        b.append('\n');
        b.append("from paginas import ").append(String.join(", ", groups.stream().map(g -> g.group().page()).distinct().sorted(SingleEmitter::natural).toList()))
                .append("\n\n");
        b.append("URL_INICIAL = ").append(Py.str(cfg.startUrl())).append('\n');
        if (inputs) {
            b.append("ENTRADAS = (").append(String.join(", ", cfg.inputs().stream().map(Py::str).toList())).append(cfg.inputs().size() == 1 ? ",)" : ")")
                    .append("\n\n\n");
            b.append("@dataclass(frozen=True, repr=False)\nclass Entrada:\n");
            b.append("    \"\"\"Entrada da execução (os valores nunca são registrados).\"\"\"\n\n");
            for (String in : cfg.inputs()) b.append("    ").append(in).append(": str\n");
            b.append("\n    @classmethod\n    def de(cls, dados: Mapping[str, object]) -> Entrada:\n");
            b.append("        valores: dict[str, str] = {}\n        for nome in ENTRADAS:\n            valor = dados.get(nome)\n");
            b.append("            if not isinstance(valor, str) or not valor.strip():\n                raise BusinessRuleRejected(\"ENTRADA_INVALIDA\")\n");
            b.append("            valores[nome] = valor\n        return cls(**valores)\n");
        }
        if (!secrets.isEmpty()) {
            b.append("\n\ndef _segredos(rt: Runtime) -> SecretProvider:\n");
            b.append("    if rt.secrets is None:\n        raise InvalidConfiguration(detail=\"sem_provedor_de_segredos\")\n    return rt.secrets\n");
        }
        b.append("\n\nclass Processo(RpaProcess):\n");
        b.append("    name = ").append(Py.str(cfg.processName())).append('\n');
        b.append("    requires_browser = True\n");
        b.append("    required_secrets = (").append(String.join(", ", secrets.stream().map(Py::str).toList())).append(secrets.size() == 1 ? ",)" : ")").append("\n\n");
        b.append("    def init(self, rt: Runtime) -> None:\n        rt.act.navigate(URL_INICIAL, step_id=\"INICIO\")\n\n");
        b.append("    def process(self, rt: Runtime, item: TransactionItem) -> ProcessResult:\n");
        if (inputs) b.append("        entrada = Entrada.de(item.data)  # antes de qualquer ação: entrada inválida recusa o item\n");
        if (!secrets.isEmpty()) b.append("        segredos = _segredos(rt)\n");
        if (outputs) b.append("        saidas: dict[str, str] = {}\n");
        for (G g : groups) {
            List<String> args = new ArrayList<>();
            for (String in : g.inputs()) args.add("entrada." + in);
            if (g.secrets()) args.add("segredos");
            String call = g.group().page() + "(rt.act)." + g.group().method() + "(" + String.join(", ", args) + ")";
            b.append("        ").append(g.outputs() ? "saidas.update(" + call + ")" : call).append('\n');
        }
        b.append("        return ProcessResult(").append(outputs ? "saidas" : "{}").append(")\n");
        return b.toString();
    }

    // ------------------------------------------------------------------------------------------------ main.py
    private static String main(SingleConfig cfg) {
        boolean inputs = !cfg.inputs().isEmpty();
        StringBuilder b = new StringBuilder();
        b.append(HEADER);
        b.append("\"\"\"Execução: python main.py --saida <pasta>").append(inputs ? " --entrada <entrada.json>" : "")
                .append(" [--channel chrome|chromium] [--headless]\"\"\"\n\n");
        b.append("from __future__ import annotations\n\nimport argparse\nimport json\nimport sys\nfrom pathlib import Path\n\n");
        b.append("from nekkora_rpa import Engine, EnvSecretProvider, SingleInputProvider, TransactionItem, load_config\n");
        b.append("from nekkora_rpa.data import JsonDirectoryResultSink\n\n");
        b.append("from processo import Processo\n\n");
        b.append("AQUI = Path(__file__).resolve().parent\n");
        b.append("SAIDA = {\"COMPLETED\": 0, \"COMPLETED_WITH_FAILURES\": 1, \"STOPPED\": 2, \"INIT_FAILED\": 3, \"CANCELLED\": 4}\n");
        if (inputs) {
            b.append("\n\ndef ler_entrada(caminho: Path) -> dict[str, object]:\n");
            b.append("    \"\"\"Objeto JSON com as entradas da execução; os valores nunca são impressos nem registrados.\"\"\"\n");
            b.append("    dados: object = json.loads(caminho.read_text(encoding=\"utf-8\"))\n");
            b.append("    if not isinstance(dados, dict):\n        raise SystemExit(\"entrada: o arquivo precisa ser um objeto JSON\")\n");
            b.append("    return {str(k): v for k, v in dados.items()}\n");
        }
        b.append("\n\ndef main(argv: list[str] | None = None) -> int:\n");
        b.append("    ap = argparse.ArgumentParser(description=").append(Py.str("Processo gerado: " + cfg.processName())).append(")\n");
        b.append("    ap.add_argument(\"--saida\", required=True, type=Path, help=\"pasta da execução (resultados, eventos, checkpoint)\")\n");
        if (inputs) b.append("    ap.add_argument(\"--entrada\", required=True, type=Path, help=\"objeto JSON com as entradas\")\n");
        b.append("    ap.add_argument(\"--channel\", choices=[\"chromium\", \"chrome\"])\n");
        b.append("    ap.add_argument(\"--headless\", action=\"store_true\")\n    ap.add_argument(\"--console\", action=\"store_true\")\n");
        b.append("    a = ap.parse_args(argv)\n");
        b.append("    saida = a.saida.resolve()\n    saida.mkdir(parents=True, exist_ok=True)\n");
        b.append("    overrides: dict[str, object] = {}\n    if a.channel:\n        overrides[\"browser.channel\"] = a.channel\n");
        b.append("    if a.headless:\n        overrides[\"browser.headless\"] = True\n");
        b.append("    config = load_config(AQUI / \"config\" / \"execucao.toml\", overrides)\n");
        b.append("    processo = Processo()\n");
        b.append("    item = TransactionItem(\"execucao-unica\", ").append(inputs ? "ler_entrada(a.entrada)" : "{}").append(")\n");
        b.append("    engine = Engine(\n        processo,\n        SingleInputProvider(item),\n        JsonDirectoryResultSink(saida / \"resultados\"),\n        config,\n");
        b.append("        secrets=EnvSecretProvider(processo.required_secrets),\n        run_dir=saida / \"execucao\",\n        console=a.console,\n    )\n");
        b.append("    resumo = engine.run()\n");
        b.append("    d = resumo.to_dict()\n");
        b.append("    print(json.dumps({k: d[k] for k in (\"execution_id\", \"status\", \"stop_code\", \"stop_detail\", \"counts\")}))\n");
        b.append("    return SAIDA[resumo.status.value]\n\n\n");
        b.append("if __name__ == \"__main__\":\n    sys.exit(main())\n");
        return b.toString();
    }

    private static String readme(SingleConfig cfg, List<Step> steps) {
        Set<String> secrets = new java.util.TreeSet<>();
        for (Step s : steps) if (s.credential() != null) secrets.add(s.credential());
        if (cfg.modern()) return Modern.readme(cfg, secrets);
        StringBuilder b = new StringBuilder();
        b.append("<!-- GERADO pelo Nekkora Studio (STUDIO-014). -->\n# Processo gerado: ").append(cfg.processName()).append("\n\n");
        b.append("Gerado por \"Desenvolver automação\" a partir do modelo validado e do PDD pronto (ver `manifest.json`: automação, revisões do PDD, ")
                .append("StepIds e hashes). Correções vão para o MODELO ou o PDD e o código é gerado de novo; arquivo editado à mão não é sobrescrito.\n\n");
        b.append("Execução única (sem planilha). ");
        if (!cfg.inputs().isEmpty()) {
            b.append("Entradas (arquivo JSON em `--entrada`, um objeto com estas chaves): ")
                    .append(String.join(", ", cfg.inputs().stream().map(x -> "`" + x + "`").toList())).append(". ");
        }
        if (!secrets.isEmpty()) {
            b.append("Segredos (variáveis de ambiente, nunca no projeto): ")
                    .append(String.join(", ", secrets.stream().map(x -> "`NK_SECRET_" + x.toUpperCase().replace('.', '_').replace('-', '_') + "`").toList()))
                    .append(". ");
        }
        b.append("\n\n").append(InstallHint.readmeBlock(SingleGenerator.FRAMEWORK_REQUIREMENT, "python main.py --saida <pasta-da-execucao>"
                + (cfg.inputs().isEmpty() ? "" : " --entrada <entrada.json>") + " --console"));
        b.append("Saída: `resultados/` (o desfecho da execução, com as leituras) e `execucao/` (eventos JSONL sem valores, checkpoint, resumo).\n");
        b.append("Gerar código não é executar: rode somente em ambiente autorizado (").append(cfg.environment()).append(").\n");
        return b.toString();
    }

    private static String manifest(Automation a, GuidedPlan plan, SingleConfig cfg, List<Step> steps, Map<String, String> files) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schema", "nekkora-studio/codegen-manifest");
        m.put("version", cfg.modern() ? "1.2.0" : "1.1.0");
        m.put("generator", cfg.modern() ? SingleGenerator.GENERATOR_LIST : SingleGenerator.GENERATOR);
        m.put("framework", cfg.modern() ? SingleGenerator.FRAMEWORK_REQUIREMENT_LIST : SingleGenerator.FRAMEWORK_REQUIREMENT);
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
        m.put("command", cfg.list() ? Modern.command() : "python main.py --saida <pasta>" + (cfg.inputs().isEmpty() ? "" : " --entrada <entrada.json>"));
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
        List<Object> list = new ArrayList<>();
        for (Step s : steps) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("stepId", s.step().id().value().toString());
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
            e.put("capability", cfg.modern() ? Modern.capability(s) : capability(s.step().actionType()));
            e.put("decisionActor", s.step().targetDecision().map(d -> d.actor().name()).orElse(null));
            list.add(e);
        }
        m.put("steps", list);
        Map<String, Object> hashes = new LinkedHashMap<>();
        for (Map.Entry<String, String> f : files.entrySet()) {
            if (!f.getKey().equals("manifest.json")) hashes.put(f.getKey(), "sha256:" + CodegenConfig.sha256(f.getValue()));
        }
        m.put("files", hashes);
        m.put("exclusions", List.of("INPUT_VALUES", "CREDENTIAL_VALUES", "LITERAL_VALUES", "HAND_WRITTEN_SELECTORS", "POSITIONAL_LOCATORS", "URL_QUERIES"));
        return Pretty.json(m) + "\n";
    }

    /** Capacidade do framework usada pela ação (o mesmo id do catálogo do AI Engine). */
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
