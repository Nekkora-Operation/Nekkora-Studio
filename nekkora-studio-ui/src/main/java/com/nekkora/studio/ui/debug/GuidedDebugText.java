package com.nekkora.studio.ui.debug;

import java.util.List;
import java.util.Map;

/**
 * STUDIO-009 (§1.4): textos do debug guiado DERIVADOS DE CÓDIGOS — nunca da página e nunca inventados. Cada código do
 * contrato {@code guided-debug/1} (status, motivos, verificações) e da regra de reuso tem uma frase curta e fixa; um código
 * desconhecido aparece como "motivo não descrito" (nada é suposto). Nenhum texto aqui vem do navegador.
 */
public final class GuidedDebugText {
    private GuidedDebugText() {}

    /** O que o resultado da inspeção significa (o "por quê" principal). */
    public static String status(String status, String identitySource) {
        boolean descriptor = "DESCRIPTOR".equals(identitySource);
        return switch (status == null ? "" : status) {
            case "RESOLVED" -> descriptor
                    ? "Elemento reencontrado pelo descritor salvo (tipo, nome e identificadores); a prova é mais fraca que a identidade registrada."
                    : "Elemento reencontrado pela identidade registrada na gravação, comparando tipo, nome e contexto.";
            case "NOT_FOUND" -> "Nenhum elemento compatível com esta etapa foi encontrado na página atual.";
            case "AMBIGUOUS" -> "Há mais de um elemento igualmente plausível: nenhum é escolhido por palpite.";
            case "IDENTITY_MISMATCH" -> "O endereço do alvo agora contém outro tipo de controle.";
            case "STALE_TARGET" -> "O elemento foi removido da página e não há substituto comprovado.";
            case "SCOPE_UNAVAILABLE" -> "O alvo fica num quadro ou componente que não pode ser inspecionado agora.";
            case "INSUFFICIENT_EVIDENCE" -> "Há um candidato plausível, mas sem prova suficiente de que é o mesmo elemento.";
            case "CONTEXT_UNAVAILABLE" -> "Não foi possível observar a página agora (navegação, carregamento ou extensão indisponível).";
            default -> "Resultado não descrito.";
        };
    }

    private static final Map<String, String> REASONS = Map.ofEntries(
            Map.entry("TARGET_UNKNOWN_IN_REGISTRY", "A identidade registrada não está disponível neste navegador; usado o descritor salvo."),
            Map.entry("DESCRIPTOR_ONLY", "Reencontro só pelo descritor salvo (sem a identidade registrada)."),
            Map.entry("DESCRIPTOR_CANNOT_CROSS_SCOPE", "Pelo descritor não é possível atravessar quadros ou componentes fechados."),
            Map.entry("INDISTINGUISHABLE_AT_REGISTRATION", "Já na gravação havia outro elemento idêntico."),
            Map.entry("LOOKALIKES_PRESENT", "Há elementos parecidos na página; o alvo foi diferenciado pelo contexto."),
            Map.entry("NODE_REPLACED", "A página recriou o elemento desde a gravação (mesma identidade)."),
            Map.entry("ID_ATTRIBUTE_CHANGED", "O atributo id do elemento mudou."),
            Map.entry("NAME_ATTRIBUTE_CHANGED", "O atributo name do elemento mudou."),
            Map.entry("KIND_INCOMPATIBLE_AT_ADDRESS", "O localizador forte agora aponta para um controle de outro tipo."),
            Map.entry("FRAME_NOT_FOUND", "O quadro (iframe) do alvo não foi encontrado."),
            Map.entry("FRAME_AMBIGUOUS", "Há mais de um quadro (iframe) compatível."),
            Map.entry("FRAME_DOCUMENT_CHANGED", "O documento do quadro mudou."),
            Map.entry("FRAME_PERMISSION_DENIED", "O quadro pertence a uma origem sem autorização."),
            Map.entry("FRAME_SANDBOX_RESTRICTED", "O quadro está isolado (sandbox)."),
            Map.entry("FRAME_LOADING", "O quadro ainda está carregando."),
            Map.entry("SHADOW_ROOT_CLOSED", "O alvo fica num componente fechado (shadow root fechado)."),
            Map.entry("SHADOW_HOST_NOT_FOUND", "O componente que contém o alvo não foi encontrado."),
            Map.entry("TARGET_NOT_MATERIALIZED", "O elemento ainda não existe na página (aparece depois)."),
            Map.entry("TARGET_AMBIGUOUS", "Os candidatos não se distinguem entre si."),
            Map.entry("HIDDEN", "O elemento está oculto."),
            Map.entry("OUT_OF_VIEWPORT", "O elemento está fora da área visível (role a página para vê-lo)."),
            Map.entry("DISABLED", "O elemento está desabilitado."),
            Map.entry("READ_ONLY", "O campo é somente leitura."),
            Map.entry("INACCESSIBLE", "O elemento está bloqueado (ex.: atrás de um diálogo)."),
            Map.entry("NOT_EDITABLE", "O elemento não aceita digitação."),
            Map.entry("KIND_UNUSUAL_FOR_ACTION", "O tipo do elemento não combina com a ação da etapa."),
            Map.entry("PAGE_CHANGED_DURING_CHECK", "A página mudou durante a verificação: o resultado não vale para a página atual."),
            Map.entry("CAPTURE_UNAVAILABLE", "A extensão não conseguiu observar a página."),
            Map.entry("HIGHLIGHT_FAILED", "O destaque não pôde ser desenhado (o elemento saiu da página)."));

