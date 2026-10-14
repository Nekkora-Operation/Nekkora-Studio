# STUDIO-001 — Modelo de domínio

Módulo `nekkora-studio-domain`, pacote `com.nekkora.studio.domain`. Java 21 puro, testável sem Swing.

```text
AutomationCatalog                 automações do ambiente (nomes únicos, ordem de criação)
└── Automation                    raiz do agregado — toda alteração passa por ela
    ├── LogicalSession            unidade lógica (ex.: Autenticação, Consulta, Resultado)
    │   ├── AutomationStep        instrução individual
    │   └── AutomationStep
    └── LogicalSession
        └── AutomationStep
```

## Automation

| Campo | Observação |
|---|---|
| `id` (`AutomationId`) | UUID interno; `toString()` = `AutomationId[interno]` |
| `name`, `description` | nome obrigatório (1–120, sem quebras/controle); descrição opcional (≤ 2000) |
| `status` | `AutomationStatus.DRAFT` — rascunho em memória (estados de persistência/execução virão depois) |
| `sessions()` | lista ordenada, somente leitura |
| `createdAt`, `updatedAt` | via `Clock` injetado |
| `modelVersion` | `ModelVersion.CURRENT` = `1.0.0` (schema `nekkora-studio/automation`) |
| `revision()` | contador incrementado a cada operação bem-sucedida |

Operações: `updateDetails`, `addSession`, `renameSession`, `describeSession`, `appendStep`, `updateStepBasics`;
leituras: `session`, `step`, `sessionOf`, `sessionNumber`, `sequence`, `positionOf`, `stepCount`.
Não há vínculo obrigatório com gravação, usuário, organização ou tenant.

## LogicalSession

`id` (`SessionId`), `name` (único na automação, sem diferenciar maiúsculas), `description`, `notes`, `steps()`
(ordem explícita). É uma divisão de **entendimento** do processo: não equivale a URL/janela/página e **não** se
confunde com as sessões técnicas de captura/gravação da Chrome Extension.

## AutomationStep

| Campo | Observação |
|---|---|
| `id` (`StepId`) | identidade permanente; a numeração visual não é identidade |
| `name`, `description` | instrução legível ("Preencher usuário") |
| `actionType` | `ActionType` |
| `provenance` | `StepOrigin` + `CorrelationKeys` internas |
| `element()` | `ElementReference` opcional — etapas podem não envolver HTML |
| `parameters()` | `ActionParameters`: valor (`ValueSource`) e/ou opção (`OptionChoice`) |
| `context()` | `StepContext` opcional (aplicação, tela, endereço) — não presume navegador |
| `comments()` | `StepComment` (edição na STUDIO-004) |
| `observedOutcome()` | condição/resultado observado, quando houver |

Mutadores são restritos ao pacote: só `Automation` altera sessões e etapas.

## Tipos de ação (`ActionType`)

| Tipo | Rótulo | Alvo | Valor | Opção |
|---|---|---|---|---|
| `CLICK` | Clicar | esperado | — | — |
| `FILL` | Preencher | esperado | sim | — |
| `SELECT_OPTION` | Selecionar opção | esperado | — | sim |
| `CHECK` / `UNCHECK` | Marcar / Desmarcar | esperado | — | — |
| `NAVIGATE` | Navegar | nenhum | sim | — |
| `WAIT_FOR` | Aguardar | opcional | — | — |
| `OBSERVE` | Observar | opcional | — | — |
| `UNKNOWN` | Não classificada | opcional | sim | sim |

Cada tipo carrega rótulo, descrição e requisitos; telas e formulários usam esses metadados, então um tipo novo não
exige mudar a interface (o ícone tem padrão em `ActionTypeStyle`). `UNKNOWN` não é oferecido na criação manual.
Nenhuma ação é executada nesta missão.

