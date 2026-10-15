# STUDIO-004 — Reorganizar, mover, duplicar e excluir etapas

## Operações

| Operação | Interface | Domínio |
|---|---|---|
| Mover uma posição na sessão | **Alt+↑ / Alt+↓** com o foco na lista; menu de contexto; **Para cima/Para baixo** no painel | `moveStep(stepId, mesmaSessão, índice)` |
| Mover para outra posição ou sessão | **Mover para…** (menu e painel) → diálogo **Mover etapa** (sessão de destino + "No início", "Depois de 02 · …", "No final") | `moveStep(stepId, destino, índice)` |
| Duplicar | **Ctrl+D** com o foco na lista; menu; **Duplicar** no painel | `duplicateStep(stepId)` |
| Excluir | **Delete** com o foco na lista; menu; **Excluir** no painel, sempre com confirmação | `removeStep(stepId)` |

## Regras de `moveStep(stepId, destination, targetIndex)`

- `targetIndex` é a posição final, de 0 a n, na sessão de destino, contada **sem** a própria etapa.
- Origem, destino e posição são validados **antes** de qualquer alteração:
  - destino inexistente: `SESSION_NOT_FOUND`;
  - posição inválida: `STEP_POSITION_INVALID`;
  - a etapa nunca fica fora de uma sessão.
- Só sessões da mesma automação: uma sessão de outra automação é "não encontrada".
- Identidade, procedência, referência do elemento, parâmetros, comentários e observações capturadas não mudam (é o mesmo
  objeto).
- Uma única revisão por movimentação. Na mesma posição, nada muda.
- Seleção: a mesma etapa, na sessão de destino.
- Tela: só as sessões de origem e destino são relidas. As demais apenas renumeram, se a numeração mudou.

## Duplicar

- A cópia entra logo depois da original e fica selecionada.
- Ela recebe um `StepId` **novo** e origem `MANUAL`. Não tem chaves de gravação nem de evento, nem
  `RecordingObservation`, nem valor capturado, e não aumenta a contagem de etapas gravadas.
- Copia nome, descrição, tipo, parâmetros, a referência **efetiva** do elemento, os localizadores propostos (continuam
  "não verificado") e o contexto. Não copia comentários.
- A referência do elemento vai **sem** as chaves `RECORDING_ID` e `RECORDED_EVENT_ID`, que identificam a interação
  observada e não o elemento (o Recorder as põe ali). Ficam `TARGET_ID` e os demais dados para reconhecer o elemento.
- O vínculo com a original é `derivedFrom`, interno e nunca exibido como identificador. A interface mostra "Manual —
  duplicada de outra etapa".
- Cancelar a gravação de origem não remove a cópia, porque ela não pertence à gravação.

## Excluir

```
Excluir etapa?

Clicar no botão Entrar

Esta operação removerá a etapa da sessão Autenticação. Você pode desfazer enquanto o histórico desta automação existir.

[Cancelar] [Excluir]
```

Seleção depois da exclusão, de forma determinística:

1. a próxima etapa da sessão;
2. se não houver, a anterior;
3. se a sessão ficou vazia, a própria sessão.

Nenhuma seleção aponta para uma etapa inexistente: depois de qualquer operação, a seleção é conferida e, se ficou
inválida, volta para a automação.

## Numeração

A numeração é calculada pela ordem das coleções e recalculada depois de cada operação. Ela não é identidade e não é
guardada.

## Evidência

- Domínio: `AutomationEditingTest.reorganizarNaSessao`, `moverEntreSessoes`, `duplicar` e `excluirEtapa`.
- Aplicação: `EditorStudioTest.reorganizarPeloTeclado`, `moverEntreSessoes`, `duplicar`, `excluirEtapa` e
  `incremental`.
- Homologação real, cenários 3, 6 e 7.
