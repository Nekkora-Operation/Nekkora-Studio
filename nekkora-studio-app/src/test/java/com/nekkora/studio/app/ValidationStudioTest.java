package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.app.recorder.RecorderPrompts;
import com.nekkora.studio.app.recorder.RecorderWindowHost;
import com.nekkora.studio.app.ui.StudioActions;
import com.nekkora.studio.app.workspace.ProjectService;
import com.nekkora.studio.app.workspace.ProjectState;
import com.nekkora.studio.app.workspace.Selection;
import com.nekkora.studio.app.workspace.ValidationService;
import com.nekkora.studio.bridge.BrowserLink;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.evidence.EvidenceType;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary;
import com.nekkora.studio.domain.evidence.ObservedEvidence;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepProvenance;
import com.nekkora.studio.ui.ValidationPanel;
import com.nekkora.studio.validation.Finding;
import com.nekkora.studio.validation.FindingCode;
import com.nekkora.studio.validation.ValidationReport;
import com.nekkora.studio.validation.ValidationStatus;
import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** STUDIO-007: validação no Studio montado como na aplicação (sem janela, usuário roteirizado, disco real). */
class ValidationStudioTest {

    @TempDir
    Path dir;

    private final ScriptedPrompts prompts = new ScriptedPrompts();
    private final List<Studio> started = new ArrayList<>();
    private Studio studio;

    private Studio start(StudioEnvironment env, BrowserLink link) {
        Edt.theme(NekkoraThemes.SAKURA_DARK);
        Studio s = Edt.call(() -> Studio.create(StudioFixture.CLOCK, prompts, () -> { }, link, RecorderPrompts.none(), p -> RecorderWindowHost.headless(), env));
        Edt.render(s.view().component(), 1440, 900);
        started.add(s);
        studio = s;
        return s;
    }

    private Studio start() {
        return start(StudioEnvironment.inMemory(), BrowserLink.offline("DISABLED"));
    }

    @AfterEach
    void tearDown() {
        for (Studio s : started) Edt.run(s::dispose);
    }

    private static <T> T await(CompletableFuture<T> f) throws Exception {
        T t = f.get(15, TimeUnit.SECONDS);
        Edt.flush();
        return t;
    }

    /** "Consulta de Clientes": uma etapa FILL sem valor, uma SELECT sem opção e um clique configurado (gravados). */
    private Automation automation() {
        prompts.then(ScriptedPrompts.name("Consulta de Clientes", "Exemplo fictício"));
        Edt.run(() -> studio.controller().newAutomation());
        prompts.then(ScriptedPrompts.name("Pesquisa", null));
        Edt.run(() -> studio.controller().newSession());
        Automation a = studio.state().activeAutomation().orElseThrow();
        Edt.run(() -> {
            studio.service().appendStep(a.id(), a.sessions().get(0).id(), recorded("Preencher pesquisa", ActionType.FILL, "ev-1"));
            studio.service().appendStep(a.id(), a.sessions().get(0).id(), recorded("Selecionar estado", ActionType.SELECT_OPTION, "ev-2"));
            studio.service().appendStep(a.id(), a.sessions().get(0).id(), recorded("Clicar em Pesquisar", ActionType.CLICK, "ev-3"));
        });
        return a;
    }

    private static final Map<String, String> ID = Map.of("ev-1", "pesquisa", "ev-2", "estado", "ev-3", "pesquisar", "ev-4", "exportar");

    private static StepDraft recorded(String name, ActionType type, String eventId) {
        ElementReference e = ElementReference.builder().semanticKind("BUTTON").tag("button").privacyPolicy("nekkora-privacy/2")
                .addLocator(new LocatorCandidate(LocatorStrategy.ID, "button {id=" + ID.get(eventId) + "}", "#" + ID.get(eventId), LocatorStability.OBSERVED_STABLE, 1, true))
                .preferLastLocator().build();
        CorrelationKeys keys = CorrelationKeys.of(Map.of(CorrelationKeys.Key.RECORDING_ID, "rec-1", CorrelationKeys.Key.RECORDED_EVENT_ID, eventId));
        RecordingObservation obs = new RecordingObservation(RecordingObservation.CaptureMode.CAPTURE_AND_INTERACT,
                RecordingObservation.Observation.INTERACTION_OBSERVED, null, List.of(), List.of());
        return StepDraft.builder().name(name).actionType(type).element(e).provenance(StepProvenance.recorded(keys, obs)).build();
    }

