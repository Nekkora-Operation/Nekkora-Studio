package com.nekkora.studio.codegen;

import com.nekkora.studio.codegen.CodePlan.Helper;
import com.nekkora.studio.codegen.CodePlan.Method;
import com.nekkora.studio.codegen.CodePlan.Op;
import com.nekkora.studio.codegen.CodePlan.Page;
import com.nekkora.studio.codegen.SingleGenerator.Step;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * NEKKORA-CODEGEN-QUALITY-001 — traduz o {@link CodePlan} para {@code paginas.py} e {@code processo.py}. Só texto:
 * nenhuma decisão de negócio nasce aqui. Regras de estilo: chamadas explícitas ao framework ({@code self.acoes.…} e
 * {@code self.elemento(…)}, da base {@code PaginaBase}; sem apelidos de uma letra), {@code step_id} = traceId legível (o UUID fica no manifesto), docstring curta por classe e
 * método, comentário só onde a intenção não é óbvia, nenhum valor de negócio e nenhum seletor no código.
 */
final class SemanticEmitter {
    private SemanticEmitter() {}

    static final String HEADER = "# GERADO pelo Nekkora Studio (\"Desenvolver automação\") a partir do modelo validado e do PDD pronto.\n"
            + "# Não edite: correções vão para o modelo ou o PDD e o código é gerado de novo (arquivo editado à mão não é sobrescrito).\n";

    // ------------------------------------------------------------------------------------------------ paginas.py
    static String pages(CodePlan plan) {
        StringBuilder b = new StringBuilder(HEADER);
        List<String> titles = plan.pages().stream().map(p -> p.className()).toList();
        b.append("\"\"\"Telas da automação: ").append(CodePlan.join(titles)).append(".\n\n");
        b.append("Cada tela é uma classe com o que o processo faz nela. ``self.acoes`` são as ações do nekkora_rpa (preencher, clicar,\n");
        b.append("ler) e ``self.elemento(\"nome\")`` aponta um elemento da tela pelo nome dele no registro selectors.json: o framework\n");
        b.append("confere a identidade e a unicidade do elemento antes de agir. O ``step_id`` de cada ação liga a ação à etapa do\n");
        b.append("Studio (mapa em manifest.json).\n\"\"\"\n\n");
        b.append("from __future__ import annotations\n\n");
        Set<String> text = new TreeSet<>();
        boolean rejects = false;
        boolean secrets = false;
        boolean dataclass = !plan.models().isEmpty();
        for (Page p : plan.pages()) {
            for (Helper h : p.helpers()) {
                if (h == Helper.TEXT || h == Helper.TEXT_IF_PRESENT) text.add("join_items");
                if (h == Helper.DATE || h == Helper.DATE_IF_PRESENT) {
                    text.add("parse_long_date");
                    rejects = true;
                }
            }
            for (Method m : p.methods()) {
                if (m.params().stream().anyMatch(x -> x.type().equals("SecretProvider"))) secrets = true;
                for (Op op : m.ops()) {
                    if (op.step().read() != null && op.step().read().expected() != null) {
                        rejects = true;
                        text.add("address".equals(op.step().read().kind()) ? "same_address" : "normalize_text");
                    }
                }
            }
        }
        if (dataclass) b.append("from dataclasses import dataclass\n\n");
        b.append("from nekkora_rpa import Actions\n");
        if (rejects) b.append("from nekkora_rpa.errors import BusinessRuleRejected\n");
        b.append("from nekkora_rpa.pages import BasePage\n");
        if (secrets) b.append("from nekkora_rpa.security.secrets import SecretProvider\n");
        if (!text.isEmpty()) b.append("from nekkora_rpa.text import ").append(String.join(", ", text)).append('\n');
        base(b);
        for (CodePlan.Model model : plan.models()) model(b, model);
        for (Page p : plan.pages()) page(b, plan, p);
        return b.toString();
    }

    /**
     * Base das telas (CQ-02): nomes legíveis para a API de Pages do framework — {@code acoes} = {@code BasePage.act},
     * {@code elemento(nome)} = {@code BasePage.k(nome)} — sem mudar o framework (o requisito 0.2.0/0.3.0 continua o mesmo).
     */
    private static void base(StringBuilder b) {
        b.append("\n\nclass PaginaBase(BasePage):\n");
        b.append("    \"\"\"Base das telas: nomes legíveis para a API de Pages do nekkora_rpa (o comportamento é o do framework).\"\"\"\n\n");
        b.append("    @property\n    def acoes(self) -> Actions:\n");
        b.append("        \"\"\"Ações do nekkora_rpa nesta tela (o mesmo objeto que ``BasePage.act``).\"\"\"\n        return self.act\n\n");
        b.append("    def elemento(self, nome: str) -> str:\n");
        b.append("        \"\"\"Chave do elemento ``nome`` desta tela no registro de alvos (``BasePage.k``).\"\"\"\n        return self.k(nome)\n");
    }

