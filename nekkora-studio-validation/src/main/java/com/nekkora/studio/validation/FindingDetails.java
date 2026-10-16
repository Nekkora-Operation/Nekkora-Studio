package com.nekkora.studio.validation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vocabulário FECHADO dos detalhes de um apontamento. O único detalhe hoje é o tipo de obstáculo observado: vem do
 * produtor (PageMap {@code AutomationObstacle.type} e as limitações do Recorder), mas só entra no relatório se estiver
 * nesta lista — qualquer outro valor vira {@link #OTHER}. Assim nenhum texto do produtor chega ao relatório.
 */
public final class FindingDetails {
    /** Tipo fora do vocabulário conhecido. */
    public static final String OTHER = "OTHER";

    private static final Map<String, String> OBSTACLES = new LinkedHashMap<>();

    static {
        // PageMap 0.2.0 (contracts/page-map.schema.json da Chrome Extension, $defs/AutomationObstacle/type)
        OBSTACLES.put("NESTED_IFRAME", "frame aninhado");
        OBSTACLES.put("FRAME_CONTEXT", "contexto de frame");
        OBSTACLES.put("CROSS_ORIGIN_FRAME", "frame de outra origem");
        OBSTACLES.put("INACCESSIBLE_FRAME", "frame inacessível");
        OBSTACLES.put("SANDBOXED_FRAME", "frame isolado (sandbox)");
        OBSTACLES.put("FRAME_LOADING", "frame carregando");
        OBSTACLES.put("SHADOW_DOM", "shadow DOM");
        OBSTACLES.put("CLOSED_SHADOW_ROOT", "shadow root fechado");
        OBSTACLES.put("VIRTUALIZED_COMPONENT", "componente virtualizado");
        OBSTACLES.put("DYNAMIC_IDS", "identificadores dinâmicos");
        OBSTACLES.put("AMBIGUOUS_ELEMENTS", "elementos ambíguos");
        OBSTACLES.put("ASYNC_LOADING", "carregamento assíncrono");
        OBSTACLES.put("TRANSIENT_ELEMENTS", "elementos transitórios");
        OBSTACLES.put("MODAL", "janela modal");
        OBSTACLES.put("PORTAL", "portal");
        OBSTACLES.put("SPA_NAVIGATION", "navegação de aplicação de página única");
        OBSTACLES.put("INTERACTION_DEPENDENCY", "dependência de outra interação");
        OBSTACLES.put("INACCESSIBLE_ELEMENTS", "elementos inacessíveis");
        OBSTACLES.put("HUMAN_VERIFICATION", "verificação humana");
        OBSTACLES.put("ANTIBOT_TECHNOLOGY", "tecnologia antirrobô");
        OBSTACLES.put("ACCESS_BLOCKED", "acesso bloqueado");
        OBSTACLES.put("UNKNOWN_PROTECTION", "proteção desconhecida");
        // limitações do Recorder registradas como obstáculo (STUDIO-003, RecordedStepDrafts)
        OBSTACLES.put("ELEMENT_UNRESOLVED", "elemento não correlacionado");
        OBSTACLES.put("AMBIGUOUS_TARGET", "alvo sem semântica de interação");
        OBSTACLES.put("INDISTINGUISHABLE_LOOKALIKES", "elementos idênticos indistinguíveis");
        OBSTACLES.put("ACCESS_RESTRICTED", "trajetória com acesso restrito");
        OBSTACLES.put(OTHER, "tipo não reconhecido");
    }

    private FindingDetails() {}

    /** Detail de um obstáculo: o próprio tipo, se conhecido; senão {@link #OTHER}. */
    public static String obstacle(String type) {
        return type != null && OBSTACLES.containsKey(type) ? type : OTHER;
    }

    /** Rótulo legível de um detalhe (vazio se desconhecido). */
    public static String label(String detail) {
        return detail == null ? "" : OBSTACLES.getOrDefault(detail, "");
    }

    /** Todos os detalhes possíveis (contrato do relatório). */
    public static java.util.Set<String> all() {
        return java.util.Collections.unmodifiableSet(OBSTACLES.keySet());
    }
}
