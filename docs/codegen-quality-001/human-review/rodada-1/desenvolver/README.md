<!-- GERADO pelo Nekkora Studio ("Desenvolver automação"). -->
# BiografiasDaWikipedia

**O que faz.** Uma transação por linha da lista de entrada. Passa por: Pesquisa do artigo → Extração dos dados. Cada linha da lista de entrada (CSV ou XLSX com as colunas `termo_de_busca`, `endereco_do_artigo`) vira uma transação; a planilha `.xlsx` de saída tem as colunas `URL`, `Nome completo`, `Nascimento`, `Morte`, `Nacionalidade`, `Ocupação` e só é gravada se TODOS os itens derem certo (tudo ou nada, pelo `XlsxResultSink` do nekkora_rpa).

## Como está organizado

| Arquivo | Conteúdo |
|---|---|
| `paginas.py` | uma classe por tela: `PaginaPesquisa`, `PaginaExtracao` |
| `processo.py` | `BiografiasDaWikipedia`: a ordem das telas e a linha da planilha |
| `main.py` | linha de comando |
| `selectors.json` | como cada elemento é localizado (o código só usa chaves) |
| `manifest.json` | revisões, hashes, mapa `step_id` ↔ etapa do Studio |

## Telas e o que cada método faz

- **`PaginaPesquisa`** — Pesquisa do artigo.
  - `pesquisar_artigo()` — Pesquisar o artigo pelo nome.
- **`PaginaExtracao`** — Extração dos dados.
  - `conferir_endereco_da_pagina()` — Lê endereço da página e o confere com ``endereco_do_artigo`` (diferente recusa o item).
  - `ler_dados()` — Lê título, “Nome completo”, “Nascimento”, “Morte”, “Nacionalidade”, “Ocupação” e “Campo(s)”. Título, “Nome completo”, “Ocupação” e “Campo(s)” podem faltar nesta página (fonte alternativa declarada no PDD).

## Como cada elemento é localizado

| Chave | Estratégia | Leitura |
|---|---|---|
| `processo.pesquisa.campo_termo_de_busca` | CSS `header input[name="search"]` | região/elemento estável + atributo |
| `processo.extracao.titulo` | CSS `h1#firstHeading` | id ou atributo de teste estável |
| `processo.extracao.nome_completo` | XPATH `//tr[th[normalize-space(.)="Nome completo"]]//td` | âncora de linha (cabeçalho → célula ou termo → definição) |
| `processo.extracao.nascimento` | XPATH `//tr[th[normalize-space(.)="Nascimento"]]//td` | âncora de linha (cabeçalho → célula ou termo → definição) |
| `processo.extracao.morte` | XPATH `//tr[th[normalize-space(.)="Morte"]]//td` | âncora de linha (cabeçalho → célula ou termo → definição) |
| `processo.extracao.nacionalidade` | XPATH `//tr[th[normalize-space(.)="Nacionalidade"]]//td` | âncora de linha (cabeçalho → célula ou termo → definição) |
| `processo.extracao.ocupacao` | XPATH `//tr[th[normalize-space(.)="Ocupação"]]//td` | âncora de linha (cabeçalho → célula ou termo → definição) |
| `processo.extracao.campos` | XPATH `//tr[th[normalize-space(.)="Campo(s)"]]//td` | âncora de linha (cabeçalho → célula ou termo → definição) |

O nekkora_rpa só age se o localizador encontrar EXATAMENTE um elemento com a identidade esperada; nenhum, ou mais de um, é erro (nunca escolhe o primeiro).

## Executar

O Nekkora Studio prepara o ambiente desta automação: **Desenvolver automação → Pendências → Preparar ambiente** (venv própria, `nekkora-rpa[excel]==0.3.0`, dependências conferidas por hash, confirmação antes de instalar).

Fora do Studio (Python 3.12+, depois da publicação no índice aprovado):

```sh
python -m venv .venv
.venv/Scripts/python -m pip install "nekkora-rpa[excel]==0.3.0"
.venv/Scripts/python main.py --entrada <lista.csv|lista.xlsx> --planilha <saida.xlsx> --saida <pasta> --console
```

Eventos JSONL sem valores, checkpoint e resumo em `<pasta>/execucao/`.
Gerar código não é executar: rode somente em ambiente autorizado (benchmark).
