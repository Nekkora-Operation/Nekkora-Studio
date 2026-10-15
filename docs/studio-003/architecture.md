# STUDIO-003 — Arquitetura da captura funcional de interações

```
Chrome (página)                      Chrome Extension                         Native Host            Nekkora Studio
───────────────                      ────────────────                         ───────────            ──────────────
eventos do usuário ──► sensor (content, por documento) ──► normalizador ──┐
                                                                         ▼
                       service worker: sequência ─► correlação ◄──► recorder.element.describe/verify (motor Java)
                                                │                                │
                                                ▼                                ▼
                                    studio.recording.step ──(nm/1)──► StepPrivacy + schema ──(bridge/1)──►
                                                                                                  RecorderController
                                                                                                  StepInbox (ordem/dedup)
                                                                                                  rota → sessão lógica
                                                                                                  RecordedStepDrafts → StepDraft
                                                                                                  StepSink → StepIntake (EDT)
                                                                                                  ◄── ACK (após o domínio)
```

| Responsabilidade | Componente | Repositório |
|---|---|---|
| Observar interações e identificar o alvo funcional | sensor `interaction-recorder.js` | extensão |
| Transformar eventos em operações (regras determinísticas) | `interaction-normalizer.js` | extensão |
| Identificar o elemento (localizadores, identidade, trajetória) | motor Java + `recorder.element.describe` | extensão |
| Ordenar, entregar, reter, retransmitir | `studio-steps.js` | extensão |
| Receber, ordenar, rotear por sessão, montar a etapa | `RecorderController`, `StepInbox`, `RecordedStepDrafts` | Studio |
| Inserir no domínio | `StepIntake` (`WorkspaceService.appendStep`) via `WorkspaceStepSink` | Studio |
| Apresentar | `LogicalSessionView` (atualização incremental), Floating Recorder, painel de propriedades | Studio |

O Studio **não** analisa DOM nem recalcula localizadores. O normalizador fica na extensão porque as regras dependem do
DOM: alvo funcional, label → controle, select/option e ativação de checkbox. O Studio aplica só regras de
apresentação: o nome da instrução em português e a procedência.

## Decisões

| Id | Decisão |
|---|---|
| D-STUDIO-003-01 | Normalizador determinístico na extensão; o Studio monta o `StepDraft` |
| D-STUDIO-003-02 | Sensor por documento acompanhado; ouvintes passivos em CAPTURE_AND_INTERACT e interceptação em CAPTURE_ONLY |
| D-STUDIO-003-03 | Prioridade explícita: com os sensores ligados, o Inspector fica SUSPENDED (consumidor preservado) e volta depois |
| D-STUDIO-003-04 | Capacidade negociada `steps/1`; sem ela, gravação só técnica, como na STUDIO-002 |
| D-STUDIO-003-05 | Sequência atribuída no service worker na chegada; entre documentos vale a ordem de recepção (documentada); timestamps só informativos |
| D-STUDIO-003-06 | Outbox até o ACK, que o Studio só envia depois do domínio; sync; limite de 100 pendentes → DEGRADED, sem descarte |
| D-STUDIO-003-07 | Rota opaca por sessão lógica (`routeRef`), carimbada em cada etapa na chegada; a troca de sessão consolida antes |
| D-STUDIO-003-08 | Referência compacta pelo motor Java numa chamada, com verificação ao vivo dos localizadores; sem correlação → `UNRESOLVED` |
| D-STUDIO-003-09 | Privacidade em três barreiras (content, service worker, host) e contrato sem campo de valor |
| D-STUDIO-003-10 | Catálogo: `PRESS_KEY` acrescentado (Enter num campo); radio = `CHECK`; nenhuma etapa NAVIGATE ou SUBMIT automática |
| D-STUDIO-003-11 | `StepProvenance.recording` (`RecordingObservation`): modo, proposta × interação observada, resultado de negócio não validado |
| D-STUDIO-003-12 | Cancelamento confirmado → `Automation.discardRecording` (uma operação, só as etapas da gravação) |
| D-STUDIO-003-13 | Finalizar: consolida, espera as confirmações por até 5 s; `actionCapture` NOT_AVAILABLE, AVAILABLE ou PARTIAL |
| D-STUDIO-003-14 | Navegação como contexto da etapa anterior; preenchimento aberto num documento substituído → perda declarada |

## Threading (STUDIO-002 preservado)

- Eventos do Bridge chegam na thread do servidor.
- O `RecorderController` processa tudo na sua thread única.
- A `WorkspaceStepSink` entrega ao domínio por `SwingUtilities.invokeLater`, na EDT.
- O futuro da entrega completa depois da operação de domínio, e só então o ACK sai.
- A tela é atualizada pelo evento de alteração da **sessão afetada** (atualização incremental da STUDIO-002, seção
  20), sem reconstruir todas as sessões.
- Verificação: `RecorderStepsStudioTest.s003LoginGravadoNoStudio`, com o `RepaintManager` verificador e 0 violações.
