package com.nekkora.studio.app.ui;

import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.studio.ui.form.FormSpec;
import java.util.Optional;
import java.util.function.Function;

/**
 * Interação modal com o usuário. A aplicação usa {@link SwingStudioPrompts} (diálogos reais do design
 * system); os testes usam uma implementação roteirizada com os <b>mesmos</b> formulários, sem janelas.
 */
public interface StudioPrompts {

    /**
     * Mostra o formulário; cada confirmação chama {@code submit}, que executa a operação de domínio.
     *
     * @return {@code true} se a operação foi concluída
     */
    <V> boolean showForm(FormSpec<V> spec, Function<V, Optional<ValidationProblem>> submit);

    /** Pergunta se o usuário quer fechar descartando as alterações em memória. */
    boolean confirmExit(String message);

    /**
     * STUDIO-004: confirmação explícita de uma operação (ex.: excluir, remover parâmetros incompatíveis).
     *
     * @return {@code true} se o usuário confirmou
     */
    boolean confirm(Confirmation confirmation);

    /** Pergunta de confirmação: título, explicação, rótulo do botão e se a operação é destrutiva. */
    record Confirmation(String title, String message, String confirmLabel, boolean danger) {}
}
