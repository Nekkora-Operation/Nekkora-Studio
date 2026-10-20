package com.nekkora.studio.ai.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.ai.ConsultaCase;
import com.nekkora.studio.ai.context.ProjectContext;
import com.nekkora.studio.ai.quality.QualityIssue;
import com.nekkora.studio.ai.spec.EffectVocabulary;
import com.nekkora.studio.ai.spec.FunctionalSpec;
import com.nekkora.studio.ai.spec.SpecResolver;
import com.nekkora.studio.pdd.DocumentDraft;
import com.nekkora.studio.pdd.DraftBuilder;
import com.nekkora.studio.pdd.ProcessProjection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PddGateTest {

    static PddGate.Result gate(ConsultaCase c, Optional<DocumentDraft> d, FunctionalSpec spec) {
        return PddGate.evaluate(ProjectContext.of(c.a, d), d, spec, ProcessProjection.of(c.a), ConsultaCase.CLOCK.instant());
    }

    static List<String> codes(PddGate.Result r) {
        return r.issues().stream().filter(i -> i.kind().blocking()).map(QualityIssue::code).toList();
    }

    @Test
    @DisplayName("PDD revisado da revisão atual + efeitos derivados do vocabulário + leitura do resultado → PDD_READY com as revisões")
    void pronto() {
        ConsultaCase c = ConsultaCase.confirmed();
        DocumentDraft d = c.reviewedDraft();
        PddGate.Result r = gate(c, Optional.of(d), FunctionalSpec.empty(c.a.id()));
        assertEquals(PddGate.State.PDD_READY, r.state(), codes(r).toString());
        assertEquals(EffectVocabulary.Effect.NONE, r.spec().effects().get(c.enter).value());
        assertEquals(SpecResolver.Provenance.DERIVED_LABEL, r.spec().effects().get(c.query).provenance());
        assertEquals(c.result, r.spec().conclusion().value());
        GateRecord rec = r.record();
        assertEquals(c.a.revision(), rec.automationRevision());
        assertEquals(d.revision(), rec.documentRevision());
        assertEquals(rec.totalSteps(), rec.coveredSteps());
        assertTrue(rec.reviewers().contains("TEST_AGENT"));
        assertEquals(rec, GateRecord.parse(rec.toJson()), "registro relido igual");
    }

    @Test
    @DisplayName("sem PDD, PDD de outra revisão e texto a revisar bloqueiam (PDF não entra na conta)")
    void bloqueios() {
        ConsultaCase c = ConsultaCase.confirmed();
        assertEquals(List.of("PDD_MISSING"), codes(gate(c, Optional.empty(), FunctionalSpec.empty(c.a.id()))));
        DocumentDraft d = c.reviewedDraft();
        c.a.renameSession(c.a.sessions().get(1).id(), "Consulta de registros"); // muda o conteúdo do processo
        PddGate.Result r = gate(c, Optional.of(d), FunctionalSpec.empty(c.a.id()));
        assertTrue(codes(r).contains("PDD_OUTDATED"), codes(r).toString());
        ConsultaCase fresh = ConsultaCase.confirmed();
        DocumentDraft raw = DraftBuilder.build(ProcessProjection.of(fresh.a)); // sem revisão dos textos marcados
        PddGate.Result r2 = gate(fresh, Optional.of(raw), FunctionalSpec.empty(fresh.a.id()));
        if (raw.pending(ProcessProjection.of(fresh.a)).stream().anyMatch(DocumentDraft.Pending::blocking)) {
            assertEquals(PddGate.State.PDD_BLOCKED, r2.state());
            assertTrue(codes(r2).stream().anyMatch(x -> x.endsWith("_REVIEW") || x.equals("PDD_TEXT_POLICY")), codes(r2).toString());
        }
    }

    @Test
    @DisplayName("bloco sem imagem é limitação visual (INFO), não bloqueio")
    void semImagem() {
        ConsultaCase c = ConsultaCase.confirmed();
        PddGate.Result r = gate(c, Optional.of(c.reviewedDraft()), FunctionalSpec.empty(c.a.id()));
        assertTrue(r.issues().stream().anyMatch(i -> i.code().equals("PDD_CAPTURE_MISSING") && i.kind() == QualityIssue.Kind.INFO));
        assertTrue(r.ready());
    }

    @Test
    @DisplayName("rótulo fora do vocabulário → pedido de ajuda com destino; a resposta (com ator) libera e é invalidada se a etapa mudar")
    void ajudaEfeito() {
        ConsultaCase c = ConsultaCase.unknownButton();
        DocumentDraft d = c.reviewedDraft();
        PddGate.Result r = gate(c, Optional.of(d), FunctionalSpec.empty(c.a.id()));
        assertEquals(List.of("SPEC_EFFECT_UNKNOWN"), codes(r));
        QualityIssue q = r.issues().stream().filter(i -> i.code().equals("SPEC_EFFECT_UNKNOWN")).findFirst().orElseThrow();
        assertEquals(c.query, q.step());
        assertEquals(FunctionalSpec.Field.EFFECT, q.assistance().orElseThrow().destination());
        assertTrue(q.assistance().orElseThrow().options().stream().anyMatch(o -> "EXTERNAL".equals(o.value())));
        String fp = ProjectContext.of(c.a, Optional.of(d)).step(c.query).orElseThrow().fingerprint();
        FunctionalSpec spec = FunctionalSpec.empty(c.a.id()).with(new FunctionalSpec.Decision(FunctionalSpec.Field.EFFECT, c.query, "EXTERNAL",
                FunctionalSpec.Actor.TEST_AGENT, FunctionalSpec.Basis.ANSWER, fp, ConsultaCase.CLOCK.instant()));
        PddGate.Result r2 = gate(c, Optional.of(d), spec);
        assertEquals(List.of("SPEC_POSTCONDITION_MISSING"), codes(r2), "efeito externo pede a confirmação do envio");
        spec = spec.with(new FunctionalSpec.Decision(FunctionalSpec.Field.POSTCONDITION, c.query, "NEXT_TARGET_VISIBLE", FunctionalSpec.Actor.TEST_AGENT,
                FunctionalSpec.Basis.ANSWER, fp, ConsultaCase.CLOCK.instant()));
        PddGate.Result r3 = gate(c, Optional.of(d), spec);
        assertTrue(r3.ready(), codes(r3).toString());
        assertFalse(r.record().currentFor(r3.record().automationRevision(), r3.record().processRevision(), r3.record().documentRevision(),
                r3.record().specRevision()), "outra especificação → o registro anterior não vale");
        assertEquals(spec, FunctionalSpec.parse(spec.toJson()), "especificação relida igual");
        // a etapa muda de alvo: a decisão deixa de valer e a pergunta volta
        String stale = "sha256:" + "0".repeat(64);
        FunctionalSpec old = FunctionalSpec.empty(c.a.id()).with(new FunctionalSpec.Decision(FunctionalSpec.Field.EFFECT, c.query, "NONE",
                FunctionalSpec.Actor.TEST_AGENT, FunctionalSpec.Basis.ANSWER, stale, ConsultaCase.CLOCK.instant()));
        assertEquals(List.of("SPEC_EFFECT_UNKNOWN"), codes(gate(c, Optional.of(d), old)));
    }

    @Test
    @DisplayName("sem etapa que leia o resultado → pedido de ajuda; aceitar a limitação é decisão registrada")
    void conclusao() {
        ConsultaCase c = ConsultaCase.withoutObservation();
        DocumentDraft d = c.reviewedDraft();
        assertEquals(List.of("SPEC_CONCLUSION_MISSING"), codes(gate(c, Optional.of(d), FunctionalSpec.empty(c.a.id()))));
        FunctionalSpec spec = FunctionalSpec.empty(c.a.id()).with(new FunctionalSpec.Decision(FunctionalSpec.Field.CONCLUSION, null, FunctionalSpec.NO_CONCLUSION,
                FunctionalSpec.Actor.DEVELOPER, FunctionalSpec.Basis.ANSWER, null, ConsultaCase.CLOCK.instant()));
        assertTrue(gate(c, Optional.of(d), spec).ready());
    }

    @Test
    @DisplayName("vocabulário: gravar vence consultar; rótulo desconhecido não vira palpite")
    void vocabulario() {
        assertEquals(EffectVocabulary.Effect.EXTERNAL, EffectVocabulary.fromLabel("Salvar e sair").orElseThrow());
        assertEquals(EffectVocabulary.Effect.NONE, EffectVocabulary.fromLabel("Consultar").orElseThrow());
        assertEquals(EffectVocabulary.Effect.NONE, EffectVocabulary.fromLabel("Log in").orElseThrow());
        assertTrue(EffectVocabulary.fromLabel("Processar").isEmpty());
        assertTrue(EffectVocabulary.fromLabel("").isEmpty());
    }

    @Test
    @DisplayName("fotografia do projeto: sem valores, sem localizadores, sem URL com consulta; origem e início sem query")
    void contexto() {
        ConsultaCase c = ConsultaCase.confirmed();
        ProjectContext ctx = ProjectContext.of(c.a, Optional.empty());
        assertEquals(List.of(ConsultaCase.ORIGIN), ctx.origins());
        assertEquals(ConsultaCase.ORIGIN + "/lab/studio014/portal.html", ctx.startUrl());
        ProjectContext.Step pw = ctx.step(c.password).orElseThrow();
        assertTrue(pw.sensitive());
        assertEquals(ProjectContext.ValueKind.NONE, pw.value());
        String all = ctx.toString();
        assertFalse(all.contains("#senha") || all.contains("#usuario"), "localizadores não entram");
        assertFalse(all.contains("tela=consulta"), "consulta da URL não entra");
    }
}
