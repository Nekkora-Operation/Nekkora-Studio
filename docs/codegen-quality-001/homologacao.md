# NEKKORA-CODEGEN-QUALITY-001 — Homologação (G2 · G7 · G8)

## G2 — laboratório: referência manual × gerador (réplica sintética)

Laboratório genérico do framework (`lab/static/leitura/`, pessoas fictícias, `http://127.0.0.1:18766`), Chromium
headless, `nekkora_rpa` 0.3.0. Entradas: Ana, Fábio, Gina (3 linhas) e o caso errado (a busca leva a outra ficha).
Oráculo INDEPENDENTE (`oraculo_fichas.py`): lê o HTML estático com parser próprio (sem `nekkora_rpa`), aplica a regra
do PDD (fontes alternativas; data por extenso completa) e compara com a planilha reaberta pelo openpyxl.

| Código | 3 fichas | Oráculo | Caso errado |
|---|---|---|---|
| REFERENCIA_MANUAL_VALIDACAO | `COMPLETED`, 3 `SUCCEEDED` | 18/18 células, 0 fórmulas | `BUSINESS_REJECTED` (`ENDERECO_DIFERENTE_DO_ESPERADO`), planilha não gravada |
| Gerador ANTERIOR (base) | `COMPLETED`, 3 `SUCCEEDED` | 18/18 | `BUSINESS_REJECTED` (`CONFERENCIA_FALHOU`), planilha não gravada |
| GERADO_PELO_STUDIO (novo gerador, caso sintético) | ver G7 (`ListFlowTest.execucaoReal`) | — | — |

## G7 — testes automatizados novos

| Teste | O que prova |
|---|---|
| `CodegenQualityRulesTest` (6) | nomes pelo PDD (tela, método), marcador de plural, reservadas, colisão; nomes opacos reconhecidos; localizador posicional disfarçado recusado e âncora de linha/irmão vizinho aceitos; o gerador NÃO cita a referência manual |
| `CodegenQualityFlowTest.semantico` | Pages `PaginaPesquisa`/`PaginaExtracao`, métodos do PDD, dataclass com opcionais, processo com nome de negócio, nenhum UUID nem comentário por etapa, step_id ↔ etapa bijetivo e completo, uma chave por elemento |
| `CodegenQualityFlowTest.rastreioEstavel` | traceId fixo com o PDD renomeado (a Page muda de nome, o step_id não) e com a ordem das etapas trocada; aposentado nunca reaproveitado (`…_2`) |
| `CodegenQualityFlowTest.equivalencia` (Python real) | 7 cenários (completo, sem Nome completo, sem Ocupação, sem título e sem nome, data incompleta, outra ficha, nacionalidade vazia): mesmo desfecho do gerador anterior; nos bem-sucedidos, as MESMAS chamadas ao framework, na mesma ordem, nos mesmos elementos |
| `CodegenQualityFlowTest.qualidadeAst` (Python real) | VQ sem achados no código novo; o anterior acusa UUID, comentário por etapa, nome genérico, variável opaca, `or ""`, step_id sem mapa; 6 mutantes pegos pelo código certo |
| `CodegenQualityFlowTest.ancoraAmbigua` (Python real + laboratório) | duas tabelas com "Nome completo": `TARGET_AMBIGUOUS` no `extracao.ler_nome_completo`, planilha não gravada (nunca "o primeiro") |
| `CodegenSamplesTest` | determinismo (mesma entrada → mesmos bytes) e amostras para leitura |
| `FrameworkCatalogGateTest.versaoDesconhecida` | D13: com `FRAMEWORK_CATALOG_MISSING`, VQ fica `NOT_CHECKED` (não vira PASS) |
| `ListFlowTest.execucaoReal`, `SingleFlowTest.pythonReal` | o código NOVO executa no laboratório (3 fichas, planilha conferida) e passa compilação, importação, ruff, ruff format, mypy estrito, contrato e VQ |

### Regressão completa (commit `edee926`)

| Ambiente | Testes | Falhas | Pulados |
|---|---|---|---|
| Windows 11, JDK 21, Python do framework (0.3.0) | 737 | 0 | 0 |
| Linux de referência (`tools/golden-linux.sh verify`) | 737 | 0 | 15 |

Os 15 pulados no Linux **não contam como aprovados**: 1 é o `AtomicSaveTest` (a imagem roda como root, permissão
de arquivo não se aplica) e 14 exigem o interpretador do framework, que a imagem não tem
(`FrameworkCapabilityCatalogTest` 1, `SupervisedRunTest` 3, `DependencyManagerTest` 3, `ListFlowTest` 2,
`SingleFlowTest` 2 e os 3 novos do `CodegenQualityFlowTest` — equivalência, AST e âncora ambígua). Os 14 rodaram e
passaram no Windows, com Python real.

## G8 — interface real do Studio

Robot (mouse/teclado) + Chrome for Testing + extensão, máquina livre, diretórios ISOLADOS (`~/.nekkora-cq-*`, nada no
teste-rpa da usuária), operador `TEST_AGENT`, sem IA (não é necessária para nomear). Evidências em `evidence/g8/`.

| Roteiro | Resultado | Evidência |
|---|---|---|
| E2E-002 Wikipédia (`CodegenQuality001Homologation`): gravação → PDD → Desenvolver → Preparar ambiente → teste autorizado (4 artigos) → Validação | **76 verificações, 0 falhas**; V4 5/5, V5 7 testes de contrato, VQ "AST sem achados", V6 4 linhas | `evidence/g8/wikipedia/` |
| Oráculo independente (`oraculo.py`, sem `nekkora_rpa`) sobre a planilha reaberta | **24/24 células OK**, cabeçalhos OK, 0 fórmulas | `oraculo-veredito.json` (só vereditos, sem valores) |
| the-internet (`Studio014Sites`, execução única com login) | **7 verificações, 0 falhas**; execução `COMPLETED`, 1 transação, eventos sem senha | `evidence/g8/the-internet/` |

Tentativas anteriores (registradas, não escondidas):
1. Wikipédia 66/4 — os diretórios isolados não tinham a venv da automação (3 falhas encadeadas) e a checagem de
   nome opaco era um falso positivo (`endereco_da_pagina = self.act` contém `a = self.act`). Correção: o harness segue
   o caminho da pessoa (pendência → "Preparar ambiente…" → confirmação) e a checagem usa limite de palavra.
2. Wikipédia 76/1 — o harness leu a Validação no estágio rápido ("procurando o Python…"). Correção no harness (espera
   o estágio completo) e no PRODUTO: com o Python pendente, VQ agora diz "aguardando a verificação do código"
   (`RUNNING`), em vez de "AST não executada".
3. the-internet 7/1 — mesma ausência da venv; mesmo passo "Preparar ambiente…" no `Studio014Sites`.

Arquivos para a revisão humana: `human-review/rodada-1/` (originais do Studio com SHA-256).
