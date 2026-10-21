package com.nekkora.studio.ai.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.ai.ConsultaCase;
import com.nekkora.studio.ai.FichaCase;
import com.nekkora.studio.ai.PythonForTests;
import com.nekkora.studio.ai.capability.FrameworkCapabilityCatalog;
import com.nekkora.studio.ai.context.ProjectContext;
import com.nekkora.studio.ai.gate.PddGate;
import com.nekkora.studio.ai.pycheck.PythonChecks;
import com.nekkora.studio.ai.quality.QualityIssue;
import com.nekkora.studio.ai.spec.FunctionalSpec;
import com.nekkora.studio.ai.spec.SpecResolver;
import com.nekkora.studio.codegen.OutputWriter;
import com.nekkora.studio.codegen.PythonGenerator;
import com.nekkora.studio.codegen.SingleConfig;
import com.nekkora.studio.codegen.SingleGenerator;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.pdd.DocumentDraft;
import com.nekkora.studio.pdd.ProcessProjection;
import com.nekkora.studio.validation.AutomationValidator;
import com.nekkora.studio.validation.RuleSet;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * NEKKORA-E2E-002 — do modelo gravado (âncoras de linha, Enter, endereço conferido, fontes alternativas) à planilha:
 * especificação "lista → planilha", plano 1.2.0, código pelo gerador oficial, verificação com ferramentas reais e, com o
 * laboratório genérico do framework, a EXECUÇÃO do código gerado conferida REABRINDO o .xlsx.
 */
public class ListFlowTest {
    @TempDir
    Path tmp;

    public record Flow(FichaCase c, DevelopmentPlan plan, PythonGenerator.Result result, List<QualityIssue> specIssues) {}

    public static FunctionalSpec spec(FichaCase c, ProjectContext ctx) {
        FunctionalSpec s = FunctionalSpec.empty(c.a.id());
        var now = ConsultaCase.CLOCK.instant();
        s = s.with(new FunctionalSpec.Decision(FunctionalSpec.Field.DATA_FLOW, null, "LIST_XLSX", FunctionalSpec.Actor.TEST_AGENT, FunctionalSpec.Basis.ANSWER, null, now));
        s = s.with(new FunctionalSpec.Decision(FunctionalSpec.Field.EFFECT, c.enter, "NONE", FunctionalSpec.Actor.TEST_AGENT, FunctionalSpec.Basis.ANSWER, fp(ctx, c.enter), now));
        s = s.with(new FunctionalSpec.Decision(FunctionalSpec.Field.OUTPUT_COLUMN, c.title, FunctionalSpec.FALLBACK + c.fullName.value(), FunctionalSpec.Actor.TEST_AGENT,
                FunctionalSpec.Basis.ANSWER, fp(ctx, c.title), now));
        s = s.with(new FunctionalSpec.Decision(FunctionalSpec.Field.OUTPUT_COLUMN, c.fields, FunctionalSpec.FALLBACK + c.occupation.value(), FunctionalSpec.Actor.TEST_AGENT,
                FunctionalSpec.Basis.ANSWER, fp(ctx, c.fields), now));
        return s;
    }

    static String fp(ProjectContext ctx, StepId id) {
        return ctx.step(id).orElseThrow().fingerprint();
    }

    public static Flow develop(FichaCase c) {
        DocumentDraft d = c.reviewedDraft();
        ProjectContext ctx = ProjectContext.of(c.a, Optional.of(d));
        VariableDeriver.apply(c.a, VariableDeriver.derive(c.a, ctx));
        d = c.reviewedDraft(); // a revisão do processo mudou (variáveis registradas): o PDD é refeito para a revisão atual
        ctx = ProjectContext.of(c.a, Optional.of(d));
        FunctionalSpec spec = spec(c, ctx);
        PddGate.Result gate = PddGate.evaluate(ctx, Optional.of(d), spec, ProcessProjection.of(c.a), ConsultaCase.CLOCK.instant());
        assertTrue(gate.ready(), gate.issues().toString());
        DevelopmentPlan plan = DevelopmentPlanner.plan(c.a, ctx, gate, FrameworkCapabilityCatalog.bundled(), Optional.empty(), Optional.of(d));
        assertTrue(plan.ready(), plan.issues().toString());
        PythonGenerator.Result r = SingleGenerator.generate(c.a, new AutomationValidator(RuleSet.V1_1).validate(c.a), plan.singleConfig().orElseThrow(), plan.names());
        return new Flow(c, plan, r, gate.issues());
    }

