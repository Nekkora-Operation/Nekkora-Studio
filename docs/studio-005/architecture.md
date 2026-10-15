# STUDIO-005 — Arquitetura (Studio)

```
Bridge (thread do Bridge)
   │ studio.recording.evidence
   ▼
RecorderController (thread do Recorder)
   ├─ RecordedEvidence.parse      leitura estrita + 4ª barreira de privacidade
   ├─ EvidenceInbox               ordem por evidenceSequence, deduplicação, lacunas, ACK contíguo
   ├─ espera da etapa             evidência de etapa ainda não recebida fica numa fila limitada (500)
   ├─ fila única (chain)          etapas e evidências entram no domínio NA ORDEM (evidência depois da etapa)
   └─ ack/sync                    studio.recording.evidence.ack depois do domínio; sync nas lacunas
         │
         ▼ EvidenceSink (WorkspaceEvidenceSink → SwingUtilities.invokeLater)
WorkspaceService (EDT) — EvidenceIntake.attachEvidence
   ├─ Automation.attachEvidence   domínio: etapa RECORDED desta gravação + evento; executada; idempotente; limite
   ├─ sem histórico, sem revisão  StudioChange.EVIDENCE (só as sessões afetadas)
   ▼
UI: lista (selo), painel (grupo Evidências), detalhes (aba Evidências), Floating Recorder (contadores)
```

## Módulos tocados

| Módulo | Acréscimos |
|---|---|
| `nekkora-studio-domain` | pacote `evidence` (`EvidenceType`, `EvidenceVocabulary`, `ObservedEvidence`, `ObservationSummary`), `AutomationStep.evidence()/observation()`, `Automation.attachEvidence/unattributedEvidence/recordedStep`, porta `EvidenceIntake`, 3 `ValidationProblem` |
| `nekkora-studio-bridge` | `BridgeProtocol` (comandos e evento novos, capacidades) e cópia do contrato/fixtures |
| `nekkora-studio-recorder` | `RecordedEvidence`, `EvidenceInbox`, `EvidenceSink`, `EvidenceProgress`, `RecorderSnapshot.evidence`, `RecordingSummary.evidence`, controlador |
| `nekkora-studio-app` | `WorkspaceService` como `EvidenceIntake`, `WorkspaceEvidenceSink`, `StudioChange.EVIDENCE`, montagem em `Studio`, `StudioView` |
| `nekkora-studio-ui` | `EvidencePresentation`, grupo no `PropertiesPresenter`, aba em `StepDetailsForm`, selo em `LogicalSessionView`, Floating Recorder/`RecorderText` |

## Fluxo de uma mensagem de erro (cenário 1 da homologação)

1. O usuário clica em "Entrar". A extensão emite a etapa 3 e, depois, as evidências `FORM_SUBMITTED`,
   `VISIBLE_ERROR_MESSAGE` ("Usuário ou senha inválidos.") e `OBSERVATION_COMPLETE`.
2. O Recorder valida, ordena e espera a etapa 3 entrar no domínio (mesma fila).
3. Na EDT, `attachEvidence` encontra a etapa por `RECORDING_ID` + `RECORDED_EVENT_ID` e acrescenta a evidência. A revisão não muda.
4. O ACK sobe até a maior sequência contígua concluída.
5. A tela atualiza só a sessão afetada: o selo "Indicador de erro observado", o grupo Evidências e "Evidências: N registradas" no Floating Recorder.

## O que o Studio não faz

Não executa JavaScript, não pede inspeção à página, não interpreta texto, não valida negócio e não persiste em
definitivo (STUDIO-006).
