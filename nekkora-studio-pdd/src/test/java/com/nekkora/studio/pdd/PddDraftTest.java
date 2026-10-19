package com.nekkora.studio.pdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepContext;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.pdd.DocumentDraft.Annotation;
import com.nekkora.studio.pdd.DocumentDraft.Block;
import com.nekkora.studio.pdd.DocumentDraft.Pending;
import com.nekkora.studio.pdd.DocumentDraft.SafeCapture;
import com.nekkora.studio.pdd.DraftEdits.DraftException;
import com.nekkora.studio.pdd.ProcessProjection.Origin;
import com.nekkora.studio.pdd.ProcessProjection.StepFacts;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** STUDIO-010 G1/G2/G3 — projeção, agrupamento, instruções e rascunho, sem navegador e sem interface. */
class PddDraftTest {

    /** "Impressão digital" do que o PDD jamais pode alterar na automação. */
    private static String fingerprint(Automation a) {
        StringBuilder b = new StringBuilder().append(a.revision()).append('|').append(a.name()).append('|');
        for (LogicalSession s : a.sessions()) {
            b.append(s.id()).append(s.name()).append(s.description()).append(s.notes()).append('[');
            for (AutomationStep st : s.steps()) {
                b.append(st.id()).append(st.name()).append(st.actionType()).append(st.parameters()).append(st.element().map(Object::toString).orElse(""))
                        .append(st.context()).append(st.targetDecision()).append(';');
            }
            b.append(']');
        }
        return b.toString();
    }

