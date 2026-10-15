package com.nekkora.studio.app.workspace;

import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.SessionId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Notificação de alteração do estado.
 *
 * @param changes    o que mudou (natureza da alteração)
 * @param automation automação afetada, quando houver
 * @param session    sessão lógica afetada, quando a alteração se limita a uma sessão (STUDIO-002: permite atualizar
 *                   só a parte da tela correspondente, sem percorrer todas as sessões)
 */
public record StudioEvent(Set<StudioChange> changes, AutomationId automation, SessionId session) {
    public StudioEvent {
        changes = Collections.unmodifiableSet(changes.isEmpty() ? EnumSet.noneOf(StudioChange.class) : EnumSet.copyOf(changes));
    }

    public StudioEvent(Set<StudioChange> changes, AutomationId automation) {
        this(changes, automation, null);
    }

    public boolean has(StudioChange change) {
        return changes.contains(change);
    }
}
