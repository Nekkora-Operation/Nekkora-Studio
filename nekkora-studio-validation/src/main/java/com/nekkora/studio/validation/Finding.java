package com.nekkora.studio.validation;

import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.variable.VariableId;
import java.util.Objects;
import java.util.Optional;

/**
 * Um apontamento da validação. Imutável e sem dados do domínio além das IDENTIDADES (automação, sessão, etapa) e das
 * posições derivadas: nenhum nome, valor, localizador ou texto da página.
 *
 * @param id            identificador ESTÁVEL: {@code código:identidade[:detalhe]} — o mesmo estado produz o mesmo id
 * @param code          o que foi encontrado (fixa severidade, campo, textos)
 * @param scope         automação, sessão ou etapa
 * @param session       sessão (escopos SESSION e STEP)
 * @param step          etapa (escopo STEP)
 * @param sessionNumber posição da sessão, a partir de 1 (0 no escopo AUTOMATION)
 * @param stepNumber    posição da etapa DENTRO da sessão, a partir de 1 (0 fora do escopo STEP)
 * @param detail        detalhe do vocabulário fechado ({@link FindingDetails}), ou {@code null}
 * @param variable      (regras 1.1.0) a variável declarada envolvida — obrigatória no escopo VARIABLE; numa etapa, a
 *                      variável declarada que ela usa (nula se o nome não está declarado). Só a identidade, nunca o nome.
 */
public record Finding(String id, FindingCode code, FindingScope scope, SessionId session, StepId step, int sessionNumber, int stepNumber,
                      String detail, VariableId variable) {

    public Finding {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(scope, "scope");
        if ((scope == FindingScope.SESSION || scope == FindingScope.STEP) && session == null) throw new IllegalArgumentException("sessão obrigatória");
        if (scope == FindingScope.STEP && step == null) throw new IllegalArgumentException("etapa obrigatória");
        if (scope == FindingScope.VARIABLE && (variable == null || step != null)) throw new IllegalArgumentException("variável obrigatória");
        if (detail != null && !FindingDetails.all().contains(detail)) throw new IllegalArgumentException("detalhe fora do vocabulário");
    }

    /** Apontamento das regras 1.0.0 (sem variável). */
    public Finding(String id, FindingCode code, FindingScope scope, SessionId session, StepId step, int sessionNumber, int stepNumber, String detail) {
        this(id, code, scope, session, step, sessionNumber, stepNumber, detail, null);
    }

    public Optional<VariableId> variableId() {
        return Optional.ofNullable(variable);
    }

    public Severity severity() {
        return code.severity();
    }

    public FindingField field() {
        return code.field();
    }

    public Optional<SessionId> sessionId() {
        return Optional.ofNullable(session);
    }

    public Optional<StepId> stepId() {
        return Optional.ofNullable(step);
    }

    /** Explicação segura: o modelo do código e, se houver, o rótulo do detalhe. */
    public String explanation() {
        String label = FindingDetails.label(detail);
        return label.isEmpty() ? code.explanation() : code.explanation() + " Tipo: " + label + ".";
    }

    public String guidance() {
        return code.guidance();
    }
}
