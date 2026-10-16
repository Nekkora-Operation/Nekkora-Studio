# STUDIO-007 — Plano de testes e matriz S007

Camadas: **motor** (`AutomationValidatorTest`, `ValidationContractTest`, `ValidationPerformanceTest` — sem interface),
**Studio integrado** (`ValidationStudioTest`, `Scale150Test.validacao150`, `LayoutRegressionTest.listaComNomeAcessivelPorLinha`)
e **homologação real** (`Studio007Homologation`: Studio, Chrome for Testing, extensão, Native Host, laboratório fictício,
mouse e teclado do sistema).

Legenda: **A** automatizado (`./mvnw verify`), **H** homologação real no Windows, **R** regressão completa.

| Caso | Verificação | Onde | Tipo |
|---|---|---|---|
| S007-001 | Baseline da STUDIO-006 | `./mvnw clean verify` em `b0bd441`: 426/426 Windows; Linux 425 + 1 não aplicável | R |
| S007-002 | Automação vazia | `AutomationValidatorTest.s007002Vazia` | A |
| S007-003 | Sessão vazia | `s007003SessaoVazia` | A |
| S007-004 | CLICK configurado | `s007004ClickConfigurado` | A |
| S007-005 | CLICK sem elemento | `s007005SemElemento` | A |
| S007-006 | FILL sem valor | `s007006a009Fill`; homologação cenários 1–3 | A H |
| S007-007 | FILL com LiteralValue | `s007006a009Fill` | A |
| S007-008 | FILL com VariableReference | `s007006a009Fill`; homologação cenário 2 (variável pelo editor real) | A H |
| S007-009 | FILL com CredentialReference | `s007006a009Fill` | A |
| S007-010 | SELECT_OPTION sem opção | `s007010e011Select`; homologação cenário 1 | A H |
| S007-011 | SELECT_OPTION com opção | `s007010e011Select` | A |
| S007-012 | CHECK sem elemento | `s007005SemElemento` | A |
| S007-013 | UNCHECK sem elemento | `s007005SemElemento` | A |
| S007-014 | PRESS_KEY sem tecla | `s007014PressKey` | A |
| S007-015 | NAVIGATE sem valor | `s007015Navigate`; homologação cenário 1 | A H |
| S007-016 | WAIT_FOR sem condição modelada | `s007016a018`; homologação cenário 1 | A H |
| S007-017 | OBSERVE | `s007016a018` | A |
| S007-018 | UNKNOWN | `s007016a018` | A |
| S007-019 | Elemento ausente | `s007005SemElemento` | A |
| S007-020 | Localizador ausente | `s007020LocalizadorAusente` | A |
| S007-021 | Localizador validado na captura | `s007021ValidadoNaCaptura` | A |
| S007-022 | Localizador manual não verificado | `s007022Manual` | A |
| S007-023 | Múltiplos resultados históricos | `s007023e024` | A |
| S007-024 | Estabilidade desconhecida | `s007023e024` | A |
| S007-025 | Obstáculo observado | `s007025Obstaculos` | A |
| S007-026 | Privacidade v2 | `s007026Privacidade`; homologação cenário 5 | A H |
| S007-027 | Sem dados privados no relatório | `ValidationContractTest.s007027SemDadosPrivados`; homologação cenário 5 | A H |
| S007-028 | Evidência OBSERVED | `s007028a030Evidencias`; homologação cenário 5 (evidência real) | A H |
| S007-029 | Evidência INCOMPLETE | `s007028a030Evidencias` | A |
| S007-030 | BusinessOutcome continua NOT_VALIDATED | `s007028a030Evidencias`; homologação cenário 5 | A H |
| S007-031 | Ordenação determinística | `s007031Ordem`; `ValidationContractTest.fixtureCongelada` | A |
| S007-032 | Identificadores estáveis | `s007032e033Estaveis`; homologação cenário 4 | A H |
| S007-033 | Validação repetida | `s007032e033Estaveis` | A |
| S007-034 | Validação após edição | `ValidationStudioTest.s007034a036EdicaoUndoRedo`; homologação cenário 2 | A H |
| S007-035 | Validação após Undo | idem; homologação cenário 3 (Ctrl+Z real) | A H |
| S007-036 | Validação após Redo | idem; homologação cenário 3 (Ctrl+Y real) | A H |
| S007-037 | Projeto carregado | `s007037a039ProjetoSalvo`; homologação cenário 4 (salvo/reaberto e fixture da STUDIO-006) | A H |
| S007-038 | Nenhuma alteração no `.nekkora` | `s007037a039ProjetoSalvo`; `naoAltera`; homologação cenário 4 (SHA-256) | A H |
| S007-039 | Não marca não salvo | `s007037a039ProjetoSalvo`; homologação cenários 1 e 4 | A H |
| S007-040 | Não altera o histórico | `s007034a036EdicaoUndoRedo`; homologação cenário 1 | A H |
| S007-041 | Não executa ações | `s007041a043SemNavegador`; `ValidationContractTest.independente` | A |
| S007-042 | Não utiliza Chrome | `s007041a043SemNavegador` (nenhuma chamada ao navegador) | A |
| S007-043 | Não depende do Native Host | `ValidationContractTest.independente` (módulo sem Bridge/Recorder) | A |
| S007-044 | Atualização do painel | `s007044e045PainelENavegacao`, `outraAutomacao`; homologação cenário 1 | A H |
| S007-045 | Navegação até etapa | `s007044e045PainelENavegacao`; homologação cenário 2 (mouse real, editor existente) | A H |
| S007-046 | Relatório desatualizado | `s007046e047Desatualizado`; homologação cenários 2 e 3 | A H |
| S007-047 | Alterado durante a validação | `s007046e047Desatualizado`, `duranteGravacaoERecarga`; homologação cenário 5 | A H |
| S007-048 | Quatro temas | `s007048e049TemasEAcessibilidade`; homologação (Ctrl+Shift+T real) | A H |
| S007-049 | Acessibilidade | `s007048e049TemasEAcessibilidade`, `LayoutRegressionTest.listaComNomeAcessivelPorLinha`, `Scale150Test.validacao150` | A |
| S007-050 | Regressão completa | `./mvnw clean verify` Windows e `tools/golden-linux.sh verify` | R |
| S007-051 | Golden Screenshots | `GoldenScreenshotTest` 4/4 nos dois sistemas, sem regenerar | R |
| S007-052 | Homologação Windows | `Studio007Homologation`, seis cenários | H |

