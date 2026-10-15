package com.nekkora.studio.app.workspace;

/** O que mudou no estado da aplicação — cada parte da tela atualiza apenas o que lhe diz respeito. */
public enum StudioChange {
    /** Automações do catálogo (criação, nome, resumo). */
    CATALOG,
    /** Conjunto de automações abertas na área de trabalho. */
    OPEN_AUTOMATIONS,
    /** Automação ativa. */
    ACTIVE_AUTOMATION,
    /** Sessões/etapas de uma automação. */
    CONTENT,
    /** Item selecionado. */
    SELECTION,
    /** Existência de alterações não persistidas. */
    UNSAVED_CHANGES,
    /** Nova notificação para o usuário. */
    NOTICE,
    /** Diálogo/operação em andamento. */
    OPERATION,
    /** Histórico de edição (desfazer/refazer) de uma automação (STUDIO-004). */
    HISTORY,
    /** Uma automação passou a estar (ou deixou de estar) bloqueada para edição por uma gravação (STUDIO-004). */
    EDIT_LOCK
}
