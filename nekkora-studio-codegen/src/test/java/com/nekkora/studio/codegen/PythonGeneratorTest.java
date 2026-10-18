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
        assertEquals(Set.of("README.md", "config/execucao.toml", "main.py", "manifest.json", "paginas.py", "processo.py", "requirements.txt", "selectors.json"),
                r.files().keySet());
        assertEquals(r.files(), gen(c).files(), "mesma entrada → mesmos bytes");
        Map<String, Object> sel = Json.parseObject(r.files().get("selectors.json"));
        assertEquals("1.1.0", sel.get("version"));
        String selectors = r.files().get("selectors.json");
        assertTrue(selectors.contains("\"strategy\": \"label\"") && selectors.contains("\"text\": \"First Name\""), selectors);
        assertFalse(selectors.contains("nth-of-type"), "nunca gera localizador posicional");
        assertTrue(selectors.contains("\"label\": \"Email\""), "rótulo é a identidade quando o nome acessível é vazio");
        String pages = r.files().get("paginas.py");
        assertTrue(pages.contains("a.download("), pages);
        assertTrue(pages.contains("postcondition=lambda: a.replaced(marca)"), "envio só conclui com prova de avanço");
        for (var id : c.ids) assertTrue(pages.contains(id.value().toString()) || id.equals(c.download), "StepId rastreável " + id);
        Map<String, Object> m = Json.parseObject(r.files().get("manifest.json"));
        assertEquals(r.planRevision(), m.get("planRevision"));
        assertEquals(c.ids.size(), Json.array(m, "steps").size());
        assertEquals(7, Json.object(m, "files").size());
        assertTrue(r.files().get("main.py").contains("Column(\"First Name\")"));
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
        assertEquals(OutputWriter.Status.UNCHANGED, second.files().get("paginas.py"), second.toString());
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

    @SuppressWarnings("unused")
    private static StepDraft unused(StepEdit e) {
        return null;
    }
}
