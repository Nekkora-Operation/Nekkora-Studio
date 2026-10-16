package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.app.recorder.RecorderPrompts;
import com.nekkora.studio.app.recorder.RecorderWindowHost;
import com.nekkora.studio.app.ui.StudioActions;
import com.nekkora.studio.app.ui.StudioPrompts;
import com.nekkora.studio.app.workspace.Notice;
import com.nekkora.studio.app.workspace.ProjectService;
import com.nekkora.studio.app.workspace.ProjectState;
import com.nekkora.studio.app.workspace.Selection;
import com.nekkora.studio.bridge.BrowserLink;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.evidence.EvidenceType;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary;
import com.nekkora.studio.domain.evidence.ObservedEvidence;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepProvenance;
import com.nekkora.studio.persistence.ProjectException;
import com.nekkora.studio.persistence.ProjectFiles;
import com.nekkora.studio.persistence.ProjectRepository;
import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** STUDIO-006: projetos no Studio montado como na aplicação (sem janela), com disco real e usuário roteirizado. */
class ProjectStudioTest {

    @TempDir
    Path dir;

    private final ScriptedPrompts prompts = new ScriptedPrompts();
    private final AtomicInteger exits = new AtomicInteger();
    private final List<Studio> started = new ArrayList<>();
    private Studio studio;
    /** Executado no instante em que o Studio encerraria a JVM (o que estiver no disco nesse momento é o que sobra). */
    private volatile Runnable atExit = () -> { };

    private Studio start(StudioEnvironment env, BrowserLink link) {
        Edt.theme(NekkoraThemes.SAKURA_DARK);
        Studio s = Edt.call(() -> Studio.create(StudioFixture.CLOCK, prompts, () -> {
            atExit.run();
            exits.incrementAndGet();
        }, link, RecorderPrompts.none(), p -> RecorderWindowHost.headless(), env));
        Edt.render(s.view().component(), 1440, 900);
        started.add(s);
        studio = s;
        return s;
    }

    private Studio start(StudioEnvironment env) {
        return start(env, BrowserLink.offline("DISABLED"));
    }

