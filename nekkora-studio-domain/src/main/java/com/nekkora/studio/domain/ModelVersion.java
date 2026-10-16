package com.nekkora.studio.domain;

/**
 * Versão do modelo de automação. Gravada em cada {@link Automation} para que a serialização futura
 * (STUDIO-006) possa migrar formatos sem alterações destrutivas: campos novos entram como opcionais
 * e incrementam o MINOR; mudanças incompatíveis incrementam o MAJOR e exigem migração explícita.
 *
 * <p>STUDIO-008 (D-S008-06): a automação declara a MENOR versão capaz de representá-la — {@link #CURRENT} (1.0.0, a
 * base, sem variáveis) ou {@link #V1_1} (com o catálogo de variáveis). Uma automação sem variáveis continua 1.0.0, igual
 * byte a byte ao que a STUDIO-006 gravava.
 */
public final class ModelVersion {
    /** Identificador do contrato do modelo de automação. */
    public static final String SCHEMA = "nekkora-studio/automation";
    /** Versão de base (SemVer): o modelo sem variáveis declaradas. */
    public static final String CURRENT = "1.0.0";
    /** STUDIO-008: modelo com catálogo de variáveis declaradas. */
    public static final String V1_1 = "1.1.0";
    /** Versão mais recente conhecida por este Studio. */
    public static final String LATEST = V1_1;

    /** A versão é lida por este Studio. */
    public static boolean supported(String version) {
        return CURRENT.equals(version) || V1_1.equals(version);
    }

    private ModelVersion() {}
}
