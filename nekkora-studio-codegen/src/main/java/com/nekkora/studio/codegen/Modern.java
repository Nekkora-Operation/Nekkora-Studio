package com.nekkora.studio.codegen;

import com.nekkora.studio.codegen.SingleGenerator.Step;
import com.nekkora.studio.domain.action.ActionType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * NEKKORA-E2E-002 — textos do projeto na configuração 1.2.0 (determinísticos; só o que o pré-voo aprovou):
 * <ul>
 *   <li>Pages: Enter pelo {@code press} (confirmado pela troca de endereço quando a gravação viu a página mudar); leituras
 *       pelo {@code read_items} com a normalização do framework ({@code join_items}, {@code parse_long_date}) ou pelo
 *       {@code current_address}; leitura OPCIONAL só onde há fonte alternativa declarada ({@code exists}, sem espera:
 *       antes, uma espera pela primeira leitura obrigatória da tela garante a página nova); conferência com a entrada;</li>
 *   <li>processo: lista → cada transação começa no endereço inicial; a linha da planilha sai da regra declarada
 *       (coluna ← leitura, ou a alternativa); dado obrigatório vazio → {@code BusinessRuleRejected("DADO_INCOMPLETO")};</li>
 *   <li>main: lista CSV/XLSX pelo {@code tabular} (colunas = variáveis de entrada) e planilha pelo {@code XlsxResultSink}
 *       (tudo ou nada, quantidade = linhas da lista).</li>
 * </ul>
 */
final class Modern {
    private Modern() {}

    /** Grupo com as etapas prontas. */
    record G(SingleConfig.Group group, List<Step> steps) {
        List<String> params() {
            List<String> out = new ArrayList<>();
            for (Step s : steps) {
                if (s.input() != null && !out.contains(s.input())) out.add(s.input());
                if (s.read() != null && s.read().expected() != null && !out.contains(s.read().expected())) out.add(s.read().expected());
            }
            return out;
        }

        boolean secrets() {
            return steps.stream().anyMatch(s -> s.credential() != null);
        }

        boolean outputs() {
            return steps.stream().anyMatch(s -> s.output() != null);
        }
    }

    static String command() {
        return "python main.py --entrada <lista.csv|lista.xlsx> --planilha <saida.xlsx> --saida <pasta>";
    }

