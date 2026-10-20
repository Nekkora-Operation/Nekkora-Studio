package com.nekkora.studio.codegen;

import com.nekkora.studio.codegen.PythonGenerator.Role;
import com.nekkora.studio.codegen.PythonGenerator.Step;
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

/** Textos do projeto gerado (determinísticos). Só usa o que o pré-voo aprovou. */
final class Emitter {

    private Emitter() {}

    static Map<String, String> emit(Automation a, GuidedPlan plan, CodegenConfig cfg, List<Step> steps) {
        Map<String, String> files = new TreeMap<>();
        Map<Integer, List<Step>> bySession = new TreeMap<>();
        for (Step s : steps) bySession.computeIfAbsent(s.session(), k -> new ArrayList<>()).add(s);
        files.put("selectors.json", selectors(steps));
        files.put("config/execucao.toml", toml(cfg, steps));
        files.put("paginas.py", PyWrap.wrap(pages(cfg, bySession)));
        files.put("processo.py", PyWrap.wrap(process(cfg, bySession, steps)));
        files.put("main.py", PyWrap.wrap(main(cfg)));
        files.put("requirements.txt", InstallHint.requirementsTxt("STUDIO-011", PythonGenerator.FRAMEWORK_REQUIREMENT));
        files.put("README.md", readme(cfg));
        files.put("manifest.json", manifest(a, plan, cfg, steps, files));
        return files;
    }

    // ------------------------------------------------------------------------------------------------ selectors.json
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

    // ------------------------------------------------------------------------------------------------ configuração
    private static String toml(CodegenConfig cfg, List<Step> steps) {
        boolean download = steps.stream().anyMatch(Step::download);
        StringBuilder b = new StringBuilder();
        b.append("# GERADO pelo Nekkora Studio (STUDIO-011). Sem credenciais nem valores: segredos vêm de NK_SECRET_<NOME>.\n");
        b.append("[runtime]\n");
        b.append("environment = ").append(Py.str(cfg.environment())).append('\n');
        b.append("allowed_origins = [").append(String.join(", ", cfg.allowedOrigins().stream().map(Py::str).toList())).append("]\n");
        b.append("base_url = ").append(Py.str(cfg.startUrl())).append('\n');
        b.append("process_name = ").append(Py.str(cfg.processName())).append("\n\n");
        b.append("[timeouts]\naction_ms = 8000\nnavigation_ms = 30000\ntransaction_ms = 60000\n\n");
        b.append("[retry]\nmax_attempts = 2\ninitial_backoff_ms = 200\nmax_backoff_ms = 1000\n\n");
        b.append("[batch]\nmax_transaction_attempts = 1\nmax_consecutive_technical_failures = 2\n\n");
        b.append("[browser]\nchannel = \"chrome\"\nheadless = false\naccept_downloads = ").append(download).append("\n\n");
        b.append("[paths]\nselectors = \"../selectors.json\"\n");
        return b.toString();
    }

