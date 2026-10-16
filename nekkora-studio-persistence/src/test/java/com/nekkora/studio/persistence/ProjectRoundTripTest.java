package com.nekkora.studio.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.StepField;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.ManualLocator;
import com.nekkora.studio.domain.evidence.ObservationSummary;
import com.nekkora.studio.domain.evidence.ObservedEvidence;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepOrigin;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** STUDIO-006: salvar → reabrir preserva TUDO (comparação do modelo estruturado, não de telas). */
class ProjectRoundTripTest {

    @TempDir
    Path dir;

    private final RichAutomation.TickClock clock = new RichAutomation.TickClock("2026-10-16T12:00:00Z");
    private final ProjectFiles files = new ProjectFiles(new ProjectRepository(), "teste");

    private ProjectFiles.Loaded roundTrip(Automation a, String name) throws Exception {
        Path file = dir.resolve(name + ".nekkora");
        String projectId = UUID.randomUUID().toString();
        files.save(file, ProjectFiles.Snapshot.of(a, projectId), null, false, Instant.parse("2026-10-16T13:00:00Z"), clock);
        ProjectFiles.Loaded l = files.load(file, clock);
        assertEquals(projectId, l.manifest().projectId());
        return l;
    }

    @Test
    @DisplayName("S006-003 — projeto vazio (sem sessões) salvo e reaberto igual")
    void s006003ProjetoVazio() throws Exception {
        Automation a = Automation.create("Vazia", "", clock);
        Automation b = roundTrip(a, "vazia").automation();
        assertEquals(ModelDump.of(a), ModelDump.of(b));
        assertTrue(b.sessions().isEmpty());
    }

    @Test
    @DisplayName("S006-004 — uma sessão com etapas")
    void s006004UmaSessao() throws Exception {
        Automation a = Automation.create("Uma sessão", "", clock);
        var s = a.addSession("Autenticação", "");
        a.appendStep(s.id(), com.nekkora.studio.domain.StepDraft.manual("Abrir sistema", com.nekkora.studio.domain.action.ActionType.NAVIGATE, ""));
        assertEquals(ModelDump.of(a), ModelDump.of(roundTrip(a, "uma").automation()));
    }

    @Test
    @DisplayName("S006-005/006/007/008/009/010/011/012/013 — automação completa: sessões, ordem, identidades, nomes, estado e instantes idênticos")
    void s006005a013AutomacaoCompleta() throws Exception {
        Automation a = RichAutomation.build(clock);
        Automation b = roundTrip(a, "completa").automation();
        assertEquals(ModelDump.of(a), ModelDump.of(b), "dump completo antes × depois");
        assertEquals(a.id(), b.id(), "S006-008 AutomationId");
        assertEquals(a.sessions().stream().map(s -> s.id()).toList(), b.sessions().stream().map(s -> s.id()).toList(), "S006-006/009 sessões e ordem");
        assertEquals(a.sequence(), b.sequence(), "S006-007/010 etapas, identidades e ordem");
        assertEquals(List.of("Autenticação", "Consulta", "Resultado"), b.sessions().stream().map(s -> s.name()).toList());
        assertEquals("Revisar com a equipe", b.sessions().get(2).notes());
        assertEquals(a.name() + a.description(), b.name() + b.description(), "S006-011");
        assertEquals(a.status(), b.status(), "S006-012");
        assertEquals(a.createdAt(), b.createdAt(), "S006-013");
        assertEquals(a.updatedAt(), b.updatedAt(), "S006-013");
        assertEquals(a.revision(), b.revision());
    }

