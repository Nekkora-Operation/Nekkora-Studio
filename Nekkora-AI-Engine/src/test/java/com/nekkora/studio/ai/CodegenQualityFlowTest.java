package com.nekkora.studio.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.ai.capability.FrameworkCapabilityCatalog;
import com.nekkora.studio.ai.context.ProjectContext;
import com.nekkora.studio.ai.gate.PddGate;
import com.nekkora.studio.ai.plan.DevelopmentPlan;
import com.nekkora.studio.ai.plan.DevelopmentPlanner;
import com.nekkora.studio.ai.plan.ListFlowTest;
import com.nekkora.studio.ai.plan.VariableDeriver;
import com.nekkora.studio.ai.pycheck.PythonChecks;
import com.nekkora.studio.ai.quality.GeneratedCodeQuality;
import com.nekkora.studio.ai.quality.QualityReport;
import com.nekkora.studio.ai.store.TraceRegistry;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.codegen.CodeNames;
import com.nekkora.studio.codegen.CodePlan;
import com.nekkora.studio.codegen.OutputWriter;
import com.nekkora.studio.codegen.PythonGenerator;
import com.nekkora.studio.codegen.SingleGenerator;
import com.nekkora.studio.pdd.DocumentDraft;
import com.nekkora.studio.pdd.DraftEdits;
import com.nekkora.studio.pdd.ProcessProjection;
import com.nekkora.studio.validation.AutomationValidator;
import com.nekkora.studio.validation.RuleSet;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * NEKKORA-CODEGEN-QUALITY-001 — Q01/Q02/Q03 no gerador oficial, pelo caso sintético das fichas (lista → planilha):
 * nomes do PDD, step_id legível com mapa bijetivo, estabilidade do traceId (PDD renomeado, ordem trocada, etapa
 * removida), equivalência de comportamento com o gerador anterior e a camada VQ (manifesto + AST, com controles negativos).
 */
class CodegenQualityFlowTest {
    @TempDir
    Path tmp;

    static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    static final Pattern STEP_ID = Pattern.compile("step_id=\"([^\"]+)\"");