    // ------------------------------------------------------------------------------------------------ paginas.py
    private static String pages(CodegenConfig cfg, Map<Integer, List<Step>> bySession) {
        StringBuilder b = new StringBuilder();
        b.append(PythonGenerator.HEADER);
        b.append("\"\"\"Pages geradas: uma por sessão do modelo, etapas na ordem do modelo, alvos só por CHAVE do registro.\"\"\"\n\n");
        b.append("from __future__ import annotations\n\nfrom collections.abc import Mapping\nfrom pathlib import Path\n\n");
        b.append("from nekkora_rpa.libs.actions import DownloadedFile\nfrom nekkora_rpa.pages import BasePage\nfrom nekkora_rpa.security.secrets import SecretProvider\n\n");
        b.append("XLSX_MAGIC = b\"PK\\x03\\x04\"\n\n\n");
        b.append("def _valor(dados: Mapping[str, object], coluna: str) -> str:\n");
        b.append("    v = dados.get(coluna)\n    if not isinstance(v, str):\n        raise TypeError(\"coluna sem texto\")  # o processo já recusou linhas com problema\n    return v\n");
        String firstRecordFill = null;
        for (Map.Entry<Integer, List<Step>> e : bySession.entrySet()) {
            for (Step s : e.getValue()) {
                if (s.role() == Role.RECORD && s.step().actionType() == ActionType.FILL) {
                    firstRecordFill = s.key();
                    break;
                }
            }
        }
        for (Map.Entry<Integer, List<Step>> e : bySession.entrySet()) {
            int n = e.getKey();
            List<Step> list = e.getValue();
            Role role = list.get(0).role();
            String identity = list.stream().filter(s -> s.key() != null).map(s -> s.key().substring(s.key().lastIndexOf('.') + 1)).findFirst()
                    .orElse(null);
            b.append("\n\nclass Sessao").append(n).append("(BasePage):\n");
            b.append("    \"\"\"Sessão ").append(n).append(" do modelo — papel: ").append(role == Role.INIT ? "inicialização" : role == Role.RECORD
                    ? "uma transação por registro" : "conclusão").append(".\"\"\"\n\n");
            b.append("    prefix = ").append(Py.str(PythonGenerator.SYSTEM + ".s" + n)).append('\n');
            b.append("    identity = ").append(Py.str(identity == null ? "" : identity)).append("\n\n");
            switch (role) {
                case INIT -> b.append("    def executar(self, destino: Path, segredos: SecretProvider | None) -> tuple[DownloadedFile | None, dict[str, str]]:\n")
                        .append("        a = self.act\n        baixado: DownloadedFile | None = None\n        obs: dict[str, str] = {}\n");
                case RECORD -> b.append("    def executar(self, dados: Mapping[str, object], segredos: SecretProvider | None) -> dict[str, str]:\n")
                        .append("        a = self.act\n        obs: dict[str, str] = {}\n");
                case END -> b.append("    def executar(self, segredos: SecretProvider | None) -> dict[str, str]:\n")
                        .append("        a = self.act\n        obs: dict[str, str] = {}\n");
            }
            int lastClick = -1;
            for (int i = 0; i < list.size(); i++) if (list.get(i).step().actionType() == ActionType.CLICK && !list.get(i).download()) lastClick = i;
            for (int i = 0; i < list.size(); i++) {
                Step s = list.get(i);
                String sid = Py.str(s.step().id().value().toString());
                String k = s.key() == null ? null : "self.k(" + Py.str(s.key().substring(s.key().lastIndexOf('.') + 1)) + ")";
                b.append("        # etapa ").append(s.step().id().value()).append(" · ").append(s.step().actionType().name()).append('\n');
                switch (s.step().actionType()) {
                    case NAVIGATE -> b.append("        a.navigate(").append(Py.str(s.navigateUrl())).append(", step_id=").append(sid).append(")\n");
                    case FILL -> {
                        String value = s.column() != null ? "_valor(dados, " + Py.str(s.column()) + ")" : "_segredo(segredos, " + Py.str(s.credential()) + ")";
                        b.append("        a.fill(").append(k).append(", ").append(value).append(", step_id=").append(sid).append(")\n");
                    }
                    case CLICK -> {
                        if (s.download()) {
                            b.append("        baixado = a.download(").append(k).append(", dest_dir=destino, file_name=").append(Py.str(cfg.input().fileName()))
                                    .append(", magic=").append("xlsx".equals(cfg.input().format()) ? "XLSX_MAGIC" : "None").append(", step_id=").append(sid).append(")\n");
                        } else if (role == Role.RECORD && i == lastClick) {
                            // ENVIO: não idempotente; concluído só com prova de avanço (formulário recriado ou removido)
                            b.append("        marca = a.mark(self.k(").append(Py.str(firstRecordFill.substring(firstRecordFill.lastIndexOf('.') + 1)))
                                    .append("), step_id=").append(sid).append(")\n");
                            b.append("        a.click(").append(k).append(", step_id=").append(sid).append(", postcondition=lambda: a.replaced(marca))\n");
                        } else if (role == Role.INIT && i == lastClick && cfg.initAdvance() == CodegenConfig.Advance.REPLACED && firstRecordFill != null) {
                            b.append("        marca = a.mark(").append(Py.str(firstRecordFill)).append(", step_id=").append(sid).append(")\n");
                            b.append("        a.click(").append(k).append(", step_id=").append(sid).append(", postcondition=lambda: a.replaced(marca))\n");
                        } else {
                            b.append("        a.click(").append(k).append(", step_id=").append(sid).append(")\n");
                        }
                    }
                    case WAIT_FOR -> b.append("        a.wait_visible(").append(k).append(", step_id=").append(sid).append(")\n");
                    case OBSERVE -> {
                        if (s.textPattern() != null)
                            b.append("        obs[").append(sid).append("] = a.read_text_matching(").append(Py.str(s.textPattern())).append(", step_id=").append(sid).append(")\n");
                        else if (cfg.observations()) b.append("        obs[").append(sid).append("] = a.read_text(").append(k).append(", step_id=").append(sid).append(")\n");
                        else b.append("        a.wait_visible(").append(k).append(", step_id=").append(sid).append(")\n");
                    }
                    default -> throw new IllegalStateException("ação não suportada passou pelo pré-voo");
                }
            }
            switch (role) {
                case INIT -> b.append("        return baixado, obs\n");
                default -> b.append("        return obs\n");
            }
        }
        b.append("\n\ndef _segredo(segredos: SecretProvider | None, nome: str):  # type: ignore[no-untyped-def]\n");
        b.append("    if segredos is None:\n        raise ValueError(\"sem provedor de segredos\")\n    return segredos.get(nome)\n");
        return b.toString();
    }

