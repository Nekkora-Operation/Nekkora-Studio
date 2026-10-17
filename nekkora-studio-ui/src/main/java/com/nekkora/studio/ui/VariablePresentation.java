package com.nekkora.studio.ui;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.variable.SemanticStatus;
import com.nekkora.studio.domain.variable.SemanticType;
import com.nekkora.studio.domain.variable.VariableConstraints;
import com.nekkora.studio.domain.variable.VariableDefinition;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.variable.VariableType;
import com.nekkora.studio.validation.DataFlowAnalysis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Textos de apresentação das variáveis (STUDIO-008). Mostra a DECLARAÇÃO e o uso derivado; nunca um valor de execução
 * (que o Studio não tem). O valor fixo de uma variável não sensível não é exibido nas listas — só no formulário.
 */
public final class VariablePresentation {
    private VariablePresentation() {}

    /** Lugar de uma etapa: "Sessão 1 · Etapa 02 — nome". Índices montados uma vez por chamada a {@link #places}. */
    public static Map<StepId, String> places(Automation a) {
        Map<StepId, String> out = new HashMap<>();
        int s = 0;
        for (LogicalSession session : a.sessions()) {
            s++;
            int n = 0;
            for (AutomationStep st : session.steps()) {
                n++;
                out.put(st.id(), "Sessão " + s + " · Etapa " + (n < 10 ? "0" : "") + n + " — " + st.name());
            }
        }
        return out;
    }

    public static String scope(Automation a, VariableDefinition v) {
        if (v.scope().kind() == com.nekkora.studio.domain.variable.VariableScope.Kind.AUTOMATION) return "Automação inteira";
        SessionId id = v.scope().session();
        int n = a.sessionNumber(id);
        return n == 0 ? "Sessão (removida)" : "Sessão " + n + " — " + a.session(id).map(LogicalSession::name).orElse("");
    }

    public static String semantic(VariableDefinition v) {
        if (v.semantic() == SemanticType.UNSPECIFIED) return SemanticType.UNSPECIFIED.label();
        return v.semantic().label() + (v.authority() == SemanticStatus.USER_CONFIRMED ? " (confirmado)" : " (proposto, não confirmado)");
    }

    /** Origem declarada, com a ressalva do que o Studio não comprova. */
    public static String origin(VariableDefinition v, DataFlowAnalysis flow, Map<StepId, String> places) {
        VariableOrigin o = v.origin();
        return switch (o.kind()) {
            case INPUT -> "Entrada (o valor só existe na execução)";
            case CONSTANT -> "Valor fixo, gravado no projeto sem criptografia";
            case UNBOUND -> "Sem origem declarada";
            case STEP_OUTPUT -> switch (flow.producer(v)) {
                case MISSING -> "Saída de etapa — etapa declarada não existe mais";
                case OUT_OF_SCOPE -> "Saída de etapa — etapa declarada fora do escopo";
                default -> "Saída declarada de " + places.getOrDefault(o.producer(), "etapa") + " (não comprovada)";
            };
        };
    }

    public static String constraints(VariableDefinition v) {
        VariableConstraints c = v.constraints();
        List<String> parts = new ArrayList<>();
        if (c.required()) parts.add("obrigatória");
        if (c.minLength() != null || c.maxLength() != null) {
            parts.add(c.minLength() != null && c.minLength().equals(c.maxLength()) ? c.minLength() + " caracteres"
                    : "comprimento " + (c.minLength() == null ? "0" : c.minLength()) + "–" + (c.maxLength() == null ? "…" : c.maxLength()));
        }
        if (c.textFormat() != com.nekkora.studio.domain.variable.TextFormat.ANY) {
            String label = c.textFormat().label(); // só a inicial em minúscula: "máscara de CPF", não "máscara de cpf"
            parts.add(label.substring(0, 1).toLowerCase(java.util.Locale.ROOT) + label.substring(1));
        }
        if (c.minimum() != null || c.maximum() != null) {
            parts.add("de " + (c.minimum() == null ? "…" : c.minimum().toPlainString()) + " a " + (c.maximum() == null ? "…" : c.maximum().toPlainString()));
        }
        if (c.scale() != null) parts.add(c.scale() + " casas decimais");
        if (c.currency() != null) parts.add("moeda " + c.currency());
        return parts.isEmpty() ? "Nenhuma" : String.join(", ", parts);
    }

    /** Linha principal da lista: nome · tipo · significado. */
    public static String title(VariableDefinition v) {
        return v.name() + " · " + v.type().label() + (v.semantic() == SemanticType.UNSPECIFIED ? "" : " · " + v.semantic().label());
    }

    /** Linha secundária: origem resumida, escopo, uso e sensibilidade. */
    public static String summary(Automation a, VariableDefinition v, DataFlowAnalysis flow) {
        int uses = flow.consumersOf(v.id()).size();
        String origin = switch (v.origin().kind()) {
            case INPUT -> "Entrada";
            case CONSTANT -> "Valor fixo";
            case STEP_OUTPUT -> "Saída de etapa";
            case UNBOUND -> "Sem origem";
        };
        return origin + " · " + scope(a, v) + " · " + (uses == 0 ? "não usada" : "usada em " + StudioText.steps(uses)) + " · " + v.sensitivity().label();
    }

    /** Tipos aceitos como valor de uma ação (o mesmo critério das regras 1.1.0). */
    public static String typeLabel(VariableType t) {
        return t.label();
    }
}