    private static void model(StringBuilder b, CodePlan.Model model) {
        boolean anyOptional = model.fields().stream().anyMatch(CodePlan.Field::optional);
        b.append("\n\n@dataclass(frozen=True)\nclass ").append(model.name()).append(":\n");
        b.append("    \"\"\"Dados lidos por ``").append(model.page()).append('.').append(model.method()).append("()``.");
        if (anyOptional) b.append("\n\n    ``None``: o dado não existe nesta página (ausência prevista no PDD, com fonte alternativa).\n    \"\"\"\n\n");
        else b.append("\"\"\"\n\n");
        for (CodePlan.Field f : model.fields()) b.append("    ").append(f.name()).append(": ").append(f.optional() ? "str | None" : "str").append('\n');
    }

    private static void page(StringBuilder b, CodePlan plan, Page p) {
        b.append("\n\nclass ").append(p.className()).append("(PaginaBase):\n");
        doc(b, "    ", p.summary(), String.join(" ", p.details()));
        b.append('\n');
        b.append("    prefix = ").append(Py.str(p.prefix())).append('\n');
        b.append("    identity = ").append(Py.str(p.identity())).append('\n');
        for (Method m : p.methods()) method(b, plan, p, m);
        helpers(b, p.helpers());
    }

    private static void method(StringBuilder b, CodePlan plan, Page p, Method m) {
        List<String> params = new ArrayList<>();
        for (CodePlan.Param x : m.params()) params.add(x.name() + ": " + x.type());
        b.append("\n    def ").append(m.name()).append("(self").append(params.isEmpty() ? "" : ", " + String.join(", ", params)).append(") -> ")
                .append(m.result().type()).append(":\n");
        doc(b, "        ", m.summary(), m.detail());
        Set<String> locals = new HashSet<>();
        for (CodePlan.Param x : m.params()) locals.add(x.name());
        boolean direct = m.result().kind() == CodePlan.Result.Kind.MODEL && plan.modern() && m.ops().stream().allMatch(o -> o.action()
                == com.nekkora.studio.domain.action.ActionType.OBSERVE && o.key() != null && o.step().read().expected() == null)
                && (m.ops().get(0).waitFirst() || m.ops().stream().noneMatch(Op::waitFirst));
        if (direct) {
            Op first = m.ops().get(0);
            if (first.waitFirst()) waitFor(b, p, first);
            b.append("        return ").append(m.result().model()).append("(\n");
            for (Op op : m.ops()) b.append("            ").append(op.step().output()).append('=').append(readExpr(op)).append(",\n");
            b.append("        )\n");
            return;
        }
        Op only = m.ops().size() == 1 ? m.ops().get(0) : null;
        if (m.result().kind() == CodePlan.Result.Kind.VALUE && only != null && only.action() == com.nekkora.studio.domain.action.ActionType.OBSERVE
                && !only.waitFirst() && (only.step().read() == null || only.step().read().expected() == null)) {
            // uma leitura só: devolve direto, sem variável intermediária
            String sid = "step_id=" + Py.str(only.traceId());
            String expr = !plan.modern() ? "self.acoes.read_text(" + k(only) + ", " + sid + ")"
                    : "address".equals(only.step().read().kind()) ? "self.acoes.current_address(" + sid + ")" : readExpr(only);
            b.append("        return ").append(expr).append('\n');
            return;
        }
        for (Op op : m.ops()) {
            if (op.waitFirst()) waitFor(b, p, op);
            op(b, plan, op, locals);
        }
        switch (m.result().kind()) {
            case VALUE -> b.append("        return ").append(m.result().var()).append('\n');
            case MODEL -> {
                List<String> args = new ArrayList<>();
                for (Op op : m.ops()) if (op.step().output() != null) args.add(op.step().output() + "=" + op.step().output());
                b.append("        return ").append(m.result().model()).append('(').append(String.join(", ", args)).append(")\n");
            }
            case NONE -> {
            }
        }
    }

