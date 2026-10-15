package com.nekkora.studio.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.OptionChoice;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.element.ManualLocator;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.RecordingObservation.CaptureMode;
import com.nekkora.studio.domain.origin.RecordingObservation.Observation;
import com.nekkora.studio.domain.origin.StepOrigin;
import com.nekkora.studio.domain.origin.StepProvenance;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** STUDIO-004: operações de edição do agregado — estrutura, etapa, parâmetros, localizadores, comentários e histórico. */
class AutomationEditingTest {

    /** Relógio que o teste avança (instantes de criação × edição). */
    private static final class MovableClock extends Clock {
        Instant now = Instant.parse("2026-10-15T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }
    }

    private final MovableClock clock = new MovableClock();
    private AutomationCatalog catalog;
    private Automation a;
    private LogicalSession auth;
    private LogicalSession consulta;
    private AutomationStep usuario;
    private AutomationStep senha;
    private AutomationStep entrar;
    private AutomationStep clientes;
    private AutomationStep pesquisa;

    static ElementReference element(String kind, String name, boolean sensitive) {
        return ElementReference.builder().semanticKind(kind).accessibleName(name).tag("input").htmlId("campo").sensitive(sensitive)
                .privacyPolicy("nekkora-privacy/1").sourceContract("nekkora-bridge/1")
                .addLocator(new LocatorCandidate(LocatorStrategy.ID, "id \"campo\"", "#campo", LocatorStability.HEURISTIC_STABLE, 1, true))
                .preferLastLocator()
                .addLocator(new LocatorCandidate(LocatorStrategy.ROLE_NAME, "textbox \"" + name + "\"", "", LocatorStability.HEURISTIC_STABLE, 1, true))
                .addLocator(new LocatorCandidate(LocatorStrategy.TEXT, "texto", "", LocatorStability.HEURISTIC_UNSTABLE, 3, true))
                .correlation(CorrelationKeys.of(Map.of(CorrelationKeys.Key.TARGET_ID, "tgt-1")))
                .build();
    }

    static StepDraft recorded(String name, ActionType type, ElementReference e, ActionParameters params, String eventId) {
        CorrelationKeys keys = CorrelationKeys.of(Map.of(CorrelationKeys.Key.RECORDING_ID, "rec-1", CorrelationKeys.Key.RECORDED_EVENT_ID, eventId));
        RecordingObservation obs = new RecordingObservation(CaptureMode.CAPTURE_AND_INTERACT, Observation.INTERACTION_OBSERVED, null,
                List.of("FORM_SUBMITTED"), List.of());
        return StepDraft.builder().name(name).actionType(type).element(e).parameters(params).provenance(StepProvenance.recorded(keys, obs))
                .context(new StepContext("Navegador", "Login de teste", "http://127.0.0.1/login")).build();
    }

    @BeforeEach
    void setUp() {
        catalog = new AutomationCatalog(clock);
        a = catalog.create("Consulta de clientes", "Consulta cadastral");
        auth = a.addSession("Autenticação", null);
        consulta = a.addSession("Consulta", null);
        usuario = a.appendStep(auth.id(), recorded("Preencher campo Usuário", ActionType.FILL, element("TEXT_INPUT", "Usuário", false), null, "ev-1"));
        senha = a.appendStep(auth.id(), recorded("Preencher campo Senha", ActionType.FILL, element("PASSWORD_INPUT", "Senha", true), null, "ev-2"));
        entrar = a.appendStep(auth.id(), recorded("Clicar no botão Entrar", ActionType.CLICK, element("BUTTON", "Entrar", false), null, "ev-3"));
        clientes = a.appendStep(auth.id(), recorded("Clicar no link Clientes", ActionType.CLICK, element("LINK", "Clientes", false), null, "ev-4"));
        pesquisa = a.appendStep(consulta.id(), StepDraft.manual("Preencher Pesquisa", ActionType.FILL, null));
    }

    private static List<String> names(LogicalSession s) {
        return s.steps().stream().map(AutomationStep::name).toList();
    }

    private List<Integer> numbers() {
        return a.sequence().stream().map(StepPosition::number).toList();
    }

    // ------------------------------------------------------------------ automação e sessões

    @Test
    @DisplayName("S004-001/002/003 editar nome e descrição da automação; nome duplicado recusado sem alteração parcial")
    void automacao() {
        catalog.create("Outra", null);
        long rev = a.revision();
        catalog.updateDetails(a.id(), "Consulta de clientes PF", "Nova descrição");
        assertEquals("Consulta de clientes PF", a.name());
        assertEquals("Nova descrição", a.description());
        assertEquals(rev + 1, a.revision());
        assertEquals(ValidationProblem.AUTOMATION_NAME_IN_USE, assertThrows(DomainValidationException.class,
                () -> catalog.updateDetails(a.id(), "outra", "x")).problem());
        assertEquals("Consulta de clientes PF", a.name());
        assertEquals("Nova descrição", a.description(), "nada mudou");
        catalog.updateDetails(a.id(), "Consulta de clientes PF", "Nova descrição");
        assertEquals(rev + 1, a.revision(), "sem efeito: revisão igual");
        assertEquals(2, a.sessions().size());
        assertEquals(5, a.stepCount());
    }

