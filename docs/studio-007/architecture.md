# STUDIO-007 — Arquitetura do Automation Validation Engine

## Visão geral

```text
Nekkora Studio (Swing)
   │  menu Automação › Validar automação · barra de ferramentas · Ctrl+Shift+V
   ▼
StudioController.validateActive / goToFinding / editFinding        (app, ui)
   │
   ▼
ValidationService                                                    (app, workspace — EDT, somente leitura)
   │  último relatório por automação + atualidade (objeto da automação + geração de persistência)
   ▼
AutomationValidator                                                  (nekkora-studio-validation — Java puro)
   │  regras de estrutura, ação, parâmetros, dependências, elemento, localizadores,
   │  obstáculos e limitações do Recorder, limites da observação
   ▼
ValidationReport (imutável, determinístico)  ── toJson() ──►  nekkora-studio/validation-report 1.0.0
   │
   ▼
ValidationPanel                                                      (nekkora-studio-ui — Swing, design system)
```

## Módulos

| Módulo | Papel | Depende de |
|---|---|---|
| `nekkora-studio-validation` (novo) | motor, catálogo de códigos, relatório e JSON canônico | só `nekkora-studio-domain` (o Bridge entra apenas nos testes, para ler JSON) |
| `nekkora-studio-ui` | `ValidationPanel` (apresentação; não edita nada) | domínio, validação, design system |
| `nekkora-studio-app` | `ValidationService`, ações, menu, integração com o workspace | todos |

O motor **funciona sem interface**: um teste Java faz `new AutomationValidator().validate(automation)` sem Swing,
Chrome, Native Host, Recorder, servidor, disco ou banco. `ValidationContractTest.independente` varre o código do módulo
e recusa `java.awt`, `javax.swing`, `java.net`, `java.nio.file`, threads, relógio, aleatoriedade e os pacotes de Bridge,
Recorder, persistência, app e design system. Isso prepara a reutilização pelo Nekkora Agent e pela Platform.

## Reutilização do domínio (nada duplicado)

| Necessidade | Fonte existente |
|---|---|
| tipos de ação, alvo esperado, aceita valor/opção | `ActionType`, `TargetRequirement`, `acceptsValue()`, `acceptsOption()` |
| parâmetros | `ActionParameters`, `ValueSource` (`LiteralValue`, `VariableReference`, `CredentialReference`), `OptionChoice` |
| elemento e localizadores | `ElementReference`, `LocatorCandidate` (`validated()`, `matchCount`, `stability`, `strategy`), `ManualLocator`, `ObservedObstacle` |
| limitações da captura | `RecordingObservation.limitations()` (códigos do protocolo do Recorder) |
| evidências | `ObservationSummary.of(step)` (STUDIO-005) |

O motor não cria um segundo catálogo de ações nem classes paralelas às do domínio: o relatório só referencia
identidades (`AutomationId`, `SessionId`, `StepId`).

## Fluxo de uma validação

1. O usuário pede "Validar automação" (menu, botão ou Ctrl+Shift+V).
2. `ValidationService.validate` roda na **EDT**, de uma vez, sobre o estado EFETIVO atual (5.000 etapas ≈ 6 ms):
   nenhuma alteração pode acontecer no meio da análise (o domínio é confinado à EDT).
3. O relatório é guardado com o "carimbo" (objeto da automação, objeto e geração do estado de persistência) e um
   evento `StudioChange.VALIDATION` atualiza o painel.
4. Cada alteração posterior (edição, Undo/Redo, evidência, etapa do Recorder, renomear, recarregar) faz o painel
   mostrar "Resultado desatualizado" até uma nova validação.

Ver [workspace-integration.md](workspace-integration.md), [validation-model.md](validation-model.md) e
[deterministic-results.md](deterministic-results.md).
