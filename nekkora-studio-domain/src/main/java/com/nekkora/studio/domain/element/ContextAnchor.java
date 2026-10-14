package com.nekkora.studio.domain.element;

import java.util.Objects;

/**
 * Relação de contexto necessária para reencontrar o elemento (linha de tabela, modal, formulário,
 * componente ancestral). Não há profundidade fixa: o produtor inclui as âncoras de que a identificação
 * depende, e nenhuma é descartada pelo Studio.
 *
 * @param relation tipo da relação — valor de {@code ContextRelation.type} do PageMap (ex.: {@code IN_ROW},
 *                 {@code IN_DIALOG}); mantido como texto para não fixar o Studio a uma versão do contrato
 * @param kind     categoria semântica da âncora (valor de {@code elementKind}, ex.: {@code TABLE_ROW})
 * @param name     nome legível e seguro da âncora (ex.: "Linha do cliente 101")
 * @param locator  localizador da âncora; pode ser vazio
 */
public record ContextAnchor(String relation, String kind, String name, String locator) {
    public ContextAnchor {
        Objects.requireNonNull(relation, "relation");
        kind = kind == null ? ElementReference.UNKNOWN_KIND : kind;
        name = name == null ? "" : name;
        locator = locator == null ? "" : locator;
    }
}