    @Test
    @DisplayName("S004-004/005 editar nome, descrição e observações da sessão preservando identidade e etapas")
    void sessao() {
        SessionId id = auth.id();
        a.renameSession(id, "Acesso");
        a.describeSession(id, "Entrada no sistema", "Credenciais do cliente");
        LogicalSession s = a.session(id).orElseThrow();
        assertSame(auth, s);
        assertEquals("Acesso", s.name());
        assertEquals("Entrada no sistema", s.description());
        assertEquals("Credenciais do cliente", s.notes());
        assertEquals(4, s.stepCount());
        long rev = a.revision();
        a.describeSession(id, "Entrada no sistema", "Credenciais do cliente");
        assertEquals(rev, a.revision(), "sem efeito: revisão igual");

        assertTrue(a.editSession(id, "Autenticação", "Acesso inicial", "Observação"));
        assertEquals(rev + 1, a.revision(), "edição completa = uma revisão");
        assertEquals(ValidationProblem.DESCRIPTION_TOO_LONG, assertThrows(DomainValidationException.class,
                () -> a.editSession(id, "Novo nome", "x".repeat(Texts.DESCRIPTION_MAX + 1), "")).problem());
        assertEquals("Autenticação", s.name(), "nome não muda quando a descrição é recusada (sem alteração parcial)");
        assertEquals(ValidationProblem.SESSION_NAME_IN_USE, assertThrows(DomainValidationException.class,
                () -> a.editSession(id, "consulta", "", "")).problem());
        assertFalse(a.editSession(id, "Autenticação", "Acesso inicial", "Observação"));
    }

    @Test
    @DisplayName("S004-006/007/008/019 reorganizar sessões (primeira, última, inválida, sem efeito) recalcula a numeração")
    void reorganizarSessoes() {
        LogicalSession resultado = a.addSession("Resultado", null);
        a.appendStep(resultado.id(), StepDraft.manual("Conferir", ActionType.OBSERVE, null));
        long rev = a.revision();
        assertTrue(a.moveSession(consulta.id(), 0));
        assertEquals(List.of("Consulta", "Autenticação", "Resultado"), a.sessions().stream().map(LogicalSession::name).toList());
        assertEquals(rev + 1, a.revision(), "uma revisão por operação");
        assertEquals(1, a.positionOf(pesquisa.id()).orElseThrow().number(), "numeração recalculada pela ordem");
        assertEquals(2, a.positionOf(usuario.id()).orElseThrow().number());
        assertTrue(a.moveSession(consulta.id(), 2), "primeira → última");
        assertEquals(List.of("Autenticação", "Resultado", "Consulta"), a.sessions().stream().map(LogicalSession::name).toList());
        assertTrue(a.moveSession(consulta.id(), 1), "última para cima");
        assertFalse(a.moveSession(consulta.id(), 1), "mesma posição: sem efeito");
        assertEquals(rev + 3, a.revision());
        assertEquals(ValidationProblem.SESSION_POSITION_INVALID, assertThrows(DomainValidationException.class, () -> a.moveSession(auth.id(), 3)).problem());
        assertEquals(ValidationProblem.SESSION_POSITION_INVALID, assertThrows(DomainValidationException.class, () -> a.moveSession(auth.id(), -1)).problem());
        assertEquals(ValidationProblem.SESSION_NOT_FOUND, assertThrows(DomainValidationException.class, () -> a.moveSession(SessionId.newId(), 0)).problem());
        assertEquals(List.of(1, 2, 3, 4, 5, 6), numbers());
        assertSame(auth, a.session(auth.id()).orElseThrow(), "identidade preservada");
    }

    @Test
    @DisplayName("S004-009/010 excluir sessão vazia e sessão com etapas (atômica, sem etapa órfã, numeração recalculada)")
    void excluirSessoes() {
        LogicalSession vazia = a.addSession("Vazia", null);
        assertEquals(0, a.removeSession(vazia.id()));
        assertEquals(4, a.removeSession(auth.id()));
        assertEquals(List.of("Consulta"), a.sessions().stream().map(LogicalSession::name).toList());
        assertEquals(1, a.stepCount());
        assertTrue(a.step(usuario.id()).isEmpty(), "nenhuma etapa órfã");
        assertEquals(List.of(1), numbers());
        a.removeSession(consulta.id());
        assertTrue(a.sessions().isEmpty(), "a automação pode ficar sem sessões");
    }

