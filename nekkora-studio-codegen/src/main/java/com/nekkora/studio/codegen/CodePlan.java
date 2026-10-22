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
 * plano para texto; o manifesto o descreve ({@code architecture}, {@code trace}, {@code naming}, {@code selectors}). Ordem
 * das operações = ordem do modelo.
 *
 * <p>Rodada 3 (Q10/Q11): todo identificador Python é INGLÊS ({@link EnglishNames}, glossário governado) e todo alvo é uma
 * chave TIPADA ({@code SelectorKey.<MEMBRO>.value}, em {@code automation/selectors.py}). Nomes de variáveis do modelo,
 * colunas da lista, cabeçalhos da planilha, rótulos da página, segredos e códigos de recusa continuam como estão: são
 * contratos e dados, não identificadores — o plano guarda a correspondência explícita ({@link #identifiers()}).
 *
 * @param modern      configuração 1.2.0 (framework 0.3.0: leitura tipada, Enter, planilha); senão 1.1.0 (0.2.0)
 * @param list        uma transação por linha da lista (planilha de saída)
 * @param pages       Pages na ordem em que aparecem
 * @param models      dados lidos por um método (dataclass), só quando um método lê dois ou mais dados
 * @param process     o processo (classe, chamadas, linha da planilha)
 * @param traces      mapa traceId ↔ StepId (bijetivo; a abertura sintética do endereço inicial fica fora)
 * @param selectors   chaves tipadas do registro de alvos, na ordem do modelo
 * @param identifiers nome no modelo do Studio (variável) → identificador Python, na ordem do modelo
 */
public record CodePlan(boolean modern, boolean list, List<Page> pages, List<Model> models, Process process, List<Trace> traces, List<Selector> selectors,
                       Map<String, String> identifiers) {

    /** Sistema do registro de alvos do código em inglês: toda chave começa por {@code process.}. */
    public static final String SYSTEM = "process";

    /**
     * Abertura do endereço inicial (sem etapa do Studio), na convenção {@code <área>.<ação>_<objeto>}. O manifesto declara os
     * ids anteriores desta mesma abertura ({@link #PREVIOUS_START_TRACES}) para correlacionar eventos de execuções antigas;
     * nenhum deles é reaproveitado por uma etapa.
     */
    public static final String START_TRACE = "process.open_start_page";

    /** Ids anteriores da abertura: o da rodada 2 (português) e o da rodada 1. */
    public static final List<String> PREVIOUS_START_TRACES = List.of("processo.abrir_pagina_inicial", "INICIO");

    /** Pacote do código gerado e seus módulos (nenhum módulo de topo com nome da biblioteca padrão, como {@code selectors}). */
    public static final String PACKAGE = "automation";
    public static final String PAGES_FILE = PACKAGE + "/pages.py";
    public static final String PROCESS_FILE = PACKAGE + "/process.py";
    public static final String SELECTORS_FILE = PACKAGE + "/selectors.py";
    public static final String INIT_FILE = PACKAGE + "/__init__.py";

    /**
     * Repetição segura de um clique/Enter depois de resultado incerto ({@code idempotent=True} no framework). Efeito
     * nenhum declarado NÃO basta: autenticação e fim de sessão mudam o estado do acesso (CQ-04).
     */
    public enum Retry { SAFE, AUTHENTICATION, SESSION_END, EXTERNAL_EFFECT }

    /** {@code identity}: membro de {@code SelectorKey} do elemento que prova que esta é a tela atual. */
    public record Page(String className, String prefix, String identity, String summary, List<String> details, List<Method> methods,
                       Set<Helper> helpers, CodeNames.Source nameSource, String boundary) {}

    /** {@code summary}: o que o método faz; {@code detail}: o que devolve, quando termina, o que recusa e se repete. */
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

    /**
     * {@code source}: a variável do modelo do Studio que o campo carrega. {@code optionalColumn} (SPEC-OPTIONAL-COLUMNS-001):
     * o campo pode faltar porque a COLUNA é opcional no PDD (e não por ter fonte alternativa).
     */
    public record Field(String name, boolean optional, String source, boolean optionalColumn) {
        public Field(String name, boolean optional, String source) {
            this(name, optional, source, false);
        }
    }

    /** Leitura de texto/data por um helper da Page (0.3.0). */
    public enum Helper { TEXT, TEXT_IF_PRESENT, DATE, DATE_IF_PRESENT }

    /**
     * Uma operação (uma etapa do modelo). {@code member}: o alvo como membro de {@code SelectorKey}; {@code waitFirst} =
     * antes dela, a espera de "página pronta" pelo alvo {@code waitMember} (traceId {@code waitTrace}).
     */
    public record Op(Step step, String traceId, String member, boolean waitFirst, String waitMember, String waitTrace, String waitLabel, Retry retry,
                     String nextMember, String firstFillMember, String input, String output, String expected) {
        public ActionType action() {
            return step.step().actionType();
        }

        public boolean optional() {
            return step.read() != null && step.read().optional();
        }
    }

    public record Call(String page, String method, String pageVar, List<String> args, String resultVar) {}

    /**
     * Coluna da planilha: {@code primary} e (opcional) {@code fallback} são referências Python já resolvidas.
     * {@code required = false}: a coluna é opcional no PDD (motivo em {@code requirementReason}) e pode ficar vazia.
     */
    public record Column(String field, String header, String primary, String fallback, String fallbackLabel, boolean required, String requirementReason) {
        public Column(String field, String header, String primary, String fallback, String fallbackLabel) {
            this(field, header, primary, fallback, fallbackLabel, true, null);
        }
    }

    /** Etapas cuja leitura pode faltar só porque a coluna é opcional (sem fonte alternativa). */
    static Set<String> optionalColumnReads(SingleConfig cfg) {
        Set<String> out = new LinkedHashSet<>();
        if (cfg.spreadsheet()) for (SingleConfig.Column c : cfg.output().columns()) if (!c.required() && c.fallbackStep() == null) out.add(c.step());
        return out;
    }

    /** Entrada: identificador Python e a coluna da lista (o nome da variável no modelo, contrato da lista). */
    public record Input(String name, String column) {}

    /**
     * @param outputs sem planilha: chave do resultado (nome da variável no modelo, contrato do resultado) → referência Python
     */
    public record Process(String className, String summary, List<Call> calls, List<Param> rowParams, List<Column> columns, Map<String, String> outputs,
                          List<Input> inputs, String inputClass, String inputVar) {}

    public record Trace(String traceId, String stepId, String action, String targetKey, String symbol, List<String> previous) {}

    /** Chave tipada: membro do {@code SelectorKey}, chave canônica do registro e as etapas que a usam. */
    public record Selector(String member, String key, String label, List<String> traceIds, List<String> stepIds) {}

    // ------------------------------------------------------------------------------------------------ construção

    static CodePlan build(SingleConfig cfg, List<Step> steps, CodeNames names, String processClass) {
        boolean modern = cfg.modern();
        Map<String, String> py = identifiers(cfg, steps);
        Map<String, String> member = members(steps);
        Map<String, String> traceOf = traceIds(steps, names, py);
        List<Page> pages = new ArrayList<>();
        List<Model> models = new ArrayList<>();
        Map<String, List<SingleConfig.Group>> byPage = new LinkedHashMap<>();
        for (SingleConfig.Group g : cfg.groups()) byPage.computeIfAbsent(g.page(), k -> new ArrayList<>()).add(g);
        Set<String> modelNames = new HashSet<>();
        Map<String, String> labelOfKey = new LinkedHashMap<>();
        for (Step s : steps) if (s.key() != null) labelOfKey.putIfAbsent(s.key(), s.label());
        for (Map.Entry<String, List<SingleConfig.Group>> e : byPage.entrySet()) {
            String cls = e.getKey();
            CodeNames.PageInfo info = names.page(cls);
            List<Step> pageSteps = steps.stream().filter(s -> s.group().page().equals(cls)).toList();
            String identityKey = pageSteps.stream().filter(s -> s.key() != null && !(s.read() != null && s.read().optional())).map(Step::key).findFirst()
                    .orElse(pageSteps.stream().map(Step::key).filter(k -> k != null).findFirst().orElse(null));
            List<Method> methods = new ArrayList<>();
            Set<Helper> helpers = new LinkedHashSet<>();
            for (SingleConfig.Group g : e.getValue()) {
                List<Step> mine = steps.stream().filter(s -> s.group().equals(g)).toList();
                Method m = method(cfg, g, mine, traceOf, names, modelNames, models, cls, labelOfKey, py, member);
                for (Op op : m.ops()) if (modern && op.action() == ActionType.OBSERVE && op.member() != null) helpers.add(helper(op));
                methods.add(m);
            }
            // a docstring da tela diz ONDE ela está no PDD e, com mais de um método, o PAPEL dela — sem repetir o título (CQ-03)
            String where = info.title().isEmpty() ? "Screen of the process" : "Screen of the PDD section “" + info.title().strip() + "”";
            String role = role(methods);
            String summary = role.isEmpty() ? where + "." : where + ": " + role + ".";
            pages.add(new Page(cls, SYSTEM + "." + e.getValue().get(0).prefix(), identityKey == null ? null : member.get(identityKey), summary, List.of(), methods,
                    helpers, info.title().isEmpty() ? CodeNames.Source.PROVISIONAL : info.source(), info.boundary()));
        }
        Process process = process(cfg, pages, models, processClass, names, steps, py);
        List<Trace> traces = new ArrayList<>();
        for (Page p : pages) for (Method m : p.methods()) for (Op op : m.ops()) {
            String sid = op.step().step().id().value().toString();
            List<String> previous = names.previous().getOrDefault(sid, List.of()).stream().filter(x -> !x.equals(op.traceId())).toList();
            traces.add(new Trace(op.traceId(), sid, op.action().name(), op.step().key(), PAGES_FILE + ":" + p.className() + "." + m.name(), previous));
        }
        List<Selector> selectors = new ArrayList<>();
        for (Map.Entry<String, String> e : member.entrySet()) {
            List<String> tids = new ArrayList<>();
            List<String> sids = new ArrayList<>();
            String label = "";
            for (Step s : steps) {
                if (!e.getKey().equals(s.key())) continue;
                tids.add(traceOf.get(s.step().id().value().toString()));
                sids.add(s.step().id().value().toString());
                if (label.isEmpty()) label = s.label();
            }
            selectors.add(new Selector(e.getValue(), e.getKey(), label, List.copyOf(tids), List.copyOf(sids)));
        }
        return new CodePlan(modern, cfg.list(), List.copyOf(pages), List.copyOf(models), process, List.copyOf(traces), List.copyOf(selectors),
                java.util.Collections.unmodifiableMap(py));
    }

    /**
     * Identificador Python inglês de cada variável do modelo usada (entradas, depois saídas, na ordem do modelo), único e
     * fora das palavras reservadas e dos nomes que o código gerado já usa.
     */
    static Map<String, String> identifiers(SingleConfig cfg, List<Step> steps) {
        Map<String, String> out = new LinkedHashMap<>();
        Set<String> taken = new HashSet<>(Set.of("row", "inputs", "secrets", "rt", "item", "self", "data", "name", "value"));
        List<String> names = new ArrayList<>(cfg.inputs());
        for (Step s : steps) if (s.output() != null && !names.contains(s.output())) names.add(s.output());
        Set<String> dates = new HashSet<>();
        for (Step s : steps) if (s.output() != null && s.read() != null && "date".equals(s.read().kind())) dates.add(s.output());
        for (String n : names) {
            // R4-02: valor lido como data → o nome diz que é data (glossário 1.1.0, contexto de tipo)
            String base = CodeNaming.safe(EnglishNames.identifier(n, dates.contains(n)).snake());
            if (base.isEmpty() || !Character.isLetter(base.charAt(0))) base = "value";
            out.put(n, CodeNaming.unique(base, taken, "_"));
        }
        return out;
    }

    /**
     * Membro de {@code SelectorKey} de cada chave do registro: {@code <TELA>_<ELEMENTO>} em maiúsculas, sem repetir a tela
     * quando o elemento já começa por ela ({@code process.search.search_term_field} → {@code SEARCH_TERM_FIELD};
     * {@code process.profile.full_name} → {@code PROFILE_FULL_NAME}). Único, na ordem do modelo.
     */
    static Map<String, String> members(List<Step> steps) {
        Map<String, String> out = new LinkedHashMap<>();
        Set<String> taken = new HashSet<>();
        for (Step s : steps) {
            if (s.key() == null || out.containsKey(s.key())) continue;
            out.put(s.key(), CodeNaming.unique(member(s.key()), taken, "_"));
        }
        return out;
    }

    static String member(String key) {
        String[] parts = key.split("\\.");
        String page = parts.length > 2 ? parts[parts.length - 2] : "";
        String name = parts[parts.length - 1];
        String base = page.isEmpty() || name.equals(page) || name.startsWith(page + "_") ? name : page + "_" + name;
        return base.toUpperCase(java.util.Locale.ROOT);
    }

    private static Method method(SingleConfig cfg, SingleConfig.Group g, List<Step> mine, Map<String, String> traceOf, CodeNames names, Set<String> modelNames,
                                 List<Model> models, String cls, Map<String, String> labelOfKey, Map<String, String> py, Map<String, String> member) {
        List<Param> params = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Step s : mine) {
            if (s.input() != null && seen.add(s.input())) params.add(new Param(py.get(s.input()), "str"));
            if (s.read() != null && s.read().expected() != null && seen.add(s.read().expected())) params.add(new Param(py.get(s.read().expected()), "str"));
        }
        if (mine.stream().anyMatch(s -> s.credential() != null)) params.add(new Param("secrets", "SecretProvider"));
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
        boolean credentials = false;
        for (Step s : mine) {
            boolean waitFirst = cfg.modern() && optional && !waited && anchor != null && s.read() != null && s.read().optional();
            if (waitFirst) waited = true;
            if (s.credential() != null) credentials = true;
            String tid = traceOf.get(s.step().id().value().toString());
            ops.add(new Op(s, tid, s.key() == null ? null : member.get(s.key()), waitFirst, waitFirst ? member.get(anchor.key()) : null,
                    waitFirst ? traceOf.get(anchor.step().id().value().toString()) : null, waitFirst ? anchor.label() : null, retry(s, credentials),
                    s.nextKey() == null ? null : member.get(s.nextKey()), s.firstFillKey() == null ? null : member.get(s.firstFillKey()),
                    s.input() == null ? null : py.get(s.input()), s.output() == null ? null : py.get(s.output()),
                    s.read() == null || s.read().expected() == null ? null : py.get(s.read().expected())));
        }
        List<Field> fields = new ArrayList<>();
        Set<String> columnOptional = optionalColumnReads(cfg);
        for (Step s : mine) {
            if (s.output() == null) continue;
            boolean readOptional = cfg.modern() && s.read() != null && s.read().optional();
            fields.add(new Field(py.get(s.output()), readOptional, s.output(), readOptional && columnOptional.contains(s.step().id().value().toString())));
        }
        Result result;
        if (fields.isEmpty()) {
            result = Result.none();
        } else if (fields.size() == 1) {
            result = new Result(Result.Kind.VALUE, fields.get(0).name(), fields.get(0).optional() ? "str | None" : "str", null);
        } else {
            String preferred = names.page(cls).model();
            String base = preferred.isEmpty() ? EnglishNames.identifier(g.prefix()).pascal() + "Data" : preferred;
            String name = CodeNaming.unique(base, modelNames, "");
            models.add(new Model(name, cls, g.method(), List.copyOf(fields)));
            result = new Result(Result.Kind.MODEL, null, name, name);
        }
        CodeNames.MethodInfo info = names.method(g.page(), g.method());
        // a docstring diz o que o método faz com as entradas, o que devolve e as condições que importam (CQ-03)
        return new Method(g.page(), g.method(), List.copyOf(params), result, describe(ops), conditions(ops, result, cfg.modern(), labelOfKey, columnOptional), List.copyOf(ops),
                info.source());
    }

    /** Repetição segura de um clique/Enter: só sem efeito declarado e fora de autenticação e de fim de sessão. */
    static Retry retry(Step s, boolean credentialsBefore) {
        ActionType t = s.step().actionType();
        if (t != ActionType.CLICK && t != ActionType.PRESS_KEY) return null;
        if (!"NONE".equals(s.effect().effect())) return Retry.EXTERNAL_EFFECT;
        if (credentialsBefore) return Retry.AUTHENTICATION;
        if (endsSession(s.label())) return Retry.SESSION_END;
        return Retry.SAFE;
    }

    /** Rótulo de quem encerra a sessão ("Sair", "Logout", "Sign out", "Encerrar sessão", "Desconectar"). */
    static boolean endsSession(String label) {
        String n = String.join(" ", CodeNaming.words(label));
        n = n.replace("log out", "logout").replace("sign out", "signout").replace("log off", "logoff");
        for (String w : n.split(" ")) {
            if (Set.of("sair", "logout", "signout", "logoff", "desconectar").contains(w)) return true;
        }
        return n.contains("encerrar sessao") || n.contains("terminar sessao");
    }

    /**
     * Papel da tela quando ela tem mais de um método: o que cada método faz, curto — o resumo dele quando é curto, senão o
     * nome do método na 3ª pessoa ("check_page_address" → "checks the page address"). Com um método só, a docstring dele já
     * diz o papel (não repetir).
     */
    static String role(List<Method> methods) {
        if (methods.size() < 2) return "";
        List<String> parts = new ArrayList<>();
        for (Method m : methods) {
            String s = m.summary().replaceAll("[.!?]+$", "");
            String part = s.length() <= 40 ? Character.toLowerCase(s.charAt(0)) + s.substring(1) : spoken(m.name());
            if (!parts.contains(part)) parts.add(part);
        }
        return parts.stream().anyMatch(x -> x.contains(" and ")) ? String.join("; ", parts) : join(parts);
    }

    /** Nome de método inglês dito na 3ª pessoa: "read_profile_data" → "reads the profile data"; "sign_in" → "signs in". */
    static String spoken(String method) {
        String[] w = method.split("_");
        if (w.length >= 2 && Set.of("in", "out", "up", "back", "for", "forward").contains(w[1])) {
            String rest = w.length > 2 ? " the " + String.join(" ", java.util.Arrays.copyOfRange(w, 2, w.length)) : "";
            return EnglishNames.thirdPerson(w[0] + " " + w[1]) + rest;
        }
        String rest = w.length > 1 ? " the " + String.join(" ", java.util.Arrays.copyOfRange(w, 1, w.length)) : "";
        return EnglishNames.thirdPerson(w[0]) + rest;
    }

    /**
     * O que vale saber além do resumo: o que o método devolve, quando termina, o que recusa o item e se a ação pode ser
     * repetida depois de um resultado incerto. Frases curtas, uma por condição.
     */
    static String conditions(List<Op> ops, Result result, boolean modern, Map<String, String> labelOfKey, Set<String> columnOptional) {
        List<String> out = new ArrayList<>();
        switch (result.kind()) {
            case MODEL -> out.add("Returns ``" + result.model() + "``.");
            case VALUE -> {
                boolean address = ops.stream().anyMatch(o -> o.step().read() != null && "address".equals(o.step().read().kind()));
                out.add(address ? "Returns the address read." : "Returns the text read.");
            }
            case NONE -> {
            }
        }
        List<String> optional = new ArrayList<>();
        List<String> emptyAllowed = new ArrayList<>();
        boolean dates = false;
        for (Op op : ops) {
            Step s = op.step();
            if (op.waitFirst()) {
                out.add("First waits for " + quoted(op.waitLabel()) + " (a required read): only with the page ready is a missing optional value conclusive.");
            }
            if (modern && s.read() != null && s.read().optional()) {
                (columnOptional.contains(s.step().id().value().toString()) ? emptyAllowed : optional).add(readName(op));
            }
            if (modern && s.read() != null && "date".equals(s.read().kind())) dates = true;
            if (s.read() != null && s.read().expected() != null) {
                out.add("address".equals(s.read().kind()) ? "A different address rejects the item (``ENDERECO_DIFERENTE_DO_ESPERADO``)."
                        : "A different value rejects the item (``VALOR_DIFERENTE_DO_ESPERADO``).");
            }
            if (op.retry() == null) continue;
            String next = s.nextKey() != null && s.nextOnOtherScreen() ? quoted(labelOfKey.get(s.nextKey())) : null;
            switch (op.retry()) {
                case SAFE -> {
                    if (s.press() != null && s.press().navigates()) out.add("Finishes when the page address changes.");
                    else if (next != null) out.add("Finishes when " + next + " appears.");
                    out.add("No declared effect on the system: if the outcome is uncertain, the " + (s.press() != null ? "submission" : "click") + " may be repeated.");
                }
                case AUTHENTICATION -> {
                    if (next != null) out.add("Finishes when " + next + " appears.");
                    out.add("Sends the credentials only once: if the outcome is uncertain, the item stops (``OUTCOME_UNKNOWN``) instead of signing in again.");
                }
                case SESSION_END -> out.add("Ends the session only once: if the outcome is uncertain, it is not repeated (``OUTCOME_UNKNOWN``).");
                case EXTERNAL_EFFECT -> out.add("Changes something in the system: done only once and concluded only with the confirmation declared in the specification.");
            }
        }
        if (!optional.isEmpty()) {
            String who = join(optional);
            out.add(Character.toUpperCase(who.charAt(0)) + who.substring(1) + " may be missing on this page (``None``; the alternative source is in the PDD).");
        }
        if (!emptyAllowed.isEmpty()) {
            String who = join(emptyAllowed);
            out.add(Character.toUpperCase(who.charAt(0)) + who.substring(1) + " may be missing on this page (``None``; an optional column in the PDD).");
        }
        if (dates) out.add("A written-out date becomes YYYY-MM-DD; an incomplete or ambiguous date rejects the item (``DATA_INCOMPLETA_OU_AMBIGUA``).");
        return String.join(" ", out);
    }

    /**
     * Como a leitura aparece no texto: "the page address", "the title", o texto de um link/botão, ou o rótulo da tela entre
     * aspas (o texto exato da página, que não é traduzido).
     */
    static String readName(Op op) {
        return readName(op.step());
    }

    static String readName(Step s) {
        if (s.read() != null && "address".equals(s.read().kind())) return "the page address";
        if (s.label().isEmpty() && heading(s)) return "the title";
        String kind = s.step().element().map(e -> e.semanticKind() == null ? "" : e.semanticKind()).orElse("");
        if (!s.label().isEmpty() && ("LINK".equals(kind) || "BUTTON".equals(kind))) {
            return "the text of the " + quoted(s.label()) + ("LINK".equals(kind) ? " link" : " button");
        }
        return s.label().isEmpty() ? "the value" : quoted(s.label());
    }

    static boolean heading(Step s) {
        return s.step().element().map(e -> "HEADING".equals(e.semanticKind())).orElse(false);
    }

    private static Process process(SingleConfig cfg, List<Page> pages, List<Model> models, String processClass, CodeNames names, List<Step> steps,
                                   Map<String, String> py) {
        Map<String, String> readOf = new LinkedHashMap<>();
        for (Step s : steps) readOf.put(s.step().id().value().toString(), readName(s));
        Map<String, Integer> callsPerPage = new LinkedHashMap<>();
        for (Page p : pages) callsPerPage.put(p.className(), p.methods().size());
        String inputVar = cfg.list() ? "row" : "inputs";
        Set<String> locals = new HashSet<>(Set.of(inputVar, "secrets", "rt", "item", "self"));
        Map<String, String> pageVar = new LinkedHashMap<>();
        Map<String, String> refOf = new LinkedHashMap<>(); // variável do modelo (saída) → expressão Python
        List<Call> calls = new ArrayList<>();
        Map<String, String> resultTypes = new LinkedHashMap<>();
        Map<String, String> sourceOfParam = new LinkedHashMap<>();
        for (String in : cfg.inputs()) sourceOfParam.put(py.get(in), in);
        for (SingleConfig.Group g : cfg.groups()) {
            Page page = pages.stream().filter(p -> p.className().equals(g.page())).findFirst().orElseThrow();
            Method m = page.methods().stream().filter(x -> x.name().equals(g.method())).findFirst().orElseThrow();
            String pv = null;
            if (callsPerPage.get(page.className()) > 1) {
                pv = pageVar.computeIfAbsent(page.className(), k -> CodeNaming.unique(CodeNaming.safe(page.prefix().substring(page.prefix().lastIndexOf('.') + 1)), locals, "_"));
            }
            List<String> args = new ArrayList<>();
            for (Param p : m.params()) args.add(p.type().equals("SecretProvider") ? "secrets" : inputVar + "." + p.name());
            String rv = null;
            switch (m.result().kind()) {
                case VALUE -> {
                    rv = CodeNaming.unique(m.result().var(), locals, "_");
                    String source = m.ops().stream().filter(o -> o.output() != null).map(o -> o.step().output()).findFirst().orElseThrow();
                    refOf.put(source, rv);
                    resultTypes.put(rv, m.result().type());
                }
                case MODEL -> {
                    rv = CodeNaming.unique(m.result().model().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT), locals, "_");
                    Model model = models.stream().filter(x -> x.name().equals(m.result().model())).findFirst().orElseThrow();
                    for (Field f : model.fields()) refOf.put(f.source(), rv + "." + f.name());
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
                String fallbackLabel = c.fallbackStep() == null ? null : readOf.get(c.fallbackStep());
                columns.add(new Column(c.field(), c.header(), refOf.get(pv), fv == null ? null : refOf.get(fv), fallbackLabel, c.required(), c.requirementReason()));
                used.add(root(refOf.get(pv)));
                if (fv != null) used.add(root(refOf.get(fv)));
            }
            for (String v : used) rowParams.add(new Param(v, resultTypes.get(v)));
        } else {
            for (String out : cfg.outputs().values()) if (refOf.containsKey(out)) outputs.put(out, refOf.get(out));
        }
        List<String> titles = new ArrayList<>();
        for (Page p : pages) if (!names.page(p.className()).title().isEmpty()) titles.add("“" + names.page(p.className()).title() + "”");
        String flow = titles.isEmpty() ? "" : " Goes through the PDD sections " + String.join(" → ", titles) + ".";
        String summary = (cfg.list() ? "One transaction per row of the input list." : "Single run.") + flow;
        List<Input> inputs = new ArrayList<>();
        for (String in : cfg.inputs()) inputs.add(new Input(py.get(in), in));
        return new Process(processClass, summary, List.copyOf(calls), List.copyOf(rowParams), List.copyOf(columns), outputs, List.copyOf(inputs),
                cfg.list() ? "InputRow" : "Inputs", inputVar);
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
     * traceId de cada etapa: o já REGISTRADO na convenção atual (estável), senão {@code <área>.<ação>_<objeto>} em inglês
     * derivado da etapa, único na automação e fora dos aposentados e dos ids ANTERIORES de qualquer etapa (os em português
     * de antes da rodada 3 ficam declarados no manifesto, nunca reaproveitados).
     */
    static Map<String, String> traceIds(List<Step> steps, CodeNames names, Map<String, String> py) {
        Map<String, String> out = new LinkedHashMap<>();
        Set<String> taken = new HashSet<>(names.retired());
        taken.add(START_TRACE);
        taken.addAll(PREVIOUS_START_TRACES);
        names.previous().values().forEach(taken::addAll);
        for (Step s : steps) {
            String id = s.step().id().value().toString();
            String pinned = names.traces().get(id);
            if (pinned != null && SingleConfig.TRACE.matcher(pinned).matches() && !out.containsValue(pinned) && !names.retired().contains(pinned)) {
                out.put(id, pinned);
                taken.add(pinned);
            }
        }
        for (Step s : steps) {
            String id = s.step().id().value().toString();
            if (out.containsKey(id)) continue;
            String area = s.group().prefix();
            String base = area + "." + operation(s, py);
            if (base.length() > 76) base = base.substring(0, 76).replaceAll("_+$", "");
            out.put(id, CodeNaming.unique(base, taken, "_"));
        }
        return out;
    }

    static String operation(Step s, Map<String, String> py) {
        String target = s.key() == null ? "" : rel(s.key());
        return switch (s.step().actionType()) {
            case NAVIGATE -> "open_page";
            case FILL -> "fill_" + (s.input() != null ? py.get(s.input())
                    : s.credential() != null ? EnglishNames.identifier(s.credential().substring(s.credential().lastIndexOf('.') + 1)).snake() : target);
            case CLICK -> "click_" + target;
            case PRESS_KEY -> "press_enter";
            case WAIT_FOR -> "wait_for_" + target;
            case OBSERVE -> "read_" + (s.output() != null ? py.get(s.output()) : target);
            default -> "step";
        };
    }

    /** O que o método FAZ, na ordem das etapas, com as entradas que usa: só o que o código faz, sem inventar. */
    static String describe(List<Op> ops) {
        // preenchimentos e leituras seguidos viram uma frase só
        List<String> out = new ArrayList<>();
        List<String> run = new ArrayList<>();
        String runVerb = null;
        boolean runSecret = false;
        for (Op op : ops) {
            Step s = op.step();
            String verb = null;
            String what = null;
            String phrase = null;
            switch (s.step().actionType()) {
                case NAVIGATE -> phrase = "opens the start page";
                case FILL -> {
                    verb = "fills";
                    what = (s.label().isEmpty() ? "the field" : quoted(s.label())) + (op.input() != null ? " with ``" + op.input() + "``" : "");
                }
                case CLICK -> phrase = "clicks " + quoted(s.label());
                case PRESS_KEY -> phrase = "submits with Enter";
                case WAIT_FOR -> phrase = "waits for " + quoted(s.label());
                case OBSERVE -> {
                    what = readName(op);
                    if (s.read() != null && s.read().expected() != null) {
                        phrase = "reads " + what + " and compares it with ``" + op.expected() + "``";
                        what = null;
                    } else {
                        verb = "reads";
                    }
                }
                default -> {
                }
            }
            if (verb != null && verb.equals(runVerb)) {
                run.add(what);
                runSecret |= s.credential() != null;
                continue;
            }
            if (runVerb != null) out.add(runVerb + " " + join(run) + (runSecret ? " with the credentials from the secret provider" : ""));
            run = new ArrayList<>();
            runVerb = verb;
            runSecret = s.credential() != null;
            if (verb != null) run.add(what);
            if (phrase != null) out.add(phrase);
        }
        if (runVerb != null) out.add(runVerb + " " + join(run) + (runSecret ? " with the credentials from the secret provider" : ""));
        // frases que já têm "and" dentro ficam separadas por ";" (senão "compares it with X and reads Y" se confunde)
        String text = out.isEmpty() ? "Steps of this screen" : out.stream().anyMatch(x -> x.contains(" and ")) ? String.join("; ", out) : join(out);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1) + ".";
    }

    /** Rótulo de interface entre aspas (o texto exato da tela, não traduzido); vazio vira "the target". */
    static String quoted(String label) {
        return label == null || label.isEmpty() ? "the target" : "“" + label + "”";
    }

    static String join(List<String> items) {
        if (items.size() == 1) return items.get(0);
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }

    static String rel(String key) {
        return key.substring(key.lastIndexOf('.') + 1);
    }
}