    private static ObservedEvidence evidence(String id, long seq, String eventId, EvidenceType type) {
        boolean error = type == EvidenceType.VISIBLE_ERROR_MESSAGE;
        return new ObservedEvidence(id, seq, "rec-1", eventId, Instant.parse("2026-10-16T12:00:00Z").plusSeconds(seq), type,
                EvidenceVocabulary.Source.FEEDBACK_OBSERVER, EvidenceVocabulary.Relation.AFTER_ACTION, EvidenceVocabulary.Confidence.TEMPORALLY_ASSOCIATED,
                error ? EvidenceVocabulary.Classification.ERROR_INDICATOR : null, null, "alerta", error ? ObservedEvidence.Message.suppressed()
                        : ObservedEvidence.Message.none(), null, null, null, List.of(EvidenceVocabulary.Signal.ROLE_ALERT), 0, 0,
                error ? List.of(EvidenceVocabulary.Limitation.MESSAGE_SUPPRESSED) : List.of());
    }

    private ValidationReport validate() {
        Edt.run(() -> studio.controller().validateActive());
        return Edt.call(() -> studio.validation().result(studio.state().activeAutomation().orElseThrow().id()).orElseThrow().report());
    }

    private ValidationService.Result result(Automation a) {
        return Edt.call(() -> studio.validation().result(a.id()).orElseThrow());
    }

    private static Set<FindingCode> codes(ValidationReport r, AutomationStep s) {
        return r.forStep(s.id()).stream().map(Finding::code).collect(Collectors.toSet());
    }

    private ValidationPanel panel() {
        return studio.view().validationPanel();
    }

    private ProjectState project(Automation a) {
        return Edt.call(() -> studio.state().project(a.id()).orElseThrow());
    }

    // ================================================================================================ testes

    @Test
    @DisplayName("S007-034/035/036/040 — validação após edição, Undo e Redo acompanha o estado EFETIVO; validar não entra no histórico")
    void s007034a036EdicaoUndoRedo() {
        start();
        Automation a = automation();
        AutomationStep fill = a.sessions().get(0).steps().get(0);
        assertTrue(codes(validate(), fill).contains(FindingCode.REQUIRED_VALUE_MISSING));
        String undoBefore = Edt.call(() -> studio.service().history().undoLabel(a.id()).orElse(""));
        boolean canUndo = Edt.call(() -> studio.service().canUndo(a.id()));
        validate();
        assertEquals(undoBefore, Edt.call(() -> studio.service().history().undoLabel(a.id()).orElse("")), "S007-040: validar não é uma edição");
        assertEquals(canUndo, Edt.call(() -> studio.service().canUndo(a.id())));
        assertFalse(Edt.call(() -> studio.service().canRedo(a.id())));

        Edt.run(() -> studio.service().setStepParameters(a.id(), fill.id(), ActionParameters.withValue(new ValueSource.VariableReference("cliente.nome"))));
        assertFalse(result(a).current(), "editar deixa o relatório anterior desatualizado");
        ValidationReport afterEdit = validate();
        assertFalse(codes(afterEdit, fill).contains(FindingCode.REQUIRED_VALUE_MISSING), "S007-034");
        // STUDIO-008 (ajuste autorizado): com o catálogo de variáveis, uma referência sem declaração é VARIABLE_NOT_DECLARED
        assertTrue(codes(afterEdit, fill).contains(FindingCode.VARIABLE_NOT_DECLARED));

        Edt.run(() -> studio.service().undo(a.id()));
        assertFalse(result(a).current());
        assertTrue(codes(validate(), fill).contains(FindingCode.REQUIRED_VALUE_MISSING), "S007-035: o bloqueio reaparece depois do Ctrl+Z");

        Edt.run(() -> studio.service().redo(a.id()));
        assertFalse(codes(validate(), fill).contains(FindingCode.REQUIRED_VALUE_MISSING), "S007-036: some de novo depois do Ctrl+Y");
        assertTrue(result(a).current());
    }

