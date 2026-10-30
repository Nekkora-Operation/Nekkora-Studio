package com.nekkora.studio.codegen;

import com.nekkora.studio.codegen.PythonGenerator.PageName;
import com.nekkora.studio.codegen.PythonGenerator.Role;
import com.nekkora.studio.codegen.PythonGenerator.Step;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.guided.GuidedPlan;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * NEKKORA-CODEGEN-QUALITY-001 rodada 4 (Q12) — projeto TABULAR (planilha → uma transação por linha) no formato da rodada 3:
 * pacote {@code automation/} em inglês ({@code selectors.py} com {@code SelectorKey} e {@code TARGETS}, {@code pages.py}
 * com uma tela por sessão, {@code process.py} com {@code InputRow} e a ordem das sessões), {@code selectors.json} derivado
 * do mesmo plano (o runtime 0.2.0 só lê o JSON), {@code step_id} legível com o mapa para o StepId no manifesto, linha de
 * comando {@code --input/--output} com os nomes anteriores aceitos. Só texto: o pré-voo do {@link PythonGenerator} já
 * decidiu tudo. Cabeçalhos da planilha, rótulos da página, padrões de texto e códigos de recusa aparecem como estão (são
 * dados e contratos); as pastas da execução ({@code resultados/}, {@code execucao/}, {@code entrada/}) e as chaves de
 * {@code observacoes.json} também não mudam — são lidas pelas ferramentas que já existem.
 */
final class TabularEmitter {

    static final String CONFIG_FILE = SingleEmitter.CONFIG_FILE;
    /** Efeito do clique que baixa a planilha de entrada: nome do método, do {@code step_id} e da chave do alvo. */
    static final String DOWNLOAD_OPERATION = "download_spreadsheet";

    private TabularEmitter() {}

    /** Uma operação do código: a etapa, o step_id legível e o membro de {@code SelectorKey} do alvo (se tiver). */
    record Op(Step step, String traceId, String member, String label) {}

    /**
     * Método de uma tela. {@code params}: "nome: tipo"; {@code keywordOnly}: parâmetros só por nome (a linha da planilha);
     * {@code mark}: membro do 1º campo do formulário que o ÚLTIMO clique do método tem de substituir (prova de avanço), ou null.
     */
    record Meth(String name, String summary, String detail, List<String> params, boolean keywordOnly, String returns, List<Op> ops, String mark) {
        Meth(String name, String summary, String detail, List<String> params, boolean keywordOnly, String returns, List<Op> ops) {
            this(name, summary, detail, params, keywordOnly, returns, ops, null);
        }
    }

    record Pg(PageName name, String identity, String summary, List<Meth> methods) {}

    static Map<String, String> emit(Automation a, GuidedPlan plan, CodegenConfig cfg, List<Step> steps, Map<Integer, PageName> pageNames) {
        Map<String, String> members = members(steps);
        Map<String, String> py = identifiers(cfg);
        Map<String, String> traces = traces(steps, py);
        List<Pg> pages = pages(cfg, steps, pageNames, members, py, traces);
        String processClass = SingleEmitter.processClass(cfg.processName());
        Map<String, Map<String, Object>> targets = new LinkedHashMap<>();
        for (Step s : steps) if (s.key() != null && !targets.containsKey(s.key())) targets.put(s.key(), s.target());
        Map<String, String> files = new TreeMap<>();
        files.put("selectors.json", selectorsJson(targets));
        files.put(CONFIG_FILE, toml(cfg, steps));
        files.put(CodePlan.INIT_FILE, PythonGenerator.HEADER + "\"\"\"Generated automation: screens (pages.py), process (process.py) and target keys (selectors.py).\"\"\"\n");
        List<String[]> keyList = new ArrayList<>();
        members.forEach((k, m) -> keyList.add(new String[] {m, k}));
        files.put(CodePlan.SELECTORS_FILE, SemanticEmitter.selectors(PythonGenerator.HEADER, keyList, targets));
        files.put(CodePlan.PAGES_FILE, PyWrap.wrap(pagesPy(cfg, steps, pages)));
        files.put(CodePlan.PROCESS_FILE, PyWrap.wrap(processPy(cfg, steps, pages, py, processClass)));
        files.put("main.py", PyWrap.wrap(mainPy(cfg, processClass)));
        files.put("requirements.txt", InstallHint.requirementsTxtEnglish("CODEGEN-QUALITY-001", PythonGenerator.FRAMEWORK_REQUIREMENT));
        files.put("README.md", readme(cfg, steps, pages, members, processClass));
        files.put("manifest.json", manifest(a, plan, cfg, steps, pages, members, py, traces, targets, processClass, files));
        return files;
    }

    // ------------------------------------------------------------------------------------------------ nomes
    /** Membro de {@code SelectorKey} de cada chave (a tela e o elemento, sem repetir a tela), único, na ordem do modelo. */
    static Map<String, String> members(List<Step> steps) {
        Map<String, String> out = new LinkedHashMap<>();
        Set<String> taken = new HashSet<>();
        for (Step s : steps) {
            if (s.key() == null || out.containsKey(s.key())) continue;
            out.put(s.key(), CodeNaming.unique(CodePlan.member(s.key()), taken, "_"));
        }
        return out;
    }

    /** Identificador inglês de cada coluna (pela variável do modelo ligada a ela): {@code first_name}, {@code role_in_company}. */
    static Map<String, String> identifiers(CodegenConfig cfg) {
        Map<String, String> out = new LinkedHashMap<>();
        Set<String> taken = new HashSet<>(Set.of("row", "item", "self", "secrets", "rt", "data", "folder", "observations"));
        for (CodegenConfig.Column c : cfg.input().columns()) {
            String base = CodeNaming.safe(EnglishNames.identifier(c.variable()).snake());
            if (base.isEmpty() || !Character.isLetter(base.charAt(0))) base = "value";
            out.put(c.header(), CodeNaming.unique(base, taken, "_"));
        }
        return out;
    }

    /** {@code step_id} legível de cada etapa: {@code <tela>.<operação>} ("record_filling.fill_first_name"), único. */
    static Map<String, String> traces(List<Step> steps, Map<String, String> py) {
        Map<String, String> out = new LinkedHashMap<>();
        Set<String> taken = new HashSet<>();
        taken.add(CodePlan.START_TRACE);
        taken.addAll(CodePlan.PREVIOUS_START_TRACES);
        for (Step s : steps) {
            String base = s.prefix() + "." + operation(s, py);
            if (base.length() > 76) base = base.substring(0, 76).replaceAll("_+$", "");
            out.put(s.step().id().value().toString(), CodeNaming.unique(base, taken, "_"));
        }
        return out;
    }

