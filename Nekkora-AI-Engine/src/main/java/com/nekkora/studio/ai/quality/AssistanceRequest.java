package com.nekkora.studio.ai.quality;

import com.nekkora.studio.ai.spec.FunctionalSpec;
import java.util.List;
import java.util.Objects;

/**
 * STUDIO-014 — pedido de ajuda ESPECÍFICO: uma pergunta curta, por que o Studio não pode decidir sozinho, opções
 * fundamentadas e o DESTINO da resposta (o campo da especificação funcional que ela preenche). Nada de formulário
 * genérico, nada de valor sensível. Respondido, a especificação ganha a decisão com o ator, os gates afetados deixam de
 * valer e a análise recomeça só no que mudou.
 *
 * @param destination campo da especificação que recebe a resposta (ou {@code null} quando a resposta é uma ação no
 *                    Studio — ex.: abrir o PDD, depurar no navegador)
 */
public record AssistanceRequest(String question, String why, List<Option> options, FunctionalSpec.Field destination, String destinationLabel) {

    /**
     * Uma opção. {@code value} é o que vai para a especificação (vocabulário fechado); {@code basis} explica de onde a
     * opção vem; {@code studioAction} é uma ação do Studio em vez de resposta (ex.: {@code OPEN_PDD}).
     */
    public record Option(String id, String label, String basis, String value, String studioAction) {
        public Option {
            Objects.requireNonNull(id);
            Objects.requireNonNull(label);
            basis = basis == null ? "" : basis;
        }

        public static Option answer(String value, String label, String basis) {
            return new Option(value, label, basis, value, null);
        }

        public static Option action(String action, String label, String basis) {
            return new Option(action, label, basis, null, action);
        }

        public boolean isAnswer() {
            return value != null;
        }
    }

    public AssistanceRequest {
        Objects.requireNonNull(question);
        why = why == null ? "" : why;
        options = List.copyOf(options);
        destinationLabel = destinationLabel == null ? "" : destinationLabel;
        if (options.isEmpty()) throw new IllegalArgumentException("pedido de ajuda sem opção");
    }
}
