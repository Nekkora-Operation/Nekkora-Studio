package com.nekkora.studio.app.develop;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** NEKKORA-CODEGEN-QUALITY-001 — "Revisar código gerado": arquitetura, mapa step_id → etapa, localizadores e o código intacto. */
class CodeReviewTextTest {
    @TempDir
    Path tmp;

    @Test
    @DisplayName("mostra a arquitetura, o mapa step_id → etapa, os localizadores e o código exatamente como está na pasta")
    void revisao() throws Exception {
        Files.writeString(tmp.resolve("manifest.json"), """
                {"version": "1.3.0", "generator": "nekkora-studio-codegen/1.2.0", "framework": "nekkora-rpa[excel]==0.3.0",
                 "architecture": {"pages": [{"class": "PaginaPesquisa", "summary": "Pesquisa do artigo.", "nameSource": "PDD_SECTION_TITLE",
                   "boundary": "", "methods": [{"name": "pesquisar_artigo", "nameSource": "PDD_BLOCK_TITLE", "summary": "Pesquisar o artigo pelo nome.",
                   "parameters": ["termo_de_busca"], "returns": "None", "traceIds": ["pesquisa.preencher_termo_de_busca"]}]}],
                   "models": [], "process": "BiografiasDaWikipedia"},
                 "trace": {"mappings": [{"traceId": "pesquisa.preencher_termo_de_busca", "studioStepId": "afb51a78-579b-4e81-9256-90d974ccb3e0",
                   "action": "FILL", "targetKey": "processo.pesquisa.campo_termo_de_busca", "pythonSymbol": "paginas.py:PaginaPesquisa.pesquisar_artigo"}]},
                 "locators": [{"key": "processo.pesquisa.campo_termo_de_busca", "kind": "SCOPED", "description": "CSS `header input`"}],
                 "files": {"paginas.py": "sha256:abc"}}
                """, StandardCharsets.UTF_8);
        String code = "class PaginaPesquisa(BasePage):\n    \"\"\"Pesquisa do artigo.\"\"\"\n";
        Files.writeString(tmp.resolve("paginas.py"), code, StandardCharsets.UTF_8);
        String text = CodeReviewText.build(tmp);
        assertTrue(text.contains("ARQUITETURA") && text.contains("PaginaPesquisa — Pesquisa do artigo.  [nome: título da seção do PDD]"), text);
        assertTrue(text.contains("pesquisar_artigo(termo_de_busca) -> None  [nome: título do passo no PDD]"), text);
        assertTrue(text.contains("pesquisa.preencher_termo_de_busca → afb51a78-579b-4e81-9256-90d974ccb3e0 (FILL)"), text);
        assertTrue(text.contains("COMO CADA ELEMENTO É LOCALIZADO") && text.contains("SCOPED"), text);
        assertTrue(text.contains(code), "o código aparece sem nenhuma reformatação");
    }

    @Test
    @DisplayName("código do gerador anterior (sem arquitetura no manifesto): orienta gerar de novo")
    void geradorAnterior() throws Exception {
        Files.writeString(tmp.resolve("manifest.json"), "{\"version\": \"1.2.0\", \"generator\": \"x\", \"framework\": \"y\", \"files\": {}}", StandardCharsets.UTF_8);
        assertTrue(CodeReviewText.build(tmp).contains("gerador anterior"));
    }
}
