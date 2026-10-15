package com.nekkora.studio.app.recorder;

import com.nekkora.studio.app.workspace.WorkspaceService;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.DomainValidationException;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.recorder.StepSink;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

/**
 * Destino das etapas gravadas no Studio (STUDIO-003): a {@link com.nekkora.studio.domain.port.StepIntake} do
 * {@link WorkspaceService}, chamada NA EDT (o estado é observado pelos componentes Swing). Nenhum clique é simulado e
 * nenhum componente é tocado diretamente: a tela se atualiza pelo evento de alteração da sessão afetada.
 *
 * <p>O futuro completa só depois da operação de domínio: é ele que autoriza o Recorder a confirmar a etapa à
 * extensão. Uma recusa de validação vira {@code false} (a etapa não conta como registrada).
 */
public final class WorkspaceStepSink implements StepSink {

    private static final Logger LOG = Logger.getLogger(WorkspaceStepSink.class.getName());
    private final WorkspaceService service;

    public WorkspaceStepSink(WorkspaceService service) {
        this.service = service;
    }

    @Override
    public CompletableFuture<Boolean> deliver(AutomationId automation, SessionId session, StepDraft draft) {
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> {
            try {
                service.appendStep(automation, session, draft);
                done.complete(true);
            } catch (DomainValidationException e) {
                LOG.warning("Etapa da gravação recusada pelo domínio: " + e.problem());
                done.complete(false);
            } catch (RuntimeException e) {
                LOG.warning("Etapa da gravação não registrada: " + e.getClass().getSimpleName());
                done.complete(false);
            }
        });
        return done;
    }

    @Override
    public CompletableFuture<Integer> discardRecording(AutomationId automation, String recordingId) {
        CompletableFuture<Integer> done = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> {
            try {
                done.complete(service.discardRecording(automation, recordingId));
            } catch (RuntimeException e) {
                LOG.warning("Descarte da gravação cancelada falhou: " + e.getClass().getSimpleName());
                done.complete(0);
            }
        });
        return done;
    }
}
