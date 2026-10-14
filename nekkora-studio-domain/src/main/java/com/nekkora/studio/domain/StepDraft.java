package com.nekkora.studio.domain;

import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.origin.StepProvenance;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dados de entrada para criar uma etapa — independe de quem a produz (formulário manual hoje, Recorder,
 * importação ou geração amanhã). A validação acontece na operação de domínio
 * {@link Automation#appendStep(SessionId, StepDraft)}, nunca na interface.
 */
public final class StepDraft {
    private final String name;
    private final String description;
    private final ActionType actionType;
    private final StepProvenance provenance;
    private final ElementReference element;
    private final ActionParameters parameters;
    private final StepContext context;
    private final String observedOutcome;
    private final List<StepComment> comments;

    private StepDraft(Builder b) {
        this.name = b.name;
        this.description = b.description;
        this.actionType = b.actionType;
        this.provenance = b.provenance == null ? StepProvenance.manual() : b.provenance;
        this.element = b.element;
        this.parameters = b.parameters == null ? ActionParameters.none() : b.parameters;
        this.context = b.context;
        this.observedOutcome = b.observedOutcome;
        this.comments = List.copyOf(b.comments);
    }

    /** Etapa manual básica: nome, tipo de ação e descrição opcional. */
    public static StepDraft manual(String name, ActionType actionType, String description) {
        return builder().name(name).actionType(actionType).description(description).build();
    }

    public static Builder builder() {
        return new Builder();
    }

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

    public ElementReference element() {
        return element;
    }

    public ActionParameters parameters() {
        return parameters;
    }

    public StepContext context() {
        return context;
    }

    public String observedOutcome() {
        return observedOutcome;
    }

    public List<StepComment> comments() {
        return comments;
    }

    public static final class Builder {
        private String name;
        private String description;
        private ActionType actionType;
        private StepProvenance provenance;
        private ElementReference element;
        private ActionParameters parameters;
        private StepContext context;
        private String observedOutcome;
        private final List<StepComment> comments = new ArrayList<>();

        private Builder() {}

        public Builder name(String v) {
            name = v;
            return this;
        }

        public Builder description(String v) {
            description = v;
            return this;
        }

        public Builder actionType(ActionType v) {
            actionType = v;
            return this;
        }

        public Builder provenance(StepProvenance v) {
            provenance = v;
            return this;
        }

        public Builder element(ElementReference v) {
            element = v;
            return this;
        }

        public Builder parameters(ActionParameters v) {
            parameters = v;
            return this;
        }

        public Builder context(StepContext v) {
            context = v;
            return this;
        }

        public Builder observedOutcome(String v) {
            observedOutcome = v;
            return this;
        }

        public Builder addComment(StepComment c) {
            comments.add(Objects.requireNonNull(c));
            return this;
        }

        public StepDraft build() {
            return new StepDraft(this);
        }
    }
}
