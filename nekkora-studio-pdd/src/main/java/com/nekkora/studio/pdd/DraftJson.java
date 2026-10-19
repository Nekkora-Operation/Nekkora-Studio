package com.nekkora.studio.pdd;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.pdd.DocumentDraft.Annotation;
import com.nekkora.studio.pdd.DocumentDraft.Block;
import com.nekkora.studio.pdd.DocumentDraft.Instruction;
import com.nekkora.studio.pdd.DocumentDraft.SafeCapture;
import com.nekkora.studio.pdd.DocumentDraft.Section;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * JSON canônico do rascunho ({@code nekkora-studio/pdd-visual/1.0.0}): mesma forma → mesmos bytes. Leitura ESTRITA:
 * esquema/versão exatos, campos desconhecidos recusados, textos revalidados pela política (um rascunho adulterado com um
 * valor num texto "revisado" não volta como exportável).
 *
 * <p>STUDIO-012 PDD-01 — versão {@code 1.1.0}: cada captura com a sua referência ({@code ref}) e o recorte
 * ({@code crop}, ou {@code null}); marcadores com procedência ({@code provenance}, {@code reviewer}); regiões da
 * {@code nekkora-visual/2} com {@code tone}. É escrita SÓ quando o rascunho usa uma dessas novidades
 * ({@link DocumentDraft#needsV11()}); sem elas, o rascunho continua 1.0.0, byte a byte igual ao da STUDIO-010. As duas
 * versões são lidas; um 1.0.0 com algo do 1.1.0 é recusado.
 */
public final class DraftJson {
    private DraftJson() {}

    // ------------------------------------------------------------------------------------------- escrita

    public static String write(DocumentDraft d) {
        Map<String, Object> root = new LinkedHashMap<>();
        boolean v11 = d.needsV11();
        root.put("schema", DocumentDraft.SCHEMA);
        root.put("version", d.needsV12() ? DocumentDraft.VERSION_V12 : v11 ? DocumentDraft.VERSION_V11 : DocumentDraft.VERSION);
        root.put("automationId", d.automationId().value().toString());
        root.put("processRevision", d.processRevision());
        List<Object> sections = new ArrayList<>();
        for (Section s : d.sections()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sessionId", s.sessionId().value().toString());
            m.put("number", s.number());
            m.put("title", text(s.title()));
            List<Object> blocks = new ArrayList<>();
            for (Block b : s.blocks()) {
                Map<String, Object> bm = new LinkedHashMap<>();
                bm.put("blockId", b.blockId());
                bm.put("stepIds", ids(b.stepIds()));
                bm.put("title", text(b.title()));
                List<Object> ins = new ArrayList<>();
                for (Instruction i : b.instructions()) {
                    Map<String, Object> im = new LinkedHashMap<>();
                    im.put("stepId", i.stepId().value().toString());
                    im.put("ordinal", i.ordinal());
                    im.put("kind", i.kind().name());
                    im.put("text", text(i.text()));
                    ins.add(im);
                }
                bm.put("instructions", ins);
                bm.put("screenKey", b.screenKey());
                bm.put("capture", b.captureSha());
                blocks.add(bm);
            }
            m.put("blocks", blocks);
            sections.add(m);
        }
        root.put("sections", sections);
        List<Object> captures = new ArrayList<>();
        for (SafeCapture c : d.captures().values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            if (v11) m.put("ref", c.ref());
            m.put("sha256", c.sha256());
            m.put("width", c.width());
            m.put("height", c.height());
            m.put("devicePixelRatio", num(c.devicePixelRatio()));
            m.put("observedAt", c.observedAt().toString());
            m.put("pageEpoch", c.pageEpoch());
            m.put("policy", c.policy());
            m.put("screenKey", c.screenKey());
            m.put("forSteps", ids(c.forSteps()));
            List<Object> anns = new ArrayList<>();
            for (Annotation a : c.annotations()) {
                Map<String, Object> am = new LinkedHashMap<>();
                am.put("stepId", a.stepId().value().toString());
                am.put("status", a.status().name());
                am.put("x", num(a.x()));
                am.put("y", num(a.y()));
                am.put("width", num(a.width()));
                am.put("height", num(a.height()));
                am.put("reason", a.reason());
                if (v11) {
                    am.put("provenance", a.provenance().name());
                    am.put("reviewer", a.reviewer());
                }
                anns.add(am);
            }
            m.put("annotations", anns);
            m.put("limitations", new ArrayList<Object>(c.limitations()));
            List<Object> regs = new ArrayList<>();
            for (DocumentDraft.Region r : c.regions()) {
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put("kind", r.kind());
                rm.put("x", r.x());
                rm.put("y", r.y());
                rm.put("width", r.width());
                rm.put("height", r.height());
                if (r.tone() != null) rm.put("tone", r.tone());
                if (r.text() != null) {
                    rm.put("text", r.text());
                    rm.put("size", r.size());
                    rm.put("weight", r.weight());
                }
                if (r.ink() != null) rm.put("ink", r.ink());
                if (r.align() != null) rm.put("align", r.align());
                regs.add(rm);
            }
            m.put("regions", regs);
            if (v11) {
                if (c.crop() == null) {
                    m.put("crop", null);
                } else {
                    Map<String, Object> cm = new LinkedHashMap<>();
                    cm.put("x", c.crop().x());
                    cm.put("y", c.crop().y());
                    cm.put("width", c.crop().width());
                    cm.put("height", c.crop().height());
                    cm.put("reviewer", c.crop().reviewer());
                    m.put("crop", cm);
                }
            }
            captures.add(m);
        }
        root.put("captures", captures);
        StringBuilder b = new StringBuilder();
        Pretty.value(b, root, 0);
        return b.append('\n').toString();
    }

    private static Map<String, Object> text(DocText t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", t.value());
        m.put("state", t.state().name());
        m.put("reviewer", t.reviewer());
        return m;
    }

    private static List<Object> ids(List<StepId> ids) {
        return new ArrayList<>(ids.stream().map(i -> i.value().toString()).toList());
    }

    /** Número com no máximo 6 casas e forma estável (sem notação científica). */
    private static Object num(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e9) return (long) v;
        return new java.math.BigDecimal(String.format(Locale.ROOT, "%.6f", v)).stripTrailingZeros();
    }

    // ------------------------------------------------------------------------------------------- leitura

    public static DocumentDraft read(String json) {
        Map<String, Object> root = Json.parseObject(json);
        only(root, Set.of("schema", "version", "automationId", "processRevision", "sections", "captures"));
        String version = Json.string(root, "version");
        boolean v12 = DocumentDraft.VERSION_V12.equals(version);
        boolean v11 = v12 || DocumentDraft.VERSION_V11.equals(version); // o 1.2.0 contém tudo do 1.1.0
        if (!DocumentDraft.SCHEMA.equals(Json.string(root, "schema")) || !(v11 || DocumentDraft.VERSION.equals(version))) {
            throw new IllegalArgumentException("rascunho de outro esquema ou versão");
        }
        String rev = req(root, "processRevision");
        if (!rev.matches("sha256:[0-9a-f]{64}")) throw new IllegalArgumentException("processRevision inválida");
        List<Section> sections = new ArrayList<>();
        for (Object o : Json.array(root, "sections")) {
            Map<String, Object> s = obj(o);
            only(s, Set.of("sessionId", "number", "title", "blocks"));
            List<Block> blocks = new ArrayList<>();
            SessionId sid = new SessionId(UUID.fromString(req(s, "sessionId")));
            for (Object bo : Json.array(s, "blocks")) {
                Map<String, Object> b = obj(bo);
                only(b, Set.of("blockId", "stepIds", "title", "instructions", "screenKey", "capture"));
                List<Instruction> ins = new ArrayList<>();
                for (Object io : Json.array(b, "instructions")) {
                    Map<String, Object> i = obj(io);
                    only(i, Set.of("stepId", "ordinal", "kind", "text"));
                    ins.add(new Instruction(new StepId(UUID.fromString(req(i, "stepId"))), (int) Json.number(i, "ordinal", -1),
                            text(Json.object(i, "text")), Instruction.Kind.valueOf(req(i, "kind"))));
                }
                String blockId = req(b, "blockId");
                if (!blockId.matches("blk-[0-9a-f]{16}")) throw new IllegalArgumentException("blockId inválido");
                blocks.add(new Block(blockId, sid, stepIds(Json.array(b, "stepIds")), text(Json.object(b, "title")), ins, Json.string(b, "screenKey"),
                        Json.string(b, "capture")));
            }
            sections.add(new Section(sid, (int) Json.number(s, "number", -1), text(Json.object(s, "title")), blocks));
        }
        Map<String, SafeCapture> captures = new LinkedHashMap<>();
        for (Object o : Json.array(root, "captures")) {
            Map<String, Object> c = obj(o);
            Set<String> captureFields = new java.util.HashSet<>(Set.of("sha256", "width", "height", "devicePixelRatio", "observedAt", "pageEpoch", "policy",
                    "screenKey", "forSteps", "annotations", "limitations", "regions"));
            if (v11) captureFields.addAll(Set.of("ref", "crop"));
            only(c, captureFields);
            List<Annotation> anns = new ArrayList<>();
            for (Object ao : Json.array(c, "annotations")) {
                Map<String, Object> a = obj(ao);
                Set<String> annFields = new java.util.HashSet<>(Set.of("stepId", "status", "x", "y", "width", "height", "reason"));
                if (v11) annFields.addAll(Set.of("provenance", "reviewer"));
                only(a, annFields);
                Annotation.Provenance prov = v11 ? Annotation.Provenance.valueOf(req(a, "provenance")) : Annotation.Provenance.AUTOMATIC;
                anns.add(new Annotation(new StepId(UUID.fromString(req(a, "stepId"))), Annotation.Status.valueOf(req(a, "status")), dbl(a, "x"), dbl(a, "y"),
                        dbl(a, "width"), dbl(a, "height"), code(Json.string(a, "reason")), prov, v11 ? Json.string(a, "reviewer") : ""));
            }
            List<String> limits = new ArrayList<>();
            for (Object l : Json.array(c, "limitations")) limits.add(code(String.valueOf(l)));
            List<DocumentDraft.Region> regs = new ArrayList<>();
            for (Object ro : Json.array(c, "regions")) {
                Map<String, Object> r = obj(ro);
                only(r, v12 ? Set.of("kind", "x", "y", "width", "height", "tone", "text", "size", "weight", "ink", "align")
                        : v11 ? Set.of("kind", "x", "y", "width", "height", "tone") : Set.of("kind", "x", "y", "width", "height"));
                regs.add(new DocumentDraft.Region(req(r, "kind"), (int) Json.number(r, "x", -1), (int) Json.number(r, "y", -1), (int) Json.number(r, "width", 0),
                        (int) Json.number(r, "height", 0), Json.string(r, "tone"), Json.string(r, "text"), (int) Json.number(r, "size", 0), Json.string(r, "weight"),
                        Json.string(r, "ink"), Json.string(r, "align")));
            }
            DocumentDraft.Crop crop = null;
            if (v11) {
                if (!c.containsKey("ref") || !c.containsKey("crop")) throw new IllegalArgumentException("campo obrigatório: ref/crop");
                Object co = c.get("crop");
                if (co != null && co != Json.NULL) {
                    Map<String, Object> cm = obj(co);
                    only(cm, Set.of("x", "y", "width", "height", "reviewer"));
                    crop = new DocumentDraft.Crop((int) Json.number(cm, "x", -1), (int) Json.number(cm, "y", -1), (int) Json.number(cm, "width", 0),
                            (int) Json.number(cm, "height", 0), req(cm, "reviewer"));
                }
            }
            SafeCapture sc = new SafeCapture(v11 ? req(c, "ref") : req(c, "sha256"), req(c, "sha256"), (int) Json.number(c, "width", 0),
                    (int) Json.number(c, "height", 0), dbl(c, "devicePixelRatio"), Instant.parse(req(c, "observedAt")), (int) Json.number(c, "pageEpoch", 0),
                    req(c, "policy"), Json.string(c, "screenKey"), stepIds(Json.array(c, "forSteps")), anns, limits, regs, crop);
            if (captures.put(sc.ref(), sc) != null) throw new IllegalArgumentException("captura repetida");
        }
        DocumentDraft d = new DocumentDraft(new AutomationId(UUID.fromString(req(root, "automationId"))), rev, sections, captures);
        if (!v11 && d.needsV11()) throw new IllegalArgumentException("rascunho 1.0.0 com conteúdo do 1.1.0");
        if (v12 != d.needsV12()) throw new IllegalArgumentException("versão do rascunho incoerente com a política visual"); // 1.1.0 nunca tem v3
        return d;
    }

    private static DocText text(Map<String, Object> t) {
        if (t == null) throw new IllegalArgumentException("texto ausente");
        only(t, Set.of("value", "state", "reviewer"));
        return new DocText(req(t, "value"), DocText.State.valueOf(req(t, "state")), Json.string(t, "reviewer"));
    }

    private static List<StepId> stepIds(List<Object> l) {
        return l.stream().map(o -> new StepId(UUID.fromString(String.valueOf(o)))).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> obj(Object o) {
        if (!(o instanceof Map<?, ?> m)) throw new IllegalArgumentException("objeto esperado");
        return (Map<String, Object>) m;
    }

    private static void only(Map<String, Object> m, Set<String> allowed) {
        for (String k : m.keySet()) if (!allowed.contains(k)) throw new IllegalArgumentException("campo desconhecido: " + k);
    }

    private static String req(Map<String, Object> m, String k) {
        String v = Json.string(m, k);
        if (v == null) throw new IllegalArgumentException("campo obrigatório: " + k);
        return v;
    }

    private static double dbl(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (!(v instanceof Number n)) throw new IllegalArgumentException("número esperado: " + k);
        return n.doubleValue();
    }

    private static String code(String c) {
        if (c == null || c.isEmpty()) return "";
        if (!c.matches("[A-Z][A-Z0-9_]{0,59}")) throw new IllegalArgumentException("código inválido");
        return c;
    }

    /** Escrita canônica com indentação de 2 espaços. */
    static final class Pretty {
        private Pretty() {}

        @SuppressWarnings("unchecked")
        static void value(StringBuilder b, Object v, int level) {
            if (v == null) {
                b.append("null");
            } else if (v instanceof String s) {
                b.append(Json.write(s));
            } else if (v instanceof java.math.BigDecimal bd) {
                b.append(bd.toPlainString());
            } else if (v instanceof Number || v instanceof Boolean) {
                b.append(v);
            } else if (v instanceof Map<?, ?> m) {
                if (m.isEmpty()) {
                    b.append("{}");
                    return;
                }
                b.append("{\n");
                int i = 0;
                for (Map.Entry<String, Object> e : ((Map<String, Object>) m).entrySet()) {
                    b.append("  ".repeat(level + 1)).append(Json.write(e.getKey())).append(": ");
                    value(b, e.getValue(), level + 1);
                    b.append(++i < m.size() ? ",\n" : "\n");
                }
                b.append("  ".repeat(level)).append('}');
            } else if (v instanceof List<?> l) {
                if (l.isEmpty()) {
                    b.append("[]");
                    return;
                }
                if (l.stream().allMatch(x -> x instanceof String || x instanceof Number)) {
                    b.append('[');
                    for (int i = 0; i < l.size(); i++) {
                        if (i > 0) b.append(", ");
                        value(b, l.get(i), level);
                    }
                    b.append(']');
                    return;
                }
                b.append("[\n");
                for (int i = 0; i < l.size(); i++) {
                    b.append("  ".repeat(level + 1));
                    value(b, l.get(i), level + 1);
                    b.append(i < l.size() - 1 ? ",\n" : "\n");
                }
                b.append("  ".repeat(level)).append(']');
            } else {
                throw new IllegalArgumentException("tipo não suportado");
            }
        }
    }
}