    private Studio start() {
        return start(StudioEnvironment.inMemory());
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

    private ProjectService projects() {
        return studio.projects();
    }

    private Automation automation(String name) {
        prompts.then(ScriptedPrompts.name(name, "Processo de teste"));
        Edt.run(() -> studio.controller().newAutomation());
        for (String s : List.of("Autenticação", "Consulta")) {
            prompts.then(ScriptedPrompts.name(s, null));
            Edt.run(() -> studio.controller().newSession());
        }
        prompts.then(ScriptedPrompts.step("Autenticação", "Preencher usuário", ActionType.FILL));
        Edt.run(() -> studio.controller().newStep(null));
        prompts.then(ScriptedPrompts.step("Consulta", "Clicar em Pesquisar", ActionType.CLICK));
        Edt.run(() -> studio.controller().newStep(null));
        return studio.state().activeAutomation().orElseThrow();
    }

    private ProjectService.Outcome save(AutomationId id) throws Exception {
        return await(Edt.call(() -> projects().save(id)));
    }

    private ProjectService.Outcome open(Path file) throws Exception {
        return await(Edt.call(() -> projects().open(file)));
    }

    private ProjectState state(AutomationId id) {
        return Edt.call(() -> studio.state().project(id).orElseThrow());
    }

    private String tab(Automation a) {
        return Edt.call(() -> {
            var tabs = studio.view().workArea().getTabs();
            return tabs.getTitleAt(tabs.indexOfComponent(studio.view().overview(a.id()).orElseThrow()));
        });
    }

    private String status() {
        return Edt.call(() -> studio.view().statusItems().get(0).getText());
    }

    /** Etapa GRAVADA executada (como entregue pelo Recorder) para receber evidências. */
    private AutomationStep recordedStep(Automation a, String eventId) {
        CorrelationKeys keys = CorrelationKeys.of(Map.of(CorrelationKeys.Key.RECORDING_ID, "rec-1", CorrelationKeys.Key.RECORDED_EVENT_ID, eventId));
        RecordingObservation obs = new RecordingObservation(RecordingObservation.CaptureMode.CAPTURE_AND_INTERACT,
                RecordingObservation.Observation.INTERACTION_OBSERVED, null, List.of(), List.of());
        StepDraft d = StepDraft.builder().name("Clicar no botão Entrar").actionType(ActionType.CLICK).provenance(StepProvenance.recorded(keys, obs)).build();
        return Edt.call(() -> studio.service().appendStep(a.id(), a.sessions().get(0).id(), d));
    }

    private static ObservedEvidence evidence(String id, long seq, String eventId) {
        return new ObservedEvidence(id, seq, "rec-1", eventId, Instant.parse("2026-10-16T12:00:00Z").plusSeconds(seq), EvidenceType.VISIBLE_ERROR_MESSAGE,
                EvidenceVocabulary.Source.FEEDBACK_OBSERVER, EvidenceVocabulary.Relation.AFTER_ACTION, EvidenceVocabulary.Confidence.TEMPORALLY_ASSOCIATED,
                EvidenceVocabulary.Classification.ERROR_INDICATOR, null, "alerta", ObservedEvidence.Message.of("Usuário ou senha inválidos."), null, null, null,
                List.of(EvidenceVocabulary.Signal.ROLE_ALERT), 0, 0, List.of(EvidenceVocabulary.Limitation.MESSAGE_SUPPRESSED));
    }

    private Path firstSave(Automation a, String name) throws Exception {
        Path file = dir.resolve(name + ".nekkora");
        prompts.thenSaveAs(file);
        assertEquals(ProjectService.Outcome.SAVED, save(a.id()));
        return file;
    }

    // ================================================================================================ testes

    @Test
    @DisplayName("S006-002 — novo projeto: alterações não salvas, sem arquivo, asterisco na aba")
    void s006002NovoProjeto() {
        start();
        Automation a = automation("Consulta de Clientes");
        assertTrue(state(a.id()).dirty());
        assertEquals(ProjectState.Status.UNSAVED, state(a.id()).status());
        assertEquals("Consulta de Clientes *", tab(a));
        assertEquals("Alterações não salvas — sem arquivo", status());
        assertTrue(Edt.call(() -> studio.view().actions().get(StudioActions.SAVE).isEnabled()));
    }

    @Test
    @DisplayName("S006-038 — Salvar: pede o destino só na primeira vez; depois grava no mesmo arquivo; sucesso só após a confirmação")
    void s006038Salvar() throws Exception {
        start();
        Automation a = automation("Consulta de Clientes");
        Path file = firstSave(a, "consulta");
        assertEquals(List.of("Consulta de Clientes.nekkora"), prompts.savePromptsSuggested, "nome sugerido a partir da automação");
        assertTrue(Files.isRegularFile(file));
        assertFalse(state(a.id()).dirty());
        assertEquals("Consulta de Clientes", tab(a), "sem asterisco depois de salvar");
        assertEquals("Salvo — consulta.nekkora", status());
        assertEquals("Projeto salvo", studio.state().lastNotice().orElseThrow().title());
        Edt.run(() -> studio.service().renameSession(a.id(), a.sessions().get(1).id(), "Pesquisa"));
        assertEquals("Consulta de Clientes *", tab(a));
        assertEquals(ProjectService.Outcome.SAVED, save(a.id()));
        assertEquals(1, prompts.savePromptsSuggested.size(), "segundo salvar não pergunta o destino");
        Automation back = new ProjectFiles(new ProjectRepository(), "teste").load(file, StudioFixture.CLOCK).automation();
        assertEquals("Pesquisa", back.sessions().get(1).name());
    }

    @Test
    @DisplayName("S006-039 — Salvar como: novo arquivo, mesma identidade; a automação não é duplicada no catálogo")
    void s006039SalvarComo() throws Exception {
        start();
        Automation a = automation("Consulta");
        Path first = firstSave(a, "um");
        Path second = dir.resolve("dois.nekkora");
        prompts.thenSaveAs(second);
        assertEquals(ProjectService.Outcome.SAVED, await(Edt.call(() -> projects().saveAs(a.id()))));
        assertEquals(1, studio.state().automations().size());
        assertEquals(second, state(a.id()).path().orElseThrow());
        ProjectFiles f = new ProjectFiles(new ProjectRepository(), "teste");
        assertEquals(f.load(first, StudioFixture.CLOCK).manifest().projectId(), f.load(second, StudioFixture.CLOCK).manifest().projectId());
        assertEquals(a.id(), f.load(second, StudioFixture.CLOCK).automation().id());
    }

    @Test
    @DisplayName("S006-040 — sobrescrever um arquivo existente só com confirmação")
    void s006040Sobrescrita() throws Exception {
        start();
        Automation a = automation("Consulta");
        Path existing = Files.writeString(dir.resolve("existente.nekkora"), "outro conteúdo");
        prompts.thenSaveAs(existing).thenConfirm(false);
        assertEquals(ProjectService.Outcome.CANCELLED, save(a.id()));
        assertEquals("outro conteúdo", Files.readString(existing));
        prompts.thenSaveAs(existing).thenConfirm(true);
        assertEquals(ProjectService.Outcome.SAVED, save(a.id()));
        assertNotEquals("outro conteúdo", Files.readString(existing, java.nio.charset.StandardCharsets.ISO_8859_1));
    }

    @Test
    @DisplayName("S006-041 — arquivo alterado fora do Studio: conflito declarado, nada sobrescrito; Cancelar, Salvar com outro nome ou Recarregar")
    void s006041Conflito() throws Exception {
        start();
        Automation a = automation("Consulta");
        Path file = firstSave(a, "conflito");
        // outra instância salvou o mesmo projeto com outro conteúdo
        ProjectFiles other = new ProjectFiles(new ProjectRepository(), "outro");
        ProjectFiles.Loaded l = other.load(file, StudioFixture.CLOCK);
        l.automation().updateDetails("Versão externa", "");
        other.save(file, ProjectFiles.Snapshot.of(l.automation(), l.manifest().projectId()), l.fingerprint(), false, Instant.now(), StudioFixture.CLOCK);
        byte[] external = Files.readAllBytes(file);
        Edt.run(() -> studio.service().renameSession(a.id(), a.sessions().get(0).id(), "Entrada"));
        prompts.thenConflict(StudioPrompts.ConflictChoice.CANCEL);
        assertEquals(ProjectService.Outcome.CONFLICT, save(a.id()));
        assertArrayEquals(external, Files.readAllBytes(file), "não sobrescreve");
        assertEquals(ProjectState.Status.CONFLICT, state(a.id()).status());
        assertTrue(status().startsWith("Arquivo alterado externamente"));
        Path copy = dir.resolve("minha-versao.nekkora");
        prompts.thenConflict(StudioPrompts.ConflictChoice.SAVE_AS).thenSaveAs(copy);
        save(a.id());
        await(CompletableFuture.supplyAsync(() -> {
            long end = System.currentTimeMillis() + 10_000;
            while (!Files.exists(copy) && System.currentTimeMillis() < end) Thread.onSpinWait();
            return true;
        }));
        Edt.flush();
        assertTrue(Files.exists(copy), "salvo com outro nome");
        assertArrayEquals(external, Files.readAllBytes(file));
    }

    @Test
    @DisplayName("S006-041 — Recarregar: com confirmação, descarta as alterações locais e abre o conteúdo atual do arquivo")
    void s006041Recarregar() throws Exception {
        start();
        Automation a = automation("Consulta");
        Path file = firstSave(a, "recarregar");
        ProjectFiles other = new ProjectFiles(new ProjectRepository(), "outro");
        ProjectFiles.Loaded l = other.load(file, StudioFixture.CLOCK);
        l.automation().updateDetails("Versão externa", "");
        other.save(file, ProjectFiles.Snapshot.of(l.automation(), l.manifest().projectId()), l.fingerprint(), false, Instant.now(), StudioFixture.CLOCK);
        Edt.run(() -> studio.service().renameSession(a.id(), a.sessions().get(0).id(), "Entrada"));
        prompts.thenConflict(StudioPrompts.ConflictChoice.RELOAD).thenConfirm(true);
        save(a.id());
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end && !Edt.call(() -> studio.state().activeAutomation().map(Automation::name).orElse("")).equals("Versão externa")) {
            Thread.sleep(20);
            Edt.flush();
        }
        Automation now = studio.state().activeAutomation().orElseThrow();
        assertEquals("Versão externa", now.name());
        assertEquals(a.id(), now.id(), "mesma identidade");
        assertEquals(1, studio.state().automations().size());
        assertFalse(state(now.id()).dirty());
    }

