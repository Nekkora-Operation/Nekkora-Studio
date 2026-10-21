package com.nekkora.studio.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * CODEGEN-QUALITY-001 rodada 3: gerar o código em inglês (pacote automation/) numa pasta que tinha o projeto anterior
 * (paginas.py/processo.py) nunca apaga nada — o que não faz mais parte do projeto vai, intacto, para uma pasta de versão;
 * o que foi editado à mão fica onde está.
 */
class OutputWriterRetireTest {
    @TempDir
    Path dir;

    static Map<String, String> project(String version, int revision, Map<String, String> code) {
        Map<String, String> files = new TreeMap<>(code);
        Map<String, Object> hashes = new LinkedHashMap<>();
        code.forEach((f, c) -> hashes.put(f, "sha256:" + CodegenConfig.sha256(c)));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", version);
        m.put("automationRevision", revision);
        m.put("files", hashes);
        files.put("manifest.json", Pretty.json(m) + "\n");
        return files;
    }

    @Test
    @DisplayName("arquivos da geração anterior que saíram do projeto: guardados por versão (com o manifesto anterior); editado à mão: mantido")
    void aposentaPorVersao() throws Exception {
        Map<String, String> before = project("1.3.0", 52, Map.of("paginas.py", "# antes\n", "processo.py", "# processo\n", "main.py", "# main antes\n"));
        OutputWriter.write(dir, before);
        Files.writeString(dir.resolve("processo.py"), "# processo\n# editado à mão\n", StandardCharsets.UTF_8);
        Map<String, String> after = project("1.4.0", 54, Map.of("automation/pages.py", "# pages\n", "main.py", "# main depois\n"));
        OutputWriter.Report r = OutputWriter.write(dir, after);
        assertEquals(OutputWriter.Status.RETIRED, r.files().get("paginas.py"));
        assertEquals(OutputWriter.Status.KEPT, r.files().get("processo.py"), "editado à mão: nada é movido nem sobrescrito");
        assertEquals(OutputWriter.Status.WRITTEN, r.files().get("main.py"));
        assertEquals(OutputWriter.Status.WRITTEN, r.files().get("automation/pages.py"));
        Path version = dir.resolve(OutputWriter.PREVIOUS).resolve("manifest-1.3.0-rev52");
        assertEquals("# antes\n", Files.readString(version.resolve("paginas.py"), StandardCharsets.UTF_8), "a geração anterior continua inteira");
        assertEquals(before.get("manifest.json"), Files.readString(version.resolve("manifest.json"), StandardCharsets.UTF_8));
        assertFalse(Files.exists(dir.resolve("paginas.py")));
        assertEquals("# processo\n# editado à mão\n", Files.readString(dir.resolve("processo.py"), StandardCharsets.UTF_8));
        assertEquals(after.get("manifest.json"), Files.readString(dir.resolve("manifest.json"), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("com arquivo protegido (.novo/.diff), nada é aposentado: o manifesto anterior continua valendo")
    void protegidoNaoAposenta() throws Exception {
        OutputWriter.write(dir, project("1.3.0", 52, Map.of("paginas.py", "# antes\n", "main.py", "# main antes\n")));
        Files.writeString(dir.resolve("main.py"), "# main editado\n", StandardCharsets.UTF_8);
        OutputWriter.Report r = OutputWriter.write(dir, project("1.4.0", 54, Map.of("automation/pages.py", "# pages\n", "main.py", "# main depois\n")));
        assertEquals(OutputWriter.Status.PROTECTED, r.files().get("main.py"));
        assertTrue(Files.exists(dir.resolve("paginas.py")), "nada aposentado enquanto houver pendência");
        assertFalse(Files.exists(dir.resolve(OutputWriter.PREVIOUS)));
    }
}
