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

## G8 — interface real do Studio

Pendente nesta versão do documento (ver relatório final).
