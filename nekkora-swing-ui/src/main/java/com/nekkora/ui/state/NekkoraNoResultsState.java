package com.nekkora.ui.state;

import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.icon.IconName;

/** Busca/filtro sem resultados: mostra o termo e oferece limpar. */
public class NekkoraNoResultsState extends NekkoraEmptyState {

    public NekkoraNoResultsState(String query, Runnable onClear) {
        super(IconName.SEARCH, Tone.NEUTRAL, "Nenhum resultado" + (query == null || query.isBlank() ? "" : " para \"" + query + "\""),
                "Tente outros termos ou remova filtros.");
        if (onClear != null) addAction(NekkoraButton.secondary("Limpar busca").onClick(e -> onClear.run()));
    }
}
