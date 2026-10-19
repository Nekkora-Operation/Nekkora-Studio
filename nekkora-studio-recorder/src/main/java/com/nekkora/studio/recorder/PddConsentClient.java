package com.nekkora.studio.recorder;

import com.nekkora.studio.bridge.BrowserLink;
import com.nekkora.studio.bridge.json.Json;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * NEKKORA-STUDIO-013 — consentimento de captura do PDD por origem exata ({@code nekkora-pdd-consent/1}), independente do
 * acesso do Chrome (que autoriza inspeção e gravação). Quem concede no Studio é a pessoa, por um clique na janela do PDD; o
 * registro diz quem decidiu ({@code DEVELOPER} ou {@code TEST_AGENT}, este só em modo de laboratório explícito). A
 * extensão confere de novo a origem da aba (a mesma mostrada ao usuário) e só aceita site já permitido pelo Chrome.
 * Consentimentos ficam na extensão (sessão do navegador ou site lembrado), nunca no projeto {@code .nekkora}.
 */
public final class PddConsentClient {
    public static final Set<String> SCOPES = Set.of("SESSION", "REMEMBERED");
    public static final Set<String> ACTORS = Set.of("DEVELOPER", "TEST_AGENT");
    private static final Pattern ORIGIN = Pattern.compile("https?://[A-Za-z0-9.\\[\\]:-]{1,280}");

    /** Situação da aba: acesso do Chrome, origem (sem caminho), consentimento e perfil de captura resultante. */
    public record Status(String tabRef, String origin, TabAccess access, String consent, String actor, String via, String visualCapture, String reason) {}

    /** Consentimento registrado. */
    public record Consent(String origin, String scope, String actor, String via, String grantedAt, boolean chromeAccess) {}

    private final BrowserLink link;
    private final Duration timeout;

    public PddConsentClient(BrowserLink link, Duration timeout) {
        this.link = Objects.requireNonNull(link);
        this.timeout = Objects.requireNonNull(timeout);
    }

    /** Origem aceitável para mostrar e mandar de volta (esquema://host[:porta]). */
    public static boolean validOrigin(String origin) {
        return origin != null && ORIGIN.matcher(origin).matches();
    }

    public CompletableFuture<Status> status(String tabRef) {
        return link.send("studio.pdd.consent.status", Map.of("tabRef", tabRef), timeout).thenApply(p -> {
            String origin = Json.string(p, "origin");
            return new Status(Json.string(p, "tabRef"), validOrigin(origin) ? origin : null, TabAccess.of(Json.string(p, "access")), Json.string(p, "consent"),
                    Json.string(p, "actor"), Json.string(p, "via"), Json.string(p, "visualCapture"), Json.string(p, "reason"));
        });
    }

    /** Concede para a origem que o usuário viu; a extensão recusa se a aba estiver em outra origem. */
    public CompletableFuture<Consent> grant(String tabRef, String origin, String scope, String actor) {
        if (!validOrigin(origin) || !SCOPES.contains(scope) || !ACTORS.contains(actor)) throw new IllegalArgumentException("consentimento inválido");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("tabRef", tabRef);
        p.put("origin", origin);
        p.put("scope", scope);
        p.put("actor", actor);
        return link.send("studio.pdd.consent.grant", p, timeout).thenApply(r -> new Consent(Json.string(r, "origin"), Json.string(r, "consent"),
                Json.string(r, "actor"), Json.string(r, "via"), Json.string(r, "grantedAt"), true));
    }

    public CompletableFuture<Boolean> revoke(String origin) {
        if (!validOrigin(origin)) throw new IllegalArgumentException("origem inválida");
        return link.send("studio.pdd.consent.revoke", Map.of("origin", origin), timeout).thenApply(r -> Json.bool(r, "revoked", false));
    }

    public CompletableFuture<List<Consent>> list() {
        return link.send("studio.pdd.consent.list", Map.of(), timeout).thenApply(r -> {
            List<Consent> out = new ArrayList<>();
            for (Object o : Json.array(r, "consents")) {
                if (o instanceof Map<?, ?> raw) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> c = (Map<String, Object>) raw;
                    String origin = Json.string(c, "origin");
                    if (validOrigin(origin)) {
                        out.add(new Consent(origin, Json.string(c, "consent"), Json.string(c, "actor"), Json.string(c, "via"), Json.string(c, "grantedAt"),
                                Json.bool(c, "chromeAccess", false)));
                    }
                }
            }
            return out;
        });
    }
}