    // ------------------------------------------------------------------ etapas: estrutura

    @Test
    @DisplayName("S004-016/018/019 reorganizar etapas na mesma sessão preserva identidade, procedência, referência e comentários")
    void reorganizarNaSessao() {
        a.addComment(entrar.id(), "Habilita depois dos campos obrigatórios");
        long rev = a.revision();
        assertTrue(a.moveStep(entrar.id(), auth.id(), 0));
        assertEquals(List.of("Clicar no botão Entrar", "Preencher campo Usuário", "Preencher campo Senha", "Clicar no link Clientes"), names(auth));
        assertEquals(rev + 1, a.revision());
        AutomationStep moved = a.step(entrar.id()).orElseThrow();
        assertSame(entrar, moved);
        assertEquals(StepOrigin.RECORDED, moved.provenance().origin());
        assertEquals("ev-3", moved.provenance().correlation().get(CorrelationKeys.Key.RECORDED_EVENT_ID).orElseThrow());
        assertEquals(1, moved.comments().size());
        assertEquals(1, a.positionOf(entrar.id()).orElseThrow().number());
        assertTrue(a.moveStep(entrar.id(), auth.id(), 3), "para o final da sessão");
        assertEquals("Clicar no botão Entrar", names(auth).get(3));
        assertFalse(a.moveStep(entrar.id(), auth.id(), 3), "mesma posição: sem efeito");
        assertEquals(ValidationProblem.STEP_POSITION_INVALID,
                assertThrows(DomainValidationException.class, () -> a.moveStep(entrar.id(), auth.id(), 4)).problem());
    }

    @Test
    @DisplayName("S004-017/018/019/039 mover etapa entre sessões: mesma entidade, procedência, comentários e numeração global")
    void moverEntreSessoes() {
        a.addComment(clientes.id(), "Menu lateral");
        long rev = a.revision();
        assertTrue(a.moveStep(clientes.id(), consulta.id(), 0));
        assertEquals(List.of("Preencher campo Usuário", "Preencher campo Senha", "Clicar no botão Entrar"), names(auth));
        assertEquals(List.of("Clicar no link Clientes", "Preencher Pesquisa"), names(consulta));
        assertEquals(rev + 1, a.revision(), "uma única revisão lógica");
        assertSame(clientes, a.step(clientes.id()).orElseThrow());
        assertEquals(consulta.id(), a.sessionOf(clientes.id()).orElseThrow().id());
        assertEquals(4, a.positionOf(clientes.id()).orElseThrow().number());
        assertEquals(List.of(1, 2, 3, 4, 5), numbers());
        assertEquals("Menu lateral", clientes.comments().get(0).text(), "o comentário acompanha a etapa");
        assertTrue(clientes.provenance().recordingObservation().isPresent());
        assertEquals(ValidationProblem.STEP_POSITION_INVALID,
                assertThrows(DomainValidationException.class, () -> a.moveStep(usuario.id(), consulta.id(), 3)).problem());
        assertEquals(ValidationProblem.SESSION_NOT_FOUND,
                assertThrows(DomainValidationException.class, () -> a.moveStep(usuario.id(), SessionId.newId(), 0)).problem());
        assertEquals(3, auth.stepCount(), "destino inválido: a etapa não sai da origem");
        Automation other = catalog.create("Outra", null);
        LogicalSession os = other.addSession("S", null);
        assertEquals(ValidationProblem.SESSION_NOT_FOUND,
                assertThrows(DomainValidationException.class, () -> a.moveStep(usuario.id(), os.id(), 0)).problem(),
                "não move para outra automação");
    }

    @Test
    @DisplayName("S004-020/021 duplicar: StepId novo, origem MANUAL derivada, sem gravação inventada")
    void duplicar() {
        a.addComment(entrar.id(), "Comentário da original");
        a.choosePreferredLocator(entrar.id(), 1);
        AutomationStep copy = a.duplicateStep(entrar.id());
        assertNotEquals(entrar.id(), copy.id());
        assertEquals(List.of("Preencher campo Usuário", "Preencher campo Senha", "Clicar no botão Entrar", "Clicar no botão Entrar",
                "Clicar no link Clientes"), names(auth));
        assertEquals(copy.id(), auth.steps().get(3).id(), "logo depois da original");
        assertEquals(StepOrigin.MANUAL, copy.provenance().origin());
        assertTrue(copy.provenance().correlation().isEmpty(), "sem identidade de gravação nem de evento");
        assertTrue(copy.provenance().recordingObservation().isEmpty(), "não é uma segunda interação observada");
        assertTrue(copy.baseline().isEmpty());
        assertTrue(copy.manualRevision().isEmpty());
        assertEquals(entrar.id(), copy.derivedFrom().orElseThrow());
        assertTrue(copy.comments().isEmpty(), "comentários não são copiados");
        assertEquals(1, copy.element().orElseThrow().preferredLocatorIndex(), "referência efetiva copiada");
        long recorded = a.sessions().stream().flatMap(s -> s.steps().stream()).filter(s -> s.provenance().origin() == StepOrigin.RECORDED).count();
        assertEquals(4, recorded, "a contagem de etapas gravadas não aumenta");
        assertEquals(0, a.discardRecording("rec-x"));
        assertEquals(4, a.discardRecording("rec-1"), "o cancelamento da gravação não leva a cópia manual");
        assertTrue(a.step(copy.id()).isPresent());
    }