    @Test
    @DisplayName("colunas derivadas dos rótulos das linhas; título e Campo(s) como fontes alternativas; endereço como chave; data como data")
    void plano() {
        Flow f = develop(FichaCase.confirmed());
        SingleConfig cfg = f.plan().singleConfig().orElseThrow();
        assertTrue(cfg.list() && cfg.spreadsheet() && cfg.modern(), cfg.toJson());
        assertEquals(DevelopmentPlan.ExecutionModel.LIST, f.plan().model(), "lista → planilha não é rotulada como execução única");
        assertTrue(cfg.toJson().contains("\"version\": \"1.2.0\""));
        assertEquals(List.of("URL", "Nome completo", "Nascimento", "Morte", "Nacionalidade", "Ocupação"),
                cfg.output().columns().stream().map(SingleConfig.Column::header).toList());
        assertEquals(List.of("url", "text", "iso_date", "iso_date", "text", "text"), cfg.output().columns().stream().map(SingleConfig.Column::kind).toList());
        assertEquals("url", cfg.output().keyField());
        SingleConfig.Column nome = cfg.output().columns().get(1);
        assertEquals(f.c().title.value().toString(), nome.fallbackStep());
        assertEquals(f.c().fields.value().toString(), cfg.output().columns().get(5).fallbackStep());
        assertEquals("address", cfg.reads().get(f.c().address.value().toString()).kind());
        assertEquals("endereco_esperado", cfg.reads().get(f.c().address.value().toString()).expected());
        assertEquals("date", cfg.reads().get(f.c().born.value().toString()).kind());
        assertTrue(cfg.reads().get(f.c().fullName.value().toString()).optional() && cfg.reads().get(f.c().title.value().toString()).optional());
        assertFalse(cfg.reads().get(f.c().born.value().toString()).optional(), "leitura sem alternativa é obrigatória");
        assertTrue(cfg.keys().get(f.c().enter.value().toString()).navigates());
        assertTrue(f.result().ok(), f.result().blockers().toString());
        Map<String, String> files = f.result().files();
        String code = files.get("automation/pages.py") + files.get("automation/process.py") + files.get("main.py");
        assertTrue(code.contains("self.actions.press(") && code.contains("self.actions.current_address() != address_before"), "Enter confirmado pela troca de endereço");
        assertTrue(code.contains("parse_long_date(") && code.contains("join_items(") && code.contains("XlsxResultSink("));
        // os códigos de recusa são contrato público: continuam os mesmos (só o nome interno do campo é inglês)
        assertTrue(code.contains("raise BusinessRuleRejected(\"ENDERECO_DIFERENTE_DO_ESPERADO\", step_id=\"profile.read_page_address\")")
                && code.contains("raise BusinessRuleRejected(f\"DADO_OBRIGATORIO_VAZIO:{field}\")") && code.contains("\"DATA_INCOMPLETA_OU_AMBIGUA\""), code);
        assertFalse(code.contains(":nth-") || code.contains(".nth(") || code.contains("first()") || code.contains("Ana ") || code.contains("wikipedia"),
                "sem posição, sem dado, sem site");
        // CODEGEN-QUALITY-001: nomes do PDD, step_id legível, nenhum UUID nem comentário por etapa no código
        assertTrue(code.contains("class SearchPage(PageBase)") && code.contains("class ProfilePage(PageBase)") && code.contains("def search_person("), code);
        assertFalse(code.matches("(?s).*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-.*") || code.contains("# etapa ") || code.contains(" a = self.act"), code);
        assertTrue(files.get("selectors.json").contains("tr[th[normalize-space(.)=\\\"Nascimento\\\"]]"), files.get("selectors.json"));
        assertTrue(files.get("requirements.txt").contains("nekkora-rpa[excel]==0.3.0"));
        assertTrue(files.get("manifest.json").contains("\"version\": \"1.4.0\"") && files.get("manifest.json").contains("\"Nome completo\"")
                && files.get("manifest.json").contains("\"framework\": \"nekkora-rpa[excel]==0.3.0\""));
        // mesma entrada → mesmos bytes
        PythonGenerator.Result again = SingleGenerator.generate(f.c().a, new AutomationValidator(RuleSet.V1_1).validate(f.c().a), cfg, f.plan().names());
        assertEquals(files, again.files());
    }

