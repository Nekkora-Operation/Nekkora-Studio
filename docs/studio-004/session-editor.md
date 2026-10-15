# STUDIO-004 — Sessões lógicas: editar, reorganizar e excluir

## Editar

- **F2** com a sessão selecionada, o ícone de lápis no cabeçalho da sessão ou **Editar** no painel.
- O diálogo **Sessão — nome** tem nome, descrição e **observações**.
- `Automation.editSession` valida tudo antes, aplica numa única operação e conta uma revisão. Nome duplicado ou texto
  longo demais: nada muda.
- `SessionId`, etapas, ordem, seleção e associação com a automação são preservados.

## Reorganizar

- Pelo menu **Mais ações** da sessão ("Mover sessão para cima/para baixo") ou pelos botões **Para cima/Para baixo** do
  painel. Nos limites, o botão fica indisponível, e pedir mesmo assim não faz nada.
- `Automation.moveSession(sessionId, targetIndex)`: `targetIndex` é a posição final, de 0 a n-1. Fora dos limites
  é recusado com `SESSION_POSITION_INVALID`. Na mesma posição, nada muda e a revisão é a mesma.
- A numeração das etapas é derivada da ordem e é recalculada na hora. A tela reordena as mesmas visões de sessão, sem
  recriá-las.
- Os controles funcionam pelo teclado (menu de contexto com Shift+F10 e botões focáveis). Não há arrastar e soltar.

## Excluir

- Pelo menu **Mais ações** → **Excluir sessão** ou pelo botão **Excluir** do painel. Sempre há uma confirmação
  explícita:

```
Excluir sessão?

Sessão: Consulta
Etapas existentes: 8

A exclusão também removerá as 8 etapas contidas nesta sessão. Você pode desfazer enquanto o histórico desta automação
existir.

[Cancelar] [Excluir sessão e etapas]
```

- Sessão vazia: o botão é "Excluir sessão".
- `Automation.removeSession` remove a sessão com as etapas dela de uma vez, sem deixar nenhuma etapa órfã. A automação
  pode ficar sem sessões.
- Seleção depois da exclusão: a sessão que ocupou o lugar, senão a anterior, senão a automação.
- Desfazer devolve a sessão e as etapas com as mesmas identidades.

## Evidência

- Domínio: `AutomationEditingTest.sessao`, `reorganizarSessoes` e `excluirSessoes`.
- Aplicação: `EditorStudioTest.sessao`, `reorganizarSessoes` e `excluirSessao` (cancelar, vazia e com etapas).
- Homologação real, cenário 2.
