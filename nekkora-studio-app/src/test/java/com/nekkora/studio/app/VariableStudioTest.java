package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.app.recorder.RecorderPrompts;
import com.nekkora.studio.app.recorder.RecorderWindowHost;
import com.nekkora.studio.app.workspace.ProjectService;
import com.nekkora.studio.app.workspace.ProjectState;
import com.nekkora.studio.bridge.BrowserLink;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.DomainValidationException;
import com.nekkora.studio.domain.ModelVersion;
import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.variable.SemanticAuthority;
import com.nekkora.studio.domain.variable.SemanticType;
import com.nekkora.studio.domain.variable.Sensitivity;
import com.nekkora.studio.domain.variable.TextFormat;
import com.nekkora.studio.domain.variable.VariableConstraints;
import com.nekkora.studio.domain.variable.VariableDefinition;
import com.nekkora.studio.domain.variable.VariableDraft;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.variable.VariableType;
import com.nekkora.studio.persistence.ProjectFiles;
import com.nekkora.studio.persistence.ProjectFormat;
import com.nekkora.studio.persistence.ProjectRepository;
import com.nekkora.tokens.NekkoraThemes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** STUDIO-008: operações de variáveis no Studio montado (histórico, alterações não salvas) e migração do formato ao salvar. */
class VariableStudioTest {

    @TempDir
    Path dir;

    private final ScriptedPrompts prompts = new ScriptedPrompts();
    private final List<Studio> started = new ArrayList<>();
    private Studio studio;
    private final ProjectFiles files = new ProjectFiles(new ProjectRepository(), "teste");

