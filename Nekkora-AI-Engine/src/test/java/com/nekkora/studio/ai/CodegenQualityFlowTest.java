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
 * Rodada 3 (Q10/Q11): código em inglês no pacote automation/, chaves tipadas SelectorKey, ids anteriores declarados,
 * campos só com espaços e mesma entrada → mesmos bytes.
 */
class CodegenQualityFlowTest {
    @TempDir
    Path tmp;

    static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    static final Pattern STEP_ID = Pattern.compile("step_id=\"([^\"]+)\"");

    @Test
    @DisplayName("Q01/Q02 + rodada 3: Pages e métodos em inglês pelo PDD, chaves tipadas, sem UUID nem comentário por etapa; step_id ↔ etapa bijetivo")
    void semantico() {
        ListFlowTest.Flow f = ListFlowTest.develop(FichaCase.confirmed());
        Map<String, String> files = f.result().files();
        String pages = files.get("automation/pages.py");
        String process = files.get("automation/process.py");
        String selectors = files.get("automation/selectors.py");
        String code = pages + process + selectors + files.get("main.py");
        // layout: pacote próprio, nenhum módulo de topo com nome da biblioteca padrão (selectors.py só dentro do pacote)
        assertTrue(files.containsKey("automation/__init__.py") && !files.containsKey("selectors.py") && !files.containsKey("paginas.py"), files.keySet().toString());
        // CQ-01 em inglês: a tela onde os dados são lidos leva o nome do DONO dos dados no PDD ("os dados da ficha" → profile)
        assertTrue(pages.contains("class SearchPage(PageBase):\n    \"\"\"Screen of the PDD section “Pesquisa da pessoa”: opens the start page and "
                + "searches the person.") && pages.contains("class ProfilePage(PageBase):"), pages);
        assertTrue(pages.contains("def search_person(self, search_term: str) -> None:") && pages.contains("def open_search(self) -> None:"), pages);
        assertTrue(pages.contains("def read_profile_data(") && pages.contains("class ProfileData:") && pages.contains("    full_name: str | None\n")
                && pages.contains("    birth_date: str\n") && pages.contains("    death_date: str\n"), pages); // R4-02: valor de data → nome de data
        assertTrue(process.contains("class SyntheticCollectionProfilesProcess(RpaProcess):") && process.contains("def build_spreadsheet_row("), process);
        // ajuste herdado (rodada 3): ausente, vazio ou SÓ espaços não satisfaz a coluna; a fonte alternativa do PDD vale
        assertTrue(process.contains("\"full_name\": _filled(profile_data.full_name) or _filled(profile_data.title)")
                && process.contains("\"occupation\": _filled(profile_data.occupation) or _filled(profile_data.fields)"), process);
        // o mapeamento coluna da lista → campo é explícito (a coluna é o contrato e não é traduzida)
        assertTrue(process.contains("search_term=_required_input(data, \"termo_de_busca\"),") && process.contains("INPUT_COLUMNS = (\"termo_de_busca\", "), process);
        // Q10: toda chave de alvo é SelectorKey.<NOME>.value; act só na PageBase; nenhuma chave escrita como texto nas telas
        assertTrue(pages.contains("class PageBase(BasePage):") && pages.contains("def actions(self) -> Actions:") && pages.contains("identity_key: ClassVar[SelectorKey]"));
        assertEquals(1, count(pages, "self.act\n"), "self.act só na PageBase");
        assertEquals(0, count(pages, "self.k("), "nenhuma chave relativa");
        assertFalse(pages.contains("\"process."), "nenhuma chave de alvo escrita como texto nas telas");
        assertTrue(pages.contains("self.actions.fill(SelectorKey.SEARCH_TERM_FIELD.value, search_term, step_id=\"search.fill_search_term\")"), pages);
        assertTrue(selectors.contains("class SelectorKey(StrEnum):") && selectors.contains("    PROFILE_FULL_NAME = \"process.profile.full_name\"\n")
                && selectors.contains("TARGETS: dict[str, dict[str, object]] = {") && !UUID.matcher(selectors).find(), selectors);
        // os rótulos da página continuam como estão (são dados externos): o XPath da linha "Nome completo" não é traduzido
        assertTrue(selectors.contains("normalize-space(.)=\"Nome completo\""), selectors);
        // CQ-03: a explicação da espera está na docstring, não num comentário solto
        assertTrue(pages.replaceAll("\\s+", " ").contains("First waits for “Nascimento” (a required read)"), pages);
        assertFalse(UUID.matcher(pages + process).find(), "nenhum UUID no corpo das Pages/processo");
        assertFalse(code.contains("# etapa") || code.contains(" a = self.act") || code.contains("etapas_sessao") || code.contains("\"fill\"") || code.contains("campo_s"));
        for (String pt : List.of("Pagina", "acoes", "elemento", "dados_da_ficha", "linha_da_planilha", "Entrada", "--entrada\", required", "segredos")) {
            assertFalse(code.contains(pt), "identificador em português no código novo: " + pt);
        }
        Map<String, Object> m = Json.parseObject(files.get("manifest.json"));
        Map<String, String> traceToStep = traces(m);
        Set<String> steps = new LinkedHashSet<>();
        for (Object o : (List<?>) m.get("steps")) steps.add(String.valueOf(((Map<?, ?>) o).get("stepId")));
        assertEquals(steps, new LinkedHashSet<>(traceToStep.values()), "toda etapa tem um traceId");
        assertEquals(traceToStep.size(), new LinkedHashSet<>(traceToStep.values()).size(), "bijetivo");
        Matcher x = STEP_ID.matcher(pages + process);
        while (x.find()) assertTrue(traceToStep.containsKey(x.group(1)) || x.group(1).equals(CodePlan.START_TRACE), "step_id sem mapa: " + x.group(1));
        assertTrue(traceToStep.containsKey("search.fill_search_term") && traceToStep.containsKey("profile.read_full_name"), traceToStep.toString());
        // CQ-08: abertura sintética na convenção, declarando os ids anteriores para correlacionar execuções antigas
        assertTrue(process.contains("step_id=\"process.open_start_page\"") && !code.contains("\"INICIO\"") && !code.contains("abrir_pagina_inicial"), process);
        Map<?, ?> start = (Map<?, ?>) ((List<?>) ((Map<?, ?>) m.get("trace")).get("synthetic")).get(0);
        assertEquals(CodePlan.START_TRACE, start.get("traceId"));
        assertEquals(List.of("processo.abrir_pagina_inicial", "INICIO"), start.get("previousTraceIds"));
        assertTrue(files.get("README.md").contains("## How each element is located") && files.get("README.md").contains("`SelectorKey.PROFILE_FULL_NAME`"));
        // a busca e o Enter agem no MESMO elemento: uma chave só no registro
        assertTrue(files.get("selectors.json").contains("\"process.search.search_term_field\"") && !files.get("selectors.json").contains("press_key"));
        // manifesto 1.4.0: mapa de nomes (origem → inglês), chaves tipadas ↔ etapas, equivalência .py ↔ JSON, linha de comando
        assertEquals("1.4.0", m.get("version"));
        assertEquals("en", m.get("language"));
        Map<?, ?> naming = (Map<?, ?>) m.get("naming");
        assertEquals(List.of(), naming.get("untranslated"), "o caso sintético não tem palavra fora do glossário");
        assertTrue(Json.write(naming.get("identifiers")).contains("\"model\":\"termo_de_busca\",\"python\":\"search_term\""), Json.write(naming));
        assertTrue(Json.write(naming.get("columns")).contains("\"header\":\"Nome completo\",\"field\":\"full_name\""), Json.write(naming));
        Map<?, ?> sel = (Map<?, ?>) m.get("selectors");
        assertEquals("automation/selectors.py", sel.get("module"));
        assertTrue(String.valueOf(sel.get("equivalence")).matches("sha256:[0-9a-f]{64}"));
        Map<?, ?> fullName = ((List<?>) sel.get("keys")).stream().map(k -> (Map<?, ?>) k).filter(k -> "PROFILE_FULL_NAME".equals(k.get("member"))).findFirst().orElseThrow();
        assertEquals("process.profile.full_name", fullName.get("key"));
        assertEquals(List.of("profile.read_full_name"), fullName.get("traceIds"));
        assertEquals(List.of(traceToStep.get("profile.read_full_name")), fullName.get("studioStepIds"));
        assertEquals("--input", ((Map<?, ?>) ((Map<?, ?>) m.get("cli")).get("options")).get("input"));
    }

