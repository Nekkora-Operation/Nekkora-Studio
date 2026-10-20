package com.nekkora.studio.ai.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PromptRepositoryTest {

    @Test
    @DisplayName("o jar leva o Prompt-Mestre v0.3 do diretório Nekkora-AI-Engine, com o hash conferido")
    void vigente() throws Exception {
        PromptRepository.Prompt p = PromptRepository.bundled().current();
        assertEquals("v003", p.id());
        assertEquals("0.3", p.version());
        assertTrue(p.text().startsWith("# NEKKORA — PROMPT-MESTRE DE DESENVOLVIMENTO RPA"));
        // o recurso é o MESMO arquivo do diretório (lugar único), não uma cópia editada
        String onDisk = Files.readString(Path.of("NEKKORA-PROMPT-MESTRE-DESENVOLVIMENTO-RPA-v003.md"), StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertEquals(onDisk, p.text());
        assertTrue(p.label().matches("v003 \\(0\\.3, sha256:[0-9a-f]{64}\\)"));
    }

    @Test
    @DisplayName("arquivo adulterado, índice sem o arquivo, nome fora do padrão e índice inválido são recusados com código")
    void recusas() throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        files.put("prompts.json", Files.readAllBytes(Path.of("prompts.json")));
        files.put("NEKKORA-PROMPT-MESTRE-DESENVOLVIMENTO-RPA-v003.md", "# texto trocado\n".getBytes(StandardCharsets.UTF_8));
        PromptRepository.Loader loader = n -> Optional.ofNullable(files.get(n));
        assertEquals("PROMPT_HASH_MISMATCH", assertThrows(PromptRepository.PromptUnavailable.class, () -> PromptRepository.of(loader).current()).code());
        files.remove("NEKKORA-PROMPT-MESTRE-DESENVOLVIMENTO-RPA-v003.md");
        assertEquals("PROMPT_FILE_MISSING", assertThrows(PromptRepository.PromptUnavailable.class, () -> PromptRepository.of(loader).current()).code());
        files.put("prompts.json", new String(files.get("prompts.json"), StandardCharsets.UTF_8).replace("NEKKORA-PROMPT-MESTRE-DESENVOLVIMENTO-RPA-v003.md",
                "../segredo.md").getBytes(StandardCharsets.UTF_8));
        assertEquals("PROMPT_INDEX_INVALID", assertThrows(PromptRepository.PromptUnavailable.class, () -> PromptRepository.of(loader)).code());
        files.put("prompts.json", "{".getBytes(StandardCharsets.UTF_8));
        assertEquals("PROMPT_INDEX_INVALID", assertThrows(PromptRepository.PromptUnavailable.class, () -> PromptRepository.of(loader)).code());
        files.remove("prompts.json");
        assertEquals("PROMPT_INDEX_MISSING", assertThrows(PromptRepository.PromptUnavailable.class, () -> PromptRepository.of(loader)).code());
    }

    @Test
    @DisplayName("quebras de linha CRLF (checkout no Windows) dão o mesmo hash")
    void crlf() throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        files.put("prompts.json", Files.readAllBytes(Path.of("prompts.json")));
        String lf = Files.readString(Path.of("NEKKORA-PROMPT-MESTRE-DESENVOLVIMENTO-RPA-v003.md"), StandardCharsets.UTF_8).replace("\r\n", "\n");
        files.put("NEKKORA-PROMPT-MESTRE-DESENVOLVIMENTO-RPA-v003.md", lf.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8));
        assertEquals("v003", PromptRepository.of(n -> Optional.ofNullable(files.get(n))).current().id());
    }
}