    @Test
    @DisplayName("S006-014/015/016/017 — procedência, valores capturados, elemento capturado e efetivo preservados (original não substituído pelo efetivo)")
    void s006014a017Origem() throws Exception {
        Automation a = RichAutomation.build(clock);
        Automation b = roundTrip(a, "origem").automation();
        AutomationStep before = RichAutomation.step(a, "Abrir Clientes");
        AutomationStep after = RichAutomation.step(b, "Abrir Clientes");
        assertEquals(StepOrigin.RECORDED, after.provenance().origin(), "gravada continua gravada");
        assertEquals(before.provenance().correlation(), after.provenance().correlation());
        assertEquals(before.provenance().recordingObservation(), after.provenance().recordingObservation());
        assertEquals("Clicar no link Clientes", after.baseline().orElseThrow().name(), "original capturado preservado");
        assertEquals("Abrir Clientes", after.name(), "efetivo preservado");
        assertTrue(after.modified(StepField.NAME) && after.manualRevision().orElseThrow().fields().contains(StepField.NAME));
        AutomationStep usuario = RichAutomation.step(b, "Preencher campo Usuário");
        assertEquals(0, usuario.capturedElement().orElseThrow().preferredLocatorIndex(), "S006-016 capturado: preferencial original");
        assertEquals(1, usuario.element().orElseThrow().preferredLocatorIndex(), "S006-017 efetivo: escolhido pelo usuário");
        assertTrue(usuario.modified(StepField.PREFERRED_LOCATOR));
        assertEquals(StepOrigin.MANUAL, RichAutomation.step(b, "Preencher Pesquisa").provenance().origin(), "manual continua manual");
    }

    @Test
    @DisplayName("S006-018/019/020 — parâmetros (variável, credencial só pelo apelido, literal) e opção configurada")
    void s006018a020Parametros() throws Exception {
        Automation b = roundTrip(RichAutomation.build(clock), "params").automation();
        assertEquals(new ValueSource.VariableReference("cliente.usuario"), RichAutomation.step(b, "Preencher campo Usuário").parameters().value().orElseThrow());
        assertEquals(new ValueSource.CredentialReference("sistema.senha"), RichAutomation.step(b, "Preencher campo Senha").parameters().value().orElseThrow());
        assertEquals(new ValueSource.LiteralValue("termo de pesquisa"), RichAutomation.step(b, "Preencher Pesquisa").parameters().value().orElseThrow());
        var option = RichAutomation.step(b, "Selecionar São Paulo no campo Estado").parameters().option().orElseThrow();
        assertEquals("São Paulo", option.label());
        assertEquals("SP", option.value());
    }

    @Test
    @DisplayName("S006-021/022/023 — localizadores capturados com classificação, preferencial apontando o MESMO, manuais continuam não verificados")
    void s006021a023Localizadores() throws Exception {
        Automation a = RichAutomation.build(clock);
        Automation b = roundTrip(a, "loc").automation();
        AutomationStep x = RichAutomation.step(a, "Preencher campo Usuário");
        AutomationStep y = RichAutomation.step(b, "Preencher campo Usuário");
        assertEquals(x.capturedElement().orElseThrow().locators(), y.capturedElement().orElseThrow().locators());
        assertEquals(x.element().orElseThrow().preferredLocator(), y.element().orElseThrow().preferredLocator());
        assertFalse(y.capturedElement().orElseThrow().locators().get(2).validated(), "não validado continua não validado");
        assertEquals(1, y.manualLocators().size());
        assertEquals(ManualLocator.Verification.NOT_VERIFIED, y.manualLocators().get(0).verification());
    }

    @Test
    @DisplayName("S006-024/025/026 — comentários (criação e edição), edição manual e vínculo derivedFrom da duplicada")
    void s006024a026Revisoes() throws Exception {
        Automation a = RichAutomation.build(clock);
        Automation b = roundTrip(a, "rev").automation();
        AutomationStep x = RichAutomation.step(a, "Clicar no botão Entrar");
        AutomationStep y = RichAutomation.step(b, "Clicar no botão Entrar");
        assertEquals(x.comments(), y.comments());
        assertTrue(y.comments().get(0).edited().isPresent(), "data de edição preservada");
        assertEquals(x.lastManualChange(), y.lastManualChange());
        AutomationStep copy = b.sessions().get(0).steps().get(3);
        assertEquals(StepOrigin.MANUAL, copy.provenance().origin(), "duplicada não vira gravada");
        assertEquals(y.id(), copy.derivedFrom().orElseThrow());
        assertTrue(copy.baseline().isEmpty() && copy.provenance().recordingObservation().isEmpty());
    }