    static String operation(Step s, Map<String, String> py) {
        String target = s.key() == null ? "" : CodePlan.rel(s.key());
        return switch (s.step().actionType()) {
            case NAVIGATE -> "open_page";
            case FILL -> "fill_" + (s.column() != null ? py.get(s.column())
                    : EnglishNames.identifier(s.credential().substring(s.credential().lastIndexOf('.') + 1)).snake());
            case CLICK -> s.download() ? DOWNLOAD_OPERATION : "click_" + target;
            case WAIT_FOR -> "wait_for_" + target;
            case OBSERVE -> s.textPattern() != null ? patternRead(s) : "read_" + target;
            default -> "step";
        };
    }

    /** Leitura por padrão de texto (sem alvo): o nome vem do título da etapa ("Ler o resultado" → {@code read_result}). */
    static String patternRead(Step s) {
        Optional<EnglishNames.Phrase> m = EnglishNames.method(s.step().name());
        String name = m.map(EnglishNames.Phrase::snake).orElse("");
        return name.startsWith("read") && name.matches("[a-z][a-z0-9_]*") ? name : "read_matching_text";
    }

    /** Texto de interface que identifica o alvo: rótulo associado, âncora da linha ou nome acessível (como está na página). */
    static String label(Step s) {
        ElementReference el = s.step().element().orElse(null);
        if (el == null) return "";
        for (LocatorCandidate c : el.locators()) {
            if (c.strategy() == LocatorStrategy.LABEL && PythonGenerator.labelText(c.expression()) != null) return PythonGenerator.labelText(c.expression());
        }
        String anchor = PythonGenerator.anchorText(el);
        if (anchor != null) return anchor;
        return el.accessibleName() == null ? "" : el.accessibleName().strip();
    }

    private static String quoted(Op op) {
        return op.label().isEmpty() ? "the element ``SelectorKey." + op.member() + "``" : CodePlan.quoted(op.label());
    }

    // ------------------------------------------------------------------------------------------------ telas e métodos
    private static List<Pg> pages(CodegenConfig cfg, List<Step> steps, Map<Integer, PageName> names, Map<String, String> members, Map<String, String> py,
                                  Map<String, String> traces) {
        Map<Integer, List<Step>> bySession = new TreeMap<>();
        for (Step s : steps) bySession.computeIfAbsent(s.session(), k -> new ArrayList<>()).add(s);
        Step firstRecordFill = steps.stream().filter(s -> s.role() == Role.RECORD && s.step().actionType() == ActionType.FILL).findFirst().orElse(null);
        List<Pg> out = new ArrayList<>();
        for (Map.Entry<Integer, List<Step>> e : bySession.entrySet()) {
            PageName name = names.get(e.getKey());
            List<Step> list = e.getValue();
            List<Op> ops = new ArrayList<>();
            for (Step s : list) ops.add(new Op(s, traces.get(s.step().id().value().toString()), s.key() == null ? null : members.get(s.key()), label(s)));
            String identity = list.stream().filter(s -> s.key() != null).map(s -> members.get(s.key())).findFirst().orElse(null);
            List<Meth> methods = new ArrayList<>();
            if (name.role() == Role.RECORD) {
                methods.add(recordMethod(ops, py, firstRecordFill == null ? null : members.get(firstRecordFill.key()), firstRecordFill == null ? "" : label(firstRecordFill)));
            } else {
                int lastClick = -1;
                for (int i = 0; i < ops.size(); i++) if (ops.get(i).step().step().actionType() == ActionType.CLICK && !ops.get(i).step().download()) lastClick = i;
                Set<String> taken = new HashSet<>();
                for (int i = 0; i < ops.size(); i++) {
                    boolean advance = name.role() == Role.INIT && i == lastClick && cfg.initAdvance() == CodegenConfig.Advance.REPLACED && firstRecordFill != null;
                    methods.add(stepMethod(cfg, ops.get(i), taken, advance ? firstRecordFill : null, members));
                }
            }
            out.add(new Pg(name, identity, pageSummary(name, ops), methods));
        }
        return out;
    }

    private static String pageSummary(PageName name, List<Op> ops) {
        String role = switch (name.role()) {
            case INIT -> "runs once, before the spreadsheet rows";
            case RECORD -> "runs once per spreadsheet row";
            case END -> "runs once, after the spreadsheet rows";
        };
        return "Screen of the session " + CodePlan.quoted(name.from()) + " (" + role + "): " + describe(ops) + ".";
    }

    /** O que as operações fazem, na ordem, com os rótulos da página: só o que o código faz. */
    static String describe(List<Op> ops) {
        List<String> out = new ArrayList<>();
        List<String> fills = new ArrayList<>();
        for (Op op : ops) {
            Step s = op.step();
            if (s.step().actionType() == ActionType.FILL) {
                fills.add(quoted(op));
                continue;
            }
            if (!fills.isEmpty()) {
                out.add("fills " + CodePlan.join(fills));
                fills.clear();
            }
            out.add(switch (s.step().actionType()) {
                case NAVIGATE -> "opens ``" + s.navigateUrl() + "``";
                case CLICK -> s.download() ? "downloads the spreadsheet (" + quoted(op) + ")" : "clicks " + quoted(op);
                case WAIT_FOR -> "waits for " + quoted(op);
                case OBSERVE -> s.textPattern() != null ? "reads the text that matches the declared pattern" : "reads " + quoted(op);
                default -> "runs a step";
            });
        }
        if (!fills.isEmpty()) out.add("fills " + CodePlan.join(fills));
        return CodePlan.join(out);
    }

