package com.nekkora.studio.domain.evidence;

/**
 * Tipo de uma evidência observada (STUDIO-005, contrato {@code evidence/1}). Fechado: cada tipo tem regra de produção na
 * extensão e testes. Nenhum tipo afirma resultado de negócio — uma mensagem de erro observada não é uma falha validada.
 */
public enum EvidenceType {
    FORM_SUBMITTED("Formulário enviado", Category.FORM),
    ROUTE_CHANGED("Rota alterada", Category.NAVIGATION),
    DOCUMENT_CHANGED("Novo documento carregado", Category.NAVIGATION),
    VISIBLE_ALERT("Alerta exibido", Category.MESSAGE),
    VISIBLE_ERROR_MESSAGE("Mensagem de erro exibida", Category.MESSAGE),
    VISIBLE_STATUS_MESSAGE("Mensagem de status exibida", Category.MESSAGE),
    VALIDATION_FEEDBACK("Validação de campo", Category.VALIDATION),
    LOADING_INDICATOR("Carregamento iniciado", Category.LOADING),
    LOADING_FINISHED("Carregamento terminado", Category.LOADING),
    ELEMENT_STATE_CHANGED("Estado do elemento alterado", Category.STATE),
    CONTENT_CHANGED("Conteúdo atualizado", Category.CONTENT),
    OBSERVATION_COMPLETE("Observação concluída", Category.WINDOW),
    OBSERVATION_INCOMPLETE("Observação incompleta", Category.WINDOW),
    AMBIGUOUS_FEEDBACK("Mudança observada sem etapa definida", Category.AMBIGUOUS);

    /** Agrupamento para apresentação e resumo. */
    public enum Category { FORM, NAVIGATION, MESSAGE, VALIDATION, LOADING, STATE, CONTENT, WINDOW, AMBIGUOUS }

    private final String label;
    private final Category category;

    EvidenceType(String label, Category category) {
        this.label = label;
        this.category = category;
    }

    public String label() {
        return label;
    }

    public Category category() {
        return category;
    }

    /** Marca o fim da janela de observação (não é um sinal observado). */
    public boolean windowEnd() {
        return category == Category.WINDOW;
    }
}
