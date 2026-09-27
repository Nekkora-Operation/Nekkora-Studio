package com.nekkora.ui.data;

import com.nekkora.ui.core.Status;

/**
 * Badge de status (ícone + rótulo + cor — nunca só cor). {@link Status#RUNNING} gira o ícone.
 *
 * <pre>{@code
 * new NekkoraStatusBadge(Status.SUCCESS);                 // "Sucesso"
 * new NekkoraStatusBadge(Status.FAILED, "Erro na etapa 3");
 * }</pre>
 */
public class NekkoraStatusBadge extends NekkoraBadge {
    private Status status;

    public NekkoraStatusBadge(Status status) {
        this(status, status.label());
    }

    public NekkoraStatusBadge(Status status, String label) {
        super(label, status.tone());
        setStatus(status, label);
    }

    public static NekkoraStatusBadge of(Status status) {
        return new NekkoraStatusBadge(status);
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        setStatus(status, status.label());
    }

    public void setStatus(Status status, String label) {
        this.status = status;
        setTone(status.tone());
        setIconInternal(status.icon());
        setSpinning(status.isActive());
        setText(label);
        getAccessibleContext().setAccessibleDescription("status: " + status.label());
    }
}
