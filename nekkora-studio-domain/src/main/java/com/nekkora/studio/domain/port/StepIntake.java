package com.nekkora.studio.domain.port;

import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepDraft;

/**
 * Ponto de entrada de etapas no Studio, independente da interface. A aplicação implementa esta porta
 * sobre {@link com.nekkora.studio.domain.Automation#appendStep}; o Recorder (STUDIO-002) a receberá por
 * injeção e entregará etapas {@code RECORDED} sem simular cliques na tela.
 *
 * <p>Implementações devem ser chamadas na thread de interface (EDT) ou fazer a transferência para ela —
 * o estado da aplicação é observado pelos componentes Swing.
 */
public interface StepIntake {

    /** Acrescenta a etapa ao final da sessão e devolve a etapa criada. */
    AutomationStep appendStep(AutomationId automation, SessionId session, StepDraft draft);
}
