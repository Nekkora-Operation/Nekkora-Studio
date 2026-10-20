package com.nekkora.studio.recorder;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.StepContext;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.Texts;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.OptionChoice;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.element.ContextAnchor;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.element.ObservedObstacle;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepProvenance;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Transforma uma etapa gravada em {@link StepDraft} por regras DETERMINÍSTICAS (sem IA): nome da instrução em
 * português a partir da ação, da categoria e do nome acessível; referência do elemento como veio da extensão (o
 * Studio não recalcula localizadores); procedência RECORDED com a observação do navegador.
 *
 * <p>Nenhum valor digitado existe aqui: preencher registra apenas que houve preenchimento (o valor futuro virá de um
 * {@link ValueSource} configurado pelo usuário). Identificadores internos vão só para {@link CorrelationKeys}.
 */
public final class RecordedStepDrafts {

    static final String SOURCE_CONTRACT = "nekkora-bridge/1#studio.recording.step";

    private RecordedStepDrafts() {
    }

    public static StepDraft draft(RecordedStep s) {
        ElementReference element = element(s);
        // NEKKORA-E2E-002: o dado apontado no modo "Selecionar dado" é uma etapa Observar (o robô lê; nada é clicado)
        ActionType type = "EXTRACT".equals(s.action()) ? ActionType.OBSERVE : ActionType.valueOf(s.action());
        StepDraft.Builder b = StepDraft.builder()
                .name(name(s, element))
                .description(description(s))
                .actionType(type)
                .element(element)
                .provenance(StepProvenance.recorded(correlation(recordingKeys(s), s.element()), observation(s)))
                .context(new StepContext("Navegador", safe(s.title()), safe(s.url())));
        if (type == ActionType.SELECT_OPTION && s.optionLabel() != null && !s.optionLabel().isBlank() && DisplaySafety.isSafeToDisplay(s.optionLabel())) {
            String value = s.optionValue() == null || !DisplaySafety.isSafeToDisplay(s.optionValue()) ? "" : s.optionValue();
            b.parameters(ActionParameters.withOption(new OptionChoice(s.optionLabel(), value)));
        } else if (type == ActionType.PRESS_KEY && "Enter".equals(s.key())) {
            b.parameters(ActionParameters.withValue(new ValueSource.LiteralValue("Enter")));
        }
        String outcome = outcome(s);
        if (!outcome.isEmpty()) {
            b.observedOutcome(outcome);
        }
        return b.build();
    }

    // ------------------------------------------------------------------------------------------ nome

    /** "Preencher campo Usuário", "Clicar no botão Entrar", "Selecionar São Paulo no campo Estado"… */
    static String name(RecordedStep s, ElementReference e) {
        String n = e.accessibleName().isBlank() || !DisplaySafety.isSafeToDisplay(e.accessibleName()) ? "" : e.accessibleName().strip();
        String kind = e.semanticKind();
        String name = switch (s.action()) {
            case "FILL" -> n.isEmpty() ? "Preencher campo" : "Preencher campo " + n;
            case "SELECT_OPTION" -> {
                String opt = s.optionLabel() == null || s.optionLabel().isBlank() || !DisplaySafety.isSafeToDisplay(s.optionLabel()) ? null : s.optionLabel().strip();
                String field = n.isEmpty() ? "no campo" : "no campo " + n;
                yield opt == null ? "Selecionar opção " + field : "Selecionar " + opt + " " + field;
            }
            case "CHECK" -> "RADIO".equals(kind) ? (n.isEmpty() ? "Marcar opção" : "Marcar opção " + n) : (n.isEmpty() ? "Marcar caixa de seleção" : "Marcar " + n);
            case "UNCHECK" -> n.isEmpty() ? "Desmarcar caixa de seleção" : "Desmarcar " + n;
            case "PRESS_KEY" -> "Pressionar " + ("Enter".equals(s.key()) ? "Enter" : "tecla") + (n.isEmpty() ? "" : " no campo " + n);
            case "UNKNOWN" -> "Interação não classificada" + (n.isEmpty() ? (e.tag().isBlank() ? "" : " em <" + e.tag() + ">") : " em " + n);
            case "EXTRACT" -> read(e, n);
            default -> click(kind, n, e.tag());
        };
        name = name.replaceAll("\\s+", " ").strip();
        return name.length() > Texts.NAME_MAX ? name.substring(0, Texts.NAME_MAX - 1).strip() + "…" : name;
    }