    @Test
    @DisplayName("S004-022/023 excluir etapa preserva as demais e a ordem")
    void excluirEtapa() {
        long rev = a.revision();
        a.removeStep(senha.id());
        assertEquals(List.of("Preencher campo Usuário", "Clicar no botão Entrar", "Clicar no link Clientes"), names(auth));
        assertEquals(List.of(1, 2, 3, 4), numbers());
        assertEquals(rev + 1, a.revision());
        assertEquals(ValidationProblem.STEP_NOT_FOUND, assertThrows(DomainValidationException.class, () -> a.removeStep(senha.id())).problem());
    }

    // ------------------------------------------------------------------ etapas: edição

    @Test
    @DisplayName("S004-012/013/014/015/046 editar nome, descrição e tipo; parâmetros incompatíveis recusados sem alteração parcial")
    void editarEtapa() {
        a.setStepParameters(pesquisa.id(), ActionParameters.withValue(new ValueSource.VariableReference("consulta.termo")));
        long rev = a.revision();
        assertTrue(a.editStep(pesquisa.id(), StepEdit.of(a, pesquisa).name("Pesquisar cliente").description("Termo da consulta")));
        assertEquals("Pesquisar cliente", pesquisa.name());
        assertEquals("Termo da consulta", pesquisa.description());
        assertEquals(rev + 1, a.revision());
        assertFalse(a.editStep(pesquisa.id(), StepEdit.of(a, pesquisa)), "sem diferença: sem revisão");
        assertEquals(rev + 1, a.revision());

        DomainValidationException e = assertThrows(DomainValidationException.class,
                () -> a.editStep(pesquisa.id(), StepEdit.of(a, pesquisa).name("Outro nome").actionType(ActionType.CLICK)));
        assertEquals(ValidationProblem.VALUE_NOT_ACCEPTED, e.problem(), "o valor não é descartado em silêncio");
        assertEquals("Pesquisar cliente", pesquisa.name(), "nada mudou");
        assertEquals(ActionType.FILL, pesquisa.actionType());
        assertTrue(pesquisa.parameters().value().isPresent());
        assertEquals(rev + 1, a.revision());

        assertTrue(a.editStep(pesquisa.id(), StepEdit.of(a, pesquisa).actionType(ActionType.CLICK).parameters(ActionParameters.none())),
                "com a remoção explícita dos parâmetros");
        assertEquals(ActionType.CLICK, pesquisa.actionType());
        assertThrows(DomainValidationException.class, () -> a.editStep(pesquisa.id(), StepEdit.of(a, pesquisa).name(" ")));
        assertThrows(DomainValidationException.class, () -> a.editStep(pesquisa.id(), StepEdit.of(a, pesquisa).session(SessionId.newId())));
        assertEquals("Pesquisar cliente", pesquisa.name());
    }

    @Test
    @DisplayName("editar a sessão de destino move a etapa para o final dela (mesma identidade)")
    void editarSessao() {
        assertTrue(a.editStep(usuario.id(), StepEdit.of(a, usuario).session(consulta.id()).name("Usuário")));
        assertEquals(List.of("Preencher Pesquisa", "Usuário"), names(consulta));
        assertSame(usuario, consulta.steps().get(1));
    }

