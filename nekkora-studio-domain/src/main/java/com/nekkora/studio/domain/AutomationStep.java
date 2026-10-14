package com.nekkora.studio.domain;

import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.origin.StepProvenance;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Instrução individual de uma automação. Criada e alterada apenas pelas operações de {@link Automation}
 * (mutadores restritos ao pacote), de modo que toda alteração passa pelas regras de domínio.
 *
 * <p>A posição da etapa é dada pela ordem explícita da sessão; a numeração apresentada é calculada por
 * {@link Automation#sequence()} e não é identidade — a identidade é {@link #id()}.
 */
public final class AutomationStep {
    private final StepId id;
    private String name;
    private String description;
    private ActionType actionType;
    private final StepProvenance provenance;
    private final ElementReference element;
    private final ActionParameters parameters;
    private final StepContext context;
    private final String observedOutcome;
    private final List<StepComment> comments;
    private final Instant createdAt;

    AutomationStep(StepId id, String name, String description, StepDraft draft, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.actionType = draft.actionType();
        this.provenance = draft.provenance();
        this.element = draft.element();
        this.parameters = draft.parameters();
        this.context = draft.context();
        this.observedOutcome = draft.observedOutcome() == null ? "" : draft.observedOutcome().strip();
        this.comments = draft.comments();
        this.createdAt = createdAt;
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

    public StepProvenance provenance() {
        return provenance;
    }

    public Optional<ElementReference> element() {
        return Optional.ofNullable(element);
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

    void rename(String name) {
        this.name = name;
    }

    void describe(String description) {
        this.description = description;
    }

    void changeActionType(ActionType type) {
        this.actionType = type;
    }

    @Override
    public String toString() {
        return "AutomationStep[" + actionType + " \"" + name + "\"]";
    }
}
