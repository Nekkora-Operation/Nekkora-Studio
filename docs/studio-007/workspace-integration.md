# STUDIO-007 — Integração com o workspace

## ValidationService (app, somente leitura)

- `validate(id)`: valida o estado EFETIVO atual na EDT e guarda o relatório com o carimbo (objeto da automação, objeto
  do `ProjectState` e sua geração). Dispara `StudioChange.VALIDATION`.
- `result(id)`: último relatório e se ele é **atual**: mesma automação (recarregar troca o objeto), mesmo estado de
  persistência e mesma geração.
- `forget(id)`: ao fechar o projeto.

## Por que a geração de persistência

A geração da STUDIO-006 muda com toda alteração a salvar: edições, etapas do Recorder, evidências (que **não** mudam
`Automation.revision()`), desfazer/refazer, renomear. Usá-la (só para leitura) cobre o que a revisão do domínio não
cobre. O motor não a modifica. Na dúvida, o relatório é dado como desatualizado — nunca o contrário (um Ctrl+Z que volta
ao estado validado também marca desatualizado; basta validar de novo).

## Garantias (testadas)

| Garantia | Teste |
|---|---|
| validar não edita a automação (revisão, instante, etapas, parâmetros) | `AutomationValidatorTest.naoAltera` |
| validar não entra no desfazer/refazer | `ValidationStudioTest` S007-040 |
| validar não marca "não salvo" (mesma geração, aba sem `*`) | S007-039 |
| validar não grava nada (arquivo com o mesmo SHA, nenhum arquivo novo) | S007-038 |
| projeto carregado valida igual ao salvo | S007-037 |
| edição, Undo, Redo, evidência, etapa do Recorder, renomear e recarregar invalidam | S007-034/035/036/046/047, `duranteGravacaoERecarga` |
| outra automação ativa não mostra o relatório da anterior | `outraAutomacao` |
| validar durante uma gravação é permitido (consulta), editar não | `duranteGravacaoERecarga`; homologação cenário 5 |
| nenhum comando ao navegador | S007-041/042/043 |

## Condições especiais

| Situação | Comportamento |
|---|---|
| automação sem sessões / sessão vazia | `AUTOMATION_HAS_NO_SESSIONS` / `SESSION_EMPTY` |
| Recorder ativo | validar permitido; o painel fica desatualizado assim que chega a próxima etapa ou evidência |
| alterações não salvas | valida o estado em memória |
| recuperação pendente | nada é tocado (validar não lê nem escreve fotografias) |
| projeto recarregado do arquivo | relatório anterior desatualizado (objeto novo) |
| correções automáticas | não existem: o motor aponta, o usuário decide |