    static String capability(Step s) {
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

    // ------------------------------------------------------------------------------------------------ paginas.py
    static String pages(List<G> groups) {
        StringBuilder b = new StringBuilder();
        b.append(SingleEmitter.HEADER);
        b.append("\"\"\"Pages geradas: uma por tela do processo; intenções da tela por CHAVE do registro de alvos (sem seletores).\"\"\"\n\n");
        b.append("from __future__ import annotations\n\n");
        Set<String> text = new TreeSet<>();
        boolean secrets = false;
        boolean rejects = false;
        for (G g : groups) {
            secrets |= g.secrets();
            for (Step s : g.steps()) {
                if (s.read() == null) continue;
                switch (s.read().kind()) {
                    case "text" -> text.add("join_items");
                    case "date" -> text.add("parse_long_date");
                    default -> {
                    }
                }
                if (s.read().expected() != null) {
                    rejects = true;
                    text.add("address".equals(s.read().kind()) ? "same_address" : "normalize_text");
                }
            }
        }
        if (rejects) b.append("from nekkora_rpa.errors import BusinessRuleRejected\n");
        b.append("from nekkora_rpa.pages import BasePage\n");
        if (secrets) b.append("from nekkora_rpa.security.secrets import SecretProvider\n");
        if (!text.isEmpty()) b.append("from nekkora_rpa.text import ").append(String.join(", ", text)).append('\n');
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
        for (String in : g.params()) params.add(in + ": str");
        if (g.secrets()) params.add("segredos: SecretProvider");
        b.append("\n    def ").append(g.group().method()).append("(self").append(params.isEmpty() ? "" : ", " + String.join(", ", params)).append(") -> ")
                .append(g.outputs() ? "dict[str, str]" : "None").append(":\n");
        b.append("        a = self.act\n");
        if (g.outputs()) b.append("        saidas: dict[str, str] = {}\n");
        // leituras opcionais usam exists() (sem espera): antes, a primeira leitura OBRIGATÓRIA com alvo da tela garante a página
        Step anchor = null;
        boolean optional = false;
        for (Step s : g.steps()) {
            if (s.read() == null || s.key() == null) continue;
            if (s.read().optional()) optional = true;
            else if (anchor == null) anchor = s;
        }
        boolean waited = false;
        for (Step s : g.steps()) {
            String sid = Py.str(s.step().id().value().toString());
            String k = s.key() == null ? null : "self.k(" + Py.str(rel(s.key())) + ")";
            if (optional && !waited && anchor != null && s.read() != null && s.read().optional()) {
                b.append("        # página nova pronta: 1ª leitura obrigatória da tela (etapa ").append(anchor.step().id().value()).append(")\n");
                b.append("        a.wait_visible(self.k(").append(Py.str(rel(anchor.key()))).append("), step_id=")
                        .append(Py.str(anchor.step().id().value().toString())).append(")\n");
                waited = true;
            }
            b.append("        # etapa ").append(s.step().id().value()).append(" · ").append(s.step().actionType().name()).append('\n');
            switch (s.step().actionType()) {
                case NAVIGATE -> b.append("        a.navigate(").append(Py.str(s.navigateUrl())).append(", step_id=").append(sid).append(")\n");
                case FILL -> {
                    String value = s.input() != null ? s.input() : "segredos.get(" + Py.str(s.credential()) + ")";
                    b.append("        a.fill(").append(k).append(", ").append(value).append(", step_id=").append(sid).append(")\n");
                }
                case CLICK -> click(b, s, k, sid, "click", "");
                case PRESS_KEY -> click(b, s, k, sid, "press", ", \"Enter\"");
                case WAIT_FOR -> b.append("        a.wait_visible(").append(k).append(", step_id=").append(sid).append(")\n");
                case OBSERVE -> read(b, s, k, sid);
                default -> throw new IllegalStateException("ação não suportada passou pelo pré-voo");
            }
        }
        if (g.outputs()) b.append("        return saidas\n");
    }

    private static void read(StringBuilder b, Step s, String k, String sid) {
        SingleConfig.Read r = s.read();
        String var = Py.str(s.output());
        // leitura em duas linhas (itens, depois a normalização): linhas curtas e o StepId sempre na chamada do framework
        String read = "itens = a.read_items(" + k + ", step_id=" + sid + ")";
        String expr = switch (r.kind()) {
            case "address" -> null;
            case "date" -> "parse_long_date(\" \".join(itens)) or \"\"";
            default -> "join_items(itens)";
        };
        if (expr == null) {
            b.append("        saidas[").append(var).append("] = a.current_address(step_id=").append(sid).append(")\n");
        } else if (r.optional() && k != null) {
            // fonte alternativa declarada: o alvo pode faltar nesta página (nada é presumido; a regra da coluna decide)
            b.append("        if a.exists(").append(k).append(", step_id=").append(sid).append("):\n");
            b.append("            ").append(read).append('\n');
            b.append("            saidas[").append(var).append("] = ").append(expr).append('\n');
            b.append("        else:\n");
            b.append("            saidas[").append(var).append("] = \"\"\n");
        } else {
            b.append("        ").append(read).append('\n');
            b.append("        saidas[").append(var).append("] = ").append(expr).append('\n');
        }
        if (r.expected() != null) {
            String cmp = "address".equals(r.kind()) ? "same_address(saidas[" + var + "], " + r.expected() + ")"
                    : "normalize_text(saidas[" + var + "]) == normalize_text(" + r.expected() + ")";
            b.append("        if not ").append(cmp).append(":\n");
            b.append("            raise BusinessRuleRejected(\"CONFERENCIA_FALHOU\")  # a página não é a esperada\n");
        }
    }

    private static void click(StringBuilder b, Step s, String k, String sid, String op, String keyArg) {
        SingleConfig.Effect e = s.effect();
        boolean navigates = s.press() != null && s.press().navigates();
        if ("NONE".equals(e.effect())) {
            if (navigates) {
                // a gravação viu o documento mudar: o avanço é provado pela troca de endereço (nada de ler a página antiga)
                b.append("        antes = a.current_address(step_id=").append(sid).append(")\n");
                b.append("        a.").append(op).append('(').append(k).append(keyArg).append(", step_id=").append(sid).append(", idempotent=True")
                        .append(", postcondition=lambda: a.current_address() != antes)\n");
                return;
            }
            boolean otherScreen = s.nextKey() != null && !s.nextKey().startsWith(PythonGenerator.SYSTEM + "." + s.group().prefix() + ".");
            b.append("        a.").append(op).append('(').append(k).append(keyArg).append(", step_id=").append(sid).append(", idempotent=True");
            if (otherScreen) b.append(", postcondition=lambda: a.exists(").append(Py.str(s.nextKey())).append(")");
            b.append(")\n");
            return;
        }
        switch (e.postcondition()) {
            case "TARGET_HIDDEN" -> b.append("        a.").append(op).append('(').append(k).append(keyArg).append(", step_id=").append(sid)
                    .append(", postcondition=lambda: not a.is_visible(").append(k).append("))\n");
            case "FIRST_FIELD_REPLACED" -> {
                b.append("        marca = a.mark(").append(Py.str(s.firstFillKey())).append(", step_id=").append(sid).append(")\n");
                b.append("        a.").append(op).append('(').append(k).append(keyArg).append(", step_id=").append(sid)
                        .append(", postcondition=lambda: a.replaced(marca))\n");
            }
            case "NEXT_TARGET_VISIBLE" -> b.append("        a.").append(op).append('(').append(k).append(keyArg).append(", step_id=").append(sid)
                    .append(", postcondition=lambda: a.exists(").append(Py.str(s.nextKey())).append("))\n");
            default -> throw new IllegalStateException("confirmação desconhecida passou pelo pré-voo");
        }
    }

    // ------------------------------------------------------------------------------------------------ processo.py
    static String process(SingleConfig cfg, List<G> groups, List<Step> steps) {
        Set<String> secrets = new TreeSet<>();
        for (Step s : steps) if (s.credential() != null) secrets.add(s.credential());
        boolean inputs = !cfg.inputs().isEmpty();
        boolean outputs = groups.stream().anyMatch(G::outputs);
        boolean sheet = cfg.spreadsheet();
        StringBuilder b = new StringBuilder();
        b.append(SingleEmitter.HEADER);
        b.append(cfg.list() ? "\"\"\"Processo gerado (uma transação por linha da lista).\n\nCada item começa no endereço inicial, faz as etapas na "
                + "ordem do modelo e devolve UMA linha da planilha.\n\"\"\"\n\n"
                : "\"\"\"Processo gerado (execução única): INIT abre o início; PROCESS faz as etapas na ordem do modelo; END fecha.\"\"\"\n\n");
        b.append("from __future__ import annotations\n\n");
        if (inputs) b.append("from collections.abc import Mapping\nfrom dataclasses import dataclass\n\n");
        b.append("from nekkora_rpa import ProcessResult, RpaProcess, Runtime, TransactionItem\n");
        List<String> errs = new ArrayList<>();
        if (inputs || sheet) errs.add("BusinessRuleRejected");
        if (!secrets.isEmpty()) errs.add("InvalidConfiguration");
        if (!errs.isEmpty()) b.append("from nekkora_rpa.errors import ").append(String.join(", ", errs)).append('\n');
        if (!secrets.isEmpty()) b.append("from nekkora_rpa.security.secrets import SecretProvider\n");
        b.append('\n');
        b.append("from paginas import ").append(String.join(", ", groups.stream().map(g -> g.group().page()).distinct().sorted(SingleEmitter::natural).toList()))
                .append("\n\n");
        b.append("URL_INICIAL = ").append(Py.str(cfg.startUrl())).append('\n');
        if (inputs) {
            b.append("ENTRADAS = (").append(String.join(", ", cfg.inputs().stream().map(Py::str).toList())).append(cfg.inputs().size() == 1 ? ",)" : ")")
                    .append('\n');
        }
        if (sheet) {
            b.append("# coluna da planilha ← leitura (variável) e, se o alvo dela faltar na página, a fonte alternativa declarada\n");
            b.append("COLUNAS: tuple[tuple[str, str, str | None], ...] = (\n");
            for (SingleConfig.Column c : cfg.output().columns()) {
                b.append("    (").append(Py.str(c.field())).append(", ").append(Py.str(cfg.outputs().get(c.step()))).append(", ")
                        .append(c.fallbackStep() == null ? "None" : Py.str(cfg.outputs().get(c.fallbackStep()))).append("),\n");
            }
            b.append(")\n");
        }
        if (inputs) {
            b.append("\n\n@dataclass(frozen=True, repr=False)\nclass Entrada:\n");
            b.append("    \"\"\"Entrada de um item (os valores nunca são registrados).\"\"\"\n\n");
            for (String in : cfg.inputs()) b.append("    ").append(in).append(": str\n");
            b.append("\n    @classmethod\n    def de(cls, dados: Mapping[str, object]) -> Entrada:\n");
            b.append("        valores: dict[str, str] = {}\n        for nome in ENTRADAS:\n            valor = dados.get(nome)\n");
            b.append("            if not isinstance(valor, str) or not valor.strip():\n                raise BusinessRuleRejected(\"ENTRADA_INVALIDA\")\n");
            b.append("            valores[nome] = valor\n        return cls(**valores)\n");
        }
        if (sheet) {
            b.append("\n\ndef linha(saidas: Mapping[str, str]) -> dict[str, object]:\n");
            b.append("    \"\"\"Uma linha da planilha pela regra declarada; dado obrigatório vazio rejeita o item (nada é inventado).\"\"\"\n");
            b.append("    out: dict[str, object] = {}\n");
            b.append("    for campo, principal, alternativa in COLUNAS:\n");
            b.append("        valor = saidas.get(principal, \"\")\n");
            b.append("        if not valor.strip() and alternativa is not None:\n");
            b.append("            valor = saidas.get(alternativa, \"\")\n");
            b.append("        if not valor.strip():\n");
            b.append("            raise BusinessRuleRejected(\"DADO_INCOMPLETO\")\n");
            b.append("        out[campo] = valor\n");
            b.append("    return out\n");
        }
        if (!secrets.isEmpty()) {
            b.append("\n\ndef _segredos(rt: Runtime) -> SecretProvider:\n");
            b.append("    if rt.secrets is None:\n        raise InvalidConfiguration(detail=\"sem_provedor_de_segredos\")\n    return rt.secrets\n");
        }
        b.append("\n\nclass Processo(RpaProcess):\n");
        b.append("    name = ").append(Py.str(cfg.processName())).append('\n');
        b.append("    requires_browser = True\n");
        b.append("    required_secrets = (").append(String.join(", ", secrets.stream().map(Py::str).toList())).append(secrets.size() == 1 ? ",)" : ")").append("\n\n");
        if (!cfg.list()) b.append("    def init(self, rt: Runtime) -> None:\n        rt.act.navigate(URL_INICIAL, step_id=\"INICIO\")\n\n");
        b.append("    def process(self, rt: Runtime, item: TransactionItem) -> ProcessResult:\n");
        if (inputs) b.append("        entrada = Entrada.de(item.data)  # antes de qualquer ação: entrada inválida recusa o item\n");
        if (cfg.list()) b.append("        rt.act.navigate(URL_INICIAL, step_id=\"INICIO\")  # cada item começa no mesmo ponto\n");
        if (!secrets.isEmpty()) b.append("        segredos = _segredos(rt)\n");
        if (outputs) b.append("        saidas: dict[str, str] = {}\n");
        for (G g : groups) {
            List<String> args = new ArrayList<>();
            for (String in : g.params()) args.add("entrada." + in);
            if (g.secrets()) args.add("segredos");
            String call = g.group().page() + "(rt.act)." + g.group().method() + "(" + String.join(", ", args) + ")";
            b.append("        ").append(g.outputs() ? "saidas.update(" + call + ")" : call).append('\n');
        }
        b.append("        return ProcessResult(").append(sheet ? "linha(saidas)" : outputs ? "saidas" : "{}").append(")\n");
        return b.toString();
    }

    // ------------------------------------------------------------------------------------------------ main.py
    static String main(SingleConfig cfg) {
        if (!cfg.list()) return singleMain(cfg);
        StringBuilder b = new StringBuilder();
        b.append(SingleEmitter.HEADER);
        b.append("\"\"\"Execução do processo em lista.\n\n    python main.py --entrada <lista.csv|lista.xlsx> --planilha <saida.xlsx> --saida <pasta>\n"
                + "        [--channel chrome|chromium] [--headless]\n\nA lista tem uma coluna por entrada:\n");
        for (String in : cfg.inputs()) b.append("- ").append(in).append('\n');
        b.append("A planilha só é gravada se TODOS os itens derem certo.\n\"\"\"\n\n");
        b.append("from __future__ import annotations\n\nimport argparse\nimport json\nimport sys\nfrom pathlib import Path\n\n");
        b.append("from nekkora_rpa import Engine, EnvSecretProvider, load_config\n");
        b.append("from nekkora_rpa.errors import NekkoraRpaError\n");
        b.append("from nekkora_rpa.tabular import Column, TabularInputProvider, TabularSchema, read_tabular\n");
        b.append("from nekkora_rpa.xlsx import ColumnKind, OutputColumn, OverwritePolicy, XlsxResultSink, XlsxSchema\n\n");
        b.append("from processo import ENTRADAS, Processo\n\n");
        b.append("AQUI = Path(__file__).resolve().parent\n");
        b.append("SAIDA = {\"COMPLETED\": 0, \"COMPLETED_WITH_FAILURES\": 1, \"STOPPED\": 2, \"INIT_FAILED\": 3, \"CANCELLED\": 4}\n");
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
        b.append("\n\ndef main(argv: list[str] | None = None) -> int:\n");
        b.append("    ap = argparse.ArgumentParser(description=").append(Py.str("Processo gerado: " + cfg.processName())).append(")\n");
        b.append("    ap.add_argument(\"--entrada\", required=True, type=Path, help=\"lista CSV ou XLSX (uma linha por item)\")\n");
        b.append("    ap.add_argument(\"--planilha\", required=True, type=Path, help=\"planilha .xlsx de saída\")\n");
        b.append("    ap.add_argument(\"--saida\", required=True, type=Path, help=\"pasta da execução (eventos, checkpoint)\")\n");
        b.append("    ap.add_argument(\"--channel\", choices=[\"chromium\", \"chrome\"])\n");
        b.append("    ap.add_argument(\"--headless\", action=\"store_true\")\n    ap.add_argument(\"--console\", action=\"store_true\")\n");
        b.append("    a = ap.parse_args(argv)\n");
        b.append("    saida = a.saida.resolve()\n    saida.mkdir(parents=True, exist_ok=True)\n");
        b.append("    overrides: dict[str, object] = {}\n    if a.channel:\n        overrides[\"browser.channel\"] = a.channel\n");
        b.append("    if a.headless:\n        overrides[\"browser.headless\"] = True\n");
        b.append("    config = load_config(AQUI / \"config\" / \"execucao.toml\", overrides)\n");
        b.append("    try:\n        lista = read_tabular(a.entrada, LISTA)\n");
        b.append("        destino = XlsxResultSink(\n            a.planilha.resolve(),\n            PLANILHA,\n            state_dir=saida / \"execucao\",\n");
        b.append("            overwrite=OverwritePolicy.REPLACE,\n            expected_rows=len(lista.rows),\n        )\n");
        b.append("    except NekkoraRpaError as err:\n");
        b.append("        print(json.dumps({\"status\": \"INPUT_INVALID\", \"code\": err.code.value, \"detail\": err.detail}))\n        return 5\n");
        b.append("    processo = Processo()\n");
        b.append("    engine = Engine(\n        processo,\n        TabularInputProvider(lista),\n        destino,\n        config,\n");
        b.append("        secrets=EnvSecretProvider(processo.required_secrets),\n        run_dir=saida / \"execucao\",\n        console=a.console,\n    )\n");
        b.append("    resumo = engine.run()\n");
        b.append("    d = resumo.to_dict()\n");
        b.append("    campos = (\"execution_id\", \"status\", \"stop_code\", \"stop_detail\", \"counts\", \"output\")\n");
        b.append("    print(json.dumps({k: d[k] for k in campos}))\n");
        b.append("    return SAIDA[resumo.status.value]\n\n\n");
        b.append("if __name__ == \"__main__\":\n    sys.exit(main())\n");
        return b.toString();
    }

    /** Execução única 1.2.0 (sem lista): mesmo main da 1.1.0, com o resumo da saída. */
    private static String singleMain(SingleConfig cfg) {
        boolean inputs = !cfg.inputs().isEmpty();
        StringBuilder b = new StringBuilder();
        b.append(SingleEmitter.HEADER);
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

    static String readme(SingleConfig cfg, Set<String> secrets) {
        StringBuilder b = new StringBuilder();
        b.append("<!-- GERADO pelo Nekkora Studio (NEKKORA-E2E-002). -->\n# Processo gerado: ").append(cfg.processName()).append("\n\n");
        b.append("Gerado por \"Desenvolver automação\" a partir do modelo validado e do PDD pronto (ver `manifest.json`: automação, revisões do PDD, ")
                .append("StepIds e hashes). Correções vão para o MODELO ou o PDD e o código é gerado de novo; arquivo editado à mão não é sobrescrito.\n\n");
        if (cfg.list()) {
            b.append("Uma transação por linha da lista de entrada (CSV ou XLSX com as colunas ")
                    .append(String.join(", ", cfg.inputs().stream().map(x -> "`" + x + "`").toList())).append("). ");
            b.append("Saída: planilha `.xlsx` com as colunas ")
                    .append(String.join(", ", cfg.output().columns().stream().map(c -> "`" + c.header() + "`").toList()))
                    .append(", gravada pelo `XlsxResultSink` do nekkora_rpa só se TODOS os itens derem certo (nada parcial). ");
        } else {
            b.append("Execução única. ");
        }
        if (!secrets.isEmpty()) {
            b.append("Segredos (variáveis de ambiente, nunca no projeto): ")
                    .append(String.join(", ", secrets.stream().map(x -> "`NK_SECRET_" + x.toUpperCase().replace('.', '_').replace('-', '_') + "`").toList()))
                    .append(". ");
        }
        b.append("\n\n").append(InstallHint.readmeBlock(SingleGenerator.FRAMEWORK_REQUIREMENT_LIST,
                (cfg.list() ? command() : "python main.py --saida <pasta-da-execucao>") + " --console"));
        b.append("Eventos JSONL sem valores, checkpoint e resumo em `<pasta>/execucao/`.\n");
        b.append("Gerar código não é executar: rode somente em ambiente autorizado (").append(cfg.environment()).append(").\n");
        return b.toString();
    }

    private static String rel(String key) {
        return key.substring(key.lastIndexOf('.') + 1);
    }

    static boolean needs(ActionType t) {
        return t == ActionType.PRESS_KEY;
    }
}
