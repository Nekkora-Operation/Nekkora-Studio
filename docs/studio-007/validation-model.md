# STUDIO-007 — Modelo de validação

## Validação estrutural ≠ validação de execução

| Tipo | Verifica | STUDIO-007 |
|---|---|---|
| domínio | regras internas do modelo | já existia (recusa estados inválidos) |
| **estrutural** | ações, parâmetros e referências suficientemente configurados | **implementada** |
| **dependências** | referências com resolução demonstrável | **diagnosticada** (nada é resolvido nem consultado) |
| localizadores ao vivo | se o seletor encontra o elemento agora | não implementada |
| execução | se a automação funciona | não implementada |
| negócio | se o resultado atende à regra | não implementada |

## Estados (`ValidationStatus`)

| Estado | Rótulo | Quando |
|---|---|---|
| `INCOMPLETE` | Configuração incompleta | há pelo menos um `BLOCKER` |
| `NOT_FULLY_ASSESSABLE` | Avaliação parcial | sem bloqueios, mas algum apontamento tem `limitsAssessment` (variável, credencial, condição de espera) |
| `NO_STATIC_BLOCKERS` | Sem bloqueios estáticos | nenhum dos anteriores |

Não existe `READY_FOR_EXECUTION`: `NO_STATIC_BLOCKERS` diz apenas que as regras estáticas implementadas não
encontraram bloqueios — a explicação do estado repete isso na interface ("NÃO significa que a automação está pronta
para execução nem que funcionará na página real"). O painel usa a cor de informação (nunca de "sucesso") para ele.

## Severidades (`Severity`)

| Severidade | Símbolo | Significado |
|---|---|---|
| `BLOCKER` | × | falta uma informação obrigatória para a configuração da ação |
| `WARNING` | ! | limitação ou risco técnico conhecido |
| `INFO` | i | informação relevante, sem comprovação de problema |

A severidade é uma propriedade fixa do **código** (`FindingCode`), nunca de texto gerado. O símbolo e o nome
acompanham a cor em todas as linhas do painel.

## Apontamento (`Finding`)

| Campo | Conteúdo |
|---|---|
| `id` | `CÓDIGO:identidade[:detalhe]` — estável para o mesmo estado (ver [deterministic-results.md](deterministic-results.md)) |
| `code` | código estável (40 na versão de regras 1.0.0) |
| `scope` | `AUTOMATION`, `SESSION` ou `STEP` |
| `session`, `step` | identidades, quando aplicável |
| `sessionNumber`, `stepNumber` | posições derivadas (sessão a partir de 1; etapa dentro da sessão a partir de 1) |
| `field` | `STRUCTURE`, `ACTION_TYPE`, `PARAMETERS`, `ELEMENT`, `LOCATORS`, `OBSERVATION` |
| `detail` | só do vocabulário fechado (tipos de obstáculo), ou `null` |
| `explanation`, `guidance` | modelos textuais controlados do código (+ rótulo do detalhe) |

## Catálogo de códigos (regras `nekkora-validation-rules/1.0.0`)

A ordem é a ordem das regras dentro de um escopo. Severidade e campo nunca mudam para um código.

| Código | Sev. | Campo | Limita avaliação | Regra |
|---|---|---|---|---|
| `AUTOMATION_HAS_NO_SESSIONS` | × | estrutura | | automação sem sessões |
| `AUTOMATION_HAS_NO_STEPS` | × | estrutura | | sessões sem nenhuma etapa |
| `UNATTRIBUTED_EVIDENCE` | i | observação | | evidências sem etapa definida |
| `SESSION_EMPTY` | ! | estrutura | | sessão vazia |
| `ACTION_UNCLASSIFIED` | × | tipo | | `UNKNOWN` |
| `WAIT_CONDITION_NOT_MODELED` | ! | tipo | sim | `WAIT_FOR` (sem contrato de condição) |
| `OBSERVE_IS_NOT_VALIDATION` | i | tipo | | `OBSERVE` |
| `REQUIRED_VALUE_MISSING` | × | parâmetros | | `FILL` sem valor |
| `KEY_NOT_CONFIGURED` | × | parâmetros | | `PRESS_KEY` sem tecla |
| `KEY_NOT_RECOGNIZED` | ! | parâmetros | | tecla fora da lista |
| `NAVIGATION_TARGET_MISSING` | × | parâmetros | | `NAVIGATE` sem valor |
| `NAVIGATION_TARGET_NOT_ADDRESS` | ! | parâmetros | | literal sem `http://`/`https://` |
| `OPTION_NOT_CONFIGURED` | × | parâmetros | | `SELECT_OPTION` sem opção |
| `PARAMETER_NOT_APPLICABLE` | ! | parâmetros | | parâmetro que o tipo não usa |
| `VARIABLE_RESOLUTION_UNAVAILABLE` | ! | parâmetros | sim | `VariableReference` |
| `CREDENTIAL_RESOLUTION_UNAVAILABLE` | ! | parâmetros | sim | `CredentialReference` |
| `SENSITIVE_FIELD_WITHOUT_CREDENTIAL` | ! | parâmetros | | campo sensível com variável |
| `ELEMENT_MISSING` | × | elemento | | alvo `EXPECTED` sem elemento |
| `LOCATOR_MISSING` | × | localizadores | | elemento sem nenhum localizador |
| `MANUAL_LOCATOR_ONLY` | ! | localizadores | | só localizadores manuais |
| `LOCATOR_NOT_VALIDATED` | ! | localizadores | | nenhum capturado validado |
| `LOCATOR_MULTIPLE_MATCHES` | ! | localizadores | | idem, com múltiplas correspondências |
| `LOCATOR_VALIDATED_AT_CAPTURE` | i | localizadores | | validação HISTÓRICA |
| `LOCATOR_PREFERENCE_NOT_SET` | i | localizadores | | validados sem preferencial |
| `LOCATOR_STABILITY_UNKNOWN` | i | localizadores | | estabilidade desconhecida |
| `LOCATOR_UNSTABLE` | ! | localizadores | | estabilidade instável |
| `LOCATOR_POSITIONAL` | ! | localizadores | | estratégia posicional |
| `MANUAL_LOCATOR_UNVERIFIED` | i | localizadores | | manuais ao lado de capturados |
| `OBSTACLE_HIGH` / `_MEDIUM` / `_LOW` | ! / ! / i | elemento | | obstáculo observado (detalhe = tipo) |
| `RECORDER_NO_UNIQUE_LOCATOR` | i | localizadores | | limitação da captura |
| `RECORDER_SAFE_LOCATOR_UNAVAILABLE` | i | localizadores | | limitação da captura |
| `RECORDER_LIVE_CHECK_UNAVAILABLE` | i | elemento | | continuidade técnica ≠ mesmo alvo |
| `PRIVACY_LOCATORS_WITHHELD` | i | localizadores | | política de privacidade (não é defeito) |
| `PRIVACY_OPTION_WITHHELD` | i | parâmetros | | idem, só enquanto a opção faltar |
| `OBSERVATION_SIGNALS` | i | observação | | `OBSERVED` |
| `OBSERVATION_ERROR_INDICATOR` | i | observação | | indicador de erro observado |
| `OBSERVATION_INCOMPLETE` | i | observação | | `INCOMPLETE` |
| `OBSERVATION_IN_PROGRESS` | i | observação | | `IN_PROGRESS` |

Detalhes por família: [action-rules.md](action-rules.md), [parameter-rules.md](parameter-rules.md),
[element-rules.md](element-rules.md), [locator-rules.md](locator-rules.md), [dependency-rules.md](dependency-rules.md),
[observation-boundaries.md](observation-boundaries.md).

## Contrato JSON

`ValidationReport.toJson()` → `nekkora-studio/validation-report` 1.0.0. Schema (JSON Schema 2020-12) e fixture
congelada em `nekkora-studio-validation/src/test/resources/contracts/validation-report/1.0.0/`. `ValidationContractTest`
confere: schema = código (códigos, detalhes, severidades, escopos, campos, estados, versões), saída conforme ao schema
e idêntica byte a byte à fixture (identidades normalizadas), e que o validador do teste recusa de fato (inclusive um
estado `READY_FOR_EXECUTION`).

O relatório **não é persistido** no projeto `.nekkora` (ver [decisions.md](decisions.md), D-S007-07).
