package com.nekkora.studio.ai.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.ai.ConsultaCase;
import com.nekkora.studio.ai.FichaCase;
import com.nekkora.studio.ai.PythonForTests;
import com.nekkora.studio.ai.capability.FrameworkCapabilityCatalog;
import com.nekkora.studio.ai.context.ProjectContext;
import com.nekkora.studio.ai.gate.PddGate;
import com.nekkora.studio.ai.pycheck.PythonChecks;
import com.nekkora.studio.ai.quality.AutomationQualityGate;
import com.nekkora.studio.ai.quality.QualityIssue;
import com.nekkora.studio.ai.spec.DataFlowDocument;
import com.nekkora.studio.ai.spec.FunctionalSpec;
import com.nekkora.studio.ai.spec.SpecResolver;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.codegen.CodegenConfig;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * NEKKORA-SPEC-OPTIONAL-COLUMNS-001 — obrigatoriedade revisada por coluna, da especificação funcional ao Python e ao XLSX
 * (matriz G5). O caso é o sintético das fichas (mesmo formato da Wikipédia: "Nascimento", "Morte"…); nenhum site externo.
 */
class OptionalColumnsTest {
    @TempDir
    Path tmp;

    private static final java.time.Instant NOW = ConsultaCase.CLOCK.instant();

    record Flow(FichaCase c, DevelopmentPlan plan, PythonGenerator.Result result, PddGate.Result gate, FunctionalSpec spec) {}

    /** O fluxo da lista com decisões a mais (as do teste) por cima da especificação de sempre. */
    static Flow develop(FichaCase c, BiFunction<FunctionalSpec, ProjectContext, FunctionalSpec> extra) {
        DocumentDraft d = c.reviewedDraft();
        ProjectContext ctx = ProjectContext.of(c.a, Optional.of(d));
        VariableDeriver.apply(c.a, VariableDeriver.derive(c.a, ctx));
        d = c.reviewedDraft();
        ctx = ProjectContext.of(c.a, Optional.of(d));
        FunctionalSpec spec = extra.apply(ListFlowTest.spec(c, ctx), ctx);
        PddGate.Result gate = PddGate.evaluate(ctx, Optional.of(d), spec, ProcessProjection.of(c.a), NOW);
        DevelopmentPlan plan = DevelopmentPlanner.plan(c.a, ctx, gate, FrameworkCapabilityCatalog.bundled(), Optional.empty(), Optional.of(d));
        PythonGenerator.Result r = plan.singleConfig().isEmpty() ? null
                : SingleGenerator.generate(c.a, new AutomationValidator(RuleSet.V1_1).validate(c.a), plan.singleConfig().orElseThrow(), plan.names());
        return new Flow(c, plan, r, gate, spec);
    }

    static FunctionalSpec.Decision requirement(ProjectContext ctx, StepId step, String value) {
        return new FunctionalSpec.Decision(FunctionalSpec.Field.OUTPUT_REQUIREMENT, step, value, FunctionalSpec.Actor.TEST_AGENT, FunctionalSpec.Basis.ANSWER,
                ctx.step(step).orElseThrow().fingerprint(), NOW);
    }

    static Flow deathOptional() {
        FichaCase c = FichaCase.confirmed();
        return develop(c, (s, ctx) -> s.with(requirement(ctx, c.died, FunctionalSpec.OPTIONAL + "ABSENT_FOR_SOME_ITEMS")));
    }