**Uma instrução, vários elementos** — "Selecionar São Paulo no campo Estado" é **uma** etapa `SELECT_OPTION`: a
referência principal é o `select`, e a opção (`OptionChoice("São Paulo", "SP")`) é parâmetro subordinado. Provado em
`PageMapContractTest.selectEmIframeViraUmaEtapa`.

## Origem das etapas

`MANUAL` (única produzida nesta missão), `RECORDED`, `IMPORTED`, `GENERATED` (previsões contratuais). A origem fica
na etapa (`StepProvenance`) junto com chaves de correlação (`RECORDING_ID`, `CAPTURE_ID`, `TARGET_ID`, `NODE_ID`,
`DOCUMENT_ID`, `FRAME_ID`, ...), que nunca são exibidas.

## ElementReference (fronteira com a Chrome Extension)

Representação **pequena** de um elemento do PageMap 0.2.0 — o Studio não recalcula identidade nem localizadores:

| Parte | Origem no PageMap 0.2.0 |
|---|---|
| `accessibleName`, `semanticKind` (`elementKind`), `tag` | `ElementDescriptor.text.accessibleName`, `.kind`, `.tag` |
| `htmlId`, `htmlName` | `attributes.id/name` (já sanitizados pelo produtor) |
| `locators` (`LocatorCandidate`), `preferredLocator` (só se validado) | `LocatorSet.primary/fallbacks` (`strategy`, `stability`, `matchCount`, `matchesTarget`) |
| `accessPath` (`AccessStep`: MAIN_DOCUMENT/IFRAME/SHADOW_ROOT/ELEMENT) | `ElementAccessPath.steps` |
| `context` (`ContextAnchor`) — sem profundidade fixa | `ContextRelation` (`IN_ROW`, `IN_DIALOG`, `IN_FORM`...) |
| `stabilityNotes`, `obstacles` (`ObservedObstacle`) | `stabilityReasons`, `AutomationObstacle` |
| `sensitive`, `privacyPolicy`, `sourceContract` | `ElementDescriptor.sensitive`, `nekkora-privacy/1`, `nekkora-pagemap/0.2.0` |
| `correlation` | `nodeId`, `targetId`, `documentId`, `captureId` — internas |

Enums espelhados (`LocatorStrategy`, `LocatorStability`, `AccessStep.Type`, `ObservedObstacle.Severity`) são
verificados contra um recorte do contrato (`src/test/resources/contracts/pagemap-0.2.0/enums.json`) e, quando o
repositório da extensão está ao lado, contra o `page-map.schema.json` vivo.

## Ordenação

A sequência é a ordem das listas do agregado (sessões na automação, etapas na sessão) — explícita no modelo, não
derivada do Swing. `Automation.sequence()` devolve `StepPosition(sessionNumber, numberInSession, number)`; a tela
mostra `number` com dois dígitos. Renomear ou editar não altera identidades nem ordem.

## Identificadores internos e privacidade

- `AutomationId`, `SessionId`, `StepId`: `toString()` mascarado; o UUID só sai por `value()` (correlação/persistência).
- `CorrelationKeys.toString()` não revela valores.
- `DisplaySafety`: detecta UUIDs, IDs de contrato (`tgt-`, `doc:`), hashes, tokens opacos e dados sensíveis (e-mail,
  CPF, CNPJ, cartão/Luhn, JWT, Bearer). Usado para filtrar textos técnicos antes de exibir.
- `id`/`name` HTML só são apresentáveis com política de produtor reconhecida (`nekkora-privacy/1`), elemento não
  sensível e texto seguro.
- Senhas: elemento `sensitive` nunca recebe `LiteralValue`; `LiteralValue` recusa textos com aparência de segredo.
  Use `VariableReference` ou `CredentialReference` (apenas nomes — o cofre é de missão futura).

## Preparação para versionamento (P9)

`modelVersion` em cada automação; campos novos entram como opcionais (MINOR); mudanças incompatíveis exigem migração
explícita (MAJOR). Objetos de valor são imutáveis; o agregado expõe apenas leituras e operações nomeadas.
