package com.nekkora.studio.semantics;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.variable.SemanticStatus;
import com.nekkora.studio.domain.variable.SemanticType;
import com.nekkora.studio.domain.variable.VariableDefinition;
import com.nekkora.studio.domain.variable.VariableId;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.validation.DataFlowAnalysis;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Contrato semântico de uma automação (STUDIO-008, {@value #SCHEMA} {@value #VERSION}): o que o desenvolvedor DECLAROU
 * sobre os dados do processo e o que o Studio DERIVOU do modelo — variáveis, onde são usadas, de onde vêm e o que não pode
 * ser comprovado.
 *
 * <ul>
 *   <li><b>Determinístico</b>: o mesmo estado produz os mesmos bytes (sem instantes, sem identidades geradas).</li>
 *   <li><b>Local</b>: derivado em memória; não é persistido no projeto e não sai da máquina.</li>
 *   <li><b>Sem segredos</b>: nunca valores — nem de execução (o Studio não tem), nem o valor fixo declarado
 *       ({@code constantDeclared} diz só que existe), nem literais de etapas, nem apelidos de credencial, nem nomes de
 *       etapas, sessões ou automação, nem a finalidade em texto livre, nem nomes de referências não declaradas, nem nada
 *       observado na página. A lista {@link #EXCLUSIONS} vai no próprio contrato.</li>
 *   <li><b>Separado do relatório de validação</b>: o relatório diz o que falta; o contrato descreve o que está declarado.
 *       Nenhum dos dois certifica execução.</li>
 * </ul>
 */
public final class SemanticContract {
    public static final String SCHEMA = "nekkora-studio/semantic-contract";
    public static final String VERSION = "1.0.0";
    /** Regras que definem uso derivado, escopo e ordem (as mesmas da validação 1.1.0). */
    public static final String DATA_FLOW_RULES = "nekkora-validation-rules/1.1.0";

    /** O que este contrato NUNCA contém (vai no próprio contrato, para quem o consome). */
    public static final List<String> EXCLUSIONS = List.of("RUNTIME_VALUES", "CONSTANT_VALUES", "LITERAL_VALUES", "CREDENTIAL_ALIASES",
            "UNDECLARED_REFERENCE_NAMES", "AUTOMATION_SESSION_AND_STEP_NAMES", "FREE_TEXT_PURPOSE", "PAGE_CONTENT", "LOCATORS", "EVIDENCE");

    /**
     * Situação da etapa produtora DECLARADA de uma variável {@code STEP_OUTPUT}. Nenhuma situação significa "verificada
     * na execução": o Studio não executa nada ({@code runtimeVerification} é sempre {@code UNVERIFIED_AT_RUNTIME}).
     */
    public enum ProducerStatus {
        /** Declarada; nenhuma etapa usa a variável (não há ordem a conferir). */
        DECLARED,
        /** Declarada e coerente com o modelo: existe, está no escopo e vem antes de todas as consumidoras, sem ciclo. */
        STATICALLY_CONSISTENT,
        /** Declarada, mas incoerente com o modelo (inexistente, fora do escopo, depois de uma consumidora, a própria etapa ou ciclo). */
        INVALID
    }

    /** Origem do valor de uma etapa (só o tipo; nunca o conteúdo). */
    public enum ValueKind { NONE, LITERAL, VARIABLE, CREDENTIAL }

    /** Referência de variável de uma etapa. */
    public enum Reference { DECLARED, UNDECLARED }

    /** O que não pode ser comprovado (vocabulário fechado). */
    public enum UncertaintyCode {
        /** Valor de entrada: só existe na execução. */
        INPUT_VALUE_UNKNOWN,
        /** Saída de etapa declarada: não comprovada. */
        STEP_OUTPUT_UNVERIFIED,
        /** Variável sem origem declarada. */
        ORIGIN_UNBOUND,
        /** Significado proposto, não confirmado pelo desenvolvedor. */
        SEMANTIC_UNCONFIRMED,
        /** Significado não especificado. */
        SEMANTIC_UNSPECIFIED,
        /** Etapa usa um nome que não está declarado. */
        UNDECLARED_REFERENCE,
        /** Etapa usa uma variável declarada fora do escopo dela. */
        OUT_OF_SCOPE_REFERENCE,
        /** Etapa usa uma credencial: só o apelido existe, sem cofre. */
        CREDENTIAL_UNRESOLVED
    }

    /** Uma incerteza, ligada a uma variável e/ou a uma etapa (só identidades). */
    public record Uncertainty(UncertaintyCode code, VariableId variable, StepId step) {}

    /** Produtora declarada. */
    public record Producer(StepId step, ProducerStatus status) {}

    /** Variável declarada, com o uso derivado. */
    public record Variable(VariableDefinition definition, Producer producer, List<StepId> consumers) {}

    /** Etapa: identidade, posição e tipo de ação; do valor, só o tipo da origem. */
    public record Step(StepId id, SessionId session, int sessionNumber, int stepNumber, ActionType actionType, ValueKind value, VariableId variable,
                       Reference reference) {}

    private final AutomationId automationId;
    private final String modelVersion;
    private final List<Variable> variables;
    private final List<Step> steps;
    private final List<Uncertainty> uncertainties;

    private SemanticContract(AutomationId automationId, String modelVersion, List<Variable> variables, List<Step> steps, List<Uncertainty> uncertainties) {
        this.automationId = automationId;
        this.modelVersion = modelVersion;
        this.variables = List.copyOf(variables);
        this.steps = List.copyOf(steps);
        this.uncertainties = List.copyOf(uncertainties);
    }

    /** Deriva o contrato do estado atual (na thread dona da automação). Só lê; linear no tamanho da automação. */
    public static SemanticContract of(Automation a) {
        DataFlowAnalysis flow = DataFlowAnalysis.of(a);
        List<Variable> vars = new ArrayList<>();
        List<Uncertainty> uncertain = new ArrayList<>();
        for (VariableDefinition v : a.variables()) {
            List<DataFlowAnalysis.Consumption> uses = flow.consumersOf(v.id());
            Producer producer = null;
            if (v.origin().kind() == VariableOrigin.Kind.STEP_OUTPUT) producer = new Producer(v.origin().producer(), producerStatus(v, uses, flow));
            vars.add(new Variable(v, producer, uses.stream().map(DataFlowAnalysis.Consumption::step).toList()));
            switch (v.origin().kind()) {
                case INPUT -> uncertain.add(new Uncertainty(UncertaintyCode.INPUT_VALUE_UNKNOWN, v.id(), null));
                case STEP_OUTPUT -> uncertain.add(new Uncertainty(UncertaintyCode.STEP_OUTPUT_UNVERIFIED, v.id(), v.origin().producer()));
                case UNBOUND -> uncertain.add(new Uncertainty(UncertaintyCode.ORIGIN_UNBOUND, v.id(), null));
                case CONSTANT -> {
                    // declarado pelo desenvolvedor: não há o que comprovar além da forma (conferida pelo domínio)
                }
            }
            if (v.authority() == SemanticStatus.PROPOSED_UNCONFIRMED) uncertain.add(new Uncertainty(UncertaintyCode.SEMANTIC_UNCONFIRMED, v.id(), null));
            if (v.semantic() == SemanticType.UNSPECIFIED) uncertain.add(new Uncertainty(UncertaintyCode.SEMANTIC_UNSPECIFIED, v.id(), null));
        }
        List<Step> list = new ArrayList<>();
        int s = 0;
        for (LogicalSession session : a.sessions()) {
            s++;
            int n = 0;
            for (AutomationStep st : session.steps()) {
                n++;
                ValueSource value = st.parameters().value().orElse(null);
                ValueKind kind = value == null ? ValueKind.NONE : switch (value) {
                    case ValueSource.LiteralValue l -> ValueKind.LITERAL;
                    case ValueSource.VariableReference r -> ValueKind.VARIABLE;
                    case ValueSource.CredentialReference c -> ValueKind.CREDENTIAL;
                };
                VariableId variable = null;
                Reference reference = null;
                if (kind == ValueKind.VARIABLE) {
                    VariableDefinition declared = flow.consumptionAt(st.id()).map(DataFlowAnalysis.Consumption::variable).orElse(null);
                    reference = declared == null ? Reference.UNDECLARED : Reference.DECLARED;
                    if (declared == null) {
                        uncertain.add(new Uncertainty(UncertaintyCode.UNDECLARED_REFERENCE, null, st.id()));
                    } else {
                        variable = declared.id();
                        if (!declared.scope().visibleIn(session.id())) uncertain.add(new Uncertainty(UncertaintyCode.OUT_OF_SCOPE_REFERENCE, variable, st.id()));
                    }
                } else if (kind == ValueKind.CREDENTIAL) {
                    uncertain.add(new Uncertainty(UncertaintyCode.CREDENTIAL_UNRESOLVED, null, st.id()));
                }
                list.add(new Step(st.id(), session.id(), s, n, st.actionType(), kind, variable, reference));
            }
        }
        return new SemanticContract(a.id(), a.modelVersion(), vars, list, uncertain);
    }

    private static ProducerStatus producerStatus(VariableDefinition v, List<DataFlowAnalysis.Consumption> uses, DataFlowAnalysis flow) {
        if (flow.producer(v) != DataFlowAnalysis.ProducerState.PRESENT) return ProducerStatus.INVALID;
        int producer = flow.order(v.origin().producer());
        for (DataFlowAnalysis.Consumption c : uses) {
            if (c.order() <= producer || flow.cycleSteps().contains(c.step()) || !v.scope().visibleIn(c.session())) return ProducerStatus.INVALID;
        }
        return uses.isEmpty() ? ProducerStatus.DECLARED : ProducerStatus.STATICALLY_CONSISTENT;
    }

    public AutomationId automationId() {
        return automationId;
    }

    /** Versão do modelo da automação descrita (1.0.0 sem variáveis, 1.1.0 com). */
    public String modelVersion() {
        return modelVersion;
    }

    public List<Variable> variables() {
        return variables;
    }

    public List<Step> steps() {
        return steps;
    }

    public List<Uncertainty> uncertainties() {
        return uncertainties;
    }

    /** JSON canônico (mesmo estado → mesmos bytes). */
    public String toJson() {
        return SemanticContractJson.write(this);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SemanticContract c && automationId.equals(c.automationId) && modelVersion.equals(c.modelVersion)
                && variables.equals(c.variables) && steps.equals(c.steps) && uncertainties.equals(c.uncertainties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(automationId, modelVersion, variables, steps, uncertainties);
    }

    @Override
    public String toString() {
        return "SemanticContract[" + variables.size() + " variáveis, " + steps.size() + " etapas, " + uncertainties.size() + " incertezas]";
    }
}