    @Test
    @DisplayName("G1 determinismo e não mutação: mesma automação → mesma revisão e mesmos bytes; projetar, montar e editar o rascunho não muda a automação")
    void determinismoENaoMutacao() {
        Automation a = PddLab.consulta();
        String before = fingerprint(a);
        ProcessProjection p1 = ProcessProjection.of(a);
        ProcessProjection p2 = ProcessProjection.of(a);
        assertEquals(p1.revision(), p2.revision());
        DocumentDraft d1 = DraftBuilder.build(p1);
        assertEquals(DraftJson.write(d1), DraftJson.write(DraftBuilder.build(p2)), "mesmos bytes");
        assertEquals(d1.revision(), DraftBuilder.build(p2).revision());
        String login = d1.sections().get(0).blocks().get(0).blockId();
        DocumentDraft edited = DraftEdits.split(d1, p1, login, 2);
        edited = DraftEdits.reviseBlockTitle(edited, login, "Realizar autenticação", "TEST_AGENT");
        edited = DraftEdits.join(edited, p1, login, edited.sections().get(0).blocks().get(1).blockId());
        DraftEdits.reviseSectionTitle(edited, 1, "Autenticação", "TEST_AGENT");
        assertEquals(before, fingerprint(a), "a automação não muda (inclusive a revisão do domínio)");
        assertTrue(p1.revision().matches("sha256:[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("G1 revisão do processo: muda com a ordem e o conteúdo (nova etapa, outra ação); não muda com nome livre de etapa")
    void revisaoDoProcesso() {
        Automation a = PddLab.consulta();
        String r0 = ProcessProjection.of(a).revision();
        AutomationStep first = a.sessions().get(0).steps().get(0);
        a.updateStepBasics(first.id(), "Outro nome com CPF 123.456.789-09", first.description(), first.actionType());
        assertEquals(r0, ProcessProjection.of(a).revision(), "nome livre da etapa não faz parte do documento");
        a.moveStep(first.id(), a.sessions().get(0).id(), 1);
        String r1 = ProcessProjection.of(a).revision();
        assertNotEquals(r0, r1, "ordem");
        a.appendStep(a.sessions().get(1).id(), StepDraft.builder().name("x").actionType(ActionType.WAIT_FOR)
                .context(new StepContext("Laboratório", "Consulta", PddLab.CONSULTA)).build());
        assertNotEquals(r1, ProcessProjection.of(a).revision(), "etapa nova");
    }

    @Test
    @DisplayName("G1 rastreabilidade: toda etapa em exatamente um bloco, na ordem, com uma instrução; seções na ordem das sessões")
    void rastreabilidade() {
        Automation a = PddLab.consulta();
        DocumentDraft d = DraftBuilder.build(ProcessProjection.of(a));
        assertEquals(a.sessions().stream().map(LogicalSession::id).toList(), d.sections().stream().map(DocumentDraft.Section::sessionId).toList());
        for (int i = 0; i < a.sessions().size(); i++) {
            List<StepId> inBlocks = new ArrayList<>();
            d.sections().get(i).blocks().forEach(b -> inBlocks.addAll(b.stepIds()));
            assertEquals(a.sessions().get(i).steps().stream().map(AutomationStep::id).toList(), inBlocks, "zero perda, mesma ordem");
        }
        for (Block b : d.blocks()) {
            assertEquals(b.stepIds(), b.instructions().stream().map(DocumentDraft.Instruction::stepId).toList());
            for (int k = 0; k < b.instructions().size(); k++) assertEquals(k + 1, b.instructions().get(k).ordinal(), "numeração 1, 2, 3");
        }
    }

    @Test
    @DisplayName("G1/G3 sem dados internos: valores, variáveis, apelidos, seletores, ids de alvo, URLs, nomes e notas livres nunca entram no rascunho")
    void semDadosInternos() {
        Automation a = PddLab.consulta();
        String json = DraftJson.write(DraftBuilder.build(ProcessProjection.of(a)));
        for (String s : PddLab.SENTINELS) assertFalse(json.contains(s), "sentinela no rascunho: " + s);
    }

    @Test
    @DisplayName("G2 agrupamento: login (3 etapas) = 1 bloco; consulta = 1 bloco; resultado = 1 bloco; uma sessão com 2 telas = 2 blocos")
    void agrupamento() {
        Automation a = PddLab.consulta();
        DocumentDraft d = DraftBuilder.build(ProcessProjection.of(a));
        assertEquals(List.of(1, 1, 1), d.sections().stream().map(s -> s.blocks().size()).toList());
        assertEquals(3, d.sections().get(0).blocks().get(0).stepIds().size());
        // a sessão Consulta passa a ter uma segunda tela (Resultado) depois do clique
        LogicalSession consulta = a.sessions().get(1);
        a.appendStep(consulta.id(), StepDraft.builder().name("y").actionType(ActionType.OBSERVE)
                .context(new StepContext("Laboratório", "Resultado", PddLab.RESULTADO)).build());
        DocumentDraft d2 = DraftBuilder.build(ProcessProjection.of(a));
        assertEquals(2, d2.sections().get(1).blocks().size(), "sessão ≠ página: troca de tela abre bloco novo");
        // sessão vazia vira seção sem blocos (nada inventado)
        a.addSession("Vazia", "");
        DocumentDraft d3 = DraftBuilder.build(ProcessProjection.of(a));
        assertTrue(d3.sections().get(3).blocks().isEmpty());
    }

    @Test
    @DisplayName("G2 fronteiras: troca de rota/documento observada, navegação e tela desconhecida separam blocos")
    void fronteiras() {
        StepId x = new StepId(java.util.UUID.randomUUID());
        StepFacts fill = new StepFacts(x, 1, Origin.RECORDED, ActionType.FILL, true, "TEXT_INPUT", "Usuário", false, "", "tela-a", false);
        StepFacts click = new StepFacts(x, 2, Origin.RECORDED, ActionType.CLICK, true, "BUTTON", "Entrar", false, "", "tela-a", false);
        assertFalse(DraftBuilder.boundary(fill, click));
        assertTrue(DraftBuilder.boundary(new StepFacts(x, 1, Origin.RECORDED, ActionType.CLICK, true, "BUTTON", "Entrar", false, "", "tela-a", true), fill),
                "evidência de troca de documento depois da etapa");
        assertTrue(DraftBuilder.boundary(fill, new StepFacts(x, 2, Origin.DECLARED, ActionType.NAVIGATE, false, "", "", false, "", "tela-a", false)));
        assertTrue(DraftBuilder.boundary(fill, new StepFacts(x, 2, Origin.DECLARED, ActionType.CLICK, true, "BUTTON", "Ok", false, "", "", false)),
                "tela conhecida × desconhecida");
        assertTrue(DraftBuilder.boundary(fill, new StepFacts(x, 2, Origin.RECORDED, ActionType.CLICK, true, "BUTTON", "Ok", false, "", "tela-b", false)));
    }

    @Test
    @DisplayName("G2 dividir/juntar: só no rascunho, sem perda; juntar exige blocos adjacentes da mesma tela; captura nunca sobrevive")
    void dividirJuntar() {
        Automation a = PddLab.consulta();
        ProcessProjection p = ProcessProjection.of(a);
        DocumentDraft d = DraftBuilder.build(p);
        Block login = d.sections().get(0).blocks().get(0);
        DocumentDraft s = DraftEdits.split(d, p, login.blockId(), 2);
        assertEquals(List.of(2, 1), s.sections().get(0).blocks().stream().map(b -> b.stepIds().size()).toList());
        assertEquals(login.blockId(), s.sections().get(0).blocks().get(0).blockId(), "a primeira parte mantém a identidade");
        assertEquals(1, s.sections().get(0).blocks().get(1).instructions().get(0).ordinal(), "renumeração por bloco");
        DocumentDraft j = DraftEdits.join(s, p, login.blockId(), s.sections().get(0).blocks().get(1).blockId());
        assertEquals(login.stepIds(), j.sections().get(0).blocks().get(0).stepIds());
        assertEquals("SPLIT_POSITION_INVALID", assertThrows(DraftException.class, () -> DraftEdits.split(d, p, login.blockId(), 3)).code());
        String consulta = d.sections().get(1).blocks().get(0).blockId();
        assertEquals("BLOCKS_NOT_ADJACENT", assertThrows(DraftException.class, () -> DraftEdits.join(d, p, login.blockId(), consulta)).code(),
                "blocos de sessões diferentes nunca se juntam");
        // mesma sessão, telas diferentes
        a.appendStep(a.sessions().get(1).id(), StepDraft.builder().name("z").actionType(ActionType.OBSERVE)
                .context(new StepContext("Laboratório", "Resultado", PddLab.RESULTADO)).build());
        ProcessProjection p2 = ProcessProjection.of(a);
        DocumentDraft d2 = DraftBuilder.build(p2);
        List<Block> bs = d2.sections().get(1).blocks();
        assertEquals("JOIN_CROSSES_SCREEN", assertThrows(DraftException.class, () -> DraftEdits.join(d2, p2, bs.get(0).blockId(), bs.get(1).blockId())).code());
    }

    @Test
    @DisplayName("G3 instruções do login e da consulta: modelos determinísticos, sem valor, variável, apelido ou seletor; título pela composição")
    void instrucoes() {
        DocumentDraft d = DraftBuilder.build(ProcessProjection.of(PddLab.consulta()));
        Block login = d.sections().get(0).blocks().get(0);
        assertEquals(List.of("Informar o campo “Usuário”", "Informar a senha", "Selecionar “Entrar”"),
                login.instructions().stream().map(i -> i.text().value()).toList());
        assertTrue(login.instructions().stream().allMatch(i -> i.text().state() == DocText.State.DERIVED && i.kind() == DocumentDraft.Instruction.Kind.OBSERVED));
        assertEquals("Preencher os campos e selecionar “Entrar”", login.title().value());
        Block consulta = d.sections().get(1).blocks().get(0);
        assertEquals(List.of("Informar o campo “Código do registro”", "Selecionar “Consultar”"),
                consulta.instructions().stream().map(i -> i.text().value()).toList());
        Block resultado = d.sections().get(2).blocks().get(0);
        assertEquals("Observar a tela exibida", resultado.instructions().get(0).text().value());
        assertEquals(DocumentDraft.Instruction.Kind.DECLARED, resultado.instructions().get(0).kind(), "etapa manual = intenção declarada");
        // títulos de seção: só nomes do vocabulário passam sem revisão
        assertEquals(DocText.State.NEEDS_REVIEW, d.sections().get(0).title().state(), "\"Autenticação\" não é termo do vocabulário: precisa de revisão");
        assertEquals("Sessão 1", d.sections().get(0).title().value());
        assertEquals("Consulta", d.sections().get(1).title().value());
        assertEquals(DocText.State.DERIVED, d.sections().get(1).title().state());
    }

    @Test
    @DisplayName("G3 nome do alvo fora da política (pessoa, dígitos) ou ausente: instrução neutra e para revisão — nunca o nome")
    void nomeForaDaPolitica() {
        Automation a = Automation.create("x", "", PddLab.CLOCK);
        LogicalSession s = a.addSession("Consulta", "");
        PddLab.recorded(a, s, "a", ActionType.FILL, PddLab.el("tgt-1", "TEXT_INPUT", "input", "Código do registro de Ana Souza", "#a"),
                ActionParameters.none(), PddLab.CONSULTA, "Consulta");
        PddLab.recorded(a, s, "b", ActionType.CLICK, PddLab.el("tgt-2", "BUTTON", "button", "Pedido 483192", "#b"), ActionParameters.none(), PddLab.CONSULTA,
                "Consulta");
        a.appendStep(s.id(), StepDraft.builder().name("c").actionType(ActionType.UNKNOWN).context(new StepContext("Laboratório", "Consulta", PddLab.CONSULTA))
                .build());
        Block b = DraftBuilder.build(ProcessProjection.of(a)).sections().get(0).blocks().get(0);
        assertEquals(List.of("Informar o campo", "Selecionar o elemento indicado", "Executar a etapa 3"),
                b.instructions().stream().map(i -> i.text().value()).toList());
        assertTrue(b.instructions().stream().allMatch(i -> i.text().state() == DocText.State.NEEDS_REVIEW));
        assertFalse(DraftJson.write(DraftBuilder.build(ProcessProjection.of(a))).contains("Ana Souza"));
    }

    @Test
    @DisplayName("G3 texto revisado: CPF, dígitos, e-mail, URL, seletor, expressão de variável e identificador técnico recusados; \"Entrar\" permitido")
    void textoRevisado() {
        ProcessProjection p = ProcessProjection.of(PddLab.consulta());
        DocumentDraft d = DraftBuilder.build(p);
        String b = d.sections().get(0).blocks().get(0).blockId();
        for (String bad : List.of("Informar CPF 123.456.789-09", "Usar o código 4321", "Enviar para ana@exemplo.com", "Abrir https://intranet/x",
                "Clicar em #entrar", "Preencher //input[@id='u']", "Usar ${registro.codigo}", "Informar registro.codigo", "Ver tgt-abc123def")) {
            DraftException e = assertThrows(DraftException.class, () -> DraftEdits.reviseInstruction(d, b, 1, bad, "TEST_AGENT"), bad);
            assertTrue(e.code().startsWith("TEXT_POLICY"), bad + " → " + e.code());
        }
        DocumentDraft ok = DraftEdits.reviseInstruction(d, b, 3, "Selecionar Entrar", "TEST_AGENT");
        assertEquals(DocText.State.REVIEWED, ok.block(b).orElseThrow().instructions().get(2).text().state());
        assertThrows(IllegalArgumentException.class, () -> DocText.reviewed("x", "HUMAN_CONFIRMED"), "nunca rótulo de confirmação humana");
    }

    @Test
    @DisplayName("G1/G8 pendências: textos para revisar bloqueiam; imagem e marcador ausentes só avisam; automação alterada exige atualizar")
    void pendencias() {
        Automation a = PddLab.consulta();
        ProcessProjection p = ProcessProjection.of(a);
        DocumentDraft d = DraftBuilder.build(p);
        List<String> codes = d.pending(p).stream().map(Pending::code).toList();
        assertTrue(codes.contains("SECTION_TITLE_REVIEW"));
        assertTrue(codes.contains("CAPTURE_MISSING"));
        assertFalse(Pendings.exportable(d.pending(p)));
        DocumentDraft r = DraftEdits.reviseSectionTitle(d, 1, "Autenticação", "TEST_AGENT");
        assertTrue(Pendings.exportable(r.pending(p)), "só avisos restam: " + r.pending(p));
        a.appendStep(a.sessions().get(1).id(), StepDraft.builder().name("w").actionType(ActionType.WAIT_FOR)
                .context(new StepContext("Laboratório", "Consulta", PddLab.CONSULTA)).build());
        ProcessProjection now = ProcessProjection.of(a);
        assertTrue(r.pending(now).stream().anyMatch(x -> x.code().equals("PROCESS_CHANGED") && x.blocking()));
        DocumentDraft refreshed = DraftEdits.refresh(r, p, now);
        assertEquals(now.revision(), refreshed.processRevision());
        assertEquals(DocText.State.REVIEWED, refreshed.sections().get(0).title().state(), "revisão de seção preservada");
        assertEquals(3, refreshed.sections().get(1).blocks().get(0).stepIds().size(), "etapa nova entrou no bloco da consulta");
        Block changed = refreshed.sections().get(1).blocks().get(0);
        assertTrue(refreshed.pending(now).stream().anyMatch(x -> x.code().equals("TITLE_REVIEW") && x.place().equals(changed.blockId())),
                "composição nova (preencher, clicar, aguardar) não tem modelo de título: revisar");
        DocumentDraft done = DraftEdits.reviseBlockTitle(refreshed, changed.blockId(), "Consultar o registro", "TEST_AGENT");
        assertTrue(Pendings.exportable(done.pending(now)), done.pending(now).toString());
    }

    @Test
    @DisplayName("G4/G5 captura no rascunho: só para as mesmas etapas e a mesma tela; dividir descarta; marcador omitido vira aviso")
    void captura() {
        ProcessProjection p = ProcessProjection.of(PddLab.consulta());
        DocumentDraft d = DraftBuilder.build(p);
        Block login = d.sections().get(0).blocks().get(0);
        Block consulta = d.sections().get(1).blocks().get(0);
        SafeCapture c = capture(login, List.of(new Annotation(login.stepIds().get(0), Annotation.Status.PLACED, 0.1, 0.2, 0.3, 0.05, ""),
                new Annotation(login.stepIds().get(1), Annotation.Status.PLACED, 0.1, 0.3, 0.3, 0.05, ""),
                new Annotation(login.stepIds().get(2), Annotation.Status.OMITTED, 0, 0, 0, 0, "TARGET_AMBIGUOUS")));
        DocumentDraft w = DraftEdits.attachCapture(d, login.blockId(), c);
        assertEquals(c.sha256(), w.block(login.blockId()).orElseThrow().captureSha());
        assertTrue(w.pending(p).stream().anyMatch(x -> x.code().equals("MARKER_UNAVAILABLE") && x.instruction() == 3 && !x.blocking()));
        assertEquals("CAPTURE_DOES_NOT_MATCH_BLOCK", assertThrows(DraftException.class, () -> DraftEdits.attachCapture(d, consulta.blockId(), c)).code(),
                "a imagem do login nunca ilustra a consulta");
        DocumentDraft split = DraftEdits.split(w, p, login.blockId(), 2);
        assertTrue(split.blocks().stream().allMatch(b -> b.capture().isEmpty()), "dividir descarta a captura (não inventa)");
        assertTrue(DraftEdits.dropUnreferencedCaptures(split).captures().isEmpty(), "retenção: sem referência, sem imagem");
        DocumentDraft back = DraftJson.read(DraftJson.write(w));
        assertEquals(DraftJson.write(w), DraftJson.write(back), "ida e volta sem perda");
    }

    @Test
    @DisplayName("G1 leitura estrita: campo desconhecido, outra versão e texto adulterado (revisado com CPF) não voltam como exportáveis")
    void leituraEstrita() {
        ProcessProjection p = ProcessProjection.of(PddLab.consulta());
        String json = DraftJson.write(DraftEdits.reviseSectionTitle(DraftBuilder.build(p), 1, "Autenticação", "TEST_AGENT"));
        assertThrows(IllegalArgumentException.class, () -> DraftJson.read(json.replace("\"schema\"", "\"extra\": 1,\n  \"schema\"")));
        // STUDIO-012 PDD-01: 1.1.0 passou a existir (lido com regras próprias); versões desconhecidas continuam recusadas
        assertThrows(IllegalArgumentException.class, () -> DraftJson.read(json.replace("\"1.0.0\"", "\"1.2.0\"")));
        assertThrows(IllegalArgumentException.class, () -> DraftJson.read(json.replace("\"1.0.0\"", "\"2.0.0\"")));
        DocumentDraft tampered = DraftJson.read(json.replace("\"value\": \"Autenticação\"", "\"value\": \"CPF 123.456.789-09\""));
        assertTrue(tampered.pending(p).stream().anyMatch(x -> x.code().equals("TEXT_POLICY") && x.blocking()));
    }

    static SafeCapture capture(Block b, List<Annotation> anns) {
        return new SafeCapture("sha256:" + "a".repeat(64), 1280, 720, 1.0, Instant.parse("2026-10-17T12:00:00Z"), 1, "nekkora-visual/1", b.screenKey(),
                b.stepIds(), anns, List.of(), List.of(new DocumentDraft.Region("CARRIER", 10, 10, 40, 20)));
    }

    @SuppressWarnings("unused")
    private static String codes(List<Pending> p) {
        return p.stream().map(Pending::code).collect(Collectors.joining(","));
    }
}