    @Test
    @DisplayName("S007-037/038/039 — projeto salvo e reaberto: valida igual; validar não altera o arquivo, não marca não salvo (sem *) e não muda a geração")
    void s007037a039ProjetoSalvo() throws Exception {
        start();
        Automation a = automation();
        Path file = dir.resolve("consulta.nekkora");
        prompts.thenSaveAs(file);
        assertEquals(ProjectService.Outcome.SAVED, await(Edt.call(() -> studio.projects().save(a.id()))));
        byte[] saved = Files.readAllBytes(file);
        long generation = project(a).generation();
        ValidationReport before = validate();
        assertFalse(project(a).dirty(), "S007-039: validar não marca o projeto como não salvo");
        assertEquals(generation, project(a).generation());
        assertEquals("Consulta de Clientes", Edt.call(() -> {
            var tabs = studio.view().workArea().getTabs();
            return tabs.getTitleAt(tabs.getSelectedIndex());
        }), "sem asterisco");
        assertArrayEquals(saved, Files.readAllBytes(file), "S007-038: o arquivo não muda");
        try (var s = Files.list(dir)) {
            assertEquals(List.of("consulta.nekkora"), s.map(p -> p.getFileName().toString()).toList(), "nenhum arquivo de validação gravado");
        }
        assertEquals(ProjectService.Outcome.CLOSED, await(Edt.call(() -> studio.projects().closeProject(a.id()))), "fecha sem perguntar: nada a salvar");
        assertTrue(prompts.unsavedAsked.isEmpty());
        assertEquals(ProjectService.Outcome.OPENED, await(Edt.call(() -> studio.projects().open(file))));
        ValidationReport reopened = validate();
        assertEquals(before.findings().stream().map(Finding::id).toList(), reopened.findings().stream().map(Finding::id).toList(),
                "S007-037: projeto carregado valida com os mesmos apontamentos e ids");
        assertEquals(before.toJson(), reopened.toJson());
        assertFalse(project(studio.state().activeAutomation().orElseThrow()).dirty());
        assertArrayEquals(saved, Files.readAllBytes(file));
    }

    @Test
    @DisplayName("S007-041/042/043 — validar não envia nenhum comando ao navegador nem depende do Native Host")
    void s007041a043SemNavegador() {
        FakeBrowser browser = new FakeBrowser();
        start(StudioEnvironment.inMemory(), browser);
        automation();
        int before = browser.calls.size();
        validate();
        validate();
        assertEquals(before, browser.calls.size(), "nenhuma chamada ao navegador");
    }

    @Test
    @DisplayName("S007-044/045 — painel: abre com o relatório, filtra por severidade, navega até a etapa e abre o editor de etapa existente")
    void s007044e045PainelENavegacao() {
        start();
        Automation a = automation();
        assertFalse(Edt.call(() -> studio.view().validationVisible()));
        ValidationReport r = validate();
        assertTrue(Edt.call(() -> studio.view().validationVisible()), "o painel aparece ao validar");
        assertEquals(r.findings().size(), Edt.call(() -> panel().findingList().getModel().getSize()));
        assertTrue(StudioFixture.texts(panel()).contains("Estado: " + ValidationStatus.INCOMPLETE.label()));
        assertTrue(Edt.call(() -> panel().counts().getText()).startsWith("× 2 bloqueios"), Edt.call(() -> panel().counts().getText()));
        assertTrue(StudioFixture.texts(panel()).contains(ValidationPanel.NO_EXECUTION));
        Edt.run(() -> panel().filter().setSelectedItem(ValidationPanel.Filter.BLOCKERS));
        assertEquals(2, (int) Edt.call(() -> panel().findingList().getModel().getSize()));
        String first = Edt.call(() -> panel().findingList().getModel().getElementAt(0).place());
        assertEquals("Sessão 1 · Etapa 01 — Preencher pesquisa", first);

        AutomationStep select = a.sessions().get(0).steps().get(1);
        Edt.run(() -> panel().findingList().setSelectedIndex(1));
        Edt.run(() -> panel().goToButton().doClick());
        assertEquals(new Selection.OfStep(a.id(), a.sessions().get(0).id(), select.id()), Edt.call(() -> studio.state().selection()), "S007-045");
        assertTrue(Edt.call(() -> panel().editButton().isEnabled()));
        prompts.thenCancel();
        Edt.run(() -> panel().editButton().doClick());
        assertEquals(select.name(), prompts.last().title(), "abre o editor de etapa já existente (não há editor no painel)");
        assertTrue(prompts.last().form() instanceof com.nekkora.studio.ui.form.StepDetailsForm);
        assertEquals(select.name(), Edt.call(() -> studio.state().activeAutomation().orElseThrow().step(select.id()).orElseThrow().name()));
    }

