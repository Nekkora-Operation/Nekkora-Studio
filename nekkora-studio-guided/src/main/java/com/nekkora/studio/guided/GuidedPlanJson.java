package com.nekkora.studio.guided;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON canônico do {@link GuidedPlan} ({@code nekkora-studio/guided-debug-plan/1.0.0}): todos os campos sempre presentes e na
 * mesma ordem, ausência como {@code null}, recuo de dois espaços, quebras {@code \n}. Escrito só a partir das cópias do
 * plano — os objetos do domínio nem chegam aqui.
 */
final class GuidedPlanJson {
    private GuidedPlanJson() {}

    /**
     * @param planRevision revisão a publicar; {@code null} = forma usada para CALCULAR a revisão (revisão vazia e sem a
     *                     revisão da automação, que muda com edições que não alteram o plano)
     */
    static String write(GuidedPlan p, String planRevision) {
        boolean forRevision = planRevision == null;
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema", GuidedPlan.SCHEMA);
        root.put("version", GuidedPlan.VERSION);
        root.put("automationId", p.automationId().value().toString());
        root.put("modelVersion", p.modelVersion());
        root.put("automationRevision", forRevision ? 0L : p.automationRevision());
        root.put("planRevision", forRevision ? "" : planRevision);
        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("rules", p.rulesVersion());
        validation.put("status", p.staticStatus().name());
        validation.put("meaning", "STATIC_ONLY");
        root.put("staticValidation", validation);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("steps", p.steps().size());
        for (GuidedPlan.Applicability a : GuidedPlan.Applicability.values()) {
            summary.put(camel(a.name()), p.steps().stream().filter(s -> s.applicability() == a).count());
        }
        summary.put("variables", p.variables().size());
        root.put("summary", summary);
        List<Object> vars = new ArrayList<>();
        for (GuidedPlan.Variable v : p.variables()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("variableId", v.id().value().toString());
            m.put("name", v.name());
            m.put("technicalType", v.technicalType());
            m.put("semanticType", v.semanticType());
            m.put("semanticStatus", v.semanticStatus());
            m.put("sensitivity", v.sensitivity());
            m.put("origin", v.origin());
            m.put("producerStepId", v.producer() == null ? null : v.producer().value().toString());
            vars.add(m);
        }
        root.put("variables", vars);
        List<Object> steps = new ArrayList<>();
        for (GuidedPlan.Step s : p.steps()) steps.add(step(s));
        root.put("steps", steps);
        root.put("exclusions", GuidedPlan.EXCLUSIONS);
        StringBuilder b = new StringBuilder(4096 + p.steps().size() * 1200);
        value(b, root, 0);
        return b.append('\n').toString();
    }

    private static Map<String, Object> step(GuidedPlan.Step s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stepId", s.id().value().toString());
        m.put("sessionId", s.session().value().toString());
        Map<String, Object> position = new LinkedHashMap<>();
        position.put("session", s.sessionNumber());
        position.put("step", s.stepNumber());
        m.put("position", position);
        m.put("origin", s.origin().name());
        GuidedPlan.Intent i = s.intent();
        Map<String, Object> intent = new LinkedHashMap<>();
        intent.put("actionType", i.actionType().name());
        intent.put("targetRequirement", i.targetRequirement().name());
        intent.put("value", i.value().name());
        intent.put("variableId", i.variableId() == null ? null : i.variableId().value().toString());
        intent.put("variableName", i.variableName());
        intent.put("key", i.key());
        intent.put("optionDeclared", i.optionDeclared());
        m.put("intent", intent);
        m.put("target", target(s.target()));
        if (s.context() == null) {
            m.put("context", null);
        } else {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("application", s.context().application());
            c.put("screenTitle", s.context().screenTitle());
            c.put("location", s.context().location());
            m.put("context", c);
        }
        m.put("applicability", s.applicability().name());
        m.put("criteria", s.criteria().stream().map(Enum::name).toList());
        m.put("uncertainties", s.uncertainties().stream().map(Enum::name).toList());
        m.put("staticFindings", s.findings().stream().map(Enum::name).toList());
        m.put("semanticUncertainties", s.semanticUncertainties().stream().map(Enum::name).toList());
        return m;
    }

    private static Map<String, Object> target(GuidedPlan.Target t) {
        if (t == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("identitySource", t.identitySource().name());
        m.put("state", t.state().name());
        m.put("kind", t.kind());
        m.put("tag", t.tag());
        m.put("name", t.name());
        m.put("sensitive", t.sensitive());
        List<Object> locators = new ArrayList<>();
        for (GuidedPlan.Locator l : t.locators()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("index", l.index());
            x.put("strategy", l.strategy().name());
            x.put("description", l.description());
            x.put("expression", l.expression());
            x.put("stability", l.stability().name());
            x.put("matchCountAtCapture", l.matchCountAtCapture());
            x.put("validatedAtCapture", l.validatedAtCapture());
            locators.add(x);
        }
        m.put("locators", locators);
        m.put("preferredLocator", t.preferredLocator());
        List<Object> manual = new ArrayList<>();
        for (GuidedPlan.ManualLocatorView v : t.manualLocators()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("syntax", v.syntax().name());
            x.put("expression", v.expression());
            x.put("verification", "NOT_VERIFIED");
            manual.add(x);
        }
        m.put("manualLocators", manual);
        List<Object> path = new ArrayList<>();
        for (GuidedPlan.PathStep p : t.accessPath()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("type", p.type().name());
            x.put("label", p.label());
            path.add(x);
        }
        m.put("accessPath", path);
        List<Object> anchors = new ArrayList<>();
        for (GuidedPlan.Anchor a : t.context()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("relation", a.relation());
            x.put("kind", a.kind());
            x.put("name", a.name());
            anchors.add(x);
        }
        m.put("context", anchors);
        return m;
    }

    /** {@code INSPECT_TARGET} → {@code inspectTarget}. */
    private static String camel(String constant) {
        StringBuilder b = new StringBuilder();
        boolean up = false;
        for (char c : constant.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            if (c == '_') {
                up = true;
            } else {
                b.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return b.toString();
    }

    static String revisionOf(String canonical) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ escrita

    @SuppressWarnings("unchecked")
    private static void value(StringBuilder b, Object v, int level) {
        if (v == null) {
            b.append("null");
        } else if (v instanceof String s) {
            b.append(str(s));
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
                indent(b, level + 1).append(str(e.getKey())).append(": ");
                value(b, e.getValue(), level + 1);
                b.append(++i < m.size() ? ",\n" : "\n");
            }
            indent(b, level).append('}');
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
                indent(b, level + 1);
                value(b, l.get(i), level + 1);
                b.append(i < l.size() - 1 ? ",\n" : "\n");
            }
            indent(b, level).append(']');
        } else {
            throw new IllegalArgumentException("tipo não suportado no plano: " + v.getClass().getSimpleName());
        }
    }

    private static StringBuilder indent(StringBuilder b, int level) {
        return b.append("  ".repeat(level));
    }

    /** String JSON (RFC 8259): aspas, barra invertida e controles escapados. */
    static String str(String s) {
        StringBuilder b = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }
}
