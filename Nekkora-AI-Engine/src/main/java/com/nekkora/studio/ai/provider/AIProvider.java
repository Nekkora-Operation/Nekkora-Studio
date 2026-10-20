package com.nekkora.studio.ai.provider;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * STUDIO-014 — provedor de IA SUBSTITUÍVEL. O Studio não depende dele para validar nem para gerar: a IA só PROPÕE, numa
 * resposta estruturada por ferramenta com schema, e o {@code PlanVerifier} decide o que vale. Implementações: a da
 * Anthropic (opcional, chave por variável de ambiente) e, nos testes, um mock ROTULADO como mock. Um proxy da Nekkora
 * (tenants/infraestrutura) entra como outra implementação, sem mudar quem chama.
 */
public interface AIProvider {

    /** Identificação para relatórios (ex.: {@code anthropic:claude-sonnet-5-5}, {@code mock:testes}). Nunca a chave. */
    String id();

    /** Há credencial e configuração para chamar agora? (sem rede) */
    boolean available();

    /** Uma chamada. Lança {@link AIException} com código estável; nunca devolve texto livre fora da ferramenta. */
    Response call(Request request, BooleanSupplier cancelled) throws AIException;

    /**
     * Pedido: instruções de sistema (o Prompt-Mestre + regras do contrato), a carga do usuário (JSON minimizado), a
     * ferramenta obrigatória e seu schema, e os limites.
     */
    record Request(String system, String payload, String toolName, String toolDescription, String toolSchema, int maxOutputTokens, Duration timeout,
                   int maxInputTokens) {
        public Request {
            Objects.requireNonNull(system);
            Objects.requireNonNull(payload);
            Objects.requireNonNull(toolName);
            Objects.requireNonNull(toolSchema);
            Objects.requireNonNull(timeout);
        }

        /** Estimativa conservadora de tokens de entrada (antes de enviar; para o limite de custo). */
        public int estimatedInputTokens() {
            return (system.length() + payload.length() + toolSchema.length()) / 3 + 64;
        }
    }

    /** Resposta: só a entrada da ferramenta (JSON) e o consumo declarado pelo provedor. */
    record Response(String toolInput, int inputTokens, int outputTokens, String model) {}

    /** Falha com código estável (sem conteúdo do pedido, da resposta ou da chave). */
    final class AIException extends Exception {
        public enum Code { NO_KEY, NO_CONSENT, BUDGET_EXCEEDED, TIMEOUT, RATE_LIMITED, NETWORK, HTTP_ERROR, INVALID_RESPONSE, PARTIAL_RESPONSE, CANCELLED, OVERLOADED }

        private final Code code;
        private final int status;
        private final String detail;

        public AIException(Code code, int status) {
            this(code, status, "");
        }

        /** {@code detail}: tipo e mensagem de erro devolvidos pelo PROVEDOR (curtos; nunca o pedido nem a chave). */
        public AIException(Code code, int status, String detail) {
            super(code.name() + (status > 0 ? " " + status : ""));
            this.code = code;
            this.status = status;
            this.detail = detail == null ? "" : detail;
        }

        public String detail() {
            return detail;
        }

        public AIException(Code code) {
            this(code, 0);
        }

        public Code code() {
            return code;
        }

        public int status() {
            return status;
        }
    }
}
