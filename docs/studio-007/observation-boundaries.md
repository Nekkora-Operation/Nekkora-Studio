# STUDIO-007 — Limites entre configuração e observação

Diagnóstico de configuração e evidência de comportamento permanecem separados.

| `ObservationSummary.Status` (STUDIO-005) | Apontamento | Severidade |
|---|---|---|
| `OBSERVED` | `OBSERVATION_SIGNALS` | informação |
| qualquer status com indicador de erro | + `OBSERVATION_ERROR_INDICATOR` | informação |
| `INCOMPLETE` | `OBSERVATION_INCOMPLETE` | informação |
| `IN_PROGRESS` | `OBSERVATION_IN_PROGRESS` | informação |
| `NOT_APPLICABLE`, `UNAVAILABLE`, `NOTHING_OBSERVED` | — | ausência de evidência não é configuração incorreta |
| evidências sem etapa definida | `UNATTRIBUTED_EVIDENCE` (automação) | informação |

## Regras

- Observação é sempre **informação**: nunca bloqueio, nunca aviso (teste `s007028a030Evidencias`).
- Uma evidência não resolve um bloqueio: o `FILL` gravado continua sem valor mesmo com sinais observados.
- A ausência de evidência não indica etapa mal configurada.
- "Foram observados indicadores de erro após esta interação. Isso não conclui que a operação falhou, nem que foi
  validada." — o motor nunca conclui falha nem sucesso.
- `BusinessOutcome` continua `NOT_VALIDATED`; o relatório não tem campo de resultado de negócio, e o teste confere que
  não há `VALIDATED`, `SUCCESS` nem `PASSED` no JSON.
- O texto da página (mensagens) nunca chega ao relatório: o motor só usa contagens e classificações do resumo.

Homologação: cenário 5 (evidência real do laboratório fictício da STUDIO-005).
