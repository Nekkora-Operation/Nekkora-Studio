# STUDIO-004 — Arquitetura do editor

A STUDIO-004 amplia o Studio existente: não há um editor paralelo. O caminho de toda alteração continua o mesmo.

```
Interação do usuário (formulário, atalho, menu, painel)
        │
        ▼
StudioController ── confirmações e formulários (StudioPrompts)
        │
        ▼
WorkspaceService ── bloqueio por gravação (RecordingGuard) · histórico (EditHistory)
        │
        ▼
Automation ── regras de domínio (validar antes de alterar; uma revisão por operação)
        │
        ▼
StudioState ── seleção por automação, alterações não salvas
        │
        ▼
StudioEvent ── sessões afetadas (uma, duas ou "estrutura")
        │
        ▼
StudioView / AutomationOverview / LogicalSessionView / PropertiesPresenter
```

Nenhum componente Swing altera as listas de sessões ou etapas. Elas só mudam por operações de `Automation`, chamadas
pelo `WorkspaceService` na EDT.

## Camadas

| Camada | O que mudou |
|---|---|
| Domínio (`nekkora-studio-domain`) | Operações novas em `Automation`. A etapa separa o estado efetivo do `CapturedBaseline`. Novos tipos: `StepEdit`, `StepField`, `ManualRevision`, `ManualLocator`, `Automation.Snapshot`; `StepComment` ganhou `editedAt` |
| Aplicação (`nekkora-studio-app`) | Casos de uso no `WorkspaceService`, `EditHistory`, `RecordingGuard`, controlador, ações (F2, Ctrl+Z, Ctrl+Y) e eventos com várias sessões |
| Interface do Studio (`nekkora-studio-ui`) | `StepDetailsForm` evoluído para o editor completo; `MoveStepForm`; observações na sessão; menus e atalhos na lista; painel com ações e "Origem e alterações" |
| Design System (`nekkora-swing-ui`) | Desfazer a digitação no próprio campo de texto (`TextUndo`). Sem mudança visual |
| Recorder (`nekkora-studio-recorder`) | Sem mudança. A aplicação lê o estado vivo dele para o bloqueio |

## Reaproveitado (auditoria da fase 1)

- `Automation.updateDetails`, `renameSession`, `describeSession`, `appendStep`, `discardRecording` e `updateStepBasics`.
  Este último agora é um atalho de `editStep`.
- `ActionParameters`, `ValueSource` (`LiteralValue`, `VariableReference`, `CredentialReference`) e `OptionChoice`, sem
  um segundo modelo de parâmetros.
- `ElementReference`, `LocatorCandidate.validated()`, `StepProvenance` e `RecordingObservation`, sem duplicação.
- `StudioForm`, `FormSpec`, `StudioDialogs.trySubmit` e `StepForm`: o editor reaproveita o formulário básico de etapa.
- A atualização incremental da STUDIO-002 (`refreshSession`), agora para N sessões (`refreshSessions`).
- O `StepIntake` da STUDIO-003, que continua sendo a única entrada das etapas gravadas e fica fora do histórico.

## Contratos

`nekkora-bridge/1` e `nekkora-pagemap/0.2.0` não mudaram. A extensão não foi alterada.
