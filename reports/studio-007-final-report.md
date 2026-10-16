# NEKKORA-STUDIO-007 — Relatório final

**Automation Validation Engine: diagnóstico estrutural e preparação de automações**

| Item | Valor |
|---|---|
| Repositório | `Nekkora-Operation/Nekkora-Studio` |
| Branch | `feat/nekkora-studio-007` |
| Baseline | `b0bd441436010df95f95eac452a3b6a3c2ace778` (main com o PR #8 da STUDIO-006) |
| Commit de código testado | `09083ea` (regressão Windows e Linux e homologação real neste mesmo commit) |
| Extensão | sem alteração (árvore idêntica à `main` `2c7c06b`, sem alterações locais) |
| Formato `.nekkora` | inalterado (1.0.0; schema e fixtures congeladas intactos) |
| Dados reais | não utilizados (laboratório fictício e dados sintéticos) |
| Merge | não feito — PR para revisão |

| Commit | Conteúdo |
|---|---|
| `81f05d5` | módulo `nekkora-studio-validation`: motor, catálogo de códigos, relatório, JSON canônico, schema, fixture, testes de regras/contrato/desempenho |
| `57ba159` | Studio: `ValidationService`, menu Automação, ação e atalho, painel, desatualização; `NekkoraList` acessível por linha |
| `fb61e7b` | painel compacto e legível em janelas estreitas; símbolo × |
| `5c48fe4` | homologação real `Studio007Homologation` |
| `09083ea` | painel indexa as etapas uma vez (achado da homologação); roteiro do cenário 5 |
| (docs) | `docs/studio-007/`, este relatório, evidências e medições |

## 1. Baseline

| Execução | Commit | Resultado |
|---|---|---|
| Windows 11, `./mvnw clean verify` (Bridge e dados isolados) | `b0bd441` | 426/426, BUILD SUCCESS |
| Linux de referência, `tools/golden-linux.sh verify` | `b0bd441` | 426 executados: 425 aprovados + 1 não aplicável (superusuário), BUILD SUCCESS |

Confere com o relatório da STUDIO-006.

## 2. Arquitetura

Motor independente da interface no módulo novo `nekkora-studio-validation` (Java puro, só o domínio):
`new AutomationValidator().validate(automation)` funciona num teste sem Swing, Chrome, Native Host, Recorder, servidor,
disco ou banco. No Studio: `ValidationService` (somente leitura, EDT) → `ValidationPanel` (design system). Detalhes:
[architecture.md](../docs/studio-007/architecture.md).

Reutilização do domínio, sem catálogo paralelo nem classes duplicadas: `ActionType`/`TargetRequirement`/`acceptsValue`/
`acceptsOption`, `ActionParameters`/`ValueSource`/`OptionChoice`, `ElementReference`/`LocatorCandidate`/`ManualLocator`/
`ObservedObstacle`, `RecordingObservation.limitations()` e `ObservationSummary` (STUDIO-005). O domínio não mudou.

## 3. Contrato

`ValidationReport` imutável e determinístico, serializado como `nekkora-studio/validation-report` **1.0.0** com as regras
`nekkora-validation-rules/1.0.0`. Contém identidade da automação, sessão e etapa, código estável, severidade, campo,
detalhe de vocabulário fechado, explicação e orientação controladas, posição, totais e estado. Não contém valores,
nomes, credenciais, conteúdo de página, localizadores nem objetos do domínio. Schema JSON 2020-12 e fixture congelada em
`nekkora-studio-validation/src/test/resources/contracts/validation-report/1.0.0/`. Não persistido no `.nekkora`.
[validation-model.md](../docs/studio-007/validation-model.md).

## 4. Estados e severidades

| Estado | Quando |
|---|---|
| `INCOMPLETE` — Configuração incompleta | há bloqueio |
| `NOT_FULLY_ASSESSABLE` — Avaliação parcial | sem bloqueio, com algo que o modelo não avalia (variável, credencial, condição de espera) |
| `NO_STATIC_BLOCKERS` — Sem bloqueios estáticos | nada acima; **não** significa pronta para execução |

Não existe estado "pronto para executar" (o teste de contrato recusa `READY_FOR_EXECUTION`). Severidades `BLOCKER` (×),
`WARNING` (!), `INFO` (i), fixas por código.

## 5. Regras implementadas

40 códigos em sete famílias — estrutura, ação, parâmetros, dependências, elemento, localizadores (inclusive obstáculos e
limitações declaradas pelo Recorder) e observação. Tabela completa em [validation-model.md](../docs/studio-007/validation-model.md);
regras por família em [action-rules.md](../docs/studio-007/action-rules.md), [parameter-rules.md](../docs/studio-007/parameter-rules.md),
[element-rules.md](../docs/studio-007/element-rules.md), [locator-rules.md](../docs/studio-007/locator-rules.md),
[dependency-rules.md](../docs/studio-007/dependency-rules.md), [observation-boundaries.md](../docs/studio-007/observation-boundaries.md).

## 6. O que o Validator consegue e o que NÃO consegue comprovar

| Consegue comprovar (estaticamente) | Não consegue comprovar |
|---|---|
| que a ação está classificada | que a automação executa |
| que parâmetros obrigatórios (valor, opção, tecla, endereço) estão configurados | que um localizador encontra o elemento HOJE (validação foi histórica) |
| que há elemento e localizador; se foram validados na captura; estabilidade, estratégia, ambiguidade e obstáculos registrados | que uma variável está declarada ou terá valor |
| que referências de variável/credencial existem | que uma credencial existe num cofre |
| o que foi observado (sinais, indicador de erro, observação incompleta) | qualquer resultado de negócio (sucesso ou falha) |
| a estrutura (sessões e etapas) | que a condição de espera será atendida |

## 7. Integração com o Studio

Menu **Automação** (Nova sessão, Nova etapa, Validar automação), botão na barra e **Ctrl+Shift+V**. Painel abaixo da
área de trabalho: estado (com "não executou nenhuma etapa e não consultou o navegador"), totais, filtro, lista com
símbolo e nome da severidade, "Como corrigir", **Ir para etapa** e **Editar etapa** (editor existente; não há editor no
painel), Esc fecha. Relatório desatualizado anunciado em destaque a cada mudança (edição, Undo/Redo, evidência, etapa do
Recorder, renomear, recarregar). Validar não edita, não entra no histórico, não marca "não salvo" e não grava nada.
[ui-ux.md](../docs/studio-007/ui-ux.md), [workspace-integration.md](../docs/studio-007/workspace-integration.md).

## 8. Privacidade e CHROME-SECURITY-001

Nenhum caminho novo para dados capturados; `nekkora-privacy/2` intacta. Nome ou identificadores omitidos e localizadores
ou opção descartados pela política não são defeito (informação no máximo; nada sugere desativar a proteção).
Continuidade técnica de um nó não é tratada como o mesmo alvo funcional (`RECORDER_LIVE_CHECK_UNAVAILABLE`).
[privacy.md](../docs/studio-007/privacy.md).

## 9. Testes

### Regressão final (commit `09083ea`)

| Execução | Resultado |
|---|---|
| Windows 11, `./mvnw clean verify` (Bridge e dados isolados) | **464/464**, BUILD SUCCESS |
| Linux de referência, `timeout 2400 sh tools/golden-linux.sh verify` | **464 executados: 463 aprovados + 1 não aplicável** (a mesma *assumption* de superusuário da STUDIO-006), BUILD SUCCESS |
| Golden screenshots | 4/4 nos dois sistemas, **sem regenerar** |

Por módulo (iguais nos dois sistemas): tokens 24, swing-ui 48 (+1), showcase 5, domínio 93, bridge 22, recorder 62,
persistência 44, **validação 27 (novo)**, studio-ui 14, app 123 (+9), escala 150% 2 (+1). Total 464 = 426 do baseline +
38 novos. Nenhum teste anterior foi removido, afrouxado ou alterado.

Testes novos: `AutomationValidatorTest` (21), `ValidationContractTest` (5), `ValidationPerformanceTest` (1),
`ValidationStudioTest` (9), `Scale150Test.validacao150`, `LayoutRegressionTest.listaComNomeAcessivelPorLinha`. Matriz
S007-001 a 052 e casos acrescentados (053 a 059) em [test-plan.md](../docs/studio-007/test-plan.md).

Extensão (Java 271 / Node 148 / Chrome 251): não repetida — nenhuma alteração na extensão nem no protocolo; a
integração foi exercida de novo na homologação (gravação real e evidência).

## 10. Desempenho

Motor, etapas realistas (`performance-windows.json`, `performance-linux.json`, `09083ea`):

| Etapas | Windows (mediana / máx.) | Linux (mediana / máx.) | Apontamentos |
|---:|---:|---:|---:|
| 10 | 0,20 / 0,29 ms | 0,46 / 0,67 ms | 35 |
| 100 | 0,83 / 0,99 ms | 0,62 / 0,90 ms | 351 |
| 1.000 | 5,20 / 6,43 ms | 4,56 / 10,79 ms | 3.494 |
| 5.000 | 8,08 / 9,19 ms | 7,65 / 21,20 ms | 17.472 |

Studio real (homologação, 5.000 etapas, atalho real): motor 11 ms; **maior espera da EDT 35 ms** enquanto o painel
recebe 6.000 linhas. Antes da correção de `09083ea` era 316 ms (ver §12).
[performance.md](../docs/studio-007/performance.md).

## 11. Homologação real

Execução final: **29 verificações, 0 falhas**, sobre `09083ea` (Windows 11, JDK 21, Chrome for Testing 155.0.8059.39,
extensão da `main` `2c7c06b`, Native Host instalado pelo script oficial, laboratório fictício `/lab/studio005/login-erro.html`,
dados e Bridge isolados). Mouse e teclado do sistema (Robot). Log: [`S007-homologacao-log.txt`](studio-007/evidence/S007-homologacao-log.txt);
logs do Studio: [`S007-logs-do-studio.txt`](studio-007/evidence/S007-logs-do-studio.txt); capturas `S007-01` a `S007-06`
(só o conteúdo da janela do Studio e do editor de etapa).

| Cenário | O que foi feito de verdade | Resultado |
|---|---|---|
| 1 — Automação incompleta | automação com navegar, preencher, selecionar e aguardar sem parâmetros; **menu Automação → Validar automação**; troca real de tema (Ctrl+Shift+T) nos quatro temas | OK: menu e botão; 6 apontamentos (endereço, valor, opção, condição de espera…); linha "× Bloqueio · Sessão 1 · Etapa 01 — Abrir portal"; observação de não execução visível; mesma geração e mesmo histórico |
| 2 — Correção manual | clique real no apontamento, **Ir para etapa**, **Editar etapa** → editor existente, aba Parâmetros, Variável `cliente.nome`, Salvar; **Ctrl+Shift+V** | OK: etapa selecionada; "Resultado desatualizado" depois da edição; bloqueio some, dependência não comprovada aparece |
| 3 — Undo/Redo | **Ctrl+Z** real, validar, **Ctrl+Y** real, validar | OK: desatualizado após Ctrl+Z; o bloqueio reaparece; some de novo após Ctrl+Y |
| 4 — Projeto salvo | Salvar pelo diálogo, validar, Fechar projeto, reabrir pelos Recentes, validar; abrir pelo **Ctrl+O** a fixture `.nekkora` 1.0.0 da STUDIO-006, validar, fechar | OK: continua "Salvo" sem `*`; fecha sem perguntar; mesmos apontamentos e ids depois de reabrir; arquivos com o mesmo SHA-256; nenhum arquivo de validação; projeto da STUDIO-006 validado (9 etapas, `INCOMPLETE`) e fechado sem perguntas |
| 5 — Evidências | gravação real (Capturar e interagir) no laboratório: usuário e senha **digitados**, **validar durante a gravação**, clique em Entrar com mensagem de erro real; finalizar; validar | OK: validar durante a gravação não interfere; a etapa seguinte deixa o resultado desatualizado; Entrar com `OBSERVATION_SIGNALS` e `OBSERVATION_ERROR_INDICATOR` só como informação; `NOT_VALIDATED`; campos gravados continuam com bloqueio (o que foi digitado nunca é guardado); nada da página nem do que foi digitado no painel ou no relatório |
| 6 — Projeto extenso | projeto sintético de 5.000 etapas aberto pelo Ctrl+O, **Ctrl+Shift+V**, clique na lista | OK: 5.000 etapas validadas; maior espera da EDT 35 ms; lista navegável |
| Logs | handler sobre o `java.util.logging` | OK: só registros técnicos, sem o que foi digitado nem nomes de variáveis |

## 12. Defeitos encontrados e corrigidos durante a missão

| Achado | Onde | Correção |
|---|---|---|
| Linhas de lista do design system invisíveis para leitores de tela (renderizador só pintava) | revisão de acessibilidade | `NekkoraList` expõe o nome acessível de cada linha (`57ba159`), sem mudar a pintura; teste novo |
| A 150% em 1366×768 a área de trabalho ficava com altura zero ao abrir o painel | `Scale150Test.validacao150` | divisor pela altura real, alturas mínimas (`57ba159`) |
| "✕" (U+2715) aparecia como quadrado vazio: a fonte não tem o caractere | conferência visual nos 4 temas | símbolo "×" (U+00D7) (`fb61e7b`) |
| Em janela estreita (900 px), rodapé e totais cortados; cabeçalho e rodapé podiam se sobrepor | conferência visual | layout compacto e altura mínima calculada (`fb61e7b`) |
| Validar 5.000 etapas segurava a EDT ~300–400 ms (motor 10 ms): cada linha procurava a etapa na automação inteira | homologação, cenário 6 | índice de etapas e sessões uma vez por relatório (`09083ea`): 35 ms |

A primeira execução da homologação parou no cenário 5 por erro do **roteiro** (esperava a etapa da senha antes de o
campo perder o foco; a etapa só é consolidada no blur); corrigido em `09083ea`, sem mudança de produto.

## 13. Critérios de aceite

| ID | Critério | Evidência |
|---|---|---|
| CA-001 | Validação independente da interface | módulo Java puro; `ValidationContractTest.independente` |
| CA-002 | Regras baseadas no ActionType existente | `AutomationValidator`; [action-rules.md](../docs/studio-007/action-rules.md) |
| CA-003 | Parâmetros obrigatórios | S007-006 a 015 |
| CA-004 | Referências de elementos | S007-005, 019, 020 |
| CA-005 | Localizadores | S007-020 a 025 |
| CA-006 | Dependências não verificáveis explicitadas | S007-008/009; `NOT_FULLY_ASSESSABLE` |
| CA-007 | Resultados determinísticos | S007-031 a 033; fixture congelada |
| CA-008 | Identificação estável | S007-032; cenário 4 |
| CA-009 | Relatório imutável | `imutavel` |
| CA-010 | Nenhuma execução | S007-041; nenhum executor |
| CA-011 | Nenhuma consulta ao navegador | S007-042; módulo sem Bridge |
| CA-012 | Nenhuma alteração na extensão | árvore = `main` `2c7c06b` |
| CA-013 | Privacidade | S007-026/027; cenário 5 |
| CA-014 | Evidências não viram sucesso | S007-028 a 030; cenário 5 |
| CA-015 | Integração com o editor | menu, botão, atalho, editor existente; cenários 1–2 |
| CA-016 | Navegação até problemas | S007-045; cenário 2 |
| CA-017 | Undo/Redo | S007-034 a 036; cenário 3 |
| CA-018 | Validação não altera o projeto | S007-038 a 040; cenários 1 e 4 |
| CA-019 | `.nekkora` 1.0.0 preservado | sem mudança no formato; `ProjectFormatContractTest` intacto |
| CA-020 | Projetos existentes abrem | fixture da STUDIO-006 aberta e validada (cenário 4) |
| CA-021 | Quatro temas | S007-048; cenário 1 |
| CA-022 | Testes anteriores preservados | 426 do baseline continuam, nenhum alterado |
| CA-023 | Matriz S007 | [test-plan.md](../docs/studio-007/test-plan.md) |
| CA-024 | Homologação real | §11, 29/29 |
| CA-025 | PR sem merge | PR aberto para revisão |

## 14. Limitações e riscos residuais

Ver [known-limitations.md](../docs/studio-007/known-limitations.md). Principais: sem verificação ao vivo de
localizadores; variáveis e credenciais nunca comprovadas; `WAIT_FOR` sem contrato; etapas manuais não recebem elemento
pelo editor; atualidade conservadora (voltar ao estado validado também marca desatualizado); relatório não persistido;
sem golden screenshot do painel (os goldens são do design system; o painel é coberto por testes de renderização, 150% e
homologação).

| Risco | Avaliação |
|---|---|
| Usuário ler "Sem bloqueios estáticos" como "pronta" | texto do estado diz o contrário; cor de informação, nunca de sucesso |
| Códigos mudarem de significado | versão das regras no relatório e fixture congelada |
| Recorder sem autorização para dados reais | inalterado; homologação só com laboratório fictício |

## 15. Preparação para as próximas missões

- STUDIO-008 (proposta: dependências e variáveis) pode fazer `VARIABLE_RESOLUTION_UNAVAILABLE` dar lugar a regras de
  resolução com códigos novos numa nova versão das regras.
- STUDIO-009 (proposta: verificação ao vivo) complementa `LOCATOR_VALIDATED_AT_CAPTURE` com uma verificação atual,
  separada da histórica.
- O relatório JSON já é consumível por Agent e Platform.

Nenhuma funcionalidade da STUDIO-008 foi iniciada. Nada foi executado, nenhum dado real foi usado, a extensão não mudou.
