package com.nekkora.studio.validation;

/**
 * Versões das regras da validação estrutural. Cada versão fixa o catálogo de códigos que pode emitir e a versão do
 * contrato do relatório. Uma versão nunca muda depois de publicada: a STUDIO-008 acrescentou {@link #V1_1} sem alterar
 * {@link #V1_0} (o relatório 1.0.0 continua idêntico, byte a byte).
 */
public enum RuleSet {
    /** STUDIO-007: estrutura, parâmetros, elementos, localizadores e observação. */
    V1_0("nekkora-validation-rules/1.0.0", "1.0.0"),
    /** STUDIO-008: + variáveis declaradas e fluxo de dados (uso derivado, produtor só declarado, escopo, tipo). */
    V1_1("nekkora-validation-rules/1.1.0", "1.1.0");

    /** A versão mais nova (a usada pelo Studio). */
    public static final RuleSet LATEST = V1_1;

    private final String version;
    private final String reportVersion;

    RuleSet(String version, String reportVersion) {
        this.version = version;
        this.reportVersion = reportVersion;
    }

    /** Identificador das regras (vai no relatório). */
    public String version() {
        return version;
    }

    /** Versão do contrato {@value ValidationReport#SCHEMA} produzido por estas regras. */
    public String reportVersion() {
        return reportVersion;
    }

    /** Esta versão das regras inclui a outra (as regras são cumulativas). */
    public boolean includes(RuleSet other) {
        return ordinal() >= other.ordinal();
    }
}
