# STUDIO-007 — Desempenho

## Motor (`ValidationPerformanceTest`)

Etapas REALISTAS: gravadas, 3 localizadores cada (um validado preferencial, um posicional instável, um com múltiplas
correspondências), obstáculos, parâmetros variados (sem valor, variável, literal, opção, Enter) e 3 evidências por
etapa. Mediana de 9 execuções depois de 5 de aquecimento, Windows 11, JDK 21:

| Etapas | Mediana | Máximo | Apontamentos | JSON (só testes/contrato) |
|---:|---:|---:|---:|---:|
| 10 | 0,24 ms | 0,62 ms | 35 | 22 KB |
| 100 | 0,76 ms | 1,27 ms | 351 | 225 KB |
| 1.000 | 3,86 ms | 4,44 ms | 3.494 | 2,2 MB |
| 5.000 | 6,43 ms | 12,46 ms | 17.472 | 11,2 MB |

Os números exatos de cada execução ficam no log da regressão; o teste exige 5.000 etapas abaixo de 1 s (folga para
máquinas lentas).

## Por que é linear

Cada etapa é avaliada uma vez, só com os próprios dados (elemento, parâmetros, evidências); não há buscas globais por
etapa. `ObservationSummary.of(step)` percorre só as evidências da etapa (máximo 100). Nada de navegador, disco ou rede.

## Interface

A validação roda na EDT de uma vez (consistência; ver [workspace-integration.md](workspace-integration.md)). O painel
monta as linhas uma vez por relatório; alterações posteriores só mudam a atualidade (sem refazer milhares de linhas).
A homologação (cenário 6) mede, com o atalho real num projeto de 5.000 etapas, a maior espera da EDT enquanto o painel
é preenchido — ver o relatório final.
