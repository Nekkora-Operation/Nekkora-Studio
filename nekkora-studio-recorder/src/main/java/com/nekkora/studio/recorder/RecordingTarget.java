package com.nekkora.studio.recorder;

import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.SessionId;
import java.util.Objects;

/** Destino lógico da gravação: a sessão lógica de uma automação. */
public record RecordingTarget(AutomationId automation, SessionId session) {
    public RecordingTarget {
        Objects.requireNonNull(automation, "automation");
        Objects.requireNonNull(session, "session");
    }
}