    @Test
    @DisplayName("Q01/Q02: Pages e métodos com nomes do PDD, sem UUID nem comentário por etapa; step_id ↔ etapa bijetivo e completo")
    void semantico() {
        ListFlowTest.Flow f = ListFlowTest.develop(FichaCase.confirmed());
        Map<String, String> files = f.result().files();
        String pages = files.get("paginas.py");
        String process = files.get("processo.py");
        String code = pages + process + files.get("main.py");
        // rodada 2 (CQ-01): a tela onde os dados são lidos leva o nome do DONO dos dados dito no PDD ("os dados da ficha")
        assertTrue(pages.contains("class PaginaPesquisa(PaginaBase):\n    \"\"\"Tela da seção “Pesquisa da pessoa” do PDD: abre a busca do acervo; "
                + "pesquisa a pessoa pelo nome.") && pages.contains("class PaginaFicha(PaginaBase):"), pages);
        assertTrue(pages.contains("def pesquisar_pessoa(self, termo_de_busca: str) -> None:") && pages.contains("def abrir_busca(self) -> None:"), pages);
        assertTrue(pages.contains("def ler_dados_da_ficha(") && pages.contains("class DadosDaFicha:") && pages.contains("    nome_completo: str | None\n")
                && pages.contains("    nascimento: str\n"), pages);
        assertTrue(process.contains("class FichasDoAcervoSintetica(RpaProcess):") && process.contains("def linha_da_planilha("), process);
        assertTrue(process.contains("\"nome_completo\": dados_da_ficha.nome_completo or dados_da_ficha.titulo")
                && process.contains("\"ocupacao\": dados_da_ficha.ocupacao or dados_da_ficha.campos"), process);
        // CQ-02: a API do framework aparece com nomes legíveis; act/k só dentro da PaginaBase
        assertTrue(pages.contains("class PaginaBase(BasePage):") && pages.contains("def acoes(self) -> Actions:") && pages.contains("def elemento(self, nome: str)"));
        assertEquals(1, count(pages, "self.act"), "self.act só na PaginaBase");
        assertEquals(1, count(pages, "self.k("), "self.k só na PaginaBase");
        // CQ-03: a explicação da espera está na docstring, não num comentário solto
        assertFalse(pages.contains("        # a página está pronta"), pages);
        assertTrue(pages.replaceAll("\\s+", " ").contains("Antes, espera “Nascimento” (leitura obrigatória)"), pages);
        assertFalse(UUID.matcher(pages + process).find(), "nenhum UUID no corpo das Pages/processo");
        assertFalse(code.contains("# etapa") || code.contains(" a = self.act") || code.contains("etapas_sessao") || code.contains("\"fill\"") || code.contains("campo_s"));
        Map<String, Object> m = Json.parseObject(files.get("manifest.json"));
        Map<String, String> traceToStep = traces(m);
        Set<String> steps = new LinkedHashSet<>();
        for (Object o : (List<?>) m.get("steps")) steps.add(String.valueOf(((Map<?, ?>) o).get("stepId")));
        assertEquals(steps, new LinkedHashSet<>(traceToStep.values()), "toda etapa tem um traceId");
        assertEquals(traceToStep.size(), new LinkedHashSet<>(traceToStep.values()).size(), "bijetivo");
        Matcher x = STEP_ID.matcher(pages + process);
        while (x.find()) assertTrue(traceToStep.containsKey(x.group(1)) || x.group(1).equals(CodePlan.START_TRACE), "step_id sem mapa: " + x.group(1));
        assertTrue(traceToStep.containsKey("pesquisa.preencher_termo_de_busca") && traceToStep.containsKey("ficha.ler_nome_completo"), traceToStep.toString());
        // CQ-08: abertura sintética na convenção, declarando o id anterior para correlacionar execuções antigas
        assertTrue(process.contains("step_id=\"processo.abrir_pagina_inicial\"") && !code.contains("\"INICIO\""), process);
        Map<?, ?> start = (Map<?, ?>) ((List<?>) ((Map<?, ?>) m.get("trace")).get("synthetic")).get(0);
        assertEquals(CodePlan.START_TRACE, start.get("traceId"));
        assertEquals(List.of("INICIO"), start.get("previousTraceIds"));
        assertTrue(files.get("README.md").contains("## Como cada elemento é localizado") && files.get("README.md").contains("âncora de linha"));
        // a busca e o Enter agem no MESMO elemento: uma chave só no registro
        assertTrue(files.get("selectors.json").contains("\"processo.pesquisa.campo_termo_de_busca\"") && !files.get("selectors.json").contains("press_key"));
    }

    @Test
    @DisplayName("Q01: traceId FIXO — PDD renomeado, ordem trocada e registro; aposentado nunca é reaproveitado")
    void rastreioEstavel() {
        FichaCase c = FichaCase.confirmed();
        ListFlowTest.Flow f = ListFlowTest.develop(c);
        Map<String, String> first = stepToTrace(Json.parseObject(f.result().files().get("manifest.json")));
        TraceRegistry reg = TraceRegistry.empty(c.a.id()).updatedWith(first, first.keySet());
        // 1) a pessoa muda o título da seção no PDD: a Page muda de nome, os step_id não
        DocumentDraft d = DraftEdits.reviseSectionTitle(c.reviewedDraft(), 2, "Busca da pessoa", "TEST_AGENT");
        Map<String, String> renamed = generate(c, d, reg.applyTo(CodeNames.none()));
        assertEquals(first, renamed, "título do PDD mudou: traceIds iguais");
        assertTrue(lastPages.contains("class PaginaBusca(PaginaBase):"), "a Page muda de nome com o PDD");
        // sem registro, o novo título daria outra área — é o registro que mantém a correlação
        Map<String, String> unpinned = generate(c, d, CodeNames.none());
        assertNotEquals(first.get(c.search.value().toString()), unpinned.get(c.search.value().toString()));
        // 2) a ordem das leituras muda (nacionalidade passa para antes do título): os traceIds ficam
        int titleIndex = c.a.sessions().get(2).steps().indexOf(c.a.step(c.title).orElseThrow());
        assertTrue(c.a.moveStep(c.nationality, c.a.sessions().get(2).id(), titleIndex));
        assertEquals(first, generate(c, c.reviewedDraft(), reg.applyTo(CodeNames.none())), "ordem trocada: traceIds iguais");
        // 3) uma etapa sai do modelo: o traceId dela é aposentado e não vai para outra etapa
        TraceRegistry after = reg.updatedWith(Map.of(), List.copyOf(new LinkedHashSet<>(first.keySet()) {
            {
                remove(c.died.value().toString());
            }
        }));
        assertTrue(after.retired().contains(first.get(c.died.value().toString())));
        CodeNames withRetired = new CodeNames(Map.of(), Map.of(), Map.of(), after.retired());
        Map<String, String> fresh = generate(c, c.reviewedDraft(), withRetired);
        assertEquals(first.get(c.died.value().toString()) + "_2", fresh.get(c.died.value().toString()), "aposentado não é reaproveitado");
    }

