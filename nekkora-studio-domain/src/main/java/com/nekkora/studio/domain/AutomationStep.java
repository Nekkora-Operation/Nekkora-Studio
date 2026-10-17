package com.nekkora.studio.domain;

import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.ManualLocator;
import com.nekkora.studio.domain.origin.StepOrigin;
import com.nekkora.studio.domain.origin.StepProvenance;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Instrução individual de uma automação. Criada e alterada apenas pelas operações de {@link Automation}
 * (mutadores restritos ao pacote), de modo que toda alteração passa pelas regras de domínio.
 *
 * <p>A posição da etapa é dada pela ordem explícita da sessão; a numeração apresentada é calculada por
 * {@link Automation#sequence()} e não é identidade — a identidade é {@link #id()}.
 *
 * <p>STUDIO-004: a etapa separa o <b>estado efetivo</b> (nome, descrição, tipo, parâmetros, localizador preferencial,
 * localizadores propostos e comentários — editáveis) da <b>observação original</b> de uma etapa gravada
 * ({@link #baseline()}, {@link #capturedElement()} e a {@link com.nekkora.studio.domain.origin.RecordingObservation} da
 * procedência — imutáveis). Editar não transforma {@code RECORDED} em {@code MANUAL}: a alteração aparece em
 * {@link #manualRevision()}.
 */
public final class AutomationStep {
    private final StepId id;
    private final StepProvenance provenance;
    private final ElementReference capturedElement;
    private final StepContext context;
    private final String observedOutcome;
    private final Instant createdAt;
    private final CapturedBaseline baseline;
    private final StepId derivedFrom;

    // ---- estado efetivo (editável pelas operações de Automation)
    private String name;
    private String description;
    private ActionType actionType;
    private ActionParameters parameters;
    private ElementReference element;
    private List<ManualLocator> manualLocators = List.of();
    private List<StepComment> comments;
    private Map<StepField, Instant> edits = Map.of();
    private Instant lastManualChange;
    /** STUDIO-009: alvo que SUBSTITUI o capturado depois de uma correção aprovada (null = o capturado vale). */
    private ElementReference replacement;
    /** STUDIO-009: decisão durável sobre o alvo (confirmado/rejeitado, por quem), separada da verificação técnica. */
    private com.nekkora.studio.domain.target.TargetDecision decision;
    /** Estado efetivo imutável atual, compartilhado pelas fotografias do histórico enquanto a etapa não muda. */
    private State cached;
    /**
     * STUDIO-005: evidências observadas depois da ação — só acrescentadas (nunca editadas nem removidas), em ordem de
     * sequência técnica. Ficam FORA do estado efetivo ({@link State}): o histórico de desfazer/refazer não as apaga nem as
     * reaplica, e a chegada delas não conta revisão.
     */
    private final List<com.nekkora.studio.domain.evidence.ObservedEvidence> evidence = new java.util.ArrayList<>();
    private final List<com.nekkora.studio.domain.evidence.ObservedEvidence> evidenceView = java.util.Collections.unmodifiableList(evidence);

    AutomationStep(StepId id, String name, String description, StepDraft draft, Instant createdAt) {
        this(id, name, description, draft, createdAt, null);
    }

    AutomationStep(StepId id, String name, String description, StepDraft draft, Instant createdAt, StepId derivedFrom) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.actionType = draft.actionType();
        this.provenance = draft.provenance();
        this.capturedElement = draft.element();
        this.element = draft.element();
        this.parameters = draft.parameters();
        this.context = draft.context();
        this.observedOutcome = draft.observedOutcome() == null ? "" : draft.observedOutcome().strip();
        this.comments = draft.comments();
        this.createdAt = createdAt;
        this.derivedFrom = derivedFrom;
        this.baseline = provenance.origin() == StepOrigin.RECORDED
                ? new CapturedBaseline(name, description, actionType, parameters, element == null ? -1 : element.preferredLocatorIndex())
                : null;
    }

    /**
     * Reconstrução (STUDIO-006): original e efetivo exatamente como persistidos — o original NÃO é recalculado a partir
     * do estado efetivo. Só {@link AutomationRestoration} usa, depois de validar tudo.
     */
    AutomationStep(StepId id, StepProvenance provenance, ElementReference capturedElement, StepContext context, String observedOutcome, Instant createdAt,
            CapturedBaseline baseline, StepId derivedFrom, State effective, List<com.nekkora.studio.domain.evidence.ObservedEvidence> evidence) {
        this.id = id;
        this.provenance = provenance;
        this.capturedElement = capturedElement;
        this.context = context;
        this.observedOutcome = observedOutcome == null ? "" : observedOutcome.strip();
        this.createdAt = createdAt;
        this.baseline = baseline;
        this.derivedFrom = derivedFrom;
        apply(effective);
        this.evidence.addAll(evidence);
    }

    public StepId id() {
        return id;
    }

    /** Nome apresentado ao usuário (ex.: "Preencher usuário"). */
    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public ActionType actionType() {
        return actionType;
    }

    /** Procedência original — nunca muda com edições, movimentações ou duplicações. */
    public StepProvenance provenance() {
        return provenance;
    }

    /** Referência EFETIVA do elemento (com o localizador preferencial escolhido). */
    public Optional<ElementReference> element() {
        return Optional.ofNullable(element);
    }

    /** Referência do elemento como foi recebida (captura ou criação) — imutável. */
    public Optional<ElementReference> capturedElement() {
        return Optional.ofNullable(capturedElement);
    }

    /**
     * STUDIO-009: o elemento que substituiu o capturado depois de uma correção aprovada. Vazio = o alvo é o capturado. A
     * referência capturada ({@link #capturedElement()}) e a procedência nunca mudam.
     */
    public Optional<ElementReference> replacementTarget() {
        return Optional.ofNullable(replacement);
    }

    /** STUDIO-009: decisão durável sobre o alvo efetivo, se houver. */
    public Optional<com.nekkora.studio.domain.target.TargetDecision> targetDecision() {
        return Optional.ofNullable(decision);
    }

    /** Base do alvo efetivo: o substituto, se houver; senão o capturado. */
    Optional<ElementReference> targetBase() {
        return replacement != null ? Optional.of(replacement) : capturedElement();
    }

    /** Localizadores propostos manualmente — todos {@link ManualLocator.Verification#NOT_VERIFIED}. */
    public List<ManualLocator> manualLocators() {
        return manualLocators;
    }

    public ActionParameters parameters() {
        return parameters;
    }

    public Optional<StepContext> context() {
        return Optional.ofNullable(context);
    }

    /** Condição ou resultado observado, quando disponível (vazio na criação manual). */
    public String observedOutcome() {
        return observedOutcome;
    }

    public List<StepComment> comments() {
        return comments;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** Valores capturados de uma etapa gravada (vazio para etapas manuais, inclusive duplicadas). */
    public Optional<CapturedBaseline> baseline() {
        return Optional.ofNullable(baseline);
    }

    /** Etapa da qual esta foi duplicada (vínculo interno; a procedência desta continua {@code MANUAL}). */
    public Optional<StepId> derivedFrom() {
        return Optional.ofNullable(derivedFrom);
    }

    /** O campo difere do valor capturado (sempre {@code false} sem valor capturado). */
    public boolean modified(StepField field) {
        if (baseline == null) return false;
        return switch (field) {
            case NAME -> !name.equals(baseline.name());
            case DESCRIPTION -> !description.equals(baseline.description());
            case ACTION_TYPE -> actionType != baseline.actionType();
            case PARAMETERS -> !parameters.equals(baseline.parameters());
            case PREFERRED_LOCATOR -> replacement == null && element != null && element.preferredLocatorIndex() != baseline.preferredLocator();
            case TARGET -> replacement != null;
        };
    }

    /**
     * Revisão manual de uma etapa gravada: campos diferentes do capturado, localizadores propostos e/ou comentários
     * acrescentados depois da gravação. Vazio quando a etapa não tem valor capturado ou continua como foi gravada.
     */
    public Optional<ManualRevision> manualRevision() {
        if (baseline == null) return Optional.empty();
        Set<StepField> fields = EnumSet.noneOf(StepField.class);
        for (StepField f : StepField.values()) if (modified(f)) fields.add(f);
        if (fields.isEmpty() && manualLocators.isEmpty() && comments.isEmpty()) return Optional.empty();
        return Optional.of(new ManualRevision(fields, !manualLocators.isEmpty(), !comments.isEmpty(),
                lastManualChange == null ? createdAt : lastManualChange));
    }

    /** Última alteração manual registrada no estado efetivo, se houve (persistência — STUDIO-006). */
    public Optional<Instant> lastManualChange() {
        return Optional.ofNullable(lastManualChange);
    }

    /** Quando o campo foi alterado manualmente pela última vez, se foi. */
    public Optional<Instant> editedAt(StepField field) {
        return Optional.ofNullable(edits.get(field));
    }

    /** Evidências observadas desta etapa (somente leitura, em ordem de sequência) — STUDIO-005. */
    public List<com.nekkora.studio.domain.evidence.ObservedEvidence> evidence() {
        return evidenceView;
    }

    /** Resumo estruturado do que foi observado depois da ação (derivado das evidências). */
    public com.nekkora.studio.domain.evidence.ObservationSummary observation() {
        return com.nekkora.studio.domain.evidence.ObservationSummary.of(this);
    }

    /** Acrescenta uma evidência (restrito ao pacote: só por {@link Automation#attachEvidence}). {@code false} se repetida. */
    boolean addEvidence(com.nekkora.studio.domain.evidence.ObservedEvidence e) {
        for (com.nekkora.studio.domain.evidence.ObservedEvidence x : evidence) {
            if (x.evidenceId().equals(e.evidenceId())) return false;
        }
        int at = evidence.size();
        while (at > 0 && evidence.get(at - 1).sequence() > e.sequence()) at--; // ordem técnica, mesmo com chegada fora de ordem
        evidence.add(at, e);
        return true;
    }

    // ------------------------------------------------------------------ estado (restrito ao pacote)

    /** Fotografia imutável do estado efetivo (histórico de edição em memória). */
    State state() {
        if (cached == null) {
            cached = new State(name, description, actionType, parameters, element, manualLocators, comments, edits, lastManualChange, replacement, decision);
        }
        return cached;
    }

    void apply(State s) {
        cached = s;
        this.name = s.name();
        this.description = s.description();
        this.actionType = s.actionType();
        this.parameters = s.parameters();
        this.element = s.element();
        this.manualLocators = s.manualLocators();
        this.comments = s.comments();
        this.edits = s.edits();
        this.lastManualChange = s.lastManualChange();
        this.replacement = s.replacement();
        this.decision = s.decision();
    }

    /** Aplica um novo estado efetivo registrando, nos campos que mudaram, o instante da alteração manual. */
    void edit(State next, Instant at) {
        State before = state();
        EnumMap<StepField, Instant> e = edits.isEmpty() ? new EnumMap<>(StepField.class) : new EnumMap<>(edits);
        if (!before.name().equals(next.name())) e.put(StepField.NAME, at);
        if (!before.description().equals(next.description())) e.put(StepField.DESCRIPTION, at);
        if (before.actionType() != next.actionType()) e.put(StepField.ACTION_TYPE, at);
        if (!before.parameters().equals(next.parameters())) e.put(StepField.PARAMETERS, at);
        if (preferred(before.element()) != preferred(next.element())) e.put(StepField.PREFERRED_LOCATOR, at);
        if (before.replacement() != next.replacement()) e.put(StepField.TARGET, at);
        boolean changed = !before.equalsIgnoringHistory(next);
        apply(new State(next.name(), next.description(), next.actionType(), next.parameters(), next.element(), next.manualLocators(),
                next.comments(), e.isEmpty() ? Map.of() : Map.copyOf(e), changed ? at : lastManualChange, next.replacement(), next.decision()));
    }

    private static int preferred(ElementReference e) {
        return e == null ? -1 : e.preferredLocatorIndex();
    }

    /** Estado efetivo de uma etapa. */
    record State(String name, String description, ActionType actionType, ActionParameters parameters, ElementReference element,
                 List<ManualLocator> manualLocators, List<StepComment> comments, Map<StepField, Instant> edits, Instant lastManualChange,
                 ElementReference replacement, com.nekkora.studio.domain.target.TargetDecision decision) {
        State {
            Objects.requireNonNull(name);
            Objects.requireNonNull(description);
            Objects.requireNonNull(actionType);
            Objects.requireNonNull(parameters);
            manualLocators = List.copyOf(manualLocators);
            comments = List.copyOf(comments);
            edits = Map.copyOf(edits);
        }

        State withName(String n) {
            return new State(n, description, actionType, parameters, element, manualLocators, comments, edits, lastManualChange, replacement, decision);
        }

        /** Mesmo estado com outros parâmetros (a decisão e o substituto seguem; o reuso confere a expectativa). */
        State withParameters(ActionParameters p) {
            return new State(name, description, actionType, p, element, manualLocators, comments, edits, lastManualChange, replacement, decision);
        }

        boolean equalsIgnoringHistory(State o) {
            return name.equals(o.name) && description.equals(o.description) && actionType == o.actionType && parameters.equals(o.parameters)
                    && preferred(element) == preferred(o.element) && manualLocators.equals(o.manualLocators) && comments.equals(o.comments)
                    && replacement == o.replacement && Objects.equals(decision, o.decision);
        }
    }

    @Override
    public String toString() {
        return "AutomationStep[" + actionType + " \"" + name + "\"]";
    }
}