    private static void waitFor(StringBuilder b, Page p, Op op) {
        if (op.waitKey().equals(p.identity())) {
            b.append("        self.wait_loaded(step_id=").append(Py.str(op.waitTrace())).append(")\n");
        } else {
            b.append("        self.acoes.wait_visible(self.elemento(").append(Py.str(op.waitKey())).append("), step_id=").append(Py.str(op.waitTrace())).append(")\n");
        }
    }

    /** Docstring: resumo e, se houver, um parágrafo de detalhe (PEP 257). */
    static void doc(StringBuilder b, String indent, String summary, String detail) {
        if (detail == null || detail.isEmpty()) {
            doc(b, indent, summary);
            return;
        }
        String q = "\"\"\"";
        List<String> first = wrapWords(summary, 116 - indent.length() - 3);
        b.append(indent).append(q).append(first.get(0)).append('\n');
        for (int i = 1; i < first.size(); i++) b.append(indent).append(first.get(i)).append('\n');
        b.append('\n');
        for (String l : wrapWords(detail, 116 - indent.length())) b.append(indent).append(l).append('\n');
        b.append(indent).append(q).append('\n');
    }

    /** Docstring de uma linha, ou várias (quebra em palavras até a coluna 116). */
    static void doc(StringBuilder b, String indent, String text) {
        List<String> lines = wrapWords(text, 116 - indent.length() - 3);
        String q = "\"\"\"";
        if (lines.size() == 1) {
            b.append(indent).append(q).append(lines.get(0)).append(q).append('\n');
            return;
        }
        b.append(indent).append(q).append(lines.get(0)).append('\n');
        for (int i = 1; i < lines.size(); i++) b.append(indent).append(lines.get(i)).append('\n');
        b.append(indent).append(q).append('\n');
    }

    static void comment(StringBuilder b, String indent, String text) {
        for (String l : wrapWords(text, 118 - indent.length() - 2)) b.append(indent).append("# ").append(l).append('\n');
    }

