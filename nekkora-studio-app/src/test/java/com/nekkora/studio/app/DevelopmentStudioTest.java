package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.ai.quality.AssistanceRequest;
import com.nekkora.studio.ai.quality.QualityIssue;
import com.nekkora.studio.ai.spec.FunctionalSpec;
import com.nekkora.studio.app.recorder.RecorderPrompts;
import com.nekkora.studio.app.recorder.RecorderWindowHost;
import com.nekkora.studio.app.workspace.ProjectService;
import com.nekkora.studio.bridge.BrowserLink;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepContext;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.privacy.CapturePrivacy;
import com.nekkora.studio.domain.target.DecisionActor;
import com.nekkora.studio.domain.target.TargetDecision;
import com.nekkora.studio.pdd.DocText;
import com.nekkora.studio.pdd.DocumentDraft;
import com.nekkora.studio.pdd.DraftBuilder;
import com.nekkora.studio.pdd.DraftEdits;
import com.nekkora.studio.pdd.PddStore;
import com.nekkora.studio.pdd.ProcessProjection;
import com.nekkora.studio.ui.develop.DevelopmentPanel;
import com.nekkora.tokens.NekkoraThemes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * STUDIO-014 — "Desenvolver automação" e "Validar automação" pelo Studio montado como na aplicação (sem janela),
 * com diretório de dados REAL (temporário) e a gravação do projeto real. Nenhum navegador, nenhuma IA.
 */
class DevelopmentStudioTest {
    @TempDir
    Path dir;
    final ScriptedPrompts prompts = new ScriptedPrompts();
    Studio studio;

    @AfterEach
    void tearDown() {
        if (studio != null) Edt.run(studio::dispose);
    }

    Studio start() {
        Edt.theme(NekkoraThemes.SAKURA_DARK);
        studio = Edt.call(() -> Studio.create(StudioFixture.CLOCK, prompts, () -> { }, BrowserLink.offline("DISABLED"), RecorderPrompts.none(),
                p -> RecorderWindowHost.headless(), StudioEnvironment.at(dir.resolve("dados"))));
        Edt.render(studio.view().component(), 1440, 900);
        return studio;
    }

    record Case(Automation a, Map<String, StepId> steps, Path project) {}

    /** Caso A sintético (como o Recorder grava): login → consulta → ler o resultado → sair; alvos confirmados; sem valores. */
    Case consulta(String queryLabel) throws Exception {
        Automation a = Edt.call(() -> studio.service().createAutomation("Consulta de registro", "Processo sintético"));
        Map<String, StepId> ids = new java.util.LinkedHashMap<>();
        Edt.run(() -> {
            LogicalSession s1 = studio.service().addSession(a.id(), "Acesso", null);
            LogicalSession s2 = studio.service().addSession(a.id(), "Consulta", null);
            // como a extensão entrega (nekkora-privacy/2): endereço só como MODELO, título do vocabulário de interface
            StepContext p1 = new StepContext("Navegador", "Entrar", "http://127.0.0.1:18765/{…}/{…}/portal.html");
            StepContext p2 = new StepContext("Navegador", "Consulta de registro", "http://127.0.0.1:18765/{…}/{…}/consulta.html");
            ids.put("usuario", add(a, s1, ActionType.FILL, p1, el("TEXT_INPUT", "input", "Usuário", "#usuario", false)));
            ids.put("senha", add(a, s1, ActionType.FILL, p1, el("TEXT_INPUT", "input", "Senha", "#senha", true)));
            ids.put("entrar", add(a, s1, ActionType.CLICK, p1, el("BUTTON", "button", "Entrar", "#entrar", false)));
            ids.put("codigo", add(a, s2, ActionType.FILL, p2, el("SEARCH_INPUT", "input", "Código do registro", "#registro-codigo", false)));
            ids.put("consultar", add(a, s2, ActionType.CLICK, p2, el("BUTTON", "button", queryLabel, "#consultar", false)));
            ids.put("resultado", add(a, s2, ActionType.OBSERVE, p2, el("GENERIC", "div", "Resultado da consulta", "#resultado", false)));
            ids.put("sair", add(a, s2, ActionType.CLICK, p2, el("BUTTON", "button", "Sair", "#sair", false)));
            for (StepId id : ids.values()) {
                studio.service().decideTarget(a.id(), id, TargetDecision.Kind.CONFIRMED, DecisionActor.TEST_AGENT,
                        new TargetDecision.Basis("TARGET_ID", "RESOLVED", List.of(), StudioFixture.CLOCK.instant(), ""));
            }
        });
        Path file = dir.resolve("consulta.nekkora");
        prompts.thenSaveAs(file);
        ProjectService.Outcome saved = await(Edt.call(() -> studio.projects().save(a.id())));
        assertEquals(ProjectService.Outcome.SAVED, saved, studio.state().lastNotice().map(n -> n.title() + " — " + n.message()).orElse(""));
        return new Case(a, ids, file);
    }