    @Test
    @DisplayName("S007-046/047 — relatório desatualizado: edição, evidência (sem mudar a revisão), etapa recebida do Recorder e renomear marcam; nunca apresentado como atual")
    void s007046e047Desatualizado() {
        start();
        Automation a = automation();
        validate();
        assertTrue(Edt.call(() -> panel().isCurrent()));
        assertFalse(Edt.call(() -> panel().staleAlert().isVisible()));

        long revision = a.revision();
        Edt.run(() -> studio.service().attachEvidence(a.id(), evidence("evd-1", 1, "ev-3", EvidenceType.VISIBLE_ERROR_MESSAGE)));
        assertEquals(revision, a.revision(), "evidência não muda a revisão do domínio");
        assertFalse(result(a).current(), "mas o relatório fica desatualizado (geração de persistência)");
        assertFalse(Edt.call(() -> panel().isCurrent()));
        assertTrue(Edt.call(() -> panel().staleAlert().isVisible()));
        assertTrue(StudioFixture.texts(panel()).contains(ValidationPanel.STALE_TITLE + " — último estado"));
        ValidationReport withEvidence = validate();
        assertTrue(withEvidence.forStep(a.sessions().get(0).steps().get(2).id()).stream().anyMatch(f -> f.code() == FindingCode.OBSERVATION_ERROR_INDICATOR
                || f.code() == FindingCode.OBSERVATION_IN_PROGRESS));
        assertTrue(Edt.call(() -> panel().isCurrent()));

        // S007-047: uma etapa chega do Recorder logo depois da validação (mesma thread, sem intervalo)
        Edt.run(() -> {
            studio.controller().validateActive();
            studio.service().appendStep(a.id(), a.sessions().get(0).id(), recorded("Clicar em Exportar", ActionType.CLICK, "ev-4"));
        });
        assertFalse(result(a).current());
        assertFalse(Edt.call(() -> panel().isCurrent()), "o relatório anterior não é apresentado como diagnóstico do novo estado");

        validate();
        Edt.run(() -> studio.service().updateAutomation(a.id(), "Consulta de Clientes 2", ""));
        assertFalse(result(a).current(), "renomear também conta");
    }

    @Test
    @DisplayName("S007 — automação recebendo gravação: validar é permitido (consulta), sem editar; recarregar do arquivo troca o objeto e o relatório fica desatualizado")
    void duranteGravacaoERecarga() throws Exception {
        start();
        Automation a = automation();
        Path file = dir.resolve("recarga.nekkora");
        prompts.thenSaveAs(file);
        assertEquals(ProjectService.Outcome.SAVED, await(Edt.call(() -> studio.projects().save(a.id()))));
        validate();
        prompts.thenConfirm(true);
        assertEquals(ProjectService.Outcome.RELOADED, await(Edt.call(() -> studio.projects().reload(a.id()))));
        assertFalse(result(a).current(), "a automação recarregada é outro objeto: relatório desatualizado");
        assertFalse(Edt.call(() -> panel().isCurrent()));
        Edt.run(() -> studio.service().setRecordingGuard(id -> true));
        Edt.run(() -> studio.service().editLockChanged(a.id()));
        ValidationReport r = validate();
        assertEquals(3, r.summary().steps());
        assertTrue(result(a).current());
        assertFalse(Edt.call(() -> panel().editButton().isEnabled()), "editar fica indisponível durante a gravação");
        assertTrue(Edt.call(() -> panel().goToButton().isEnabled()), "consultar continua");
    }