    @Test
    @DisplayName("rodada 3: mesma automação e mesma revisão → mesmos bytes em todos os arquivos (duas gerações)")
    void mesmosBytes() {
        FichaCase c = FichaCase.confirmed();
        Map<String, String> first = generateFiles(c, c.reviewedDraft(), CodeNames.none());
        Map<String, String> second = generateFiles(c, c.reviewedDraft(), CodeNames.none());
        assertEquals(first.keySet(), second.keySet());
        for (String f : first.keySet()) assertEquals(first.get(f), second.get(f), f);
    }

    @Test
    @DisplayName("rodada 3: traceIds de antes (português) não são fixados — viram previousTraceIds da etapa e nunca são reaproveitados")
    void idsAnterioresDeclarados() {
        FichaCase c = FichaCase.confirmed();
        Map<String, String> english = generate(c, c.reviewedDraft(), CodeNames.none());
        String full = english.entrySet().stream().filter(e -> e.getValue().equals("profile.read_full_name")).map(Map.Entry::getKey).findFirst().orElseThrow();
        // registro da convenção anterior (1.0.0): o id em português da leitura do nome completo
        TraceRegistry legacy = TraceRegistry.parse("{\"schema\":\"nekkora-studio/trace-registry\",\"version\":\"1.0.0\",\"automationId\":\"" + c.a.id().value()
                + "\",\"active\":[{\"studioStepId\":\"" + full + "\",\"traceId\":\"ficha.ler_nome_completo\"}],\"retired\":[]}");
        assertEquals("pt", legacy.convention());
        Map<String, String> regenerated = generate(c, c.reviewedDraft(), legacy.applyTo(CodeNames.none()));
        assertEquals(english, regenerated, "os ids novos são os da convenção atual");
        Map<String, Object> m = Json.parseObject(lastManifest);
        Map<?, ?> mapping = ((List<?>) ((Map<?, ?>) m.get("trace")).get("mappings")).stream().map(x -> (Map<?, ?>) x)
                .filter(x -> full.equals(x.get("studioStepId"))).findFirst().orElseThrow();
        assertEquals(List.of("ficha.ler_nome_completo"), mapping.get("previousTraceIds"));
        // depois de gerar: registro na convenção atual, com o id anterior guardado na etapa
        TraceRegistry after = legacy.updatedWith(stepToTrace(m), english.keySet());
        assertEquals("en", after.convention());
        assertEquals("profile.read_full_name", after.active().get(full));
        assertEquals(List.of("ficha.ler_nome_completo"), after.previous().get(full));
        assertEquals(after, TraceRegistry.parse(after.toJson()), "1.1.0 ida e volta");
        // e o id anterior nunca vai para outra etapa (fica reservado)
        assertFalse(regenerated.values().stream().anyMatch("ficha.ler_nome_completo"::equals));
    }

