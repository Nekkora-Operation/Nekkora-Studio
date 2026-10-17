# STUDIO-008 — Desempenho

`VariablesPerformanceTest` (Windows 11, Java 21.0.12; mediana de 5 medições após aquecimento). Uma variável para cada
5 etapas (até 1.000), 4 de cada 5 etapas usando uma variável; origens alternadas entre entrada, valor fixo e saída de
etapa. Números de uma execução; não são limites do produto.

| Etapas | Variáveis | Referências | Apontamentos | Validação 1.1 | Fluxo de dados | Contrato semântico | Linhas do painel | Renomear (5 usos) | Renomear (usada por todas) | Desfazer (restauração) | Salvar com releitura | Abrir completo |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 10 | 2 | 8 | 16 | 0,34 ms | 0,03 ms | 0,15 ms | 0,08 ms | 0,13 ms | — | — | 11 ms | 4 ms |
| 100 | 20 | 80 | 174 | 1,0 ms | 0,12 ms | 1,0 ms | 0,31 ms | 0,12 ms | — | — | 17 ms | 7 ms |
| 1.000 | 200 | 800 | 1.770 | 5,1 ms | 0,67 ms | 4,9 ms | 1,7 ms | 0,29 ms | — | — | 82 ms | 31 ms |
| 5.000 | 1.000 | 4.000 | 8.864 | 8,6 ms | 1,1 ms | 17,9 ms | 5,9 ms | 1,3 ms | 81,5 ms (4.000 etapas) | 0,47 ms | 287 ms | 155 ms |

O arquivo de 5.000 etapas tem ~3,1 MB; o contrato semântico, ~2,2 MB. Limites folgados no teste (1,5 s) detectam
regressão quadrática.

Na homologação real (Windows, janela do Studio, 5.000 etapas e 1.000 variáveis): ver `homologation.md`.