    @Test
    @DisplayName("S007-048/049 — quatro temas, acessibilidade: severidade em texto e símbolo, nomes acessíveis, menu Automação, atalho sem conflito, Esc fecha")
    void s007048e049TemasEAcessibilidade() {
        start();
        automation();
        validate();
        for (NekkoraTheme t : NekkoraThemes.all()) {
            Edt.theme(t);
            Edt.call(() -> {
                javax.swing.SwingUtilities.updateComponentTreeUI(studio.view().component());
                Edt.render(studio.view().component(), 1366, 768);
                return null;
            });
            assertTrue(Edt.call(() -> panel().isShowing() || panel().getWidth() > 0), t.displayName());
        }
        Edt.theme(NekkoraThemes.SAKURA_DARK);
        String row = Edt.call(() -> {
            var list = panel().findingList();
            list.getCellRenderer().getListCellRendererComponent(list, list.getModel().getElementAt(0), 0, false, false);
            return list.getAccessibleContext().getAccessibleChild(0).getAccessibleContext().getAccessibleName();
        });
        assertTrue(row.startsWith("×  Bloqueio · Sessão 1 · Etapa 01"), "linha lida por tecnologia assistiva com símbolo e nome da severidade: " + row);
        assertTrue(row.contains("Valor obrigatório não configurado."), row);
        assertEquals("Apontamentos da validação", Edt.call(() -> panel().findingList().getAccessibleContext().getAccessibleName()));
        assertEquals(ValidationPanel.TITLE, Edt.call(() -> panel().getAccessibleContext().getAccessibleName()));
        assertEquals("Mostrar apontamentos", Edt.call(() -> panel().filter().getAccessibleContext().getAccessibleName()));
        List<String> menu = Edt.call(() -> {
            var bar = studio.view().menuBar();
            List<String> out = new ArrayList<>();
            var m = bar.getMenu(1);
            out.add(m.getText());
            for (int i = 0; i < m.getItemCount(); i++) {
                JMenuItem it = m.getItem(i);
                if (it != null) out.add(it.getText());
            }
            return out;
        });
        // STUDIO-009 (D-S009-15): o menu Automação ganhou "Depurar no navegador…" (Ctrl+Shift+D); a validação continua no lugar
        // STUDIO-010 (D-S010-13): e o submenu "Gerar documentação" (→ PDD visual…)
        assertEquals(List.of("Automação", "Nova sessão", "Nova etapa", "Validar automação", "Depurar no navegador…", "Gerar documentação"), menu);
        var key = studio.view().actions().get(StudioActions.VALIDATE).shortcutKey();
        assertEquals(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, com.nekkora.ui.action.NekkoraKeys.menuMask()
                | java.awt.event.InputEvent.SHIFT_DOWN_MASK), key);
        Set<String> keys = new java.util.HashSet<>();
        for (var action : studio.view().actions().registry().all()) {
            if (action.shortcutKey() != null) assertTrue(keys.add(action.shortcutKey().toString()), "atalho repetido: " + action.shortcutKey());
        }
        Edt.run(() -> {
            var esc = panel().getActionMap().get("validation.close");
            esc.actionPerformed(new java.awt.event.ActionEvent(panel(), 0, "esc"));
        });
        assertFalse(Edt.call(() -> studio.view().validationVisible()), "Esc fecha o painel");
        Edt.run(() -> studio.view().actions().get(StudioActions.VALIDATE).actionPerformed(new java.awt.event.ActionEvent(this, 0, "v")));
        assertTrue(Edt.call(() -> studio.view().validationVisible()), "a ação reabre");
    }

    @Test
    @DisplayName("S007 — ativar outra automação não mostra o relatório da anterior; sem automação ativa, Validar fica desabilitado")
    void outraAutomacao() {
        start();
        assertFalse(Edt.call(() -> studio.view().actions().get(StudioActions.VALIDATE).isEnabled()));
        Automation a = automation();
        validate();
        prompts.then(ScriptedPrompts.name("Outra automação", null));
        Edt.run(() -> studio.controller().newAutomation());
        assertTrue(StudioFixture.texts(panel()).contains("Automação ainda não validada"));
        assertEquals(0, (int) Edt.call(() -> panel().findingList().getModel().getSize()));
        Edt.run(() -> studio.controller().openAutomation(a.id()));
        assertEquals(result(a).report().findings().size(), (int) Edt.call(() -> panel().findingList().getModel().getSize()));
    }

    @Test
    @DisplayName("S007 — nenhum texto de sucesso simulado nem de execução no código da validação (mesma regra do S001-020)")
    void semSucessoSimulado() throws Exception {
        java.util.regex.Pattern fake = java.util.regex.Pattern.compile("(?i)(execução concluída|alterações salvas|\"salvo\"|itens processados|"
                + "pronta para execu[cç][aã]o\"|ready_for_execution|aprovad[ao]|executad[ao] com sucesso|etapas? gravadas?)");
        for (String module : List.of("../nekkora-studio-validation/src/main/java", "../nekkora-studio-ui/src/main/java/com/nekkora/studio/ui/ValidationPanel.java",
                "src/main/java/com/nekkora/studio/app/workspace/ValidationService.java")) {
            try (var files = Files.walk(Path.of(module))) {
                for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    List<String> lines = Files.readAllLines(f);
                    for (int i = 0; i < lines.size(); i++) {
                        String l = lines.get(i).strip();
                        if (l.startsWith("*") || l.startsWith("//") || l.startsWith("/**")) continue;
                        assertFalse(fake.matcher(l).find(), f.getFileName() + ":" + (i + 1) + "  " + l);
                    }
                }
            }
        }
    }
}
