package com.nekkora.ui.state;

import com.nekkora.ui.core.Tone;
import com.nekkora.ui.feedback.NekkoraSpinner;
import com.nekkora.ui.icon.IconName;

/** Carregando uma área inteira: spinner + mensagem curta. */
public class NekkoraLoadingState extends NekkoraEmptyState {

    public NekkoraLoadingState(String message) {
        super(IconName.LOADER, Tone.ACCENT, message, null);
        replaceGlyph(new NekkoraSpinner(NekkoraSpinner.Size.LARGE));
    }

    public NekkoraLoadingState() {
        this("Carregando…");
    }
}