    private StepId add(Automation a, LogicalSession s, ActionType t, StepContext c, ElementReference e) {
        return studio.service().appendStep(a.id(), s.id(), StepDraft.builder().name(t.label()).actionType(t).context(c).element(e).build()).id();
    }

    static ElementReference el(String kind, String tag, String name, String css, boolean sensitive) {
        LocatorCandidate preferred = new LocatorCandidate(LocatorStrategy.ID, "", css, LocatorStability.OBSERVED_STABLE, 1, true);
        return ElementReference.builder().semanticKind(kind).tag(tag).accessibleName(name).sensitive(sensitive).privacyPolicy(CapturePrivacy.VERSION)
                .sourceContract("nekkora-pagemap/0.3.0").addLocator(preferred).preferLastLocator()
                .addAccessStep(AccessStep.of(AccessStep.Type.MAIN_DOCUMENT, "documento principal"))
                .correlation(CorrelationKeys.of(Map.of(CorrelationKeys.Key.TARGET_ID, "tgt-" + css.substring(1)))).build();
    }

    /** Rascunho do PDD da revisão ATUAL com os textos revisados (o que a pessoa faria no editor do PDD). */
    void reviewedPdd(Automation a) throws Exception {
        DocumentDraft d = Edt.call(() -> {
            DocumentDraft x = DraftBuilder.build(ProcessProjection.of(a));
            for (DocumentDraft.Section s : x.sections()) {
                if (s.title().state() == DocText.State.NEEDS_REVIEW) x = DraftEdits.reviseSectionTitle(x, s.number(), "Etapa do processo", "TEST_AGENT");
            }
            for (DocumentDraft.Block b : x.blocks()) {
                if (b.title().state() == DocText.State.NEEDS_REVIEW) x = DraftEdits.reviseBlockTitle(x, b.blockId(), "Bloco do processo", "TEST_AGENT");
                for (DocumentDraft.Instruction i : b.instructions()) {
                    if (i.text().state() == DocText.State.NEEDS_REVIEW) x = DraftEdits.reviseInstruction(x, b.blockId(), i.ordinal(), "Informar o campo", "TEST_AGENT");
                }
            }
            return x;
        });
        new PddStore(dir.resolve("dados")).saveDraft(d);
    }

    static <T> T await(CompletableFuture<T> f) throws Exception {
        T t = f.get(15, TimeUnit.SECONDS);
        Edt.flush();
        return t;
    }

    static final String START = "http://127.0.0.1:18765/lab/studio014/portal.html";

    /** Responde à pergunta do endereço inicial (a gravação guarda só o modelo do endereço). */
    void answerStart(String url) throws Exception {
        QualityIssue q = model().pending().stream().filter(i -> i.code().equals("SPEC_START_URL_UNKNOWN")).findFirst().orElseThrow();
        Edt.run(() -> studio.development().answerText(q, url));
        idle();
    }

    void develop() throws Exception {
        Edt.run(() -> studio.view().actions().get(com.nekkora.studio.app.ui.StudioActions.DEVELOP).actionPerformed(new java.awt.event.ActionEvent(this, 0, "g")));
        idle();
    }

    void idle() throws Exception {
        for (int i = 0; i < 3; i++) {
            studio.development().awaitIdle();
            Edt.flush();
        }
    }

    DevelopmentPanel.Model model() {
        return Edt.call(() -> studio.development().panel().model());
    }

