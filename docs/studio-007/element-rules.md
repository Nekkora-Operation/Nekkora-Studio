# STUDIO-007 — Regras de elementos

## Elemento ausente × localizador ausente

| Situação | Código |
|---|---|
| ação com alvo `EXPECTED` e nenhuma `ElementReference` | `ELEMENT_MISSING` (bloqueio) |
| elemento identificado, mas sem nenhum localizador (capturado ou manual) | `LOCATOR_MISSING` (bloqueio) — ver [locator-rules.md](locator-rules.md) |

Um elemento pode ter sido identificado sem produzir um localizador transportável (privacidade, falta de localizador
seguro e único): isso é `LOCATOR_MISSING`, nunca `ELEMENT_MISSING`.

Etapas criadas manualmente não têm elemento, e o domínio só aceita localizadores propostos manualmente em etapas com
elemento capturado. Por isso a orientação de `ELEMENT_MISSING` é gravar a etapa com o Recorder, e o documento de
limitações registra que o editor ainda não vincula elementos a etapas manuais.

## Obstáculos observados

Cada `ObservedObstacle` vira `OBSTACLE_HIGH` (aviso), `OBSTACLE_MEDIUM` (aviso) ou `OBSTACLE_LOW` (informação),
conforme a gravidade informada pelo produtor. O tipo só entra no relatório se pertencer ao vocabulário fechado
(`FindingDetails`: os 22 tipos do PageMap 0.2.0 e os 4 do Recorder); qualquer outro vira `OTHER`. A descrição livre do
produtor nunca é copiada.

## Continuidade técnica não é identidade lógica (CHROME-SECURITY-001, D7)

`RECORDER_LIVE_CHECK_UNAVAILABLE` explica: "A continuidade técnica de um elemento não comprova que ele representa o
mesmo alvo funcional." O motor nunca promove um elemento a "o mesmo" por continuidade de nó, e as obstáculos
`ELEMENT_UNRESOLVED`, `AMBIGUOUS_TARGET` e `INDISTINGUISHABLE_LOOKALIKES` aparecem como tal.

## Privacidade

Nome acessível vazio, `id`/`name` omitidos ou localizadores descartados pela `nekkora-privacy/2` **não** são defeito: o
nome omitido não gera apontamento, e os descartes geram só informações (`PRIVACY_*`). Nenhum texto sugere desativar a
proteção para recuperar nomes ou localizadores (teste `s007026Privacidade`).

Testes: `AutomationValidatorTest` S007-005, 019, 020, 025, 026.
