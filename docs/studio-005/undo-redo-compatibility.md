# STUDIO-005 — Compatibilidade com desfazer/refazer

## Requisitos

- A chegada de evidência **não entra** no histórico.
- **Desfazer não apaga** evidências e **refazer não as reaplica**.
- O histórico **não é invalidado** indiscriminadamente.
- Há testes de concorrência.

## Como foi garantido

1. **Fora da fotografia.** O histórico da STUDIO-004 restaura `AutomationStep.State` (estado editável) e a estrutura
   (sessões e ordem, com os **mesmos objetos** de etapa). As evidências ficam num campo próprio da etapa, fora do
   `State`. Ao restaurar, a etapa volta com o que tinha de evidências naquele objeto, e nada é recriado nem duplicado.
2. **Fora da revisão.** `Automation.attachEvidence` não chama `touch()`. A revisão esperada pelas entradas do histórico
   continua igual, e o desfazer/refazer não vê "conflito" (`HISTORY_CONFLICT`) por causa de evidências.
3. **Fora do caso de uso de edição.** `WorkspaceService.attachEvidence` não passa por `edit(...)`, então não registra
   `EditHistory.Entry`, e dispara `StudioChange.EVIDENCE`, não `HISTORY`.
4. **Exclusão e desfazer.** Excluir uma etapa remove o objeto da sessão; desfazer o devolve com as mesmas evidências.

## Provas

| Teste | O que prova |
|---|---|
| `EvidenceDomainTest.s005031E032DesfazerERefazer…` | evidência chega depois de uma edição: revisão igual; restaurar "antes" e "depois" mantém 1 evidência |
| `RecorderEvidenceStudioTest.s005HistoricoEDuplicacao` | depois da gravação: editar → evidência tardia → `canUndo` continua verdadeiro → desfazer/refazer sem perder nem duplicar; repetida é idempotente |
| `RecorderEvidenceStudioTest.s005ConcorrenciaComHistorico…` | 60 evidências chegam da thread do Bridge enquanto o usuário desfaz/refaz outra automação 20 vezes: todas registradas em ordem; o histórico da outra fica íntegro |
| `RecorderEvidenceStudioTest.s005BloqueioDeEdicaoComRecepcao` | durante a gravação: o tamanho do histórico e a revisão continuam iguais depois da evidência |
| homologação, cenário 9 | Ctrl+Z / Ctrl+Y reais (teclado do sistema) na etapa "Entrar" com as evidências do cenário 1: a quantidade é a mesma depois de desfazer e de refazer |

## Limite conhecido

A STUDIO-004 já descarta o histórico quando **etapas** do Recorder chegam a uma automação com edições anteriores (revisão
diferente). Isso não mudou e não é causado por evidências.