    /** Sessão por registro: UM método, com um parâmetro por coluna da linha (só por nome), na ordem das etapas. */
    private static Meth recordMethod(List<Op> ops, Map<String, String> py, String firstFillMember, String firstFillLabel) {
        List<String> params = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        boolean secrets = false;
        boolean reads = false;
        List<String> fills = new ArrayList<>();
        for (Op op : ops) {
            Step s = op.step();
            if (s.column() != null && seen.add(py.get(s.column()))) params.add(py.get(s.column()) + ": str");
            if (s.credential() != null) secrets = true;
            if (s.step().actionType() == ActionType.OBSERVE && (s.textPattern() != null || s.key() != null)) reads = true;
            if (s.step().actionType() == ActionType.FILL && s.column() != null) fills.add(quoted(op));
        }
        if (secrets) params.add("secrets: SecretProvider");
        Op submit = ops.get(ops.size() - 1);
        String summary = describe(ops);
        summary = Character.toUpperCase(summary.charAt(0)) + summary.substring(1) + ".";
        if (!fills.isEmpty()) summary = summary.replaceFirst("^Fills " + java.util.regex.Pattern.quote(CodePlan.join(fills)),
                java.util.regex.Matcher.quoteReplacement("Fills " + CodePlan.join(fills) + " with the values of one spreadsheet row"));
        String detail = "Finishes when the field " + (firstFillLabel.isEmpty() ? "``SelectorKey." + firstFillMember + "``" : CodePlan.quoted(firstFillLabel))
                + " is replaced by a new one (proof that the form moved on). Submits only once: if the outcome is uncertain, the item stops "
                + "(``OUTCOME_UNKNOWN``) instead of clicking " + quoted(submit) + " again." + (reads ? " Returns the texts read, by ``step_id``." : "");
        return new Meth("submit_record", summary, detail, params, true, reads ? "dict[str, str]" : "None", ops, firstFillMember);
    }

    /** Sessões de início e de conclusão: um método por etapa, com o nome do que a etapa faz. */
    private static Meth stepMethod(CodegenConfig cfg, Op op, Set<String> taken, Step advanceMark, Map<String, String> members) {
        Step s = op.step();
        String name = CodeNaming.unique(op.traceId().substring(op.traceId().indexOf('.') + 1), taken, "_");
        String what = quoted(op);
        return switch (s.step().actionType()) {
            case NAVIGATE -> new Meth(name, "Opens ``" + s.navigateUrl() + "``.", null, List.of(), false, "None", List.of(op));
            case FILL -> new Meth(name, "Fills " + what + " with the credential ``" + s.credential() + "`` from the secret provider.", null,
                    List.of("secrets: SecretProvider"), false, "None", List.of(op));
            case CLICK -> {
                if (s.download()) {
                    boolean xlsx = "xlsx".equals(cfg.input().format());
                    yield new Meth(name, "Clicks " + what + " and saves the downloaded file in ``folder`` as ``" + cfg.input().fileName() + "``.",
                            (xlsx ? "The file is accepted only if it is an .xlsx (its first bytes are checked). " : "")
                                    + "Returns the downloaded file (path and SHA-256).", List.of("folder: Path"), false, "DownloadedFile", List.of(op));
                }
                if (advanceMark != null) {
                    String field = label(advanceMark).isEmpty() ? "``SelectorKey." + members.get(advanceMark.key()) + "``" : CodePlan.quoted(label(advanceMark));
                    yield new Meth(name, "Clicks " + what + ".", "Finishes when the form field " + field + " is replaced by a new one (proof that the "
                            + "form is ready for the first row).", List.of(), false, "None", List.of(op), members.get(advanceMark.key()));
                }
                yield new Meth(name, "Clicks " + what + ".", null, List.of(), false, "None", List.of(op));
            }
            case WAIT_FOR -> new Meth(name, "Waits until " + what + " is visible.", null, List.of(), false, "None", List.of(op));
            case OBSERVE -> {
                if (s.textPattern() != null) {
                    yield new Meth(name, "Reads the page text that matches the pattern declared in the generation config.", "Returns the text read.",
                            List.of(), false, "str", List.of(op));
                }
                if (cfg.observations()) yield new Meth(name, "Reads the text of " + what + ".", "Returns the text read.", List.of(), false, "str", List.of(op));
                yield new Meth(name, "Waits until " + what + " is visible (reading is off in the generation config).", null, List.of(), false, "None", List.of(op));
            }
            default -> throw new IllegalStateException("ação não suportada passou pelo pré-voo");
        };
    }

