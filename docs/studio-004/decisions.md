# STUDIO-004 — Decisões

| Decisão | Definição | Como foi implementada |
|---|---|---|
| D-S004-01 | O domínio continua sendo a fonte única da estrutura da automação | Toda alteração é uma operação de `Automation`. A tela relê o domínio, sem cópias |
| D-S004-02 | Identidades de etapas e sessões permanecem estáveis ao movimentar | `moveStep`/`moveSession` movem o mesmo objeto. Desfazer restaura as mesmas instâncias |
| D-S004-03 | A ordem é determinada pelas coleções, nunca por números persistidos | A numeração é calculada (`Automation.sequence()`). Nada guarda "número" |
| D-S004-04 | Alterações estruturais são transacionais | Cada operação valida tudo antes de alterar. Recusa = nenhuma mudança, nenhuma entrada no histórico, revisão igual |
| D-S004-05 | A procedência original das etapas gravadas é preservada | `StepProvenance`, `RecordingObservation`, `CapturedBaseline` e o `ElementReference` capturado são imutáveis |
| D-S004-06 | Alterações manuais são distinguíveis das informações capturadas | `ManualRevision` compara o estado efetivo com o `CapturedBaseline`, campo a campo |
| D-S004-07 | Localizadores editados manualmente não são considerados validados automaticamente | `ManualLocator` é sempre `NOT_VERIFIED`, sem `matchCount`/`matchesTarget`, e nunca pode ser preferencial |
| D-S004-08 | Valores sensíveis nunca são armazenados como literais | `LiteralValue` recusa aparência de segredo; literal em elemento sensível é recusado; comentários e localizadores manuais também passam pela barreira |
| D-S004-09 | Desfazer/refazer funciona somente em memória e sobre operações controladas | `EditHistory` guarda fotografias (`Automation.Snapshot`) de operações confirmadas, até 100 por automação |
| D-S004-10 | Alterações estruturais não podem disputar o estado com uma gravação ativa | `RecordingGuard` no `WorkspaceService` recusa a edição da automação em gravação (início, pausa, interrupção, encerramento, reconciliação ou entrega pendente) |
| D-S004-11 | A interface utiliza exclusivamente o Nekkora Design System | Abas, rádios, menus, banners, diálogos e badges do DS. Nenhuma cor fora dos tokens (`DesignLintTest`) |
| D-S004-12 | Nenhuma edição representa execução ou validação de negócio | `BusinessOutcome` continua `NOT_VALIDATED`. Nada é executado; o editor não fala em "salvo" (só em memória) |

## Decisões complementares

- **D-S004-13: o editor é o `StepDetailsForm` evoluído.** A missão pede para evoluir a visualização de detalhes. A
  classe continua sendo a mesma (`stepId()`, `basics()`, `overviewRows()` preservados) e ganhou abas. Não existe uma
  segunda estrutura de propriedades.
- **D-S004-14: uma edição = uma operação.** O editor acumula as mudanças das abas e "Salvar alterações" aplica um único
  `editStep`, com uma revisão e uma entrada no histórico. "Cancelar" descarta tudo.
- **D-S004-15: duplicar copia a referência efetiva, não a gravação.** A cópia tem `StepId` novo, origem `MANUAL`, nenhuma
  chave de gravação ou de evento, nenhuma observação e nenhum valor capturado. O vínculo com a original é
  `derivedFrom` (interno).
- **D-S004-16: mudar a sessão no editor leva a etapa para o final da sessão escolhida.** A posição exata é escolhida pelo
  diálogo "Mover etapa".
- **D-S004-17: "Nova sessão" continua permitida durante a gravação.** É assim que o Floating Recorder (STUDIO-002/003)
  cria a próxima sessão. Acrescentar ao final não disputa a ordem com as etapas gravadas. As demais edições ficam
  bloqueadas, inclusive "Nova etapa" manual e desfazer.
- **D-S004-18: conflito do histórico.** Desfazer/refazer só se aplica se a automação estiver exatamente na revisão
  esperada. Se ela mudou por outro caminho (etapas do Recorder, cancelamento), o histórico daquela automação é descartado
  e o usuário vê o motivo. Nada é aplicado sobre um estado diferente.
- **D-S004-19: desfazer a digitação no campo.** Ctrl+Z/Ctrl+Y dentro de um campo de texto pertencem ao campo (Design
  System, `TextUndo`). Os atalhos da janela também ignoram o foco em campos.