    // ------------------------------------------------------------------------------------------------ processo.py
    private static String process(CodegenConfig cfg, Map<Integer, List<Step>> bySession, List<Step> steps) {
        Set<String> secrets = new LinkedHashSet<>();
        for (Step s : steps) if (s.credential() != null) secrets.add(s.credential());
        StringBuilder b = new StringBuilder();
        b.append(PythonGenerator.HEADER);
        b.append("\"\"\"Processo gerado: INIT (sessões de inicialização) → uma transação por linha da planilha → END (conclusão).\"\"\"\n\n");
        b.append("from __future__ import annotations\n\nimport json\nfrom pathlib import Path\n\n");
        // ordem do isort (ruff I001): terceiros (nekkora_rpa) antes dos módulos do próprio projeto
        b.append("from nekkora_rpa import ProcessResult, RpaProcess, Runtime, TransactionItem\nfrom nekkora_rpa.errors import BusinessRuleRejected\n");
        b.append("from nekkora_rpa.tabular import PROBLEMS_KEY\n\n");
        b.append("from paginas import ").append(String.join(", ", bySession.keySet().stream().map(n -> "Sessao" + n).toList())).append("\n\n");
        b.append("URL_INICIAL = ").append(Py.str(cfg.startUrl())).append("\n\n\n");
        b.append("class Processo(RpaProcess):\n");
        b.append("    name = ").append(Py.str(cfg.processName())).append('\n');
        b.append("    requires_browser = True\n");
        b.append("    required_secrets = (").append(String.join(", ", secrets.stream().map(Py::str).toList())).append(secrets.size() == 1 ? ",)" : ")").append("\n\n");
        b.append("    def __init__(self, saida: Path) -> None:\n        self.saida = saida\n        self.entrada: Path | None = None\n");
        b.append("        self.entrada_sha256: str | None = None\n        self.observacoes: dict[str, str] = {}\n\n");
        b.append("    def init(self, rt: Runtime) -> None:\n");
        b.append("        rt.act.navigate(URL_INICIAL, step_id=\"INICIO\")\n");
        b.append("        destino = self.saida / \"entrada\" / rt.ctx.execution_id\n        destino.mkdir(parents=True, exist_ok=True)\n");
        for (Map.Entry<Integer, List<Step>> e : bySession.entrySet()) {
            if (e.getValue().get(0).role() != Role.INIT) continue;
            b.append("        baixado, obs = Sessao").append(e.getKey()).append("(rt.act).executar(destino, rt.secrets)\n");
            b.append("        self.observacoes.update(obs)\n");
            b.append("        if baixado is not None:\n            self.entrada, self.entrada_sha256 = baixado.path, baixado.sha256\n");
        }
        b.append("\n    def process(self, rt: Runtime, item: TransactionItem) -> ProcessResult:\n");
        b.append("        if item.data.get(PROBLEMS_KEY):\n            raise BusinessRuleRejected(\"TABULAR_ROW_INVALID\")  # antes de qualquer ação\n");
        for (Map.Entry<Integer, List<Step>> e : bySession.entrySet()) {
            if (e.getValue().get(0).role() != Role.RECORD) continue;
            b.append("        obs = Sessao").append(e.getKey()).append("(rt.act).executar(item.data, rt.secrets)\n");
        }
        b.append("        return ProcessResult({\"enviado\": True, **({\"observacoes\": obs} if obs else {})})\n\n");
        b.append("    def end(self, rt: Runtime) -> None:\n");
        boolean anyEnd = false;
        for (Map.Entry<Integer, List<Step>> e : bySession.entrySet()) {
            if (e.getValue().get(0).role() != Role.END) continue;
            anyEnd = true;
            b.append("        self.observacoes.update(Sessao").append(e.getKey()).append("(rt.act).executar(rt.secrets))\n");
        }
        if (!anyEnd) b.append("        pass\n");
        b.append("\n    def gravar_observacoes(self) -> None:\n");
        b.append("        \"\"\"Observações lidas da página (etapas OBSERVE) + hash da planilha baixada; nunca valores de entrada.\"\"\"\n");
        b.append("        doc = {\"entrada_sha256\": self.entrada_sha256, \"observacoes\": self.observacoes}\n");
        b.append("        (self.saida / \"observacoes.json\").write_text(json.dumps(doc, ensure_ascii=False, indent=2) + \"\\n\", encoding=\"utf-8\")\n");
        return b.toString();
    }

