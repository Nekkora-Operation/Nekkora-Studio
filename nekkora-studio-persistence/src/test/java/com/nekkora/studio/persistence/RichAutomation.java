package com.nekkora.studio.persistence;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepContext;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.OptionChoice;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.element.ContextAnchor;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.element.ManualLocator;
import com.nekkora.studio.domain.element.ObservedObstacle;
import com.nekkora.studio.domain.evidence.EvidenceType;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary;
import com.nekkora.studio.domain.evidence.ObservedEvidence;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepProvenance;
import com.nekkora.studio.domain.privacy.CapturePrivacy;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import com.nekkora.studio.domain.variable.SemanticStatus;
import com.nekkora.studio.domain.variable.SemanticType;
import com.nekkora.studio.domain.variable.Sensitivity;
import com.nekkora.studio.domain.variable.TextFormat;
import com.nekkora.studio.domain.variable.VariableConstraints;
import com.nekkora.studio.domain.variable.VariableDefinition;
import com.nekkora.studio.domain.variable.VariableDraft;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.variable.VariableScope;
import com.nekkora.studio.domain.variable.VariableType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Automação completa do §36 da STUDIO-006: três sessões, etapas gravadas e manuais, parâmetros (variável, credencial,
 * literal, opção), comentários, localizadores capturados e manuais, revisões manuais, evidências atribuídas e não
 * atribuídas, uma etapa duplicada e uma movimentada. Os dados capturados seguem a {@code nekkora-privacy/2} (como os
 * entregues pelo Recorder).
 */
final class RichAutomation {

    /** Relógio que avança 1 s a cada leitura: instantes todos distintos e reproduzíveis. */
    static final class TickClock extends Clock {
        private Instant now;

        TickClock(String start) {
            this.now = Instant.parse(start);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public synchronized Instant instant() {
            now = now.plusSeconds(1);
            return now;
        }
    }

    static final String URL = CapturePrivacy.urlTemplate("http://127.0.0.1:18765/lab/studio003/clientes.html?id=55821");

    private RichAutomation() {}

    static Automation build(Clock clock) {
        Automation a = Automation.create("Consulta de Clientes", "Processo de consulta de clientes.", clock);
        LogicalSession auth = a.addSession("Autenticação", "Entrar no sistema");
        LogicalSession cons = a.addSession("Consulta", "");
        LogicalSession res = a.addSession("Resultado", "");
        a.describeSession(res.id(), "Detalhes do cliente", "Revisar com a equipe");

        AutomationStep usuario = a.appendStep(auth.id(), recorded("Preencher campo Usuário", ActionType.FILL, "ev-1",
                element("Usuário", "TEXT_INPUT", "input", "usuario", false, true), ActionParameters.none(), ""));
        AutomationStep senha = a.appendStep(auth.id(), recorded("Preencher campo Senha", ActionType.FILL, "ev-2",
                element("Senha", "PASSWORD_INPUT", "input", null, true, false), ActionParameters.none(), ""));
        AutomationStep entrar = a.appendStep(auth.id(), recorded("Clicar no botão Entrar", ActionType.CLICK, "ev-3",
                element("Entrar", "BUTTON", "button", "entrar", false, true), ActionParameters.none(),
                "Envio de formulário observado (resultado de negócio não validado)."));
        AutomationStep clientes = a.appendStep(cons.id(), recorded("Clicar no link Clientes", ActionType.CLICK, "ev-4",
                element("Clientes", "LINK", "a", "clientes", false, true), ActionParameters.none(), "A rota da página mudou (resultado de negócio não validado)."));
        AutomationStep pesquisa = a.appendStep(cons.id(), StepDraft.manual("Preencher Pesquisa", ActionType.FILL, "Termo da pesquisa"));
        AutomationStep estado = a.appendStep(cons.id(), recorded("Selecionar São Paulo no campo Estado", ActionType.SELECT_OPTION, "ev-6",
                element("Estado", "SELECT", "select", "estado", false, true), ActionParameters.withOption(new OptionChoice("São Paulo", "SP")), ""));
        AutomationStep pesquisar = a.appendStep(cons.id(), recorded("Clicar no botão Pesquisar", ActionType.CLICK, "ev-7",
                element("Pesquisar", "BUTTON", "button", "pesquisar", false, true), ActionParameters.none(), ""));
        a.appendStep(res.id(), StepDraft.manual("Consultar detalhes", ActionType.OBSERVE, ""));

        // revisões manuais
        a.updateStepBasics(clientes.id(), "Abrir Clientes", "Abre a lista de clientes", ActionType.CLICK);
        a.setStepParameters(usuario.id(), ActionParameters.withValue(new ValueSource.VariableReference("cliente.usuario")));
        a.setStepParameters(senha.id(), ActionParameters.withValue(new ValueSource.CredentialReference("sistema.senha")));
        a.setStepParameters(pesquisa.id(), ActionParameters.withValue(new ValueSource.LiteralValue("termo de pesquisa")));
        a.addComment(entrar.id(), "Confirmar o botão com a equipe");
        a.addComment(entrar.id(), "Segundo comentário");
        a.editComment(entrar.id(), 0, "Confirmar o botão com a equipe de suporte");
        a.addManualLocator(usuario.id(), new ManualLocator(ManualLocator.Syntax.CSS, "form#login input[name=usuario]", "proposto na revisão",
                clock.instant()));
        a.choosePreferredLocator(usuario.id(), 1); // segundo localizador validado: PREFERRED_LOCATOR modificado
        // evidências (Recorder: executadas na página; uma ambígua sem etapa)
        a.attachEvidence(evidence("evd-1", 1, "ev-3", EvidenceType.FORM_SUBMITTED, null));
        a.attachEvidence(evidence("evd-2", 2, "ev-3", EvidenceType.VISIBLE_ERROR_MESSAGE, EvidenceVocabulary.Classification.ERROR_INDICATOR));
        a.attachEvidence(evidence("evd-3", 3, "ev-3", EvidenceType.OBSERVATION_COMPLETE, null));
        a.attachEvidence(evidence("evd-4", 4, "ev-7", EvidenceType.LOADING_INDICATOR, null));
        a.attachEvidence(evidence("evd-5", 5, null, EvidenceType.AMBIGUOUS_FEEDBACK, null));
        // estrutura: duplicada (MANUAL derivada) e movimentada
        a.duplicateStep(entrar.id());
        a.moveStep(pesquisar.id(), res.id(), 0);
        a.moveStep(estado.id(), cons.id(), 0);
        return a;
    }

