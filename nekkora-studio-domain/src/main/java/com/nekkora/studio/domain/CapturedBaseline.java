package com.nekkora.studio.domain;

import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import java.util.Objects;

/**
 * Valores de uma etapa GRAVADA no instante em que ela entrou no modelo (STUDIO-004, D-S004-05). Imutável: nenhuma
 * edição o altera. Junto com o {@link com.nekkora.studio.domain.element.ElementReference} original e a
 * {@link com.nekkora.studio.domain.origin.RecordingObservation} da procedência, forma a observação original.
 *
 * <p>Só guarda o que o Recorder entregou, já sanitizado na origem: nenhum valor digitado existe aqui (a gravação nunca
 * transmite valores), então restaurar o original nunca traz de volta um segredo.
 *
 * @param name             nome gerado pela gravação
 * @param description      descrição gerada pela gravação
 * @param actionType       tipo de ação observado
 * @param parameters       parâmetros observados (ex.: opção escolhida; nunca um valor digitado)
 * @param preferredLocator índice do localizador preferencial da captura, ou -1
 */
public record CapturedBaseline(String name, String description, ActionType actionType, ActionParameters parameters, int preferredLocator) {
    public CapturedBaseline {
        Objects.requireNonNull(name, "name");
        description = description == null ? "" : description;
        Objects.requireNonNull(actionType, "actionType");
        parameters = parameters == null ? ActionParameters.none() : parameters;
    }
}
