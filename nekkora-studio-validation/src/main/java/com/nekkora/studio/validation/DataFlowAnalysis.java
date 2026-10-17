package com.nekkora.studio.validation;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.variable.VariableDefinition;
import com.nekkora.studio.domain.variable.VariableId;
import com.nekkora.studio.domain.variable.VariableOrigin;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Análise estática do fluxo de dados de uma automação (STUDIO-008). Só lê o modelo; nada executa, nada infere.
 *
 * <ul>
 *   <li><b>Uso derivado</b>: uma etapa consome a variável cujo nome está EXATAMENTE na sua referência efetiva
 *       ({@link ValueSource.VariableReference}). Nome parecido não é o mesmo nome.</li>
 *   <li><b>Produtor só declarado</b>: a única ligação produtor→variável é a origem {@code STEP_OUTPUT} declarada pelo
 *       desenvolvedor. O Studio não comprova que a etapa produz o valor — a ligação fica "declarada", nunca "verificada".</li>
 *   <li><b>Ordem</b>: a ordem linear das etapas (sessões na ordem, etapas na ordem). Não existem desvios nem repetições
 *       no modelo atual.</li>
 * </ul>
 *
 * <p>Linear no tamanho da automação: um índice das etapas e um dos nomes, montados uma vez.
 */
public final class DataFlowAnalysis {

    /**
     * Uma referência de variável numa etapa.
     *
     * @param variable a variável declarada com esse nome, ou vazia (não declarada)
     */
    public record Consumption(StepId step, SessionId session, int order, VariableDefinition variable) {
        public Optional<VariableDefinition> declared() {
            return Optional.ofNullable(variable);
        }
    }

    /** Situação da produtora declarada de uma variável {@code STEP_OUTPUT}, em relação ao modelo. */
    public enum ProducerState {
        /** A variável não declara produtora (não é saída de etapa). */
        NOT_APPLICABLE,
        /** A produtora declarada existe e está no escopo da variável. */
        PRESENT,
        /** A produtora declarada não existe mais. */
        MISSING,
        /** A produtora declarada existe, mas fora do escopo da variável. */
        OUT_OF_SCOPE
    }

    /** Posição de uma etapa na ordem linear. */
    private record Position(SessionId session, int order) {}

    private final Map<StepId, Position> positions;
    private final Set<SessionId> sessions;
    private final List<Consumption> consumptions;
    private final Map<VariableId, List<Consumption>> byVariable;
    private final Map<StepId, Consumption> byStep;
    private final Set<StepId> cycle;

    private DataFlowAnalysis(Automation a) {
        Map<String, VariableDefinition> names = new HashMap<>();
        for (VariableDefinition v : a.variables()) names.put(v.name(), v);
        positions = new HashMap<>();
        sessions = new LinkedHashSet<>();
        List<Consumption> all = new ArrayList<>();
        Map<VariableId, List<Consumption>> per = new HashMap<>();
        Map<StepId, Consumption> steps = new HashMap<>();
        int order = 0;
        for (LogicalSession s : a.sessions()) {
            sessions.add(s.id());
            for (AutomationStep st : s.steps()) {
                positions.put(st.id(), new Position(s.id(), order));
                if (st.parameters().value().orElse(null) instanceof ValueSource.VariableReference r) {
                    Consumption c = new Consumption(st.id(), s.id(), order, names.get(r.name()));
                    all.add(c);
                    steps.put(st.id(), c);
                    if (c.variable() != null) per.computeIfAbsent(c.variable().id(), k -> new ArrayList<>()).add(c);
                }
                order++;
            }
        }
        consumptions = List.copyOf(all);
        byVariable = per;
        byStep = steps;
        cycle = cycles(a);
    }

    /** Analisa a automação (na thread dona dela). */
    public static DataFlowAnalysis of(Automation a) {
        return new DataFlowAnalysis(a);
    }

    /** Todas as referências de variável, na ordem das etapas. */
    public List<Consumption> consumptions() {
        return consumptions;
    }

    /** Consumidoras derivadas de uma variável, na ordem das etapas. */
    public List<Consumption> consumersOf(VariableId id) {
        return byVariable.getOrDefault(id, List.of());
    }

    /** A referência de variável da etapa, se houver. */
    public Optional<Consumption> consumptionAt(StepId step) {
        return Optional.ofNullable(byStep.get(step));
    }

    /** Posição da etapa na ordem linear (a partir de 0), ou -1 se não existe. */
    public int order(StepId step) {
        Position p = positions.get(step);
        return p == null ? -1 : p.order();
    }

    /** Sessão da etapa, se ela existe. */
    public Optional<SessionId> sessionOf(StepId step) {
        Position p = positions.get(step);
        return p == null ? Optional.empty() : Optional.of(p.session());
    }

    /** A sessão do escopo existe (sempre verdadeiro para o escopo da automação). */
    public boolean scopeExists(VariableDefinition v) {
        return v.scope().sessionId().map(sessions::contains).orElse(true);
    }

    /** Situação da produtora declarada. */
    public ProducerState producer(VariableDefinition v) {
        if (v.origin().kind() != VariableOrigin.Kind.STEP_OUTPUT) return ProducerState.NOT_APPLICABLE;
        Position p = positions.get(v.origin().producer());
        if (p == null) return ProducerState.MISSING;
        return v.scope().visibleIn(p.session()) ? ProducerState.PRESENT : ProducerState.OUT_OF_SCOPE;
    }

    /** Etapas que participam de um ciclo de dependência declarada (produtora → consumidora). */
    public Set<StepId> cycleSteps() {
        return cycle;
    }

    /**
     * Ciclos no grafo "a etapa consumidora depende da produtora declarada da variável que consome". Cada etapa tem no
     * máximo uma referência de valor, logo no máximo uma aresta de saída: basta seguir a cadeia (linear no total).
     */
    private Set<StepId> cycles(Automation a) {
        Map<StepId, StepId> dependsOn = new HashMap<>();
        for (Consumption c : consumptions) {
            VariableDefinition v = c.variable();
            if (v != null && v.origin().kind() == VariableOrigin.Kind.STEP_OUTPUT && positions.containsKey(v.origin().producer())) {
                dependsOn.put(c.step(), v.origin().producer());
            }
        }
        Set<StepId> inCycle = new LinkedHashSet<>();
        Map<StepId, Integer> state = new HashMap<>(); // 1 = no caminho atual, 2 = concluída
        for (StepId start : dependsOn.keySet()) {
            if (state.containsKey(start)) continue;
            Deque<StepId> path = new ArrayDeque<>();
            StepId cur = start;
            while (cur != null && !state.containsKey(cur)) {
                state.put(cur, 1);
                path.push(cur);
                cur = dependsOn.get(cur);
            }
            if (cur != null && state.get(cur) == 1) {
                // a cadeia voltou a uma etapa do caminho atual: tudo desde ela até o fim do caminho é o ciclo
                for (StepId s : path) {
                    inCycle.add(s);
                    if (s.equals(cur)) break;
                }
            }
            for (StepId s : path) state.put(s, 2);
        }
        return Collections.unmodifiableSet(inCycle);
    }
}
