package com.nekkora.studio.ai.provider;

/**
 * STUDIO-014 — escolha do provedor de IA padrão (num só lugar, fora do aplicativo): a Anthropic, que só fica disponível
 * com uma chave no ambiente. Um futuro proxy da Nekkora entra aqui, sem mudar quem chama.
 */
public final class AIProviders {
    private AIProviders() {}

    public static AIProvider standard() {
        return new AnthropicProvider();
    }

    /** Explicação para quem pede ajuda sem provedor configurado (sem citar valores). */
    public static String unavailableExplanation() {
        return "Análise por IA: não configurada (nenhuma chave em NEKKORA_ANTHROPIC_API_KEY ou ANTHROPIC_API_KEY no ambiente). Nada é enviado a "
                + "serviços externos; o desenvolvimento e a validação funcionam sem IA.";
    }
}