    @Test
    @DisplayName("[Python real] equivalência: o código novo faz as MESMAS chamadas ao framework, nos mesmos elementos, e o mesmo desfecho do anterior")
    void equivalencia() throws Exception {
        Path py = PythonForTests.framework();
        Assumptions.assumeTrue(py != null, "sem o interpretador do framework neste ambiente");
        Path antes = antes("fichas");
        Path novo = tmp.resolve("novo");
        OutputWriter.write(novo, ListFlowTest.develop(FichaCase.confirmed()).result().files());
        String ana = "http://127.0.0.1:18766/leitura/ficha-ana.html";
        Map<String, Object> base = scenario(ana, ana, Map.of(), List.of());
        List<Map<String, Object>> cases = List.of(base,
                scenario(ana, ana, Map.of(), List.of(5)), // sem "Nome completo": vale o título
                scenario(ana, ana, Map.of(), List.of(9)), // sem "Ocupação": vale "Campo(s)"
                scenario(ana, ana, Map.of(), List.of(4, 5)), // sem título e sem nome completo: recusa
                scenario(ana, ana, Map.of("6", List.of("junho de 1839")), List.of()), // data incompleta: recusa
                scenario(ana, "http://127.0.0.1:18766/leitura/ficha-gina.html", Map.of(), List.of()), // outra ficha: recusa
                scenario(ana, ana, Map.of("8", List.of()), List.of())); // nacionalidade vazia: recusa
        for (int i = 0; i < cases.size(); i++) {
            Path sc = tmp.resolve("cenario-" + i + ".json");
            Files.writeString(sc, Json.write(cases.get(i)), StandardCharsets.UTF_8);
            Map<String, Object> a = Json.parseObject(lastLine(runPython(py, List.of(resource("equivalencia.py"), antes.toString(), sc.toString()))));
            Map<String, Object> b = Json.parseObject(lastLine(runPython(py, List.of(resource("equivalencia.py"), novo.toString(), sc.toString()))));
            Map<?, ?> oa = (Map<?, ?>) a.get("outcome");
            Map<?, ?> ob = (Map<?, ?>) b.get("outcome");
            assertEquals(oa.get("status"), ob.get("status"), "cenário " + i);
            // CQ-07: o código novo diz QUAL campo ficou vazio — o nome do campo, nunca o conteúdo
            if (i == 3) assertEquals("DADO_OBRIGATORIO_VAZIO:nome_completo", ob.get("reason"), "cenário " + i);
            if (i == 6) assertEquals("DADO_OBRIGATORIO_VAZIO:nacionalidade", ob.get("reason"), "cenário " + i);
            assertEquals(oa.get("row"), ob.get("row"), "cenário " + i);
            if ("SUCCEEDED".equals(oa.get("status"))) assertEquals(a.get("calls"), b.get("calls"), "cenário " + i + ": mesmas chamadas, mesma ordem, mesmos elementos");
        }
    }