    // ------------------------------------------------------------------------------------------------ selectors.json / toml
    private static String selectorsJson(Map<String, Map<String, Object>> targets) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("schema", "nekkora-rpa/selectors");
        doc.put("version", "1.1.0");
        doc.put("system", PythonGenerator.SYSTEM);
        doc.put("targets", targets);
        return Pretty.json(doc) + "\n";
    }

    private static String toml(CodegenConfig cfg, List<Step> steps) {
        boolean download = steps.stream().anyMatch(Step::download);
        StringBuilder b = new StringBuilder();
        b.append("# GENERATED by Nekkora Studio. No credentials and no values: secrets come from NK_SECRET_<NAME>.\n");
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

    private static boolean credentials(List<Step> steps) {
        return steps.stream().anyMatch(s -> s.credential() != null);
    }

    // ------------------------------------------------------------------------------------------------ pages.py
    private static String pagesPy(CodegenConfig cfg, List<Step> steps, List<Pg> pages) {
        boolean download = steps.stream().anyMatch(Step::download);
        boolean xlsx = download && "xlsx".equals(cfg.input().format());
        StringBuilder b = new StringBuilder(PythonGenerator.HEADER);
        b.append("\"\"\"Screens of the automation: ").append(CodePlan.join(pages.stream().map(p -> p.name().className()).toList())).append(".\n\n");
        b.append("One class per session of the model, with what the process does on it. ``self.actions`` are the nekkora_rpa actions\n");
        b.append("(fill, click, download, read) and ``SelectorKey.<NAME>.value`` names an element of the target registry\n");
        b.append("(automation/selectors.py): the framework checks the identity and uniqueness of the element before acting. The\n");
        b.append("``step_id`` of each action links it to the Studio step (map in manifest.json).\n\"\"\"\n\n");
        b.append("from __future__ import annotations\n\n");
        if (download) b.append("from pathlib import Path\n");
        b.append("from typing import ClassVar\n\n");
        b.append("from nekkora_rpa import Actions\n");
        if (download) b.append("from nekkora_rpa.libs.actions import DownloadedFile\n");
        b.append("from nekkora_rpa.pages import BasePage\n");
        if (credentials(steps)) b.append("from nekkora_rpa.security.secrets import SecretProvider\n");
        b.append("\nfrom automation.selectors import SelectorKey\n");
        if (xlsx) {
            b.append("\nXLSX_SIGNATURE = b\"PK\\x03\\x04\"\n");
            b.append("\"\"\"First bytes of every .xlsx file: a downloaded file that does not start with them is refused.\"\"\"\n");
        }
        SemanticEmitter.base(b, EnumSet.noneOf(CodePlan.Helper.class), pages.stream().anyMatch(p -> p.identity() == null));
        for (Pg p : pages) page(b, cfg, p, xlsx);
        return b.toString();
    }

    private static void page(StringBuilder b, CodegenConfig cfg, Pg p, boolean xlsx) {
        b.append("\n\nclass ").append(p.name().className()).append("(PageBase):\n");
        SemanticEmitter.doc(b, "    ", p.summary());
        b.append('\n');
        if (p.identity() != null) b.append("    identity_key = SelectorKey.").append(p.identity()).append('\n');
        else SemanticEmitter.noIdentity(b, p.name().prefix());
        for (Meth m : p.methods()) method(b, cfg, m, xlsx);
    }

    private static void method(StringBuilder b, CodegenConfig cfg, Meth m, boolean xlsx) {
        if (m.keywordOnly() && !m.params().isEmpty()) {
            b.append("\n    def ").append(m.name()).append("(\n        self,\n        *,\n");
            for (String x : m.params()) b.append("        ").append(x).append(",\n");
            b.append("    ) -> ").append(m.returns()).append(":\n");
        } else {
            b.append("\n    def ").append(m.name()).append("(self").append(m.params().isEmpty() ? "" : ", " + String.join(", ", m.params())).append(") -> ")
                    .append(m.returns()).append(":\n");
        }
        SemanticEmitter.doc(b, "        ", m.summary(), m.detail());
        boolean collects = "dict[str, str]".equals(m.returns());
        if (collects) b.append("        observations: dict[str, str] = {}\n");
        Op last = m.ops().get(m.ops().size() - 1);
        Map<String, String> py = identifiersOf(m);
        for (Op op : m.ops()) {
            Step s = op.step();
            String sid = "step_id=" + Py.str(op.traceId());
            String key = op.member() == null ? null : "SelectorKey." + op.member() + ".value";
            switch (s.step().actionType()) {
                case NAVIGATE -> b.append("        self.actions.navigate(").append(Py.str(s.navigateUrl())).append(", ").append(sid).append(")\n");
                case FILL -> {
                    String value = s.column() != null ? py.get(s.column()) : "secrets.get(" + Py.str(s.credential()) + ")";
                    b.append("        self.actions.fill(").append(key).append(", ").append(value).append(", ").append(sid).append(")\n");
                }
                case CLICK -> {
                    if (s.download()) {
                        b.append("        return self.actions.download(\n            ").append(key).append(",\n            dest_dir=folder,\n            file_name=")
                                .append(Py.str(cfg.input().fileName())).append(",\n            magic=").append(xlsx ? "XLSX_SIGNATURE" : "None").append(",\n            ")
                                .append(sid).append(",\n        )\n");
                    } else if (m.mark() != null && op == last) {
                        // ENVIO / avanço: não idempotente; concluído só com prova de avanço (o 1º campo do formulário é outro nó)
                        b.append("        field_before = self.actions.mark(SelectorKey.").append(m.mark()).append(".value, ").append(sid).append(")\n");
                        b.append("        self.actions.click(").append(key).append(", ").append(sid)
                                .append(", postcondition=lambda: self.actions.replaced(field_before))\n");
                    } else {
                        b.append("        self.actions.click(").append(key).append(", ").append(sid).append(")\n");
                    }
                }
                case WAIT_FOR -> b.append("        self.actions.wait_visible(").append(key).append(", ").append(sid).append(")\n");
                case OBSERVE -> {
                    String read = s.textPattern() != null ? "self.actions.read_text_matching(" + Py.str(s.textPattern()) + ", " + sid + ")"
                            : cfg.observations() ? "self.actions.read_text(" + key + ", " + sid + ")" : null;
                    if (read == null) b.append("        self.actions.wait_visible(").append(key).append(", ").append(sid).append(")\n");
                    else if (collects) b.append("        observations[").append(Py.str(op.traceId())).append("] = ").append(read).append('\n');
                    else b.append("        return ").append(read).append('\n');
                }
                default -> throw new IllegalStateException("ação não suportada passou pelo pré-voo");
            }
        }
        if (collects) b.append("        return observations\n");
    }

    /** Parâmetro de cada coluna no método da linha (o nome inglês que a assinatura declara). */
    private static Map<String, String> identifiersOf(Meth m) {
        Map<String, String> out = new LinkedHashMap<>();
        int i = 0;
        for (Op op : m.ops()) {
            String col = op.step().column();
            if (col == null || out.containsKey(col)) continue;
            out.put(col, m.params().get(i++).split(":")[0]);
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ process.py
    private static String processPy(CodegenConfig cfg, List<Step> steps, List<Pg> pages, Map<String, String> py, String processClass) {
        boolean download = steps.stream().anyMatch(Step::download);
        boolean secrets = credentials(steps);
        Set<String> secretNames = new TreeSet<>();
        for (Step s : steps) if (s.credential() != null) secretNames.add(s.credential());
        Pg record = pages.stream().filter(p -> p.name().role() == Role.RECORD).findFirst().orElseThrow();
        Meth submit = record.methods().get(0);
        List<String> sessions = pages.stream().map(p -> CodePlan.quoted(p.name().from())).toList();
        StringBuilder b = new StringBuilder(PythonGenerator.HEADER);
        b.append("\"\"\"Process ").append(processClass).append(": one transaction per spreadsheet row.\n\n");
        StringBuilder flow = new StringBuilder();
        for (Pg p : pages) {
            if (!flow.isEmpty()) flow.append(", ");
            flow.append(CodePlan.quoted(p.name().from())).append(switch (p.name().role()) {
                case INIT -> " runs once before the rows";
                case RECORD -> " once per row";
                case END -> " once after the rows";
            });
        }
        for (String l : SemanticEmitter.wrapWords(Py.docText(flow.toString()) + ". A row with problems found by the spreadsheet reader is rejected before any action ("
                + "``TABULAR_ROW_INVALID``).", 116)) b.append(l).append('\n');
        b.append("\"\"\"\n\n");
        b.append("from __future__ import annotations\n\nimport json\nfrom collections.abc import Mapping\nfrom dataclasses import dataclass\nfrom pathlib import Path\n\n");
        b.append("from nekkora_rpa import ProcessResult, RpaProcess, Runtime, TransactionItem\n");
        b.append("from nekkora_rpa.errors import BusinessRuleRejected").append(secrets ? ", InvalidConfiguration" : "").append('\n');
        if (secrets) b.append("from nekkora_rpa.security.secrets import SecretProvider\n");
        b.append("from nekkora_rpa.tabular import PROBLEMS_KEY\n\n");
        b.append("from automation.pages import ").append(String.join(", ", pages.stream().map(p -> p.name().className()).sorted().toList())).append("\n\n");
        b.append("START_URL = ").append(Py.str(cfg.startUrl())).append("\n\n\n");
        // linha da planilha
        // rodada 5 (CQ-R5-01): sem repr automático — a linha traz dados pessoais
        b.append("@dataclass(frozen=True, repr=False)\nclass InputRow:\n");
        b.append("    \"\"\"One spreadsheet row: the columns the form uses (headers read in ``from_item``; the values are never logged).\"\"\"\n\n");
        for (CodegenConfig.Column c : cfg.input().columns()) b.append("    ").append(py.get(c.header())).append(": str\n");
        b.append("\n    @classmethod\n    def from_item(cls, item: TransactionItem) -> InputRow:\n");
        SemanticEmitter.doc(b, "        ", "Reads the row of ``item``; a row with problems found by the spreadsheet reader is rejected before any action.");
        b.append("        if item.data.get(PROBLEMS_KEY):\n            raise BusinessRuleRejected(\"TABULAR_ROW_INVALID\")\n");
        b.append("        return cls(\n");
        for (CodegenConfig.Column c : cfg.input().columns()) b.append("            ").append(py.get(c.header())).append("=_cell(item.data, ").append(Py.str(c.header())).append("),\n");
        b.append("        )\n\n\n");
        b.append("def _cell(data: Mapping[str, object], header: str) -> str:\n");
        b.append("    \"\"\"Text of the column ``header`` (the spreadsheet reader already rejected rows with empty or missing cells).\"\"\"\n");
        b.append("    value = data.get(header)\n    if not isinstance(value, str):\n        raise TypeError(f\"column {header!r} without text\")\n    return value\n");
        if (secrets) {
            b.append("\n\ndef _secrets(rt: Runtime) -> SecretProvider:\n");
            b.append("    \"\"\"Secret provider of the runtime (the values come from environment variables, never from the project).\"\"\"\n");
            b.append("    if rt.secrets is None:\n        raise InvalidConfiguration(detail=\"secret_provider_missing\")\n    return rt.secrets\n");
        }
        b.append("\n\nclass ").append(processClass).append("(RpaProcess):\n");
        SemanticEmitter.doc(b, "    ", "One transaction per spreadsheet row: " + String.join(" → ", sessions) + ".");
        b.append('\n');
        b.append("    name = ").append(Py.str(cfg.processName())).append('\n');
        b.append("    requires_browser = True\n");
        b.append("    required_secrets = (").append(String.join(", ", secretNames.stream().map(Py::str).toList())).append(secretNames.size() == 1 ? ",)" : ")")
                .append("\n\n");
        b.append("    def __init__(self, output: Path) -> None:\n        self.output = output\n        self.input_file: Path | None = None\n");
        b.append("        self.input_sha256: str | None = None\n        self.observations: dict[str, str] = {}\n\n");
        // init
        b.append("    def init(self, rt: Runtime) -> None:\n");
        List<Pg> init = pages.stream().filter(p -> p.name().role() == Role.INIT).toList();
        SemanticEmitter.doc(b, "        ", "Opens the start address" + (init.isEmpty() ? "" : " and goes through " + CodePlan.join(init.stream()
                .map(p -> CodePlan.quoted(p.name().from())).toList())) + " once, before the rows.");
        b.append("        rt.act.navigate(START_URL, step_id=").append(Py.str(CodePlan.START_TRACE)).append(")\n");
        if (download) {
            b.append("        download_folder = self.output / \"entrada\" / rt.ctx.execution_id\n");
            b.append("        download_folder.mkdir(parents=True, exist_ok=True)\n");
        }
        if (secrets && pages.stream().anyMatch(p -> p.name().role() != Role.RECORD && needsSecrets(p))) b.append("        secrets = _secrets(rt)\n");
        for (Pg p : init) calls(b, p, "        ");
        // process
        b.append("\n    def process(self, rt: Runtime, item: TransactionItem) -> ProcessResult:\n");
        SemanticEmitter.doc(b, "        ", "Goes through " + CodePlan.quoted(record.name().from()) + " with one spreadsheet row.");
        b.append("        row = InputRow.from_item(item)\n");
        String call = record.name().className() + "(rt.act)." + submit.name();
        boolean collects = "dict[str, str]".equals(submit.returns());
        b.append("        ").append(collects ? "observations = " : "").append(call).append("(\n");
        Map<String, String> param = identifiersOf(submit);
        for (Map.Entry<String, String> e : param.entrySet()) b.append("            ").append(e.getValue()).append("=row.").append(py.get(e.getKey())).append(",\n");
        if (needsSecrets(record)) b.append("            secrets=_secrets(rt),\n");
        b.append("        )\n");
        b.append("        return ProcessResult({\"enviado\": True").append(collects ? ", **({\"observacoes\": observations} if observations else {})" : "").append("})\n\n");
        // end
        b.append("    def end(self, rt: Runtime) -> None:\n");
        List<Pg> end = pages.stream().filter(p -> p.name().role() == Role.END).toList();
        if (end.isEmpty()) {
            SemanticEmitter.doc(b, "        ", "Nothing to do after the rows.");
        } else {
            SemanticEmitter.doc(b, "        ", "Goes through " + CodePlan.join(end.stream().map(p -> CodePlan.quoted(p.name().from())).toList()) + " once, after the rows.");
            if (secrets && end.stream().anyMatch(TabularEmitter::needsSecrets)) b.append("        secrets = _secrets(rt)\n");
            for (Pg p : end) calls(b, p, "        ");
        }
        b.append("\n    def write_observations(self) -> None:\n");
        SemanticEmitter.doc(b, "        ", "Writes ``observacoes.json`` in the run folder: the texts read from the page, by ``step_id``, and the SHA-256 of the "
                + "downloaded spreadsheet (never input values).");
        b.append("        document = {\"entrada_sha256\": self.input_sha256, \"observacoes\": self.observations}\n");
        b.append("        (self.output / \"observacoes.json\").write_text(json.dumps(document, ensure_ascii=False, indent=2) + \"\\n\", encoding=\"utf-8\")\n");
        return b.toString();
    }

    private static boolean needsSecrets(Pg p) {
        return p.methods().stream().anyMatch(m -> m.params().contains("secrets: SecretProvider"));
    }

    /** Chamadas de uma tela de início/conclusão, na ordem: leituras vão para as observações; o download dá a planilha. */
    private static void calls(StringBuilder b, Pg p, String indent) {
        String var = p.name().prefix();
        boolean many = p.methods().size() > 1;
        String target = many ? var : p.name().className() + "(rt.act)";
        if (many) b.append(indent).append(var).append(" = ").append(p.name().className()).append("(rt.act)\n");
        for (Meth m : p.methods()) {
            String args = m.params().isEmpty() ? "" : m.params().get(0).startsWith("folder") ? "download_folder" : "secrets";
            String c = target + "." + m.name() + "(" + args + ")";
            if ("DownloadedFile".equals(m.returns())) {
                b.append(indent).append("spreadsheet = ").append(c).append('\n');
                b.append(indent).append("self.input_file, self.input_sha256 = spreadsheet.path, spreadsheet.sha256\n");
            } else if ("str".equals(m.returns())) {
                b.append(indent).append("self.observations[").append(Py.str(m.ops().get(0).traceId())).append("] = ").append(c).append('\n');
            } else {
                b.append(indent).append(c).append('\n');
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ main.py
    static String command(CodegenConfig cfg) {
        return "python main.py --output <folder>" + (cfg.input().source() == CodegenConfig.Source.FILE ? " --input <spreadsheet>" : "");
    }

    static Map<String, Object> cli(CodegenConfig cfg) {
        Map<String, Object> c = new LinkedHashMap<>();
        Map<String, String> options = new LinkedHashMap<>();
        Map<String, String> legacy = new LinkedHashMap<>();
        if (cfg.input().source() == CodegenConfig.Source.FILE) {
            options.put("input", "--input");
            legacy.put("--entrada", "--input");
        }
        options.put("output", "--output");
        legacy.put("--saida", "--output");
        c.put("options", options);
        c.put("legacyAliases", legacy);
        return c;
    }

    private static String mainPy(CodegenConfig cfg, String processClass) {
        boolean file = cfg.input().source() == CodegenConfig.Source.FILE;
        StringBuilder b = new StringBuilder(PythonGenerator.HEADER);
        b.append("\"\"\"One transaction per spreadsheet row.\n\nUsage: ").append(command(cfg)).append("\n    [--channel chrome|chromium] [--headless] [--console]\n\n");
        for (String l : SemanticEmitter.wrapWords((file ? "The spreadsheet comes from --input" : "The process downloads the spreadsheet itself") + "; its columns: "
                + String.join(", ", cfg.input().columns().stream().map(CodegenConfig.Column::header).toList()) + ".", 116)) b.append(l).append('\n');
        b.append("The previous option names (").append(file ? "--entrada, --saida" : "--saida").append(") are still accepted.\n\"\"\"\n\n");
        b.append("from __future__ import annotations\n\nimport argparse\nimport json\nimport sys\nfrom pathlib import Path\n\n");
        b.append("from nekkora_rpa import Engine, EnvSecretProvider, load_config\nfrom nekkora_rpa.data import JsonDirectoryResultSink\n");
        b.append("from nekkora_rpa.tabular import Column, ").append(file ? "TabularInputProvider" : "DeferredTabularInputProvider").append(", TabularSchema\n\n");
        b.append("from automation.process import ").append(processClass).append("\n\n");
        b.append("HERE = Path(__file__).resolve().parent\n");
        b.append("INPUT_SCHEMA = TabularSchema(\n    (\n");
        for (CodegenConfig.Column c : cfg.input().columns()) {
            b.append("        Column(").append(Py.str(c.header())).append(c.integerAsText() ? ", integer_as_text=True" : "").append("),\n");
        }
        b.append("    ),\n    sheet=").append(cfg.input().sheet() == null ? "None" : Py.str(cfg.input().sheet())).append(",\n)\n");
        b.append("EXIT_CODES = {\"COMPLETED\": 0, \"COMPLETED_WITH_FAILURES\": 1, \"STOPPED\": 2, \"INIT_FAILED\": 3, \"CANCELLED\": 4}\n\n\n");
        b.append("def main(argv: list[str] | None = None) -> int:\n");
        SemanticEmitter.doc(b, "    ", "Reads the arguments, runs the process over the spreadsheet rows and prints the summary (no values); returns the exit code.");
        b.append("    parser = argparse.ArgumentParser(description=").append(Py.str("Generated process: " + cfg.processName())).append(")\n");
        b.append("    parser.add_argument(\n        \"--output\", \"--saida\", dest=\"output\", required=True, type=Path, help=\"run folder (results, events, checkpoint)\"\n    )\n");
        if (file) b.append("    parser.add_argument(\"--input\", \"--entrada\", dest=\"input\", required=True, type=Path, help=\"input spreadsheet\")\n");
        b.append("    parser.add_argument(\"--channel\", choices=[\"chromium\", \"chrome\"])\n");
        b.append("    parser.add_argument(\"--headless\", action=\"store_true\")\n    parser.add_argument(\"--console\", action=\"store_true\")\n");
        b.append("    args = parser.parse_args(argv)\n");
        b.append("    run_folder = args.output.resolve()\n    run_folder.mkdir(parents=True, exist_ok=True)\n");
        b.append("    overrides: dict[str, object] = {}\n    if args.channel:\n        overrides[\"browser.channel\"] = args.channel\n");
        b.append("    if args.headless:\n        overrides[\"browser.headless\"] = True\n");
        b.append("    config = load_config(HERE / \"config\" / \"execution.toml\", overrides)\n");
        b.append("    process = ").append(processClass).append("(run_folder)\n");
        if (file) b.append("    provider = TabularInputProvider.from_file(args.input, INPUT_SCHEMA)\n");
        else b.append("    provider = DeferredTabularInputProvider(lambda: process.input_file, INPUT_SCHEMA)  # spreadsheet downloaded in init\n");
        b.append("    engine = Engine(\n        process,\n        provider,\n        JsonDirectoryResultSink(run_folder / \"resultados\"),\n        config,\n");
        b.append("        secrets=EnvSecretProvider(process.required_secrets),\n        run_dir=run_folder / \"execucao\",\n        console=args.console,\n    )\n");
        b.append("    summary = engine.run()\n    process.write_observations()\n");
        b.append("    fields = summary.to_dict()\n");
        b.append("    published = (\"execution_id\", \"status\", \"stop_code\", \"stop_detail\", \"counts\")\n");
        b.append("    print(json.dumps({key: fields[key] for key in published}))\n");
        b.append("    return EXIT_CODES[summary.status.value]\n\n\n");
        b.append("if __name__ == \"__main__\":\n    sys.exit(main())\n");
        return b.toString();
    }

    // ------------------------------------------------------------------------------------------------ README.md
    private static String readme(CodegenConfig cfg, List<Step> steps, List<Pg> pages, Map<String, String> members, String processClass) {
        StringBuilder b = new StringBuilder();
        b.append("<!-- GENERATED by Nekkora Studio from the validated model and the declared generation config. -->\n# ").append(processClass).append("\n\n");
        b.append("**What it does.** One transaction per row of the input spreadsheet (").append(cfg.input().format()).append(", columns ")
                .append(String.join(", ", cfg.input().columns().stream().map(c -> "`" + c.header() + "`").toList())).append("). ");
        b.append(cfg.input().source() == CodegenConfig.Source.DOWNLOAD ? "The process downloads the spreadsheet itself, in the first session. "
                : "The spreadsheet comes from `--input`. ");
        b.append("Sessions: ").append(String.join(" → ", pages.stream().map(p -> CodePlan.quoted(p.name().from())).toList())).append(".\n\n");
        Set<String> secrets = new TreeSet<>();
        for (Step s : steps) if (s.credential() != null) secrets.add(s.credential());
        if (!secrets.isEmpty()) {
            b.append("**Secrets** (environment variables, never in the project): ")
                    .append(String.join(", ", secrets.stream().map(x -> "`NK_SECRET_" + x.toUpperCase().replace('.', '_').replace('-', '_') + "`").toList()))
                    .append(".\n\n");
        }
        b.append("Names in the code are in English. Page labels, spreadsheet headers, session names, text patterns, secret names and\n");
        b.append("rejection codes stay as they are: they are data and contracts (the mapping is in `manifest.json` → `naming`).\n\n");
        b.append("## How it is organized\n\n| File | Content |\n|---|---|\n");
        b.append("| `").append(CodePlan.PAGES_FILE).append("` | one class per session: ").append(String.join(", ", pages.stream().map(p -> "`" + p.name().className() + "`").toList()))
                .append(" |\n");
        b.append("| `").append(CodePlan.PROCESS_FILE).append("` | `InputRow` (one spreadsheet row) and `").append(processClass).append("`: the order of the sessions |\n");
        b.append("| `").append(CodePlan.SELECTORS_FILE).append("` | `SelectorKey` (one key per element) and how each element is located |\n");
        b.append("| `main.py` | command line and the spreadsheet columns |\n");
        b.append("| `selectors.json` | the same targets, read by the nekkora_rpa runtime (generated from the same plan; never edit by hand) |\n");
        b.append("| `manifest.json` | revisions, hashes, `step_id` ↔ Studio step map, names and keys |\n\n");
        b.append("## Screens and what each method does\n\n");
        for (Pg p : pages) {
            b.append("- **`").append(p.name().className()).append("`** — ").append(p.summary()).append('\n');
            for (Meth m : p.methods()) b.append("  - `").append(m.name()).append("()` — ").append(m.summary()).append('\n');
        }
        b.append("\n## How each element is located\n\n| Key | Strategy | Reading |\n|---|---|---|\n");
        Set<String> shown = new LinkedHashSet<>();
        for (Step s : steps) {
            if (s.key() == null || !shown.add(s.key())) continue;
            b.append("| `SelectorKey.").append(members.get(s.key())).append("` | ").append(LocatorPolicy.describe(s.target())).append(" | ")
                    .append(LocatorPolicy.kind(s.target()).label()).append(" |\n");
        }
        b.append("\nnekkora_rpa acts only if the locator finds EXACTLY one element with the expected identity; none, or more than one, is an "
                + "error (it never picks the first one).\n\n## Run\n\n");
        b.append(InstallHint.readmeBlockEnglish(PythonGenerator.FRAMEWORK_REQUIREMENT, command(cfg) + " --console"));
        b.append("Output: `resultados/` (one file per row), `execucao/` (JSONL events without values, checkpoint, summary) and `observacoes.json` "
                + "(texts read from the page and the SHA-256 of the spreadsheet).\n");
        b.append("Generating code is not running it: run only in an authorized environment (").append(cfg.environment()).append(").\n");
        return b.toString();
    }

    // ------------------------------------------------------------------------------------------------ manifest.json
    private static String manifest(Automation a, GuidedPlan plan, CodegenConfig cfg, List<Step> steps, List<Pg> pages, Map<String, String> members,
                                   Map<String, String> py, Map<String, String> traces, Map<String, Map<String, Object>> targets, String processClass,
                                   Map<String, String> files) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schema", "nekkora-studio/codegen-manifest");
        m.put("version", SingleEmitter.MANIFEST_VERSION);
        m.put("generator", PythonGenerator.GENERATOR);
        m.put("framework", PythonGenerator.FRAMEWORK_REQUIREMENT);
        m.put("execution", "tabular");
        m.put("language", "en");
        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("schema", SingleEmitter.LAYOUT);
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
        m.put("command", command(cfg));
        m.put("cli", cli(cfg));
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("format", cfg.input().format());
        input.put("sheet", cfg.input().sheet());
        input.put("source", cfg.input().source().name());
        input.put("fileName", cfg.input().fileName());
        List<Object> cols = new ArrayList<>();
        for (CodegenConfig.Column c : cfg.input().columns()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("header", c.header());
            x.put("variable", c.variable());
            x.put("field", py.get(c.header()));
            x.put("integerAsText", c.integerAsText());
            cols.add(x);
        }
        input.put("columns", cols);
        m.put("input", input);
        Map<String, String> methodOf = new LinkedHashMap<>();
        Map<String, String> pageOf = new LinkedHashMap<>();
        for (Pg p : pages) for (Meth mm : p.methods()) for (Op op : mm.ops()) {
            methodOf.put(op.step().step().id().value().toString(), mm.name());
            pageOf.put(op.step().step().id().value().toString(), p.name().className());
        }
        List<Object> list = new ArrayList<>();
        for (Step s : steps) {
            String id = s.step().id().value().toString();
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("stepId", id);
            e.put("traceId", traces.get(id));
            e.put("session", s.session());
            e.put("role", s.role().name());
            e.put("page", pageOf.get(id));
            e.put("method", methodOf.get(id));
            e.put("action", s.step().actionType().name());
            e.put("key", s.key());
            e.put("column", s.column());
            e.put("credential", s.credential() != null);
            e.put("download", s.download());
            e.put("textPattern", s.textPattern() != null);
            e.put("capability", s.download() ? "actions.download" : s.textPattern() != null ? "actions.read_text_matching" : SingleEmitter.capability(s.step().actionType()));
            e.put("decisionActor", s.step().targetDecision().map(d -> d.actor().name()).orElse(null));
            list.add(e);
        }
        m.put("steps", list);
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("schema", SingleEmitter.TRACE_SCHEMA);
        trace.put("version", "1.1.0");
        List<Object> mappings = new ArrayList<>();
        for (Step s : steps) {
            String id = s.step().id().value().toString();
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("traceId", traces.get(id));
            e.put("studioStepId", id);
            e.put("action", s.step().actionType().name());
            e.put("targetKey", s.key());
            e.put("pythonSymbol", pageOf.get(id) + "." + methodOf.get(id));
            // os step_id do gerador anterior desta etapa eram o próprio StepId (correlação de eventos antigos)
            e.put("previousTraceIds", List.of(id));
            mappings.add(e);
        }
        trace.put("mappings", mappings);
        Map<String, Object> start = new LinkedHashMap<>();
        start.put("traceId", CodePlan.START_TRACE);
        start.put("meaning", "open the start address (no Studio step)");
        start.put("previousTraceIds", CodePlan.PREVIOUS_START_TRACES);
        trace.put("synthetic", List.of(start));
        m.put("trace", trace);
        Map<String, Object> arch = new LinkedHashMap<>();
        List<Object> pg = new ArrayList<>();
        for (Pg p : pages) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("class", p.name().className());
            x.put("prefix", PythonGenerator.SYSTEM + "." + p.name().prefix());
            x.put("identityKey", p.identity() == null ? null : "SelectorKey." + p.identity());
            x.put("session", p.name().session());
            x.put("role", p.name().role().name());
            x.put("nameSource", p.name().fromRole() ? "SESSION_ROLE" : "SESSION_NAME");
            x.put("summary", p.summary());
            List<Object> ms = new ArrayList<>();
            for (Meth mm : p.methods()) {
                Map<String, Object> y = new LinkedHashMap<>();
                y.put("name", mm.name());
                y.put("summary", mm.summary());
                y.put("parameters", mm.params().stream().map(z -> z.split(":")[0]).toList());
                y.put("returns", mm.returns());
                y.put("traceIds", mm.ops().stream().map(Op::traceId).toList());
                ms.add(y);
            }
            x.put("methods", ms);
            pg.add(x);
        }
        arch.put("pages", pg);
        arch.put("models", List.of(Map.of("name", "InputRow", "readBy", "InputRow.from_item", "fields",
                cfg.input().columns().stream().map(c -> py.get(c.header())).toList())));
        arch.put("process", processClass);
        m.put("architecture", arch);
        m.put("naming", naming(cfg, pages, py, members, processClass));
        Map<String, Object> sel = new LinkedHashMap<>();
        sel.put("module", CodePlan.SELECTORS_FILE);
        sel.put("enum", "SelectorKey");
        sel.put("registry", "selectors.json");
        sel.put("system", PythonGenerator.SYSTEM);
        sel.put("equivalence", SingleEmitter.equivalence(targets));
        List<Object> keys = new ArrayList<>();
        for (Map.Entry<String, String> k : members.entrySet()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("member", k.getValue());
            e.put("key", k.getKey());
            List<String> tids = new ArrayList<>();
            List<String> sids = new ArrayList<>();
            for (Step s : steps) {
                if (!k.getKey().equals(s.key())) continue;
                tids.add(traces.get(s.step().id().value().toString()));
                sids.add(s.step().id().value().toString());
            }
            e.put("traceIds", tids);
            e.put("studioStepIds", sids);
            keys.add(e);
        }
        sel.put("keys", keys);
        m.put("selectors", sel);
        List<Object> locators = new ArrayList<>();
        for (Step s : steps) {
            if (s.key() == null) continue;
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("key", s.key());
            x.put("kind", LocatorPolicy.kind(s.target()).name());
            x.put("description", LocatorPolicy.describe(s.target()));
            locators.add(x);
        }
        m.put("locators", locators);
        Map<String, Object> hashes = new LinkedHashMap<>();
        for (Map.Entry<String, String> f : files.entrySet()) {
            if (!f.getKey().equals("manifest.json")) hashes.put(f.getKey(), "sha256:" + CodegenConfig.sha256(f.getValue()));
        }
        m.put("files", hashes);
        m.put("exclusions", List.of("CELL_VALUES", "CREDENTIAL_VALUES", "LITERAL_VALUES", "HAND_WRITTEN_SELECTORS", "POSITIONAL_LOCATORS",
                "STUDIO_STEP_IDS_IN_CODE"));
        return Pretty.json(m) + "\n";
    }

    /** De onde cada nome inglês saiu (nome da sessão, variável da coluna) e as palavras sem tradução no glossário. */
    private static Map<String, Object> naming(CodegenConfig cfg, List<Pg> pages, Map<String, String> py, Map<String, String> members, String processClass) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("glossary", EnglishNames.GLOSSARY);
        List<Object> untranslated = new ArrayList<>();
        Map<String, Object> process = new LinkedHashMap<>();
        process.put("from", cfg.processName());
        process.put("python", processClass);
        n.put("process", process);
        for (String w : EnglishNames.identifier(cfg.processName()).untranslated()) untranslated.add(pending(w, "process", processClass));
        List<Object> pg = new ArrayList<>();
        for (Pg p : pages) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("python", p.name().className());
            x.put("from", p.name().from());
            x.put("source", p.name().fromRole() ? "SESSION_ROLE" : "SESSION_NAME");
            pg.add(x);
            for (String w : p.name().untranslated()) untranslated.add(pending(w, "page", p.name().className()));
        }
        n.put("pages", pg);
        List<Object> ids = new ArrayList<>();
        for (CodegenConfig.Column c : cfg.input().columns()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("model", c.variable());
            x.put("header", c.header());
            x.put("python", py.get(c.header()));
            x.put("role", "input");
            ids.add(x);
            for (String w : EnglishNames.identifier(c.variable()).untranslated()) untranslated.add(pending(w, "variable", py.get(c.header())));
        }
        n.put("identifiers", ids);
        for (Map.Entry<String, String> k : members.entrySet()) {
            for (String w : EnglishNames.noun(CodePlan.rel(k.getKey())).untranslated()) untranslated.add(pending(w, "selector", "SelectorKey." + k.getValue()));
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
}