    /** "Ler “Nascimento”" (rótulo da linha/termo), "Ler o título", "Ler o dado". Nunca o texto lido. */
    private static String read(ElementReference e, String n) {
        for (var c : e.context()) {
            String label = c.name() == null ? "" : c.name().strip();
            if (!label.isEmpty() && DisplaySafety.isSafeToDisplay(label) && ("IN_ROW".equals(c.relation()) || "TERM".equals(c.kind()))) {
                return "Ler “" + label + "”";
            }
        }
        if ("HEADING".equals(e.semanticKind())) {
            return "Ler o título";
        }
        return n.isEmpty() ? "Ler o dado" : "Ler “" + n + "”";
    }

    private static String click(String kind, String n, String tag) {
        String target = switch (kind) {
            case "BUTTON", "CUSTOM_DROPDOWN" -> n.isEmpty() ? "no botão" : "no botão " + n;
            case "LINK" -> n.isEmpty() ? "no link" : "no link " + n;
            case "MENUITEM" -> n.isEmpty() ? "no item de menu" : "no item de menu " + n;
            case "TAB" -> n.isEmpty() ? "na aba" : "na aba " + n;
            case "COMBOBOX" -> n.isEmpty() ? "na lista" : "na lista " + n;
            default -> n.isEmpty() ? (tag.isBlank() ? "no elemento" : "no elemento <" + tag + ">") : "em " + n;
        };
        return "Clicar " + target;
    }

    private static String description(RecordedStep s) {
        if ("EXTRACT".equals(s.action())) {
            return "Dado apontado no modo “Selecionar dado”: o robô LÊ este elemento na execução. Nada foi clicado e nenhum texto da página foi guardado.";
        }
        return s.proposed()
                ? "Proposta registrada no modo “Capturar”: a ação NÃO foi executada no navegador."
                : "Interação observada no navegador e encaminhada à página. O resultado de negócio não foi validado.";
    }

