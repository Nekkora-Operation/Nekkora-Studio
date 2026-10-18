package com.nekkora.studio.recorder;

import com.nekkora.studio.bridge.BridgeEvent;
import com.nekkora.studio.bridge.BrowserLink;
import com.nekkora.studio.bridge.LinkStatus;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * STUDIO-010 — cliente da captura visual SANITIZADA ({@code pdd-capture/1}, política {@code nekkora-visual/1}).
 *
 * <p>Uma instância = uma captura: {@link #arm} pede à extensão que espere o GESTO do usuário no Chrome (o atalho) numa
 * aba de laboratório; {@link #result} completa com o evento {@code studio.pdd.captured}: PNG já sanitizado (reverificado
 * no host), regiões só por tipo/retângulo e marcadores normalizados por etapa — ou só o código da falha. O que vai à
 * extensão é o {@code targetId} e o descritor recuperável já persistido de cada etapa; nunca valores. Quem recebe a
 * imagem ainda aplica a 4ª barreira (verificação de pixels no Studio) antes de guardá-la.
 *
 * <p>STUDIO-012 PDD-01: o Studio pode pedir {@code nekkora-visual/2} (representação documental) ao armar — só para uma
 * extensão que a declare ({@link #supportsV2}); sem pedido, o contrato é exatamente o da v1.
 */
public final class PddCaptureClient implements BrowserLink.Listener, AutoCloseable {

    /** Etapa do bloco: identidade lógica para o marcador (sem alvo → marcador omitido). */
    public record StepTarget(StepId step, ActionType action, ElementReference element) {
        public StepTarget {
            Objects.requireNonNull(step);
            Objects.requireNonNull(action);
        }
    }

    /** Captura armada: a extensão espera o atalho ({@code shortcut} vazio = o usuário precisa definir um no Chrome). */
    public record Armed(String captureId, String shortcut, String policy) {}

    /** Região da imagem sanitizada (px do dispositivo); {@code tone} só em SURFACE da v2. */
    public record Region(String kind, int x, int y, int width, int height, String tone) {
        public Region(String kind, int x, int y, int width, int height) {
            this(kind, x, y, width, height, null);
        }
    }

    /** Política visual pedida ao armar. */
    public static final String POLICY_V1 = "nekkora-visual/1";
    public static final String POLICY_V2 = "nekkora-visual/2";

    /** A extensão conectada entende a v2? (0.5.0 em diante; versão desconhecida = não). */
    public static boolean supportsV2(String extensionVersion) {
        if (extensionVersion == null || !extensionVersion.matches("\\d+\\.\\d+\\.\\d+.*")) return false;
        String[] v = extensionVersion.split("[.-]");
        int major = Integer.parseInt(v[0]);
        int minor = Integer.parseInt(v[1]);
        return major > 0 || minor >= 5;
    }

    /** Marcador de uma etapa: retângulo normalizado e situação ({@code PLACED}/{@code OMITTED} com motivo em código). */
    public record Marker(StepId step, String status, double x, double y, double width, double height, String reason) {}

    /** Resultado: imagem sanitizada ou código da falha. */
    public record Result(String captureId, boolean captured, String failureCode, byte[] png, String sha256, int width, int height,
                         double devicePixelRatio, Instant observedAt, List<Region> regions, List<Marker> markers, List<String> limitations, String policy) {
        public Result {
            regions = regions == null ? List.of() : List.copyOf(regions);
            markers = markers == null ? List.of() : List.copyOf(markers);
            limitations = limitations == null ? List.of() : List.copyOf(limitations);
        }

        static Result failed(String captureId, String code) {
            return new Result(captureId, false, code, null, null, 0, 0, 0, null, List.of(), List.of(), List.of(), null);
        }
    }

    private final BrowserLink link;
    private final Duration timeout;
    private final String captureId = "cap-" + UUID.randomUUID();
    private final CompletableFuture<Result> result = new CompletableFuture<>();
    private volatile boolean armed;

    public PddCaptureClient(BrowserLink link, Duration timeout) {
        this.link = Objects.requireNonNull(link);
        this.timeout = Objects.requireNonNull(timeout);
        link.addListener(this);
    }

    public String captureId() {
        return captureId;
    }

    /** Arma a captura para as etapas do bloco, na aba indicada (referência opaca da listagem de abas), na política v1. */
    public CompletableFuture<Armed> arm(String tabRef, List<StepTarget> steps, Duration lease) {
        return arm(tabRef, steps, lease, null);
    }

    /** Arma pedindo a política ({@code null} = sem pedido: v1, compatível com extensões anteriores). */
    public CompletableFuture<Armed> arm(String tabRef, List<StepTarget> steps, Duration lease, String policy) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("captureId", captureId);
        p.put("tabRef", tabRef);
        p.put("leaseMs", (int) lease.toMillis());
        if (policy != null) p.put("policy", policy);
        List<Object> list = new ArrayList<>();
        for (StepTarget s : steps) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stepRef", s.step().value().toString());
            m.put("targetId", s.element() == null ? null : s.element().correlation().get(CorrelationKeys.Key.TARGET_ID).orElse(null));
            m.put("descriptor", s.element() == null ? null : GuidedDebugClient.descriptor(s.element()));
            m.put("actionType", s.action().name());
            list.add(m);
        }
        p.put("steps", list);
        return link.send("studio.pdd.capture.arm", p, timeout).thenApply(r -> {
            armed = true;
            String sc = Json.string(r, "shortcut");
            String armedPolicy = Json.string(r, "policy");
            return new Armed(captureId, sc == null ? "" : sc, armedPolicy == null ? POLICY_V1 : armedPolicy);
        });
    }

    /** Resultado (completa uma vez: imagem sanitizada ou falha). */
    public CompletableFuture<Result> result() {
        return result;
    }

    /** Cancela a captura armada (o atalho deixa de valer). */
    public CompletableFuture<Void> cancel() {
        return link.send("studio.pdd.capture.cancel", Map.of("captureId", captureId), timeout).handle((r, e) -> null);
    }

    @Override
    public void statusChanged(LinkStatus status) {
        if (!status.connected() && armed && !result.isDone()) result.complete(Result.failed(captureId, "LINK_LOST"));
    }

    @Override
    public void event(BridgeEvent event) {
        if (!"studio.pdd.captured".equals(event.type())) return;
        Map<String, Object> p = event.payload();
        if (!captureId.equals(Json.string(p, "captureId"))) return;
        if (!"CAPTURED".equals(Json.string(p, "status"))) {
            String code = Json.string(p, "code");
            result.complete(Result.failed(captureId, code == null ? "INTERNAL" : code));
            return;
        }
        try {
            List<Region> regions = new ArrayList<>();
            for (Object o : Json.array(p, "regions")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> r = (Map<String, Object>) o;
                regions.add(new Region(Json.string(r, "kind"), (int) Json.number(r, "x", -1), (int) Json.number(r, "y", -1), (int) Json.number(r, "width", 0),
                        (int) Json.number(r, "height", 0), Json.string(r, "tone")));
            }
            List<Marker> markers = new ArrayList<>();
            for (Object o : Json.array(p, "annotations")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> a = (Map<String, Object>) o;
                markers.add(new Marker(new StepId(UUID.fromString(Json.string(a, "stepRef"))), Json.string(a, "status"), dbl(a, "x"), dbl(a, "y"),
                        dbl(a, "width"), dbl(a, "height"), Json.string(a, "reason")));
            }
            List<String> limits = new ArrayList<>();
            for (Object o : Json.array(p, "limitations")) limits.add(String.valueOf(o));
            String at = Json.string(p, "observedAt");
            result.complete(new Result(captureId, true, null, Base64.getDecoder().decode(Json.string(p, "png")), Json.string(p, "sha256"),
                    (int) Json.number(p, "width", 0), (int) Json.number(p, "height", 0), dbl(p, "devicePixelRatio"), at == null ? Instant.now() : Instant.parse(at),
                    regions, markers, limits, Json.string(p, "policy")));
        } catch (RuntimeException e) {
            result.complete(Result.failed(captureId, "SANITIZATION_CHECK_FAILED"));
        }
    }

    private static double dbl(Map<String, Object> m, String k) {
        return m.get(k) instanceof Number n ? n.doubleValue() : 0;
    }

    @Override
    public void close() {
        link.removeListener(this);
        if (!result.isDone()) result.complete(Result.failed(captureId, "CANCELLED"));
    }
}