    // ------------------------------------------------------------------------------------------------ main.py
    private static String main(CodegenConfig cfg) {
        StringBuilder b = new StringBuilder();
        b.append(PythonGenerator.HEADER);
        b.append("\"\"\"Execução: python main.py --saida <pasta> [--channel chrome|chromium] [--headless] [--console]").append(
                cfg.input().source() == CodegenConfig.Source.FILE ? " --entrada <arquivo>" : "").append("\"\"\"\n\n");
        b.append("from __future__ import annotations\n\nimport argparse\nimport json\nimport sys\nfrom pathlib import Path\n\n");
        b.append("from nekkora_rpa import Engine, EnvSecretProvider, load_config\nfrom nekkora_rpa.data import JsonDirectoryResultSink\n");
        // só o provedor que este projeto usa (ruff F401: nada importado sem uso)
        b.append("from nekkora_rpa.tabular import Column, ").append(cfg.input().source() == CodegenConfig.Source.FILE
                ? "TabularInputProvider" : "DeferredTabularInputProvider").append(", TabularSchema\n\n");
        b.append("from processo import Processo\n\n");
        b.append("AQUI = Path(__file__).resolve().parent\n");
        b.append("ESQUEMA = TabularSchema(\n    (\n");
        for (CodegenConfig.Column c : cfg.input().columns()) {
            b.append("        Column(").append(Py.str(c.header())).append(c.integerAsText() ? ", integer_as_text=True" : "").append("),\n");
        }
        b.append("    ),\n    sheet=").append(cfg.input().sheet() == null ? "None" : Py.str(cfg.input().sheet())).append(",\n)\n");
        b.append("SAIDA = {\"COMPLETED\": 0, \"COMPLETED_WITH_FAILURES\": 1, \"STOPPED\": 2, \"INIT_FAILED\": 3, \"CANCELLED\": 4}\n\n\n");
        b.append("def main(argv: list[str] | None = None) -> int:\n");
        b.append("    ap = argparse.ArgumentParser(description=").append(Py.str("Processo gerado: " + cfg.processName())).append(")\n");
        b.append("    ap.add_argument(\"--saida\", required=True, type=Path, help=\"pasta da execução (resultados, eventos, checkpoint)\")\n");
        if (cfg.input().source() == CodegenConfig.Source.FILE) b.append("    ap.add_argument(\"--entrada\", required=True, type=Path)\n");
        b.append("    ap.add_argument(\"--channel\", choices=[\"chromium\", \"chrome\"])\n");
        b.append("    ap.add_argument(\"--headless\", action=\"store_true\")\n    ap.add_argument(\"--console\", action=\"store_true\")\n");
        b.append("    a = ap.parse_args(argv)\n");
        b.append("    saida = a.saida.resolve()\n    saida.mkdir(parents=True, exist_ok=True)\n");
        b.append("    overrides: dict[str, object] = {}\n    if a.channel:\n        overrides[\"browser.channel\"] = a.channel\n");
        b.append("    if a.headless:\n        overrides[\"browser.headless\"] = True\n");
        b.append("    config = load_config(AQUI / \"config\" / \"execucao.toml\", overrides)\n");
        b.append("    processo = Processo(saida)\n");
        if (cfg.input().source() == CodegenConfig.Source.FILE) {
            b.append("    provider = TabularInputProvider.from_file(a.entrada, ESQUEMA)\n");
        } else {
            b.append("    provider = DeferredTabularInputProvider(lambda: processo.entrada, ESQUEMA)  # planilha baixada no INIT\n");
        }
        b.append("    engine = Engine(\n        processo,\n        provider,\n        JsonDirectoryResultSink(saida / \"resultados\"),\n        config,\n");
        b.append("        secrets=EnvSecretProvider(processo.required_secrets),\n        run_dir=saida / \"execucao\",\n        console=a.console,\n    )\n");
        b.append("    resumo = engine.run()\n    processo.gravar_observacoes()\n");
        b.append("    d = resumo.to_dict()\n");
        b.append("    print(json.dumps({k: d[k] for k in (\"execution_id\", \"status\", \"stop_code\", \"stop_detail\", \"counts\")}))\n");
        b.append("    return SAIDA[resumo.status.value]\n\n\n");
        b.append("if __name__ == \"__main__\":\n    sys.exit(main())\n");
        return b.toString();
    }

