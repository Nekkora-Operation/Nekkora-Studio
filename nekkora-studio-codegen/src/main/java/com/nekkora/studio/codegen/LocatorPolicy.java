package com.nekkora.studio.codegen;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * NEKKORA-CODEGEN-QUALITY-001 (Q03) — classificação e recusa de localizadores do registro gerado. Ordem de preferência
 * (a gravação e o debug guiado já escolheram e CONFIRMARAM o alvo): identificador forte → âncora semântica → XPath
 * relacional. Posição nunca é aceita, nem disfarçada dentro de CSS/XPath: {@code :nth-child}, {@code :nth-of-type},
 * {@code >> nth=}, XPath absoluto ({@code /html…}), índice numérico fora de um eixo de irmão vizinho, {@code position()},
 * {@code last()}. A unicidade em execução é do framework: 0 ou 2+ correspondências → erro, nunca "o primeiro".
 */
public final class LocatorPolicy {
    private LocatorPolicy() {}

    /** Tipo do localizador, do mais forte ao mais fraco (o fraco não sai do gerador). Rótulo em inglês: vai para o README gerado. */
    public enum Kind {
        ROLE_NAME("role + accessible name"), LABEL("associated label"), STABLE_ID("stable ID or test attribute"),
        ROW_ANCHOR("row anchored by its heading (heading → cell, or term → definition)"), SCOPED("stable container + attribute"),
        RELATIONAL_XPATH("relational XPath"), ATTRIBUTE("attribute"), POSITIONAL("position (refused)");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final Pattern CSS_POSITION = Pattern.compile(":nth-(child|of-type|last-child|last-of-type)\\s*\\(|:first-child|:last-child|>>\\s*nth=|:eq\\(");
    private static final Pattern XPATH_ABSOLUTE = Pattern.compile("^\\(?\\s*/(html|body)\\b|^\\(?\\s*/[a-z]");
    private static final Pattern XPATH_POSITION_FN = Pattern.compile("position\\s*\\(|last\\s*\\(");
    /** índice numérico [n] — permitido só logo depois de um eixo de irmão (following/preceding-sibling::x[1]). */
    private static final Pattern XPATH_INDEX = Pattern.compile("(following-sibling::|preceding-sibling::)?[A-Za-z*][\\w-]*\\[\\s*\\d+\\s*]");
    private static final Pattern ROW_ANCHOR = Pattern.compile("(th|dt)\\[(1\\]\\[)?normalize-space\\(\\.\\)=\"[^\"]+\"\\]");
    private static final Pattern STABLE_ID = Pattern.compile("^[a-z0-9]*#[A-Za-z][\\w-]*$|\\[data-testid=|\\[data-test=|\\[data-qa=");

    /** O localizador depende de posição (ou é absoluto)? Recusado na geração. */
    public static boolean positional(String strategy, String expression) {
        if (expression == null) return false;
        String e = expression.strip();
        if ("css".equals(strategy)) return CSS_POSITION.matcher(e).find();
        if ("xpath".equals(strategy)) {
            if (XPATH_ABSOLUTE.matcher(e).find() && !e.startsWith("//") && !e.startsWith("(//")) return true;
            if (XPATH_POSITION_FN.matcher(e).find()) return true;
            var m = XPATH_INDEX.matcher(e);
            while (m.find()) if (m.group(1) == null) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static Kind kind(Map<String, Object> target) {
        Map<String, Object> p = (Map<String, Object>) target.get("preferred");
        String strategy = String.valueOf(p.get("strategy"));
        String expr = p.get("expression") == null ? "" : String.valueOf(p.get("expression"));
        if ("role".equals(strategy)) return Kind.ROLE_NAME;
        if ("label".equals(strategy)) return Kind.LABEL;
        if (positional(strategy, expr)) return Kind.POSITIONAL;
        if ("xpath".equals(strategy)) return ROW_ANCHOR.matcher(expr).find() ? Kind.ROW_ANCHOR : Kind.RELATIONAL_XPATH;
        if (STABLE_ID.matcher(expr).find()) return Kind.STABLE_ID;
        if (expr.contains(" ") || expr.contains(">")) return Kind.SCOPED;
        return Kind.ATTRIBUTE;
    }

    /**
     * Descrição curta para o README e o manifesto gerados (o seletor completo fica no selectors.json). Rodada 4 (R4-01): em
     * INGLÊS, como o resto do projeto gerado; o rótulo da página e a expressão aparecem como estão (são dados).
     */
    @SuppressWarnings("unchecked")
    public static String describe(Map<String, Object> target) {
        Map<String, Object> p = (Map<String, Object>) target.get("preferred");
        String strategy = String.valueOf(p.get("strategy"));
        if ("role".equals(strategy)) return "role `" + p.get("role") + "` named `" + p.get("name") + "`";
        if ("label".equals(strategy)) return "field labelled `" + p.get("text") + "`";
        String expr = String.valueOf(p.get("expression"));
        return strategy.toUpperCase(java.util.Locale.ROOT) + " `" + expr.replace("|", "\\|").replace("`", "'") + "`";
    }
}
