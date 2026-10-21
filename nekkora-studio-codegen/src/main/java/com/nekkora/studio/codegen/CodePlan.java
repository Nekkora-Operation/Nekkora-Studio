package com.nekkora.studio.codegen;

import com.nekkora.studio.codegen.SingleGenerator.Step;
import com.nekkora.studio.domain.action.ActionType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NEKKORA-CODEGEN-QUALITY-001 — representação intermediária TIPADA do projeto, montada ANTES da emissão a partir da
 * configuração validada, das etapas prontas do pré-voo e dos nomes ({@link CodeNames}). Os emissores só traduzem este
 * plano para texto; o manifesto o descreve ({@code architecture}, {@code trace}). Ordem das operações = ordem do modelo.
 *
 * @param modern  configuração 1.2.0 (framework 0.3.0: leitura tipada, Enter, planilha); senão 1.1.0 (0.2.0)
 * @param list    uma transação por linha da lista (planilha de saída)
 * @param pages   Pages na ordem em que aparecem
 * @param models  dados lidos por um método (dataclass), só quando um método lê dois ou mais dados
 * @param process o processo (classe, chamadas, linha da planilha)
 * @param traces  mapa traceId ↔ StepId (bijetivo; o {@code INICIO} sintético fica fora)
 */
public record CodePlan(boolean modern, boolean list, List<Page> pages, List<Model> models, Process process, List<Trace> traces) {

    public static final String START_TRACE = "INICIO";

    public record Page(String className, String prefix, String identity, String summary, List<String> details, List<Method> methods,
                       Set<Helper> helpers, CodeNames.Source nameSource, String boundary) {}

    /** {@code summary}: o passo do PDD (título revisado do bloco) ou, sem ele, o que o método faz; {@code detail}: o que faz. */
    public record Method(String page, String name, List<Param> params, Result result, String summary, String detail, List<Op> ops, CodeNames.Source nameSource) {}

    public record Param(String name, String type) {}

    /** {@code NONE}: sem retorno; {@code VALUE}: um dado ({@code var}); {@code MODEL}: dataclass {@code model}. */
    public record Result(Kind kind, String var, String type, String model) {
        public enum Kind { NONE, VALUE, MODEL }

        static Result none() {
            return new Result(Kind.NONE, null, "None", null);
        }
    }

    public record Model(String name, String page, String method, List<Field> fields) {}

    public record Field(String name, boolean optional) {}

    /** Leitura de texto/data por um helper da Page (0.3.0). */
    public enum Helper { TEXT, TEXT_IF_PRESENT, DATE, DATE_IF_PRESENT }

    /**
     * Uma operação (uma etapa do modelo). {@code key} é relativa ao prefixo da Page; {@code waitFirst} = antes dela, a
     * espera de "página pronta" pelo alvo {@code waitKey} (traceId {@code waitTrace}).
     */
    public record Op(Step step, String traceId, String key, boolean waitFirst, String waitKey, String waitTrace, String waitLabel) {
        public ActionType action() {
            return step.step().actionType();
        }

        public boolean optional() {
            return step.read() != null && step.read().optional();
        }
    }

    public record Call(String page, String method, String pageVar, List<String> args, String resultVar) {}

    /** Coluna da planilha: {@code primary} e (opcional) {@code fallback} são referências Python já resolvidas. */
    public record Column(String field, String header, String primary, String fallback, String fallbackLabel) {}

    public record Process(String className, String summary, List<Call> calls, List<Param> rowParams, List<Column> columns, Map<String, String> outputs) {}

    public record Trace(String traceId, String stepId, String action, String targetKey, String symbol) {}

    // ------------------------------------------------------------------------------------------------ construção