    @Test
    @DisplayName("S004-024/025/026/027 valor literal permitido, variável, credencial; literal sensível recusado")
    void parametros() {
        assertTrue(a.setStepParameters(pesquisa.id(), ActionParameters.withValue(new ValueSource.LiteralValue("Silva"))));
        assertEquals("Silva", pesquisa.parameters().value().orElseThrow().display());
        a.setStepParameters(usuario.id(), ActionParameters.withValue(new ValueSource.VariableReference("cliente.usuario")));
        assertEquals("Variável cliente.usuario", usuario.parameters().value().orElseThrow().display());
        a.setStepParameters(senha.id(), ActionParameters.withValue(new ValueSource.CredentialReference("sistema.senha")));
        assertEquals("Credencial sistema.senha", senha.parameters().value().orElseThrow().display());
        assertEquals(ValidationProblem.LITERAL_ON_SENSITIVE_TARGET, assertThrows(DomainValidationException.class,
                () -> a.setStepParameters(senha.id(), ActionParameters.withValue(new ValueSource.LiteralValue("Senha2026"))))
                .problem());
        assertEquals("Credencial sistema.senha", senha.parameters().value().orElseThrow().display(), "nada mudou");
        assertEquals(ValidationProblem.LITERAL_LOOKS_SENSITIVE, assertThrows(DomainValidationException.class,
                () -> new ValueSource.LiteralValue("ana@exemplo.com")).problem());
        assertEquals(ValidationProblem.REFERENCE_NAME_INVALID, assertThrows(DomainValidationException.class,
                () -> new ValueSource.VariableReference("cliente nome")).problem());
    }

    @Test
    @DisplayName("S004-028 editar OptionChoice: rótulo e valor técnico distintos, numa única etapa")
    void opcao() {
        AutomationStep estado = a.appendStep(consulta.id(), recorded("Selecionar São Paulo no campo Estado", ActionType.SELECT_OPTION,
                element("SELECT", "Estado", false), ActionParameters.withOption(new OptionChoice("São Paulo", "SP")), "ev-5"));
        int steps = a.stepCount();
        assertTrue(a.setStepParameters(estado.id(), ActionParameters.withOption(new OptionChoice("Rio de Janeiro", "RJ"))));
        assertEquals(new OptionChoice("Rio de Janeiro", "RJ"), estado.parameters().option().orElseThrow());
        assertEquals(steps, a.stepCount(), "nenhuma etapa extra para a opção");
        assertTrue(estado.modified(StepField.PARAMETERS));
        assertEquals(new OptionChoice("São Paulo", "SP"), estado.baseline().orElseThrow().parameters().option().orElseThrow(), "original preservado");
        assertEquals(ValidationProblem.OPTION_NOT_ACCEPTED, assertThrows(DomainValidationException.class,
                () -> a.setStepParameters(entrar.id(), ActionParameters.withOption(new OptionChoice("x", "x")))).problem());
    }

    // ------------------------------------------------------------------ referências técnicas

    @Test
    @DisplayName("S004-029/030/032 localizadores: escolher um validado; não validado recusado; original intacto")
    void localizadores() {
        ElementReference original = entrar.capturedElement().orElseThrow();
        assertEquals(3, entrar.element().orElseThrow().locators().size());
        assertTrue(a.choosePreferredLocator(entrar.id(), 1));
        assertEquals(1, entrar.element().orElseThrow().preferredLocatorIndex());
        assertSame(original, entrar.capturedElement().orElseThrow(), "o ElementReference capturado não muda");
        assertEquals(0, original.preferredLocatorIndex());
        assertTrue(entrar.modified(StepField.PREFERRED_LOCATOR));
        assertEquals(ValidationProblem.LOCATOR_NOT_VALIDATED,
                assertThrows(DomainValidationException.class, () -> a.choosePreferredLocator(entrar.id(), 2)).problem(), "3 correspondências");
        assertEquals(ValidationProblem.LOCATOR_NOT_FOUND,
                assertThrows(DomainValidationException.class, () -> a.choosePreferredLocator(entrar.id(), 9)).problem());
        assertEquals(ValidationProblem.STEP_HAS_NO_ELEMENT,
                assertThrows(DomainValidationException.class, () -> a.choosePreferredLocator(pesquisa.id(), 0)).problem());
        assertEquals("tgt-1", entrar.element().orElseThrow().correlation().get(CorrelationKeys.Key.TARGET_ID).orElseThrow(),
                "IDs técnicos do motor não mudam");
    }

    @Test
    @DisplayName("S004-031 localizador manual permanece não verificado e não pode ser preferencial")
    void localizadorManual() {
        ManualLocator m = new ManualLocator(ManualLocator.Syntax.CSS, "form#login button[type=submit]", "botão do formulário", clock.instant());
        assertTrue(a.addManualLocator(entrar.id(), m));
        assertEquals(List.of(m), entrar.manualLocators());
        assertEquals(ManualLocator.Verification.NOT_VERIFIED, entrar.manualLocators().get(0).verification());
        assertEquals(3, entrar.element().orElseThrow().locators().size(), "não entra na lista capturada");
        assertTrue(entrar.manualRevision().orElseThrow().manualLocators());
        assertEquals(ValidationProblem.MANUAL_LOCATOR_INVALID, assertThrows(DomainValidationException.class,
                () -> new ManualLocator(ManualLocator.Syntax.XPATH, "  ", null, clock.instant())).problem());
        assertEquals(ValidationProblem.MANUAL_LOCATOR_INVALID, assertThrows(DomainValidationException.class,
                () -> new ManualLocator(ManualLocator.Syntax.XPATH, "//a\n//b", null, clock.instant())).problem());
        assertEquals(ValidationProblem.MANUAL_LOCATOR_LOOKS_SENSITIVE, assertThrows(DomainValidationException.class,
                () -> new ManualLocator(ManualLocator.Syntax.CSS, "[data-email='ana@exemplo.com']", null, clock.instant())).problem());
        assertEquals(ValidationProblem.STEP_HAS_NO_ELEMENT, assertThrows(DomainValidationException.class,
                () -> a.addManualLocator(pesquisa.id(), m)).problem());
        assertTrue(a.removeManualLocator(entrar.id(), 0));
        assertTrue(entrar.manualLocators().isEmpty());
    }

