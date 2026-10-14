package com.nekkora.studio.domain;

/**
 * Versão do modelo de automação. Gravada em cada {@link Automation} para que a serialização futura
 * (STUDIO-006) possa migrar formatos sem alterações destrutivas: campos novos entram como opcionais
 * e incrementam o MINOR; mudanças incompatíveis incrementam o MAJOR e exigem migração explícita.
 */
public final class ModelVersion {
    /** Identificador do contrato do modelo de automação. */
    public static final String SCHEMA = "nekkora-studio/automation";
    /** Versão atual (SemVer). */
    public static final String CURRENT = "1.0.0";

    private ModelVersion() {}
}
