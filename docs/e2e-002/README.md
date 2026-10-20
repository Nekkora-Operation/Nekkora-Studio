# NEKKORA-E2E-002 — Wikipédia → PDD → IA → Python → Excel

Prova da cadeia completa operada pela interface do Nekkora Studio (Claude como `TEST_AGENT`, mouse e teclado reais)
sobre quatro biografias públicas de `https://pt.wikipedia.org` (Machado de Assis, Clarice Lispector, Marie Curie,
Albert Einstein): gravação (busca + leitura de texto não interativo), debug guiado, PDD revisado e exportado, ajuda da
IA real, Python gerado pelo gerador oficial sobre o `nekkora_rpa`, e o **teste autorizado** pela interface gravando
`biografias.xlsx` com as colunas **URL | Nome completo | Nascimento | Morte | Nacionalidade | Ocupação**.

Não é um scraper de Wikipédia: o framework ganhou capacidades GENÉRICAS (escrita de XLSX transacional, leitura de
texto e de listas, tecla, endereço da página) e o Studio/extensão, a leitura semântica de dado não interativo.

| Documento | Conteúdo |
|---|---|
| [homologation.md](homologation.md) | montagem, rodada final (69/69 + conferidor 24/24), defeitos reais achados e corrigidos |
| [decisions.md](decisions.md) | decisões da usuária (D-E2E2-01/02/12) e técnicas |
| [security.md](security.md) | privacidade, IA, execução supervisionada, dados de saída |
| [known-limitations.md](known-limitations.md) | o que não está coberto |
| `evidence/` | log, capturas das janelas do Studio, metadados da IA, PDF, código gerado, projeto, execução, conferidor |

## Critérios de aceite

| ID | Critério | Estado | Prova |
|---|---|---|---|
| C01 | Uso do Studio como usuário | **PASS** | Robot + capturas das janelas (`evidence/capturas`), log da rodada; projeto, PDD, configuração e Python nunca escritos à mão |
| C02 | Campo de texto editável | **PASS** | busca gravada COM interação (Preencher + Enter); valor fora do modelo (variável `termo_de_busca`); campo recriado pela página localizado pela região, sem posição |
| C03 | Textos de artigo | **PASS** | "Selecionar dado": título + 5 dados (e "Campo(s)" como fonte alternativa) por alvo selecionado no Studio, inclusive texto não interativo; IT `E2e002SelectDataIT` na extensão |
| C04 | Quatro artigos | **PASS** | 4 URLs distintas; debug guiado com identidade comprovada em cada artigo; endereço lido e CONFERIDO com a lista na execução |
| C05 | PDD revisado | **PASS** | PDD_READY na revisão atual; PDF exportado pelo Studio com o apêndice "Dados do processo" (colunas na ordem, regras, fontes alternativas); bloqueio por PDD desatualizado demonstrado |
| C06 | Anthropic real | **PASS** | botão "Solicitar ajuda (IA opcional)", consentimento com prévia, chamada real `RESPONSE_VERIFIED` (`evidence/ia-chamadas.jsonl`, só metadados); falhas reais vistas em outras rodadas (`SCHEMA_INVALID`, `PARTIAL_RESPONSE`) reportadas |
| C07 | Python oficial | **PASS** | "Desenvolver automação" → gerador oficial, CODE_VALIDATED (compilação, importação, ruff, mypy estrito, testes de contrato); manifesto com hashes conferido antes de executar; nenhuma edição |
| C08 | Writer XLSX | **PASS** | `nekkora_rpa.xlsx` (framework 0.3.0): esquema declarado, texto anti-fórmula, gravação atômica conferida, tudo ou nada, retomada; testes unitários, de integração e 30/30 mutações; leitura independente (openpyxl) |
| C09 | Excel correto | **PASS** | planilha real com 6 colunas exatas, 4 linhas, sem fórmulas; conferidor independente 24/24 células; proveniência separada (linha da planilha → coluna → etapa → alvo) |
| C10 | Validação segura | **PASS** | V0–V5 efetivas; V6 ✓ só depois do teste autorizado da MESMA revisão; "Validar automação" sem execução |
| C11 | Execução via Studio | **PASS** | botão "Executar teste autorizado", confirmação, progresso na janela, **Cancelar** (planilha não gravada) e resultado na UI; Python é subprocesso sob comando do Studio |
| C12 | Sem regressões | **PASS** | Studio 765 testes (Windows) e 703 (Linux, contêiner) sem falhas; framework 354 (Windows) e 353+1 (Linux), 30/30 mutações; extensão: núcleo 223, host 98, Node 221 e **ITs da suíte padrão 40/40 classes, 305 testes, 0 falhas** (129 da rodada completa interrompida por memória + 8 grupos sequenciais de até 3 classes, memória amostrada — `Nekkora-Extension-Chrome/docs/e2e-002/c12-matriz.md`); defeito PRÉ-EXISTENTE de tempo no `Studio013SitesIT` (falha também na base `main`) corrigido no teste |

## Rodada final

Commits da homologação: `Nekkora-RPA-Framework` 2f36a0a · `Nekkora-Extension-Chrome` 9e4687e · `Nekkora-Studio` 0293b50 (depois: só documentação e, na extensão, a correção de tempo do `Studio013SitesIT` — commit `7beeb25`, branch `test/e2e-002-c12-regressao`).
Três rodadas reais completas com 100% (r10, r13, r14). A planilha gerada fica só na estação de homologação
(`Documentos/teste-rpa/wikipedia-biografias/biografias.xlsx`); não é commitada.