    // ------------------------------------------------------------------ procedência e alterações

    @Test
    @DisplayName("S004-033/034 editar etapa gravada: continua RECORDED, observação intacta, alteração identificável")
    void procedencia() {
        RecordingObservation obs = entrar.provenance().recordingObservation().orElseThrow();
        assertTrue(entrar.manualRevision().isEmpty(), "gravada e não editada");
        clock.advance(Duration.ofMinutes(3));
        a.updateStepBasics(entrar.id(), "Confirmar autenticação", entrar.description(), ActionType.CLICK);
        assertEquals(StepOrigin.RECORDED, entrar.provenance().origin(), "a edição não transforma RECORDED em MANUAL");
        assertSame(obs, entrar.provenance().recordingObservation().orElseThrow());
        assertEquals(RecordingObservation.BusinessOutcome.NOT_VALIDATED, obs.businessOutcome(), "editar não valida o negócio");
        ManualRevision r = entrar.manualRevision().orElseThrow();
        assertEquals(java.util.Set.of(StepField.NAME), r.fields());
        assertEquals(clock.instant(), r.lastChangedAt());
        assertEquals(clock.instant(), entrar.editedAt(StepField.NAME).orElseThrow());
        assertEquals("Clicar no botão Entrar", entrar.baseline().orElseThrow().name(), "nome original preservado");
        assertTrue(pesquisa.manualRevision().isEmpty(), "etapa manual não tem revisão sobre captura");
        assertTrue(pesquisa.baseline().isEmpty());
    }

    @Test
    @DisplayName("S004-035 restaurar o valor original campo a campo, com validação de compatibilidade")
    void restaurar() {
        a.updateStepBasics(entrar.id(), "Confirmar autenticação", "Envia o login", ActionType.CLICK);
        a.choosePreferredLocator(entrar.id(), 1);
        assertTrue(a.restoreOriginal(entrar.id(), StepField.NAME));
        assertEquals("Clicar no botão Entrar", entrar.name());
        assertEquals("Envia o login", entrar.description(), "só o campo pedido");
        assertTrue(a.restoreOriginal(entrar.id(), StepField.PREFERRED_LOCATOR));
        assertEquals(0, entrar.element().orElseThrow().preferredLocatorIndex());
        assertFalse(a.restoreOriginal(entrar.id(), StepField.NAME), "já igual: sem efeito");
        assertEquals(java.util.Set.of(StepField.DESCRIPTION), entrar.manualRevision().orElseThrow().fields());

        AutomationStep estado = a.appendStep(consulta.id(), recorded("Selecionar Estado", ActionType.SELECT_OPTION, element("SELECT", "Estado", false),
                ActionParameters.withOption(new OptionChoice("São Paulo", "SP")), "ev-5"));
        a.editStep(estado.id(), StepEdit.of(a, estado).actionType(ActionType.CLICK).parameters(ActionParameters.none()));
        assertEquals(ValidationProblem.OPTION_NOT_ACCEPTED, assertThrows(DomainValidationException.class,
                () -> a.restoreOriginal(estado.id(), StepField.PARAMETERS)).problem(), "o tipo atual não aceita a opção original");
        assertTrue(a.restoreOriginal(estado.id(), StepField.ACTION_TYPE));
        assertTrue(a.restoreOriginal(estado.id(), StepField.PARAMETERS));
        assertTrue(estado.manualRevision().isEmpty(), "de volta ao capturado");
        assertEquals(ValidationProblem.NO_CAPTURED_ORIGINAL, assertThrows(DomainValidationException.class,
                () -> a.restoreOriginal(pesquisa.id(), StepField.NAME)).problem());
    }