    public static String reason(String code) {
        return REASONS.getOrDefault(code, "Motivo não descrito (" + code + ").");
    }

    /** Motivos que descrevem LIMITAÇÕES (o resto é explicação do resultado). */
    public static boolean limitation(String code) {
        return List.of("DESCRIPTOR_ONLY", "TARGET_UNKNOWN_IN_REGISTRY", "DESCRIPTOR_CANNOT_CROSS_SCOPE", "SHADOW_ROOT_CLOSED", "FRAME_PERMISSION_DENIED",
                "FRAME_SANDBOX_RESTRICTED", "CAPTURE_UNAVAILABLE", "HIGHLIGHT_FAILED", "PAGE_CHANGED_DURING_CHECK").contains(code);
    }

    public static final List<String> CHECK_ORDER = List.of("identity", "unique", "visible", "enabled", "editable", "kindMatchesAction", "frameReachable",
            "shadowReachable");

    public static String checkLabel(String check) {
        return switch (check) {
            case "identity" -> "Identidade comprovada";
            case "unique" -> "Único na página";
            case "visible" -> "Visível";
            case "enabled" -> "Habilitado";
            case "editable" -> "Aceita digitação";
            case "kindMatchesAction" -> "Tipo combina com a ação";
            case "frameReachable" -> "Quadro acessível";
            case "shadowReachable" -> "Componente acessível";
            default -> check;
        };
    }

    public static String checkValue(String value) {
        return switch (value == null ? "" : value) {
            case "PASS" -> "Sim";
            case "FAIL" -> "Não";
            case "NOT_APPLICABLE" -> "Não se aplica";
            default -> "Não verificado";
        };
    }

    /** Motivos de revisão da regra de reuso (G5). */
    public static String reuse(String code) {
        return switch (code) {
            case "DECISION_REJECTED" -> "O alvo desta etapa foi rejeitado antes.";
            case "IDENTITY_NOT_RESOLVED" -> "A identidade não foi comprovada agora.";
            case "IDENTITY_SOURCE_WEAKER" -> "Confirmado pela identidade registrada; agora só pelo descritor.";
            case "ACTION_CHANGED" -> "A ação da etapa mudou desde a confirmação.";
            case "VALUE_SOURCE_CHANGED" -> "A origem do valor da etapa mudou desde a confirmação.";
            case "TARGET_IDENTITY_CHANGED" -> "O alvo da etapa foi trocado desde a confirmação.";
            case "TARGET_KIND_CHANGED" -> "O tipo do alvo mudou desde a confirmação.";
            case "PREFERRED_LOCATOR_CHANGED" -> "O localizador preferencial mudou desde a confirmação.";
            case "ACCESS_PATH_CHANGED" -> "O caminho até o elemento (quadros/componentes) mudou.";
            case "ATTRIBUTES_CHANGED" -> "Atributos que identificavam o elemento mudaram na página.";
            case "STATE_NOT_SUITABLE" -> "O elemento não está apto agora (oculto, desabilitado ou não único).";
            case "TEST_AGENT_DECISION_NOT_TRANSFERABLE" -> "A confirmação anterior foi feita por um agente de teste: precisa da sua revisão.";
            case "PAGE_CHANGED" -> "A página mudou durante a verificação.";
            default -> "Motivo não descrito (" + code + ").";
        };
    }

    /** Sempre presentes: o que este debug NÃO comprova. */
    public static final List<String> ALWAYS_LIMITS = List.of(
            "Verificação situada: vale para esta página, agora.",
            "Nada foi clicado, preenchido ou enviado; o resultado de negócio não é verificado aqui.");
}