    @Test
    @DisplayName("S014 — sem PDD: um clique analisa, bloqueia em Verificando PDD com a pendência e NÃO grava nenhum arquivo nem muda o modelo")
    void semPdd() throws Exception {
        start();
        Case c = consulta("Consultar");
        long rev = c.a().revision();
        develop();
        DevelopmentPanel.Model m = model();
        assertEquals("PDD não está pronto: nenhum código foi gerado", m.statusTitle());
        assertTrue(m.pending().stream().anyMatch(i -> i.code().equals("PDD_MISSING")), m.pending().toString());
        assertTrue(m.phases().stream().anyMatch(p -> p.label().equals("Verificando PDD") && p.state() == DevelopmentPanel.PhaseState.BLOCKED));
        assertFalse(Files.exists(dir.resolve("consulta-python")), "nenhum arquivo gerado");
        assertEquals(rev, c.a().revision(), "nada muda no modelo antes do PDD pronto");
        assertTrue(m.answers().stream().anyMatch(x -> x.question().equals("O que será criado?") && x.answer().startsWith("Depende do PDD")));
    }

    @Test
    @DisplayName("S014 — um clique com PDD pronto: variáveis derivadas (desfazíveis), Python pelo gerador oficial em <projeto>-python/, sem questionário")
    void umClique() throws Exception {
        start();
        Case c = consulta("Consultar");
        reviewedPdd(c.a());
        develop();
        // a única pergunta: o endereço inicial (mascarado pela privacidade da gravação); resposta fora do modelo é recusada
        assertEquals(List.of("SPEC_START_URL_UNKNOWN"), model().pending().stream().filter(i -> i.kind() == QualityIssue.Kind.HELP_REQUIRED)
                .map(QualityIssue::code).toList());
        answerStart("http://127.0.0.1:18765/outra/pagina.html?usuario=x");
        assertTrue(model().pending().stream().anyMatch(i -> i.code().equals("SPEC_START_URL_UNKNOWN")), "endereço fora do modelo: nada guardado");
        answerStart(START);
        DevelopmentPanel.Model m = model();
        Path out = dir.resolve("consulta-python");
        assertTrue(Files.isRegularFile(out.resolve("manifest.json")), m.statusTitle() + " " + m.pending());
        assertTrue(m.results().stream().anyMatch(r -> r.equals("✓ CODE_GENERATED")), m.results().toString());
        assertTrue(m.results().stream().anyMatch(r -> r.contains("Registrar variáveis derivadas")));
        assertTrue(m.results().stream().anyMatch(r -> r.startsWith("— Execução real: não executada")));
        assertTrue(m.uses().stream().anyMatch(u -> u.id().equals("actions.read_text") && u.where().contains("nekkora_rpa/libs/actions.py")));
        assertTrue(m.variables().stream().anyMatch(v -> v.name().equals("codigo_do_registro") && v.origin().equals("entrada da execução")));
        assertTrue(m.variables().stream().anyMatch(v -> v.name().equals("credencial.senha") && v.origin().startsWith("credencial")));
        String manifest = Files.readString(out.resolve("manifest.json"));
        assertTrue(manifest.contains("\"execution\": \"single\"") && manifest.contains("\"gate\": \"PDD_READY\""));
        assertTrue(Files.readString(out.resolve("processo.py")).contains("URL_INICIAL = \"" + START + "\""));
        assertTrue(Files.isRegularFile(dir.resolve("dados/desenvolvimento").resolve(c.a().id().value().toString()).resolve("codegen.json")),
                "configuração derivada guardada para auditoria, fora do projeto");
        assertFalse(Files.exists(dir.resolve("consulta.codegen.json")), "nenhum *.codegen.json pedido ou criado ao lado do projeto");
        // as variáveis derivadas são UMA operação desfazível
        assertTrue(Edt.call(() -> studio.service().canUndo(c.a().id())));
        assertEquals("Registrar variáveis derivadas", Edt.call(() -> studio.service().history().undoLabel(c.a().id()).orElseThrow()));
        Edt.run(() -> studio.service().undo(c.a().id()));
        assertTrue(c.a().variables().isEmpty());
        assertTrue(c.a().step(c.steps().get("senha")).orElseThrow().parameters().value().isEmpty(), "desfazer volta a etapa sem origem de valor");
        // de novo: idempotente; o código no disco é o mesmo (UNCHANGED)
        develop();
        assertTrue(model().results().stream().anyMatch(r -> r.contains("igual  paginas.py")), model().results().toString());
    }

