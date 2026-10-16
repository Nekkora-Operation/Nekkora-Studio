package com.nekkora.studio.app;

import com.nekkora.studio.app.ui.StudioPrompts;
import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.ui.form.FormSpec;
import com.nekkora.studio.ui.form.NameDescriptionForm;
import com.nekkora.studio.ui.form.StepForm;
import com.nekkora.studio.ui.form.StudioDialogs;
import com.nekkora.studio.ui.form.StudioForm;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Usuário roteirizado: recebe os <b>mesmos</b> formulários dos diálogos reais, preenche os campos e confirma
 * pelo mesmo caminho ({@link StudioDialogs#trySubmit}) — só não abre janelas (testes headless).
 */
public final class ScriptedPrompts implements StudioPrompts {
    private record Answer(Consumer<StudioForm<?>> fill, boolean cancel) {}

    private final Deque<Answer> answers = new ArrayDeque<>();
    /** STUDIO-006: respostas sobre alterações não salvas (fechar projeto / sair), na ordem. */
    private final Deque<UnsavedChoice> unsavedAnswers = new ArrayDeque<>();
    private final Deque<Optional<java.nio.file.Path>> openAnswers = new ArrayDeque<>();
    private final Deque<Optional<java.nio.file.Path>> saveAnswers = new ArrayDeque<>();
    private final Deque<ConflictChoice> conflictAnswers = new ArrayDeque<>();
    private final Deque<RecoveryChoice> recoveryAnswers = new ArrayDeque<>();
    private final Deque<Optional<RecoveryPreferences>> recoverySettingsAnswers = new ArrayDeque<>();
    /** Perguntas feitas (para as asserções). */
    public final List<List<String>> unsavedAsked = new ArrayList<>();
    public final List<String> savePromptsSuggested = new ArrayList<>();
    public final List<String> conflictsAsked = new ArrayList<>();
    public final List<RecoveryOffer> recoveryOffers = new ArrayList<>();
    public final List<String> recoveryExplanations = new ArrayList<>();
    public final List<FormSpec<?>> shown = new ArrayList<>();
    public final List<Boolean> results = new ArrayList<>();
    public final List<String> exitMessages = new ArrayList<>();
    private final Deque<Boolean> confirmAnswers = new ArrayDeque<>();
    /** Confirmações pedidas (STUDIO-004: excluir, remover parâmetros incompatíveis). */
    public final List<Confirmation> confirmations = new ArrayList<>();

    /** Próximo diálogo: preenche e confirma. */
    public ScriptedPrompts then(Consumer<StudioForm<?>> fill) {
        answers.add(new Answer(fill, false));
        return this;
    }

    /** Próximo diálogo: cancela (Esc). */
    public ScriptedPrompts thenCancel() {
        answers.add(new Answer(f -> { }, true));
        return this;
    }

    /** Próxima confirmação: aceita ({@code true}) ou recusa. */
    public ScriptedPrompts thenConfirm(boolean accept) {
        confirmAnswers.add(accept);
        return this;
    }

    @Override
    public boolean confirm(Confirmation confirmation) {
        confirmations.add(confirmation);
        Boolean b = confirmAnswers.poll();
        if (b == null) throw new AssertionError("Confirmação inesperada: " + confirmation.title());
        return b;
    }

    /** Aviso de saída com alterações: descartar ({@code true}) ou cancelar (compatível com a STUDIO-001/002). */
    public ScriptedPrompts thenExit(boolean discard) {
        unsavedAnswers.add(discard ? UnsavedChoice.DISCARD : UnsavedChoice.CANCEL);
        return this;
    }

    /** STUDIO-006: próxima decisão sobre alterações não salvas (fechar projeto ou sair). */
    public ScriptedPrompts thenUnsaved(UnsavedChoice choice) {
        unsavedAnswers.add(choice);
        return this;
    }

    public ScriptedPrompts thenOpen(java.nio.file.Path path) {
        openAnswers.add(Optional.ofNullable(path));
        return this;
    }

    public ScriptedPrompts thenSaveAs(java.nio.file.Path path) {
        saveAnswers.add(Optional.ofNullable(path));
        return this;
    }

    public ScriptedPrompts thenConflict(ConflictChoice choice) {
        conflictAnswers.add(choice);
        return this;
    }

    public ScriptedPrompts thenRecovery(RecoveryChoice choice) {
        recoveryAnswers.add(choice);
        return this;
    }

    public ScriptedPrompts thenRecoverySettings(RecoveryPreferences prefs) {
        recoverySettingsAnswers.add(Optional.ofNullable(prefs));
        return this;
    }

    @Override
    public Optional<java.nio.file.Path> chooseProjectToOpen(java.nio.file.Path dir) {
        Optional<java.nio.file.Path> p = openAnswers.poll();
        if (p == null) throw new AssertionError("Escolha de arquivo para abrir inesperada");
        return p;
    }

    @Override
    public Optional<java.nio.file.Path> chooseSaveLocation(String suggestedName, java.nio.file.Path dir) {
        savePromptsSuggested.add(suggestedName);
        Optional<java.nio.file.Path> p = saveAnswers.poll();
        if (p == null) throw new AssertionError("Escolha de destino inesperada (sugerido: " + suggestedName + ")");
        return p;
    }

    @Override
    public UnsavedChoice confirmUnsaved(List<String> projects, boolean exit) {
        unsavedAsked.add(List.copyOf(projects));
        if (exit) exitMessages.add(String.join(", ", projects));
        UnsavedChoice c = unsavedAnswers.poll();
        if (c == null) throw new AssertionError((exit ? "Aviso de saída" : "Aviso de fechamento") + " inesperado: " + projects);
        return c;
    }

    @Override
    public ConflictChoice resolveConflict(String project) {
        conflictsAsked.add(project);
        ConflictChoice c = conflictAnswers.poll();
        if (c == null) throw new AssertionError("Conflito inesperado: " + project);
        return c;
    }

    @Override
    public RecoveryChoice offerRecovery(RecoveryOffer offer) {
        recoveryOffers.add(offer);
        RecoveryChoice c = recoveryAnswers.poll();
        if (c == null) throw new AssertionError("Oferta de recuperação inesperada: " + offer.project());
        return c;
    }

    @Override
    public Optional<RecoveryPreferences> editRecovery(RecoveryPreferences current, List<Integer> intervals, String explanation) {
        recoveryExplanations.add(explanation);
        Optional<RecoveryPreferences> p = recoverySettingsAnswers.poll();
        if (p == null) throw new AssertionError("Diálogo de recuperação inesperado");
        return p;
    }

    public static Consumer<StudioForm<?>> name(String name, String description) {
        return f -> {
            NameDescriptionForm n = (NameDescriptionForm) f;
            n.nameInput().setText(name);
            if (n.descriptionInput() != null) n.descriptionInput().setText(description == null ? "" : description);
        };
    }

    /** Preenche o formulário de etapa (sessão pelo nome; nula mantém a sugerida). */
    public static Consumer<StudioForm<?>> step(String session, String name, ActionType type) {
        return f -> {
            StepForm s = (StepForm) f;
            if (session != null && s.sessionInput() != null) {
                for (int i = 0; i < s.sessionInput().getItemCount(); i++) {
                    if (s.sessionInput().getItemAt(i).label().endsWith(" · " + session)) s.sessionInput().setSelectedIndex(i);
                }
            }
            s.nameInput().setText(name);
            s.typeInput().setSelectedItem(type);
        };
    }

    public boolean pending() {
        return !answers.isEmpty();
    }

    public FormSpec<?> last() {
        return shown.get(shown.size() - 1);
    }

    @Override
    public <V> boolean showForm(FormSpec<V> spec, Function<V, Optional<ValidationProblem>> submit) {
        shown.add(spec);
        Answer a = answers.poll();
        if (a == null) throw new AssertionError("Diálogo inesperado: " + spec.title());
        boolean ok = !a.cancel() && confirm(spec, a, submit);
        results.add(ok);
        return ok;
    }

    private static <V> boolean confirm(FormSpec<V> spec, Answer a, Function<V, Optional<ValidationProblem>> submit) {
        a.fill().accept(spec.form());
        return StudioDialogs.trySubmit(spec.form(), submit);
    }

}
