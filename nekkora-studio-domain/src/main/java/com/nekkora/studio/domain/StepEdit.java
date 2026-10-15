package com.nekkora.studio.domain;

import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.element.ManualLocator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Edição completa de uma etapa existente (STUDIO-004): o estado efetivo desejado, aplicado de uma vez por
 * {@link Automation#editStep(StepId, StepEdit)} — que valida TUDO antes de alterar e conta uma única revisão. A
 * identidade, a procedência e o valor capturado não fazem parte da edição e nunca mudam por ela.
 *
 * <p>Comece de {@link #of(AutomationStep)} (valores atuais) e altere só o que o usuário mudou.
 */
public final class StepEdit {

    /**
     * Comentário na edição: {@code original} é a posição (0..n-1) de um comentário existente — a data de criação dele é
     * preservada — ou -1 para um comentário novo.
     */
    public record CommentEdit(int original, String text) {
        public CommentEdit {
            Objects.requireNonNull(text, "text");
            if (original < -1) throw new IllegalArgumentException("original < -1");
        }

        public static CommentEdit added(String text) {
            return new CommentEdit(-1, text);
        }
    }

    private String name;
    private String description;
    private ActionType actionType;
    private ActionParameters parameters;
    private SessionId session;
    private int preferredLocator;
    private final List<ManualLocator> manualLocators;
    private final List<CommentEdit> comments;

    private StepEdit(AutomationStep step, SessionId session) {
        this.name = step.name();
        this.description = step.description();
        this.actionType = step.actionType();
        this.parameters = step.parameters();
        this.session = session;
        this.preferredLocator = step.element().map(e -> e.preferredLocatorIndex()).orElse(-1);
        this.manualLocators = new ArrayList<>(step.manualLocators());
        this.comments = new ArrayList<>();
        for (int i = 0; i < step.comments().size(); i++) comments.add(new CommentEdit(i, step.comments().get(i).text()));
    }

    /** Edição que parte do estado atual da etapa (na sessão em que ela está). */
    public static StepEdit of(Automation automation, AutomationStep step) {
        SessionId session = automation.sessionOf(step.id()).map(LogicalSession::id)
                .orElseThrow(() -> new DomainValidationException(ValidationProblem.STEP_NOT_FOUND));
        return new StepEdit(step, session);
    }

    public StepEdit name(String v) {
        name = v;
        return this;
    }

    public StepEdit description(String v) {
        description = v;
        return this;
    }

    public StepEdit actionType(ActionType v) {
        actionType = v;
        return this;
    }

    public StepEdit parameters(ActionParameters v) {
        parameters = v == null ? ActionParameters.none() : v;
        return this;
    }

    /** Sessão de destino: diferente da atual, a etapa vai para o final dela (mesma identidade). */
    public StepEdit session(SessionId v) {
        session = v;
        return this;
    }

    /** Índice (na lista capturada) do localizador preferencial; -1 = nenhum. */
    public StepEdit preferredLocator(int index) {
        preferredLocator = index;
        return this;
    }

    public StepEdit manualLocators(List<ManualLocator> v) {
        manualLocators.clear();
        manualLocators.addAll(v);
        return this;
    }

    public StepEdit comments(List<CommentEdit> v) {
        comments.clear();
        comments.addAll(v);
        return this;
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

    public ActionParameters parameters() {
        return parameters;
    }

    public SessionId session() {
        return session;
    }

    public int preferredLocator() {
        return preferredLocator;
    }

    public List<ManualLocator> manualLocators() {
        return List.copyOf(manualLocators);
    }

    public List<CommentEdit> comments() {
        return List.copyOf(comments);
    }
}
