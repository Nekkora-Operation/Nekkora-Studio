# STUDIO-003 — Roteamento por sessão lógica

A divisão em sessões continua manual: o usuário escolhe a sessão no diálogo de início e a troca pelo Floating
Recorder. Nada é inferido.

## Rotas

- No início, o Studio gera uma rota opaca (`route-1`) e guarda `route-1 → sessão escolhida`. O `start` leva
  `routeRef: route-1`.
- Na troca de sessão (Floating Recorder ou "Nova sessão"):
  1. o Studio associa a gravação à nova sessão;
  2. cria `route-N → nova sessão`;
  3. envia `studio.recording.route {routeRef: route-N}`.
- A extensão consolida o que estava aberto, espera as interações chegarem e só então passa a carimbar `route-N`.
  Ela devolve `boundarySequence` (a última sequência da rota anterior).
- Cada etapa carrega a rota vigente **na sua chegada** à extensão. O Studio entrega cada uma à sessão da sua rota,
  então uma etapa atrasada da sessão anterior **nunca** cai na nova.

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

Se o comando de rota não for confirmado (ex.: ligação perdida), a extensão continua carimbando a rota anterior. As
etapas vão, coerentemente, para a sessão anterior, e o aviso fica no log do Studio.
