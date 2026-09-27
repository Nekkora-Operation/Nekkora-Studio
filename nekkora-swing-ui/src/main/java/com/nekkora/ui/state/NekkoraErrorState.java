package com.nekkora.ui.state;

import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.icon.IconName;

/** Algo deu errado ao carregar: explica e oferece "Tentar novamente". */
public class NekkoraErrorState extends NekkoraEmptyState {

    public NekkoraErrorState(String title, String description, Runnable onRetry) {
        super(IconName.X_CIRCLE, Tone.ERROR, title, description);
        if (onRetry != null) {
            addAction(NekkoraButton.secondary("Tentar novamente").withIcon(IconName.REFRESH).onClick(e -> onRetry.run()));
        }
    }

    public NekkoraErrorState(Runnable onRetry) {
        this("Não foi possível carregar", "Verifique sua conexão e tente novamente.", onRetry);
    }
}