    /** Operações reais com falhas injetáveis. */
    static final class Ops implements ProjectRepository.FileOps {
        final ProjectRepository.FileOps real = ProjectRepository.FileOps.system();
        volatile IOException failWrite;
        volatile CountDownLatch hold;
        final CountDownLatch writing = new CountDownLatch(1);
        /** Barreira controlada: só as gravações cujo arquivo satisfaz o filtro esperam por {@link #hold}. */
        volatile java.util.function.Predicate<Path> holdIf = p -> true;
        final CountDownLatch held = new CountDownLatch(1);
        /** Barreira na leitura (ex.: recarregar em andamento). */
        volatile CountDownLatch holdRead;
        volatile java.util.function.Predicate<Path> holdReadIf = p -> false;
        final CountDownLatch reading = new CountDownLatch(1);
        /** Exclusões que falham (ex.: arquivo de recuperação bloqueado por outro programa). */
        volatile java.util.function.Predicate<Path> failDeleteIf = p -> false;

        @Override
        public Path createTemp(Path d, String prefix, String suffix) throws IOException {
            return real.createTemp(d, prefix, suffix);
        }

        @Override
        public void writeAndSync(Path file, byte[] data) throws IOException {
            writing.countDown();
            if (hold != null && holdIf.test(file)) {
                held.countDown();
                await(hold);
            }
            if (failWrite != null) throw failWrite;
            real.writeAndSync(file, data);
        }