    @Test
    @DisplayName("sem decisão de fluxo: execução única de sempre (pergunta não bloqueante); leitura sem rótulo pede a coluna")
    void perguntas() {
        FichaCase c = FichaCase.confirmed();
        DocumentDraft d = c.reviewedDraft();
        ProjectContext ctx = ProjectContext.of(c.a, Optional.of(d));
        SpecResolver.ResolvedSpec r = SpecResolver.resolve(ctx, FunctionalSpec.empty(c.a.id()));
        assertEquals("SINGLE_JSON", r.dataFlow().value());
        QualityIssue q = r.issues().stream().filter(i -> i.code().equals("SPEC_DATA_FLOW_OPTIONAL")).findFirst().orElseThrow();
        assertFalse(q.kind().blocking());
        FunctionalSpec list = FunctionalSpec.empty(c.a.id()).with(new FunctionalSpec.Decision(FunctionalSpec.Field.DATA_FLOW, null, "LIST_XLSX",
                FunctionalSpec.Actor.TEST_AGENT, FunctionalSpec.Basis.ANSWER, null, ConsultaCase.CLOCK.instant()));
        SpecResolver.ResolvedSpec r2 = SpecResolver.resolve(ctx, list);
        List<QualityIssue> asks = r2.issues().stream().filter(i -> i.code().equals("SPEC_OUTPUT_COLUMN_UNKNOWN")).toList();
        assertEquals(List.of(c.title), asks.stream().map(QualityIssue::step).toList(), "só o título não tem rótulo de linha");
        assertTrue(asks.get(0).kind().blocking());
        assertTrue(asks.get(0).help().options().stream().anyMatch(o -> (FunctionalSpec.FALLBACK + c.fullName.value()).equals(o.value())));
        assertEquals("COLUMN:Campo(s)", r2.columns().get(c.fields).value(), "rótulo com marcador de plural vale (D-E2E2-12)");
        // cabeçalho com cara de fórmula ou dado fora do vocabulário é recusado
        assertFalse(FunctionalSpec.headerOk("=HIPERLINK"));
        assertFalse(FunctionalSpec.headerOk("Joaquim"));
        assertTrue(FunctionalSpec.headerOk("Nome completo"));
    }

    @Test
    @DisplayName("[Python real] o projeto 1.2.0 passa em compilação, importação, ruff, ruff format, mypy estrito e contrato")
    void pythonReal() throws Exception {
        Path py = PythonForTests.framework();
        Assumptions.assumeTrue(py != null, "sem o interpretador do framework neste ambiente");
        Flow f = develop(FichaCase.confirmed());
        Path out = tmp.resolve("fichas-python");
        OutputWriter.write(out, f.result().files());
        PythonChecks.Environment env = PythonChecks.resolve(Optional.of(py), out).orElseThrow();
        assertEquals("0.3.0", env.framework());
        for (PythonChecks.Check ch : PythonChecks.run(env, out, () -> false)) {
            assertEquals(PythonChecks.Status.PASS, ch.status(), ch.id() + " " + ch.codes() + " " + ch.reason() + "\n" + SingleFlowTest.dump(out));
        }
    }