    @Test
    @DisplayName("S006-027/028/029/030/031/033 — evidências: identidade, ordem técnica, não atribuídas, NOT_VALIDATED, sem duplicação")
    void s006027a033Evidencias() throws Exception {
        Automation a = RichAutomation.build(clock);
        Automation b = roundTrip(a, "evid").automation();
        AutomationStep x = RichAutomation.step(a, "Clicar no botão Entrar");
        AutomationStep y = RichAutomation.step(b, "Clicar no botão Entrar");
        assertEquals(x.evidence(), y.evidence(), "evidências idênticas (record: todos os campos)");
        assertEquals(List.of("evd-1", "evd-2", "evd-3"), y.evidence().stream().map(ObservedEvidence::evidenceId).toList());
        assertEquals(List.of(1L, 2L, 3L), y.evidence().stream().map(ObservedEvidence::sequence).toList(), "ordem técnica");
        assertEquals(a.unattributedEvidence(), b.unattributedEvidence());
        assertEquals(ObservedEvidence.Message.Availability.SUPPRESSED, y.evidence().get(1).message().availability());
        assertEquals(null, y.evidence().get(1).message().text(), "texto omitido continua omitido");
        ObservationSummary s = y.observation();
        assertEquals(ObservationSummary.Status.OBSERVED, s.status());
        assertTrue(s.error(), "indicador de erro observado");
        assertEquals(RecordingObservation.BusinessOutcome.NOT_VALIDATED, s.businessOutcome(), "S006-031 resultado de negócio não inventado");
        assertFalse(b.attachEvidence(x.evidence().get(0)), "S006-033 a mesma evidência não entra de novo");
        assertEquals(3, y.evidence().size());
    }

    @Test
    @DisplayName("S006-032/034 — etapas não recriadas; restaurar original funciona depois de abrir")
    void s006032e034NaoRecriaERestaura() throws Exception {
        Automation a = RichAutomation.build(clock);
        Automation b = roundTrip(a, "restaura").automation();
        AutomationStep y = RichAutomation.step(b, "Abrir Clientes");
        assertEquals(RichAutomation.id(a, "Abrir Clientes"), y.id());
        assertTrue(b.restoreOriginal(y.id(), StepField.NAME));
        assertEquals("Clicar no link Clientes", y.name());
        assertFalse(y.modified(StepField.NAME));
        assertTrue(b.restoreOriginal(RichAutomation.id(b, "Preencher campo Usuário"), StepField.PREFERRED_LOCATOR));
        assertEquals(0, RichAutomation.step(b, "Preencher campo Usuário").element().orElseThrow().preferredLocatorIndex());
    }

    @Test
    @DisplayName("§36 — dois ciclos: salvar, reabrir, editar de novo, salvar, reabrir — sem divergência")
    void doisCiclos() throws Exception {
        Automation a = RichAutomation.build(clock);
        Path file = dir.resolve("ciclos.nekkora");
        String pid = UUID.randomUUID().toString();
        var saved = files.save(file, ProjectFiles.Snapshot.of(a, pid), null, false, clock.instant(), clock);
        Automation b = files.load(file, clock).automation();
        assertEquals(ModelDump.of(a), ModelDump.of(b));
        b.addComment(RichAutomation.id(b, "Abrir Clientes"), "Ajuste depois de reabrir");
        b.moveSession(b.sessions().get(2).id(), 0);
        b.removeStep(RichAutomation.id(b, "Consultar detalhes"));
        List<String> edited = ModelDump.of(b);
        files.save(file, ProjectFiles.Snapshot.of(b, pid), saved.fingerprint(), false, clock.instant(), clock);
        Automation c = files.load(file, clock).automation();
        assertEquals(edited, ModelDump.of(c));
        assertNotNull(c.step(RichAutomation.id(b, "Abrir Clientes")).orElseThrow());
    }
}
