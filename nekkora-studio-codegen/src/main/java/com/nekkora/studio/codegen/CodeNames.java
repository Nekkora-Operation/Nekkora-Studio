package com.nekkora.studio.codegen;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NEKKORA-CODEGEN-QUALITY-001 — evidência de NOMES que o planejador entrega ao gerador junto da configuração validada:
 * textos REVISADOS do PDD por Page e por método (viram docstrings) e de onde cada nome veio, e o registro de rastreio
 * (traceId já atribuído a cada etapa, e os aposentados). Nada aqui é valor de negócio: são títulos do PDD (já filtrados
 * pela política de texto) e identificadores.
 *
 * @param pages   por classe da Page: título da seção, títulos de bloco, fonte do nome e a evidência da tela
 * @param methods por {@code Page.metodo}: título do bloco que deu o nome (ou vazio) e a fonte do nome
 * @param traces  traceId já atribuído por StepId (o registro de rastreio da automação)
 * @param retired traceIds aposentados (etapas removidas): nunca reaproveitados
 */
public record CodeNames(Map<String, PageInfo> pages, Map<String, MethodInfo> methods, Map<String, String> traces, Set<String> retired) {

    /** Fonte de um nome (vai ao manifesto e à prévia da arquitetura). */
    public enum Source { PDD_SECTION_TITLE, PDD_BLOCK_TITLE, SESSION_NAME, ACTIONS, PROVISIONAL }

    public record PageInfo(String title, List<String> blockTitles, Source source, String boundary) {
        public PageInfo {
            title = title == null ? "" : title;
            blockTitles = List.copyOf(blockTitles);
            boundary = boundary == null ? "" : boundary;
        }
    }

    public record MethodInfo(String title, Source source) {
        public MethodInfo {
            title = title == null ? "" : title;
        }
    }

    public CodeNames {
        pages = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(pages));
        methods = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(methods));
        traces = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(traces));
        retired = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(retired));
    }

    /** Sem evidência do PDD nem registro (chamadas antigas e testes de baixo nível): nomes e docstrings neutros. */
    public static CodeNames none() {
        return new CodeNames(Map.of(), Map.of(), Map.of(), Set.of());
    }

    public PageInfo page(String className) {
        return pages.getOrDefault(className, new PageInfo("", List.of(), Source.PROVISIONAL, ""));
    }

    public MethodInfo method(String page, String method) {
        return methods.getOrDefault(page + "." + method, new MethodInfo("", Source.ACTIONS));
    }
}
