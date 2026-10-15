# STUDIO-005 — Resultado de negócio

A STUDIO-005 separa quatro coisas:

| Conceito | O que é | Onde |
|---|---|---|
| **Interação** | a ação do usuário (etapa) | `AutomationStep` (STUDIO-003) |
| **Observação** | a janela delimitada em que a página foi acompanhada | `OBSERVATION_COMPLETE/INCOMPLETE`, `ObservationSummary.status` |
| **Evidência** | um acontecimento observado (mensagem, validação, navegação…) | `ObservedEvidence` |
| **Resultado de negócio** | se a operação de negócio deu certo | **não avaliado**: `NOT_VALIDATED` |

## Garantias

- O contrato não tem campo de resultado de negócio. A fixture `s005-evidence-business-outcome` (`businessOutcome: SUCCESS`) é recusada.
- `EvidenceType` não tem tipo de sucesso ou falha (`EvidenceDomainTest.s005064…`).
- `ObservationSummary` força `NOT_VALIDATED` no construtor, qualquer que seja a evidência.
- A interface diz "Resultado de negócio: Não validado" no painel, na aba Evidências e no resumo do Floating Recorder.
- Homologação: a mensagem de erro (cenário 1), o "Pedido registrado com sucesso." (cenário 2) e "3 resultados
  encontrados." (cenário 5) foram observados, e o resultado de negócio continuou **não validado** em todos.
- O teste de interface procura e não encontra "Sucesso"/"Falha" no painel; o IT procura `businessOutcome`/`SUCCESS`/`FAILURE` nas evidências.

A validação de negócio (asserções, critérios de aceite) depende de execução e fica fora desta missão.
