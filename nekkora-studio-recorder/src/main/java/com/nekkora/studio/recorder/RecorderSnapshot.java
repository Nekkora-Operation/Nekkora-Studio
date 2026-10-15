package com.nekkora.studio.recorder;

import com.nekkora.studio.bridge.LinkStatus;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.SessionId;

/**
 * Fotografia imutável do Recorder para a interface. Sem identificadores técnicos da gravação ou do navegador: a
 * automação e a sessão vêm como ids de domínio (a interface resolve os nomes), a aba como título/host.
 *
 * @param state       estado confirmado (ou aguardando confirmação)
 * @param mode        modo confirmado pela extensão (null antes do início)
 * @param pendingMode modo pedido e ainda não confirmado (ou null)
 * @param automation  automação da gravação (ou null)
 * @param session     sessão lógica associada (ou null)
 * @param tabTitle    título da aba (mascarado) ou host
 * @param tabHost     host da aba
 * @param progress    estado técnico da captura
 * @param reason      motivo técnico do estado atual (ex.: TAB_CLOSED, LINK_LOST), ou null
 * @param problem     problema a explicar ao usuário, ou null
 * @param summary     resumo do desfecho (COMPLETED/CANCELLED), ou null
 * @param link        estado da ligação com o navegador
 * @param steps       entrega das etapas gravadas (STUDIO-003)
 * @param pendingSession sessão pedida e ainda não confirmada pela extensão (ou null); {@code session} é a confirmada
 */
public record RecorderSnapshot(RecorderState state, RecordingMode mode, RecordingMode pendingMode, AutomationId automation, SessionId session,
        String tabTitle, String tabHost, RecordingProgress progress, String reason, UserProblem problem, RecordingSummary summary, LinkStatus link,
        StepProgress steps, SessionId pendingSession) {

    public RecorderSnapshot {
        steps = steps == null ? StepProgress.NONE : steps;
    }

    /** A perda foi da ligação com o navegador (e não da página). */
    public boolean linkLost() {
        return state == RecorderState.DEGRADED && "LINK_LOST".equals(reason);
    }
}