    private static String readme(CodegenConfig cfg) {
        return "<!-- GERADO pelo Nekkora Studio (STUDIO-011). -->\n# Processo gerado: " + cfg.processName() + "\n\n"
                + "Gerado a partir do modelo validado do Nekkora Studio (ver `manifest.json`: automação, revisão do plano, StepIds e hashes).\n"
                + "Correções vão para o MODELO (debug guiado) e o código é gerado de novo; arquivo editado à mão não é sobrescrito.\n\n"
                + InstallHint.readmeBlock(PythonGenerator.FRAMEWORK_REQUIREMENT, "python main.py --saida <pasta-da-execucao> --console")
                + "Saída: `resultados/` (um arquivo por linha), `execucao/` (eventos JSONL, checkpoint, resumo) e `observacoes.json`.\n";
    }

    // ------------------------------------------------------------------------------------------------ manifesto
    private static String manifest(Automation a, GuidedPlan plan, CodegenConfig cfg, List<Step> steps, Map<String, String> files) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schema", "nekkora-studio/codegen-manifest");
        m.put("version", "1.0.0");
        m.put("generator", PythonGenerator.GENERATOR);
        m.put("framework", PythonGenerator.FRAMEWORK_REQUIREMENT);
        m.put("automationId", a.id().value().toString());
        m.put("modelVersion", a.modelVersion());
        m.put("automationRevision", a.revision());
        m.put("planRevision", plan.planRevision());
        m.put("configRevision", cfg.revision());
        m.put("command", "python main.py --saida <pasta>");
        List<Object> list = new ArrayList<>();
        for (Step s : steps) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("stepId", s.step().id().value().toString());
            e.put("session", s.session());
            e.put("role", s.role().name());
            e.put("action", s.step().actionType().name());
            e.put("key", s.key());
            e.put("column", s.column());
            e.put("download", s.download());
            e.put("textPattern", s.textPattern() != null);
            e.put("decisionActor", s.step().targetDecision().map(d -> d.actor().name()).orElse(null));
            list.add(e);
        }
        m.put("steps", list);
        Map<String, Object> hashes = new LinkedHashMap<>();
        for (Map.Entry<String, String> f : files.entrySet()) {
            if (!f.getKey().equals("manifest.json")) hashes.put(f.getKey(), "sha256:" + CodegenConfig.sha256(f.getValue()));
        }
        m.put("files", hashes);
        m.put("exclusions", List.of("CELL_VALUES", "CREDENTIAL_VALUES", "LITERAL_VALUES", "HAND_WRITTEN_SELECTORS", "POSITIONAL_LOCATORS"));
        return Pretty.json(m) + "\n";
    }
}
