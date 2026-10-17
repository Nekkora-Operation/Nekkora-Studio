package com.nekkora.studio.semantics;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON canônico do {@link SemanticContract}: campos sempre presentes e na mesma ordem, ausência como {@code null}, recuo de
 * dois espaços, quebras {@code \n}. Escrito à mão e só a partir das cópias do contrato (nunca dos objetos do domínio): a
 * finalidade, o valor fixo e os nomes de etapas e sessões nem chegam aqui.
 */
final class SemanticContractJson {
    private SemanticContractJson() {}

    static String write(SemanticContract c) {
        StringBuilder b = new StringBuilder(1024 + c.steps().size() * 200 + c.variables().size() * 600);
        b.append("{\n");
        field(b, 1, "schema", str(SemanticContract.SCHEMA), true);
        field(b, 1, "version", str(SemanticContract.VERSION), true);
        field(b, 1, "automationId", str(c.automationId().value().toString()), true);
        field(b, 1, "modelVersion", str(c.modelVersion()), true);
        field(b, 1, "dataFlowRules", str(SemanticContract.DATA_FLOW_RULES), true);
        long references = c.steps().stream().filter(s -> s.value() == SemanticContract.ValueKind.VARIABLE).count();
        long undeclared = c.steps().stream().filter(s -> s.reference() == SemanticContract.Reference.UNDECLARED).count();
        indent(b, 1).append("\"summary\": {\"variables\": ").append(c.variables().size()).append(", \"steps\": ").append(c.steps().size())
                .append(", \"variableReferences\": ").append(references).append(", \"undeclaredReferences\": ").append(undeclared)
                .append(", \"uncertainties\": ").append(c.uncertainties().size()).append("},\n");
        array(b, "variables", c.variables(), v -> variable(v));
        array(b, "steps", c.steps(), s -> "{\"stepId\": " + str(s.id().value().toString()) + ", \"sessionId\": " + str(s.session().value().toString())
                + ", \"position\": {\"session\": " + s.sessionNumber() + ", \"step\": " + s.stepNumber() + "}, \"actionType\": " + str(s.actionType().name())
                + ", \"value\": " + str(s.value().name()) + ", \"reference\": " + (s.reference() == null ? "null" : str(s.reference().name()))
                + ", \"variableId\": " + (s.variable() == null ? "null" : str(s.variable().value().toString())) + "}");
        array(b, "uncertainties", c.uncertainties(), u -> "{\"code\": " + str(u.code().name()) + ", \"variableId\": "
                + (u.variable() == null ? "null" : str(u.variable().value().toString())) + ", \"stepId\": "
                + (u.step() == null ? "null" : str(u.step().value().toString())) + "}");
        indent(b, 1).append("\"exclusions\": [");
        for (int i = 0; i < SemanticContract.EXCLUSIONS.size(); i++) b.append(i == 0 ? "" : ", ").append(str(SemanticContract.EXCLUSIONS.get(i)));
        b.append("]\n");
        b.append("}\n");
        return b.toString();
    }

    private static String variable(SemanticContract.Variable d) {
        SemanticContract.Constraints c = d.constraints();
        List<String> consumers = new ArrayList<>();
        for (var s : d.consumers()) consumers.add(str(s.value().toString()));
        String producer = d.producer() == null ? "null"
                : "{\"stepId\": " + str(d.producer().step().value().toString()) + ", \"status\": " + str(d.producer().status().name())
                        + ", \"runtimeVerification\": \"UNVERIFIED_AT_RUNTIME\"}";
        return "{\"variableId\": " + str(d.id().value().toString()) + ", \"name\": " + str(d.name()) + ", \"technicalType\": " + str(d.technicalType().name())
                + ", \"semanticType\": " + str(d.semanticType().name()) + ", \"semanticStatus\": " + str(d.semanticStatus().name()) + ", \"sensitivity\": "
                + str(d.sensitivity().name()) + ",\n      \"scope\": {\"kind\": " + str(d.scope().kind().name()) + ", \"sessionId\": "
                + (d.scope().session() == null ? "null" : str(d.scope().session().value().toString())) + "}, \"origin\": {\"kind\": "
                + str(d.origin().kind().name()) + ", \"constantDeclared\": " + d.origin().constantDeclared() + ", \"producer\": " + producer + "},\n      \"constraints\": {\"required\": "
                + c.required() + ", \"minLength\": " + c.minLength() + ", \"maxLength\": " + c.maxLength() + ", \"textFormat\": " + str(c.textFormat().name())
                + ", \"minimum\": " + (c.minimum() == null ? "null" : str(c.minimum().toPlainString())) + ", \"maximum\": "
                + (c.maximum() == null ? "null" : str(c.maximum().toPlainString())) + ", \"scale\": " + c.scale() + ", \"currency\": "
                + (c.currency() == null ? "null" : str(c.currency())) + "},\n      \"consumers\": [" + String.join(", ", consumers) + "]}";
    }

    private static <T> void array(StringBuilder b, String name, List<T> items, java.util.function.Function<T, String> item) {
        indent(b, 1).append('"').append(name).append("\": [");
        if (items.isEmpty()) {
            b.append("],\n");
            return;
        }
        b.append('\n');
        for (int i = 0; i < items.size(); i++) indent(b, 2).append(item.apply(items.get(i))).append(i == items.size() - 1 ? "\n" : ",\n");
        indent(b, 1).append("],\n");
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
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch));
                    else b.append(ch);
                }
            }
        }
        return b.append('"').toString();
    }
}
