package com.nekkora.studio.recorder;

import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.SessionId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Gravação TÉCNICA (D-STUDIO-002-11): pertence a uma automação e está associada a uma sessão lógica de cada vez.
 * Identidade própria ({@code recordingId}, técnico e nunca exibido), distinta dos ids da automação e das sessões. A
 * associação pode mudar sem nova captura; o histórico fica registrado para a STUDIO-003 direcionar as etapas.
 */
public final class TechnicalRecording {

    /** Associação a uma sessão lógica a partir de um instante. */
    public record Association(SessionId session, Instant since) {
    }

    private final String recordingId;
    private final AutomationId automation;
    private final List<Association> associations = new ArrayList<>();

    TechnicalRecording(String recordingId, AutomationId automation, SessionId session, Instant at) {
        this.recordingId = Objects.requireNonNull(recordingId);
        this.automation = Objects.requireNonNull(automation);
        associations.add(new Association(Objects.requireNonNull(session), at));
    }

    public String recordingId() {
        return recordingId;
    }

    public AutomationId automation() {
        return automation;
    }

    public SessionId session() {
        return associations.get(associations.size() - 1).session();
    }

    public List<Association> associations() {
        return List.copyOf(associations);
    }

    boolean associate(SessionId session, Instant at) {
        if (session().equals(session)) {
            return false;
        }
        associations.add(new Association(session, at));
        return true;
    }
}
