package com.nekkora.studio.app.recorder;

import com.nekkora.studio.app.workspace.WorkspaceService;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.DomainValidationException;
import com.nekkora.studio.domain.evidence.ObservedEvidence;
import com.nekkora.studio.recorder.EvidenceSink;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

/**
 * Destino das evidências observadas no Studio (STUDIO-005): a {@link com.nekkora.studio.domain.port.EvidenceIntake} do
 * {@link WorkspaceService}, chamada NA EDT. O futuro completa só depois da operação de domínio — é ele que autoriza o
 * Recorder a confirmar a evidência à extensão. Uma recusa (etapa inexistente, ação não executada, limite) vira
 * {@code false}; uma repetida conta como registrada (idempotente).
 */
public final class WorkspaceEvidenceSink implements EvidenceSink {

    private static final Logger LOG = Logger.getLogger(WorkspaceEvidenceSink.class.getName());
    private final WorkspaceService service;

    public WorkspaceEvidenceSink(WorkspaceService service) {
        this.service = service;
    }

    @Override
    public CompletableFuture<Boolean> deliver(AutomationId automation, ObservedEvidence evidence) {
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> {
            try {
                service.attachEvidence(automation, evidence);
                done.complete(true);
            } catch (DomainValidationException e) {
                LOG.warning("Evidência recusada pelo domínio: " + e.problem());
                done.complete(false);
            } catch (RuntimeException e) {
                LOG.warning("Evidência não registrada: " + e.getClass().getSimpleName());
                done.complete(false);
            }
        });
        return done;
    }
}
