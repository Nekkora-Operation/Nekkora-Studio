package com.nekkora.studio.domain.action;

/**
 * Relação de um tipo de ação com um elemento-alvo. Nenhum requisito é obrigatório nesta fase: etapas
 * manuais podem existir sem referência de elemento e recebê-la mais tarde (gravação, edição).
 */
public enum TargetRequirement {
    /** A ação normalmente atua sobre um elemento (clicar, preencher). */
    EXPECTED,
    /** A ação pode ou não envolver um elemento (aguardar, observar). */
    OPTIONAL,
    /** A ação não atua sobre um elemento (navegar). */
    NONE
}