    static StepDraft recorded(String name, ActionType type, String eventId, ElementReference element, ActionParameters params, String outcome) {
        CorrelationKeys keys = CorrelationKeys.of(Map.of(CorrelationKeys.Key.RECORDING_ID, "rec-1", CorrelationKeys.Key.RECORDED_EVENT_ID, eventId,
                CorrelationKeys.Key.TARGET_ID, "tgt-" + eventId));
        RecordingObservation obs = new RecordingObservation(RecordingObservation.CaptureMode.CAPTURE_AND_INTERACT,
                RecordingObservation.Observation.INTERACTION_OBSERVED, null, List.of("FORM_SUBMITTED"), List.of("LIVE_CHECK_UNAVAILABLE"));
        return StepDraft.builder().name(name).actionType(type).description("Interação observada no navegador e encaminhada à página.")
                .provenance(StepProvenance.recorded(keys, obs)).element(element).parameters(params).observedOutcome(outcome)
                .context(new StepContext("Navegador", CapturePrivacy.title("Login"), URL)).build();
    }

    static ElementReference element(String name, String kind, String tag, String htmlId, boolean sensitive, boolean twoValidated) {
        ElementReference.Builder b = ElementReference.builder().accessibleName(CapturePrivacy.interfaceText(name)).semanticKind(kind).tag(tag)
                .htmlId(htmlId).sensitive(sensitive).privacyPolicy(DisplaySafety.KNOWN_PRODUCER_POLICY).sourceContract("nekkora-bridge/1#studio.recording.step")
                .correlation(CorrelationKeys.of(Map.of(CorrelationKeys.Key.TARGET_ID, "tgt-" + tag, CorrelationKeys.Key.NODE_ID, "n12")));
        String id = htmlId == null ? tag : htmlId;
        b.addLocator(new LocatorCandidate(LocatorStrategy.ID, tag + " {id=" + id + "}", "#" + id, LocatorStability.HEURISTIC_STABLE, 1, true))
                .preferLastLocator();
        if (twoValidated) {
            b.addLocator(new LocatorCandidate(LocatorStrategy.POSITIONAL, "html >> body >> form {nth-of-type 1} >> " + tag + " {nth-of-type 2}",
                    "html > body > form:nth-of-type(1) > " + tag + ":nth-of-type(2)", LocatorStability.HEURISTIC_UNSTABLE, 1, true));
        }
        b.addLocator(new LocatorCandidate(LocatorStrategy.TEXT, tag + " {text=\"Salvar\"}", "//" + tag + "[normalize-space(.)=\"Salvar\"]",
                LocatorStability.UNKNOWN, 3, true));
        b.addAccessStep(new AccessStep(AccessStep.Type.MAIN_DOCUMENT, "Documento", "", CorrelationKeys.none()));
        b.addAccessStep(new AccessStep(AccessStep.Type.IFRAME, "Cadastro", "#cadastro", CorrelationKeys.of(Map.of(CorrelationKeys.Key.FRAME_ID, "f1"))));
        b.addContext(new ContextAnchor("IN_FORM", "FORM", "Login", ""));
        b.addStabilityNote("Localizador preferencial executado na página real e único");
        b.addStabilityNote("2 elemento(s) parecido(s) na página");
        b.addObstacle(new ObservedObstacle("INDISTINGUISHABLE_LOOKALIKES", ObservedObstacle.Severity.MEDIUM,
                "Há elementos idênticos na página que não se distinguem pelos atributos."));
        return b.build();
    }

