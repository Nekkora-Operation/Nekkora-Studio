# STUDIO-003 — Roteamento por sessão lógica

A divisão em sessões continua manual: o usuário escolhe a sessão no diálogo de início e a troca pelo Floating
Recorder. Nada é inferido.

## Rotas

- No início, o Studio gera uma rota opaca (`route-1`) e guarda `route-1 → sessão escolhida`. O `start` leva
  `routeRef: route-1`.
- Na troca de sessão (Floating Recorder ou "Nova sessão"):
  1. o Studio cria `route-N → nova sessão` e marca a troca como **pendente**. A sessão apresentada e a associação
     da gravação continuam as anteriores;
  2. envia `studio.recording.route {routeRef: route-N}` com uma operação própria;
  3. só com a confirmação da extensão a gravação passa a ser associada à nova sessão.
- A extensão consolida a época da rota anterior e abre uma época nova, da rota nova, confirmada pelo sensor da
  página. Cada etapa leva a **rota da época em que a interação aconteceu**, não a vigente na chegada.
  `boundarySequence` é informativa.
- O Studio entrega cada etapa à sessão da sua rota. Os mapeamentos das rotas antigas são preservados durante toda a
  gravação, então uma etapa atrasada da sessão anterior **nunca** cai na nova.

## Troca pendente, recusada ou sem resposta

| Desfecho do `studio.recording.route` | O que o Studio faz |
|---|---|
| Confirmado | associa a gravação à nova sessão; o aviso some |
| Recusa explícita (ex.: `CAPTURE_FAILED`/`SENSOR_NOT_CONFIRMED`) | mantém a associação anterior e informa: "Sessão não alterada. O navegador não confirmou a troca de sessão. As próximas etapas continuam na sessão anterior." |
| Tempo esgotado | resultado **desconhecido**: nem sucesso nem falha. O mesmo pedido (mesma operação, idempotente na extensão) é refeito a cada renovação até haver resposta |
| Ligação perdida | continua pendente; ao reconectar, depois da consulta do estado, o mesmo pedido é refeito |
| Gravação encerrada | a troca pendente é descartada |

Enquanto a troca está pendente, o Floating Recorder mostra a sessão confirmada, bloqueia o seletor e avisa:
"Aguardando a confirmação da extensão para trocar para a sessão “Consulta”. Até lá, as etapas continuam na sessão
atual." Uma nova escolha feita nesse intervalo é aplicada depois, como outra troca.

## Evidência

- Studio:
  - `RecorderStepsTest.s003033…`: a etapa da rota antiga, chegando depois, foi para a sessão antiga;
  - `RecorderStepsStudioTest.s003TrocaDeSessao`: pela interface, a "Senha" atrasada ficou na Autenticação e o
    "Clientes" foi para a Consulta.
- Extensão:
  - Node `studio-steps.test.js`: a fronteira de sequência e a idempotência da operação;
  - Chrome real `Studio003InteractionsIT.s003Sessions`: o preenchimento aberto na troca saiu na rota anterior, com
    fronteira exata.
- Homologação real (cenário 6): login na Autenticação e consulta na Consulta, sem nenhuma etapa da Consulta na
  Autenticação.

Revisão técnica (P1): antes, o Studio associava a sessão nova antes da confirmação e só registrava a falha no log. O
Studio podia mostrar a Sessão B enquanto o Chrome seguia na A. Os testes `RecorderStepsTest.revTrocaDeSessao…`
(confirmada, recusada, tempo esgotado, desconexão, evento atrasado) falharam no código anterior (4 de 4) e passam
agora. `RecorderStepsStudioTest.revTrocaDeSessaoPendenteNaTela` confere a tela.