    @Test
    @DisplayName("Q01: traceId FIXO — PDD renomeado, ordem trocada e registro; aposentado nunca é reaproveitado")
    void rastreioEstavel() {
        FichaCase c = FichaCase.confirmed();
        ListFlowTest.Flow f = ListFlowTest.develop(c);
        Map<String, String> first = stepToTrace(Json.parseObject(f.result().files().get("manifest.json")));
        TraceRegistry reg = TraceRegistry.empty(c.a.id()).updatedWith(first, first.keySet());
        // 1) a pessoa muda o título da seção no PDD: a Page muda de nome, os step_id não
        DocumentDraft d = DraftEdits.reviseSectionTitle(c.reviewedDraft(), 2, "Consulta da pessoa", "TEST_AGENT");
        Map<String, String> renamed = generate(c, d, reg.applyTo(CodeNames.none()));
        assertEquals(first, renamed, "título do PDD mudou: traceIds iguais");
        assertTrue(lastPages.contains("class QueryPage(PageBase):"), "a Page muda de nome com o PDD");
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
            // rodada 3: o campo interno é inglês; a coluna da planilha (o contrato) é a mesma
            if (i == 3) assertEquals("DADO_OBRIGATORIO_VAZIO:full_name", ob.get("reason"), "cenário " + i);
            if (i == 6) assertEquals("DADO_OBRIGATORIO_VAZIO:nationality", ob.get("reason"), "cenário " + i);
            if (i == 3 || i == 6) assertEquals(i == 3 ? "Nome completo" : "Nacionalidade", ob.get("column"), "cenário " + i);
            assertEquals(oa.get("row"), ob.get("row"), "cenário " + i);
            if ("SUCCEEDED".equals(oa.get("status"))) assertEquals(a.get("calls"), b.get("calls"), "cenário " + i + ": mesmas chamadas, mesma ordem, mesmos elementos");
        }
        // ajuste herdado (rodada 3): só espaços não satisfaz a coluna obrigatória — só o código NOVO (o anterior aceitava)
        Map<String, Object> blankName = scenario(ana, ana, Map.of("5", List.of("   ")), List.of());
        Map<String, Object> blankBoth = scenario(ana, ana, Map.of("4", List.of(" \t "), "5", List.of("   ")), List.of());
        Map<String, Object> blankOccupation = scenario(ana, ana, Map.of("9", List.of("  ")), List.of());
        Map<String, Object> inner = scenario(ana, ana, Map.of("5", List.of("Ana  Maria  Exemplo")), List.of());
        Map<String, Object> blankRequired = scenario(ana, ana, Map.of("8", List.of("   ")), List.of());
        Map<?, ?> r1 = (Map<?, ?>) runScenario(py, novo, blankName, "branco-1").get("outcome");
        assertEquals("SUCCEEDED", r1.get("status"));
        assertEquals("Ana Exemplo", ((Map<?, ?>) r1.get("row")).get("Nome completo"), "principal só com espaços: vale o título (fonte alternativa)");
        Map<?, ?> r2 = (Map<?, ?>) runScenario(py, novo, blankBoth, "branco-2").get("outcome");
        assertEquals("DADO_OBRIGATORIO_VAZIO:full_name", r2.get("reason"), "principal e alternativa só com espaços: recusa com o nome do campo");
        assertFalse(Json.write(r2).contains("   "), "o conteúdo nunca vai ao motivo");
        Map<?, ?> r3 = (Map<?, ?>) runScenario(py, novo, blankOccupation, "branco-3").get("outcome");
        assertEquals("física", ((Map<?, ?>) r3.get("row")).get("Ocupação"), "ocupação só com espaços: vale Campo(s)");
        // o leitor do framework (join_items) já normaliza o texto exibido; a regra da linha não muda o valor que passa
        Map<?, ?> r4 = (Map<?, ?>) runScenario(py, novo, inner, "branco-4").get("outcome");
        assertEquals("Ana Maria Exemplo", ((Map<?, ?>) r4.get("row")).get("Nome completo"), "o texto lido é o normalizado pelo framework");
        String filled = lastLine(runPython(py, List.of("-c", "import json, sys; sys.path.insert(0, sys.argv[1]); from automation.process import _filled; "
                + "print(json.dumps([_filled(' Ana  Maria '), _filled(' \\t '), _filled(''), _filled(None)]))", novo.toString())));
        assertEquals("[\" Ana  Maria \", null, null, null]", filled, "_filled: só espaços/vazio/ausente → None; o resto passa intacto (espaços do meio inclusive)");
        Map<?, ?> r5 = (Map<?, ?>) runScenario(py, novo, blankRequired, "branco-5").get("outcome");
        assertEquals("DADO_OBRIGATORIO_VAZIO:nationality", r5.get("reason"), "coluna sem alternativa só com espaços: recusa");
    }

    private Map<String, Object> runScenario(Path py, Path project, Map<String, Object> scenario, String name) throws Exception {
        Path sc = tmp.resolve(name + ".json");
        Files.writeString(sc, Json.write(scenario), StandardCharsets.UTF_8);
        return Json.parseObject(lastLine(runPython(py, List.of(resource("equivalencia.py"), project.toString(), sc.toString()))));
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
        mutants.put("UUID_EXPOSTO_NO_CODIGO", new String[] {"step_id=\"search.fill_search_term\"", "step_id=\"0e0dfafe-aecb-42d1-a861-57e00fde87f6\""});
        mutants.put("COMENTARIO_POR_ETAPA", new String[] {"    def open_search(self) -> None:\n", "    def open_search(self) -> None:\n        # step 1 · NAVIGATE\n"});
        mutants.put("NOME_GENERICO", new String[] {"def search_person(", "def etapas_sessao_2("});
        mutants.put("SILENCIA_FALHA", new String[] {"return join_items(self.actions.read_items(key, step_id=step_id))",
                "return join_items(self.actions.read_items(key, step_id=step_id)) or \"\""});
        mutants.put("SELETOR_NO_CODIGO", new String[] {"self.actions.fill(SelectorKey.SEARCH_TERM_FIELD.value,", "self.actions.fill(\"form#buscaform input\","});
        mutants.put("TRACE_SEM_MAPEAMENTO", new String[] {"step_id=\"search.open_page\"", "step_id=\"search.invented_step\""});
        // rodada 3: regressão de idioma e chave de alvo escrita como texto
        mutants.put("IDENTIFICADOR_EM_PORTUGUES", new String[] {"def search_person(self, search_term: str)", "def pesquisar_pessoa(self, termo_de_busca: str)"});
        mutants.put("CHAVE_DE_ALVO_LITERAL", new String[] {"self.actions.fill(SelectorKey.SEARCH_TERM_FIELD.value,", "self.actions.fill(\"process.search.search_term_field\","});
        for (Map.Entry<String, String[]> e : mutants.entrySet()) {
            Path dir = tmp.resolve("mutante-" + e.getKey());
            OutputWriter.write(dir, ListFlowTest.develop(FichaCase.confirmed()).result().files());
            Path p = dir.resolve("automation").resolve("pages.py");
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
            String run = runPython(py, List.of(code.resolve("main.py").toString(), "--input", lista.toString(), "--spreadsheet", xlsx.toString(), "--output",
                    tmp.resolve("exec").toString(), "--channel", "chromium", "--headless"));
            assertTrue(run.contains("COMPLETED_WITH_FAILURES") || run.contains("STOPPED"), run);
            assertFalse(Files.exists(xlsx), "tudo ou nada: nenhuma planilha com dado de uma tabela escolhida ao acaso");
            String events = Files.readString(tmp.resolve("exec").resolve("execucao").resolve("events.jsonl"), StandardCharsets.UTF_8);
            assertTrue(events.contains("\"error_code\":\"TARGET_AMBIGUOUS\"") && events.contains("\"step_id\":\"profile.read_full_name\""),
                    "a ambiguidade é do alvo Nome completo, com o step_id legível: " + events.lines().filter(l -> l.contains("AMBIGUOUS")).toList());
        } finally {
            lab.descendants().forEach(ProcessHandle::destroyForcibly);
            lab.destroyForcibly();
        }
    }

    // ------------------------------------------------------------------------------------------------ apoio

    private String lastPages = "";
    private String lastManifest = "";

    private Map<String, String> generate(FichaCase c, DocumentDraft d, CodeNames registry) {
        Map<String, String> files = generateFiles(c, d, registry);
        lastPages = files.get("automation/pages.py");
        lastManifest = files.get("manifest.json");
        return stepToTrace(Json.parseObject(lastManifest));
    }

    private Map<String, String> generateFiles(FichaCase c, DocumentDraft d, CodeNames registry) {
        ProjectContext ctx = ProjectContext.of(c.a, Optional.of(d));
        VariableDeriver.apply(c.a, VariableDeriver.derive(c.a, ctx));
        d = d.processRevision().equals(ProcessProjection.of(c.a).revision()) ? d : c.reviewedDraft();
        ctx = ProjectContext.of(c.a, Optional.of(d));
        PddGate.Result gate = PddGate.evaluate(ctx, Optional.of(d), ListFlowTest.spec(c, ctx), ProcessProjection.of(c.a), ConsultaCase.CLOCK.instant());
        assertTrue(gate.ready(), gate.issues().toString());
        DevelopmentPlan plan = DevelopmentPlanner.plan(c.a, ctx, gate, FrameworkCapabilityCatalog.bundled(), Optional.empty(), Optional.of(d));
        assertTrue(plan.ready(), plan.issues().toString());
        CodeNames names = new CodeNames(plan.names().pages(), plan.names().methods(), registry.traces(), registry.retired(), registry.previous());
        PythonGenerator.Result r = SingleGenerator.generate(c.a, new AutomationValidator(RuleSet.V1_1).validate(c.a), plan.singleConfig().orElseThrow(), names);
        assertTrue(r.ok(), r.blockers().toString());
        return r.files();
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
