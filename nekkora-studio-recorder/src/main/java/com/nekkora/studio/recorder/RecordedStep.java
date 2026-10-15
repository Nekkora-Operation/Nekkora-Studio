package com.nekkora.studio.recorder;

import com.nekkora.studio.bridge.json.Json;
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
 * @param action      CLICK, FILL, SELECT_OPTION, CHECK, UNCHECK, PRESS_KEY ou UNKNOWN
 * @param element     referência do elemento (mapa do contrato ElementRef)
 * @param optionLabel opção escolhida (SELECT_OPTION), ou null
 * @param optionValue valor técnico da opção, ou null
 * @param key         tecla (PRESS_KEY), ou null
 * @param url         URL sanitizada do documento, ou null
 * @param title       título do documento, ou null
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
    static final Set<String> ACTIONS = Set.of("CLICK", "FILL", "SELECT_OPTION", "CHECK", "UNCHECK", "PRESS_KEY", "UNKNOWN");

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
        Map<String, Object> after = ctx == null ? null : Json.object(ctx, "after");
        Instant at;
        try {
            at = Instant.parse(String.valueOf(Json.string(p, "observedAt")));
        } catch (RuntimeException e) {
            at = null;
        }
        return Optional.of(new RecordedStep(recordingId, eventId, sequence, routeRef, at, mode, "PROPOSED".equals(observation), action, element,
                option == null ? null : Json.string(option, "label"), option == null ? null : Json.string(option, "value"), Json.string(params, "key"),
                ctx == null ? null : Json.string(ctx, "url"), ctx == null ? null : Json.string(ctx, "title"),
                ctx != null && "FRAME".equals(Json.string(ctx, "frame")), after == null ? null : Json.string(after, "navigation"),
                strings(Json.array(p, "evidence")), strings(Json.array(p, "notes")), Math.max(1, Json.number(p, "events", 1))));
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