        private static void await(CountDownLatch l) {
            try {
                l.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void move(Path from, Path to, CopyOption... options) throws IOException {
            real.move(from, to, options);
        }

        @Override
        public void deleteIfExists(Path file) throws IOException {
            if (failDeleteIf.test(file)) throw new FileSystemException(file.toString(), null, "being used by another process");
            real.deleteIfExists(file);
        }

        @Override
        public byte[] readAll(Path file) throws IOException {
            if (holdRead != null && holdReadIf.test(file)) {
                reading.countDown();
                await(holdRead);
            }
            return real.readAll(file);
        }

        @Override
        public void syncDirectory(Path d) {
            real.syncDirectory(d);
        }
    }

    @Test
    @DisplayName("S006-042/043 — falha de escrita (disco cheio): erro declarado, arquivo anterior intacto, alterações continuam abertas")
    void s006042FalhaDeEscrita() throws Exception {
        Ops ops = new Ops();
        start(StudioEnvironment.inMemory().withFileOps(ops));
        Automation a = automation("Consulta");
        Path file = firstSave(a, "falha");
        byte[] before = Files.readAllBytes(file);
        Edt.run(() -> studio.service().renameSession(a.id(), a.sessions().get(0).id(), "Entrada"));
        ops.failWrite = new FileSystemException("x", null, "There is not enough space on the disk");
        assertEquals(ProjectService.Outcome.FAILED, save(a.id()));
        assertEquals(ProjectState.Status.ERROR, state(a.id()).status());
        assertEquals(ProjectException.Error.DISK_FULL, state(a.id()).lastError().orElseThrow());
        assertTrue(state(a.id()).dirty(), "não anunciado como salvo");
        assertTrue(studio.state().lastNotice().orElseThrow().message().contains("arquivo anterior foi preservado"));
        assertArrayEquals(before, Files.readAllBytes(file));
        assertTrue(status().startsWith("Erro ao salvar"));
        ops.failWrite = null;
        assertEquals(ProjectService.Outcome.SAVED, save(a.id()), "depois do erro, salvar de novo funciona");
    }

    @Test
    @DisplayName("S006-054 — arquivo inválido: nada entra no workspace (integral ou recusado)")
    void s006054Integral() throws Exception {
        start();
        Automation a = automation("Existente");
        Path bad = Files.writeString(dir.resolve("ruim.nekkora"), "PK não é um projeto");
        int tabs = Edt.call(() -> studio.view().workArea().getTabs().getTabCount());
        assertEquals(ProjectService.Outcome.FAILED, open(bad));
        assertEquals(List.of(a), studio.state().automations());
        assertEquals(tabs, Edt.call(() -> studio.view().workArea().getTabs().getTabCount()));
        assertEquals("Não foi possível abrir o projeto", studio.state().lastNotice().orElseThrow().title());
    }

    @Test
    @DisplayName("S006-055 — alterações não salvas controladas POR automação (A salvo, B alterado, C salvo)")
    void s006055PorAutomacao() throws Exception {
        start();
        Automation a = automation("A");
        firstSave(a, "a");
        Automation b = automation("B");
        firstSave(b, "b");
        Automation c = automation("C");
        firstSave(c, "c");
        Edt.run(() -> studio.service().renameSession(b.id(), b.sessions().get(0).id(), "Entrada"));
        assertEquals(List.of(b), studio.state().unsavedAutomations());
        assertEquals("A", tab(a));
        assertEquals("B *", tab(b));
        assertEquals("C", tab(c));
    }

    @Test
    @DisplayName("S006-056/037 — evidência que chega depois de salvar marca o projeto como não salvo, sem entrar no desfazer")
    void s006056EvidenciaPosterior() throws Exception {
        start();
        Automation a = automation("Consulta");
        AutomationStep entrar = recordedStep(a, "ev-1");
        firstSave(a, "evidencia");
        int undo = Edt.call(() -> studio.service().history().undoSize(a.id()));
        long revision = a.revision();
        Edt.run(() -> studio.service().attachEvidence(a.id(), evidence("evd-1", 1, "ev-1")));
        assertTrue(state(a.id()).dirty(), "evidência também precisa ser salva");
        assertEquals(revision, a.revision(), "não é edição");
        assertEquals(undo, Edt.call(() -> studio.service().history().undoSize(a.id())), "fora do desfazer");
        assertEquals(1, entrar.evidence().size());
    }

    @Test
    @DisplayName("S006-057/058/059 — assíncrono: a EDT responde durante a gravação; alteração feita durante o salvamento continua pendente")
    void s006057a059Assincrono() throws Exception {
        Ops ops = new Ops();
        start(StudioEnvironment.inMemory().withFileOps(ops));
        Automation a = automation("Consulta");
        Path file = dir.resolve("lento.nekkora");
        ops.hold = new CountDownLatch(1);
        prompts.thenSaveAs(file);
        CompletableFuture<ProjectService.Outcome> f = Edt.call(() -> projects().save(a.id()));
        assertTrue(ops.writing.await(10, TimeUnit.SECONDS), "gravação começou no worker");
        assertFalse(f.isDone(), "S006-058: salvar devolve antes de gravar");
        long t0 = System.nanoTime();
        assertEquals(1, (int) Edt.call(() -> 1), "S006-059: EDT livre enquanto o disco trabalha");
        assertTrue(System.nanoTime() - t0 < 1_000_000_000L);
        assertEquals(ProjectState.Status.SAVING, state(a.id()).status());
        assertTrue(status().startsWith("Salvando…"), status());
        Edt.run(() -> studio.service().renameSession(a.id(), a.sessions().get(0).id(), "Entrada")); // durante a gravação
        ops.hold.countDown();
        assertEquals(ProjectService.Outcome.SAVED, await(f));
        assertTrue(state(a.id()).dirty(), "S006-057: só a geração gravada conta como salva");
        assertEquals(ProjectState.Status.UNSAVED, state(a.id()).status());
        Automation back = new ProjectFiles(new ProjectRepository(), "t").load(file, StudioFixture.CLOCK).automation();
        assertEquals("Autenticação", back.sessions().get(0).name(), "o arquivo tem a fotografia tirada no início");
        assertTrue(projects().lastSnapshotNanos() > 0 && projects().lastSnapshotNanos() < 1_000_000_000L);
    }

    @Test
    @DisplayName("S006-035/036 — desfazer/refazer começa vazio ao abrir e volta a funcionar depois de uma nova edição")
    void s006035e036Historico() throws Exception {
        start();
        Automation a = automation("Consulta");
        Path file = firstSave(a, "historico");
        assertEquals(ProjectService.Outcome.CLOSED, await(Edt.call(() -> projects().closeProject(a.id()))));
        assertEquals(ProjectService.Outcome.OPENED, open(file));
        Automation b = studio.state().activeAutomation().orElseThrow();
        assertEquals(a.id(), b.id());
        assertFalse(Edt.call(() -> studio.service().canUndo(b.id())));
        assertFalse(Edt.call(() -> studio.service().canRedo(b.id())));
        Edt.run(() -> studio.service().renameSession(b.id(), b.sessions().get(0).id(), "Entrada"));
        assertTrue(Edt.call(() -> studio.service().canUndo(b.id())));
        Edt.run(() -> studio.service().undo(b.id()));
        assertEquals("Autenticação", b.sessions().get(0).name());
    }

    @Test
    @DisplayName("S006-066 — abrir duas vezes o mesmo arquivo ativa a automação existente (sem segunda instância)")
    void s006066DuasVezes() throws Exception {
        start();
        Automation a = automation("Consulta");
        Path file = firstSave(a, "duas");
        assertEquals(ProjectService.Outcome.ACTIVATED, open(file));
        assertEquals(1, studio.state().automations().size());
    }

    @Test
    @DisplayName("S006-067 — outro arquivo com a MESMA identidade: conflito detectado, nada substituído")
    void s006067MesmaIdentidade() throws Exception {
        start();
        Automation a = automation("Consulta");
        Path file = firstSave(a, "original");
        Path copy = Files.copy(file, dir.resolve("copia.nekkora"));
        assertEquals(ProjectService.Outcome.CONFLICT, open(copy));
        assertEquals(List.of(a), studio.state().automations());
        assertEquals(file, state(a.id()).path().orElseThrow());
    }

    @Test
    @DisplayName("§29 — fechar projeto: Cancelar mantém; Salvar grava e fecha; Descartar fecha sem gravar; fechado sai do catálogo")
    void fecharProjeto() throws Exception {
        start();
        Automation a = automation("A");
        Path file = firstSave(a, "a");
        Edt.run(() -> studio.service().renameSession(a.id(), a.sessions().get(0).id(), "Entrada"));
        prompts.thenUnsaved(StudioPrompts.UnsavedChoice.CANCEL);
        assertEquals(ProjectService.Outcome.CANCELLED, await(Edt.call(() -> projects().closeProject(a.id()))));
        assertEquals(1, studio.state().automations().size());
        prompts.thenUnsaved(StudioPrompts.UnsavedChoice.SAVE);
        assertEquals(ProjectService.Outcome.CLOSED, await(Edt.call(() -> projects().closeProject(a.id()))));
        assertTrue(studio.state().automations().isEmpty());
        assertEquals("Entrada", new ProjectFiles(new ProjectRepository(), "t").load(file, StudioFixture.CLOCK).automation().sessions().get(0).name());
        assertEquals(ProjectService.Outcome.OPENED, open(file));
        Automation b = studio.state().activeAutomation().orElseThrow();
        Edt.run(() -> studio.service().renameSession(b.id(), b.sessions().get(0).id(), "Descartada"));
        byte[] saved = Files.readAllBytes(file);
        prompts.thenUnsaved(StudioPrompts.UnsavedChoice.DISCARD);
        assertEquals(ProjectService.Outcome.CLOSED, await(Edt.call(() -> projects().closeProject(b.id()))));
        assertArrayEquals(saved, Files.readAllBytes(file));
    }

    @Test
    @DisplayName("S006-068/069 — sair com alterações: Salvar todos grava cada projeto (pede destino aos novos) e só então fecha")
    void s006068e069SalvarTodos() throws Exception {
        start();
        Automation a = automation("A");
        Path fa = firstSave(a, "a");
        Edt.run(() -> studio.service().renameSession(a.id(), a.sessions().get(0).id(), "Entrada"));
        Automation b = automation("B");
        Path fb = dir.resolve("b.nekkora");
        prompts.thenUnsaved(StudioPrompts.UnsavedChoice.SAVE).thenSaveAs(fb);
        Edt.run(() -> studio.controller().requestExit());
        long end = System.currentTimeMillis() + 15_000;
        while (exits.get() == 0 && System.currentTimeMillis() < end) {
            Thread.sleep(20);
            Edt.flush();
        }
        assertEquals(1, exits.get(), "fechou depois de salvar todos");
        assertEquals(List.of(List.of("A", "B")), prompts.unsavedAsked);
        ProjectFiles f = new ProjectFiles(new ProjectRepository(), "t");
        assertEquals("Entrada", f.load(fa, StudioFixture.CLOCK).automation().sessions().get(0).name());
        assertEquals(b.id(), f.load(fb, StudioFixture.CLOCK).automation().id());
    }

    @Test
    @DisplayName("S006-069/070 — se algum salvamento falhar, o Studio NÃO fecha; Descartar fecha")
    void s006069FalhaNaoFecha() throws Exception {
        start();
        Automation a = automation("A");
        firstSave(a, "a");
        Edt.run(() -> studio.service().renameSession(a.id(), a.sessions().get(0).id(), "Entrada"));
        Edt.run(() -> projects().setBusyGuard(id -> id.equals(a.id()))); // ex.: gravação em andamento
        prompts.thenUnsaved(StudioPrompts.UnsavedChoice.SAVE);
        Edt.run(() -> studio.controller().requestExit());
        Thread.sleep(200);
        Edt.flush();
        assertEquals(0, exits.get(), "não fechou: um projeto não foi salvo");
        assertEquals("O Studio continua aberto", studio.state().lastNotice().orElseThrow().title());
        prompts.thenExit(true);
        Edt.run(() -> studio.controller().requestExit());
        assertEquals(1, exits.get(), "descartar fecha");
    }

    @Test
    @DisplayName("S006-060 — salvamento definitivo bloqueado sem quiescência, com o motivo; nada é gravado")
    void s006060SemQuiescencia() throws Exception {
        start();
        Automation a = automation("A");
        Path file = dir.resolve("gravando.nekkora");
        Edt.run(() -> projects().setBusyGuard(id -> true));
        assertEquals(ProjectService.Outcome.RECORDING, save(a.id()));
        assertEquals(ProjectService.RECORDING_MESSAGE, studio.state().lastNotice().orElseThrow().message());
        assertFalse(Files.exists(file));
        assertTrue(prompts.savePromptsSuggested.isEmpty(), "nem chega a pedir o destino");
    }

    @Test
    @DisplayName("S006-061/062/063/064 — recuperação: só com consentimento; após encerramento inesperado é OFERECIDA; não substitui o projeto; descarte")
    void s006061a064Recuperacao() throws Exception {
        Path data = dir.resolve("dados");
        StudioEnvironment env = StudioEnvironment.at(data);
        start(env);
        Automation a = automation("Consulta");
        Path file = firstSave(a, "recuperavel");
        byte[] confirmed = Files.readAllBytes(file);
        Edt.run(() -> studio.service().renameSession(a.id(), a.sessions().get(0).id(), "Entrada"));
        assertEquals(0, await(Edt.call(() -> projects().checkpoint())), "S006-061: sem consentimento, nenhuma fotografia");
        assertFalse(Files.exists(data.resolve("recovery")));
        prompts.thenRecoverySettings(new StudioPrompts.RecoveryPreferences(true, 60, false));
        Edt.run(() -> studio.controller().configureRecovery());
        assertTrue(prompts.recoveryExplanations.get(0).contains(data.resolve("recovery").toString()), "informa onde ficam");
        assertTrue(prompts.recoveryExplanations.get(0).contains("NÃO são criptografados"));
        assertEquals(1, await(Edt.call(() -> projects().checkpoint())));
        assertEquals(1, projects().recovery().orElseThrow().count());
        assertArrayEquals(confirmed, Files.readAllBytes(file), "S006-063: a recuperação não toca no projeto");
        // encerramento inesperado: nenhuma pergunta, nenhum salvamento (o processo simplesmente para)
        Edt.run(studio::dispose);
        started.remove(studio);
        Studio again = start(env);
        prompts.thenRecovery(StudioPrompts.RecoveryChoice.IGNORE);
        assertEquals(0, await(Edt.call(() -> again.projects().offerRecoveries())));
        assertTrue(again.state().automations().isEmpty(), "Ignorar: nada restaurado");
        assertEquals(1, again.projects().recovery().orElseThrow().count(), "Ignorar mantém a fotografia");
        prompts.thenRecovery(StudioPrompts.RecoveryChoice.RECOVER);
        assertEquals(1, await(Edt.call(() -> again.projects().offerRecoveries())));
        StudioPrompts.RecoveryOffer offer = prompts.recoveryOffers.get(1);
        assertEquals("Consulta", offer.project());
        assertEquals("recuperavel.nekkora", offer.origin());
        Automation r = again.state().activeAutomation().orElseThrow();
        assertEquals("Entrada", r.sessions().get(0).name(), "S006-062: alterações recuperadas");
        ProjectState p = Edt.call(() -> again.state().project(r.id()).orElseThrow());
        assertTrue(p.dirty() && p.recovered(), "continua NÃO salvo");
        assertEquals(ProjectState.Status.RECOVERED, p.status());
        assertArrayEquals(confirmed, Files.readAllBytes(file), "o arquivo original não foi alterado");
        assertEquals(ProjectService.Outcome.SAVED, await(Edt.call(() -> again.projects().save(r.id()))), "salva no arquivo de origem (sem conflito)");
        Thread.sleep(200);
        Edt.flush();
        assertEquals(0, again.projects().recovery().orElseThrow().count(), "salvamento confirmado supera a fotografia");
        // S006-064: limpeza explícita
        Edt.run(() -> again.service().renameSession(r.id(), r.sessions().get(0).id(), "Outra"));
        assertEquals(1, await(Edt.call(() -> again.projects().checkpoint())));
        Edt.run(() -> again.projects().applyRecovery(false, 60, true));
        Thread.sleep(200);
        assertEquals(0, again.projects().recovery().orElseThrow().count());
    }

    @Test
    @DisplayName("S006-065 — projetos recentes: só metadados; arquivo removido aparece indisponível e pode sair da lista")
    void s006065Recentes() throws Exception {
        Path data = dir.resolve("dados");
        start(StudioEnvironment.at(data));
        Automation a = automation("Consulta");
        Path file = firstSave(a, "recente");
        assertEquals(file, projects().recents().entries().get(0).path());
        String stored = Files.readString(data.resolve("recent-projects.json"));
        assertFalse(stored.contains("Consulta") || stored.contains("Autenticação"), "nenhuma cópia do projeto na lista");
        assertEquals(ProjectService.Outcome.CLOSED, await(Edt.call(() -> projects().closeProject(a.id()))));
        Files.delete(file);
        Edt.run(() -> studio.view().recentMenu());
        Edt.run(() -> studio.view().menuBar());
        List<String> items = Edt.call(() -> {
            var m = studio.view().recentMenu();
            m.setSelected(true);
            m.setSelected(false);
            List<String> out = new ArrayList<>();
            for (int i = 0; i < m.getItemCount(); i++) out.add(m.getItem(i).getText());
            return out;
        });
        assertTrue(items.get(0).contains("indisponível"), items.toString());
        assertEquals(ProjectService.Outcome.FAILED, await(Edt.call(() -> projects().open(file))));
        Edt.run(() -> projects().recents().remove(file));
        assertTrue(projects().recents().entries().isEmpty());
    }

    @Test
    @DisplayName("S006-074 — logs só técnicos: sem nome, comentário, caminho ou conteúdo do projeto")
    void s006074Logs() throws Exception {
        List<String> logged = new ArrayList<>();
        Handler h = new Handler() {
            @Override
            public void publish(LogRecord r) {
                logged.add(String.valueOf(r.getMessage()));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger log = Logger.getLogger(ProjectService.class.getName());
        log.addHandler(h);
        try {
            start();
            Automation a = automation("Cliente Bartolomeu");
            Edt.run(() -> studio.service().addComment(a.id(), a.sessions().get(0).steps().get(0).id(), "comentário secreto"));
            Path file = firstSave(a, "registro-bartolomeu");
            await(Edt.call(() -> projects().closeProject(a.id())));
            open(file);
            open(dir.resolve("nao-existe.nekkora"));
        } finally {
            log.removeHandler(h);
        }
        assertTrue(logged.stream().anyMatch(m -> m.contains("operacao=salvar resultado=ok")), logged.toString());
        assertTrue(logged.stream().anyMatch(m -> m.contains("operacao=abrir resultado=erro") && m.contains("codigo=NOT_FOUND")), logged.toString());
        for (String m : logged) {
            assertFalse(m.contains("Bartolomeu") || m.contains("bartolomeu") || m.contains("secreto") || m.contains(dir.toString()) || m.contains(".nekkora"), m);
        }
    }

    @Test
    @DisplayName("S006-080 — abrir um projeto não executa nada: nenhum comando ao navegador, nenhum seletor executado")
    void s006080SemExecucao() throws Exception {
        start();
        Automation a = automation("Consulta");
        Path file = firstSave(a, "inerte");
        FakeBrowser browser = new FakeBrowser();
        Edt.run(studio::dispose);
        started.remove(studio);
        start(StudioEnvironment.inMemory(), browser);
        int before = browser.calls.size();
        assertEquals(ProjectService.Outcome.OPENED, open(file));
        assertEquals(before, browser.calls.size(), "nenhum comando ao navegador ao abrir");
    }

    @Test
    @DisplayName("S006-075/076 — menu Arquivo nos quatro temas, com nomes acessíveis e atalhos sem conflito")
    void s006075e076MenuTemasAcessibilidade() {
        start();
        for (NekkoraTheme t : NekkoraThemes.all()) {
            Edt.theme(t);
            List<String> texts = Edt.call(() -> {
                var bar = studio.view().menuBar();
                javax.swing.SwingUtilities.updateComponentTreeUI(bar);
                Edt.render(studio.view().component(), 1366, 768);
                List<String> out = new ArrayList<>();
                var file = bar.getMenu(0);
                for (int i = 0; i < file.getItemCount(); i++) {
                    JMenuItem it = file.getItem(i);
                    if (it == null) continue;
                    assertFalse(it.getText().isBlank());
                    out.add(it.getText());
                }
                return out;
            });
            assertEquals(List.of("Novo projeto", "Abrir projeto…", "Salvar", "Salvar como…", "Projetos recentes", "Fechar projeto",
                    "Recuperação automática…", "Sair"), texts, t.displayName());
        }
        Set<String> keys = new HashSet<>();
        for (var action : studio.view().actions().registry().all()) {
            var k = action.shortcutKey();
            if (k != null) assertTrue(keys.add(k.toString()), "atalho repetido: " + k);
        }
        assertTrue(keys.contains(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, com.nekkora.ui.action.NekkoraKeys.menuMask()).toString()));
    }

    /** Arquivos temporários nunca ficam para trás depois de salvar. */
    @Test
    @DisplayName("§33 — nenhum temporário restante na pasta do projeto depois de salvar")
    void semTemporarios() throws Exception {
        start();
        Automation a = automation("Consulta");
        firstSave(a, "limpo");
        try (Stream<Path> s = Files.list(dir)) {
            assertEquals(List.of("limpo.nekkora"), s.map(p -> p.getFileName().toString()).toList());
        }
    }

    // ================================================================================================ revisão 1 (PR #8)

    private void enableRecovery() {
        prompts.thenRecoverySettings(new StudioPrompts.RecoveryPreferences(true, 60, false));
        Edt.run(() -> studio.controller().configureRecovery());
    }

    /** Fotografias de recuperação (e temporários) guardadas no diretório de dados. */
    private static List<String> recoveryFiles(Path data) throws IOException {
        Path r = data.resolve("recovery");
        if (!Files.isDirectory(r)) return List.of();
        try (Stream<Path> s = Files.walk(r)) {
            return s.filter(Files::isRegularFile).map(p -> r.relativize(p).toString()).sorted().toList();
        }
    }

    /** Cópia do diretório de dados no instante do encerramento: é o que outro processo encontraria. */
    private static void copyTree(Path from, Path to) {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : s.toList()) {
                Path t = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(t);
                else Files.copy(p, t);
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws Exception {
        long end = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < end) {
            Thread.sleep(10);
            Edt.flush();
        }
    }

    @Test
    @DisplayName("P1-A — Recarregar recusado (nome em conflito com outro projeto aberto) deixa o workspace exatamente como estava")
    void p1aRecarregarRecusadoPreservaWorkspace() throws Exception {
        start();
        Automation a = automation("Consulta de Clientes");
        Path fa = firstSave(a, "clientes");
        Automation b = automation("Cadastro de Produtos");
        Path fb = firstSave(b, "produtos");
        // outro programa grava clientes.nekkora: arquivo válido, MESMO AutomationId, mas com o nome do projeto B
        ProjectFiles other = new ProjectFiles(new ProjectRepository(), "outro");
        ProjectFiles.Loaded l = other.load(fa, StudioFixture.CLOCK);
        l.automation().updateDetails("Cadastro de Produtos", "");
        other.save(fa, ProjectFiles.Snapshot.of(l.automation(), l.manifest().projectId()), l.fingerprint(), false, Instant.now(), StudioFixture.CLOCK);
        byte[] external = Files.readAllBytes(fa);
        // alterações locais, histórico e seleção no projeto A
        Edt.run(() -> studio.service().openAutomation(a.id()));
        Edt.run(() -> studio.service().renameSession(a.id(), a.sessions().get(0).id(), "Entrada"));
        Selection chosen = new Selection.OfStep(a.id(), a.sessions().get(1).id(), a.sessions().get(1).steps().get(0).id());
        Edt.run(() -> studio.service().select(chosen));
        ProjectState before = state(a.id());
        long generation = before.generation();
        ProjectRepository.Fingerprint fingerprint = before.fingerprint().orElseThrow();
        List<Automation> catalog = Edt.call(() -> List.copyOf(studio.state().automations()));
        List<AutomationId> open = Edt.call(() -> List.copyOf(studio.state().openAutomations()));
        assertEquals("Consulta de Clientes *", tab(a));

        prompts.thenConfirm(true);
        assertEquals(ProjectService.Outcome.FAILED, await(Edt.call(() -> projects().reload(a.id()))));

        assertEquals(catalog, Edt.call(() -> List.copyOf(studio.state().automations())), "A continua no workspace, na mesma posição");
        assertSame(a, Edt.call(() -> studio.state().catalog().require(a.id())), "a MESMA automação, não a do arquivo");
        assertEquals("Entrada", a.sessions().get(0).name(), "alterações locais preservadas");
        assertSame(before, state(a.id()), "mesmo estado de projeto");
        assertEquals(generation, state(a.id()).generation());
        assertTrue(state(a.id()).dirty(), "continua com alterações não salvas");
        assertEquals(fa, state(a.id()).path().orElseThrow(), "associação com o arquivo");
        assertEquals(fingerprint, state(a.id()).fingerprint().orElseThrow());
        assertEquals(open, Edt.call(() -> List.copyOf(studio.state().openAutomations())));
        assertEquals(a.id(), Edt.call(() -> studio.state().activeId().orElseThrow()));
        assertEquals(chosen, Edt.call(() -> studio.state().selection()), "seleção preservada");
        assertEquals("Consulta de Clientes *", tab(a));
        assertEquals(fb, state(b.id()).path().orElseThrow(), "B intacto");
        assertArrayEquals(external, Files.readAllBytes(fa), "o arquivo não foi tocado");
        Notice n = studio.state().lastNotice().orElseThrow();
        assertEquals("Não foi possível recarregar", n.title());
        assertTrue(n.message().contains("O projeto aberto não mudou"), n.message());
        assertTrue(Edt.call(() -> studio.service().canUndo(a.id())), "histórico de edição preservado");
        Edt.run(() -> studio.service().undo(a.id()));
        assertEquals("Autenticação", a.sessions().get(0).name());
    }

    @Test
    @DisplayName("P1-A — recarregar e salvar não se sobrepõem: recarregar durante um salvamento (e o contrário) é recusado sem perguntar nada")
    void p1aRecarregarESalvarNaoSeSobrepoem() throws Exception {
        Ops ops = new Ops();
        start(StudioEnvironment.inMemory().withFileOps(ops));
        Automation a = automation("Consulta");
        Path file = firstSave(a, "concorrente");
        Edt.run(() -> studio.service().renameSession(a.id(), a.sessions().get(0).id(), "Entrada"));
        ops.hold = new CountDownLatch(1);
        CompletableFuture<ProjectService.Outcome> saving = Edt.call(() -> projects().save(a.id()));
        assertTrue(ops.held.await(10, TimeUnit.SECONDS), "salvamento retido no worker");
        assertEquals(ProjectService.Outcome.BUSY, await(Edt.call(() -> projects().reload(a.id()))));
        assertTrue(prompts.confirmations.isEmpty(), "nem pergunta: há um salvamento em andamento");
        ops.hold.countDown();
        assertEquals(ProjectService.Outcome.SAVED, await(saving));
        ops.hold = null;
        // recarregar em andamento (leitura retida): salvar e fechar não começam
        Edt.run(() -> studio.service().renameSession(a.id(), a.sessions().get(0).id(), "Descartável"));
        ops.holdReadIf = p -> p.getFileName().toString().equals("concorrente.nekkora");
        ops.holdRead = new CountDownLatch(1);
        prompts.thenConfirm(true);
        CompletableFuture<ProjectService.Outcome> reloading = Edt.call(() -> projects().reload(a.id()));
        assertTrue(ops.reading.await(10, TimeUnit.SECONDS), "recarga retida no worker");
        assertEquals(ProjectService.Outcome.BUSY, await(Edt.call(() -> projects().save(a.id()))));
        assertEquals(ProjectService.Outcome.BUSY, await(Edt.call(() -> projects().closeProject(a.id()))));
        assertEquals(ProjectService.Outcome.BUSY, await(Edt.call(() -> projects().reload(a.id()))));
        ops.holdRead.countDown();
        assertEquals(ProjectService.Outcome.RELOADED, await(reloading));
        Automation now = Edt.call(() -> studio.state().catalog().require(a.id()));
        assertEquals("Entrada", now.sessions().get(0).name(), "conteúdo do arquivo");
        assertFalse(state(a.id()).dirty());
        assertEquals(file, state(a.id()).path().orElseThrow());
    }

    @Test
    @DisplayName("P1-B cenário A — Descartar e sair: o Studio só encerra depois de apagar as fotografias; outro processo não as oferece")
    void p1bDescartarESair() throws Exception {
        Ops ops = new Ops();
        Path data = dir.resolve("dados");
        start(StudioEnvironment.at(data).withFileOps(ops));
        Automation a = automation("Consulta");
        firstSave(a, "descartar");
        enableRecovery();
        Edt.run(() -> studio.service().renameSession(a.id(), a.sessions().get(0).id(), "Entrada"));
        assertEquals(1, await(Edt.call(() -> projects().checkpoint())));
        Edt.run(() -> studio.service().renameSession(a.id(), a.sessions().get(0).id(), "Descartada"));
        // barreira controlada: uma fotografia está sendo gravada no worker quando o usuário pede Sair
        ops.holdIf = p -> p.startsWith(data);
        ops.hold = new CountDownLatch(1);
        CompletableFuture<Integer> inFlight = Edt.call(() -> projects().checkpoint());
        assertTrue(ops.held.await(10, TimeUnit.SECONDS), "fotografia retida no worker");
        Path disk = dir.resolve("disco-ao-encerrar");
        atExit = () -> copyTree(data, disk);
        prompts.thenUnsaved(StudioPrompts.UnsavedChoice.DISCARD);
        Edt.run(() -> studio.controller().requestExit());
        Edt.flush();
        assertEquals(0, exits.get(), "não encerra enquanto a limpeza não terminou");
        assertEquals(1, (int) Edt.call(() -> 1), "a EDT continua livre durante o encerramento");
        ops.hold.countDown();
        waitUntil(() -> exits.get() == 1);
        assertEquals(1, exits.get(), "encerra depois da limpeza");
        await(inFlight);
        assertEquals(List.of(), recoveryFiles(disk), "nenhuma fotografia no disco no instante do encerramento");
        // outro processo, com o disco exatamente como estava quando o Studio encerrou
        start(StudioEnvironment.at(disk));
        assertEquals(0, await(Edt.call(() -> studio.projects().offerRecoveries())));
        assertTrue(prompts.recoveryOffers.isEmpty(), "as alterações descartadas não são oferecidas de novo");
    }

    @Test
    @DisplayName("P1-B cenário B — salvar e sair logo em seguida: a fotografia anterior ao salvamento confirmado não volta como recuperação pendente")
    void p1bSalvarESairImediatamente() throws Exception {
        Ops ops = new Ops();
        Path data = dir.resolve("dados");
        start(StudioEnvironment.at(data).withFileOps(ops));
        Automation a = automation("A");
        firstSave(a, "a");
        enableRecovery();
        Edt.run(() -> studio.service().renameSession(a.id(), a.sessions().get(0).id(), "Entrada"));
        assertEquals(1, await(Edt.call(() -> projects().checkpoint())), "fotografia de A anterior ao salvamento");
        String folderA = a.id().value().toString();
        Automation b = automation("B");
        // barreira controlada: logo depois do salvamento de A, a fotografia de B fica retida no worker
        ops.holdIf = p -> p.startsWith(data.resolve("recovery").resolve(b.id().value().toString()));
        ops.hold = new CountDownLatch(1);
        CompletableFuture<ProjectService.Outcome> saved = Edt.call(() -> {
            CompletableFuture<ProjectService.Outcome> f = projects().save(a.id());
            projects().checkpoint();
            return f;
        });
        assertEquals(ProjectService.Outcome.SAVED, saved.get(15, TimeUnit.SECONDS));
        assertTrue(ops.held.await(10, TimeUnit.SECONDS));
        assertTrue(recoveryFiles(data).stream().noneMatch(f -> f.startsWith(folderA)),
                "o salvamento só é informado depois de apagar as fotografias que ele supera: " + recoveryFiles(data));
        Path disk = dir.resolve("disco-ao-encerrar");
        atExit = () -> copyTree(data, disk);
        prompts.thenUnsaved(StudioPrompts.UnsavedChoice.DISCARD); // B
        Edt.run(() -> studio.controller().requestExit());
        ops.hold.countDown();
        waitUntil(() -> exits.get() == 1);
        assertEquals(1, exits.get());
        assertTrue(recoveryFiles(disk).stream().noneMatch(f -> f.startsWith(folderA)), recoveryFiles(disk).toString());
        start(StudioEnvironment.at(disk));
        assertEquals(0, await(Edt.call(() -> studio.projects().offerRecoveries())));
        assertTrue(prompts.recoveryOffers.isEmpty(), "nada superado é oferecido");
    }

    @Test
    @DisplayName("P1-B — falha ao apagar fotografias: o Studio avisa, não afirma que limpou e só sai com a decisão do usuário")
    void p1bFalhaAoApagarRecuperacao() throws Exception {
        Ops ops = new Ops();
        Path data = dir.resolve("dados");
        start(StudioEnvironment.at(data).withFileOps(ops));
        Automation a = automation("Consulta");
        firstSave(a, "bloqueada");
        enableRecovery();
        ops.failDeleteIf = p -> p.startsWith(data.resolve("recovery"));
        Edt.run(() -> studio.service().renameSession(a.id(), a.sessions().get(0).id(), "Entrada"));
        assertEquals(1, await(Edt.call(() -> projects().checkpoint())));
        assertEquals(ProjectService.Outcome.SAVED, save(a.id()));
        Notice saved = studio.state().lastNotice().orElseThrow();
        assertEquals("Projeto salvo", saved.title());
        assertTrue(saved.message().contains("não pôde ser apagada"), saved.message());
        assertEquals(1, recoveryFiles(data).size());
        Edt.run(() -> studio.service().renameSession(a.id(), a.sessions().get(0).id(), "Descartada"));
        prompts.thenUnsaved(StudioPrompts.UnsavedChoice.DISCARD).thenConfirm(false);
        Edt.run(() -> studio.controller().requestExit());
        waitUntil(() -> !prompts.confirmations.isEmpty());
        assertEquals(0, exits.get(), "não sai sem a decisão do usuário");
        StudioPrompts.Confirmation c = prompts.confirmations.get(0);
        assertTrue(c.message().contains("não puderam ser apagadas"), c.message());
        assertFalse(recoveryFiles(data).isEmpty(), "de fato não foram apagadas");
        assertTrue(state(a.id()).dirty(), "nada foi descartado: o Studio continua aberto com as alterações");
        prompts.thenUnsaved(StudioPrompts.UnsavedChoice.DISCARD).thenConfirm(true);
        Edt.run(() -> studio.controller().requestExit());
        waitUntil(() -> exits.get() == 1);
        assertEquals(1, exits.get(), "sai com a decisão informada");
    }

    @Test
    @DisplayName("P2 — Salvar como: alteração externa no destino DEPOIS da confirmação de sobrescrita é conflito; nada é sobrescrito")
    void p2SobrescritaAlteradaDepoisDaConfirmacao() throws Exception {
        Ops ops = new Ops();
        start(StudioEnvironment.inMemory().withFileOps(ops));
        Automation a = automation("Consulta");
        Path existing = Files.writeString(dir.resolve("existente.nekkora"), "conteúdo que o usuário autorizou substituir");
        prompts.thenSaveAs(existing).thenConfirm(true);
        // barreira controlada entre a confirmação e a substituição: o temporário está sendo gravado
        ops.holdIf = p -> p.getFileName().toString().startsWith(".existente.nekkora.");
        ops.hold = new CountDownLatch(1);
        CompletableFuture<ProjectService.Outcome> f = Edt.call(() -> projects().save(a.id()));
        assertTrue(ops.held.await(10, TimeUnit.SECONDS));
        assertEquals(1, prompts.confirmations.size(), "a sobrescrita foi confirmada");
        Files.writeString(existing, "versão gravada por outro programa depois da confirmação");
        ops.hold.countDown();
        assertEquals(ProjectService.Outcome.CONFLICT, await(f));
        assertEquals("versão gravada por outro programa depois da confirmação", Files.readString(existing), "não sobrescreve");
        assertTrue(state(a.id()).dirty(), "as alterações continuam abertas");
        assertTrue(state(a.id()).path().isEmpty(), "o projeto não foi associado ao arquivo");
        Notice n = studio.state().lastNotice().orElseThrow();
        assertTrue(n.message().contains("depois da sua confirmação"), n.message());
        // conteúdo inalterado depois da confirmação: substitui normalmente
        prompts.thenSaveAs(existing).thenConfirm(true);
        ops.hold = null;
        assertEquals(ProjectService.Outcome.SAVED, save(a.id()));
        assertEquals(a.id(), new ProjectFiles(new ProjectRepository(), "t").load(existing, StudioFixture.CLOCK).automation().id());
    }
}
