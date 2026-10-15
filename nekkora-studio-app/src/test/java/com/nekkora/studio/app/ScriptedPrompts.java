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
    private final Deque<Boolean> exitAnswers = new ArrayDeque<>();
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

    public ScriptedPrompts thenExit(boolean discard) {
        exitAnswers.add(discard);
        return this;
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

    @Override
    public boolean confirmExit(String message) {
        exitMessages.add(message);
        Boolean b = exitAnswers.poll();
        if (b == null) throw new AssertionError("Aviso de saída inesperado");
        return b;
    }
}
