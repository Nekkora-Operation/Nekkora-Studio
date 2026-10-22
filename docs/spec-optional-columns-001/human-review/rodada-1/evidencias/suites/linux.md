# Suíte Linux — commit 6828edc

Total pelo log: 780 testes, 0 falhas, 0 erros, 27 pulados, 753 aprovados (118 classes).

Comando: `sh tools/golden-linux.sh verify` (contêiner `eclipse-temurin:21.0.12.1_1-jdk-resolute` fixado por digest,
`./mvnw clean verify` sobre `git archive HEAD`). Os relatórios XML ficam dentro do contêiner; os pulados abaixo vêm do
log (por classe) e o motivo é a condição `assume…` do próprio teste.

| Classe | Pulados | Motivo |
|---|---|---|
| `AtomicSaveTest` | 1 | `assumeFalse(Files.isWritable(file))`: o contêiner roda como root, que escreve em arquivo somente leitura |
| `TabularReprTest` | 1 | sem o interpretador do framework neste ambiente (importa o código gerado) |
| `CodegenRound3Test` | 3 | sem o interpretador do framework neste ambiente (executa o código gerado) |
| `FrameworkCapabilityCatalogTest` | 1 | sem o interpretador do framework neste ambiente |
| `SupervisedRunTest` | 4 | sem o interpretador do framework neste ambiente |
| `DependencyManagerTest` | 3 | sem Python 3.12+ (ou sem acesso ao PyPI) |
| `CodegenQualityFlowTest` | 4 | sem o interpretador do framework neste ambiente |
| `ListFlowTest` | 2 | sem o interpretador do framework neste ambiente |
| `OptionalColumnsTest` | 3 | sem o interpretador do framework neste ambiente (matriz Python real, planilha real e Chromium no laboratório) |
| `SingleFlowTest` | 2 | sem o interpretador do framework neste ambiente |
| `EnterNavigationNegativeTest` | 3 | sem o interpretador do framework neste ambiente |

Os 26 testes que dependem de Python rodam (e passam) no Windows: 780 de 780 lá, 0 pulados. Os 3 pulados novos desta missão são os de
`OptionalColumnsTest` que executam Python (matriz, planilha real, laboratório com Chromium).
