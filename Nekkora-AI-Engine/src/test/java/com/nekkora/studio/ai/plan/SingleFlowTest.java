package com.nekkora.studio.ai.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.ai.ConsultaCase;
import com.nekkora.studio.ai.PythonForTests;
import com.nekkora.studio.ai.capability.FrameworkCapabilityCatalog;
import com.nekkora.studio.ai.context.ProjectContext;
import com.nekkora.studio.ai.gate.PddGate;
import com.nekkora.studio.ai.pycheck.PythonChecks;
import com.nekkora.studio.ai.spec.FunctionalSpec;
import com.nekkora.studio.codegen.OutputWriter;
import com.nekkora.studio.codegen.PythonGenerator;
import com.nekkora.studio.codegen.SingleGenerator;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.pdd.DocumentDraft;
import com.nekkora.studio.pdd.ProcessProjection;
import com.nekkora.studio.validation.AutomationValidator;
import com.nekkora.studio.validation.RuleSet;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SingleFlowTest {
    @TempDir
    Path tmp;

    record Flow(ConsultaCase c, DevelopmentPlan plan, PythonGenerator.Result result) {}

    static Flow develop(ConsultaCase c) {
        DocumentDraft d = c.reviewedDraft();
        ProjectContext ctx = ProjectContext.of(c.a, Optional.of(d));
        VariableDeriver.apply(c.a, VariableDeriver.derive(c.a, ctx)); // a operação desfazível do Studio faz isto
        ctx = ProjectContext.of(c.a, Optional.of(d));
        PddGate.Result gate = PddGate.evaluate(ctx, Optional.of(d), FunctionalSpec.empty(c.a.id()), ProcessProjection.of(c.a), ConsultaCase.CLOCK.instant());
        assertTrue(gate.ready(), gate.issues().toString());
        DevelopmentPlan plan = DevelopmentPlanner.plan(c.a, ctx, gate, FrameworkCapabilityCatalog.bundled(), Optional.empty());
        assertTrue(plan.ready(), plan.issues().toString());
        PythonGenerator.Result r = SingleGenerator.generate(c.a, new AutomationValidator(RuleSet.V1_1).validate(c.a), plan.singleConfig().orElseThrow());
        return new Flow(c, plan, r);
    }

    @Test
    @DisplayName("variáveis derivadas: senha e usuário viram credenciais; código vira entrada; o resultado vira saída; nada de constante")
    void variaveis() {
        ConsultaCase c = ConsultaCase.confirmed();
        ProjectContext ctx = ProjectContext.of(c.a, Optional.empty());
        List<VariableDeriver.Planned> plan = VariableDeriver.derive(c.a, ctx);
        Map<String, VariableDeriver.Planned> byName = new java.util.LinkedHashMap<>();
        plan.forEach(p -> byName.put(p.name(), p));
        assertEquals(VariableDeriver.Provenance.DERIVED_SENSITIVE, byName.get("credencial.senha").provenance());
        assertEquals(VariableDeriver.Provenance.DERIVED_LOGIN_PAIR, byName.get("credencial.usuario").provenance());
        assertEquals("INPUT", byName.get("codigo_do_registro").origin());
        assertEquals("STEP_OUTPUT", byName.get("resultado_da_consulta").origin());
        int changed = VariableDeriver.apply(c.a, plan);
        assertEquals(4, changed);
        assertTrue(c.a.step(c.password).orElseThrow().parameters().value().orElseThrow() instanceof ValueSource.CredentialReference);
        assertEquals(VariableOrigin.Kind.STEP_OUTPUT, c.a.variableNamed("resultado_da_consulta").orElseThrow().origin().kind());
        assertTrue(c.a.variables().stream().noneMatch(v -> v.origin().kind() == VariableOrigin.Kind.CONSTANT));
        // idempotente: derivar de novo só reaproveita
        List<VariableDeriver.Planned> again = VariableDeriver.derive(c.a, ProjectContext.of(c.a, Optional.empty()));
        assertTrue(again.stream().noneMatch(VariableDeriver.Planned::changesModel), again.toString());
    }

    @Test
    @DisplayName("plano: Pages por tela, sem planilha, capacidades reais do catálogo; geração oficial determinística sem valores")
    void plano() {
        Flow f = develop(ConsultaCase.confirmed());
        DevelopmentPlan p = f.plan();
        assertEquals(DevelopmentPlan.ExecutionModel.SINGLE, p.model());
        assertEquals(List.of("TelaAcesso", "TelaConsulta"), p.pages().stream().map(DevelopmentPlan.PlannedPage::className).toList());
        assertTrue(p.capabilities().containsAll(List.of("actions.fill", "actions.click", "actions.read_text", "core.single_input", "security.env_secrets")));
        assertFalse(p.capabilities().contains("tabular.input"), "nada de planilha");
        assertTrue(f.result().ok(), f.result().blockers().toString());
        Map<String, String> files = f.result().files();
        assertTrue(files.keySet().containsAll(List.of("paginas.py", "processo.py", "main.py", "selectors.json", "manifest.json", "config/execucao.toml")));
        String all = files.get("paginas.py") + files.get("processo.py") + files.get("main.py");
        assertFalse(all.contains("perRecord") || all.contains("TabularSchema") || all.contains("xlsx"), "sem lote fabricado");
        assertFalse(all.contains("#senha"), "nenhum seletor escrito à mão fora do registro");
        assertTrue(files.get("processo.py").contains("SingleInputProvider") || files.get("main.py").contains("SingleInputProvider"));
        assertTrue(files.get("manifest.json").contains("\"gate\": \"PDD_READY\""));
        // determinismo
        Flow g = develop(ConsultaCase.confirmed());
        assertEquals(f.result().files().keySet(), g.result().files().keySet());
        assertEquals(f.result().files().get("paginas.py").replaceAll("[0-9a-f]{8}-[0-9a-f-]{27}", "ID"),
                g.result().files().get("paginas.py").replaceAll("[0-9a-f]{8}-[0-9a-f-]{27}", "ID"));
        PythonGenerator.Result again = SingleGenerator.generate(f.c().a, new AutomationValidator(RuleSet.V1_1).validate(f.c().a), p.singleConfig().orElseThrow());
        assertEquals(f.result().files(), again.files(), "mesma entrada → mesmos bytes");
    }

    @Test
    @DisplayName("[Python real] o projeto gerado compila, importa, passa no ruff (perfil do framework), no mypy estrito e nos testes de contrato")
    void pythonReal() throws Exception {
        Path py = PythonForTests.framework();
        Assumptions.assumeTrue(py != null, "sem o interpretador do framework neste ambiente");
        Flow f = develop(ConsultaCase.confirmed());
        String keep = System.getenv("NEKKORA_TEST_KEEP_OUTPUT"); // inspeção manual do código gerado (opcional)
        Path out = keep == null || keep.isBlank() ? tmp.resolve("consulta-python") : Path.of(keep);
        OutputWriter.write(out, f.result().files());
        PythonChecks.Environment env = PythonChecks.resolve(Optional.of(py), out).orElseThrow();
        assertEquals("0.3.0", env.framework()); // o código 1.1.0 continua válido no runtime 0.3.0 (aditivo)
        List<PythonChecks.Check> checks = PythonChecks.run(env, out, () -> false);
        for (PythonChecks.Check ch : checks) {
            assertEquals(PythonChecks.Status.PASS, ch.status(), ch.id() + " " + ch.codes() + " " + ch.reason() + "\n" + (ch.id().startsWith("RUFF") || ch.id().startsWith("MYPY")
                    ? dump(out) : ""));
        }
        assertEquals(6, checks.size());
        assertNotNull(checks.stream().filter(x -> x.id().equals("CONTRACT_TESTS")).findFirst().orElseThrow().codes().get("TOTAL"));
    }

    @Test
    @DisplayName("[Python real] controle negativo: projeto estragado (import sobrando, erro de tipo, valor escrito no preenchimento) é REPROVADO")
    void pythonRealNegativo() throws Exception {
        Path py = PythonForTests.framework();
        Assumptions.assumeTrue(py != null, "sem o interpretador do framework neste ambiente");
        Flow f = develop(ConsultaCase.confirmed());
        Path out = tmp.resolve("estragado");
        OutputWriter.write(out, f.result().files());
        Path pages = out.resolve("paginas.py");
        String src = java.nio.file.Files.readString(pages);
        src = src.replace("from __future__ import annotations\n", "from __future__ import annotations\n\nimport os\n")
                .replace("a.fill(self.k(\"codigo_do_registro\"), codigo_do_registro,", "a.fill(self.k(\"codigo_do_registro\"), \"REG-0001\",")
                .replace("        return saidas\n", "        x: int = \"texto\"\n        return saidas\n");
        java.nio.file.Files.writeString(pages, src);
        PythonChecks.Environment env = PythonChecks.resolve(Optional.of(py), out).orElseThrow();
        Map<String, PythonChecks.Check> by = new java.util.LinkedHashMap<>();
        for (PythonChecks.Check c : PythonChecks.run(env, out, () -> false)) by.put(c.id(), c);
        assertEquals(PythonChecks.Status.FAIL, by.get("RUFF_CHECK").status(), by.get("RUFF_CHECK").toString());
        assertTrue(by.get("RUFF_CHECK").codes().containsKey("F401"), by.get("RUFF_CHECK").codes().toString());
        assertEquals(PythonChecks.Status.FAIL, by.get("MYPY_STRICT").status(), by.get("MYPY_STRICT").toString());
        assertEquals(PythonChecks.Status.FAIL, by.get("CONTRACT_TESTS").status(), "hash do manifesto e valor escrito no preenchimento");
        assertTrue(by.get("CONTRACT_TESTS").codes().containsKey("MANIFEST_HASH_MISMATCH") && by.get("CONTRACT_TESTS").codes().containsKey("FILL_LITERAL_VALUE"),
                by.get("CONTRACT_TESTS").codes().toString());
    }

    static String dump(Path out) {
        try {
            return java.nio.file.Files.readString(out.resolve("paginas.py")) + "\n-----\n" + java.nio.file.Files.readString(out.resolve("processo.py"));
        } catch (java.io.IOException e) {
            return "";
        }
    }
}
