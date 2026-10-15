# STUDIO-002 — RecorderController e máquina de estados

Código: `nekkora-studio-recorder` (Java puro, sem Swing).

## Responsabilidades

`RecorderController` inicia, pausa, retoma, altera o modo, finaliza e cancela a gravação. Também consulta o estado
real, processa confirmações e eventos, renova a concessão, associa a gravação à sessão lógica e detecta desconexões.
Toda a lógica fica aqui: a janela flutuante é só uma visão (`FloatingRecorderPanel`) e o `RecorderPresenter` só liga
as duas pontas.

- **Uma thread própria** (`nekkora-recorder`): os métodos públicos só enfileiram (nunca bloqueiam quem chama, inclusive
  a EDT), e todas as transições acontecem nessa fila.
- **Fotografias imutáveis** (`RecorderSnapshot`) para os ouvintes, que repassam para a EDT. Elas não contêm
  identificadores técnicos: a automação e a sessão vêm como ids de domínio, para a interface resolver os nomes, e a aba
  como título e host.
- **A verdade é da extensão**: respostas e eventos trazem a fotografia completa da gravação. Uma fotografia mais antiga
  do que a última aplicada (`updatedAt`) é ignorada, o que evita que um evento atrasado faça o estado regredir.

## Estados

| Estado | Rótulo | Significado |
|---|---|---|
| `IDLE` | Pronto para gravar | nenhuma gravação |
| `STARTING` | Iniciando… | `start` enviado, sem confirmação |
| `RECORDING` | Captura técnica ativa | confirmado: consumidor inscrito e página observada |
| `PAUSING` / `PAUSED` | Pausando… / Pausada | pausa **do usuário**, confirmada (o consumidor foi liberado). Não é o `PAUSED` técnico da CaptureSession |
| `RESUMING` | Retomando… | retomada enviada |
| `FINALIZING` / `COMPLETED` | Finalizando… / Concluída | finalização confirmada, com resumo técnico |
| `CANCELLING` / `CANCELLED` | Cancelando… / Cancelada | cancelamento confirmado |
| `DEGRADED` | Captura interrompida | a página deixou de ser acompanhada (aba fechada, permissão revogada, erro) **ou** a ligação com o navegador caiu (`reason = LINK_LOST`) |
| `RECOVERING` | Recuperando… | consultando o estado real para reconciliar, ou tentando retomar uma interrupção |
| `ERROR` | Erro | perda de controle (concessão expirada, gravação desconhecida, encerrada só no Studio) |

## Transições permitidas (`RecorderTransitions`)

```
IDLE ─► STARTING ─► RECORDING ─► PAUSING ─► PAUSED ─► RESUMING ─► RECORDING
                        │                      │
                        ├─► FINALIZING ─► COMPLETED    (também de PAUSED e DEGRADED)
                        ├─► CANCELLING ─► CANCELLED    (também de PAUSED e DEGRADED)
                        └─► DEGRADED ─► RECOVERING ─► RECORDING | PAUSED | DEGRADED | COMPLETED | CANCELLED | ERROR | IDLE
COMPLETED | CANCELLED | ERROR ─► IDLE
```

A tabela completa está no código e é verificada por `RecorderTransitionsTest`:

- todo estado é alcançável a partir de IDLE;
- os terminais só voltam para IDLE;
- **nenhum estado confirmado é alcançado sem passar pelo pendente**: `IDLE → RECORDING`, `RECORDING → PAUSED` e
  `RECORDING → COMPLETED` são proibidos.

## Regras de confirmação

| Situação | Resultado |
|---|---|
| Resposta de sucesso | aplica a fotografia (estado, modo, aba, captura, cobertura, contadores) |
| Recusa explícita (ex.: `CAPTURE_FAILED`) | volta ao estado estável anterior e explica o problema; nunca finge sucesso |
| Resultado desconhecido (`TIMEOUT`, `LINK_LOST`) | com ligação: RECOVERING + `studio.recording.status`; sem ligação: DEGRADED (`LINK_LOST`) |
| `RECORDING_ENDED`, `RECORDING_NOT_FOUND`, `INVALID_STATE` | reconcilia pela consulta (a extensão sabe a verdade) |
| Início recusado (ex.: site não autorizado) | IDLE + aviso no Studio com a orientação da extensão ("Abra a Nekkora Extension no Chrome e conceda acesso ao site") |
| Ligação perdida durante STARTING; a consulta responde `RECORDING_NOT_FOUND` | IDLE + "Gravação não iniciada" |
| Evento `degraded` do navegador | DEGRADED com o motivo; "Tentar retomar" vira RECOVERING e envia `resume` |
| Evento `expired` | ERROR ("Gravação expirada") |
| Ligação perdida e o usuário desiste | "Encerrar no Studio" → ERROR, **sem** declarar a gravação finalizada; a extensão libera a captura quando a concessão expirar |

## Concorrência (determinística)

| Caso | Comportamento |
|---|---|
| Pausar duas vezes | o segundo comando não sai do Studio (o estado já não é RECORDING); na extensão, repetir é sem efeito (`duplicate`) |
| Continuar duas vezes | idem |
| Finalizar durante a pausa | permitido (PAUSED → FINALIZING) |
| Cancelar durante a finalização | o Studio não envia (FINALIZING não aceita outro comando); na extensão, a fila única dá um único desfecho e o perdedor recebe `RECORDING_ENDED` |
| Ligação perdida durante o início | DEGRADED; ao voltar, a consulta decide entre RECORDING e IDLE "não iniciada" |
| Aba fechada | evento `degraded`/`TAB_CLOSED`; retomar → `TAB_NOT_FOUND`; finalizar registra `INTERRUPTED` |
| Permissão revogada | evento `degraded`/`PERMISSION_REVOKED`; retomar → `ORIGIN_NOT_ALLOWED` até nova autorização |

## Concessão e encerramento

- Início com `leaseMs = 30000`; `studio.recording.renew` a cada 10 s enquanto há gravação e ligação.
- **Saída normal do Studio com gravação**: o diálogo pergunta "Finalizar e sair", "Cancelar e sair" ou "Voltar ao
  Studio". O Studio envia o comando e espera a confirmação por até 5 s antes de fechar.
- **Queda abrupta ou travamento**: as renovações param e, depois de 30 s no máximo, a extensão expira a gravação e
  libera **só** o consumidor do Recorder. Homologado com uma concessão de 10 s.

## Associação com o domínio (seção 17)

`TechnicalRecording` tem identidade própria (`rec-<uuid>`, técnica e nunca exibida), pertence a uma automação e
guarda o histórico de associações a sessões lógicas, para a STUDIO-003 direcionar as etapas. Trocar de sessão
(`associate`) não envia nada ao navegador e não reinicia a captura. "Nova sessão" no Recorder usa o caso de uso
existente (`StudioController.newSession`, mesmo formulário e mesmas validações) e associa a sessão criada.

**Nenhuma etapa é criada.** A porta `StepIntake` continua disponível e intocada para a STUDIO-003. Os testes
`s002029…`, no Recorder e no Studio, comparam a sequência e a revisão do domínio antes e depois de um ciclo completo.
