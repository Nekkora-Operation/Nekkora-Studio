package com.nekkora.studio.codegen;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NEKKORA-CODEGEN-QUALITY-001 — evidência de NOMES que o planejador entrega ao gerador junto da configuração validada:
 * textos REVISADOS do PDD por Page e por método (viram docstrings) e de onde cada nome veio, e o registro de rastreio
 * (traceId já atribuído a cada etapa, os aposentados e, na rodada 3, os traceIds ANTERIORES de cada etapa). Nada aqui é
 * valor de negócio: são títulos do PDD (já filtrados pela política de texto) e identificadores.
 *
 * @param pages    por classe da Page: título da seção, títulos de bloco, fonte do nome e a evidência da tela
 * @param methods  por {@code Page.metodo}: título do bloco que deu o nome (ou vazio) e a fonte do nome
 * @param traces   traceId já atribuído por StepId (o registro de rastreio da automação, na convenção atual)
 * @param retired  traceIds aposentados (etapas removidas): nunca reaproveitados
 * @param previous traceIds que a etapa já teve (ex.: os em português de antes da rodada 3): declarados no manifesto,
 *                 nunca reaproveitados por outra etapa
 */
public record CodeNames(Map<String, PageInfo> pages, Map<String, MethodInfo> methods, Map<String, String> traces, Set<String> retired,
                        Map<String, List<String>> previous) {

    /**
     * Fonte de um nome (vai ao manifesto e à prévia da arquitetura). {@code PDD_DATA_OWNER}: o dono dos dados dito num
     * título do PDD ("os dados da ficha" → {@code ProfilePage}).
     */
    public enum Source { PDD_DATA_OWNER, PDD_SECTION_TITLE, PDD_BLOCK_TITLE, SESSION_NAME, ACTIONS, PROVISIONAL }

    /**
     * @param model        nome da dataclass dos dados lidos nesta tela quando o PDD diz de quem são ({@code ProfileData});
     *                     vazio = o gerador usa {@code <Tela>Data}
     * @param nameFrom     o texto em português de onde o nome inglês saiu ("ficha", "Área segura"), para rastrear a origem
     * @param untranslated palavras desse texto fora do glossário (ficaram como estão no nome: pendência apontada)
     */
    public record PageInfo(String title, List<String> blockTitles, Source source, String boundary, String model, String nameFrom, List<String> untranslated) {
        public PageInfo {
            title = title == null ? "" : title;
            blockTitles = List.copyOf(blockTitles);
            boundary = boundary == null ? "" : boundary;
            model = model == null ? "" : model;
            nameFrom = nameFrom == null ? "" : nameFrom;
            untranslated = untranslated == null ? List.of() : List.copyOf(untranslated);
        }

        public PageInfo(String title, List<String> blockTitles, Source source, String boundary, String model) {
            this(title, blockTitles, source, boundary, model, "", List.of());
        }

        public PageInfo(String title, List<String> blockTitles, Source source, String boundary) {
            this(title, blockTitles, source, boundary, "");
        }
    }

    public record MethodInfo(String title, Source source, String nameFrom, List<String> untranslated) {
        public MethodInfo {
            title = title == null ? "" : title;
            nameFrom = nameFrom == null ? "" : nameFrom;
            untranslated = untranslated == null ? List.of() : List.copyOf(untranslated);
        }

        public MethodInfo(String title, Source source) {
            this(title, source, "", List.of());
        }
    }

    public CodeNames {
        pages = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(pages));
        methods = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(methods));
        traces = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(traces));
        retired = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(retired));
        Map<String, List<String>> p = new LinkedHashMap<>();
        if (previous != null) previous.forEach((k, v) -> p.put(k, List.copyOf(v)));
        previous = java.util.Collections.unmodifiableMap(p);
    }

    public CodeNames(Map<String, PageInfo> pages, Map<String, MethodInfo> methods, Map<String, String> traces, Set<String> retired) {
        this(pages, methods, traces, retired, Map.of());
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
