# STUDIO-LEAN-001: matriz de regressão

Comparação por CASO, não só por contagem: a lista nominal (`módulo`, classe, método, resultado) foi extraída dos
relatórios do surefire antes e depois.

| Bateria | Antes | Depois | Casos |
|---|---|---|---|
| Windows `./mvnw -o -B clean verify` (todos os módulos; golden do design system; `Scale150Test`/`Variables150Test` em JVM própria) | 780 PASS | 781 PASS | **780 preservados (mesmos nomes, mesmo resultado); 1 adicionado** (`PackagedResourcesTest.recursosPythonEmpacotadosNaoLevamCachesLocais`); **0 removidos; 0 desabilitados** |
| Escala 200% | não existe no Studio | — | — |
| Geração de pacotes e execução REAL do código gerado (`ListFlowTest`, `SingleFlowTest`, `OptionalColumnsTest`, `CodegenQualityFlowTest`, `EnterNavigationNegativeTest`: réplica `/leitura/` da Wikipédia, The Internet, RPA Challenge do laboratório) | PASS | PASS | dentro da bateria acima |
| Determinismo do codegen, manifesto e `selectors.json`/`selectors.py` (`PythonGeneratorTest`, `OutputWriterRetireTest`, `EnglishNamesTest`, `TabularReprTest`) | PASS | PASS | dentro da bateria |
| Privacidade e segredos (`CapturePrivacy*`, vocabulário fechado, `secret_provider_missing`) | PASS | PASS | dentro da bateria |
| Abertura do produto em ambiente limpo (cópia do jar, dados descartáveis, sem checkout) | janela em 1 328 ms | janela em 1 340 ms | 5/5 rodadas abriram |
| Guarda do release (`PackagedResourcesTest`) | **falha** no código antigo (`.ruff_cache` listado) | PASS | vermelho → verde |

Não reexecutado nesta missão, com motivo:

- Fluxos de referência nos sites REAIS pela interface do Studio (Wikipédia, The Internet, RPA Challenge): as mudanças
  não tocam código de execução (só recursos excluídos do jar e uma dependência Maven não usada), e os fluxos acima
  executam o código gerado de verdade contra as réplicas. O pacote da Wikipédia gerado pelo Studio foi executado contra
  a Wikipédia real na missão E2E-005 (2/2 itens).
- Linux (golden de referência): o código Java não mudou. A bateria Linux não foi refeita.

Arquivos removidos ou movidos: **nenhum**. Desabilitados: **nenhum**.
