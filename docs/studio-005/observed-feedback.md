# STUDIO-005 — Retorno observado (resumo estruturado)

`ObservationSummary` (domínio) substitui o texto livre por um resumo **estruturado e derivado**, calculado de forma
determinística a partir das evidências da etapa (`AutomationStep.observation()`). O campo antigo `observedOutcome`
(texto, STUDIO-001) continua existindo e não foi alterado.

## Situação (`Status`)

| Situação | Quando | Texto para o usuário |
|---|---|---|
| `NOT_APPLICABLE` | etapa manual, duplicada, ou proposta (`CAPTURE_ONLY`) | "Não se aplica — nada foi executado na página." |
| `UNAVAILABLE` | etapa executada sem nenhuma evidência (extensão sem a capacidade, nada recebido) | "Observação indisponível" |
| `IN_PROGRESS` | há evidências, mas a janela ainda não terminou | "Observação em andamento" |
| `NOTHING_OBSERVED` | janela concluída sem sinais | "Nenhum sinal observado — **isso não significa que não houve erro**" |
| `OBSERVED` | janela concluída com sinais | "Mudanças observadas — nenhuma delas é validação de negócio" |
| `INCOMPLETE` | janela interrompida (navegação, pausa, encerramento) ou cobertura parcial | "Observação incompleta" + o motivo |

## Demais campos

Contagem por categoria (formulário, navegação, mensagem, validação, carregamento, estado, conteúdo); `error` (algum
indicador de erro **observado**); `suppressed` (textos omitidos por privacidade); `cut` (motivo do corte); limitações;
última observação; `businessOutcome` = **NOT_VALIDATED** (forçado no construtor).

## Linguagem

- "Indicador de erro observado na página — não é validação de negócio". Nunca "falhou".
- "Mudanças observadas". Nunca "sucesso", mesmo quando o texto da página diz "Pedido registrado com sucesso.".
- A ausência é dita do jeito que foi: "nada observado", "indisponível", "interrompida" ou "não se aplica".
