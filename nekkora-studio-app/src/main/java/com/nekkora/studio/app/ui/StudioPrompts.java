package com.nekkora.studio.app.ui;

import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.studio.ui.form.FormSpec;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
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

    /**
     * STUDIO-004: confirmação explícita de uma operação (ex.: excluir, remover parâmetros incompatíveis).
     *
     * @return {@code true} se o usuário confirmou
     */
    boolean confirm(Confirmation confirmation);

    /** Pergunta de confirmação: título, explicação, rótulo do botão e se a operação é destrutiva. */
    record Confirmation(String title, String message, String confirmLabel, boolean danger) {}

    // ------------------------------------------------------------------ STUDIO-006: projetos

    /** Escolha do arquivo {@code .nekkora} a abrir ({@code dir} sugerido, pode ser nulo). */
    Optional<Path> chooseProjectToOpen(Path dir);

    /** Escolha do destino de "Salvar como" (nome sugerido sem caminho; {@code dir} sugerido, pode ser nulo). */
    Optional<Path> chooseSaveLocation(String suggestedName, Path dir);

    /** Decisão sobre alterações não salvas (fechar projeto ou encerrar o Studio). */
    enum UnsavedChoice { SAVE, DISCARD, CANCEL }

    /**
     * @param projects nomes dos projetos com alterações não salvas
     * @param exit     é o encerramento do Studio (salvar = salvar todos)
     */
    UnsavedChoice confirmUnsaved(List<String> projects, boolean exit);

    /** Decisão quando o arquivo foi alterado fora do Studio (nunca há mescla automática). */
    enum ConflictChoice { SAVE_AS, RELOAD, CANCEL }

    ConflictChoice resolveConflict(String project);

    /** Decisão sobre uma fotografia de recuperação encontrada ao abrir o Studio. */
    enum RecoveryChoice { RECOVER, IGNORE, CANCEL }

    /**
     * @param project nome da automação
     * @param at      instante da fotografia
     * @param origin  nome do arquivo do projeto do qual derivam as alterações (ou nulo se nunca foi salvo)
     */
    record RecoveryOffer(String project, Instant at, String origin) {}

    RecoveryChoice offerRecovery(RecoveryOffer offer);

    /** Configuração da recuperação automática (consentimento informado). */
    record RecoveryPreferences(boolean enabled, int intervalSeconds, boolean clear) {}

    /**
     * @param explanation o que é guardado, onde, por quanto tempo e o que NÃO é (sem criptografia, sem segredos)
     */
    Optional<RecoveryPreferences> editRecovery(RecoveryPreferences current, List<Integer> intervals, String explanation);
}
