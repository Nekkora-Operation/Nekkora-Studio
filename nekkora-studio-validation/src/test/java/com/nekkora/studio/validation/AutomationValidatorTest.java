package com.nekkora.studio.validation;

import static com.nekkora.studio.validation.Fixtures.add;
import static com.nekkora.studio.validation.Fixtures.good;
import static com.nekkora.studio.validation.Fixtures.locator;
import static com.nekkora.studio.validation.Fixtures.manual;
import static com.nekkora.studio.validation.Fixtures.recorded;
import static com.nekkora.studio.validation.Fixtures.withElement;
import static com.nekkora.studio.validation.Fixtures.withSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.OptionChoice;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.element.ManualLocator;
import com.nekkora.studio.domain.element.ObservedObstacle;
import com.nekkora.studio.domain.evidence.EvidenceType;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Limitation;
import com.nekkora.studio.domain.evidence.ObservationSummary;
import com.nekkora.studio.domain.origin.RecordingObservation;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** STUDIO-007: regras do Automation Validation Engine (matriz S007, parte do motor — sem interface, sem navegador). */
class AutomationValidatorTest {

    private final AutomationValidator validator = new AutomationValidator();

    private static Set<FindingCode> codes(ValidationReport r, AutomationStep s) {
        return r.forStep(s.id()).stream().map(Finding::code).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static Set<FindingCode> codes(ValidationReport r) {
        return r.findings().stream().map(Finding::code).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    // ------------------------------------------------------------------ estrutura

    @Test
    @DisplayName("S007-002 — automação vazia: bloqueio de estrutura; estado Configuração incompleta")
    void s007002Vazia() {
        Automation a = Automation.create("Consulta de Clientes", "", Fixtures.CLOCK);
        ValidationReport r = validator.validate(a);
        assertEquals(List.of(FindingCode.AUTOMATION_HAS_NO_SESSIONS), r.findings().stream().map(Finding::code).toList());
        assertEquals(FindingScope.AUTOMATION, r.findings().get(0).scope());
        assertEquals(ValidationStatus.INCOMPLETE, r.status());
        assertEquals(new ValidationReport.Summary(0, 0, 1, 0, 0, 0, 0), r.summary());
    }

    @Test
    @DisplayName("S007-003 — sessão vazia: aviso na sessão; só sessões vazias = automação sem etapas (bloqueio)")
    void s007003SessaoVazia() {
        Automation a = withSession("Consulta");
        ValidationReport only = validator.validate(a);
        assertEquals(List.of(FindingCode.AUTOMATION_HAS_NO_STEPS, FindingCode.SESSION_EMPTY), only.findings().stream().map(Finding::code).toList());
        LogicalSession pesquisa = a.addSession("Pesquisa", null);
        a.appendStep(pesquisa.id(), withElement("Clicar em Pesquisar", ActionType.CLICK, good()));
        ValidationReport r = validator.validate(a);
        Finding empty = r.findings().get(0);
        assertEquals(FindingCode.SESSION_EMPTY, empty.code());
        assertEquals(Severity.WARNING, empty.severity());
        assertEquals(a.sessions().get(0).id(), empty.session());
        assertEquals(1, empty.sessionNumber());
        assertFalse(codes(r).contains(FindingCode.AUTOMATION_HAS_NO_STEPS));
        assertEquals(ValidationStatus.NO_STATIC_BLOCKERS, r.status());
    }

    // ------------------------------------------------------------------ ações

    @Test
    @DisplayName("S007-004 — CLICK configurado: só a informação histórica do localizador; sem bloqueios estáticos (não é 'pronta para executar')")
    void s007004ClickConfigurado() {
        Automation a = withSession("Login");
        AutomationStep s = add(a, withElement("Clicar em Entrar", ActionType.CLICK, good()));
        ValidationReport r = validator.validate(a);
        assertEquals(Set.of(FindingCode.LOCATOR_VALIDATED_AT_CAPTURE), codes(r, s));
        assertEquals(ValidationStatus.NO_STATIC_BLOCKERS, r.status());
        assertTrue(r.findings().get(0).explanation().contains("Sua disponibilidade atual não foi verificada"));
        assertTrue(ValidationStatus.NO_STATIC_BLOCKERS.explanation().contains("NÃO significa que a automação está pronta para execução"));
        assertEquals(0, r.summary().stepsWithoutFindings(), "a informação conta como apontamento");
    }

    @Test
    @DisplayName("S007-005/012/013/019 — CLICK, CHECK, UNCHECK e FILL sem elemento: bloqueio ELEMENT_MISSING; a etapa continua existindo")
    void s007005SemElemento() {
        Automation a = withSession("Login");
        for (ActionType t : List.of(ActionType.CLICK, ActionType.CHECK, ActionType.UNCHECK, ActionType.FILL)) {
            AutomationStep s = add(a, manual("Etapa " + t, t, t == ActionType.FILL ? ActionParameters.withValue(new ValueSource.LiteralValue("termo")) : ActionParameters.none()));
            ValidationReport r = validator.validate(a);
            assertTrue(codes(r, s).contains(FindingCode.ELEMENT_MISSING), t + ": " + codes(r, s));
            assertEquals(Severity.BLOCKER, r.forStep(s.id()).stream().filter(f -> f.code() == FindingCode.ELEMENT_MISSING).findFirst().orElseThrow().severity());
        }
        assertEquals(4, a.stepCount(), "o motor aponta, não remove nem impede a edição");
    }

    @Test
    @DisplayName("S007-006/007/008/009 — FILL: sem valor = bloqueio; literal = ok; variável e credencial = dependência não comprovada (avaliação parcial)")
    void s007006a009Fill() {
        Automation a = withSession("Login");
        AutomationStep none = add(a, withElement("Preencher pesquisa", ActionType.FILL, good()));
        ValidationReport r = validator.validate(a);
        assertTrue(codes(r, none).contains(FindingCode.REQUIRED_VALUE_MISSING));
        assertEquals(ValidationStatus.INCOMPLETE, r.status());

        Automation b = withSession("Login");
        AutomationStep literal = add(b, withElement("Preencher pesquisa", ActionType.FILL, good(), ActionParameters.withValue(new ValueSource.LiteralValue("Silva"))));
        ValidationReport rb = validator.validate(b);
        assertEquals(Set.of(FindingCode.LOCATOR_VALIDATED_AT_CAPTURE), codes(rb, literal));
        assertEquals(ValidationStatus.NO_STATIC_BLOCKERS, rb.status());

        Automation c = withSession("Login");
        AutomationStep variable = add(c, withElement("Preencher pesquisa", ActionType.FILL, good(),
                ActionParameters.withValue(new ValueSource.VariableReference("cliente.nome"))));
        ValidationReport rc = validator.validate(c);
        assertTrue(codes(rc, variable).contains(FindingCode.VARIABLE_RESOLUTION_UNAVAILABLE));
        assertEquals(Severity.WARNING, FindingCode.VARIABLE_RESOLUTION_UNAVAILABLE.severity());
        assertEquals(ValidationStatus.NOT_FULLY_ASSESSABLE, rc.status(), "não afirma que a variável existe");

        Automation d = withSession("Login");
        AutomationStep credential = add(d, withElement("Preencher senha", ActionType.FILL, Fixtures.password(),
                ActionParameters.withValue(new ValueSource.CredentialReference("sistema.usuario"))));
        ValidationReport rd = validator.validate(d);
        assertTrue(codes(rd, credential).contains(FindingCode.CREDENTIAL_RESOLUTION_UNAVAILABLE));
        assertFalse(codes(rd, credential).contains(FindingCode.SENSITIVE_FIELD_WITHOUT_CREDENTIAL));
        assertEquals(ValidationStatus.NOT_FULLY_ASSESSABLE, rd.status());
        assertTrue(FindingCode.CREDENTIAL_RESOLUTION_UNAVAILABLE.explanation().contains("não consulta nenhum cofre"));

        Automation e = withSession("Login");
        AutomationStep sensitive = add(e, withElement("Preencher senha", ActionType.FILL, Fixtures.password(),
                ActionParameters.withValue(new ValueSource.VariableReference("sistema.senha"))));
        assertTrue(codes(validator.validate(e), sensitive).contains(FindingCode.SENSITIVE_FIELD_WITHOUT_CREDENTIAL));
    }

    @Test
    @DisplayName("S007-010/011 — SELECT_OPTION: sem opção = bloqueio; com opção = sem bloqueio")
    void s007010e011Select() {
        Automation a = withSession("Consulta");
        AutomationStep none = add(a, withElement("Selecionar estado", ActionType.SELECT_OPTION, good()));
        AutomationStep with = add(a, withElement("Selecionar cidade", ActionType.SELECT_OPTION, good(),
                ActionParameters.withOption(new OptionChoice("São Paulo", "SP"))));
        ValidationReport r = validator.validate(a);
        assertTrue(codes(r, none).contains(FindingCode.OPTION_NOT_CONFIGURED));
        assertEquals(Set.of(FindingCode.LOCATOR_VALIDATED_AT_CAPTURE), codes(r, with));
    }

    @Test
    @DisplayName("S007-014 — PRESS_KEY: sem tecla = bloqueio; tecla fora da lista = aviso; Enter = ok")
    void s007014PressKey() {
        Automation a = withSession("Login");
        AutomationStep none = add(a, withElement("Pressionar tecla", ActionType.PRESS_KEY, good()));
        AutomationStep odd = add(a, withElement("Pressionar tecla", ActionType.PRESS_KEY, good(), ActionParameters.withValue(new ValueSource.LiteralValue("Enterr"))));
        AutomationStep enter = add(a, withElement("Pressionar Enter", ActionType.PRESS_KEY, good(), ActionParameters.withValue(new ValueSource.LiteralValue("Enter"))));
        ValidationReport r = validator.validate(a);
        assertTrue(codes(r, none).contains(FindingCode.KEY_NOT_CONFIGURED));
        assertTrue(codes(r, odd).contains(FindingCode.KEY_NOT_RECOGNIZED));
        assertEquals(Set.of(FindingCode.LOCATOR_VALIDATED_AT_CAPTURE), codes(r, enter));
    }

    @Test
    @DisplayName("S007-015 — NAVIGATE: sem valor = bloqueio; literal que não é http(s) = aviso; endereço ou variável = sem bloqueio; nenhum elemento exigido")
    void s007015Navigate() {
        Automation a = withSession("Portal");
        AutomationStep none = add(a, manual("Abrir portal", ActionType.NAVIGATE));
        AutomationStep odd = add(a, manual("Abrir portal", ActionType.NAVIGATE, ActionParameters.withValue(new ValueSource.LiteralValue("portal interno"))));
        AutomationStep url = add(a, manual("Abrir portal", ActionType.NAVIGATE, ActionParameters.withValue(new ValueSource.LiteralValue("https://portal.exemplo.test/"))));
        ValidationReport r = validator.validate(a);
        assertEquals(Set.of(FindingCode.NAVIGATION_TARGET_MISSING), codes(r, none), "sem ELEMENT_MISSING: navegar não atua sobre elemento");
        assertEquals(Set.of(FindingCode.NAVIGATION_TARGET_NOT_ADDRESS), codes(r, odd));
        assertEquals(Set.of(), codes(r, url));
    }

    @Test
    @DisplayName("S007-016/017/018 — WAIT_FOR (condição não modelada), OBSERVE (não é validação de negócio) e UNKNOWN (bloqueio de classificação)")
    void s007016a018() {
        Automation a = withSession("Processo");
        AutomationStep wait = add(a, manual("Aguardar resultado", ActionType.WAIT_FOR));
        AutomationStep observe = add(a, manual("Observar mensagem", ActionType.OBSERVE));
        ValidationReport r = validator.validate(a);
        assertEquals(Set.of(FindingCode.WAIT_CONDITION_NOT_MODELED), codes(r, wait), "elemento é opcional: sem ELEMENT_MISSING");
        assertTrue(FindingCode.WAIT_CONDITION_NOT_MODELED.limitsAssessment());
        assertEquals(Set.of(FindingCode.OBSERVE_IS_NOT_VALIDATION), codes(r, observe));
        assertEquals(Severity.INFO, FindingCode.OBSERVE_IS_NOT_VALIDATION.severity());
        assertEquals(ValidationStatus.NOT_FULLY_ASSESSABLE, r.status());

        Automation b = withSession("Processo");
        AutomationStep unknown = add(b, recorded("Ação não classificada", ActionType.UNKNOWN, "ev-1", null));
        ValidationReport rb = validator.validate(b);
        assertEquals(Set.of(FindingCode.ACTION_UNCLASSIFIED), codes(rb, unknown));
        assertEquals(ValidationStatus.INCOMPLETE, rb.status());
    }

    // ------------------------------------------------------------------ elementos e localizadores

    @Test
    @DisplayName("S007-020 — localizador ausente (elemento identificado sem localizador) ≠ elemento ausente")
    void s007020LocalizadorAusente() {
        Automation a = withSession("Login");
        ElementReference noLocator = ElementReference.builder().semanticKind("BUTTON").tag("button").build();
        AutomationStep s = add(a, recorded("Clicar no botão", ActionType.CLICK, "ev-1", noLocator, "SAFE_LOCATOR_UNAVAILABLE"));
        ValidationReport r = validator.validate(a);
        assertEquals(Set.of(FindingCode.LOCATOR_MISSING, FindingCode.RECORDER_SAFE_LOCATOR_UNAVAILABLE), codes(r, s));
        assertFalse(codes(r, s).contains(FindingCode.ELEMENT_MISSING));
    }

    @Test
    @DisplayName("S007-021 — localizador validado na captura: informação HISTÓRICA, nunca 'elemento disponível'")
    void s007021ValidadoNaCaptura() {
        Automation a = withSession("Login");
        add(a, withElement("Clicar em Entrar", ActionType.CLICK, good()));
        Finding f = validator.validate(a).findings().get(0);
        assertEquals(FindingCode.LOCATOR_VALIDATED_AT_CAPTURE, f.code());
        assertEquals(Severity.INFO, f.severity());
        assertTrue(f.explanation().contains("validado na captura"));
        for (FindingCode c : FindingCode.values()) {
            assertFalse(c.explanation().toLowerCase().contains("garantidamente") || c.explanation().toLowerCase().contains("disponível agora"), c.name());
        }
    }

    @Test
    @DisplayName("S007-022 — localizador manual não verificado: único = aviso; ao lado de capturados = informação")
    void s007022Manual() {
        Automation a = withSession("Login");
        ElementReference noLocator = ElementReference.builder().semanticKind("BUTTON").tag("button").build();
        AutomationStep only = add(a, recorded("Clicar no botão", ActionType.CLICK, "ev-1", noLocator));
        a.addManualLocator(only.id(), new ManualLocator(ManualLocator.Syntax.CSS, "form button.primario", "", Fixtures.T));
        AutomationStep extra = add(a, recorded("Clicar em Entrar", ActionType.CLICK, "ev-2", good()));
        a.addManualLocator(extra.id(), new ManualLocator(ManualLocator.Syntax.XPATH, "//button[1]", "", Fixtures.T));
        ValidationReport r = validator.validate(a);
        assertEquals(Set.of(FindingCode.MANUAL_LOCATOR_ONLY), codes(r, only));
        assertEquals(Set.of(FindingCode.LOCATOR_VALIDATED_AT_CAPTURE, FindingCode.MANUAL_LOCATOR_UNVERIFIED), codes(r, extra));
    }

    @Test
    @DisplayName("S007-023/024 — múltiplos resultados históricos; não validado; estabilidade desconhecida, instável, posicional; preferencial não escolhido")
    void s007023e024() {
        Automation a = withSession("Login");
        AutomationStep multi = add(a, withElement("Clicar", ActionType.CLICK, ElementReference.builder().semanticKind("BUTTON")
                .addLocator(locator(LocatorStrategy.TEXT, LocatorStability.HEURISTIC_STABLE, 3, true)).build()));
        AutomationStep missed = add(a, withElement("Clicar", ActionType.CLICK, ElementReference.builder().semanticKind("BUTTON")
                .addLocator(locator(LocatorStrategy.ATTRIBUTE, LocatorStability.HEURISTIC_STABLE, 1, false)).build()));
        AutomationStep unknown = add(a, withElement("Clicar", ActionType.CLICK, ElementReference.builder().semanticKind("BUTTON")
                .addLocator(locator(LocatorStrategy.ID, LocatorStability.UNKNOWN, 1, true)).preferLastLocator().build()));
        AutomationStep unstable = add(a, withElement("Clicar", ActionType.CLICK, ElementReference.builder().semanticKind("BUTTON")
                .addLocator(locator(LocatorStrategy.POSITIONAL, LocatorStability.OBSERVED_UNSTABLE, 1, true)).preferLastLocator().build()));
        AutomationStep noPreference = add(a, withElement("Clicar", ActionType.CLICK, ElementReference.builder().semanticKind("BUTTON")
                .addLocator(locator(LocatorStrategy.ID, LocatorStability.OBSERVED_STABLE, 1, true)).build()));
        ValidationReport r = validator.validate(a);
        assertEquals(Set.of(FindingCode.LOCATOR_MULTIPLE_MATCHES), codes(r, multi));
        assertEquals(Set.of(FindingCode.LOCATOR_NOT_VALIDATED), codes(r, missed));
        assertEquals(Set.of(FindingCode.LOCATOR_VALIDATED_AT_CAPTURE, FindingCode.LOCATOR_STABILITY_UNKNOWN), codes(r, unknown));
        assertEquals(Set.of(FindingCode.LOCATOR_VALIDATED_AT_CAPTURE, FindingCode.LOCATOR_UNSTABLE, FindingCode.LOCATOR_POSITIONAL), codes(r, unstable));
        assertEquals(Set.of(FindingCode.LOCATOR_VALIDATED_AT_CAPTURE, FindingCode.LOCATOR_PREFERENCE_NOT_SET), codes(r, noPreference));
        assertEquals(ValidationStatus.NO_STATIC_BLOCKERS, r.status(), "riscos são avisos, não bloqueios");
    }

    @Test
    @DisplayName("S007-025 — obstáculos observados: severidade pela gravidade; tipo só do vocabulário (desconhecido = OTHER)")
    void s007025Obstaculos() {
        Automation a = withSession("Login");
        ElementReference e = ElementReference.builder().semanticKind("BUTTON")
                .addLocator(locator(LocatorStrategy.ID, LocatorStability.OBSERVED_STABLE, 1, true)).preferLastLocator()
                .addObstacle(new ObservedObstacle("ELEMENT_UNRESOLVED", ObservedObstacle.Severity.HIGH, "texto do produtor"))
                .addObstacle(new ObservedObstacle("SHADOW_DOM", ObservedObstacle.Severity.MEDIUM, ""))
                .addObstacle(new ObservedObstacle("ASYNC_LOADING", ObservedObstacle.Severity.LOW, ""))
                .addObstacle(new ObservedObstacle("tipo inventado <script>", ObservedObstacle.Severity.LOW, "")).build();
        AutomationStep s = add(a, withElement("Clicar", ActionType.CLICK, e));
        List<Finding> f = validator.validate(a).forStep(s.id());
        assertEquals(List.of("LOCATOR_VALIDATED_AT_CAPTURE:null", "OBSTACLE_HIGH:ELEMENT_UNRESOLVED", "OBSTACLE_MEDIUM:SHADOW_DOM",
                "OBSTACLE_LOW:ASYNC_LOADING", "OBSTACLE_LOW:OTHER"), f.stream().map(x -> x.code() + ":" + x.detail()).toList());
        assertTrue(f.get(1).explanation().contains("elemento não correlacionado"));
        assertFalse(f.stream().anyMatch(x -> x.explanation().contains("texto do produtor") || x.explanation().contains("script")));
    }

    @Test
    @DisplayName("S007-026 — privacidade v2: nome omitido não é defeito; limitações de privacidade são informações, nunca bloqueio nem sugestão de desativar a proteção")
    void s007026Privacidade() {
        Automation a = withSession("Login");
        ElementReference omitted = ElementReference.builder().accessibleName("").semanticKind("BUTTON").tag("button")
                .addLocator(locator(LocatorStrategy.ROLE_NAME, LocatorStability.HEURISTIC_STABLE, 1, true)).preferLastLocator()
                .privacyPolicy("nekkora-privacy/2").build();
        AutomationStep click = add(a, recorded("Clicar no botão", ActionType.CLICK, "ev-1", omitted, "LOCATORS_WITHHELD"));
        AutomationStep select = add(a, recorded("Selecionar opção", ActionType.SELECT_OPTION, "ev-2", omitted, "OPTION_WITHHELD"));
        ValidationReport r = validator.validate(a);
        assertEquals(Set.of(FindingCode.LOCATOR_VALIDATED_AT_CAPTURE, FindingCode.PRIVACY_LOCATORS_WITHHELD), codes(r, click), "nome vazio não gera apontamento");
        assertEquals(Set.of(FindingCode.OPTION_NOT_CONFIGURED, FindingCode.LOCATOR_VALIDATED_AT_CAPTURE, FindingCode.PRIVACY_OPTION_WITHHELD), codes(r, select));
        assertEquals(Severity.INFO, FindingCode.PRIVACY_LOCATORS_WITHHELD.severity());
        assertTrue(FindingCode.PRIVACY_LOCATORS_WITHHELD.explanation().contains("não é defeito da captura"));
        for (FindingCode c : FindingCode.values()) {
            String t = (c.explanation() + " " + c.guidance()).toLowerCase();
            assertFalse(t.contains("desativ") && !t.contains("nada da proteção de privacidade deve ser desativado"), "nunca sugere desativar a privacidade: " + c);
        }
        a.setStepParameters(select.id(), ActionParameters.withOption(new OptionChoice("São Paulo", "SP")));
        assertEquals(Set.of(FindingCode.LOCATOR_VALIDATED_AT_CAPTURE), codes(validator.validate(a), select), "opção configurada: nada a explicar");
    }

    // ------------------------------------------------------------------ evidências (STUDIO-005)

    @Test
    @DisplayName("S007-028/029/030 — evidências: OBSERVED e indicador de erro são informação; INCOMPLETE não é falha; resultado de negócio continua NOT_VALIDATED; evidência não resolve bloqueio")
    void s007028a030Evidencias() {
        Automation a = withSession("Login");
        AutomationStep fill = add(a, recorded("Preencher usuário", ActionType.FILL, "ev-1", good()));
        AutomationStep click = add(a, recorded("Clicar em Entrar", ActionType.CLICK, "ev-2", good()));
        AutomationStep cut = add(a, recorded("Clicar em Ajuda", ActionType.CLICK, "ev-3", good()));
        a.attachEvidence(Fixtures.evidence("evd-1", 1, "ev-1", EvidenceType.FORM_SUBMITTED));
        a.attachEvidence(Fixtures.evidence("evd-2", 2, "ev-1", EvidenceType.OBSERVATION_COMPLETE));
        a.attachEvidence(Fixtures.evidence("evd-3", 3, "ev-2", EvidenceType.VISIBLE_ERROR_MESSAGE, Limitation.MESSAGE_SUPPRESSED));
        a.attachEvidence(Fixtures.evidence("evd-4", 4, "ev-2", EvidenceType.OBSERVATION_COMPLETE));
        a.attachEvidence(Fixtures.evidence("evd-5", 5, "ev-3", EvidenceType.OBSERVATION_INCOMPLETE, Limitation.WINDOW_CUT_BY_PAUSE));
        ValidationReport r = validator.validate(a);
        assertTrue(codes(r, fill).contains(FindingCode.REQUIRED_VALUE_MISSING), "evidência não resolve o bloqueio de parâmetro");
        assertTrue(codes(r, fill).contains(FindingCode.OBSERVATION_SIGNALS));
        assertEquals(Set.of(FindingCode.LOCATOR_VALIDATED_AT_CAPTURE, FindingCode.OBSERVATION_SIGNALS, FindingCode.OBSERVATION_ERROR_INDICATOR), codes(r, click));
        assertEquals(Set.of(FindingCode.LOCATOR_VALIDATED_AT_CAPTURE, FindingCode.OBSERVATION_INCOMPLETE), codes(r, cut));
        for (Finding f : r.findings()) {
            if (f.field() == FindingField.OBSERVATION) assertEquals(Severity.INFO, f.severity(), "observação nunca é bloqueio nem aviso: " + f.code());
        }
        String err = FindingCode.OBSERVATION_ERROR_INDICATOR.explanation();
        assertTrue(err.contains("não conclui que a operação falhou, nem que foi validada"));
        assertEquals(RecordingObservation.BusinessOutcome.NOT_VALIDATED, ObservationSummary.of(click).businessOutcome());
        String json = r.toJson();
        assertFalse(json.contains("\"VALIDATED\"") || json.contains("SUCCESS") || json.contains("PASSED"), "nenhum resultado de negócio no relatório");

        Automation b = withSession("Login");
        AutomationStep noEvidence = add(b, recorded("Clicar em Entrar", ActionType.CLICK, "ev-1", good()));
        assertEquals(Set.of(FindingCode.LOCATOR_VALIDATED_AT_CAPTURE), codes(validator.validate(b), noEvidence), "sem evidência ≠ mal configurada");
    }

    // ------------------------------------------------------------------ determinismo e identidade

    private Automation rich() {
        Automation a = withSession("Consulta de Clientes");
        add(a, recorded("Preencher usuário", ActionType.FILL, "ev-1", good()));
        add(a, withElement("Preencher senha", ActionType.FILL, Fixtures.password(), ActionParameters.withValue(new ValueSource.CredentialReference("sistema.senha"))));
        add(a, withElement("Clicar em Entrar", ActionType.CLICK, good()));
        LogicalSession consulta = a.addSession("Consulta", null);
        a.appendStep(consulta.id(), manual("Abrir Clientes", ActionType.NAVIGATE, ActionParameters.withValue(new ValueSource.LiteralValue("https://lab.exemplo.test/clientes"))));
        a.appendStep(consulta.id(), withElement("Preencher pesquisa", ActionType.FILL, good()));
        a.appendStep(consulta.id(), withElement("Selecionar estado", ActionType.SELECT_OPTION, good()));
        a.appendStep(consulta.id(), withElement("Clicar em Pesquisar", ActionType.CLICK, good()));
        a.addSession("Encerramento", null);
        return a;
    }

    @Test
    @DisplayName("S007-031 — ordem determinística: automação → sessões → etapas → regras")
    void s007031Ordem() {
        ValidationReport r = validator.validate(rich());
        int lastSession = 0;
        int lastStep = 0;
        int lastRule = -1;
        for (Finding f : r.findings()) {
            assertTrue(f.sessionNumber() >= lastSession, f.id());
            if (f.sessionNumber() > lastSession) {
                lastStep = 0;
                lastRule = -1;
            }
            assertTrue(f.stepNumber() >= lastStep, f.id());
            if (f.stepNumber() > lastStep) lastRule = -1;
            assertTrue(f.code().ordinal() > lastRule || f.detail() != null, "regras em ordem dentro da etapa: " + f.id());
            lastSession = f.sessionNumber();
            lastStep = f.stepNumber();
            lastRule = f.code().ordinal();
        }
        assertEquals(FindingCode.SESSION_EMPTY, r.findings().get(r.findings().size() - 1).code(), "sessão 3 (vazia) por último");
    }

    @Test
    @DisplayName("S007-032/033 — identificadores estáveis e validação repetida idêntica (mesmo objeto, mesmos bytes); edição não muda os ids dos outros apontamentos")
    void s007032e033Estaveis() {
        Automation a = rich();
        ValidationReport first = validator.validate(a);
        ValidationReport again = validator.validate(a);
        assertEquals(first, again);
        assertEquals(first.toJson(), again.toJson());
        assertEquals(first.findings().size(), first.findings().stream().map(Finding::id).distinct().count(), "ids únicos");
        AutomationStep search = a.sessions().get(1).steps().get(1);
        a.setStepParameters(search.id(), ActionParameters.withValue(new ValueSource.LiteralValue("Silva")));
        ValidationReport after = validator.validate(a);
        assertNotEquals(first, after);
        Set<String> before = first.findings().stream().filter(f -> !search.id().equals(f.step())).map(Finding::id).collect(Collectors.toSet());
        Set<String> now = after.findings().stream().filter(f -> !search.id().equals(f.step())).map(Finding::id).collect(Collectors.toSet());
        assertEquals(before, now, "os demais apontamentos mantêm os mesmos ids");
        assertTrue(first.findings().stream().anyMatch(f -> f.id().equals("REQUIRED_VALUE_MISSING:" + search.id().value())));
    }

    @Test
    @DisplayName("S007-038 (motor) — validar NÃO altera a automação: revisão, instante, etapas, evidências e parâmetros iguais")
    void naoAltera() {
        Automation a = rich();
        long revision = a.revision();
        var updated = a.updatedAt();
        List<String> before = a.sequence().stream().map(Object::toString).toList();
        List<ActionParameters> params = a.sessions().stream().flatMap(s -> s.steps().stream()).map(AutomationStep::parameters).toList();
        for (int i = 0; i < 3; i++) validator.validate(a);
        assertEquals(revision, a.revision());
        assertEquals(updated, a.updatedAt());
        assertEquals(before, a.sequence().stream().map(Object::toString).toList());
        assertEquals(params, a.sessions().stream().flatMap(s -> s.steps().stream()).map(AutomationStep::parameters).toList());
    }

    @Test
    @DisplayName("relatório imutável (CA-009): listas somente leitura")
    void imutavel() {
        ValidationReport r = validator.validate(rich());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () -> r.findings().clear());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () -> r.forStep(r.findings().get(0).step()).clear());
    }

    @Test
    @DisplayName("exemplo da missão: 7 etapas, bloqueios, avisos e etapas sem apontamentos contados")
    void exemploDaMissao() {
        ValidationReport r = validator.validate(rich());
        assertEquals(3, r.summary().sessions());
        assertEquals(7, r.summary().steps());
        assertEquals(ValidationStatus.INCOMPLETE, r.status());
        assertEquals(3, r.summary().stepsWithBlockers(), "usuário sem valor, pesquisa sem valor, estado sem opção");
        assertEquals(1, r.summary().stepsWithoutFindings(), "navegar para um endereço");
    }
}
