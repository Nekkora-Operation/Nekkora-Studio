package com.nekkora.studio.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CODEGEN-QUALITY-001 rodada 3 (Q11): nomes em inglês pelo glossário governado, sem tradução livre. */
class EnglishNamesTest {

    @Test
    @DisplayName("frases nominais: grupos de \"de\" invertidos, núcleo no fim, inglês/nome próprio no começo")
    void nounPhrases() {
        assertEquals("search_term", EnglishNames.noun("termo de busca").snake());
        assertEquals("search_term", EnglishNames.identifier("termo_de_busca").snake());
        assertEquals("full_name", EnglishNames.noun("Nome completo").snake());
        assertEquals("secure_area", EnglishNames.noun("Área segura").snake());
        assertEquals("profile_data", EnglishNames.noun("dados da ficha").snake());
        assertEquals("article_address", EnglishNames.identifier("endereco_do_artigo").snake());
        assertEquals("page_address", EnglishNames.identifier("endereco_da_pagina").snake());
        assertEquals("expected_address", EnglishNames.identifier("endereco_esperado").snake());
        assertEquals("logout_link_text", EnglishNames.identifier("texto_do_link_logout").snake());
        assertEquals("search_term_field", EnglishNames.noun("campo termo_de_busca").snake());
        assertEquals("start_page", EnglishNames.noun("página inicial").snake());
        assertEquals("WikipediaBiographies", EnglishNames.identifier("biografias_da_wikipedia").pascal());
        assertEquals("internet_secure_area", EnglishNames.identifier("area_segura_the_internet").snake());
        assertEquals("expected_profile_address", EnglishNames.noun("Endereço da ficha esperada").snake());
        assertEquals("record_code", EnglishNames.noun("Código do registro").snake());
        assertEquals("first_name", EnglishNames.noun("primeiro nome").snake());
        assertEquals("issue_date", EnglishNames.noun("Data de emissão").snake());
        assertEquals("invoice_number", EnglishNames.noun("número da nota fiscal").snake());
    }

    @Test
    @DisplayName("plural e feminino regulares seguem o glossário; rótulos já em inglês ficam como estão")
    void inflection() {
        assertEquals("fields", EnglishNames.noun("Campo(s)").snake());
        assertEquals("nationalities", EnglishNames.noun("nacionalidades").snake());
        assertEquals("options", EnglishNames.noun("opções").snake());
        assertEquals("people", EnglishNames.noun("pessoas").snake());
        assertEquals("birth", EnglishNames.noun("Nascimento").snake());
        assertEquals("username", EnglishNames.noun("Username").snake());
        assertEquals("logout", EnglishNames.noun("Logout").snake());
        assertTrue(EnglishNames.noun("Username").untranslated().isEmpty());
    }

    @Test
    @DisplayName("palavra fora do glossário não é inventada: fica como está e volta como pendência")
    void untranslatedIsReported() {
        EnglishNames.Phrase p = EnglishNames.noun("Protocolo do sinistro");
        assertEquals(List.of("sinistro", "protocolo"), p.words());
        assertEquals(List.of("sinistro", "protocolo"), p.untranslated());
    }

    @Test
    @DisplayName("métodos: verbo do glossário + objeto; sem verbo do glossário, vazio")
    void methods() {
        assertEquals("search_article", EnglishNames.method("Pesquisar o artigo pelo nome").orElseThrow().snake());
        assertEquals("search_person", EnglishNames.method("Pesquisar a pessoa pelo nome").orElseThrow().snake());
        assertEquals("open_search", EnglishNames.method("Abrir a busca do acervo").orElseThrow().snake());
        assertEquals("sign_in", EnglishNames.method("Entrar").orElseThrow().snake());
        assertEquals("sign_in", EnglishNames.noun("Entrar").snake());
        assertEquals("sign_out", EnglishNames.noun("Sair").snake());
        assertTrue(EnglishNames.method("Extração dos dados").isEmpty());
        assertEquals("searches", EnglishNames.thirdPerson("search"));
        assertEquals("signs in", EnglishNames.thirdPerson("sign in"));
        assertEquals("verifies", EnglishNames.thirdPerson("verify"));
    }

    @Test
    @DisplayName("R4-02 (glossário 1.1.0): valor de DATA ganha o nome de data; fora de data, nada muda; nome que já diz data não repete")
    void dateContext() {
        assertEquals("nekkora-studio/naming-pt-en/1.1.0", EnglishNames.GLOSSARY);
        assertEquals("birth_date", EnglishNames.noun("Nascimento", true).snake());
        assertEquals("death_date", EnglishNames.noun("Morte", true).snake());
        assertEquals("birth_date", EnglishNames.identifier("nascimento", true).snake());
        assertEquals("birth", EnglishNames.noun("Nascimento").snake(), "fora de data continua como na 1.0.0");
        assertEquals("birth_date", EnglishNames.noun("Data de nascimento", true).snake(), "já diz data: sem date_date");
        assertEquals("full_name", EnglishNames.noun("Nome completo", true).snake(), "palavra sem contexto de data não muda");
        assertTrue(EnglishNames.noun("Nascimento", true).untranslated().isEmpty());
        assertEquals("record_filling", EnglishNames.noun("Preenchimento por registro").snake());
        assertEquals("preparation", EnglishNames.noun("Preparação").snake());
        assertEquals("conclusion", EnglishNames.noun("Conclusão").snake());
    }

    @Test
    @DisplayName("determinístico: a mesma entrada dá sempre o mesmo nome")
    void deterministic() {
        for (int i = 0; i < 3; i++) assertEquals("search_term_field", EnglishNames.noun("campo termo_de_busca").snake());
    }
}