    @Test
    @DisplayName("[Python real] camada VQ: código novo sem achados; o anterior e mutantes (UUID, comentário por etapa, nome opaco, or \"\", seletor) são pegos")
    void qualidadeAst() throws Exception {
        Path py = PythonForTests.framework();
        Assumptions.assumeTrue(py != null, "sem o interpretador do framework neste ambiente");
        Path novo = tmp.resolve("novo");
        OutputWriter.write(novo, ListFlowTest.develop(FichaCase.confirmed()).result().files());
        PythonChecks.Environment env = PythonChecks.resolve(Optional.of(py), novo).orElseThrow();
        PythonChecks.Check ok = quality(env, novo);
        assertEquals(PythonChecks.Status.PASS, ok.status(), ok.codes().toString());
        assertEquals(QualityReport.State.PASS, GeneratedCodeQuality.assess(novo, List.of(ok), true).layer().state());
        PythonChecks.Check old = quality(env, antes("fichas"));
        assertEquals(PythonChecks.Status.FAIL, old.status());
        String oldCodes = old.codes().keySet().toString();
        for (String c : List.of("UUID_EXPOSTO_NO_CODIGO", "COMENTARIO_POR_ETAPA", "NOME_GENERICO", "VARIAVEL_OPACA", "SILENCIA_FALHA", "TRACE_SEM_MAPEAMENTO")) {
            assertTrue(oldCodes.contains(c), c + " no código anterior: " + oldCodes);
        }
        assertEquals("CODIGO_DE_GERADOR_ANTERIOR", GeneratedCodeQuality.assess(antes("fichas"), List.of(), true).issues().get(0).code());
        // controles negativos: cada mutação é pega pelo código certo
        Map<String, String[]> mutants = new LinkedHashMap<>();
        mutants.put("UUID_EXPOSTO_NO_CODIGO", new String[] {"step_id=\"pesquisa.preencher_termo_de_busca\"", "step_id=\"0e0dfafe-aecb-42d1-a861-57e00fde87f6\""});
        mutants.put("COMENTARIO_POR_ETAPA", new String[] {"    def abrir_busca(self) -> None:\n", "    def abrir_busca(self) -> None:\n        # etapa 1 · NAVIGATE\n"});
        mutants.put("NOME_GENERICO", new String[] {"def pesquisar_pessoa(", "def etapas_sessao_2("});
        mutants.put("SILENCIA_FALHA", new String[] {"return join_items(self.acoes.read_items(self.elemento(elemento), step_id=step_id))",
                "return join_items(self.acoes.read_items(self.elemento(elemento), step_id=step_id)) or \"\""});
        mutants.put("SELETOR_NO_CODIGO", new String[] {"self.elemento(\"campo_termo_de_busca\"),", "\"form#buscaform input\","});
        mutants.put("TRACE_SEM_MAPEAMENTO", new String[] {"step_id=\"pesquisa.abrir_pagina\"", "step_id=\"pesquisa.passo_inventado\""});
        for (Map.Entry<String, String[]> e : mutants.entrySet()) {
            Path dir = tmp.resolve("mutante-" + e.getKey());
            OutputWriter.write(dir, ListFlowTest.develop(FichaCase.confirmed()).result().files());
            Path p = dir.resolve("paginas.py");
            String src = Files.readString(p, StandardCharsets.UTF_8);
            assertTrue(src.contains(e.getValue()[0]), "mutação aplicável: " + e.getKey());
            Files.writeString(p, src.replace(e.getValue()[0], e.getValue()[1]), StandardCharsets.UTF_8);
            PythonChecks.Check q = quality(env, dir);
            assertEquals(PythonChecks.Status.FAIL, q.status(), e.getKey());
            assertTrue(q.codes().keySet().stream().anyMatch(k -> k.startsWith(e.getKey() + " ")), e.getKey() + " → " + q.codes());
        }
    }

