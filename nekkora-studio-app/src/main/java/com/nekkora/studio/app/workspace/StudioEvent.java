package com.nekkora.studio.app.workspace;

import com.nekkora.studio.domain.AutomationId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Notificação de alteração do estado.
 *
 * @param changes    o que mudou
 * @param automation automação afetada, quando houver
 */
public record StudioEvent(Set<StudioChange> changes, AutomationId automation) {
    public StudioEvent {
        changes = Collections.unmodifiableSet(changes.isEmpty() ? EnumSet.noneOf(StudioChange.class) : EnumSet.copyOf(changes));
    }

    public boolean has(StudioChange change) {
        return changes.contains(change);
    }
}
