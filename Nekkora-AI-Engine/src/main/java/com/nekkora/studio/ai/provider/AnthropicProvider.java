package com.nekkora.studio.ai.provider;

import com.nekkora.studio.bridge.json.Json;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * STUDIO-014 — provedor OPCIONAL da Anthropic (Messages API, uso de ferramenta FORÇADO com {@code input_schema}).
 *
 * <ul>
 *   <li>Chave: SÓ por variável de ambiente ({@code NEKKORA_ANTHROPIC_API_KEY} ou {@code ANTHROPIC_API_KEY}). Nunca no
 *       {@code .nekkora}, no PDD, no repositório, em arquivo de configuração, em log ou em mensagem de erro. Nenhuma
 *       chave da Nekkora vai no aplicativo.</li>
 *   <li>Limites: estimativa de entrada recusada ANTES de enviar ({@code BUDGET_EXCEEDED}), {@code max_tokens} de saída,
 *       prazo total, cancelamento cooperativo (a requisição é abandonada).</li>
 *   <li>Falhas viram códigos: 429 → {@code RATE_LIMITED}, 529 → {@code OVERLOADED}, rede → {@code NETWORK}, resposta sem a
 *       ferramenta → {@code INVALID_RESPONSE}, corte por limite → {@code PARTIAL_RESPONSE}. Nada é repetido sozinho.</li>
 * </ul>
 */
public final class AnthropicProvider implements AIProvider {

    public static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    public static final String API_VERSION = "2023-06-01";
    public static final String DEFAULT_MODEL = "claude-sonnet-5-5";

    private final Function<String, String> env;
    private final String model;
    private final HttpClient http;
    private final URI endpoint;

    public AnthropicProvider() {
        this(System::getenv, DEFAULT_MODEL, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(), URI.create(ENDPOINT));
    }

    /** Para testes de contrato: ambiente, cliente HTTP e endereço substituíveis (um servidor LOCAL simula o provedor). */
    public AnthropicProvider(Function<String, String> env, String model, HttpClient http, URI endpoint) {
        this.env = env;
        this.model = model;
        this.http = http;
        this.endpoint = endpoint;
    }

    @Override
    public String id() {
        return "anthropic:" + model;
    }

    @Override
    public boolean available() {
        return key().isPresent();
    }

    private Optional<String> key() {
        for (String name : List.of("NEKKORA_ANTHROPIC_API_KEY", "ANTHROPIC_API_KEY")) {
            String v = env.apply(name);
            if (v != null && !v.isBlank()) return Optional.of(v.strip());
        }
        return Optional.empty();
    }

    /** Corpo da requisição (sem a chave: ela só vai no cabeçalho). */
    String body(Request r) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", r.toolName());
        tool.put("description", r.toolDescription());
        tool.put("input_schema", Json.parseObject(r.toolSchema()));
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("type", "tool");
        choice.put("name", r.toolName());
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", r.payload());
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("model", model);
        b.put("max_tokens", r.maxOutputTokens());
        b.put("system", r.system());
        b.put("messages", List.of(msg));
        b.put("tools", List.of(tool));
        b.put("tool_choice", choice);
        return Json.write(b);
    }

    @Override
    public Response call(Request r, BooleanSupplier cancelled) throws AIException {
        String k = key().orElseThrow(() -> new AIException(AIException.Code.NO_KEY));
        if (r.estimatedInputTokens() > r.maxInputTokens()) throw new AIException(AIException.Code.BUDGET_EXCEEDED);
        HttpRequest req = HttpRequest.newBuilder(endpoint).timeout(r.timeout()).header("content-type", "application/json")
                .header("x-api-key", k).header("anthropic-version", API_VERSION).POST(HttpRequest.BodyPublishers.ofString(body(r), StandardCharsets.UTF_8)).build();
        CompletableFuture<HttpResponse<String>> f = http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        long deadline = System.nanoTime() + r.timeout().toNanos();
        HttpResponse<String> resp;
        try {
            while (true) {
                if (cancelled.getAsBoolean()) {
                    f.cancel(true);
                    throw new AIException(AIException.Code.CANCELLED);
                }
                try {
                    resp = f.get(100, TimeUnit.MILLISECONDS);
                    break;
                } catch (TimeoutException te) {
                    if (System.nanoTime() > deadline) {
                        f.cancel(true);
                        throw new AIException(AIException.Code.TIMEOUT);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            f.cancel(true);
            throw new AIException(AIException.Code.CANCELLED);
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof HttpTimeoutException) throw new AIException(AIException.Code.TIMEOUT);
            if (c instanceof ConnectException || c instanceof IOException) throw new AIException(AIException.Code.NETWORK);
            throw new AIException(AIException.Code.NETWORK);
        }
        return parse(resp.statusCode(), resp.body(), r.toolName());
    }

    /** Interpreta a resposta HTTP (separado para os testes de contrato). */
    @SuppressWarnings("unchecked")
    static Response parse(int status, String body, String toolName) throws AIException {
        if (status == 429) throw new AIException(AIException.Code.RATE_LIMITED, status);
        if (status == 529) throw new AIException(AIException.Code.OVERLOADED, status);
        if (status != 200) throw new AIException(AIException.Code.HTTP_ERROR, status);
        Map<String, Object> m;
        try {
            m = Json.parseObject(body);
        } catch (RuntimeException e) {
            throw new AIException(AIException.Code.INVALID_RESPONSE);
        }
        if ("max_tokens".equals(m.get("stop_reason"))) throw new AIException(AIException.Code.PARTIAL_RESPONSE);
        if (!(m.get("content") instanceof List<?> content)) throw new AIException(AIException.Code.INVALID_RESPONSE);
        for (Object o : content) {
            if (o instanceof Map<?, ?> c && "tool_use".equals(c.get("type")) && toolName.equals(c.get("name")) && c.get("input") instanceof Map<?, ?> in) {
                int inTok = 0;
                int outTok = 0;
                if (m.get("usage") instanceof Map<?, ?> u) {
                    inTok = u.get("input_tokens") instanceof Number n ? n.intValue() : 0;
                    outTok = u.get("output_tokens") instanceof Number n ? n.intValue() : 0;
                }
                return new Response(Json.write((Map<String, Object>) in), inTok, outTok, m.get("model") instanceof String s ? s : "");
            }
        }
        throw new AIException(AIException.Code.INVALID_RESPONSE);
    }
}