    @Test
    @DisplayName("S014 — pedido de ajuda: rótulo desconhecido bloqueia; a resposta vai para a especificação com o ator e a análise retoma até gerar")
    void ajuda() throws Exception {
        start();
        Case c = consulta(""); // rótulo fora do vocabulário de interface: a extensão não o guarda → efeito desconhecido
        reviewedPdd(c.a());
        develop();
        answerStart(START);
        QualityIssue q = model().pending().stream().filter(i -> i.code().equals("SPEC_EFFECT_UNKNOWN")).findFirst().orElseThrow();
        assertEquals(c.steps().get("consultar"), q.step());
        AssistanceRequest.Option external = q.assistance().orElseThrow().options().stream().filter(o -> "EXTERNAL".equals(o.value())).findFirst().orElseThrow();
        Edt.run(() -> studio.development().resolve(q, external));
        idle();
        QualityIssue post = model().pending().stream().filter(i -> i.code().equals("SPEC_POSTCONDITION_MISSING")).findFirst().orElseThrow();
        AssistanceRequest.Option next = post.assistance().orElseThrow().options().stream().filter(o -> "NEXT_TARGET_VISIBLE".equals(o.value())).findFirst().orElseThrow();
        Edt.run(() -> studio.development().resolve(post, next));
        idle();
        assertTrue(Files.isRegularFile(dir.resolve("consulta-python/paginas.py")), model().pending().toString());
        String pages = Files.readString(dir.resolve("consulta-python/paginas.py"));
        assertTrue(pages.contains("postcondition=lambda: a.exists(\"processo.consulta.resultado_da_consulta\")"), "envio confirmado pela etapa seguinte");
        String send = pages.substring(pages.indexOf("step_id=\"" + c.steps().get("consultar").value() + "\""));
        send = send.substring(0, send.indexOf("# etapa"));
        assertFalse(send.contains("idempotent=True"), "envio com efeito externo sai UMA vez (não idempotente): " + send);
        FunctionalSpec spec = studio.development().store().spec(c.a().id());
        assertEquals(FunctionalSpec.Actor.DEVELOPER, spec.decision(FunctionalSpec.Field.EFFECT, c.steps().get("consultar")).orElseThrow().actor());
    }

    @Test
    @DisplayName("S014 — Validar automação: camadas V0–V6 na aba Validação, sem rede; V4/V5/V6 'não verificado' sem código; Resolver abre a pendência")
    void validarCamadas() throws Exception {
        start();
        Case c = consulta("Consultar");
        Edt.run(() -> studio.view().actions().get(com.nekkora.studio.app.ui.StudioActions.VALIDATE).actionPerformed(new java.awt.event.ActionEvent(this, 0, "v")));
        idle();
        String layers = Edt.call(() -> studio.view().validationPanel().layersText().getText());
        assertTrue(layers.contains("V1 ×"), layers);
        assertTrue(layers.contains("V4 —") && layers.contains("V6 —"), layers);
        String full = Edt.call(() -> studio.view().validationPanel().layersText().getAccessibleContext().getAccessibleDescription());
        assertTrue(full.contains("V6 Execução real: não executada pelo Studio"), full);
        assertFalse(full.toLowerCase().contains("aprovad") || full.toLowerCase().contains("garantid") || full.toLowerCase().contains("produção"), full);
        List<String> items = Edt.call(() -> {
            List<String> out = new ArrayList<>();
            var l = studio.view().validationPanel().findingList();
            for (int i = 0; i < l.getModel().getSize(); i++) if (!l.getModel().getElementAt(i).structural()) out.add(l.getModel().getElementAt(i).issue().code());
            return out;
        });
        assertTrue(items.contains("PDD_MISSING"), items.toString());
        // com o PDD pronto e o código gerado, V1–V3 passam e V4/V5 dizem "não configurado" (sem Python de verificação nos testes)
        reviewedPdd(c.a());
        develop();
        answerStart(START);
        Edt.run(() -> studio.view().actions().get(com.nekkora.studio.app.ui.StudioActions.VALIDATE).actionPerformed(new java.awt.event.ActionEvent(this, 0, "v")));
        idle();
        String after = Edt.call(() -> studio.view().validationPanel().layersText().getAccessibleContext().getAccessibleDescription());
        assertTrue(after.contains("V1 PDD: PDD_READY"), after);
        assertTrue(after.contains("V6 Execução real: não executada"), after);
        assertFalse(after.contains("V4 Código Python: código ainda não gerado"), after);
        // o modelo mudou depois da geração → código desatualizado com a correção segura "gerar de novo"
        Edt.run(() -> studio.service().renameSession(c.a().id(), c.a().sessions().get(1).id(), "Consulta de registros"));
        Edt.run(() -> studio.controller().validateActive());
        idle();
        String stale = Edt.call(() -> studio.view().validationPanel().layersText().getAccessibleContext().getAccessibleDescription());
        assertTrue(stale.contains("V1 PDD: PDD_BLOCKED"), "renomear a sessão mudou o processo: o PDD precisa ser atualizado — " + stale);
    }

