package com.nekkora.ui.state;

import com.nekkora.ui.core.Tone;
import com.nekkora.ui.icon.IconName;

/** Sem permissão para ver/editar a área. */
public class NekkoraPermissionState extends NekkoraEmptyState {

    public NekkoraPermissionState(String description) {
        super(IconName.LOCK, Tone.WARNING, "Acesso restrito",
                description == null ? "Você não tem permissão para acessar esta área. Fale com o administrador." : description);
    }

    public NekkoraPermissionState() {
        this(null);
    }
}