    @Test
    @DisplayName("[Python real + laboratório] Q03: duas tabelas com \"Nome completo\" → TARGET_AMBIGUOUS no traceId da leitura (nunca \"o primeiro\"); planilha não gravada")
    void ancoraAmbigua() throws Exception {
        Path py = PythonForTests.framework();
        Assumptions.assumeTrue(py != null, "sem o interpretador do framework neste ambiente");
        Path code = tmp.resolve("fichas-python");
        OutputWriter.write(code, ListFlowTest.develop(FichaCase.confirmed()).result().files());
        // cópia do laboratório genérico com UMA ficha a mais: dois quadros, ambos com a linha "Nome completo"
        Path site = tmp.resolve("site");
        Path src = PythonForTests.frameworkRepo().resolve("lab").resolve("static");
        try (var s = Files.walk(src)) {
            for (Path p : s.toList()) {
                Path to = site.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(to);
                else Files.copy(p, to);
            }
        }
        Path js = site.resolve("leitura").resolve("busca.js");
        Files.writeString(js, Files.readString(js, StandardCharsets.UTF_8).replace("'gina inventada': 'ficha-gina.html'",
                "'gina inventada': 'ficha-gina.html', 'dupla teste': 'ficha-dupla.html'"), StandardCharsets.UTF_8);
        String ana = Files.readString(site.resolve("leitura").resolve("ficha-ana.html"), StandardCharsets.UTF_8);
        String table = ana.substring(ana.indexOf("<table"), ana.indexOf("</table>") + "</table>".length());
        Files.writeString(site.resolve("leitura").resolve("ficha-dupla.html"), ana.replace(table, table + "\n<h2>Cônjuge</h2>\n<table class=\"quadro\"><tbody>"
                + "<tr><th scope=\"row\">Nome completo</th><td>Outra Pessoa Qualquer</td></tr></tbody></table>"), StandardCharsets.UTF_8);
        Process lab = new ProcessBuilder(py.toString(), "-m", "http.server", "18766", "--bind", "127.0.0.1", "--directory", site.toString())
                .redirectErrorStream(true).start();
        try {
            Thread.sleep(1500);
            Path lista = tmp.resolve("lista.csv");
            Files.writeString(lista, "termo_de_busca,endereco_esperado\nDupla Teste,http://127.0.0.1:18766/leitura/ficha-dupla.html\n", StandardCharsets.UTF_8);
            Path xlsx = tmp.resolve("dupla.xlsx");
            String run = runPython(py, List.of(code.resolve("main.py").toString(), "--entrada", lista.toString(), "--planilha", xlsx.toString(), "--saida",
                    tmp.resolve("exec").toString(), "--channel", "chromium", "--headless"));
            assertTrue(run.contains("COMPLETED_WITH_FAILURES") || run.contains("STOPPED"), run);
            assertFalse(Files.exists(xlsx), "tudo ou nada: nenhuma planilha com dado de uma tabela escolhida ao acaso");
            String events = Files.readString(tmp.resolve("exec").resolve("execucao").resolve("events.jsonl"), StandardCharsets.UTF_8);
            assertTrue(events.contains("\"error_code\":\"TARGET_AMBIGUOUS\"") && events.contains("\"step_id\":\"ficha.ler_nome_completo\""),
                    "a ambiguidade é do alvo Nome completo, com o step_id legível: " + events.lines().filter(l -> l.contains("AMBIGUOUS")).toList());
        } finally {
            lab.descendants().forEach(ProcessHandle::destroyForcibly);
            lab.destroyForcibly();
        }
    }

    // ------------------------------------------------------------------------------------------------ apoio

    private String lastPages = "";

    private Map<String, String> generate(FichaCase c, DocumentDraft d, CodeNames registry) {
        ProjectContext ctx = ProjectContext.of(c.a, Optional.of(d));
        VariableDeriver.apply(c.a, VariableDeriver.derive(c.a, ctx));
        d = d.processRevision().equals(ProcessProjection.of(c.a).revision()) ? d : c.reviewedDraft();
        ctx = ProjectContext.of(c.a, Optional.of(d));
        PddGate.Result gate = PddGate.evaluate(ctx, Optional.of(d), ListFlowTest.spec(c, ctx), ProcessProjection.of(c.a), ConsultaCase.CLOCK.instant());
        assertTrue(gate.ready(), gate.issues().toString());
        DevelopmentPlan plan = DevelopmentPlanner.plan(c.a, ctx, gate, FrameworkCapabilityCatalog.bundled(), Optional.empty(), Optional.of(d));
        assertTrue(plan.ready(), plan.issues().toString());
        CodeNames names = new CodeNames(plan.names().pages(), plan.names().methods(), registry.traces(), registry.retired());
        PythonGenerator.Result r = SingleGenerator.generate(c.a, new AutomationValidator(RuleSet.V1_1).validate(c.a), plan.singleConfig().orElseThrow(), names);
        assertTrue(r.ok(), r.blockers().toString());
        lastPages = r.files().get("paginas.py");
        return stepToTrace(Json.parseObject(r.files().get("manifest.json")));
    }