    @Test
    @DisplayName("restaurar parâmetros de uma etapa de senha gravada não traz nenhum valor (nunca foi guardado)")
    void restaurarSenhaNaoTrazSegredo() {
        a.setStepParameters(senha.id(), ActionParameters.withValue(new ValueSource.CredentialReference("sistema.senha")));
        assertTrue(a.restoreOriginal(senha.id(), StepField.PARAMETERS));
        assertTrue(senha.parameters().isEmpty());
        assertTrue(senha.baseline().orElseThrow().parameters().isEmpty());
    }

    // ------------------------------------------------------------------ comentários

    @Test
    @DisplayName("S004-036/037/038 comentários: adicionar, editar (data de criação preservada) e excluir, com validação")
    void comentarios() {
        Instant created = clock.instant();
        a.addComment(pesquisa.id(), "Aguardar carregamento dos resultados.");
        clock.advance(Duration.ofMinutes(10));
        a.addComment(pesquisa.id(), "Segundo");
        assertEquals(List.of("Aguardar carregamento dos resultados.", "Segundo"), pesquisa.comments().stream().map(StepComment::text).toList());
        clock.advance(Duration.ofMinutes(5));
        assertTrue(a.editComment(pesquisa.id(), 0, "Aguardar a tabela de resultados."));
        StepComment c = pesquisa.comments().get(0);
        assertEquals("Aguardar a tabela de resultados.", c.text());
        assertEquals(created, c.createdAt(), "data de criação preservada");
        assertEquals(clock.instant(), c.editedAt());
        assertFalse(a.editComment(pesquisa.id(), 0, "Aguardar a tabela de resultados."), "mesmo texto: sem efeito");
        assertTrue(a.removeComment(pesquisa.id(), 1));
        assertEquals(1, pesquisa.comments().size());
        assertEquals(ValidationProblem.COMMENT_REQUIRED, assertThrows(DomainValidationException.class, () -> a.addComment(pesquisa.id(), "  ")).problem());
        assertEquals(ValidationProblem.COMMENT_TOO_LONG,
                assertThrows(DomainValidationException.class, () -> a.addComment(pesquisa.id(), "x".repeat(1001))).problem());
        assertEquals(ValidationProblem.COMMENT_INVALID_CHARACTERS,
                assertThrows(DomainValidationException.class, () -> a.addComment(pesquisa.id(), "a\u0007b")).problem());
        assertEquals(ValidationProblem.COMMENT_LOOKS_SENSITIVE,
                assertThrows(DomainValidationException.class, () -> a.addComment(pesquisa.id(), "senha do cpf 123.456.789-09")).problem());
        assertEquals(ValidationProblem.COMMENT_NOT_FOUND, assertThrows(DomainValidationException.class, () -> a.removeComment(pesquisa.id(), 5)).problem());
        assertTrue(a.addComment(pesquisa.id(), "Linha 1\nLinha 2"), "quebras de linha são permitidas");
        assertEquals(2, pesquisa.comments().size());
        assertTrue(pesquisa.manualRevision().isEmpty(), "comentário não é alteração do capturado");
    }

    // ------------------------------------------------------------------ histórico (fotografias)

    @Test
    @DisplayName("S004-045 fotografia restaura sessões e etapas excluídas com as mesmas identidades")
    void fotografia() {
        a.addComment(clientes.id(), "Menu");
        Automation.Snapshot before = a.snapshot();
        a.removeSession(auth.id());
        a.renameSession(consulta.id(), "Pesquisa");
        catalog.updateDetails(a.id(), "Outro nome", "");
        a.restore(before);
        assertEquals("Consulta de clientes", a.name());
        assertEquals(List.of("Autenticação", "Consulta"), a.sessions().stream().map(LogicalSession::name).toList());
        assertSame(auth, a.sessions().get(0), "a mesma sessão, não uma nova");
        assertSame(usuario, a.step(usuario.id()).orElseThrow(), "a mesma etapa");
        assertEquals("Menu", clientes.comments().get(0).text());
        assertEquals(List.of(1, 2, 3, 4, 5), numbers());
        Automation.Snapshot after = a.snapshot();
        a.moveStep(clientes.id(), consulta.id(), 0);
        a.restore(after);
        assertEquals(4, auth.stepCount());
        assertTrue(before.hasStep(senha.id()));
        assertEquals(auth.id(), before.sessionOf(clientes.id()).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> catalog.create("Z", null).restore(before), "fotografia de outra automação");
    }

    // ------------------------------------------------------------------ revisão técnica (P1/P2)

    static final List<String> UNSAFE_OPTION_TEXTS = List.of(
            "usuario@empresa.com",                                            // e-mail
            "123.456.789-09",                                                 // CPF
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.c2lnbmF0dXJh",    // JWT completo
            "eyJhbGciOiJIUzI1NiJ9",                                           // cabeçalho de JWT isolado
            "Bearer abcdefghijklmnop123",                                     // token Bearer
            "3f2a9c1e-7b44-4d1a-9a55-0c2e8f6d1b77",                           // UUID (identificador interno)
            "tgt-9f8e7d6c5b4a",                                               // identificador do contrato
            "a1b2c3d4e5f6a7b8c9d0");                                          // hash/hex longo

