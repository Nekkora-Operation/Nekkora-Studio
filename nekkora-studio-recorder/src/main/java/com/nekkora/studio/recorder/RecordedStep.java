package com.nekkora.studio.recorder;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.privacy.CapturePrivacy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Etapa gravada recebida da extensão (evento {@code studio.recording.step} do nekkora-bridge/1), já validada. O Studio
 * não analisa DOM nem recalcula localizadores: só lê a interação normalizada e a referência compacta do elemento.
 *
 * @param recordingId gravação técnica (interna)
 * @param eventId     identidade do evento (deduplicação)
 * @param sequence    sequência explícita da gravação
 * @param routeRef    rota vigente quando a interação foi consolidada (sessão lógica)
 * @param observedAt  instante observado (informativo; a ordem vem da sequência)
 * @param mode        modo de captura
 * @param proposed    proposta (CAPTURE_ONLY: ação não executada) ou interação encaminhada à página
 * @param action      CLICK, FILL, SELECT_OPTION, CHECK, UNCHECK, PRESS_KEY, UNKNOWN ou EXTRACT (dado apontado para leitura)
 * @param element     referência do elemento (mapa do contrato ElementRef)
 * @param optionLabel sempre null (CHROME-SECURITY-001: a opção escolhida é valor do usuário e nunca é aceita)
 * @param optionValue sempre null
 * @param key         tecla (PRESS_KEY), ou null
 * @param url         URL do documento como MODELO (nekkora-privacy/2), ou null
 * @param title       título do documento só se for do vocabulário controlado, ou null
 * @param inFrame     a interação veio de um documento de iframe acompanhado
 * @param navigationAfter navegação observada logo depois (NONE, SAME_DOCUMENT, NEW_DOCUMENT) ou null
 * @param evidence    códigos de evidência
 * @param notes       códigos de limitação
 * @param events      eventos do navegador consolidados
 */