    static ObservedEvidence evidence(String id, long seq, String eventId, EvidenceType type, EvidenceVocabulary.Classification classification) {
        boolean ambiguous = eventId == null;
        EvidenceVocabulary.Confidence confidence = ambiguous ? EvidenceVocabulary.Confidence.AMBIGUOUS
                : type.windowEnd() ? EvidenceVocabulary.Confidence.DIRECTLY_OBSERVED : EvidenceVocabulary.Confidence.TEMPORALLY_ASSOCIATED;
        ObservedEvidence.Message msg = type == EvidenceType.VISIBLE_ERROR_MESSAGE ? ObservedEvidence.Message.of("Usuário ou senha inválidos.")
                : ObservedEvidence.Message.none();
        return new ObservedEvidence(id, seq, "rec-1", eventId, Instant.parse("2026-10-16T12:00:00Z").plusSeconds(seq), type,
                EvidenceVocabulary.Source.FEEDBACK_OBSERVER, EvidenceVocabulary.Relation.AFTER_ACTION, confidence, classification,
                new ObservedEvidence.DocumentContext(false, false, "título ignorado", URL), type == EvidenceType.VISIBLE_ERROR_MESSAGE ? "alerta" : null,
                msg, null, null, type == EvidenceType.FORM_SUBMITTED ? new ObservedEvidence.Navigation(false, URL) : null,
                List.of(EvidenceVocabulary.Signal.ROLE_ALERT), ambiguous ? 2 : 0, type.windowEnd() ? 3 : 0,
                type == EvidenceType.VISIBLE_ERROR_MESSAGE ? List.of(EvidenceVocabulary.Limitation.MESSAGE_SUPPRESSED) : List.of());
    }

    /**
     * STUDIO-008: a mesma automação com um catálogo de variáveis que cobre cada tipo, origem, escopo e restrição (dados
     * fictícios; nenhum valor de entrada). {@code cliente.usuario} é a variável usada pela etapa "Preencher campo Usuário".
     */
    static Automation withVariables(Clock clock) {
        Automation a = build(clock);
        LogicalSession cons = a.sessions().get(1);
        LogicalSession res = a.sessions().get(2);
        a.declareVariable(VariableDraft.of("cliente.login", VariableType.STRING).withSemantic(SemanticType.IDENTIFIER, SemanticStatus.USER_CONFIRMED)
                .withPurpose("Usuário de acesso do cliente fictício").withOrigin(VariableOrigin.input()).withSensitivity(Sensitivity.INTERNAL)
                .withConstraints(new VariableConstraints(true, 3, 40, TextFormat.LETTERS_AND_DIGITS, null, null, null, null)));
        VariableDefinition login = a.variableNamed("cliente.login").orElseThrow();
        a.renameVariable(login.id(), "cliente.usuario"); // updatedAt > createdAt; o consumidor passa a apontar para ela
        a.declareVariable(VariableDraft.of("cliente.cpf", VariableType.STRING).withSemantic(SemanticType.BRAZILIAN_CPF, SemanticStatus.USER_CONFIRMED)
                .withSensitivity(Sensitivity.PERSONAL_DATA).withOrigin(VariableOrigin.input()).withConstraints(new VariableConstraints(true, 11, 11, TextFormat.DIGITS, null, null, null, null)));
        a.declareVariable(VariableDraft.of("consulta.termo", VariableType.STRING).withSemantic(SemanticType.FREE_TEXT, SemanticStatus.PROPOSED_UNCONFIRMED)
                .withScope(VariableScope.session(cons.id())).withOrigin(VariableOrigin.constant("termo de pesquisa")).withSensitivity(Sensitivity.PUBLIC));
        a.declareVariable(VariableDraft.of("resultado.codigo", VariableType.STRING).withScope(VariableScope.session(res.id()))
                .withOrigin(VariableOrigin.stepOutput(id(a, "Consultar detalhes"))).withSensitivity(Sensitivity.INTERNAL));
        a.declareVariable(VariableDraft.of("pedido.valor", VariableType.DECIMAL).withSemantic(SemanticType.CURRENCY, SemanticStatus.USER_CONFIRMED)
                .withSensitivity(Sensitivity.INTERNAL).withOrigin(VariableOrigin.input())
                .withConstraints(new VariableConstraints(false, null, null, TextFormat.ANY, new BigDecimal("0.00"), new BigDecimal("99999.99"), 2, "BRL")));
        a.declareVariable(VariableDraft.of("pedido.quantidade", VariableType.INTEGER).withOrigin(VariableOrigin.constant("3"))
                .withConstraints(new VariableConstraints(false, null, null, TextFormat.ANY, BigDecimal.ONE, new BigDecimal("10"), null, null)));
        a.declareVariable(VariableDraft.of("pedido.data", VariableType.DATE).withSemantic(SemanticType.DATE_ISO, SemanticStatus.USER_CONFIRMED)
                .withOrigin(VariableOrigin.input()));
        a.declareVariable(VariableDraft.of("cliente.ativo", VariableType.BOOLEAN).withOrigin(VariableOrigin.unbound()));
        return a;
    }

    /** A etapa por nome (os nomes desta automação são únicos). */
    static AutomationStep step(Automation a, String name) {
        return a.sessions().stream().flatMap(s -> s.steps().stream()).filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    static StepId id(Automation a, String name) {
        return step(a, name).id();
    }
}
