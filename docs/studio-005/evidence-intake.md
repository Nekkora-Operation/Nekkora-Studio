# STUDIO-005 — EvidenceIntake (entrada de evidências)

Porta `com.nekkora.studio.domain.port.EvidenceIntake`, **separada** da `StepIntake`:

```java
boolean attachEvidence(AutomationId automation, ObservedEvidence evidence);
```

## Regras (em `Automation.attachEvidence`)

1. **Ambígua** (`eventId == null`): vai para a lista de não atribuídas. Repetida não muda nada. Acima de 500, é recusada.
2. **Com etapa**: a etapa precisa existir e ser **GRAVADA** por esta gravação, com o mesmo evento
   (`CorrelationKeys.RECORDING_ID` + `RECORDED_EVENT_ID`). Senão, `EVIDENCE_STEP_NOT_FOUND`.
3. A etapa precisa ter sido **executada** (`INTERACTION_OBSERVED`). Uma proposta de `CAPTURE_ONLY`, ou uma etapa manual
   ou duplicada, não tem consequência: `EVIDENCE_NOT_APPLICABLE`.
4. Repetida (mesmo `evidenceId`) → `false`, e nada muda.
5. Limite de 100 por etapa → `EVIDENCE_LIMIT_REACHED`.
6. **Nada mais muda**: identidade, ordem, sessão, valores originais (`baseline`), tipo, parâmetros, comentários,
   revisão e histórico continuam como estavam.

## Na aplicação (`WorkspaceService.attachEvidence`)

- Só na EDT (`requireEdt`). O `WorkspaceEvidenceSink` faz a transferência com `SwingUtilities.invokeLater` e completa
  o futuro **depois** da operação de domínio. Só então o Recorder confirma à extensão.
- **Permitida durante a gravação**: o bloqueio de edição (`RecordingGuard`, S004) vale para edições, e a evidência não é edição.
- Não entra no `EditHistory` nem conta revisão.
- Notifica `StudioChange.EVIDENCE` (+ `UNSAVED_CHANGES`) só para a sessão da etapa. A tela atualiza a lista dessa
  sessão e o painel, sem tocar na seleção.
- Recusa do domínio → `false` no futuro → o Recorder informa a sequência em `rejected[]` no ACK.

## No Recorder

- `EvidenceInbox`: ordem por `evidenceSequence`, deduplicação por sequência e `evidenceId`, até 500 fora de ordem e
  pedido de `evidence.sync` nas lacunas (também quando `renew`/`status`/ACK mostram `lastSequence` maior).
- Evidência cuja etapa ainda não chegou espera numa fila por `eventId` (até 500 no total). Quando a etapa entra na
  fila de entrega, as evidências dela entram **logo depois**, na mesma fila serializada. Acima do limite, a evidência
  é recusada e declarada; nenhuma etapa é inventada.
- ACK cumulativo até a maior sequência **contígua** concluída (aceita ou recusada).
- Cancelamento confirmado: `halted`. Nada mais entra, e o descarte da gravação remove etapas e evidências.

## Testes

`EvidenceDomainTest` (12), `RecorderEvidenceTest` (12) e `RecorderEvidenceStudioTest` (6).
