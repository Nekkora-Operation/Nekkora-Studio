package com.nekkora.studio.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** NEKKORA-CODEGEN-QUALITY-001 — regras de nome (Q01) e de localizador (Q03), puras. */
class CodegenQualityRulesTest {

    @Test
    @DisplayName("Page pelo título da seção do PDD; título genérico não vira nome (sem inventar)")
    void nomeDaTela() {
        assertEquals(Optional.of("pesquisa"), CodeNaming.screenNoun("Pesquisa do artigo"));
        assertEquals(Optional.of("extracao"), CodeNaming.screenNoun("Extração dos dados"));
        assertEquals(Optional.of("transferencia"), CodeNaming.screenNoun("Transferência de dados (Excel)"));
        assertEquals(Optional.empty(), CodeNaming.screenNoun("Etapas do processo"));
        assertEquals(Optional.empty(), CodeNaming.screenNoun("Sessão 2"));
        assertEquals(Optional.empty(), CodeNaming.screenNoun(""));
        assertEquals("PaginaPesquisa", "Pagina" + CodeNaming.pascal("pesquisa"));
    }

    @Test
    @DisplayName("método pelo título do bloco que começa por verbo no infinitivo: verbo + objeto")
    void nomeDoMetodo() {
        assertEquals(Optional.of("pesquisar_artigo"), CodeNaming.methodFromTitle("Pesquisar o artigo pelo nome"));
        assertEquals(Optional.of("ler_endereco"), CodeNaming.methodFromTitle("Ler o endereço e os dados da ficha"));
        assertEquals(Optional.of("abrir_busca"), CodeNaming.methodFromTitle("Abrir a busca do acervo"));
        assertEquals(Optional.empty(), CodeNaming.methodFromTitle("Etapas do processo"), "genérico");
        assertEquals(Optional.empty(), CodeNaming.methodFromTitle("Dados da ficha"), "não começa por verbo");
        assertEquals(Optional.empty(), CodeNaming.methodFromTitle("Lugar da busca"), "substantivo em -ar não é verbo");
    }

    @Test
    @DisplayName("nomes seguros: marcador de plural (D-E2E2-12), ASCII, palavra reservada, membro do BasePage, colisão")
    void nomesSeguros() {
        assertEquals("campos", CodeNaming.snake("Campo(s)", 40));
        assertEquals("autor", CodeNaming.snake("Autor(a)", 40));
        assertEquals("ocupacao", CodeNaming.snake("Ocupação", 40));
        assertEquals("", CodeNaming.snake("123", 40), "sem letra no começo: nada");
        assertEquals("class_", CodeNaming.safe("class"));
        assertEquals("process_", CodeNaming.safe("process"));
        assertEquals("identity_", CodeNaming.safe("identity"));
        List<String> taken = new ArrayList<>(List.of("ler_dados"));
        assertEquals("ler_dados_2", CodeNaming.unique("ler_dados", taken, "_"));
        assertEquals("ler_dados_3", CodeNaming.unique("ler_dados", taken, "_"));
    }

    @Test
    @DisplayName("o Quality Gate reconhece os nomes opacos da saída antiga")
    void nomesOpacos() {
        for (String n : List.of("Tela1", "Tela3", "etapas_sessao_3", "fill", "press_key", "campo_s", "a", "alvo_x")) assertTrue(CodeNaming.opaque(n), n);
        for (String n : List.of("PaginaPesquisa", "pesquisar_artigo", "nome_completo", "campos", "ler_dados")) assertFalse(CodeNaming.opaque(n), n);
    }

    @Test
    @DisplayName("Q03: posição disfarçada em CSS/XPath é recusada; âncora de linha e irmão vizinho são aceitos")
    void localizadores() {
        assertTrue(LocatorPolicy.positional("css", "div#bodyContent > div:nth-of-type(2) > table > tbody > tr:nth-of-type(3) > td"));
        assertTrue(LocatorPolicy.positional("css", "ul li:first-child"));
        assertTrue(LocatorPolicy.positional("css", "button >> nth=1"));
        assertTrue(LocatorPolicy.positional("xpath", "/html/body/div[3]/table/tr[2]/td"));
        assertTrue(LocatorPolicy.positional("xpath", "//table//tr[3]/td"));
        assertTrue(LocatorPolicy.positional("xpath", "(//td)[last()]"));
        assertTrue(LocatorPolicy.positional("xpath", "//tr[position()=2]//td"));
        assertFalse(LocatorPolicy.positional("xpath", "//tr[th[normalize-space(.)=\"Nome completo\"]]//td"));
        assertFalse(LocatorPolicy.positional("xpath", "//dd[preceding-sibling::dt[1][normalize-space(.)=\"Nascimento\"]]"));
        assertFalse(LocatorPolicy.positional("css", "header input[name=\"search\"]"));
        assertFalse(LocatorPolicy.positional("css", "h1#firstHeading"));
        assertEquals(LocatorPolicy.Kind.ROW_ANCHOR, LocatorPolicy.kind(target("xpath", "//tr[th[normalize-space(.)=\"Nascimento\"]]//td")));
        assertEquals(LocatorPolicy.Kind.STABLE_ID, LocatorPolicy.kind(target("css", "h1#firstHeading")));
        assertEquals(LocatorPolicy.Kind.SCOPED, LocatorPolicy.kind(target("css", "header input[name=\"search\"]")));
        assertEquals(LocatorPolicy.Kind.POSITIONAL, LocatorPolicy.kind(target("css", "tr:nth-of-type(3) > td")));
        assertEquals(LocatorPolicy.Kind.LABEL, LocatorPolicy.kind(Map.of("preferred", Map.of("strategy", "label", "text", "Usuário"))));
    }

    @Test
    @DisplayName("exceção ÚNICA: nenhuma fonte do gerador lê, copia ou cita a referência manual da missão")
    void geradorNaoUsaReferenciaManual() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("nekkora-studio-codegen"))) root = root.getParent();
        assertTrue(root != null, "raiz do Studio");
        for (String module : List.of("nekkora-studio-codegen", "Nekkora-AI-Engine", "nekkora-studio-app")) {
            Path main = root.resolve(module).resolve("src").resolve("main");
            try (Stream<Path> s = Files.walk(main)) {
                for (Path f : s.filter(Files::isRegularFile).toList()) {
                    String t = new String(Files.readAllBytes(f), java.nio.charset.StandardCharsets.ISO_8859_1); // binário (ícone, wheel) também
                    assertFalse(t.contains("referencia-manual") || t.contains("REFERENCIA_MANUAL"), "o gerador cita a referência manual: " + root.relativize(f));
                }
            }
        }
    }

    private static Map<String, Object> target(String strategy, String expression) {
        return Map.of("preferred", Map.of("strategy", strategy, "expression", expression), "expected", Map.of("tag", "td"));
    }
}