    // ------------------------------------------------------------------------------------------- G1: contrato
    @Test
    @DisplayName("G1: OUTPUT_REQUIREMENT só REQUIRED ou OPTIONAL:<motivo do vocabulário>; 1.2.0 só quando usado; campo novo em versão antiga é recusado")
    void contrato() {
        FichaCase c = FichaCase.confirmed();
        StepId step = c.died;
        for (String bad : List.of("OPTIONAL", "OPTIONAL:", "OPTIONAL:porque sim", "N/A", "required", "")) {
            assertThrows(IllegalArgumentException.class, () -> new FunctionalSpec.Decision(FunctionalSpec.Field.OUTPUT_REQUIREMENT, step, bad,
                    FunctionalSpec.Actor.DEVELOPER, FunctionalSpec.Basis.ANSWER, "sha256:x", NOW), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> new FunctionalSpec.Decision(FunctionalSpec.Field.OUTPUT_REQUIREMENT, null, "REQUIRED",
                FunctionalSpec.Actor.DEVELOPER, FunctionalSpec.Basis.ANSWER, null, NOW), "por etapa, presa ao fingerprint");
        FunctionalSpec base = FunctionalSpec.empty(c.a.id()).with(new FunctionalSpec.Decision(FunctionalSpec.Field.DATA_FLOW, null, "LIST_XLSX",
                FunctionalSpec.Actor.DEVELOPER, FunctionalSpec.Basis.ANSWER, null, NOW));
        assertTrue(base.toJson().contains("\"version\":\"1.1.0\""), "sem o campo novo: 1.1.0 como antes");
        FunctionalSpec with = base.with(new FunctionalSpec.Decision(FunctionalSpec.Field.OUTPUT_REQUIREMENT, step, "OPTIONAL:NOT_APPLICABLE_FOR_SOME_ITEMS",
                FunctionalSpec.Actor.DEVELOPER, FunctionalSpec.Basis.ANSWER, "sha256:x", NOW));
        assertTrue(with.toJson().contains("\"version\":\"1.2.0\""));
        assertEquals(with, FunctionalSpec.parse(with.toJson()), "ida e volta");
        assertNotEquals(base.revision(), with.revision(), "a decisão muda a revisão da especificação");
        String downgraded = with.toJson().replace("\"version\":\"1.2.0\"", "\"version\":\"1.1.0\"");
        assertEquals("SPEC_VERSION", assertThrows(IllegalArgumentException.class, () -> FunctionalSpec.parse(downgraded)).getMessage());
    }

    @Test
    @DisplayName("G1: a IA não decide obrigatoriedade — proposta OUTPUT_REQUIREMENT é recusada pelo verificador (KIND_UNKNOWN)")
    void iaNaoDecide() {
        FichaCase c = FichaCase.confirmed();
        ProjectContext ctx = ProjectContext.of(c.a, Optional.of(c.reviewedDraft()));
        String proposal = "{\"proposals\": [{\"kind\": \"OUTPUT_REQUIREMENT\", \"stepId\": \"" + c.died.value() + "\", \"value\": \"OPTIONAL:ABSENT_FOR_SOME_ITEMS\"}]}";
        com.nekkora.studio.ai.provider.PlanVerifier.Result r = com.nekkora.studio.ai.provider.PlanVerifier.verify(proposal, ctx);
        assertTrue(r.accepted().isEmpty());
        assertEquals(List.of(new com.nekkora.studio.ai.provider.PlanVerifier.Rejection("KIND_UNKNOWN", 0)), r.rejected());
    }

    // ------------------------------------------------------------------------------------------- G1/G2: resolução
    @Test
    @DisplayName("G1: sem decisão, toda coluna é obrigatória; cada coluna (menos a chave) é informação revisável; chave opcional bloqueia; alvo mudou → decisão não vale")
    void resolucao() {
        Flow legacy = develop(FichaCase.confirmed(), (s, ctx) -> s);
        SpecResolver.ResolvedSpec rs = legacy.gate().spec();
        assertTrue(rs.requirements().values().stream().allMatch(r -> FunctionalSpec.REQUIRED.equals(r.value()) && r.provenance() == SpecResolver.Provenance.DERIVED_KIND));
        List<QualityIssue> infos = legacy.gate().issues().stream().filter(i -> i.code().equals("SPEC_OUTPUT_REQUIREMENT")).toList();
        assertEquals(5, infos.size(), "Nome completo, Nascimento, Morte, Nacionalidade, Ocupação (a URL é a chave)");
        assertTrue(infos.stream().allMatch(i -> i.kind() == QualityIssue.Kind.INFO && !i.kind().blocking()), "informação, nunca bloqueio");
        QualityIssue morte = infos.stream().filter(i -> i.detail().contains("“Morte”")).findFirst().orElseThrow();
        assertEquals(FunctionalSpec.Field.OUTPUT_REQUIREMENT, morte.help().destination());
        assertEquals(java.util.Arrays.asList("REQUIRED", "OPTIONAL:ABSENT_FOR_SOME_ITEMS", "OPTIONAL:NOT_APPLICABLE_FOR_SOME_ITEMS", null),
                morte.help().options().stream().map(o -> o.value()).toList());
        assertTrue(morte.detail().contains("obrigatória (padrão, sem decisão)"), morte.detail());
        // chave opcional: bloqueio
        FichaCase k = FichaCase.confirmed();
        Flow key = develop(k, (s, ctx) -> s.with(requirement(ctx, k.address, "OPTIONAL:ABSENT_FOR_SOME_ITEMS")));
        assertTrue(key.gate().issues().stream().anyMatch(i -> i.code().equals("SPEC_OUTPUT_KEY_OPTIONAL") && i.kind().blocking()), key.gate().issues().toString());
        assertFalse(key.gate().ready());
        // decisão presa ao fingerprint: outro fingerprint → não vale (volta ao padrão)
        FichaCase f = FichaCase.confirmed();
        Flow stale = develop(f, (s, ctx) -> s.with(new FunctionalSpec.Decision(FunctionalSpec.Field.OUTPUT_REQUIREMENT, f.died, "OPTIONAL:ABSENT_FOR_SOME_ITEMS",
                FunctionalSpec.Actor.TEST_AGENT, FunctionalSpec.Basis.ANSWER, "sha256:alvo-de-antes", NOW)));
        assertFalse(stale.gate().spec().optionalColumn(f.died), "alvo mudou: a decisão anterior não vale");
    }

    @Test
    @DisplayName("G2: a obrigatoriedade de cada coluna chega à janela Desenvolver junto do código gerado (o orquestrador não a perde)")
    void chegaAJanela() {
        FichaCase c = FichaCase.confirmed();
        DocumentDraft d = c.reviewedDraft();
        ProjectContext ctx = ProjectContext.of(c.a, Optional.of(d));
        VariableDeriver.apply(c.a, VariableDeriver.derive(c.a, ctx));
        d = c.reviewedDraft();
        ctx = ProjectContext.of(c.a, Optional.of(d));
        FunctionalSpec spec = ListFlowTest.spec(c, ctx).with(requirement(ctx, c.died, "OPTIONAL:ABSENT_FOR_SOME_ITEMS"));
        com.nekkora.studio.ai.generation.GenerationOrchestrator.Run run = com.nekkora.studio.ai.generation.GenerationOrchestrator.prepare(c.a, Optional.of(d), spec,
                FrameworkCapabilityCatalog.bundled(), Optional.empty(), tmp.resolve("saida"), NOW, p -> { }, () -> false);
        assertTrue(run.files() != null && run.files().containsKey("main.py"), run.issues().toString());
        List<QualityIssue> reqs = run.issues().stream().filter(i -> i.code().equals("SPEC_OUTPUT_REQUIREMENT")).toList();
        assertEquals(5, reqs.size(), run.issues().toString());
        assertTrue(reqs.stream().anyMatch(i -> i.detail().contains("“Morte”: opcional — o dado não existe em alguns itens")));
    }

    // ------------------------------------------------------------------------------------------- G3: gerador
    @Test
    @DisplayName("G3: \"Morte\" opcional → str | None, exists() sem espera depois da leitura obrigatória, REQUIRED_FIELDS sem ela, OutputColumn(required=False), cabeçalho igual")
    void geracao() throws Exception {
        Flow f = deathOptional();
        assertTrue(f.result().ok(), f.result().blockers().toString());
        OutputWriter.write(Path.of("target", "codegen-samples", "optional-columns"), f.result().files()); // para leitura (não é evidência oficial)
        SingleConfig cfg = f.plan().singleConfig().orElseThrow();
        assertTrue(cfg.toJson().contains("\"version\": \"1.3.0\""), "configuração 1.3.0 só com coluna opcional");
        SingleConfig.Column morte = cfg.output().columns().stream().filter(c -> c.header().equals("Morte")).findFirst().orElseThrow();
        assertFalse(morte.required());
        assertEquals("ABSENT_FOR_SOME_ITEMS", morte.requirementReason());
        assertTrue(cfg.output().columns().stream().filter(c -> !c.header().equals("Morte")).allMatch(SingleConfig.Column::required), "as outras continuam obrigatórias");
        assertTrue(cfg.reads().get(f.c().died.value().toString()).optional(), "a leitura pode faltar (exists)");
        assertFalse(cfg.reads().get(f.c().born.value().toString()).optional());
        Map<String, String> files = f.result().files();
        String pages = files.get("automation/pages.py");
        String process = files.get("automation/process.py");
        String main = files.get("main.py");
        assertTrue(pages.contains("    death_date: str | None\n") && pages.contains("    birth_date: str\n"), pages);
        assertTrue(pages.contains("death_date = self._date_if_present(SelectorKey.PROFILE_DEATH_DATE.value, step_id=\"profile.read_death_date\")"), pages);
        assertTrue(pages.contains("birth_date = self._date(SelectorKey.PROFILE_BIRTH_DATE.value, step_id=\"profile.read_birth_date\")"));
        assertTrue(pages.contains("self.wait_loaded(step_id=\"profile.read_birth_date\")"), "a página pronta antes de uma ausência ser conclusiva");
        String flat = pages.replaceAll("\\s+", " ");
        assertTrue(flat.contains("“Morte” may be missing on this page (``None``; an optional column in the PDD)."), pages);
        assertTrue(flat.contains("absence foreseen in the PDD: an alternative source or an optional column"), pages);
        assertTrue(process.contains("REQUIRED_FIELDS = (\"url\", \"full_name\", \"birth_date\", \"nationality\", \"occupation\")"), process);
        assertTrue(process.contains("    for field in REQUIRED_FIELDS:\n        if row[field] is None:\n            raise BusinessRuleRejected(f\"DADO_OBRIGATORIO_VAZIO:{field}\")"));
        assertTrue(process.contains("\"death_date\": _filled(profile_data.death_date),"), "a ausência fica None (nunca \"\", \"N/A\", data inventada)");
        assertTrue(process.contains("- “Morte”: optional in the PDD (the data does not exist for some items): stays empty when missing."), process);
        assertTrue(main.contains("OutputColumn(\"Morte\", \"death_date\", ColumnKind.ISO_DATE, required=False),"), main);
        assertTrue(main.contains("OutputColumn(\"Nascimento\", \"birth_date\", ColumnKind.ISO_DATE),"), "obrigatória como antes");
        for (String invented : List.of("\"N/A\"", "\"None\"", "or \"\"", "1900-01-01", "0000-00-00")) assertFalse((pages + process + main).contains(invented), invented);
        Map<String, Object> m = Json.parseObject(files.get("manifest.json"));
        assertEquals("1.5.0", m.get("version"));
        Map<?, ?> output = (Map<?, ?>) m.get("output");
        Map<?, ?> rule = (Map<?, ?>) output.get("requirement");
        assertEquals("nekkora-studio/output-requirement/1.0.0", rule.get("schema"));
        assertEquals(f.gate().record().specRevision(), rule.get("specRevision"), "a regra aponta a especificação revisada que a autorizou");
        List<?> cols = (List<?>) output.get("columns");
        Map<?, ?> d = cols.stream().map(x -> (Map<?, ?>) x).filter(x -> "Morte".equals(x.get("header"))).findFirst().orElseThrow();
        assertEquals("death_date", d.get("field"));
        assertEquals("OPTIONAL", d.get("requirement"));
        assertEquals("ABSENT_FOR_SOME_ITEMS", d.get("requirementReason"));
        assertTrue(files.get("README.md").contains("Optional in the PDD (the cell stays empty when the data is missing; nothing is invented): `Morte`."));
    }

    @Test
    @DisplayName("G3: genérico — a mesma decisão em \"Nacionalidade\" (texto) dá o mesmo padrão; o gerador não tem nome de coluna escrito")
    void generico() throws Exception {
        FichaCase c = FichaCase.confirmed();
        Flow f = develop(c, (s, ctx) -> s.with(requirement(ctx, c.nationality, "OPTIONAL:NOT_APPLICABLE_FOR_SOME_ITEMS")));
        assertTrue(f.result().ok(), f.result().blockers().toString());
        Map<String, String> files = f.result().files();
        assertTrue(files.get("automation/pages.py").contains("    nationality: str | None\n"));
        assertTrue(files.get("automation/pages.py").contains("nationality = self._text_if_present(SelectorKey.PROFILE_NATIONALITY.value"));
        assertTrue(files.get("automation/pages.py").contains("    death_date: str\n"), "Morte volta a ser obrigatória");
        assertTrue(files.get("automation/process.py").contains("REQUIRED_FIELDS = (\"url\", \"full_name\", \"birth_date\", \"death_date\", \"occupation\")"));
        assertTrue(files.get("main.py").contains("OutputColumn(\"Nacionalidade\", \"nationality\", ColumnKind.TEXT, required=False),"));
        // nenhum tratamento especial por nome nas fontes do gerador, do planejador ou da resolução
        List<Path> roots = List.of(Path.of("src", "main", "java"), Path.of("..", "nekkora-studio-codegen", "src", "main", "java"));
        for (Path root : roots) {
            try (Stream<Path> s = Files.walk(root)) {
                for (Path p : s.filter(x -> x.toString().endsWith(".java")).toList()) {
                    // só o CÓDIGO (comentários e javadoc citam exemplos); o vocabulário de TIPO data (VariableDeriver.DATE_LABELS,
                    // minúsculo) é anterior e não decide obrigatoriedade
                    String code = Files.readAllLines(p, StandardCharsets.UTF_8).stream().map(String::strip)
                            .filter(l -> !l.startsWith("*") && !l.startsWith("/*") && !l.startsWith("//")).map(l -> l.replaceAll("//.*$", ""))
                            .collect(java.util.stream.Collectors.joining("\n"));
                    for (String name : List.of("\"Morte\"", "\"death_date\"", "\"Nascimento\"", "\"birth_date\"")) {
                        assertFalse(code.contains(name), p + " tem " + name);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("G3/compatibilidade: sem decisão ou com REQUIRED explícito, o Python é byte a byte o de antes (configuração 1.2.0, manifesto 1.4.0)")
    void legadoIntacto() {
        Flow before = develop(FichaCase.confirmed(), (s, ctx) -> s);
        FichaCase c = FichaCase.confirmed();
        Flow explicit = develop(c, (s, ctx) -> s.with(requirement(ctx, c.died, "REQUIRED")).with(requirement(ctx, c.born, "REQUIRED")));
        assertTrue(before.result().ok() && explicit.result().ok());
        assertTrue(before.plan().singleConfig().orElseThrow().toJson().contains("\"version\": \"1.2.0\""));
        // cada FichaCase tem StepIds novos: só a procedência (UUID) muda entre os dois; o resto é igual byte a byte
        for (String f : List.of("automation/pages.py", "automation/process.py", "automation/selectors.py", "main.py", "selectors.json", "README.md")) {
            assertEquals(noIds(before.result().files().get(f)), noIds(explicit.result().files().get(f)), f);
        }
        for (String f : List.of("automation/pages.py", "automation/process.py", "automation/selectors.py", "main.py")) {
            assertFalse(before.result().files().get(f).matches("(?s).*[0-9a-f]{8}-[0-9a-f]{4}-.*"), "o Python não tem UUID: comparação exata");
        }
        String code = before.result().files().get("automation/process.py") + before.result().files().get("main.py");
        assertTrue(code.contains("    for field, value in row.items():\n        if value is None:") && !code.contains("REQUIRED_FIELDS") && !code.contains("required=False"));
        assertEquals("1.4.0", Json.parseObject(before.result().files().get("manifest.json")).get("version"));
        assertNotEquals(before.gate().record().specRevision(), explicit.gate().record().specRevision(), "a decisão explícita é registrada (outra revisão)");
    }

    @Test
    @DisplayName("G2/G4: mudar a obrigatoriedade muda a especificação → o código gerado antes fica DESATUALIZADO (não vale como validado)")
    void mudancaInvalidaCodigo() throws Exception {
        Flow before = develop(FichaCase.confirmed(), (s, ctx) -> s);
        Path dir = tmp.resolve("antes");
        OutputWriter.write(dir, before.result().files());
        assertTrue(AutomationQualityGate.codeState(dir, before.c().a, before.gate()).current());
        FichaCase c = before.c();
        ProjectContext ctx = ProjectContext.of(c.a, Optional.of(c.reviewedDraft()));
        FunctionalSpec changed = before.spec().with(requirement(ctx, c.died, "OPTIONAL:ABSENT_FOR_SOME_ITEMS"));
        PddGate.Result gate = PddGate.evaluate(ctx, Optional.of(c.reviewedDraft()), changed, ProcessProjection.of(c.a), NOW);
        AutomationQualityGate.CodeState state = AutomationQualityGate.codeState(dir, c.a, gate);
        assertFalse(state.current(), "código anterior não vale para a regra nova");
        assertEquals("PDD ou especificação mudaram", state.reason());
        assertFalse(before.gate().record().currentFor(c.a.revision(), gate.record().processRevision(), gate.record().documentRevision(), gate.record().specRevision()),
                "o gate anterior também não vale");
    }

    @Test
    @DisplayName("G2: o apêndice \"Dados do processo\" do PDD diz qual coluna é opcional e por quê; sem decisão, o texto de antes")
    void apendicePdd() {
        Flow f = deathOptional();
        List<String> lines = DataFlowDocument.lines(f.c().a, Optional.of(f.c().reviewedDraft()), f.spec());
        String morte = lines.stream().filter(l -> l.contains(". Morte ←")).findFirst().orElseThrow();
        assertTrue(morte.contains("OPCIONAL (o dado não existe em alguns itens): sem o dado, a célula fica vazia"), morte);
        assertTrue(lines.stream().noneMatch(l -> l.contains(". Nascimento ←") && l.contains("OPCIONAL")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("coluna opcional sem dado fica vazia (nunca recebe outro valor)")), lines.toString());
        Flow legacy = develop(FichaCase.confirmed(), (s, ctx) -> s);
        List<String> old = DataFlowDocument.lines(legacy.c().a, Optional.of(legacy.c().reviewedDraft()), legacy.spec());
        assertTrue(old.contains("Nada é presumido: dado obrigatório vazio ou data incompleta rejeita o item, e a planilha não é gravada."), old.toString());
        assertTrue(old.stream().noneMatch(l -> l.contains("OPCIONAL")));
    }

    @Test
    @DisplayName("G4: configuração recusa coluna opcional sem motivo, chave opcional e 1.3.0 sem coluna opcional")
    void configuracaoEstrita() {
        String cfg = deathOptional().plan().singleConfig().orElseThrow().toJson();
        SingleConfig.parse(cfg);
        assertEquals("CONFIG_OUTPUT_REQUIREMENT", assertThrows(CodegenConfig.Invalid.class,
                () -> SingleConfig.parse(cfg.replace("\"requirementReason\": \"ABSENT_FOR_SOME_ITEMS\"", "\"requirementReason\": null"))).code());
        assertEquals("CONFIG_OUTPUT_REQUIREMENT", assertThrows(CodegenConfig.Invalid.class,
                () -> SingleConfig.parse(cfg.replace("\"requirementReason\": \"ABSENT_FOR_SOME_ITEMS\"", "\"requirementReason\": \"porque sim\""))).code());
        String keyOptional = cfg.replaceFirst("\"required\": true,\n(\\s*)\"requirementReason\": null", "\"required\": false,\n$1\"requirementReason\": \"ABSENT_FOR_SOME_ITEMS\"");
        assertEquals("CONFIG_OUTPUT_KEY_OPTIONAL", assertThrows(CodegenConfig.Invalid.class, () -> SingleConfig.parse(keyOptional)).code(), "a 1ª coluna é a URL (chave)");
        String allRequired = cfg.replace("\"required\": false,", "\"required\": true,").replace("\"requirementReason\": \"ABSENT_FOR_SOME_ITEMS\"", "\"requirementReason\": null");
        assertEquals("CONFIG_VERSION", assertThrows(CodegenConfig.Invalid.class, () -> SingleConfig.parse(allRequired)).code());
    }

    // ------------------------------------------------------------------------------------------- G5: Python real
    @Test
    @DisplayName("[Python real] matriz: Morte ausente/em branco → célula vazia; presente → data; inválida → recusa; ambígua → erro; Nascimento ausente/em branco → recusa")
    void matrizPythonReal() throws Exception {
        Path py = PythonForTests.framework();
        Assumptions.assumeTrue(py != null, "sem o interpretador do framework neste ambiente");
        Path project = tmp.resolve("opcional");
        OutputWriter.write(project, deathOptional().result().files());
        String ana = FichaCase.ORIGIN + "/leitura/ficha-ana.html";
        Map<String, Object> absent = run(py, project, scenario(ana, Map.of(), List.of(7), List.of()), "ausente");
        assertEquals("SUCCEEDED", absent.get("status"), absent.toString());
        Map<?, ?> row = (Map<?, ?>) absent.get("row");
        assertTrue(row.containsKey("Morte") && row.get("Morte") == Json.NULL, "a chave da coluna fica, sem valor: " + row);
        assertEquals("1839-06-21", row.get("Nascimento"));
        Map<?, ?> blank = (Map<?, ?>) run(py, project, scenario(ana, Map.of("7", List.of("   ")), List.of(), List.of()), "branco").get("row");
        assertTrue(blank != null && blank.get("Morte") == Json.NULL, "só espaços = ausência: " + blank);
        Map<?, ?> present = (Map<?, ?>) run(py, project, scenario(ana, Map.of(), List.of(), List.of()), "presente").get("row");
        assertEquals("1908-09-29", present.get("Morte"));
        Map<String, Object> invalid = run(py, project, scenario(ana, Map.of("7", List.of("setembro de 1908")), List.of(), List.of()), "invalida");
        assertEquals("DATA_INCOMPLETA_OU_AMBIGUA", invalid.get("reason"), "data presente e inválida nunca vira ausência");
        Map<String, Object> ambiguous = run(py, project, scenario(ana, Map.of(), List.of(), List.of(7)), "ambigua");
        assertEquals("TECHNICAL", ambiguous.get("status"));
        assertEquals("TARGET_AMBIGUOUS", ambiguous.get("detail"), "ambíguo é erro, nunca \"não existe\"");
        Map<String, Object> bornBlank = run(py, project, scenario(ana, Map.of("6", List.of("  ")), List.of(), List.of()), "nascimento-branco");
        assertEquals("DATA_INCOMPLETA_OU_AMBIGUA", bornBlank.get("reason"), "obrigatória continua exigida");
        Map<String, Object> bornAbsent = run(py, project, scenario(ana, Map.of(), List.of(6), List.of()), "nascimento-ausente");
        assertEquals("TARGET_NOT_FOUND", bornAbsent.get("detail"), "obrigatória ausente: falha classificada");
        Map<String, Object> nationality = run(py, project, scenario(ana, Map.of("8", List.of(" ")), List.of(), List.of()), "nacionalidade-branco");
        assertEquals("DADO_OBRIGATORIO_VAZIO:nationality", nationality.get("reason"));
        assertFalse(Json.write(nationality).contains("brasileira"), "o conteúdo nunca vai ao motivo");
        // legado: sem decisão, Morte ausente continua recusando (falha classificada da leitura obrigatória)
        Path legacy = tmp.resolve("legado");
        OutputWriter.write(legacy, develop(FichaCase.confirmed(), (s, ctx) -> s).result().files());
        assertEquals("TARGET_NOT_FOUND", run(py, legacy, scenario(ana, Map.of(), List.of(7), List.of()), "legado").get("detail"));
    }

    @Test
    @DisplayName("[Python real] V5 tão estrita quanto o gerador: obrigatoriedade fora do vocabulário, opcional sem motivo e chave divergente do manifesto são recusados")
    void contratoV5RecusaManifestoForaDoVocabulario() throws Exception {
        Path py = PythonForTests.framework();
        Assumptions.assumeTrue(py != null, "sem o interpretador do framework neste ambiente");
        Path original = tmp.resolve("v5-original");
        OutputWriter.write(original, deathOptional().result().files());
        assertEquals(PythonChecks.Status.PASS, contract(py, original).status(), "controle: o projeto gerado passa");
        Map<String, String> mutants = new LinkedHashMap<>();
        mutants.put("requisito-desconhecido", "\"requirement\": \"QUALQUER_COISA\"");
        mutants.put("opcional-sem-motivo", "\"requirementReason\": null");
        mutants.put("chave-divergente", "\"keyField\": \"full_name\"");
        for (Map.Entry<String, String> e : mutants.entrySet()) {
            Path m = tmp.resolve("v5-" + e.getKey());
            OutputWriter.write(m, deathOptional().result().files());
            Path manifest = m.resolve("manifest.json");
            String text = Files.readString(manifest, StandardCharsets.UTF_8);
            String changed = switch (e.getKey()) {
                case "requisito-desconhecido" -> text.replaceFirst("\"requirement\"\\s*:\\s*\"OPTIONAL\"", e.getValue());
                case "opcional-sem-motivo" -> text.replaceFirst("\"requirementReason\"\\s*:\\s*\"[A-Z_]+\"", e.getValue());
                default -> text.replaceFirst("\"keyField\"\\s*:\\s*\"url\"", e.getValue());
            };
            assertNotEquals(text, changed, "mutante aplicado: " + e.getKey());
            Files.writeString(manifest, changed, StandardCharsets.UTF_8);
            PythonChecks.Check c = contract(py, m);
            assertTrue(c.codes().containsKey("OUTPUT_REQUIREMENT_MISMATCH"), e.getKey() + " deveria ser recusado: " + c.status() + " " + c.codes());
        }
    }

    private static PythonChecks.Check contract(Path py, Path project) throws Exception {
        return PythonChecks.run(PythonChecks.resolve(Optional.of(py), project).orElseThrow(), project, () -> false).stream()
                .filter(c -> c.id().equals("CONTRACT_TESTS")).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("[Python real] XLSX pelo framework: célula Morte verdadeiramente vazia com o cabeçalho; data gravada; obrigatória vazia recusada; V4/V5/VQ PASS")
    void planilhaReal() throws Exception {
        Path py = PythonForTests.framework();
        Assumptions.assumeTrue(py != null, "sem o interpretador do framework neste ambiente");
        Path project = tmp.resolve("xlsx");
        OutputWriter.write(project, deathOptional().result().files());
        Path out = tmp.resolve("saida.xlsx");
        String probe = String.join("\n",
                "import json, sys",
                "sys.path.insert(0, sys.argv[1])",
                "import main",
                "import openpyxl",
                "from nekkora_rpa.xlsx import XlsxOutputError, prepare_row, write_xlsx",
                "base = {'url': 'http://127.0.0.1:18766/leitura/a', 'full_name': 'A', 'birth_date': '1839-06-21', 'nationality': 'x', 'occupation': 'y'}",
                "rows = [dict(base, death_date=None), dict(base, url='http://127.0.0.1:18766/leitura/b', death_date='1908-09-29')]",
                "write_xlsx(sys.argv[2], main.SPREADSHEET, rows)",
                "ws = openpyxl.load_workbook(sys.argv[2]).active",
                "cells = [[c.value for c in r] for r in ws.iter_rows()]",
                "codes = []",
                "for bad in (dict(base, death_date=None, birth_date=None), dict(base, death_date='29/09/1908'), dict(base, death_date='N/A')):",
                "    try:",
                "        prepare_row(main.SPREADSHEET, bad)",
                "        codes.append('')",
                "    except XlsxOutputError as err:",
                "        codes.append(err.code)",
                "print(json.dumps({'headers': cells[0], 'empty': cells[1][3], 'date': cells[2][3], 'width': [len(r) for r in cells], 'codes': codes}))");
        Path script = tmp.resolve("probe.py");
        Files.writeString(script, probe, StandardCharsets.UTF_8);
        String got = last(python(py, List.of(script.toString(), project.toString(), out.toString())));
        Map<String, Object> r = Json.parseObject(got);
        assertEquals(List.of("URL", "Nome completo", "Nascimento", "Morte", "Nacionalidade", "Ocupação"), r.get("headers"), "cabeçalho igual, coluna não removida");
        assertEquals(Json.NULL, r.get("empty"), "célula verdadeiramente vazia (nem \"\", nem N/A): " + got);
        assertEquals("1908-09-29", r.get("date"));
        assertEquals(List.of(6L, 6L, 6L), r.get("width"), "nenhuma célula extra");
        assertEquals(List.of("missing_birth_date", "date_invalid_death_date", "date_invalid_death_date"), r.get("codes"));
        // V4 (compilação, importação, ruff, formatação, mypy) + V5 (contrato, com a verificação de obrigatoriedade) + VQ
        PythonChecks.Environment env = PythonChecks.resolve(Optional.of(py), project).orElseThrow();
        Map<String, PythonChecks.Check> by = new LinkedHashMap<>();
        for (PythonChecks.Check c : PythonChecks.run(env, project, () -> false)) by.put(c.id(), c);
        for (PythonChecks.Check c : by.values()) assertEquals(PythonChecks.Status.PASS, c.status(), c.id() + " " + c.codes() + " " + c.reason());
        // controle negativo: o manifesto diz opcional, o main.py diz obrigatória → contrato falha
        Path mutant = tmp.resolve("mutante");
        OutputWriter.write(mutant, deathOptional().result().files());
        Path main = mutant.resolve("main.py");
        Files.writeString(main, Files.readString(main, StandardCharsets.UTF_8).replace(", required=False", ""), StandardCharsets.UTF_8);
        PythonChecks.Check contract = PythonChecks.run(PythonChecks.resolve(Optional.of(py), mutant).orElseThrow(), mutant, () -> false).stream()
                .filter(c -> c.id().equals("CONTRACT_TESTS")).findFirst().orElseThrow();
        assertTrue(contract.codes().containsKey("OUTPUT_REQUIREMENT_MISMATCH") || contract.codes().containsKey("MANIFEST_HASH_MISMATCH"), contract.codes().toString());
        assertTrue(contract.codes().containsKey("OUTPUT_REQUIREMENT_MISMATCH"), "a divergência é apontada pelo nome: " + contract.codes());
    }

    @Test
    @DisplayName("[Python real + Chromium + laboratório] pessoa viva → célula Morte vazia; falecida → data; legado recusa; sem Nascimento, Morte inválida ou dupla → nada gravado")
    void laboratorioReal() throws Exception {
        Path py = PythonForTests.framework();
        Assumptions.assumeTrue(py != null, "sem o interpretador do framework neste ambiente");
        Path code = tmp.resolve("opcional-python");
        OutputWriter.write(code, deathOptional().result().files());
        Path legacy = tmp.resolve("legado-python");
        OutputWriter.write(legacy, develop(FichaCase.confirmed(), (s, ctx) -> s).result().files());
        // cópia TEMPORÁRIA do laboratório genérico (o do framework não muda) com três fichas adversas, dados fictícios
        Path site = tmp.resolve("site");
        Path src = PythonForTests.frameworkRepo().resolve("lab").resolve("static");
        try (var s = Files.walk(src)) {
            for (Path p : s.toList()) {
                Path to = site.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(to);
                else Files.copy(p, to);
            }
        }
        Path dir = site.resolve("leitura");
        Path js = dir.resolve("busca.js");
        Files.writeString(js, Files.readString(js, StandardCharsets.UTF_8).replace("'gina inventada': 'ficha-gina.html'",
                "'gina inventada': 'ficha-gina.html', 'sem nascimento': 'ficha-sem-nascimento.html', 'morte invalida': 'ficha-morte-invalida.html', "
                        + "'morte dupla': 'ficha-morte-dupla.html'"), StandardCharsets.UTF_8);
        String gina = Files.readString(dir.resolve("ficha-gina.html"), StandardCharsets.UTF_8);
        String born = row(gina, "Nascimento");
        String death = row(gina, "Morte");
        Files.writeString(dir.resolve("ficha-sem-nascimento.html"), gina.replace(born, ""), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("ficha-morte-invalida.html"), gina.replace("4 de julho de 1934", "julho de 1934"), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("ficha-morte-dupla.html"), gina.replace(death, death + death), StandardCharsets.UTF_8);
        // um servidor por execução (o http.server do Python pode ficar preso entre execuções seguidas do Chromium)
        this.site = site;
        String base = FichaCase.ORIGIN + "/leitura/";
        // 1) Morte opcional: Ana (falecida), Bruno (vivo: sem a linha "Morte"), Gina (falecida)
        Path ok = execute(py, code, "ok", List.of("Ana Exemplo," + base + "ficha-ana.html", "Bruno Modelo," + base + "ficha-bruno.html",
                "Gina Inventada," + base + "ficha-gina.html"));
        assertTrue(Files.exists(ok), "planilha gravada");
        // cópia para leitura humana (dados FICTÍCIOS do laboratório)
        Files.createDirectories(Path.of("target", "codegen-samples"));
        Files.copy(ok, Path.of("target", "codegen-samples", "optional-columns-lab.xlsx"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        List<List<Object>> cells = cells(py, ok);
        assertEquals(List.of("URL", "Nome completo", "Nascimento", "Morte", "Nacionalidade", "Ocupação"), cells.get(0), "cabeçalho intacto");
        assertEquals(4, cells.size(), "uma linha por pessoa");
        assertTrue(cells.stream().allMatch(r -> r.size() == 6), "nenhuma célula extra");
        Map<String, List<Object>> byUrl = new LinkedHashMap<>();
        for (List<Object> r : cells.subList(1, 4)) byUrl.put(String.valueOf(r.get(0)), r);
        assertEquals(Json.NULL, byUrl.get(base + "ficha-bruno.html").get(3), "pessoa viva: célula Morte VAZIA (nem \"\", nem N/A)");
        assertEquals("1990-05-01", byUrl.get(base + "ficha-bruno.html").get(2), "Nascimento continua exigido e gravado");
        assertEquals("Bruno Modelo", byUrl.get(base + "ficha-bruno.html").get(1), "fonte alternativa (título) continua valendo");
        assertEquals("1908-09-29", byUrl.get(base + "ficha-ana.html").get(3));
        assertEquals("1934-07-04", byUrl.get(base + "ficha-gina.html").get(3));
        for (List<Object> r : cells.subList(1, 4)) for (int i : List.of(0, 1, 2, 4, 5)) assertNotEquals(Json.NULL, r.get(i), "obrigatória preenchida: " + i);
        // 2) legado (sem decisão): a mesma lista é recusada na pessoa viva e nada é gravado
        Path old = execute(py, legacy, "legado", List.of("Bruno Modelo," + base + "ficha-bruno.html"));
        assertFalse(Files.exists(old), "legado continua exigindo Morte (tudo ou nada)");
        assertTrue(events("legado").contains("\"step_id\":\"profile.read_death_date\""), "a falha é da leitura de Morte");
        // 3) negativos com a Morte opcional: tudo ou nada → nenhuma planilha
        Path noBirth = execute(py, code, "sem-nascimento", List.of("Ana Exemplo," + base + "ficha-ana.html", "Sem Nascimento," + base + "ficha-sem-nascimento.html"));
        assertFalse(Files.exists(noBirth), "Nascimento ausente recusa o item; tudo ou nada: nenhuma planilha parcial");
        assertTrue(events("sem-nascimento").contains("\"step_id\":\"profile.read_birth_date\""));
        Path invalid = execute(py, code, "morte-invalida", List.of("Morte Invalida," + base + "ficha-morte-invalida.html"));
        assertFalse(Files.exists(invalid));
        assertTrue(events("morte-invalida").contains("DATA_INCOMPLETA_OU_AMBIGUA"), "data presente e incompleta nunca vira célula vazia: " + events("morte-invalida"));
        Path twice = execute(py, code, "morte-dupla", List.of("Morte Dupla," + base + "ficha-morte-dupla.html"));
        assertFalse(Files.exists(twice));
        assertTrue(events("morte-dupla").contains("\"error_code\":\"TARGET_AMBIGUOUS\"") && events("morte-dupla").contains("\"step_id\":\"profile.read_death_date\""),
                "duas linhas Morte: ambíguo, nunca \"não existe\"");
        for (String run : List.of("ok", "legado", "sem-nascimento", "morte-invalida", "morte-dupla")) {
            String ev = events(run);
            for (String value : List.of("Georgina", "Ana Maria", "ucraniana", "1934", "1908", "1990")) assertFalse(ev.contains(value), run + ": valor nos eventos");
        }
    }

    private static String row(String html, String header) {
        int th = html.indexOf(">" + header + "</th>");
        int start = html.lastIndexOf("<tr", th);
        int end = html.indexOf("</tr>", th) + "</tr>".length();
        return html.substring(start, end);
    }

    private Path site;

    private Path execute(Path py, Path project, String name, List<String> rows) throws Exception {
        Path lista = tmp.resolve(name + ".csv");
        Files.writeString(lista, "termo_de_busca,endereco_esperado\n" + String.join("\n", rows) + "\n", StandardCharsets.UTF_8);
        Path xlsx = tmp.resolve(name + ".xlsx");
        Process lab = new ProcessBuilder(py.toString(), "-m", "http.server", "18766", "--bind", "127.0.0.1", "--directory", site.toString())
                .redirectErrorStream(true).start();
        try {
            Thread.sleep(1500);
            python(py, List.of(project.resolve("main.py").toString(), "--input", lista.toString(), "--spreadsheet", xlsx.toString(), "--output",
                    tmp.resolve("exec-" + name).toString(), "--channel", "chromium", "--headless"));
        } finally {
            lab.descendants().forEach(ProcessHandle::destroyForcibly);
            lab.destroyForcibly();
            lab.waitFor();
        }
        return xlsx;
    }

    private String events(String name) throws Exception {
        return Files.readString(tmp.resolve("exec-" + name).resolve("execucao").resolve("events.jsonl"), StandardCharsets.UTF_8);
    }

    /** Conferidor independente: reabre o .xlsx com openpyxl (sem nekkora_rpa) e devolve os valores das células. */
    @SuppressWarnings("unchecked")
    private List<List<Object>> cells(Path py, Path xlsx) throws Exception {
        Path script = tmp.resolve("cells.py");
        Files.writeString(script, "import json, sys\nimport openpyxl\nws = openpyxl.load_workbook(sys.argv[1]).active\n"
                + "print(json.dumps([[c.value for c in r] for r in ws.iter_rows()], ensure_ascii=False))\n", StandardCharsets.UTF_8);
        return (List<List<Object>>) Json.parse(last(python(py, List.of(script.toString(), xlsx.toString()))));
    }

    // ------------------------------------------------------------------------------------------- utilidades
    private static Map<String, Object> scenario(String address, Map<String, List<String>> override, List<Integer> absent, List<Integer> ambiguous) {
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
        s.put("input", Map.of("termo_de_busca", "Ana Exemplo", "endereco_esperado", address));
        s.put("address", address);
        s.put("navigated", false);
        s.put("values", values);
        s.put("absent", new ArrayList<>(absent));
        s.put("ambiguous", new ArrayList<>(ambiguous));
        return s;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> run(Path py, Path project, Map<String, Object> scenario, String name) throws Exception {
        Path sc = tmp.resolve(name + ".json");
        Files.writeString(sc, Json.write(scenario), StandardCharsets.UTF_8);
        Path runner = tmp.resolve("equivalencia.py");
        if (!Files.exists(runner)) {
            try (var in = OptionalColumnsTest.class.getResourceAsStream("/nekkora/ai/equivalencia.py")) {
                Files.write(runner, in.readAllBytes());
            }
        }
        String out = python(py, List.of(runner.toString(), project.toString(), sc.toString()));
        return (Map<String, Object>) Json.parseObject(last(out)).get("outcome");
    }

    private static String python(Path py, List<String> args) throws Exception {
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

    private static String noIds(String s) {
        return s.replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<id>");
    }

    private static String last(String s) {
        String[] lines = s.strip().split("\\R");
        return lines[lines.length - 1];
    }
}