    static List<String> wrapWords(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + w.length() > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(w);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private static String k(Op op) {
        return "self.elemento(" + Py.str(op.key()) + ")";
    }

    private static void op(StringBuilder b, CodePlan plan, Op op, Set<String> locals) {
        Step s = op.step();
        String sid = "step_id=" + Py.str(op.traceId());
        switch (op.action()) {
            case NAVIGATE -> b.append("        self.acoes.navigate(").append(Py.str(s.navigateUrl())).append(", ").append(sid).append(")\n");
            case FILL -> {
                String value = s.input() != null ? s.input() : "segredos.get(" + Py.str(s.credential()) + ")";
                b.append("        self.acoes.fill(").append(k(op)).append(", ").append(value).append(", ").append(sid).append(")\n");
            }
            case CLICK -> interaction(b, op, "click", "", locals);
            case PRESS_KEY -> interaction(b, op, "press", ", \"Enter\"", locals);
            case WAIT_FOR -> b.append("        self.acoes.wait_visible(").append(k(op)).append(", ").append(sid).append(")\n");
            case OBSERVE -> read(b, plan, op);
            default -> throw new IllegalStateException("ação não suportada passou pelo pré-voo");
        }
    }

    private static String readExpr(Op op) {
        String tid = Py.str(op.traceId());
        return switch (CodePlan.helper(op)) {
            case TEXT -> "self._texto(" + Py.str(op.key()) + ", step_id=" + tid + ")";
            case TEXT_IF_PRESENT -> "self._texto_se_existir(" + Py.str(op.key()) + ", step_id=" + tid + ")";
            case DATE -> "self._data(" + Py.str(op.key()) + ", step_id=" + tid + ")";
            case DATE_IF_PRESENT -> "self._data_se_existir(" + Py.str(op.key()) + ", step_id=" + tid + ")";
        };
    }

    private static void read(StringBuilder b, CodePlan plan, Op op) {
        Step s = op.step();
        String var = s.output();
        String sid = "step_id=" + Py.str(op.traceId());
        if (!plan.modern()) {
            b.append("        ").append(var).append(" = self.acoes.read_text(").append(k(op)).append(", ").append(sid).append(")\n");
            return;
        }
        if ("address".equals(s.read().kind())) {
            b.append("        ").append(var).append(" = self.acoes.current_address(").append(sid).append(")\n");
        } else {
            b.append("        ").append(var).append(" = ").append(readExpr(op)).append('\n');
        }
        String expected = s.read().expected();
        if (expected != null) {
            if ("address".equals(s.read().kind())) {
                b.append("        if not same_address(").append(var).append(", ").append(expected).append("):\n");
                b.append("            raise BusinessRuleRejected(\"ENDERECO_DIFERENTE_DO_ESPERADO\", ").append(sid).append(")\n");
            } else {
                b.append("        if ").append(op.optional() ? var + " is None or " : "").append("normalize_text(").append(var).append(") != normalize_text(")
                        .append(expected).append("):\n");
                b.append("            raise BusinessRuleRejected(\"VALOR_DIFERENTE_DO_ESPERADO\", ").append(sid).append(")\n");
            }
        }
    }

    private static void interaction(StringBuilder b, Op op, String verb, String keyArg, Set<String> locals) {
        Step s = op.step();
        String sid = "step_id=" + Py.str(op.traceId());
        String call = "self.acoes." + verb + "(" + k(op) + keyArg + ", " + sid;
        SingleConfig.Effect e = s.effect();
        if (op.retry() == CodePlan.Retry.AUTHENTICATION || op.retry() == CodePlan.Retry.SESSION_END) {
            // sem efeito de negócio, mas muda o acesso: uma vez só (resultado incerto → OUTCOME_UNKNOWN, nunca repetido)
            boolean otherScreen = s.nextKey() != null && s.nextOnOtherScreen();
            if (!otherScreen) {
                b.append("        ").append(call).append(")\n");
                return;
            }
            b.append("        ").append(call).append(", postcondition=lambda: self.acoes.exists(").append(Py.str(s.nextKey())).append("))\n");
            return;
        }
        if ("NONE".equals(e.effect())) {
            if (s.press() != null && s.press().navigates()) {
                // a gravação viu o documento mudar: o avanço é provado pela troca de endereço
                String before = CodeNaming.unique("endereco_antes", locals, "_");
                b.append("        ").append(before).append(" = self.acoes.current_address(").append(sid).append(")\n");
                b.append("        ").append(call).append(", idempotent=True, postcondition=lambda: self.acoes.current_address() != ").append(before).append(")\n");
                return;
            }
            boolean otherScreen = s.nextKey() != null && s.nextOnOtherScreen();
            b.append("        ").append(call).append(", idempotent=True");
            if (otherScreen) b.append(", postcondition=lambda: self.acoes.exists(").append(Py.str(s.nextKey())).append(")");
            b.append(")\n");
            return;
        }
        // efeito externo: uma vez só (não idempotente), concluído apenas com a confirmação declarada na especificação
        switch (e.postcondition()) {
            case "TARGET_HIDDEN" -> b.append("        ").append(call).append(", postcondition=lambda: not self.acoes.is_visible(").append(k(op)).append("))\n");
            case "FIRST_FIELD_REPLACED" -> {
                String mark = CodeNaming.unique("campo_antes", locals, "_");
                b.append("        ").append(mark).append(" = self.acoes.mark(").append(Py.str(s.firstFillKey())).append(", ").append(sid).append(")\n");
                b.append("        ").append(call).append(", postcondition=lambda: self.acoes.replaced(").append(mark).append("))\n");
            }
            case "NEXT_TARGET_VISIBLE" -> b.append("        ").append(call).append(", postcondition=lambda: self.acoes.exists(").append(Py.str(s.nextKey()))
                    .append("))\n");
            default -> throw new IllegalStateException("confirmação desconhecida passou pelo pré-voo");
        }
    }

    private static void helpers(StringBuilder b, Set<Helper> helpers) {
        boolean text = helpers.contains(Helper.TEXT) || helpers.contains(Helper.TEXT_IF_PRESENT);
        boolean date = helpers.contains(Helper.DATE) || helpers.contains(Helper.DATE_IF_PRESENT);
        if (text) {
            b.append("\n    def _texto(self, elemento: str, *, step_id: str) -> str:\n");
            b.append("        \"\"\"Texto exibido pelo elemento (itens de uma lista unidos por \"; \").\"\"\"\n");
            b.append("        return join_items(self.acoes.read_items(self.elemento(elemento), step_id=step_id))\n");
        }
        if (helpers.contains(Helper.TEXT_IF_PRESENT)) {
            b.append("\n    def _texto_se_existir(self, elemento: str, *, step_id: str) -> str | None:\n");
            b.append("        \"\"\"Como ``_texto``; ``None`` se o elemento não existe nesta página (ambíguo continua sendo erro).\"\"\"\n");
            b.append("        if not self.acoes.exists(self.elemento(elemento), step_id=step_id):\n            return None\n");
            b.append("        return self._texto(elemento, step_id=step_id)\n");
        }
        if (date) {
            b.append("\n    def _data(self, elemento: str, *, step_id: str) -> str:\n");
            b.append("        \"\"\"Data por extenso do elemento como AAAA-MM-DD; incompleta, ambígua ou impossível recusa o item.\"\"\"\n");
            b.append("        data = parse_long_date(\" \".join(self.acoes.read_items(self.elemento(elemento), step_id=step_id)))\n");
            b.append("        if data is None:\n            raise BusinessRuleRejected(\"DATA_INCOMPLETA_OU_AMBIGUA\", step_id=step_id)\n");
            b.append("        return data\n");
        }
        if (helpers.contains(Helper.DATE_IF_PRESENT)) {
            b.append("\n    def _data_se_existir(self, elemento: str, *, step_id: str) -> str | None:\n");
            b.append("        \"\"\"Como ``_data``; ``None`` se o elemento não existe nesta página.\"\"\"\n");
            b.append("        if not self.acoes.exists(self.elemento(elemento), step_id=step_id):\n            return None\n");
            b.append("        return self._data(elemento, step_id=step_id)\n");
        }
    }

    // ------------------------------------------------------------------------------------------------ processo.py
    static String process(CodePlan plan, SingleConfig cfg, List<Step> steps) {
        CodePlan.Process pr = plan.process();
        Set<String> secrets = new TreeSet<>();
        for (Step s : steps) if (s.credential() != null) secrets.add(s.credential());
        boolean inputs = !cfg.inputs().isEmpty();
        boolean sheet = cfg.spreadsheet();
        StringBuilder b = new StringBuilder(HEADER);
        b.append("\"\"\"Processo ").append(pr.className()).append(": ").append(cfg.list() ? "uma transação por linha da lista de entrada" : "execução única")
                .append(".\"\"\"\n\n");
        b.append("from __future__ import annotations\n\n");
        if (inputs) b.append("from collections.abc import Mapping\nfrom dataclasses import dataclass\n\n");
        b.append("from nekkora_rpa import ProcessResult, RpaProcess, Runtime, TransactionItem\n");
        List<String> errs = new ArrayList<>();
        if (inputs || sheet) errs.add("BusinessRuleRejected");
        if (!secrets.isEmpty()) errs.add("InvalidConfiguration");
        if (!errs.isEmpty()) b.append("from nekkora_rpa.errors import ").append(String.join(", ", errs)).append('\n');
        if (!secrets.isEmpty()) b.append("from nekkora_rpa.security.secrets import SecretProvider\n");
        b.append('\n');
        Set<String> imports = new TreeSet<>(SingleEmitter::natural);
        for (CodePlan.Call c : pr.calls()) imports.add(c.page());
        for (CodePlan.Param p : pr.rowParams()) if (plan.models().stream().anyMatch(m -> m.name().equals(p.type()))) imports.add(p.type());
        b.append("from paginas import ").append(String.join(", ", imports)).append("\n\n");
        b.append("URL_INICIAL = ").append(Py.str(cfg.startUrl())).append('\n');
        if (inputs) {
            b.append("ENTRADAS = (").append(String.join(", ", cfg.inputs().stream().map(Py::str).toList())).append(cfg.inputs().size() == 1 ? ",)" : ")")
                    .append('\n');
            b.append("\n\n@dataclass(frozen=True, repr=False)\nclass Entrada:\n");
            b.append("    \"\"\"").append(cfg.list() ? "Uma linha da lista de entrada" : "Entradas da execução").append(" (os valores nunca são registrados).\"\"\"\n\n");
            for (String in : cfg.inputs()) b.append("    ").append(in).append(": str\n");
            b.append("\n    @classmethod\n    def de_item(cls, dados: Mapping[str, object]) -> Entrada:\n");
            b.append("        \"\"\"Recusa o item antes de qualquer ação se uma entrada faltar ou estiver vazia.\"\"\"\n");
            b.append("        valores: dict[str, str] = {}\n        for nome in ENTRADAS:\n            valor = dados.get(nome)\n");
            b.append("            if not isinstance(valor, str) or not valor.strip():\n                raise BusinessRuleRejected(\"ENTRADA_INVALIDA\")\n");
            b.append("            valores[nome] = valor\n        return cls(**valores)\n");
        }
        if (sheet) row(b, pr);
        if (!secrets.isEmpty()) {
            b.append("\n\ndef _segredos(rt: Runtime) -> SecretProvider:\n");
            b.append("    \"\"\"Provedor de segredos do runtime (os valores vêm de variáveis de ambiente, nunca do projeto).\"\"\"\n");
            b.append("    if rt.secrets is None:\n        raise InvalidConfiguration(detail=\"sem_provedor_de_segredos\")\n    return rt.secrets\n");
        }
        b.append("\n\nclass ").append(pr.className()).append("(RpaProcess):\n");
        b.append("    \"\"\"").append(pr.summary()).append("\"\"\"\n\n");
        b.append("    name = ").append(Py.str(cfg.processName())).append('\n');
        b.append("    requires_browser = True\n");
        b.append("    required_secrets = (").append(String.join(", ", secrets.stream().map(Py::str).toList())).append(secrets.size() == 1 ? ",)" : ")").append("\n\n");
        if (!cfg.list()) {
            b.append("    def init(self, rt: Runtime) -> None:\n        \"\"\"Abre o endereço inicial uma vez, antes da execução.\"\"\"\n");
            b.append("        rt.act.navigate(URL_INICIAL, step_id=").append(Py.str(CodePlan.START_TRACE)).append(")\n\n");
        }
        b.append("    def process(self, rt: Runtime, item: TransactionItem) -> ProcessResult:\n");
        b.append("        \"\"\"").append(cfg.list() ? "Um item: começa no endereço inicial e faz as etapas na ordem do processo." : "Faz as etapas na ordem do processo.")
                .append("\"\"\"\n");
        if (inputs) b.append("        entrada = Entrada.de_item(item.data)\n");
        if (cfg.list()) b.append("        rt.act.navigate(URL_INICIAL, step_id=").append(Py.str(CodePlan.START_TRACE)).append(")\n");
        if (!secrets.isEmpty()) b.append("        segredos = _segredos(rt)\n");
        Set<String> created = new HashSet<>();
        for (CodePlan.Call c : pr.calls()) {
            String target;
            if (c.pageVar() != null) {
                if (created.add(c.pageVar())) b.append("        ").append(c.pageVar()).append(" = ").append(c.page()).append("(rt.act)\n");
                target = c.pageVar();
            } else {
                target = c.page() + "(rt.act)";
            }
            String call = target + "." + c.method() + "(" + String.join(", ", c.args()) + ")";
            b.append("        ").append(c.resultVar() == null ? call : c.resultVar() + " = " + call).append('\n');
        }
        if (sheet) {
            b.append("        return ProcessResult(linha_da_planilha(").append(String.join(", ", pr.rowParams().stream().map(CodePlan.Param::name).toList()))
                    .append("))\n");
        } else if (!pr.outputs().isEmpty()) {
            List<String> kv = new ArrayList<>();
            pr.outputs().forEach((k, v) -> kv.add(Py.str(k) + ": " + v));
            b.append("        return ProcessResult({").append(String.join(", ", kv)).append("})\n");
        } else {
            b.append("        return ProcessResult({})\n");
        }
        return b.toString();
    }

    private static void row(StringBuilder b, CodePlan.Process pr) {
        List<String> params = new ArrayList<>();
        for (CodePlan.Param p : pr.rowParams()) params.add(p.name() + ": " + p.type());
        b.append("\n\ndef linha_da_planilha(").append(String.join(", ", params)).append(") -> dict[str, object]:\n");
        List<String> rules = new ArrayList<>();
        for (CodePlan.Column c : pr.columns()) {
            if (c.fallback() != null) rules.add("    - " + c.header() + ": se faltar na página, vale " + CodePlan.quoted(c.fallbackLabel()) + ".\n");
        }
        b.append("    \"\"\"Uma linha da planilha pela regra do PDD; coluna que continuar vazia recusa o item com o nome do campo (o\n");
        b.append("    conteúdo nunca vai ao motivo) e nada é inventado.");
        if (rules.isEmpty()) {
            b.append("\"\"\"\n");
        } else {
            b.append("\n\n");
            rules.forEach(b::append);
            b.append("    \"\"\"\n");
        }
        b.append("    linha: dict[str, object] = {\n");
        for (CodePlan.Column c : pr.columns()) {
            b.append("        ").append(Py.str(c.field())).append(": ").append(c.primary()).append(c.fallback() == null ? "" : " or " + c.fallback()).append(",\n");
        }
        b.append("    }\n");
        b.append("    for campo, valor in linha.items():\n        if not valor:\n");
        b.append("            raise BusinessRuleRejected(f\"DADO_OBRIGATORIO_VAZIO:{campo}\")\n    return linha\n");
    }
}