    static CodePlan build(SingleConfig cfg, List<Step> steps, CodeNames names, String processClass) {
        boolean modern = cfg.modern();
        Map<String, String> traceOf = traceIds(steps, names);
        List<Page> pages = new ArrayList<>();
        List<Model> models = new ArrayList<>();
        Map<String, List<SingleConfig.Group>> byPage = new LinkedHashMap<>();
        for (SingleConfig.Group g : cfg.groups()) byPage.computeIfAbsent(g.page(), k -> new ArrayList<>()).add(g);
        Set<String> modelNames = new HashSet<>();
        Map<String, Method> methodOf = new LinkedHashMap<>(); // grupo → método
        for (Map.Entry<String, List<SingleConfig.Group>> e : byPage.entrySet()) {
            String cls = e.getKey();
            CodeNames.PageInfo info = names.page(cls);
            List<Step> pageSteps = steps.stream().filter(s -> s.group().page().equals(cls)).toList();
            String identity = pageSteps.stream().filter(s -> s.key() != null && !(s.read() != null && s.read().optional())).map(s -> rel(s.key())).findFirst()
                    .orElse(pageSteps.stream().filter(s -> s.key() != null).map(s -> rel(s.key())).findFirst().orElse(""));
            List<Method> methods = new ArrayList<>();
            Set<Helper> helpers = new LinkedHashSet<>();
            for (SingleConfig.Group g : e.getValue()) {
                List<Step> mine = steps.stream().filter(s -> s.group().equals(g)).toList();
                Method m = method(cfg, g, mine, traceOf, identity, names, modelNames, models, cls);
                for (Op op : m.ops()) if (modern && op.action() == ActionType.OBSERVE && op.key() != null) helpers.add(helper(op));
                methods.add(m);
                methodOf.put(g.page() + "." + g.method(), m);
            }
            String summary = info.title().isEmpty() ? "Tela do processo." : sentence(info.title());
            pages.add(new Page(cls, PythonGenerator.SYSTEM + "." + e.getValue().get(0).prefix(), identity, summary, info.blockTitles().stream().map(CodePlan::sentence)
                    .filter(x -> !x.equals(summary)).distinct().toList(), methods, helpers, info.title().isEmpty() ? CodeNames.Source.PROVISIONAL : info.source(),
                    info.boundary()));
        }
        Process process = process(cfg, pages, models, processClass, names, steps);
        List<Trace> traces = new ArrayList<>();
        for (Page p : pages) for (Method m : p.methods()) for (Op op : m.ops()) {
            traces.add(new Trace(op.traceId(), op.step().step().id().value().toString(), op.action().name(), op.step().key(),
                    "paginas.py:" + p.className() + "." + m.name()));
        }
        return new CodePlan(modern, cfg.list(), List.copyOf(pages), List.copyOf(models), process, List.copyOf(traces));
    }

