package com.nekkora.studio.app.evidence;

import com.nekkora.studio.bridge.json.Json;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cliente mínimo do Chrome DevTools Protocol para a homologação (somente o necessário: abrir abas, avaliar script
 * numa página da própria extensão, listar alvos). WebSocket do JDK, sem cabeçalho Origin.
 */
final class Cdp implements AutoCloseable {

    private final HttpClient http = HttpClient.newHttpClient();
    private final int port;

    Cdp(int port) {
        this.port = port;
    }

    /** Alvo do DevTools (página, service worker...). */
    record Target(String id, String type, String url, String ws) {
    }

    List<Target> targets() throws Exception {
        String body = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/json/list")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        List<Target> out = new java.util.ArrayList<>();
        for (Object o : (List<?>) Json.parse(body)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            out.add(new Target(Json.string(m, "id"), Json.string(m, "type"), Json.string(m, "url"), Json.string(m, "webSocketDebuggerUrl")));
        }
        return out;
    }

    Target open(String url) throws Exception {
        String body = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/json/new?" + url))
                .PUT(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).body();
        Map<String, Object> m = Json.parseObject(body);
        return new Target(Json.string(m, "id"), Json.string(m, "type"), Json.string(m, "url"), Json.string(m, "webSocketDebuggerUrl"));
    }

    void close(Target t) throws Exception {
        http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/json/close/" + t.id())).build(), HttpResponse.BodyHandlers.ofString());
    }

    void activate(Target t) throws Exception {
        http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/json/activate/" + t.id())).build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Sessão numa página: avalia expressões (com await de promessas) e devolve o valor JSON. */
    Page page(Target t) throws Exception {
        return new Page(t);
    }

    final class Page implements AutoCloseable {
        private final WebSocket ws;
        private final AtomicInteger ids = new AtomicInteger();
        private final Map<Integer, CompletableFuture<Map<String, Object>>> pending = new ConcurrentHashMap<>();
        private final StringBuilder partial = new StringBuilder();

        Page(Target t) throws Exception {
            ws = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10)).buildAsync(URI.create(t.ws()), new WebSocket.Listener() {
                @Override
                public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
                    partial.append(data);
                    if (last) {
                        Map<String, Object> msg = Json.parseObject(partial.toString());
                        partial.setLength(0);
                        Object id = msg.get("id");
                        if (id instanceof Long l) {
                            CompletableFuture<Map<String, Object>> f = pending.remove(l.intValue());
                            if (f != null) f.complete(msg);
                        }
                    }
                    w.request(1);
                    return null;
                }
            }).get(10, TimeUnit.SECONDS);
        }

        Map<String, Object> send(String method, Map<String, Object> params) throws Exception {
            int id = ids.incrementAndGet();
            CompletableFuture<Map<String, Object>> f = new CompletableFuture<>();
            pending.put(id, f);
            ws.sendText(Json.write(Json.obj().put("id", id).put("method", method).put("params", params).build()), true).get(10, TimeUnit.SECONDS);
            return f.get(120, TimeUnit.SECONDS);
        }

        /** Avalia e devolve {@code result.value} (objetos são serializados por valor). */
        Object eval(String expression) throws Exception {
            Map<String, Object> r = send("Runtime.evaluate", Json.obj().put("expression", expression).put("awaitPromise", true)
                    .put("returnByValue", true).build());
            Map<String, Object> result = Json.object(r, "result");
            if (Json.object(result, "exceptionDetails") != null) {
                throw new IllegalStateException("script falhou: " + Json.object(result, "exceptionDetails"));
            }
            Object v = Json.object(result, "result") == null ? null : Json.object(result, "result").get("value");
            return v == Json.NULL ? null : v;
        }

        @Override
        public void close() {
            ws.abort();
        }
    }

    @Override
    public void close() {
        http.shutdownNow(); // close() esperaria os WebSockets ainda abertos
    }
}
