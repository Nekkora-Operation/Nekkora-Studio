package com.nekkora.studio.domain;

import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.ManualLocator;
import com.nekkora.studio.domain.variable.VariableDefinition;
import com.nekkora.studio.domain.variable.VariableDraft;
import com.nekkora.studio.domain.variable.VariableId;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.variable.VariableRules;
import com.nekkora.studio.domain.variable.VariableScope;
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
    private final Clock clock;
    private long revision;
    /** STUDIO-005: evidências observadas que não puderam ser atribuídas a uma etapa (ambíguas), por ordem de chegada. */
    private final List<com.nekkora.studio.domain.evidence.ObservedEvidence> unattributed = new ArrayList<>();
    private final List<com.nekkora.studio.domain.evidence.ObservedEvidence> unattributedView = Collections.unmodifiableList(unattributed);
    /** STUDIO-008: catálogo de variáveis declaradas, na ordem de declaração (definições imutáveis). */
    private final List<VariableDefinition> variables = new ArrayList<>();
    private final List<VariableDefinition> variablesView = Collections.unmodifiableList(variables);

    /** Evidências por etapa (limite de proteção; o excesso é recusado, nunca descartado em silêncio). */
    public static final int MAX_EVIDENCE_PER_STEP = 100;
    /** Evidências não atribuídas guardadas por automação. */
    public static final int MAX_UNATTRIBUTED = 500;

    private Automation(AutomationId id, String name, String description, Clock clock) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.clock = clock;
        this.createdAt = clock.instant();
        this.updatedAt = createdAt;
    }

    /** Reconstrução (STUDIO-006): identidade, instantes e revisão preservados. Só {@link AutomationRestoration} usa. */
    private Automation(AutomationId id, String name, String description, Instant createdAt, Instant updatedAt, long revision, Clock clock) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.clock = clock;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.revision = revision;
    }

    /** Fábrica restrita ao pacote para {@link AutomationRestoration} (que valida tudo antes). */
    static Automation restored(AutomationId id, String name, String description, Instant createdAt, Instant updatedAt, long revision, Clock clock) {
        return new Automation(id, name, description, createdAt, updatedAt, revision, clock);
    }

    /** Reconstrução: acrescenta uma sessão já validada, sem contar revisão nem mudar instantes. */
    void adoptSession(LogicalSession session) {
        sessions.add(session);
    }

    /** Reconstrução: acrescenta uma evidência não atribuída já validada, na ordem guardada. */
    void adoptUnattributed(com.nekkora.studio.domain.evidence.ObservedEvidence e) {
        unattributed.add(e);
    }

    /** Reconstrução: acrescenta uma variável já validada, na ordem guardada. */
    void adoptVariable(VariableDefinition v) {
        variables.add(v);
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

    /**
     * Versão do modelo necessária para representar esta automação ({@link ModelVersion}): a base 1.0.0 sem variáveis, ou
     * 1.1.0 com o catálogo de variáveis (STUDIO-008, D-S008-06).
     */
    public String modelVersion() {
        return variables.isEmpty() ? ModelVersion.CURRENT : ModelVersion.V1_1;
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
        if (n.equals(name) && d.equals(description)) return; // sem efeito: revisão igual
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
        if (n.equals(session.name())) return;
        session.rename(n);
        touch();
    }

    /** Altera a descrição e as observações de uma sessão. */
    public void describeSession(SessionId sessionId, String newDescription, String newNotes) {
        LogicalSession session = requireSession(sessionId);
        String d = Texts.optionalDescription(newDescription);
        String notes = Texts.optionalDescription(newNotes);
        if (d.equals(session.description()) && notes.equals(session.notes())) return;
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

    /**
     * Descarta, numa única operação, TODAS as etapas {@link com.nekkora.studio.domain.origin.StepOrigin#RECORDED} de uma
     * gravação técnica (cancelamento — STUDIO-003). Etapas manuais e de outras gravações nunca são tocadas; não há
     * exclusão parcial: as etapas são primeiro identificadas em todas as sessões e só então removidas.
     *
     * @return quantas etapas foram descartadas (0 = nada mudou; a revisão não é incrementada)
     */
    public int discardRecording(String recordingId) {
        Objects.requireNonNull(recordingId, "recordingId");
        java.util.Map<LogicalSession, List<AutomationStep>> plan = new java.util.LinkedHashMap<>();
        for (LogicalSession s : sessions) {
            List<AutomationStep> mine = s.steps().stream()
                    .filter(st -> st.provenance().origin() == com.nekkora.studio.domain.origin.StepOrigin.RECORDED)
                    .filter(st -> st.provenance().correlation().get(com.nekkora.studio.domain.element.CorrelationKeys.Key.RECORDING_ID)
                            .map(recordingId::equals).orElse(false))
                    .toList();
            if (!mine.isEmpty()) plan.put(s, mine);
        }
        int count = plan.values().stream().mapToInt(List::size).sum();
        unattributed.removeIf(e -> e.recordingId().equals(recordingId)); // STUDIO-005: nada da gravação cancelada sobrevive
        if (count == 0) return 0;
        plan.forEach(LogicalSession::removeAll); // as evidências das etapas saem com elas
        touch();
        return count;
    }

    /** Edição básica de uma etapa: nome, descrição e tipo de ação (atalho de {@link #editStep}). */
    public void updateStepBasics(StepId stepId, String newName, String newDescription, ActionType newType) {
        AutomationStep step = requireStep(stepId);
        editStep(stepId, StepEdit.of(this, step).name(newName).description(newDescription).actionType(newType));
    }

    // ------------------------------------------------------------------ STUDIO-005: evidências observadas

    /**
     * Registra uma evidência observada (STUDIO-005). Não é edição: não conta revisão, não muda o estado efetivo de nenhuma
     * etapa e não entra no histórico de desfazer/refazer — por isso é permitida durante a gravação.
     *
     * <p>Com etapa ({@link com.nekkora.studio.domain.evidence.ObservedEvidence#attributed()}): vai para a etapa GRAVADA desta
     * gravação com o mesmo evento ({@code RECORDING_ID} + {@code RECORDED_EVENT_ID}); a etapa precisa ter sido EXECUTADA
     * (uma proposta de CAPTURE_ONLY não tem consequência). Sem etapa (ambígua): fica na lista de não atribuídas. Repetida
     * (mesmo evidenceId) não muda nada. A etapa nunca é recriada nem alterada (identidade, ordem, original, tipo e
     * parâmetros ficam como estão).
     *
     * @return {@code false} se a evidência já estava registrada
     */
    public boolean attachEvidence(com.nekkora.studio.domain.evidence.ObservedEvidence e) {
        Objects.requireNonNull(e, "evidence");
        if (!e.attributed()) {
            for (com.nekkora.studio.domain.evidence.ObservedEvidence x : unattributed) {
                if (x.evidenceId().equals(e.evidenceId())) return false;
            }
            if (unattributed.size() >= MAX_UNATTRIBUTED) throw new DomainValidationException(ValidationProblem.EVIDENCE_LIMIT_REACHED);
            unattributed.add(e);
            return true;
        }
        AutomationStep step = recordedStep(e.recordingId(), e.eventId())
                .orElseThrow(() -> new DomainValidationException(ValidationProblem.EVIDENCE_STEP_NOT_FOUND));
        boolean executed = step.provenance().recordingObservation()
                .map(o -> o.observation() == com.nekkora.studio.domain.origin.RecordingObservation.Observation.INTERACTION_OBSERVED).orElse(false);
        if (!executed) throw new DomainValidationException(ValidationProblem.EVIDENCE_NOT_APPLICABLE);
        if (step.evidence().stream().anyMatch(x -> x.evidenceId().equals(e.evidenceId()))) return false;
        if (step.evidence().size() >= MAX_EVIDENCE_PER_STEP) throw new DomainValidationException(ValidationProblem.EVIDENCE_LIMIT_REACHED);
        return step.addEvidence(e);
    }

    /** Evidências observadas sem etapa definida (ambíguas), somente leitura. */
    public List<com.nekkora.studio.domain.evidence.ObservedEvidence> unattributedEvidence() {
        return unattributedView;
    }

    /** Etapa GRAVADA desta gravação com o evento informado. */
    public Optional<AutomationStep> recordedStep(String recordingId, String eventId) {
        if (recordingId == null || eventId == null) return Optional.empty();
        return sessions.stream().flatMap(s -> s.steps().stream())
                .filter(st -> st.provenance().origin() == com.nekkora.studio.domain.origin.StepOrigin.RECORDED)
                .filter(st -> st.provenance().correlation().get(com.nekkora.studio.domain.element.CorrelationKeys.Key.RECORDING_ID)
                        .map(recordingId::equals).orElse(false))
                .filter(st -> st.provenance().correlation().get(com.nekkora.studio.domain.element.CorrelationKeys.Key.RECORDED_EVENT_ID)
                        .map(eventId::equals).orElse(false))
                .findFirst();
    }

    // ------------------------------------------------------------------ STUDIO-004: estrutura

    /**
     * Edita nome, descrição e observações de uma sessão numa única operação: tudo é validado antes (nome único, limites)
     * e nada muda se algum campo for recusado. Sem diferença, sem revisão.
     *
     * @return {@code false} se nada mudou
     */
    public boolean editSession(SessionId sessionId, String newName, String newDescription, String newNotes) {
        LogicalSession session = requireSession(sessionId);
        String n = Texts.requireName(newName);
        String d = Texts.optionalDescription(newDescription);
        String notes = Texts.optionalDescription(newNotes);
        requireUniqueSessionName(n, sessionId);
        if (n.equals(session.name()) && d.equals(session.description()) && notes.equals(session.notes())) return false;
        session.rename(n);
        session.describe(d);
        session.annotate(notes);
        touch();
        return true;
    }

    /**
     * Muda a posição de uma sessão. {@code targetIndex} é a posição final (0..n-1) na lista de sessões. A identidade e as
     * etapas da sessão não mudam; a numeração das etapas é recalculada (é derivada da ordem).
     *
     * @return {@code false} se a sessão já estava nessa posição (nada muda, revisão igual)
     */
    public boolean moveSession(SessionId sessionId, int targetIndex) {
        LogicalSession session = requireSession(sessionId);
        if (targetIndex < 0 || targetIndex >= sessions.size()) throw new DomainValidationException(ValidationProblem.SESSION_POSITION_INVALID);
        int from = sessions.indexOf(session);
        if (from == targetIndex) return false;
        sessions.remove(from);
        sessions.add(targetIndex, session);
        touch();
        return true;
    }

    /**
     * Exclui a sessão e TODAS as etapas dela numa única operação (nenhuma etapa fica órfã). A automação pode ficar sem
     * sessões.
     *
     * @return quantas etapas foram removidas junto
     */
    public int removeSession(SessionId sessionId) {
        LogicalSession session = requireSession(sessionId);
        int steps = session.stepCount();
        sessions.remove(session);
        touch();
        return steps;
    }

    /**
     * Move uma etapa para a posição {@code targetIndex} (0..n) da sessão {@code destination} — a mesma ou outra da MESMA
     * automação. A posição é contada sem a própria etapa. Origem e destino são validados antes de qualquer alteração: a
     * etapa nunca fica fora de uma sessão. Identidade, procedência, parâmetros, referência e comentários não mudam.
     *
     * @return {@code false} se a etapa já estava nessa posição
     */
    public boolean moveStep(StepId stepId, SessionId destination, int targetIndex) {
        AutomationStep step = requireStep(stepId);
        if (destination == null) throw new DomainValidationException(ValidationProblem.SESSION_REQUIRED);
        LogicalSession to = requireSession(destination);
        LogicalSession from = sessionOf(stepId).orElseThrow();
        int sizeWithout = from == to ? to.stepCount() - 1 : to.stepCount();
        if (targetIndex < 0 || targetIndex > sizeWithout) throw new DomainValidationException(ValidationProblem.STEP_POSITION_INVALID);
        if (from == to && from.positionOf(stepId) - 1 == targetIndex) return false;
        from.remove(step);
        to.insert(targetIndex, step);
        touch();
        return true;
    }

    /**
     * Duplica uma etapa logo depois dela, na mesma sessão. A cópia é uma etapa NOVA ({@link StepId} próprio) de origem
     * {@code MANUAL}: não é uma segunda interação observada — não leva identidade de gravação nem de evento, nem a
     * observação da gravação, nem o valor capturado. Copia nome, descrição, tipo, parâmetros, a referência efetiva do
     * elemento, os localizadores propostos (ainda não verificados) e o contexto; não copia comentários. O vínculo com a
     * etapa de origem fica em {@link AutomationStep#derivedFrom()}.
     */
    public AutomationStep duplicateStep(StepId stepId) {
        AutomationStep source = requireStep(stepId);
        LogicalSession session = sessionOf(stepId).orElseThrow();
        // A referência do elemento vai sem as chaves da gravação e do evento da original: elas identificam a INTERAÇÃO
        // observada, não o elemento. Ficam os dados para reconhecer o elemento (ex.: TARGET_ID). O vínculo é derivedFrom.
        ElementReference element = source.element().map(e -> e.withCorrelation(e.correlation()
                .without(com.nekkora.studio.domain.element.CorrelationKeys.Key.RECORDING_ID,
                        com.nekkora.studio.domain.element.CorrelationKeys.Key.RECORDED_EVENT_ID))).orElse(null);
        StepDraft draft = StepDraft.builder().name(source.name()).description(source.description()).actionType(source.actionType())
                .parameters(source.parameters()).element(element).context(source.context().orElse(null)).build();
        AutomationStep copy = new AutomationStep(StepId.newId(), source.name(), source.description(), draft, clock.instant(), source.id());
        if (!source.manualLocators().isEmpty()) {
            AutomationStep.State s = copy.state();
            copy.apply(new AutomationStep.State(s.name(), s.description(), s.actionType(), s.parameters(), s.element(), source.manualLocators(),
                    s.comments(), s.edits(), s.lastManualChange()));
        }
        session.insert(session.positionOf(stepId), copy);
        touch();
        return copy;
    }

    /** Exclui uma etapa; as demais ficam, na mesma ordem. */
    public void removeStep(StepId stepId) {
        AutomationStep step = requireStep(stepId);
        sessionOf(stepId).orElseThrow().remove(step);
        touch();
    }

    // ------------------------------------------------------------------ STUDIO-004: edição da etapa

    /**
     * Aplica a edição completa de uma etapa (nome, descrição, tipo, parâmetros, sessão, localizador preferencial,
     * localizadores propostos e comentários). Tudo é validado ANTES de alterar; havendo qualquer problema, nada muda. Se a
     * sessão de destino for outra, a etapa vai para o final dela (mesma identidade). Uma edição sem diferença não conta
     * revisão. Parâmetros incompatíveis com o novo tipo são recusados — nunca descartados em silêncio.
     *
     * @return {@code false} se nada mudou
     */
    public boolean editStep(StepId stepId, StepEdit edit) {
        Objects.requireNonNull(edit, "edit");
        AutomationStep step = requireStep(stepId);
        String n = Texts.requireName(edit.name());
        String d = Texts.optionalDescription(edit.description());
        if (edit.actionType() == null) throw new DomainValidationException(ValidationProblem.ACTION_TYPE_REQUIRED);
        if (edit.session() == null) throw new DomainValidationException(ValidationProblem.SESSION_REQUIRED);
        LogicalSession to = requireSession(edit.session());
        ElementReference effective = withPreferred(step, edit.preferredLocator());
        checkParameters(edit.actionType(), edit.parameters(), effective);
        if (!edit.manualLocators().isEmpty() && step.capturedElement().isEmpty()) {
            throw new DomainValidationException(ValidationProblem.STEP_HAS_NO_ELEMENT);
        }
        Instant now = clock.instant();
        List<StepComment> comments = new ArrayList<>();
        for (StepEdit.CommentEdit c : edit.comments()) {
            String text = StepComment.requireText(c.text());
            if (c.original() < 0) {
                comments.add(new StepComment(text, now));
            } else {
                if (c.original() >= step.comments().size()) throw new DomainValidationException(ValidationProblem.COMMENT_NOT_FOUND);
                StepComment old = step.comments().get(c.original());
                comments.add(old.text().equals(text) ? old : new StepComment(text, old.createdAt(), now));
            }
        }
        AutomationStep.State before = step.state();
        AutomationStep.State next = new AutomationStep.State(n, d, edit.actionType(), edit.parameters(), effective, edit.manualLocators(), comments,
                before.edits(), before.lastManualChange());
        LogicalSession from = sessionOf(stepId).orElseThrow();
        boolean moves = from != to;
        if (!moves && before.equalsIgnoringHistory(next)) return false;
        step.edit(next, now);
        if (moves) {
            from.remove(step);
            to.insert(to.stepCount(), step);
        }
        touch();
        return true;
    }

    /** Define os parâmetros da etapa (valor, variável, credencial ou opção), com as regras de {@link #editStep}. */
    public boolean setStepParameters(StepId stepId, ActionParameters parameters) {
        return editStep(stepId, StepEdit.of(this, requireStep(stepId)).parameters(parameters));
    }

    /**
     * Escolhe o localizador preferencial entre os candidatos CAPTURADOS (índice na lista) — só um validado na captura
     * pode ser escolhido ({@link com.nekkora.studio.domain.element.LocatorCandidate#validated()}). -1 remove a preferência.
     */
    public boolean choosePreferredLocator(StepId stepId, int index) {
        return editStep(stepId, StepEdit.of(this, requireStep(stepId)).preferredLocator(index));
    }

    /** Propõe um localizador manual (sempre não verificado); o elemento capturado não muda. */
    public boolean addManualLocator(StepId stepId, ManualLocator locator) {
        AutomationStep step = requireStep(stepId);
        List<ManualLocator> list = new ArrayList<>(step.manualLocators());
        list.add(Objects.requireNonNull(locator, "locator"));
        return editStep(stepId, StepEdit.of(this, step).manualLocators(list));
    }

    public boolean removeManualLocator(StepId stepId, int index) {
        AutomationStep step = requireStep(stepId);
        if (index < 0 || index >= step.manualLocators().size()) throw new DomainValidationException(ValidationProblem.LOCATOR_NOT_FOUND);
        List<ManualLocator> list = new ArrayList<>(step.manualLocators());
        list.remove(index);
        return editStep(stepId, StepEdit.of(this, step).manualLocators(list));
    }

    /** Acrescenta um comentário ao final (data de criação = agora). */
    public boolean addComment(StepId stepId, String text) {
        AutomationStep step = requireStep(stepId);
        StepEdit e = StepEdit.of(this, step);
        List<StepEdit.CommentEdit> list = new ArrayList<>(e.comments());
        list.add(StepEdit.CommentEdit.added(text));
        return editStep(stepId, e.comments(list));
    }

    /** Altera o texto de um comentário, preservando a data de criação. */
    public boolean editComment(StepId stepId, int index, String text) {
        AutomationStep step = requireStep(stepId);
        if (index < 0 || index >= step.comments().size()) throw new DomainValidationException(ValidationProblem.COMMENT_NOT_FOUND);
        StepEdit e = StepEdit.of(this, step);
        List<StepEdit.CommentEdit> list = new ArrayList<>(e.comments());
        list.set(index, new StepEdit.CommentEdit(index, text));
        return editStep(stepId, e.comments(list));
    }

    public boolean removeComment(StepId stepId, int index) {
        AutomationStep step = requireStep(stepId);
        if (index < 0 || index >= step.comments().size()) throw new DomainValidationException(ValidationProblem.COMMENT_NOT_FOUND);
        StepEdit e = StepEdit.of(this, step);
        List<StepEdit.CommentEdit> list = new ArrayList<>(e.comments());
        list.remove(index);
        return editStep(stepId, e.comments(list));
    }

    /**
     * Volta um campo de uma etapa gravada ao valor capturado (sanitizado na origem — nenhum segredo existe para voltar). O
     * resultado passa pelas mesmas validações: restaurar parâmetros num tipo que não os aceita, por exemplo, é recusado.
     */
    public boolean restoreOriginal(StepId stepId, StepField field) {
        AutomationStep step = requireStep(stepId);
        CapturedBaseline b = step.baseline().orElseThrow(() -> new DomainValidationException(ValidationProblem.NO_CAPTURED_ORIGINAL));
        StepEdit e = StepEdit.of(this, step);
        switch (Objects.requireNonNull(field, "field")) {
            case NAME -> e.name(b.name());
            case DESCRIPTION -> e.description(b.description());
            case ACTION_TYPE -> e.actionType(b.actionType());
            case PARAMETERS -> e.parameters(b.parameters());
            case PREFERRED_LOCATOR -> e.preferredLocator(b.preferredLocator());
        }
        return editStep(stepId, e);
    }

    // ------------------------------------------------------------------ STUDIO-008: variáveis declaradas

    /** Variáveis declaradas, na ordem de declaração (somente leitura). */
    public List<VariableDefinition> variables() {
        return variablesView;
    }

    public Optional<VariableDefinition> variable(VariableId id) {
        return variables.stream().filter(v -> v.id().equals(id)).findFirst();
    }

    /** Declaração com EXATAMENTE este nome (resolução de {@code VariableReference}; nada é inferido por semelhança). */
    public Optional<VariableDefinition> variableNamed(String name) {
        return variables.stream().filter(v -> v.name().equals(name)).findFirst();
    }

    /**
     * Etapas cujo valor EFETIVO referencia a variável pelo nome (qualquer sessão, dentro ou fora do escopo — a análise
     * de escopo é do diagnóstico). O original capturado não conta: ele nunca é alterado.
     */
    public List<StepId> consumersOf(VariableId id) {
        VariableDefinition v = requireVariable(id);
        return consumersOfName(v.name());
    }

    private List<StepId> consumersOfName(String name) {
        List<StepId> out = new ArrayList<>();
        for (LogicalSession s : sessions) {
            for (AutomationStep st : s.steps()) {
                if (st.parameters().value().orElse(null) instanceof ValueSource.VariableReference r && r.name().equals(name)) out.add(st.id());
            }
        }
        return out;
    }

    /**
     * Declara uma variável. Tudo é conferido antes ({@link VariableRules}, nome único na automação sem distinção de
     * maiúsculas, sessão do escopo e etapa produtora existentes, produtora dentro do escopo).
     */
    public VariableDefinition declareVariable(VariableDraft draft) {
        if (variables.size() >= VariableRules.MAX_VARIABLES) throw new DomainValidationException(ValidationProblem.VARIABLE_LIMIT_REACHED);
        VariableDraft d = checkVariable(draft, null);
        Instant now = clock.instant();
        VariableDefinition v = new VariableDefinition(VariableId.newId(), d.name(), d.type(), d.semantic(), d.authority(), d.purpose(), d.scope(),
                d.origin(), d.sensitivity(), d.constraints(), now, now);
        variables.add(v);
        touch();
        return v;
    }

    /**
     * Edita uma variável numa única operação. Se o nome mudar, é uma RENOMEAÇÃO: todas as referências efetivas são
     * atualizadas na mesma operação (ver {@link #renameVariable}). Sem diferença, sem revisão.
     *
     * @return {@code false} se nada mudou
     */
    public boolean updateVariable(VariableId id, VariableDraft draft) {
        VariableDefinition current = requireVariable(id);
        VariableDraft d = checkVariable(draft, id);
        if (d.equals(current.draft())) return false;
        Instant now = clock.instant();
        List<StepId> consumers = d.name().equals(current.name()) ? List.of() : consumersOfName(current.name());
        retarget(consumers, d.name(), now);
        variables.set(variables.indexOf(current), current.with(d, now));
        touch();
        return true;
    }

    /**
     * Renomeia a variável e atualiza, na MESMA operação, todas as etapas que a referenciam pelo nome antigo (valor
     * efetivo). O original capturado e a procedência de cada etapa não mudam. Ou tudo muda, ou nada muda.
     *
     * @return quantas etapas tiveram a referência atualizada
     */
    public int renameVariable(VariableId id, String newName) {
        VariableDefinition current = requireVariable(id);
        VariableDraft d = checkVariable(current.draft().withName(newName), id);
        if (d.name().equals(current.name())) return 0;
        List<StepId> consumers = consumersOfName(current.name());
        Instant now = clock.instant();
        retarget(consumers, d.name(), now);
        variables.set(variables.indexOf(current), current.renamed(d.name(), now));
        touch();
        return consumers.size();
    }

    /**
     * Exclui a variável. Se houver etapas que a usam, só com {@code detachConsumers}: os vínculos são descartados (as
     * etapas ficam sem valor) — nunca convertidos em literais, nunca etapas excluídas.
     *
     * @return quantas etapas perderam o vínculo
     * @throws DomainValidationException {@link ValidationProblem#VARIABLE_IN_USE} se há consumidores e não foi confirmado
     */
    public int removeVariable(VariableId id, boolean detachConsumers) {
        VariableDefinition current = requireVariable(id);
        List<StepId> consumers = consumersOfName(current.name());
        if (!consumers.isEmpty() && !detachConsumers) throw new DomainValidationException(ValidationProblem.VARIABLE_IN_USE);
        Instant now = clock.instant();
        for (StepId s : consumers) {
            AutomationStep st = requireStep(s);
            AutomationStep.State before = st.state();
            st.edit(new AutomationStep.State(before.name(), before.description(), before.actionType(), ActionParameters.none(), before.element(),
                    before.manualLocators(), before.comments(), before.edits(), before.lastManualChange()), now);
        }
        variables.remove(current);
        touch();
        return consumers.size();
    }

    /** Troca a referência das etapas para o novo nome (já conferido), validando todas antes de alterar qualquer uma. */
    private void retarget(List<StepId> consumers, String newName, Instant now) {
        ActionParameters target = ActionParameters.withValue(new ValueSource.VariableReference(newName));
        List<AutomationStep> steps = new ArrayList<>();
        for (StepId s : consumers) {
            AutomationStep st = requireStep(s);
            checkParameters(st.actionType(), target, st.element().orElse(null));
            steps.add(st);
        }
        for (AutomationStep st : steps) {
            AutomationStep.State before = st.state();
            st.edit(new AutomationStep.State(before.name(), before.description(), before.actionType(), target, before.element(), before.manualLocators(),
                    before.comments(), before.edits(), before.lastManualChange()), now);
        }
    }

    private VariableDraft checkVariable(VariableDraft draft, VariableId except) {
        Objects.requireNonNull(draft, "draft");
        VariableDraft d = VariableRules.check(draft);
        for (VariableDefinition v : variables) {
            if (!v.id().equals(except) && v.name().equalsIgnoreCase(d.name())) throw new DomainValidationException(ValidationProblem.VARIABLE_NAME_IN_USE);
        }
        VariableScope scope = d.scope();
        if (scope.kind() == VariableScope.Kind.SESSION && session(scope.session()).isEmpty()) {
            throw new DomainValidationException(ValidationProblem.VARIABLE_SCOPE_SESSION_NOT_FOUND);
        }
        if (d.origin().kind() == VariableOrigin.Kind.STEP_OUTPUT) {
            StepId producer = d.origin().producer();
            LogicalSession owner = sessionOf(producer).orElseThrow(() -> new DomainValidationException(ValidationProblem.VARIABLE_PRODUCER_NOT_FOUND));
            if (!scope.visibleIn(owner.id())) throw new DomainValidationException(ValidationProblem.VARIABLE_PRODUCER_OUT_OF_SCOPE);
        }
        return d;
    }

    private VariableDefinition requireVariable(VariableId id) {
        return variable(id).orElseThrow(() -> new DomainValidationException(ValidationProblem.VARIABLE_NOT_FOUND));
    }

    // ------------------------------------------------------------------ STUDIO-004: histórico em memória

    /**
     * Fotografia completa do estado editável (nome, descrição, sessões, ordem e estado efetivo de cada etapa) para o
     * histórico de desfazer/refazer em memória. Guarda as PRÓPRIAS sessões e etapas: restaurar devolve as mesmas
     * identidades, sem criar novas. Só contém estados que o domínio aceitou.
     */
    public Snapshot snapshot() {
        List<LogicalSession.State> sessionStates = new ArrayList<>(sessions.size());
        List<AutomationStep.State[]> stepStates = new ArrayList<>(sessions.size());
        for (LogicalSession s : sessions) {
            LogicalSession.State st = s.state();
            sessionStates.add(st);
            AutomationStep.State[] states = new AutomationStep.State[st.steps().size()];
            for (int i = 0; i < states.length; i++) states[i] = st.steps().get(i).state(); // compartilhado enquanto a etapa não muda
            stepStates.add(states);
        }
        return new Snapshot(this, revision, name, description, List.copyOf(sessions), sessionStates, stepStates, List.copyOf(variables));
    }

    /**
     * Restaura uma fotografia DESTA automação (desfazer/refazer). Sessões e etapas excluídas voltam com as mesmas
     * identidades. Conta uma revisão. A coerência com o estado atual (ex.: ninguém mais alterou a automação) é
     * responsabilidade de quem mantém o histórico — ver {@link Snapshot#revision()}.
     */
    public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.owner != this) throw new IllegalArgumentException("fotografia de outra automação");
        this.name = snapshot.name;
        this.description = snapshot.description;
        sessions.clear();
        sessions.addAll(snapshot.sessions);
        for (int i = 0; i < snapshot.sessions.size(); i++) {
            LogicalSession.State st = snapshot.sessionStates.get(i);
            snapshot.sessions.get(i).apply(st);
            AutomationStep.State[] states = snapshot.stepStates.get(i);
            for (int j = 0; j < states.length; j++) st.steps().get(j).apply(states[j]);
        }
        variables.clear();
        variables.addAll(snapshot.variables); // STUDIO-008: definições imutáveis, mesmas identidades
        touch();
    }

    /** Fotografia opaca de uma automação (somente em memória). */
    public static final class Snapshot {
        private final Automation owner;
        private final long revision;
        private final String name;
        private final String description;
        private final List<LogicalSession> sessions;
        private final List<LogicalSession.State> sessionStates;
        private final List<AutomationStep.State[]> stepStates;
        private final List<VariableDefinition> variables;

        private Snapshot(Automation owner, long revision, String name, String description, List<LogicalSession> sessions,
                List<LogicalSession.State> sessionStates, List<AutomationStep.State[]> stepStates, List<VariableDefinition> variables) {
            this.owner = owner;
            this.revision = revision;
            this.name = name;
            this.description = description;
            this.sessions = sessions;
            this.sessionStates = sessionStates;
            this.stepStates = stepStates;
            this.variables = variables;
        }

        /** Revisão da automação no instante da fotografia. */
        public long revision() {
            return revision;
        }

        /** Nome da automação na fotografia (o catálogo confere a unicidade antes de restaurar). */
        public String name() {
            return name;
        }

        /** A sessão existe nesta fotografia. */
        public boolean hasSession(SessionId id) {
            return sessions.stream().anyMatch(s -> s.id().equals(id));
        }

        /** A etapa existe nesta fotografia (em alguma sessão). */
        public boolean hasStep(StepId id) {
            return sessionStates.stream().anyMatch(s -> s.steps().stream().anyMatch(st -> st.id().equals(id)));
        }

        /** Sessão que contém a etapa nesta fotografia. */
        public Optional<SessionId> sessionOf(StepId id) {
            for (int i = 0; i < sessions.size(); i++) {
                if (sessionStates.get(i).steps().stream().anyMatch(st -> st.id().equals(id))) return Optional.of(sessions.get(i).id());
            }
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ regras

    private LogicalSession requireSession(SessionId sessionId) {
        return session(sessionId).orElseThrow(() -> new DomainValidationException(ValidationProblem.SESSION_NOT_FOUND));
    }

    private AutomationStep requireStep(StepId stepId) {
        return step(stepId).orElseThrow(() -> new DomainValidationException(ValidationProblem.STEP_NOT_FOUND));
    }

    /** Referência efetiva com o localizador preferencial pedido — validado como na captura. */
    private static ElementReference withPreferred(AutomationStep step, int index) {
        ElementReference captured = step.capturedElement().orElse(null);
        if (captured == null) {
            if (index != -1) throw new DomainValidationException(ValidationProblem.STEP_HAS_NO_ELEMENT);
            return null;
        }
        if (index < -1 || index >= captured.locators().size()) throw new DomainValidationException(ValidationProblem.LOCATOR_NOT_FOUND);
        if (index >= 0 && !captured.locators().get(index).validated()) throw new DomainValidationException(ValidationProblem.LOCATOR_NOT_VALIDATED);
        return captured.withPreferredLocator(index);
    }

    private void requireUniqueSessionName(String candidate, SessionId except) {
        for (LogicalSession s : sessions) {
            if (!s.id().equals(except) && Texts.sameName(s.name(), candidate)) {
                throw new DomainValidationException(ValidationProblem.SESSION_NAME_IN_USE);
            }
        }
    }

    static void checkParameters(ActionType type, ActionParameters params, ElementReference element) {
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