## Casos acrescentados durante a missão

| Caso | Verificação | Onde |
|---|---|---|
| S007-053 | Contrato JSON: schema = código; saída conforme; fixture congelada; validador recusa `READY_FOR_EXECUTION` | `ValidationContractTest` |
| S007-054 | Desempenho 10/100/1.000/5.000 etapas | `ValidationPerformanceTest`; homologação cenário 6 |
| S007-055 | Painel a 150% em 1366×768 sem botões cortados e com área de trabalho utilizável (achou defeito) | `Scale150Test.validacao150` |
| S007-056 | Linhas de lista lidas por tecnologias assistivas | `LayoutRegressionTest.listaComNomeAcessivelPorLinha` |
| S007-057 | Validar durante gravação: permitido, sem editar | `duranteGravacaoERecarga`; homologação cenário 5 |
| S007-058 | Recarregar do arquivo invalida o relatório | `duranteGravacaoERecarga` |
| S007-059 | Sem textos de sucesso simulado/execução no código da validação | `ValidationStudioTest.semSucessoSimulado` |

## Como rodar

```bash
NEKKORA_BRIDGE_DIR=<pasta> NEKKORA_STUDIO_DATA_DIR=<pasta> ./mvnw clean verify   # Windows
timeout 2400 sh tools/golden-linux.sh verify                                      # Linux de referência
# homologação (Windows; extensão ao lado, Native Host instalado, Chrome for Testing em .tools):
NEKKORA_STUDIO_DATA_DIR=<pasta> NEKKORA_BRIDGE_DIR=<pasta> java -cp <classpath de teste do app> \
  com.nekkora.studio.app.evidence.Studio007Homologation <saída> <repositório da extensão> <classpath>
```
