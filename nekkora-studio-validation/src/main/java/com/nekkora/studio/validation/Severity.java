package com.nekkora.studio.validation;

/**
 * Severidade de um apontamento (STUDIO-007). Determinada pelo CÓDIGO do apontamento ({@link FindingCode}) — nunca por
 * texto gerado. O símbolo e o rótulo existem para que a severidade nunca dependa só de cor.
 */
public enum Severity {
    /** Falta uma informação obrigatória para a configuração da ação. */
    BLOCKER("Bloqueio", "✕", "bloqueio", "bloqueios"),
    /** Limitação ou risco técnico conhecido. */
    WARNING("Aviso", "!", "aviso", "avisos"),
    /** Informação relevante, sem comprovação de problema. */
    INFO("Informação", "i", "informação", "informações");

    private final String label;
    private final String symbol;
    private final String singular;
    private final String plural;

    Severity(String label, String symbol, String singular, String plural) {
        this.label = label;
        this.symbol = symbol;
        this.singular = singular;
        this.plural = plural;
    }

    public String label() {
        return label;
    }

    /** Símbolo textual (✕ ! i): acompanha a cor, nunca é substituído por ela. */
    public String symbol() {
        return symbol;
    }

    /** "1 bloqueio", "2 avisos", "0 informações". */
    public String count(int n) {
        return n + " " + (n == 1 ? singular : plural);
    }
}