    @Test
    @DisplayName("REV-P1 OptionChoice recusa dado sensível ou identificador interno no rótulo e no valor técnico")
    void opcaoSensivelRecusada() {
        AutomationStep estado = a.appendStep(consulta.id(), recorded("Selecionar São Paulo no campo Estado", ActionType.SELECT_OPTION,
                element("SELECT", "Estado", false), ActionParameters.withOption(new OptionChoice("São Paulo", "SP")), "ev-5"));
        long rev = a.revision();
        for (String bad : UNSAFE_OPTION_TEXTS) {
            assertThrows(DomainValidationException.class, () -> new OptionChoice(bad, "SP"), "rótulo: " + bad);
            assertThrows(DomainValidationException.class, () -> new OptionChoice("São Paulo", bad), "valor: " + bad);
        }
        assertThrows(DomainValidationException.class, () -> new OptionChoice("linha\u0007", "SP"), "caractere de controle");
        assertThrows(DomainValidationException.class, () -> new OptionChoice("x".repeat(121), "SP"), "longo demais");
        assertEquals(new OptionChoice("São Paulo", "SP"), estado.parameters().option().orElseThrow(), "nada mudou");
        assertEquals(rev, a.revision());
        assertEquals("", new OptionChoice("Rio de Janeiro", null).value(), "valor técnico continua opcional");
    }

    @Test
    @DisplayName("REV-P2 comentário numa etapa gravada é intervenção manual (sem alterar campos capturados); excluir volta ao original")
    void comentarioEhIntervencaoManual() {
        assertTrue(entrar.manualRevision().isEmpty());
        a.addComment(entrar.id(), "Verificar se a mensagem de autenticação aparece.");
        ManualRevision r = entrar.manualRevision().orElseThrow();
        assertTrue(r.comments(), "comentário reconhecido como intervenção posterior à gravação");
        assertTrue(r.fields().isEmpty(), "nenhum campo capturado foi alterado");
        assertFalse(entrar.modified(StepField.NAME));
        a.removeComment(entrar.id(), 0);
        assertTrue(entrar.manualRevision().isEmpty(), "sem comentário e sem alteração: de volta ao capturado");
        Automation.Snapshot s = a.snapshot();
        a.addComment(entrar.id(), "Outro comentário");
        a.restore(s);
        assertTrue(entrar.manualRevision().isEmpty(), "desfazer o comentário também volta");
    }

    @Test
    @DisplayName("REV-P2 duplicar não reutiliza as chaves de gravação e de evento na referência do elemento")
    void duplicarSemChavesDoEventoNoElemento() {
        ElementReference withEventKeys = ElementReference.builder().semanticKind("BUTTON").accessibleName("Entrar").tag("button")
                .privacyPolicy("nekkora-privacy/1")
                .addLocator(new LocatorCandidate(LocatorStrategy.ID, "id \"entrar\"", "#entrar", LocatorStability.HEURISTIC_STABLE, 1, true))
                .preferLastLocator()
                .correlation(CorrelationKeys.of(Map.of(CorrelationKeys.Key.TARGET_ID, "tgt-1", CorrelationKeys.Key.NODE_ID, "n7",
                        CorrelationKeys.Key.RECORDING_ID, "rec-1", CorrelationKeys.Key.RECORDED_EVENT_ID, "ev-9")))
                .build();
        AutomationStep original = a.appendStep(auth.id(), recorded("Clicar no botão Entrar", ActionType.CLICK, withEventKeys, null, "ev-9"));
        AutomationStep copy = a.duplicateStep(original.id());
        CorrelationKeys keys = copy.element().orElseThrow().correlation();
        assertTrue(keys.get(CorrelationKeys.Key.RECORDING_ID).isEmpty(), "sem a gravação da original");
        assertTrue(keys.get(CorrelationKeys.Key.RECORDED_EVENT_ID).isEmpty(), "sem o evento da original");
        assertEquals("tgt-1", keys.get(CorrelationKeys.Key.TARGET_ID).orElseThrow(), "dados para reconhecer o elemento ficam");
        assertEquals("n7", keys.get(CorrelationKeys.Key.NODE_ID).orElseThrow());
        assertEquals(1, copy.element().orElseThrow().locators().size());
        assertEquals(original.id(), copy.derivedFrom().orElseThrow(), "o vínculo legítimo é derivedFrom");
        assertEquals("ev-9", original.element().orElseThrow().correlation().get(CorrelationKeys.Key.RECORDED_EVENT_ID).orElseThrow(),
                "a original continua intacta");
    }
}
