package com.nekkora.studio.domain;

import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.ElementReference;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Processo completo — raiz do agregado {@code Automation → LogicalSession → AutomationStep}.
 *
 * <p>Toda alteração passa por uma operação deste objeto, que valida as regras, mantém a ordem explícita
 * de sessões e etapas, atualiza {@link #updatedAt()} e incrementa {@link #revision()}. A interface é
 * apenas uma das origens dessas operações: o Recorder (STUDIO-002) usará {@link #appendStep} da mesma
 * forma, sem simular cliques.
 *
 * <p>Não depende de gravação: uma automação pode ser construída manualmente, gravada, importada ou gerada.
 */
public final class Automation {
    private final AutomationId id;
    private String name;
    private String description;
    private final AutomationStatus status = AutomationStatus.DRAFT;
    private final List<LogicalSession> sessions = new ArrayList<>();
    private final List<LogicalSession> view = Collections.unmodifiableList(sessions);
    private final Instant createdAt;
    private Instant updatedAt;
    private final String modelVersion = ModelVersion.CURRENT;
    private final Clock clock;
    private long revision;

    private Automation(AutomationId id, String name, String description, Clock clock) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.clock = clock;
        this.createdAt = clock.instant();
        this.updatedAt = createdAt;
    }

    /** Cria um rascunho vazio. Nome obrigatório; descrição opcional. */
    public static Automation create(String name, String description, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return new Automation(AutomationId.newId(), Texts.requireName(name), Texts.optionalDescription(description), clock);
    }

    // ------------------------------------------------------------------ leitura

    public AutomationId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public AutomationStatus status() {
        return status;
    }

    /** Sessões na ordem da automação (somente leitura). */
    public List<LogicalSession> sessions() {
        return view;
    }

    public Optional<LogicalSession> session(SessionId sessionId) {
        return sessions.stream().filter(s -> s.id().equals(sessionId)).findFirst();
    }

    /** Posição (1..n) da sessão, ou 0 se não pertencer a esta automação. */
    public int sessionNumber(SessionId sessionId) {
        for (int i = 0; i < sessions.size(); i++) if (sessions.get(i).id().equals(sessionId)) return i + 1;
        return 0;
    }

    public Optional<AutomationStep> step(StepId stepId) {
        return sessions.stream().flatMap(s -> s.steps().stream()).filter(s -> s.id().equals(stepId)).findFirst();
    }

    /** Sessão que contém a etapa. */
    public Optional<LogicalSession> sessionOf(StepId stepId) {
        return sessions.stream().filter(s -> s.positionOf(stepId) > 0).findFirst();
    }

    public int stepCount() {
        return sessions.stream().mapToInt(LogicalSession::stepCount).sum();
    }

    /** Sequência completa, na ordem: sessão a sessão, etapa a etapa, com numeração contínua calculada. */
    public List<StepPosition> sequence() {
        List<StepPosition> out = new ArrayList<>();
        int number = 0;
        for (int s = 0; s < sessions.size(); s++) {
            LogicalSession session = sessions.get(s);
            for (int i = 0; i < session.stepCount(); i++) {
                out.add(new StepPosition(session.id(), session.steps().get(i).id(), s + 1, i + 1, ++number));
            }
        }
        return List.copyOf(out);
    }

    /** Posição calculada da etapa na sequência. */
    public Optional<StepPosition> positionOf(StepId stepId) {
        return sequence().stream().filter(p -> p.stepId().equals(stepId)).findFirst();
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /** Versão do modelo com que esta automação foi criada ({@link ModelVersion}). */
    public String modelVersion() {
        return modelVersion;
    }

    /** Contador de alterações — muda a cada operação bem-sucedida. */
    public long revision() {
        return revision;
    }

    // ------------------------------------------------------------------ operações

    /** Altera nome e descrição da automação. */
    public void updateDetails(String newName, String newDescription) {
        String n = Texts.requireName(newName);
        String d = Texts.optionalDescription(newDescription);
        this.name = n;
        this.description = d;
        touch();
    }

    /** Acrescenta uma sessão ao final da automação. Nomes de sessão são únicos na automação. */
    public LogicalSession addSession(String sessionName, String sessionDescription) {
        String n = Texts.requireName(sessionName);
        String d = Texts.optionalDescription(sessionDescription);
        requireUniqueSessionName(n, null);
        LogicalSession session = new LogicalSession(SessionId.newId(), n, d);
        sessions.add(session);
        touch();
        return session;
    }

    /** Altera o nome de uma sessão existente. */
    public void renameSession(SessionId sessionId, String newName) {
        LogicalSession session = requireSession(sessionId);
        String n = Texts.requireName(newName);
        requireUniqueSessionName(n, sessionId);
        session.rename(n);
        touch();
    }

    /** Altera a descrição e as observações de uma sessão. */
    public void describeSession(SessionId sessionId, String newDescription, String newNotes) {
        LogicalSession session = requireSession(sessionId);
        String d = Texts.optionalDescription(newDescription);
        String notes = Texts.optionalDescription(newNotes);
        session.describe(d);
        session.annotate(notes);
        touch();
    }

    /**
     * Acrescenta uma etapa ao final da sessão — a operação de domínio usada pela criação manual e, no
     * futuro, por Recorder, importação e geração.
     */
    public AutomationStep appendStep(SessionId sessionId, StepDraft draft) {
        if (sessionId == null) throw new DomainValidationException(ValidationProblem.SESSION_REQUIRED);
        Objects.requireNonNull(draft, "draft");
        LogicalSession session = requireSession(sessionId);
        String n = Texts.requireName(draft.name());
        String d = Texts.optionalDescription(draft.description());
        if (draft.actionType() == null) throw new DomainValidationException(ValidationProblem.ACTION_TYPE_REQUIRED);
        checkParameters(draft.actionType(), draft.parameters(), draft.element());
        AutomationStep step = new AutomationStep(StepId.newId(), n, d, draft, clock.instant());
        session.append(step);
        touch();
        return step;
    }

    /** Edição básica de uma etapa: nome, descrição e tipo de ação (a edição avançada é da STUDIO-004). */
    public void updateStepBasics(StepId stepId, String newName, String newDescription, ActionType newType) {
        AutomationStep step = step(stepId).orElseThrow(() -> new DomainValidationException(ValidationProblem.STEP_NOT_FOUND));
        String n = Texts.requireName(newName);
        String d = Texts.optionalDescription(newDescription);
        if (newType == null) throw new DomainValidationException(ValidationProblem.ACTION_TYPE_REQUIRED);
        checkParameters(newType, step.parameters(), step.element().orElse(null));
        step.rename(n);
        step.describe(d);
        step.changeActionType(newType);
        touch();
    }

    // ------------------------------------------------------------------ regras

    private LogicalSession requireSession(SessionId sessionId) {
        return session(sessionId).orElseThrow(() -> new DomainValidationException(ValidationProblem.SESSION_NOT_FOUND));
    }

    private void requireUniqueSessionName(String candidate, SessionId except) {
        for (LogicalSession s : sessions) {
            if (!s.id().equals(except) && Texts.sameName(s.name(), candidate)) {
                throw new DomainValidationException(ValidationProblem.SESSION_NAME_IN_USE);
            }
        }
    }

    private static void checkParameters(ActionType type, ActionParameters params, ElementReference element) {
        if (params.value().isPresent() && !type.acceptsValue()) throw new DomainValidationException(ValidationProblem.VALUE_NOT_ACCEPTED);
        if (params.option().isPresent() && !type.acceptsOption()) throw new DomainValidationException(ValidationProblem.OPTION_NOT_ACCEPTED);
        if (element != null && element.sensitive() && params.value().orElse(null) instanceof ValueSource.LiteralValue) {
            throw new DomainValidationException(ValidationProblem.LITERAL_ON_SENSITIVE_TARGET);
        }
    }

    private void touch() {
        Instant now = clock.instant();
        updatedAt = now.isBefore(updatedAt) ? updatedAt : now;
        revision++;
    }

    @Override
    public String toString() {
        return "Automation[\"" + name + "\", " + sessions.size() + " sessão(ões)]";
    }
}
