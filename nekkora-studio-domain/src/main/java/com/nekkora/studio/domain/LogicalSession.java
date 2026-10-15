package com.nekkora.studio.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Unidade lógica de contexto de uma automação (ex.: Autenticação, Consulta, Resultado). É uma divisão de
 * entendimento do processo — não representa obrigatoriamente uma URL, janela ou página, e <b>não</b> se
 * confunde com as sessões técnicas de captura/gravação da Chrome Extension.
 *
 * <p>As etapas formam uma sequência explícita (a ordem desta lista). Mutadores restritos ao pacote:
 * alterações passam por {@link Automation}.
 */
public final class LogicalSession {
    private final SessionId id;
    private String name;
    private String description;
    private String notes = "";
    private final List<AutomationStep> steps = new ArrayList<>();
    private final List<AutomationStep> view = Collections.unmodifiableList(steps);

    LogicalSession(SessionId id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public SessionId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    /** Observações opcionais (vazias por padrão). */
    public String notes() {
        return notes;
    }

    /** Etapas na ordem da sessão (somente leitura). */
    public List<AutomationStep> steps() {
        return view;
    }

    public int stepCount() {
        return steps.size();
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    public Optional<AutomationStep> step(StepId stepId) {
        return steps.stream().filter(s -> s.id().equals(stepId)).findFirst();
    }

    /** Posição (1..n) da etapa nesta sessão, ou 0 se não pertencer a ela. */
    public int positionOf(StepId stepId) {
        for (int i = 0; i < steps.size(); i++) if (steps.get(i).id().equals(stepId)) return i + 1;
        return 0;
    }

    void rename(String name) {
        this.name = name;
    }

    void describe(String description) {
        this.description = description;
    }

    void annotate(String notes) {
        this.notes = notes;
    }

    void append(AutomationStep step) {
        steps.add(step);
    }

    void removeAll(java.util.Collection<AutomationStep> toRemove) {
        steps.removeAll(toRemove);
    }

    void insert(int index, AutomationStep step) {
        steps.add(index, step);
    }

    void remove(AutomationStep step) {
        steps.remove(step);
    }

    /** Fotografia dos campos e da ordem (histórico de edição em memória). */
    State state() {
        return new State(name, description, notes, List.copyOf(steps));
    }

    void apply(State s) {
        name = s.name();
        description = s.description();
        notes = s.notes();
        steps.clear();
        steps.addAll(s.steps());
    }

    record State(String name, String description, String notes, List<AutomationStep> steps) {}

    @Override
    public String toString() {
        return "LogicalSession[\"" + name + "\", " + steps.size() + " etapa(s)]";
    }
}