    static Map<String, String> traces(Map<String, Object> manifest) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Object o : (List<?>) ((Map<?, ?>) manifest.get("trace")).get("mappings")) {
            Map<?, ?> x = (Map<?, ?>) o;
            out.put(String.valueOf(x.get("traceId")), String.valueOf(x.get("studioStepId")));
        }
        return out;
    }

    static Map<String, String> stepToTrace(Map<String, Object> manifest) {
        Map<String, String> out = new LinkedHashMap<>();
        traces(manifest).forEach((t, s) -> out.put(s, t));
        return out;
    }

    private static Map<String, Object> scenario(String expected, String address, Map<String, List<String>> override, List<Integer> absent) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("4", List.of("Ana Exemplo"));
        values.put("5", List.of("Ana Maria Exemplo da Silva"));
        values.put("6", List.of("21 de junho de 1839"));
        values.put("7", List.of("29 de setembro de 1908 (69 anos)"));
        values.put("8", List.of("brasileira"));
        values.put("9", List.of("escritora", "jornalista"));
        values.put("10", List.of("física"));
        values.putAll(override);
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("input", Map.of("termo_de_busca", "Ana Exemplo", "endereco_esperado", expected));
        s.put("address", address);
        s.put("navigated", false);
        s.put("values", values);
        s.put("absent", new ArrayList<>(absent));
        return s;
    }

    private static Path antes(String name) {
        return Path.of("..", "docs", "codegen-quality-001", "evidence", "antes", name).toAbsolutePath().normalize();
    }

    private String resource(String name) throws Exception {
        Path f = tmp.resolve(name);
        if (!Files.exists(f)) {
            try (var in = CodegenQualityFlowTest.class.getResourceAsStream("/nekkora/ai/" + name)) {
                Files.write(f, in.readAllBytes());
            }
        }
        return f.toString();
    }

    private PythonChecks.Check quality(PythonChecks.Environment env, Path dir) throws Exception {
        return PythonChecks.run(env, dir, () -> false).stream().filter(c -> c.id().equals("CODE_QUALITY")).findFirst().orElseThrow();
    }

    static String lastLine(String s) {
        String[] lines = s.strip().split("\\R");
        return lines[lines.length - 1];
    }

    static String runPython(Path py, List<String> args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(py.toString());
        cmd.add("-B");
        cmd.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        Process p = pb.start();
        String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(120, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IllegalStateException("tempo esgotado");
        }
        return text;
    }

    @Test
    @DisplayName("CQ-05 (rodada 2): ler um botão ou link devolve o TEXTO dele — o nome diz isso, não \"resultado\"")
    void leituraDeControle() {
        ConsultaCase c = ConsultaCase.confirmed();
        com.nekkora.studio.domain.AutomationStep exit = c.a.step(c.exit).orElseThrow();
        var session = c.a.sessions().get(1).id();
        com.nekkora.studio.domain.StepId link = c.a.appendStep(session, com.nekkora.studio.domain.StepDraft.builder().name("Observar")
                .actionType(com.nekkora.studio.domain.action.ActionType.OBSERVE).context(exit.context().orElseThrow())
                .element(ConsultaCase.el("LINK", "a", "Logout", "#logout", false)).build()).id();
        com.nekkora.studio.domain.StepId button = c.a.appendStep(session, com.nekkora.studio.domain.StepDraft.builder().name("Observar")
                .actionType(com.nekkora.studio.domain.action.ActionType.OBSERVE).context(exit.context().orElseThrow())
                .element(ConsultaCase.el("BUTTON", "button", "Imprimir", "#imprimir", false)).build()).id();
        Map<com.nekkora.studio.domain.StepId, String> names = new LinkedHashMap<>();
        for (VariableDeriver.Planned p : VariableDeriver.derive(c.a, ProjectContext.of(c.a, Optional.empty()))) names.put(p.step(), p.name());
        assertEquals("texto_do_link_logout", names.get(link));
        assertEquals("texto_do_botao_imprimir", names.get(button));
        assertEquals("resultado_da_consulta", names.get(c.result), "um elemento comum continua sendo o resultado lido");
    }

    static int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) n++;
        return n;
    }
}