    private static String outcome(RecordedStep s) {
        List<String> parts = new ArrayList<>();
        if (s.evidence().contains("FORM_SUBMITTED")) {
            parts.add("envio de formulário observado");
        }
        if ("NEW_DOCUMENT".equals(s.navigationAfter())) {
            parts.add("a página mudou de documento");
        } else if ("SAME_DOCUMENT".equals(s.navigationAfter())) {
            parts.add("a rota da página mudou");
        }
        if (parts.isEmpty()) {
            return "";
        }
        String text = String.join("; ", parts);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1) + " (resultado de negócio não validado).";
    }

    // --------------------------------------------------------------------------------------- elemento

    static ElementReference element(RecordedStep s) {
        return element(s.element(), s.notes(), recordingKeys(s));
    }

    private static EnumMap<CorrelationKeys.Key, String> recordingKeys(RecordedStep s) {
        EnumMap<CorrelationKeys.Key, String> base = new EnumMap<>(CorrelationKeys.Key.class);
        base.put(CorrelationKeys.Key.RECORDING_ID, s.recordingId());
        base.put(CorrelationKeys.Key.RECORDED_EVENT_ID, s.eventId());
        return base;
    }

    /**
     * STUDIO-009: referência de um elemento ESCOLHIDO no navegador pelo debug guiado (evento {@code studio.debug.picked}).
     * Mesma 4ª barreira e mesmo mapeamento de uma etapa gravada; sem chaves de gravação (não houve gravação).
     */
    public static ElementReference selectedElement(Map<String, Object> raw, List<String> notes) {
        List<String> n = new java.util.ArrayList<>(notes);
        Map<String, Object> safe = RecordedStep.guard(raw, n);
        return element(safe, n, new EnumMap<>(CorrelationKeys.Key.class));
    }

    private static ElementReference element(Map<String, Object> e, List<String> notes, EnumMap<CorrelationKeys.Key, String> baseKeys) {
        boolean sensitive = Json.bool(e, "sensitive", false);
        ElementReference.Builder b = ElementReference.builder()
                .accessibleName(text(Json.string(e, "name")))
                .semanticKind(Json.string(e, "kind"))
                .tag(text(Json.string(e, "tag")))
                .htmlId(sensitive ? null : Json.string(e, "htmlId"))
                .htmlName(sensitive ? null : Json.string(e, "htmlName"))
                .sensitive(sensitive)
                .privacyPolicy(DisplaySafety.KNOWN_PRODUCER_POLICY)
                .sourceContract(SOURCE_CONTRACT)
                .correlation(correlation(baseKeys, e));
        List<Object> locators = Json.array(e, "locators");
        int preferred = (int) Json.number(e, "preferredLocator", -1);
        boolean live = false;
        for (int i = 0; i < locators.size() && i < 5; i++) {
            if (!(locators.get(i) instanceof Map<?, ?> raw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> l = (Map<String, Object>) raw;
            LocatorStrategy strategy = enumOr(LocatorStrategy.class, Json.string(l, "strategy"), LocatorStrategy.ATTRIBUTE);
            String expr = Json.string(l, "css") != null ? Json.string(l, "css") : Json.string(l, "xpath");
            LocatorCandidate c = new LocatorCandidate(strategy, text(Json.string(l, "description")), expr == null ? "" : expr,
                    enumOr(LocatorStability.class, Json.string(l, "stability"), LocatorStability.UNKNOWN),
                    (int) Math.max(0, Json.number(l, "matchCount", 0)), Json.bool(l, "matchesTarget", false));
            b.addLocator(c);
            String validation = Json.string(l, "validation");
            if (i == preferred && c.validated()) {
                b.preferLastLocator(); // só um localizador validado pela extensão é preferencial
                live = "LIVE".equals(validation);
            }
        }
        if (live) {
            b.addStabilityNote("Localizador preferencial executado na página real e único");
        } else if (preferred >= 0) {
            b.addStabilityNote("Localizador preferencial único na captura (não verificado na página real)");
        }
        for (Object o : Json.array(e, "accessPath")) {
            if (o instanceof Map<?, ?> raw) {
                @SuppressWarnings("unchecked")
                Map<String, Object> st = (Map<String, Object>) raw;
                AccessStep.Type type = enumOr(AccessStep.Type.class, Json.string(st, "type"), null);
                if (type != null) {
                    b.addAccessStep(new AccessStep(type, text(Json.string(st, "label")), Json.string(st, "locator"), CorrelationKeys.none()));
                }
            }
        }
        for (Object o : Json.array(e, "context")) {
            if (o instanceof Map<?, ?> raw) {
                @SuppressWarnings("unchecked")
                Map<String, Object> a = (Map<String, Object>) raw;
                if (Json.string(a, "relation") != null) {
                    b.addContext(new ContextAnchor(Json.string(a, "relation"), Json.string(a, "kind"), text(Json.string(a, "name")), ""));
                }
            }
        }
        long lookalikes = Json.number(e, "lookalikes", 0);
        if (lookalikes > 0) {
            b.addStabilityNote(lookalikes + " elemento(s) parecido(s) na página");
        }
        for (String note : notes) {
            switch (note) {
                case "ELEMENT_UNRESOLVED" -> b.addObstacle(new ObservedObstacle("ELEMENT_UNRESOLVED", ObservedObstacle.Severity.HIGH,
                        "O motor semântico não correlacionou o elemento: sem identidade nem localizador definitivo (revisar)."));
                case "AMBIGUOUS_TARGET" -> b.addObstacle(new ObservedObstacle("AMBIGUOUS_TARGET", ObservedObstacle.Severity.MEDIUM,
                        "O alvo do clique não tem semântica de interação: nenhum elemento ancestral foi escolhido por palpite."));
                case "INDISTINGUISHABLE_LOOKALIKES" -> b.addObstacle(new ObservedObstacle("INDISTINGUISHABLE_LOOKALIKES", ObservedObstacle.Severity.MEDIUM,
                        "Há elementos idênticos na página que não se distinguem pelos atributos."));
                case "ACCESS_RESTRICTED" -> b.addObstacle(new ObservedObstacle("ACCESS_RESTRICTED", ObservedObstacle.Severity.MEDIUM,
                        "A trajetória até o elemento passa por um documento com acesso restrito."));
                case "NO_UNIQUE_LOCATOR" -> b.addStabilityNote("Nenhum localizador único na captura");
                // CHROME-SECURITY-001: limitações de privacidade declaradas ao usuário (nada é inventado para compensar)
                case "OPTION_WITHHELD" -> b.addStabilityNote("Opção escolhida não registrada (privacidade): configure a opção no Studio");
                case "LOCATORS_WITHHELD" -> b.addStabilityNote("Localizadores com texto da página descartados (privacidade)");
                case "SAFE_LOCATOR_UNAVAILABLE" -> b.addStabilityNote(
                        "Elemento identificado parcialmente: não foi possível produzir um localizador seguro e único");
                case "LIVE_CHECK_UNAVAILABLE" -> b.addStabilityNote("Verificação na página real indisponível (o documento mudou)");
                // NEKKORA-STUDIO-012
                case "IDENTIFIER_WITHHELD" -> b.addStabilityNote(
                        "O elemento tem id/name, mas a política de privacidade reteve o valor (termo fora do vocabulário e da lista técnica): "
                                + "não há localizador por id/name");
                case "REVEALED_BY_HOVER" -> b.addObstacle(new ObservedObstacle("INTERACTION_DEPENDENCY", ObservedObstacle.Severity.MEDIUM,
                        "O alvo só aparece com o ponteiro sobre outro item (regra :hover da própria página). A reprodução precisa desse "
                                + "gesto, que o Studio não registra como etapa: revise antes de executar."));
                default -> {
                }
            }
        }
        return b.build();
    }

    private static CorrelationKeys correlation(EnumMap<CorrelationKeys.Key, String> base, Map<String, Object> e) {
        EnumMap<CorrelationKeys.Key, String> keys = new EnumMap<>(base);
        Map<String, Object> c = Json.object(e, "correlation");
        if (c != null) {
            put(keys, CorrelationKeys.Key.TARGET_ID, Json.string(c, "targetId"));
            put(keys, CorrelationKeys.Key.NODE_ID, Json.string(c, "nodeId"));
            put(keys, CorrelationKeys.Key.CAPTURE_ID, Json.string(c, "captureId"));
            put(keys, CorrelationKeys.Key.DOCUMENT_ID, Json.string(c, "documentId"));
        }
        return CorrelationKeys.of(keys);
    }

    private static void put(Map<CorrelationKeys.Key, String> m, CorrelationKeys.Key k, String v) {
        if (v != null && !v.isBlank()) {
            m.put(k, v);
        }
    }

    private static RecordingObservation observation(RecordedStep s) {
        // SELECT_DATA também seleciona sem executar: no modelo é uma proposta do modo "Capturar" (sem novo valor no formato)
        RecordingObservation.CaptureMode mode = s.mode() == RecordingMode.CAPTURE_ONLY || s.mode() == RecordingMode.SELECT_DATA
                ? RecordingObservation.CaptureMode.CAPTURE_ONLY
                : RecordingObservation.CaptureMode.CAPTURE_AND_INTERACT;
        return new RecordingObservation(mode, s.proposed() ? RecordingObservation.Observation.PROPOSED : RecordingObservation.Observation.INTERACTION_OBSERVED,
                RecordingObservation.BusinessOutcome.NOT_VALIDATED, s.evidence(), s.notes());
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback) {
        if (name == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static String text(String s) {
        return s == null ? "" : s.strip();
    }

    private static String safe(String s) {
        return s == null || !DisplaySafety.isSafeToDisplay(s) ? "" : s;
    }
}
