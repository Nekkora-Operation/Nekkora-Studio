package com.nekkora.studio.app.workspace;

import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.SessionId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Notificação de alteração do estado.
 *
 * @param changes    o que mudou (natureza da alteração)
 * @param automation automação afetada, quando houver
 * @param sessions   sessões lógicas afetadas, quando a alteração se limita a elas (STUDIO-002: uma; STUDIO-004: origem e
 *                   destino de uma movimentação). Vazio = a estrutura pode ter mudado (ordem, exclusão): reconciliar tudo.
 */
public record StudioEvent(Set<StudioChange> changes, AutomationId automation, Set<SessionId> sessions) {
    public StudioEvent {
        changes = Collections.unmodifiableSet(changes.isEmpty() ? EnumSet.noneOf(StudioChange.class) : EnumSet.copyOf(changes));
        sessions = sessions == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(sessions));
    }

    public StudioEvent(Set<StudioChange> changes, AutomationId automation) {
        this(changes, automation, Set.of());
    }

    public boolean has(StudioChange change) {
        return changes.contains(change);
    }

    /** Sessão afetada quando a alteração se limita a UMA sessão (compatível com a STUDIO-002/003); senão {@code null}. */
    public SessionId session() {
        return sessions.size() == 1 ? sessions.iterator().next() : null;
    }
}