public record RecordedStep(String recordingId, String eventId, long sequence, String routeRef, Instant observedAt, RecordingMode mode, boolean proposed,
                           String action, Map<String, Object> element, String optionLabel, String optionValue, String key, String url, String title,
                           boolean inFrame, String navigationAfter, List<String> evidence, List<String> notes, long events) {

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._:-]{1,80}");
    static final Set<String> ACTIONS = Set.of("CLICK", "FILL", "SELECT_OPTION", "CHECK", "UNCHECK", "PRESS_KEY", "UNKNOWN", "EXTRACT");

    public RecordedStep {
        evidence = List.copyOf(evidence);
        notes = List.copyOf(notes);
        element = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(element)); // campos nulos do JSON são legítimos
    }

    /**
     * Lê e valida o payload do evento. Vazio quando algo essencial falta ou está fora do contrato (o evento não vira
     * etapa — e não é inventado nada para completar).
     */
    public static Optional<RecordedStep> parse(Map<String, Object> p) {
        if (p == null) {
            return Optional.empty();
        }
        String recordingId = Json.string(p, "recordingId");
        String eventId = Json.string(p, "eventId");
        String routeRef = Json.string(p, "routeRef");
        long sequence = Json.number(p, "sequence", -1);
        String action = Json.string(p, "action");
        RecordingMode mode = RecordingMode.ofWire(Json.string(p, "mode"));
        Map<String, Object> element = Json.object(p, "element");
        if (!id(recordingId) || !id(eventId) || !id(routeRef) || sequence < 1 || !ACTIONS.contains(action) || mode == null || element == null) {
            return Optional.empty();
        }
        String observation = Json.string(p, "observation");
        if (!"PROPOSED".equals(observation) && !"INTERACTION_OBSERVED".equals(observation)) {
            return Optional.empty();
        }
        Map<String, Object> params = Json.object(p, "parameters");
        if (params == null || Json.bool(params, "valueCaptured", true)) {
            return Optional.empty(); // valor digitado nunca é aceito
        }
        Map<String, Object> option = Json.object(params, "option");
        Map<String, Object> ctx = Json.object(p, "context");
        // CHROME-SECURITY-001 — 4ª barreira: o Studio reaplica nekkora-privacy/2 a tudo, mesmo vindo de extensão antiga
        List<String> notes = new ArrayList<>(strings(Json.array(p, "notes")));
        Map<String, Object> safeElement = guard(element, notes);
        if (option != null && !notes.contains("OPTION_WITHHELD")) {
            notes.add("OPTION_WITHHELD"); // a opção escolhida não é aceita, nem de uma extensão anterior à política
        }
        Map<String, Object> after = ctx == null ? null : Json.object(ctx, "after");
        Instant at;
        try {
            at = Instant.parse(String.valueOf(Json.string(p, "observedAt")));
        } catch (RuntimeException e) {
            at = null;
        }
        return Optional.of(new RecordedStep(recordingId, eventId, sequence, routeRef, at, mode, "PROPOSED".equals(observation), action, safeElement,
                null, null, Json.string(params, "key"),
                ctx == null ? null : CapturePrivacy.urlTemplate(Json.string(ctx, "url")), ctx == null ? null : CapturePrivacy.title(Json.string(ctx, "title")),
                ctx != null && "FRAME".equals(Json.string(ctx, "frame")), after == null ? null : Json.string(after, "navigation"),
                strings(Json.array(p, "evidence")), notes, Math.max(1, Json.number(p, "events", 1))));
    }

    /**
     * Referência do elemento pela política: nome, rótulos e nomes de contexto só do vocabulário (senão ""); id/name só
     * identificadores do vocabulário; localizador com qualquer literal fora da política é descartado (LOCATORS_WITHHELD) —
     * o Studio não reescreve nem "conserta" localizadores.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> guard(Map<String, Object> element, List<String> notes) {
        Map<String, Object> e = new java.util.LinkedHashMap<>(element);
        e.put("name", nameOrEmpty(e.get("name")));
        // P1-01 (revisão dos PRs #7): papel ARIA só pela regra fechada do atributo role
        String role = e.get("role") instanceof String s ? CapturePrivacy.attribute("role", s, null, null) : null;
        e.put("role", role == null || role.isEmpty() ? null : role);
        // 2ª revisão dos PRs #7: nome de elemento fora do catálogo HTML/SVG/MathML e do vocabulário => marcador neutro
        if (e.get("tag") instanceof String t && !"unknown".equals(t)) {
            String safe = CapturePrivacy.tagName(t);
            e.put("tag", safe == null ? "unknown" : safe);
        } else if (e.containsKey("tag") && !(e.get("tag") instanceof String)) {
            e.put("tag", "unknown");
        }
        boolean sensitive = Boolean.TRUE.equals(e.get("sensitive"));
        for (String k : new String[]{"htmlId", "htmlName"}) {
            Object v = e.get(k);
            e.put(k, sensitive || !(v instanceof String s) ? null : CapturePrivacy.developerIdentifier(s)); // STUDIO-012: lista técnica
        }
        if (e.get("locators") instanceof List<?> ls) {
            List<Object> kept = new ArrayList<>();
            int preferred = e.get("preferredLocator") instanceof Number n ? n.intValue() : -1;
            int newPreferred = -1;
            for (int i = 0; i < ls.size(); i++) {
                if (ls.get(i) instanceof Map<?, ?> l && safe(l.get("css")) && safe(l.get("xpath")) && safe(l.get("description"))) {
                    if (i == preferred) {
                        newPreferred = kept.size();
                    }
                    kept.add(l);
                }
            }
            if (kept.size() < ls.size()) {
                if (!notes.contains("LOCATORS_WITHHELD")) {
                    notes.add("LOCATORS_WITHHELD");
                }
                e.put("locators", kept);
                e.put("preferredLocator", (long) newPreferred);
            }
        }
        if (e.get("accessPath") instanceof List<?> ap) {
            List<Object> out = new ArrayList<>();
            for (Object o : ap) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> s = new java.util.LinkedHashMap<>((Map<String, Object>) m);
                    s.put("label", nameOrEmpty(s.get("label")));
                    if (!safe(s.get("locator"))) {
                        s.put("locator", null);
                    }
                    out.add(s);
                }
            }
            e.put("accessPath", out);
        }
        if (e.get("context") instanceof List<?> cs) {
            List<Object> out = new ArrayList<>();
            for (Object o : cs) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> s = new java.util.LinkedHashMap<>((Map<String, Object>) m);
                    s.put("name", nameOrEmpty(s.get("name")));
                    out.add(s);
                }
            }
            e.put("context", out);
        }
        return e;
    }

    private static String nameOrEmpty(Object v) {
        String t = v instanceof String s ? CapturePrivacy.interfaceText(s) : null;
        return t == null ? "" : t;
    }

    private static boolean safe(Object expr) {
        // null do JSON chega como Json.NULL: ausência de expressão, não expressão insegura
        return expr == null || expr == Json.NULL || expr instanceof String s && CapturePrivacy.expressionSafe(s);
    }

    private static boolean id(String s) {
        return s != null && ID.matcher(s).matches();
    }

    private static List<String> strings(List<Object> l) {
        List<String> out = new ArrayList<>();
        for (Object o : l) {
            if (o instanceof String s && s.matches("[A-Z_]{1,40}")) {
                out.add(s);
            }
        }
        return out;
    }
}