    private Studio start() {
        Edt.theme(NekkoraThemes.SAKURA_DARK);
        Studio s = Edt.call(() -> Studio.create(StudioFixture.CLOCK, prompts, () -> { }, BrowserLink.offline("DISABLED"), RecorderPrompts.none(),
                p -> RecorderWindowHost.headless(), StudioEnvironment.inMemory()));
        Edt.render(s.view().component(), 1440, 900);
        started.add(s);
        studio = s;
        return s;
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

    private Automation automation(String name) {
        prompts.then(ScriptedPrompts.name(name, "Processo fictício"));
        Edt.run(() -> studio.controller().newAutomation());
        prompts.then(ScriptedPrompts.name("Cadastro", null));
        Edt.run(() -> studio.controller().newSession());
        prompts.then(ScriptedPrompts.step("Cadastro", "Preencher CPF", ActionType.FILL));
        Edt.run(() -> studio.controller().newStep(null));
        return studio.state().activeAutomation().orElseThrow();
    }

    private ProjectService.Outcome save(Automation a) throws Exception {
        return await(Edt.call(() -> studio.projects().save(a.id())));
    }

    private ProjectState state(Automation a) {
        return Edt.call(() -> studio.state().project(a.id()).orElseThrow());
    }

    private static VariableDraft cpf() {
        return VariableDraft.of("cliente.cpf", VariableType.STRING).withSemantic(SemanticType.BRAZILIAN_CPF, SemanticAuthority.USER_CONFIRMED)
                .withSensitivity(Sensitivity.PERSONAL_DATA).withOrigin(VariableOrigin.input())
                .withConstraints(new VariableConstraints(true, 11, 11, TextFormat.DIGITS, null, null, null, null));
    }

    @Test
    @DisplayName("S008 — declarar, renomear e excluir: edições desfazíveis que marcam o projeto como alterado")
    void operacoesComHistorico() throws Exception {
        start();
        Automation a = automation("Cadastro de Clientes");
        AutomationStep step = a.sessions().get(0).steps().get(0);
        Path file = dir.resolve("cadastro.nekkora");
        prompts.thenSaveAs(file);
        assertEquals(ProjectService.Outcome.SAVED, save(a));
        assertFalse(state(a).dirty());

        VariableDefinition v = Edt.call(() -> studio.service().declareVariable(a.id(), cpf()));
        assertTrue(state(a).dirty(), "declarar é uma alteração");
        Edt.run(() -> studio.service().setStepParameters(a.id(), step.id(), ActionParameters.withValue(new ValueSource.VariableReference("cliente.cpf"))));
        assertEquals(1, (int) Edt.call(() -> studio.service().renameVariable(a.id(), v.id(), "titular.cpf")));
        assertEquals(new ValueSource.VariableReference("titular.cpf"), a.sessions().get(0).steps().get(0).parameters().value().orElseThrow());
        Edt.run(() -> studio.service().undo(a.id()));
        assertEquals("cliente.cpf", a.variable(v.id()).orElseThrow().name(), "desfazer devolve o nome e a referência juntos");
        assertEquals(new ValueSource.VariableReference("cliente.cpf"), a.sessions().get(0).steps().get(0).parameters().value().orElseThrow());
        Edt.run(() -> studio.service().redo(a.id()));
        assertEquals("titular.cpf", a.variable(v.id()).orElseThrow().name());

        DomainValidationException inUse = assertThrows(DomainValidationException.class,
                () -> Edt.call(() -> studio.service().removeVariable(a.id(), v.id(), false)));
        assertEquals(ValidationProblem.VARIABLE_IN_USE, inUse.problem());
        assertEquals(1, (int) Edt.call(() -> studio.service().removeVariable(a.id(), v.id(), true)));
        assertTrue(a.variables().isEmpty());
        Edt.run(() -> studio.service().undo(a.id()));
        assertEquals(1, a.variables().size(), "exclusão desfeita");
        assertEquals(new ValueSource.VariableReference("titular.cpf"), a.sessions().get(0).steps().get(0).parameters().value().orElseThrow());
    }

    @Test
    @DisplayName("S008 — projeto 1.0 com variável nova: migração só com confirmação e cópia byte a byte do original")
    void migracaoComConfirmacaoECopia() throws Exception {
        start();
        Automation a = automation("Cadastro de Clientes");
        Path file = dir.resolve("cadastro.nekkora");
        prompts.thenSaveAs(file);
        assertEquals(ProjectService.Outcome.SAVED, save(a));
        assertEquals(ProjectFormat.FORMAT_VERSION, files.load(file, StudioFixture.CLOCK).manifest().formatVersion(), "sem variáveis: 1.0.0");
        assertEquals(ProjectFormat.FORMAT_VERSION, state(a).fileFormat().orElseThrow());
        byte[] original = Files.readAllBytes(file);

        Edt.call(() -> studio.service().declareVariable(a.id(), cpf()));
        prompts.thenConfirm(false);
        assertEquals(ProjectService.Outcome.CANCELLED, save(a));
        assertEquals("Atualizar o formato do projeto?", prompts.confirmations.get(prompts.confirmations.size() - 1).title());
        assertTrue(prompts.confirmations.get(prompts.confirmations.size() - 1).message().contains("cadastro (formato 1.0.0).nekkora"));
        assertArrayEquals(original, Files.readAllBytes(file), "recusar não toca no arquivo");
        assertTrue(state(a).dirty());
        try (Stream<Path> s = Files.list(dir)) {
            assertEquals(1, s.count(), "nenhuma cópia sem confirmação");
        }

        prompts.thenConfirm(true);
        assertEquals(ProjectService.Outcome.SAVED, save(a));
        Path backup = dir.resolve("cadastro (formato 1.0.0).nekkora");
        assertArrayEquals(original, Files.readAllBytes(backup), "cópia idêntica ao arquivo 1.0.0");
        assertEquals(ProjectFormat.FORMAT_VERSION, files.load(backup, StudioFixture.CLOCK).manifest().formatVersion());
        ProjectFiles.Loaded migrated = files.load(file, StudioFixture.CLOCK);
        assertEquals(ProjectFormat.FORMAT_VERSION_1_1, migrated.manifest().formatVersion());
        assertEquals(ModelVersion.V1_1, migrated.automation().modelVersion());
        assertEquals(1, migrated.automation().variables().size());
        assertTrue(studio.state().lastNotice().orElseThrow().message().contains("cadastro (formato 1.0.0).nekkora"));
        assertEquals(ProjectFormat.FORMAT_VERSION_1_1, state(a).fileFormat().orElseThrow());

        // já em 1.1: salvar de novo não pergunta nem copia
        Edt.run(() -> studio.service().renameSession(a.id(), a.sessions().get(0).id(), "Cadastro do titular"));
        int asked = prompts.confirmations.size();
        assertEquals(ProjectService.Outcome.SAVED, save(a));
        assertEquals(asked, prompts.confirmations.size());
        try (Stream<Path> s = Files.list(dir)) {
            assertEquals(2, s.filter(p -> p.toString().endsWith(".nekkora")).count());
        }
    }

    @Test
    @DisplayName("S008 — uma segunda migração não substitui a cópia existente; Salvar como em outro arquivo não migra o original")
    void copiaNuncaSubstituida() throws Exception {
        start();
        Automation a = automation("Cadastro");
        Path file = dir.resolve("cadastro.nekkora");
        prompts.thenSaveAs(file);
        assertEquals(ProjectService.Outcome.SAVED, save(a));
        Files.writeString(dir.resolve("cadastro (formato 1.0.0).nekkora"), "cópia antiga do usuário");
        Edt.call(() -> studio.service().declareVariable(a.id(), cpf()));
        prompts.thenConfirm(true);
        assertEquals(ProjectService.Outcome.SAVED, save(a));
        assertEquals("cópia antiga do usuário", Files.readString(dir.resolve("cadastro (formato 1.0.0).nekkora")));
        assertTrue(Files.exists(dir.resolve("cadastro (formato 1.0.0) 2.nekkora")));

        Automation b = automation("Outro cadastro");
        Path old = dir.resolve("outro.nekkora");
        prompts.thenSaveAs(old);
        assertEquals(ProjectService.Outcome.SAVED, save(b));
        byte[] before = Files.readAllBytes(old);
        Edt.call(() -> studio.service().declareVariable(b.id(), cpf()));
        Path other = dir.resolve("outro-1.1.nekkora");
        prompts.thenSaveAs(other);
        int asked = prompts.confirmations.size();
        assertEquals(ProjectService.Outcome.SAVED, await(Edt.call(() -> studio.projects().saveAs(b.id()))));
        assertEquals(asked, prompts.confirmations.size(), "outro arquivo: nada a migrar");
        assertArrayEquals(before, Files.readAllBytes(old), "o arquivo 1.0.0 continua intacto");
        assertEquals(ProjectFormat.FORMAT_VERSION_1_1, files.load(other, StudioFixture.CLOCK).manifest().formatVersion());
    }
}