    @Test
    @DisplayName("S014 — regressão tabular: o projeto da E2E-001 com a configuração declarada gera EXATAMENTE os mesmos arquivos do gerador antigo")
    void regressaoTabular() throws Exception {
        start();
        Path src = Path.of("..", "docs", "e2e-001", "evidence", "projeto").toAbsolutePath().normalize();
        Path project = dir.resolve("rpa-challenge.nekkora");
        Files.copy(src.resolve("rpa-challenge.nekkora"), project);
        // a configuração declarada da RÉPLICA local (lab) — a mesma da homologação da E2E-001
        Files.copy(Path.of("..", "docs", "e2e-001", "evidence", "g6-laboratorio", "replica.codegen.json").toAbsolutePath().normalize(),
                dir.resolve("rpa-challenge.codegen.json"));
        assertEquals(ProjectService.Outcome.OPENED, await(Edt.call(() -> studio.projects().open(project))));
        Automation a = Edt.call(() -> studio.state().activeAutomation().orElseThrow());
        reviewedPdd(a);
        develop();
        // perguntas do caso real: "Start" fora do vocabulário e a confirmação do "Submit" (efeito externo) — respondidas como no laboratório
        for (int round = 0; round < 4; round++) {
            List<QualityIssue> pend = model().pending().stream().filter(i -> i.kind() == QualityIssue.Kind.HELP_REQUIRED).toList();
            if (pend.isEmpty()) break;
            QualityIssue q = pend.get(0);
            String value = switch (q.code()) {
                case "SPEC_EFFECT_UNKNOWN" -> "NONE";
                case "SPEC_POSTCONDITION_MISSING" -> "FIRST_FIELD_REPLACED";
                default -> null;
            };
            assertTrue(value != null, "pergunta inesperada: " + q.code() + " " + q.title());
            AssistanceRequest.Option o = q.assistance().orElseThrow().options().stream().filter(x -> value.equals(x.value())).findFirst().orElseThrow();
            Edt.run(() -> studio.development().resolve(q, o));
            idle();
        }
        Path out = dir.resolve("rpa-challenge-python");
        assertTrue(Files.isRegularFile(out.resolve("manifest.json")), model().statusTitle() + " " + model().pending());
        assertTrue(model().answers().stream().anyMatch(x -> x.answer().startsWith("Processo em lote")));
        // mesmo gerador, mesma configuração → mesmos bytes que o gerador antigo (Automação → Avançado)
        Path legacy = dir.resolve("legado");
        String text = Edt.call(() -> com.nekkora.studio.app.codegen.CodegenPresenter.generate(a, dir.resolve("rpa-challenge.codegen.json"), legacy));
        assertTrue(text.contains("GERADO"), text);
        try (Stream<Path> files = Files.walk(legacy)) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                Path rel = legacy.relativize(f);
                assertEquals(Files.readString(f), Files.readString(out.resolve(rel)), "arquivo diferente: " + rel);
            }
        }
    }
}
