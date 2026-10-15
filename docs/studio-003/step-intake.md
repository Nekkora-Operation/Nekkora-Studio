# STUDIO-003 — Entrada das etapas gravadas pelo StepIntake

A porta `StepIntake` (STUDIO-001) é o **único** caminho das etapas gravadas para o domínio. Nenhum clique é simulado
e nenhum componente Swing é tocado diretamente.

## Fluxo

1. `RecorderController.onStep` (thread do Recorder) valida a etapa (`RecordedStep.parse`). Ela é ignorada se for de
   outra gravação, de gravação cancelada ou expirada, ou se estiver fora do contrato.
2. `StepInbox.accept` ordena pela sequência e descarta repetidas (mesma sequência ou mesmo `eventId`). As fora de
   ordem ficam retidas, e a lacuna gera um pedido de reemissão.
3. Para cada etapa pronta, em ordem:
   - a rota leva à sessão lógica (`routeRef` → `SessionId`);
   - `RecordedStepDrafts.draft` monta o `StepDraft`;
   - a entrega vai pela cadeia sequencial `chain`: uma de cada vez, na ordem.
4. `WorkspaceStepSink.deliver`, na EDT, chama `WorkspaceService.appendStep`, que dispara a alteração da sessão
   afetada. O futuro completa com `true` (aceita) ou `false` (o domínio recusou: `DomainValidationException`).
5. De volta à thread do Recorder: contagem atualizada e ACK cumulativo (`upToSequence`, `rejected[]`).

## Garantias

- **Confirmação só depois do domínio.** Nenhum ACK sai antes de o futuro completar
  (`RecorderStepsTest.s003034…`: com o domínio "travado", nenhum ACK e 0 registradas; depois, o ACK até 2).
- **Recusa nunca conta como registrada.** Vai em `rejected` e é mostrada no Floating Recorder ("1 etapa não pôde ser
  registrada pelo Studio").
- **Ordem.** A entrega é sequencial, e o domínio recebe na ordem da sequência mesmo que os eventos cheguem fora de
  ordem.
- **EDT.** O domínio só é alterado na EDT; a interface nunca é bloqueada esperando o navegador.
- **Cancelamento.** Confirmado o CANCELLED, `discardRecording` (EDT) remove numa operação as etapas RECORDED daquela
  gravação. Etapas manuais e de outras gravações ficam. Se a etapa selecionada sumiu, a seleção volta à sessão.

## Procedência

Toda etapa gravada recebe `StepOrigin.RECORDED` e `CorrelationKeys` internas (`RECORDING_ID`, `RECORDED_EVENT_ID`,
`TARGET_ID`, `NODE_ID`, `CAPTURE_ID`, `DOCUMENT_ID`), que nunca são exibidas. Também recebe a `RecordingObservation`
(ver `recorded-step-model.md`).
