package com.nekkora.studio.domain.variable;

import com.nekkora.studio.domain.SessionId;
import java.util.Objects;
import java.util.Optional;

/**
 * Visibilidade de uma variável (STUDIO-008): a automação inteira, ou SÓ uma sessão lógica. Não existe escopo global da
 * máquina ou da plataforma. O escopo determina visibilidade, não permite nomes repetidos (o nome é único na automação).
 *
 * @param kind    tipo de escopo
 * @param session sessão (só em {@link Kind#SESSION})
 */
public record VariableScope(Kind kind, SessionId session) {
    public enum Kind {
        AUTOMATION("Automação"), SESSION("Sessão");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public VariableScope {
        Objects.requireNonNull(kind, "kind");
        if ((kind == Kind.SESSION) != (session != null)) throw new IllegalArgumentException("escopo de sessão exige a sessão (e só ele)");
    }

    public static VariableScope automation() {
        return new VariableScope(Kind.AUTOMATION, null);
    }

    public static VariableScope session(SessionId session) {
        return new VariableScope(Kind.SESSION, Objects.requireNonNull(session, "session"));
    }

    public Optional<SessionId> sessionId() {
        return Optional.ofNullable(session);
    }

    /** A variável é visível numa etapa desta sessão. */
    public boolean visibleIn(SessionId stepSession) {
        return kind == Kind.AUTOMATION || session.equals(stepSession);
    }
}
