package com.nekkora.studio.codegen;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.StepEdit;
import com.nekkora.studio.validation.AutomationValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonGeneratorTest {

    private static PythonGenerator.Result gen(FormCase c) {
        return PythonGenerator.generate(c.a, new AutomationValidator().validate(c.a), CodegenConfig.parse(c.config()));
    }

    private static List<String> codes(PythonGenerator.Result r) {
        return r.blockers().stream().map(PythonGenerator.Blocker::code).toList();
    }

    @Test
    void geraProjetoCompletoDeterministico() {
        FormCase c = FormCase.confirmed();
        PythonGenerator.Result r = gen(c);
        assertTrue(r.ok(), r.blockers().toString());
        assertEquals(Set.of("README.md", "automation/__init__.py", "automation/pages.py", "automation/process.py", "automation/selectors.py",
                "config/execution.toml", "main.py", "manifest.json", "requirements.txt", "selectors.json"), r.files().keySet());
        assertEquals(r.files(), gen(c).files(), "mesma entrada → mesmos bytes");
        Map<String, Object> sel = Json.parseObject(r.files().get("selectors.json"));
        assertEquals("1.1.0", sel.get("version"));
        assertEquals("process", sel.get("system"));
        String selectors = r.files().get("selectors.json");
        assertTrue(selectors.contains("\"strategy\": \"label\"") && selectors.contains("\"text\": \"First Name\""), selectors);
        assertFalse(selectors.contains("nth-of-type"), "nunca gera localizador posicional");
        assertTrue(selectors.contains("\"label\": \"Email\""), "rótulo é a identidade quando o nome acessível é vazio");
        String pages = r.files().get("automation/pages.py");
        assertTrue(pages.contains("self.actions.download("), pages);
        assertTrue(pages.contains("postcondition=lambda: self.actions.replaced(field_before)"), "envio só conclui com prova de avanço");
        Map<String, Object> m = Json.parseObject(r.files().get("manifest.json"));
        String code = r.files().get("automation/pages.py") + r.files().get("automation/process.py") + r.files().get("main.py");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mappings = (List<Map<String, Object>>) Json.object(m, "trace").get("mappings");
        for (var id : c.ids) {
            assertFalse(code.contains(id.value().toString()), "o StepId fica no manifesto, não no código: " + id);
            Map<String, Object> mp = mappings.stream().filter(x -> id.value().toString().equals(x.get("studioStepId"))).findFirst().orElseThrow();
            assertTrue(code.contains("step_id=\"" + mp.get("traceId") + "\""), "step_id legível rastreável " + mp.get("traceId"));
        }
        assertEquals(r.planRevision(), m.get("planRevision"));
        assertEquals("1.4.0", m.get("version"));
        assertEquals("en", m.get("language"));
        assertEquals("tabular", m.get("execution"));
        assertEquals(c.ids.size(), Json.array(m, "steps").size());
        assertEquals(9, Json.object(m, "files").size());
        assertTrue(r.files().get("main.py").contains("Column(\"First Name\")"));
    }

    @Test
    void q12CodigoTabularEmInglesComChavesTipadas() {
        // CODEGEN-QUALITY-001 rodada 4 (Q12): a modalidade tabular segue Q01–Q11 — telas pelo nome da sessão em inglês,
        // SelectorKey.<MEMBRO>.value (nunca a chave como texto), InputRow com os cabeçalhos como contrato, CLI em inglês
        PythonGenerator.Result r = gen(FormCase.confirmed());
        String pages = r.files().get("automation/pages.py");
        String process = r.files().get("automation/process.py");
        String selectorsPy = r.files().get("automation/selectors.py");
        String main = r.files().get("main.py");
        assertTrue(pages.contains("class PreparationPage(PageBase):") && pages.contains("class RecordFillingPage(PageBase):")
                && pages.contains("class ConclusionPage(PageBase):"), pages);
        assertTrue(pages.contains("def submit_record(") && pages.contains("first_name: str,") && pages.contains("email: str,"), pages);
        assertTrue(selectorsPy.contains("RECORD_FILLING_FIRST_NAME = \"process.record_filling.first_name\""), selectorsPy);
        assertFalse(pages.contains("\"process."), "nenhuma chave de alvo escrita como texto nas telas");
        assertTrue(pages.contains("SelectorKey.RECORD_FILLING_SUBMIT.value"), pages);
        assertTrue(process.contains("class InputRow:") && process.contains("first_name=_cell(item.data, \"First Name\")"), "cabeçalho fica como está");
        assertTrue(process.contains("class RecordFormProcess(RpaProcess):"), process);
        assertTrue(process.contains("step_id=\"process.open_start_page\""), process);
        assertTrue(main.contains("\"--output\", \"--saida\"") && main.contains("INPUT_SCHEMA = TabularSchema("), main);
        assertTrue(r.files().get("config/execution.toml").contains("selectors = \"../selectors.json\""));
        for (String f : List.of("automation/pages.py", "automation/process.py", "automation/selectors.py", "main.py")) {
            String s = r.files().get(f);
            for (String pt : List.of("Sessao", "executar", "segredos", "dados", "baixado", "marca", "obs[", "_valor", "Processo(", "URL_INICIAL", "# etapa")) {
                assertFalse(s.contains(pt), f + ": identificador do gerador anterior (" + pt + ")");
            }
        }
        Map<String, Object> m = Json.parseObject(r.files().get("manifest.json"));
        @SuppressWarnings("unchecked")
        Map<String, Object> selectors = Json.object(m, "selectors");
        assertEquals(SingleEmitter.equivalence(targetsOf(r.files().get("selectors.json"))), selectors.get("equivalence"), "selectors.py ↔ JSON declarados iguais");
        assertEquals(Json.object(Json.parseObject(r.files().get("selectors.json")), "targets").size(), Json.array(selectors, "keys").size());
        @SuppressWarnings("unchecked")
        Map<String, Object> cli = Json.object(m, "cli");
        assertEquals(Map.of("--saida", "--output"), cli.get("legacyAliases"));
        assertEquals(EnglishNames.GLOSSARY, Json.object(m, "naming").get("glossary"));
    }

    @Test
    void r5ChaveDoDownloadPeloEfeitoETokenDeIconeForaDoNome() {
        // CQ-R5-02: o clique que baixa a planilha tem a chave do efeito (como o método e o step_id); o texto da página
        // continua igual nos localizadores
        PythonGenerator.Result r = gen(FormCase.confirmed());
        String selectorsPy = r.files().get("automation/selectors.py");
        assertTrue(selectorsPy.contains("PREPARATION_DOWNLOAD_SPREADSHEET = \"process.preparation.download_spreadsheet\""), selectorsPy);
        assertTrue(selectorsPy.contains("\"name\": \"Baixar planilha\""), "rótulo da página sem tradução no localizador");
        assertTrue(r.files().get("automation/pages.py").contains("def download_spreadsheet("));
        // token de ícone de fonte no texto acessível: fora do NOME, nunca do texto
        assertEquals("Download Excel", SingleGenerator.withoutIconTokens("Download Excel cloud_download"));
        assertEquals("Next", SingleGenerator.withoutIconTokens("arrow_forward Next"));
        assertEquals("cloud_download", SingleGenerator.withoutIconTokens("cloud_download"), "só o ícone: fica como está");
        assertEquals("Nome completo", SingleGenerator.withoutIconTokens("Nome completo"));
        assertEquals("E-mail Address", SingleGenerator.withoutIconTokens("E-mail Address"));
    }

    @Test
    void r5ChaveRenomeadaRegistraAChaveAnterior(@TempDir Path dir) throws Exception {
        // CQ-R5-02: regerar sobre um projeto cuja chave era outra não troca em silêncio — o manifesto declara a anterior
        FormCase c = FormCase.confirmed();
        PythonGenerator.Result r = gen(c);
        String id = c.download.value().toString();
        String manifest = r.files().get("manifest.json");
        assertFalse(manifest.contains("previousTargetKeys"), "do zero: sem anterior");
        String old = manifest.replace("\"targetKey\": \"process.preparation.download_spreadsheet\"", "\"targetKey\": \"process.preparation.download_excel_cloud_download\"");
        assertNotEquals(manifest, old);
        String linked = OutputWriter.linkPreviousKeys(manifest, old);
        Map<String, Object> mapping = mappingOf(linked, id);
        assertEquals("process.preparation.download_spreadsheet", mapping.get("targetKey"));
        assertEquals(List.of("process.preparation.download_excel_cloud_download"), mapping.get("previousTargetKeys"));
        assertEquals(1, linked.split("previousTargetKeys", -1).length - 1, "só a etapa renomeada ganha o vínculo");
        // a anterior da anterior vem junto; manifesto legado (steps[].key) também conta
        String older = OutputWriter.linkPreviousKeys(manifest, linked.replace("\"targetKey\": \"process.preparation.download_spreadsheet\"",
                "\"targetKey\": \"process.preparation.download_excel\""));
        assertEquals(List.of("process.preparation.download_excel_cloud_download", "process.preparation.download_excel"), mappingOf(older, id).get("previousTargetKeys"));
        String legacy = Pretty.json(Map.of("version", "1.0.0", "steps", List.of(Map.of("stepId", id, "key", "processo.s1.download_excel_cloud_download")))) + "\n";
        assertEquals(List.of("processo.s1.download_excel_cloud_download"), mappingOf(OutputWriter.linkPreviousKeys(manifest, legacy), id).get("previousTargetKeys"));
        assertEquals(manifest, OutputWriter.linkPreviousKeys(manifest, manifest), "mesma chave: nada muda");
        assertEquals(manifest, OutputWriter.linkPreviousKeys(manifest, "{ ilegível"), "anterior ilegível: nada é presumido");
        // pela gravação real: a pasta com o manifesto anterior
        OutputWriter.write(dir, Map.of("manifest.json", old));
        OutputWriter.write(dir, r.files());
        assertEquals(List.of("process.preparation.download_excel_cloud_download"),
                mappingOf(Files.readString(dir.resolve("manifest.json")), id).get("previousTargetKeys"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mappingOf(String manifest, String stepId) {
        for (Object o : Json.array(Json.object(Json.parseObject(manifest), "trace"), "mappings")) {
            Map<String, Object> x = (Map<String, Object>) o;
            if (stepId.equals(x.get("studioStepId"))) return x;
        }
        throw new AssertionError("etapa sem mapeamento: " + stepId);
    }

    @Test
    void q12SessaoSemNomeTraduzivelUsaOPapel() {
        FormCase c = FormCase.confirmed();
        c.a.renameSession(c.a.sessions().get(0).id(), "123");
        PythonGenerator.Result r = gen(c);
        assertTrue(r.ok(), r.blockers().toString());
        assertTrue(r.files().get("automation/pages.py").contains("class SetupPage(PageBase):"), r.files().get("automation/pages.py"));
        assertTrue(r.files().get("manifest.json").contains("\"source\": \"SESSION_ROLE\""));
    }

    @Test
    void q12RegerarSobreProjetoAnteriorGuardaAGeracaoAntiga(@TempDir Path dir) throws Exception {
        // projeto tabular gerado antes (paginas.py/processo.py na raiz): nada é apagado — vai, intacto, para previous-generation/
        Map<String, String> old = new java.util.TreeMap<>(Map.of("paginas.py", "# antes\n", "processo.py", "# processo antes\n",
                "config/execucao.toml", "[runtime]\n", "main.py", "# main antes\n"));
        Map<String, Object> hashes = new java.util.LinkedHashMap<>();
        old.forEach((k, v) -> hashes.put(k, "sha256:" + CodegenConfig.sha256(v)));
        old.put("manifest.json", Pretty.json(Map.of("schema", "nekkora-studio/codegen-manifest", "version", "1.0.0", "automationRevision", 7, "files", hashes)) + "\n");
        OutputWriter.write(dir, old);
        OutputWriter.Report rep = OutputWriter.write(dir, gen(FormCase.confirmed()).files());
        assertEquals(OutputWriter.Status.RETIRED, rep.files().get("paginas.py"), rep.toString());
        assertEquals(OutputWriter.Status.RETIRED, rep.files().get("config/execucao.toml"), rep.toString());
        assertFalse(Files.exists(dir.resolve("paginas.py")));
        try (var s = Files.walk(dir.resolve(OutputWriter.PREVIOUS))) {
            assertTrue(s.anyMatch(p -> p.getFileName().toString().equals("processo.py")), "a geração anterior continua inteira");
        }
        assertTrue(Files.exists(dir.resolve("automation/pages.py")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> targetsOf(String selectorsJson) {
        Map<String, Map<String, Object>> out = new java.util.LinkedHashMap<>();
        Json.object(Json.parseObject(selectorsJson), "targets").forEach((k, v) -> out.put(k, (Map<String, Object>) v));
        return out;
    }

    @Test
    void linhasDentroDoLimiteDoPerfilDoFramework() {
        // E2E-001: ruff E501 (perfil do framework, 120) no projeto gerado — chamadas com StepId passavam de 200 colunas
        PythonGenerator.Result r = gen(FormCase.confirmed());
        for (String f : List.of("main.py", "automation/process.py", "automation/pages.py", "automation/selectors.py")) {
            for (String l : r.files().get(f).split("\n")) assertTrue(l.length() <= PyWrap.MAX, f + ": " + l);
        }
        String longa = "        x = a.f(self.k(\"y\"), \"texto, com (vírgula) e # cerquilha\", step_id=\"" + "0".repeat(80) + "\")  # nota";
        assertEquals("        # nota\n        x = a.f(\n            self.k(\"y\"),\n            \"texto, com (vírgula) e # cerquilha\",\n"
                + "            step_id=\"" + "0".repeat(80) + "\",\n        )", PyWrap.wrap(longa));
        assertEquals("curta(a, b)", PyWrap.wrap("curta(a, b)"));
    }

    @Test
    void importsSemSobraEOrdenados() {
        // E2E-001: ruff no projeto gerado acusou F401 (provedor importado sem uso) e I001 (módulo local antes de nekkora_rpa)
        PythonGenerator.Result r = gen(FormCase.confirmed());
        String main = r.files().get("main.py");
        assertTrue(main.contains("DeferredTabularInputProvider") && !main.contains(" TabularInputProvider"), "só o provedor usado");
        for (String f : List.of("main.py", "automation/process.py", "automation/pages.py")) {
            String s = r.files().get(f);
            int local = s.indexOf("\nfrom automation.");
            assertTrue(local > s.lastIndexOf("\nfrom nekkora_rpa"), f + ": terceiros antes dos módulos do projeto");
        }
    }

    @Test
    void preVooBloqueiaAlvoSemDecisaoConfirmada() {
        FormCase c = FormCase.undecided();
        assertTrue(codes(gen(c)).contains("TARGET_NOT_CONFIRMED"));
        assertTrue(gen(c).files().isEmpty(), "com bloqueio, nada é gerado");
    }

    @Test
    void preVooBloqueiaLocalizadorPosicional() {
        FormCase c = FormCase.confirmed();
        c.a.choosePreferredLocator(c.fill(0), 1); // o alternativo POSITIONAL vira preferido
        c.decide(c.fill(0));
        assertTrue(codes(gen(c)).contains("LOCATOR_POSITIONAL"), codes(gen(c)).toString());
    }

    @Test
    void preVooBloqueiaAlvoDentroDeIframe() {
        // NEKKORA-STUDIO-012: o runtime resolve só no documento principal — gerar para um alvo em iframe seria agir no documento errado
        FormCase c = FormCase.confirmed();
        com.nekkora.studio.domain.element.ElementReference inFrame = com.nekkora.studio.domain.element.ElementReference.builder()
                .semanticKind("TEXT_INPUT").tag("input").accessibleName("").privacyPolicy(com.nekkora.studio.domain.privacy.CapturePrivacy.VERSION)
                .addLocator(new com.nekkora.studio.domain.element.LocatorCandidate(com.nekkora.studio.domain.element.LocatorStrategy.ID, "", "#dsLogin",
                        com.nekkora.studio.domain.element.LocatorStability.HEURISTIC_STABLE, 1, true)).preferLastLocator()
                .addAccessStep(com.nekkora.studio.domain.element.AccessStep.of(com.nekkora.studio.domain.element.AccessStep.Type.MAIN_DOCUMENT, ""))
                .addAccessStep(com.nekkora.studio.domain.element.AccessStep.of(com.nekkora.studio.domain.element.AccessStep.Type.IFRAME, "iframe"))
                .build();
        c.a.replaceTarget(c.fill(0), inFrame, com.nekkora.studio.domain.target.DecisionActor.TEST_AGENT,
                new com.nekkora.studio.domain.target.TargetDecision.Basis("DESCRIPTOR", "RESOLVED", List.of(), FormCase.CLOCK.instant(), ""));
        c.decide(c.fill(0));
        List<String> codes = codes(gen(c));
        assertTrue(codes.contains("TARGET_IN_FRAME_NOT_SUPPORTED"), codes.toString());
        assertTrue(gen(c).files().isEmpty(), "com bloqueio, nada é gerado");
    }

    @Test
    void preVooBloqueiaValorLiteralEAcaoNaoSuportada() {
        FormCase c = FormCase.confirmed();
        c.a.setStepParameters(c.fill(1), ActionParameters.withValue(new ValueSource.LiteralValue("valor-gravado")));
        c.a.updateStepBasics(c.start, "Start", "", ActionType.PRESS_KEY);
        List<String> codes = codes(gen(c));
        assertTrue(codes.contains("LITERAL_VALUE_NOT_GENERATED"), codes.toString());
        assertTrue(codes.contains("UNSUPPORTED_ACTION_PRESS_KEY"), codes.toString());
    }

    @Test
    void preVooBloqueiaConfiguracaoIncoerente() {
        FormCase c = FormCase.confirmed();
        String semColuna = c.config().replaceAll(",\\s*\\{\"header\": \"Email\", \"variable\": \"email\"\\}", "");
        assertNotEquals(c.config(), semColuna);
        assertTrue(codes(PythonGenerator.generate(c.a, new AutomationValidator().validate(c.a), CodegenConfig.parse(semColuna)))
                .contains("VARIABLE_WITHOUT_COLUMN"));
        String semPapel = c.config().replace("\"end\": [3]", "\"end\": []");
        assertTrue(codes(PythonGenerator.generate(c.a, new AutomationValidator().validate(c.a), CodegenConfig.parse(semPapel)))
                .contains("SESSION_WITHOUT_ROLE"));
        String outroDownload = c.config().replace(c.download.value().toString(), "00000000-0000-0000-0000-000000000000");
        assertTrue(codes(PythonGenerator.generate(c.a, new AutomationValidator().validate(c.a), CodegenConfig.parse(outroDownload)))
                .contains("DOWNLOAD_STEP_NOT_FOUND"));
    }

    @Test
    void configuracaoEstrita() {
        FormCase c = FormCase.confirmed();
        String ok = c.config();
        assertEquals("CONFIG_UNKNOWN_FIELD", assertThrows(CodegenConfig.Invalid.class,
                () -> CodegenConfig.parse(ok.replace("\"observations\": true", "\"observations\": true, \"seletores\": {}"))).code());
        assertEquals("CONFIG_PROCESS_NAME", assertThrows(CodegenConfig.Invalid.class,
                () -> CodegenConfig.parse(ok.replace("formulario_por_registro", "Formulário X"))).code());
        assertEquals("CONFIG_START_URL", assertThrows(CodegenConfig.Invalid.class,
                () -> CodegenConfig.parse(ok.replace(FormCase.ORIGIN + "/desafio", "https://outro.exemplo/desafio"))).code());
        assertEquals("CONFIG_COLUMNS_HEADER", assertThrows(CodegenConfig.Invalid.class,
                () -> CodegenConfig.parse(ok.replace("\"Last Name\"", "\"First Name \""))).code());
        assertEquals("CONFIG_SESSIONS_OVERLAP", assertThrows(CodegenConfig.Invalid.class,
                () -> CodegenConfig.parse(ok.replace("\"end\": [3]", "\"end\": [2]"))).code());
        assertEquals("CONFIG_JSON", assertThrows(CodegenConfig.Invalid.class, () -> CodegenConfig.parse("{x")).code());
    }

    @Test
    void escritaProtegeEdicaoManualERegeneraSoOQueMudou(@TempDir Path dir) throws Exception {
        FormCase c = FormCase.confirmed();
        PythonGenerator.Result r = gen(c);
        OutputWriter.Report first = OutputWriter.write(dir, r.files());
        assertTrue(first.files().values().stream().allMatch(s -> s == OutputWriter.Status.WRITTEN), first.toString());
        OutputWriter.Report same = OutputWriter.write(dir, r.files());
        assertTrue(same.files().values().stream().allMatch(s -> s == OutputWriter.Status.UNCHANGED), same.toString());
        // edição manual de um seletor no código gerado: nunca sobrescrita
        Path selectors = dir.resolve("selectors.json");
        Files.writeString(selectors, Files.readString(selectors).replace("First Name", "Primeiro Nome"));
        String manifestBefore = Files.readString(dir.resolve("manifest.json"));
        OutputWriter.Report protectedRun = OutputWriter.write(dir, r.files());
        assertEquals(OutputWriter.Status.PROTECTED, protectedRun.files().get("selectors.json"));
        assertTrue(Files.readString(selectors).contains("Primeiro Nome"));
        assertTrue(Files.exists(dir.resolve("selectors.json.novo")));
        String diff = Files.readString(dir.resolve("selectors.json.diff"));
        assertTrue(diff.contains("-") && diff.contains("Primeiro Nome") && diff.contains("First Name"), diff);
        assertEquals(manifestBefore, Files.readString(dir.resolve("manifest.json")), "manifesto anterior preservado");
        // reparo NO MODELO (outro localizador preferido) + nova geração: só o que mudou é regravado
        Files.writeString(selectors, Files.readString(dir.resolve("selectors.json.novo")));
        c.a.updateStepBasics(c.submit, "Enviar", "", ActionType.CLICK);
        PythonGenerator.Result again = gen(c);
        OutputWriter.Report second = OutputWriter.write(dir, again.files());
        assertEquals(OutputWriter.Status.UNCHANGED, second.files().get("automation/pages.py"), second.toString());
        assertNotEquals(OutputWriter.Status.PROTECTED, second.files().get("manifest.json"));
        assertFalse(Files.exists(dir.resolve("selectors.json.novo")), "pendência resolvida");
    }

    @Test
    void codigoNaoContemValoresNemNomesDeEtapa() {
        FormCase c = FormCase.confirmed();
        c.a.updateStepBasics(c.start, "Iniciar com SENTINELA-NOME", "descrição SENTINELA-DESC", ActionType.CLICK);
        PythonGenerator.Result r = gen(c);
        for (Map.Entry<String, String> f : r.files().entrySet()) {
            assertFalse(f.getValue().contains("SENTINELA"), f.getKey());
            assertFalse(new String(f.getValue().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8).contains("Formulário por registro"), f.getKey());
        }
    }

    @Test
    void observacaoPorPadraoDeTextoSemAlvo() {
        FormCase c = FormCase.confirmed();
        var end = c.a.sessions().get(2);
        var obs = c.a.appendStep(end.id(), StepDraft.builder().name("Ler resultado").actionType(ActionType.OBSERVE).build());
        String sem = c.config();
        assertTrue(codes(PythonGenerator.generate(c.a, new AutomationValidator().validate(c.a), CodegenConfig.parse(sem))).contains("OBSERVE_WITHOUT_TARGET"));
        String com = sem.substring(0, sem.lastIndexOf('}')) + ", \"textObservations\": [{\"stepId\": \"" + obs.id().value() + "\", \"pattern\": \"^Resultado: \\\\d+%$\"}]}";
        PythonGenerator.Result r = PythonGenerator.generate(c.a, new AutomationValidator().validate(c.a), CodegenConfig.parse(com));
        assertTrue(r.ok(), r.blockers().toString());
        String pg = r.files().get("automation/pages.py");
        assertTrue(pg.contains("self.actions.read_text_matching(\"^Resultado: \\\\d+%$\", step_id=\"conclusion.read_result\")"), pg);
        assertTrue(r.files().get("automation/process.py").contains("self.observations[\"conclusion.read_result\"] = conclusion.read_result()"),
                "a leitura vai para observacoes.json pelo step_id legível");
        assertEquals("CONFIG_TEXT_PATTERN", assertThrows(CodegenConfig.Invalid.class,
                () -> CodegenConfig.parse(com.replace("^Resultado", "Resultado"))).code());
    }

    @SuppressWarnings("unused")
    private static StepDraft unused(StepEdit e) {
        return null;
    }
}
