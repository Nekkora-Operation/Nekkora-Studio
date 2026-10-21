# Antes (rodada 2) → depois (rodada 3)

Cada item do adendo, com o arquivo e a linha nas duas rodadas (arquivos originais do Studio, nas pastas `rodada-2/` e
`rodada-3/`). Nada foi editado à mão: o "depois" é o que o gerador oficial produziu pela interface real.

## Arquivos

| Rodada 2 | Rodada 3 | Observação |
|---|---|---|
| `paginas.py` | `automation/pages.py` | pacote próprio |
| `processo.py` | `automation/process.py` | |
| (não existia) | `automation/selectors.py` | `SelectorKey` + `TARGETS` (revisável em Python) |
| (não existia) | `automation/__init__.py` | marca o pacote |
| `selectors.json` | `selectors.json` | DERIVADO do mesmo plano; é o que o runtime 0.3.0 lê; sistema `processo` → `process` |
| `config/execucao.toml` | `config/execution.toml` | mesmo conteúdo de execução |
| `main.py` | `main.py` | inglês; `--input/--spreadsheet/--output` + nomes antigos aceitos |
| `manifest.json` 1.3.0 | `manifest.json` 1.4.0 | + `language`, `layout`, `cli`, `naming`, `selectors` |

## Mudanças, com linha

| Pedido | Antes (rodada 2) | Depois (rodada 3) |
|---|---|---|
| Q10 chave tipada (fill) | `final/paginas.py:62` `self.elemento("campo_termo_de_busca")` | `final/automation/pages.py:84` `SelectorKey.SEARCH_TERM_FIELD.value` |
| Q10 chave tipada (pós-condição do login) | `the-internet/paginas.py:52` `"processo.area_segura.logout"` | `the-internet/automation/pages.py:62` `SelectorKey.SECURE_AREA_LOGOUT.value` |
| Q10 registro em Python | — | `final/automation/selectors.py:15` `SelectorKey`; `final/automation/selectors.py:28` `TARGETS` |
| Q10 identidade da tela | `final/paginas.py:54` `identity = "nascimento"` | `final/automation/pages.py:98` `identity_key = SelectorKey.PROFILE_BIRTH` |
| Q11 base das telas | `final/paginas.py:21` `PaginaBase` / `acoes` / `elemento` | `final/automation/pages.py:24` `PageBase` / `actions` |
| Q11 tela de busca | `final/paginas.py:50` `PaginaPesquisa.pesquisar_artigo(termo_de_busca)` | `final/automation/pages.py:73` `SearchPage.search_article(search_term)` |
| Q11 tela da ficha | `final/paginas.py:74` `PaginaFicha.ler_dados_da_ficha()` | `final/automation/pages.py:95` `ProfilePage.read_profile_data()` |
| Q11 dados lidos | `final/paginas.py:35` `DadosDaFicha` | `final/automation/pages.py:58` `ProfileData` |
| Q11 processo | `final/processo.py:59` `BiografiasDaWikipedia` | `final/automation/process.py:70` `WikipediaBiographiesProcess` (nome nas execuções igual) |
| Q11 entrada (coluna → campo) | `final/processo.py:20` `Entrada.de_item` (campo = coluna) | `final/automation/process.py:39` `search_term=_required_input(data, "termo_de_busca")` |
| Q11 linha da planilha | `final/processo.py:38` `linha_da_planilha` | `final/automation/process.py:49` `build_spreadsheet_row` |
| Q11 coluna ↔ campo | `final/main.py:32` `OutputColumn("Nome completo", "nome_completo"…)` | `final/main.py:33` `OutputColumn("Nome completo", "full_name"…)` |
| Q11 linha de comando | `final/main.py:46` `--entrada` | `final/main.py:49` `--input` (e `--entrada` aceito) |
| Q11 step_id | `final/paginas.py:101` `ficha.ler_nome_completo` | `final/automation/pages.py:121` `profile.read_full_name` |
| Q11 abertura sintética | `final/processo.py:69` `processo.abrir_pagina_inicial` | `final/automation/process.py:82` `process.open_start_page` (ids anteriores no manifesto) |
| Q11 login (the-internet) | `the-internet/paginas.py:31` `PaginaAcesso.entrar(segredos)` | `the-internet/automation/pages.py:38` `AccessPage.sign_in(secrets)` |
| Q11 área segura | `the-internet/paginas.py:56` `ler_texto_do_link_logout` | `the-internet/automation/pages.py:66` `read_logout_link_text` |
| Ajuste: só espaços | `final/processo.py:47` `a or b` ("   " passava) | `final/automation/process.py:44` `_filled(a) or _filled(b)` |
| Docstrings | `final/paginas.py:51` português | `final/automation/pages.py:74` inglês (rótulos da página entre aspas, como estão) |

## O que NÃO foi traduzido (dados e contratos)

Rótulos da página (XPath com "Nome completo"), cabeçalhos da planilha, colunas da lista (`termo_de_busca`,
`endereco_do_artigo`), chave do resultado sem planilha (`texto_do_link_logout`), apelidos de segredo
(`credencial.senha`), `RpaProcess.name`, códigos de recusa (`ENDERECO_DIFERENTE_DO_ESPERADO`, `DADO_OBRIGATORIO_VAZIO`,
`DATA_INCOMPLETA_OU_AMBIGUA`) e as pastas de execução (`execucao/`, `resultados/`).