    @Test
    @DisplayName("[Python real + laboratório] o código GERADO lê três fichas fictícias e grava a planilha conferida reabrindo o arquivo")
    void execucaoReal() throws Exception {
        Path py = PythonForTests.framework();
        Assumptions.assumeTrue(py != null, "sem o interpretador do framework neste ambiente");
        Flow f = develop(FichaCase.confirmed());
        Path out = tmp.resolve("fichas-python");
        OutputWriter.write(out, f.result().files());
        SingleConfig cfg = f.plan().singleConfig().orElseThrow();
        String o = FichaCase.ORIGIN;
        Path lista = tmp.resolve("lista.csv");
        Files.writeString(lista, String.join(",", cfg.inputs()) + "\n" + "Ana Exemplo," + o + "/leitura/ficha-ana.html\n"
                + "Fabio Ficticio," + o + "/leitura/ficha-fabio.html\n" + "Gina Inventada," + o + "/leitura/ficha-gina.html\n", StandardCharsets.UTF_8);
        assertEquals(List.of("termo_de_busca", "endereco_esperado"), cfg.inputs());
        Process lab = new ProcessBuilder(py.toString(), "-c", "import sys,time; sys.path.insert(0, sys.argv[1]); from lab.server import Lab; "
                + "Lab(port=18766).start(); print('pronto', flush=True); time.sleep(300)", PythonForTests.frameworkRepo().toString())
                .redirectErrorStream(true).start();
        try {
            String ready = new String(lab.getInputStream().readNBytes(6), StandardCharsets.UTF_8);
            assertEquals("pronto", ready);
            Path xlsx = tmp.resolve("saida").resolve("fichas.xlsx");
            Files.createDirectories(xlsx.getParent());
            String run = runPython(py, out, List.of("main.py", "--input", lista.toString(), "--spreadsheet", xlsx.toString(), "--output", tmp.resolve("exec").toString(),
                    "--channel", "chromium", "--headless"));
            assertTrue(run.contains("\"status\": \"COMPLETED\"") && run.contains("\"COMMITTED\""), run);
            String rows = runPython(py, out, List.of("-c", "import json,sys,openpyxl; ws=openpyxl.load_workbook(sys.argv[1])['Dados']; "
                    + "print(json.dumps([[c.value for c in r] for r in ws.iter_rows()], ensure_ascii=False))", xlsx.toString()));
            assertEquals("[[\"URL\", \"Nome completo\", \"Nascimento\", \"Morte\", \"Nacionalidade\", \"Ocupação\"], "
                    + "[\"" + o + "/leitura/ficha-ana.html\", \"Ana Maria Exemplo da Silva\", \"1839-06-21\", \"1908-09-29\", \"brasileira\", \"escritora; jornalista; tradutora\"], "
                    + "[\"" + o + "/leitura/ficha-fabio.html\", \"Fábio Fictício\", \"1901-04-03\", \"1980-05-04\", \"alemão; suíço\", \"Físico; Matemático\"], "
                    + "[\"" + o + "/leitura/ficha-gina.html\", \"Georgina Inventada Silva\", \"1867-11-07\", \"1934-07-04\", \"polonesa, francesa\", \"física, química\"]]",
                    rows.strip());
            // artigo errado (a busca leva a outra ficha): conferência recusa e a planilha NÃO é gravada (tudo ou nada)
            Path errada = tmp.resolve("errada.csv");
            Files.writeString(errada, String.join(",", cfg.inputs()) + "\nAna Exemplo," + o + "/leitura/ficha-gina.html\n", StandardCharsets.UTF_8);
            Path x2 = tmp.resolve("saida").resolve("errada.xlsx");
            // compatibilidade: os nomes anteriores das opções continuam aceitos pelo main.py novo
            String run2 = runPython(py, out, List.of("main.py", "--entrada", errada.toString(), "--planilha", x2.toString(), "--saida", tmp.resolve("exec2").toString(),
                    "--channel", "chromium", "--headless"));
            assertTrue(run2.contains("\"COMPLETED_WITH_FAILURES\"") && run2.contains("\"WITHHELD\"") && run2.contains("BUSINESS_REJECTED"), run2);
            assertFalse(Files.exists(x2));
        } finally {
            lab.descendants().forEach(ProcessHandle::destroyForcibly);
            lab.destroyForcibly();
        }
    }

    static String runPython(Path py, Path cwd, List<String> args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(py.toString());
        cmd.add("-B");
        cmd.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(cwd.toFile()).redirectErrorStream(true);
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        Process p = pb.start();
        String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(180, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IllegalStateException("tempo esgotado");
        }
        return text;
    }
}
