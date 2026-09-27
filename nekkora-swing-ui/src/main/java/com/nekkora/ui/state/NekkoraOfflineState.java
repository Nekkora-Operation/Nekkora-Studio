package com.nekkora.ui.state;

import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.icon.IconName;

/** Sem conexão: informa e permite tentar de novo. */
public class NekkoraOfflineState extends NekkoraEmptyState {

    public NekkoraOfflineState(Runnable onRetry) {
        super(IconName.WIFI_OFF, Tone.NEUTRAL, "Você está offline",
                "Algumas informações podem estar desatualizadas. Reconecte para continuar.");
        if (onRetry != null) addAction(NekkoraButton.secondary("Tentar reconectar").withIcon(IconName.REFRESH).onClick(e -> onRetry.run()));
    }
}