    private static Method method(SingleConfig cfg, SingleConfig.Group g, List<Step> mine, Map<String, String> traceOf, String identity, CodeNames names,
                                 Set<String> modelNames, List<Model> models, String cls) {
        List<Param> params = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Step s : mine) {
            if (s.input() != null && seen.add(s.input())) params.add(new Param(s.input(), "str"));
            if (s.read() != null && s.read().expected() != null && seen.add(s.read().expected())) params.add(new Param(s.read().expected(), "str"));
        }
        if (mine.stream().anyMatch(s -> s.credential() != null)) params.add(new Param("segredos", "SecretProvider"));
        // leituras opcionais usam exists() (sem espera): antes, a primeira leitura OBRIGATÓRIA com alvo do grupo garante a página
        Step anchor = null;
        boolean optional = false;
        for (Step s : mine) {
            if (s.read() == null || s.key() == null) continue;
            if (s.read().optional()) optional = true;
            else if (anchor == null) anchor = s;
        }
        List<Op> ops = new ArrayList<>();
        boolean waited = false;
        for (Step s : mine) {
            boolean waitFirst = cfg.modern() && optional && !waited && anchor != null && s.read() != null && s.read().optional();
            if (waitFirst) waited = true;
            String tid = traceOf.get(s.step().id().value().toString());
            ops.add(new Op(s, tid, s.key() == null ? null : rel(s.key()), waitFirst, waitFirst ? rel(anchor.key()) : null,
                    waitFirst ? traceOf.get(anchor.step().id().value().toString()) : null, waitFirst ? anchor.label() : null));
        }
        List<Field> fields = new ArrayList<>();
        for (Step s : mine) if (s.output() != null) fields.add(new Field(s.output(), cfg.modern() && s.read() != null && s.read().optional()));
        Result result;
        if (fields.isEmpty()) {
            result = Result.none();
        } else if (fields.size() == 1) {
            result = new Result(Result.Kind.VALUE, fields.get(0).name(), fields.get(0).optional() ? "str | None" : "str", null);
        } else {
            String base = "Dados" + CodeNaming.pascal(g.prefix());
            String name = CodeNaming.unique(base, modelNames, "");
            models.add(new Model(name, cls, g.method(), List.copyOf(fields)));
            result = new Result(Result.Kind.MODEL, null, name, name);
        }
        CodeNames.MethodInfo info = names.method(g.page(), g.method());
        String described = describe(mine, cfg.modern());
        // nome pelo passo do PDD → o resumo é o passo e o detalhe, o que o código faz; nome pelas ações → só o que faz (o
        // título do bloco pode descrever mais do que este método)
        boolean fromStep = info.source() == CodeNames.Source.PDD_BLOCK_TITLE && !info.title().isEmpty();
        String summary = fromStep ? sentence(info.title()) : described;
        return new Method(g.page(), g.method(), List.copyOf(params), result, summary, fromStep ? described : "", List.copyOf(ops), info.source());
    }

    private static Process process(SingleConfig cfg, List<Page> pages, List<Model> models, String processClass, CodeNames names, List<Step> steps) {
        Map<String, String> labelOf = new LinkedHashMap<>();
        for (Step s : steps) labelOf.put(s.step().id().value().toString(), s.label());
        Map<String, Integer> callsPerPage = new LinkedHashMap<>();
        for (Page p : pages) callsPerPage.put(p.className(), p.methods().size());
        Set<String> locals = new HashSet<>(Set.of("entrada", "segredos", "rt", "item", "self"));
        Map<String, String> pageVar = new LinkedHashMap<>();
        Map<String, String> refOf = new LinkedHashMap<>(); // variável de saída → expressão Python
        Map<String, Boolean> optionalOf = new LinkedHashMap<>();
        List<Call> calls = new ArrayList<>();
        Map<String, String> resultTypes = new LinkedHashMap<>();
        for (SingleConfig.Group g : cfg.groups()) {
            Page page = pages.stream().filter(p -> p.className().equals(g.page())).findFirst().orElseThrow();
            Method m = page.methods().stream().filter(x -> x.name().equals(g.method())).findFirst().orElseThrow();
            String pv = null;
            if (callsPerPage.get(page.className()) > 1) {
                pv = pageVar.computeIfAbsent(page.className(), k -> CodeNaming.unique(CodeNaming.safe(page.prefix().substring(page.prefix().lastIndexOf('.') + 1)), locals, "_"));
            }
            List<String> args = new ArrayList<>();
            for (Param p : m.params()) args.add(p.type().equals("SecretProvider") ? "segredos" : "entrada." + p.name());
            String rv = null;
            switch (m.result().kind()) {
                case VALUE -> {
                    rv = CodeNaming.unique(m.result().var(), locals, "_");
                    refOf.put(m.result().var(), rv);
                    optionalOf.put(m.result().var(), m.result().type().endsWith("None"));
                    resultTypes.put(rv, m.result().type());
                }
                case MODEL -> {
                    rv = CodeNaming.unique("dados", locals, "_");
                    Model model = models.stream().filter(x -> x.name().equals(m.result().model())).findFirst().orElseThrow();
                    for (Field f : model.fields()) {
                        refOf.put(f.name(), rv + "." + f.name());
                        optionalOf.put(f.name(), f.optional());
                    }
                    resultTypes.put(rv, model.name());
                }
                case NONE -> {
                }
            }
            calls.add(new Call(page.className(), m.name(), pv, List.copyOf(args), rv));
        }
        List<Column> columns = new ArrayList<>();
        List<Param> rowParams = new ArrayList<>();
        Map<String, String> outputs = new LinkedHashMap<>();
        if (cfg.spreadsheet()) {
            Set<String> used = new LinkedHashSet<>();
            for (SingleConfig.Column c : cfg.output().columns()) {
                String pv = cfg.outputs().get(c.step());
                String fv = c.fallbackStep() == null ? null : cfg.outputs().get(c.fallbackStep());
                String fallbackLabel = c.fallbackStep() == null ? null : labelOf.get(c.fallbackStep());
                columns.add(new Column(c.field(), c.header(), refOf.get(pv), fv == null ? null : refOf.get(fv), fallbackLabel));
                used.add(root(refOf.get(pv)));
                if (fv != null) used.add(root(refOf.get(fv)));
            }
            for (String v : used) rowParams.add(new Param(v, resultTypes.get(v)));
        } else {
            for (String out : cfg.outputs().values()) if (refOf.containsKey(out)) outputs.put(out, refOf.get(out));
        }
        List<String> titles = new ArrayList<>();
        for (Page p : pages) if (!names.page(p.className()).title().isEmpty()) titles.add(names.page(p.className()).title());
        String flow = titles.isEmpty() ? "" : " Passa por: " + String.join(" → ", titles) + ".";
        String summary = (cfg.list() ? "Uma transação por linha da lista de entrada." : "Execução única.") + flow;
        return new Process(processClass, summary, List.copyOf(calls), List.copyOf(rowParams), List.copyOf(columns), outputs);
    }

    private static String root(String ref) {
        int dot = ref.indexOf('.');
        return dot < 0 ? ref : ref.substring(0, dot);
    }

    static Helper helper(Op op) {
        boolean date = op.step().read() != null && "date".equals(op.step().read().kind());
        return date ? (op.optional() ? Helper.DATE_IF_PRESENT : Helper.DATE) : (op.optional() ? Helper.TEXT_IF_PRESENT : Helper.TEXT);
    }

    /**
     * traceId de cada etapa: o já REGISTRADO (estável), senão {@code <área>.<ação>_<objeto>} derivado da etapa, único na
     * automação e fora dos aposentados. A atribuição segue a ordem do modelo só para etapas novas.
     */
    static Map<String, String> traceIds(List<Step> steps, CodeNames names) {
        Map<String, String> out = new LinkedHashMap<>();
        Set<String> taken = new HashSet<>(names.retired());
        taken.add(START_TRACE);
        for (Step s : steps) {
            String id = s.step().id().value().toString();
            String pinned = names.traces().get(id);
            if (pinned != null && SingleConfig.TRACE.matcher(pinned).matches() && !out.containsValue(pinned)) {
                out.put(id, pinned);
                taken.add(pinned);
            }
        }
        for (Step s : steps) {
            String id = s.step().id().value().toString();
            if (out.containsKey(id)) continue;
            String area = s.group().prefix();
            String op = operation(s);
            String base = area + "." + op;
            if (base.length() > 76) base = base.substring(0, 76).replaceAll("_+$", "");
            out.put(id, CodeNaming.unique(base, taken, "_"));
        }
        return out;
    }

    static String operation(Step s) {
        String target = s.key() == null ? "" : rel(s.key());
        return switch (s.step().actionType()) {
            case NAVIGATE -> "abrir_pagina";
            case FILL -> "preencher_" + (s.input() != null ? s.input() : s.credential() != null ? s.credential().substring(s.credential().lastIndexOf('.') + 1)
                    : target);
            case CLICK -> "clicar_" + target;
            case PRESS_KEY -> "pressionar_enter";
            case WAIT_FOR -> "aguardar_" + target;
            case OBSERVE -> "ler_" + (s.output() != null ? s.output() : target);
            default -> "etapa";
        };
    }

    /** Descrição derivada das AÇÕES (quando o PDD não deu um título de passo): só o que o código faz, sem inventar. */
    static String describe(List<Step> mine, boolean modern) {
        // frases na ORDEM das etapas; preenchimentos e leituras seguidos viram uma frase só
        List<String> out = new ArrayList<>();
        List<String> run = new ArrayList<>();
        String runVerb = null;
        List<String> optional = new ArrayList<>();
        for (Step s : mine) {
            String verb = null;
            String what = null;
            String phrase = null;
            switch (s.step().actionType()) {
                case NAVIGATE -> phrase = "abre a página inicial";
                case FILL -> {
                    verb = "preenche";
                    what = quoted(s.label());
                }
                case CLICK -> phrase = "aciona " + quoted(s.label());
                case PRESS_KEY -> phrase = "confirma com Enter";
                case WAIT_FOR -> phrase = "espera " + quoted(s.label());
                case OBSERVE -> {
                    what = s.read() != null && "address".equals(s.read().kind()) || "título".equals(s.label()) ? s.label() : quoted(s.label());
                    if (modern && s.read() != null && s.read().optional()) optional.add(what);
                    if (s.read() != null && s.read().expected() != null) {
                        phrase = "lê " + what + " e o confere com ``" + s.read().expected() + "`` (diferente recusa o item)";
                        what = null;
                    } else {
                        verb = "lê";
                    }
                }
                default -> {
                }
            }
            if (verb != null && verb.equals(runVerb)) {
                run.add(what);
                continue;
            }
            if (runVerb != null) out.add(runVerb + " " + join(run));
            run = new ArrayList<>();
            runVerb = verb;
            if (verb != null) run.add(what);
            if (phrase != null) out.add(phrase);
        }
        if (runVerb != null) out.add(runVerb + " " + join(run));
        String text = out.isEmpty() ? "Etapas desta tela" : String.join("; ", out);
        text = Character.toUpperCase(text.charAt(0)) + text.substring(1) + ".";
        if (!optional.isEmpty()) text += " " + Character.toUpperCase(join(optional).charAt(0)) + join(optional).substring(1)
                + (optional.size() == 1 ? " pode" : " podem") + " faltar nesta página (fonte alternativa declarada no PDD).";
        return text;
    }

    /** Rótulo de interface entre aspas (o texto exato da tela); vazio vira "o alvo". */
    static String quoted(String label) {
        return label == null || label.isEmpty() ? "o alvo" : "“" + label + "”";
    }

    static String join(List<String> items) {
        if (items.size() == 1) return items.get(0);
        return String.join(", ", items.subList(0, items.size() - 1)) + " e " + items.get(items.size() - 1);
    }

    /** Texto do PDD como frase de docstring (maiúscula inicial, ponto final). */
    static String sentence(String text) {
        String t = text.strip();
        if (t.isEmpty()) return t;
        t = Character.toUpperCase(t.charAt(0)) + t.substring(1);
        return t.matches(".*[.!?]$") ? t : t + ".";
    }

    static String rel(String key) {
        return key.substring(key.lastIndexOf('.') + 1);
    }
}
