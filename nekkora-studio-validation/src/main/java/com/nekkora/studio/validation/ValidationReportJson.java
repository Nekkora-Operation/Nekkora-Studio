package com.nekkora.studio.validation;

/**
 * JSON canônico do {@link ValidationReport} (contrato {@value ValidationReport#SCHEMA} {@value ValidationReport#VERSION}):
 * campos sempre presentes, sempre na mesma ordem, ausência como {@code null}, recuo de dois espaços e quebras {@code \n}.
 * O mesmo relatório produz exatamente os mesmos bytes.
 */
final class ValidationReportJson {
    private ValidationReportJson() {}

    static String write(ValidationReport r) {
        StringBuilder b = new StringBuilder(256 + r.findings().size() * 400);
        b.append("{\n");
        field(b, 1, "schema", str(ValidationReport.SCHEMA), true);
        field(b, 1, "version", str(ValidationReport.VERSION), true);
        field(b, 1, "rules", str(r.rulesVersion()), true);
        field(b, 1, "automationId", str(r.automationId().value().toString()), true);
        field(b, 1, "status", str(r.status().name()), true);
        ValidationReport.Summary s = r.summary();
        indent(b, 1).append("\"summary\": {\n");
        field(b, 2, "sessions", Integer.toString(s.sessions()), true);
        field(b, 2, "steps", Integer.toString(s.steps()), true);
        field(b, 2, "blockers", Integer.toString(s.blockers()), true);
        field(b, 2, "warnings", Integer.toString(s.warnings()), true);
        field(b, 2, "infos", Integer.toString(s.infos()), true);
        field(b, 2, "stepsWithoutFindings", Integer.toString(s.stepsWithoutFindings()), true);
        field(b, 2, "stepsWithBlockers", Integer.toString(s.stepsWithBlockers()), false);
        indent(b, 1).append("},\n");
        indent(b, 1).append("\"findings\": [");
        if (r.findings().isEmpty()) {
            b.append("]\n");
        } else {
            b.append('\n');
            for (int i = 0; i < r.findings().size(); i++) {
                Finding f = r.findings().get(i);
                indent(b, 2).append("{\n");
                field(b, 3, "id", str(f.id()), true);
                field(b, 3, "code", str(f.code().name()), true);
                field(b, 3, "severity", str(f.severity().name()), true);
                field(b, 3, "scope", str(f.scope().name()), true);
                field(b, 3, "sessionId", f.session() == null ? "null" : str(f.session().value().toString()), true);
                field(b, 3, "stepId", f.step() == null ? "null" : str(f.step().value().toString()), true);
                field(b, 3, "position", f.scope() == FindingScope.AUTOMATION ? "null"
                        : "{\"session\": " + f.sessionNumber() + ", \"step\": " + (f.scope() == FindingScope.STEP ? Integer.toString(f.stepNumber()) : "null") + "}",
                        true);
                field(b, 3, "field", str(f.field().name()), true);
                field(b, 3, "detail", f.detail() == null ? "null" : str(f.detail()), true);
                field(b, 3, "limitsAssessment", Boolean.toString(f.code().limitsAssessment()), true);
                field(b, 3, "explanation", str(f.explanation()), true);
                field(b, 3, "guidance", str(f.guidance()), false);
                indent(b, 2).append(i == r.findings().size() - 1 ? "}\n" : "},\n");
            }
            indent(b, 1).append("]\n");
        }
        b.append("}\n");
        return b.toString();
    }

    private static void field(StringBuilder b, int level, String name, String json, boolean comma) {
        indent(b, level).append('"').append(name).append("\": ").append(json).append(comma ? ",\n" : "\n");
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
